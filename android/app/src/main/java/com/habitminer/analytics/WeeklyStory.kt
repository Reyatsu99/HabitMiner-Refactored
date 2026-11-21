package com.habitminer.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One card of the Weekly Story. */
data class StoryCard(
    val kind: Kind,
    /** Small label above the headline. */
    val label: String,
    /** The one big thing: a duration, a count or a short phrase. */
    val headline: String,
    val body: String,
    /** Optional per-day values for a small bar strip (Mon…Sun order of the week shown). */
    val bars: List<Pair<String, Long>> = emptyList(),
    /** For "Try this week": the pattern whose action the card offers to start. */
    val pattern: Pattern? = null,
    /** One thing to try, if there is one. */
    val tip: String? = null,
) {
    enum class Kind { WEEK, PHONE_FREE, ROUTINE, CHANGE, SLEEP, MILESTONES, PATTERN, GOALS, EXPERIMENT }
}

data class WeeklyStory(
    /** First and last day covered. */
    val from: LocalDate,
    val to: LocalDate,
    val cards: List<StoryCard>,
    /** Screen time per day for the month heatmap, oldest first (5 full weeks, Monday first). */
    val month: List<Pair<LocalDate, Long?>>,
)

/**
 * The Sunday recap: a handful of cards about the last seven days, written plainly and kindly,
 * ending with one small experiment to try. Built only from things HabitMiner already measures.
 */
object WeeklyStoryBuilder {
    private const val IMPACT_DAYS = PatternFinder.IMPACT_MIN_DAYS

