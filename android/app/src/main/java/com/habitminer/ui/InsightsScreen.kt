@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitminer.analytics.DayTypes
import com.habitminer.analytics.Format
import com.habitminer.analytics.PredictabilityResult
import com.habitminer.analytics.SleepEstimate
import com.habitminer.analytics.WeekComparison
import com.habitminer.engine.AnalyticsMappers
import com.habitminer.engine.HabitUiState
import com.habitminer.engine.HabitViewModel
import com.habitminer.ui.components.BodyText
import com.habitminer.ui.components.CardHeader
import com.habitminer.ui.components.Hint
import com.habitminer.ui.components.InfoCard
import com.habitminer.ui.components.InfoRow
import com.habitminer.ui.components.LoadingState
import com.habitminer.ui.components.ScoreBar
import com.habitminer.ui.components.StatBlock
import com.habitminer.ui.components.SurfaceCard
import com.habitminer.ui.components.TypicalDayChart
import com.habitminer.ui.components.WeekHeatmap
import com.habitminer.ui.theme.StatusSuccess
import com.habitminer.ui.theme.StatusWarning
import java.time.format.TextStyle
import java.util.Locale

private val dayTypeColors = listOf(Color(0xFF4FC3F7), Color(0xFFAB47BC), Color(0xFFFFB74D))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    state: HabitUiState,
    viewModel: HabitViewModel,
    initialTab: Int = 0,
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("Routines", "Deviations", "Blueprint")

    if (state.insights == null && state.discoveredHabits.isEmpty() && state.recentDeviations.isEmpty()) {
        LoadingState(message = "Analysing your data…\nThis takes a few seconds the first time.")
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }
            when (selectedTab) {
                0 -> routinesTab(state)
                1 -> deviationsTab(state, viewModel)
                else -> blueprintTab(state)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Routines
// ---------------------------------------------------------------------------------------

private fun LazyListScope.routinesTab(state: HabitUiState) {
    item { PredictabilityCard(state.insights?.predictability) }

    val groups = state.insights?.patternGroups.orEmpty()
    item {
        Column {
            Text(
                text = "Your routines",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Hint("App sequences you repeat at the same time of day, most reliable first.")
        }
    }
    if (groups.isEmpty()) {
        item { InfoCard(title = "No strong routines yet", message = "Routines appear once the same app sequence shows up on at least 5 days.") }
    } else {
        items(groups, key = { it.sequence }) { g ->
            SurfaceCard {
                Text(
                    text = g.sequence,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BodyText(g.whenText)
                Hint("${g.evidenceText} · last seen ${Labels.shortDate(g.lastSeen)}")
            }
        }
    }
}

@Composable
private fun PredictabilityCard(result: PredictabilityResult?) {
    SurfaceCard {
        CardHeader("How predictable is your next app?", Icons.Default.Speed)
        Spacer(modifier = Modifier.height(10.dp))
        if (result == null) {
            Hint("Needs a few more days of app switches to measure. We test our guesses on your most recent 3 days.")
            return@SurfaceCard
        }
        ScoreBar("HabitMiner's guess was right", result.hitRate, MaterialTheme.colorScheme.primary, emphasized = true)
        Spacer(modifier = Modifier.height(10.dp))
        ScoreBar("Right within its top 3 guesses", result.top3HitRate, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.height(10.dp))
        ScoreBar("Always guessing your most-used app", result.mostUsedBaseline, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        Spacer(modifier = Modifier.height(10.dp))
        ScoreBar("Random guess", result.randomBaseline, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(10.dp))
        val lift = result.hitRate - result.mostUsedBaseline
        val verdict =
            when {
                lift >= 0.1f -> "Your app switching follows clear routines."
                lift > 0.02f -> "Your app switching is somewhat routine."
                else -> "Your app switching varies a lot. That's normal, not good or bad."
            }
        BodyText(verdict)
        Hint("Tested on ${result.testedTransitions} app switches from the last ${result.testDays} days, using a model trained on the days before.")
    }
}

// ---------------------------------------------------------------------------------------
// Deviations
// ---------------------------------------------------------------------------------------

private fun LazyListScope.deviationsTab(
    state: HabitUiState,
    viewModel: HabitViewModel,
) {
    item {
        Column {
            Text(
                text = "Unusual moments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Hint("Times your phone use differed from your usual pattern. Tell us whether they were expected; your answers are saved as labels for evaluation.")
        }
    }
    if (state.recentDeviations.isEmpty()) {
        item { InfoCard(title = "Nothing unusual", message = "No recent differences from your usual routine.") }
    } else {
        items(state.recentDeviations, key = { it.id }) { dev ->
            DeviationCard(dev, state.deviationFeedback[AnalyticsMappers.fingerprint(dev)]) { value ->
                viewModel.giveDeviationFeedback(dev, value)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Blueprint
// ---------------------------------------------------------------------------------------

private fun LazyListScope.blueprintTab(state: HabitUiState) {
    val insights = state.insights
    if (insights == null) {
        item { InfoCard(title = "Analysing…", message = "Your blueprint appears in a few seconds.") }
        return
    }
    item {
        Column {
            Text(
                text = "Your behavioural blueprint",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Hint("How your phone use is shaped across days, hours and surroundings.")
        }
    }
    insights.typicalDay?.let { curve ->
        item {
            SurfaceCard {
                CardHeader("Today vs a usual day", Icons.Default.ShowChart, trailing = "based on ${curve.selection.description}")
                Spacer(modifier = Modifier.height(10.dp))
                TypicalDayChart(curve)
            }
        }
    }
    insights.heatmap?.takeIf { it.maxMinutes > 0 }?.let { map ->
        item {
            SurfaceCard {
                CardHeader("When you use your phone", Icons.Default.GridView, trailing = "last 7 days")
                Spacer(modifier = Modifier.height(10.dp))
                WeekHeatmap(map)
            }
        }
    }
    item { DayTypesCard(insights.dayTypes) }
    insights.week?.let { item { WeekCompareCard(it) } }
    if (insights.sleepNights.isNotEmpty()) item { SleepWeekCard(insights.sleepNights) }
    if (insights.contextInsights.isNotEmpty()) {
        items(insights.contextInsights, key = { it.headline }) { ContextInsightCard(it) }
    }
    if (insights.placeUsage.isNotEmpty()) {
        item {
            SurfaceCard {
                CardHeader("Where you use your phone", Icons.Default.Place, trailing = "this week")
                Spacer(modifier = Modifier.height(8.dp))
                insights.placeUsage.take(5).forEach { p ->
                    InfoRow(p.place, "${Format.duration(p.perDayMs)}/day" + (p.topApp?.let { " · $it" } ?: ""))
                }
                Hint("From the Wi-Fi network you're connected to. Rename places in Settings.")
            }
        }
    }
    item {
        SurfaceCard {
            CardHeader("Your check-ins", Icons.Default.QuestionAnswer)
            Spacer(modifier = Modifier.height(6.dp))
            BodyText(
                if (state.checkInCount == 0) {
                    "No check-ins answered yet. They show up as notifications up to 3 times a day."
                } else {
                    "You've answered ${state.checkInCount} check-ins. Together with your deviation answers that's ${state.labelCount} labels, included in Export."
                },
            )
        }
    }
}

@Composable
private fun DayTypesCard(dayTypes: DayTypes?) {
    SurfaceCard {
        CardHeader("Your kinds of days", Icons.Default.CalendarMonth)
        Spacer(modifier = Modifier.height(10.dp))
        if (dayTypes == null) {
            Hint("Needs at least 6 full days of data to group your days.")
            return@SurfaceCard
        }
        dayTypes.types.forEachIndexed { i, type ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(dayTypeColors[i % dayTypeColors.size]))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(type.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Hint("${type.description} · ${type.days.size} days")
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Strip of recent days coloured by type.
        val recent = dayTypes.dayToType.keys.sorted().takeLast(14)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            recent.forEach { date ->
                val idx = dayTypes.dayToType[date] ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(dayTypeColors[idx % dayTypeColors.size]),
                    )
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Hint("Days are grouped by when and how much you used your phone (k-means clustering).")
    }
}

@Composable
private fun WeekCompareCard(week: WeekComparison) {
    SurfaceCard {
        CardHeader("This week vs last week", Icons.Default.CompareArrows)
        Spacer(modifier = Modifier.height(10.dp))
        val delta = week.deltaMs
        val deltaText =
            when {
                kotlin.math.abs(delta) < 5 * 60_000L -> "About the same"
                delta > 0 -> "${Format.duration(delta)} more a day"
                else -> "${Format.duration(-delta)} less a day"
            }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatBlock("Last 7 days", "${Format.duration(week.currentPerDayMs)}/day")
            StatBlock(
                "Change",
                deltaText,
                alignEnd = true,
                valueColor = if (delta > 5 * 60_000L) StatusWarning else if (delta < -5 * 60_000L) StatusSuccess else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (week.biggestMovers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Hint("Biggest changes per day")
            week.biggestMovers.forEach { c ->
                val sign = if (c.deltaMs > 0) "+" else "−"
                InfoRow(c.name, "$sign${Format.duration(kotlin.math.abs(c.deltaMs))}  (${Format.duration(c.currentPerDayMs)}/day)")
            }
        }
        Hint("Averages over days with data: ${week.currentDays} this week, ${week.previousDays} the week before.")
    }
}

@Composable
private fun SleepWeekCard(nights: List<SleepEstimate>) {
    val zone = java.time.ZoneId.systemDefault()
    var expanded by remember { mutableStateOf(false) }
    SurfaceCard {
        CardHeader("Sleep (estimated)", Icons.Default.Bedtime, tint = Color(0xFF7986CB))
        Spacer(modifier = Modifier.height(8.dp))
        val shown = if (expanded) nights.reversed() else nights.reversed().take(3)
        shown.forEach { n ->
            val day = n.wakeDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            InfoRow(
                "$day  ${Format.clock(n.sleepStart, zone)} → ${Format.clock(n.wakeTime, zone)}",
                Format.duration(n.durationMs) + if (n.confidence.name == "LOW") " (unsure)" else "",
            )
        }
        if (nights.size > 3) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show less" else "Show all ${nights.size} nights") }
        }
        Hint("From the longest overnight stretch with your screen off. Charging and darkness raise confidence.")
    }
}
