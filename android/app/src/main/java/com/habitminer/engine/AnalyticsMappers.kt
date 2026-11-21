package com.habitminer.engine

import com.habitminer.analytics.CategoryMapper
import com.habitminer.analytics.ContextSample
import com.habitminer.analytics.UsageSession
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.data.DeviationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Converts Room entities into the plain models the analytics package works with. */
object AnalyticsMappers {
    fun session(e: AppUsageEntity): UsageSession =
        UsageSession(
            packageName = e.packageName,
            appName = e.appName,
            category = CategoryMapper.categorize(e.packageName, e.appName, e.appCategory),
            start = e.startTime,
            end = e.endTime,
            durationMs = e.durationMs,
        )

    fun sample(e: ContextSnapshotEntity): ContextSample =
        ContextSample(
            timestamp = e.timestamp,
            lightLux = e.lightLux.takeIf { it >= 0f },
            motionVariance = e.accelVariance.takeIf { it >= 0f },
            isCharging = e.isCharging,
            isScreenOn = e.isScreenOn,
            proximityNear = e.proximityNear,
            batteryLevel = e.batteryLevel.takeIf { it in 0..100 },
            place = e.wifiPlace,
            recentSteps = e.recentSteps.takeIf { it >= 0 },
            stepsSinceLast = e.stepsSinceLastSnapshot.takeIf { it >= 0 },
            accelMean = e.accelMean.takeIf { it >= 0f && it.isFinite() },
            accelStd = e.accelStd.takeIf { it >= 0f && it.isFinite() },
            accelMin = e.accelMin.takeIf { it >= 0f && it.isFinite() },
            accelMax = e.accelMax.takeIf { it >= 0f && it.isFinite() },
            accelEnergy = e.accelEnergy.takeIf { it >= 0f && it.isFinite() },
            gyroMean = e.gyroMean.takeIf { it >= 0f && it.isFinite() },
            gyroVariance = e.gyroVariance.takeIf { it >= 0f && it.isFinite() },
            gyroStd = e.gyroStd.takeIf { it >= 0f && it.isFinite() },
            gyroMin = e.gyroMin.takeIf { it >= 0f && it.isFinite() },
            gyroMax = e.gyroMax.takeIf { it >= 0f && it.isFinite() },
            gyroEnergy = e.gyroEnergy.takeIf { it >= 0f && it.isFinite() },
            batteryTempC = e.batteryTempC?.takeIf { it.isFinite() },
            powerSave = e.powerSave,
            thermalStatus = e.thermalStatus?.takeIf { it in 0..6 },
            unlockCount = e.unlockCount.takeIf { it >= 0 },
            notificationsLastHour = e.notificationsLastHour.takeIf { it >= 0 },
            sensingMs = e.sensingMs.coerceAtLeast(0L),
        )

    /**
     * Stable identity for a deviation across re-detections (they are deleted and re-inserted
     * every time today's data changes, so database IDs are not stable).
     */
    fun fingerprint(d: DeviationEntity): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(d.timestamp))
        return "$date|${d.deviationType}|${d.timeBin}|${d.affectedCategory}"
    }
}
