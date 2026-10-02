package org.homeguard.core.data

import kotlinx.coroutines.flow.Flow
import org.homeguard.core.database.HomeGuardDatabase
import org.homeguard.core.database.dao.EventAuditDao
import org.homeguard.core.database.dao.PendingEventDao
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.EventSummaries
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.model.EventSyncStatus
import org.homeguard.core.model.EventType
import org.json.JSONObject
import java.util.UUID

class EventRepository(
    private val pendingEventDao: PendingEventDao,
    private val eventAuditDao: EventAuditDao,
    private val database: HomeGuardDatabase? = null
) {

    val pendingCountFlow: Flow<Int> = pendingEventDao.getPendingCountFlow()
    val recentAuditsFlow: Flow<List<EventAuditEntity>> = eventAuditDao.getRecentEventsFlow()

    suspend fun enqueueEmergencySos(deviceId: String, details: String? = null): String {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("type", EventType.EMERGENCY_SOS.name)
            put("details", details ?: "")
        }.toString()

        val entity = PendingEventEntity(
            id = eventId,
            eventType = EventType.EMERGENCY_SOS.name,
            timestamp = timestamp,
            payloadJson = payload,
            syncStatus = EventSyncStatus.PENDING.name,
            retryCount = 0
        )

        val audit = EventAuditEntity(
            id = UUID.randomUUID().toString(),
            eventType = EventType.EMERGENCY_SOS.name,
            timestamp = timestamp,
            summary = EventSummaries.EMERGENCY_SOS,
            details = details
        )

        persistAtomically(entity, audit)
        return eventId
    }

    suspend fun enqueueCheckIn(deviceId: String): String {
        val eventId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", timestamp)
            put("type", EventType.CHECK_IN.name)
        }.toString()

        val entity = PendingEventEntity(
            id = eventId,
            eventType = EventType.CHECK_IN.name,
            timestamp = timestamp,
            payloadJson = payload,
            syncStatus = EventSyncStatus.PENDING.name,
            retryCount = 0
        )

        val audit = EventAuditEntity(
            id = UUID.randomUUID().toString(),
            eventType = EventType.CHECK_IN.name,
            timestamp = timestamp,
            summary = EventSummaries.CHECK_IN,
            details = "User confirmed: I'm OK"
        )

        persistAtomically(entity, audit)
        return eventId
    }

    private suspend fun persistAtomically(entity: PendingEventEntity, audit: EventAuditEntity) {
        val db = database
        if (db != null) {
            db.insertEventWithAudit(entity, audit)
        } else {
            pendingEventDao.insert(entity)
            eventAuditDao.insert(audit)
        }
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
        return pendingEventDao.getPendingEvents(PendingEventDao.MAX_RETRIES)
    }

    suspend fun markEventSynced(id: String) {
        pendingEventDao.deleteById(id)
    }

    suspend fun markEventFailed(id: String) {
        val existing = pendingEventDao.getById(id)
        if (existing != null && existing.retryCount < PendingEventDao.MAX_RETRIES) {
            pendingEventDao.update(
                existing.copy(
                    syncStatus = EventSyncStatus.FAILED.name,
                    retryCount = existing.retryCount + 1
                )
            )
        }
    }
}