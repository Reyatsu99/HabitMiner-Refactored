package com.habitminer.engine

import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitminer.collection.DataCollectionWorker
import com.habitminer.collection.HabitNotificationListener
import com.habitminer.collection.UsageDataCollector
import com.habitminer.data.AppUsageEntity
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.data.DeviationEntity
import com.habitminer.data.DiscoveredHabitEntity
import com.habitminer.data.PrefsKeys
import com.habitminer.domain.AppIdentityResolver
import com.habitminer.repository.ContextRepository
import com.habitminer.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@Immutable
data class HabitUiState(
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val retentionDays: Int = 90,
    val daysOfData: Int = 0,
    val todayScreenTimeMs: Long = 0L,
    val todayUnlocks: Int = 0,
    val todayTopApp: String = "",
    val todayUsageByApp: ImmutableMap<String, Long> = persistentMapOf(),
    val todayAppUsage: ImmutableList<AppUsageEntity> = persistentListOf(),
    val todaySnapshots: ImmutableList<ContextSnapshotEntity> = persistentListOf(),
    val discoveredHabits: ImmutableList<DiscoveredHabitEntity> = persistentListOf(),
    val todayDeviations: ImmutableList<DeviationEntity> = persistentListOf(),
    val recentDeviations: ImmutableList<DeviationEntity> = persistentListOf(),
    val overallDeviationScore: Float = 0f,
    val predictions: ImmutableList<PredictionEngine.Prediction> = persistentListOf(),
    val predictabilityScore: Float = 0f,
    val baselineStatus: String = "Building baseline...",
    val hasEnoughData: Boolean = false,
    val latestContext: ContextSnapshotEntity? = null,
    val hasUsagePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasRuntimePermissions: Boolean = false,
    val expectedScreenTimeMs: Long = 0L,
    val exportMessage: String? = null,
    val usageRecordCount: Int = 0,
    val liveUsageRecordCount: Int = 0,
    val historicalUsageRecordCount: Int = 0,
    val contextRecordCount: Int = 0,
    val lastUsageUpdate: Long? = null,
    val isMonitoringServiceActive: Boolean = false,
    val selectedHistoryDate: Long = 0L,
    val historicalAppUsage: ImmutableList<AppUsageEntity> = persistentListOf(),
    val historicalSnapshots: ImmutableList<ContextSnapshotEntity> = persistentListOf(),
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HabitViewModel
    @Inject
    constructor(
        application: Application,
        private val contextRepository: ContextRepository,
        private val habitRepository: HabitRepository,
        private val habitEngine: HabitEngine,
        private val predictionEngine: PredictionEngine,
        private val baselineBuilder: BaselineBuilder,
        private val deviationDetector: DeviationDetector,
        private val usageDataCollector: UsageDataCollector,
        private val appIdentityResolver: AppIdentityResolver,
        private val exportManager: com.habitminer.data.ExportManager,
        private val habitServiceManager: com.habitminer.collection.HabitServiceManager,
    ) : AndroidViewModel(application) {
        private val _uiState = MutableStateFlow(HabitUiState(selectedHistoryDate = getStartOfDay()))
        val uiState: StateFlow<HabitUiState> = _uiState.asStateFlow()
        // One-shot event: emits the file path for the Share Sheet. replay=0 means no re-play
        // after rotation, so the Share Sheet fires exactly once per export.
        private val _shareExportEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
        val shareExportEvent: SharedFlow<String> = _shareExportEvent.asSharedFlow()
        private var initialCollectionStarted = false
        private val syncMutex = Mutex()

        init {
            // checkPermissions() is called by MainActivity.onCreate() and onResume();
            // avoid calling it a third time here to prevent triple-init on first launch (MINOR-2)
            observeData()
        }

        fun checkPermissions() {
            val application = getApplication<Application>()
            val appOps = application.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        application.packageName,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(),
                        application.packageName,
                    )
                }
            val hasUsage = mode == AppOpsManager.MODE_ALLOWED
            val hasNotif = HabitNotificationListener.isEnabled(application)
            val retentionDays =
                application.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                    .getInt(PrefsKeys.RETENTION_DAYS, 90).coerceIn(30, 180)
            val collectionEnabled =
                application.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(PrefsKeys.COLLECTION_ENABLED, true)

            val hasRuntime =
                buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(android.Manifest.permission.POST_NOTIFICATIONS)
                }.all {
                    androidx.core.content.ContextCompat.checkSelfPermission(application, it) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                }

            _uiState.update {
                it.copy(
                    hasUsagePermission = hasUsage,
                    hasNotificationPermission = hasNotif,
                    hasRuntimePermissions = hasRuntime,
                    retentionDays = retentionDays,
                )
            }

            if (hasUsage && collectionEnabled && !initialCollectionStarted) {
                initialCollectionStarted = true
                synchronizeUsageAndModel()
            }
        }

        fun loadHistoricalData() {
            if (!_uiState.value.hasUsagePermission) return
            val application = getApplication<Application>()
            application.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PrefsKeys.COLLECTION_ENABLED, true).apply()
            synchronizeUsageAndModel()
        }

        private fun synchronizeUsageAndModel() {
            viewModelScope.launch(Dispatchers.IO) {
                if (!syncMutex.tryLock()) return@launch
                _uiState.update { it.copy(isLoading = true, isSyncing = true) }
                try {
                    val application = getApplication<Application>()
                    val count = contextRepository.getUsageCount()
                    val lastTimestamp = contextRepository.getLastInsertedUsageTimestamp()
                    val usages =
                        when {
                            count == 0 -> usageDataCollector.collectHistoricalData()
                            else -> {
                                val launcherPackages = appIdentityResolver.getLauncherPackages()
                                val prevPkg = contextRepository.getLastUsedNonLauncherPackage(launcherPackages)
                                usageDataCollector.collectUsageSince(
                                    lastTimestamp ?: System.currentTimeMillis() - 24 * 60 * 60 * 1000L,
                                    prevPkg,
                                )
                            }
                        }
                    if (usages.isNotEmpty()) contextRepository.insertAllAppUsage(usages)
                    
                    habitServiceManager.startServices()

                    val preferences = application.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                    var labelsChanged = false
                    if (!preferences.getBoolean("stored_labels_resolved", false)) {
                        contextRepository.getPackagesWithFallbackNames().forEach { packageName ->
                            val appName = appIdentityResolver.getAppName(packageName)
                            if (appName != packageName) {
                                contextRepository.updateFallbackAppName(packageName, appName)
                                labelsChanged = true
                            }
                        }
                        preferences.edit().putBoolean("stored_labels_resolved", true).apply()
                    }

                    val revision = contextRepository.getModelRevision(getStartOfDay())
                    if (labelsChanged || preferences.getString("source_revision", null) != revision) {
                        refreshHabits()
                        preferences.edit()
                            .putString("source_revision", revision)
                            .putInt(PrefsKeys.DAYS_OF_DATA, _uiState.value.daysOfData)
                            .putFloat("predictability_score", _uiState.value.predictabilityScore)
                            .apply()
                    } else {
                        val days = preferences.getInt(PrefsKeys.DAYS_OF_DATA, 0)
                        val existingBaselines = habitRepository.getAllBaselines().first()
                        val hasEnough = days >= 5 || existingBaselines.isNotEmpty()
                        _uiState.update {
                            it.copy(
                                daysOfData = days,
                                hasEnoughData = hasEnough,
                                baselineStatus = if (hasEnough) "Model up to date · $days days" else "Building baseline: $days/5 days",
                                predictabilityScore = preferences.getFloat("predictability_score", 0f),
                            )
                        }
                    }

                    // Trigger immediate collection for fresh sensor context
                    DataCollectionWorker.runOnce(application)
                } catch (error: Exception) {
                    android.util.Log.e("HabitMiner", "Could not sync usage or update the model", error)
                } finally {
                    syncMutex.unlock()
                    _uiState.update { it.copy(isLoading = false, isSyncing = false) }
                }
            }
        }

        fun setRetentionDays(days: Int) {
            val normalizedDays = days.coerceIn(30, 180)
            val application = getApplication<Application>()
            application.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(PrefsKeys.RETENTION_DAYS, normalizedDays).apply()
            _uiState.update { it.copy(retentionDays = normalizedDays) }
            DataCollectionWorker.runOnce(application)
        }

        fun clearCollectedData() {
            viewModelScope.launch(Dispatchers.IO) {
                contextRepository.clearCollectedData()
                habitRepository.clearModelData()
                val application = getApplication<Application>()
                application.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .remove("source_revision")
                    .remove(PrefsKeys.DAYS_OF_DATA)
                    .remove("predictability_score")
                    .remove("stored_labels_resolved")
                    .putBoolean(PrefsKeys.COLLECTION_ENABLED, false)
                    .apply()
                _uiState.update {
                    HabitUiState(
                        hasUsagePermission = true,
                        hasRuntimePermissions = true,
                        retentionDays = it.retentionDays,
                        baselineStatus = "Data cleared. Tap Sync Usage to resume collection.",
                    )
                }
                // Re-check actual permission state instead of hardcoding true (MINOR-10)
                checkPermissions()
            }
        }

        fun exportDataToCsv() {
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(exportMessage = "Exporting data...") }
                val path = exportManager.exportDataToCsv()
                if (path != null) {
                    _uiState.update { it.copy(exportMessage = "Export complete. Choose an app to share.") }
                    _shareExportEvent.emit(path)
                } else {
                    _uiState.update { it.copy(exportMessage = "Export failed. Please try again.") }
                }
            }
        }

        fun selectHistoryDate(timeInMillis: Long) {
            val startOfDay = Calendar.getInstance().apply {
                this.timeInMillis = timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            _uiState.update { it.copy(selectedHistoryDate = startOfDay) }
        }

        fun acknowledgeDeviation(deviationId: Long) {
            viewModelScope.launch {
                // We'll just delete it for now to acknowledge it
                habitRepository.deleteDeviation(deviationId)
            }
        }

        fun clearExportMessage() {
            _uiState.update { it.copy(exportMessage = null) }
        }

        private suspend fun refreshHabits() = withContext(Dispatchers.Default) {
            val startOfDay = getStartOfDay()
            val allUsage = contextRepository.getAllUsage().first()
            val todayUsage = contextRepository.getTodayUsage(startOfDay).first()
            val historicalUsage = allUsage.filter { it.startTime < startOfDay }

            // Baseline
            val contextWindowStart = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            val snapshots = contextRepository.getSnapshotsSince(contextWindowStart)
            val newBaselines = baselineBuilder.buildBaseline(historicalUsage, snapshots.filter { it.timestamp < startOfDay })
            newBaselines.forEach { habitRepository.insertBaseline(it) }

            // Habits
            val habits = habitEngine.discoverHabits(historicalUsage)
            habitRepository.deleteAllHabits()
            habits.forEach { habitRepository.insertHabit(it) }

            // Predictability
            val predictability = habitEngine.computePredictabilityScore(historicalUsage)

            // Deviations
            val todayContexts = snapshots.filter { it.timestamp >= startOfDay }
            val deviationsResult = deviationDetector.detectDeviations(todayUsage, todayContexts, newBaselines)

            val daysOfData = baselineBuilder.getDaysOfData(allUsage)
            val existingBaselines = habitRepository.getAllBaselines().first()
            val hasEnoughData = daysOfData >= 5 || existingBaselines.isNotEmpty()
            val baselineStatus = if (hasEnoughData) "Baseline built from $daysOfData days" else "Building baseline: $daysOfData/5 days"

            _uiState.update { current ->
                current.copy(
                    daysOfData = daysOfData,
                    hasEnoughData = hasEnoughData,
                    baselineStatus = baselineStatus,
                    predictabilityScore = predictability,
                )
            }
        }

        private fun observeData() {
            viewModelScope.launch {
                kotlinx.coroutines.supervisorScope {
                    val startOfDayFlow =
                        flow {
                            while (currentCoroutineContext().isActive) {
                                emit(getStartOfDay())
                                delay(60_000L)
                            }
                        }.distinctUntilChanged()

                    // Shared cache of all usage rows — updated by the debounced flow below.
                    // The today-usage collector reads from this cache to avoid a full-table
                    // scan on every DB write (CRITICAL-5).
                    val allUsageCache = MutableStateFlow<List<AppUsageEntity>>(emptyList())

                    launch {
                        startOfDayFlow.collectLatest { startOfDay ->
                            contextRepository.getTodayUsage(startOfDay).collect { usage ->
                                val (validUsage, totalTime, categories) =
                                    withContext(Dispatchers.Default) {
                                        val valid = usage.filterNot { appIdentityResolver.isLauncher(it.packageName) }
                                        val byApp =
                                            valid.groupBy { appIdentityResolver.getAppName(it.packageName) }
                                                .mapValues { entry -> entry.value.sumOf { item -> item.durationMs } }
                                        Triple(valid, valid.sumOf { it.durationMs }, byApp)
                                    }
                                val topCategory = categories.maxByOrNull { it.value }?.key.orEmpty()

                                _uiState.update {
                                    it.copy(
                                        todayScreenTimeMs = totalTime,
                                        todayUsageByApp = categories.toImmutableMap(),
                                        todayTopApp = topCategory,
                                        todayAppUsage = usage.toImmutableList(),
                                    )
                                }

                                if (validUsage.isNotEmpty()) {
                                    val latest = validUsage.first()
                                    // Use the cached all-usage list — avoids a full table scan here (CRITICAL-5)
                                    val cachedAllUsage = allUsageCache.value
                                    if (cachedAllUsage.isNotEmpty()) {
                                        val predictions =
                                            withContext(Dispatchers.Default) {
                                                predictionEngine.predict(
                                                    cachedAllUsage,
                                                    appIdentityResolver.getAppName(latest.packageName),
                                                    latest.timeSlot,
                                                    latest.dayType,
                                                )
                                            }
                                        _uiState.update { it.copy(predictions = predictions.toImmutableList()) }
                                    }

                                    // Evaluate today's deviations dynamically
                                    withContext(Dispatchers.Default) {
                                        val currentBaselines = habitRepository.getAllBaselines().first()
                                        val todayContexts = contextRepository.getTodaySnapshots(startOfDay).first()
                                        val deviationsResult = deviationDetector.detectDeviations(usage, todayContexts, currentBaselines)
                                        habitRepository.deleteDeviationsSince(startOfDay)
                                        deviationsResult.forEach { dev ->
                                            habitRepository.insertDeviation(
                                                com.habitminer.data.DeviationEntity(
                                                    timestamp = System.currentTimeMillis(),
                                                    timeBin = dev.timeBin,
                                                    deviationType = dev.deviationType,
                                                    description = dev.description,
                                                    zScore = dev.zScore,
                                                    normalizedScore = dev.normalizedScore,
                                                    affectedCategory = dev.affectedCategory,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    launch {
                        startOfDayFlow.collectLatest { startOfDay ->
                            contextRepository.getTodaySnapshots(startOfDay).collect { snapshots ->
                                _uiState.update { it.copy(todaySnapshots = snapshots.toImmutableList()) }
                            }
                        }
                    }

                    launch {
                        habitRepository.getAllHabits().collect { habits ->
                            _uiState.update { it.copy(discoveredHabits = habits.toImmutableList()) }
                        }
                    }

                    launch {
                        habitRepository.getAllBaselines().collect { baselines ->
                            val cal = Calendar.getInstance()
                            val day = cal.get(Calendar.DAY_OF_WEEK)
                            val dayType = if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) "WEEKEND" else "WEEKDAY"
                            val expected = baselines.filter { it.timeBin.startsWith(dayType) }.sumOf { it.avgScreenTimeMs }
                            _uiState.update { it.copy(expectedScreenTimeMs = expected) }
                        }
                    }

                    launch {
                        startOfDayFlow.collectLatest { startOfDay ->
                            habitRepository.getTodayDeviations(startOfDay).collect { devs ->
                                val overallScore = devs.maxOfOrNull { it.normalizedScore } ?: 0f
                                _uiState.update { it.copy(todayDeviations = devs.toImmutableList(), overallDeviationScore = overallScore) }
                            }
                        }
                    }

                    launch {
                        habitRepository.getRecentDeviations(20).collect { devs ->
                            _uiState.update { it.copy(recentDeviations = devs.toImmutableList()) }
                        }
                    }

                    launch {
                        contextRepository.getLatestSnapshot().collect { snapshot ->
                            _uiState.update {
                                it.copy(
                                    latestContext = snapshot,
                                    todayUnlocks = snapshot?.unlockCount ?: 0,
                                )
                            }
                        }
                    }

                    // Historical Data observation
                    launch {
                        _uiState.map { it.selectedHistoryDate }
                            .distinctUntilChanged()
                            .collectLatest { date ->
                                val endOfDay = date + 24 * 60 * 60 * 1000L - 1L
                                val usage = contextRepository.getUsageForDateRange(date, endOfDay)
                                _uiState.update { it.copy(historicalAppUsage = usage.toImmutableList()) }
                            }
                    }

                    launch {
                        _uiState.map { it.selectedHistoryDate }
                            .distinctUntilChanged()
                            .collectLatest { date ->
                                val endOfDay = date + 24 * 60 * 60 * 1000L - 1L
                                val snaps = contextRepository.getSnapshotsForDateRange(date, endOfDay)
                                _uiState.update { it.copy(historicalSnapshots = snaps.toImmutableList()) }
                            }
                    }

                    launch {
                        contextRepository.getAllUsage().debounce(300).collect { usage ->
                            // Update the shared cache so today-usage collector can use it without re-querying (CRITICAL-5)
                            allUsageCache.value = usage
                            val days = withContext(Dispatchers.Default) { baselineBuilder.getDaysOfData(usage) }
                            val lastUpdate = usage.maxOfOrNull { it.endTime }
                            _uiState.update { it.copy(daysOfData = days, lastUsageUpdate = lastUpdate) }
                        }
                    }

                    launch {
                        contextRepository.getUsageCountFlow().collect { count ->
                            _uiState.update { it.copy(usageRecordCount = count) }
                        }
                    }

                    launch {
                        contextRepository.getHistoricalUsageCountFlow().collect { count ->
                            _uiState.update { it.copy(historicalUsageRecordCount = count) }
                        }
                    }

                    launch {
                        contextRepository.getLiveUsageCountFlow().collect { count ->
                            _uiState.update { it.copy(liveUsageRecordCount = count) }
                        }
                    }

                    launch {
                        contextRepository.getSnapshotCountFlow().collect { count ->
                            _uiState.update { it.copy(contextRecordCount = count) }
                        }
                    }

                    launch {
                        com.habitminer.collection.MonitoringService.isServiceRunning.collectLatest { isRunning ->
                            _uiState.update {
                                it.copy(isMonitoringServiceActive = isRunning)
                            }
                        }
                    }
                }
            }
        }

        private fun getStartOfDay(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun clearDatabase() {
            viewModelScope.launch(Dispatchers.IO) {
                val app = getApplication<Application>()

                // Cancel all background work
                habitServiceManager.stopServices()

                // Clear all Room tables
                contextRepository.clearCollectedData()
                habitRepository.clearModelData()

                // Clear all SharedPreferences caches
                app.getSharedPreferences("sensor_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                app.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

                // Reset state flow UI flags if necessary
                initialCollectionStarted = false
                _uiState.update { HabitUiState() }
                checkPermissions()
            }
        }
    }
