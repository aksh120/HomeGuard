package org.homeguard

import kotlinx.coroutines.test.runTest
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.RemoteSyncDataSource
import org.homeguard.core.data.SyncRepository
import org.homeguard.core.database.entity.PendingEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeRemoteSyncDataSource : RemoteSyncDataSource {
    var shouldFail = false
    val receivedEvents = mutableListOf<PendingEventEntity>()

    override suspend fun sendEvent(event: PendingEventEntity): Result<Unit> {
        return if (shouldFail) {
            Result.failure(Exception("Network offline or server timeout"))
        } else {
            receivedEvents.add(event)
            Result.success(Unit)
        }
    }

    override suspend fun sendHeartbeat(
        deviceId: String,
        batteryLevel: Int,
        isCharging: Boolean
    ): Result<Unit> {
        return if (shouldFail) Result.failure(Exception("Offline")) else Result.success(Unit)
    }
}

class SyncRepositoryTest {

    private lateinit var fakePendingDao: FakePendingEventDao
    private lateinit var fakeAuditDao: FakeEventAuditDao
    private lateinit var eventRepository: EventRepository
    private lateinit var fakeRemoteSource: FakeRemoteSyncDataSource
    private lateinit var syncRepository: SyncRepository

    @Before
    fun setUp() {
        fakePendingDao = FakePendingEventDao()
        fakeAuditDao = FakeEventAuditDao()
        eventRepository = EventRepository(fakePendingDao, fakeAuditDao)
        fakeRemoteSource = FakeRemoteSyncDataSource()
        syncRepository = SyncRepository(eventRepository, fakeRemoteSource)
    }

    @Test
    fun `test sync succeeds and clears pending queue when online`() = runTest {
        eventRepository.enqueueEmergencySos("dev-1", "SOS 1")
        eventRepository.enqueueCheckIn("dev-1")

        assertEquals(2, eventRepository.getPendingEvents().size)

        val result = syncRepository.syncPendingEvents()
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        // Verified queue is drained
        assertEquals(0, eventRepository.getPendingEvents().size)
        assertEquals(2, fakeRemoteSource.receivedEvents.size)
    }

    @Test
    fun `test sync fails gracefully and preserves events in queue when offline`() = runTest {
        eventRepository.enqueueEmergencySos("dev-1", "SOS offline")
        fakeRemoteSource.shouldFail = true

        val result = syncRepository.syncPendingEvents()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())

        // Event remains in pending queue with incremented retry count
        val pending = eventRepository.getPendingEvents()
        assertEquals(0, pending.size) // Marked failed in pending dao
        assertEquals(1, fakePendingDao.events.size)
        assertEquals(1, fakePendingDao.events.values.first().retryCount)
    }

    @Test
    fun `test duplicate event prevention with idempotent IDs`() = runTest {
        val eventId = "fixed-uuid-1234"
        val entity1 = PendingEventEntity(
            id = eventId,
            eventType = "EMERGENCY_SOS",
            timestamp = 1000L,
            payloadJson = "{}"
        )
        val entity2 = PendingEventEntity(
            id = eventId,
            eventType = "EMERGENCY_SOS",
            timestamp = 1001L,
            payloadJson = "{}"
        )

        fakePendingDao.insert(entity1)
        val insertResult2 = fakePendingDao.insert(entity2)

        assertEquals(-1L, insertResult2) // Duplicate rejected
        assertEquals(1, fakePendingDao.events.size)
    }
}
