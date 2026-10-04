-- HomeGuard migration 007: split the households policy per command
--
-- Root cause of "new row violates row-level security policy for table households"
--
-- The policy was a single FOR ALL:
--   USING (is_household_member(id)) WITH CHECK (created_by = auth.uid())
--
-- Confirmed by instrumenting a BEFORE INSERT trigger, which reported for a real insert attempt:
--   created_by_eq_auth      = t     <- the WITH CHECK expression is satisfied
--   is_member_of_own_household = f  <- the USING expression is false
--
-- is_household_member(id) can never be true while a household is being created: membership lives
-- in household_members and is only written after the household row exists. So one bundled policy
-- demanded both "you are already a member" and "you created this", and no insert could satisfy it.
--
-- Verified independent of the HTTP client: reproduced with curl and with PowerShell, and with and
-- without Prefer: resolution=merge-duplicates, which is the header the app always sends.
--
-- Splitting per command also closes a privilege hole. Under FOR ALL with USING (membership), every
-- caregiver in a household could DELETE the household or reassign its created_by. Now only the
-- creator may do either, which is what was always intended.

DROP POLICY IF EXISTS "own_households" ON public.households;

-- Members may read the household. Unchanged behaviour.
CREATE POLICY "read_own_households" ON public.households
    FOR SELECT USING (public.is_household_member(id));

-- The creator owns it. Deliberately no USING clause: at INSERT time the caller cannot yet be a
-- member, so requiring membership would be unsatisfiable. WITH CHECK alone is correct and is
-- unforgeable because created_by is filled from the JWT by assign_row_ownership().
CREATE POLICY "create_own_household" ON public.households
    FOR INSERT WITH CHECK (created_by = auth.uid());

-- Members may rename a household, but may not transfer ownership of it.
CREATE POLICY "update_own_household" ON public.households
    FOR UPDATE USING (public.is_household_member(id))
    WITH CHECK (created_by = auth.uid());

-- Only the creator may delete a household. Previously any member could.
CREATE POLICY "delete_own_household" ON public.households
    FOR DELETE USING (created_by = auth.uid());

-- ------------------------------------------------------------------------------
-- Remove the diagnostic trigger and function used to isolate the cause.
-- ------------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_diag_probe ON public.households;
DROP FUNCTION IF EXISTS public.diag_probe();

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- Verify: four per-command policies, no FOR ALL remaining, no diagnostic trigger.
-- ------------------------------------------------------------------------------
SELECT policyname, cmd, qual, with_check
FROM pg_policies
WHERE schemaname = 'public' AND tablename = 'households'
ORDER BY cmd, policyname;

SELECT count(*) AS leftover_diag_triggers
FROM pg_trigger
WHERE tgname = 'trg_diag_probe';