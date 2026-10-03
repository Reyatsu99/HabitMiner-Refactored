package com.habitminer.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
private const val MIN = 60_000L

/** 2026-10-03 is a Saturday. */
private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, 1).plusDays((day - 1).toLong())

private fun at(
    date: LocalDate,
    h: Int,
    m: Int = 0,
): Long = TimeUtil.at(date, h, m, ZONE)

private fun s(
    app: String,
    date: LocalDate,
    h: Int,
    m: Int,
    minutes: Long,
    cat: AppCategory = AppCategory.SOCIAL,
): UsageSession {
    val start = at(date, h, m)
    return UsageSession("pkg.${app.lowercase()}", app, cat, start, start + minutes * MIN, minutes * MIN)
}

private fun sample(
    time: Long,
    lux: Float? = null,
    motion: Float? = null,
    charging: Boolean = false,
    place: String? = null,
) = ContextSample(time, lux, motion, charging, isScreenOn = lux != null, proximityNear = null, batteryLevel = 50, place = place)

class CategoryMapperTest {
    @Test
    fun `maps common apps to friendly categories`() {
        assertEquals(AppCategory.VIDEO, CategoryMapper.categorize("com.drifty.app", "YT (drifty)", "OTHER"))
        assertEquals(AppCategory.GAMES, CategoryMapper.categorize("com.topgames.evony", "Evony", "GAMING"))
        assertEquals(AppCategory.MESSAGING, CategoryMapper.categorize("org.telegram.messenger", "Telegram"))
        assertEquals(AppCategory.BROWSING, CategoryMapper.categorize("com.brave.browser", "Brave"))
        assertEquals(AppCategory.PRODUCTIVITY, CategoryMapper.categorize("com.anthropic.claude", "Claude"))
        assertEquals(AppCategory.MUSIC, CategoryMapper.categorize("com.google.android.apps.youtube.music", "YouTube Music"))
        assertEquals(AppCategory.OTHER, CategoryMapper.categorize("com.example.thing", "Thing"))
    }

    @Test
    fun `falls back to the stored category`() {
        assertEquals(AppCategory.SHOPPING, CategoryMapper.categorize("com.example.store", "Store", "SHOPPING"))
    }

    @Test
    fun `recognises system noise`() {
        assertTrue(CategoryMapper.isSystemNoise("com.iqoo.secure", "Phone management"))
        assertTrue(CategoryMapper.isSystemNoise("com.vendor.x", "Phone management"))
        assertFalse(CategoryMapper.isSystemNoise("com.whatsapp", "WhatsApp"))
    }
}

class SessionGrouperTest {
    @Test
    fun `splits on gaps longer than five minutes and totals apps`() {
        val list =
            listOf(
                s("A", d(1), 10, 0, 10),
                s("B", d(1), 10, 12, 5),
                s("A", d(1), 10, 18, 2),
                s("C", d(1), 11, 0, 3),
            )
        val groups = SessionGrouper.group(list)
        assertEquals(2, groups.size)
        assertEquals(listOf("A" to 12 * MIN, "B" to 5 * MIN), groups[0].appTotals)
        assertEquals(17 * MIN, groups[0].totalMs)
    }
}

class SleepDetectorTest {
    private val wake = d(3)

    @Test
    fun `finds the overnight gap and uses context for confidence`() {
        val sessions =
            listOf(
                s("Evony", d(2), 22, 0, 60),
                s("Evony", d(2), 23, 30, 120), // until 01:30
                s("Snapchat", wake, 9, 0, 5),
                s("WhatsApp", wake, 10, 0, 5),
            )
        val samples = listOf(sample(at(wake, 3, 0), charging = true), sample(at(wake, 5, 0), charging = true))
        val est = SleepDetector.detect(sessions, emptyList(), samples, wake, at(wake, 18), ZONE)!!

        assertEquals(at(wake, 1, 30), est.sleepStart)
        assertEquals(at(wake, 9, 0), est.wakeTime)
        assertEquals(Confidence.HIGH, est.confidence)
        assertEquals("Evony", est.lastAppBeforeSleep)
        assertEquals("Snapchat", est.firstAppAfterWake)
        assertEquals(60 * MIN, est.preSleepUseMs)
    }

