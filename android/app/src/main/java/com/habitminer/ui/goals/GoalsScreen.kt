@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.habitminer.ui.goals

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.habitminer.analytics.AppSummary
import com.habitminer.analytics.Format
import com.habitminer.goals.Goals
import com.habitminer.ui.design.AppIcon
import com.habitminer.ui.design.HeroContainer
import com.habitminer.ui.design.ListRow
import com.habitminer.ui.design.Note
import com.habitminer.ui.design.RowGroup
import com.habitminer.ui.design.ScreenPadding
import com.habitminer.ui.design.ScreenTitle
import com.habitminer.ui.design.SectionHeader
import com.habitminer.ui.design.rememberConfirmHaptic
import com.habitminer.ui.theme.LocalDataColors
import com.habitminer.analytics.GoalDay
import com.habitminer.analytics.GoalWeek
import java.time.LocalDate

@Composable
fun GoalsScreen(
    goals: Goals,
    apps: List<AppSummary>,
    dailyTotals: List<Pair<LocalDate, Long>>,
    todayMs: Long,
    onChange: ((Goals) -> Goals) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    /** How each Use less app is going since it was added. */
    impacts: Map<String, com.habitminer.analytics.GoalImpact> = emptyMap(),
) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) } // "less" or "more"
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val haptic = rememberConfirmHaptic()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "title") { ScreenTitle("Goals", subtitle = "Soft targets. Nothing is blocked.") }

        item(key = "intention") {
            Spacer(Modifier.height(8.dp))
            IntentionPanel(goals.intentionFor(today)) { text ->
                haptic()
                onChange { it.copy(intention = text?.trim()?.takeIf { t -> t.isNotEmpty() }, intentionDate = today) }
            }
        }

        item(key = "target") {
            val hues = LocalDataColors.current
            SectionHeader(
                "Daily phone time",
                info = "A target for your whole day. The week below shows the days you stayed under it.",
                icon = Icons.Rounded.Timer,
                iconColor = hues.screenHue.start,
            )
            val options = listOf<Int?>(null, 120, 180, 240, 300)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                options.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = goals.dailyTargetMinutes == m,
                        onClick = { onChange { it.copy(dailyTargetMinutes = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = com.habitminer.ui.design.hueSegmentedColors(hues.screenHue),
                    ) { Text(m?.let { "${it / 60}h" } ?: "None") }
                }
            }
            goals.dailyTargetMinutes?.let { target -> TargetProgress(target, todayMs, dailyTotals, today) }
        }

        item(key = "less") {
            SectionHeader("Use less", actionLabel = "Choose", onAction = { picking = "less" }, icon = Icons.Rounded.TrendingDown, iconColor = LocalDataColors.current.screenHue.start)
            if (goals.useLess.isEmpty()) {
                Note("Pick apps you'd like to spend less time in. You'll see how this week compares with last week.")
            } else {
                AppGoalRows(
                    goals.useLess,
                    byPackage,
                    less = true,
                    today = today,
                    impacts = impacts,
                    nightOnly = goals.pauseFrom.keys,
                    onOpenApp = onOpenApp,
                    goals = goals,
                    onChange = onChange,
                )
            }
        }

        item(key = "reminder") {
            val remindHue = LocalDataColors.current.stepsHue
            SectionHeader(
                "Remind me",
                icon = Icons.Rounded.NotificationsActive,
                iconColor = remindHue.start,
                info = "A quiet notification after this long straight in a use-less app. It counts towards the three-a-day limit, so it never piles up.",
            )
            val options = listOf(0, 15, 30, 45)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding)) {
                options.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = goals.reminderMinutes == m,
                        onClick = { onChange { it.copy(reminderMinutes = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        enabled = goals.useLess.isNotEmpty() || m == 0,
                        colors = com.habitminer.ui.design.hueSegmentedColors(remindHue),
                    ) { Text(if (m == 0) "Off" else "$m min") }
                }
            }
        }

        item(key = "more") {
            SectionHeader("Use more", actionLabel = "Choose", onAction = { picking = "more" }, icon = Icons.Rounded.TrendingUp, iconColor = LocalDataColors.current.pickupsHue.start)
            if (goals.useMore.isEmpty()) {
                Note("Pick apps you'd like to use more, like a language course or a reading app.")
            } else {
                AppGoalRows(goals.useMore, byPackage, less = false, today = today, onOpenApp = onOpenApp)
            }
        }
    }

    picking?.let { which ->
        val selected = if (which == "less") goals.useLess else goals.useMore
        AppPicker(
            title = if (which == "less") "Apps to use less" else "Apps to use more",
            apps = apps,
            selected = selected.toSet(),
            onDone = { chosen ->
                onChange { g -> if (which == "less") g.copy(useLess = chosen.toList(), useMore = g.useMore - chosen) else g.copy(useMore = chosen.toList(), useLess = g.useLess - chosen) }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun IntentionPanel(
    intention: String?,
    onSave: (String?) -> Unit,
) {
    var editing by rememberSaveable(intention) { mutableStateOf(intention == null) }
    var text by rememberSaveable(intention) { mutableStateOf(intention.orEmpty()) }
    val hue = LocalDataColors.current.pickupsHue
    HeroContainer(accent = hue.start) {
        com.habitminer.ui.design.Kicker("Today's intention", hue, Icons.Rounded.Flag)
        Spacer(Modifier.height(8.dp))
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                placeholder = { Text("Finish the lab report") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(onDone = {
                        onSave(text)
                        editing = false
                    }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            com.habitminer.ui.design.HueButton("Set for today", hue, {
                onSave(text)
                editing = false
            }, enabled = text.isNotBlank())
        } else {
            Text(intention.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { editing = true }) { Text("Change", color = hue.text) }
                TextButton(onClick = { onSave(null) }) { Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun TargetProgress(
    targetMinutes: Int,
    todayMs: Long,
    daily: List<Pair<LocalDate, Long>>,
    today: LocalDate,
) {
    val colors = LocalDataColors.current
    val targetMs = targetMinutes * 60_000L
    val days = remember(daily, todayMs, targetMs) { GoalWeek.underTarget(daily.filter { it.first != today } + (today to todayMs), today, targetMs) }
    val (met, withData) = GoalWeek.count(days)
    val left = targetMs - todayMs
    Row(modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        com.habitminer.ui.design.ActivityRings(
            listOf(com.habitminer.ui.design.RingSpec(todayMs.toFloat() / targetMs, colors.screenHue)),
            size = 76.dp,
            stroke = 13.dp,
            description = "${Format.duration(todayMs)} of ${Format.duration(targetMs)} today",
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (left >= 0) "On track today, ${Format.duration(left)} left" else "${Format.duration(-left)} over today",
                style = MaterialTheme.typography.titleMedium,
                color = if (left >= 0) colors.screenHue.text else colors.alert,
            )
            Spacer(Modifier.height(8.dp))
            GoalDots(days, labels = true)
            Spacer(Modifier.height(4.dp))
            Text(
                "Under target on $met of the last ${if (withData < 7) "$withData day${if (withData == 1) "" else "s"} with data" else "7 days"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Seven dots, oldest first: green on a day that went your way, pink when it didn't, grey
 * without data. Today is a ring, still in progress.
 */
@Composable
private fun GoalDots(
    days: List<GoalDay>,
    labels: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDataColors.current
    val dot = if (labels) 16.dp else 10.dp
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(if (labels) 6.dp else 5.dp)) {
        days.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val c =
                    when (d.met) {
                        null -> colors.track
                        true -> colors.goodHue.start
                        false -> colors.screenHue.start
                    }
                val shape = androidx.compose.foundation.shape.CircleShape
                if (d.today) {
                    Box(Modifier.size(dot).border(2.dp, c, shape))
                } else {
                    Box(Modifier.size(dot).clip(shape).background(c))
                }
                if (labels) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppGoalRows(
    packages: List<String>,
    byPackage: Map<String, AppSummary>,
    less: Boolean,
    today: LocalDate,
    onOpenApp: (String) -> Unit,
    impacts: Map<String, com.habitminer.analytics.GoalImpact> = emptyMap(),
    /** Apps paused only at night. */
    nightOnly: Set<String> = emptySet(),
    goals: Goals? = null,
    onChange: ((Goals) -> Goals) -> Unit = {},
) {
    RowGroup {
        packages.forEach { pkg ->
            val a = byPackage[pkg]
            row {
                val week = a?.weekMs ?: 0L
                val previous = a?.let { prevWeekMs(it) } ?: 0L
                val days = remember(a) { a?.let { GoalWeek.againstLastWeek(it.last14, today, more = !less) }.orEmpty() }
                val (met, withData) = GoalWeek.count(days)
                val verdict =
                    when {
                        a == null -> "Not used in the last five weeks"
                        previous == 0L || days.isEmpty() -> "${Format.duration(week / 7)} a day this week"
                        less -> "Under last week's average on $met of the last $withData days"
                        else -> "At or above last week's average on $met of the last $withData days"
                    }
                Column {
                    ListRow(
                        a?.appName ?: pkg.substringAfterLast('.'),
                        supporting = verdict,
                        leading = { AppIcon(pkg, a?.appName ?: pkg, size = 36.dp) },
                        trailingText = Format.duration(week),
                        onClick = { onOpenApp(pkg) },
                    )
                    if (days.isNotEmpty()) {
                        GoalDots(days, labels = false, modifier = Modifier.padding(start = 68.dp, bottom = 12.dp))
                    }
                    goals?.pauseFrom?.get(pkg)?.let { from ->
                        val weeknights = pkg in goals.weeknightsOnly
                        Row(modifier = Modifier.padding(start = 68.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Paused after ${Format.clockFromMinutes(from)}, ${if (weeknights) "Sun to Thu nights" else "every night"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onChange { g -> g.copy(weeknightsOnly = if (weeknights) g.weeknightsOnly - pkg else g.weeknightsOnly + pkg) }
                            }) { Text(if (weeknights) "Every night" else "Weeknights only", color = LocalDataColors.current.pickupsHue.text) }
                        }
                    }
                    goals?.reasons?.get(pkg)?.let { why ->
                        Text(
                            "Why: $why",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 10.dp),
                        )
                    }
                    impacts[pkg]?.let { im ->
                        val good = !im.tooEarly && im.afterPerDayMs != null && im.beforePerDayMs != null && im.afterPerDayMs < im.beforePerDayMs
                        Row(modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Rounded.Insights,
                                contentDescription = null,
                                tint = if (good) LocalDataColors.current.goodHue.text else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Since ${java.time.Instant.ofEpochMilli(im.since).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))}: " +
                                    im.summary(a?.appName ?: "it").replaceFirstChar { it.lowercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        im.suggestion(pkg in nightOnly)?.let { tip ->
                            Column(modifier = Modifier.padding(start = 90.dp, end = 16.dp, bottom = 10.dp)) {
                                Text(tip, style = MaterialTheme.typography.bodySmall, color = LocalDataColors.current.stepsHue.text)
                                val guards = com.habitminer.ui.design.LocalAppGuards.current
                                if (pkg !in nightOnly && guards != null) {
                                    TextButton(
                                        onClick = { guards.onGuard(com.habitminer.ui.design.GuardRequest(pkg, true, 23 * 60, weeknightsOnly = true)) },
                                        contentPadding = PaddingValues(horizontal = 0.dp),
                                    ) { Text("Pause only after 23:00 on weeknights", color = LocalDataColors.current.stepsHue.text) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Minutes in the 7 days before this week, from the 14-day series. */
private fun prevWeekMs(a: AppSummary): Long = (a.last14.take(7).sum() * 60_000f).toLong()

@Composable
private fun AppPicker(
    title: String,
    apps: List<AppSummary>,
    selected: Set<String>,
    onDone: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { onDone(chosen) }) { Text("Done") }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().height(480.dp)) {
                items(apps.take(60), key = { it.packageName }) { a ->
                    ListRow(
                        a.appName,
                        supporting = "${Format.duration(a.weekMs)} this week",
                        leading = { AppIcon(a.packageName, a.appName, size = 36.dp) },
                        trailing = {
                            Checkbox(checked = a.packageName in chosen, onCheckedChange = { on -> chosen = if (on) chosen + a.packageName else chosen - a.packageName })
                        },
                        onClick = { chosen = if (a.packageName in chosen) chosen - a.packageName else chosen + a.packageName },
                    )
                }
            }
        }
    }
}
