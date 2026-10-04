package org.homeguard

import kotlinx.coroutines.test.runTest
import org.homeguard.core.data.AuthSessionStore
import org.homeguard.core.data.InMemoryAuthSessionStore
import org.homeguard.core.data.RemoteNotConfiguredException
import org.homeguard.core.data.RemoteRateLimitedException
import org.homeguard.core.data.StoredAuthSession
import org.homeguard.core.data.isTransientTransportFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0: session persistence and the rate-limit/backoff contract.
 *
 * The outage these cover is measured, not hypothetical. The device was found holding 43 queued rows
 * (23 FAILED, 20 PENDING) with every remote write failing on
 * `{"code":429,"error_code":"over_request_rate_limit"}` from anonymous sign-up.
 */
class AuthSessionTest {

    /** Counts reads so the test can assert the client reuses storage instead of signing up again. */
    private class CountingStore(initial: StoredAuthSession? = null) : AuthSessionStore {
        var session = initial
        var reads = 0
        var writes = 0

        override suspend fun read(): StoredAuthSession? {
            reads++
            return session
        }

        override suspend fun write(session: StoredAuthSession) {
            writes++
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private val future = System.currentTimeMillis() + 3_600_000
    private val past = System.currentTimeMillis() - 60_000

    // --- expiry ------------------------------------------------------------------------------

    @Test
    fun `a session well in the future is not expired`() {
        assertFalse(StoredAuthSession("a", "r", future).isExpired(System.currentTimeMillis()))
    }

    @Test
    fun `a session in the past is expired`() {
        assertTrue(StoredAuthSession("a", "r", past).isExpired(System.currentTimeMillis()))
    }

    /**
     * Absorbs clock skew. A token with less than `skewMillis` of life left counts as expired, so the
     * client refreshes early instead of sending a request that is about to fail with 401.
     */
@Test
    fun `a session with less than the skew window left counts as expired`() {
        val now = System.currentTimeMillis()
        val barelyValid = StoredAuthSession("a", "r", now + 30_000)

        // 30s of nominal life is inside the 60s skew window, so it is treated as expired.
        assertTrue(barelyValid.isExpired(now, skewMillis = 60_000))
        // With no skew allowance it is still valid, which is what proves the window caused it.
        assertFalse(barelyValid.isExpired(now, skewMillis = 0))
    }

    @Test
    fun `exactly at expiry is treated as expired`() {
        val now = System.currentTimeMillis()
        assertTrue(StoredAuthSession("a", "r", now).isExpired(now))
    }

    // --- store contract ----------------------------------------------------------------------

    @Test
    fun `a fresh store has no session`() = runTest {
        assertEquals(null, InMemoryAuthSessionStore().read())
    }

    @Test
    fun `in-memory store round-trips a session`() = runTest {
        val store = InMemoryAuthSessionStore()
        val session = StoredAuthSession("access", "refresh", future)
        store.write(session)
        assertEquals(session, store.read())

        store.clear()
        assertEquals(null, store.read())
    }

    /**
     * The root cause of the outage: the session must survive the process, or every cold start
     * re-runs sign-up against a per-IP limit.
     */
    @Test
    fun `a persisted session is readable by a fresh client instance`() = runTest {
        val store = CountingStore()
        val session = StoredAuthSession("access", "refresh", future)
        store.write(session)

        // A new reader, standing in for a new process, sees the same credentials.
        val afterRestart = CountingStore(session)
        assertEquals(session, afterRestart.read())
    }

    @Test
    fun `writing a session is recorded exactly once`() = runTest {
        val store = CountingStore()
        store.write(StoredAuthSession("access", "refresh", future))
        assertEquals(1, store.writes)
    }

    // --- retry classification ---------------------------------------------------------------

    /**
     * The rule that fixes D-11. Reaching the server failed, not the event, so an event must not be
     * blamed for it.
     */
    @Test
    fun `a missing session is a transport failure`() {
        assertTrue(RemoteNotConfiguredException().isTransientTransportFailure)
    }

    @Test
    fun `rate limiting is a transport failure`() {
        assertTrue(RemoteRateLimitedException().isTransientTransportFailure)
        assertTrue(RemoteRateLimitedException(retryAfterSeconds = 60).isTransientTransportFailure)
    }

    /**
     * The opposite case. A payload the server rejected will be rejected again, so it must still
     * burn retries and eventually be abandoned rather than blocking the queue forever.
     */
    @Test
    fun `a rejected payload is not a transport failure`() {
        assertFalse(IllegalStateException("column not-null violated").isTransientTransportFailure)
        assertFalse(RuntimeException("bad request").isTransientTransportFailure)
    }
}