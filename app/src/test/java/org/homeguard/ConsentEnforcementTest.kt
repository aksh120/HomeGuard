package org.homeguard

import org.homeguard.core.model.ApprovalKind
import org.homeguard.core.model.ApprovalOutcome
import org.homeguard.core.model.ApprovalPolicy
import org.homeguard.core.model.JoinRequestOutcome
import org.homeguard.core.model.MyRemoteRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Decision D4, now that it is enforced rather than merely intended.
 *
 * Phase 3 built the approval machinery and left the `join_household` insert policy open beside it, so
 * anybody holding an invite code became a member immediately and APPROVE_JOIN was never filed. These
 * tests pin the client-side half of the fix: what the app believes happened after each server
 * response, because the two ways to get this wrong are both silent.
 *
 * A partial approval reported as a success tells a caregiver they now have access to somebody's
 * medical details when they do not. That is the exact failure D4 exists to prevent, reintroduced
 * through a status message rather than through a policy.
 */
class ConsentEnforcementTest {

    // --- approval is a group, not a single code ----------------------------------------------

    @Test
    fun `an incomplete approval is not reported as complete`() {
        val firstOfTwo = ApprovalOutcome(
            kind = ApprovalKind.APPROVE_JOIN,
            completed = false,
            approvalsRequired = 1
        )

        assertFalse(
            "one of two approvals must not read as done",
            firstOfTwo.completed
        )
        assertTrue(
            "and must be recognisable as still waiting",
            firstOfTwo.isPartiallyApproved
        )
    }

    @Test
    fun `a completed approval reports nothing outstanding`() {
        val done = ApprovalOutcome(
            kind = ApprovalKind.APPROVE_JOIN,
            completed = true,
            approvalsRequired = 0
        )

        assertTrue(done.completed)
        assertFalse(
            "a completed group must not be shown as still waiting",
            done.isPartiallyApproved
        )
    }

    /**
     * The default is deliberately incomplete.
     *
     * Every failure mode here is "an approval arrived and the client assumed it was enough". A
     * default of completed = true would make that the behaviour of any code path that forgets to
     * populate the field, which is exactly the path that would ship.
     */
    @Test
    fun `an approval with no stated outcome is treated as not complete`() {
        val unknown = ApprovalOutcome()

        assertFalse(unknown.completed)
        assertFalse(
            "an unknown outcome must not claim to be waiting for a specific count either",
            unknown.isPartiallyApproved
        )
    }

    // --- a request is not membership -----------------------------------------------------------

    @Test
    fun `a join request reports how many people must agree, and at least one`() {
        val twoPeople = JoinRequestOutcome(
            householdId = "hh-1",
            householdName = "Mum and Dad",
            reportedApprovals = 2
        )
        assertEquals(2, twoPeople.approvalsRequired)

        // Zero, or a negative, would render as a request nobody has to agree to, which is precisely
        // the bypass that was closed. The invariant lives in the model so no construction path,
        // including a malformed server response, can produce a self-approved looking request.
        assertEquals(1, JoinRequestOutcome(reportedApprovals = 0).approvalsRequired)
        assertEquals(1, JoinRequestOutcome(reportedApprovals = -3).approvalsRequired)
    }

    // --- a requester sees state, never the code -------------------------------------------------

    @Test
    fun `a requester can see their own request state`() {
        val request = MyRemoteRequest(
            id = "cmd-1",
            kind = ApprovalKind.APPROVE_JOIN,
            status = org.homeguard.core.model.ApprovalStatus.PENDING,
            householdName = "Mum and Dad"
        )

        assertTrue(request.isPending)
        assertEquals("Mum and Dad", request.householdName)
    }

