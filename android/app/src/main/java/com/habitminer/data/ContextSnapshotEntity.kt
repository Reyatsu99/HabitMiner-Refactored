package com.habitminer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "context_snapshots",
    indices = [
        androidx.room.Index(value = ["timestamp"]),
    ],
)
data class ContextSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val accelMean: Float,
    val accelVariance: Float,
    val accelStd: Float,
    val accelMin: Float,
    val accelMax: Float,
    val accelEnergy: Float,
    val gyroMean: Float,
    val gyroVariance: Float,
    val gyroStd: Float,
    val gyroMin: Float,
    val gyroMax: Float,
    val gyroEnergy: Float,
    val lightLux: Float,
    val proximityNear: Boolean?,
    val stepsSinceLastSnapshot: Int,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isScreenOn: Boolean,
    val unlockCount: Int,
    val notificationsLastHour: Int,
    /** Hashed ID of the connected Wi-Fi network (only when Wi-Fi places are enabled). */
    val wifiPlace: String? = null,
    /** How long sensors were switched on to take this snapshot. */
    val sensingMs: Long = 0L,
    /** Steps in the two minutes before this snapshot (-1 when the step counter wasn't available). */
    val recentSteps: Int = -1,
)
