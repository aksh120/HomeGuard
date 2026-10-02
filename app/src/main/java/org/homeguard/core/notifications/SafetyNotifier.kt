package org.homeguard.core.notifications

import android.Manifest
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
 * Resident-facing notifications.
 *
 * These alert the person holding the device. Alerting a *monitoring contact* is a separate,
 * server-side concern: see backend/ for the webhook that turns an emergency row into a message.
 * A local notification cannot reach someone else's phone.
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

    /** Reassures the user that an SOS triggered while offline is stored and will send. */
    fun notifyEmergencyQueuedOffline() {
        if (!canPost()) return
        post(
            SAVED_EMERGENCY_ID,
            NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_EMERGENCY)
                .setSmallIcon(R.drawable.ic_stat_homeguard)
                .setContentTitle(context.getString(R.string.notif_emergency_queued_title))
                .setContentText(context.getString(R.string.notif_emergency_queued_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
        )
    }

    fun notifyCheckInRecorded() {
        if (!canPost()) return
        post(
            CHECKIN_ID,
            NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_CHECKIN)
                .setSmallIcon(R.drawable.ic_stat_homeguard)
                .setContentTitle(context.getString(R.string.notif_checkin_title))
                .setContentText(context.getString(R.string.notif_checkin_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
        )
    }

    /**
     * A scheduled check-in window elapsed without a response.
     *
     * This never claims an emergency. Section 15 is explicit that a missed check-in downgrades
     * SAFE to UNKNOWN and only an explicit SOS creates EMERGENCY, so the wording stays factual.
     */
    fun notifyCheckInMissed() {
        if (!canPost()) return
        post(
            CHECKIN_MISSED_ID,
            NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_CHECKIN)
                .setSmallIcon(R.drawable.ic_stat_homeguard)
                .setContentTitle(context.getString(R.string.checkin_missed_notification_title))
                .setContentText(context.getString(R.string.checkin_missed_notification_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
        )
    }

    fun notifyBatteryLow(level: Int) {
        if (!canPost()) return
        post(
            BATTERY_LOW_ID,
            NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_TELEMETRY)
                .setSmallIcon(R.drawable.ic_stat_homeguard)
                .setContentTitle(context.getString(R.string.battery_low_warning))
                .setContentText(context.getString(R.string.battery_low_body, level))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
        )
    }

    fun notifyBatteryCritical(level: Int) {
        if (!canPost()) return
        post(
            BATTERY_CRITICAL_ID,
            NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_TELEMETRY)
                .setSmallIcon(R.drawable.ic_stat_homeguard)
                .setContentTitle(context.getString(R.string.battery_critical_warning))
                .setContentText(context.getString(R.string.battery_critical_body, level))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
        )
    }

    /** Clears the battery alerts once charging resumes so a stale warning cannot persist. */
    fun clearBatteryAlerts() {
        if (!canPost()) return
        manager.cancel(BATTERY_LOW_ID)
        manager.cancel(BATTERY_CRITICAL_ID)
    }

    private fun post(id: Int, notification: NotificationCompat.Builder) {
        try {
            manager.notify(id, notification.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and the post; nothing to do.
        }
    }

    companion object {
        const val SAVED_EMERGENCY_ID = 1001
        const val CHECKIN_ID = 1002
        const val BATTERY_LOW_ID = 1003
        const val BATTERY_CRITICAL_ID = 1004
        const val CHECKIN_MISSED_ID = 1005
    }
}