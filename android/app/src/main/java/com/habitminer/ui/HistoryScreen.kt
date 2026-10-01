@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.engine.HabitUiState
import com.habitminer.engine.HabitViewModel
import com.habitminer.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class TimelineItemData {
    abstract val timestamp: Long

    data class Session(val usages: List<AppUsageEntity>) : TimelineItemData() {
        override val timestamp: Long = usages.last().endTime
    }

    data class Snapshot(val entity: ContextSnapshotEntity) : TimelineItemData() {
        override val timestamp: Long = entity.timestamp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: HabitUiState, viewModel: HabitViewModel) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Apps Only", "Context Events")

    val calendar = Calendar.getInstance()
    val todayStart = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val dateList = remember {
        val list = mutableListOf<Long>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = todayStart
        for (i in 0..14) {
            list.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        list.reversed()
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE dd", Locale.getDefault()) }

    val mergedTimeline = remember(state.historicalAppUsage, state.historicalSnapshots, selectedFilter) {
        val list = mutableListOf<TimelineItemData>()
        
        if (selectedFilter != "Context Events") {
            val sessions = mutableListOf<List<AppUsageEntity>>()
            var currentSession = mutableListOf<AppUsageEntity>()
            
            val sortedUsage = state.historicalAppUsage.sortedBy { it.startTime }
            for (usage in sortedUsage) {
                if (currentSession.isEmpty()) {
                    currentSession.add(usage)
                } else {
                    val gap = usage.startTime - currentSession.last().endTime
                    if (gap <= 5 * 60 * 1000L) { // 5 mins
                        currentSession.add(usage)
                    } else {
                        sessions.add(currentSession.toList())
                        currentSession = mutableListOf(usage)
                    }
                }
            }
            if (currentSession.isNotEmpty()) {
                sessions.add(currentSession)
            }
            sessions.forEach { list.add(TimelineItemData.Session(it)) }
        }

        if (selectedFilter != "Apps Only") {
            state.historicalSnapshots.forEach { list.add(TimelineItemData.Snapshot(it)) }
        }
        
        list.sortedBy { it.timestamp }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(dateList) { dateMs ->
                val isSelected = state.selectedHistoryDate == dateMs
                val label = if (dateMs == todayStart) "Today" else dateFormat.format(Date(dateMs))
                
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.selectHistoryDate(dateMs) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (mergedTimeline.isEmpty()) {
            EmptyState(
                title = "No Activity",
                message = "We haven't recorded any data for this filter on this date."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(mergedTimeline) { index, item ->
                    val isLast = index == mergedTimeline.lastIndex
                    when (item) {
                        is TimelineItemData.Session -> SessionNode(item.usages, timeFormat, isLast)
                        is TimelineItemData.Snapshot -> SnapshotNode(item.entity, isLast)
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun SessionNode(usages: List<AppUsageEntity>, timeFormat: SimpleDateFormat, isLast: Boolean) {
    val totalDurationMs = usages.sumOf { it.durationMs }
    val durationMins = (totalDurationMs / 60000).coerceAtLeast(1)
    val startTimeStr = timeFormat.format(Date(usages.first().startTime))
    val endTimeStr = timeFormat.format(Date(usages.last().endTime))

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = startTimeStr,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.width(48.dp).padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(
                    modifier = Modifier.width(16.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (usages.size > 2) 120.dp else 80.dp)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Session",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$durationMins min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val flowText = usages.joinToString(" → ") { it.appName }
                    Text(
                        text = flowText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3
                    )
                    
                    if (usages.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val distinctCategories = usages.map { it.appCategory }.distinct().take(3)
                            distinctCategories.forEach { cat ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SnapshotNode(
    snapshot: ContextSnapshotEntity,
    isLast: Boolean,
) {
    val motion =
        if (snapshot.accelVariance < 0.5f) {
            "Low motion"
        } else if (snapshot.accelVariance < 2.0f) {
            "Moderate motion"
        } else {
            "High motion"
        }
    val light =
        if (snapshot.lightLux > 100) {
            "Bright"
        } else if (snapshot.lightLux > 10) {
            "Dim"
        } else {
            "Dark"
        }
    val batteryText = if (snapshot.batteryLevel < 0) "" else " · ${snapshot.batteryLevel}% battery"

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(56.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
            )
            if (!isLast) {
                Box(
                    modifier =
                        Modifier
                            .width(2.dp)
                            .height(32.dp)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
                )
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = "──── Context ────",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$motion · ${snapshot.lightLux.toInt()} lux$batteryText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}
