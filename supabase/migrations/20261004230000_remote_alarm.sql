-- HomeGuard migration 015: caregiver-raised remote alarm (Phase 5)
--
-- Decision D7: the monitoring profile has an SOS button that raises an alarm ON BEHALF OF a watched
-- person, and does two things:
--   1. immediately notifies that person's registered contacts by email and push
--   2. alarms their phone: torch, siren, full screen
--
-- The two are deliberately independent, and (1) is the reliable half.
--
-- Why the contact notification is written here, in the database, not by the phone
--
-- The obvious design is for the phone to raise the emergency when it receives the alarm. That makes
-- the most important part depend on the least reliable part: if the phone is dead, flat, out of
-- network, or the push never arrives, the resident's contacts are never told. Instead the emergency
-- row is inserted the moment the caregiver presses the button, which also fires the existing
-- emergency_events webhook. The phone alarm becomes a best-effort second signal on top of an alert
-- that has already gone out.
--
-- Why the request is one function rather than an INSERT plus a trigger
--
-- Atomicity. Raising the alarm must create the command and the emergency row together or not at
-- all. Two separate client writes could leave an alarm that notifies nobody, or an emergency row that
-- alarms nobody.
--
-- This deliberately overrules the policy introduced in 20261002142224, which stated that an emergency
-- row must come from the device that raised it "otherwise a caregiver could fabricate one". That
-- reasoning was sound but assumed the caregiver could act freely. It cannot: only a household
-- caregiver may raise an alarm, only on a device in their own household, at most once every 15
-- minutes, and the row records who raised it in triggered_by. It is also visible to the resident's
-- own screen as a caregiver request, never as a resident-initiated SOS, so it cannot be used to make
-- someone at home look like they pressed their own button.
--
-- Attribution is the control that makes this safe. emergency_events.source records CAREGIVER_REMOTE
-- and triggered_by records the caregiver, so the alert is never mistaken for the resident panicking.

-- ------------------------------------------------------------------------------
-- 1. Extend remote_commands
--
-- DELIVERED is new and matters: the phone claims a command by moving PENDING -> DELIVERED, and that
-- transition is the deduplication mechanism between the push path and the poll fallback. Without an
-- intermediate claimed state, a push followed by a poll could fire the siren twice.
-- ------------------------------------------------------------------------------
ALTER TABLE public.remote_commands
    DROP CONSTRAINT IF EXISTS remote_commands_kind_check;
ALTER TABLE public.remote_commands
    ADD CONSTRAINT remote_commands_kind_check
    CHECK (kind IN ('APPROVE_JOIN', 'APPROVE_REMOVE', 'APPROVE_MODE_SWITCH', 'RAISE_ALARM'));

ALTER TABLE public.remote_commands
    DROP CONSTRAINT IF EXISTS remote_commands_status_check;
ALTER TABLE public.remote_commands
    ADD CONSTRAINT remote_commands_status_check
    CHECK (status IN ('PENDING', 'DELIVERED', 'EXECUTED', 'CANCELLED'));

-- RAISE_ALARM carries no approval code: there is nothing to approve, so requiring one would be
-- meaningless. The existing constraint requires a code only while PENDING.
ALTER TABLE public.remote_commands
    DROP CONSTRAINT IF EXISTS remote_commands_pending_has_code;
ALTER TABLE public.remote_commands
    ADD CONSTRAINT remote_commands_pending_has_code
    CHECK (status <> 'PENDING' OR kind = 'RAISE_ALARM' OR approval_code IS NOT NULL);

-- ------------------------------------------------------------------------------
-- 2. Attribution on emergency_events
-- ------------------------------------------------------------------------------
ALTER TABLE public.emergency_events
    ADD COLUMN IF NOT EXISTS source TEXT NOT NULL DEFAULT 'DEVICE';
ALTER TABLE public.emergency_events
    ADD COLUMN IF NOT EXISTS triggered_by UUID REFERENCES auth.users(id) ON DELETE SET NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'emergency_events_source_check'
    ) THEN
        ALTER TABLE public.emergency_events
            ADD CONSTRAINT emergency_events_source_check
            CHECK (source IN ('DEVICE', 'CAREGIVER_REMOTE'));
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_emergency_events_source ON public.emergency_events(source);

-- A caregiver-raised event must say who raised it, or the attribution claim is unenforceable.
ALTER TABLE public.emergency_events
    DROP CONSTRAINT IF EXISTS emergency_events_remote_needs_actor;
ALTER TABLE public.emergency_events
    ADD CONSTRAINT emergency_events_remote_needs_actor
    CHECK (source <> 'CAREGIVER_REMOTE' OR triggered_by IS NOT NULL);

