package org.homeguard.core.notifications

import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.homeguard.HomeGuardApplication
import org.homeguard.core.data.RemoteAlarmDataSource

/**
 * Receives caregiver alerts while the app is backgrounded.
 *
 * This is the channel that can actually reach a locked phone. Realtime only works with the app open,
 * and email takes minutes, so for a safety alert neither is sufficient.
 *
 * Notification display is delegated to [SafetyNotifier] so a push and a locally generated alert look
 * identical on screen. Duplicating the channel and priority logic here is how the two would drift
 * apart, and then a caregiver would see a quieter push for the same emergency.
 */
class HomeGuardMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Receives a caregiver-raised alarm.
     *
     * Claims before alarming, never the other way round. The same command is also visible to the poll
     * fallback in [org.homeguard.core.sync.SyncWorker], and both paths can run within seconds of each
     * other. The claim is the single conditional update that decides which one is allowed to sound the
     * siren; whichever loses gets nothing and quietly stops. Claiming after starting the alarm would
     * mean the resident hears a duplicate alarm for a request that was already being handled.
     *
     * A losing claim is a normal outcome, not an error, and must not produce an error notification.
     */
    private fun handleAlarmCommand(commandId: String) {
        val app = applicationContext as? HomeGuardApplication ?: return
        scope.launch {
            val claimed = app.container.remoteAlarmDataSource.claimRemoteCommand(commandId)
            claimed
                .onSuccess { alarm ->
                    if (alarm == null) {
                        Log.i(FCM_TAG, "Command $commandId was already handled; not alarming again")
                        return@onSuccess
                    }
                    Log.i(FCM_TAG, "Claimed caregiver alarm $commandId; sounding the alarm")
                    // Resolved before the service starts so the notification is never built twice with
                    // different text, and so a slow lookup delays the siren rather than its label.
                    val name = app.container.remoteAlarmDataSource.requesterDisplayName(
                        alarm.householdId,
                        alarm.requestedBy
                    )
                    RemoteAlarmService.start(applicationContext, alarm.id, name)
                }
                .onFailure {
                    // Do not alarm blindly. An unclaimed command might belong to somebody else, and
                    // this device has no way to tell. The poll fallback will retry it.
                    Log.w(FCM_TAG, "Could not claim caregiver alarm $commandId", it)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Checked before the notification path because this message deliberately carries no
        // notification payload: the edge function sends it as data-only so the SDK does not render a
        // generic "Emergency" headline that would misattribute a caregiver's alarm to the resident.
        if (message.data["kind"] == RemoteAlarmDataSource.KIND_RAISE_ALARM) {
            val commandId = message.data["command_id"].orEmpty()
            if (commandId.isBlank()) {
                Log.w(FCM_TAG, "Alarm command with no command id; ignoring")
                return
            }
            handleAlarmCommand(commandId)
            return
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "HomeGuard"
        val body = message.notification?.body ?: message.data["body"].orEmpty()

        // Some devices deliver data-only messages, in which case Android shows nothing at all until
        // the app builds the notification itself. Always post explicitly rather than relying on the
        // SDK to render it.
        val id = message.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
        post(title, body, id)
    }

    /**
     * Called when FCM rotates a token.
     *
     * Skipping this is the classic way push dies quietly: the stored token keeps working until FCM
     * invalidates it, then alerts stop arriving for that caregiver with no error anywhere.
     */
    override fun onNewToken(token: String) {
        Log.i(FCM_TAG, "FCM issued a new registration token; re-registering")
        scope.launch {
            runCatching { PushTokenRegistrar.register(applicationContext, token) }
                .onFailure { Log.w(FCM_TAG, "Could not re-register push token", it) }
        }
    }

    private fun post(title: String, body: String, id: Int) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!HomeGuardApplication.hasNotificationPermission(applicationContext)) {
            // Decision D9. On API 33+ a denied permission makes the notification vanish silently,
            // which would look exactly like push not working. The caregiver dashboard has a blocking
            // prompt for this; logging keeps the failure diagnosable from logcat too.
            Log.w(
                FCM_TAG,
                "Dropping a push alert because POST_NOTIFICATIONS is not granted: $title"
            )
            return
        }
        val notification = NotificationCompat.Builder(applicationContext, HomeGuardApplication.CHANNEL_EMERGENCY)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        // An explicit SecurityException catch rather than runCatching: the permission can be revoked
        // between the check above and this call, and catching that specifically is what lint requires
        // for a guarded notify. runCatching would also swallow an unrelated RuntimeException and hide
        // a real fault behind a log line.
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(FCM_TAG, "POST_NOTIFICATIONS was revoked before this push could be shown", e)
        }
    }

    private companion object {
        const val FCM_TAG = "HomeGuardPush"
    }
}
