package com.habitminer.ui

import com.habitminer.analytics.CategoryMapper
import com.habitminer.analytics.Format
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.ContextSnapshotEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-language labels shared by the screens. */
object Labels {
    fun deviationTitle(type: String): String =
        when (type) {
            "EXCESS_DURATION" -> "More phone time than usual"
            "NEW_BEHAVIOR" -> "New for this time of day"
            "MISSING_ROUTINE" -> "Quieter than usual"
            "CONTEXT_SHIFT" -> "Different surroundings"
            else -> type.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        }

    fun dayTime(ms: Long): String = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(ms))

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    fun shortDate(ms: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))

    /** "Dim", "Still", "Charging" … for one snapshot; null parts are omitted. */
    fun contextSummary(s: ContextSnapshotEntity): String {
        val parts = mutableListOf<String>()
        if (s.lightLux >= 0f) {
            parts +=
                when {
                    s.lightLux <= 10f -> "Dark"
                    s.lightLux <= 100f -> "Dim"
                    else -> "Bright"
                } + " (${s.lightLux.toInt()} lux)"
        }
        if (s.accelVariance >= 0f) {
            parts +=
                when {
                    s.accelVariance < 0.5f -> "Still"
                    s.accelVariance < 2f -> "Moving"
                    else -> "Very active"
                }
        }
        if (parts.isEmpty()) parts += if (s.isScreenOn) "No sensor reading" else "Screen off"
        if (s.batteryLevel in 0..100) parts += "${s.batteryLevel}% battery" + if (s.isCharging) ", charging" else ""
        return parts.joinToString(" · ")
    }

    fun age(ms: Long): String = Format.ago(System.currentTimeMillis() - ms)

    /** Category of an app usage row, using the improved mapper. */
    fun category(u: AppUsageEntity) = CategoryMapper.categorize(u.packageName, u.appName, u.appCategory)
}
