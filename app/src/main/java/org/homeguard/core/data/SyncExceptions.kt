package org.homeguard.core.data

/**
 * Raised when Supabase credentials are absent. Deliberately modelled as a failure rather than a
 * success: an unconfigured build must keep events queued on the device, never delete them.
 */
class RemoteNotConfiguredException : Exception("Supabase credentials not configured")

/** Raised when one or more queued events could not be delivered during a sync pass. */
class SyncIncompleteException(val failedCount: Int) :
    Exception("$failedCount event(s) failed to sync and remain queued")