package com.habitminer.analytics

/** Consecutive app sessions with short gaps between them, i.e. one "phone pickup". */
data class ScreenSession(
    val start: Long,
    val end: Long,
    val usages: List<UsageSession>,
) {
    val totalMs: Long get() = usages.sumOf { it.durationMs }

    /** Per-app totals, largest first. */
    val appTotals: List<Pair<String, Long>>
        get() =
            usages.groupBy { it.appName }
                .map { (app, list) -> app to list.sumOf { it.durationMs } }
                .sortedByDescending { it.second }

    val categoryTotals: List<Pair<AppCategory, Long>>
        get() =
            usages.groupBy { it.category }
                .map { (cat, list) -> cat to list.sumOf { it.durationMs } }
                .sortedByDescending { it.second }

    val isOnlySystemNoise: Boolean
        get() = usages.all { CategoryMapper.isSystemNoise(it.packageName, it.appName) }
}

object SessionGrouper {
    const val DEFAULT_GAP_MS = 5 * TimeUtil.MINUTE

    fun group(
        usages: List<UsageSession>,
        maxGapMs: Long = DEFAULT_GAP_MS,
    ): List<ScreenSession> {
        val sorted = usages.sortedBy { it.start }
        val result = mutableListOf<ScreenSession>()
        var current = mutableListOf<UsageSession>()
        var currentEnd = Long.MIN_VALUE
        for (u in sorted) {
            if (current.isNotEmpty() && u.start - currentEnd > maxGapMs) {
                result.add(ScreenSession(current.first().start, currentEnd, current))
                current = mutableListOf()
            }
            current.add(u)
            currentEnd = if (current.size == 1) u.end else maxOf(currentEnd, u.end)
        }
        if (current.isNotEmpty()) result.add(ScreenSession(current.first().start, currentEnd, current))
        return result
    }

    /** Totals by category, largest first. */
    fun byCategory(usages: List<UsageSession>): List<Pair<AppCategory, Long>> =
        usages.groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.durationMs } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

    /** Totals by app name, largest first. */
    fun byApp(usages: List<UsageSession>): List<Pair<String, Long>> =
        usages.groupBy { it.appName }
            .map { (app, list) -> app to list.sumOf { it.durationMs } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
}

enum class Motion(val label: String) { STILL("Still"), MOVING("Moving"), ACTIVE("Very active") }

enum class Light(val label: String) { DARK("Dark"), DIM("Dim"), BRIGHT("Bright") }

/** Turns raw sensor numbers into labels; missing readings stay null instead of looking like "low". */
object ContextLabels {
    /**
     * Accelerometer magnitude variance (m/s²)² below this reads as still. A phone held
     * steady or typed on stays well under it; walking while looking at the screen is above.
     */
    const val STILL_VARIANCE = 0.3f

    /** Variance above this reads as very active (running, or a phone shaken in a pocket). */
    const val ACTIVE_VARIANCE = 4.0f

    /** How far back "recent steps" look before each reading. */
    const val RECENT_STEPS_WINDOW_MS = 2 * TimeUtil.MINUTE

    /** Steps in that window that mean walking (step counters ignore a few stray steps). */
    const val WALKING_STEPS = 20

    /** Steps in that window that mean brisk walking or running (~130+ steps a minute). */
    const val BRISK_STEPS = 260

    /**
     * Motion from the accelerometer, the step counter, or both. Each one can only push the
     * label up: the step counter catches walking that a short accelerometer window held in
     * a steady hand reads as still, and the accelerometer catches movement without steps.
     * Few or no steps without an accelerometer reading stays unknown (the phone could be
     * in a vehicle).
     */
    fun motion(
        variance: Float?,
        recentSteps: Int? = null,
    ): Motion? {
        val fromAccel =
            when {
                variance == null || variance < 0f -> null
                variance < STILL_VARIANCE -> Motion.STILL
                variance < ACTIVE_VARIANCE -> Motion.MOVING
                else -> Motion.ACTIVE
            }
        val fromSteps =
            when {
                recentSteps == null || recentSteps < 0 -> null
                recentSteps >= BRISK_STEPS -> Motion.ACTIVE
                recentSteps >= WALKING_STEPS -> Motion.MOVING
                else -> null
            }
        return listOfNotNull(fromAccel, fromSteps).maxByOrNull { it.ordinal }
    }

