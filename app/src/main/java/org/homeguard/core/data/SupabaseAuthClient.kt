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
 * Raised when Supabase rate-limits a request.
 *
 * Deliberately distinct from [RemoteNotConfiguredException]. Both make a send fail and both must
 * leave events queued, but they mean opposite things to the user: "this build has no backend" is a
 * permanent condition, whereas "the backend is throttling us" is transient and resolves on its own.
 * Reporting both as "not signed in" is what made the original failure hard to diagnose.
 */
class RemoteRateLimitedException(
    val retryAfterSeconds: Long? = null
) : Exception("Supabase rate limit reached")

/**
 * True when the failure was in reaching the server, not in the event being sent.
 *
 * Drives whether a failure may consume an event's retry budget. A missing session or a throttled
 * server says nothing about the payload: the identical event will go through unchanged once the
 * connection recovers, so counting it against the event is pure loss. A rejected payload is the
 * opposite case and must still burn retries, or a permanently undeliverable event would sit at the
 * head of the queue forever.
 */
val Throwable.isTransientTransportFailure: Boolean
    get() = this is RemoteNotConfiguredException || this is RemoteRateLimitedException

/**
 * Where the anonymous session is kept between launches.
 *
 * Kept behind an interface so [SupabaseAuthClient] stays unit testable without DataStore, matching
 * how the rest of the data layer takes its collaborators.
 */
interface AuthSessionStore {
    suspend fun read(): StoredAuthSession?
    suspend fun write(session: StoredAuthSession)
    suspend fun clear()
}

data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    /** Treats a token as expired slightly early, to absorb clock skew between phone and server. */
    fun isExpired(nowMillis: Long, skewMillis: Long = 60_000): Boolean =
        expiresAtMillis - skewMillis <= nowMillis
}

/**
 * In-memory fallback, used when no persistent store is supplied.
 */
class InMemoryAuthSessionStore : AuthSessionStore {
    @Volatile
    private var session: StoredAuthSession? = null

    override suspend fun read(): StoredAuthSession? = session

