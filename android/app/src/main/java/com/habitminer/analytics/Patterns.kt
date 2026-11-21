package com.habitminer.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** What a pattern card offers to do about it. */
enum class PatternAction {
    NONE,

    /** Put the app on Use less with a mindful pause. */
    PAUSE_APP,

    /** The same, but only from [Pattern.nightFromMinute] until 05:00. */
    PAUSE_APP_AT_NIGHT,

    /** Open Android's notification settings for the app. */
    APP_NOTIFICATIONS,
    OPEN_SLEEP,

    /** Pause each of [Pattern.apps] from [Pattern.nightFromMinute] until 05:00. */
    PAUSE_APPS_AT_NIGHT,
}

enum class PatternKind {
    LONG_SESSIONS,
    LATE_WEEKDAY_SLEEP,
    WEEK_CHANGE,
    APP_WEEK_CHANGE,
    NOTIFICATION_PULL,
    LEAD_IN,
    BUSY_HOURS,
    CHECKING_PEAK,
    MORNING_APP,
    LATE_RECORD,
    LATE_FREE_RUN,
}

/**
 * Something true about how you use your phone, found in your own data and only shown when it
 * clearly holds. [fresh] ones follow the last week or two and change often; the others are
 * slower patterns over the last four weeks.
 */
data class Pattern(
    val kind: PatternKind,
    /** Stable for the same pattern about the same app. */
    val key: String,
    val title: String,
    val detail: String,
    val packageName: String? = null,
    val appName: String? = null,
    val action: PatternAction = PatternAction.NONE,
    val nightFromMinute: Int? = null,
    val fresh: Boolean = false,
    /** For ordering: roughly how strong or how new it is, 0–1. */
    val strength: Float = 0.5f,
    /** A good thing (a win), shown in a friendlier colour. */
    val good: Boolean = false,
    /** For [PatternAction.PAUSE_APPS_AT_NIGHT]: (package, name) of the apps, most first. */
    val apps: List<Pair<String, String>> = emptyList(),
) {
    /** A few words for the home-screen widget, for the patterns that change day to day. */
    val widgetLine: String?
        get() =
            when (kind) {
                PatternKind.LATE_FREE_RUN -> title.replace("without a long session after midnight", "without a late session")
                PatternKind.WEEK_CHANGE -> title.removePrefix("This week: ").replace(" than the week before", " than last week")
                PatternKind.APP_WEEK_CHANGE -> title
                else -> null
            }
}

/** One app's habit: how you get there, how long you stay, and where you go next. */
data class AppHook(
    val days: Int,
    val visits: Int,
    /** Visits under two minutes. */
    val quickShare: Float,
    val quickPerDay: Float,
    /** Visits of 15 minutes or more. */
    val longPerDay: Float,
    /** Share of the app's time spent in those long visits. */
    val longTimeShare: Float,
    /** The three hours most long visits start in (start hour), when there's a clear peak. */
    val longPeakHour: Int?,
    val longPeakShare: Float,
    /** Apps you were in just before (within two minutes), with their share of visits. */
    val from: List<Pair<String, Float>>,
    val afterNotificationShare: Float,
    /** First app after unlocking, or null without unlock data. */
    val afterUnlockShare: Float?,
    /** Where you go next (within two minutes). */
    val next: List<Pair<String, Float>>,
)

