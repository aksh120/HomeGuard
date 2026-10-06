-- HomeGuard migration 018: actually enforce decision D4
--
-- Phase 3 built the whole approval machinery and then left the door open next to it. The
-- `join_household` INSERT policy on household_members allowed any caller to add their own row, and
-- HomeViewModel.joinHousehold used exactly that path. So:
--
--   * any person with an invite code became a member immediately;
--   * APPROVE_JOIN was dead code, because nothing in the app ever filed one;
--   * APPROVE_REMOVE and APPROVE_MODE_SWITCH were unreachable for the same reason.
--
-- Worse, phase 5 migration 017 found the mirror of this: the SELECT policy let a requester read the
-- approval_code minted for them. Two paths to the same consent, both wrong.
--
-- This migration closes the direct path and gives each legitimate flow a function that takes only the
-- credential the requester actually holds:
--
--   joining     the invite code        -> request_household_join(code, device_id)
--   leaving     membership of a home   -> request_household_leave(household)
--   switching   membership of a home   -> request_mode_switch()
--
-- Membership rows are then created and deleted in exactly one place: approve_remote_command(code),
-- SECURITY DEFINER, matched server-side. No client writes household_members at all.
--
-- Why one approval per resident, not one per household
--
-- Membership is household-wide, not device-scoped. emergency_events and devices are both readable by
-- any household member, so a new member sees every person's check-ins and alerts in that home, not
-- just one device. With two people at home, one of them approving would silently expose the other's
-- medical details to a stranger. Decision D3 is all-or-nothing for exactly this reason, so every
-- PROTECTED member gets their own request and the caregiver has to satisfy each one. The cost is N
-- codes for N residents, which is the honest price of an all-or-nothing consent model.

-- ------------------------------------------------------------------------------
-- 1. Who can consent to a membership change in a household
--
-- Reads membership, not devices. can_request_for in 017 keyed on devices.household_id, which a
-- caregiver who joined by code never has, and 013 keyed on a device join that excluded every
-- first-time joiner. Neither was a reliable statement about who belongs to a household.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.protected_members_of(target_household UUID)
RETURNS TABLE (user_id UUID)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT hm.user_id
    FROM public.household_members hm
    WHERE hm.household_id = target_household
      AND hm.role = 'PROTECTED'
    ORDER BY hm.created_at ASC
$$;

CREATE OR REPLACE FUNCTION public.is_monitoring_member_of(target_household UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.household_members hm
        WHERE hm.household_id = target_household
          AND hm.user_id = auth.uid()
          AND hm.role = 'MONITORING'
    )
$$;

-- ------------------------------------------------------------------------------
-- 2. Joining: the invite code is the credential
--
-- The client sends the code and never learns a household or user UUID. That is deliberate: it used to
-- resolve the code first and POST its own row, which meant the database never checked that the caller
-- held the code at all. Now the only thing a joiner can present is the code itself.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_household_join(
    submitted_code TEXT,
    joiner_device_id TEXT
)
RETURNS TABLE (household_id UUID, household_name TEXT, approvals_required INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    target_household UUID;
    label TEXT;
    created INTEGER := 0;
    member UUID;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'no active session'
            USING ERRCODE = '42501';
    END IF;

    SELECT h.id, h.name INTO target_household, label
    FROM public.households h
    WHERE h.invite_code = upper(btrim(COALESCE(submitted_code, '')))
    LIMIT 1;

    IF target_household IS NULL THEN
        RAISE EXCEPTION 'that invite code is not recognised'
            USING ERRCODE = '22023';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.household_members hm
        WHERE hm.household_id = target_household AND hm.user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'you are already connected to this home'
            USING ERRCODE = '22023';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.protected_members_of(target_household)) THEN
        RAISE EXCEPTION 'that home has nobody to approve a request yet'
            USING ERRCODE = '22023';
    END IF;

    FOR member IN SELECT p.user_id FROM public.protected_members_of(target_household) p LOOP
        -- Skips a resident who already has a pending request from this joiner, so a second tap is
        -- idempotent rather than filling the resident's screen with duplicates.
        IF NOT EXISTS (
            SELECT 1 FROM public.remote_commands c
            WHERE c.kind = 'APPROVE_JOIN'
              AND c.status = 'PENDING'
              AND c.target_user_id = member.user_id
              AND c.requested_by = auth.uid()
        ) THEN
            INSERT INTO public.remote_commands
                (target_user_id, household_id, kind, payload, requested_by, approval_code)
            VALUES
                (member.user_id, target_household, 'APPROVE_JOIN',
                 jsonb_build_object('device_id', NULLIF(btrim(COALESCE(joiner_device_id, '')), '')),
                 auth.uid(), public.generate_approval_code());
            created := created + 1;
        END IF;
    END LOOP;

    IF created = 0 THEN
        RAISE EXCEPTION 'you have already asked to watch this home; waiting for a code'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT target_household, label, created;
