package org.homeguard.core.model

/**
 * Last known state of the remote connection.
 *
 * Section 53 asks the user four questions when they open the app, and the fourth is "Is the device
 * working?". Answering it honestly requires distinguishing "nothing to send" from "the backend is
 * unreachable" — without this the app reports healthy while alerts quietly pile up.
 */
data class SyncHealth(
    val isRemoteConfigured: Boolean = false,
    val isAuthenticated: Boolean = false,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null
) {
    /**
     * Overall verdict, most severe condition first.
     *
     * `NEEDS_ATTENTION` covers the case that matters most: events are queued and the remote has
     * failed. That combination means an emergency may not reach anyone.
     */
    val status: SyncStatus
        get() = when {
            !isRemoteConfigured -> SyncStatus.NOT_CONFIGURED
            !isAuthenticated -> SyncStatus.NOT_AUTHENTICATED
            !lastError.isNullOrBlank() -> SyncStatus.ERROR
            lastSuccessAt != null -> SyncStatus.HEALTHY
            else -> SyncStatus.PENDING
        }
}

enum class SyncStatus {
    /** No Supabase credentials in this build. Local-only operation. */
    NOT_CONFIGURED,

    /** Credentials present, but no anonymous session has been established yet. */
    NOT_AUTHENTICATED,

    /** A sync attempt has been made but none has succeeded yet. */
    PENDING,

    /** Last attempt succeeded. */
    HEALTHY,

    /** The last attempt failed. Alerts may be queued. */
    ERROR
}