/** How a pause or Use less goal is working: pauses answered, and time against before. */
data class GoalImpact(
    val packageName: String,
    val since: Long,
    /** Full days since it started. */
    val days: Int,
    val pauses: Int,
    val closed: Int,
    val openedAnyway: Int,
    /** Daily time since (full days), and on usual days in the four weeks before. */
    val afterPerDayMs: Long?,
    val beforePerDayMs: Long?,
    /**
     * Your other leisure apps (social, video, games) since against before, per day; and the one
     * that went up most. Only set when this app went down and they clearly went up.
     */
    val spilloverMs: Long? = null,
    val spilloverApp: Pair<String, Long>? = null,
    /** This app down by 10+ min a day and other leisure apps not up by 10+: a real saving. */
    val noSpillover: Boolean = false,
) {
    val tooEarly: Boolean get() = days < PatternFinder.IMPACT_MIN_DAYS

    /** One plain sentence. */
    fun summary(appName: String): String {
        val parts = mutableListOf<String>()
        if (pauses > 0) parts += "You closed $appName at $closed of $pauses pauses"
        if (tooEarly) {
            parts += "a fair comparison needs ${PatternFinder.IMPACT_MIN_DAYS} full days (${days.coerceAtLeast(0)} so far)"
        } else if (afterPerDayMs != null && beforePerDayMs != null && beforePerDayMs > 0) {
            val diff = afterPerDayMs - beforePerDayMs
            parts +=
                when {
                    kotlin.math.abs(diff) < maxOf(5 * TimeUtil.MINUTE, beforePerDayMs / 10) ->
                        "${Format.duration(afterPerDayMs)} a day, about the same as before (${Format.duration(beforePerDayMs)}). No change yet"
                    diff < 0 -> "${Format.duration(afterPerDayMs)} a day, ${Format.duration(-diff)} less than your usual ${Format.duration(beforePerDayMs)}"
                    else -> "${Format.duration(afterPerDayMs)} a day, ${Format.duration(diff)} more than your usual ${Format.duration(beforePerDayMs)}"
                }
        }
        if (parts.isEmpty()) return "No pauses yet."
        var out = parts.joinToString("; ").replaceFirstChar { it.uppercase() } + "."
        if (!tooEarly) {
            spilloverMs?.let { sp ->
                out += " But your other leisure apps went up ${Format.duration(sp)} a day" +
                    (spilloverApp?.let { (n, ms) -> ", mostly $n (+${Format.duration(ms)})" } ?: "") + "."
            }
            if (noSpillover) out += " Your other leisure apps didn't make up for it."
        }
        return out
    }

    /**
     * When the pause isn't doing much after the first days: you mostly open the app anyway and
     * its time hasn't dropped. Null when it's working, or too early to say.
     */
    fun suggestion(nightOnly: Boolean): String? {
        if (tooEarly || pauses < 10) return null
        val closeRate = closed.toFloat() / pauses
        val dropped = afterPerDayMs != null && beforePerDayMs != null && afterPerDayMs < beforePerDayMs * 0.9
        if (closeRate >= 0.2f || dropped) return null
        return if (nightOnly) {
            "You open it anyway at most pauses, even at night. A pause may not be the right tool for this app; a daily reminder in Remind me might suit it better."
        } else {
            "You open it anyway at most pauses. A pause only at night, when long sessions cost the most, may suit it better."
        }
    }
}

/**
 * Finds [Pattern]s. Every threshold here was checked against real exports so that patterns show
 * when they hold and stay quiet when they don't; see docs/FINDINGS.md.
 */
object PatternFinder {
    private const val SAME_VISIT_MS = 60_000L
    private const val LINK_MS = 2 * TimeUtil.MINUTE

    /**
     * An app only counts as where you came from if you were in it this long. Launching an app
     * from the Google search bar or feed puts Google in front for a second or two; that's how
     * you opened it, not what led you there.
     */
    private const val MIN_LEAD_MS = 15_000L
    private const val QUICK_MS = 2 * TimeUtil.MINUTE
    private const val LONG_MS = 15 * TimeUtil.MINUTE
    private const val WINDOW_DAYS = 28L
    const val IMPACT_MIN_DAYS = 5

    /** A visit: one app, with gaps under a minute merged. */
    data class Visit(
        val packageName: String,
        val appName: String,
        val category: AppCategory,
        val start: Long,
        val end: Long,
    ) {
        val ms: Long get() = end - start
    }

    fun visits(sessions: List<UsageSession>): List<Visit> {
        val out = ArrayList<Visit>()
        for (s in sessions.sortedBy { it.start }) {
            if (s.end <= s.start) continue
            val last = out.lastOrNull()
            if (last != null && last.packageName == s.packageName && s.start - last.end <= SAME_VISIT_MS) {
                out[out.lastIndex] = last.copy(end = maxOf(last.end, s.end))
            } else {
                out += Visit(s.packageName, s.appName, s.category, s.start, s.end)
            }
        }
        return out
    }

    /**
     * @param sessions app use over the last few weeks (launchers already removed)
     * @param nights detected nights, oldest first
     */
    fun find(
        sessions: List<UsageSession>,
        notifications: List<NotificationEvent>,
        unlocks: List<Long>,
        nights: List<SleepEstimate>,
        today: LocalDate,
        now: Long,
        zone: ZoneId,
    ): List<Pattern> {
        val from = TimeUtil.startOfDay(today.minusDays(WINDOW_DAYS), zone)
        val recent = sessions.filter { it.start >= from && it.start < now }
        if (recent.isEmpty()) return emptyList()
        val visits = visits(recent)
        val days = recent.map { TimeUtil.dateOf(it.start, zone) }.distinct().size
        val out = mutableListOf<Pattern>()
        runCatching { out += longSessions(visits, days, today, zone) }
        runCatching { lateWeekdaySleep(nights, visits, zone)?.let { out += it } }
        runCatching { out += weekChanges(visits, today, zone) }
        runCatching { out += notificationPull(visits, notifications.filter { it.time >= from }) }
        runCatching { out += leadIns(visits) }
        runCatching { busyHours(recent, today, zone)?.let { out += it } }
        runCatching { checkingPeak(visits, days, zone)?.let { out += it } }
        runCatching { morningApp(visits, nights)?.let { out += it } }
        runCatching { out += lateNights(visits, today, now, zone) }
        return out.sortedByDescending { it.strength }
    }

