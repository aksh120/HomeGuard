package org.homeguard.core.model

enum class DeviceMode {
    PROTECTED_DEVICE,
    MONITORING_DEVICE
}

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
    val isAcousticDetectionEnabled: Boolean = false,
    val checkInIntervalHours: Int = 24,
    val checkInGracePeriodMinutes: Int = 60
)