package com.habitminer.analytics

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/** Context dimensions that can be related to app-use rates. */
enum class ContextDimension(val label: String) {
    LIGHT("light"),
    PHONE_MOTION("phone movement"),
    PHONE_ROTATION("phone rotation"),
    CHARGING("charging"),
    HEADPHONES("headphone connection"),
    VEHICLE("vehicle activity"),
    POWER_SAVER("battery saver"),
    THERMAL("thermal state"),
}

/**
 * An exposure-adjusted association between an app and one observed context state.
 *
 * Rates use sampled, screen-on exposure rather than raw percentages of app time. The comparison
 * is restricted to matching weekday/weekend and three-hour time-of-day strata. This is descriptive
 * evidence, not a causal claim.
 */
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
                " h, matched by weekday/weekend and three-hour time window. Association, not causation."

    private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)
}

/**
 * Finds repeatable app/context relationships without treating sparse sensor readings as continuous
 * ground truth. Each sample contributes at most 15 minutes of screen-on exposure, and gaps over
 * 30 minutes are not forward-filled. App sessions are divided into five-minute slices and matched
 * to a nearby screen-on sample.
 *
 * Thresholds are conservative engineering gates; they should be re-evaluated against more users.
 */
object ContextualBehaviorAnalyzer {
    private const val MAX_OBSERVATION_GAP_MS = 30 * TimeUtil.MINUTE
    private const val MAX_EXPOSURE_HOLD_MS = 15 * TimeUtil.MINUTE
    private const val MAX_CONTEXT_DISTANCE_MS = 10 * TimeUtil.MINUTE
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

    private data class State(
        val dimension: ContextDimension,
        val phrase: String,
    )

    private data class ExposureKey(
        val dimension: ContextDimension,
        val phrase: String,
        val stratum: Stratum,
    )

    private data class UseKey(
        val appName: String,
        val dimension: ContextDimension,
        val phrase: String,
        val stratum: Stratum,
    )

    private data class GroupKey(
        val appName: String,
        val dimension: ContextDimension,
        val phrase: String,
    )

    private class ExposureAccumulator {
        var durationMs: Long = 0L
        val days = mutableSetOf<java.time.LocalDate>()
    }

    private class UseAccumulator {
        var durationMs: Long = 0L
        val sessionIds = mutableSetOf<Int>()
        val days = mutableSetOf<java.time.LocalDate>()
    }

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

        val spanIndex = SpanIndex(situations)
        val includeHeadphones = situations.any { it.tag == SpanIndex.HEADPHONES }
        val includeVehicle = situations.any { it.tag == SpanIndex.VEHICLE }
        val situationBoundaries =
            situations.flatMap { listOf(it.start, it.end) }.distinct().sorted()

        val exposure = HashMap<ExposureKey, ExposureAccumulator>()
        val use = HashMap<UseKey, UseAccumulator>()
        val sampleIndex = SampleIndex(orderedSamples)

        // Exposure is based on consecutive observations, not on app-use duration. Long gaps and
        // the final unpaired observation are deliberately excluded.
        for (i in 0 until orderedSamples.lastIndex) {
            val sample = orderedSamples[i]
            val next = orderedSamples[i + 1]
            val gap = next.timestamp - sample.timestamp
            if (!sample.isScreenOn || gap <= 0L || gap > MAX_OBSERVATION_GAP_MS) continue

            val end = sample.timestamp + minOf(gap, MAX_EXPOSURE_HOLD_MS)
            addExposureWindow(
                sample = sample,
                start = sample.timestamp,
                end = end,
                zone = zone,
                spanIndex = spanIndex,
                includeHeadphones = includeHeadphones,
                includeVehicle = includeVehicle,
                gyroMedian = gyroMedian,
                situationBoundaries = situationBoundaries,
                exposure = exposure,
            )
        }