    // ---- Long sessions: where the time really goes ----------------------------------------

    private fun longSessions(
        visits: List<Visit>,
        days: Int,
        today: LocalDate,
        zone: ZoneId,
    ): List<Pattern> {
        if (days < 7) return emptyList()
        val byApp = visits.groupBy { it.packageName }
        return byApp.entries.sortedByDescending { e -> e.value.sumOf { it.ms } }.take(5).mapNotNull { (pkg, vs) ->
            val total = vs.sumOf { it.ms }
            if (total / days < 20 * TimeUtil.MINUTE) return@mapNotNull null
            val long = vs.filter { it.ms >= LONG_MS }
            val longShare = long.sumOf { it.ms }.toFloat() / total
            val longPerDay = long.size.toFloat() / days
            if (longShare < 0.5f || longPerDay < 0.7f) return@mapNotNull null
            val quickPerDay = vs.count { it.ms < QUICK_MS }.toFloat() / days
            val (peak, peakShare) = peak3h(long.map { TimeUtil.zoned(it.start, zone).hour })
            val name = vs.first().appName
            val late = peakShare >= 0.4f && (peak >= 21 || peak <= 2)
            val n = Math.round(longPerDay).coerceAtLeast(1)
            val count = if (n == 1) "One long $name session a day makes" else "$n long $name sessions a day make"
            Pattern(
                kind = PatternKind.LONG_SESSIONS,
                key = "LONG|$pkg",
                title = "$count up ${Format.percent(longShare)} of its time",
                detail =
                    buildString {
                        append("Sessions of 15 minutes or more")
                        if (peakShare >= 0.4f) append(", mostly starting between ${hh(peak)} and ${hh(peak + 3)}")
                        append(".")
                        if (quickPerDay >= 1f) append(" The other ${Math.round(quickPerDay)} visits a day are mostly quick checks under 2 minutes.")
                    },
                packageName = pkg,
                appName = name,
                action = if (late) PatternAction.PAUSE_APP_AT_NIGHT else PatternAction.PAUSE_APP,
                // An hour before they usually start.
                nightFromMinute = if (late) Math.floorMod(peak - 1, 24) * 60 else null,
                strength = 0.6f + 0.4f * longShare,
            )
        }
    }

    // ---- Late weekday nights and sleep --------------------------------------------------

    private fun lateWeekdaySleep(
        nights: List<SleepEstimate>,
        visits: List<Visit>,
        zone: ZoneId,
    ): Pattern? {
        // Nights before a weekday morning: weekends you sleep in and make up for it.
        val wk = nights.filter { it.wakeDate.dayOfWeek != DayOfWeek.SATURDAY && it.wakeDate.dayOfWeek != DayOfWeek.SUNDAY && it.confidence != Confidence.LOW }
        if (wk.size < 10) return null
        val bed = wk.map { SleepDetector.bedtimeMinutes(it.sleepStart, zone) }
        val median = bed.sorted()[bed.size / 2]
        val early = wk.filterIndexed { i, _ -> bed[i] <= median }
        val late = wk.filterIndexed { i, _ -> bed[i] > median }
        if (early.size < 5 || late.size < 5) return null
        fun med(l: List<Long>) = l.sorted()[l.size / 2]
        val sleepEarly = med(early.map { it.durationMs })
        val sleepLate = med(late.map { it.durationMs })
        val cost = sleepEarly - sleepLate
        if (cost < 30 * TimeUtil.MINUTE) return null
        val wakeEarly = med(early.map { TimeUtil.minuteOfDay(it.wakeTime, zone).toLong() })
        val wakeLate = med(late.map { TimeUtil.minuteOfDay(it.wakeTime, zone).toLong() })
        val wakeShift = (wakeLate - wakeEarly).coerceAtLeast(0)
        val culprits = lateApps(visits, zone)
        return Pattern(
            kind = PatternKind.LATE_WEEKDAY_SLEEP,
            key = "LATE_SLEEP",
            title = "Late weekday nights cost you about ${Format.duration(cost)} of sleep",
            detail =
                "On weekday nights you fell asleep after about ${Format.clockFromMinutes(median)}, you slept ${Format.duration(sleepLate)} instead of " +
                    "${Format.duration(sleepEarly)}" +
                    (if (wakeShift >= 10) ": you woke ${Format.duration(wakeShift * TimeUtil.MINUTE)} later." else ": you woke at about the same time.") +
                    (if (culprits.isNotEmpty()) " Most of your long sessions after 23:00 are in ${names(culprits.map { it.second })}." else ""),
            action = if (culprits.isNotEmpty()) PatternAction.PAUSE_APPS_AT_NIGHT else PatternAction.OPEN_SLEEP,
            nightFromMinute = if (culprits.isNotEmpty()) 23 * 60 else null,
            apps = culprits,
            strength = (0.6f + cost.toFloat() / (4 * TimeUtil.HOUR)).coerceAtMost(1f),
        )
    }

