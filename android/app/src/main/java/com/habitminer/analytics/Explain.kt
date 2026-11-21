package com.habitminer.analytics

import java.time.LocalDate
import java.time.ZoneId

/** The colour family of a story slide. */
enum class StoryTone { SCREEN, SLEEP, GOOD, NOTIFY, CHECK, CALM }

/**
 * One slide of a story on Today: what's going on in one plain sentence, what it means (how
 * habits like this work), and one thing to try. Numbers stay in the background.
 */
data class StorySlide(
    val id: String,
    val kicker: String,
    val headline: String,
    /** What it means. */
    val meaning: String,
    /** One thing to try, or null. */
    val tip: String? = null,
    /** The pattern behind it, for its one-tap action. */
    val pattern: Pattern? = null,
    val tone: StoryTone = StoryTone.CALM,
    /** The numbers behind it, for anyone who wants them. */
    val numbers: String? = null,
)

/**
 * Turns patterns and last night into short stories in everyday words. Explanations stay general
 * and hedged ("often", "for many people"): they describe how habits like these usually work,
 * not a diagnosis.
 */
object Explainer {
    /** Up to five slides for today: anything new first, then the slower patterns in turn. */
    fun today(
        patterns: List<Pattern>,
        today: LocalDate,
        /** The insight of the day, (headline, what it means), if there is one. */
        insight: Pair<String, String>? = null,
    ): List<StorySlide> {
        val fresh = patterns.filter { it.fresh }.sortedByDescending { it.strength }
        val slow = patterns.filterNot { it.fresh }
        // The slower ones take turns, so each day starts somewhere different.
        val turn = if (slow.isEmpty()) emptyList() else slow.indices.map { slow[(it + today.toEpochDay().toInt()) % slow.size] }
        // One or two a day: the newest thing (this week, last night), and one slower pattern in
        // turn, so tomorrow's is different.
        val out = mutableListOf<StorySlide>()
        insight?.let { (h, m) -> out += StorySlide("insight", "Today", h, m, tone = StoryTone.CALM) }
        fresh.firstOrNull()?.let { p -> explain(p)?.let { out += it } }
        turn.firstOrNull()?.let { p -> explain(p)?.let { out += it } }
        return out.distinctBy { it.id }.take(2)
    }

