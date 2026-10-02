package org.homeguard.core.model

import java.util.UUID

enum class DeviceMode {
    PROTECTED_DEVICE,
    MONITORING_DEVICE
}

/**
 * The person the protected device watches over.
 *
 * Section 9 requires name, optional photo, and relationship. Every field is optional and nothing
 * is pre-filled: section 50 forbids personal defaults.
 */
data class ProtectedPerson(
    val name: String = "",
    val relationship: String = "",
    val photoUri: String? = null
)

/**
 * A person the device can reach in an emergency.
 *
 * [phoneOrHandle] holds either a phone number or a web address. The device may have no SIM, so the
 * communication layer treats the two interchangeably rather than assuming telephony.
 */
data class ContactInfo(
    val name: String = "",
    val phoneOrHandle: String = "",
    val relationship: String = "",
    val isEmergencyServices: Boolean = false
)

data class HomeInfo(
    val address: String = "",
    val buildingSecurityContact: String = "",
    val accessInstructions: String = ""
)

data class MedicalInfo(
    val allergies: String = "",
    val bloodGroup: String = "",
    val medications: String = "",
    val doctorContact: String = "",
    val notes: String = ""
)

/**
 * The offline reference card a first responder or neighbour would need.
 *
 * Section 21 requires all of this to be optional. Medical fields are never mandatory and are never
 * pre-filled. This is a view over the persisted pieces, grouped so the information screen and the
 * backend payload have one coherent shape.
 */
data class EmergencyInformation(
    val localEmergencyNumber: String = "",
    val home: HomeInfo = HomeInfo(),
    val medical: MedicalInfo = MedicalInfo(),
    val primaryContact: ContactInfo = ContactInfo(),
    val secondaryContact: ContactInfo = ContactInfo(),
    val emergencyContact: ContactInfo = ContactInfo()
)

/**
 * When the device asks the resident to confirm they are okay.
 *
 * Section 14 requires a configurable schedule. [dailyTimes] holds minutes-from-midnight, so a
 * morning-and-evening pair is simply [listOf(9 * 60, 21 * 60)].
 */
data class CheckInSchedule(
    val enabled: Boolean = true,
    val dailyTimes: List<Int> = listOf(10 * 60),
    /** Minutes allowed to respond before the status is treated as unconfirmed. */
    val timeoutMinutes: Int = 60
) {
    val isValid: Boolean get() = dailyTimes.isNotEmpty() && dailyTimes.all { it in 0..1439 }

    /** Formatted as local wall-clock times, for display and for choosing the next alarm. */
    fun displayTimes(): List<String> =
        dailyTimes.sorted().map { "%02d:%02d".format(it / 60, it % 60) }

    /** Next configured time strictly after [nowMinute], wrapping to the earliest. */
    fun nextOccurrence(nowMinute: Int): Int? {
        if (!enabled || !isValid) return null
        val sorted = dailyTimes.sorted()
        return sorted.firstOrNull { it > nowMinute } ?: sorted.firstOrNull()
    }
}

/**
 * Night mode activation window.
 *
 * Section 16 requires configurable start and end times rather than a bare toggle.
 */
data class NightModeSchedule(
    val enabled: Boolean = false,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60
) {
    val isValid: Boolean
        get() = startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute

    /**
     * True when [nowMinute] falls inside the window. Handles windows that wrap past midnight,
     * which is the normal case for night mode.
     */
    fun isActiveAt(nowMinute: Int): Boolean {
        if (!enabled || !isValid) return false
        return if (startMinute < endMinute) {
            nowMinute in startMinute until endMinute
        } else {
            nowMinute >= startMinute || nowMinute < endMinute
        }
    }

    /** Human-readable window, e.g. "22:00 – 07:00", or "Off" when disabled. */
    fun displayWindow(): String {
        if (!enabled) return "Off"
        return "%02d:%02d – %02d:%02d".format(
            startMinute / 60, startMinute % 60,
            endMinute / 60, endMinute % 60
        )
    }
}

/**
 * Battery levels at which the resident and monitoring contact are warned.
 *
 * Section 17 gives 20%/10% as the default but leaves the values configurable.
 */
data class BatteryThresholds(
    val lowPercent: Int = 20,
    val criticalPercent: Int = 10
) {
    val isValid: Boolean
        get() = lowPercent in 1..100 && criticalPercent in 1..100 && criticalPercent < lowPercent
}

data class AppConfig(
    val isSetupComplete: Boolean = false,
    val deviceMode: DeviceMode = DeviceMode.PROTECTED_DEVICE,
    // Blank by default. Identity is minted and persisted by PreferencesManager.ensureDeviceId();
    // defaulting this to a fresh UUID would hand out a new identity on every construction.
    val deviceId: String = "",
    val personName: String = "",
    val personRole: String = "",
    val primaryContact: ContactInfo = ContactInfo(),
    val secondaryContact: ContactInfo = ContactInfo(),
    val emergencyContact: ContactInfo = ContactInfo(),
    val localEmergencyNumber: String = "",
    val homeInfo: HomeInfo = HomeInfo(),
    val medicalInfo: MedicalInfo = MedicalInfo(),
    val isNightModeEnabled: Boolean = false,
    val checkInIntervalHours: Int = 24,
    val checkInGracePeriodMinutes: Int = 60,
    val checkInSchedule: CheckInSchedule = CheckInSchedule(),
    val nightModeSchedule: NightModeSchedule = NightModeSchedule(),
    val batteryThresholds: BatteryThresholds = BatteryThresholds(),
    /** Set once the resident has read and accepted the safety notice. */
    val hasAcknowledgedSafetyNotice: Boolean = false
) {
    val protectedPerson: ProtectedPerson
        get() = ProtectedPerson(name = personName, relationship = personRole)

    val emergencyInformation: EmergencyInformation
        get() = EmergencyInformation(
            localEmergencyNumber = localEmergencyNumber,
            home = homeInfo,
            medical = medicalInfo,
            primaryContact = primaryContact,
            secondaryContact = secondaryContact,
            emergencyContact = emergencyContact
        )
}