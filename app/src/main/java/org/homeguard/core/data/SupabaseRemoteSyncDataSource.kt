package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.homeguard.BuildConfig
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.model.EventType
import org.homeguard.core.model.SafetyStatus
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SupabaseRemoteSyncDataSource(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val authClient: SupabaseAuthClient = SupabaseAuthClient(supabaseUrl, anonKey)
) : RemoteSyncDataSource {

    val isConfigured: Boolean get() = authClient.isConfigured()

    /** True once an anonymous session exists, so the UI can report "not signed in" separately. */
    fun hasSession(): Boolean = authClient.hasSession()

    override suspend fun sendEvent(event: PendingEventEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            Log.w("SupabaseSync", "Supabase credentials not configured; keeping event queued.")
            return@withContext Result.failure(RemoteNotConfiguredException())
        }

        try {
            val jsonPayload = try {
                JSONObject(event.payloadJson)
            } catch (e: Exception) {
                Log.e("SupabaseSync", "Corrupt payload for event ${event.id}", e)
                return@withContext Result.failure(e)
            }
            val deviceId = jsonPayload.optString("deviceId", "").ifBlank {
                Log.e("SupabaseSync", "Event ${event.id} has no deviceId; cannot deliver.")
                return@withContext Result.failure(IllegalStateException("Missing deviceId"))
            }
            val isoDate = formatIso8601(event.timestamp)

            val (endpoint, requestBody) = when (event.eventType) {
                EventType.EMERGENCY_SOS.name -> {
                    val body = JSONObject().apply {
                        put("id", event.id)
                        put("device_id", deviceId)
                        put("triggered_at", isoDate)
                        put("details", jsonPayload.optString("details", ""))
                    }
                    Pair("emergency_events", body.toString())
                }
                EventType.CHECK_IN.name -> {
                    val body = JSONObject().apply {
                        put("id", event.id)
                        put("device_id", deviceId)
                        put("confirmed_at", isoDate)
                    }
                    Pair("check_ins", body.toString())
                }
                else -> {
                    // Generic telemetry/status
                    Pair("device_status", jsonPayload.toString())
                }
            }

            postToSupabase(endpoint, requestBody)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Error sending event ${event.id} to Supabase: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun sendHeartbeat(
        deviceId: String,
        batteryLevel: Int,
        isCharging: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(RemoteNotConfiguredException())
        }

        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("battery_level", batteryLevel)
                put("is_charging", isCharging)
                put("last_seen_at", formatIso8601(System.currentTimeMillis()))
                // Columns are NOT NULL in the schema; an insert missing them would be rejected.
                put("is_wifi_connected", true)
                put("is_internet_available", true)
                put("safety_status", SafetyStatus.UNKNOWN.name)
            }
            postToSupabase("device_status", body.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerDevice(
        deviceId: String,
        deviceMode: String,
        personName: String,
        personRole: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(RemoteNotConfiguredException())
        }

        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("device_mode", deviceMode)
                put("person_name", personName)
                put("person_role", personRole)
            }
            // merge-duplicates keeps this idempotent across repeated launches.
            postToSupabase("devices", body.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Publishes the primary monitoring contact so the backend can notify them.
     *
     * Without this the notification webhook has no recipient. Sections 11 and 14 require the
     * monitoring contact to be told about an SOS or a missed check-in, but nothing in the schema
     * previously linked a person to a device. Upserted by device_id so repeated saves do not
     * create duplicates.
     */
    suspend fun syncMonitoringContact(
        deviceId: String,
        name: String,
        email: String?,
        phone: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(RemoteNotConfiguredException())
        }
        // Section 51: only publish a contact when there is a way to reach them.
        if (name.isBlank() || (email.isNullOrBlank() && phone.isNullOrBlank())) {
            Log.i("SupabaseSync", "No monitoring contact configured; nothing to publish.")
            return@withContext Result.success(Unit)
        }
        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("name", name)
                put("email", email?.takeIf { it.isNotBlank() })
                put("phone", phone?.takeIf { it.isNotBlank() })
                put("is_primary", true)
            }
            postToSupabase("monitoring_contacts", body.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Could not publish monitoring contact: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Stamps the household id onto this device's row.
     *
     * Without it the device is invisible to caregivers even after they have joined the household,
     * because every read filters on household_id.
     */
    suspend fun sendHouseholdId(
        householdId: String,
        deviceId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(RemoteNotConfiguredException())
        try {
            val body = JSONObject().apply { put("household_id", householdId) }
            postToSupabase(
                "devices?device_id=eq.${java.net.URLEncoder.encode(deviceId, "UTF-8")}",
                body.toString(),
                method = "PATCH"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Could not attach device to household: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun postToSupabase(
        table: String,
        jsonBody: String,
        method: String = "POST"
    ) {
        // RLS is scoped to auth.uid(), so an unauthenticated request would be rejected outright.
        val token = authClient.accessToken()
            ?: throw RemoteNotConfiguredException()

        val cleanUrl = supabaseUrl.trimEnd('/')
        val url = URL("$cleanUrl/rest/v1/$table")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 401 || responseCode == 403) {
                // Token rejected: drop it so the next attempt re-authenticates.
                authClient.invalidate()
                throw IllegalStateException("Supabase rejected the session (HTTP $responseCode)")
            }
            if (responseCode == 429) {
                // Throttling. Distinct from a missing session so the caller does not report the
                // user's backend as broken, and so queued events do not lose their retries.
                throw RemoteRateLimitedException(
                    conn.getHeaderField("Retry-After")?.toLongOrNull()
                )
            }
            if (responseCode !in 200..299) {
                val errorStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IllegalStateException("Supabase returned HTTP $responseCode: $errorStream")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun formatIso8601(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(timestamp))
    }
}