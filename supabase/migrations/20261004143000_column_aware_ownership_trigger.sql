-- HomeGuard migration 006: make the ownership trigger column-aware
--
-- Corrects 20261004140000_household_ownership.sql, which made any INSERT into households fail with
--   42703 record "new" has no field "user_id"
--
-- The trigger body referenced NEW.user_id and NEW.household_id unconditionally, but those columns
-- do not exist on every table the trigger is attached to:
--
--   households            has created_by, no user_id, no household_id, no device_id
--   device_readings       has user_id,      no household_id
--   monitoring_contacts   has user_id,      no household_id
--
-- migration 003 added household_id to devices, device_status, emergency_events and check_ins only,
-- so the two tables above were never given it. The earlier test pass exercised devices, households
-- and check_ins, which is why this surfaced now rather than in 004.
--
-- Note this would have broken syncMonitoringContact, which the app calls on every settings save.
--
-- The fix tests for the column with to_jsonb(NEW) instead of assuming it. The IFs are nested rather
-- than combined with AND: referencing a missing field on a record raises at runtime, and nesting
-- guarantees the reference is only reached when the column is actually present, rather than relying
-- on AND short-circuiting.
CREATE OR REPLACE FUNCTION public.assign_row_ownership()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    row_json jsonb := to_jsonb(NEW);
BEGIN
    IF row_json ? 'user_id' THEN
        IF NEW.user_id IS NULL THEN
            NEW.user_id := auth.uid();
        END IF;
    END IF;

    IF row_json ? 'created_by' THEN
        IF NEW.created_by IS NULL THEN
            NEW.created_by := auth.uid();
        END IF;
    END IF;

    IF row_json ? 'household_id' THEN
        IF row_json ? 'device_id' AND NEW.household_id IS NULL AND NEW.device_id IS NOT NULL THEN
            SELECT d.household_id INTO NEW.household_id
            FROM public.devices d
            WHERE d.device_id = NEW.device_id;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

-- Re-assert every trigger, so the set is correct regardless of the order the files were applied.
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

DROP TRIGGER IF EXISTS trg_assign_ownership_device_readings ON public.device_readings;
CREATE TRIGGER trg_assign_ownership_device_readings
    BEFORE INSERT ON public.device_readings
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

DROP TRIGGER IF EXISTS trg_assign_ownership_households ON public.households;
CREATE TRIGGER trg_assign_ownership_households
    BEFORE INSERT ON public.households
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- Verify: seven triggers, each on a table that actually has the columns the trigger touches.
-- ------------------------------------------------------------------------------
SELECT tgrelid::regclass AS table_name, tgname
FROM pg_trigger
WHERE tgname LIKE 'trg_assign_ownership_%'
ORDER BY tgname;

SELECT c.relname AS table_name,
       bool_or(p.proname IS NOT NULL) AS trigger_present
FROM pg_class c
LEFT JOIN pg_trigger t ON t.tgrelid = c.oid AND t.tgname LIKE 'trg_assign_ownership_%'
LEFT JOIN pg_proc p ON p.oid = t.tgfoid
WHERE c.relname IN ('devices', 'device_status', 'emergency_events', 'check_ins',
                    'monitoring_contacts', 'device_readings', 'households')
GROUP BY c.relname
ORDER BY c.relname;