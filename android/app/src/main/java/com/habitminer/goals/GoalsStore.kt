package com.habitminer.goals

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What you want from your phone use. Soft targets: nothing is ever blocked. */
data class Goals(
    /** A daily phone-time target in minutes, or null for none. */
    val dailyTargetMinutes: Int? = null,
    /** Apps you want to use less (package names). */
    val useLess: List<String> = emptyList(),
    /** Apps you want to use more. */
    val useMore: List<String> = emptyList(),
    /** Remind after this many minutes straight in a use-less app; 0 = off. */
    val reminderMinutes: Int = 0,
    /** Today's intention, a short sentence, and the day it was set. */
    val intention: String? = null,
    val intentionDate: LocalDate? = null,
    /** When each Use less app was added, to show whether it's working. */
    val since: Map<String, Long> = emptyMap(),
    /** Use less apps paused only at night: from this minute of the day until 05:00. */
    val pauseFrom: Map<String, Int> = emptyMap(),
) {
    fun intentionFor(day: LocalDate): String? = intention?.takeIf { intentionDate == day }
}

@Singleton
class GoalsStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("goals", Context.MODE_PRIVATE)
        private val _goals = MutableStateFlow(read())
        val goals: StateFlow<Goals> = _goals.asStateFlow()

        fun update(change: (Goals) -> Goals) {
            val changed = change(_goals.value)
            // Keep "since" for the apps on Use less: new ones start now, removed ones go.
            val now = System.currentTimeMillis()
            val next =
                changed.copy(
                    since = changed.useLess.associateWith { changed.since[it] ?: now },
                    pauseFrom = changed.pauseFrom.filterKeys { it in changed.useLess },
                )
            prefs.edit()
                .putInt(KEY_TARGET, next.dailyTargetMinutes ?: -1)
                .putString(KEY_LESS, next.useLess.joinToString(SEP))
                .putString(KEY_MORE, next.useMore.joinToString(SEP))
                .putInt(KEY_REMINDER, next.reminderMinutes)
                .putString(KEY_INTENTION, next.intention)
                .putString(KEY_INTENTION_DATE, next.intentionDate?.toString())
                .putString(KEY_SINCE, next.since.entries.joinToString(SEP) { "${it.key}=${it.value}" })
                .putString(KEY_PAUSE_FROM, next.pauseFrom.entries.joinToString(SEP) { "${it.key}=${it.value}" })
                .apply()
            _goals.value = next
        }

        private fun read(): Goals =
            Goals(
                dailyTargetMinutes = prefs.getInt(KEY_TARGET, -1).takeIf { it > 0 },
                useLess = prefs.getString(KEY_LESS, "").orEmpty().split(SEP).filter { it.isNotBlank() },
                useMore = prefs.getString(KEY_MORE, "").orEmpty().split(SEP).filter { it.isNotBlank() },
                reminderMinutes = prefs.getInt(KEY_REMINDER, 0),
                intention = prefs.getString(KEY_INTENTION, null),
                intentionDate = prefs.getString(KEY_INTENTION_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                since = pairs(KEY_SINCE) { it.toLongOrNull() },
                pauseFrom = pairs(KEY_PAUSE_FROM) { it.toIntOrNull() },
            )

        private fun <T> pairs(
            key: String,
            parse: (String) -> T?,
        ): Map<String, T> =
            prefs.getString(key, "").orEmpty().split(SEP).mapNotNull { line ->
                val i = line.lastIndexOf('=')
                if (i <= 0) null else parse(line.substring(i + 1))?.let { line.substring(0, i) to it }
            }.toMap()

        companion object {
            private const val SEP = "\n"
            private const val KEY_TARGET = "daily_target_minutes"
            private const val KEY_LESS = "use_less"
            private const val KEY_MORE = "use_more"
            private const val KEY_REMINDER = "reminder_minutes"
            private const val KEY_INTENTION = "intention"
            private const val KEY_INTENTION_DATE = "intention_date"
            private const val KEY_SINCE = "use_less_since"
            private const val KEY_PAUSE_FROM = "pause_from"
        }
    }
