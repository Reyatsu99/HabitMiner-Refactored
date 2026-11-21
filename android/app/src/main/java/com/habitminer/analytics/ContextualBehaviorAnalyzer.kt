package com.habitminer.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

enum class ContextDimension(val label: String) {
    LIGHT("light"), PHONE_MOTION("phone movement"), PHONE_ROTATION("phone rotation"),
    CHARGING("charging"), HEADPHONES("headphone connection"), VEHICLE("vehicle activity"),
    POWER_SAVER("battery saver"), THERMAL("thermal state"),
}

data class ContextRelationship(
    val appName: String,
    val dimension: ContextDimension,
    val statePhrase: String,
    val sessionsInContext: Int,
    val sessionsOutsideContext: Int,
    val daysWithUseInContext: Int,
    val daysWithUseOutsideContext: Int,
    val contextExposureHours: Double,
    val comparisonExposureHours: Double,
    val useMinutesPerExposureHour: Double,
    val comparisonUseMinutesPerExposureHour: Double,
    val rateRatio: Double,
    val matchedStrata: Int,
) {
    val headline: String
        get() {
            val factor = if (rateRatio >= 1.0) rateRatio else 1.0 / rateRatio
            val direction = if (rateRatio >= 1.0) "higher" else "lower"
            return appName + " use rate is " + oneDecimal(factor) + "× " + direction + " when " + statePhrase
        }

    val detail: String
        get() =
            sessionsInContext.toString() + " sessions across " + daysWithUseInContext +
                " days; " + oneDecimal(useMinutesPerExposureHour) +
                " min of app use per sampled screen-on exposure hour versus " +
                oneDecimal(comparisonUseMinutesPerExposureHour) + " min in other " +
                dimension.label + " states. Observed exposure: " +
                oneDecimal(contextExposureHours) + " h versus " +
                oneDecimal(comparisonExposureHours) +
                " h. Rates are standardized across matching weekday/weekend and three-hour time windows. " +
                "Association, not causation."

    private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
}

/**
 * Uses one shared set of bounded observation intervals for both exposure and app-use attribution.
 * App usage is credited only where it overlaps an interval that contributes denominator exposure.
 *
 * Headphone and vehicle dimensions are intentionally omitted until the event stream can distinguish
 * known false states from unknown periods. Missing positive events must not imply disconnected or
 * non-vehicle states.
 */
object ContextualBehaviorAnalyzer {
    private const val MAX_OBSERVATION_GAP_MS = 30 * TimeUtil.MINUTE
    private const val MAX_EXPOSURE_HOLD_MS = 15 * TimeUtil.MINUTE
    private const val SESSION_SEGMENT_MS = 5 * TimeUtil.MINUTE
    private const val MIN_STRATUM_EXPOSURE_MS = 5 * TimeUtil.MINUTE
    private const val MIN_GROUP_EXPOSURE_MS = 30 * TimeUtil.MINUTE
    private const val MIN_GROUP_DAYS = 5
    private const val MIN_USE_DAYS = 3
    private const val MIN_SESSIONS_PER_GROUP = 3
    private const val MIN_TOTAL_SESSIONS = 10
    private const val MIN_USE_PER_GROUP_MS = 5 * TimeUtil.MINUTE
    private const val MIN_GYRO_SAMPLES = 30
    private const val MIN_MATCHED_STRATA = 2

    private data class Stratum(val weekend: Boolean, val threeHourBand: Int)
    private data class State(val dimension: ContextDimension, val phrase: String)
    private data class ExposureKey(val dimension: ContextDimension, val phrase: String, val stratum: Stratum)
    private data class UseKey(val appName: String, val dimension: ContextDimension, val phrase: String, val stratum: Stratum)
    private data class GroupKey(val appName: String, val dimension: ContextDimension, val phrase: String)
    private data class Observation(
        val start: Long,
        val end: Long,
        val stratum: Stratum,
        val day: LocalDate,
        val states: List<State>,
    )

    private class ExposureAccumulator {
        var durationMs: Long = 0L
        val days = mutableSetOf<LocalDate>()
    }

