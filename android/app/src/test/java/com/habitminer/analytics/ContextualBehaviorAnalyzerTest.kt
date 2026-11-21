package com.habitminer.analytics

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ContextualBehaviorAnalyzerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val minute = TimeUtil.MINUTE

    @Test
    fun `finds exposure adjusted light relationships across repeated days`() {
        val sessions = mutableListOf<UsageSession>()
        val samples = mutableListOf<ContextSample>()
        val firstDay = LocalDate.of(2026, 10, 1)

        repeat(6) { dayOffset ->
            val date = firstDay.plusDays(dayOffset.toLong())
            val observations =
                (0..8).map { index ->
                    val totalMinutes = 18 * 60 + index * 20
                    val time = TimeUtil.at(date, totalMinutes / 60, totalMinutes % 60, zone)
                    ContextSample(
                        timestamp = time,
                        lightLux = if (index % 2 == 0) 2f else 500f,
                        motionVariance = 0.05f,
                        isCharging = false,
                        isScreenOn = true,
                        proximityNear = null,
                        batteryLevel = 60,
                    )
                }
            samples += observations

            // Four five-minute app sessions in dark conditions, versus one in bright
            // conditions, at the same weekday/weekend and three-hour time band.
            listOf(0, 2, 4, 6, 1).forEach { index ->
                val start = observations[index].timestamp + 5 * minute
                sessions +=
                    UsageSession(
                        packageName = "com.example.video",
                        appName = "Video",
                        category = AppCategory.VIDEO,
                        start = start,
                        end = start + 5 * minute,
                        durationMs = 5 * minute,
                    )
            }
        }

        val relationships = ContextualBehaviorAnalyzer.compute(sessions, samples, zone)
        val dark = relationships.single { it.dimension == ContextDimension.LIGHT && it.statePhrase == "ambient light is dark" }

        assertTrue(dark.rateRatio > 2.5)
        assertEquals(6, dark.daysWithUseInContext)
        assertEquals(6, dark.daysWithUseOutsideContext)
        assertTrue(dark.contextExposureHours >= 5.5)
        assertTrue(dark.comparisonExposureHours >= 5.5)
        assertTrue(dark.detail.contains("Association, not causation"))
    }

    @Test
    fun `does not invent exposure across overnight gaps`() {
        val first = LocalDate.of(2026, 10, 1)
        val samples =
            (0..5).map { offset ->
                val time = TimeUtil.at(first.plusDays(offset.toLong()), 20, 0, zone)
                ContextSample(
                    timestamp = time,
                    lightLux = 1f,
                    motionVariance = 0.05f,
                    isCharging = false,
                    isScreenOn = true,
                    proximityNear = null,
                    batteryLevel = 60,
                )
            }
        val sessions =
            samples.map { sample ->
                UsageSession(
                    packageName = "com.example.video",
                    appName = "Video",
                    category = AppCategory.VIDEO,
                    start = sample.timestamp + minute,
                    end = sample.timestamp + 6 * minute,
                    durationMs = 5 * minute,
                )
            }

        assertTrue(ContextualBehaviorAnalyzer.compute(sessions, samples, zone).isEmpty())
    }

    @Test
    fun `does not infer sensor context while the screen is off`() {
        val first = LocalDate.of(2026, 10, 1)
        val samples =
            (0..10).map { offset ->
                ContextSample(
                    timestamp = TimeUtil.at(first, 18, offset * 20, zone),
                    lightLux = 1f,
                    motionVariance = 0.05f,
                    isCharging = false,
                    isScreenOn = false,
                    proximityNear = null,
                    batteryLevel = 60,
                )
            }
        val sessions =
            (0..9).map { offset ->
                val start = samples[offset].timestamp + minute
                UsageSession(
                    packageName = "com.example.video",
                    appName = "Video",
                    category = AppCategory.VIDEO,
                    start = start,
                    end = start + 5 * minute,
                    durationMs = 5 * minute,
                )
            }

        assertTrue(ContextualBehaviorAnalyzer.compute(sessions, samples, zone).isEmpty())
    }
}