    fun motion(sample: ContextSample): Motion? = motion(sample.motionVariance, sample.recentSteps)

    /** True when the step counter, not the accelerometer, is what made this reading "moving". */
    fun motionFromSteps(
        variance: Float?,
        recentSteps: Int?,
    ): Boolean = motion(variance, recentSteps) != motion(variance, null)

    fun light(lux: Float?): Light? =
        when {
            lux == null || lux < 0f -> null
            lux <= 10f -> Light.DARK
            lux <= 100f -> Light.DIM
            else -> Light.BRIGHT
        }

    fun hasSensorData(sample: ContextSample): Boolean = sample.lightLux != null || sample.motionVariance != null

    /** Most recent sample that actually has sensor readings, if any within [maxAgeMs]. */
    fun latestWithSensors(
        samples: List<ContextSample>,
        now: Long,
        maxAgeMs: Long = 12 * TimeUtil.HOUR,
    ): ContextSample? =
        samples.filter { hasSensorData(it) && it.timestamp <= now && now - it.timestamp <= maxAgeMs }
            .maxByOrNull { it.timestamp }

    /** Nearest sample with sensor data within [toleranceMs] of [time]. */
    fun nearestWithSensors(
        samples: List<ContextSample>,
        time: Long,
        toleranceMs: Long = 10 * TimeUtil.MINUTE,
    ): ContextSample? =
        samples.filter { hasSensorData(it) && kotlin.math.abs(it.timestamp - time) <= toleranceMs }
            .minByOrNull { kotlin.math.abs(it.timestamp - time) }

    fun nearest(
        samples: List<ContextSample>,
        time: Long,
        toleranceMs: Long = 10 * TimeUtil.MINUTE,
    ): ContextSample? =
        samples.filter { kotlin.math.abs(it.timestamp - time) <= toleranceMs }
            .minByOrNull { kotlin.math.abs(it.timestamp - time) }
}

/** Time-sorted samples with fast nearest-neighbour lookup (binary search). */
class SampleIndex(samples: List<ContextSample>) {
    private val all = samples.sortedBy { it.timestamp }
    private val withSensors = all.filter { ContextLabels.hasSensorData(it) }

    val isEmpty: Boolean get() = all.isEmpty()

    fun nearest(
        time: Long,
        toleranceMs: Long = 10 * TimeUtil.MINUTE,
    ): ContextSample? = nearestIn(all, time, toleranceMs)

    fun nearestWithSensors(
        time: Long,
        toleranceMs: Long = 10 * TimeUtil.MINUTE,
    ): ContextSample? = nearestIn(withSensors, time, toleranceMs)

    fun between(
        start: Long,
        end: Long,
    ): List<ContextSample> {
        val from = lowerBound(all, start)
        val out = mutableListOf<ContextSample>()
        var i = from
        while (i < all.size && all[i].timestamp < end) {
            out.add(all[i])
            i++
        }
        return out
    }

    private fun nearestIn(
        list: List<ContextSample>,
        time: Long,
        toleranceMs: Long,
    ): ContextSample? {
        if (list.isEmpty()) return null
        val i = lowerBound(list, time)
        var best: ContextSample? = null
        var bestDist = Long.MAX_VALUE
        for (j in intArrayOf(i - 1, i)) {
            if (j in list.indices) {
                val d = kotlin.math.abs(list[j].timestamp - time)
                if (d <= toleranceMs && d < bestDist) {
                    best = list[j]
                    bestDist = d
                }
            }
        }
        return best
    }

    private fun lowerBound(
        list: List<ContextSample>,
        time: Long,
    ): Int {
        var lo = 0
        var hi = list.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].timestamp < time) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
