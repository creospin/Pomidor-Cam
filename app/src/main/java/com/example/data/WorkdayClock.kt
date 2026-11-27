package com.example.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WorkdayClock {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val shortTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

    /**
     * Logical workday runs from [startHour]:00 (default 06:00) through [endHour]:00 (default 04:00 next day).
     * Timestamps between 00:00 and (startHour - 1):59 belong to the previous calendar day's shift.
     */
    fun getWorkdayDate(timestampMs: Long, startHour: Int = 6): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            val currentHour = get(Calendar.HOUR_OF_DAY)
            if (currentHour < startHour) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }
        return synchronized(dateFormat) { dateFormat.format(cal.time) }
    }

    /**
     * Returns the previous logical workday ("yyyy-MM-dd") relative to [timestampMs].
     * For example, at 08:00 on 2026-11-27 (startHour = 6), returns "2026-11-26".
     */
    fun getPreviousWorkdayDate(timestampMs: Long, startHour: Int = 6): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestampMs
            val currentHour = get(Calendar.HOUR_OF_DAY)
            if (currentHour < startHour) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            add(Calendar.DAY_OF_MONTH, -1)
        }
        return synchronized(dateFormat) { dateFormat.format(cal.time) }
    }

    fun getCurrentHour(timestampMs: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun computeNextMorningTriggerDelayMs(
        nowMs: Long,
        targetHour: Int,
        targetMinute: Int = 1
    ): Long {
        val next = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, targetHour.coerceIn(0, 23))
            set(Calendar.MINUTE, targetMinute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMs) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return (next.timeInMillis - nowMs).coerceAtLeast(60_000L)
    }

    fun isWithinWorkdayWindow(
        timestampMs: Long,
        startHour: Int = 6,
        endHour: Int = 4
    ): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            // Wraps past midnight, e.g., 06:00 -> 04:00 next day
            hour >= startHour || hour < endHour
        }
    }

    fun formatWindowLabel(startHour: Int = 6, endHour: Int = 4): String {
        val s = String.format(Locale.US, "%02d:00", startHour)
        val e = String.format(Locale.US, "%02d:00", endHour)
        return "$s → $e"
    }

    fun formatTime(timestampMs: Long): String {
        return synchronized(timeFormat) { timeFormat.format(Date(timestampMs)) }
    }

    fun formatShortTime(timestampMs: Long): String {
        return synchronized(shortTimeFormat) { shortTimeFormat.format(Date(timestampMs)) }
    }

    fun formatDurationHuman(totalSeconds: Int, isRu: Boolean = false): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val h = safe / 3600
        val m = (safe % 3600) / 60
        val s = safe % 60
        return if (isRu) {
            when {
                h > 0 -> String.format(Locale.US, "%dч %02dм %02dс", h, m, s)
                m > 0 -> String.format(Locale.US, "%dм %02dс", m, s)
                else -> String.format(Locale.US, "%dс", s)
            }
        } else {
            when {
                h > 0 -> String.format(Locale.US, "%dh %02dm %02ds", h, m, s)
                m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
                else -> String.format(Locale.US, "%ds", s)
            }
        }
    }

    fun formatMmSs(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
