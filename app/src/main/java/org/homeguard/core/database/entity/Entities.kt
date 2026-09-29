package org.homeguard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.homeguard.core.model.EventSyncStatus
import org.homeguard.core.model.EventType

@Entity(tableName = "pending_events")
data class PendingEventEntity(
    @PrimaryKey
    val id: String,
    val eventType: String,
    val timestamp: Long,
    val payloadJson: String,
    val syncStatus: String = EventSyncStatus.PENDING.name,
    val retryCount: Int = 0
)

@Entity(tableName = "event_history")
data class EventAuditEntity(
    @PrimaryKey
    val id: String,
    val eventType: String,
    val timestamp: Long,
    val summary: String,
    val details: String? = null
)
