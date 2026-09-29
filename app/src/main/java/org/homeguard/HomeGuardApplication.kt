package org.homeguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class HomeGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
    }

    companion object {
        const val CHANNEL_EMERGENCY = "channel_homeguard_emergency"
        const val CHANNEL_CHECKIN = "channel_homeguard_checkin"
        const val CHANNEL_TELEMETRY = "channel_homeguard_telemetry"
    }
}
