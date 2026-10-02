-- ==============================================================================
-- HomeGuard PostgreSQL / Supabase Database Schema
-- Open-Source Home Safety Companion
-- ==============================================================================
--
-- SECURITY MODEL
-- -------------
-- Row Level Security is scoped to auth.uid(). The client bootstraps an anonymous
-- Supabase session on first launch (POST /auth/v1/signup with an empty body), so each
-- install owns exactly one auth user and can only ever read or write its own rows.
--
-- The previous policies were USING (true), which let anyone holding the project's anon
-- key read every device's name, address, medical details, and emergency events.
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. DEVICES TABLE
-- Tracks registered protected and monitoring devices
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT UNIQUE NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_mode TEXT NOT NULL CHECK (device_mode IN ('PROTECTED_DEVICE', 'MONITORING_DEVICE')),
    person_name TEXT,
    person_role TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. DEVICE TELEMETRY / STATUS TABLE
-- Lightweight heartbeat and current state (updated at sensible intervals)
CREATE TABLE IF NOT EXISTS public.device_status (
    device_id TEXT PRIMARY KEY REFERENCES public.devices(device_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    battery_level INT NOT NULL CHECK (battery_level >= 0 AND battery_level <= 100),
    is_charging BOOLEAN NOT NULL DEFAULT FALSE,
    is_wifi_connected BOOLEAN NOT NULL DEFAULT TRUE,
    is_internet_available BOOLEAN NOT NULL DEFAULT TRUE,
    safety_status TEXT NOT NULL CHECK (safety_status IN ('SAFE', 'UNKNOWN', 'EMERGENCY')),
    last_check_in_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. EMERGENCY EVENTS TABLE
-- Immutable emergency alert logs.
-- `id` has no default on purpose: the client generates the UUID so retries upsert
-- idempotently instead of duplicating an emergency.
CREATE TABLE IF NOT EXISTS public.emergency_events (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    triggered_at TIMESTAMPTZ NOT NULL,
    details TEXT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. CHECK-INS TABLE
-- Scheduled "I'M OK" safety confirmations
CREATE TABLE IF NOT EXISTS public.check_ins (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    confirmed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_devices_device_id ON public.devices(device_id);
CREATE INDEX IF NOT EXISTS idx_devices_user_id ON public.devices(user_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_device_id ON public.emergency_events(device_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_user_id ON public.emergency_events(user_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_device_id ON public.check_ins(device_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_user_id ON public.check_ins(user_id);
CREATE INDEX IF NOT EXISTS idx_device_status_last_seen ON public.device_status(last_seen_at);

-- ==============================================================================
-- ROW LEVEL SECURITY
-- ==============================================================================
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.check_ins ENABLE ROW LEVEL SECURITY;

-- A signed-in user sees and writes only their own rows. An unauthenticated request
-- (bare anon key, no session) matches no row, so it is rejected.
DROP POLICY IF EXISTS "Allow public device read access" ON public.devices;
DROP POLICY IF EXISTS "Allow public device insert/update" ON public.devices;
DROP POLICY IF EXISTS "Allow device_status read/write" ON public.device_status;
DROP POLICY IF EXISTS "Allow emergency_events read/write" ON public.emergency_events;
DROP POLICY IF EXISTS "Allow check_ins read/write" ON public.check_ins;

CREATE POLICY "own_devices" ON public.devices
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_device_status" ON public.device_status
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_emergency_events" ON public.emergency_events
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "own_check_ins" ON public.check_ins
    FOR ALL
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Anonymous users are created with the `authenticated` role, so RLS applies normally.
-- No extra grant is needed beyond the default Supabase anon/authenticated grants.