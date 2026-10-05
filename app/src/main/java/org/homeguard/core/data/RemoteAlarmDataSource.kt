package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.homeguard.core.model.ApprovalPolicy
import org.homeguard.core.model.RemoteAlarm
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The caregiver-raised alarm channel (Phase 5, decision D7).
 *
 * Two halves, deliberately separate because they fail differently:
 *
 *  - Raising the alarm is a server-side write. It notifies the resident's contacts immediately and
 *    does not depend on the resident's phone being alive. This is the part that must not fail.
 *  - Delivering it to the resident's phone is best effort: push first, poll as a fallback. If the
 *    phone is dead, flat or out of network the contacts have still been told.
 *
 * Duplication is avoided by claiming: both paths call [claimRemoteCommand], which is a single
 * conditional update. Exactly one caller receives a row, so a push followed by a poll cannot sound
 * the siren twice.
 */
class RemoteAlarmDataSource(
    private val authClient: SupabaseAuthClient,
    private val supabaseUrl: String
) {

    /**
     * Raises an alarm for [targetUserId].
     *
     * The whole operation is one server-side function, so the command and the emergency notification
     * are created together or not at all. Doing it as two client writes could leave an alarm that
     * notifies nobody, or an emergency that alarms nobody.
     */
    suspend fun raiseAlarm(
        targetUserId: String,
        householdId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val body = JSONObject().apply {
            put("target", targetUserId)
            put("household", householdId)
        }
        when (val result = request("POST", "rpc/request_remote_alarm", body.toString(), token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                val commandId = result.rows.firstOrNull()?.optString("command_id")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(
                        IllegalStateException("Alarm was raised but no command id was returned")
                    )
                Result.success(commandId)
            }
        }
    }

    /**
     * Pending alarms addressed to this device.
     *
     * The fallback path. Called from the periodic sync so an alarm still arrives when the push was
     * dropped, the device was offline, or the caregiver's phone has no Google Play Services.
     */
    suspend fun pendingAlarms(limit: Int = 5): Result<List<RemoteAlarm>> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val path = "remote_commands" +
                "?kind=eq.$KIND_RAISE_ALARM&status=eq.PENDING&order=created_at.desc&limit=$limit"
            when (val result = request("GET", path, null, token)) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success -> Result.success(result.rows.map { it.toRemoteAlarm() })
            }
        }

    /**
     * Takes ownership of a command, or returns null if another path already has it.
     *
     * Null is the normal, expected outcome for the loser of a race and must not be treated as an
     * error. Treating it as one would surface a benign duplicate delivery as a failure to the user.
     */
    suspend fun claimRemoteCommand(commandId: String): Result<RemoteAlarm?> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val body = JSONObject().apply { put("command", commandId) }
            when (
                val result = request("POST", "rpc/claim_remote_command", body.toString(), token)
            ) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success -> {
                    val row = result.rows.firstOrNull()
                    if (row == null) {
                        Log.i(TAG, "Command $commandId was already claimed by another path")
                        Result.success(null)
                    } else {
                        Result.success(row.toRemoteAlarm())
                    }
                }
            }
        }

    /**
     * Records that an alarm finished sounding, or was dismissed before it started.
     *
     * Only the target device may call this, enforced server-side, so a caregiver cannot mark their
     * own alarm as delivered.
     */
    suspend fun completeRemoteCommand(
        commandId: String,
        executed: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val body = JSONObject().apply {
            put("command", commandId)
            put("outcome", if (executed) "EXECUTED" else "CANCELLED")
        }
        when (
            val result = request("POST", "rpc/complete_remote_command", body.toString(), token)
        ) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(Unit)
        }
    }

    /**
     * The display name of the caregiver who raised an alarm, or an empty string if unknown.
     *
     * [org.homeguard.core.model.RemoteAlarm.requestedBy] is an auth UUID, and a phone that reads
     * "Asked by 7f3a91c4-…" is worse than one that omits the name: it looks broken and it tells the
     * resident nothing. The resident is a member of the same household, so it can already read the
     * household's devices, and the caregiver's name is on theirs.
     *
     * Returns empty rather than failing the alarm. Who asked is context; that somebody asked is the
     * message, and the siren must not wait on a name lookup that might be slow.
     */
    suspend fun requesterDisplayName(householdId: String, requesterId: String): String =
        withContext(Dispatchers.IO) {
            if (householdId.isBlank() || requesterId.isBlank()) return@withContext ""
            val token = authClient.accessToken() ?: return@withContext ""
            val path = "devices" +
                "?household_id=eq.${URLEncoder.encode(householdId, "UTF-8")}" +
                "&user_id=eq.${URLEncoder.encode(requesterId, "UTF-8")}" +
                "&select=person_name&limit=1"
            when (val result = request("GET", path, null, token)) {
                is HttpResult.Failure -> {
                    Log.w(TAG, "Could not resolve the requesting caregiver's name: ${result.error.message}")
                    ""
                }
                is HttpResult.Success -> result.rows.firstOrNull()
                    ?.optString("person_name")
                    ?.trim()
                    .orEmpty()
            }
        }

    // --- transport, matching MonitoringRemoteDataSource ------------------------------------

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
                return@withContext HttpResult.Failure(
                    IllegalStateException("Supabase rejected the session (HTTP $code)")
                )
            }
            if (code == 429) {
                return@withContext HttpResult.Failure(
                    RemoteRateLimitedException(conn.getHeaderField("Retry-After")?.toLongOrNull())
                )
            }
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext HttpResult.Failure(
                    IllegalStateException(humanise(code, err))
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
            Log.e(TAG, "$method $path failed: ${e.message}")
            HttpResult.Failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Turns the server's message into something a caregiver can act on.
     *
     * The rate limit is a normal outcome rather than an error, and the raw Postgres message for it is
     * not something to show someone who just wants to help.
     */
    private fun humanise(status: Int, body: String): String {
        val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
        return when {
            message.contains("already raised", ignoreCase = true) ->
                "An alarm was already raised for this person in the last 15 minutes."
            message.contains("cannot raise an alarm", ignoreCase = true) ->
                "You cannot raise an alarm for that device."
            message.contains("not part of this home", ignoreCase = true) ->
                "That device is no longer part of this home."
            message.isNotBlank() -> message
            else -> "The request failed (HTTP $status)."
        }
    }

    companion object {
        /**
         * The wire value of the alarm command kind.
         *
         * Shared with [org.homeguard.core.model.ApprovalPolicy] consumers and the push receiver so the
         * three places that have to agree cannot drift. Deliberately not an [ApprovalKind] value: an
         * alarm carries no approval code and must never be listed as something a resident consents to.
         */
        const val KIND_RAISE_ALARM = "RAISE_ALARM"

        private const val TAG = "RemoteAlarm"
    }
}

/**
 * Parses a claimed command row. [requested_by] identifies the caregiver that raised it.
 *
 * Parsed as an alarm rather than an [org.homeguard.core.model.ApprovalRequest] on purpose: an alarm is
 * not a consent request, and routing it through the approvals model would have made it eligible to
 * appear in the resident's approvals list.
 */
private fun JSONObject.toRemoteAlarm(): RemoteAlarm = RemoteAlarm(
    id = optString("id"),
    householdId = optStringOrNull("household_id").orEmpty(),
    requestedBy = optStringOrNull("requested_by").orEmpty()
)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }