package com.habitminer.engine

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.BaselineEntity
import com.habitminer.domain.AppIdentityResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviationDetector
    @Inject
    constructor(
        private val appIdentityResolver: AppIdentityResolver,
        private val timeProvider: TimeProvider,
    ) {
        data class DeviationResult(
            val timeBin: String,
            val deviationType: String,
            val description: String,
            val zScore: Float,
            val normalizedScore: Float,
            val affectedCategory: String,
            /** When the deviating behaviour happened (not when it was detected). */
            val occurredAt: Long,
        )

        fun detectDeviations(
            todayUsage: List<AppUsageEntity>,
            todayContexts: List<com.habitminer.data.ContextSnapshotEntity>,
            baseline: List<BaselineEntity>,
        ): List<DeviationResult> {
            val validTodayUsage = todayUsage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            val results = mutableListOf<DeviationResult>()
            val baselineMap = baseline.associateBy { it.timeBin }
            val gson = Gson()
            val type = object : TypeToken<Map<String, Long>>() {}.type

            // Get current time once — used for day-type detection, slot progress, and startOfDay below.
            val nowCal = timeProvider.getCalendar()
            val currentDay = nowCal.get(java.util.Calendar.DAY_OF_WEEK)
            val todayDayType =
                if (currentDay == java.util.Calendar.SATURDAY || currentDay == java.util.Calendar.SUNDAY) {
                    "WEEKEND"
                } else {
                    "WEEKDAY"
                }
            val groupedToday = validTodayUsage.groupBy { "${it.dayType}_${it.timeSlot}" }
            val hour = nowCal.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = nowCal.get(java.util.Calendar.MINUTE)
            val minuteOfDay = hour * 60 + minute

            val slotProgress =
                mapOf(
                    "MORNING" to
                        when {
                            hour < 6 -> 0f
                            hour >= 12 -> 1f
                            else -> (minuteOfDay - (6 * 60)) / (6 * 60f)
                        },
                    "AFTERNOON" to
                        when {
                            hour < 12 -> 0f
                            hour >= 17 -> 1f
                            else -> (minuteOfDay - (12 * 60)) / (5 * 60f)
                        },
                    "EVENING" to
                        when {
                            hour < 17 -> 0f
                            hour >= 22 -> 1f
                            else -> (minuteOfDay - (17 * 60)) / (5 * 60f)
                        },
                    "NIGHT" to
                        when {
                            hour in 6..21 -> 0f
                            else -> {
                                val mins = if (hour >= 22) (hour - 22) * 60 + minute else (hour + 2) * 60 + minute
                                mins / (8 * 60f)
                            }
                        },
                )

            val startOfDay =
                (nowCal.clone() as java.util.Calendar).apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
            val nowMs = nowCal.timeInMillis

            for ((bin, base) in baselineMap) {
                if (!bin.startsWith("${todayDayType}_")) continue
                val timeSlot = bin.substringAfter('_')
                val slotWord = timeSlot.lowercase()
                // "this morning", "this evening", but "tonight" rather than "this night".
                val thisSlot = if (timeSlot == "NIGHT") "tonight" else "this $slotWord"
                val progress = slotProgress[timeSlot] ?: continue
                if (progress <= 0f) continue
                val usages = groupedToday[bin].orEmpty()

                val todayDuration = usages.sumOf { it.durationMs }
                val expectedDuration = (base.avgScreenTimeMs * progress).toLong()

                // 1. Check for excess duration
                if (base.stdScreenTimeMs > 0) {
                    val zScore = (todayDuration - expectedDuration).toFloat() / base.stdScreenTimeMs
                    if (zScore > 1.5f) {
                        val norm = (1f / (1f + kotlin.math.exp(-zScore))).coerceIn(0f, 1f)
                        val cat = usages.maxByOrNull { it.durationMs }?.appName ?: "Unknown App"
                        results.add(
                            DeviationResult(
                                timeBin = bin,
                                deviationType = "EXCESS_DURATION",
                                description =
                                    "${formatDuration(todayDuration)} on your phone $thisSlot, " +
                                        "compared with about ${formatDuration(expectedDuration)} by now on a usual day. " +
                                        "Most of it was $cat.",
                                zScore = zScore,
                                normalizedScore = norm,
                                affectedCategory = cat,
                                occurredAt = usages.maxOfOrNull { it.endTime } ?: nowMs,
                            ),
                        )
                    }
                }

                // 2. Check for new behavior (categories)
                val todayCategories = usages.groupBy { it.appName }.mapValues { it.value.sumOf { u -> u.durationMs } }
                val baseCategories: Map<String, Long> =
                    runCatching {
                        gson.fromJson<Map<String, Long>>(base.typicalCategoriesJson, type)
                    }.getOrNull() ?: emptyMap()

                for ((cat, duration) in todayCategories) {
                    if (duration >= NEW_BEHAVIOR_MIN_MS && !baseCategories.containsKey(cat)) {
                        results.add(
                            DeviationResult(
                                timeBin = bin,
                                deviationType = "NEW_BEHAVIOR",
                                description =
                                    "You used $cat for ${formatDuration(duration)} $thisSlot. " +
                                        "It isn't usually part of your ${slotWord}s.",
                                zScore = 2.0f,
                                normalizedScore = 0.8f,
                                affectedCategory = cat,
                                occurredAt = usages.filter { it.appName == cat }.minOfOrNull { it.startTime } ?: nowMs,
                            ),
                        )
                    }
                }

                // Simplified version: if total duration < 20% of avg and avg > 30 mins
                if (progress >= 1f && base.avgScreenTimeMs > 30 * 60 * 1000 && todayDuration < base.avgScreenTimeMs * 0.2) {
                    results.add(
                        DeviationResult(
                            timeBin = bin,
                            deviationType = "MISSING_ROUTINE",
                            description =
                                "You usually spend about ${formatDuration(base.avgScreenTimeMs)} on your phone " +
                                    "in the $slotWord, but barely used it $thisSlot.",
                            zScore = -2.0f,
                            normalizedScore = 0.7f,
                            affectedCategory = "ALL",
                            occurredAt = slotEndMs(startOfDay, timeSlot).coerceAtMost(nowMs),
                        ),
                    )
                }

                val (slotStartHour, slotEndHour) =
                    when (timeSlot) {
                        "MORNING" -> 6 to 12
                        "AFTERNOON" -> 12 to 17
                        "EVENING" -> 17 to 22
                        "NIGHT" -> 22 to 30 // Up to 6am next day
                        else -> 0 to 24
                    }

                val slotStartMs = startOfDay + slotStartHour * 3600000L
                val slotEndMs = startOfDay + slotEndHour * 3600000L

                // Context Shift Detection
                val relevantContexts =
                    todayContexts.filter {
                        it.timestamp in slotStartMs..slotEndMs
                    }

                // Sensors are only sampled while the screen is on; -1 means "no reading" and must
                // never be averaged in (it used to make every slot look dark and still).
                val motionContexts = relevantContexts.filter { it.accelEnergy >= 0f }
                val lightContexts = relevantContexts.filter { it.lightLux >= 0f }

                if (motionContexts.size >= MIN_CONTEXT_SAMPLES && base.avgAccelEnergy >= 0f) {
                    val dynamicEnergyThreshold = (base.avgAccelEnergy * 0.5f).coerceAtLeast(2f).coerceAtMost(10f)
                    val activeCount = motionContexts.count { it.accelEnergy > dynamicEnergyThreshold }
                    val currentActiveRatio = activeCount.toFloat() / motionContexts.size

                    val baselineActive = base.avgAccelEnergy > dynamicEnergyThreshold
                    val currentActive = currentActiveRatio > ACTIVE_RATIO_THRESHOLD

                    if (baselineActive && !currentActive && base.avgAccelEnergy > 10f) {
                        results.add(
                            DeviationResult(
                                timeBin = bin,
                                deviationType = "CONTEXT_SHIFT",
                                description =
                                    "You're usually on the move during your ${slotWord}s, " +
                                        "but today you've mostly stayed still.",
                                zScore = 1.8f,
                                normalizedScore = 0.75f,
                                affectedCategory = "ALL",
                                occurredAt = motionContexts.maxOf { it.timestamp },
                            ),
                        )
                    } else if (!baselineActive && currentActive && currentActiveRatio > 0.6f) {
                        results.add(
                            DeviationResult(
                                timeBin = bin,
                                deviationType = "CONTEXT_SHIFT",
                                description =
                                    "You're usually still during your ${slotWord}s, " +
                                        "but today you've been very active.",
                                zScore = 1.8f,
                                normalizedScore = 0.75f,
                                affectedCategory = "ALL",
                                occurredAt = motionContexts.maxOf { it.timestamp },
                            ),
                        )
                    }
                }

                if (lightContexts.size >= MIN_CONTEXT_SAMPLES && base.avgLightLux >= 0f) {
                    val currentAvgLight = lightContexts.map { it.lightLux.toDouble() }.average().toFloat()
                    val dynamicDarknessLux = (base.avgLightLux * 0.5f).coerceAtLeast(30f)
                    val dynamicBrightnessLux = (base.avgLightLux * 1.5f).coerceAtLeast(200f).coerceAtMost(1000f)

                    val baselineDark = base.avgLightLux < dynamicDarknessLux
                    val currentDark = currentAvgLight < dynamicDarknessLux
                    val baselineBright = base.avgLightLux > dynamicBrightnessLux
                    val currentBright = currentAvgLight > dynamicBrightnessLux

                    if (baselineDark && currentBright) {
                        results.add(
                            DeviationResult(
                                timeBin = bin,
                                deviationType = "CONTEXT_SHIFT",
                                description =
                                    "Your ${slotWord}s are usually spent somewhere dark, " +
                                        "but today it's been bright.",
                                zScore = 1.6f,
                                normalizedScore = 0.65f,
                                affectedCategory = "ALL",
                                occurredAt = lightContexts.maxOf { it.timestamp },
                            ),
                        )
                    } else if (baselineBright && currentDark) {
                        results.add(
                            DeviationResult(
                                timeBin = bin,
                                deviationType = "CONTEXT_SHIFT",
                                description =
                                    "Your ${slotWord}s are usually spent somewhere bright, " +
                                        "but today it's been dark.",
                                zScore = 1.6f,
                                normalizedScore = 0.65f,
                                affectedCategory = "ALL",
                                occurredAt = lightContexts.maxOf { it.timestamp },
                            ),
                        )
                    }
                }
            }

            return results
        }

        fun generateDescription(
            deviationType: String,
            category: String,
            zScore: Float,
            timeBin: String,
        ): String {
            val parts = timeBin.split("_")
            val timeLabel = if (parts.size > 1) parts[1].lowercase() else "time"

            return when (deviationType) {
                "EXCESS_DURATION" -> "$category took substantially more of your $timeLabel than your usual routine."
                "NEW_BEHAVIOR" -> "$category is new in your typical $timeLabel routine today."
                "MISSING_ROUTINE" -> "Your usual $timeLabel activity has not appeared today."
                else -> "Deviation detected"
            }
        }

        fun getOverallDeviationScore(deviations: List<DeviationResult>): Float {
            return deviations.maxOfOrNull { it.normalizedScore }?.coerceIn(0f, 1f) ?: 0f
        }

        private fun formatDuration(milliseconds: Long): String {
            val minutes = (milliseconds / 60_000).coerceAtLeast(1)
            return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
        }

        private fun slotEndMs(
            startOfDay: Long,
            timeSlot: String,
        ): Long {
            val endHour =
                when (timeSlot) {
                    "MORNING" -> 12
                    "AFTERNOON" -> 17
                    "EVENING" -> 22
                    else -> 30
                }
            return startOfDay + endHour * 3_600_000L
        }

        companion object {
            /** A new app must be used at least this long in a slot before it counts as new behaviour. */
            const val NEW_BEHAVIOR_MIN_MS = 10 * 60 * 1000L
            const val MIN_CONTEXT_SAMPLES = 2
            const val ACTIVE_ENERGY_THRESHOLD = 5f
            const val ACTIVE_RATIO_THRESHOLD = 0.3f
            const val DARKNESS_THRESHOLD_LUX = 20f
            const val BRIGHTNESS_THRESHOLD_LUX = 500f
        }
    }
