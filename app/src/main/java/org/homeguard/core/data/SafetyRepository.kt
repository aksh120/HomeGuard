package org.homeguard.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.homeguard.core.datastore.PreferencesManager
import org.homeguard.core.model.SafetyStatus
import java.util.concurrent.TimeUnit

/**
 * Pure safety state machine, extracted so it can be tested without a Context.
 *
 * Precedence is deliberate: an explicit EMERGENCY always wins, a never-checked-in device is
 * UNKNOWN rather than SAFE, and an elapsed check-in window downgrades to UNKNOWN.
 */
fun calculateSafetyStatus(
    stored: SafetyStatus,
    lastCheckIn: Long?,
    intervalHours: Int,
    graceMinutes: Int,
    now: Long = System.currentTimeMillis()
): SafetyStatus = when {
    stored == SafetyStatus.EMERGENCY -> SafetyStatus.EMERGENCY
    lastCheckIn == null -> SafetyStatus.UNKNOWN
    now - lastCheckIn > TimeUnit.HOURS.toMillis(intervalHours.toLong()) +
        TimeUnit.MINUTES.toMillis(graceMinutes.toLong()) -> SafetyStatus.UNKNOWN
    else -> SafetyStatus.SAFE
}

class SafetyRepository(
    private val preferencesManager: PreferencesManager,
    private val eventRepository: EventRepository
) {

    val safetyStatusFlow: Flow<SafetyStatus> = combine(
        preferencesManager.safetyStatusFlow,
        preferencesManager.lastCheckInTimestampFlow,
        preferencesManager.appConfigFlow
    ) { status, lastCheckIn, config ->
        calculateSafetyStatus(
            stored = status,
            lastCheckIn = lastCheckIn,
            intervalHours = config.checkInIntervalHours,
            graceMinutes = config.checkInGracePeriodMinutes
        )
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

    /**
     * Resolving an emergency records a deliberate check-in rather than assuming SAFE. Previously
     * this set UNKNOWN while the UI claimed the device was returned to a known-safe state.
     */
    suspend fun resolveEmergency(deviceId: String) {
        preferencesManager.recordCheckIn(System.currentTimeMillis())
        preferencesManager.setSafetyStatus(SafetyStatus.SAFE)
        eventRepository.recordAudit(
            eventType = org.homeguard.core.model.EventType.CHECK_IN,
            summary = org.homeguard.core.database.entity.EventSummaries.EMERGENCY_RESOLVED,
            details = "Alarm stopped and safety confirmed by the resident"
        )
        eventRepository.enqueueCheckIn(deviceId)
    }
}