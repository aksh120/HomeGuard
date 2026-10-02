package org.homeguard

import org.homeguard.core.data.calculateSafetyStatus
import org.homeguard.core.model.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests the real production state machine. The previous version of this file inlined a copy of
 * the logic in each test body, so a regression in SafetyRepository would not have been caught.
 */
class SafetyStatusTest {

    private val intervalHours = 24
    private val graceMinutes = 60
    private val windowMillis =
        TimeUnit.HOURS.toMillis(intervalHours.toLong()) + TimeUnit.MINUTES.toMillis(graceMinutes.toLong())

    @Test
    fun `no check-in ever recorded is UNKNOWN, never SAFE`() {
        assertEquals(
            SafetyStatus.UNKNOWN,
            calculateSafetyStatus(
                stored = SafetyStatus.UNKNOWN,
                lastCheckIn = null,
                intervalHours = intervalHours,
                graceMinutes = graceMinutes
            )
        )
    }

    @Test
    fun `recent check-in is SAFE`() {
        val now = 1_700_000_000_000L
        assertEquals(
            SafetyStatus.SAFE,
            calculateSafetyStatus(
                stored = SafetyStatus.SAFE,
                lastCheckIn = now - TimeUnit.MINUTES.toMillis(30),
                intervalHours = intervalHours,
                graceMinutes = graceMinutes,
                now = now
            )
        )
    }

    @Test
    fun `check-in beyond interval plus grace is UNKNOWN`() {
        val now = 1_700_000_000_000L
        assertEquals(
            SafetyStatus.UNKNOWN,
            calculateSafetyStatus(
                stored = SafetyStatus.SAFE,
                lastCheckIn = now - TimeUnit.HOURS.toMillis(26),
                intervalHours = intervalHours,
                graceMinutes = graceMinutes,
                now = now
            )
        )
    }

    @Test
    fun `exactly at the boundary is still SAFE`() {
        val now = 1_700_000_000_000L
        assertEquals(
            SafetyStatus.SAFE,
            calculateSafetyStatus(
                stored = SafetyStatus.SAFE,
                lastCheckIn = now - windowMillis,
                intervalHours = intervalHours,
                graceMinutes = graceMinutes,
                now = now
            )
        )
    }

    @Test
    fun `one millisecond past the boundary is UNKNOWN`() {
        val now = 1_700_000_000_000L
        assertEquals(
            SafetyStatus.UNKNOWN,
            calculateSafetyStatus(
                stored = SafetyStatus.SAFE,
                lastCheckIn = now - windowMillis - 1,
                intervalHours = intervalHours,
                graceMinutes = graceMinutes,
                now = now
            )
        )
    }

    /** A missed check-in must never escalate to EMERGENCY on its own. */
    @Test
    fun `elapsed window downgrades to UNKNOWN and not EMERGENCY`() {
        val now = 1_700_000_000_000L
        val result = calculateSafetyStatus(
            stored = SafetyStatus.SAFE,
            lastCheckIn = now - TimeUnit.DAYS.toMillis(30),
            intervalHours = intervalHours,
            graceMinutes = graceMinutes,
            now = now
        )
        assertEquals(SafetyStatus.UNKNOWN, result)
    }

    /** EMERGENCY must win even when the check-in window has not elapsed. */
    @Test
    fun `stored EMERGENCY takes precedence over a fresh check-in`() {
        val now = 1_700_000_000_000L
        assertEquals(
            SafetyStatus.EMERGENCY,
            calculateSafetyStatus(
                stored = SafetyStatus.EMERGENCY,
                lastCheckIn = now,
                intervalHours = intervalHours,
                graceMinutes = graceMinutes,
                now = now
            )
        )
    }

    @Test
    fun `interval and grace period are both honoured`() {
        val now = 1_700_000_000_000L
        // 25h ago: past the 24h interval but inside the 1h grace window.
        assertEquals(
            SafetyStatus.SAFE,
            calculateSafetyStatus(
                stored = SafetyStatus.SAFE,
                lastCheckIn = now - TimeUnit.HOURS.toMillis(25),
                intervalHours = intervalHours,
                graceMinutes = graceMinutes,
                now = now
            )
        )
    }
}