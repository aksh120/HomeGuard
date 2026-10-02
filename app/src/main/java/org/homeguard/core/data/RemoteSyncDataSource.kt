package org.homeguard.core.data

import org.homeguard.core.database.entity.PendingEventEntity

/**
 * Transport for queued events.
 *
 * Only SupabaseRemoteSyncDataSource implements this. The former DefaultRemoteSyncDataSource was a
 * simulated no-op that returned success without sending anything, which caused queued events to
 * be deleted as if delivered.
 */
interface RemoteSyncDataSource {
    suspend fun sendEvent(event: PendingEventEntity): Result<Unit>

    suspend fun sendHeartbeat(
        deviceId: String,
        batteryLevel: Int,
        isCharging: Boolean
    ): Result<Unit>
}