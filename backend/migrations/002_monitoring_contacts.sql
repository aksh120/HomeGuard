-- ==============================================================================
-- HomeGuard migration 002: monitoring contacts and caregiver notification
-- ==============================================================================
--
-- WHY THIS EXISTS
-- ---------------
-- Sections 27 and 28 require a monitoring contact to be able to see a protected device's state
-- and to be notified when something happens. Sections 11 and 14 require the app to "notify the
-- monitoring contact" after an SOS or a missed check-in.
--
-- Nothing in the schema previously connected a caregiver to a device. `devices` held the protected
-- person's details but no way to reach anyone, so even a fully synced emergency_events row would
-- have told a caregiver nothing.
--
-- This migration adds:
--   1. monitoring_contacts: who to notify for a given device.
--   2. device_readings: the status history a monitoring dashboard needs.
--
-- NOTIFICATION DELIVERY
-- ---------------------
-- Delivery is a Supabase Database Webhook on emergency_events, handled by
-- supabase/functions/notify-caregiver/index.ts. That keeps the app free of
-- third-party credentials: the phone never holds an email API key, and
-- section 29 forbids shipping secrets.
--
-- Run this file on an EMPTY database, or after 001_rls_ownership.sql has run.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Monitoring contacts
--
-- A device may have several trusted contacts. `is_primary` marks the one that receives
-- unattended alerts. Email is the only channel wired up today; phone is stored so a
-- push/SMS provider can be added without another migration.
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.monitoring_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT monitoring_contacts_has_channel CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

-- One primary contact per device. Enforced in the database rather than in the app so a
-- duplicated insert cannot silently create two people receiving every alert.
CREATE UNIQUE INDEX IF NOT EXISTS idx_monitoring_contacts_primary
    ON public.monitoring_contacts(device_id)
    WHERE is_primary;

CREATE INDEX IF NOT EXISTS idx_monitoring_contacts_device
    ON public.monitoring_contacts(device_id);

-- ------------------------------------------------------------------------------
-- 2. Device readings
--
-- device_status is upserted, so it holds only the latest values. A monitoring
-- dashboard needs history to answer "was this device online an hour ago?".
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.device_readings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    battery_level INT CHECK (battery_level >= 0 AND battery_level <= 100),
    is_charging BOOLEAN,
    is_internet_available BOOLEAN,
    safety_status TEXT CHECK (safety_status IN ('SAFE', 'UNKNOWN', 'EMERGENCY'))
);

CREATE INDEX IF NOT EXISTS idx_device_readings_device_time
    ON public.device_readings(device_id, recorded_at DESC);

-- ------------------------------------------------------------------------------
-- 3. Row Level Security, matching migration 001.
-- ------------------------------------------------------------------------------
ALTER TABLE public.monitoring_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_readings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "own_monitoring_contacts" ON public.monitoring_contacts;
DROP POLICY IF EXISTS "own_device_readings" ON public.device_readings;

CREATE POLICY "own_monitoring_contacts" ON public.monitoring_contacts
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_device_readings" ON public.device_readings
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- 4. Verify
-- ------------------------------------------------------------------------------
SELECT tablename, policyname, cmd, qual
FROM pg_policies
WHERE schemaname = 'public'
  AND tablename IN ('monitoring_contacts', 'device_readings')
ORDER BY tablename, policyname;

-- ==============================================================================
-- NEXT STEPS (not SQL, so kept as comments)
-- ==============================================================================
--
-- 1. Deploy the notification function:
--      supabase functions deploy notify-caregiver --no-verify-jwt
--
--    --no-verify-jwt is required because a Database Webhook posts with the
--    service role, not a user JWT.
--
-- 2. Add the RESEND_API_KEY secret:
--      supabase secrets set RESEND_API_KEY=re_...
--
-- 3. Create the Database Webhook (Dashboard -> Database -> Webhooks):
--      Table:       emergency_events
--      Event:       INSERT
--      Type:        Supabase Database Webhook
--      URL:         https://<project-ref>.supabase.co/functions/v1/notify-caregiver
--      Authorization: service_role  (Add Header)
--
-- 4. Add a second webhook on check_ins if you want every confirmation delivered.
--    Section 36 warns against notification spam, so this is deliberately not on by
--    default: a caregiver does not need to be told the device is fine every morning.
--
-- 5. Populate monitoring_contacts from the app. The protected device uploads its
--    primary contact on save, so no manual SQL is needed.