    /** Categories you'd rarely want paused at night: work, maps, music, tools, health. */
    private val NOT_PAUSED_AT_NIGHT =
        setOf(AppCategory.PRODUCTIVITY, AppCategory.NAVIGATION, AppCategory.MUSIC, AppCategory.TOOLS, AppCategory.HEALTH)

    /**
     * The (up to three) apps behind your long late sessions: 15+ minutes starting after 23:00 or
     * before 05:00, or running past midnight (a 23:40 start that goes on to 01:00 counts).
     */
    private fun lateApps(
        visits: List<Visit>,
        zone: ZoneId,
    ): List<Pair<String, String>> {
        val late =
            visits.filter { v ->
                if (v.ms < LONG_MS || v.category in NOT_PAUSED_AT_NIGHT) return@filter false
                val h = TimeUtil.zoned(v.start, zone).hour
                h >= 23 || h < 5 || TimeUtil.dateOf(v.end, zone) != TimeUtil.dateOf(v.start, zone)
            }
        return late.groupBy { it.packageName }
            .filterValues { it.size >= 3 }
            .entries.sortedByDescending { e -> e.value.sumOf { it.ms } }
            .take(3)
            .map { it.key to it.value.first().appName }
    }

    private fun names(n: List<String>): String =
        when (n.size) {
            0 -> ""
            1 -> n[0]
            else -> n.dropLast(1).joinToString(", ") + " and " + n.last()
        }

    /** "YouTube and Evony", for buttons. */
    fun appNames(apps: List<Pair<String, String>>): String = names(apps.map { it.second })

    // ---- This week against the week before ----------------------------------------------

    private fun weekChanges(
        visits: List<Visit>,
        today: LocalDate,
        zone: ZoneId,
    ): List<Pattern> {
        // Full days only: the last 7 ending yesterday, and the 7 before.
        val curFrom = TimeUtil.startOfDay(today.minusDays(7), zone)
        val prevFrom = TimeUtil.startOfDay(today.minusDays(14), zone)
        val to = TimeUtil.startOfDay(today, zone)
        val haveDays = visits.map { TimeUtil.dateOf(it.start, zone) }.filter { !it.isBefore(today.minusDays(14)) && it.isBefore(today) }.distinct().size
        if (haveDays < 12) return emptyList()
        val cur = visits.filter { it.start in curFrom until to }
        val prev = visits.filter { it.start in prevFrom until curFrom }
        val out = mutableListOf<Pattern>()
        val c = cur.sumOf { it.ms } / 7
        val p = prev.sumOf { it.ms } / 7
        if (p > 0 && kotlin.math.abs(c - p) >= 30 * TimeUtil.MINUTE && kotlin.math.abs(c - p).toFloat() / p >= 0.1f) {
            val up = c > p
            out +=
                Pattern(
                    kind = PatternKind.WEEK_CHANGE,
                    key = "WEEK|$today",
                    title = "This week: ${Format.duration(kotlin.math.abs(c - p))} a day ${if (up) "more" else "less"} than the week before",
                    detail = "${Format.duration(c)} a day over the last 7 days, ${Format.duration(p)} the 7 before.",
                    fresh = true,
                    good = !up,
                    strength = (0.5f + kotlin.math.abs(c - p).toFloat() / p).coerceAtMost(0.95f),
                )
        }
        val curBy = cur.groupBy { it.packageName }.mapValues { e -> e.value.sumOf { it.ms } / 7 }
        val prevBy = prev.groupBy { it.packageName }.mapValues { e -> e.value.sumOf { it.ms } / 7 }
        val names = (cur + prev).associate { it.packageName to it.appName }
        val cats = (cur + prev).associate { it.packageName to it.category }
        (curBy.keys + prevBy.keys).mapNotNull { pkg ->
            val a = curBy[pkg] ?: 0L
            val b = prevBy[pkg] ?: 0L
            if (maxOf(a, b) < 20 * TimeUtil.MINUTE || b == 0L) return@mapNotNull null
            val rel = (a - b).toFloat() / b
            if (kotlin.math.abs(a - b) < 20 * TimeUtil.MINUTE || kotlin.math.abs(rel) < 0.3f) return@mapNotNull null
            Triple(pkg, a, b)
        }.sortedByDescending { (_, a, b) -> kotlin.math.abs(a - b) }.take(2).forEach { (pkg, a, b) ->
            val up = a > b
            val name = names[pkg] ?: pkg
            out +=
                Pattern(
                    kind = PatternKind.APP_WEEK_CHANGE,
                    key = "APPWEEK|$pkg|$today",
                    title = "$name ${if (up) "up" else "down"} ${Format.percent(kotlin.math.abs(a - b).toFloat() / b)} this week",
                    detail = "${Format.duration(a)} a day over the last 7 days, from ${Format.duration(b)} the 7 before.",
                    packageName = pkg,
                    appName = name,
                    action = if (up && cats[pkg]?.isLeisure == true) PatternAction.PAUSE_APP else PatternAction.NONE,
                    fresh = true,
                    good = !up,
                    strength = (0.45f + kotlin.math.abs(a - b).toFloat() / maxOf(a, b) / 2).coerceAtMost(0.9f),
                )
        }
        return out
    }