    fun build(
        sessions: List<UsageSession>,
        unlocks: List<Long>,
        nights: List<SleepEstimate>,
        naps: List<Pair<Long, Long>>,
        patterns: List<PatternGroup>,
        dailyTargetMinutes: Int?,
        useLess: Set<String>,
        today: LocalDate,
        now: Long,
        zone: ZoneId,
        /** Your current patterns (already without the ones you hid), strongest first. */
        found: List<Pattern> = emptyList(),
        /** Each Use less app's name with how it's going. */
        goals: List<Pair<String, GoalImpact>> = emptyList(),
    ): WeeklyStory? {
        // The last seven full days; from Sunday 18:00 the week includes that Sunday.
        val to = if (today.dayOfWeek == DayOfWeek.SUNDAY && TimeUtil.hourOf(now, zone) >= 18) today else today.minusDays(1)
        val from = to.minusDays(6)
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        val byDay = sessions.groupBy { TimeUtil.dateOf(it.start, zone) }
        val totals = days.map { d -> d to (byDay[d]?.sumOf { it.durationMs } ?: 0L) }
        if (totals.count { it.second > 0 } < 3) return null
        val cards = mutableListOf<StoryCard>()
        val shortDay = { d: LocalDate -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

        // 1. The week in phone time.
        val withData = totals.filter { it.second > 0 }
        val avg = withData.sumOf { it.second } / withData.size
        val prevDays = days.map { it.minusDays(7) }
        val prevTotals = prevDays.mapNotNull { d -> byDay[d]?.sumOf { it.durationMs }?.takeIf { it > 0 } }
        val prevAvg = if (prevTotals.size >= 3) prevTotals.sum() / prevTotals.size else null
        val fullDay = { d: LocalDate -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
        val lightestDay = withData.minByOrNull { it.second }?.first
        val heaviestDay = withData.maxByOrNull { it.second }?.first
        val lighter = prevAvg != null && avg < prevAvg - 10 * TimeUtil.MINUTE
        val heavier = prevAvg != null && avg > prevAvg + 10 * TimeUtil.MINUTE
        cards +=
            StoryCard(
                StoryCard.Kind.WEEK,
                "Your week",
                when {
                    lighter -> "A lighter week: ${Format.duration(avg)} a day, ${Format.duration(prevAvg!! - avg)} less than the week before."
                    heavier -> "A heavier week: ${Format.duration(avg)} a day, ${Format.duration(avg - prevAvg!!)} more than the week before."
                    else -> "About ${Format.duration(avg)} a day on your phone, much like the week before."
                },
                when {
                    lighter ->
                        "Lightest on ${lightestDay?.let(fullDay)}. Changes that stick usually come from the situation (where the phone was, what else you were doing) more than from willpower."
                    heavier ->
                        "Heaviest on ${heaviestDay?.let(fullDay)}. Busy, stressful or bored weeks often show up like this: the phone fills the gaps. It's a signal, not a failure."
                    else -> "Your heaviest day was ${heaviestDay?.let(fullDay)} and your lightest ${lightestDay?.let(fullDay)}."
                },
                bars = totals.map { shortDay(it.first) to it.second },
                tip = if (lighter) "Think of one thing that made it easier, and keep it going." else if (heavier) "Look at what filled the time on ${heaviestDay?.let(fullDay)}." else null,
            )

        // 2. Phone-free best.
        val phoneFree =
            days.mapNotNull { d ->
                val wake = nights.firstOrNull { it.wakeDate == d }?.wakeTime ?: TimeUtil.at(d, 8, 0, zone)
                val end = minOf(nights.firstOrNull { it.wakeDate == d.plusDays(1) }?.sleepStart ?: TimeUtil.at(d, 23, 0, zone), TimeUtil.at(d.plusDays(1), 0, 0, zone), now)
                if (end <= wake) null else PhoneFree.longestToday(sessions, unlocks, wake, end, naps)?.let { d to it }
            }
        phoneFree.maxByOrNull { it.second.durationMs }?.let { (d, best) ->
            cards +=
                StoryCard(
                    StoryCard.Kind.PHONE_FREE,
                    "Your longest break",
                    "Your longest break from the phone was ${Format.duration(best.durationMs)}, on ${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}.",
                    "${Format.clock(best.start, zone)} to ${Format.clock(best.end, zone)}, awake and not using it. Stretches like this are when your attention gets to rest.",
                    tip = "Notice what you were doing then: it's the easiest thing to repeat.",
                )
        }

        // 3. A routine you kept.
        patterns.filter { TimeUtil.dateOf(it.lastSeen, zone) >= from }.maxByOrNull { it.bestConfidence * it.totalOccurrences }?.let { g ->
            cards +=
                StoryCard(
                    StoryCard.Kind.ROUTINE,
                    "A routine you repeat",
                    "${g.whenText}, you tend to go ${g.sequence}.",
                    "Routines like this run on autopilot: one app cues the next without you deciding. Knowing it is the first step if you'd like to change it.",
                )
        }

        // 4. Biggest change by app.
        val thisWeek = sessions.filter { TimeUtil.dateOf(it.start, zone) in days }
        val lastWeek = sessions.filter { TimeUtil.dateOf(it.start, zone) in prevDays }
        if (prevTotals.size >= 3) {
            val names = (thisWeek + lastWeek).associate { it.appName to it.packageName }
            val now7 = thisWeek.groupBy { it.appName }.mapValues { (_, v) -> v.sumOf { it.durationMs } / 7 }
            val prev7 = lastWeek.groupBy { it.appName }.mapValues { (_, v) -> v.sumOf { it.durationMs } / 7 }
            val change = (now7.keys + prev7.keys).map { app -> app to ((now7[app] ?: 0L) - (prev7[app] ?: 0L)) }.maxByOrNull { kotlin.math.abs(it.second) }
            if (change != null && kotlin.math.abs(change.second) >= 10 * TimeUtil.MINUTE) {
                val (app, delta) = change
                val goal = names[app]?.let { it in useLess } == true
                cards +=
                    StoryCard(
                        StoryCard.Kind.CHANGE,
                        "Biggest change",
                        "$app took ${Format.duration(kotlin.math.abs(delta))} a day ${if (delta < 0) "less" else "more"} this week.",
                        when {
                            goal && delta < 0 -> "That's one you wanted to use less, so it's working."
                            delta < 0 -> "Something else filled that time. Whatever it was, it changed your week more than anything else."
                            else -> "Worth asking honestly: did you choose that, or did it just happen?"
                        },
                    )
            }
        }

        // 5. Sleep.
        val weekNights = nights.filter { it.wakeDate in days }
        if (weekNights.size >= 3) {
            val avgNight = weekNights.sumOf { it.durationMs } / weekNights.size
            val beds = weekNights.map { SleepDetector.bedtimeMinutes(it.sleepStart, zone) }
            val spread = beds.maxOrNull()!! - beds.minOrNull()!!
            cards +=
                StoryCard(
                    StoryCard.Kind.SLEEP,
                    "Sleep",
                    "You slept about ${Format.duration(avgNight)} a night.",
                    if (spread <= 60) {
                        "You fell asleep around ${Format.clockFromMinutes(beds.average().toInt())}, within an hour each night. A steady bedtime is one of the simplest things that helps sleep."
                    } else {
                        "Your bedtime moved by ${Format.duration(spread * TimeUtil.MINUTE)} across the week. A bedtime that keeps shifting works like a small jet lag: mornings feel harder even with the same hours."
                    },
                    tip = if (spread > 60) "Pick one bedtime for weeknights and keep it for a week." else null,
                )
        }

        // 6. Milestones.
        val milestones = mutableListOf<String>()
        dailyTargetMinutes?.let { t ->
            val under = withData.count { it.second <= t * TimeUtil.MINUTE }
            if (under > 0) milestones += "$under of ${withData.size} days under your ${Format.duration(t * TimeUtil.MINUTE)} target"
        }
        val earlier = sessions.filter { TimeUtil.dateOf(it.start, zone).isBefore(from) }.groupBy { TimeUtil.dateOf(it.start, zone) }.mapValues { (_, v) -> v.sumOf { it.durationMs } }
        val lightest = withData.minByOrNull { it.second }
        if (lightest != null && earlier.size >= 14 && lightest.second < (earlier.values.minOrNull() ?: Long.MAX_VALUE)) {
            milestones += "${lightest.first.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} was your lightest day in ${earlier.size + 7} days"
        }
        val unlockCounts = days.map { d -> unlocks.count { TimeUtil.dateOf(it, zone) == d } }.filter { it > 0 }
        if (unlockCounts.size >= 3) milestones += "About ${unlockCounts.sum() / unlockCounts.size} pickups a day"
        if (milestones.isNotEmpty()) {
            cards += StoryCard(StoryCard.Kind.MILESTONES, "This week", milestones.first(), milestones.drop(1).joinToString(". ").let { if (it.isEmpty()) "" else "$it." })
        }

        // 7. A win from last night or this week, else the strongest of your patterns.
        val win = found.firstOrNull { it.kind == PatternKind.LATE_FREE_RUN }
        val pick = win ?: found.firstOrNull { !it.fresh }
        pick?.let { p ->
            Explainer.explain(p)?.let { e ->
                cards += StoryCard(StoryCard.Kind.PATTERN, e.kicker, e.headline, e.meaning, pattern = p.takeIf { it.action != PatternAction.NONE }, tip = e.tip)
            }
        }

        // 8. Is it working? Use less apps with a fair comparison, biggest drop first; the
        // ones still too new are counted in one line, not repeated.
        if (goals.isNotEmpty()) {
            val ready = goals.filterNot { it.second.tooEarly }
            val early = goals.filter { it.second.tooEarly }
            fun drop(im: GoalImpact) = (im.beforePerDayMs ?: 0L) - (im.afterPerDayMs ?: 0L)
            val best = ready.maxByOrNull { drop(it.second) }
            val headline =
                when {
                    best == null -> "Too early to tell"
                    drop(best.second) > 5 * TimeUtil.MINUTE -> "${best.first}: ${Format.duration(drop(best.second))} less a day"
                    else -> "No change yet"
                }
            val lines = ready.sortedByDescending { drop(it.second) }.take(3).map { (n, im) -> "$n: ${im.summary(n)}" }.toMutableList()
            if (early.isNotEmpty()) {
                val firstReady = early.minOf { TimeUtil.dateOf(it.second.since, zone) }.plusDays(IMPACT_DAYS + 1L)
                val names = early.map { it.first }
                val who = if (names.size <= 3) names.joinToString(", ") else "${names.size} apps"
                lines +=
                    "$who: added too recently for a fair comparison. The first results come " +
                    "${firstReady.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${firstReady.dayOfMonth} ${firstReady.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}."
            }
            cards += StoryCard(StoryCard.Kind.GOALS, "Is it working?", headline, lines.joinToString("\n\n"))
        }

        // 9. One thing to try: the biggest lever among your patterns that isn't set up yet,
        // else one small experiment from the week's own data.
        cards += lever(found, useLess) ?: experiment(thisWeek, useLess, avg, zone)

        // Month heatmap: five full weeks ending this week, Monday first.
        val lastMonday = to.minusDays((to.dayOfWeek.value - 1).toLong())
        val start = lastMonday.minusWeeks(4)
        val month =
            generateSequence(start) { it.plusDays(1) }.take(35).map { d ->
                d to (if (d.isAfter(today)) null else byDay[d]?.sumOf { it.durationMs } ?: if (d.isBefore(sessions.minOfOrNull { TimeUtil.dateOf(it.start, zone) } ?: today)) null else 0L)
            }.toList()
        return WeeklyStory(from, to, cards, month)
    }

    /**
     * The one pattern most worth acting on this week, if any isn't acted on yet: late weekday
     * nights first (sleep), then long sessions, notifications that pull you in, and checking.
     * Next week's "Is it working?" card shows how it went.
     */
    private fun lever(
        found: List<Pattern>,
        useLess: Set<String>,
    ): StoryCard? {
        val order =
            listOf(PatternKind.LATE_WEEKDAY_SLEEP, PatternKind.LONG_SESSIONS, PatternKind.NOTIFICATION_PULL, PatternKind.NOTIFICATION_NOISE, PatternKind.CHECKING_PEAK, PatternKind.LEAD_IN)
        val open =
            found.filter { p ->
                when (p.action) {
                    PatternAction.PAUSE_APPS_AT_NIGHT -> p.apps.any { it.first !in useLess }
                    PatternAction.PAUSE_APP, PatternAction.PAUSE_APP_AT_NIGHT -> p.packageName != null && p.packageName !in useLess
                    PatternAction.APP_NOTIFICATIONS -> p.packageName != null
                    else -> false
                }
            }
        val p = open.minByOrNull { order.indexOf(it.kind).let { i -> if (i < 0) order.size else i } } ?: return null
        val headline =
            when (p.action) {
                PatternAction.PAUSE_APPS_AT_NIGHT -> "Pause ${PatternFinder.appNames(p.apps)} after ${Format.clockFromMinutes(p.nightFromMinute ?: 23 * 60)} on weeknights"
                PatternAction.PAUSE_APP_AT_NIGHT -> "Pause ${p.appName} after ${Format.clockFromMinutes(p.nightFromMinute ?: 23 * 60)}"
                PatternAction.APP_NOTIFICATIONS -> "Quieter ${p.appName} notifications"
                else -> "A pause before ${p.appName}"
            }
        val e = Explainer.explain(p)
        return StoryCard(
            StoryCard.Kind.EXPERIMENT,
            "Try this week",
            headline,
            (e?.headline?.let { "$it " } ?: "") +
                if (p.action == PatternAction.APP_NOTIFICATIONS) {
                    "Turning its notifications down takes a minute; if it still pulls you in, it'll stay in Your patterns."
                } else {
                    "Try it for 7 days; next Sunday's Story shows whether it worked."
                },
            pattern = p,
        )
    }

    private fun experiment(
        week: List<UsageSession>,
        useLess: Set<String>,
        avgPerDay: Long,
        zone: ZoneId,
    ): StoryCard {
        val late = week.filter { TimeUtil.hourOf(it.start, zone) >= 23 || TimeUtil.hourOf(it.start, zone) < 4 }
        val lateDays = late.map { TimeUtil.dateOf(it.start, zone) }.distinct().size
        val lessApp =
            week.filter { it.packageName in useLess }.groupBy { it.appName }.maxByOrNull { (_, v) -> v.sumOf { it.durationMs } }?.key
        val (headline, body) =
            when {
                lateDays >= 3 ->
                    "Phone down by 23:00, twice" to
                        "You used the phone after 23:00 on $lateDays nights. Try leaving it out of reach by 23:00 on two nights and see how mornings feel."
                lessApp != null ->
                    "$lessApp after 10:00 only" to
                        "On two days, keep $lessApp closed until 10:00. Mornings tend to set the tone for the rest of the day."
                avgPerDay >= 4 * TimeUtil.HOUR ->
                    "One phone-free hour a day" to
                        "Pick one hour each day, like a meal or a walk, and leave the phone in another room. Watch how the phone-free card changes."
                else ->
                    "Notice your first pickup" to
                        "For two mornings, notice what makes you pick up the phone first. No need to change it, just notice."
            }
        return StoryCard(StoryCard.Kind.EXPERIMENT, "One small experiment", headline, body)
    }
}

/** "Welcome back": how a break went against the two weeks before it. */
data class BreakSummary(
    val days: Int,
    val avgPerDayMs: Long,
    val usualPerDayMs: Long?,
    val pickupsPerDay: Int,
    val usualPickupsPerDay: Int?,
) {
    val headline: String
        get() =
            when {
                usualPerDayMs == null -> "${Format.duration(avgPerDayMs)} a day on your phone during the break"
                avgPerDayMs < usualPerDayMs * 0.85 -> "${Format.duration(usualPerDayMs - avgPerDayMs)} a day less than before your break"
                avgPerDayMs > usualPerDayMs * 1.15 -> "${Format.duration(avgPerDayMs - usualPerDayMs)} a day more than before your break"
                else -> "About your usual phone time during the break"
            }

    companion object {
        fun compute(
            sessions: List<UsageSession>,
            unlocks: List<Long>,
            from: LocalDate,
            until: LocalDate,
            zone: ZoneId,
        ): BreakSummary {
            fun range(
                a: LocalDate,
                b: LocalDate,
            ) = generateSequence(a) { it.plusDays(1) }.takeWhile { !it.isAfter(b) }.toList()
            val breakDays = range(from, until)
            val before = range(from.minusDays(14), from.minusDays(1))
            val byDay = sessions.groupBy { TimeUtil.dateOf(it.start, zone) }.mapValues { (_, v) -> v.sumOf { it.durationMs } }
            val unlocksByDay = unlocks.groupingBy { TimeUtil.dateOf(it, zone) }.eachCount()
            val beforeDays = before.filter { (byDay[it] ?: 0L) > 0 }
            return BreakSummary(
                days = breakDays.size,
                avgPerDayMs = breakDays.sumOf { byDay[it] ?: 0L } / breakDays.size.coerceAtLeast(1),
                usualPerDayMs = if (beforeDays.size >= 3) beforeDays.sumOf { byDay.getValue(it) } / beforeDays.size else null,
                pickupsPerDay = breakDays.sumOf { unlocksByDay[it] ?: 0 } / breakDays.size.coerceAtLeast(1),
                usualPickupsPerDay = if (beforeDays.size >= 3) beforeDays.sumOf { unlocksByDay[it] ?: 0 } / beforeDays.size else null,
            )
        }
    }
}
