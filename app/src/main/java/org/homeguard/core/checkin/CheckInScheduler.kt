package org.homeguard.core.checkin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import org.homeguard.core.model.CheckInSchedule
import java.util.Calendar

/**
 * Schedules the "Are you okay?" prompt using exact-ish alarms.
 *
 * Section 5 and section 38 forbid continuous polling, so this is alarm-driven: the device is woken
 * only at the configured times, never on a timer. That matters on the low-end hardware the brief
 * targets.
 */
class CheckInScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /** Applies the schedule: cancels any pending alarm and sets the next occurrence. */
    fun applySchedule(schedule: CheckInSchedule) {
        cancel()
        if (!schedule.enabled || !schedule.isValid) {
            Log.i("CheckInScheduler", "Scheduled check-ins disabled or invalid; no alarm set.")
            return
        }
        val next = nextTriggerAt(schedule) ?: return
        scheduleAt(next)
        Log.i("CheckInScheduler", "Next scheduled check-in at ${formatMinute(next)}")
    }

    private fun scheduleAt(triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        val pending = pendingIntent()
        try {
            // setWindow lets the system batch the wake-up with others, which is kinder to the
            // battery than an exact alarm and does not require the SCHEDULE_EXACT_ALARM
            // permission that a safety app should not be asking for.
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                WINDOW_MILLIS,
                pending
            )
        } catch (e: SecurityException) {
            Log.w("CheckInScheduler", "Cannot set check-in alarm: ${e.message}")
        }
    }

    fun cancel() {
        alarmManager?.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, CheckInAlarmReceiver::class.java).setAction(ACTION_CHECK_IN_DUE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val ACTION_CHECK_IN_DUE = "org.homeguard.action.CHECK_IN_DUE"
        private const val REQUEST_CODE = 4201

        /** Two minutes of slack; the prompt is a comfort check, not a second-by-second deadline. */
        private const val WINDOW_MILLIS = 2 * 60 * 1000L

        /**
         * Next wall-clock occurrence of any configured time, in epoch millis.
         *
         * Pure enough to unit test: [now] is injectable.
         */
        fun nextTriggerAt(
            schedule: CheckInSchedule,
            now: Long = System.currentTimeMillis()
        ): Long? {
            if (!schedule.enabled || !schedule.isValid) return null

            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            val nowMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val nextMinute = schedule.nextOccurrence(nowMinute) ?: return null

            val target = calendar.clone() as Calendar
            target.set(Calendar.HOUR_OF_DAY, nextMinute / 60)
            target.set(Calendar.MINUTE, nextMinute % 60)
            target.set(Calendar.SECOND, 0)
            target.set(Calendar.MILLISECOND, 0)

            // A time later today is chosen today; otherwise it is tomorrow's.
            if (target.timeInMillis <= now) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        fun formatMinute(epochMillis: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
            return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }
    }
}