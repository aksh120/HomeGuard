package org.homeguard.core.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isLow: Boolean = false,
    val isCritical: Boolean = false
)

/**
 * Battery thresholds, extracted so they can be tested without constructing an Intent.
 * Critical is checked first: a device at 5% is both low and critical, and must report critical.
 */
fun evaluateBatteryState(percentage: Int, isCharging: Boolean): BatteryInfo {
    val isCritical = percentage <= 10
    val isLow = percentage in 11..20
    return BatteryInfo(
        level = percentage,
        isCharging = isCharging,
        isLow = isLow,
        isCritical = isCritical
    )
}

class BatteryMonitor(private val context: Context) {

    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateFromIntent(intent)
        }
    }

    init {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { updateFromIntent(it) }
    }

    private fun updateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val percentage = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            100
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        _batteryInfo.value = evaluateBatteryState(percentage, isCharging)
    }

    fun unregister() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}
