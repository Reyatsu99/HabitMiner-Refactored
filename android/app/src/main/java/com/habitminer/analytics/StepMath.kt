package com.habitminer.analytics

import java.time.LocalDate

/**
 * Turns Android's cumulative step counter (total steps since the last reboot) into
 * "steps today" and "steps since the previous reading". The counter resets to zero on
 * reboot, which shows up as a value smaller than the previous one.
 */
object StepMath {
    /** Steps between two counter readings; a smaller current value means a reboot reset. */
    fun delta(
        previous: Long?,
        current: Long,
    ): Long =
        when {
            previous == null || previous < 0 -> 0L
            current >= previous -> current - previous
            else -> current
        }

    /** Running state for today's count; persisted between app restarts. */
    data class DayState(
        val day: LocalDate,
        /** Counter value that marks the start of today's count. */
        val base: Long,
        /** Steps counted today before a reboot reset the counter. */
        val carried: Long,
        /** Most recent counter value seen. */
        val last: Long,
    ) {
        val stepsToday: Long get() = (carried + (last - base)).coerceAtLeast(0L)
    }

    fun advance(
        state: DayState?,
        today: LocalDate,
        counter: Long,
    ): DayState {
        if (state == null) return DayState(today, base = counter, carried = 0L, last = counter)
        if (state.day != today) {
            // New day. If we saw the counter yesterday, steps since that reading count for
            // today; after a longer gap we can't tell when they happened, so start fresh.
            val base = if (state.day == today.minusDays(1) && counter >= state.last) state.last else counter
            return DayState(today, base = base, carried = 0L, last = counter)
        }
        if (counter < state.last) {
            // Reboot: keep what was counted so far and restart from the new counter.
            return DayState(today, base = 0L, carried = state.stepsToday, last = counter)
        }
        return state.copy(last = counter)
    }

    /** One step-counter report: when the steps happened (device uptime, ms) and the counter value. */
    data class StepPoint(
        val atMs: Long,
        val counter: Long,
    )

    /**
     * Steps taken in the [windowMs] before [nowMs], from the counter reports in [points].
     *
     * The counter only reports when steps happen, so no report inside the window means no
     * steps. Without a report from before the window, the first report inside it can't be
     * attributed (its steps may be older), so the result is a lower bound. Returns null when
     * there are no reports at all.
     */
    fun stepsInWindow(
        points: List<StepPoint>,
        nowMs: Long,
        windowMs: Long,
    ): Int? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.atMs }
        val start = nowMs - windowMs
        val inWindow = sorted.filter { it.atMs > start && it.atMs <= nowMs }
        if (inWindow.isEmpty()) return 0
        val baseline = sorted.lastOrNull { it.atMs <= start }
        val chain = listOfNotNull(baseline) + inWindow
        var steps = 0L
        for (i in 1 until chain.size) steps += delta(chain[i - 1].counter, chain[i].counter)
        return steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Drops reports older than [keepMs], keeping the newest older one as a baseline. */
    fun trim(
        points: List<StepPoint>,
        nowMs: Long,
        keepMs: Long,
    ): List<StepPoint> {
        val sorted = points.sortedBy { it.atMs }
        val cutoff = nowMs - keepMs
        val recent = sorted.filter { it.atMs > cutoff }
        val baseline = sorted.lastOrNull { it.atMs <= cutoff }
        return listOfNotNull(baseline) + recent
    }
}

/** Summary statistics of a sensor's magnitude over a short window. */
data class SignalStats(
    val mean: Float,
    val variance: Float,
    val std: Float,
    val min: Float,
    val max: Float,
    val energy: Float,
    val count: Int,
)

object MotionMath {
    /** Minimum readings after the warm-up for the window to count. */
    const val MIN_READINGS = 8

    /**
     * Statistics over (msSinceRegistration, magnitude) readings, ignoring the first [warmUpMs].
     *
     * Some sensor drivers first replay the last cached value (or several copies of it) when a
     * listener registers, which made a phone that was moving look perfectly still. Returns
     * null when too few readings remain, or (with [rejectConstant]) when every reading is
     * identical: an accelerometer always shows some noise, so that means the driver only
     * replayed a stale value rather than measuring. Gyroscopes can legitimately report a flat
     * zero on a table, so they pass false.
     */
    fun stats(
        readings: List<Pair<Long, Float>>,
        warmUpMs: Long,
        rejectConstant: Boolean = true,
    ): SignalStats? {
        val values = readings.filter { it.first >= warmUpMs }.map { it.second }
        if (values.size < MIN_READINGS) return null
        if (rejectConstant && values.distinct().size == 1) return null
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return SignalStats(
            mean = mean.toFloat(),
            variance = variance.toFloat(),
            std = kotlin.math.sqrt(variance).toFloat(),
            min = values.min(),
            max = values.max(),
            energy = values.map { it.toDouble() * it }.average().toFloat(),
            count = values.size,
        )
    }
}
