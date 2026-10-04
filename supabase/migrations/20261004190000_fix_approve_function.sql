-- HomeGuard migration 012: fix ambiguous column reference in approve_remote_command
--
-- 20261004180000 declared RETURNS TABLE (kind TEXT, household_id UUID). PL/pgSQL turns OUT
-- parameters into variables, so `household_id` inside the function became ambiguous between the
-- output variable and the household_members column of the same name:
--
--   42702 column reference "household_id" is ambiguous
--
-- The failure surfaced the first time the function was actually executed, which is the argument for
-- exercising a migration's functions rather than only checking that it was accepted.
--
-- Fixed by returning a single column with an unambiguous name. The household id is not returned
-- because the caller already knows which household it asked about, so the extra column bought
-- nothing except this ambiguity.

-- CREATE OR REPLACE cannot change a function's return type (42P13), so the old signature has to be
-- dropped explicitly rather than replaced in place.
DROP FUNCTION IF EXISTS public.approve_remote_command(TEXT);

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

    SELECT d.household_id INTO resolved_household
    FROM public.devices d
    WHERE d.user_id = cmd.target_user_id
    LIMIT 1;

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

REVOKE ALL ON FUNCTION public.approve_remote_command(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_remote_command(TEXT) TO anon, authenticated;

NOTIFY pgrst, 'reload schema';