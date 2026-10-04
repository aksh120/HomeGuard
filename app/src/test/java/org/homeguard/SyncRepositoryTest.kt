package org.homeguard

import kotlinx.coroutines.test.runTest
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.RemoteNotConfiguredException
import org.homeguard.core.data.RemoteRateLimitedException
import org.homeguard.core.data.RemoteSyncDataSource
import org.homeguard.core.data.SyncIncompleteException
import org.homeguard.core.data.SyncRepository
import org.homeguard.core.database.dao.PendingEventDao
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.model.EventSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Scriptable transport. Defaults to success; each test overrides with the failure it needs. */
class FakeRemoteSyncDataSource : RemoteSyncDataSource {
    var behaviour: (PendingEventEntity) -> Result<Unit> = { Result.success(Unit) }
    val sentIds = mutableListOf<String>()

    override suspend fun sendEvent(event: PendingEventEntity): Result<Unit> {
        sentIds += event.id
        return behaviour(event)
    }

    override suspend fun sendHeartbeat(
        deviceId: String,
        batteryLevel: Int,
        isCharging: Boolean
    ): Result<Unit> = Result.success(Unit)
}

class SyncRepositoryTest {

    private lateinit var pendingDao: FakePendingEventDao
    private lateinit var auditDao: FakeEventAuditDao
    private lateinit var eventRepository: EventRepository
    private lateinit var remote: FakeRemoteSyncDataSource
    private lateinit var syncRepository: SyncRepository

    @Before
    fun setUp() {
        pendingDao = FakePendingEventDao()
        auditDao = FakeEventAuditDao()
        eventRepository = EventRepository(pendingDao, auditDao)
        remote = FakeRemoteSyncDataSource()
        syncRepository = SyncRepository(eventRepository, remote)
    }

