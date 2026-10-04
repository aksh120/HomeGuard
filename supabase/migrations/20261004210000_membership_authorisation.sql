-- HomeGuard migration 014: membership-based authorisation and join-request abuse control
--
-- Two corrections, both found by running the flow rather than reasoning about it.
--
-- 1. can_request_for keyed on devices.household_id, which is the wrong relationship
--
-- A caregiver legitimately holds a device whose household_id is NULL: they are in
-- household_members for the household, but their own device row is not attached. Membership is the
-- actual relationship being tested, so test that. Keying on the device meant a genuine member was
-- refused. Verified before: member_may_request came back false.
--
-- 2. A stranger could file unlimited APPROVE_JOIN requests against a known household
--
-- Joining is gated by the invite code, but that gate is checked when the code is resolved into a
-- household id. After that the request itself was open to any signed-in caller, so the only thing
-- stopping screen spam on a resident's phone was politeness. An earlier note in this migration set
-- claimed approvals cannot be spam; that was wrong for joins specifically, because a join request is
-- created by someone who is by definition not yet a member.
--
-- Two controls, both cheap and neither blocking a legitimate flow:
--   * a partial unique index, so the same person cannot file duplicate pending joins
--   * a minimum interval per target, so a stranger cannot flood the resident's screen
--
-- Removal and mode-switch requests are limited to real household members by can_request_for, and a
-- member cannot flood themselves, so the interval applies only to joins.

CREATE OR REPLACE FUNCTION public.can_request_for(target UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.household_members requester
        WHERE requester.user_id = auth.uid()
          AND requester.household_id IN (
              SELECT d.household_id
              FROM public.devices d
              WHERE d.user_id = target
                AND d.household_id IS NOT NULL
          )
    )
$$;

-- One pending join request per person per target. Lets several caregivers each ask once, which
-- decision D2 requires, while making duplicate taps idempotent in effect.
CREATE UNIQUE INDEX IF NOT EXISTS idx_remote_commands_one_pending_join
    ON public.remote_commands (target_user_id, requested_by)
    WHERE kind = 'APPROVE_JOIN' AND status = 'PENDING';

-- Minimum interval between join requests for the same target, so a stranger holding a household id
-- cannot flood the resident's approvals list.
CREATE OR REPLACE FUNCTION public.throttle_join_request()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    recent_count INTEGER;
BEGIN
    IF NEW.kind <> 'APPROVE_JOIN' THEN
        RETURN NEW;
    END IF;

    SELECT count(*) INTO recent_count
    FROM public.remote_commands c
    WHERE c.target_user_id = NEW.target_user_id
      AND c.kind = 'APPROVE_JOIN'
      AND c.created_at > now() - interval '60 minutes';

    IF recent_count > 0 THEN
        RAISE EXCEPTION 'a join request was already asked for this home recently; please wait'
            USING ERRCODE = '22023';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_throttle_join_request ON public.remote_commands;
CREATE TRIGGER trg_throttle_join_request
    BEFORE INSERT ON public.remote_commands
    FOR EACH ROW EXECUTE FUNCTION public.throttle_join_request();

NOTIFY pgrst, 'reload schema';

-- ------------------------------------------------------------------------------
-- Verify
-- ------------------------------------------------------------------------------
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'remote_commands';

SELECT tgname FROM pg_trigger
WHERE tgrelid = 'public.remote_commands'::regclass AND NOT tgisinternal;