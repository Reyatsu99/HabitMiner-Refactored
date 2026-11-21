package com.habitminer.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Patterns you put away: "hidden" (not useful) or "intentional" (you know, and it's fine).
 * Either way they stop showing, until you bring them back from Settings.
 */
@Singleton
class PatternPrefs
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("patterns", Context.MODE_PRIVATE)
        private val _hidden = MutableStateFlow(read())

        /** Pattern key to reason ("hidden" or "intentional"). */
        val hidden: StateFlow<Map<String, String>> = _hidden.asStateFlow()

        fun hide(
            key: String,
            reason: String,
        ) = write(_hidden.value + (key to reason))

        fun showAll() = write(emptyMap())

        private fun write(m: Map<String, String>) {
            prefs.edit().putString(KEY, m.entries.joinToString("\n") { "${it.value}\t${it.key}" }).apply()
            _hidden.value = m
        }

        private fun read(): Map<String, String> =
            prefs.getString(KEY, "").orEmpty().split('\n').mapNotNull { line ->
                val i = line.indexOf('\t')
                if (i <= 0) null else line.substring(i + 1) to line.substring(0, i)
            }.toMap()

        companion object {
            const val HIDDEN = "hidden"
            const val INTENTIONAL = "intentional"
            private const val KEY = "hidden"
        }
    }
