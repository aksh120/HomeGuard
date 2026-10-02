package org.homeguard.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.homeguard.HomeGuardApplication
import org.homeguard.R

/**
 * Posts the notifications the README promises. Previously three channels were created and nothing
 * ever called notify().
 *
 * Every path here is a no-op when POST_NOTIFICATIONS is not granted (API 33+), so callers do not
 * need to gate on permission themselves.
 */
class SafetyNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    /** Reassures the user that an SOS triggered while offline is safely stored and will send. */
    fun notifyEmergencyQueuedOffline() {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_EMERGENCY)
            .setSmallIcon(R.drawable.ic_stat_homeguard)
            .setContentTitle(context.getString(R.string.notif_emergency_queued_title))
            .setContentText(context.getString(R.string.notif_emergency_queued_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notify(SAVED_EMERGENCY_ID, notification)
    }

    fun notifyCheckInRecorded() {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_stat_homeguard)
            .setContentTitle(context.getString(R.string.notif_checkin_title))
            .setContentText(context.getString(R.string.notif_checkin_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notify(CHECKIN_ID, notification)
    }

    fun notifyBatteryLow(level: Int) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_TELEMETRY)
            .setSmallIcon(R.drawable.ic_stat_homeguard)
            .setContentTitle(context.getString(R.string.battery_low_warning))
            .setContentText(context.getString(R.string.battery_low_body, level))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        notify(BATTERY_LOW_ID, notification)
    }

    fun notifyBatteryCritical(level: Int) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_TELEMETRY)
            .setSmallIcon(R.drawable.ic_stat_homeguard)
            .setContentTitle(context.getString(R.string.battery_critical_warning))
            .setContentText(context.getString(R.string.battery_critical_body, level))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notify(BATTERY_CRITICAL_ID, notification)
    }

    /** Clears the battery alerts once charging resumes so a stale warning cannot persist. */
    fun clearBatteryAlerts() {
        if (!canPost()) return
        manager.cancel(BATTERY_LOW_ID)
        manager.cancel(BATTERY_CRITICAL_ID)
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the post; nothing to do.
        }
    }

    companion object {
        const val SAVED_EMERGENCY_ID = 1001
        const val CHECKIN_ID = 1002
        const val BATTERY_LOW_ID = 1003
        const val BATTERY_CRITICAL_ID = 1004
    }
}