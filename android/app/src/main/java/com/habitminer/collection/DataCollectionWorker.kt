package com.habitminer.collection

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.habitminer.data.PrefsKeys
import com.habitminer.domain.AppIdentityResolver
import com.habitminer.repository.ContextRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class DataCollectionWorker
    @AssistedInject
    constructor(
        @Assisted private val appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val usageCollector: UsageDataCollector,
        private val sensorCollector: SensorContextCollector,
        private val appIdentityResolver: AppIdentityResolver,
        private val contextRepository: ContextRepository,
        private val habitRepository: com.habitminer.repository.HabitRepository,
        private val feedbackRepository: com.habitminer.repository.FeedbackRepository,
        private val wifiPlaceProvider: WifiPlaceProvider,
        private val proactiveEngine: com.habitminer.proactive.ProactiveEngine,
    ) : CoroutineWorker(appContext, workerParams) {
        override suspend fun doWork(): Result =
            workerMutex.withLock {
                try {
                    val preferences = appContext.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                    if (!preferences.getBoolean(PrefsKeys.COLLECTION_ENABLED, true)) {
                        return@withLock Result.success()
                    }

                    val retentionDays = preferences.getInt(PrefsKeys.RETENTION_DAYS, 90).coerceIn(30, 180)
                    val retentionCutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
                    contextRepository.clearOldData(retentionCutoff)
                    habitRepository.clearOldData(retentionCutoff)
                    feedbackRepository.clearOldData(retentionCutoff)

                    val lastTimestamp =
                        contextRepository.getLastInsertedUsageTimestamp()
                            ?: (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14))

                    val prevPkg = contextRepository.getLastUsedNonLauncherPackage(appIdentityResolver.getLauncherPackages())
                    val newUsage = usageCollector.collectUsageSince(lastTimestamp, prevPkg)
                    if (newUsage.isNotEmpty()) {
                        contextRepository.insertAllAppUsage(newUsage)
                    }

                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val isScreenOn = pm.isInteractive

                    val now = System.currentTimeMillis()
                    val startOfDay =
                        Calendar.getInstance().run {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                            timeInMillis
                        }
                    val recordedUnlocks = contextRepository.countDeviceEventsSince(com.habitminer.collection.DeviceEventReceiver.EVENT_UNLOCK, startOfDay)
                    val systemUnlocks = runCatching { usageCollector.countUnlocksSince(startOfDay) }.getOrNull() ?: 0
                    val unlockCount = maxOf(recordedUnlocks, systemUnlocks)
                    val notificationsLastHour =
                        if (com.habitminer.collection.HabitNotificationListener.isEnabled(appContext)) {
                            contextRepository.countDeviceEventsSince(com.habitminer.collection.DeviceEventReceiver.EVENT_NOTIFICATION, now - TimeUnit.HOURS.toMillis(1))
                        } else {
                            -1
                        }
                    val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                    var batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    var isCharging = batteryManager.isCharging
                    if (batteryLevel !in 0..100) {
                        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                        val batteryIntent = appContext.registerReceiver(null, filter)
                        if (batteryIntent != null) {
                            val level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                            val scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                            if (level >= 0 && scale > 0) {
                                batteryLevel = (level * 100 / scale)
                            } else {
                                batteryLevel = -1
                            }
                            val status = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                            isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == android.os.BatteryManager.BATTERY_STATUS_FULL
                        } else {
                            batteryLevel = -1
                        }
                    }

                    val shouldSampleSensors = isScreenOn && (batteryLevel >= 15 || isCharging)

                    contextRepository.collectionMutex.withLock {
                        if (contextRepository.shouldSkipContextCollection()) {
                            Log.d(TAG, "Skipping context collection: recent snapshot exists")
                        } else {
                            val place = wifiPlaceProvider.currentPlaceHash()
                            val snapshot =
                                sensorCollector.collectSnapshot(
                                    unlockCount = unlockCount,
                                    isScreenOn = isScreenOn,
                                    notificationsLastHour = notificationsLastHour,
                                    collectSensors = shouldSampleSensors,
                                    batteryLevel = batteryLevel,
                                    isCharging = isCharging,
                                    wifiPlace = place,
                                )

                            contextRepository.insertSnapshot(snapshot)
                            if (place != null) feedbackRepository.recordPlaceSeen(place, snapshot.timestamp)
                        }
                    }

                    // Fallback for check-ins/nudges/digest when the foreground service was killed.
                    if (!MonitoringService.isServiceRunning.value) proactiveEngine.tick()

                    Result.success()
                } catch (e: CancellationException) {
                    // Re-throw so WorkManager honours explicit cancellation (e.g. clearDatabase)
                    // rather than rescheduling work that was intentionally stopped (DCW-2)
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "DataCollectionWorker failed", e)
                    Result.retry()
                }
            }

        companion object {
            private val workerMutex = Mutex()
            private const val TAG = "HabitMiner"
            private const val WORK_NAME = "DataCollectionWorker"

            fun schedulePeriodicWork(context: Context) {
                val constraints =
                    androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()

                val request =
                    PeriodicWorkRequestBuilder<DataCollectionWorker>(15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }

            fun runOnce(context: Context) {
                // Apply battery-not-low constraint so we don't drain battery near-empty (DCW-1)
                val constraints =
                    androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                val request =
                    OneTimeWorkRequestBuilder<DataCollectionWorker>()
                        .setConstraints(constraints)
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "DataCollectionWorker_Once",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }
    }