    @Test
    fun `an unlock in the night splits the gap`() {
        val sessions = listOf(s("A", d(2), 23, 0, 30), s("B", wake, 9, 0, 5))
        val est = SleepDetector.detect(sessions, listOf(at(wake, 3, 0)), emptyList(), wake, at(wake, 18), ZONE)!!
        assertEquals(at(wake, 3, 0), est.sleepStart)
        assertEquals(at(wake, 9, 0), est.wakeTime)
    }

    @Test
    fun `no estimate before waking up`() {
        val sessions = listOf(s("A", d(2), 23, 0, 30))
        assertNull(SleepDetector.detect(sessions, emptyList(), emptyList(), wake, at(wake, 6), ZONE))
    }

    @Test
    fun `summary averages bedtimes across midnight`() {
        val a = SleepEstimate(wake, at(d(2), 23, 30), at(wake, 7), Confidence.HIGH, 0, null, null, null)
        val b = SleepEstimate(d(4), at(wake, 23, 59).plus(MIN).plus(30 * MIN), at(d(4), 8), Confidence.HIGH, 0, null, null, null)
        val sum = SleepDetector.summarize(listOf(a, b), ZONE)!!
        assertEquals(0, sum.avgBedtimeMinutes)
    }
}

class PickupAnalyzerTest {
    @Test
    fun `classifies pickups after notifications`() {
        val day = d(1)
        val unlocks = listOf(at(day, 10, 0), at(day, 11, 0), at(day, 12, 0))
        val notifs = listOf(TimedEvent(at(day, 9, 59), "pkg.whatsapp"), TimedEvent(at(day, 11, 30), "pkg.whatsapp"))
        val sessions = listOf(s("WhatsApp", day, 10, 0, 3), s("Evony", day, 11, 0, 20))
        val stats = PickupAnalyzer.analyze(unlocks, notifs, sessions, at(day, 0), at(day, 23)) { "WhatsApp" }

        assertEquals(3, stats.total)
        assertEquals(1, stats.afterNotification)
        assertEquals(1, stats.quickChecks) // 12:00 unlock with no app use
        assertEquals("WhatsApp", stats.topTriggers.first().appName)
        assertEquals(2, stats.selfInitiated)
    }
}

class ContextInsightsTest {
    @Test
    fun `reports dark use for an app`() {
        val day = d(1)
        val sessions = (0 until 4).map { s("Evony", day, 20 + it, 0, 30, AppCategory.GAMES) }
        val samples = (0 until 4).map { sample(at(day, 20 + it, 15), lux = 2f, motion = 0.1f) }
        val insights = ContextInsights.compute(sessions, samples, ZONE)
        assertTrue(insights.any { it.kind == InsightKind.DARK && it.headline.contains("Evony") })
    }

    @Test
    fun `no sensor data means no light insight`() {
        val day = d(1)
        val sessions = (0 until 4).map { s("Evony", day, 20 + it, 0, 30) }
        val insights = ContextInsights.compute(sessions, emptyList(), ZONE)
        assertFalse(insights.any { it.kind == InsightKind.DARK })
    }
}

class BlueprintTest {
    @Test
    fun `heatmap splits sessions across hours`() {
        val today = d(7)
        val map = Heatmap.build(listOf(s("A", today, 10, 30, 60)), today, ZONE)
        assertEquals(30, map.minutes.last()[10])
        assertEquals(30, map.minutes.last()[11])
        assertEquals("A", map.topApps.last()[10])
        assertEquals(10, map.busiestHour)
    }

    @Test
    fun `typical day uses comparable days and percentiles`() {
        // History starts on Oct 1 (skipped as partial). Weekend days Oct 3 and 4 each 60 min at 10:00.
        val sessions =
            listOf(s("A", d(1), 10, 0, 5), s("A", d(2), 10, 0, 30)) +
                listOf(d(3), d(4)).map { s("A", it, 10, 0, 60) } +
                listOf(d(10)).map { s("A", it, 8, 0, 15) } // Oct 10 is a Saturday (today)
        val today = d(10)
        val curve = TypicalDay.build(sessions, today, at(today, 12), ZONE)!!
        assertTrue(curve.selection.sameDayType)
        assertEquals(2, curve.selection.days.size)
        assertEquals(60, curve.median[24])
        assertEquals(15, curve.today.last().second)
    }

