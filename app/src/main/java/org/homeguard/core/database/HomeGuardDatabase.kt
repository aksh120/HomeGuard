package org.homeguard.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import org.homeguard.core.database.dao.EventAuditDao
import org.homeguard.core.database.dao.PendingEventDao
import org.homeguard.core.database.entity.EventAuditEntity
import org.homeguard.core.database.entity.PendingEventEntity

@Database(
    entities = [
        PendingEventEntity::class,
        EventAuditEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HomeGuardDatabase : RoomDatabase() {

    abstract fun pendingEventDao(): PendingEventDao
    abstract fun eventAuditDao(): EventAuditDao

    /**
     * Coordinates the two-table write behind an enqueue. Previously these were separate DAO calls,
     * so a crash between them left a queued event with no audit record.
     */
    @Transaction
    open suspend fun insertEventWithAudit(
        event: PendingEventEntity,
        audit: EventAuditEntity
    ) {
        pendingEventDao().insert(event)
        eventAuditDao().insert(audit)
    }

    companion object {
        @Volatile
        private var INSTANCE: HomeGuardDatabase? = null

        fun getInstance(context: Context): HomeGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HomeGuardDatabase::class.java,
                    "homeguard_database.db"
                ).fallbackToDestructiveMigration()
                 .build().also { INSTANCE = it }
            }
        }
    }
}