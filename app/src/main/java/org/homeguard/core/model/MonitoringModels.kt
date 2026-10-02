package org.homeguard.core.model

/**
 * A shared group of devices: one home device and the contacts allowed to watch it.
 *
 * Created by the home device. [inviteCode] is the join credential a caregiver types on their own
 * phone; it identifies a household, it does not identify a person.
 */
data class Household(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = ""
)

data class HouseholdMember(
    val deviceId: String = "",
    val role: String = ""
)

/**
 * A home device as seen by a caregiver.
 *
 * This is remote state, not local state. [lastSeenAt] empty means the device has never reported,
 * which is materially different from a device that reported recently and is therefore current.
 */
data class MonitoredDevice(
    val deviceId: String = "",
    val personName: String = "",
    val personRole: String = "",
    val deviceMode: String = "",
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val isInternetAvailable: Boolean = false,
    val safetyStatus: String = "",
    val lastSeenAt: String = ""
) {
    val displayName: String get() = personName.ifBlank { "Protected person" }

    val hasReported: Boolean get() = lastSeenAt.isNotBlank()

    val safety: SafetyStatus
        get() = runCatching { SafetyStatus.valueOf(safetyStatus) }.getOrDefault(SafetyStatus.UNKNOWN)

    /**
     * Whether the device has gone quiet long enough to be worth surfacing.
     *
     * Section 19 is explicit that a silent device must NOT be reported as an emergency; it shows
     * as unknown. Two missed heartbeats at the 15-minute sync interval is a reasonable threshold.
     */
    fun isStale(nowMillis: Long, staleAfterMillis: Long = 45 * 60 * 1000L): Boolean {
        val parsed = runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(lastSeenAt.removeSuffix("Z"))
        }.getOrNull() ?: return true
        return nowMillis - parsed.time > staleAfterMillis
    }
}

/** An emergency raised remotely. Read-only to a caregiver. */
data class RemoteEmergencyEvent(
    val id: String = "",
    val deviceId: String = "",
    val triggeredAt: String = "",
    val details: String = "",
    val resolvedAt: String? = null
) {
    val isResolved: Boolean get() = !resolvedAt.isNullOrBlank()
}

/** A safety check-in confirmed remotely. */
data class RemoteCheckIn(
    val id: String = "",
    val deviceId: String = "",
    val confirmedAt: String = ""
)