-- ==============================================================================
-- HomeGuard migration 001b: attribute existing rows to an owner
-- For a database that ALREADY CONTAINS DATA.
-- ==============================================================================
--
-- Run 001_rls_ownership.sql ONLY on an empty database. If your tables already hold
-- devices, emergency events, or check-ins, use this file instead: it adds the
-- ownership column, assigns every existing row to an owner, and only then applies
-- the NOT NULL constraints and the ownership-scoped policies.
--
-- Order matters. Do not run 001_rls_ownership.sql first on a database with data:
-- its SET NOT NULL statements will fail on unowned rows.
--
-- REQUIREMENT: enable Anonymous sign-ins first, under
--   Dashboard -> Authentication -> Providers -> Anonymous Sign-Ins
--
-- Plain SQL only. No psql meta-commands, no explicit transaction control.
-- ==============================================================================

-- 1. Add the owning user to each table, nullable for now.
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.device_status
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.emergency_events
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.check_ins
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

-- 2. Attribute every existing row.
--
--    All rows are assigned to a single owner: the oldest row in auth.users.
--    That is a guess, not a deliberate choice. It is correct when the database
--    holds exactly one install's data, which is the normal case for a personal
--    deployment. If it holds several, override afterwards with the four UPDATE
--    statements in the OWNING EXISTING DATA section below.
--
--    If this raises an exception, auth.users is empty: enable Anonymous sign-ins,
--    create a session, then re-run. It cannot safely guess an owner without one.
DO $$
DECLARE
    fallback_id UUID;
    existing_rows INTEGER;
BEGIN
    SELECT id INTO fallback_id FROM auth.users ORDER BY created_at ASC LIMIT 1;

    SELECT
        (SELECT COUNT(*) FROM public.devices) +
        (SELECT COUNT(*) FROM public.device_status) +
        (SELECT COUNT(*) FROM public.emergency_events) +
        (SELECT COUNT(*) FROM public.check_ins)
    INTO existing_rows;

    IF existing_rows > 0 AND fallback_id IS NULL THEN
        RAISE EXCEPTION
            'Cannot migrate: % existing row(s) have no owner and auth.users is empty. '
            'Enable Anonymous Sign-Ins and create a session, then re-run.',
            existing_rows;
    END IF;

    IF fallback_id IS NOT NULL THEN
        UPDATE public.devices          SET user_id = fallback_id WHERE user_id IS NULL;
        UPDATE public.device_status    SET user_id = fallback_id WHERE user_id IS NULL;
        UPDATE public.emergency_events SET user_id = fallback_id WHERE user_id IS NULL;
        UPDATE public.check_ins        SET user_id = fallback_id WHERE user_id IS NULL;
    END IF;
END $$;

-- 3. Confirm nothing was left unowned, then enforce NOT NULL.
DO $$
DECLARE
    orphans INTEGER;
BEGIN
    SELECT
        (SELECT COUNT(*) FROM public.devices          WHERE user_id IS NULL) +
        (SELECT COUNT(*) FROM public.device_status    WHERE user_id IS NULL) +
        (SELECT COUNT(*) FROM public.emergency_events WHERE user_id IS NULL) +
        (SELECT COUNT(*) FROM public.check_ins        WHERE user_id IS NULL)
    INTO orphans;

    IF orphans > 0 THEN
        RAISE EXCEPTION 'Refusing to continue: % row(s) still have no owner.', orphans;
    END IF;
END $$;

ALTER TABLE public.devices          ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.device_status    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.emergency_events ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.check_ins        ALTER COLUMN user_id SET NOT NULL;

-- 4. Index the ownership column.
CREATE INDEX IF NOT EXISTS idx_devices_user_id          ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_user_id ON public.emergency_events(user_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_user_id        ON public.check_ins(user_id);

-- 5. Replace the open policies.
DROP POLICY IF EXISTS "Allow public device read access"   ON public.devices;
DROP POLICY IF EXISTS "Allow public device insert/update" ON public.devices;
DROP POLICY IF EXISTS "Allow device_status read/write"    ON public.device_status;
DROP POLICY IF EXISTS "Allow emergency_events read/write" ON public.emergency_events;
DROP POLICY IF EXISTS "Allow check_ins read/write"        ON public.check_ins;

CREATE POLICY "own_devices" ON public.devices
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_device_status" ON public.device_status
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_emergency_events" ON public.emergency_events
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_check_ins" ON public.check_ins
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- 6. Verify. All four rows should read own_* and reference auth.uid().
SELECT tablename, policyname, cmd, qual
FROM pg_policies
WHERE schemaname = 'public'
ORDER BY tablename, policyname;

-- ==============================================================================
-- OWNING EXISTING DATA (optional, run after the migration if needed)
-- ==============================================================================
-- Move every row to a specific account:
--
--   UPDATE public.devices          SET user_id = '<uuid>' WHERE user_id <> '<uuid>';
--   UPDATE public.device_status    SET user_id = '<uuid>' WHERE user_id <> '<uuid>';
--   UPDATE public.emergency_events SET user_id = '<uuid>' WHERE user_id <> '<uuid>';
--   UPDATE public.check_ins        SET user_id = '<uuid>' WHERE user_id <> '<uuid>';