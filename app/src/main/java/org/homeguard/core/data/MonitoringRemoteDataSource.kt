package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.homeguard.core.model.Household
import org.homeguard.core.model.HouseholdMember
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.RemoteCheckIn
import org.homeguard.core.model.RemoteEmergencyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.random.Random

/**
 * Remote access for the monitoring (caregiver) side, plus household create/join.
 *
 * Strictly read-only for a household's watched data. An emergency row must originate from the
 * device that raised it; letting a caregiver write one would make safety alerts forgeable, so the
 * backend policies grant SELECT only and this class exposes no write path for watched rows.
 *
 * The home device uses the same session to create and publish its household.
 */
class MonitoringRemoteDataSource(
    private val authClient: SupabaseAuthClient,
    private val supabaseUrl: String
) {

    // --- household lifecycle ------------------------------------------------------------

    suspend fun createHousehold(name: String): Result<Household> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val userId = authClient.currentUserId()
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No active session"))
        }
        val body = JSONObject().apply {
            put("name", name.ifBlank { "Home" })
            put("invite_code", generateInviteCode())
            put("created_by", userId)
        }
        when (val result = request("POST", "households", body.toString(), token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                val row = result.rows.firstOrNull()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Household creation returned no row")
                    )
                Result.success(
                    Household(
                        id = row.optString("id"),
                        name = row.optString("name"),
                        inviteCode = row.optString("invite_code")
                    )
                )
            }
        }
    }

    suspend fun findHouseholdByCode(inviteCode: String): Result<Household> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val encoded = URLEncoder.encode(inviteCode.trim(), "UTF-8")
            when (val result = request("GET", "households?invite_code=eq.$encoded&select=*", null, token)) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success -> {
                    val row = result.rows.firstOrNull()
                        ?: return@withContext Result.failure(
                            IllegalStateException("No household matches that code")
                        )
                    Result.success(
                        Household(
                            id = row.optString("id"),
                            name = row.optString("name"),
                            inviteCode = row.optString("invite_code")
                        )
                    )
                }
            }
        }

    /**
     * Joins the caller to a household.
     *
     * The `join_household` insert policy permits only rows whose user_id matches the caller, so
     * this cannot be used to enrol somebody else.
     */
    suspend fun joinHousehold(
        householdId: String,
        deviceId: String,
        role: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val userId = authClient.currentUserId()
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No active session"))
        }
        val body = JSONObject().apply {
            put("household_id", householdId)
            put("user_id", userId)
            put("device_id", deviceId)
            put("role", role)
        }
        when (val result = request("POST", "household_members", body.toString(), token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(Unit)
        }
    }

    /** Links this device into a household so a caregiver can find it. */
    suspend fun attachDeviceToHousehold(
        householdId: String,
        deviceId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val body = JSONObject().apply { put("household_id", householdId) }
        val path = "devices?device_id=eq.${URLEncoder.encode(deviceId, "UTF-8")}"
        when (val result = request("PATCH", path, body.toString(), token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(Unit)
        }
    }

    // --- monitoring reads ---------------------------------------------------------------

    suspend fun listMembers(householdId: String): Result<List<HouseholdMember>> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val q = "household_id=eq.${URLEncoder.encode(householdId, "UTF-8")}"
            when (
                val result = request(
                    "GET",
                    "household_members?$q&select=device_id,role", null, token
                )
            ) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success -> Result.success(
                    result.rows.map {
                        HouseholdMember(
                            deviceId = it.optString("device_id"),
                            role = it.optString("role")
                        )
                    }
                )
            }
        }

    suspend fun listDevices(householdId: String): Result<List<MonitoredDevice>> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val q = "household_id=eq.${URLEncoder.encode(householdId, "UTF-8")}"
            when (
                val result = request("GET", "devices?$q&select=*,device_status(*)", null, token)
            ) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success -> Result.success(result.rows.map { it.toMonitoredDevice() })
            }
        }

    suspend fun listEmergencyEvents(
        householdId: String,
        limit: Int = 20
    ): Result<List<RemoteEmergencyEvent>> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val q = "household_id=eq.${URLEncoder.encode(householdId, "UTF-8")}"
        when (
            val result = request(
                "GET",
                "emergency_events?$q&select=*&order=triggered_at.desc&limit=$limit", null, token
            )
        ) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(
                result.rows.map {
                    RemoteEmergencyEvent(
                        id = it.optString("id"),
                        deviceId = it.optString("device_id"),
                        triggeredAt = it.optString("triggered_at"),
                        details = it.optString("details"),
                        resolvedAt = it.optStringOrNull("resolved_at")
                    )
                }
            )
        }
    }

    suspend fun listCheckIns(
        householdId: String,
        limit: Int = 20
    ): Result<List<RemoteCheckIn>> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val q = "household_id=eq.${URLEncoder.encode(householdId, "UTF-8")}"
        when (
            val result = request(
                "GET", "check_ins?$q&select=*&order=confirmed_at.desc&limit=$limit", null, token
            )
        ) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(
                result.rows.map {
                    RemoteCheckIn(
                        id = it.optString("id"),
                        deviceId = it.optString("device_id"),
                        confirmedAt = it.optString("confirmed_at")
                    )
                }
            )
        }
    }

    // --- transport -----------------------------------------------------------------------

    private sealed interface HttpResult {
        data class Success(val rows: List<JSONObject>) : HttpResult
        data class Failure(val error: Throwable) : HttpResult
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String?,
        token: String
    ): HttpResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("${supabaseUrl.trimEnd('/')}/rest/v1/$path")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("apikey", token)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", "return=representation")
                connectTimeout = 15000
                readTimeout = 15000
                if (body != null) doOutput = true
            }
            if (body != null) conn.outputStream.bufferedWriter().use { it.write(body) }

            val code = conn.responseCode
            if (code == 401 || code == 403) {
                authClient.invalidate()
                val rejected = IllegalStateException("Supabase rejected the session (HTTP $code)")
                return@withContext HttpResult.Failure(rejected)
            }
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext HttpResult.Failure(
                    IllegalStateException("HTTP $code from $method $path: $err")
                )
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.isBlank()) return@withContext HttpResult.Success(emptyList())
            val array = runCatching { JSONArray(text) }.getOrNull()
            HttpResult.Success(
                if (array != null) {
                    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                } else {
                    listOfNotNull(runCatching { JSONObject(text) }.getOrNull())
                }
            )
        } catch (e: Exception) {
            Log.e("MonitoringRemote", "$method $path failed: ${e.message}")
            HttpResult.Failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        /**
         * Invite codes avoid ambiguous characters (0/O, 1/I) because they are read aloud or
         * retyped from a text message.
         */
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val CODE_LENGTH = 8

        fun generateInviteCode(random: Random = Random.Default): String =
            (1..CODE_LENGTH)
                .map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
                .joinToString("")
                .let { it } // already uppercase and unambiguous

        /** Normalises user input: uppercase, strip anything not in the alphabet. */
        fun normaliseInviteCode(input: String): String =
            input.trim().uppercase(Locale.ROOT).filter { CODE_ALPHABET.contains(it) }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.toMonitoredDevice(): MonitoredDevice {
    val status = optJSONObject("device_status")
    return MonitoredDevice(
        deviceId = optString("device_id"),
        personName = optString("person_name"),
        personRole = optString("person_role"),
        deviceMode = optString("device_mode"),
        batteryLevel = status?.optInt("battery_level", -1) ?: -1,
        isCharging = status?.optBoolean("is_charging", false) ?: false,
        isInternetAvailable = status?.optBoolean("is_internet_available", false) ?: false,
        safetyStatus = status?.optString("safety_status").orEmpty(),
        lastSeenAt = status?.optString("last_seen_at").orEmpty()
    )
}