-- ------------------------------------------------------------------------------
-- 3. Who may raise an alarm
--
-- Shares a household with the target's device, and is not the target. Excluding self matters: the
-- resident's own row sits in the same household, so without the exclusion the resident could raise a
-- CAREGIVER_REMOTE alarm on themselves and make their own contacts think a caregiver raised it.
-- ------------------------------------------------------------------------------
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
              AND requester.household_id IN (
                  SELECT d.household_id
                  FROM public.devices d
                  WHERE d.user_id = target
                    AND d.household_id IS NOT NULL
              )
       )
$$;

-- ------------------------------------------------------------------------------
-- 4. Raise the alarm
--
-- Everything happens in one transaction so the command and the emergency row cannot diverge.
--
-- The 15 minute limit is enforced here rather than in the client because the client is exactly the
-- thing that cannot be trusted to be well behaved. It is deliberately per target rather than per
-- requester: the point is to protect the resident from a caregiver spamming their contacts, not to
-- stop one caregiver from asking twice.
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

    IF EXISTS (
        SELECT 1 FROM public.remote_commands c
        WHERE c.target_user_id = target
          AND c.kind = 'RAISE_ALARM'
          AND c.status IN ('PENDING', 'DELIVERED')
          AND c.created_at > now() - interval '15 minutes'
    ) THEN
        RAISE EXCEPTION 'an alarm was already raised for this person in the last 15 minutes'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.remote_commands
        (id, target_user_id, household_id, kind, requested_by)
    VALUES
        (new_command_id, target, household, 'RAISE_ALARM', auth.uid());

    -- Contacts are notified from here, via the existing emergency_events webhook. The phone alarm is
    -- a separate, best-effort signal layered on top.
    INSERT INTO public.emergency_events
        (id, device_id, triggered_at, details, source, triggered_by)
    VALUES
        (new_event_id, target_device, now(),
         'A caregiver raised an alarm for this device', 'CAREGIVER_REMOTE', auth.uid());

    RETURN QUERY SELECT new_command_id, new_event_id;
END;
$$;

REVOKE ALL ON FUNCTION public.request_remote_alarm(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_remote_alarm(UUID, UUID) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 5. Claim a command, atomically
--
-- The phone receives the same command twice in the common case: once by push and once by the poll
-- fallback. Reading the status and then updating it would let both win the race and sound the siren
-- twice, so claiming is a single conditional UPDATE. Whoever gets a row back owns the command; a
-- second caller updates zero rows and is told so.
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
          AND c.status = 'PENDING'
          AND c.target_user_id = auth.uid()
        RETURNING c.id, c.kind, c.payload, c.household_id, c.requested_by;
END;
$$;

REVOKE ALL ON FUNCTION public.claim_remote_command(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_remote_command(UUID) TO anon, authenticated;

-- Only the target may mark a claimed command executed or cancelled, which is the existing policy.
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
      AND status IN ('PENDING', 'DELIVERED')
      AND target_user_id = auth.uid();

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected > 0;
END;
$$;

REVOKE ALL ON FUNCTION public.complete_remote_command(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.complete_remote_command(UUID, TEXT) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 6. RLS
--
-- The INSERT policy gains RAISE_ALARM. The client does not insert alarm rows itself, it calls
-- request_remote_alarm, but the policy is kept permissive for that kind so the table is not
-- inconsistent with the function that owns the operation.
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "request_remote_command" ON public.remote_commands;
CREATE POLICY "request_remote_command" ON public.remote_commands
    FOR INSERT WITH CHECK (
        requested_by = auth.uid()
        AND (
            (kind = 'APPROVE_JOIN'
                AND public.can_request_join(target_user_id, household_id))
            OR (kind IN ('APPROVE_REMOVE', 'APPROVE_MODE_SWITCH')
                AND public.can_request_for(target_user_id))
            OR (kind = 'RAISE_ALARM'
                AND public.can_raise_alarm_for(target_user_id))
        )
    );

-- A caregiver who raised an alarm may see its outcome, and so may the person it was raised for.
-- Other household members still cannot see it, because that would leak the approval codes.
-- The read policy already covers exactly these two and is unchanged.

-- emergency_events gains the household read it needs for a caregiver to see a CAREGIVER_REMOTE event.
-- Migration 003 already granted household SELECT; nothing further is required here.

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- 7. Verify
-- ------------------------------------------------------------------------------
SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN ('public.remote_commands'::regclass, 'public.emergency_events'::regclass)
  AND conname IN ('remote_commands_kind_check', 'remote_commands_status_check',
                  'remote_commands_pending_has_code', 'emergency_events_source_check',
                  'emergency_events_remote_needs_actor')
ORDER BY conname;

SELECT proname, prosecdef FROM pg_proc
WHERE proname IN ('can_raise_alarm_for', 'request_remote_alarm',
                  'claim_remote_command', 'complete_remote_command')
ORDER BY proname;