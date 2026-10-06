-- HomeGuard migration 021: an approval must satisfy the whole group, not just one person
--
-- Found by execution. request_household_join files one APPROVE_JOIN per resident, which is what makes
-- consent all-or-nothing: with two people at home, a new watcher needs both to agree. But
-- approve_remote_command executed on the FIRST code it was shown, so:
--
--   * one resident's approval created the membership immediately;
--   * the second resident's request stayed PENDING forever, orphaned;
--   * nothing recorded that a second person had never been asked.
--
-- So the guarantee migration 018's comment claims was not actually enforced. A comment that overstates
-- what the code does is worse than no comment, because the next reader trusts it.
--
-- Approval is now counted. Each code marks its own request EXECUTED, and the membership change happens
-- only when every request in the group has been executed. Until then the caller is told how many are
-- still outstanding, so the caregiver can see progress rather than being left wondering.
--
-- A veto ends the group. If any request is CANCELLED, the join cannot complete and the remainder are
-- cancelled too: one person declining is enough, because the exposure being agreed is every person's
-- medical details (D3), and one person said no.
--
-- Leaving is deliberately different, and the asymmetry is the point. A removal needs one approval, not
-- unanimity. Withdrawing access is the protective direction: making somebody prove that everybody at
-- home consents before they may stop watching would let a caregiver be held in a household they want
-- to leave. D4's approval gate is still honoured, because nothing is removed until a code is typed.
--
-- CREATE OR REPLACE cannot change a return type (42P13), so the function is dropped and recreated.
-- Dropping briefly removes a live function; this is unavoidable in Postgres and preferable to leaving
-- a return shape that cannot express "approved, but still waiting".

DROP FUNCTION IF EXISTS public.approve_remote_command(TEXT);