    @Test
    fun `percentile interpolates`() {
        assertEquals(15, TypicalDay.percentile(listOf(10, 20), 0.5))
        assertEquals(7, TypicalDay.percentile(listOf(7), 0.25))
    }

    @Test
    fun `clusters heavy nights and light mornings into two types`() {
        val sessions = mutableListOf(s("A", d(1), 9, 0, 5))
        for (i in 2..5) sessions.add(s("Game", d(i), 0, 0, 240))
        for (i in 6..9) sessions.add(s("Mail", d(i), 9, 0, 30))
        val types = DayTypeClusterer.cluster(sessions, d(10), ZONE)!!
        assertEquals(2, types.types.size)
        assertEquals("Busier days", types.types[0].name)
        assertTrue(types.types[0].description.contains("extra use 00–04"))
        assertEquals("Quieter days (weekdays)", types.types[1].name)
        assertEquals(types.dayToType[d(2)], types.dayToType[d(3)])
    }

    @Test
    fun `near-identical days are reported as one consistent type`() {
        val sessions = mutableListOf(s("A", d(1), 9, 0, 5))
        for (i in 2..9) sessions.add(s("Game", d(i), 0, 0, 120L + (i % 3) * 5L))
        val types = DayTypeClusterer.cluster(sessions, d(10), ZONE)!!
        assertEquals(1, types.types.size)
        assertEquals("Consistent days", types.types.single().name)
    }

    @Test
    fun `week comparison averages per day`() {
        val today = LocalDate.of(2026, 10, 20)
        val sessions = mutableListOf<UsageSession>()
        sessions.add(s("A", today.minusDays(15), 9, 0, 5)) // partial first day
        for (i in 8..14) sessions.add(s("A", today.minusDays(i.toLong()), 10, 0, 60))
        for (i in 1..7) sessions.add(s("A", today.minusDays(i.toLong()), 10, 0, 120))
        val cmp = WeekComparer.compare(sessions, today, ZONE)!!
        assertEquals(120 * MIN, cmp.currentPerDayMs)
        assertEquals(60 * MIN, cmp.previousPerDayMs)
        assertEquals("A", cmp.biggestMovers.first().name)
    }
}

class PredictabilityTest {
    @Test
    fun `a perfectly regular sequence beats the baselines`() {
        val apps = listOf("A", "B", "C")
        val sessions = mutableListOf<UsageSession>()
        for (day in 1..10) {
            var minute = 0
            repeat(30) { i ->
                sessions.add(s(apps[i % 3], d(day), 10 + minute / 60, minute % 60, 1))
                minute += 2
            }
        }
        val result = PredictabilityEvaluator.evaluate(sessions, at(d(10), 23), ZONE)!!
        assertEquals(1f, result.hitRate, 0.001f)
        assertTrue(result.hitRate > result.mostUsedBaseline)
        assertTrue(result.hitRate > result.randomBaseline)
    }

    @Test
    fun `too little data gives no score`() {
        assertNull(PredictabilityEvaluator.evaluate(listOf(s("A", d(1), 10, 0, 1)), at(d(1), 23), ZONE))
    }
}

class PatternGrouperTest {
    @Test
    fun `merges the same sequence across time slots`() {
        val rows =
            listOf(
                PatternRow("Snapchat → Telegram", "NIGHT", "WEEKDAY", 10, 1f, 5L),
                PatternRow("Snapchat → Telegram", "EVENING", "WEEKDAY", 8, 0.8f, 9L),
                PatternRow("WhatsApp → Snapchat", "EVENING", "WEEKDAY", 9, 0.9f, 3L),
            )
        val groups = PatternGrouper.group(rows)
        assertEquals(2, groups.size)
        val first = groups.first()
        assertEquals("Snapchat → Telegram", first.sequence)
        assertEquals("Weekday evenings & nights", first.whenText)
        assertEquals("Seen on 10 of the last 10 weekday nights", first.evidenceText)
        assertEquals(9L, first.lastSeen)
    }
}

