package org.homeguard

import org.homeguard.core.model.SyncHealth
import org.homeguard.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Section 53 asks "Is the device working?" as one of the four questions a user should be able to
 * answer on opening the app. These tests pin the severity ordering so a broken remote can never be
 * reported as healthy.
 */
class SyncHealthTest {

    @Test
    fun `missing credentials is not an error, it is local-only mode`() {
        val health = SyncHealth(isRemoteConfigured = false)
        assertEquals(SyncStatus.NOT_CONFIGURED, health.status)
    }

    @Test
    fun `configured but not signed in is reported separately from missing credentials`() {
        val health = SyncHealth(isRemoteConfigured = true, isAuthenticated = false)
        assertEquals(SyncStatus.NOT_AUTHENTICATED, health.status)
    }

    @Test
    fun `configured and signed in with no attempt yet is pending`() {
        val health = SyncHealth(isRemoteConfigured = true, isAuthenticated = true)
        assertEquals(SyncStatus.PENDING, health.status)
    }

    @Test
    fun `a successful attempt is healthy`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = true,
            lastSuccessAt = 1_700_000_000_000L
        )
        assertEquals(SyncStatus.HEALTHY, health.status)
    }

    /** The case that matters: alerts are stuck and the resident must be told. */
    @Test
    fun `an error outranks a previous success`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = true,
            lastSuccessAt = 1_700_000_000_000L,
            lastError = "Supabase rejected the session"
        )
        assertEquals(SyncStatus.ERROR, health.status)
    }

    @Test
    fun `missing credentials outrank an error message`() {
        val health = SyncHealth(
            isRemoteConfigured = false,
            isAuthenticated = true,
            lastError = "stale error from a previous configuration"
        )
        assertEquals(SyncStatus.NOT_CONFIGURED, health.status)
    }

    @Test
    fun `not being signed in outranks an error message`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = false,
            lastError = "Not signed in to Supabase"
        )
        assertEquals(SyncStatus.NOT_AUTHENTICATED, health.status)
    }

    @Test
    fun `a blank error string counts as healthy rather than broken`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = true,
            lastSuccessAt = 1_700_000_000_000L,
            lastError = ""
        )
        assertEquals(SyncStatus.HEALTHY, health.status)
    }

    // --- Phase 0: rate limiting is not "not signed in" -------------------------------------------

    /**
     * The distinction that made the outage undiagnosable. Both states looked identical to the
     * resident, and both sent them looking for a backend setting that was already correct.
     */
    @Test
    fun `being rate limited is not the same as failing to sign in`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = false,
            isRateLimited = true
        )
        assertEquals(SyncStatus.RATE_LIMITED, health.status)
    }

    @Test
    fun `rate limiting outranks a previous success but is not an error`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = true,
            isRateLimited = true,
            lastSuccessAt = 1_700_000_000_000L
        )
        assertEquals(SyncStatus.RATE_LIMITED, health.status)
    }

    /** Nothing is wrong with the build, so local-only mode still wins over a throttle. */
    @Test
    fun `missing credentials outrank rate limiting`() {
        val health = SyncHealth(
            isRemoteConfigured = false,
            isAuthenticated = false,
            isRateLimited = true
        )
        assertEquals(SyncStatus.NOT_CONFIGURED, health.status)
    }

    @Test
    fun `clearing the rate limit returns a healthy install to healthy`() {
        val health = SyncHealth(
            isRemoteConfigured = true,
            isAuthenticated = true,
            isRateLimited = false,
            lastSuccessAt = 1_700_000_000_000L
        )
        assertEquals(SyncStatus.HEALTHY, health.status)
    }
}