    fun explain(p: Pattern): StorySlide? {
        val app = p.appName ?: "this app"
        return when (p.kind) {
            PatternKind.LONG_SESSIONS ->
                StorySlide(
                    p.key, "Where your time goes",
                    "$app doesn't take your time in small bits. It takes it in a few long sittings.",
                    "Apps that keep playing the next thing have no natural point to stop, so \"one more\" quietly turns into an hour. " +
                        "That's how they're designed, not a lack of willpower.",
                    if (p.action == PatternAction.PAUSE_APP_AT_NIGHT) "Decide when you'll stop before you open it, or let a pause catch you late at night." else "Decide when you'll stop before you open it.",
                    p, StoryTone.SCREEN, "${p.title}. ${p.detail}",
                )
            PatternKind.LATE_WEEKDAY_SLEEP ->
                StorySlide(
                    p.key, "Your sleep",
                    "Late weekday nights are costing you sleep.",
                    "On weekdays you still get up at about the same time, so every late hour comes straight out of your sleep. " +
                        "And short sleep makes the next evening's scrolling easier to slip into: a tired brain reaches for quick, easy rewards.",
                    if (p.apps.isNotEmpty()) "Put a gentle stop on ${PatternFinder.appNames(p.apps)} from 23:00 on weeknights." else "Pick a time to put the phone away on weeknights.",
                    p, StoryTone.SLEEP, "${p.title}. ${p.detail}",
                )
            PatternKind.NOTIFICATION_PULL ->
                StorySlide(
                    p.key, "Notifications",
                    "$app's notifications decide when you pick up your phone.",
                    "Alerts like these are designed to bring you back, and once you're in you often stay a while. " +
                        "The alert chooses the moment, not you.",
                    "Turn $app's notifications down to only the ones you really need.",
                    p, StoryTone.NOTIFY, p.detail,
                )
            PatternKind.CHECKING_PEAK ->
                StorySlide(
                    p.key, "Your attention",
                    "You open an app every few minutes, all day.",
                    "Each check takes seconds, but every one pulls your attention away, and it takes a while to settle back into what you were doing. " +
                        "Checks you start yourself are usually a habit loop: a moment of boredom, a quick look, a small reward.",
                    when {
                        p.action == PatternAction.APP_NOTIFICATIONS && p.apps.isNotEmpty() ->
                            "Quieter notifications from ${p.appName} would cut some of them. ${PatternFinder.appNames(p.apps)} you open by yourself: move ${if (p.apps.size == 1) "it" else "them"} off your home screen so checking takes one more step."
                        p.action == PatternAction.APP_NOTIFICATIONS -> "Quieter notifications from ${p.appName} would cut some of them."
                        p.apps.isNotEmpty() ->
                            "${PatternFinder.appNames(p.apps)}: you open ${if (p.apps.size == 1) "it" else "them"} by yourself. Move ${if (p.apps.size == 1) "it" else "them"} off your home screen so checking takes one more step."
                        else -> "Move the apps you check most off your home screen, so checking takes one more step."
                    },
                    p, StoryTone.CHECK, "${p.title}. ${p.detail}",
                )
            PatternKind.WEEK_CHANGE ->
                if (p.good) {
                    StorySlide(
                        p.key, "This week", "You were on your phone less this week.",
                        "Something was different, and it's worth noticing what. Small changes that stick usually come from the situation (where the phone was, what else you were doing) more than from willpower.",
                        "Think of one thing that made it easier, and keep it going.", p, StoryTone.GOOD, "${p.title}. ${p.detail}",
                    )
                } else {
                    StorySlide(
                        p.key, "This week", "This week took more of your time than the last.",
                        "Busy, stressful or bored weeks often show up this way: the phone fills the gaps. It's a signal to look at, not a failure.",
                        "Look at which app grew most, and whether that was your choice.", p, StoryTone.SCREEN, "${p.title}. ${p.detail}",
                    )
                }
            PatternKind.APP_WEEK_CHANGE ->
                if (p.good) {
                    StorySlide(
                        p.key, "This week", "$app took much less of your time this week.",
                        "When an app loses its grip like this, it's often because something else filled that time. Whatever it was, it's working.",
                        null, p, StoryTone.GOOD, "${p.title}. ${p.detail}",
                    )
                } else {
                    StorySlide(
                        p.key, "This week", "$app took a lot more of your time this week.",
                        "Worth asking honestly: did you choose that, or did it just happen? Both are fine, but only one is a decision.",
                        if (p.action == PatternAction.PAUSE_APP) "If it just happened, a short pause before opening it gives you that moment to decide." else null,
                        p, StoryTone.SCREEN, "${p.title}. ${p.detail}",
                    )
                }
            PatternKind.LEAD_IN ->
                StorySlide(
                    p.key, "How it starts",
                    "Opening $app is often not a decision. It's what your thumb does next.",
                    "Habits chain together: finishing one thing on the phone cues the next, without you choosing it.",
                    "A short pause before $app gives you a second to notice.", p, StoryTone.CHECK, "${p.title}. ${p.detail}",
                )
            PatternKind.MORNING_APP ->
                StorySlide(
                    p.key, "Your mornings",
                    "Your day starts in $app, before you're properly awake.",
                    "What you see first tends to set the tone for the morning: other people's news and feeds before your own plans.",
                    "For a few days, leave $app until after breakfast and see how mornings feel.", p, StoryTone.CALM, p.detail,
                )
            PatternKind.BUSY_HOURS ->
                StorySlide(
                    p.key, "Your day", "Most of your phone time lands in the same two hours.",
                    "That's when the habit is strongest, so it's the easiest place to make a change.",
                    "Plan something for that time that doesn't need the phone.", p, StoryTone.SCREEN, "${p.title}. ${p.detail}",
                )
            PatternKind.LATE_RECORD ->
                StorySlide(
                    p.key, "Last night", "Last night ran later than any night in two weeks.",
                    "One late night is fine. It's worth noticing how today feels, because that's what the next late night costs.",
                    "If it's becoming a habit, let a pause catch you after 23:00.", p, StoryTone.SLEEP, "${p.title}. ${p.detail}",
                )
            PatternKind.NOTIFICATION_NOISE ->
                StorySlide(
                    p.key, "Notifications",
                    "$app interrupts you a lot, and you almost never open what it sends.",
                    "Each alert still pulls your eyes to the screen for a moment, even when you swipe it away. If you read them on the lock screen and that's all you need, fine; if not, they're just noise.",
                    "Turn off the $app notifications you don't need.",
                    p, StoryTone.NOTIFY, "${p.title}. ${p.detail}",
                )
            PatternKind.LATE_FREE_RUN ->
                StorySlide(
                    p.key, "A good run", p.title.replace("without a long session after midnight", "without a long late-night session") + ".",
                    "Nights like these give your sleep a real chance. Runs are easier to keep going than to start, so tonight counts.",
                    null, p, StoryTone.GOOD, p.detail,
                )
        }
    }

