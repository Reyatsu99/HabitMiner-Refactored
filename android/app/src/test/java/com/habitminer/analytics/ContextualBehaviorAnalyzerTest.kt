package com.habitminer.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ContextualBehaviorAnalyzerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val minute = TimeUtil.MINUTE

    @Test
    fun detectsExposureAdjustedLightRelationshipsAndIgnoresUnobservedGapUsage() {
        val sessions = mutableListOf<UsageSession>()
        val samples = mutableListOf<ContextSample>()
        val firstDay = LocalDate.of(2026, 10, 1)

        repeat(6) { dayOffset ->
            val date = firstDay.plusDays(dayOffset.toLong())
            val observations = (0..8).map { index ->
                val totalMinutes = 18 * 60 + index * 20
                ContextSample(
                    timestamp = TimeUtil.at(date, totalMinutes / 60, totalMinutes % 60, zone),
                    lightLux = if (index % 2 == 0) 2f else 500f,
                    motionVariance = 0.05f,
                    isCharging = false,
                    isScreenOn = true,
                    proximityNear = null,
                    batteryLevel = 60,
                )
            }
            samples += observations
            listOf(0, 2, 4, 6, 1).forEach { index ->
                val start = observations[index].timestamp + 5 * minute
                sessions += UsageSession("com.example.video", "Video", AppCategory.VIDEO, start, start + 5 * minute, 5 * minute)
            }

            // This session is in the five-minute gap before the next bright sample.
            val gapStart = observations[0].timestamp + 16 * minute
            sessions += UsageSession(
                "com.example.video", "Video", AppCategory.VIDEO,
                gapStart, gapStart + 3 * minute, 3 * minute,
            )
        }

        val relationships = ContextualBehaviorAnalyzer.compute(sessions, samples, zone)
        val dark = relationships.single {
            it.dimension == ContextDimension.LIGHT && it.statePhrase == "ambient light is dark"
        }
        assertTrue(dark.rateRatio > 2.5)
        assertEquals(6, dark.daysWithUseInContext)
        assertEquals(6, dark.daysWithUseOutsideContext)
        assertTrue(dark.contextExposureHours >= 5.5)
        assertTrue(dark.comparisonExposureHours >= 5.5)
        assertTrue(dark.detail.contains("Association, not causation"))
    }

    @Test
    fun doesNotInventExposureAcrossOvernightGaps() {
        val first = LocalDate.of(2026, 10, 1)
        val samples = (0..5).map { offset ->
            val time = TimeUtil.at(first.plusDays(offset.toLong()), 20, 0, zone)
            ContextSample(time, 1f, 0.05f, false, true, null, 60)
        }
        val sessions = samples.map { sample ->
            UsageSession(
                "com.example.video", "Video", AppCategory.VIDEO,
                sample.timestamp + minute, sample.timestamp + 6 * minute, 5 * minute,
            )
        }
        assertTrue(ContextualBehaviorAnalyzer.compute(sessions, samples, zone).isEmpty())
    }

    @Test
    fun doesNotInferSensorContextWhileScreenIsOff() {
        val first = LocalDate.of(2026, 10, 1)
        val samples = (0..10).map { offset ->
            val totalMinutes = 18 * 60 + offset * 20
            ContextSample(
                TimeUtil.at(first, totalMinutes / 60, totalMinutes % 60, zone),
                1f, 0.05f, false, false, null, 60,
            )
        }
        val sessions = (0..9).map { offset ->
            val start = samples[offset].timestamp + minute
            UsageSession(
                "com.example.video", "Video", AppCategory.VIDEO,
                start, start + 5 * minute, 5 * minute,
            )
        }
        assertTrue(ContextualBehaviorAnalyzer.compute(sessions, samples, zone).isEmpty())
    }

    @Test
    fun doesNotTreatMissingHeadphoneOrVehicleEventsAsKnownNegativeStates() {
        val first = LocalDate.of(2026, 10, 1)
        val samples = (0..10).map { offset ->
            val totalMinutes = 18 * 60 + offset * 20
            ContextSample(
                TimeUtil.at(first, totalMinutes / 60, totalMinutes % 60, zone),
                20f, 0.05f, false, true, null, 60,
            )
        }
        val sessions = samples.dropLast(1).map { sample ->
            UsageSession(
                "com.example.video", "Video", AppCategory.VIDEO,
                sample.timestamp + minute, sample.timestamp + 6 * minute, 5 * minute,
            )
        }
        val relationships = ContextualBehaviorAnalyzer.compute(sessions, samples, zone)
        assertTrue(relationships.none {
            it.dimension == ContextDimension.HEADPHONES || it.dimension == ContextDimension.VEHICLE
        })
    }
    @Test
    fun doesNotSpreadMergedSessionDurationAcrossUnobservedForegroundGaps() {
        val sessions = mutableListOf<UsageSession>()
        val samples = mutableListOf<ContextSample>()
        val firstDay = LocalDate.of(2026, 10, 1)

        repeat(6) { dayOffset ->
            val date = firstDay.plusDays(dayOffset.toLong())
            val observations = (0..8).map { index ->
                val totalMinutes = 18 * 60 + index * 20
                ContextSample(
                    timestamp = TimeUtil.at(date, totalMinutes / 60, totalMinutes % 60, zone),
                    lightLux = if (index % 2 == 0) 2f else 500f,
                    motionVariance = 0.05f,
                    isCharging = false,
                    isScreenOn = true,
                    proximityNear = null,
                    batteryLevel = 60,
                )
            }
            samples += observations

            // Models a merged row: five minutes of wall time but only three minutes
            // of foreground use, with a two-minute gap whose exact location is unknown.
            listOf(0, 2, 4, 6, 1).forEach { index ->
                val start = observations[index].timestamp + 5 * minute
                sessions += UsageSession(
                    "com.example.video", "Video", AppCategory.VIDEO,
                    start, start + 5 * minute, 3 * minute,
                )
            }
        }

        // The previous proportional allocator fabricated use inside those unknown gaps.
        assertTrue(ContextualBehaviorAnalyzer.compute(sessions, samples, zone).isEmpty())
    }

}
