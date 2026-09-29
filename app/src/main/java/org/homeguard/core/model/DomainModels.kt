package org.homeguard.core.model

import java.util.UUID

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

data class EmergencyEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val syncStatus: EventSyncStatus = EventSyncStatus.PENDING,
    val details: String? = null
)

data class CheckIn(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val syncStatus: EventSyncStatus = EventSyncStatus.PENDING
)

data class DeviceStatus(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isWifiConnected: Boolean = true,
    val isInternetAvailable: Boolean = true,
    val lastCheckInTimestamp: Long? = null,
    val safetyStatus: SafetyStatus = SafetyStatus.UNKNOWN,
    val isMonitoringActive: Boolean = true
)

data class AppConfig(
    val isSetupComplete: Boolean = false,
    val deviceMode: DeviceMode = DeviceMode.PROTECTED_DEVICE,
    val deviceId: String = UUID.randomUUID().toString(),
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
    val checkInGracePeriodMinutes: Int = 60
)
