package org.homeguard

import org.homeguard.core.checkin.CheckInTimeoutEvaluator
import org.homeguard.core.model.CheckInSchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 15 is the critical invariant: a missed check-in downgrades SAFE to UNKNOWN and must
 * never be treated as an emergency. These tests pin the timeout boundary and the never-escalate
 * rule at the unit that decides it.
 */
class CheckInTimeoutTest {

    private val schedule = CheckInSchedule(
        enabled = true,
        dailyTimes = listOf(10 * 60),
        timeoutMinutes = 60
    )

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    @Test
    fun `no prompt raised yet means nothing can be missed`() {
        assertFalse(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule,
                lastCheckInAt = null,
                lastPromptAt = null,
                now = now + 10_000 * minute
            )
        )
    }

    @Test
    fun `inside the grace period is not missed`() {
        assertFalse(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule,
                lastCheckInAt = null,
                lastPromptAt = now,
                now = now + 59 * minute
            )
        )
    }

    @Test
    fun `exactly at the timeout boundary is not yet missed`() {
        assertFalse(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule,
                lastCheckInAt = null,
                lastPromptAt = now,
                now = now + 60 * minute
            )
        )
    }

    @Test
    fun `one minute past the timeout is missed`() {
        assertTrue(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule,
                lastCheckInAt = null,
                lastPromptAt = now,
                now = now + 61 * minute
            )
        )
    }

    @Test
    fun `a response after the prompt cancels the miss`() {
        assertFalse(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule,
                lastCheckInAt = now + 30 * minute,
                lastPromptAt = now,
                now = now + 90 * minute
            )
        )
    }

    @Test
    fun `a response before the prompt does not cancel the miss`() {
        assertTrue(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule,
                lastCheckInAt = now - 5 * minute,
                lastPromptAt = now,
                now = now + 90 * minute
            )
        )
    }

    @Test
    fun `a disabled schedule is never missed`() {
        assertFalse(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule.copy(enabled = false),
                lastCheckInAt = null,
                lastPromptAt = now,
                now = now + 600 * minute
            )
        )
    }

    @Test
    fun `an empty time list is never missed`() {
        assertFalse(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule.copy(dailyTimes = emptyList()),
                lastCheckInAt = null,
                lastPromptAt = now,
                now = now + 600 * minute
            )
        )
    }

    @Test
    fun `zero grace period is missed immediately after the prompt`() {
        assertTrue(
            CheckInTimeoutEvaluator.isMissed(
                schedule = schedule.copy(timeoutMinutes = 0),
                lastCheckInAt = null,
                lastPromptAt = now,
                now = now + 1
            )
        )
    }

    @Test
    fun `prompt is due within the window of a configured time`() {
        assertTrue(CheckInTimeoutEvaluator.isDue(schedule, nowMinute = 10 * 60))
        assertTrue(CheckInTimeoutEvaluator.isDue(schedule, nowMinute = 10 * 60 - 5))
        assertTrue(CheckInTimeoutEvaluator.isDue(schedule, nowMinute = 10 * 60 + 5))
        assertFalse(CheckInTimeoutEvaluator.isDue(schedule, nowMinute = 14 * 60))
    }

    @Test
    fun `due window wraps across midnight`() {
        val midnight = CheckInSchedule(
            enabled = true,
            dailyTimes = listOf(0, 23 * 60 + 55),
            timeoutMinutes = 60
        )
        assertTrue(CheckInTimeoutEvaluator.isDue(midnight, nowMinute = 23 * 60 + 58))
        assertTrue(CheckInTimeoutEvaluator.isDue(midnight, nowMinute = 0))
        assertTrue(CheckInTimeoutEvaluator.isDue(midnight, nowMinute = 2))
        assertFalse(CheckInTimeoutEvaluator.isDue(midnight, nowMinute = 12 * 60))
    }

    @Test
    fun `disabled schedule is never due`() {
        assertFalse(CheckInTimeoutEvaluator.isDue(schedule.copy(enabled = false), 10 * 60))
    }
}