-- HomeGuard migration 030: two more defects in the D4 enforcement, both found by executing it
--
-- 1. request_mode_switch had the same loop bug 019 fixed elsewhere
--
--     `FOR watcher IN SELECT hm.user_id ... LOOP ... watcher.user_id` raises
--     42P01 "missing FROM-clause entry for table watcher", exactly as request_household_join did. The
--     mode switch could never be requested at all.
--
--     Migration 019's verification checked request_household_join, request_household_leave and
--     approve_remote_command for loop-shaped bodies, and left request_mode_switch out of the list.
--     A check that enumerates three of four functions is worse than no check, because it reads as
--     coverage. Extended below to every request function.
--
-- 2. Leaving required unanimity, contradicting what 021 said it did
--
--     021's comment states plainly that a removal needs one approval, on the grounds that nobody may
--     be held in a household they want to leave. The code applied the group wait before the
--     kind-specific branch, so it applied to APPROVE_REMOVE too and a caregiver could not leave until
--     every person at home had agreed. That is a genuine consent trap: with two residents, one refusing
--     would trap the watcher indefinitely.
--
--     Withdrawing access is the protective direction, so one code is enough. The group wait now applies
--     only where the group guards something being exposed: a join, and a mode switch.

-- ------------------------------------------------------------------------------
-- 1. request_mode_switch, without the loop
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.request_mode_switch()
RETURNS TABLE (approvals_required INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    my_household UUID;
    watcher_count INTEGER;
    created INTEGER := 0;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'no active session'
            USING ERRCODE = '42501';
    END IF;

    SELECT hm.household_id INTO my_household
    FROM public.household_members hm
    WHERE hm.user_id = auth.uid() AND hm.role = 'PROTECTED'
    LIMIT 1;

    IF my_household IS NULL THEN
        RAISE EXCEPTION 'this phone is not set up as a home device'
            USING ERRCODE = '22023';
    END IF;

    SELECT count(*) INTO watcher_count
    FROM public.household_members hm
    WHERE hm.household_id = my_household
      AND hm.role = 'MONITORING'
      AND hm.user_id IS DISTINCT FROM auth.uid();

    IF watcher_count = 0 THEN
        RAISE EXCEPTION 'nobody is watching this home, so there is nothing to ask permission for'
            USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.remote_commands
        (target_user_id, household_id, kind, payload, requested_by, approval_code)
    SELECT hm.user_id,
           my_household,
           'APPROVE_MODE_SWITCH',
           '{}'::jsonb,
           auth.uid(),
           public.generate_approval_code()
    FROM public.household_members hm
    WHERE hm.household_id = my_household
      AND hm.role = 'MONITORING'
      AND hm.user_id IS DISTINCT FROM auth.uid()
      AND NOT EXISTS (
          SELECT 1 FROM public.remote_commands c
          WHERE c.kind = 'APPROVE_MODE_SWITCH'
            AND c.status = 'PENDING'
            AND c.target_user_id = hm.user_id
            AND c.requested_by = auth.uid()
            AND c.household_id = my_household
      );

    GET DIAGNOSTICS created = ROW_COUNT;

    IF created = 0 THEN
        RAISE EXCEPTION 'you have already asked to change this phone'
            USING ERRCODE = '22023';
    END IF;

    RETURN QUERY SELECT created;
END;
$$;

-- ------------------------------------------------------------------------------
-- 2. approve_remote_command: only exposure-bearing kinds wait for the group
-- ------------------------------------------------------------------------------
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
    must_agree BOOLEAN := FALSE;
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

    -- A join exposes everybody's data in the household and a mode switch ends somebody's protection, so
    -- both wait for every person asked. A removal withdraws access, so one code is enough.
    must_agree := cmd.kind IN ('APPROVE_JOIN', 'APPROVE_MODE_SWITCH');

    UPDATE public.remote_commands
    SET status = 'EXECUTED', executed_at = NOW()
    WHERE id = cmd.id;

    IF must_agree THEN
        SELECT count(*) INTO group_total
        FROM public.remote_commands c
        WHERE c.kind = cmd.kind
          AND c.requested_by = cmd.requested_by
          AND c.household_id = resolved_household;

        SELECT
            count(*) FILTER (WHERE c.status = 'EXECUTED'),
            bool_or(c.status = 'CANCELLED')
        INTO group_done, group_vetoed
        FROM public.remote_commands c
        WHERE c.kind = cmd.kind
          AND c.requested_by = cmd.requested_by
          AND c.household_id = resolved_household;

        -- One veto ends it. The remainder are cancelled so nothing is left pending forever.
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
            RETURN QUERY SELECT cmd.kind, FALSE, group_total - group_done;
            RETURN;
        END IF;
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
        DELETE FROM public.household_members
        WHERE household_id = resolved_household AND user_id = auth.uid();

        -- Settle the rest of the group here rather than leaving it pending forever.
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
-- 3. Verify
--
-- Every request function is listed, not the three that happened to be broken. An enumeration that
-- skips one entry is indistinguishable from coverage once it has been read twice.
-- ------------------------------------------------------------------------------
SELECT proname,
       position('FOR ' in prosrc) = 0 AS no_loop_variable,
       position('GET DIAGNOSTICS' in prosrc) > 0 AS counts_inserted_rows
FROM pg_proc
WHERE proname IN (
    'request_household_join',
    'request_household_leave',
    'request_mode_switch',
    'approve_remote_command'
)
ORDER BY proname;

-- Only the exposure-bearing kinds may wait for the group.
SELECT position('must_agree := cmd.kind IN (''APPROVE_JOIN'', ''APPROVE_MODE_SWITCH'')' in prosrc) > 0
           AS group_wait_excludes_remove,
       position('must_agree THEN' in prosrc) > 0 AS group_wait_is_conditional
FROM pg_proc WHERE proname = 'approve_remote_command';

SELECT pg_get_function_result(p.oid) AS returns
FROM pg_proc p WHERE p.proname = 'approve_remote_command';

SELECT count(*) AS approve_overloads FROM pg_proc WHERE proname = 'approve_remote_command';

NOTIFY pgrst, 'reload schema';