    /** Last night in a few slides: how long, why, what came before it, and what to do. */
    fun lastNight(
        night: SleepEstimate,
        /** Your usual night over the last week, if known. */
        usualMs: Long?,
        /** Your usual time of falling asleep, in [SleepDetector.bedtimeMinutes] form. */
        usualBedMinutes: Int?,
        zone: ZoneId,
        /** The apps behind your late sessions, (package, name), from the sleep pattern if there is one. */
        lateApps: List<Pair<String, String>> = emptyList(),
    ): List<StorySlide> {
        val out = mutableListOf<StorySlide>()
        val dur = night.durationMs
        val bed = SleepDetector.bedtimeMinutes(night.sleepStart, zone)
        val diff = usualMs?.let { dur - it }
        val short = diff != null && diff <= -30 * TimeUtil.MINUTE
        val headline =
            when {
                diff == null -> "You slept ${Format.duration(dur)}."
                short -> "You slept ${Format.duration(dur)}, about ${Format.duration(-diff!!)} less than usual."
                diff >= 30 * TimeUtil.MINUTE -> "You slept ${Format.duration(dur)}, about ${Format.duration(diff)} more than usual."
                else -> "You slept ${Format.duration(dur)}, about your usual."
            }
        val lateBy = usualBedMinutes?.let { bed - it }
        val sleptThrough = night.evidence.firstOrNull { it.startsWith("slept through the alarm") }
        val why =
            buildString {
                append("You fell asleep around ${Format.clock(night.sleepStart, zone)} and woke at ${Format.clock(night.wakeTime, zone)}")
                when {
                    lateBy != null && lateBy >= 30 -> append(", later than your usual ${Format.clockFromMinutes(DayPulse.clockMinute(usualBedMinutes))}. ")
                    lateBy != null && lateBy <= -30 -> append(", earlier than usual. ")
                    else -> append(". ")
                }
                if (short && lateBy != null && lateBy >= 30) {
                    append("Going to bed late and getting up at the usual time is where the lost sleep came from. ")
                }
                if (night.briefWakes.size >= 3) {
                    append("You woke briefly ${night.briefWakes.size} times; checking the phone in the night makes it harder to drift off again. ")
                } else if (night.glances >= 3) {
                    append("You looked at the lock screen ${night.glances} times in the night; even a glance at the time can wake you up a little more. ")
                }
                // "slept through the alarm at 07:30" or "... 3 times from 07:30".
                sleptThrough?.let { append("You ${it}, so your body probably needed the sleep.") }
            }.trim()
        // A short night gets something to do about it: pause the late apps, or see your nights.
        val nightAction =
            when {
                !short -> null
                lateApps.isNotEmpty() ->
                    Pattern(
                        PatternKind.LATE_WEEKDAY_SLEEP, "night|pause", "A short night", "",
                        action = PatternAction.PAUSE_APPS_AT_NIGHT, nightFromMinute = 23 * 60, apps = lateApps,
                    )
                else -> Pattern(PatternKind.LATE_WEEKDAY_SLEEP, "night|sleep", "A short night", "", action = PatternAction.OPEN_SLEEP)
            }
        out +=
            StorySlide(
                "night", "Last night", headline, why,
                if (short) {
                    if (lateApps.isNotEmpty()) "Tonight, start winding down 30 minutes earlier, and let a pause on ${PatternFinder.appNames(lateApps)} remind you after 23:00." else "Tonight, try to start winding down 30 minutes earlier."
                } else {
                    null
                },
                pattern = nightAction,
                tone = StoryTone.SLEEP,
            )
        if (night.preSleepUseMs >= 20 * TimeUtil.MINUTE) {
            val app = night.lastAppBeforeSleep
            val pkg = night.lastPackageBeforeSleep
            val dark = (night.preSleepDarkShare ?: 0f) >= 0.6f
            out +=
                StorySlide(
                    "presleep", "Before you slept",
                    "You were on your phone for ${Format.duration(night.preSleepUseMs)} right before sleeping" +
                        (app?.let { ", ending in $it." } ?: "."),
                    (if (dark) "Most of it was in the dark. " else "") +
                        "Phones keep your mind switched on: the content asks for one more reply or one more video, and that tends to push sleep later. " +
                        "Many people fall asleep faster with a short phone-free wind-down.",
                    if (night.lastAppLeisure && app != null) "Let a pause catch $app after 23:00, so the last half hour is yours." else "Put the phone out of reach 20 minutes before you want to sleep.",
                    pattern =
                        if (night.lastAppLeisure && pkg != null && app != null) {
                            Pattern(PatternKind.LATE_RECORD, "presleep|$pkg", "Last app before sleep", "", pkg, app, PatternAction.PAUSE_APP_AT_NIGHT, 23 * 60)
                        } else {
                            null
                        },
                    tone = StoryTone.SLEEP,
                )
        }
        night.firstAppAfterWake?.let { first ->
            out +=
                StorySlide(
                    "morning", "This morning", "The first thing you opened was $first.",
                    "What you see first tends to set the tone for the morning. Starting with your own plans instead of a feed can make the day feel more yours.",
                    tone = StoryTone.CALM,
                )
        }
        return out
    }

