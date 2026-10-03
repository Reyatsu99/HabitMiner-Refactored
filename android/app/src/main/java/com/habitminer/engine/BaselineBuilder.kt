package com.habitminer.engine

import com.google.gson.Gson
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.BaselineEntity
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.domain.AppIdentityResolver
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class BaselineBuilder
    @Inject
    constructor(
        private val appIdentityResolver: AppIdentityResolver,
    ) {
        fun buildBaseline(
            allUsage: List<AppUsageEntity>,
            snapshots: List<ContextSnapshotEntity>,
        ): List<BaselineEntity> {
            val startMs = allUsage.minOfOrNull { it.startTime } ?: return emptyList()
            // Only count fully-elapsed days. The caller passes history from before today, so
            // counting today as a calendar day padded it as a zero-usage day and dragged
            // every baseline average down.
            val endMs =
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis - 1
            val validUsage = allUsage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            val gson = Gson()
            val newBaselines = mutableListOf<BaselineEntity>()
            val groupedByBin = validUsage.groupBy { "${it.dayType}_${it.timeSlot}" }

            for ((bin, usagesInBin) in groupedByBin) {
                val dayType = bin.substringBefore('_')
                val calendarDays = countDaysOfType(startMs, endMs, dayType)
                if (calendarDays < 5) continue

                val groupedByDate =
                    usagesInBin.groupBy {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it.startTime
                        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
                    }

                val dailyDurations = mutableListOf<Long>()
                val dailySessions = mutableListOf<Float>()
                val categoryTotals = mutableMapOf<String, Long>()

                for ((_, dailyUsages) in groupedByDate) {
                    val totalDuration = dailyUsages.sumOf { it.durationMs }.coerceAtMost(21600000L) // Cap at 6 hours
                    dailyDurations.add(totalDuration)
                    dailySessions.add(dailyUsages.size.toFloat())

                    // Distribute capped time across categories proportionally
                    val rawTotal = dailyUsages.sumOf { it.durationMs }
                    if (rawTotal <= 0L) continue

                    val scaleFactor = totalDuration.toDouble() / rawTotal.toDouble()

                    dailyUsages.forEach {
                        val scaledDuration = (it.durationMs * scaleFactor).toLong()
                        categoryTotals[it.appName] = categoryTotals.getOrDefault(it.appName, 0L) + scaledDuration
                    }
                }

                // Pad missing calendar days with zero usage
                val missingDays = (calendarDays - groupedByDate.size).coerceAtLeast(0)
                repeat(missingDays) {
                    dailyDurations.add(0L)
                    dailySessions.add(0f)
                }

                val dataPointCount = calendarDays
                val avgDuration = dailyDurations.average()
                val stdDuration = sqrt(dailyDurations.map { (it - avgDuration) * (it - avgDuration) }.average()).toLong()

                val avgSess = dailySessions.average()
                val stdSess = sqrt(dailySessions.map { (it - avgSess) * (it - avgSess) }.average()).toFloat()

                // Average category times per day
                val avgCategories = categoryTotals.mapValues { it.value / dataPointCount }

                val binParts = bin.split('_', limit = 2)
                val matchingSnapshots =
                    snapshots.filter { snapshot ->
                        val cal = Calendar.getInstance().apply { timeInMillis = snapshot.timestamp }
                        val day = cal.get(Calendar.DAY_OF_WEEK)
                        val dayType = if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) "WEEKEND" else "WEEKDAY"
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        val timeSlot =
                            when (hour) {
                                in 6..11 -> "MORNING"
                                in 12..16 -> "AFTERNOON"
                                in 17..21 -> "EVENING"
                                else -> "NIGHT"
                            }
                        dayType == binParts[0] && timeSlot == binParts[1]
                    }
                val validLightSnaps = matchingSnapshots.filter { it.lightLux >= 0f }
                val averageLight = if (validLightSnaps.isNotEmpty()) validLightSnaps.map { it.lightLux }.average().toFloat() else -1f

                val validEnergySnaps = matchingSnapshots.filter { it.accelEnergy >= 0f }
                val averageEnergy = if (validEnergySnaps.isNotEmpty()) validEnergySnaps.map { it.accelEnergy }.average().toFloat() else -1f

                val averageUnlocks = matchingSnapshots.map { it.unlockCount.toFloat() }.average().takeIf { it.isFinite() }?.toFloat() ?: 0f

                val newBaseline =
                    BaselineEntity(
                        timeBin = bin,
                        avgScreenTimeMs = avgDuration.toLong(),
                        stdScreenTimeMs = stdDuration,
                        avgSessionCount = avgSess.toFloat(),
                        stdSessionCount = stdSess,
                        avgUnlockCount = averageUnlocks,
                        typicalCategoriesJson = gson.toJson(avgCategories),
                        avgAccelEnergy = averageEnergy,
                        avgLightLux = averageLight,
                        updatedAt = System.currentTimeMillis(),
                        dataPointCount = dataPointCount,
                    )

                // Recompute from the complete local history. Blending this value into
                // the previous result on every app launch caused the baseline to drift.
                newBaselines.add(newBaseline)
            }
            return newBaselines
        }

        private fun countDaysOfType(
            startMs: Long,
            endMs: Long,
            dayType: String,
        ): Int {
            val cal = Calendar.getInstance()
            cal.timeInMillis = startMs
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            var count = 0
            while (cal.timeInMillis <= endMs) {
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
                val currentDayType = if (isWeekend) "WEEKEND" else "WEEKDAY"
                if (currentDayType == dayType) {
                    count++
                }
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return count.coerceAtLeast(1)
        }

        fun hasEnoughData(allUsage: List<AppUsageEntity>): Boolean {
            return getDaysOfData(allUsage) >= 5
        }

        fun getDaysOfData(allUsage: List<AppUsageEntity>): Int {
            val validUsage = allUsage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            val days = mutableSetOf<String>()
            val cal = Calendar.getInstance()
            validUsage.forEach {
                cal.timeInMillis = it.startTime
                days.add("${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}")
            }
            return days.size
        }
    }
