-- HomeGuard migration 008: create a household and its first member atomically
--
-- Root cause of the households INSERT failure (42501)
--
-- The INSERT itself always worked. Proven by varying only the Prefer header:
--
--   Prefer: return=minimal            -> 201 created
--   Prefer: tx=rollback               -> 201 created
--   Prefer: return=representation     -> 403 "new row violates row-level security policy"
--   Prefer: resolution=merge-duplicates-> 403 (the header the app always sends)
--
-- return=resolution and return=representation make PostgREST use INSERT ... RETURNING. RETURNING is
-- evaluated against the table's SELECT policy, which is read_own_households USING
-- (is_household_member(id)). At that moment the caller has just created the household and is not yet
-- a member of it, so the row is invisible and PostgreSQL raises 42501 instead of silently returning
-- nothing. The app could never get past this because it creates the household and then inserts the
-- membership as two separate requests.
--
-- The fix performs both writes in one SECURITY DEFINER function. Inside it the current role is the
-- owner, which bypasses RLS, so RETURNING is unfiltered. This also makes the operation atomic: the
-- previous two-request sequence could leave a household with no member, which nobody could ever see
-- or join again.
--
-- Joining is unaffected: household_members has only an INSERT policy, so its RETURNING is governed
-- by the SELECT policy is_household_member(household_id), and by then the caller's own membership
-- row exists and is visible within the same statement.

-- ------------------------------------------------------------------------------
-- Remove the temporary permissive policy used to isolate the cause.
-- ------------------------------------------------------------------------------
DROP POLICY IF EXISTS "diag_permissive_insert" ON public.households;
DROP FUNCTION IF EXISTS public.diag_invoker();
DROP FUNCTION IF EXISTS public.diag_definer();

-- ------------------------------------------------------------------------------
-- Atomic household creation
--
-- The caller supplies the invite code because it is the client that already knows how to generate
-- one and must display it; the server validates uniqueness and raises a clear error on collision
-- rather than silently retrying, so the caller can generate another and try again.
--
-- created_by is filled by assign_row_ownership() from the verified JWT, so it is not accepted as a
-- parameter at all. A caller cannot create a household owned by somebody else.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_household_with_membership(
    household_name TEXT,
    code TEXT,
    member_device_id TEXT
)
RETURNS TABLE (id UUID, name TEXT, invite_code TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    new_household_id UUID;
    new_name TEXT := COALESCE(NULLIF(btrim(household_name), ''), 'Home');
    new_code TEXT := upper(btrim(COALESCE(code, '')));
BEGIN
    IF length(new_code) < 6 THEN
        RAISE EXCEPTION 'invite code is too short'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.households (name, invite_code)
    VALUES (new_name, new_code)
    RETURNING households.id INTO new_household_id;

    INSERT INTO public.household_members (household_id, user_id, device_id, role)
    VALUES (new_household_id, auth.uid(), member_device_id, 'PROTECTED');

    RETURN QUERY
        SELECT new_household_id, new_name, new_code;

EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION 'that invite code is already in use'
            USING ERRCODE = '23505';
END;
$$;

REVOKE ALL ON FUNCTION public.create_household_with_membership(TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_household_with_membership(TEXT, TEXT, TEXT)
    TO anon, authenticated;

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- Verify: the permissive policy is gone and both RPCs exist and are executable.
-- ------------------------------------------------------------------------------
SELECT policyname, cmd, permissive
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'households'
ORDER BY cmd, policyname;

SELECT proname, prosecdef, proacl
FROM pg_proc
WHERE proname IN ('create_household_with_membership', 'find_household_by_invite_code')
ORDER BY proname;