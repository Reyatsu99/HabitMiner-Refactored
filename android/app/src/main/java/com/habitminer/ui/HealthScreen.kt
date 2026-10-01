@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitminer.engine.HabitUiState
import com.habitminer.ui.theme.StatusSuccess
import com.habitminer.ui.theme.StatusError
import androidx.compose.material3.LinearProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SensorInfo(
    val name: String,
    val vendor: String,
    val isAvailable: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(state: HabitUiState) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    val allSensors =
        remember {
            val requiredTypes =
                listOf(
                    Pair(Sensor.TYPE_ACCELEROMETER, "Accelerometer"),
                    Pair(Sensor.TYPE_GYROSCOPE, "Gyroscope"),
                    Pair(Sensor.TYPE_PROXIMITY, "Proximity"),
                    Pair(Sensor.TYPE_LIGHT, "Ambient Light"),
                    Pair(Sensor.TYPE_STEP_COUNTER, "Step Counter"),
                )

            requiredTypes.map { (type, typeName) ->
                val sensor = sensorManager.getDefaultSensor(type)
                if (sensor != null) {
                    SensorInfo(name = typeName, vendor = sensor.vendor ?: "Unknown", isAvailable = true)
                } else {
                    SensorInfo(name = typeName, vendor = "N/A", isAvailable = false)
                }
            }.sortedBy { !it.isAvailable }
        }
    val activeSensors = allSensors.count { it.isAvailable }

    val df = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val usageTime = state.lastUsageUpdate?.let { df.format(Date(it)) } ?: "Never"
    val contextTime = state.latestContext?.timestamp?.let { df.format(Date(it)) } ?: "Never"

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Data Health", fontWeight = FontWeight.SemiBold) },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                if (!state.hasUsagePermission || !state.hasRuntimePermissions || !state.hasNotificationPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Collection halted. Permissions missing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (state.daysOfData == 0 && state.liveUsageRecordCount == 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Sensors, contentDescription = "Active", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Waiting for first live collection run.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (!state.isMonitoringServiceActive) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                val intent =
                                    android.content.Intent().apply {
                                        action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                    }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Foreground Service Killed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Your device (e.g. Xiaomi) may be aggressively killing the service. " +
                                        "Tap to open battery settings and disable restrictions.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Background Operation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("WorkManager (Periodic sync)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (state.hasUsagePermission) "Active" else "Inactive",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.hasUsagePermission) StatusSuccess else MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Foreground Service", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (state.isMonitoringServiceActive) "Active" else "Stopped/Dead",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isMonitoringServiceActive) StatusSuccess else MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Last App Usage Sync", style = MaterialTheme.typography.bodyMedium)
                            Text(usageTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Last Context Snapshot", style = MaterialTheme.typography.bodyMedium)
                            Text(contextTime, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Background Restrictions", style = MaterialTheme.typography.bodyMedium)

                            val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                            val isRestricted =
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                    am.isBackgroundRestricted
                                } else {
                                    false
                                }

                            Text(
                                if (isRestricted) "Restricted (Action Needed)" else "Unrestricted",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isRestricted) MaterialTheme.colorScheme.error else StatusSuccess,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Data Coverage & Provenance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Historical Backfill (UsageStats)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${state.historicalUsageRecordCount} records",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Live Collection (HabitMiner)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${state.liveUsageRecordCount} records",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Hardware Context Snapshots", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${state.contextRecordCount} records",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Learning Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Live Data Period", style = MaterialTheme.typography.bodyMedium)
                            Text("${state.daysOfData} Days", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Baseline", style = MaterialTheme.typography.bodyMedium)
                            Text(state.baselineStatus, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (state.daysOfData < 5) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (state.daysOfData / 5f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Discovered Patterns", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${state.discoveredHabits.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recent Deviations", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${state.recentDeviations.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Storage & Retention",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Local Database",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val dbFile = context.getDatabasePath("habitminer_db")
                            val dbSizeKb = if (dbFile.exists()) dbFile.length() / 1024 else 0
                            Text(
                                "Size: $dbSizeKb KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Rolling window: ${state.retentionDays} days",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sensor Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "$activeSensors/5 Available",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(allSensors) { sensor ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = sensor.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = sensor.vendor,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            val telemetry = when (sensor.name) {
                                "Accelerometer" -> state.latestContext?.accelEnergy?.let { "Energy: ${String.format(Locale.US, "%.1f", it)}" }
                                "Gyroscope" -> state.latestContext?.gyroEnergy?.let { "Energy: ${String.format(Locale.US, "%.1f", it)}" }
                                "Ambient Light" -> state.latestContext?.lightLux?.let { "${it.toInt()} lx" }
                                "Proximity" -> state.latestContext?.proximityNear?.let { if (it) "Near" else "Far" }
                                "Step Counter" -> state.latestContext?.stepsSinceLastSnapshot?.let { "$it steps" }
                                else -> null
                            }
                            if (telemetry != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = telemetry,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .background(
                                        color =
                                            if (sensor.isAvailable) {
                                                StatusSuccess.copy(alpha = 0.2f)
                                            } else {
                                                StatusError.copy(alpha = 0.2f)
                                            },
                                        shape = CircleShape,
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (sensor.isAvailable) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = if (sensor.isAvailable) "Available" else "Not Available",
                                tint = if (sensor.isAvailable) StatusSuccess else StatusError,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
