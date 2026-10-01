package com.habitminer.collection

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun startServices() {
        // Schedule periodic background sync
        DataCollectionWorker.schedulePeriodicWork(context)

        // Start Foreground Service for real-time monitoring
        val serviceIntent = Intent(context, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("HabitServiceManager", "Could not start MonitoringService: ${e.message}")
        }
    }

    fun stopServices() {
        // Cancel all periodic work
        WorkManager.getInstance(context).cancelAllWork()
        
        // Stop foreground service
        val serviceIntent = Intent(context, MonitoringService::class.java)
        context.stopService(serviceIntent)
    }
}
