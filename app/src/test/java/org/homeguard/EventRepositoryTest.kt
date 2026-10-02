package org.homeguard

import kotlinx.coroutines.test.runTest
import org.homeguard.core.data.EventRepository
import org.homeguard.core.model.EventSyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real EventRepository against in-memory DAOs. The previous version of this file
 * reimplemented the state machine inside the test body and asserted against its own copy.
 */
class EventRepositoryTest {

    private lateinit var pendingDao: FakePendingEventDao
    private lateinit var auditDao: FakeEventAuditDao
    private lateinit var repository: EventRepository

    @Before
    fun setUp() {
        pendingDao = FakePendingEventDao()
        auditDao = FakeEventAuditDao()
        repository = EventRepository(pendingDao, auditDao)
    }

    @Test
    fun `emergency SOS creates a pending event and an audit record`() = runTest {
        val eventId = repository.enqueueEmergencySos("device-1", "Panic button pressed")

        val pending = repository.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals(eventId, pending[0].id)
        assertEquals("EMERGENCY_SOS", pending[0].eventType)
        assertEquals(EventSyncStatus.PENDING.name, pending[0].syncStatus)

        assertEquals(1, auditDao.audits.size)
        assertEquals("Emergency SOS Triggered", auditDao.audits[0].summary)
    }

    @Test
    fun `check-in creates a pending event and an audit record`() = runTest {
        val eventId = repository.enqueueCheckIn("device-1")

        val pending = repository.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals(eventId, pending[0].id)
        assertEquals("CHECK_IN", pending[0].eventType)

        assertEquals(1, auditDao.audits.size)
        assertEquals("Safety Check-In Completed", auditDao.audits[0].summary)
    }

    /**
     * P1-1: details are serialised through JSONObject. A quote or backslash used to break the
     * hand-built JSON string, and the consumer silently substituted an empty object.
     */
    @Test
    fun `payload with quotes and backslashes stays parseable`() = runTest {
        val nasty = """He said "help" \ then fell — 50% <threshold> & more"""
        val eventId = repository.enqueueEmergencySos("device-1", nasty)

        val stored = pendingDao.events.getValue(eventId)
        val parsed = stored.payload()

        assertEquals("device-1", parsed.optString("deviceId"))
        assertEquals(nasty, parsed.optString("details"))
        assertEquals("EMERGENCY_SOS", parsed.optString("type"))
    }

    @Test
    fun `check-in payload carries the device id`() = runTest {
        val eventId = repository.enqueueCheckIn("device-42")

        val parsed = pendingDao.events.getValue(eventId).payload()
        assertEquals("device-42", parsed.optString("deviceId"))
        assertEquals("CHECK_IN", parsed.optString("type"))
        assertNotNull(parsed.optLong("timestamp"))
    }

    /**
     * P0-2: a FAILED event must remain selectable until it exhausts MAX_RETRIES. The production
     * query previously returned PENDING only, stranding the event after one failure.
     */
    @Test
    fun `failed event is retried until the ceiling then abandoned`() = runTest {
        val eventId = repository.enqueueEmergencySos("device-1")

        // Drive it past the retry ceiling.
        repeat(org.homeguard.core.database.dao.PendingEventDao.MAX_RETRIES) {
            assertTrue(
                "Event should still be retryable at attempt $it",
                repository.getPendingEvents().any { it.id == eventId }
            )
            repository.markEventFailed(eventId)
        }

        val exhausted = pendingDao.events.getValue(eventId)
        assertEquals(EventSyncStatus.FAILED.name, exhausted.syncStatus)

        assertTrue(
            "Event must not be retried once MAX_RETRIES is reached",
            repository.getPendingEvents().none { it.id == eventId }
        )

        // markEventFailed must not increment past the ceiling.
        repository.markEventFailed(eventId)
        assertEquals(
            org.homeguard.core.database.dao.PendingEventDao.MAX_RETRIES,
            pendingDao.events.getValue(eventId).retryCount
        )
    }

    @Test
    fun `synced event is removed from the queue`() = runTest {
        val eventId = repository.enqueueCheckIn("device-1")
        repository.markEventSynced(eventId)

        assertTrue(repository.getPendingEvents().isEmpty())
        // The audit trail is intentionally retained after delivery.
        assertEquals(1, auditDao.audits.size)
    }

    @Test
    fun `duplicate insert of the same id is ignored`() = runTest {
        val event = repository.enqueueCheckIn("device-1")
        val duplicate = pendingDao.events.getValue(event).copy()
        val result = pendingDao.insert(duplicate)

        assertEquals(-1L, result)
        assertEquals(1, pendingDao.events.size)
    }
}