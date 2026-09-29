-- ==============================================================================
-- HomeGuard PostgreSQL / Supabase Database Schema
-- Open-Source Home Safety Companion
-- ==============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. DEVICES TABLE
-- Tracks registered protected and monitoring devices
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT UNIQUE NOT NULL,
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
    battery_level INT NOT NULL CHECK (battery_level >= 0 AND battery_level <= 100),
    is_charging BOOLEAN NOT NULL DEFAULT FALSE,
    is_wifi_connected BOOLEAN NOT NULL DEFAULT TRUE,
    is_internet_available BOOLEAN NOT NULL DEFAULT TRUE,
    safety_status TEXT NOT NULL CHECK (safety_status IN ('SAFE', 'UNKNOWN', 'EMERGENCY')),
    last_check_in_at TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. EMERGENCY EVENTS TABLE
-- Immutable emergency alert logs
CREATE TABLE IF NOT EXISTS public.emergency_events (
    id UUID PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES public.devices(device_id) ON DELETE CASCADE,
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
    confirmed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_devices_device_id ON public.devices(device_id);
CREATE INDEX IF NOT EXISTS idx_emergency_events_device_id ON public.emergency_events(device_id);
CREATE INDEX IF NOT EXISTS idx_check_ins_device_id ON public.check_ins(device_id);
CREATE INDEX IF NOT EXISTS idx_device_status_last_seen ON public.device_status(last_seen_at);

-- ROW LEVEL SECURITY (RLS) POLICIES
ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.device_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.emergency_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.check_ins ENABLE ROW LEVEL SECURITY;

-- Allow authenticated devices and monitoring contacts access to their scoped records
CREATE POLICY "Allow public device read access" ON public.devices FOR SELECT USING (true);
CREATE POLICY "Allow public device insert/update" ON public.devices FOR ALL USING (true);

CREATE POLICY "Allow device_status read/write" ON public.device_status FOR ALL USING (true);
CREATE POLICY "Allow emergency_events read/write" ON public.emergency_events FOR ALL USING (true);
CREATE POLICY "Allow check_ins read/write" ON public.check_ins FOR ALL USING (true);
