package org.homeguard

import kotlinx.coroutines.flow.flowOf
import org.homeguard.core.database.dao.EventAuditDao
import org.homeguard.core.database.dao.PendingEventDao
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.model.EventSyncStatus
import org.json.JSONObject

/**
 * In-memory stand-in for PendingEventDao.
 *
 * Mirrors the production query: PENDING rows plus FAILED rows that have not exhausted
 * MAX_RETRIES. Returns null from getMaxRetriesConstraint when the DAO is driven by tests that
 * supply the limit explicitly.
 */
class FakePendingEventDao : PendingEventDao {
    val events = linkedMapOf<String, PendingEventEntity>()

    override suspend fun insert(event: PendingEventEntity): Long {
        return if (events.containsKey(event.id)) -1L else {
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

    override suspend fun getPendingEvents(maxRetries: Int): List<PendingEventEntity> {
        return events.values
            .filter {
                it.syncStatus == EventSyncStatus.PENDING.name ||
                    (it.syncStatus == EventSyncStatus.FAILED.name && it.retryCount < maxRetries)
            }
            .sortedBy { it.timestamp }
    }

    override fun getPendingCountFlow() = flowOf(events.size)

    override suspend fun getById(id: String): PendingEventEntity? = events[id]
}

class FakeEventAuditDao : EventAuditDao {
    val audits = mutableListOf<EventAuditEntity>()

    override suspend fun insert(audit: EventAuditEntity) {
        audits.removeAll { it.id == audit.id }
        audits.add(audit)
    }

    override fun getRecentEventsFlow() = flowOf(audits)

    override suspend fun getRecentEvents(limit: Int): List<EventAuditEntity> = audits.take(limit)

    override suspend fun clearHistory() {
        audits.clear()
    }
}

/** Parses a stored payload the way the real sync layer does. */
fun PendingEventEntity.payload(): JSONObject = JSONObject(payloadJson)