class PromptPolicyTest {
    private val day = d(7) // Wednesday

    @Test
    fun `check-ins respect hours, windows and the daily cap`() {
        assertTrue(PromptPolicy.canSendCheckIn(true, true, emptyList(), at(day, 10), ZONE))
        assertFalse(PromptPolicy.canSendCheckIn(true, true, emptyList(), at(day, 23), ZONE))
        assertFalse(PromptPolicy.canSendCheckIn(true, false, emptyList(), at(day, 10), ZONE))
        assertFalse(PromptPolicy.canSendCheckIn(false, true, emptyList(), at(day, 10), ZONE))
        // Already one in this window.
        val sent = listOf(SentPrompt(PromptKind.CHECK_IN, at(day, 9, 5)))
        assertFalse(PromptPolicy.canSendCheckIn(true, true, sent, at(day, 12), ZONE))
        // Daily cap reached.
        val three = listOf(9, 13, 17).map { SentPrompt(PromptKind.NUDGE, at(day, it)) }
        assertFalse(PromptPolicy.canSendCheckIn(true, true, three, at(day, 20), ZONE))
    }

    @Test
    fun `late-night leisure stretch triggers a nudge`() {
        val sessions = listOf(s("Evony", day, 23, 0, 15, AppCategory.GAMES), s("Evony", day, 23, 16, 15, AppCategory.GAMES))
        val nudge = PromptPolicy.nudgeFor(true, sessions, sample(at(day, 23, 20), lux = 1f), emptyList(), at(day, 23, 31), ZONE)
        assertNotNull(nudge)
        assertEquals("late_night", nudge!!.key)
        assertTrue(nudge.body.contains("dark"))
    }

    @Test
    fun `work stretch does not trigger a nudge`() {
        val sessions = listOf(s("Docs", day, 23, 0, 40, AppCategory.PRODUCTIVITY))
        assertNull(PromptPolicy.nudgeFor(true, sessions, null, emptyList(), at(day, 23, 41), ZONE))
    }

    @Test
    fun `digest goes out once on Sunday evening`() {
        val sunday = d(4)
        assertTrue(PromptPolicy.shouldSendDigest(true, null, at(sunday, 19, 30), ZONE))
        assertFalse(PromptPolicy.shouldSendDigest(true, null, at(sunday, 18, 0), ZONE))
        assertFalse(PromptPolicy.shouldSendDigest(true, at(sunday, 19, 5), at(sunday, 20, 0), ZONE))
        assertTrue(PromptPolicy.shouldSendDigest(true, at(sunday, 19, 5), at(sunday.plusDays(7), 19, 30), ZONE))
    }
}

class PolicyAndFormatTest {
    @Test
    fun `sensing mode follows screen, motion and battery`() {
        assertEquals(SensingMode.ACTIVE, SensingPolicy.choose(true, true, false, 80))
        assertEquals(SensingMode.NORMAL, SensingPolicy.choose(true, false, false, 80))
        assertEquals(SensingMode.IDLE, SensingPolicy.choose(false, null, false, 80))
        assertEquals(SensingMode.NORMAL, SensingPolicy.choose(false, null, true, 80))
        assertEquals(SensingMode.LOW_BATTERY, SensingPolicy.choose(true, true, false, 10))
    }

    @Test
    fun `places get sensible names`() {
        val day = d(7)
        val samples =
            listOf(1, 2, 3).map { sample(at(day, it), place = "h1") } +
                listOf(10, 11, 12).map { sample(at(day, it), place = "c1") } +
                listOf(sample(at(day, 20), place = "x1"))
        val names = PlaceInference.suggestNames(samples, ZONE)
        assertEquals("Home", names["h1"])
        assertEquals("Campus", names["c1"])
        assertEquals("Place 1", names["x1"])
    }

    @Test
    fun `durations read naturally`() {
        assertEquals("2h 5m", Format.duration(125 * MIN))
        assertEquals("45m", Format.duration(45 * MIN))
        assertEquals("3h", Format.duration(180 * MIN))
        assertEquals("<1m", Format.duration(20_000))
        assertEquals("23:30", Format.clockFromMinutes(-30))
    }
}
