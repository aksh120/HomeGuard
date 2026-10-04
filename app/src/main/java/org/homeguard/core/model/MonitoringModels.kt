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

/**
 * A household this install belongs to, as persisted locally.
 *
 * Decision D2 is one code per house, so a caregiver watching two households holds two of these and
 * sees the people from both in one feed.
 */
data class HouseholdRef(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = ""
) {
    /**
     * Name for display, deliberately never the invite code.
     *
     * The code was previously used as the household label, which is a small leak: it is the join
     * credential, it gets read aloud, screenshotted, and texted. Blank means "not named yet" and the
     * caller supplies a localised default.
     */
    val hasName: Boolean get() = name.isNotBlank()
}

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

/**
 * One watched person, with the remote rows that belong to them already resolved.
 *
 * The dashboard used to take a single device plus the full check-in list, which meant the latest
 * check-in shown belonged to whichever device happened to be first. Resolving per device here is
 * what makes a multi-person list correct rather than merely plural.
 */
data class WatchedPerson(
    val device: MonitoredDevice,
    val lastCheckInAt: String? = null,
    val openEmergency: RemoteEmergencyEvent? = null
) {
    val deviceId: String get() = device.deviceId
    val hasOpenEmergency: Boolean get() = openEmergency != null
}

/** One row in the combined activity feed, tagged with who it happened to. */
sealed interface CaregiverActivity {
    /** Epoch millis for ordering; 0 when the backend timestamp could not be parsed. */
    val sortKey: Long
    val personName: String

    data class Emergency(
        val event: RemoteEmergencyEvent,
        override val personName: String,
        override val sortKey: Long
    ) : CaregiverActivity

    data class CheckIn(
        val checkIn: RemoteCheckIn,
        override val personName: String,
        override val sortKey: Long
    ) : CaregiverActivity
}

/**
 * Turns raw remote rows into what the caregiver dashboard shows.
 *
 * Pure and free of Android and network types so the rules are unit testable: which devices count as
 * people, whose check-in belongs to whom, and what order the feed is in.
 */
object CaregiverAggregator {

    /**
     * The protected devices a caregiver watches, most urgent first.
     *
     * Monitoring devices are excluded: the caregiver's own phone is in the household as a member,
     * and listing it as a person to watch would be nonsense. Unresolved emergencies float to the
     * top, then stale devices, then people by name, so the reading order matches the urgency.
     */
    fun watchedPeople(
        devices: List<MonitoredDevice>,
        checkIns: List<RemoteCheckIn> = emptyList(),
        emergencies: List<RemoteEmergencyEvent> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<WatchedPerson> {
        val latestCheckIn = checkIns
            .filter { it.deviceId.isNotBlank() }
            .groupBy { it.deviceId }
            .mapValues { (_, rows) -> rows.maxByOrNull { parseIsoMillis(it.confirmedAt) }?.confirmedAt }

        val openByDevice = emergencies
            .filter { !it.isResolved }
            .groupBy { it.deviceId }

        return devices
            .filter { it.deviceMode != MONITORING_DEVICE_MARKER && it.deviceId.isNotBlank() }
            .map { device ->
                WatchedPerson(
                    device = device,
                    lastCheckInAt = latestCheckIn[device.deviceId],
                    openEmergency = openByDevice[device.deviceId]?.maxByOrNull {
                        parseIsoMillis(it.triggeredAt)
                    }
                )
            }
            .sortedWith(
                compareByDescending<WatchedPerson> { it.hasOpenEmergency }
                    // An EMERGENCY reported by the device outranks one merely inferred, and a silent
                    // device is surfaced before a chatty one so a dead phone is not missed.
                    .thenByDescending { it.device.safety == SafetyStatus.EMERGENCY }
                    .thenByDescending { it.device.isStale(nowMillis) }
                    .thenBy { it.device.displayName.lowercase() }
            )
    }

    /**
     * Emergencies and check-ins from every watched device in one reverse-chronological feed.
     *
     * Rows for devices that are not being watched are dropped rather than shown unattributed: an
     * activity entry with no person attached is worse than no entry, because the caregiver cannot
     * act on it.
     */
    fun activityFeed(
        people: List<WatchedPerson>,
        checkIns: List<RemoteCheckIn> = emptyList(),
        emergencies: List<RemoteEmergencyEvent> = emptyList(),
        limit: Int = 12
    ): List<CaregiverActivity> {
        val namesById = people.associate { it.deviceId to it.device.displayName }

        val items = buildList {
            emergencies.forEach { event ->
                val name = namesById[event.deviceId] ?: return@forEach
                add(
                    CaregiverActivity.Emergency(
                        event = event,
                        personName = name,
                        sortKey = parseIsoMillis(event.triggeredAt)
                    )
                )
            }
            checkIns.forEach { checkIn ->
                val name = namesById[checkIn.deviceId] ?: return@forEach
                add(
                    CaregiverActivity.CheckIn(
                        checkIn = checkIn,
                        personName = name,
                        sortKey = parseIsoMillis(checkIn.confirmedAt)
                    )
                )
            }
        }

        return items.sortedByDescending { it.sortKey }.take(limit)
    }

    /**
     * Parses a backend ISO-8601 instant to epoch millis.
     *
     * Returns 0 when parsing fails rather than substituting the current time. An unparseable
     * timestamp should sink to the bottom of the feed, not masquerade as the newest event.
     */
    fun parseIsoMillis(iso: String): Long {
        if (iso.isBlank()) return 0
        val normalised = iso.removeSuffix("Z").substringBefore("+").trim()
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.parse(normalised)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return 0
    }

    const val MONITORING_DEVICE_MARKER = "MONITORING_DEVICE"
}