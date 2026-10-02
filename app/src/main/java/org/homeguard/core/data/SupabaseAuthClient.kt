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

    /** Returns a usable access token, refreshing it if the cached one has expired. */
    suspend fun accessToken(): String? {
        if (!isConfigured()) return null
        cachedAccessToken?.let { return it }
        return mutex.withLock {
            cachedAccessToken ?: requestAnonymousSession()?.also { cachedAccessToken = it }
        }
    }

    fun invalidate() {
        cachedAccessToken = null
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