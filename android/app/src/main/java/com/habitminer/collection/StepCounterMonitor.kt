package com.habitminer.collection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.habitminer.analytics.StepMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a listener on the hardware step counter for as long as the app process lives.
 *
 * The step counter only reports when a step happens, and many phones send nothing on
 * registration, so the old "listen for 2 seconds while the screen is on" approach almost
 * always timed out. The counter runs in low-power hardware and batches its reports
 * (here up to 1 minute), so keeping it registered costs very little battery.
 */
@Singleton
class StepCounterMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SensorEventListener {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val prefs = context.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)

        private val _stepsToday = MutableStateFlow(restoreState()?.takeIf { it.day == LocalDate.now() }?.stepsToday ?: -1L)

        /** Steps counted today, or -1 when no reading has arrived yet. */
        val stepsToday: StateFlow<Long> = _stepsToday.asStateFlow()

        @Volatile private var registered = false

        @Volatile private var latestCounter: Long = prefs.getLong(KEY_LAST, -1L)

        fun hasSensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        fun hasPermission(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        /** Starts listening if possible; safe to call repeatedly. Returns whether it is listening. */
        @Synchronized
        fun start(): Boolean {
            if (registered) return true
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false
            if (!hasPermission()) return false
            registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL, MAX_LATENCY_US)
            return registered
        }

        @Synchronized
        fun stop() {
            if (registered) sensorManager.unregisterListener(this)
            registered = false
        }

        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
            record(event.values[0].toLong())
        }

        override fun onAccuracyChanged(
            sensor: Sensor?,
            accuracy: Int,
        ) = Unit

        /** Feeds a counter value (also used by the one-off read fallback). */
        @Synchronized
        fun record(counter: Long) {
            latestCounter = counter
            val state = StepMath.advance(restoreState(), LocalDate.now(), counter)
            prefs.edit()
                .putString(KEY_DAY, state.day.toString())
                .putLong(KEY_BASE, state.base)
                .putLong(KEY_CARRIED, state.carried)
                .putLong(KEY_LAST, state.last)
                .apply()
            _stepsToday.value = state.stepsToday
        }

        /**
         * Steps since the previous context snapshot, or null if the counter hasn't reported
         * yet. Each call moves the snapshot marker forward.
         */
        @Synchronized
        fun takeDeltaSinceLastSnapshot(): Int? {
            val current = latestCounter.takeIf { it >= 0 } ?: return null
            val previous = prefs.getLong(KEY_SNAPSHOT, -1L).takeIf { it >= 0 }
            prefs.edit().putLong(KEY_SNAPSHOT, current).apply()
            return StepMath.delta(previous, current).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        /** Re-checks the date so "today" resets after midnight even without new steps. */
        fun refreshDay() {
            val state = restoreState() ?: return
            if (state.day != LocalDate.now()) _stepsToday.value = 0L
        }

        private fun restoreState(): StepMath.DayState? {
            val day = prefs.getString(KEY_DAY, null) ?: return null
            return runCatching {
                StepMath.DayState(
                    day = LocalDate.parse(day),
                    base = prefs.getLong(KEY_BASE, 0L),
                    carried = prefs.getLong(KEY_CARRIED, 0L),
                    last = prefs.getLong(KEY_LAST, 0L),
                )
            }.getOrNull()
        }

        companion object {
            private const val MAX_LATENCY_US = 60_000_000 // batch reports for up to 1 minute
            private const val KEY_DAY = "day"
            private const val KEY_BASE = "base"
            private const val KEY_CARRIED = "carried"
            private const val KEY_LAST = "last"
            private const val KEY_SNAPSHOT = "snapshot_counter"
        }
    }
