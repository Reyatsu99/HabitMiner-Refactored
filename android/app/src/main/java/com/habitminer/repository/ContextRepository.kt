package com.habitminer.repository

import com.habitminer.data.AppUsageDao
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.ContextDao
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.data.DeviceEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextRepository
    @Inject
    constructor(
        private val appUsageDao: AppUsageDao,
        private val contextDao: ContextDao,
        private val deviceEventDao: DeviceEventDao,
    ) {
        val collectionMutex = Mutex()

        suspend fun shouldSkipContextCollection(): Boolean {
            val latest = contextDao.getLatestSnapshot().firstOrNull()
            return latest != null && (System.currentTimeMillis() - latest.timestamp) < 10 * 60 * 1000L
        }

        fun getLatestSnapshot(): Flow<ContextSnapshotEntity?> = contextDao.getLatestSnapshot()

        fun getTodayUsage(startOfDayMs: Long): Flow<List<AppUsageEntity>> = appUsageDao.getTodayUsage(startOfDayMs)

        fun getTodaySnapshots(startOfDayMs: Long): Flow<List<ContextSnapshotEntity>> = contextDao.getTodaySnapshots(startOfDayMs)

        fun getAllUsage(): Flow<List<AppUsageEntity>> = appUsageDao.getAllUsage()

        suspend fun getSnapshotsSince(sinceMs: Long): List<ContextSnapshotEntity> = contextDao.getSnapshotsSince(sinceMs)

        suspend fun getUsageForDateRange(startMs: Long, endMs: Long): List<AppUsageEntity> = appUsageDao.getUsageForDateRange(startMs, endMs)

        suspend fun getSnapshotsForDateRange(startMs: Long, endMs: Long): List<ContextSnapshotEntity> = contextDao.getSnapshotsForDateRange(startMs, endMs)

        fun getAllSnapshots(): Flow<List<ContextSnapshotEntity>> = contextDao.getAllSnapshots()

        suspend fun getUsageRevision(): String = appUsageDao.getUsageRevision()

        suspend fun getModelRevision(beforeMs: Long): String =
            "${appUsageDao.getModelRevision(beforeMs)}|${contextDao.getModelRevision(beforeMs)}"

        suspend fun getPackagesWithFallbackNames(): List<String> = appUsageDao.getPackagesWithPackageNameLabels()

        suspend fun updateFallbackAppName(
            packageName: String,
            appName: String,
        ) = appUsageDao.updateFallbackAppName(packageName, appName)

        suspend fun getUsageCount(): Int = appUsageDao.getUsageCount().first()

        fun getUsageCountFlow(): Flow<Int> = appUsageDao.getUsageCount()

        fun getHistoricalUsageCountFlow(): Flow<Int> = appUsageDao.getHistoricalUsageCount()

        fun getLiveUsageCountFlow(): Flow<Int> = appUsageDao.getLiveUsageCount()

        fun getSnapshotCountFlow(): Flow<Int> = contextDao.getSnapshotCount()

        suspend fun getLastInsertedUsageTimestamp(): Long? = appUsageDao.getLastInsertedTimestamp()

        suspend fun getLastUsedNonLauncherPackage(launcherPackages: List<String>): String? =
            if (launcherPackages.isEmpty()) {
                appUsageDao.getLastUsedPackage()
            } else {
                appUsageDao.getLastUsedNonLauncherPackage(launcherPackages)
            }

        suspend fun getSnapshotRevision(): String = contextDao.getSnapshotRevision()

        suspend fun insertSnapshot(snapshot: ContextSnapshotEntity) = contextDao.insert(snapshot)

        suspend fun insertAppUsage(usage: AppUsageEntity) = appUsageDao.insert(usage)

        suspend fun insertAllAppUsage(usages: List<AppUsageEntity>) = appUsageDao.insertAll(usages)

        suspend fun insertDeviceEvent(event: com.habitminer.data.DeviceEventEntity) = deviceEventDao.insert(event)

        suspend fun countDeviceEventsSince(eventType: String, sinceMs: Long): Int = deviceEventDao.countSince(eventType, sinceMs)

        suspend fun clearOldData(retentionCutoffMs: Long) {
            appUsageDao.deleteOlderThan(retentionCutoffMs)
            contextDao.deleteOlderThan(retentionCutoffMs)
            deviceEventDao.deleteOlderThan(retentionCutoffMs)
        }

        suspend fun clearCollectedData() {
            appUsageDao.deleteAll()
            contextDao.deleteAll()
            deviceEventDao.deleteAll()
        }
    }
