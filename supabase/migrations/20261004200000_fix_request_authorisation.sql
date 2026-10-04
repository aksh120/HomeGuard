-- HomeGuard migration 013: separate "may ask to join" from "may ask within a household"
--
-- can_request_for() was wrong in two ways, both found by executing the flow rather than reading it.
--
-- 1. The join condition collapsed to "requester is the target"
--
--    It joined household_members to devices on d.user_id = hm.user_id, then filtered
--    d.user_id = target. Both sides had to be the same person, so the function returned false for
--    every requester except the target. Verified: a genuine household member got false.
--
-- 2. A first-time caregiver has no device in the household yet
--
--    Even with the join fixed, "shares a household" is the wrong test for APPROVE_JOIN: someone who
--    has just typed an invite code holds no membership and has no device in that household. They
--    would never be allowed to ask.
--
-- The gate for joining is the invite code, and it was already validated: find_household_by_invite_code
-- is SECURITY DEFINER and only resolves a code that actually exists, so a caller holding the id got
-- it by knowing the code. Joining therefore checks that the target's device really is in the named
-- household, and trust the code to have gated the lookup. The household id is a UUID, so it cannot be
-- guessed.
--
-- Removal and mode switching stay restricted to actual household members.

CREATE OR REPLACE FUNCTION public.can_request_for(target UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.devices target_device
        JOIN public.devices requester_device
          ON requester_device.household_id = target_device.household_id
         AND requester_device.household_id IS NOT NULL
        WHERE target_device.user_id = target
          AND requester_device.user_id = auth.uid()
    )
$$;

-- May the caller ask to watch the household that the target's device belongs to?
CREATE OR REPLACE FUNCTION public.can_request_join(target UUID, household UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.devices d
        WHERE d.user_id = target
          AND d.household_id = household
    )
$$;

-- The policy now branches on kind, and additionally pins requested_by to the caller so nobody can
-- file a request in someone else's name.
DROP POLICY IF EXISTS "request_remote_command" ON public.remote_commands;
CREATE POLICY "request_remote_command" ON public.remote_commands
    FOR INSERT WITH CHECK (
        requested_by = auth.uid()
        AND (
            (kind = 'APPROVE_JOIN'
                AND public.can_request_join(target_user_id, household_id))
            OR (kind IN ('APPROVE_REMOVE', 'APPROVE_MODE_SWITCH')
                AND public.can_request_for(target_user_id))
        )
    );

-- A join may not name a household the target's device is not part of, and the approval must resolve
-- to the household the request was actually made against rather than whichever household the target
-- happens to be in now.
CREATE OR REPLACE FUNCTION public.approve_remote_command(submitted_code TEXT)
RETURNS TABLE (approved_kind TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    cmd public.remote_commands%ROWTYPE;
    resolved_household UUID;
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

    -- The household the request was filed against is authoritative. Falling back to whatever the
    -- target is in now would let a stale request act on a household the resident has since left.
    resolved_household := cmd.household_id;

    IF resolved_household IS NULL THEN
        RAISE EXCEPTION 'that request is no longer valid'
            USING ERRCODE = '22023';
    END IF;

    IF cmd.kind = 'APPROVE_JOIN' THEN
        INSERT INTO public.household_members (household_id, user_id, role)
        VALUES (resolved_household, auth.uid(), 'MONITORING')
        ON CONFLICT (household_id, user_id) DO NOTHING;
    ELSIF cmd.kind = 'APPROVE_REMOVE' THEN
        DELETE FROM public.household_members
        WHERE household_id = resolved_household AND user_id = auth.uid();
    ELSE
        RAISE EXCEPTION 'that request cannot be approved from here'
            USING ERRCODE = '22023';
    END IF;

    UPDATE public.remote_commands
    SET status = 'EXECUTED', executed_at = NOW()
    WHERE id = cmd.id;

    RETURN QUERY SELECT cmd.kind;
END;
$$;

GRANT EXECUTE ON FUNCTION public.can_request_join(UUID, UUID) TO anon, authenticated;

NOTIFY pgrst, 'reload schema';