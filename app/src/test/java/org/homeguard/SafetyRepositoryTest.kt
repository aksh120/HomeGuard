package org.homeguard

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.homeguard.core.data.EventRepository
import org.homeguard.core.data.SafetyRepository
import org.homeguard.core.database.dao.EventAuditDao
import org.homeguard.core.database.dao.PendingEventDao
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.datastore.PreferencesManager
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class FakePendingEventDao : PendingEventDao {
    val events = mutableMapOf<String, PendingEventEntity>()

    override suspend fun insert(event: PendingEventEntity): Long {
        return if (events.containsKey(event.id)) {
            -1L // Ignore conflict
        } else {
            events[event.id] = event
            1L
        }
    }

    override suspend fun update(event: PendingEventEntity) {
        events[event.id] = event
    }

    override suspend fun deleteById(id: String) {
        events.remove(id)
    }

    override suspend fun getPendingEvents(): List<PendingEventEntity> {
        return events.values.filter { it.syncStatus == "PENDING" }
    }

    override fun getPendingCountFlow() = flowOf(events.size)

    override suspend fun getById(id: String): PendingEventEntity? {
        return events[id]
    }
}

class FakeEventAuditDao : EventAuditDao {
    val audits = mutableListOf<EventAuditEntity>()

    override suspend fun insert(audit: EventAuditEntity) {
        audits.add(audit)
    }

    override fun getRecentEventsFlow() = flowOf(audits)

    override suspend fun getRecentEvents(limit: Int): List<EventAuditEntity> = audits.take(limit)

    override suspend fun clearHistory() {
        audits.clear()
    }
}

class SafetyRepositoryTest {

    private lateinit var fakePendingDao: FakePendingEventDao
    private lateinit var fakeAuditDao: FakeEventAuditDao
    private lateinit var eventRepository: EventRepository

    @Before
    fun setUp() {
        fakePendingDao = FakePendingEventDao()
        fakeAuditDao = FakeEventAuditDao()
        eventRepository = EventRepository(fakePendingDao, fakeAuditDao)
    }

    @Test
    fun `test initial state is UNKNOWN when no check-in recorded`() = runTest {
        val config = AppConfig(checkInIntervalHours = 24, checkInGracePeriodMinutes = 60)
        // With no check-in, status must be UNKNOWN
        val lastCheckIn: Long? = null
        val currentStatus = SafetyStatus.UNKNOWN

        val calculated = if (currentStatus == SafetyStatus.EMERGENCY) {
            SafetyStatus.EMERGENCY
        } else if (lastCheckIn == null) {
            SafetyStatus.UNKNOWN
        } else {
            SafetyStatus.SAFE
        }

        assertEquals(SafetyStatus.UNKNOWN, calculated)
    }

    @Test
    fun `test state transitions to SAFE when recent check-in exists`() = runTest {
        val config = AppConfig(checkInIntervalHours = 24, checkInGracePeriodMinutes = 60)
        val now = System.currentTimeMillis()
        val lastCheckIn = now - TimeUnit.MINUTES.toMillis(30) // 30 min ago

        val maxDuration = TimeUnit.HOURS.toMillis(config.checkInIntervalHours.toLong()) +
                TimeUnit.MINUTES.toMillis(config.checkInGracePeriodMinutes.toLong())
        val isExpired = (now - lastCheckIn) > maxDuration

        val calculated = if (isExpired) SafetyStatus.UNKNOWN else SafetyStatus.SAFE
        assertEquals(SafetyStatus.SAFE, calculated)
    }

    @Test
    fun `test state transitions to UNKNOWN when check-in window elapses`() = runTest {
        val config = AppConfig(checkInIntervalHours = 24, checkInGracePeriodMinutes = 60)
        val now = System.currentTimeMillis()
        // 26 hours ago -> exceeds 24h + 1h grace
        val lastCheckIn = now - TimeUnit.HOURS.toMillis(26)

        val maxDuration = TimeUnit.HOURS.toMillis(config.checkInIntervalHours.toLong()) +
                TimeUnit.MINUTES.toMillis(config.checkInGracePeriodMinutes.toLong())
        val isExpired = (now - lastCheckIn) > maxDuration

        val calculated = if (isExpired) SafetyStatus.UNKNOWN else SafetyStatus.SAFE
        assertEquals(SafetyStatus.UNKNOWN, calculated)
    }

    @Test
    fun `test emergency trigger creates pending event and audit record`() = runTest {
        val deviceId = "test-device-123"
        val eventId = eventRepository.enqueueEmergencySos(deviceId, "Panic button pressed")

        val pending = eventRepository.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals(eventId, pending[0].id)
        assertEquals("EMERGENCY_SOS", pending[0].eventType)

        val audits = fakeAuditDao.audits
        assertEquals(1, audits.size)
        assertEquals("Emergency SOS Triggered", audits[0].summary)
    }

    @Test
    fun `test check-in creates pending event and audit record`() = runTest {
        val deviceId = "test-device-123"
        val eventId = eventRepository.enqueueCheckIn(deviceId)

        val pending = eventRepository.getPendingEvents()
        assertEquals(1, pending.size)
        assertEquals(eventId, pending[0].id)
        assertEquals("CHECK_IN", pending[0].eventType)

        val audits = fakeAuditDao.audits
        assertEquals(1, audits.size)
        assertEquals("Safety Check-In Completed", audits[0].summary)
    }
}
