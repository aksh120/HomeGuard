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

    /**
     * Includes retryable FAILED rows. Filtering on PENDING alone stranded every event after a
     * single transient network error, because [markEventFailed] had already flipped the status.
     */
    @Query(
        """
        SELECT * FROM pending_events
        WHERE syncStatus = 'PENDING'
           OR (syncStatus = 'FAILED' AND retryCount < :maxRetries)
        ORDER BY timestamp ASC
        """
    )
    suspend fun getPendingEvents(maxRetries: Int): List<PendingEventEntity>

    @Query("SELECT COUNT(*) FROM pending_events WHERE syncStatus = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM pending_events WHERE id = :id")
    suspend fun getById(id: String): PendingEventEntity?

    /**
     * Re-arms events stranded at the retry ceiling, returning how many were released.
     *
     * Rows at MAX_RETRIES are permanently unselectable by [getPendingEvents], so a genuine
     * emergency recorded during the sign-up rate-limit outage could never be delivered. Run once
     * per install rather than every sync: continuously re-arming rows that still cannot be sent
     * would convert a transient outage into an unbounded retry loop.
     */
    @Query(
        """
        UPDATE pending_events
        SET retryCount = 0, syncStatus = 'PENDING'
        WHERE syncStatus = 'FAILED' AND retryCount >= :maxRetries
        """
    )
    suspend fun resetExhaustedEvents(maxRetries: Int): Int

    @Query("SELECT COUNT(*) FROM pending_events WHERE syncStatus = 'FAILED' AND retryCount >= :maxRetries")
    suspend fun countExhaustedEvents(maxRetries: Int): Int

    companion object {
        const val MAX_RETRIES = 10
    }
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