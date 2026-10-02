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
import kotlinx.coroutines.flow.map
import org.homeguard.core.model.AppConfig
import org.homeguard.core.model.ContactInfo
import org.homeguard.core.model.DeviceMode
import org.homeguard.core.model.HomeInfo
import org.homeguard.core.model.MedicalInfo
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
        val KEY_ACOUSTIC_DETECTION = booleanPreferencesKey("acoustic_detection_enabled")
        val KEY_CHECKIN_INTERVAL_HOURS = intPreferencesKey("checkin_interval_hours")
        val KEY_CHECKIN_GRACE_MINUTES = intPreferencesKey("checkin_grace_minutes")
        val KEY_LAST_CHECKIN = longPreferencesKey("last_checkin_timestamp")
        val KEY_SAFETY_STATUS = stringPreferencesKey("current_safety_status")
    }

    /**
     * Stable device identity. Previously this fell back to a fresh UUID on every emission, so any
     * event recorded before setup completed carried an ID that no longer existed.
     */
    val deviceIdFlow: Flow<String> = dataStore.data
        .map { prefs -> prefs[KEY_DEVICE_ID] ?: UUID.randomUUID().toString() }
        .distinctUntilChanged()

    /** Persists the device ID on first call so it survives process death and reboots. */
    suspend fun ensureDeviceId(): String {
        var resolved = ""
        dataStore.edit { prefs ->
            val existing = prefs[KEY_DEVICE_ID]
            resolved = existing ?: UUID.randomUUID().toString().also { prefs[KEY_DEVICE_ID] = it }
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
            isAcousticDetectionEnabled = prefs[KEY_ACOUSTIC_DETECTION] ?: false,
            checkInIntervalHours = prefs[KEY_CHECKIN_INTERVAL_HOURS] ?: 24,
            checkInGracePeriodMinutes = prefs[KEY_CHECKIN_GRACE_MINUTES] ?: 60
        )
    }

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
            prefs[KEY_NIGHT_MODE] = config.isNightModeEnabled
            prefs[KEY_ACOUSTIC_DETECTION] = config.isAcousticDetectionEnabled
            prefs[KEY_CHECKIN_INTERVAL_HOURS] = config.checkInIntervalHours
            prefs[KEY_CHECKIN_GRACE_MINUTES] = config.checkInGracePeriodMinutes
        }
    }

    suspend fun setAcousticDetectionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ACOUSTIC_DETECTION] = enabled
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
}
