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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.engine.HabitUiState
import com.habitminer.engine.HabitViewModel
import com.habitminer.ui.components.HabitCard
import com.habitminer.ui.components.DeviationCard
import com.habitminer.ui.components.TopAppMiniChart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.habitminer.ui.theme.StatusSuccess
import com.habitminer.ui.theme.StatusWarning
import com.habitminer.ui.theme.StatusError

@Composable
fun HomeScreen(
    state: HabitUiState,
    viewModel: HabitViewModel,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        
        if (!state.hasUsagePermission || !state.hasRuntimePermissions || !state.hasNotificationPermission) {
            PermissionScreen(
                hasUsage = state.hasUsagePermission,
                hasRuntime = state.hasRuntimePermissions,
                hasNotification = state.hasNotificationPermission,
                onRequestUsage = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onRuntimePermissionsGranted = {
                    viewModel.checkPermissions()
                },
                onRequestNotification = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
            )
            return@Column
        }

        // First Run Experience
        if (state.daysOfData == 0 && state.todayScreenTimeMs == 0L && state.discoveredHabits.isEmpty()) {
            FirstRunExperience(state, viewModel)
            return@Column
        }

        // Header and Learning Status
        LearningStatusHeader(state)

        // Today's Usage Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Today's behavior",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TodayUsageCard(state)
        }

        // Patterns & Deviations Section
        val hasDeviations = state.overallDeviationScore > 0.4f && state.todayDeviations.isNotEmpty()
        val hasPredictions = state.predictions.isNotEmpty()

        if (state.discoveredHabits.isNotEmpty() || hasDeviations) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Patterns & deviations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                if (hasDeviations) {
                    val dev = state.todayDeviations.maxByOrNull { it.normalizedScore }
                    if (dev != null) {
                        DeviationCard(
                            dev = dev,
                            onAcknowledge = { viewModel.acknowledgeDeviation(dev.id) }
                        )
                    }
                }

                state.discoveredHabits.take(2).forEach { habit ->
                    HabitCard(habit = habit)
                }
            }
        }

        // Context Section
        state.latestContext?.let { ctx ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Context",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CompactContextBanner(
                    context = ctx,
                    hasNotificationPermission = state.hasNotificationPermission,
                )
            }
        }

        // Lower-priority predictions
        if (hasPredictions) {
            val top = state.predictions.first()
            ActionableInsightCard(
                title = "Next Likely Activity [Experimental]",
                description = "Based on your routine → ${top.appName} (${(top.confidence * 100).toInt()}%)",
                icon = Icons.Default.AutoAwesome,
                isCritical = false,
                accentColor = MaterialTheme.colorScheme.primary,
            )
        }

        // Sync Status
        if (state.isSyncing) {
            Text(
                text = "Syncing your on-device data…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun FirstRunExperience(
    state: HabitUiState,
    viewModel: HabitViewModel,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = "Welcome to HabitMiner",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "I'm learning how your phone usage\nchanges throughout the day.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        Text(
            text = "No patterns yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "Keep using your phone normally.\nYour personal baseline will appear\nas enough data is collected.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (state.isSyncing) {
            Text("Syncing data...", color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = { viewModel.loadHistoricalData() }) {
                Text("Sync Usage Now")
            }
        }
    }
}

@Composable
fun LearningStatusHeader(state: HabitUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Monitoring",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            val statusText =
                when {
                    state.discoveredHabits.isNotEmpty() -> "${state.discoveredHabits.size} patterns learned"
                    state.hasEnoughData -> "Your baseline is established"
                    else -> "Learning your routine · Day ${state.daysOfData}"
                }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun TodayUsageCard(state: HabitUiState) {
    val screenTimeMs = state.todayScreenTimeMs
    val targetMs = if (state.expectedScreenTimeMs > 0L) state.expectedScreenTimeMs else -1L

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Neutral Baseline Presentation
            val hours = screenTimeMs / (1000 * 60 * 60)
            val mins = (screenTimeMs / (1000 * 60)) % 60

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = "Screen time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "${hours}h ${mins}m",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Unlocks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = "${state.todayUnlocks}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (targetMs > 0) {
                val tHours = targetMs / (1000 * 60 * 60)
                val tMins = (targetMs / (1000 * 60)) % 60
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            text = "Typical",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            text = "${tHours}h ${tMins}m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        val diffMs = screenTimeMs - targetMs
                        val diffSign = if (diffMs > 0) "+" else "−"
                        val diffMins = Math.abs(diffMs) / (1000 * 60)
                        val diffColor = if (diffMs > 0) StatusError else StatusSuccess

                        Text(
                            text = "Difference",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Text(
                            text = "$diffSign$diffMins min",
                            style = MaterialTheme.typography.titleMedium,
                            color = diffColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Typical for this period",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                Text(
                    text = "Building...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Top App Mini Chart
            TopAppMiniChart(appUsages = state.todayUsageByApp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactContextBanner(
    context: ContextSnapshotEntity,
    hasNotificationPermission: Boolean,
) {
    val motion =
        if (context.accelVariance < 0f) {
            "❔ Unknown"
        } else if (context.accelVariance < 0.5f) {
            "🧍 Low activity"
        } else if (context.accelVariance < 2.0f) {
            "🚶 Moderate"
        } else {
            "🏃 High activity"
        }
    val light =
        if (context.lightLux < 0) {
            "❔ Unavailable"
        } else if (context.lightLux > 100) {
            "☀️ Bright"
        } else if (context.lightLux > 10) {
            "🌙 Dim"
        } else {
            "🌑 Dark"
        }
    val batteryText = if (context.batteryLevel < 0) "Unavailable" else "🔋 ${context.batteryLevel}%"
    val steps = if (context.stepsSinceLastSnapshot > 0) "👣 ${context.stepsSinceLastSnapshot} steps" else "👣 --"
    val proximity =
        if (context.proximityNear == true) {
            "📱 Near"
        } else if (context.proximityNear == false) {
            "📱 Far"
        } else {
            "📱 --"
        }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = {},
            label = { Text(motion) }
        )
        AssistChip(
            onClick = {},
            label = { Text(light) }
        )
        AssistChip(
            onClick = {},
            label = { Text(batteryText) }
        )
        AssistChip(
            onClick = {},
            label = { Text(steps) }
        )
        AssistChip(
            onClick = {},
            label = { Text(proximity) }
        )
    }
}

@Composable
fun ActionableInsightCard(
    title: String,
    description: String,
    icon: ImageVector,
    isCritical: Boolean,
    accentColor: Color = if (isCritical) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(accentColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}
