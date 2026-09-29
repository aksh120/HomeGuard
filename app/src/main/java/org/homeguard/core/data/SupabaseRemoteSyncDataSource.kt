package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.homeguard.BuildConfig
import org.homeguard.core.database.entity.PendingEventEntity
import org.homeguard.core.model.EventType
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
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
) : RemoteSyncDataSource {

    override suspend fun sendEvent(event: PendingEventEntity): Result<Unit> = withContext(Dispatchers.IO) {
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            Log.i("SupabaseSync", "Supabase credentials not configured. Operating in local mock mode.")
            return@withContext Result.success(Unit)
        }

        try {
            val jsonPayload = try {
                JSONObject(event.payloadJson)
            } catch (e: Exception) {
                JSONObject()
            }
            val deviceId = jsonPayload.optString("deviceId", "unknown_device")
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
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(Unit)
        }

        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("battery_level", batteryLevel)
                put("is_charging", isCharging)
                put("last_seen_at", formatIso8601(System.currentTimeMillis()))
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
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return@withContext Result.success(Unit)
        }

        try {
            val body = JSONObject().apply {
                put("device_id", deviceId)
                put("device_mode", deviceMode)
                put("person_name", personName)
                put("person_role", personRole)
            }
            postToSupabase("devices", body.toString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postToSupabase(table: String, jsonBody: String) {
        val cleanUrl = supabaseUrl.trimEnd('/')
        val url = URL("$cleanUrl/rest/v1/$table")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Authorization", "Bearer $anonKey")
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
