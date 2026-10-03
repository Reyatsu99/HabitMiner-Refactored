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
        private data class UsageItem(val packageName: String, val appName: String)

        fun discoverHabits(allUsage: List<AppUsageEntity>): List<DiscoveredHabitEntity> {
            val validUsage = allUsage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            if (validUsage.isEmpty()) return emptyList()

            val gson = Gson()
            val habits = mutableListOf<DiscoveredHabitEntity>()

            // Group by dayType and timeSlot
            val groupedByBin = validUsage.groupBy { "${it.dayType}_${it.timeSlot}" }

            for ((bin, usagesInBin) in groupedByBin) {
                val parts = bin.split("_")
                val dayType = parts[0]
                val timeSlot = parts[1]

                // Group by date (calendar day)
                val groupedByDate =
                    usagesInBin.groupBy {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it.startTime
                        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                    }

                val totalDays = groupedByDate.size
                if (totalDays < 5) continue // need at least 5 days for a pattern

                // Extract n-grams (size 2 and 3)
                val patternCounts = mutableMapOf<List<UsageItem>, Int>()
                val patternLastSeen = mutableMapOf<List<UsageItem>, Long>()

                for (dayUsages in groupedByDate.values) {
                    val sorted = dayUsages.sortedBy { it.startTime }
                    val seenToday = mutableSetOf<List<UsageItem>>()

                    for (size in 2..3) {
                        if (sorted.size < size) continue
                        for (i in 0..sorted.size - size) {
                            // Check time gap between adjacent items in the pattern
                            var isValidPattern = true
                            for (j in 0 until size - 1) {
                                val current = sorted[i + j]
                                val next = sorted[i + j + 1]
                                if (next.startTime - current.endTime > 15 * 60 * 1000L) {
                                    isValidPattern = false
                                    break
                                }
                            }
                            if (isValidPattern) {
                                val pattern = sorted.subList(i, i + size).map { UsageItem(it.packageName, it.appName) }
                                // simple deduplication of adjacent identical apps
                                val cleanPattern =
                                    pattern.filterIndexed { index, item ->
                                        index == 0 || item.packageName != pattern[index - 1].packageName
                                    }
                                if (cleanPattern.size >= 2 && seenToday.add(cleanPattern)) {
                                    patternCounts[cleanPattern] = patternCounts.getOrDefault(cleanPattern, 0) + 1
                                }
                                if (cleanPattern.size >= 2) {
                                    val seenAt = sorted[i + size - 1].endTime
                                    patternLastSeen[cleanPattern] = maxOf(patternLastSeen[cleanPattern] ?: 0L, seenAt)
                                }
                            }
                        }
                    }
                }

                // Filter out subset patterns that are just sub-parts of a longer pattern with similar counts
                val sortedPatterns = patternCounts.keys.sortedByDescending { it.size }
                val filteredCounts = mutableMapOf<List<UsageItem>, Int>()

                for (pattern in sortedPatterns) {
                    val count = patternCounts[pattern] ?: 0
                    // Check if this pattern is a subset of any already accepted larger pattern
                    val isSubset =
                        filteredCounts.keys.any { acceptedPattern ->
                            // Check if pattern is a sublist of acceptedPattern
                            var found = false
                            if (acceptedPattern.size > pattern.size) {
                                for (i in 0..acceptedPattern.size - pattern.size) {
                                    if (acceptedPattern.subList(i, i + pattern.size) == pattern) {
                                        found = true
                                        break
                                    }
                                }
                            }
                            // If it's a subset and occurs roughly the same number of times (+/- 10%), it's redundant
                            found && (count <= (filteredCounts[acceptedPattern] ?: 0) + 1)
                        }
                    if (!isSubset) {
                        filteredCounts[pattern] = count
                    }
                }

                for ((pattern, count) in filteredCounts) {
                    val confidence = count.toFloat() / totalDays
                    if (count >= 5 && confidence >= 0.4f) {
                        val dominantApp =
                            pattern.map { it.appName }
                                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "App"

                        val habitName = labelHabit(dominantApp, timeSlot, dayType)
                        val description = pattern.joinToString(" → ") { it.appName }
                        val packageSequence = pattern.map { it.packageName }

                        habits.add(
                            DiscoveredHabitEntity(
                                habitName = habitName,
                                patternDescription = description,
                                appSequence = gson.toJson(packageSequence),
                                confidence = confidence,
                                occurrenceCount = count,
                                timeSlot = timeSlot,
                                dayType = dayType,
                                discoveredAt = System.currentTimeMillis(),
                                lastSeenAt = patternLastSeen[pattern] ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }

            // Return deduplicated top patterns
            return habits.sortedByDescending { it.confidence }
                .distinctBy { it.patternDescription + it.timeSlot + it.dayType }
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
