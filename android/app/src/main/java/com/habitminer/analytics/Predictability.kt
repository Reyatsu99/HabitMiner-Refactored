package com.habitminer.analytics

import java.time.ZoneId

/**
 * Honest, measured predictability: train a time-of-day Markov model on older history,
 * then check how often it guesses the next app correctly on the most recent days.
 * Compared against two baselines so the number means something.
 */
data class PredictabilityResult(
    val hitRate: Float,
    val top3HitRate: Float,
    /** Always guessing your single most-used next app. */
    val mostUsedBaseline: Float,
    /** Picking uniformly at random among the apps you use. */
    val randomBaseline: Float,
    val testedTransitions: Int,
    val testDays: Int,
)

object PredictabilityEvaluator {
    private const val MAX_GAP_MS = 15 * TimeUtil.MINUTE
    const val MIN_TEST_TRANSITIONS = 20

    private data class Transition(val from: String, val to: String, val bin: String, val time: Long)

    private fun bin(
        ms: Long,
        zone: ZoneId,
    ): String {
        val z = TimeUtil.zoned(ms, zone)
        val slot =
            when (z.hour) {
                in 6..11 -> "M"
                in 12..16 -> "A"
                in 17..21 -> "E"
                else -> "N"
            }
        return (if (TimeUtil.isWeekend(z.toLocalDate())) "WE_" else "WD_") + slot
    }

    private fun transitions(
        sessions: List<UsageSession>,
        zone: ZoneId,
    ): List<Transition> {
        val sorted = sessions.sortedBy { it.start }
        val out = mutableListOf<Transition>()
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (b.start - a.end in 0..MAX_GAP_MS && a.appName != b.appName) {
                out.add(Transition(a.appName, b.appName, bin(b.start, zone), b.start))
            }
        }
        return out
    }

    fun evaluate(
        sessions: List<UsageSession>,
        now: Long,
        zone: ZoneId,
        testDays: Int = 3,
    ): PredictabilityResult? {
        val all = transitions(sessions.filter { it.start < now }, zone)
        val testStart = TimeUtil.startOfDay(TimeUtil.dateOf(now, zone).minusDays((testDays - 1).toLong()), zone)
        val train = all.filter { it.time < testStart }
        val test = all.filter { it.time >= testStart }
        if (test.size < MIN_TEST_TRANSITIONS || train.size < MIN_TEST_TRANSITIONS) return null

        // Model: counts[bin][from][to], with fallbacks to counts[from][to] and overall next-app counts.
        val byBin = mutableMapOf<String, MutableMap<String, MutableMap<String, Int>>>()
        val byFrom = mutableMapOf<String, MutableMap<String, Int>>()
        val overall = mutableMapOf<String, Int>()
        for (t in train) {
            byBin.getOrPut(t.bin) { mutableMapOf() }.getOrPut(t.from) { mutableMapOf() }.merge(t.to, 1, Int::plus)
            byFrom.getOrPut(t.from) { mutableMapOf() }.merge(t.to, 1, Int::plus)
            overall.merge(t.to, 1, Int::plus)
        }
        val overallRanked = overall.entries.sortedByDescending { it.value }.map { it.key }
        val mostUsed = overallRanked.first()

        fun ranked(t: Transition): List<String> {
            val counts = byBin[t.bin]?.get(t.from)?.takeIf { it.values.sum() >= 2 } ?: byFrom[t.from]
            val primary = counts?.entries?.sortedByDescending { it.value }?.map { it.key }.orEmpty()
            return (primary + overallRanked).distinct().filter { it != t.from }
        }

        var hits = 0
        var hits3 = 0
        var baseline = 0
        for (t in test) {
            val r = ranked(t)
            if (r.firstOrNull() == t.to) hits++
            if (t.to in r.take(3)) hits3++
            val base = if (mostUsed != t.from) mostUsed else overallRanked.getOrNull(1)
            if (base == t.to) baseline++
        }
        val vocab = (train.map { it.to } + train.map { it.from }).distinct().size.coerceAtLeast(2)
        return PredictabilityResult(
            hitRate = hits.toFloat() / test.size,
            top3HitRate = hits3.toFloat() / test.size,
            mostUsedBaseline = baseline.toFloat() / test.size,
            randomBaseline = 1f / (vocab - 1),
            testedTransitions = test.size,
            testDays = testDays,
        )
    }
}

// ---------------------------------------------------------------------------------------
// Patterns: group the same app sequence across time slots
// ---------------------------------------------------------------------------------------

data class PatternRow(
    val sequence: String,
    val timeSlot: String,
    val dayType: String,
    val occurrences: Int,
    val confidence: Float,
    val lastSeen: Long,
)

data class PatternGroup(
    val sequence: String,
    val apps: List<String>,
    /** "Weekday evenings & nights" */
    val whenText: String,
    /** "Seen on 10 of the last 10 weekday nights" */
    val evidenceText: String,
    val bestConfidence: Float,
    val totalOccurrences: Int,
    val lastSeen: Long,
    val slots: List<String>,
)

object PatternGrouper {
    private val slotOrder = listOf("MORNING", "AFTERNOON", "EVENING", "NIGHT")
    private val slotPlural = mapOf("MORNING" to "mornings", "AFTERNOON" to "afternoons", "EVENING" to "evenings", "NIGHT" to "nights")

    fun group(rows: List<PatternRow>): List<PatternGroup> =
        rows.groupBy { it.sequence }
            .map { (sequence, list) ->
                val slots = list.map { it.timeSlot }.distinct().sortedBy { slotOrder.indexOf(it) }
                val dayTypes = list.map { it.dayType }.distinct()
                val dayWord =
                    when {
                        dayTypes.size > 1 || "ANY" in dayTypes -> "Every day"
                        dayTypes.single() == "WEEKEND" -> "Weekend"
                        else -> "Weekday"
                    }
                val slotText = joinNatural(slots.map { slotPlural[it] ?: it.lowercase() })
                val best = list.maxByOrNull { it.confidence }!!
                val bestDays = if (best.confidence > 0f) Math.round(best.occurrences / best.confidence) else best.occurrences
                val bestSlotWord = (slotPlural[best.timeSlot] ?: best.timeSlot.lowercase())
                val bestDayWord = if (best.dayType == "WEEKEND") "weekend" else if (best.dayType == "WEEKDAY") "weekday" else ""
                PatternGroup(
                    sequence = sequence,
                    apps = sequence.split(" → "),
                    whenText = "$dayWord $slotText",
                    evidenceText = "Seen on ${best.occurrences} of the last $bestDays ${"$bestDayWord $bestSlotWord".trim()}",
                    bestConfidence = best.confidence,
                    totalOccurrences = list.sumOf { it.occurrences },
                    lastSeen = list.maxOf { it.lastSeen },
                    slots = slots,
                )
            }
            .sortedWith(compareByDescending<PatternGroup> { it.bestConfidence * Math.log(it.totalOccurrences + 1.0) }.thenBy { it.sequence })

    private fun joinNatural(items: List<String>): String =
        when (items.size) {
            0 -> ""
            1 -> items[0]
            else -> items.dropLast(1).joinToString(", ") + " & " + items.last()
        }
}
