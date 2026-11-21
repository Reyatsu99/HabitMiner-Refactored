package com.habitminer.analytics

import java.time.LocalDate
import java.time.ZoneId

/** Which part of your own day it is, for what Today leads with. */
enum class DayPhase { MORNING, DAY, EVENING }

/**
 * Today's hero follows your day, not the clock: the first three hours after you woke lead with
 * last night, the stretch before your usual bedtime leads with a wrap-up, and the rest of the
 * day with how today compares with a usual one.
 */
object DayPulse {
    /** How long after waking the morning view lasts. */
    const val MORNING_MS = 3 * TimeUtil.HOUR

    /** The wrap-up starts this long before your usual bedtime (and never later than 21:00). */
    const val WIND_DOWN_MINUTES = 150

    /**
     * @param wakeTime when you woke today, if last night is known
     * @param usualBedMinutes your usual bedtime in [SleepDetector.bedtimeMinutes] form (minutes
     *   from midnight, negative before it), if known
     */
    fun phase(
        now: Long,
        wakeTime: Long?,
        usualBedMinutes: Int?,
        zone: ZoneId,
    ): DayPhase {
        if (wakeTime != null && now >= wakeTime && now - wakeTime < MORNING_MS) return DayPhase.MORNING
        val rel = SleepDetector.bedtimeMinutes(now, zone)
        val bed = usualBedMinutes ?: 0
        val start = maxOf(-6 * 60, minOf(bed - WIND_DOWN_MINUTES, -3 * 60))
        if (rel >= start && rel <= bed + 2 * 60 && (wakeTime == null || now - wakeTime >= MORNING_MS)) return DayPhase.EVENING
        return DayPhase.DAY
    }

    /** A bedtime in [SleepDetector.bedtimeMinutes] form as a minute of the day (0–1439). */
    fun clockMinute(bedMinutes: Int): Int = Math.floorMod(bedMinutes, 24 * 60)
}

/** A short, honest picture of your habit with one app, for the mindful pause. */
data class PauseContext(
    /** Visits today, this one included. */
    val visitsToday: Int,
    /** Visits in the last hour, this one included. */
    val visitsLastHour: Int,
    val usedTodayMs: Long,
    /** How long a visit around this time of day usually lasts, if there are enough of them. */
    val usualVisitMs: Long?,
) {
    /** One or two sentences: what's happening now, and what usually follows. */
    fun lines(appName: String): List<String> {
        val out = mutableListOf<String>()
        when {
            visitsLastHour >= 3 -> out += "This is your ${ordinal(visitsLastHour)} time in $appName in the last hour."
            visitsToday >= 2 -> out += "This is your ${ordinal(visitsToday)} time in $appName today, ${Format.duration(usedTodayMs)} so far."
            else -> out += "Is $appName what you want right now?"
        }
        usualVisitMs?.let { out += "Around this time, a visit usually lasts about ${Format.duration(it)}." }
        return out
    }

    companion object {
        /** Visits closer together than this are one visit. */
        private const val SAME_VISIT_MS = 60_000L

        /** A typical visit length needs at least this many visits around this time of day. */
        private const val MIN_VISITS = 4

        /**
         * @param sessions this app's sessions over the last couple of weeks, any order
         */
        fun build(
            sessions: List<UsageSession>,
            now: Long,
            zone: ZoneId,
        ): PauseContext {
            val visits = mutableListOf<Pair<Long, Long>>()
            for (s in sessions.filter { it.start < now }.sortedBy { it.start }) {
                val last = visits.lastOrNull()
                if (last != null && s.start - last.second <= SAME_VISIT_MS) {
                    visits[visits.lastIndex] = last.first to maxOf(last.second, s.end)
                } else {
                    visits += s.start to s.end
                }
            }
            val todayStart = TimeUtil.startOfDay(TimeUtil.dateOf(now, zone), zone)
            val today = visits.filter { it.first >= todayStart }
            val lastHour = visits.count { it.first >= now - TimeUtil.HOUR }
            val usedToday = sessions.filter { it.start >= todayStart && it.start < now }.sumOf { it.durationMs }
            // Earlier days' visits that started within an hour of this clock time.
            val minute = TimeUtil.minuteOfDay(now, zone)
            val around =
                visits.filter { it.first < todayStart }
                    .filter { v ->
                        val d = kotlin.math.abs(TimeUtil.minuteOfDay(v.first, zone) - minute)
                        minOf(d, 24 * 60 - d) <= 60
                    }
                    .map { it.second - it.first }
                    .sorted()
            val usual = if (around.size >= MIN_VISITS) around[around.size / 2].takeIf { it >= 2 * TimeUtil.MINUTE } else null
            // The visit that's starting now counts too.
            return PauseContext(today.size + 1, lastHour + 1, usedToday, usual)
        }

        private fun ordinal(n: Int): String =
            n.toString() +
                when {
                    n % 100 in 11..13 -> "th"
                    n % 10 == 1 -> "st"
                    n % 10 == 2 -> "nd"
                    n % 10 == 3 -> "rd"
                    else -> "th"
                }
    }
}

/** How a goal went on each of the last seven days, today included and still in progress. */
data class GoalDay(
    val date: LocalDate,
    /** null: no data that day. */
    val met: Boolean?,
    val today: Boolean,
)

/** A forgiving view of goals: how many of the last seven days went your way, not a streak. */
object GoalWeek {
    /**
     * Days under a daily phone-time target. [daily] is (day, ms) ending today; today counts as
     * met while it's still under.
     */
    fun underTarget(
        daily: List<Pair<LocalDate, Long>>,
        today: LocalDate,
        targetMs: Long,
    ): List<GoalDay> =
        lastSeven(today) { d -> daily.firstOrNull { it.first == d }?.second?.takeIf { it > 0 || d == today } }
            .map { (d, ms) -> GoalDay(d, ms?.let { it <= targetMs }, d == today) }

    /**
     * Days an app stayed below (or, for [more], reached) its average of the seven days before.
     * [last14] is minutes per day, oldest first, ending today.
     */
    fun againstLastWeek(
        last14: List<Float>,
        today: LocalDate,
        more: Boolean,
    ): List<GoalDay> {
        if (last14.size < 14) return emptyList()
        val before = last14.take(7).average()
        if (before <= 0.0) return emptyList()
        return last14.takeLast(7).mapIndexed { i, m ->
            val d = today.minusDays(6L - i)
            val met = if (more) m >= before else m <= before
            GoalDay(d, met, d == today)
        }
    }

    /** "5 of the last 7 days": days met, out of days with data. */
    fun count(days: List<GoalDay>): Pair<Int, Int> = days.count { it.met == true } to days.count { it.met != null }

    private fun <T> lastSeven(
        today: LocalDate,
        value: (LocalDate) -> T?,
    ): List<Pair<LocalDate, T?>> = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to value(it) }
}

/** A pause that only applies at night: from a minute of the evening (or after midnight) until 05:00. */
object NightWindow {
    const val END_MINUTE = 5 * 60

    fun contains(
        minute: Int,
        from: Int,
    ): Boolean =
        if (from >= END_MINUTE) {
            minute >= from || minute < END_MINUTE
        } else {
            minute in from until END_MINUTE
        }
}
