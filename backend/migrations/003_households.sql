-- ==============================================================================
-- HomeGuard migration 003: households, so a caregiver device can watch a home
-- ==============================================================================
--
-- WHY THIS EXISTS
-- ---------------
-- Section 6B requires a monitoring device that can display a protected device's status, last
-- check-in, recent events, and emergency events. Section 27 requires that dashboard.
--
-- Migrations 001 and 002 scoped every table to auth.uid(). That was correct for a single-device
-- app but it makes two installs mutually invisible: each install gets its own anonymous Supabase
-- user, so a caregiver phone matches zero rows on the home phone's data. A caregiver dashboard
-- would render permanently empty.
--
-- The fix is a household: a small group of devices that share one owner relationship, with the
-- protected device and the caregiver as members. Access is then scoped to household membership
-- rather than to a single auth user.
--
-- ONBOARDING
-- ----------
-- The home device creates a household and gets a short invite code. The caregiver enters that
-- code on their phone. No email exchange, no account creation, nothing that identifies a person.
--
-- Run after 001_rls_ownership.sql and 002_monitoring_contacts.sql.
-- All four tables are still empty on a fresh install, so no back-fill is required.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Households
--
-- The invite code is the join credential. It is a short random string, not a secret in the
-- cryptographic sense: anyone who guesses it can read that household's data. That is acceptable
-- for a code a user reads aloud to a family member, and the code is regenerable below.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.households (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL DEFAULT 'Home',
    invite_code TEXT UNIQUE NOT NULL,
    created_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_households_invite_code ON public.households(invite_code);

-- ------------------------------------------------------------------------------
-- 2. Membership
--
-- One row per (household, user). A user is either the protected device's owner or a monitoring
-- contact; role is carried here rather than inferred from device_mode so that a household with
-- several caregivers is representable.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.household_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    household_id UUID NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id TEXT,
    role TEXT NOT NULL CHECK (role IN ('PROTECTED', 'MONITORING')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT household_members_unique UNIQUE (household_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_household_members_user ON public.household_members(user_id);
CREATE INDEX IF NOT EXISTS idx_household_members_device ON public.household_members(device_id);

-- ------------------------------------------------------------------------------
-- 3. Attach households to the existing tables
--
-- Nullable: a device that has not been shared yet must keep working exactly as before. This
-- preserves the single-device behaviour required by section 20.
-- ------------------------------------------------------------------------------
ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS household_id UUID REFERENCES public.households(id) ON DELETE CASCADE;

ALTER TABLE public.device_status
    ADD COLUMN IF NOT EXISTS household_id UUID REFERENCES public.households(id) ON DELETE CASCADE;

ALTER TABLE public.emergency_events
    ADD COLUMN IF NOT EXISTS household_id UUID REFERENCES public.households(id) ON DELETE CASCADE;

ALTER TABLE public.check_ins
    ADD COLUMN IF NOT EXISTS household_id UUID REFERENCES public.households(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_devices_household          ON public.devices(household_id);
CREATE INDEX IF NOT EXISTS idx_device_status_household    ON public.device_status(household_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_household ON public.emergency_events(household_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_household        ON public.check_ins(household_id);

-- ------------------------------------------------------------------------------
-- 4. Helper
--
-- SECURITY DEFINER so the membership check does not recurse back through household_members'
-- own policy. search_path is pinned to stop a caller from redirecting the lookup.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.is_household_member(target_household UUID)
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
    );
$$;

-- ------------------------------------------------------------------------------
-- 5. Row Level Security
--
-- Every table keeps the existing owner rule AND gains a household rule. An unshared device is
-- still fully private, because is_household_member(NULL) is NULL, not true.
-- ------------------------------------------------------------------------------
ALTER TABLE public.households ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.household_members ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "own_households" ON public.households;
CREATE POLICY "own_households" ON public.households
    FOR ALL USING (public.is_household_member(id))
    WITH CHECK (created_by = auth.uid());

DROP POLICY IF EXISTS "own_household_members" ON public.household_members;
CREATE POLICY "own_household_members" ON public.household_members
    FOR SELECT USING (public.is_household_member(household_id));

-- A member may add themselves (this is the join path) but may not edit anyone else's row.
CREATE POLICY "join_household" ON public.household_members
    FOR INSERT WITH CHECK (user_id = auth.uid());

-- Caregivers read the home device's data. They never write to it: an emergency row must come
-- from the device that raised it, otherwise a caregiver could fabricate one.
DROP POLICY IF EXISTS "own_devices" ON public.devices;
CREATE POLICY "household_read_devices" ON public.devices
    FOR SELECT USING (public.is_household_member(household_id));
CREATE POLICY "write_own_devices" ON public.devices
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "own_emergency_events" ON public.emergency_events;
CREATE POLICY "household_read_emergency_events" ON public.emergency_events
    FOR SELECT USING (public.is_household_member(household_id));
CREATE POLICY "write_own_emergency_events" ON public.emergency_events
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "own_check_ins" ON public.check_ins;
CREATE POLICY "household_read_check_ins" ON public.check_ins
    FOR SELECT USING (public.is_household_member(household_id));
CREATE POLICY "write_own_check_ins" ON public.check_ins
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "own_device_status" ON public.device_status;
CREATE POLICY "household_read_device_status" ON public.device_status
    FOR SELECT USING (public.is_household_member(household_id));
CREATE POLICY "write_own_device_status" ON public.device_status
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- Caregivers are listed for the home device's owner, not the other way round.
DROP POLICY IF EXISTS "own_monitoring_contacts" ON public.monitoring_contacts;
CREATE POLICY "own_monitoring_contacts" ON public.monitoring_contacts
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 6. Verify
-- ------------------------------------------------------------------------------
SELECT tablename, policyname, cmd
FROM pg_policies
WHERE schemaname = 'public'
  AND tablename IN ('households', 'household_members', 'devices', 'emergency_events',
                     'check_ins', 'device_status')
ORDER BY tablename, policyname;