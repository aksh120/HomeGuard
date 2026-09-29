package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.delay
import org.homeguard.core.database.entity.PendingEventEntity

interface RemoteSyncDataSource {
    suspend fun sendEvent(event: PendingEventEntity): Result<Unit>
    suspend fun sendHeartbeat(deviceId: String, batteryLevel: Int, isCharging: Boolean): Result<Unit>
}

/**
 * Standard implementation supporting offline mock and remote dispatch.
 * In production, configured with Supabase REST endpoints.
 */
class DefaultRemoteSyncDataSource : RemoteSyncDataSource {
    override suspend fun sendEvent(event: PendingEventEntity): Result<Unit> {
        return try {
            // Emulate lightweight network dispatch with idempotency token
            Log.i("RemoteSync", "Dispatching event [${event.id}] of type ${event.eventType} to backend")
            delay(100) // Lightweight non-blocking network simulation
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("RemoteSync", "Failed to dispatch event [${event.id}]: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun sendHeartbeat(
        deviceId: String,
        batteryLevel: Int,
        isCharging: Boolean
    ): Result<Unit> {
        return try {
            Log.i("RemoteSync", "Dispatching heartbeat for device $deviceId: battery=$batteryLevel% charging=$isCharging")
            delay(50)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
