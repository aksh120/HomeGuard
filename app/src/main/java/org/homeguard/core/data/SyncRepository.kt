package org.homeguard.core.data

import android.util.Log

class SyncRepository(
    private val eventRepository: EventRepository,
    private val remoteSyncDataSource: RemoteSyncDataSource
) {

    /**
     * Returns [Result.failure] when any event failed to deliver, so the caller can ask WorkManager
     * to retry. Previously this reported success unconditionally, which meant a total network
     * outage looked like a completed sync.
     *
     * Transport-level failures deliberately do NOT consume an event's retry budget. Retries exist to
     * stop an undeliverable event blocking the queue forever; an event whose payload is fine but
     * which could not be sent because the server was throttling us has done nothing wrong. Burning
     * retries there is what stranded 23 queued safety events at MAX_RETRIES, permanently, until the
     * app was reinstalled.
     */
    suspend fun syncPendingEvents(): Result<Int> {
        return try {
            val pendingEvents = eventRepository.getPendingEvents()
            if (pendingEvents.isEmpty()) {
                return Result.success(0)
            }

            var syncedCount = 0
            var failedCount = 0
            for (event in pendingEvents) {
                val result = remoteSyncDataSource.sendEvent(event)
                val failure = result.exceptionOrNull()
                if (failure != null && failure.isTransientTransportFailure) {
                    // Could not reach the server, so nothing can be sent. Keep every event queued and
                    // stop; continuing would fail identically for each one.
                    return Result.failure(failure)
                }
                if (result.isSuccess) {
                    eventRepository.markEventSynced(event.id)
                    syncedCount++
                } else {
                    eventRepository.markEventFailed(event.id)
                    failedCount++
                    Log.w("SyncRepository", "Event ${event.id} sync failed: ${failure?.message}")
                }
            }

            if (failedCount > 0) {
                Result.failure(SyncIncompleteException(failedCount))
            } else {
                Result.success(syncedCount)
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Exception during sync: ${e.message}")
            Result.failure(e)
        }
    }
}