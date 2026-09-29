package org.homeguard.core.data

import kotlinx.coroutines.flow.Flow
import org.homeguard.core.database.dao.EventAuditDao
import org.homeguard.core.database.dao.PendingEventDao
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.model.EventSyncStatus
import org.homeguard.core.model.EventType
import java.util.UUID

class EventRepository(
    private val pendingEventDao: PendingEventDao,
    private val eventAuditDao: EventAuditDao
) {

    val pendingCountFlow: Flow<Int> = pendingEventDao.getPendingCountFlow()
    val recentAuditsFlow: Flow<List<EventAuditEntity>> = eventAuditDao.getRecentEventsFlow()

    suspend fun enqueueEmergencySos(deviceId: String, details: String? = null): String {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val payload = """{"deviceId":"$deviceId","timestamp":$timestamp,"type":"EMERGENCY_SOS","details":"${details ?: ""}"}"""

        val entity = PendingEventEntity(
            id = eventId,
            eventType = EventType.EMERGENCY_SOS.name,
            timestamp = timestamp,
            payloadJson = payload,
            syncStatus = EventSyncStatus.PENDING.name,
            retryCount = 0
        )
        pendingEventDao.insert(entity)

        eventAuditDao.insert(
            EventAuditEntity(
                id = UUID.randomUUID().toString(),
                eventType = EventType.EMERGENCY_SOS.name,
                timestamp = timestamp,
                summary = "Emergency SOS Triggered",
                details = details
            )
        )
        return eventId
    }

    suspend fun enqueueCheckIn(deviceId: String): String {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val payload = """{"deviceId":"$deviceId","timestamp":$timestamp,"type":"CHECK_IN"}"""

        val entity = PendingEventEntity(
            id = eventId,
            eventType = EventType.CHECK_IN.name,
            timestamp = timestamp,
            payloadJson = payload,
            syncStatus = EventSyncStatus.PENDING.name,
            retryCount = 0
        )
        pendingEventDao.insert(entity)

        eventAuditDao.insert(
            EventAuditEntity(
                id = UUID.randomUUID().toString(),
                eventType = EventType.CHECK_IN.name,
                timestamp = timestamp,
                summary = "Safety Check-In Completed",
                details = "User confirmed: I'm OK"
            )
        )
        return eventId
    }

    suspend fun recordAudit(eventType: EventType, summary: String, details: String? = null) {
        eventAuditDao.insert(
            EventAuditEntity(
                id = UUID.randomUUID().toString(),
                eventType = eventType.name,
                timestamp = System.currentTimeMillis(),
                summary = summary,
                details = details
            )
        )
    }

    suspend fun getPendingEvents(): List<PendingEventEntity> {
        return pendingEventDao.getPendingEvents()
    }

    suspend fun markEventSynced(id: String) {
        pendingEventDao.deleteById(id)
    }

    suspend fun markEventFailed(id: String) {
        val existing = pendingEventDao.getById(id)
        if (existing != null) {
            pendingEventDao.update(
                existing.copy(
                    syncStatus = EventSyncStatus.FAILED.name,
                    retryCount = existing.retryCount + 1
                )
            )
        }
    }
}