    @Test
    fun `successful sync drains the queue`() = runTest {
        eventRepository.enqueueCheckIn("device-1")
        eventRepository.enqueueCheckIn("device-1")

        val result = syncRepository.syncPendingEvents()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow())
        assertTrue(pendingDao.events.isEmpty())
        // Audit history is retained even after delivery.
        assertEquals(2, auditDao.audits.size)
    }

    @Test
    fun `nothing queued reports success with zero synced`() = runTest {
        val result = syncRepository.syncPendingEvents()

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow())
        assertTrue(remote.sentIds.isEmpty())
    }

    /**
     * P0-2: a transport failure must not be reported as a completed sync, or WorkManager never
     * asks for a retry and the event is silently abandoned.
     */
    @Test
    fun `transport failure surfaces as SyncIncompleteException`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(IllegalStateException("network down")) }

        val result = syncRepository.syncPendingEvents()

        assertFalse(result.isSuccess)
        val failure = result.exceptionOrNull()
        assertTrue(failure is SyncIncompleteException)
        assertEquals(1, (failure as SyncIncompleteException).failedCount)

        // The event must still exist, marked FAILED, so a later pass can pick it up.
        val stored = pendingDao.events[eventId]
        assertNotNull(stored)
        assertEquals(EventSyncStatus.FAILED.name, stored!!.syncStatus)
        assertEquals(1, stored.retryCount)
    }

    @Test
    fun `failed event is picked up again on the next pass and delivered`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(IllegalStateException("transient")) }

        syncRepository.syncPendingEvents()
        assertEquals(EventSyncStatus.FAILED.name, pendingDao.events[eventId]?.syncStatus)

        // Network recovers.
        remote.behaviour = { Result.success(Unit) }
        val second = syncRepository.syncPendingEvents()

        assertTrue(second.isSuccess)
        assertEquals(1, second.getOrThrow())
        assertTrue(pendingDao.events.isEmpty())
        assertEquals(listOf(eventId, eventId), remote.sentIds)
    }

    @Test
    fun `partial failure reports failure while still delivering the rest`() = runTest {
        val good = eventRepository.enqueueCheckIn("device-1")
        val bad = eventRepository.enqueueCheckIn("device-1")
        remote.behaviour = { event ->
            if (event.id == bad) Result.failure(IllegalStateException("rejected"))
            else Result.success(Unit)
        }

        val result = syncRepository.syncPendingEvents()

        assertFalse(result.isSuccess)
        assertFalse(pendingDao.events.containsKey(good))
        assertTrue(pendingDao.events.containsKey(bad))
        assertEquals(1, (result.exceptionOrNull() as SyncIncompleteException).failedCount)
    }

    /**
     * P0-3: blank credentials previously returned success, which made markEventSynced delete the
     * row. An unconfigured build must leave events queued.
     */
    @Test
    fun `unconfigured remote keeps events queued and does not report success`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(RemoteNotConfiguredException()) }

        val result = syncRepository.syncPendingEvents()

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is RemoteNotConfiguredException)
        assertTrue(pendingDao.events.containsKey(eventId))
        // Retry count must not advance just because nothing is configured.
        assertEquals(0, pendingDao.events[eventId]?.retryCount)
    }

    @Test
    fun `event is abandoned only after exhausting retries`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(IllegalStateException("permanent")) }

        repeat(PendingEventDao.MAX_RETRIES) {
            syncRepository.syncPendingEvents()
        }

        // Further passes no longer select it.
        remote.sentIds.clear()
        syncRepository.syncPendingEvents()

        assertTrue(remote.sentIds.isEmpty())
        assertEquals(
            PendingEventDao.MAX_RETRIES,
            pendingDao.events[eventId]?.retryCount
        )
    }

    /**
     * Phase 0 / D-11: a rate limit says nothing about the event, so it must not spend a retry.
     *
     * This is the defect that stranded 23 queued safety events permanently. Because every failure
     * advanced retryCount, ten throttled syncs pushed real emergencies past MAX_RETRIES, from where
     * getPendingEvents never selects them again and only a reinstall cleared the queue.
     */
    @Test
    fun `rate limiting does not consume an event's retries`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(RemoteRateLimitedException(retryAfterSeconds = 30)) }

        repeat(PendingEventDao.MAX_RETRIES * 2) {
            val result = syncRepository.syncPendingEvents()
            assertTrue(result.exceptionOrNull() is RemoteRateLimitedException)
        }

        val stored = pendingDao.events[eventId]
        assertNotNull("the event must still be queued", stored)
        assertEquals(0, stored!!.retryCount)
        assertEquals(EventSyncStatus.PENDING.name, stored.syncStatus)
    }

    /** The limit clears and the emergency goes out with its retry budget fully intact. */
    @Test
    fun `event survives repeated rate limiting and delivers once the limit clears`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(RemoteRateLimitedException()) }

        repeat(5) { syncRepository.syncPendingEvents() }

        remote.behaviour = { Result.success(Unit) }
        remote.sentIds.clear()
        val result = syncRepository.syncPendingEvents()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
        assertFalse(pendingDao.events.containsKey(eventId))
        assertEquals(listOf(eventId), remote.sentIds)
    }

    /**
     * Phase 0 repair: events already stranded at the ceiling must be recoverable, because otherwise
     * the existing 23 stranded rows stay undeliverable forever.
     */
    @Test
    fun `stranded events can be re-armed and then delivered`() = runTest {
        val eventId = eventRepository.enqueueEmergencySos("device-1")
        remote.behaviour = { Result.failure(IllegalStateException("permanent")) }
        repeat(PendingEventDao.MAX_RETRIES) { syncRepository.syncPendingEvents() }

        // Exhausted: invisible to the queue.
        assertEquals(1, eventRepository.countExhaustedEvents())
        remote.sentIds.clear()
        syncRepository.syncPendingEvents()
        assertTrue(remote.sentIds.isEmpty())

        val released = eventRepository.resetExhaustedEvents()
        assertEquals(1, released)
        assertEquals(0, eventRepository.countExhaustedEvents())

        remote.behaviour = { Result.success(Unit) }
        assertTrue(syncRepository.syncPendingEvents().isSuccess)
        assertEquals(listOf(eventId), remote.sentIds)
    }

    @Test
    fun `re-arming reports zero when nothing was stranded`() = runTest {
        eventRepository.enqueueCheckIn("device-1")

        assertEquals(0, eventRepository.resetExhaustedEvents())
        assertEquals(0, eventRepository.countExhaustedEvents())
    }

    @Test
    fun `duplicate event id is not delivered twice within a pass`() = runTest {
        val eventId = eventRepository.enqueueCheckIn("device-1")
        val clone = pendingDao.events.getValue(eventId).copy()
        pendingDao.insert(clone)

        syncRepository.syncPendingEvents()

        assertEquals(listOf(eventId), remote.sentIds)
    }
}