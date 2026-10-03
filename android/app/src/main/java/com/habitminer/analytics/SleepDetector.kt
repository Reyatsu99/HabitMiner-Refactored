package com.habitminer.analytics

import java.time.LocalDate
import java.time.ZoneId

enum class Confidence(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/** One night's estimated sleep, keyed by the date the person woke up. */
data class SleepEstimate(
    val wakeDate: LocalDate,
    val sleepStart: Long,
    val wakeTime: Long,
    val confidence: Confidence,
    /** Phone use in the hour before falling asleep. */
    val preSleepUseMs: Long,
    /** Share of that pre-sleep use that happened in the dark, when light readings exist. */
    val preSleepDarkShare: Float?,
    val lastAppBeforeSleep: String?,
    val firstAppAfterWake: String?,
) {
    val durationMs: Long get() = wakeTime - sleepStart
}

data class SleepSummary(
    val nights: Int,
    val avgDurationMs: Long,
    /** Minutes after midnight; may be negative for bedtimes before midnight. */
    val avgBedtimeMinutes: Int,
    val avgWakeMinutes: Int,
    val avgPreSleepUseMs: Long,
)

/**
 * Estimates sleep from the longest stretch without phone activity overnight.
 *
 * The usage collector closes every session when the screen turns off, so a long gap
 * between sessions is a long stretch with the screen off. Unlocks without any app use
 * also count as activity. Context snapshots (charging, darkness) raise the confidence.
 */
object SleepDetector {
    private const val MIN_SLEEP_MS = 3 * TimeUtil.HOUR
    private const val MAX_SLEEP_MS = 14 * TimeUtil.HOUR

    fun detect(
        sessions: List<UsageSession>,
        unlocks: List<Long>,
        samples: List<ContextSample>,
        wakeDate: LocalDate,
        now: Long,
        zone: ZoneId,
    ): SleepEstimate? {
        // Night window: 18:00 the evening before → 14:00 on the wake date.
        val windowStart = TimeUtil.at(wakeDate, -6, 0, zone)
        val windowEnd = minOf(TimeUtil.at(wakeDate, 14, 0, zone), now)
        if (windowEnd <= windowStart) return null

        // Activity intervals inside the window (sessions + zero-length unlocks).
        val intervals =
            (
                sessions.filter { it.end > windowStart && it.start < windowEnd }
                    .map { maxOf(it.start, windowStart) to minOf(it.end, windowEnd) } +
                    unlocks.filter { it in windowStart until windowEnd }.map { it to it }
            ).sortedBy { it.first }
        if (intervals.size < 2) return null

        // Merge overlapping intervals, then find the longest gap between them.
        val merged = mutableListOf<Pair<Long, Long>>()
        for (iv in intervals) {
            val last = merged.lastOrNull()
            if (last != null && iv.first <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, iv.second)
            } else {
                merged.add(iv)
            }
        }
        var bestStart = 0L
        var bestEnd = 0L
        for (i in 0 until merged.size - 1) {
            val gapStart = merged[i].second
            val gapEnd = merged[i + 1].first
            if (gapEnd - gapStart > bestEnd - bestStart) {
                bestStart = gapStart
                bestEnd = gapEnd
            }
        }
        val gap = bestEnd - bestStart
        if (gap < MIN_SLEEP_MS || gap > MAX_SLEEP_MS) return null

        // The middle of a sleep gap should fall between 23:00 and 11:00.
        val mid = bestStart + gap / 2
        val midMinutes = TimeUtil.minuteOfDay(mid, zone)
        val midOk = midMinutes >= 23 * 60 || midMinutes <= 11 * 60
        if (!midOk) return null

        // Confidence from duration, timing and context evidence inside the gap.
        val inGap = samples.filter { it.timestamp in bestStart..bestEnd }
        val chargingShare = if (inGap.isEmpty()) 0f else inGap.count { it.isCharging }.toFloat() / inGap.size
        val darkEvidence = inGap.any { (it.lightLux ?: 1000f) in 0f..10f }
        val classicTiming = midMinutes in 0..(8 * 60)
        var score = 0
        if (gap >= 5 * TimeUtil.HOUR) score++
        if (classicTiming) score++
        if (chargingShare >= 0.5f || darkEvidence) score++
        val confidence =
            when {
                score >= 3 -> Confidence.HIGH
                score >= 2 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }

        // Phone use in the hour before sleep, and how much of it was in the dark.
        val preStart = bestStart - TimeUtil.HOUR
        val preSessions = sessions.filter { it.end > preStart && it.start < bestStart }
        val preUse = preSessions.sumOf { TimeUtil.usageIn(it, preStart, bestStart) }
        val index = SampleIndex(samples)
        var knownLight = 0L
        var dark = 0L
        for (s in preSessions) {
            val ms = TimeUtil.usageIn(s, preStart, bestStart)
            val sample = index.nearestWithSensors(s.start + (s.end - s.start) / 2) ?: continue
            val light = ContextLabels.light(sample.lightLux) ?: continue
            knownLight += ms
            if (light == Light.DARK) dark += ms
        }
        val darkShare = if (knownLight >= 5 * TimeUtil.MINUTE) dark.toFloat() / knownLight else null

        val lastApp = sessions.filter { it.end <= bestStart + TimeUtil.MINUTE }.maxByOrNull { it.end }?.appName
        val firstApp = sessions.filter { it.start >= bestEnd - TimeUtil.MINUTE }.minByOrNull { it.start }?.appName

        return SleepEstimate(
            wakeDate = wakeDate,
            sleepStart = bestStart,
            wakeTime = bestEnd,
            confidence = confidence,
            preSleepUseMs = preUse,
            preSleepDarkShare = darkShare,
            lastAppBeforeSleep = lastApp,
            firstAppAfterWake = firstApp,
        )
    }

    /** Estimates for each of the last [days] wake dates, oldest first. */
    fun detectRange(
        sessions: List<UsageSession>,
        unlocks: List<Long>,
        samples: List<ContextSample>,
        today: LocalDate,
        days: Int,
        now: Long,
        zone: ZoneId,
    ): List<SleepEstimate> =
        (days - 1 downTo 0).mapNotNull { back ->
            detect(sessions, unlocks, samples, today.minusDays(back.toLong()), now, zone)
        }

    fun summarize(
        nights: List<SleepEstimate>,
        zone: ZoneId,
    ): SleepSummary? {
        if (nights.isEmpty()) return null
        // Bedtimes are averaged relative to midnight with evening times as negatives
        // so 23:30 and 00:30 average to 00:00 instead of 12:00.
        val bedtimes =
            nights.map {
                val m = TimeUtil.minuteOfDay(it.sleepStart, zone)
                if (m >= 12 * 60) m - 24 * 60 else m
            }
        val wakes = nights.map { TimeUtil.minuteOfDay(it.wakeTime, zone) }
        return SleepSummary(
            nights = nights.size,
            avgDurationMs = nights.map { it.durationMs }.average().toLong(),
            avgBedtimeMinutes = bedtimes.average().toInt(),
            avgWakeMinutes = wakes.average().toInt(),
            avgPreSleepUseMs = nights.map { it.preSleepUseMs }.average().toLong(),
        )
    }
}
