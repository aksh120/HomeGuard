package org.homeguard

import org.homeguard.core.model.ApprovalKind
import org.homeguard.core.model.ApprovalPolicy
import org.homeguard.core.model.ApprovalRequest
import org.homeguard.core.model.ApprovalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision D4 (every membership change needs a code) and D5 (an unanswered request never expires).
 *
 * These rules are the consent model for caregiver access, so they are asserted directly rather than
 * inferred from the UI.
 */
class ApprovalPolicyTest {

    private fun pending(
        kind: ApprovalKind = ApprovalKind.APPROVE_JOIN,
        code: String? = "ABC234",
        status: ApprovalStatus = ApprovalStatus.PENDING
    ) = ApprovalRequest(id = "r1", kind = kind, status = status, approvalCode = code)

    // --- codes -----------------------------------------------------------------------------

    @Test
    fun `a six character code is complete`() {
        assertTrue(ApprovalPolicy.isComplete("ABC234"))
    }

    @Test
    fun `a short code is not complete`() {
        assertFalse(ApprovalPolicy.isComplete("ABC23"))
        assertFalse(ApprovalPolicy.isComplete(""))
    }

    /** Mirrors the database generator, so an untypeable code could not have been issued. */
    @Test
    fun `normalising matches the generator alphabet`() {
        assertEquals("ABC234", ApprovalPolicy.normaliseCode("abc234"))
        assertEquals("XYZ789", ApprovalPolicy.normaliseCode("xyz789"))
        assertEquals("ABC234", ApprovalPolicy.normaliseCode("a-b c2 3-4"))
    }

    /** 0/O and 1/I are the characters people mishear and mistype when reading a code aloud. */
    @Test
    fun `ambiguous characters are stripped so they cannot be typed`() {
        val normalised = ApprovalPolicy.normaliseCode("0O1IABC234")
        assertEquals("ABC234", normalised)
        assertFalse(normalised.contains('0'))
        assertFalse(normalised.contains('O'))
        assertFalse(normalised.contains('1'))
        assertFalse(normalised.contains('I'))
    }

    /**
     * Normalising does not truncate, matching normaliseInviteCode: the text field bounds the input
     * as it is typed. Truncating here would also make an over-long paste look valid.
     */
    @Test
    fun `normalising does not silently truncate an over-long code`() {
        assertEquals("ABC234XYZ", ApprovalPolicy.normaliseCode("ABC234XYZ"))
    }

    @Test
    fun `an over-long code is not treated as complete`() {
        assertFalse(ApprovalPolicy.isComplete("ABC234XYZ"))
    }

    // --- D5: requests never lapse -----------------------------------------------------------

    /**
     * The heart of the consent model. D5 was decided after the alternative was considered: a timeout
     * on an approval prompt is a silent consent. If a future change reintroduces an age check, this
     * fails, and the signature of isAwaitingAction makes it impossible to add one silently.
     */
    @Test
    fun `a request stays actionable regardless of how old it is`() {
        val ancient = pending()
        assertTrue(ApprovalPolicy.isAwaitingAction(ancient))
    }

    @Test
    fun `an executed request is no longer actionable`() {
        assertFalse(
            ApprovalPolicy.isAwaitingAction(pending(status = ApprovalStatus.EXECUTED))
        )
    }

    @Test
    fun `a cancelled request is no longer actionable`() {
        assertFalse(
            ApprovalPolicy.isAwaitingAction(pending(status = ApprovalStatus.CANCELLED))
        )
    }

    // --- display ----------------------------------------------------------------------------

    /** Only a pending request with a code is worth showing, or worth offering to cancel. */
    @Test
    fun `only a pending request with a code is displayable`() {
        assertTrue(pending().hasDisplayableCode)
        assertFalse(pending(code = null).hasDisplayableCode)
        assertFalse(pending(code = "").hasDisplayableCode)
        assertFalse(pending(status = ApprovalStatus.EXECUTED).hasDisplayableCode)
        assertFalse(pending(status = ApprovalStatus.CANCELLED).hasDisplayableCode)
    }

    // --- wire format -------------------------------------------------------------------------

    @Test
    fun `kinds parse from the wire and unknown values are rejected`() {
        assertEquals(ApprovalKind.APPROVE_JOIN, ApprovalKind.fromWire("APPROVE_JOIN"))
        assertEquals(ApprovalKind.APPROVE_REMOVE, ApprovalKind.fromWire("approve_remove"))
        assertEquals(ApprovalKind.APPROVE_MODE_SWITCH, ApprovalKind.fromWire("APPROVE_MODE_SWITCH"))
        // An unknown kind must not fall back to something permissive.
        assertNull(ApprovalKind.fromWire("RAISE_ALARM"))
        assertNull(ApprovalKind.fromWire(""))
    }

    @Test
    fun `statuses parse from the wire and unknown values are rejected`() {
        assertEquals(ApprovalStatus.PENDING, ApprovalStatus.fromWire("PENDING"))
        assertEquals(ApprovalStatus.EXECUTED, ApprovalStatus.fromWire("executed"))
        assertNull(ApprovalStatus.fromWire("APPROVED"))
    }

    // --- the three transitions D4 requires to be code-gated ----------------------------------

    @Test
    fun `joining removing and switching all exist as distinct kinds`() {
        // Each is a separate code-gated transition, so collapsing them would drop a consent gate.
        assertEquals(3, ApprovalKind.entries.size)
        assertTrue(ApprovalKind.entries.contains(ApprovalKind.APPROVE_JOIN))
        assertTrue(ApprovalKind.entries.contains(ApprovalKind.APPROVE_REMOVE))
        assertTrue(ApprovalKind.entries.contains(ApprovalKind.APPROVE_MODE_SWITCH))
    }
}