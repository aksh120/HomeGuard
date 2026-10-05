-- HomeGuard migration 016: address the alarm to the resident's own device
--
-- Phase 5 needs to push a command to the phone being alarmed, which is not a household caregiver.
-- caregiver_push_tokens() (014) deliberately returns only MONITORING-role members, so it cannot
-- express this. Two gaps to close:
--
--   1. emergency_events had no reference back to the command that caused it. The webhook receives
--      only the emergency row, so without this the edge function would have to re-query
--      remote_commands to work out which alarm to deliver, and could pick the wrong one when two
--      existed for the same person. Carrying the id removes the guess.
--
--   2. the resident's own push token. The resident is a household member with role PROTECTED, so
--      can_raise_alarm_for excludes them from being a target of their own alarm, but they still need
--      to be *reachable* by it.

-- ------------------------------------------------------------------------------
-- 1. Link the emergency to the command that raised it
-- ------------------------------------------------------------------------------
ALTER TABLE public.emergency_events
    ADD COLUMN IF NOT EXISTS remote_command_id UUID REFERENCES public.remote_commands(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_emergency_events_remote_command
    ON public.emergency_events(remote_command_id);

-- Backfill-free: request_remote_alarm writes both columns in one statement, so no existing row
-- needs repairing.

-- ------------------------------------------------------------------------------
-- 2. The alarmed device's own tokens
--
-- Scoped to the owner of the named device, not to the caller. The edge function runs with the
-- service role and must be able to reach a device that belongs to somebody else, so this is
-- intentionally not caller-scoped; it takes only the device id it was given by the webhook payload,
-- which names the single device whose row triggered the insert.
--
-- Ordered newest first so a device that has registered more than once gets its freshest token first
-- rather than an expired one.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.device_push_tokens(target_device_id TEXT)
RETURNS TABLE (user_id UUID, token TEXT, updated_at TIMESTAMPTZ)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT pt.user_id, pt.token, pt.updated_at
    FROM public.push_tokens pt
    JOIN public.devices d ON d.user_id = pt.user_id
    WHERE d.device_id = target_device_id
      AND pt.updated_at > now() - interval '90 days'
    ORDER BY pt.updated_at DESC
$$;

REVOKE ALL ON FUNCTION public.device_push_tokens(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.device_push_tokens(TEXT) TO service_role;

-- ------------------------------------------------------------------------------
-- 3. request_remote_alarm now records the link
--
-- Recreated rather than patched: CREATE OR REPLACE cannot change a body that references new columns
-- safely against an already-created constraint, and the function is small enough that replacing it
-- whole is clearer than accumulating patches. The behaviour is unchanged apart from the added column.
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
-- 4. resolve_remote_command
--
-- Given only a command id, resolve the person and household it concerns. The caregiver dashboard
-- lists devices from the remote side, and MonitoredDevice carries the TEXT device_id while the alarm
-- API needs the auth UUID, so this is the bridge.
--
-- Returns nothing when the caller is not a household caregiver of that device, so it cannot be used
-- to enumerate other people's households.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.resolve_remote_command(command UUID)
RETURNS TABLE (
    command_id UUID,
    kind TEXT,
    target_user_id UUID,
    household_id UUID,
    person_name TEXT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT c.id, c.kind, c.target_user_id, c.household_id, d.person_name
    FROM public.remote_commands c
    JOIN public.devices d ON d.user_id = c.target_user_id
    WHERE c.id = command
      AND public.can_request_for(c.target_user_id)
    LIMIT 1
$$;

REVOKE ALL ON FUNCTION public.resolve_remote_command(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_remote_command(UUID) TO anon, authenticated;

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- 5. Verify
-- ------------------------------------------------------------------------------
SELECT column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'emergency_events'
  AND column_name IN ('source', 'triggered_by', 'remote_command_id')
ORDER BY column_name;

SELECT proname, prosecdef FROM pg_proc
WHERE proname IN ('device_push_tokens', 'resolve_remote_command', 'request_remote_alarm')
ORDER BY proname;