    /** The Weekly Story as tap-through slides, in the same voice. */
    fun week(story: WeeklyStory): List<StorySlide> =
        story.cards.mapIndexed { i, c ->
            StorySlide(
                id = "week$i",
                kicker = c.label,
                headline = c.headline,
                meaning = c.body,
                tip = c.tip,
                pattern = c.pattern,
                tone =
                    when (c.kind) {
                        StoryCard.Kind.WEEK, StoryCard.Kind.CHANGE -> StoryTone.SCREEN
                        StoryCard.Kind.SLEEP -> StoryTone.SLEEP
                        StoryCard.Kind.PHONE_FREE, StoryCard.Kind.GOALS -> StoryTone.GOOD
                        StoryCard.Kind.ROUTINE, StoryCard.Kind.PATTERN -> StoryTone.CHECK
                        StoryCard.Kind.MILESTONES -> StoryTone.CALM
                        StoryCard.Kind.EXPERIMENT -> StoryTone.NOTIFY
                    },
            )
        }

    /**
     * What the insight of the day means, in a sentence or two, from its key
     * ("date|FAMILY|KIND|subject"). The numbers stay in the insight's own text.
     */
    fun insightMeaning(key: String): String? {
        val parts = key.split('|')
        val family = parts.getOrNull(1) ?: return null
        val kind = parts.getOrNull(2).orEmpty()
        val subject = parts.drop(3).joinToString("|")
        return when (family) {
            "SCREEN_TIME" ->
                if (kind == "LESS_USE") {
                    "A lighter day than usual. Nothing to do: just notice what was different, because that's the easiest thing to repeat."
                } else {
                    "A heavier day than usual. Days like this often come from tiredness, boredom or stress: the phone fills the gaps. It's a signal, not a failure."
                }
            "APPS" ->
                when (kind) {
                    "APP_DROP" -> "You hardly opened $subject today. When an app loses its pull, something else has usually filled that time."
                    "APP_NEW" -> "$subject is new in your day. New apps grab attention while they're fresh; worth checking whether it's earning its place."
                    else -> "$subject took much more of your day than usual. Worth asking: did you choose that, or did it just happen?"
                }
            "UNLOCKS" ->
                if (kind == "FEWER_UNLOCKS") {
                    "You picked up the phone much less than usual. Fewer pickups usually means longer stretches of attention."
                } else {
                    "You picked up the phone much more than usual. Lots of pickups usually means the day was broken into small pieces."
                }
            "LATE_NIGHT" -> "You were on your phone late, more than usual. Late nights before a normal morning come straight out of your sleep."
            "SLEEP" ->
                when (kind) {
                    "SHORT_SLEEP" -> "A short night. Expect today to feel a bit harder, and quick, easy rewards (like scrolling) to be more tempting."
                    "LONG_SLEEP" -> "A long night. Your body may have been catching up."
                    "LATE_BEDTIME" -> "You went to bed later than usual. A shifting bedtime works like a small jet lag."
                    else -> "Your sleep was different from usual last night."
                }
            "RECORDS" -> "A personal best. Notice what made it possible: that's the part worth repeating."
            "ROUTINES" -> "One of your usual routines changed. Routines run on autopilot, so a change is a good moment to choose which ones to keep."
            "WEEK" -> null
            else -> null
        }
    }
}
