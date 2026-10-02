package org.homeguard.core.checkin

import org.homeguard.core.model.CheckInSchedule

/**
 * Decides whether a scheduled check-in has gone unanswered.
 *
 * Section 15 is explicit that a missed check-in must not escalate to an emergency, so this returns
 * only a boolean: the caller downgrades SAFE to UNKNOWN and, at most, notifies the monitoring
 * contact. It never produces an EMERGENCY.
 *
 * Pure and clock-injectable so the timeout boundary is unit testable.
 */
object CheckInTimeoutEvaluator {

    /**
     * True when a check-in was due at or before [now] and the last confirmation is older than the
     * allowed timeout.
     *
     * @param lastCheckInAt epoch millis of the last confirmed check-in, or null if never confirmed.
     * @param lastPromptAt epoch millis of the most recent scheduled prompt that was raised.
     * @param graceMinutes additional allowance after the prompt before it counts as missed.
     */
    fun isMissed(
        schedule: CheckInSchedule,
        lastCheckInAt: Long?,
        lastPromptAt: Long?,
        now: Long,
        graceMinutes: Int = schedule.timeoutMinutes
    ): Boolean {
        if (!schedule.enabled || !schedule.isValid) return false

        // Nothing has been prompted yet, so nothing can be missed.
        val promptedAt = lastPromptAt ?: return false

        val deadline = promptedAt + graceMinutes.coerceAtLeast(0) * 60_000L
        if (now < deadline) return false

        // Any confirmation after the prompt cancels the miss.
        if (lastCheckInAt != null && lastCheckInAt >= promptedAt) return false

        return true
    }

    /**
     * Whether a check-in is due now.
     *
     * True within [dueWindowMinutes] of any configured time, so a device resumed slightly early
     * still prompts rather than silently skipping.
     */
    fun isDue(schedule: CheckInSchedule, nowMinute: Int, dueWindowMinutes: Int = 10): Boolean {
        if (!schedule.enabled || !schedule.isValid) return false
        return schedule.dailyTimes.any { configured ->
            val delta = Math.abs(nowMinute - configured)
            // Handles wrap past midnight.
            minOf(delta, 1440 - delta) <= dueWindowMinutes
        }
    }
}