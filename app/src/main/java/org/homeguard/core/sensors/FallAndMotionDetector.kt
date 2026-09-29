package org.homeguard.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class MotionTelemetry(
    val isMonitoring: Boolean = false,
    val lastMovementTimestamp: Long = System.currentTimeMillis(),
    val currentGForce: Float = 1.0f,
    val isStillnessDetected: Boolean = false
)

class FallAndMotionDetector(
    private val context: Context,
    private val onFallDetected: () -> Unit,
    private val onSignificantMovement: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _telemetry = MutableStateFlow(MotionTelemetry())
    val telemetry: StateFlow<MotionTelemetry> = _telemetry.asStateFlow()

    // Fall detection state machine
    private var freeFallDetectedTime = 0L
    private var impactDetectedTime = 0L
    private var stillnessTimerJob: Job? = null

    private var lastMovementReportTime = 0L

    fun start() {
        if (accelerometer != null) {
            sensorManager?.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
            _telemetry.value = _telemetry.value.copy(isMonitoring = true)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        stillnessTimerJob?.cancel()
        _telemetry.value = _telemetry.value.copy(isMonitoring = false)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val gForce = magnitude / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        _telemetry.value = _telemetry.value.copy(currentGForce = gForce)

        // 1. Passive Movement detection (phone picked up / moved)
        if (kotlin.math.abs(gForce - 1.0f) > 0.35f) {
            _telemetry.value = _telemetry.value.copy(
                lastMovementTimestamp = now,
                isStillnessDetected = false
            )
            if (now - lastMovementReportTime > 60_000L) { // Rate limit movement events to 1/min
                lastMovementReportTime = now
                onSignificantMovement()
            }
        }

        // 2. Free-fall phase (< 0.45 G)
        if (gForce < 0.45f) {
            freeFallDetectedTime = now
        }

        // 3. Impact phase (> 2.8 G within 600ms of free-fall)
        if (gForce > 2.8f && (now - freeFallDetectedTime) < 600L && freeFallDetectedTime > 0) {
            impactDetectedTime = now
            freeFallDetectedTime = 0L

            // 4. Verify post-impact stillness (user lying motionless on ground)
            stillnessTimerJob?.cancel()
            stillnessTimerJob = scope.launch {
                delay(3000L) // Wait 3s to confirm prolonged stillness
                if (now - _telemetry.value.lastMovementTimestamp > 2500L) {
                    Log.w("FallDetector", "CRITICAL: Potential fall impact & stillness confirmed!")
                    onFallDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
