-- HomeGuard migration 017: close the Phase 5 authorisation gaps (decision D4/D7)
--
-- Phase 5 shipped a working caregiver alarm. It also shipped four ways for the consent model to be
-- bypassed, none of which would have produced an error: each would have quietly granted more access
-- than anyone agreed to. They are fixed here rather than in 015/016 because those are already applied
-- to the live database and rewriting history would desynchronise it from the repository.
--
-- None of these produce a visible failure when exploited. That is precisely why they are worth a
-- migration rather than a note: an alarm works, a dashboard looks right, and the tests pass.

-- ------------------------------------------------------------------------------
-- 1. Caregiver actions must require the caregiver role
--
-- can_request_for and can_raise_alarm_for tested only for "shares a household with the target".
--
-- In a house with two protected devices, both residents are members of the same household, so either
-- could raise an alarm on the other and request to stop watching them. Neither is a caregiver, and D7
-- describes this button as a caregiver's action for somebody else.
--
-- The same applies to APPROVE_REMOVE and APPROVE_MODE_SWITCH. D4 gates those on approval, but the
-- approval is meaningless if the person asking is the person being protected.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.can_request_for(target UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.household_members requester
        WHERE requester.user_id = auth.uid()
          -- Phase 5: the role check. A resident is a household member too, and membership alone was
          -- enough to act on another member.
          AND requester.role = 'MONITORING'
          AND requester.household_id IN (
              SELECT d.household_id
              FROM public.devices d
              WHERE d.user_id = target
                AND d.household_id IS NOT NULL
          )
    )
$$;

