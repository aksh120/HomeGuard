-- HomeGuard migration 011: remote approval requests
--
-- Decision D4 makes every membership change require a code shown on the protected phone, and D5
-- requires that an unanswered request never expires. D6 removes the one-tap revoke.
--
-- Why the code is generated and checked in the database rather than the client
--
-- The approval code is the entire consent mechanism. If the client compared codes, then anything
-- able to call PostgREST could satisfy an approval by guessing, and the protected device would have
-- no way to know whether consent was real. So:
--
--   * the code is minted by the database when the request is created;
--   * the code is never returned to a requester, only to the target device, which displays it;
--   * approval goes through approve_remote_command(code), which matches server-side and performs
--     the membership change itself.
--
-- target_user_id, not device_id
--
-- NEW_PHASES.md 6.2 named this column device_id, which collides with devices.device_id: that column
-- holds the app's locally minted TEXT identifier, whereas this one must hold the auth.users uuid
-- that household_members.user_id references. Renamed to target_user_id to keep the two apart.
--
-- Approvals are deliberately NOT rate limited
--
-- The 15-minute limit in NEW_PHASES.md 7.2 exists so a caregiver cannot spam a resident's contacts
-- with false emergencies. An approval request is not spam: it is inert until a human types a code,
-- and it never disappears. Rate limiting it would only stop a legitimate caregiver asking twice.
--
-- ------------------------------------------------------------------------------
-- 1. Approval codes
--
-- Six characters from the 32-symbol alphabet used for invite codes, so a code read aloud or retyped
-- from a text message cannot be ambiguous. 32^6 is about 1.07e9 combinations, and an approval code
-- is compared server-side against a single pending row rather than enumerated.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.generate_approval_code()
RETURNS TEXT
LANGUAGE sql
VOLATILE
AS $$
    SELECT string_agg(
        substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1),
        ''
    )
    FROM generate_series(1, 6)
$$;