    /**
     * The request model has no code field at all.
     *
     * Migration 017 removed `requested_by` from the SELECT policy precisely because PostgREST returns
     * whole rows, so a requester could read the code minted for them. `list_my_remote_commands` then
     * restored the visibility without the code. If this type ever grows an approvalCode property, the
     * leak is back and it will be reachable from the dashboard.
     */
    @Test
    fun `the requester model carries no approval code`() {
        val fields = MyRemoteRequest(id = "cmd-1").javaClass.declaredFields.map { it.name }
        assertFalse(
            "a requester-facing model must never carry an approval code, found: $fields",
            fields.any { it.contains("code", ignoreCase = true) }
        )
    }

    // --- the alarm must never be treated as a consent request -------------------------------------

    @Test
    fun `an alarm kind is not a consent kind`() {
        assertEquals(
            "an alarm must not parse as something a resident consents to",
            null,
            ApprovalKind.fromWire("RAISE_ALARM")
        )
    }

    // --- the client must not write membership itself ---------------------------------------------

    /**
     * The direct insert is the hole Phase 3 left open, and it was reachable from the UI.
     *
     * Asserted against the source rather than at runtime because the call is a plain string path and
     * a unit test cannot observe a PostgREST rejection without a network. What matters is that no
     * code path can reach `POST household_members` any more, because the database refuses it.
     */
    @Test
    fun `no client code writes membership directly any more`() {
        val source = monitoringDataSourceSource().readText()

        assertFalse(
            "the client must not POST to household_members; membership changes go through " +
                "approve_remote_command",
            Regex("\"POST\"\\s*,\\s*\"household_members\"").containsMatchIn(source)
        )
        assertFalse(
            "and must not POST to remote_commands; requests go through the request functions",
            Regex("\"POST\"\\s*,\\s*\"remote_commands\"").containsMatchIn(source)
        )
    }

    /** Every request must go through one of the functions that take a real credential. */
    @Test
    fun `requests are filed through the credential taking functions`() {
        val source = monitoringDataSourceSource().readText()

        listOf(
            "rpc/request_household_join",
            "rpc/request_household_leave",
            "rpc/request_mode_switch",
            "rpc/approve_remote_command",
            "rpc/list_my_remote_commands"
        ).forEach { function ->
            assertTrue(
                "the client must call $function",
                source.contains(function)
            )
        }
    }

    /**
 * A requester reads their own request state through a function; a target reads the raw table.
 *
 * Both are correct and they are different directions. A target must be able to SELECT
 * remote_commands, because the code they have to read out lives there and the read policy allows
 * exactly the target. A requester must not, because that row also contains the code.
 *
 * So the property worth pinning is that both paths exist and that only the target path touches the
 * table. Asserting "the client never reads remote_commands" would have been wrong, and would have
 * pushed someone into reimplementing the code display through a function for no reason.
 */
@Test
fun `the target can read requests, the requester reads them through a function`() {
    val source = monitoringDataSourceSource().readText()

    assertTrue(
        "the target must be able to read pending requests to display their codes",
        source.contains("\"GET\"") && source.contains("\"remote_commands?\$statusFilter")
    )
    assertTrue(
        "the requester reads their own requests through list_my_remote_commands, which omits the code",
        source.contains("rpc/list_my_remote_commands")
    )
}

    // --- codes still behave -----------------------------------------------------------------------

    @Test
    fun `approval codes are still six characters and still ambiguous-free`() {
        assertTrue(ApprovalPolicy.isComplete(ApprovalPolicy.normaliseCode("abc234")))
        assertFalse(ApprovalPolicy.normaliseCode("0O1I").isNotEmpty())
        assertEquals(6, ApprovalPolicy.CODE_LENGTH)
    }

    private fun monitoringDataSourceSource(): File {
        var dir: File? = File(".").absoluteFile
        while (dir?.parentFile != null) {
            val candidate = File(
                dir,
                "app/src/main/java/org/homeguard/core/data/MonitoringRemoteDataSource.kt"
            )
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        throw AssertionError(
            "Could not locate MonitoringRemoteDataSource.kt above ${File(".").absolutePath}"
        )
    }
}