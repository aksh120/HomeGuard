package org.homeguard.core.model

/**
 * A request that needs a code typed by a human before it takes effect.
 *
 * Created by one person, displayed on the protected phone, approved by another. The code is the whole
 * consent mechanism, so it is minted and checked by the database and is never generated or compared
 * on the client.
 */
enum class ApprovalKind {
    /** A caregiver asking to be allowed to watch this household. */
    APPROVE_JOIN,

    /** A caregiver asking to stop watching, using the same path as joining. */
    APPROVE_REMOVE,

    /** Someone asking to convert this device from resident to caregiver. */
    APPROVE_MODE_SWITCH;

    companion object {
        fun fromWire(raw: String): ApprovalKind? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** The state of an approval request as the device shows it. */
enum class ApprovalStatus {
    /** Awaiting a code. Never expires; see ApprovalPolicy. */
    PENDING,
    EXECUTED,
    CANCELLED;

    companion object {
        fun fromWire(raw: String): ApprovalStatus? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/**
 * One approval request, as the protected device sees it.
 *
 * [approvalCode] is present only because the target device has to display it. It is never sent to a
 * requester, and the RLS policies keep it away from anyone else in the household.
 */
data class ApprovalRequest(
    val id: String = "",
    val kind: ApprovalKind = ApprovalKind.APPROVE_JOIN,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val approvalCode: String? = null,
    val createdAt: String = "",
    val requesterName: String = "",
    val householdId: String? = null
) {
    val isPending: Boolean get() = status == ApprovalStatus.PENDING

    /** Only a pending request has a code worth showing, and therefore anything worth cancelling. */
    val hasDisplayableCode: Boolean get() = isPending && !approvalCode.isNullOrBlank()

    /** The verb the resident reads. Never implies a person already has access. */
    fun titleKey(): ApprovalKind = kind
}

/**
 * The rules governing approval codes.
 *
 * Pure and separated from the UI so the parts that must not go wrong are directly testable: codes
 * are unambiguous to retype, and a request never lapses on its own.
 */
object ApprovalPolicy {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 6

    /**
     * Uppercases and strips anything outside the alphabet.
     *
     * Mirrors the database generator exactly, so a code that cannot be typed could not have been
     * issued. 0, O, 1 and I are excluded because codes are read aloud or retyped from a message.
     */
    fun normaliseCode(input: String): String =
        input.trim().uppercase().filter { ALPHABET.contains(it) }

    /** True when a code could plausibly have been issued, used to gate the submit button. */
    fun isComplete(input: String): Boolean = normaliseCode(input).length == CODE_LENGTH

    /**
     * Whether a request is still awaiting action.
     *
     * There is deliberately no age parameter. Decision D5 is that an unanswered request never
     * expires, because a timeout on a consent prompt is a silent consent. If a caller finds a way to
     * pass a time into this function, the consent model has been broken.
     */
    fun isAwaitingAction(request: ApprovalRequest): Boolean = request.isPending
}