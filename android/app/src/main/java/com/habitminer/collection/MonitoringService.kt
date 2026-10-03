package com.habitminer.collection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import com.habitminer.data.PrefsKeys
import com.habitminer.domain.AppIdentityResolver
import com.habitminer.repository.ContextRepository
import com.habitminer.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {
    @Inject
    lateinit var contextRepository: ContextRepository

    @Inject
    lateinit var sensorCollector: SensorContextCollector

    @Inject
    lateinit var appIdentityResolver: AppIdentityResolver

    @Inject
    lateinit var usageDataCollector: UsageDataCollector

    // ACTION_USER_PRESENT is not delivered to manifest-declared receivers on Android 8+,
    // so unlocks are only captured by a receiver registered at runtime while we're alive.
    private val unlockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                serviceScope.launch {
                    contextRepository.insertDeviceEvent(
                        com.habitminer.data.DeviceEventEntity(eventType = DeviceEventReceiver.EVENT_UNLOCK),
                    )
                }
            }
        }
    private var unlockReceiverRegistered = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var isMonitoringPaused = false
    private var wakeLock: PowerManager.WakeLock? = null

    // Cache variables for rich notification
    @Volatile private var todayScreenTimeMs: Long = 0L

    @Volatile private var lastUsedApp: String = ""

    @Volatile private var lastContextTime: Long = 0L

    @Volatile private var activeSensors: Int = 5

    companion object {
        const val ACTION_START = "com.habitminer.action.START"
        const val ACTION_PAUSE = "com.habitminer.action.PAUSE"
        const val ACTION_RESUME = "com.habitminer.action.RESUME"
        const val ACTION_STOP = "com.habitminer.action.STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "monitoring_channel"
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        // MutableStateFlow for reactive liveness tracking
        val isServiceRunning = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isServiceRunning.value = true

        try {
            ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            unlockReceiverRegistered = true
        } catch (e: Exception) {
            android.util.Log.w("HabitMiner", "Could not register unlock receiver", e)
        }

        // Initial population of the rich notification state to avoid "0m today" delay
        serviceScope.launch {
            updateRichNotificationState()
            updateNotification()
        }

        // Acquire a partial wakelock so Doze does not suspend the 15-min collection loop (BP-1)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HabitMiner::MonitoringLock",
            ).also { it.acquire(4 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                isMonitoringPaused = false
                promoteToForeground()
                startMonitoringLoop()
            }
            ACTION_PAUSE -> {
                isMonitoringPaused = true
                updateNotification()
            }
            ACTION_RESUME -> {
                isMonitoringPaused = false
                updateNotification()
                // Force an immediate collection on resume
                serviceScope.launch { collectContextSnapshot() }
            }
            ACTION_STOP -> {
                stopSelf()
            }
            else -> {
                // Started by system after kill (START_STICKY resurrection) or BOOT_COMPLETED
                isMonitoringPaused = false
                promoteToForeground()
                startMonitoringLoop()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoringLoop() {
        // Cancel existing monitoring coroutine without cancelling the scope (CRITICAL-3 / MS-1)
        serviceJob.cancelChildren()

        serviceScope.launch {
            while (true) {
                if (!isMonitoringPaused) {
                    collectContextSnapshot()
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun collectContextSnapshot() {
        val preferences = getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(PrefsKeys.COLLECTION_ENABLED, true)) {
            stopSelf()
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = pm.isInteractive

        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging

        // Match DataCollectionWorker's battery guard so we don't drain battery at low charge (PERF-5)
        val shouldCollectSensors = isScreenOn && (batteryLevel >= 15 || isCharging)

        // Query real unlock and notification counts from the event log (MS-2)
        val startOfDay =
            Calendar.getInstance().run {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis
            }
        val recordedUnlocks = contextRepository.countDeviceEventsSince(com.habitminer.collection.DeviceEventReceiver.EVENT_UNLOCK, startOfDay)
        val systemUnlocks = runCatching { usageDataCollector.countUnlocksSince(startOfDay) }.getOrNull() ?: 0
        val unlockCount = maxOf(recordedUnlocks, systemUnlocks)
        val notificationsLastHour =
            if (HabitNotificationListener.isEnabled(this)) {
                contextRepository.countDeviceEventsSince(
                    com.habitminer.collection.DeviceEventReceiver.EVENT_NOTIFICATION,
                    System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
                )
            } else {
                -1
            }

        contextRepository.collectionMutex.withLock {
            if (contextRepository.shouldSkipContextCollection()) {
                android.util.Log.d("HabitMiner", "Skipping context collection: recent snapshot exists")
            } else {
                val snapshot =
                    sensorCollector.collectSnapshot(
                        unlockCount = unlockCount,
                        isScreenOn = isScreenOn,
                        notificationsLastHour = notificationsLastHour,
                        collectSensors = shouldCollectSensors,
                        batteryLevel = batteryLevel,
                        isCharging = isCharging,
                    )

                contextRepository.insertSnapshot(snapshot)
            }
        }
        updateRichNotificationState()
        updateNotification()
    }

    private suspend fun updateRichNotificationState() {
        try {
            val startOfDay =
                Calendar.getInstance().run {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    timeInMillis
                }

            // Get usage directly without blocking on the Flow endlessly (first() gives current snapshot)
            val usageList = contextRepository.getTodayUsage(startOfDay).first()
            val validUsage = usageList.filterNot { appIdentityResolver.isLauncher(it.packageName) }
            todayScreenTimeMs = validUsage.sumOf { it.durationMs }
            lastUsedApp = validUsage.firstOrNull()?.let { appIdentityResolver.getAppName(it.packageName) } ?: ""

            // Get last context
            val latestContext = contextRepository.getLatestSnapshot().first()
            lastContextTime = latestContext?.timestamp ?: 0L

            // Check sensors
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            var sensors = 0
            if (sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) != null) sensors++
            if (sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null) sensors++
            if (sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_LIGHT) != null) sensors++
            if (sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY) != null) sensors++
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) != null
            ) {
                sensors++
            }
            activeSensors = sensors
        } catch (e: Exception) {
            android.util.Log.e("HabitMiner", "Failed to update notification state", e)
        }
    }

    private fun buildNotification(): Notification {
        val preferences = getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val isMissingPermissions = !preferences.getBoolean(PrefsKeys.HAS_ALL_PERMISSIONS, true)

        val title: String
        val content: String
        val actionText: String
        val actionIntent: PendingIntent

        if (isMissingPermissions) {
            title = "⚠ Collection needs attention"
            content = "Permissions missing or revoked"
            actionText = "Open App"
            val intent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            actionIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else if (isMonitoringPaused) {
            title = "HabitMiner · Paused"
            content = "Monitoring is currently paused"
            actionText = "Resume"
            val intent = Intent(this, MonitoringService::class.java).apply { action = ACTION_RESUME }
            actionIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            val h = TimeUnit.MILLISECONDS.toHours(todayScreenTimeMs)
            val m = TimeUnit.MILLISECONDS.toMinutes(todayScreenTimeMs) % 60
            val timeString = if (h > 0) "${h}h ${m}m" else "${m}m"
            val appString = if (lastUsedApp.isNotEmpty()) " · $lastUsedApp" else ""

            title = "HabitMiner · Monitoring"
            content = "🟢 $timeString today$appString"
            actionText = "Pause"
            val intent = Intent(this, MonitoringService::class.java).apply { action = ACTION_PAUSE }
            actionIntent = PendingIntent.getService(this, 2, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        val mainIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        val mainPendingIntent = PendingIntent.getActivity(this, 3, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(mainPendingIntent)
                .addAction(0, actionText, actionIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Only add InboxStyle if we are actively monitoring and have permissions
        if (!isMissingPermissions && !isMonitoringPaused) {
            val inboxStyle = NotificationCompat.InboxStyle()

            // Format duration
            val h = TimeUnit.MILLISECONDS.toHours(todayScreenTimeMs)
            val m = TimeUnit.MILLISECONDS.toMinutes(todayScreenTimeMs) % 60
            val timeString = if (h > 0) "${h}h ${m}m" else "${m}m"

            // Format freshness
            val freshness =
                if (lastContextTime > 0) {
                    val diffMs = System.currentTimeMillis() - lastContextTime
                    val diffMins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
                    if (diffMins == 0L) "Just now" else "$diffMins min ago"
                } else {
                    "Unknown"
                }

            // Learning status
            val daysOfData = preferences.getInt(PrefsKeys.DAYS_OF_DATA, 0)
            val learningStatus = if (daysOfData >= 5) "Model up to date" else "Building baseline: $daysOfData/5 days"

            inboxStyle.addLine("Status: 🟢 Active")
            inboxStyle.addLine("Usage: $timeString today")
            if (lastUsedApp.isNotEmpty()) inboxStyle.addLine("Last App: $lastUsedApp")
            inboxStyle.addLine("Context: $freshness")
            inboxStyle.addLine("Sensors: $activeSensors/5 active")
            inboxStyle.addLine("Learning: $learningStatus")

            builder.setStyle(inboxStyle)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Context Monitoring",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Continuous contextual monitoring for HabitMiner"
                    setShowBadge(false)
                }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning.value = false
        if (unlockReceiverRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            unlockReceiverRegistered = false
        }
        // DO NOT call sensorCollector.shutdown() here. It's a @Singleton so its HandlerThread
        // must outlive this service lifecycle to support DataCollectionWorker and future restarts.
        serviceScope.cancel()
        // Release wakelock (BP-1)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
