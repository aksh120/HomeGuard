package org.homeguard.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.BatteryThresholds
import org.homeguard.core.model.CheckInSchedule
import org.homeguard.core.model.ContactInfo
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.HomeInfo
import org.homeguard.core.model.MedicalInfo
import org.homeguard.core.model.NightModeSchedule
import org.homeguard.core.model.SafetyStatus
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "homeguard_prefs")

class PreferencesManager(private val context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val KEY_DEVICE_MODE = stringPreferencesKey("device_mode")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")

        val KEY_PERSON_NAME = stringPreferencesKey("person_name")
        val KEY_PERSON_ROLE = stringPreferencesKey("person_role")

        val KEY_PRIMARY_CONTACT_NAME = stringPreferencesKey("primary_contact_name")
        val KEY_PRIMARY_CONTACT_PHONE = stringPreferencesKey("primary_contact_phone")

        val KEY_SECONDARY_CONTACT_NAME = stringPreferencesKey("secondary_contact_name")
        val KEY_SECONDARY_CONTACT_PHONE = stringPreferencesKey("secondary_contact_phone")

        val KEY_EMERGENCY_CONTACT_NAME = stringPreferencesKey("emergency_contact_name")
        val KEY_EMERGENCY_CONTACT_PHONE = stringPreferencesKey("emergency_contact_phone")

        val KEY_LOCAL_EMERGENCY_NUMBER = stringPreferencesKey("local_emergency_number")

        val KEY_HOME_ADDRESS = stringPreferencesKey("home_address")
        val KEY_BUILDING_SECURITY = stringPreferencesKey("building_security")
        val KEY_ACCESS_INSTRUCTIONS = stringPreferencesKey("access_instructions")

        val KEY_ALLERGIES = stringPreferencesKey("allergies")
        val KEY_BLOOD_GROUP = stringPreferencesKey("blood_group")
        val KEY_MEDICATIONS = stringPreferencesKey("medications")
        val KEY_DOCTOR = stringPreferencesKey("doctor")
        val KEY_MEDICAL_NOTES = stringPreferencesKey("medical_notes")

        val KEY_NIGHT_MODE = booleanPreferencesKey("night_mode_enabled")
        val KEY_CHECKIN_INTERVAL_HOURS = intPreferencesKey("checkin_interval_hours")
        val KEY_CHECKIN_GRACE_MINUTES = intPreferencesKey("checkin_grace_minutes")
        val KEY_LAST_CHECKIN = longPreferencesKey("last_checkin_timestamp")
        val KEY_SAFETY_STATUS = stringPreferencesKey("current_safety_status")

        // Scheduled check-ins (section 14)
        val KEY_CHECKIN_SCHED_ENABLED = booleanPreferencesKey("checkin_schedule_enabled")
        val KEY_CHECKIN_SCHEDULE_TIMES = stringPreferencesKey("checkin_schedule_times")
        val KEY_CHECKIN_TIMEOUT_MINUTES = intPreferencesKey("checkin_timeout_minutes")

        // Night mode window (section 16)
        val KEY_NIGHT_START_MINUTE = intPreferencesKey("night_start_minute")
        val KEY_NIGHT_END_MINUTE = intPreferencesKey("night_end_minute")

        // Battery thresholds (section 17)
        val KEY_BATTERY_LOW_PERCENT = intPreferencesKey("battery_low_percent")
        val KEY_BATTERY_CRITICAL_PERCENT = intPreferencesKey("battery_critical_percent")

        // Safety notice acknowledgement (section 52)
        val KEY_ACK_SAFETY_NOTICE = booleanPreferencesKey("acknowledged_safety_notice")

        /** Separator for the persisted check-in time list. */
        private const val TIME_SEPARATOR = ","
    }

    /**
     * Stable device identity.
     *
     * Previously this fell back to a fresh UUID on every emission, so any event recorded before
     * setup completed carried an id that no longer existed.
     */
    val deviceIdFlow: Flow<String> = dataStore.data
        .map { prefs -> prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString() }
        .distinctUntilChanged()

    /** Persists the device ID on first call so it survives process death and reboots. */
    suspend fun ensureDeviceId(): String {
        var resolved = ""
        dataStore.edit { prefs ->
            val existing = prefs[KEY_DEVICE_ID]
            resolved = existing
                ?: UUID.randomUUID().toString().also { prefs[KEY_DEVICE_ID] = it }
        }
        return resolved
    }

    val appConfigFlow: Flow<AppConfig> = dataStore.data.map { prefs ->
        AppConfig(
            isSetupComplete = prefs[KEY_SETUP_COMPLETE] ?: false,
            deviceMode = try {
                DeviceMode.valueOf(prefs[KEY_DEVICE_MODE] ?: DeviceMode.PROTECTED_DEVICE.name)
            } catch (e: Exception) {
                DeviceMode.PROTECTED_DEVICE
            },
            deviceId = prefs[KEY_DEVICE_ID] ?: "",
            personName = prefs[KEY_PERSON_NAME] ?: "",
            personRole = prefs[KEY_PERSON_ROLE] ?: "",
            primaryContact = ContactInfo(
                name = prefs[KEY_PRIMARY_CONTACT_NAME] ?: "",
                phoneOrHandle = prefs[KEY_PRIMARY_CONTACT_PHONE] ?: ""
            ),
            secondaryContact = ContactInfo(
                name = prefs[KEY_SECONDARY_CONTACT_NAME] ?: "",
                phoneOrHandle = prefs[KEY_SECONDARY_CONTACT_PHONE] ?: ""
            ),
            emergencyContact = ContactInfo(
                name = prefs[KEY_EMERGENCY_CONTACT_NAME] ?: "",
                phoneOrHandle = prefs[KEY_EMERGENCY_CONTACT_PHONE] ?: ""
            ),
            localEmergencyNumber = prefs[KEY_LOCAL_EMERGENCY_NUMBER] ?: "",
            homeInfo = HomeInfo(
                address = prefs[KEY_HOME_ADDRESS] ?: "",
                buildingSecurityContact = prefs[KEY_BUILDING_SECURITY] ?: "",
                accessInstructions = prefs[KEY_ACCESS_INSTRUCTIONS] ?: ""
            ),
            medicalInfo = MedicalInfo(
                allergies = prefs[KEY_ALLERGIES] ?: "",
                bloodGroup = prefs[KEY_BLOOD_GROUP] ?: "",
                medications = prefs[KEY_MEDICATIONS] ?: "",
                doctorContact = prefs[KEY_DOCTOR] ?: "",
                notes = prefs[KEY_MEDICAL_NOTES] ?: ""
            ),
            isNightModeEnabled = prefs[KEY_NIGHT_MODE] ?: false,
            checkInIntervalHours = prefs[KEY_CHECKIN_INTERVAL_HOURS] ?: 24,
            checkInGracePeriodMinutes = prefs[KEY_CHECKIN_GRACE_MINUTES] ?: 60,
            checkInSchedule = CheckInSchedule(
                enabled = prefs[KEY_CHECKIN_SCHED_ENABLED] ?: true,
                dailyTimes = decodeTimes(prefs[KEY_CHECKIN_SCHEDULE_TIMES]),
                timeoutMinutes = prefs[KEY_CHECKIN_TIMEOUT_MINUTES] ?: 60
            ),
            nightModeSchedule = NightModeSchedule(
                enabled = prefs[KEY_NIGHT_MODE] ?: false,
                startMinute = prefs[KEY_NIGHT_START_MINUTE] ?: (22 * 60),
                endMinute = prefs[KEY_NIGHT_END_MINUTE] ?: (7 * 60)
            ),
            batteryThresholds = BatteryThresholds(
                lowPercent = prefs[KEY_BATTERY_LOW_PERCENT] ?: 20,
                criticalPercent = prefs[KEY_BATTERY_CRITICAL_PERCENT] ?: 10
            ),
            hasAcknowledgedSafetyNotice = prefs[KEY_ACK_SAFETY_NOTICE] ?: false
        )
    }

    private fun decodeTimes(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return CheckInSchedule().dailyTimes
        return raw.split(TIME_SEPARATOR)
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..1439 }
            .ifEmpty { CheckInSchedule().dailyTimes }
    }

    private fun encodeTimes(times: List<Int>): String =
        times.filter { it in 0..1439 }.joinToString(TIME_SEPARATOR)

    val lastCheckInTimestampFlow: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CHECKIN]
    }

    val safetyStatusFlow: Flow<SafetyStatus> = dataStore.data.map { prefs ->
        try {
            SafetyStatus.valueOf(prefs[KEY_SAFETY_STATUS] ?: SafetyStatus.UNKNOWN.name)
        } catch (e: Exception) {
            SafetyStatus.UNKNOWN
        }
    }

    suspend fun currentConfig(): AppConfig = appConfigFlow.first()

    suspend fun saveConfig(config: AppConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_SETUP_COMPLETE] = config.isSetupComplete
            prefs[KEY_DEVICE_MODE] = config.deviceMode.name
            // Never overwrite a real identity with a blank one from an unhydrated config.
            if (config.deviceId.isNotBlank()) {
                prefs[KEY_DEVICE_ID] = config.deviceId
            }
            prefs[KEY_PERSON_NAME] = config.personName
            prefs[KEY_PERSON_ROLE] = config.personRole
            prefs[KEY_PRIMARY_CONTACT_NAME] = config.primaryContact.name
            prefs[KEY_PRIMARY_CONTACT_PHONE] = config.primaryContact.phoneOrHandle
            prefs[KEY_SECONDARY_CONTACT_NAME] = config.secondaryContact.name
            prefs[KEY_SECONDARY_CONTACT_PHONE] = config.secondaryContact.phoneOrHandle
            prefs[KEY_EMERGENCY_CONTACT_NAME] = config.emergencyContact.name
            prefs[KEY_EMERGENCY_CONTACT_PHONE] = config.emergencyContact.phoneOrHandle
            prefs[KEY_LOCAL_EMERGENCY_NUMBER] = config.localEmergencyNumber
            prefs[KEY_HOME_ADDRESS] = config.homeInfo.address
            prefs[KEY_BUILDING_SECURITY] = config.homeInfo.buildingSecurityContact
            prefs[KEY_ACCESS_INSTRUCTIONS] = config.homeInfo.accessInstructions
            prefs[KEY_ALLERGIES] = config.medicalInfo.allergies
            prefs[KEY_BLOOD_GROUP] = config.medicalInfo.bloodGroup
            prefs[KEY_MEDICATIONS] = config.medicalInfo.medications
            prefs[KEY_DOCTOR] = config.medicalInfo.doctorContact
            prefs[KEY_MEDICAL_NOTES] = config.medicalInfo.notes
            prefs[KEY_NIGHT_MODE] = config.nightModeSchedule.enabled
            prefs[KEY_CHECKIN_INTERVAL_HOURS] = config.checkInIntervalHours
            prefs[KEY_CHECKIN_GRACE_MINUTES] = config.checkInGracePeriodMinutes
            prefs[KEY_CHECKIN_SCHED_ENABLED] = config.checkInSchedule.enabled
            prefs[KEY_CHECKIN_SCHEDULE_TIMES] = encodeTimes(config.checkInSchedule.dailyTimes)
            prefs[KEY_CHECKIN_TIMEOUT_MINUTES] = config.checkInSchedule.timeoutMinutes
            prefs[KEY_NIGHT_START_MINUTE] = config.nightModeSchedule.startMinute
            prefs[KEY_NIGHT_END_MINUTE] = config.nightModeSchedule.endMinute
            prefs[KEY_BATTERY_LOW_PERCENT] = config.batteryThresholds.lowPercent
            prefs[KEY_BATTERY_CRITICAL_PERCENT] = config.batteryThresholds.criticalPercent
            prefs[KEY_ACK_SAFETY_NOTICE] = config.hasAcknowledgedSafetyNotice
        }
    }

    suspend fun setNightMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NIGHT_MODE] = enabled
        }
    }

    suspend fun recordCheckIn(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CHECKIN] = timestamp
            prefs[KEY_SAFETY_STATUS] = SafetyStatus.SAFE.name
        }
    }

    suspend fun setSafetyStatus(status: SafetyStatus) {
        dataStore.edit { prefs ->
            prefs[KEY_SAFETY_STATUS] = status.name
        }
    }

    suspend fun acknowledgeSafetyNotice() {
        dataStore.edit { prefs ->
            prefs[KEY_ACK_SAFETY_NOTICE] = true
        }
    }
}