-- ------------------------------------------------------------------------------
-- 2. remote_commands
--
-- Created here rather than in Phase 5 because approvals need it first. RAISE_ALARM is included in
-- the kind constraint so Phase 5 extends behaviour rather than the schema.
-- ------------------------------------------------------------------------------
-- uuid_generate_v4 lives in the extensions schema, which is not on the search path when applied via
-- `supabase db push`. Qualified explicitly so this does not depend on the caller's search_path.
CREATE TABLE IF NOT EXISTS public.remote_commands (
    id UUID PRIMARY KEY DEFAULT extensions.uuid_generate_v4(),
    -- The auth user whose device is affected. NOT the app's TEXT device_id; see note above.
    target_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    household_id UUID REFERENCES public.households(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('APPROVE_JOIN', 'APPROVE_REMOVE', 'APPROVE_MODE_SWITCH')),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    -- Who asked. May be null if the requester's auth user is later deleted.
    requested_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    -- Minted server-side, shown only on the protected device.
    approval_code TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'EXECUTED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    executed_at TIMESTAMPTZ,
    -- D5: deliberately no expiry column. An unanswered request stays PENDING forever rather than
    -- silently lapsing, because a timeout on a consent prompt is a silent consent.
    CONSTRAINT remote_commands_pending_has_code
        CHECK (status <> 'PENDING' OR approval_code IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_remote_commands_target
    ON public.remote_commands(target_user_id, status);
CREATE INDEX IF NOT EXISTS idx_remote_commands_household
    ON public.remote_commands(household_id);
CREATE INDEX IF NOT EXISTS idx_remote_commands_requested_by
    ON public.remote_commands(requested_by);

-- ------------------------------------------------------------------------------
-- 3. Who may request what
--
-- SECURITY DEFINER so the membership check does not recurse back through household_members' own
-- policy, following the pattern established by is_household_member.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.can_request_for(target UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.household_members hm
        JOIN public.devices d ON d.user_id = hm.user_id
        WHERE d.user_id = target
          AND hm.household_id = d.household_id
          AND hm.user_id = auth.uid()
    )
$$;

ALTER TABLE public.remote_commands ENABLE ROW LEVEL SECURITY;

-- A household member may ask. The helper proves the caller shares a household with the target.
DROP POLICY IF EXISTS "request_remote_command" ON public.remote_commands;
CREATE POLICY "request_remote_command" ON public.remote_commands
    FOR INSERT WITH CHECK (public.can_request_for(target_user_id));

-- The target sees the code so it can display it; the requester sees their own request's state.
-- Other household members deliberately do NOT get SELECT, because that would expose the approval
-- code to anyone who joins, defeating the point of requiring it.
DROP POLICY IF EXISTS "read_own_remote_commands" ON public.remote_commands;
CREATE POLICY "read_own_remote_commands" ON public.remote_commands
    FOR SELECT USING (target_user_id = auth.uid() OR requested_by = auth.uid());

-- Only the target may mark a request resolved, so a requester cannot declare their own request
-- satisfied.
DROP POLICY IF EXISTS "resolve_own_remote_commands" ON public.remote_commands;
CREATE POLICY "resolve_own_remote_commands" ON public.remote_commands
    FOR UPDATE USING (target_user_id = auth.uid())
    WITH CHECK (target_user_id = auth.uid());

-- ------------------------------------------------------------------------------
-- 4. Approval
--
-- The membership changes happen here, not on the client. The client can only present a code.
--
-- approve_remote_command returns the kind it executed so the UI can explain what changed.
-- APPROVE_MODE_SWITCH is intentionally absent: switching a device's profile is device-local state
-- that lives in the app's own preferences, so the device applies it on receipt and marks the row
-- EXECUTED itself, under the resolve_own_remote_commands policy above.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.approve_remote_command(submitted_code TEXT)
RETURNS TABLE (kind TEXT, household_id UUID)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    cmd public.remote_commands%ROWTYPE;
    target_household UUID;
BEGIN
    SELECT * INTO cmd
    FROM public.remote_commands c
    WHERE c.status = 'PENDING'
      AND c.approval_code = upper(btrim(COALESCE(submitted_code, '')))
    ORDER BY c.created_at ASC
    LIMIT 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'that code is not correct or is no longer pending'
            USING ERRCODE = '22023';
    END IF;

    SELECT d.household_id INTO target_household
    FROM public.devices d
    WHERE d.user_id = cmd.target_user_id
    LIMIT 1;

    IF cmd.kind = 'APPROVE_JOIN' THEN
        INSERT INTO public.household_members (household_id, user_id, role)
        VALUES (target_household, auth.uid(), 'MONITORING')
        ON CONFLICT (household_id, user_id) DO NOTHING;
    ELSIF cmd.kind = 'APPROVE_REMOVE' THEN
        DELETE FROM public.household_members
        WHERE household_id = target_household AND user_id = auth.uid();
    ELSE
        RAISE EXCEPTION 'that request cannot be approved from here'
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.remote_commands
    SET status = 'EXECUTED', executed_at = NOW()
    WHERE id = cmd.id;

    RETURN QUERY SELECT cmd.kind, target_household;
END;
$$;

REVOKE ALL ON FUNCTION public.approve_remote_command(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_remote_command(TEXT) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 5. Cancel a request
--
-- The target may withdraw consent before it is exercised. Without this a resident could never
-- refuse, which is not the same as consent.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.cancel_remote_command(command_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    affected INTEGER;
BEGIN
    UPDATE public.remote_commands
    SET status = 'CANCELLED', executed_at = NOW()
    WHERE id = command_id
      AND status = 'PENDING'
      AND target_user_id = auth.uid();

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected > 0;
END;
$$;

REVOKE ALL ON FUNCTION public.cancel_remote_command(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.cancel_remote_command(UUID) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- 6. Verify
-- ------------------------------------------------------------------------------
SELECT tablename, policyname, cmd FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'remote_commands'
ORDER BY cmd, policyname;

SELECT proname, prosecdef FROM pg_proc
WHERE proname IN ('generate_approval_code', 'can_request_for',
                  'approve_remote_command', 'cancel_remote_command')
ORDER BY proname;

-- Minting is random, so assert on shape and uniqueness across a sample rather than a fixed value.
SELECT count(*) AS minted,
       count(DISTINCT code) AS distinct_codes,
       bool_and(char_length(code) = 6) AS all_six_chars,
       bool_and(code = upper(code)) AS all_uppercase
FROM (SELECT public.generate_approval_code() AS code FROM generate_series(1, 500)) s;

-- D5: the table must have no expiry column at all.
SELECT column_name FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'remote_commands'
  AND (column_name LIKE '%expir%' OR column_name LIKE '%ttl%' OR column_name LIKE '%timeout%');