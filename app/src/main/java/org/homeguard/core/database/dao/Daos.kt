package org.homeguard.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.PendingEventEntity

@Dao
interface PendingEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PendingEventEntity): Long

    @Update
    suspend fun update(event: PendingEventEntity)

    @Query("DELETE FROM pending_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM pending_events WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingEvents(): List<PendingEventEntity>

    @Query("SELECT COUNT(*) FROM pending_events WHERE syncStatus = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM pending_events WHERE id = :id")
    suspend fun getById(id: String): PendingEventEntity?
}

@Dao
interface EventAuditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audit: EventAuditEntity)

    @Query("SELECT * FROM event_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentEventsFlow(): Flow<List<EventAuditEntity>>

    @Query("SELECT * FROM event_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<EventAuditEntity>

    @Query("DELETE FROM event_history")
    suspend fun clearHistory()
}
