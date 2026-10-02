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
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? HomeGuardApplication)?.container
            ?: return Result.failure()

        return try {
            // Heartbeat first so a monitoring contact can see the device is alive even when
            // there is nothing queued.
            val deviceId = container.preferencesManager.ensureDeviceId()
            val battery = container.batteryMonitor.batteryInfo.value
            val heartbeat = container.remoteSyncDataSource.sendHeartbeat(
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
                Result.success()
            } else if (failure is RemoteNotConfiguredException) {
                // Nothing to retry against; keep events queued and wait for configuration.
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
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