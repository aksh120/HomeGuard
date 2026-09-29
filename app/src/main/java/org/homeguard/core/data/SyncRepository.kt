package org.homeguard.core.data

import android.util.Log

class SyncRepository(
    private val eventRepository: EventRepository,
    private val remoteSyncDataSource: RemoteSyncDataSource
) {

    suspend fun syncPendingEvents(): Result<Int> {
        return try {
            val pendingEvents = eventRepository.getPendingEvents()
            if (pendingEvents.isEmpty()) {
                return Result.success(0)
            }

            var syncedCount = 0
            for (event in pendingEvents) {
                val result = remoteSyncDataSource.sendEvent(event)
                if (result.isSuccess) {
                    eventRepository.markEventSynced(event.id)
                    syncedCount++
                } else {
                    eventRepository.markEventFailed(event.id)
                    Log.w("SyncRepository", "Event ${event.id} sync failed: ${result.exceptionOrNull()?.message}")
                }
            }
            Result.success(syncedCount)
        } catch (e: Exception) {
            Log.e("SyncRepository", "Exception during sync: ${e.message}")
            Result.failure(e)
        }
    }
}