    // ---- Notifications that pull you in -------------------------------------------------

    private fun notificationPull(
        visits: List<Visit>,
        notifications: List<NotificationEvent>,
    ): List<Pattern> {
        if (notifications.isEmpty()) return emptyList()
        // Phone sessions: app use with gaps under a minute, named by the first app.
        data class Run(val first: Visit, val start: Long, var end: Long)
        val runs = mutableListOf<Run>()
        for (v in visits) {
            val last = runs.lastOrNull()
            if (last != null && v.start - last.end <= SAME_VISIT_MS) last.end = maxOf(last.end, v.end) else runs += Run(v, v.start, v.end)
        }
        val notes = notifications.sortedBy { it.time }
        val times = notes.map { it.time }.toLongArray()
        val started = HashMap<String, MutableList<Boolean>>()
        val base = mutableListOf<Boolean>()
        for (r in runs) {
            var j = upperBound(times, r.start) - 1
            var hit = false
            while (j >= 0 && r.start - times[j] <= SAME_VISIT_MS) {
                if (notes[j].packageName == r.first.packageName) {
                    hit = true
                    break
                }
                j--
            }
            val long = r.end - r.start >= LONG_MS
            if (hit) started.getOrPut(r.first.packageName) { mutableListOf() } += long else base += long
        }
        if (base.size < 50) return emptyList()
        val baseShare = base.count { it }.toFloat() / base.size
        val names = visits.associate { it.packageName to it.appName }
        return started.mapNotNull { (pkg, l) ->
            if (l.size < 20) return@mapNotNull null
            val share = l.count { it }.toFloat() / l.size
            if (share < 0.2f || share < 2 * baseShare) return@mapNotNull null
            val name = names[pkg] ?: pkg
            Pattern(
                kind = PatternKind.NOTIFICATION_PULL,
                key = "NOTE|$pkg",
                title = "$name notifications often pull you in",
                detail =
                    "When your phone use starts with a notification from $name, you stay 15 minutes or more ${Format.percent(share)} of the time " +
                        "(${l.count { it }} of ${l.size}). Without a notification it's ${Format.percent(baseShare)}.",
                packageName = pkg,
                appName = name,
                action = PatternAction.APP_NOTIFICATIONS,
                strength = (0.5f + (share - baseShare)).coerceAtMost(0.95f),
            )
        }
    }

    // ---- One app leading into another ---------------------------------------------------

    private fun leadIns(visits: List<Visit>): List<Pattern> {
        val count = visits.groupingBy { it.packageName }.eachCount()
        val links = HashMap<Pair<String, String>, Int>()
        for (i in 1 until visits.size) {
            val p = visits[i - 1]
            val c = visits[i]
            if (c.packageName != p.packageName && c.start - p.end <= LINK_MS && p.ms >= MIN_LEAD_MS && c.category.isLeisure) {
                links.merge(p.packageName to c.packageName, 1, Int::plus)
            }
        }
        val names = visits.associate { it.packageName to it.appName }
        return links.entries.mapNotNull { (k, n) ->
            val total = count[k.second] ?: 0
            if (total < 30) return@mapNotNull null
            val share = n.toFloat() / total
            if (share < 0.3f) return@mapNotNull null
            Triple(k, n, share)
        }.sortedByDescending { it.third }.distinctBy { (names[it.first.second] ?: it.first.second).substringBefore(" (") }.take(2).map { (k, n, share) ->
            val a = names[k.first] ?: k.first
            val b = names[k.second] ?: k.second
            Pattern(
                kind = PatternKind.LEAD_IN,
                key = "LEAD|${k.first}|${k.second}",
                title = "${Format.percent(share)} of your $b opens come right after $a",
                detail = "Within two minutes of leaving $a ($n of ${count[k.second]} visits in the last four weeks). A pause on $b catches that moment.",
                packageName = k.second,
                appName = b,
                action = PatternAction.PAUSE_APP,
                strength = 0.4f + share / 2,
            )
        }
    }

    // ---- A clear busiest stretch (only when use is really concentrated) -----------------

