package com.habitminer.engine

/**
 * One app-use event in a single day/time-bin sequence.
 *
 * Package names are the tokens: display names can change or collide, whereas package names
 * identify the installed app consistently.
 */
internal data class SequenceEvent(
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val endTime: Long,
)

/** The observed app sequence for one calendar day in one day-type/time-of-day bin. */
internal data class DailySequence(
    val dayKey: String,
    val events: List<SequenceEvent>,
)

/** A recurring ordered subsequence, supported by distinct observed days. */
internal data class MinedSequencePattern(
    val packageSequence: List<String>,
    val appSequence: List<String>,
    val occurrences: Int,
    val lastSeenAt: Long,
)

/**
 * Bounded PrefixSpan-style sequential pattern mining.
 *
 * Unlike fixed 2/3-grams, this finds recurring subsequences with intervening app events, while
 * still requiring consecutive selected events to be no more than [maxGapMs] apart. Support is
 * counted once per day, never once per repeated occurrence on the same day.
 */
internal object SequentialPatternMiner {
    const val DEFAULT_MIN_SUPPORT = 5
    const val DEFAULT_MAX_PATTERN_LENGTH = 4
    const val DEFAULT_MAX_GAP_MS = 15 * 60 * 1000L

    private data class Projection(
        val dayIndex: Int,
        val nextIndex: Int,
        val lastEnd: Long,
    )

    fun mine(
        inputDays: List<DailySequence>,
        minSupport: Int = DEFAULT_MIN_SUPPORT,
        maxPatternLength: Int = DEFAULT_MAX_PATTERN_LENGTH,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
    ): List<MinedSequencePattern> {
        require(minSupport >= 1) { "minSupport must be positive" }
        require(maxPatternLength >= 2) { "maxPatternLength must be at least 2" }
        require(maxGapMs >= 0) { "maxGapMs cannot be negative" }
        if (inputDays.size < minSupport) return emptyList()

        // Merge adjacent sessions of the same app before mining. This avoids treating a
        // collector/session boundary as a meaningful app transition.
        val days = inputDays.map { day ->
            val collapsed = mutableListOf<SequenceEvent>()
            for (event in day.events.sortedBy { it.startTime }) {
                val previous = collapsed.lastOrNull()
                if (previous?.packageName == event.packageName) {
                    collapsed[collapsed.lastIndex] = previous.copy(
                        endTime = maxOf(previous.endTime, event.endTime),
                    )
                } else {
                    collapsed += event
                }
            }
            collapsed
        }
        if (days.count { it.isNotEmpty() } < minSupport) return emptyList()

        val names = inputDays.flatMap { it.events }.associate { it.packageName to it.appName }
        val discovered = mutableListOf<MinedSequencePattern>()

        fun grow(
            prefix: List<String>,
            projections: List<Projection>,
        ) {
            if (prefix.size >= maxPatternLength) return

            // Each token maps to all projected suffixes where that token can extend the prefix.
            val extensions = LinkedHashMap<String, MutableList<Projection>>()
            for (projection in projections) {
                val events = days[projection.dayIndex]
                for (index in projection.nextIndex until events.size) {
                    val event = events[index]
                    if (projection.lastEnd != Long.MIN_VALUE &&
                        event.startTime - projection.lastEnd > maxGapMs
                    ) {
                        // Events are time-sorted, so later events cannot satisfy the gap either.
                        break
                    }
                    if (prefix.lastOrNull() == event.packageName) continue
                    extensions.getOrPut(event.packageName) { mutableListOf() } +=
                        Projection(projection.dayIndex, index + 1, event.endTime)
                }
            }

            for ((token, nextProjections) in extensions) {
                val supportDays = nextProjections.asSequence().map { it.dayIndex }.distinct().count()
                if (supportDays < minSupport) continue

                val pattern = prefix + token
                // The empty-prefix pass seeds the search with single app tokens. Those tokens
                // are not habits; only emit a pattern once it contains an actual transition.
                if (prefix.isNotEmpty()) {
                    discovered += MinedSequencePattern(
                        packageSequence = pattern,
                        appSequence = pattern.map { names[it] ?: it },
                        occurrences = supportDays,
                        lastSeenAt = nextProjections.maxOf { it.lastEnd },
                    )
                }
                grow(pattern, nextProjections)
            }
        }

        grow(
            emptyList(),
            days.indices.map { Projection(it, 0, Long.MIN_VALUE) },
        )

        // Preserve meaningful shorter subsequences as well as longer patterns: an A → B
        // routine can remain useful even when A → X → B is also common.
        return discovered.distinctBy { it.packageSequence }.sortedWith(
            compareByDescending<MinedSequencePattern> { it.occurrences }
                .thenByDescending { it.packageSequence.size }
                .thenBy { it.packageSequence.joinToString("\u0000") },
        )
    }
}