        // Split long sessions so a context change during a session is not represented by just
        // the context at its midpoint. durationMs is proportionally apportioned over the wall span.
        sessions.forEachIndexed { sessionId, session ->
            val wallSpan = session.end - session.start
            val actualDuration = session.durationMs.coerceAtLeast(0L).coerceAtMost(wallSpan)
            if (wallSpan <= 0L || actualDuration <= 0L) return@forEachIndexed

            var cursor = session.start
            var credited = 0L
            while (cursor < session.end) {
                val segmentEnd = minOf(
                    session.end,
                    cursor + SESSION_SEGMENT_MS,
                    nextStratumBoundary(cursor, zone),
                )
                if (segmentEnd <= cursor) break

                val wallDuration = segmentEnd - cursor
                val useMs =
                    if (segmentEnd == session.end) {
                        actualDuration - credited
                    } else {
                        actualDuration * wallDuration / wallSpan
                    }
                credited += useMs
                if (useMs > 0L) {
                    val midpoint = cursor + wallDuration / 2L
                    val sample =
                        sampleIndex.nearest(midpoint, MAX_CONTEXT_DISTANCE_MS)
                            ?.takeIf { it.isScreenOn }
                    if (sample != null) {
                        val states = statesFor(
                            sample,
                            spanIndex.tagsAt(midpoint),
                            includeHeadphones,
                            includeVehicle,
                            gyroMedian,
                        )
                        val stratum = stratumAt(midpoint, zone)
                        val day = TimeUtil.dateOf(midpoint, zone)
                        states.forEach { state ->
                            val key = UseKey(session.appName, state.dimension, state.phrase, stratum)
                            val accumulator = use.getOrPut(key) { UseAccumulator() }
                            accumulator.durationMs += useMs
                            accumulator.sessionIds += sessionId
                            accumulator.days += day
                        }
                    }
                }
                cursor = segmentEnd
            }
        }

