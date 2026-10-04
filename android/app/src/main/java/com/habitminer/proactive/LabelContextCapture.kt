package com.habitminer.proactive

import android.content.Context
import android.os.PowerManager
import com.google.gson.Gson
import com.habitminer.collection.WifiPlaceProvider
import com.habitminer.repository.ContextRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures what the phone knew at the moment a label was given (latest app, light,
 * motion, charging, place), so each label can be joined with sensor features later.
 */
@Singleton
class LabelContextCapture
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val contextRepository: ContextRepository,
        private val wifiPlaceProvider: WifiPlaceProvider,
    ) {
        suspend fun captureJson(): String? =
            runCatching {
                val now = System.currentTimeMillis()
                val startOfDay =
                    Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                val lastUsage = contextRepository.getTodayUsage(startOfDay).first().firstOrNull()
                val snapshot = contextRepository.getLatestSnapshotWithSensors().first()
                val any = contextRepository.getLatestSnapshot().first()
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val map =
                    linkedMapOf<String, Any?>(
                        "time" to now,
                        "screenOn" to pm.isInteractive,
                        "lastApp" to lastUsage?.packageName,
                        "lastAppEnd" to lastUsage?.endTime,
                        "lightLux" to snapshot?.lightLux,
                        "accelVariance" to snapshot?.accelVariance,
                        "recentSteps" to snapshot?.recentSteps,
                        "sensorAgeMs" to snapshot?.let { now - it.timestamp },
                        "charging" to any?.isCharging,
                        "battery" to any?.batteryLevel,
                        "place" to wifiPlaceProvider.currentPlaceHash(),
                    )
                Gson().toJson(map)
            }.getOrNull()
    }
