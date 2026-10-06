package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.homeguard.core.model.ApprovalKind
import org.homeguard.core.model.ApprovalOutcome
import org.homeguard.core.model.ApprovalPolicy
import org.homeguard.core.model.ApprovalRequest
import org.homeguard.core.model.ApprovalStatus
import org.homeguard.core.model.Household
import org.homeguard.core.model.HouseholdMember
import org.homeguard.core.model.JoinRequestOutcome
import org.homeguard.core.model.MonitoredDevice
import org.homeguard.core.model.MyRemoteRequest
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

    /**
 * Creates a household and enrols this device as its first member in one call.
 *
 * Uses the create_household_with_membership RPC rather than inserting into `households` directly.
 * A direct insert with return=representation always failed with 42501: RETURNING is evaluated
 * against the table's SELECT policy, which requires existing membership, so the freshly created row
 * was invisible to the caller that had just created it. The previous two-request sequence could also
 * leave a household with no member, which nobody could then see or join.
 *
 * created_by is filled from the request JWT by a trigger, so ownership cannot be forged and is not
 * accepted as a parameter here.
 */
suspend fun createHousehold(name: String, deviceId: String): Result<Household> =
    withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val body = JSONObject().apply {
            put("household_name", name.ifBlank { "Home" })
            put("code", generateInviteCode())
            put("member_device_id", deviceId)
        }
        when (
            val result = request(
                "POST",
                "rpc/create_household_with_membership",
                body.toString(),
                token
            )
        ) {
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

    /**
     * Resolves an invite code to a household, for showing a name before asking to watch it.
     *
     * Uses the find_household_by_invite_code RPC rather than reading `households` directly. The
     * direct read is impossible by construction: the SELECT policy requires existing membership, and
     * a prospective caregiver has none. That made joining unreachable regardless of client code.
     *
     * Note this resolves a code to a household for any caller who holds it, and is not used to
     * establish membership. request_household_join does its own resolution internally, so nothing
     * about joining depends on this being reachable.
     *
     * The code is the credential here, so a miss returns the same message whether the code is wrong
     * or belongs to a household that no longer exists.
     */
    suspend fun findHouseholdByCode(inviteCode: String): Result<Household> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val body = JSONObject().apply {
                put("code", inviteCode.trim().uppercase(Locale.ROOT))
            }
            when (val result = request("POST", "rpc/find_household_by_invite_code", body.toString(), token)) {
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
     * Asks to watch a household, using the invite code as the only credential.
     *
     * Replaces a direct insert into household_members, which is now refused by the database. That
     * insert made anybody holding an invite code a member immediately, which meant the approval code
     * could never be the thing that grants access.
     *
     * The client deliberately learns nothing here except how many people must agree: no household id,
     * no user ids. The code is the credential, so it is also the only thing sent.
     */
    suspend fun requestHouseholdJoin(
        inviteCode: String,
        deviceId: String
    ): Result<JoinRequestOutcome> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val body = JSONObject().apply {
            put("submitted_code", MonitoringRemoteDataSource.normaliseInviteCode(inviteCode))
            put("joiner_device_id", deviceId)
        }
        when (val result = request("POST", "rpc/request_household_join", body.toString(), token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                val row = result.rows.firstOrNull()
                    ?: return@withContext Result.failure(
                        IllegalStateException("The request was accepted but nothing came back")
                    )
                Result.success(
                    JoinRequestOutcome(
                        householdId = row.optString("household_id"),
                        householdName = row.optString("household_name"),
                        reportedApprovals = row.optInt("approvals_required", 1)
                    )
                )
            }
        }
    }

    /**
     * Asks to stop watching a household.
     *
     * Clears nothing locally on its own: the local record is only dropped once the resident's code has
     * been entered, so a refused request leaves the dashboard exactly as it was.
     */
    suspend fun requestHouseholdLeave(householdId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val body = JSONObject().apply { put("target_household", householdId) }
            when (
                val result = request(
                    "POST",
                    "rpc/request_household_leave",
                    body.toString(),
                    token
                )
            ) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success ->
                    Result.success(result.rows.firstOrNull()?.optInt("approvals_required", 1) ?: 1)
            }
        }

    /**
     * Asks to turn this phone from a home device into a monitoring device.
     *
     * Consent comes from the people currently watching, so the codes appear on their dashboards, not
     * here. Nothing changes locally until the request completes.
     */
    suspend fun requestModeSwitch(): Result<Int> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        when (val result = request("POST", "rpc/request_mode_switch", "{}", token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success ->
                Result.success(result.rows.firstOrNull()?.optInt("approvals_required", 1) ?: 1)
        }
    }

    /**
     * Requests this install filed, and their state.
     *
     * Reads a SECURITY DEFINER function rather than the table, because migration 017 removed
     * `requested_by` from the SELECT policy: the requester may not read their own row, since the row
     * contains the approval code. This returns the state without the code.
     */
    suspend fun listMyRequests(): Result<List<MyRemoteRequest>> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        when (val result = request("GET", "rpc/list_my_remote_commands", null, token)) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> Result.success(
                result.rows.map {
                    MyRemoteRequest(
                        id = it.optString("id"),
                        kind = ApprovalKind.fromWire(it.optString("kind")) ?: ApprovalKind.APPROVE_JOIN,
                        status = ApprovalStatus.fromWire(it.optString("status"))
                            ?: ApprovalStatus.CANCELLED,
                        createdAt = it.optString("created_at"),
                        householdName = it.optString("household_name"),
                        targetName = it.optString("target_name")
                    )
                }
            )
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

    // --- approval requests (phase 3, decision D4) --------------------------------------------
    //
    // There is deliberately no requestApproval() here any more. It inserted straight into
    // remote_commands, which the database now refuses, and nothing ever called it: every UI screen
    // that needed a request used requestHouseholdJoin or requestModeSwitch instead. Filing a request
    // naming an arbitrary target and household was also a way to fill a stranger's approvals screen
    // without holding their invite code.

    /**
     * Approval requests addressed to this install.
     *
     * Only ever returns requests targeting the caller, per the read policy, so this cannot be used to
     * peek at someone else's pending requests or their approval codes.
     */
    suspend fun listApprovalsForSelf(includeResolved: Boolean = false): Result<List<ApprovalRequest>> =
        withContext(Dispatchers.IO) {
            val token = authClient.accessToken()
                ?: return@withContext Result.failure(RemoteNotConfiguredException())
            val statusFilter = if (includeResolved) {
                "status=neq.CANCELLED"
            } else {
                "status=eq.PENDING"
            }
            // Phase 5: alarms share this table but are not approval requests. They must never appear
            // here, where the resident could read one as somebody asking permission to watch them.
            // Excluding them server-side rather than filtering the result also means an alarm cannot
            // accidentally consume a slot in the approvals UI.
            val kindFilter = "kind=in.(APPROVE_JOIN,APPROVE_REMOVE,APPROVE_MODE_SWITCH)"
            when (
                val result = request(
                    "GET",
                    "remote_commands?$statusFilter&$kindFilter&select=*&order=created_at.desc",
                    null,
                    token
                )
            ) {
                is HttpResult.Failure -> Result.failure(result.error)
                is HttpResult.Success -> Result.success(
                    result.rows.mapNotNull { it.toApprovalRequest() }
                )
            }
        }

    /**
     * Submits a code for approval.
     *
     * The membership change is performed by the database function, not here. This client cannot
     * approve anything by itself, only present a code.
     *
     * A code is rarely the last one. With two people at home, a join produces two codes, and typing
     * the first records that person has agreed without granting anything yet. The outcome carries
     * [ApprovalOutcome.completed] and how many are still outstanding so the UI can say "one more" rather
     * than showing a success the user does not yet have.
     */
    suspend fun submitApprovalCode(code: String): Result<ApprovalOutcome> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val normalised = ApprovalPolicy.normaliseCode(code)
        if (!ApprovalPolicy.isComplete(normalised)) {
            return@withContext Result.failure(
                IllegalArgumentException("An approval code is ${ApprovalPolicy.CODE_LENGTH} characters")
            )
        }
        val body = JSONObject().apply { put("submitted_code", normalised) }
        when (
            val result = request(
                "POST",
                "rpc/approve_remote_command",
                body.toString(),
                token
            )
        ) {
            is HttpResult.Failure -> Result.failure(result.error)
            is HttpResult.Success -> {
                val row = result.rows.firstOrNull()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Approval returned no result")
                    )
                val raw = row.optString("approved_kind")
                val kind = ApprovalKind.fromWire(raw)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Unknown approval type: $raw")
                    )
                Result.success(
                    ApprovalOutcome(
                        kind = kind,
                        completed = row.optBoolean("completed", false),
                        approvalsRequired = row.optInt("approvals_required", 0).coerceAtLeast(0)
                    )
                )
            }
        }
    }

    /** Withdraws a request before it is exercised. Without this a resident could never refuse. */
    suspend fun cancelApproval(commandId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authClient.accessToken()
            ?: return@withContext Result.failure(RemoteNotConfiguredException())
        val body = JSONObject().apply { put("command_id", commandId) }
        when (
            val result = request(
                "POST",
                "rpc/cancel_remote_command",
                body.toString(),
                token
            )
        ) {
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
        token: String,
        prefer: String = "return=representation"
    ): HttpResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("${supabaseUrl.trimEnd('/')}/rest/v1/$path")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("apikey", token)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Prefer", prefer)
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
            if (code == 429) {
                // Throttling, not a broken request. Reported distinctly so queued events keep their
                // retries and the UI can say "temporarily limited" instead of "not signed in".
                val retryAfter = conn.getHeaderField("Retry-After")?.toLongOrNull()
                return@withContext HttpResult.Failure(RemoteRateLimitedException(retryAfter))
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

private fun JSONObject.toApprovalRequest(): ApprovalRequest? {
    // Defence in depth behind the server-side kind filter: an alarm shares the remote_commands table
    // but is not a consent request, so it must never render in the approvals list.
    val rawKind = optString("kind")
    if (rawKind.equals(RemoteAlarmDataSource.KIND_RAISE_ALARM, ignoreCase = true)) return null
    return ApprovalRequest(
    id = optString("id"),
    kind = ApprovalKind.fromWire(rawKind) ?: ApprovalKind.APPROVE_JOIN,
    status = ApprovalStatus.fromWire(optString("status")) ?: ApprovalStatus.PENDING,
    approvalCode = optStringOrNull("approval_code"),
    createdAt = optString("created_at"),
    requesterName = optStringOrNull("requested_by_name").orEmpty(),
    householdId = optStringOrNull("household_id")
    )
}

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
        lastSeenAt = status?.optString("last_seen_at").orEmpty(),
        // Phase 5: the alarm API addresses the person by auth UUID, not by device id.
        userId = if (isNull("user_id")) "" else optString("user_id"),
        householdId = if (isNull("household_id")) "" else optString("household_id")
    )
}