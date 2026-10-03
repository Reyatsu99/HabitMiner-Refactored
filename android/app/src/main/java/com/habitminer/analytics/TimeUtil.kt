package com.habitminer.analytics

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Small time helpers shared by the analytics code. All functions take an explicit zone. */
object TimeUtil {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    fun dateOf(
        ms: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun startOfDay(
        date: LocalDate,
        zone: ZoneId,
    ): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfDay(
        ms: Long,
        zone: ZoneId,
    ): Long = startOfDay(dateOf(ms, zone), zone)

    /** Time on [date] at [hour]:[minute]; hour may be negative or ≥ 24 to reach neighbouring days. */
    fun at(
        date: LocalDate,
        hour: Int,
        minute: Int,
        zone: ZoneId,
    ): Long {
        val dayShift = Math.floorDiv(hour, 24).toLong()
        val h = Math.floorMod(hour, 24)
        return date.plusDays(dayShift).atTime(h, minute).atZone(zone).toInstant().toEpochMilli()
    }

    fun zoned(
        ms: Long,
        zone: ZoneId,
    ): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)

    fun hourOf(
        ms: Long,
        zone: ZoneId,
    ): Int = zoned(ms, zone).hour

    fun minuteOfDay(
        ms: Long,
        zone: ZoneId,
    ): Int = zoned(ms, zone).let { it.hour * 60 + it.minute }

    fun isWeekend(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun overlap(
        aStart: Long,
        aEnd: Long,
        bStart: Long,
        bEnd: Long,
    ): Long = max(0L, min(aEnd, bEnd) - max(aStart, bStart))

    /**
     * Foreground time of [session] that falls inside [windowStart, windowEnd), pro-rating
     * sessions that straddle the edges.
     */
    fun usageIn(
        session: UsageSession,
        windowStart: Long,
        windowEnd: Long,
    ): Long {
        val span = session.end - session.start
        if (span <= 0L) return if (session.start in windowStart until windowEnd) session.durationMs else 0L
        val ov = overlap(session.start, session.end, windowStart, windowEnd)
        if (ov <= 0L) return 0L
        return if (ov >= span) session.durationMs else (session.durationMs.toDouble() * ov / span).toLong()
    }
}

/** Human-readable formatting used across the UI. */
object Format {
    /** "2h 5m", "45m", "<1m". */
    fun duration(ms: Long): String {
        val totalMin = ms / TimeUtil.MINUTE
        if (ms in 1 until TimeUtil.MINUTE) return "<1m"
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** Clock time like "23:40". */
    fun clock(
        ms: Long,
        zone: ZoneId,
    ): String {
        val z = TimeUtil.zoned(ms, zone)
        return String.format(Locale.US, "%02d:%02d", z.hour, z.minute)
    }

    /** Clock time from minutes after midnight; wraps past 24h. */
    fun clockFromMinutes(minutes: Int): String {
        val m = Math.floorMod(minutes, 24 * 60)
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    /** "5 min ago", "2 h ago", "just now". */
    fun ago(ageMs: Long): String =
        when {
            ageMs < TimeUtil.MINUTE -> "just now"
            ageMs < TimeUtil.HOUR -> "${ageMs / TimeUtil.MINUTE} min ago"
            ageMs < TimeUtil.DAY -> "${ageMs / TimeUtil.HOUR} h ago"
            else -> "${ageMs / TimeUtil.DAY} d ago"
        }

    fun percent(fraction: Float): String = "${(fraction * 100).toInt()}%"

    fun hourRange(hour: Int): String = String.format(Locale.US, "%02d:00–%02d:00", hour, (hour + 1) % 24)
}
