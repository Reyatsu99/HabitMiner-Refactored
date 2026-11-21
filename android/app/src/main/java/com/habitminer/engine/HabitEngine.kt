package com.habitminer.engine

import com.google.gson.Gson
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.DiscoveredHabitEntity
import com.habitminer.domain.AppIdentityResolver
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

@Singleton
class HabitEngine
    @Inject
    constructor(
        private val appIdentityResolver: AppIdentityResolver,
    ) {
        fun discoverHabits(allUsage: List<AppUsageEntity>): List<DiscoveredHabitEntity> {
            val validUsage = allUsage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            if (validUsage.isEmpty()) return emptyList()

            val gson = Gson()
            val habits = mutableListOf<DiscoveredHabitEntity>()

            // Keep the existing weekday/weekend and time-of-day segmentation, but mine
            // variable-length recurring subsequences within each observed day.
            val groupedByBin = validUsage.groupBy { "${it.dayType}_${it.timeSlot}" }
            for ((bin, usagesInBin) in groupedByBin) {
                val dayType = bin.substringBefore('_')
                val timeSlot = bin.substringAfter('_')
                val groupedByDate = usagesInBin.groupBy {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = it.startTime
                    "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                }
                if (groupedByDate.size < SequentialPatternMiner.DEFAULT_MIN_SUPPORT) continue

                val dailySequences = groupedByDate.map { (dayKey, dayUsages) ->
                    DailySequence(
                        dayKey = dayKey,
                        events = dayUsages.map {
                            SequenceEvent(it.packageName, it.appName, it.startTime, it.endTime)
                        },
                    )
                }
                val patterns = SequentialPatternMiner.mine(dailySequences)
                val observedDays = dailySequences.size.toFloat()

                for (pattern in patterns) {
                    val confidence = pattern.occurrences / observedDays
                    if (confidence < 0.4f) continue

                    val dominantApp =
                        pattern.appSequence.groupingBy { it }.eachCount()
                            .maxByOrNull { it.value }?.key ?: "App"
                    habits.add(
                        DiscoveredHabitEntity(
                            habitName = labelHabit(dominantApp, timeSlot, dayType),
                            patternDescription = pattern.appSequence.joinToString(" → "),
                            appSequence = gson.toJson(pattern.packageSequence),
                            confidence = confidence,
                            occurrenceCount = pattern.occurrences,
                            timeSlot = timeSlot,
                            dayType = dayType,
                            discoveredAt = System.currentTimeMillis(),
                            lastSeenAt = pattern.lastSeenAt,
                        ),
                    )
                }
            }

            return habits.sortedWith(
                compareByDescending<DiscoveredHabitEntity> { it.confidence }
                    .thenByDescending { it.occurrenceCount }
                    .thenByDescending { it.lastSeenAt },
            ).distinctBy { it.patternDescription + it.timeSlot + it.dayType }
        }

        fun labelHabit(
            dominantApp: String,
            timeSlot: String,
            dayType: String,
        ): String {
            val timeLabel =
                when (timeSlot) {
                    "MORNING" -> "🌅 Morning"
                    "AFTERNOON" -> "☀️ Afternoon"
                    "EVENING" -> "🌆 Evening"
                    "NIGHT" -> "🌙 Night"
                    else -> ""
                }

            return "$timeLabel $dominantApp Routine"
        }

        fun computePredictabilityScore(allUsage: List<AppUsageEntity>): Float {
            val validUsage = allUsage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            if (validUsage.isEmpty()) return 0f

            // compute transitions
            val transitions = mutableMapOf<String, Int>()
            val sorted = validUsage.sortedBy { it.startTime }

            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val next = sorted[i + 1]
                if (next.startTime - current.endTime > 15 * 60 * 1000L) continue

                val from = current.appName
                val to = next.appName
                val key = "$from->$to"
                transitions[key] = transitions.getOrDefault(key, 0) + 1
            }

            val total = transitions.values.sum().toFloat()
            if (total == 0f) return 0f

            var entropy = 0f
            for (count in transitions.values) {
                val p = count / total
                entropy -= (p * ln(p))
            }

            val maxEntropy = ln(transitions.size.coerceAtLeast(1).toFloat())
            if (maxEntropy == 0f) return 100f

            val normalized = 1f - (entropy / maxEntropy)
            return (normalized * 100).coerceIn(0f, 100f)
        }
    }