    private class UseAccumulator {
        var durationMs: Long = 0L
        val sessionIds = mutableSetOf<Int>()
        val days = mutableSetOf<LocalDate>()
    }

    @Suppress("UNUSED_PARAMETER")
    fun compute(
        sessions: List<UsageSession>,
        samples: List<ContextSample>,
        zone: ZoneId,
        situations: List<ContextSpan> = emptyList(),
    ): List<ContextRelationship> {
        if (sessions.isEmpty() || samples.size < 2) return emptyList()
        val orderedSamples = samples.distinctBy { it.timestamp }.sortedBy { it.timestamp }
        if (orderedSamples.size < 2) return emptyList()

        val gyroValues = orderedSamples.mapNotNull { sample ->
            sample.gyroVariance?.takeIf { it.isFinite() && it >= 0f }
        }
        val gyroMedian = median(gyroValues)?.takeIf {
            gyroValues.size >= MIN_GYRO_SAMPLES && gyroValues.distinct().size >= 2
        }

        // Both numerator and denominator are computed from these exact, non-overlapping windows.
        val observations = buildObservations(orderedSamples, zone, gyroMedian)
        if (observations.isEmpty()) return emptyList()

        val exposure = HashMap<ExposureKey, ExposureAccumulator>()
        observations.forEach { observation ->
            observation.states.forEach { state ->
                val key = ExposureKey(state.dimension, state.phrase, observation.stratum)
                val accumulator = exposure.getOrPut(key) { ExposureAccumulator() }
                accumulator.durationMs += observation.end - observation.start
                accumulator.days += observation.day
            }
        }

        val use = HashMap<UseKey, UseAccumulator>()
        sessions.withIndex().sortedBy { it.value.start }.forEach { indexed ->
            val sessionId = indexed.index
            val session = indexed.value
            val wallSpan = session.end - session.start
            val actualDuration = session.durationMs.coerceAtLeast(0L).coerceAtMost(wallSpan)
            if (wallSpan <= 0L || actualDuration <= 0L) return@forEach

            // Binary-search the first observation whose end is after this session starts.
            var low = 0
            var high = observations.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (observations[middle].end <= session.start) low = middle + 1 else high = middle
            }

            var index = low
            while (index < observations.size && observations[index].start < session.end) {
                val observation = observations[index]
                val overlapMs = (minOf(session.end, observation.end) -
                    maxOf(session.start, observation.start)).coerceAtLeast(0L)
                if (overlapMs > 0L) {
                    val useMs = if (overlapMs == wallSpan) actualDuration
                    else actualDuration * overlapMs / wallSpan
                    if (useMs > 0L) {
                        observation.states.forEach { state ->
                            val key = UseKey(session.appName, state.dimension, state.phrase, observation.stratum)
                            val accumulator = use.getOrPut(key) { UseAccumulator() }
                            accumulator.durationMs += useMs
                            accumulator.sessionIds += sessionId
                            accumulator.days += observation.day
                        }
                    }
                }
                index++
            }
        }
        return buildRelationships(exposure, use)
    }

    private fun buildObservations(
        samples: List<ContextSample>,
        zone: ZoneId,
        gyroMedian: Float?,
    ): List<Observation> {
        val observations = mutableListOf<Observation>()
        for (i in 0 until samples.lastIndex) {
            val sample = samples[i]
            val gap = samples[i + 1].timestamp - sample.timestamp
            if (!sample.isScreenOn || gap <= 0L || gap > MAX_OBSERVATION_GAP_MS) continue
            val windowEnd = sample.timestamp + minOf(gap, MAX_EXPOSURE_HOLD_MS)
            var cursor = sample.timestamp
            while (cursor < windowEnd) {
                val segmentEnd = minOf(windowEnd, cursor + SESSION_SEGMENT_MS, nextStratumBoundary(cursor, zone))
                if (segmentEnd <= cursor) break
                val midpoint = cursor + (segmentEnd - cursor) / 2L
                observations += Observation(
                    start = cursor,
                    end = segmentEnd,
                    stratum = stratumAt(midpoint, zone),
                    day = TimeUtil.dateOf(midpoint, zone),
                    states = statesFor(sample, gyroMedian),
                )
                cursor = segmentEnd
            }
        }
        return observations
    }

    private fun statesFor(sample: ContextSample, gyroMedian: Float?): List<State> {
        val out = mutableListOf<State>()
        when (ContextLabels.light(sample.lightLux)) {
            Light.DARK -> out += State(ContextDimension.LIGHT, "ambient light is dark")
            Light.DIM -> out += State(ContextDimension.LIGHT, "ambient light is dim")
            Light.BRIGHT -> out += State(ContextDimension.LIGHT, "ambient light is bright")
            null -> Unit
        }
        when (ContextLabels.motion(sample)) {
            Motion.STILL -> out += State(ContextDimension.PHONE_MOTION, "the phone is still")
            Motion.MOVING -> out += State(ContextDimension.PHONE_MOTION, "the phone is moving")
            Motion.ACTIVE -> out += State(ContextDimension.PHONE_MOTION, "the phone is highly active")
            null -> Unit
        }
        if (gyroMedian != null) {
            val variance = sample.gyroVariance
            if (variance != null && variance.isFinite() && variance >= 0f) {
                val phrase = if (variance >= gyroMedian) {
                    "the phone is rotating more than your usual level"
                } else {
                    "the phone is rotating less than your usual level"
                }
                out += State(ContextDimension.PHONE_ROTATION, phrase)
            }
        }
        // This boolean is collected in each snapshot, so both charging states are directly observed.
        out += State(
            ContextDimension.CHARGING,
            if (sample.isCharging) "the phone is charging" else "the phone is unplugged",
        )
        sample.powerSave?.let { enabled ->
            out += State(ContextDimension.POWER_SAVER, if (enabled) "battery saver is on" else "battery saver is off")
        }
        sample.thermalStatus?.takeIf { it in 0..6 }?.let { status ->
            out += State(
                ContextDimension.THERMAL,
                if (status >= 3) "device thermal status is elevated" else "device thermal status is normal",
            )
        }
        return out
    }

    private fun buildRelationships(
        exposure: Map<ExposureKey, ExposureAccumulator>,
        use: Map<UseKey, UseAccumulator>,
    ): List<ContextRelationship> {
        val groups = use.keys.map { GroupKey(it.appName, it.dimension, it.phrase) }.distinct()
        val output = mutableListOf<ContextRelationship>()

        for (group in groups) {
            val strata = exposure.keys.asSequence()
                .filter { it.dimension == group.dimension && it.phrase == group.phrase }
                .map { it.stratum }
                .distinct()
                .toList()

            var stateExposureMs = 0L
            var otherExposureMs = 0L
            var stateUseMs = 0L
            var otherUseMs = 0L
            var commonWeightMs = 0L
            var weightedStateRate = 0.0
            var weightedOtherRate = 0.0
            var matchedStrata = 0
            val stateExposureDays = mutableSetOf<LocalDate>()
            val otherExposureDays = mutableSetOf<LocalDate>()
            val stateUseDays = mutableSetOf<LocalDate>()
            val otherUseDays = mutableSetOf<LocalDate>()
            val stateSessionIds = mutableSetOf<Int>()
            val otherSessionIds = mutableSetOf<Int>()

            for (stratum in strata) {
                val stateExposure = exposure[ExposureKey(group.dimension, group.phrase, stratum)] ?: continue
                val otherEntries = exposure.filterKeys {
                    it.dimension == group.dimension && it.stratum == stratum && it.phrase != group.phrase
                }
                val otherStratumExposureMs = otherEntries.values.sumOf { it.durationMs }
                if (stateExposure.durationMs < MIN_STRATUM_EXPOSURE_MS ||
                    otherStratumExposureMs < MIN_STRATUM_EXPOSURE_MS) continue

                val stateUse = use[UseKey(group.appName, group.dimension, group.phrase, stratum)]
                val otherUseEntries = otherEntries.keys.mapNotNull { key ->
                    use[UseKey(group.appName, group.dimension, key.phrase, stratum)]
                }
                val stratumStateUseMs = stateUse?.durationMs ?: 0L
                val stratumOtherUseMs = otherUseEntries.sumOf { it.durationMs }
                val commonWeight = minOf(stateExposure.durationMs, otherStratumExposureMs)
                if (commonWeight <= 0L) continue

                matchedStrata++
                stateExposureMs += stateExposure.durationMs
                otherExposureMs += otherStratumExposureMs
                commonWeightMs += commonWeight
                // Identical stratum weights on both rates reduce exposure-mix confounding.
                weightedStateRate += (stratumStateUseMs.toDouble() / stateExposure.durationMs) * commonWeight
                weightedOtherRate += (stratumOtherUseMs.toDouble() / otherStratumExposureMs) * commonWeight
                stateExposureDays += stateExposure.days
                otherEntries.values.forEach { otherExposureDays += it.days }

                if (stateUse != null) {
                    stateUseMs += stateUse.durationMs
                    stateSessionIds += stateUse.sessionIds
                    stateUseDays += stateUse.days
                }
                otherUseEntries.forEach { otherUse ->
                    otherUseMs += otherUse.durationMs
                    otherSessionIds += otherUse.sessionIds
                    otherUseDays += otherUse.days
                }
            }

            if (
                matchedStrata < MIN_MATCHED_STRATA ||
                stateExposureMs < MIN_GROUP_EXPOSURE_MS ||
                otherExposureMs < MIN_GROUP_EXPOSURE_MS ||
                stateExposureDays.size < MIN_GROUP_DAYS ||
                otherExposureDays.size < MIN_GROUP_DAYS ||
                stateUseDays.size < MIN_USE_DAYS ||
                otherUseDays.size < MIN_USE_DAYS ||
                stateSessionIds.size < MIN_SESSIONS_PER_GROUP ||
                otherSessionIds.size < MIN_SESSIONS_PER_GROUP ||
                stateSessionIds.union(otherSessionIds).size < MIN_TOTAL_SESSIONS ||
                stateUseMs < MIN_USE_PER_GROUP_MS ||
                otherUseMs < MIN_USE_PER_GROUP_MS ||
                commonWeightMs <= 0L
            ) continue

            val stateRate = weightedStateRate / commonWeightMs
            val otherRate = weightedOtherRate / commonWeightMs
            if (stateRate <= 0.0 || otherRate <= 0.0) continue
            val ratio = stateRate / otherRate
            if (!ratio.isFinite() || ratio in 0.80..1.25) continue

            output += ContextRelationship(
                appName = group.appName,
                dimension = group.dimension,
                statePhrase = group.phrase,
                sessionsInContext = stateSessionIds.size,
                sessionsOutsideContext = otherSessionIds.size,
                daysWithUseInContext = stateUseDays.size,
                daysWithUseOutsideContext = otherUseDays.size,
                contextExposureHours = stateExposureMs.toDouble() / TimeUtil.HOUR,
                comparisonExposureHours = otherExposureMs.toDouble() / TimeUtil.HOUR,
                useMinutesPerExposureHour = stateRate * TimeUtil.HOUR / TimeUtil.MINUTE,
                comparisonUseMinutesPerExposureHour = otherRate * TimeUtil.HOUR / TimeUtil.MINUTE,
                rateRatio = ratio,
                matchedStrata = matchedStrata,
            )
        }
        return output.sortedWith(
            compareByDescending<ContextRelationship> { abs(ln(it.rateRatio)) }
                .thenByDescending { it.sessionsInContext + it.sessionsOutsideContext },
        ).take(8)
    }

    private fun stratumAt(time: Long, zone: ZoneId): Stratum {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        return Stratum(TimeUtil.isWeekend(local.toLocalDate()), local.hour / 3)
    }

    private fun nextStratumBoundary(time: Long, zone: ZoneId): Long {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        val nextHour = (local.hour / 3 + 1) * 3
        val boundary = if (nextHour >= 24) {
            local.toLocalDate().plusDays(1).atStartOfDay(zone)
        } else {
            local.toLocalDate().atTime(nextHour, 0).atZone(zone)
        }
        return boundary.toInstant().toEpochMilli()
    }

    private fun median(values: List<Float>): Float? {
        val sorted = values.filter { it.isFinite() && it >= 0f }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2f else sorted[middle]
    }
}
