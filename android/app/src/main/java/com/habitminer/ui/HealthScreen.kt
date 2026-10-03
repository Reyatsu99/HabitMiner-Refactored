@file:Suppress("ktlint:standard:function-naming")

package com.habitminer.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitminer.analytics.Format
import com.habitminer.analytics.SensingMode
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.engine.HabitUiState
import com.habitminer.ui.components.CardHeader
import com.habitminer.ui.components.Hint
import com.habitminer.ui.components.InfoRow
import com.habitminer.ui.components.SurfaceCard
import com.habitminer.ui.theme.StatusError
import com.habitminer.ui.theme.StatusSuccess
import com.habitminer.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class SensorStatus(
    val name: String,
    val present: Boolean,
    val lastReading: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(state: HabitUiState) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val df = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }
    var showDiagnostics by remember { mutableStateOf(false) }

    val sensors = sensorStatuses(sensorManager, state.latestSensorContext)
    val working = sensors.count { it.present && it.lastReading != null }
    val present = sensors.count { it.present }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Data health",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Hint("Is HabitMiner collecting properly, and what does it cost?")
        }

        item { StatusBanner(state, context) }

        item {
            SurfaceCard {
                CardHeader("Collection", Icons.Default.CheckCircle)
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow(
                    "Background monitoring",
                    if (state.isMonitoringServiceActive) "Running" else "Stopped",
                    if (state.isMonitoringServiceActive) StatusSuccess else StatusError,
                )
                InfoRow("Last app usage update", state.lastUsageUpdate?.let { df.format(Date(it)) } ?: "Never")
                InfoRow("Last surroundings reading", state.latestContext?.timestamp?.let { df.format(Date(it)) } ?: "Never")
                val restricted =
                    remember {
                        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && am.isBackgroundRestricted
                    }
                InfoRow(
                    "Battery restrictions",
                    if (restricted) "Restricted (action needed)" else "None",
                    if (restricted) StatusError else StatusSuccess,
                )
            }
        }

        item {
            val mode = state.sensingModeName?.let { runCatching { SensingMode.valueOf(it) }.getOrNull() }
            SurfaceCard {
                CardHeader("Battery-aware sensing", Icons.Default.BatteryChargingFull, tint = StatusSuccess)
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow("Current mode", mode?.let { "${it.label} · every ${it.intervalMs / 60_000} min" } ?: "Starting…")
                mode?.let { Hint(it.explanation) }
                Spacer(modifier = Modifier.height(4.dp))
                InfoRow("Sensors switched on today", formatSeconds(state.sensingMsToday))
                InfoRow("Readings today", "${state.todaySnapshots.size}")
                Hint(
                    "Each reading turns sensors on for about a second. Sampling speeds up when you're moving with the screen on " +
                        "and slows down when the phone is idle.",
                )
            }
        }

        item {
            SurfaceCard {
                CardHeader("Sensors", Icons.Default.Sensors, trailing = "$working of $present reporting")
                Spacer(modifier = Modifier.height(6.dp))
                sensors.forEach { s ->
                    val (status, color) =
                        when {
                            !s.present -> "Not on this phone" to StatusError
                            s.lastReading != null -> s.lastReading to StatusSuccess
                            else -> "No reading yet" to StatusWarning
                        }
                    InfoRow(s.name, status, color)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Hint(
                    state.latestSensorContext?.let { "Last reading ${Labels.age(it.timestamp)}. Sensors are only read while the screen is on." }
                        ?: "Sensors are only read while the screen is on, so readings appear after you next use your phone.",
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDiagnostics = !showDiagnostics },
            ) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Diagnostics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(if (showDiagnostics) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }
                    if (showDiagnostics) {
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Usage rows from Android's history", "${state.historicalUsageRecordCount}")
                        InfoRow("Usage rows collected live", "${state.liveUsageRecordCount}")
                        InfoRow("Surroundings readings", "${state.contextRecordCount}")
                        InfoRow("Days with data", "${state.daysOfData}")
                        InfoRow("Baseline", state.baselineStatus)
                        InfoRow("Routines found (raw)", "${state.discoveredHabits.size}")
                        InfoRow("Labels collected", "${state.labelCount}")
                        InfoRow("Database size", databaseSize(context))
                        InfoRow("Data kept for", "${state.retentionDays} days")
                    } else {
                        Hint("Record counts, model status and storage. Tap to show.")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusBanner(
    state: HabitUiState,
    context: Context,
) {
    val (title, body, ok) =
        when {
            !state.hasUsagePermission || !state.hasRuntimePermissions || !state.hasNotificationPermission ->
                Triple("Collection paused", "A permission is missing. Open the Today tab to grant it.", false)
            !state.isMonitoringServiceActive ->
                Triple(
                    "Background monitoring stopped",
                    "Your phone may be closing HabitMiner to save battery. Tap to open battery settings and allow it to run.",
                    false,
                )
            else -> Triple("Everything is working", "Usage and surroundings are being collected on this phone.", true)
        }
    SurfaceCard(
        onClick =
            if (!ok && state.hasUsagePermission) {
                {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }
            } else {
                null
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (ok) StatusSuccess else StatusError,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 10.dp))
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Hint(body)
            }
        }
    }
}

private fun sensorStatuses(
    sm: SensorManager,
    latest: ContextSnapshotEntity?,
): List<SensorStatus> {
    fun has(type: Int) = sm.getDefaultSensor(type) != null
    val fresh = latest?.takeIf { System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000L }
    return listOf(
        SensorStatus(
            "Motion (accelerometer)",
            has(Sensor.TYPE_ACCELEROMETER),
            fresh?.accelVariance?.takeIf { it >= 0f }?.let { v ->
                if (v < 0.5f) "Still" else if (v < 2f) "Moving" else "Very active"
            },
        ),
        SensorStatus(
            "Rotation (gyroscope)",
            has(Sensor.TYPE_GYROSCOPE),
            fresh?.gyroEnergy?.takeIf { it >= 0f }?.let { "Working" },
        ),
        SensorStatus(
            "Light",
            has(Sensor.TYPE_LIGHT),
            fresh?.lightLux?.takeIf { it >= 0f }?.let { "${it.toInt()} lux" },
        ),
        SensorStatus(
            "Proximity",
            has(Sensor.TYPE_PROXIMITY),
            fresh?.proximityNear?.let { if (it) "Covered" else "Uncovered" },
        ),
        SensorStatus(
            "Step counter",
            has(Sensor.TYPE_STEP_COUNTER),
            fresh?.stepsSinceLastSnapshot?.takeIf { it >= 0 }?.let { "$it steps since last reading" },
        ),
    )
}

/** Room keeps recent writes in the -wal file, so all three files are counted. */
private fun databaseSize(context: Context): String {
    val main = context.getDatabasePath("habitminer_database")
    val bytes =
        listOf(main, java.io.File(main.path + "-wal"), java.io.File(main.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
    return when {
        bytes <= 0L -> "Unknown"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatSeconds(ms: Long): String =
    when {
        ms < 1000L -> "under a second"
        ms < 60_000L -> "${ms / 1000} s"
        else -> Format.duration(ms) + " ${(ms / 1000) % 60}s"
    }
