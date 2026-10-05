package org.homeguard

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.homeguard.core.di.AppContainer
import org.homeguard.core.notifications.PushTokenRegistrar

class HomeGuardApplication : Application() {

    lateinit var container: AppContainer
        private set

    /**
     * Scope for fire-and-forget startup work such as push token registration.
     *
     * SupervisorJob so one failure cannot cancel anything else, and on the default dispatcher so a
     * slow network call never delays the first frame of a safety app.
     */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        // Phase 4: register for push. Runs for both profiles, because Phase 5's remote alarm needs
        // the resident's phone reachable as well as the caregiver's.
        startupScope.launch { PushTokenRegistrar.register(this@HomeGuardApplication) }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Emergency Channel (High priority)
            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                getString(R.string.channel_emergency_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_emergency_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Safety Check-In Channel (Default priority)
            val checkinChannel = NotificationChannel(
                CHANNEL_CHECKIN,
                getString(R.string.channel_checkin_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_checkin_desc)
                enableVibration(true)
            }

            // 3. Telemetry Channel (Low priority)
            val telemetryChannel = NotificationChannel(
                CHANNEL_TELEMETRY,
                getString(R.string.channel_telemetry_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_telemetry_desc)
            }

            notificationManager.createNotificationChannels(
                listOf(emergencyChannel, checkinChannel, telemetryChannel)
            )
        }

        createAlarmServiceChannel()
    }

    /**
     * The ongoing notification shown while a caregiver alarm is sounding.
     *
     * Separate from [CHANNEL_EMERGENCY] on purpose. That channel is IMPORTANCE_HIGH and carries alerts
     * the resident is meant to read. This one is IMPORTANCE_LOW with no sound and no vibration,
     * because [org.homeguard.core.hardware.EmergencyAlarmManager] already owns the audio and the
     * vibration: a channel that also made noise would double the alarm and make it impossible to stop
     * cleanly, since the channel notification would keep buzzing after the siren stopped.
     */
    private fun createAlarmServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM_SERVICE,
                getString(R.string.alarm_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.alarm_service_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        const val CHANNEL_EMERGENCY = "channel_homeguard_emergency"
        const val CHANNEL_CHECKIN = "channel_homeguard_checkin"
        const val CHANNEL_TELEMETRY = "channel_homeguard_telemetry"

        /**
         * Ongoing, silent channel for the caregiver-alarm foreground service.
         *
         * Distinct from [CHANNEL_EMERGENCY] so the siren has exactly one owner. Sharing the channel
         * would make the foreground notification sound on its own, so stopping the alarm would leave
         * a notification still buzzing on a phone that was supposed to go quiet.
         */
        const val CHANNEL_ALARM_SERVICE = "channel_homeguard_alarm_service"

        /**
         * Whether notifications may be posted at all.
         *
         * Phase 4 / decision D9. Below API 33 the permission does not exist and posting always works.
         * Above it, a denied permission makes every notification vanish with no error, so both the
         * push receiver and the local notifier check this rather than posting blindly.
         */
        fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}