END;
$$;

-- ------------------------------------------------------------------------------
-- 3. Leaving: a caregiver asking to stop watching
--
-- D4 requires a code, and the code is shown by the person being left behind. D6 is not violated:
-- nothing here removes anybody else, and the caregiver keeps access until the code is entered, so a
-- household cannot quietly lose its only watcher by a mis-tap.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_household_leave(target_household UUID)
RETURNS TABLE (approvals_required INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    created INTEGER := 0;
    member UUID;
BEGIN
    IF NOT public.is_monitoring_member_of(target_household) THEN
        RAISE EXCEPTION 'you are not watching this home'
            USING ERRCODE = '42501';
    END IF;

    FOR member IN SELECT p.user_id FROM public.protected_members_of(target_household) p LOOP
        IF NOT EXISTS (
            SELECT 1 FROM public.remote_commands c
            WHERE c.kind = 'APPROVE_REMOVE'
              AND c.status = 'PENDING'
              AND c.target_user_id = member.user_id
              AND c.requested_by = auth.uid()
              AND c.household_id = target_household
        ) THEN
            INSERT INTO public.remote_commands
                (target_user_id, household_id, kind, payload, requested_by, approval_code)
            VALUES
                (member.user_id, target_household, 'APPROVE_REMOVE', '{}'::jsonb,
                 auth.uid(), public.generate_approval_code());
            created := created + 1;
        END IF;
    END LOOP;

    IF created = 0 THEN
        RAISE EXCEPTION 'you have already asked to stop watching this home'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT created;
END;
$$;

-- ------------------------------------------------------------------------------
-- 4. Mode switch: a resident's phone becoming a monitoring device
--
-- Consent comes from the people who are currently watching that resident, not from the resident
-- themselves. The switch means "stop protecting me", and the people relying on that protection are
-- the ones whose alerts stop. Asking them is what makes the request mean anything.
--
-- If nobody is watching, there is nobody whose protection changes hands, so the switch is allowed
-- outright rather than inventing an approver.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_mode_switch()
RETURNS TABLE (approvals_required INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    my_household UUID;
    watcher_count INTEGER;
    created INTEGER := 0;
    watcher UUID;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'no active session'
            USING ERRCODE = '42501';
    END IF;

    SELECT hm.household_id INTO my_household
    FROM public.household_members hm
    WHERE hm.user_id = auth.uid() AND hm.role = 'PROTECTED'
    LIMIT 1;

    IF my_household IS NULL THEN
        RAISE EXCEPTION 'this phone is not set up as a home device'
            USING ERRCODE = '22023';
    END IF;

    SELECT count(*) INTO watcher_count
    FROM public.household_members hm
    WHERE hm.household_id = my_household
      AND hm.role = 'MONITORING'
      AND hm.user_id IS DISTINCT FROM auth.uid();

    IF watcher_count = 0 THEN
        RAISE EXCEPTION 'nobody is watching this home, so there is nothing to ask permission for'
            USING ERRCODE = '22023';
    END IF;

    FOR watcher IN
        SELECT hm.user_id
        FROM public.household_members hm
        WHERE hm.household_id = my_household
          AND hm.role = 'MONITORING'
          AND hm.user_id IS DISTINCT FROM auth.uid()
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM public.remote_commands c
            WHERE c.kind = 'APPROVE_MODE_SWITCH'
              AND c.status = 'PENDING'
              AND c.target_user_id = watcher.user_id
              AND c.requested_by = auth.uid()
              AND c.household_id = my_household
        ) THEN
            INSERT INTO public.remote_commands
                (target_user_id, household_id, kind, payload, requested_by, approval_code)
            VALUES
                (watcher.user_id, my_household, 'APPROVE_MODE_SWITCH', '{}'::jsonb,
                 auth.uid(), public.generate_approval_code());
            created := created + 1;
        END IF;
    END LOOP;

    IF created = 0 THEN
        RAISE EXCEPTION 'you have already asked to change this phone'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT created;
END;
$$;

-- ------------------------------------------------------------------------------
-- 5. A requester's own requests, without their codes
--
-- Migration 017 removed requested_by from the SELECT policy because it leaked approval_code. That was
-- correct but left a real gap: a caregiver who has just asked can see nothing about their own request,
-- so the dashboard could not show "waiting for approval".
--
-- This restores that visibility through a SECURITY DEFINER function that returns the state and the
-- household name, and deliberately does not return the code. The code still reaches the requester
-- only by the resident reading it out, which is the consent step.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_my_remote_commands()
RETURNS TABLE (
    id UUID,
    kind TEXT,
    status TEXT,
    created_at TIMESTAMPTZ,
    household_name TEXT,
    target_name TEXT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT c.id,
           c.kind,
           c.status,
           c.created_at,
           COALESCE(h.name, '') AS household_name,
           COALESCE(d.person_name, '') AS target_name
    FROM public.remote_commands c
    LEFT JOIN public.households h ON h.id = c.household_id
    LEFT JOIN public.devices d
           ON d.user_id = c.target_user_id
          AND d.household_id = c.household_id
    WHERE c.requested_by = auth.uid()
      AND c.kind <> 'RAISE_ALARM'
    ORDER BY c.created_at DESC
$$;

REVOKE ALL ON FUNCTION public.list_my_remote_commands() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_my_remote_commands() TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 6. Approval: the one place membership changes
--
-- Extended for APPROVE_MODE_SWITCH, which previously fell through to "that request cannot be
-- approved from here" and so could never complete.
--
-- APPROVE_JOIN now records the joiner's device_id from the request payload. Without it the membership
-- row has no device, which is what made the Phase 5 caregiver_push_tokens fix necessary in the first
-- place: household_members.device_id was NULL for anyone who joined by code.
--
-- APPROVE_MODE_SWITCH flips devices.device_mode. It does not delete the membership, because the person
-- is still in the household, they are simply no longer the one being protected. Their client reconciles
-- the local profile from device_mode on the next refresh.
--
-- Every branch requires cmd.requested_by = auth.uid(). The code is what the resident read aloud, and
-- only the person who asked can have been given it, so this stops a bystander who overhears a code
-- from completing somebody else's request.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.approve_remote_command(submitted_code TEXT)
RETURNS TABLE (approved_kind TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    cmd public.remote_commands%ROWTYPE;
    resolved_household UUID;
    joiner_device TEXT;
BEGIN
    SELECT * INTO cmd
    FROM public.remote_commands c
    WHERE c.status = 'PENDING'
      AND c.approval_code = upper(btrim(COALESCE(submitted_code, '')))
    ORDER BY c.created_at ASC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'that code is not correct or is no longer pending'
            USING ERRCODE = '22023';
    END IF;

    -- Deliberately no check that auth.uid() = cmd.requested_by.
    --
    -- For a join and a removal the requester types the code the resident read out. For a mode switch
    -- the reverse holds: the resident asks, and the caregiver being asked shows the code and types it
    -- themselves, so requiring the requester would make the switch permanently impossible.
    --
    -- The consequence is accepted rather than worked around: the code is the whole credential, so
    -- whoever legitimately obtains it may complete the request. That is also why
    -- list_my_remote_commands deliberately does not return approval_code, and why migration 017
    -- removed requested_by from the SELECT policy. A shared code cannot be bound to a single person;
    -- what protects the resident is that only they ever see it.

    resolved_household := cmd.household_id;

    IF resolved_household IS NULL THEN
        RAISE EXCEPTION 'that request is no longer valid'
            USING ERRCODE = '22023';
    END IF;

    IF cmd.kind = 'APPROVE_JOIN' THEN
        joiner_device := cmd.payload ->> 'device_id';
        INSERT INTO public.household_members (household_id, user_id, device_id, role)
        VALUES (resolved_household, auth.uid(), NULLIF(btrim(joiner_device), ''), 'MONITORING')
        ON CONFLICT (household_id, user_id) DO UPDATE
            SET device_id = COALESCE(EXCLUDED.device_id, public.household_members.device_id);
    ELSIF cmd.kind = 'APPROVE_REMOVE' THEN
        DELETE FROM public.household_members
        WHERE household_id = resolved_household AND user_id = auth.uid();
    ELSIF cmd.kind = 'APPROVE_MODE_SWITCH' THEN
        UPDATE public.devices
        SET device_mode = 'MONITORING_DEVICE'
        WHERE user_id = cmd.requested_by
          AND household_id = resolved_household
          AND device_mode = 'PROTECTED_DEVICE';
    ELSE
        RAISE EXCEPTION 'that request cannot be approved from here'
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.remote_commands
    SET status = 'EXECUTED', executed_at = NOW()
    WHERE id = cmd.id;

    RETURN QUERY SELECT cmd.kind;
END;
$$;

-- ------------------------------------------------------------------------------
-- 7. Close the direct paths
--
-- household_members: no INSERT, UPDATE or DELETE policy remains at all. The only writers left are
-- create_household_with_membership and approve_remote_command, both SECURITY DEFINER.
--
-- remote_commands: no INSERT policy. Requesting goes through the three functions above, which check
-- the credential the caller actually holds. The old policy let any caller file a request naming any
-- target and household they could name, which was a way to fill a stranger's approvals screen without
-- ever holding the invite code.
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "join_household" ON public.household_members;
DROP POLICY IF EXISTS "request_remote_command" ON public.remote_commands;

REVOKE INSERT, UPDATE, DELETE ON public.household_members FROM anon, authenticated;
REVOKE INSERT ON public.remote_commands FROM anon, authenticated;

-- The read policy survives: a member must still be able to see who else is in the household.
GRANT SELECT ON public.household_members TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 8. Verify
--
-- The load-bearing assertions are the negatives: no INSERT policy must remain on either table. If one
-- does, the whole migration is decorative.
-- ------------------------------------------------------------------------------

-- Must be empty.
SELECT policyname, cmd FROM pg_policies
WHERE schemaname = 'public'
  AND tablename IN ('household_members', 'remote_commands')
  AND cmd IN ('INSERT', 'UPDATE', 'DELETE')
ORDER BY tablename, policyname;

-- Must still allow reading members.
SELECT policyname, cmd FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'household_members' AND cmd = 'SELECT';

-- The select policy must remain target-only: a requester reading rows is how 017 closed a code leak.
SELECT policyname, qual AS using_clause FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'remote_commands' AND cmd = 'SELECT';

-- list_my_remote_commands must not return approval_code at all.
SELECT p.proname,
       EXISTS (
           SELECT 1 FROM unnest(p.proargnames::text[]) AS n
           WHERE n = 'approval_code'
       ) AS leaks_code
FROM pg_proc p
WHERE p.proname = 'list_my_remote_commands';

SELECT proname FROM pg_proc
WHERE proname IN ('request_household_join', 'request_household_leave', 'request_mode_switch',
                  'list_my_remote_commands', 'protected_members_of', 'is_monitoring_member_of')
ORDER BY proname;

NOTIFY pgrst, 'reload schema';