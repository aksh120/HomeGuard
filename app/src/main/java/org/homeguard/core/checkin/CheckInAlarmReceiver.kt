package org.homeguard.core.checkin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.homeguard.HomeGuardApplication
import org.homeguard.MainActivity
import org.homeguard.R
import org.homeguard.core.datastore.PreferencesManager

/**
 * Raised when a scheduled check-in time arrives.
 *
 * Posts a high-priority notification that opens the app on the safety-check prompt, then
 * re-arms the alarm for the next configured time. Re-arming here rather than from the UI means the
 * schedule survives the app being closed between prompts.
 */
class CheckInAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CheckInScheduler.ACTION_CHECK_IN_DUE) return

        Log.i("CheckInAlarmReceiver", "Scheduled safety check is due.")

        val appContext = context.applicationContext
        createChannel(appContext)
        postPromptNotification(appContext)

        // Re-arm for the next configured time.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val preferences = PreferencesManager(appContext)
                val schedule = preferences.currentConfig().checkInSchedule
                CheckInScheduler(appContext).applySchedule(schedule)
            } catch (e: Exception) {
                Log.e("CheckInAlarmReceiver", "Could not re-arm check-in alarm: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postPromptNotification(context: Context) {
        if (!canPost(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CHECK_IN_PROMPT, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HomeGuardApplication.CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_stat_homeguard)
            .setContentTitle(context.getString(R.string.checkin_dialog_title))
            .setContentText(context.getString(R.string.checkin_dialog_message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked; the in-app prompt will still appear on next launch.
        }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(HomeGuardApplication.CHANNEL_CHECKIN) != null) return
        val channel = NotificationChannel(
            HomeGuardApplication.CHANNEL_CHECKIN,
            context.getString(R.string.channel_checkin_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_checkin_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val NOTIFICATION_ID = 2001
    }
}