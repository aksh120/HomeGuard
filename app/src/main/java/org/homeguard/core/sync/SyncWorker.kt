package org.homeguard.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.util.Log
import org.homeguard.HomeGuardApplication
import org.homeguard.core.data.RemoteNotConfiguredException
import org.homeguard.core.data.RemoteRateLimitedException
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? HomeGuardApplication)?.container
            ?: return Result.failure()

        val remote = container.remoteSyncDataSource
        val preferences = container.preferencesManager

        return try {
            // Record the attempt first, so a crash mid-sync still leaves a timestamp behind.
            preferences.recordSyncAttempt(
                configured = remote.isConfigured,
                authenticated = remote.hasSession()
            )

            // Heartbeat first so a monitoring contact can see the device is alive even when
            // there is nothing queued.
            val deviceId = preferences.ensureDeviceId()
            val battery = container.batteryMonitor.batteryInfo.value
            val heartbeat = remote.sendHeartbeat(
                deviceId = deviceId,
                batteryLevel = battery.level,
                isCharging = battery.isCharging
            )
            if (heartbeat.isFailure && heartbeat.exceptionOrNull() !is RemoteNotConfiguredException) {
                Log.w("SyncWorker", "Heartbeat failed: ${heartbeat.exceptionOrNull()?.message}")
            }

            val result = container.syncRepository.syncPendingEvents()
            val failure = result.exceptionOrNull()

            if (result.isSuccess) {
                preferences.recordSyncSuccess()
                preferences.recordRateLimitCleared()
                recoverStrandedEvents(container, preferences)
                Result.success()
            } else if (failure is RemoteRateLimitedException) {
                // Transient. Events stay queued and none of them lost a retry, so this resolves
                // itself once the limit clears. Reporting it as "not signed in" would be wrong
                // advice, since there is nothing for the user to change.
                preferences.recordRateLimited(failure.retryAfterSeconds)
                Log.w("SyncWorker", "Supabase rate limited; events stay queued and will retry.")
                Result.success()
            } else if (failure is RemoteNotConfiguredException) {
                // Credentials or session missing. Keep events queued, and record why so the app
                // can tell the user their alerts are not reaching anyone.
                preferences.recordSyncError("Not signed in to Supabase")
                Log.w("SyncWorker", "Supabase not configured or not signed in; events stay queued.")
                // Do not retry in a tight loop: this will not fix itself without a config change.
                Result.success()
            } else {
                preferences.recordSyncError(failure?.message ?: "Sync failed")
                Result.retry()
            }
        } catch (e: Exception) {
            runCatching { preferences.recordSyncError(e.message ?: "Unexpected error") }
            Log.e("SyncWorker", "Sync failed: ${e.message}")
            Result.retry()
        }
    }

    /**
     * Releases events stranded at the retry ceiling, once per install.
     *
     * Runs only after a sync has actually succeeded, which proves the pipeline works again, so
     * re-arming cannot mask a continuing failure. An event that fails again simply climbs back to
     * the ceiling and stays put.
     */
    private suspend fun recoverStrandedEvents(
        container: org.homeguard.core.di.AppContainer,
        preferences: org.homeguard.core.datastore.PreferencesManager
    ) {
        try {
            if (preferences.hasRecoveredExhaustedEvents()) return
            val released = container.eventRepository.resetExhaustedEvents()
            preferences.markExhaustedEventsRecovered()
            if (released > 0) {
                Log.i("SyncWorker", "Re-armed $released event(s) stranded by the sign-up rate limit")
            }
        } catch (e: Exception) {
            // Never fail a sync because the repair could not run; it runs again next time.
            Log.w("SyncWorker", "Could not re-arm stranded events: ${e.message}")
        }
    }

    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "homeguard_periodic_sync"
        private const val IMMEDIATE_SYNC_WORK_NAME = "homeguard_immediate_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val immediateRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
        }
    }
}