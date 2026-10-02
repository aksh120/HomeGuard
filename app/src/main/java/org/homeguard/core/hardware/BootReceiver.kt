package org.homeguard.core.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.homeguard.core.sync.SyncWorker

/**
 * Re-arms the safety schedule after a reboot or app update.
 *
 * WorkManager restores its own periodic work, but an explicit re-schedule makes the intent of
 * this receiver real rather than implied, and covers the app-update case where constraints or
 * intervals may have changed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            Log.i("BootReceiver", "Re-scheduling safety checks and event sync.")
            SyncWorker.schedulePeriodicSync(context.applicationContext)
        }
    }
}