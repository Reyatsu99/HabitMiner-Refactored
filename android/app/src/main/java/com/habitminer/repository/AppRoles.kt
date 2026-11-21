package com.habitminer.repository

import android.content.Context
import com.habitminer.analytics.AppCategory
import com.habitminer.analytics.UsageSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What an app is for you, when its usual category is wrong for how you use it. */
enum class AppRole(val label: String) {
    LEISURE("Leisure"),
    WORK("Work or study"),
}

/**
 * Your choice of role for dual-purpose apps (YouTube for lectures, a browser for work). Work
 * apps are left out of long-session, late-night and "did it just move?" patterns; leisure ones
 * are counted in them even if their category says otherwise.
 */
@Singleton
class AppRoles
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("app_roles", Context.MODE_PRIVATE)
        private val _roles = MutableStateFlow(read())
        val roles: StateFlow<Map<String, AppRole>> = _roles.asStateFlow()

        /** Null goes back to the app's own category. */
        fun set(
            packageName: String,
            role: AppRole?,
        ) {
            val next = if (role == null) _roles.value - packageName else _roles.value + (packageName to role)
            prefs.edit().putString(KEY, next.entries.joinToString("\n") { "${it.key}=${it.value.name}" }).apply()
            _roles.value = next
        }

        /** The sessions with your roles applied to their categories. */
        fun apply(sessions: List<UsageSession>): List<UsageSession> {
            val r = _roles.value
            if (r.isEmpty()) return sessions
            return sessions.map { s ->
                when (r[s.packageName]) {
                    AppRole.WORK -> s.copy(category = AppCategory.PRODUCTIVITY)
                    AppRole.LEISURE -> if (s.category.isLeisure) s else s.copy(category = AppCategory.VIDEO)
                    null -> s
                }
            }
        }

        private fun read(): Map<String, AppRole> =
            prefs.getString(KEY, "").orEmpty().split('\n').mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else runCatching { line.substring(0, i) to AppRole.valueOf(line.substring(i + 1)) }.getOrNull()
            }.toMap()

        companion object {
            private const val KEY = "roles"
        }
    }