    private fun busyHours(
        sessions: List<UsageSession>,
        today: LocalDate,
        zone: ZoneId,
    ): Pattern? {
        val from = TimeUtil.startOfDay(today.minusDays(14), zone)
        val recent = sessions.filter { it.start >= from }
        val days = recent.map { TimeUtil.dateOf(it.start, zone) }.distinct().size
        if (days < 7) return null
        val byHour = LongArray(24)
        for (s in recent) {
            var t = s.start
            while (t < s.end) {
                val z = TimeUtil.zoned(t, zone)
                val next = z.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                byHour[z.hour] += minOf(s.end, next) - t
                t = minOf(s.end, next)
            }
        }
        val total = byHour.sum()
        if (total <= 0) return null
        val h = (0 until 24).maxBy { byHour[it] + byHour[(it + 1) % 24] }
        val share = (byHour[h] + byHour[(h + 1) % 24]).toFloat() / total
        if (share < 0.25f) return null
        return Pattern(
            kind = PatternKind.BUSY_HOURS,
            key = "BUSY|$h",
            title = "${Format.percent(share)} of your screen time is between ${hh(h)} and ${hh(h + 2)}",
            detail = "Over the last two weeks: about ${Format.duration((byHour[h] + byHour[(h + 1) % 24]) / days)} a day in those two hours. An even spread would be about 12%.",
            strength = 0.4f + share,
        )
    }

    // ---- When checking is most broken up ------------------------------------------------

    private fun checkingPeak(
        visits: List<Visit>,
        days: Int,
        zone: ZoneId,
    ): Pattern? {
        if (days < 4) return null
        val quick = visits.filter { it.ms < QUICK_MS }
        val perHour = DoubleArray(24)
        for (v in quick) {
            val h = TimeUtil.zoned(v.start, zone).hour
            perHour[h] += 1.0 / days
        }
        // The twelve busiest hours of the day, wherever they fall.
        val busiest = (0 until 24).sortedByDescending { perHour[it] }.take(12)
        val mean = busiest.map { perHour[it] }.average()
        if (mean < 10) return null
        val top = quick.groupingBy { it.appName }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
        val from = busiest.min()
        val to = busiest.max() + 1
        val wraps = busiest.any { it < 5 } && busiest.any { it > 18 }
        return Pattern(
            kind = PatternKind.CHECKING_PEAK,
            key = "CHECKS",
            title = "You open an app about every ${Math.round(60 / mean)} minutes, all day",
            detail =
                "About ${Math.round(mean)} quick checks (under 2 minutes) an hour in your busiest twelve hours" +
                    (if (!wraps) ", ${hh(from)} to ${hh(to)}" else "") +
                    (if (top.isNotEmpty()) ", mostly ${top.joinToString(", ")}" else "") +
                    ". They add little time, but each one breaks up what you were doing.",
            strength = 0.45f,
        )
    }

    // ---- The first app of the day --------------------------------------------------------

    private fun morningApp(
        visits: List<Visit>,
        nights: List<SleepEstimate>,
    ): Pattern? {
        val recent = nights.takeLast(14)
        if (recent.size < 7) return null
        val firsts = recent.mapNotNull { n ->
            // The alarm clock doesn't count as choosing an app.
            visits.firstOrNull { it.start >= n.wakeTime - TimeUtil.MINUTE && it.category != AppCategory.TOOLS && !it.appName.contains("Clock") }?.takeIf { it.start - n.wakeTime <= 5 * TimeUtil.MINUTE }
        }
        val top = firsts.groupingBy { it.packageName }.eachCount().maxByOrNull { it.value } ?: return null
        if (top.value * 2 < recent.size) return null
        val name = firsts.first { it.packageName == top.key }.appName
        return Pattern(
            kind = PatternKind.MORNING_APP,
            key = "MORNING|${top.key}",
            title = "$name is the first thing you open most mornings",
            detail = "On ${top.value} of the last ${recent.size} mornings, within 5 minutes of waking.",
            packageName = top.key,
            appName = name,
            action = PatternAction.PAUSE_APP,
            strength = 0.4f + top.value.toFloat() / recent.size / 3,
        )
    }

    // ---- Last night: a record, or a run of quiet nights ---------------------------------