CREATE OR REPLACE FUNCTION public.approve_remote_command(submitted_code TEXT)
RETURNS TABLE (
    approved_kind TEXT,
    completed BOOLEAN,
    approvals_required INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    cmd public.remote_commands%ROWTYPE;
    resolved_household UUID;
    joiner_device TEXT;
    joiner_role TEXT;
    group_total INTEGER := 0;
    group_done INTEGER := 0;
    group_vetoed BOOLEAN := FALSE;
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

    -- Deliberately no check that auth.uid() = cmd.requested_by.
    --
    -- For a join and a removal the requester types the code the resident read out. For a mode switch
    -- the reverse holds: the resident asks, and the caregiver being asked shows the code and types it
    -- themselves, so requiring the requester would make the switch permanently impossible.
    --
    -- The consequence is accepted rather than worked around: the code is the whole credential, so
    -- whoever legitimately obtains it may complete the request. That is why
    -- list_my_remote_commands deliberately does not return approval_code, and why migration 017
    -- removed requested_by from the SELECT policy. A shared code cannot be bound to one person; what
    -- protects the resident is that only they ever see it.
    resolved_household := cmd.household_id;

    IF resolved_household IS NULL THEN
        RAISE EXCEPTION 'that request is no longer valid'
            USING ERRCODE = '22023';
    END IF;

    IF cmd.kind NOT IN ('APPROVE_JOIN', 'APPROVE_REMOVE', 'APPROVE_MODE_SWITCH') THEN
        RAISE EXCEPTION 'that request cannot be approved from here'
            USING ERRCODE = '22023';
    END IF;

    -- How many people this request is waiting on, and how many have now agreed.
    --
    -- Grouped by requester and household, not by target, because that is the unit a single "ask to
    -- watch this home" produced. Counting rows that no longer exist would strand the group.
    SELECT count(*) INTO group_total
    FROM public.remote_commands c
    WHERE c.kind = cmd.kind
      AND c.requested_by = cmd.requested_by
      AND c.household_id = resolved_household;

    UPDATE public.remote_commands
    SET status = 'EXECUTED', executed_at = NOW()
    WHERE id = cmd.id;

    SELECT
        count(*) FILTER (WHERE c.status = 'EXECUTED'),
        bool_or(c.status = 'CANCELLED')
    INTO group_done, group_vetoed
    FROM public.remote_commands c
    WHERE c.kind = cmd.kind
      AND c.requested_by = cmd.requested_by
      AND c.household_id = resolved_household;

    -- One veto ends it. The remaining requests are cancelled so nothing is left pending forever.
    IF group_vetoed THEN
        UPDATE public.remote_commands
        SET status = 'CANCELLED', executed_at = NOW()
        WHERE kind = cmd.kind
          AND requested_by = cmd.requested_by
          AND household_id = resolved_household
          AND status = 'PENDING';

        RAISE EXCEPTION 'someone at home declined this request'
            USING ERRCODE = '22023';
    END IF;

    IF group_done < group_total THEN
        -- Recorded and still waiting. Nothing has changed for this household yet, and the caller is
        -- told exactly how many more people have to agree.
        RETURN QUERY SELECT cmd.kind, FALSE, group_total - group_done;
        RETURN;
    END IF;

    IF cmd.kind = 'APPROVE_JOIN' THEN
        joiner_device := NULLIF(btrim(COALESCE(cmd.payload ->> 'device_id', '')), '');

        -- Read from devices, never from the request. A caller-supplied role would let anybody ask to
        -- join as PROTECTED without a resident's approval, and membership is household-wide either
        -- way, so that is the caregiver gate with the sign swapped.
        SELECT COALESCE(
            (SELECT CASE WHEN d.device_mode = 'MONITORING_DEVICE' THEN 'MONITORING' ELSE 'PROTECTED' END
             FROM public.devices d
             WHERE d.user_id = auth.uid()
               AND (joiner_device IS NULL OR d.device_id = joiner_device)
             ORDER BY d.created_at DESC
             LIMIT 1),
            -- No device row means the install has not registered itself, which on the caregiver flow
            -- happens only when setup was skipped. MONITORING is the restrictive default: it is not
            -- the role that implies a new protected device appearing in the household.
            'MONITORING'
        ) INTO joiner_role;

        INSERT INTO public.household_members (household_id, user_id, device_id, role)
        VALUES (resolved_household, auth.uid(), joiner_device, joiner_role)
        ON CONFLICT (household_id, user_id) DO UPDATE
            SET device_id = COALESCE(EXCLUDED.device_id, public.household_members.device_id),
                role = EXCLUDED.role;
    ELSIF cmd.kind = 'APPROVE_REMOVE' THEN
        -- One approval is enough, and the rest of the group is settled here rather than left pending.
        DELETE FROM public.household_members
        WHERE household_id = resolved_household AND user_id = auth.uid();

        UPDATE public.remote_commands
        SET status = 'CANCELLED', executed_at = NOW()
        WHERE kind = 'APPROVE_REMOVE'
          AND requested_by = cmd.requested_by
          AND household_id = resolved_household
          AND status = 'PENDING';
    ELSIF cmd.kind = 'APPROVE_MODE_SWITCH' THEN
        UPDATE public.devices
        SET device_mode = 'MONITORING_DEVICE'
        WHERE user_id = cmd.requested_by
          AND household_id = resolved_household
          AND device_mode = 'PROTECTED_DEVICE';
    END IF;

    RETURN QUERY SELECT cmd.kind, TRUE, 0;
END;
$$;

REVOKE ALL ON FUNCTION public.approve_remote_command(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.approve_remote_command(TEXT) TO anon, authenticated;

-- ------------------------------------------------------------------------------
-- Verify
-- ------------------------------------------------------------------------------

-- The function must express the two-column-plus shape, or clients will be reading the old result.
SELECT p.proname,
       pg_get_function_result(p.oid) AS returns
FROM pg_proc p
WHERE p.proname = 'approve_remote_command';

-- The old single-column function must be gone, not shadowed.
SELECT count(*) AS approve_remote_command_overloads
FROM pg_proc WHERE proname = 'approve_remote_command';

-- Group counting must key on requester and household, which is the shape a single ask produces.
SELECT position('requested_by = cmd.requested_by' in prosrc) > 0 AS grouped_by_requester,
       position('group_done < group_total' in prosrc) > 0 AS waits_for_the_group
FROM pg_proc WHERE proname = 'approve_remote_command';

NOTIFY pgrst, 'reload schema';