-- HomeGuard migration 019: fix request_household_join, and restore the second resident
--
-- Two defects in 018, both found by executing the functions rather than reading them.
--
-- 1. 42P01 "missing FROM-clause entry for table member"
--
--     The loop was written `FOR member IN SELECT p.user_id ... LOOP ... member.user_id`. PL/pgSQL
--     cannot do field access on a scalar variable, so it fell back to parsing `member.user_id` as a
--     column of a FROM alias called member. The function raised on its first call, so joining was
--     broken for everybody, not just for one case.
--
--     Rewritten as INSERT ... SELECT. That removes the loop variable entirely rather than fixing its
--     type, which is the version of the bug that is easy to reintroduce.
--
-- 2. A second resident could no longer join their own household at all
--
--     018 dropped the join_household INSERT policy, and create_household_with_membership enrols only
--     the creator. So a household could never contain a second protected device again, even though
--     decision D2 is explicitly about one house holding several people.
--
--     The fix is not to reopen the door, because membership is household-wide: both roles can already
--     read every device and emergency in the household, so "join as a resident without approval" would
--     be a caregiver consent gate with the sign swapped. Instead the joiner is admitted through the
--     same code-gated request as anybody else, and the role is derived from server-side data.
--
--     The role comes from devices.device_mode, which the joiner does not control in any way that
--     matters: the app registers the device from the profile the user chose, and choosing PROTECTED
--     only ever grants access the person already has a code for. Accepting a role from the request
--     body instead would let any caller request MONITORING without a resident's approval, which is
--     exactly the bypass 018 closed.

-- ------------------------------------------------------------------------------
-- 1. Joining
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
        RAISE EXCEPTION 'that home has nobody who can approve a request yet'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.remote_commands
        (target_user_id, household_id, kind, payload, requested_by, approval_code)
    SELECT p.user_id,
           target_household,
           'APPROVE_JOIN',
           jsonb_build_object('device_id', NULLIF(btrim(COALESCE(joiner_device_id, '')), '')),
           auth.uid(),
           public.generate_approval_code()
    FROM public.protected_members_of(target_household) p
    WHERE NOT EXISTS (
        -- Skips a resident who already has a pending request from this joiner, so a second tap is
        -- idempotent instead of filling the resident's screen with duplicates.
        SELECT 1 FROM public.remote_commands c
        WHERE c.kind = 'APPROVE_JOIN'
          AND c.status = 'PENDING'
          AND c.target_user_id = p.user_id
          AND c.requested_by = auth.uid()
    );

    GET DIAGNOSTICS created = ROW_COUNT;

    IF created = 0 THEN
        RAISE EXCEPTION 'you have already asked to watch this home; waiting for a code'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT target_household, label, created;
END;
$$;

-- ------------------------------------------------------------------------------
-- 2. Leaving
--
-- Was restricted to MONITORING members, which would have trapped a second resident: they could join
-- their own household but never ask to leave it. Anyone may ask to leave; the code still comes from
-- someone at home, so this stays a consent flow rather than a one-tap exit, and it never removes
-- anybody else.
--
-- A member who is the household's only protected person has nobody to ask, and is told so plainly
-- rather than being left waiting for a code that will never be issued.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_household_leave(target_household UUID)
RETURNS TABLE (approvals_required INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    created INTEGER := 0;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.household_members hm
        WHERE hm.household_id = target_household AND hm.user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'you are not connected to this home'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.protected_members_of(target_household) p
        WHERE p.user_id IS DISTINCT FROM auth.uid()
    ) THEN
        RAISE EXCEPTION 'you are the only person at home on this device, so there is nobody to ask'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.remote_commands
        (target_user_id, household_id, kind, payload, requested_by, approval_code)
    SELECT p.user_id,
           target_household,
           'APPROVE_REMOVE',
           '{}'::jsonb,
           auth.uid(),
           public.generate_approval_code()
    FROM public.protected_members_of(target_household) p
    WHERE p.user_id IS DISTINCT FROM auth.uid()
      AND NOT EXISTS (
          SELECT 1 FROM public.remote_commands c
          WHERE c.kind = 'APPROVE_REMOVE'
            AND c.status = 'PENDING'
            AND c.target_user_id = p.user_id
            AND c.requested_by = auth.uid()
            AND c.household_id = target_household
      );

    GET DIAGNOSTICS created = ROW_COUNT;

    IF created = 0 THEN
        RAISE EXCEPTION 'you have already asked to stop watching this home'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT created;
END;
$$;

-- ------------------------------------------------------------------------------
-- 3. Approval: derive the role from the device, never from the request
--
-- 018 hardcoded MONITORING, which would have made every second resident a caregiver.
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
    joiner_role TEXT;
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
    -- whoever legitimately obtains it may complete the request. That is why
    -- list_my_remote_commands deliberately does not return approval_code, and why migration 017
    -- removed requested_by from the SELECT policy. A shared code cannot be bound to one person; what
    -- protects the resident is that only they ever see it.
    resolved_household := cmd.household_id;

    IF resolved_household IS NULL THEN
        RAISE EXCEPTION 'that request is no longer valid'
            USING ERRCODE = '22023';
    END IF;

    IF cmd.kind = 'APPROVE_JOIN' THEN
        joiner_device := NULLIF(btrim(COALESCE(cmd.payload ->> 'device_id', '')), '');

        -- Read from devices, never from the request. A caller-supplied role would let anybody ask to
        -- join as PROTECTED without a resident's approval, and membership is household-wide either
        -- way, so that is the caregiver gate with the sign swapped.
        SELECT COALESCE(
            (SELECT CASE WHEN d.device_mode = 'MONITORING_DEVICE' THEN 'MONITORING' ELSE 'PROTECTED' END
             FROM public.devices d
             WHERE d.user_id = auth.uid()
               AND (joiner_device IS NULL OR d.device_id = joiner_device)
             ORDER BY d.created_at DESC
             LIMIT 1),
            -- No device row means the install has not registered itself, which on the caregiver flow
            -- happens only when setup was skipped. MONITORING is the restrictive default: it is the
            -- role that cannot be mistaken for consent to contribute a new protected device.
            'MONITORING'
        ) INTO joiner_role;

        INSERT INTO public.household_members (household_id, user_id, device_id, role)
        VALUES (resolved_household, auth.uid(), joiner_device, joiner_role)
        ON CONFLICT (household_id, user_id) DO UPDATE
            SET device_id = COALESCE(EXCLUDED.device_id, public.household_members.device_id),
                role = EXCLUDED.role;
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
-- 4. Verify
-- ------------------------------------------------------------------------------

-- Neither function may reference a loop variable as if it were a relation. This is the 42P01 shape:
-- any loop-based version of these functions reintroduces the bug 018 shipped.
SELECT proname,
       position('FOR ' in prosrc) = 0 AS no_loops,
       position('INTO created' in prosrc) > 0 AS uses_get_diagnostics
FROM pg_proc
WHERE proname IN ('request_household_join', 'request_household_leave', 'approve_remote_command')
ORDER BY proname;

-- The role must be derived from devices, not from the request body.
SELECT position('EXCLUDED.role' in prosrc) > 0 AS sets_role,
       position('public.devices' in prosrc) > 0 AS reads_device_mode
FROM pg_proc WHERE proname = 'approve_remote_command';

SELECT proname FROM pg_proc
WHERE proname IN ('request_household_join', 'request_household_leave', 'request_mode_switch',
                  'list_my_remote_commands')
ORDER BY proname;

NOTIFY pgrst, 'reload schema';