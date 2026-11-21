@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui.design

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habitminer.analytics.Pattern
import com.habitminer.analytics.PatternAction
import com.habitminer.analytics.PatternKind
import com.habitminer.ui.theme.Hue
import com.habitminer.ui.theme.LocalDataColors

/** A short label and colour for each kind of pattern. */
@Composable
fun patternLook(p: Pattern): Triple<String, Hue, ImageVector> {
    val c = LocalDataColors.current
    return when (p.kind) {
        PatternKind.LONG_SESSIONS -> Triple("Where the time goes", c.screenHue, Icons.Rounded.HourglassBottom)
        PatternKind.LATE_WEEKDAY_SLEEP -> Triple("Sleep", c.sleepHue, Icons.Rounded.Bedtime)
        PatternKind.WEEK_CHANGE, PatternKind.APP_WEEK_CHANGE ->
            Triple("This week", if (p.good) c.goodHue else c.screenHue, if (p.good) Icons.Rounded.TrendingDown else Icons.Rounded.TrendingUp)
        PatternKind.NOTIFICATION_PULL -> Triple("Notifications", c.notificationsHue, Icons.Rounded.NotificationsActive)
        PatternKind.LEAD_IN -> Triple("How you get there", c.freeHue, Icons.Rounded.CallSplit)
        PatternKind.BUSY_HOURS -> Triple("Your busiest hours", c.nowHue, Icons.Rounded.Schedule)
        PatternKind.CHECKING_PEAK -> Triple("Checking", c.pickupsHue, Icons.Rounded.TouchApp)
        PatternKind.MORNING_APP -> Triple("Mornings", c.stepsHue, Icons.Rounded.WbSunny)
        PatternKind.LATE_RECORD -> Triple("Last night", c.sleepHue, Icons.Rounded.Bedtime)
        PatternKind.LATE_FREE_RUN -> Triple("A good run", c.goodHue, Icons.Rounded.EmojiEvents)
    }
}

/**
 * One pattern: what it is, the numbers behind it, and the one thing you can do about it right
 * there (a pause, the app's notification settings, or the Sleep screen).
 */
@Composable
fun PatternCard(
    pattern: Pattern,
    modifier: Modifier = Modifier,
    onOpenSleep: () -> Unit = {},
    /** Raised (Today) or in a list (Trends). */
    raised: Boolean = false,
) {
    val (label, hue, icon) = patternLook(pattern)
    val context = LocalContext.current
    val body: @Composable () -> Unit = {
        Kicker(label, hue, icon)
        Spacer(Modifier.height(6.dp))
        Text(pattern.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(pattern.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (pattern.action) {
            PatternAction.PAUSE_APP, PatternAction.PAUSE_APP_AT_NIGHT ->
                if (pattern.packageName != null && LocalAppGuards.current != null) {
                    Spacer(Modifier.height(10.dp))
                    GuardActions(
                        pattern.packageName,
                        pattern.appName ?: pattern.packageName,
                        nightFrom = pattern.nightFromMinute.takeIf { pattern.action == PatternAction.PAUSE_APP_AT_NIGHT },
                    )
                }
            PatternAction.APP_NOTIFICATIONS ->
                pattern.packageName?.let { pkg ->
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                    ) { Text("Change ${pattern.appName ?: "its"} notifications", color = hue.text) }
                }
            PatternAction.OPEN_SLEEP -> {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOpenSleep, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
                    Text("See your nights", color = hue.text)
                }
            }
            PatternAction.NONE -> Unit
        }
    }
    if (raised) {
        HeroContainer(modifier = modifier, accent = hue.start) { body() }
    } else {
        Column(
            modifier =
                modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
        ) { body() }
    }
}
