-- HomeGuard migration 014: push tokens for caregiver alerts
--
-- Phase 4. FCM is the delivery transport for push; this table records which caregiver devices can
-- be reached. See NEW_PHASES.md D9 for the decision on which channel does what:
--
--   Supabase Realtime  dashboard freshness while the caregiver has the app open
--   FCM                alerts that reach a locked, backgrounded phone
--   Resend email       the opt-in fallback
--
-- user_id is filled by assign_row_ownership() rather than supplied by the client, for the same
-- reason as everywhere else: a client-supplied owner is a value a client can forge.
--
-- The token column is globally unique, not unique per user, because an FCM token identifies a
-- device installation. If the same installation were ever signed into a different account, the old
-- row must be replaced rather than coexisting, or alerts would go to a previous user's account.

CREATE TABLE IF NOT EXISTS public.push_tokens (
    id UUID PRIMARY KEY DEFAULT extensions.uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    platform TEXT NOT NULL DEFAULT 'ANDROID' CHECK (platform = 'ANDROID'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON public.push_tokens(user_id);

-- ------------------------------------------------------------------------------
-- Ownership
--
-- assign_row_ownership() is column-aware, so it fills user_id here without needing changes.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_assign_ownership_push_tokens ON public.push_tokens;
CREATE TRIGGER trg_assign_ownership_push_tokens
    BEFORE INSERT ON public.push_tokens
    FOR EACH ROW EXECUTE FUNCTION public.assign_row_ownership();

-- ------------------------------------------------------------------------------
-- Keep updated_at honest
--
-- Without this, a stale token cannot be told apart from a fresh one, and the first symptom is push
-- silently stopping working for one caregiver with no error anywhere.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.touch_push_token_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_touch_push_token ON public.push_tokens;
CREATE TRIGGER trg_touch_push_token
    BEFORE UPDATE ON public.push_tokens
    FOR EACH ROW EXECUTE FUNCTION public.touch_push_token_updated_at();

ALTER TABLE public.push_tokens ENABLE ROW LEVEL SECURITY;

-- A user registers and reads only their own tokens. Needed for both roles: the caregiver receives
-- alerts, and Phase 5's remote alarm needs the resident's phone reachable too.
DROP POLICY IF EXISTS "own_push_tokens" ON public.push_tokens;
CREATE POLICY "own_push_tokens" ON public.push_tokens
    FOR ALL USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);

-- ------------------------------------------------------------------------------
-- Token lookup for the notification edge function
--
-- Resolves a device's household caregivers to their push tokens in one call, so notify-caregiver does
-- not have to walk households -> members -> tokens by hand.
--
-- Only non-protected members, because a person should not be listed as a caregiver watching
-- themselves. SECURITY DEFINER because the edge function runs with the service role but this must
-- still be scoped deliberately rather than by accident.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.caregiver_push_tokens(target_device_id TEXT)
RETURNS TABLE (user_id UUID, token TEXT, updated_at TIMESTAMPTZ)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT pt.user_id, pt.token, pt.updated_at
    FROM public.push_tokens pt
    JOIN public.household_members hm ON hm.user_id = pt.user_id
    JOIN public.devices d ON d.user_id = hm.user_id
    WHERE d.device_id = target_device_id
      AND hm.household_id IS NOT NULL
      AND d.household_id IS NOT NULL
      AND hm.household_id = d.household_id
      AND hm.role = 'MONITORING'
      AND (pt.updated_at > now() - interval '90 days')
$$;

-- 90 days is a deliberately generous staleness bound. FCM tokens do expire, and the documented
-- recovery is that a failed send invalidates the token, so this is only a backstop against tokens
-- that were never invalidated because the device was off.
REVOKE ALL ON FUNCTION public.caregiver_push_tokens(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.caregiver_push_tokens(TEXT) TO anon, authenticated, service_role;

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- Verify
-- ------------------------------------------------------------------------------
SELECT column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'push_tokens'
ORDER BY ordinal_position;

SELECT policyname, cmd FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'push_tokens';

SELECT tgname FROM pg_trigger
WHERE tgrelid = 'public.push_tokens'::regclass AND NOT tgisinternal;
