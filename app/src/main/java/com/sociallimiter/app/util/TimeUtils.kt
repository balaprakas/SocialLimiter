package com.sociallimiter.app.util

import com.sociallimiter.app.data.Schedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** An active blackout window: everything is blocked until [untilMillis] (epoch). */
data class ActiveWindow(val untilMillis: Long)

object TimeUtils {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** ISO `yyyy-MM-dd` formatter, matching [dayKey]'s bucket keys. */
    val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Local `yyyy-MM-dd` for [nowMillis]; used as the daily-usage bucket key. */
    fun dayKey(nowMillis: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
            .toLocalDate().format(dateFormatter)

    /** Epoch millis of the next local midnight after [nowMillis]. */
    fun nextMidnightMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** True if [schedule] has [dayBit] (0 = Monday .. 6 = Sunday) enabled. */
    private fun Schedule.appliesOn(dayIndex: Int): Boolean =
        (daysMask and (1 shl dayIndex)) != 0

    /**
     * If any enabled [schedules] blackout window covers [nowMillis], returns the
     * [ActiveWindow] with the earliest end; otherwise null. Handles windows whose
     * end minute is <= start minute as crossing into the next day.
     */
    fun activeWindow(schedules: List<Schedule>, nowMillis: Long = System.currentTimeMillis()): ActiveWindow? {
        val zone = ZoneId.systemDefault()
        val nowZdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = nowZdt.toLocalDate()
        val nowMinute = nowZdt.hour * 60 + nowZdt.minute
        val todayIndex = today.dayOfWeek.value - 1          // Mon=0 .. Sun=6
        val yesterdayIndex = (todayIndex + 6) % 7

        fun millisAt(dateOffsetDays: Long, minuteOfDay: Int): Long =
            today.plusDays(dateOffsetDays).atStartOfDay(zone)
                .plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

        var best: Long? = null
        for (s in schedules) {
            if (!s.isEnabled) continue
            val start = s.startMinuteOfDay
            val end = s.endMinuteOfDay
            val until: Long? = when {
                end > start -> // same-day window
                    if (s.appliesOn(todayIndex) && nowMinute in start until end)
                        millisAt(0, end) else null
                else -> { // crosses midnight (end <= start)
                    when {
                        s.appliesOn(todayIndex) && nowMinute >= start -> millisAt(1, end)
                        s.appliesOn(yesterdayIndex) && nowMinute < end -> millisAt(0, end)
                        else -> null
                    }
                }
            }
            val currentBest = best
            if (until != null && (currentBest == null || until < currentBest)) best = until
        }
        return best?.let { ActiveWindow(it) }
    }

    /** Formats [minuteOfDay] (0..1439) as a locale 12-hour clock, e.g. "8:00 PM". */
    fun formatMinuteOfDay(minuteOfDay: Int): String =
        LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    /** Formats an epoch [millis] time-of-day as a locale 12-hour clock. */
    fun formatClock(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}
