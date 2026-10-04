-- HomeGuard migration 005: households ownership, and PostgREST schema reload
--
-- Why this exists
--
-- Creating a household failed with:
--   42501 new row violates row-level security policy for table "households"
--
-- ...even when the client sent created_by = auth.uid() correctly. Verified with curl as well as
-- PowerShell, so it was not a client serialisation problem. Established by direct inspection:
--
--   * the column exists, is NOT NULL, and has no default
--   * anon and authenticated both hold INSERT on the table
--   * RLS is enabled and NOT forced
--   * the policy is exactly WITH CHECK (created_by = auth.uid())
--   * that expression evaluates TRUE for a correct created_by
--
-- So the row arriving at the policy had a NULL created_by: PostgREST was not inserting the column.
-- The one ownership column in this schema the client still had to supply itself was the one that
-- silently did not arrive.
--
-- The fix follows the pattern migration 20261004130928 already established for the other six
-- tables: fill ownership from the verified request JWT inside the database. A client-supplied
-- owner is also a value a client can forge, so this is the safer shape regardless.

-- ------------------------------------------------------------------------------
-- 1. Extend the trigger to cover created_by
--
-- Nested IF rather than a combined one: households is the only table with a created_by column, and
-- touching NEW.created_by on a record type that lacks the field would raise at runtime. Nesting
-- guarantees the field is never referenced for the other tables.
--
-- Only fills when absent. A client that supplies someone else's created_by is still rejected by the
-- WITH CHECK policy, exactly as before.
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

    IF TG_TABLE_NAME = 'households' THEN
        IF NEW.created_by IS NULL THEN
            NEW.created_by := auth.uid();
        END IF;
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

DROP TRIGGER IF EXISTS trg_assign_ownership_households ON public.households;
CREATE TRIGGER trg_assign_ownership_households
    BEFORE INSERT ON public.households
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

-- ------------------------------------------------------------------------------
-- 2. Reload the PostgREST schema cache
--
-- Migrations 001-003 were applied through the dashboard rather than the CLI, so PostgREST's cached
-- description of the schema may not match the database. Reloading is safe and idempotent, and
-- prevents a stale cache from hiding columns that do exist.
-- ------------------------------------------------------------------------------
NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- 3. Verify
--
-- Expect six triggers including the new households one, and the function to be SECURITY DEFINER.
-- ------------------------------------------------------------------------------
SELECT tgrelid::regclass AS table_name, tgname
FROM pg_trigger
WHERE tgname LIKE 'trg_assign_ownership_%'
ORDER BY tgname;