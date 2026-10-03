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
}
