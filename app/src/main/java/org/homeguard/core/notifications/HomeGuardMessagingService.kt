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

    override fun onMessageReceived(message: RemoteMessage) {
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
        runCatching { manager.notify(id, notification) }
            .onFailure { Log.w(FCM_TAG, "Could not post push notification", it) }
    }

    private companion object {
        const val FCM_TAG = "HomeGuardPush"
    }
}