        return buildRelationships(exposure, use)
    }

    private fun addExposureWindow(
        sample: ContextSample,
        start: Long,
        end: Long,
        zone: ZoneId,
        spanIndex: SpanIndex,
        includeHeadphones: Boolean,
        includeVehicle: Boolean,
        gyroMedian: Float?,
        situationBoundaries: List<Long>,
        exposure: MutableMap<ExposureKey, ExposureAccumulator>,
    ) {
        var cursor = start
        while (cursor < end) {
            val nextEventBoundary = situationBoundaries.firstOrNull { it > cursor } ?: end
            val pieceEnd = minOf(end, nextStratumBoundary(cursor, zone), nextEventBoundary)
            if (pieceEnd <= cursor) break

            val midpoint = cursor + (pieceEnd - cursor) / 2L
            val states = statesFor(
                sample,
                spanIndex.tagsAt(midpoint),
                includeHeadphones,
                includeVehicle,
                gyroMedian,
            )
            val stratum = stratumAt(midpoint, zone)
            val day = TimeUtil.dateOf(midpoint, zone)
            states.forEach { state ->
                val key = ExposureKey(state.dimension, state.phrase, stratum)
                val accumulator = exposure.getOrPut(key) { ExposureAccumulator() }
                accumulator.durationMs += pieceEnd - cursor
                accumulator.days += day
            }
            cursor = pieceEnd
        }
    }

    private fun statesFor(
        sample: ContextSample,
        tags: Set<String>,
        includeHeadphones: Boolean,
        includeVehicle: Boolean,
        gyroMedian: Float?,
    ): List<State> {
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
                val phrase =
                    if (variance >= gyroMedian) {
                        "the phone is rotating more than your usual level"
                    } else {
                        "the phone is rotating less than your usual level"
                    }
                out += State(ContextDimension.PHONE_ROTATION, phrase)
            }
        }

        out += State(
            ContextDimension.CHARGING,
            if (sample.isCharging) "the phone is charging" else "the phone is unplugged",
        )

        if (includeHeadphones) {
            out += State(
                ContextDimension.HEADPHONES,
                if (SpanIndex.HEADPHONES in tags) "headphones are connected" else "headphones are disconnected",
            )
        }
        if (includeVehicle) {
            out += State(
                ContextDimension.VEHICLE,
                if (SpanIndex.VEHICLE in tags) {
                    "activity recognition reports a vehicle"
                } else {
                    "activity recognition does not report a vehicle"
                },
            )
        }

        sample.powerSave?.let { enabled ->
            out += State(
                ContextDimension.POWER_SAVER,
                if (enabled) "battery saver is on" else "battery saver is off",
            )
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
        val groups =
            use.keys.map { GroupKey(it.appName, it.dimension, it.phrase) }.distinct()
        val output = mutableListOf<ContextRelationship>()

        for (group in groups) {
            val strata =
                exposure.keys.asSequence()
                    .filter { it.dimension == group.dimension && it.phrase == group.phrase }
                    .map { it.stratum }
                    .distinct()
                    .toList()

            var stateExposureMs = 0L
            var otherExposureMs = 0L
            var stateUseMs = 0L
            var otherUseMs = 0L
            var matchedStrata = 0
            val stateExposureDays = mutableSetOf<java.time.LocalDate>()
            val otherExposureDays = mutableSetOf<java.time.LocalDate>()
            val stateUseDays = mutableSetOf<java.time.LocalDate>()
            val otherUseDays = mutableSetOf<java.time.LocalDate>()
            val stateSessionIds = mutableSetOf<Int>()
            val otherSessionIds = mutableSetOf<Int>()

            for (stratum in strata) {
                val stateExposure =
                    exposure[ExposureKey(group.dimension, group.phrase, stratum)] ?: continue
                val otherExposureEntries =
                    exposure.filterKeys {
                        it.dimension == group.dimension &&
                            it.stratum == stratum &&
                            it.phrase != group.phrase
                    }
                val otherStratumExposureMs = otherExposureEntries.values.sumOf { it.durationMs }
                if (
                    stateExposure.durationMs < MIN_STRATUM_EXPOSURE_MS ||
                    otherStratumExposureMs < MIN_STRATUM_EXPOSURE_MS
                ) {
                    continue
                }

                matchedStrata++
                stateExposureMs += stateExposure.durationMs
                otherExposureMs += otherStratumExposureMs
                stateExposureDays += stateExposure.days
                otherExposureEntries.values.forEach { otherExposureDays += it.days }

                use[UseKey(group.appName, group.dimension, group.phrase, stratum)]?.let { stateUse ->
                    stateUseMs += stateUse.durationMs
                    stateSessionIds += stateUse.sessionIds
                    stateUseDays += stateUse.days
                }
                otherExposureEntries.keys.forEach { otherKey ->
                    use[UseKey(group.appName, group.dimension, otherKey.phrase, stratum)]?.let { otherUse ->
                        otherUseMs += otherUse.durationMs
                        otherSessionIds += otherUse.sessionIds
                        otherUseDays += otherUse.days
                    }
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
                stateSessionIds.size + otherSessionIds.size < MIN_TOTAL_SESSIONS ||
                stateUseMs < MIN_USE_PER_GROUP_MS ||
                otherUseMs < MIN_USE_PER_GROUP_MS
            ) {
                continue
            }

            val stateRate = stateUseMs.toDouble() / stateExposureMs
            val otherRate = otherUseMs.toDouble() / otherExposureMs
            if (stateRate <= 0.0 || otherRate <= 0.0) continue
            val ratio = stateRate / otherRate
            if (ratio in 0.80..1.25) continue

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
        return Stratum(
            weekend = TimeUtil.isWeekend(local.toLocalDate()),
            threeHourBand = local.hour / 3,
        )
    }

    private fun nextStratumBoundary(time: Long, zone: ZoneId): Long {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        val nextHour = (local.hour / 3 + 1) * 3
        val boundary =
            if (nextHour >= 24) {
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
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2f
        } else {
            sorted[middle]
        }
    }
}
