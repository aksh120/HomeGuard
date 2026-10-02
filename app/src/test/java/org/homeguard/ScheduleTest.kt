package org.homeguard

import org.homeguard.core.model.CheckInSchedule
import org.homeguard.core.model.NightModeSchedule
import org.homeguard.core.model.BatteryThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Configurable windows introduced by sections 14, 16, and 17. */
class ScheduleTest {

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(year, month, day, hour, minute, 0)
        return cal.timeInMillis
    }

    // --- Section 14: next scheduled occurrence ------------------------------------------

    @Test
    fun `next occurrence is later today when one remains`() {
        val schedule = CheckInSchedule(dailyTimes = listOf(9 * 60, 21 * 60))
        assertEquals(21 * 60, schedule.nextOccurrence(nowMinute = 10 * 60))
    }

    @Test
    fun `next occurrence wraps to the earliest time tomorrow`() {
        val schedule = CheckInSchedule(dailyTimes = listOf(9 * 60, 21 * 60))
        assertEquals(9 * 60, schedule.nextOccurrence(nowMinute = 22 * 60))
    }

    @Test
    fun `exactly at a configured time moves to the next one`() {
        val schedule = CheckInSchedule(dailyTimes = listOf(9 * 60, 21 * 60))
        assertEquals(21 * 60, schedule.nextOccurrence(nowMinute = 9 * 60))
    }

    @Test
    fun `disabled schedule has no next occurrence`() {
        assertNull(CheckInSchedule(enabled = false).nextOccurrence(10 * 60))
    }

    @Test
    fun `schedule with no times is invalid`() {
        assertFalse(CheckInSchedule(dailyTimes = emptyList()).isValid)
        assertNull(CheckInSchedule(dailyTimes = emptyList()).nextOccurrence(10 * 60))
    }

    @Test
    fun `out of range times are rejected as invalid`() {
        assertFalse(CheckInSchedule(dailyTimes = listOf(-1)).isValid)
        assertFalse(CheckInSchedule(dailyTimes = listOf(1440)).isValid)
        assertTrue(CheckInSchedule(dailyTimes = listOf(0, 1439)).isValid)
    }

    @Test
    fun `display times are formatted and sorted`() {
        val schedule = CheckInSchedule(dailyTimes = listOf(21 * 60, 9 * 60 + 30))
        assertEquals(listOf("09:30", "21:00"), schedule.displayTimes())
    }

    // --- Section 16: night mode window --------------------------------------------------

    @Test
    fun `night window within one day is active between start and end`() {
        val night = NightModeSchedule(enabled = true, startMinute = 22 * 60, endMinute = 23 * 60)
        assertFalse(night.isActiveAt(21 * 60))
        assertTrue(night.isActiveAt(22 * 60))
        assertTrue(night.isActiveAt(22 * 60 + 59))
        assertFalse(night.isActiveAt(23 * 60))
    }

    @Test
    fun `night window wrapping past midnight is active on both sides`() {
        val night = NightModeSchedule(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
        assertFalse(night.isActiveAt(21 * 60))
        assertTrue(night.isActiveAt(22 * 60))
        assertTrue(night.isActiveAt(23 * 60 + 59))
        assertTrue(night.isActiveAt(0))
        assertTrue(night.isActiveAt(6 * 60 + 59))
        assertFalse(night.isActiveAt(7 * 60))
    }

    @Test
    fun `disabled night window is never active`() {
        val night = NightModeSchedule(enabled = false, startMinute = 22 * 60, endMinute = 7 * 60)
        assertFalse(night.isActiveAt(23 * 60))
    }

    @Test
    fun `night window with identical start and end is invalid and inactive`() {
        val night = NightModeSchedule(enabled = true, startMinute = 60, endMinute = 60)
        assertFalse(night.isValid)
        assertFalse(night.isActiveAt(60))
    }

    @Test
    fun `night window display is readable`() {
        val night = NightModeSchedule(enabled = true, startMinute = 22 * 60, endMinute = 7 * 60)
        assertEquals("22:00 – 07:00", night.displayWindow())
        assertEquals("Off", night.copy(enabled = false).displayWindow())
    }

    // --- Section 17: battery thresholds -------------------------------------------------

    @Test
    fun `default thresholds match the specified 20 and 10`() {
        val thresholds = BatteryThresholds()
        assertEquals(20, thresholds.lowPercent)
        assertEquals(10, thresholds.criticalPercent)
        assertTrue(thresholds.isValid)
    }

    @Test
    fun `critical must be below warning`() {
        assertFalse(BatteryThresholds(lowPercent = 10, criticalPercent = 20).isValid)
        assertTrue(BatteryThresholds(lowPercent = 30, criticalPercent = 20).isValid)
    }

    @Test
    fun `out of range thresholds are invalid`() {
        assertFalse(BatteryThresholds(lowPercent = 0, criticalPercent = -1).isValid)
        assertFalse(BatteryThresholds(lowPercent = 101, criticalPercent = 10).isValid)
    }

    // --- Scheduler: alarm placement ------------------------------------------------------

    @Test
    fun `alarm is placed at the next configured time today`() {
        val now = calendarAt(2026, Calendar.MARCH, 10, 8, 0)
        val schedule = CheckInSchedule(dailyTimes = listOf(9 * 60, 21 * 60))
        val trigger = org.homeguard.core.checkin.CheckInScheduler.nextTriggerAt(schedule, now)
        assertEquals(calendarAt(2026, Calendar.MARCH, 10, 9, 0), trigger)
    }

    @Test
    fun `alarm rolls to tomorrow once todays time has passed`() {
        val now = calendarAt(2026, Calendar.MARCH, 10, 22, 0)
        val schedule = CheckInSchedule(dailyTimes = listOf(9 * 60, 21 * 60))
        val trigger = org.homeguard.core.checkin.CheckInScheduler.nextTriggerAt(schedule, now)
        assertEquals(calendarAt(2026, Calendar.MARCH, 11, 9, 0), trigger)
    }

    @Test
    fun `no alarm when the schedule is disabled`() {
        val now = calendarAt(2026, Calendar.MARCH, 10, 8, 0)
        val schedule = CheckInSchedule(enabled = false, dailyTimes = listOf(9 * 60))
        assertNull(org.homeguard.core.checkin.CheckInScheduler.nextTriggerAt(schedule, now))
    }
}