package com.habitminer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialPatternMinerTest {
    private val dayMs = 24 * 60 * 60 * 1000L
    private val minuteMs = 60 * 1000L

    private fun day(
        index: Int,
        vararg apps: Pair<String, Int>,
    ): DailySequence {
        val start = index * dayMs
        val events = apps.map { (app, minute) ->
            SequenceEvent(
                packageName = "pkg.$app",
                appName = app,
                startTime = start + minute * minuteMs,
                endTime = start + (minute + 1) * minuteMs,
            )
        }
        return DailySequence("day-$index", events)
    }

    @Test
    fun findsRecurringSubsequencesWithAnInterveningApp() {
        val days = (0 until 6).map { day(it, "A" to 0, "X" to 3, "B" to 6) }

        val patterns = SequentialPatternMiner.mine(days)

        assertTrue(patterns.any { it.appSequence == listOf("A", "B") && it.occurrences == 6 })
        assertTrue(patterns.any { it.appSequence == listOf("A", "X", "B") && it.occurrences == 6 })
    }

    @Test
    fun countsSupportByDistinctDayNotRepeatedOccurrences() {
        val days = (0 until 4).map { index ->
            day(index, "A" to 0, "B" to 2, "A" to 20, "B" to 22, "A" to 40, "B" to 42)
        } + day(4, "C" to 0)

        assertTrue(SequentialPatternMiner.mine(days).isEmpty())
    }

    @Test
    fun rejectsTransitionsBeyondTheMaximumGap() {
        val days = (0 until 6).map { day(it, "A" to 0, "B" to 20) }

        assertTrue(SequentialPatternMiner.mine(days).isEmpty())
    }

    @Test
    fun collapsesAdjacentSessionsOfTheSameApp() {
        val days = (0 until 5).map { index ->
            val start = index * dayMs
            DailySequence(
                "day-$index",
                listOf(
                    SequenceEvent("pkg.A", "A", start, start + minuteMs),
                    SequenceEvent("pkg.A", "A", start + minuteMs, start + 2 * minuteMs),
                    SequenceEvent("pkg.B", "B", start + 3 * minuteMs, start + 4 * minuteMs),
                ),
            )
        }

        val patterns = SequentialPatternMiner.mine(days)

        assertEquals(listOf("A", "B"), patterns.single().appSequence)
        assertEquals(5, patterns.single().occurrences)
    }
    @Test
    fun doesNotBridgeLongIdleGapBetweenSameAppVisits() {
        val days = (0 until 6).map { index ->
            val start = index * dayMs
            DailySequence(
                "day-$index",
                listOf(
                    SequenceEvent("pkg.X", "X", start, start + minuteMs),
                    SequenceEvent("pkg.A", "A", start + minuteMs, start + 2 * minuteMs),
                    SequenceEvent("pkg.A", "A", start + 120 * minuteMs, start + 121 * minuteMs),
                    SequenceEvent("pkg.B", "B", start + 122 * minuteMs, start + 123 * minuteMs),
                ),
            )
        }

        val patterns = SequentialPatternMiner.mine(days)

        assertTrue(patterns.any { it.appSequence == listOf("A", "B") })
        assertTrue(patterns.none { it.appSequence == listOf("X", "A", "B") })
    }

    @Test
    fun countsDuplicateDayKeysOnlyOnceForSupport() {
        val repeated = (0 until 5).map { day(0, "A" to 0, "B" to 2) }
        val otherDays = (1 until 4).map { day(it, "C" to 0) }

        assertTrue(SequentialPatternMiner.mine(repeated + otherDays).isEmpty())
    }

    @Test
    fun ignoresOverlappingEventsWhenBuildingTransitions() {
        val days = (0 until 6).map { index ->
            val start = index * dayMs
            DailySequence(
                "day-$index",
                listOf(
                    SequenceEvent("pkg.A", "A", start, start + 5 * minuteMs),
                    SequenceEvent("pkg.B", "B", start + 4 * minuteMs, start + 6 * minuteMs),
                    SequenceEvent("pkg.C", "C", start + 7 * minuteMs, start + 8 * minuteMs),
                ),
            )
        }

        val patterns = SequentialPatternMiner.mine(days)

        assertTrue(patterns.none { it.appSequence == listOf("A", "B") })
        assertTrue(patterns.any { it.appSequence == listOf("B", "C") })
    }

}
