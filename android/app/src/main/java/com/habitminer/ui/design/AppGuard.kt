@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habitminer.ui.theme.LocalDataColors

/**
 * Where an app stands with your goals, and how to change it from anywhere an insight or a
 * routine mentions the app.
 */
@Immutable
data class AppGuards(
    /** Apps on your Use less list. */
    val useLess: Set<String>,
    /** The mindful pause is switched on in HabitMiner. */
    val pauseEnabled: Boolean,
    /** Its accessibility service is on in Android's settings (needed for it to work). */
    val serviceOn: Boolean,
    /** Apps paused only at night, from this minute of the day. */
    val pauseFrom: Map<String, Int> = emptyMap(),
    /**
     * Put the app on Use less; with [pause], also switch the mindful pause on, and with a
     * night start (minute of the day) pause it only from then until 05:00.
     */
    val onGuard: (packageName: String, pause: Boolean, nightFrom: Int?) -> Unit,
    val onOpenGoals: () -> Unit,
) {
    val pauseWorks: Boolean get() = pauseEnabled && serviceOn
}

/** Provided once at the top of the app; null in previews, where the buttons don't show. */
val LocalAppGuards = staticCompositionLocalOf<AppGuards?> { null }

/**
 * One-tap actions for an app you'd like to use less: "Pause before opening" or "Use less", and
 * once it's on your list, a quiet line saying so instead of the same buttons again.
 */
@Composable
fun GuardActions(
    packageName: String?,
    appName: String,
    modifier: Modifier = Modifier,
    /** Suggest pausing only at night, from this minute of the day. */
    nightFrom: Int? = null,
) {
    val guards = LocalAppGuards.current ?: return
    if (packageName == null) return
    val hue = LocalDataColors.current.pickupsHue
    val listed = packageName in guards.useLess
    if (!listed) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nightFrom != null) {
                HueTonalButton("Pause after ${com.habitminer.analytics.Format.clockFromMinutes(nightFrom)}", hue, { guards.onGuard(packageName, true, nightFrom) })
                TextButton(onClick = { guards.onGuard(packageName, true, null) }) { Text("Always", color = hue.text) }
            } else {
                HueTonalButton("Pause before $appName", hue, { guards.onGuard(packageName, true, null) })
                TextButton(onClick = { guards.onGuard(packageName, false, null) }) { Text("Use less", color = hue.text) }
            }
        }
        return
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalDataColors.current.goodHue.text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                guards.pauseWorks -> guards.pauseFrom[packageName]?.let { "Pause on for $appName after ${com.habitminer.analytics.Format.clockFromMinutes(it)}" } ?: "Pause on before $appName"
                guards.pauseEnabled -> "On Use less; the pause needs Accessibility"
                else -> "On your Use less list"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (guards.pauseWorks) {
            TextButton(onClick = guards.onOpenGoals) { Text("Edit", color = hue.text) }
        } else if (guards.pauseEnabled) {
            TextButton(onClick = { guards.onGuard(packageName, true, guards.pauseFrom[packageName]) }) { Text("Turn on", color = hue.text) }
        } else {
            TextButton(onClick = { guards.onGuard(packageName, true, nightFrom) }) { Text("Add a pause", color = hue.text) }
        }
    }
}
