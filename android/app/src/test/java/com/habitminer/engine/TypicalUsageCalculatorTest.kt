package com.habitminer.engine

import com.habitminer.engine.TypicalUsageCalculator.Basis
import com.habitminer.engine.TypicalUsageCalculator.Interval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TypicalUsageCalculatorTest {
    private val tz = TimeZone.getTimeZone("Asia/Kolkata")
    private val calendar = { Calendar.getInstance(tz) }
    private val min = 60_000L
    private val hour = 60 * min

    /**
     * Millis at [h]:[m] IST. Tests span Sep 19 – Oct 3 2026 (Oct 3 is a Saturday), so a
     * [day] of 19 or more means September and anything smaller means October.
     */
    private fun at(
        day: Int,
        h: Int,
        m: Int = 0,
    ): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(2026, if (day >= 19) Calendar.SEPTEMBER else Calendar.OCTOBER, day, h, m, 0)
        }.timeInMillis

    private fun session(
        day: Int,
        h: Int,
        minutes: Long,
    ): Interval {
        val start = at(day, h)
        return Interval(start, start + minutes * min, minutes * min)
    }

    @Test
    fun `returns null without any past day`() {
        val now = at(3, 18)
        assertNull(TypicalUsageCalculator.compute(listOf(session(3, 10, 30)), now, calendar))
    }

    @Test
    fun `weekend with too few weekend days falls back to all days instead of building forever`() {
        // History: Sep 26 (Sat, partial first day, skipped) .. Oct 2 (Fri). Only one weekend
        // day (Sep 27) remains after skipping the first day, so fall back to all days.
        val history = (26..30).map { session(it, 10, 60) } + (1..2).map { session(it, 10, 60) }
        val typical = TypicalUsageCalculator.compute(history, at(3, 18), calendar)!!

        assertEquals(Basis.ALL_DAYS, typical.basis)
        assertEquals("WEEKEND", typical.dayType)
        assertEquals(6, typical.daysUsed)
        assertEquals(60 * min, typical.expectedByNowMs)
    }

    @Test
    fun `uses same day type once two such days exist`() {
        // Sep 19 (Sat) is the skipped partial first day; Sep 20, 26, 27 are weekend days.
        val weekend = listOf(20, 26, 27).map { session(it, 9, 120) }
        val weekday = listOf(22, 23, 24, 25, 28, 29, 30).map { session(it, 9, 30) } +
            listOf(1, 2).map { session(it, 9, 30) }
        val typical = TypicalUsageCalculator.compute(listOf(session(19, 23, 5)) + weekend + weekday, at(3, 18), calendar)!!

        assertEquals(Basis.SAME_DAY_TYPE, typical.basis)
        assertEquals(3, typical.daysUsed)
        assertEquals(120 * min, typical.expectedByNowMs)
    }

    @Test
    fun `by-now only counts usage before the current time of day`() {
        val history =
            listOf(session(30, 8, 1)) + // partial first day, skipped
                (1..2).flatMap { listOf(session(it, 10, 60), session(it, 21, 120)) }
        val typical = TypicalUsageCalculator.compute(history, at(3, 18), calendar)!!

        assertEquals(60 * min, typical.expectedByNowMs)
        assertEquals(180 * min, typical.expectedFullDayMs)
    }

    @Test
    fun `days with no usage count as zero`() {
        // Sep 30 skipped as first day; Oct 1 has 2h, Oct 2 has nothing.
        val history = listOf(session(30, 8, 1), session(1, 10, 120))
        val typical = TypicalUsageCalculator.compute(history, at(3, 18), calendar)!!

        assertEquals(2, typical.daysUsed)
        assertEquals(60 * min, typical.expectedByNowMs)
    }

    @Test
    fun `session straddling the cutoff is pro-rated`() {
        val s = Interval(at(1, 17, 30), at(1, 18, 30), hour)
        assertEquals(30 * min, TypicalUsageCalculator.usageIn(listOf(s), at(1, 0), at(1, 18)))
    }
}
