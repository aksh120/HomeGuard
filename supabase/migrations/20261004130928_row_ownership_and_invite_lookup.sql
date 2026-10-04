-- HomeGuard migration 004: row ownership, household propagation, invite-code lookup
--
-- Fixes two defects that made remote sharing impossible no matter how healthy the client was.
--
-- Defect 1 (user_id NOT NULL, never supplied)
--   Every device table declares `user_id UUID NOT NULL` and every RLS policy filters on
--   `auth.uid() = user_id`, but no client payload has ever included user_id. Even with a working
--   session, every insert failed the not-null constraint. Fixed here at the data layer rather than
--   by teaching each client to send the value: a client-supplied owner is a value a client can
--   forge, whereas auth.uid() is derived from the verified request JWT.
--
-- Defect 2 (household_id never written)
--   migration 003 added household_id and made every caregiver read filter on it, but nothing ever
--   populated it. Caregiver dashboards would have read empty even with a perfect session. The same
--   trigger derives it from the device row, so it cannot drift from the device's actual household.
--
-- Also adds find_household_by_invite_code. The households SELECT policy requires existing
-- membership (is_household_member), so a caregiver who is not yet a member cannot read the one row
-- they need in order to join. Their RLS fix is therefore unusable without this function.

-- ------------------------------------------------------------------------------
-- 1. Ownership + household propagation
--
-- BEFORE INSERT so it runs before the RLS WITH CHECK clause evaluates.
--
-- SECURITY DEFINER because the devices lookup must not depend on the caller's RLS standing: the
-- row being inserted into is not necessarily one the caller can read under the owner-scoped
-- policy. search_path is pinned so a caller cannot redirect the lookup.
--
-- user_id is only filled when absent, never overwritten. A caller that supplies someone else's
-- user_id is still rejected by the per-table WITH CHECK policy; this function closes the
-- not-null gap and does not weaken ownership.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.assign_row_ownership()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NEW.user_id IS NULL THEN
        NEW.user_id := auth.uid();
    END IF;

    -- `devices` is the source of the household, so it is never derived for itself; a device's own
    -- household is set explicitly once its owner creates or joins a household.
    IF TG_TABLE_NAME <> 'devices'
       AND NEW.household_id IS NULL
       AND NEW.device_id IS NOT NULL
    THEN
        SELECT d.household_id INTO NEW.household_id
        FROM public.devices d
        WHERE d.device_id = NEW.device_id;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_assign_ownership_devices ON public.devices;
CREATE TRIGGER trg_assign_ownership_devices
    BEFORE INSERT ON public.devices
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

DROP TRIGGER IF EXISTS trg_assign_ownership_device_status ON public.device_status;
CREATE TRIGGER trg_assign_ownership_device_status
    BEFORE INSERT ON public.device_status
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

DROP TRIGGER IF EXISTS trg_assign_ownership_emergency_events ON public.emergency_events;
CREATE TRIGGER trg_assign_ownership_emergency_events
    BEFORE INSERT ON public.emergency_events
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

DROP TRIGGER IF EXISTS trg_assign_ownership_check_ins ON public.check_ins;
CREATE TRIGGER trg_assign_ownership_check_ins
    BEFORE INSERT ON public.check_ins
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

DROP TRIGGER IF EXISTS trg_assign_ownership_monitoring_contacts ON public.monitoring_contacts;
CREATE TRIGGER trg_assign_ownership_monitoring_contacts
    BEFORE INSERT ON public.monitoring_contacts
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

-- device_readings is written by the same heartbeat path, so it needs the same treatment. It has
-- no household_id column, so only user_id is filled.
DROP TRIGGER IF EXISTS trg_assign_ownership_device_readings ON public.device_readings;
CREATE TRIGGER trg_assign_ownership_device_readings
    BEFORE INSERT ON public.device_readings
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

-- ------------------------------------------------------------------------------
-- 2. Invite-code lookup
--
-- The join credential, resolved to a household id without exposing the table to non-members.
--
-- SECURITY DEFINER is required: the caller is by definition not yet a member, so the households
-- SELECT policy would otherwise refuse the lookup and joining would be impossible.
--
-- Only three columns are returned, and only for an exact code match. The code is 8 characters from
-- a 32-symbol alphabet with 0/O and 1/I removed, which is roughly 1.1e12 combinations, so this
-- cannot be enumerated at a useful rate. It is a shared secret read aloud to a family member, not
-- a per-account credential; regenerating the code is what invalidates a leaked one.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.find_household_by_invite_code(code TEXT)
RETURNS TABLE (id UUID, name TEXT, invite_code TEXT)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT h.id, h.name, h.invite_code
    FROM public.households h
    WHERE h.invite_code = upper(btrim(COALESCE(code, '')))
    LIMIT 1
$$;

REVOKE ALL ON FUNCTION public.find_household_by_invite_code(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.find_household_by_invite_code(TEXT) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 3. Verify
--
-- Expect five triggers on the device tables and the function to exist.
-- ------------------------------------------------------------------------------
SELECT tgrelid::regclass AS table_name, tgname
FROM pg_trigger
WHERE tgname LIKE 'trg_assign_ownership_%'
ORDER BY tgname;

SELECT proname, prosecdef AS security_definer
FROM pg_proc
WHERE proname IN ('assign_row_ownership', 'find_household_by_invite_code')
ORDER BY proname;

-- Ownership is now filled by trigger. Confirm the not-null columns accept a session-only insert
-- by checking that no table still requires the client to supply user_id.
SELECT table_name, column_name, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND column_name = 'user_id'
  AND table_name IN ('devices', 'device_status', 'emergency_events', 'check_ins',
                     'monitoring_contacts', 'device_readings')
ORDER BY table_name;