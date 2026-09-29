package org.homeguard.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.homeguard.core.datastore.PreferencesManager
import org.homeguard.core.model.SafetyStatus
import java.util.concurrent.TimeUnit

class SafetyRepository(
    private val preferencesManager: PreferencesManager,
    private val eventRepository: EventRepository
) {

    val safetyStatusFlow: Flow<SafetyStatus> = combine(
        preferencesManager.safetyStatusFlow,
        preferencesManager.lastCheckInTimestampFlow,
        preferencesManager.appConfigFlow
    ) { status, lastCheckIn, config ->
        // If an explicit active emergency exists, keep EMERGENCY
        if (status == SafetyStatus.EMERGENCY) {
            return@combine SafetyStatus.EMERGENCY
        }

        // If no check-in has ever been recorded, status is UNKNOWN
        if (lastCheckIn == null) {
            return@combine SafetyStatus.UNKNOWN
        }

        // Check if the scheduled check-in window has elapsed
        val maxDurationMillis = TimeUnit.HOURS.toMillis(config.checkInIntervalHours.toLong()) +
                TimeUnit.MINUTES.toMillis(config.checkInGracePeriodMinutes.toLong())
        val isExpired = (System.currentTimeMillis() - lastCheckIn) > maxDurationMillis

        if (isExpired) {
            SafetyStatus.UNKNOWN
        } else {
            SafetyStatus.SAFE
        }
    }

    val lastCheckInTimestampFlow: Flow<Long?> = preferencesManager.lastCheckInTimestampFlow

    suspend fun recordCheckIn(deviceId: String) {
        val now = System.currentTimeMillis()
        preferencesManager.recordCheckIn(now)
        eventRepository.enqueueCheckIn(deviceId)
    }

    suspend fun triggerEmergency(deviceId: String, details: String? = null): String {
        preferencesManager.setSafetyStatus(SafetyStatus.EMERGENCY)
        return eventRepository.enqueueEmergencySos(deviceId, details)
    }

    suspend fun resolveEmergency() {
        preferencesManager.setSafetyStatus(SafetyStatus.UNKNOWN)
    }
}