    private fun lateNights(
        visits: List<Visit>,
        today: LocalDate,
        now: Long,
        zone: ZoneId,
    ): List<Pattern> {
        // A long visit that starts between midnight and 05:00 belongs to that date's night.
        val late = visits.filter { it.ms >= LONG_MS && TimeUtil.zoned(it.start, zone).hour < 5 }.groupBy { TimeUtil.dateOf(it.start, zone) }
        val haveDays = visits.map { TimeUtil.dateOf(it.start, zone) }.toSet()
        // Last night is settled once it's past 05:00.
        if (TimeUtil.zoned(now, zone).hour < 5) return emptyList()
        val out = mutableListOf<Pattern>()
        val lastNight = late[today].orEmpty().maxByOrNull { it.ms }
        val before = (1L..14L).map { today.minusDays(it) }.filter { it in haveDays }
        if (lastNight != null && lastNight.ms >= 30 * TimeUtil.MINUTE && before.size >= 7) {
            val prior = before.flatMap { late[it].orEmpty() }.maxOfOrNull { it.ms } ?: 0L
            if (prior in 1 until lastNight.ms) {
                out +=
                    Pattern(
                        kind = PatternKind.LATE_RECORD,
                        key = "LATEREC|$today",
                        title = "Last night: ${Format.duration(lastNight.ms)} in ${lastNight.appName}, your longest late session in two weeks",
                        detail = "From ${Format.clock(lastNight.start, zone)}. The longest one after midnight in the 14 nights before was ${Format.duration(prior)}.",
                        packageName = lastNight.packageName,
                        appName = lastNight.appName,
                        action = PatternAction.PAUSE_APP_AT_NIGHT,
                        nightFromMinute = 23 * 60,
                        fresh = true,
                        strength = 0.85f,
                    )
            }
        }
        // Nights in a row (ending last night) without a long session after midnight, when that
        // used to be common.
        var run = 0
        var d = today
        while (d in haveDays && late[d].isNullOrEmpty()) {
            run++
            d = d.minusDays(1)
        }
        val earlier = (run.toLong() until run + 14L).map { today.minusDays(it) }.filter { it in haveDays }
        val usual = earlier.count { !late[it].isNullOrEmpty() }
        if (run >= 3 && earlier.size >= 7 && usual * 2 >= earlier.size) {
            out +=
                Pattern(
                    kind = PatternKind.LATE_FREE_RUN,
                    key = "LATEFREE|$today",
                    title = "$run nights in a row without a long session after midnight",
                    detail = "Before that, $usual of ${earlier.size} nights had one.",
                    fresh = true,
                    good = true,
                    strength = 0.8f,
                )
        }
        return out
    }

    // ---- Still to come, for new users ---------------------------------------------------

    /** Patterns that need more days than you have yet, and roughly when they can show. */
    fun upcoming(
        daysOfData: Int,
        showing: Set<PatternKind> = emptySet(),
    ): List<String> {
        fun more(n: Int) = (n - daysOfData).let { if (it == 1) "1 more day" else "$it more days" }
        val out = mutableListOf<String>()
        if (daysOfData < 4) out += "Checking all day: in ${more(4)}"
        if (daysOfData < 7) out += "Long sessions, notifications that pull you in, and your mornings: in ${more(7)}"
        if (daysOfData < 14 && PatternKind.WEEK_CHANGE !in showing && PatternKind.APP_WEEK_CHANGE !in showing) {
            out += "This week against the week before: in ${more(14)}"
        }
        if (daysOfData < 21 && PatternKind.LATE_WEEKDAY_SLEEP !in showing) {
            out += "Late weekday nights and sleep: once there are 10 weekday nights, about ${if (daysOfData < 7) "3" else "2"} weeks in"
        }
        return out
    }

    // ---- App detail: how an app hooks you -------------------------------------------------

