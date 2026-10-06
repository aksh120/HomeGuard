-- HomeGuard migration 020: the join throttle blocked every other caregiver, not just the spammer
--
-- throttle_join_request counted recent APPROVE_JOIN rows for the target alone:
--
--     WHERE c.target_user_id = NEW.target_user_id AND c.kind = 'APPROVE_JOIN'
--
-- So the first person to ask a resident to watch a home locked every other person out for 60 minutes.
-- Found by execution: with two residents at home, the second resident's own join request was enough to
-- block the caregiver's request a second later, and request_household_join raised "a join request was
-- already asked for this home recently" for a request nobody had made.
--
-- That is backwards from the intent. The thing worth limiting is one person repeatedly asking, which
-- floods a resident's approvals list with near-identical requests. Two different people asking once
-- each is not spam: it is two people wanting to watch the same home, which D2 explicitly supports.
--
-- Keyed on (target, requester) to match idx_remote_commands_one_pending_join, which already treats that
-- pair as the unit of "one request from one person".

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
      AND c.requested_by = NEW.requested_by
      AND c.kind = 'APPROVE_JOIN'
      AND c.created_at > now() - interval '60 minutes';

    IF recent_count > 0 THEN
        RAISE EXCEPTION 'you have already asked to watch this home in the last hour; please wait'
            USING ERRCODE = '22023';
    END IF;

    RETURN NEW;
END;
$$;

-- ------------------------------------------------------------------------------
-- Verify
--
-- The requester must appear in the predicate. Without it this migration has changed nothing, which is
-- precisely the bug it exists to fix.
-- ------------------------------------------------------------------------------
SELECT position('c.requested_by = NEW.requested_by' in prosrc) > 0 AS keyed_on_requester,
       position('please wait' in prosrc) > 0 AS message_updated
FROM pg_proc WHERE proname = 'throttle_join_request';

SELECT indexname FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'remote_commands' AND indexdef LIKE '%pending%';

NOTIFY pgrst, 'reload schema';