package org.homeguard.core.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.homeguard.HomeGuardApplication
import org.homeguard.MainActivity
import org.homeguard.R
import org.homeguard.core.data.RemoteAlarmDataSource
import org.homeguard.core.di.AppContainer
import org.homeguard.core.hardware.EmergencyAlarmManager
import org.homeguard.core.hardware.TorchManager

/**
 * Sounds a caregiver-raised alarm on the resident's phone, even when the app is closed or the phone is
 * locked. Phase 5, decision D7.
 *
 * A foreground service, because nothing else will do:
 *
 *  - A `MediaPlayer` on the alarm stream is backgrounded within seconds of the app going to the
 *    background, and a locked screen does not keep the process alive.
 *  - Starting a foreground service from a plain background broadcast is restricted on Android 12+.
 *    Doing it from an FCM data message is the documented exception, and it is the reason the edge
 *    function sends this as data-only rather than as a notification payload.
 *
 * It completes the command when the resident stops it, so the caregiver's dashboard shows the alarm
 * reached the person instead of sitting pending forever.
 */
class RemoteAlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private var alarm: EmergencyAlarmManager? = null
    private var torch: TorchManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as HomeGuardApplication).container
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandId = intent?.getStringExtra(EXTRA_COMMAND_ID).orEmpty()

        // Checked before anything else. The Stop action arrives at this same service, and falling
        // through would start the very alarm the resident is trying to switch off.
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop requested for ${commandId.ifBlank { "the active alarm" }}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (commandId.isBlank()) {
            Log.w(TAG, "Started without a command id; stopping rather than alarming with no record")
            stopSelf()
            return START_NOT_STICKY
        }

        // A second start for the same alarm must not restart the siren: the resident has already been
        // shown this alarm and may have just dismissed it. Only a different command restarts.
        if (commandId == activeCommandId) {
            Log.i(TAG, "Already alarming for $commandId; ignoring duplicate start")
            return START_NOT_STICKY
        }
        activeCommandId = commandId

        // A display name, never an id. The caller resolves it, because the service is the only thing that
        // can be certain a UUID never reaches the resident's screen.
        val requestedByName = intent?.getStringExtra(EXTRA_REQUESTED_BY_NAME).orEmpty()

        startInForeground(commandId, requestedByName)
        startSirenAndTorch()

        scope.launch { completeWhenStopped(commandId) }

        // Not sticky. If the process is killed mid-alarm, silently restarting would re-alarm with no
        // context, and the caregiver's dashboard should show the alarm as unconfirmed instead. The
        // poll fallback will pick the command up again only while it is still PENDING; DELIVERED is
        // deliberately left for the caregiver to see as "reached the phone, not confirmed".
        return START_NOT_STICKY
    }

    /**
     * Starts in the foreground with an ongoing notification carrying a Stop action.
     *
     * Full-screen intent is set on top of the ongoing notification: on a locked screen this is what
     * takes the resident straight to the explanation rather than showing a silent notification they
     * may never notice. It degrades to the ordinary heads-up on API 34+ when the user has not granted
     * full-screen-intent access, which is why the notification also has to stand on its own.
     */
    private fun startInForeground(commandId: String, requestedByName: String) {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, RemoteAlarmService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_COMMAND_ID, commandId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Names the caregiver when a name could be resolved. Not decoration: a phone that says only
        // "a caregiver asked for help" is indistinguishable from a bug, and the resident's first
        // thought would be who. Absent a name the wording must not imply one, and must not fall back
        // to the raw id, which reads as a fault rather than as information.
        val body = if (requestedByName.isNotBlank()) {
            getString(R.string.caregiver_alarm_body) + "\n" +
                getString(R.string.caregiver_alarm_from, requestedByName)
        } else {
            getString(R.string.caregiver_alarm_body) + "\n" +
                getString(R.string.caregiver_alarm_unknown_who)
        }

        val builder = NotificationCompat.Builder(this, HomeGuardApplication.CHANNEL_ALARM_SERVICE)
            .setContentTitle(getString(R.string.caregiver_alarm_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_stat_homeguard)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .addAction(R.drawable.ic_stat_homeguard, getString(R.string.caregiver_alarm_stop), stopIntent)

        val notification = builder.build()
        // startForeground with the typed overload from API 29. The untyped one is deprecated there and
        // required a type that this service does not actually match.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Sounds the siren and lights the torch.
     *
     * Each is independent and failure-tolerant: a device with no torch, or one where CAMERA was
     * declined, still gets the alarm. Neither failure may prevent the other, because on a dark room
     * the torch is often the only thing that gets somebody to look at the phone.
     */
    private fun startSirenAndTorch() {
        alarm = EmergencyAlarmManager(applicationContext).also { it.startAlarm() }

        torch = runCatching { TorchManager(applicationContext) }
            .getOrElse {
                Log.w(TAG, "Torch unavailable; the siren is still sounding", it)
                null
            }
        torch?.setTorch(true)
    }

    /**
     * Reports completion once the resident stops the alarm.
     *
     * Polls the service flag rather than trusting a callback: [EmergencyAlarmManager] reports its own
     * state, and the service has to notice the transition even if the stop arrived from the alarm
     * manager's own failure path.
     */
    private suspend fun completeWhenStopped(commandId: String) {
        val manager = alarm ?: return
        // Whether the alarm ever actually sounded, recorded before waiting. It cannot be read after the
        // loop, because stopping the alarm is precisely what makes that flag false again.
        val didSound = manager.isAlarmPlaying.value

        while (scope.isActive && manager.isAlarmPlaying.value) {
            delay(POLL_INTERVAL_MS)
        }
        if (!scope.isActive) return

        runCatching {
            container.remoteAlarmDataSource
                .completeRemoteCommand(commandId, executed = didSound)
                .onFailure { Log.w(TAG, "Could not mark command $commandId complete", it) }
        }
        stopSelf()
    }

    override fun onDestroy() {
        // Always release the hardware. Leaking an alarm player or leaving the torch on would keep the
        // phone audibly alarming after the resident believes they stopped it.
        runCatching { alarm?.stopAlarm() }
        runCatching { torch?.setTorch(false) }
        runCatching { torch?.cleanUp() }
        alarm = null
        torch = null
        activeCommandId = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RemoteAlarmService"
        const val ACTION_STOP = "org.homeguard.action.STOP_REMOTE_ALARM"
        const val EXTRA_COMMAND_ID = "command_id"
        const val EXTRA_REQUESTED_BY_NAME = "requested_by_name"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_CONTENT = 1
        private const val REQUEST_STOP = 2
        private const val POLL_INTERVAL_MS = 500L

        /** Survives the service instance, since a restart creates a new one. */
        @Volatile
        private var activeCommandId: String? = null

        /**
 * Returns [raw] only if it can honestly be shown as a person's name.
 *
 * remote_commands.requested_by is an auth UUID, and the whole pipeline — claim, poll, service — passes
 * values along by hand, so one caller eventually passes the id where a name was expected. Showing it
 * puts "Asked by 7f3a91c4-…" on a locked screen, which reads as a malfunction and tells the resident
 * nothing they can act on. Blank is the honest answer: the notification then says a caregiver asked,
 * without inventing a name.
 *
 * Any identifier-shaped value is rejected, not just a well-formed UUID, so a future id format cannot
 * leak through by being unfamiliar to this check.
 */
internal fun usableDisplayName(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    val looksLikeIdentifier = trimmed.any { !it.isLetterOrDigit() && it != ' ' && it != '\'' } ||
        trimmed.first().isDigit() ||
        UUID_PATTERN.matches(trimmed)
    return if (looksLikeIdentifier) "" else trimmed
}

private val UUID_PATTERN =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
         * Starts the alarm.
         *
         * [requestedByName] is a display name or empty, never an id. Passing a UUID would put it on
         * the resident's lock screen, which reads as a malfunction rather than as information about
         * who asked.
         *
         * Callers must have already claimed the command. Claiming at the call site rather than here is
         * what stops the push path and the poll path both sounding the alarm.
         */
        fun start(context: Context, commandId: String, requestedByName: String) {
            val intent = Intent(context, RemoteAlarmService::class.java).apply {
                putExtra(EXTRA_COMMAND_ID, commandId)
                putExtra(EXTRA_REQUESTED_BY_NAME, usableDisplayName(requestedByName))
            }
            // Foreground start is the whole point; a plain start would be killed in the background.
            startForegroundSafely(context, intent)
        }

        fun stop(context: Context, commandId: String) {
            val intent = Intent(context, RemoteAlarmService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_COMMAND_ID, commandId)
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Could not deliver the stop request", it) }
        }

        private fun startForegroundSafely(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12+ blocks a background foreground-start in some states. Losing the in-app
                // alarm here is survivable because the resident's contacts were already notified.
                Log.e(TAG, "Could not start the caregiver alarm service", e)
            }
        }
    }
}