    fun hook(
        packageName: String,
        sessions: List<UsageSession>,
        notifications: List<Long>,
        unlocks: List<Long>,
        zone: ZoneId,
    ): AppHook? {
        val visits = visits(sessions)
        val mine = visits.withIndex().filter { it.value.packageName == packageName }
        if (mine.size < 10) return null
        val days = mine.map { TimeUtil.dateOf(it.value.start, zone) }.distinct().size.coerceAtLeast(1)
        val total = mine.sumOf { it.value.ms }.coerceAtLeast(1)
        val long = mine.filter { it.value.ms >= LONG_MS }
        val (peak, peakShare) = peak3h(long.map { TimeUtil.zoned(it.value.start, zone).hour })
        val from = HashMap<String, Int>()
        val next = HashMap<String, Int>()
        val notes = notifications.sorted().toLongArray()
        val unl = unlocks.sorted().toLongArray()
        var afterNote = 0
        var afterUnlock = 0
        for ((i, v) in mine) {
            val p = visits.getOrNull(i - 1)
            if (p != null && p.packageName != packageName && v.start - p.end <= LINK_MS && p.ms >= MIN_LEAD_MS) from.merge(p.appName, 1, Int::plus)
            val n = visits.getOrNull(i + 1)
            if (n != null && n.packageName != packageName && n.start - v.end <= LINK_MS) next.merge(n.appName, 1, Int::plus)
            val k = upperBound(notes, v.start) - 1
            if (k >= 0 && v.start - notes[k] <= SAME_VISIT_MS) afterNote++
            val u = upperBound(unl, v.start) - 1
            if (u >= 0 && v.start - unl[u] <= 30_000L && (p == null || p.end <= unl[u])) afterUnlock++
        }
        fun top(m: Map<String, Int>) = m.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value.toFloat() / mine.size }
        return AppHook(
            days = days,
            visits = mine.size,
            quickShare = mine.count { it.value.ms < QUICK_MS }.toFloat() / mine.size,
            quickPerDay = mine.count { it.value.ms < QUICK_MS }.toFloat() / days,
            longPerDay = long.size.toFloat() / days,
            longTimeShare = long.sumOf { it.value.ms }.toFloat() / total,
            longPeakHour = peak.takeIf { peakShare >= 0.4f && long.size >= 5 },
            longPeakShare = peakShare,
            from = top(from),
            afterNotificationShare = afterNote.toFloat() / mine.size,
            // Unlock events are patchy on some phones: only when there are plenty of them.
            afterUnlockShare = (afterUnlock.toFloat() / mine.size).takeIf { unlocks.size >= days * 20 },
            next = top(next),
        )
    }

    // ---- Goals: is the pause working? ---------------------------------------------------

    /**
     * @param pauses (time, outcome) of this app's pauses: "left", "opened" or "dismissed"
     * @param daily the app's time per day, any span covering the four weeks before [since]
     */
    fun impact(
        packageName: String,
        since: Long,
        pauses: List<Pair<Long, String>>,
        daily: Map<LocalDate, Long>,
        today: LocalDate,
        zone: ZoneId,
        /** Your other leisure apps' time per day, by app name (this one left out). */
        otherLeisure: Map<String, Map<LocalDate, Long>> = emptyMap(),
    ): GoalImpact {
        val start = TimeUtil.dateOf(since, zone)
        // Full days after the day it was set, up to yesterday.
        val after = generateSequence(start.plusDays(1)) { it.plusDays(1) }.takeWhile { it.isBefore(today) }.toList()
        // The four weeks before, leaving out the day it was set and the day before: a goal is
        // often set right after an unusual day, and comparing with that would flatter it.
        val before = (2L..29L).map { start.minusDays(it) }.filter { daily.containsKey(it) }
        val usual = before.map { daily[it] ?: 0L }.sorted().let { if (it.size >= 7) it[it.size / 2] else null }
        val mine = pauses.filter { it.first >= since }
        val afterMs = after.takeIf { it.isNotEmpty() }?.map { daily[it] ?: 0L }?.sorted()?.let { it[it.size / 2] }
        // Did the time just move to other leisure apps? Means over the same days, and only
        // clear changes (10+ min a day both ways), so ordinary noise doesn't read as a shift.
        var spill: Long? = null
        var spillApp: Pair<String, Long>? = null
        var noSpill = false
        val down = if (afterMs != null && usual != null) usual - afterMs else 0L
        if (after.size >= IMPACT_MIN_DAYS && before.size >= 7 && down >= 10 * TimeUtil.MINUTE && otherLeisure.isNotEmpty()) {
            fun mean(
                m: Map<LocalDate, Long>,
                ds: List<LocalDate>,
            ) = ds.sumOf { m[it] ?: 0L } / ds.size
            val change = otherLeisure.mapValues { (_, m) -> mean(m, after) - mean(m, before) }
            val total = change.values.sum()
            if (total >= 10 * TimeUtil.MINUTE) {
                spill = total
                spillApp = change.entries.maxByOrNull { it.value }?.takeIf { it.value >= 5 * TimeUtil.MINUTE }?.let { it.key to it.value }
            } else if (total < 10 * TimeUtil.MINUTE) {
                noSpill = true
            }
        }
        return GoalImpact(
            packageName = packageName,
            since = since,
            days = after.size,
            pauses = mine.size,
            closed = mine.count { it.second == "left" },
            openedAnyway = mine.count { it.second == "opened" },
            afterPerDayMs = afterMs,
            beforePerDayMs = usual,
            spilloverMs = spill,
            spilloverApp = spillApp,
            noSpillover = noSpill,
        )
    }

    // ---- Helpers ------------------------------------------------------------------------

    /** The three-hour block (by start hour, wrapping midnight) holding the most of [hours]. */
    private fun peak3h(hours: List<Int>): Pair<Int, Float> {
        if (hours.isEmpty()) return 0 to 0f
        val c = IntArray(24)
        hours.forEach { c[it]++ }
        val best = (0 until 24).maxBy { c[it] + c[(it + 1) % 24] + c[(it + 2) % 24] }
        return best to (c[best] + c[(best + 1) % 24] + c[(best + 2) % 24]).toFloat() / hours.size
    }

    private fun hh(h: Int) = String.format(java.util.Locale.US, "%02d:00", Math.floorMod(h, 24))

    private fun upperBound(
        a: LongArray,
        x: Long,
    ): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val m = (lo + hi) ushr 1
            if (a[m] <= x) lo = m + 1 else hi = m
        }
        return lo
    }
}
