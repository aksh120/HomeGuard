package org.homeguard.core.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bootstraps a Supabase anonymous session.
 *
 * Required because the RLS policies in backend/schema.sql are scoped to auth.uid(). The anon key
 * alone is not an identity: without a session every request is rejected by the policies.
 *
 * Each install gets its own auth user, so one device can never read another's rows.
 */
class SupabaseAuthClient(
    private val supabaseUrl: String,
    private val anonKey: String
) {

    private val mutex = Mutex()

    @Volatile
    private var cachedAccessToken: String? = null

    fun isConfigured(): Boolean = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

    /**
     * True once an anonymous session has been obtained.
     *
     * Surfaced so the UI can distinguish "signed in" from "cannot reach auth", which otherwise look
     * identical: a failed session makes every request throw RemoteNotConfiguredException.
     */
    fun hasSession(): Boolean = cachedAccessToken != null

    /** Forces a fresh session on the next call. Used when a request is rejected as unauthorised. */
    fun invalidate() {
        cachedAccessToken = null
    }

    /** Access token, or null when no session could be established. */
    suspend fun accessToken(): String? {
        if (!isConfigured()) return null
        cachedAccessToken?.let { return it }
        return mutex.withLock {
            cachedAccessToken ?: requestAnonymousSession()?.also { cachedAccessToken = it }
        }
    }

    /**
 * The `sub` claim of the current session, i.e. this install's auth user id.
 *
 * Read from the JWT payload rather than a second network call, so joining a household costs one
 * request. Returns an empty string when there is no session; callers must treat that as an error
 * rather than sending an empty user_id.
 */
fun currentUserId(): String {
        val token = cachedAccessToken ?: return ""
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return ""
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or
                    android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            )
            JSONObject(payload).optString("sub")
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Could not read the session subject: ${e.message}")
            ""
        }
    }

    private suspend fun requestAnonymousSession(): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("${supabaseUrl.trimEnd('/')}/auth/v1/signup")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("apikey", anonKey)
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
            }
            // An empty body asks Supabase for an anonymous user.
            OutputStreamWriter(conn.outputStream).use { it.write("{}") }

            val code = conn.responseCode
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("SupabaseAuth", "Anonymous sign-in failed: HTTP $code $error")
                return@withContext null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optString("access_token").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Anonymous sign-in error: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}