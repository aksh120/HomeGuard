-- ==============================================================================
-- HomeGuard migration 001: scope Row Level Security to auth.uid()
-- For a database with NO EXISTING DATA (all four tables empty).
-- ==============================================================================
--
-- Run this in the Supabase Dashboard SQL Editor, or:
--   psql "$SUPABASE_DB_URL" -f 001_rls_ownership.sql
--
-- Plain SQL only. No psql meta-commands, no explicit transaction control, so it
-- runs unchanged in the Supabase Dashboard, the Supabase CLI, or psql.
--
-- REQUIREMENT: enable Anonymous sign-ins first, under
--   Dashboard -> Authentication -> Providers -> Anonymous Sign-Ins
-- The app bootstraps a per-install anonymous session, and these policies are
-- scoped to that session's user id.
--
-- If your tables DO contain rows, do not run this. Use
-- 001b_backfill_existing_rows.sql instead, which attributes existing rows to
-- an owner before the NOT NULL constraints are applied.
-- ==============================================================================

-- 1. Add the owning user to each table.
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.device_status
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.emergency_events
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

ALTER TABLE public.check_ins
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

-- 2. Every row must have an owner. These tables are empty, so this is satisfied
--    trivially. It is kept as a safety net: if any row were present without an
--    owner, this statement fails loudly instead of silently weakening the column.
ALTER TABLE public.devices          ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.device_status    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.emergency_events ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.check_ins        ALTER COLUMN user_id SET NOT NULL;

-- 3. Index the ownership column, since every policy filters on it.
CREATE INDEX IF NOT EXISTS idx_devices_user_id          ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_user_id ON public.emergency_events(user_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_user_id        ON public.check_ins(user_id);

-- 4. Remove the open policies. These granted every caller full access to every
--    row, which is what this migration exists to close.
DROP POLICY IF EXISTS "Allow public device read access"   ON public.devices;
DROP POLICY IF EXISTS "Allow public device insert/update" ON public.devices;
DROP POLICY IF EXISTS "Allow device_status read/write"    ON public.device_status;
DROP POLICY IF EXISTS "Allow emergency_events read/write" ON public.emergency_events;
DROP POLICY IF EXISTS "Allow check_ins read/write"        ON public.check_ins;

-- 5. Replace them with policies scoped to the signed-in user. A request carrying
--    only the public anon key matches no rows, so it is rejected.
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