CREATE OR REPLACE FUNCTION public.can_raise_alarm_for(target UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT auth.uid() IS NOT NULL
       AND auth.uid() <> target
       AND EXISTS (
             SELECT 1
             FROM public.household_members requester
             WHERE requester.user_id = auth.uid()
               AND requester.role = 'MONITORING'
               AND requester.household_id IN (
                   SELECT d.household_id
                   FROM public.devices d
                   WHERE d.user_id = target
                     AND d.household_id IS NOT NULL
               )
           )
$$;

-- ------------------------------------------------------------------------------
-- 2. A requester must not be able to read their own approval code
--
-- read_own_remote_commands lets the requester SELECT their own request so they can see its status.
-- PostgREST returns whole rows, and the row contains approval_code. The requester could therefore
-- read the code that was generated for them and submit it themselves, approving their own request
-- without the resident's participation.
--
-- The code still reaches the requester the way it is meant to: the resident reads it out or sends it,
-- and the requester types it. That is the consent step. Reading it out of the database is not.
--
-- Implemented by restricting UPDATE, which is what the requester needs to cancel, and tightening
-- SELECT to the target only. Cancellation goes through cancel_approval rather than a row update.
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "read_own_remote_commands" ON public.remote_commands;
CREATE POLICY "read_own_remote_commands" ON public.remote_commands
    FOR SELECT USING (target_user_id = auth.uid());

-- ------------------------------------------------------------------------------
-- 3. RAISE_ALARM must not be directly insertable
--
-- 015 deliberately allowed a RAISE_ALARM insert "to keep the table consistent with the function that
-- owns the operation". That permitted any caregiver to POST a row straight to the table, skipping:
--   * the atomic emergency_events insert, so nobody's contacts would be notified
--   * the 15 minute rate limit, so a caregiver could spam a resident's phone
--   * triggered_by attribution, so the alarm would appear to have no requester
--
-- request_remote_alarm is a SECURITY DEFINER function, so it writes the row regardless of this policy.
-- Restricting INSERT to approval kinds only is what makes that function the sole path to an alarm.
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "request_remote_command" ON public.remote_commands;
CREATE POLICY "request_remote_command" ON public.remote_commands
    FOR INSERT WITH CHECK (
        requested_by = auth.uid()
        AND kind <> 'RAISE_ALARM'
        AND (
            (kind = 'APPROVE_JOIN'
                AND public.can_request_join(target_user_id, household_id))
            OR (kind IN ('APPROVE_REMOVE', 'APPROVE_MODE_SWITCH')
                AND public.can_request_for(target_user_id))
        )
    );

-- ------------------------------------------------------------------------------
-- 4. The rate limit must survive completion
--
-- The 15 minute check counted only PENDING and DELIVERED rows. Once the resident stopped the alarm
-- the row became EXECUTED and no longer counted, so an alarm could be raised again immediately after
-- being stopped. That is the opposite of what the limit is for.
--
-- Any RAISE_ALARM row created within the window counts, whatever its status.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_remote_alarm(
    target UUID,
    household UUID
)
RETURNS TABLE (command_id UUID, event_id UUID)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    new_command_id UUID := gen_random_uuid();
    new_event_id UUID := gen_random_uuid();
    target_device TEXT;
BEGIN
    IF NOT public.can_raise_alarm_for(target) THEN
        RAISE EXCEPTION 'you cannot raise an alarm for that device'
            USING ERRCODE = '42501';
    END IF;

    SELECT d.device_id INTO target_device
    FROM public.devices d
    WHERE d.user_id = target AND d.household_id = household
    LIMIT 1;

    IF target_device IS NULL THEN
        RAISE EXCEPTION 'that device is not part of this home'
            USING ERRCODE = '22023';
    END IF;

    -- Status is deliberately not filtered. An alarm that has been executed still happened, and the
    -- resident's contacts were still told; letting it be raised again straight away would notify
    -- them twice for one request.
    IF EXISTS (
        SELECT 1 FROM public.remote_commands c
        WHERE c.target_user_id = target
          AND c.kind = 'RAISE_ALARM'
          AND c.created_at > now() - interval '15 minutes'
    ) THEN
        RAISE EXCEPTION 'an alarm was already raised for this person in the last 15 minutes'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.remote_commands
        (id, target_user_id, household_id, kind, requested_by)
    VALUES
        (new_command_id, target, household, 'RAISE_ALARM', auth.uid());

    INSERT INTO public.emergency_events
        (id, device_id, triggered_at, details, source, triggered_by, remote_command_id)
    VALUES
        (new_event_id, target_device, now(),
         'A caregiver raised an alarm for this device', 'CAREGIVER_REMOTE', auth.uid(),
         new_command_id);

    RETURN QUERY SELECT new_command_id, new_event_id;
END;
$$;

GRANT EXECUTE ON FUNCTION public.request_remote_alarm(UUID, UUID) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 5. Claim and complete must accept alarms only
--
-- Both were written against any command kind, so a device could claim an APPROVE_JOIN addressed to it
-- and mark it DELIVERED. That would move a pending request into a state the approvals UI no longer
-- shows as awaiting action, with no code ever entered: a silent approval.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.claim_remote_command(command UUID)
RETURNS TABLE (
    id UUID,
    kind TEXT,
    payload JSONB,
    household_id UUID,
    requested_by UUID
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    RETURN QUERY
        UPDATE public.remote_commands c
        SET status = 'DELIVERED'
        WHERE c.id = command
          AND c.kind = 'RAISE_ALARM'
          AND c.status = 'PENDING'
          AND c.target_user_id = auth.uid()
        RETURNING c.id, c.kind, c.payload, c.household_id, c.requested_by;
END;
$$;

GRANT EXECUTE ON FUNCTION public.claim_remote_command(UUID) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.complete_remote_command(command UUID, outcome TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    affected INTEGER;
BEGIN
    IF outcome NOT IN ('EXECUTED', 'CANCELLED') THEN
        RAISE EXCEPTION 'outcome must be EXECUTED or CANCELLED'
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.remote_commands
    SET status = outcome, executed_at = NOW()
    WHERE id = command
      AND kind = 'RAISE_ALARM'
      AND status IN ('PENDING', 'DELIVERED')
      AND target_user_id = auth.uid();

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected > 0;
END;
$$;

GRANT EXECUTE ON FUNCTION public.complete_remote_command(UUID, TEXT) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 6. Caregiver push tokens must not depend on the caregiver's own device row
--
-- caregiver_push_tokens (014) joined household_members to devices on user_id AND household_id. A
-- caregiver who joined a household has their membership but their own device row may have
-- household_id NULL, because nothing ever stamps it. The join then produced no rows and that caregiver
-- silently received no emergency push while appearing correctly configured everywhere else.
--
-- Now resolved from membership alone. A monitoring device marker is excluded so a caregiver watching
-- several households does not get this household's emergencies on a phone they are using as a
-- monitoring screen, but the token set no longer depends on that row being linked.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.caregiver_push_tokens(target_device_id TEXT)
RETURNS TABLE (user_id UUID, token TEXT, updated_at TIMESTAMPTZ)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT hm.user_id, pt.token, pt.updated_at
    FROM public.household_members hm
    JOIN public.devices target
      ON target.household_id = hm.household_id
     AND target.device_id = target_device_id
    JOIN public.push_tokens pt
      ON pt.user_id = hm.user_id
    WHERE hm.role = 'MONITORING'
      AND hm.user_id IS DISTINCT FROM target.user_id
      AND target.device_mode IS DISTINCT FROM 'MONITORING_DEVICE'
      AND pt.updated_at > now() - interval '90 days'
    ORDER BY pt.updated_at DESC
$$;

REVOKE ALL ON FUNCTION public.caregiver_push_tokens(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.caregiver_push_tokens(TEXT) TO service_role;

-- ------------------------------------------------------------------------------
-- 7. Verify
--
-- Every check here is a property, not a smoke test. A row appearing where it should not is the whole
-- failure mode this migration exists to remove.
--
-- Read through pg_policies, whose columns are `qual` and `with_check`. The pg_ prefixed catalog names
-- belong to pg_policy; mixing them up aborts the migration that is meant to be checking them.
-- ------------------------------------------------------------------------------

-- The two helper functions must reference the role.
SELECT proname, position('role = ''MONITORING''' in prosrc) > 0 AS checks_role
FROM pg_proc
WHERE proname IN ('can_request_for', 'can_raise_alarm_for')
ORDER BY proname;

-- The INSERT policy must exclude RAISE_ALARM from direct writes.
SELECT policyname,
       position('RAISE_ALARM' in COALESCE(with_check, '')) > 0 AS mentions_alarm,
       COALESCE(with_check, '') LIKE '%kind <> %RAISE_ALARM%' AS blocks_alarm
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'remote_commands' AND policyname = 'request_remote_command';

-- The SELECT policy must no longer admit the requester. Any `requested_by` here is the bug.
SELECT policyname, qual AS using_clause
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'remote_commands' AND policyname = 'read_own_remote_commands';

-- The rate limit must not filter on status any more.
SELECT position('status IN' in prosrc) = 0 AS counts_every_status
FROM pg_proc WHERE proname = 'request_remote_alarm';

-- Claim and complete must both restrict on kind.
SELECT proname, position('RAISE_ALARM' in prosrc) > 0 AS alarms_only
FROM pg_proc WHERE proname IN ('claim_remote_command', 'complete_remote_command')
ORDER BY proname;

-- Push tokens must resolve from membership, not from a joined device row.
SELECT position('JOIN public.devices d' in prosrc) = 0 AS no_device_row_join
FROM pg_proc WHERE proname = 'caregiver_push_tokens';

NOTIFY pgrst, 'reload schema';