    override suspend fun write(session: StoredAuthSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

/**
 * Bootstraps and maintains a Supabase anonymous session.
 *
 * Required because the RLS policies are scoped to auth.uid(). The anon key alone is not an identity:
 * without a session every request is rejected by the policies. Each install gets its own auth user,
 * so one device can never read another's rows.
 *
 * The original version cached the access token in memory only, so every cold start called
 * `/auth/v1/signup` again. Anonymous sign-up is rate limited per IP, so the device hammered it,
 * collected HTTP 429, and never recovered — 23 queued safety events sat on the phone undelivered.
 *
 * Now the refresh token is persisted, so a restart costs one refresh call instead of a new sign-up.
 * Sign-up happens at most once per install, and a 429 is answered with backoff rather than an
 * immediate retry.
 */
class SupabaseAuthClient(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val sessionStore: AuthSessionStore = InMemoryAuthSessionStore()
) {

    private val mutex = Mutex()

    @Volatile
    private var cached: StoredAuthSession? = null

    /** Consecutive rate-limit responses, used to derive the backoff delay. */
    @Volatile
    private var consecutiveRateLimits: Int = 0

    fun isConfigured(): Boolean = supabaseUrl.isNotBlank() && anonKey.isNotBlank()

    /**
     * True once an anonymous session has been obtained.
     *
     * Surfaced so the UI can distinguish "signed in" from "cannot reach auth", which otherwise look
     * identical: a failed session makes every request throw RemoteNotConfiguredException.
     */
    fun hasSession(): Boolean = cached?.accessToken?.isNotBlank() == true

    /**
     * True when the last failure was a rate limit rather than missing credentials.
     *
     * The distinction is what lets the sync card say "temporarily limited, retrying" instead of
     * "not signed in", which would send the user looking for a setting that is already correct.
     */
    fun isRateLimited(): Boolean = consecutiveRateLimits > 0

    /** Forces a fresh session on the next call. Used when a request is rejected as unauthorised. */
    fun invalidate() {
        cached = null
    }

    /**
     * Access token, or null when no session could be established.
     *
     * Order matters: reuse a live token, refresh an expired one, and only sign up when there is no
     * refresh token at all. A 429 during refresh or sign-up throws [RemoteRateLimitedException] so
     * callers can report it accurately instead of guessing why the send failed.
     */
    suspend fun accessToken(): String? {
        if (!isConfigured()) return null
        cached?.takeUnless { it.isExpired(System.currentTimeMillis()) }
            ?.let { return it.accessToken }

        return mutex.withLock {
            val now = System.currentTimeMillis()
            val stored = cached ?: sessionStore.read().also { if (it != null) cached = it }

            if (stored != null && !stored.isExpired(now)) {
                return@withLock stored.accessToken
            }

            // A refresh is preferred over a new sign-up: sign-up is what gets rate limited, and it
            // would also orphan the auth user that owns any rows already written.
            if (stored != null) {
                refreshSession(stored)?.let {
                    cached = it
                    sessionStore.write(it)
                    consecutiveRateLimits = 0
                    return@withLock it.accessToken
                }
            }

            requestAnonymousSession()?.let {
                cached = it
                sessionStore.write(it)
                consecutiveRateLimits = 0
                return@withLock it.accessToken
            }
            null
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
        val token = cached?.accessToken ?: return ""
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

    private suspend fun refreshSession(stored: StoredAuthSession): StoredAuthSession? =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("${supabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("apikey", anonKey)
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                }
                val body = JSONObject().apply { put("refresh_token", stored.refreshToken) }
                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val code = conn.responseCode
                if (code == 429) {
                    throw RemoteRateLimitedException(readRetryAfter(conn))
                }
                if (code !in 200..299) {
                    // A rejected refresh token means the session is unrecoverable, so the caller
                    // falls through to a fresh sign-up rather than retrying a dead credential.
                    Log.w("SupabaseAuth", "Refresh rejected (HTTP $code); will sign up again.")
                    return@withContext null
                }
conn.inputStream.bufferedReader().use { it.readText() }
                    .toSession(fallbackRefresh = stored.refreshToken)
            } catch (e: RemoteRateLimitedException) {
                throw e
            } catch (e: Exception) {
                Log.e("SupabaseAuth", "Refresh error: ${e.message}")
                null
            } finally {
                conn?.disconnect()
            }
        }

    private suspend fun requestAnonymousSession(): StoredAuthSession? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            if (rateLimitDelayMillis() > 0) {
                Log.w(
                    "SupabaseAuth",
                    "Deferring anonymous sign-up: rate limited $consecutiveRateLimits time(s) ago"
                )
                throw RemoteRateLimitedException()
            }
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
            if (code == 429) {
                consecutiveRateLimits++
                throw RemoteRateLimitedException(readRetryAfter(conn))
            }
            if (code !in 200..299) {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("SupabaseAuth", "Anonymous sign-in failed: HTTP $code $error")
                return@withContext null
            }

            conn.inputStream.bufferedReader().use { it.readText() }.toSession()
        } catch (e: RemoteRateLimitedException) {
            Log.w("SupabaseAuth", "Anonymous sign-up rate limited: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Anonymous sign-in error: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Exponential backoff with jitter, capped.
     *
     * The cap matters: a safety app must keep trying every 15 minutes for weeks, so the delay has to
     * stay inside the sync period rather than growing past it.
     */
    private fun rateLimitDelayMillis(): Long {
        if (consecutiveRateLimits <= 0) return 0
        val exponential = BASE_BACKOFF_MILLIS shl (consecutiveRateLimits - 1).coerceAtMost(10)
        val capped = exponential.coerceAtMost(MAX_BACKOFF_MILLIS)
        val jitter = (capped * 0.25).toLong() * kotlin.random.Random.nextLong(0, 100) / 100
        return capped - (capped / 8) + jitter
    }

    private fun readRetryAfter(conn: HttpURLConnection): Long? =
        conn.getHeaderField("Retry-After")?.toLongOrNull()

    /**
     * Parses a GoTrue token response.
     *
     * [fallbackRefresh] is used when the response omits a refresh token, so a refresh can never
     * destroy the credential it will need next time.
     */
    private fun String.toSession(fallbackRefresh: String? = null): StoredAuthSession? {
        val json = JSONObject(this)
        val access = json.optString("access_token")
        if (access.isBlank()) return null
        val refresh = json.optString("refresh_token").ifBlank { fallbackRefresh.orEmpty() }
        if (refresh.isBlank()) return null
        val expiresIn = json.optLong("expires_in", 3600L)
        return StoredAuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000
        )
    }

    companion object {
        private const val BASE_BACKOFF_MILLIS = 30_000L
        private const val MAX_BACKOFF_MILLIS = 10 * 60_000L
    }
}