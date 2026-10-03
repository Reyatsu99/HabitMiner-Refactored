package com.habitminer.collection

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.habitminer.data.ContextSnapshotEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.sqrt

data class MotionStats(
    val mean: Float,
    val variance: Float,
    val std: Float,
    val min: Float,
    val max: Float,
    val energy: Float,
)

@Singleton
class SensorContextCollector
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val stepCounterMonitor: StepCounterMonitor,
    ) {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val sensorThread = android.os.HandlerThread("SensorThread").apply { start() }
        private val sensorHandler = Handler(sensorThread.looper)

        /** Called by MonitoringService.onDestroy() to stop the background thread cleanly. */
        fun shutdown() {
            sensorThread.quitSafely()
        }

        suspend fun collectSnapshot(
            unlockCount: Int,
            isScreenOn: Boolean,
            notificationsLastHour: Int,
            collectSensors: Boolean,
            batteryLevel: Int,
            isCharging: Boolean,
            wifiPlace: String? = null,
        ): ContextSnapshotEntity {
            val timestamp = System.currentTimeMillis()
            var sensingMs = 0L

            var lightLux = -1f
            var accelStats: MotionStats? = null
            var gyroStats: MotionStats? = null
            var proximityNear: Boolean? = null
            var stepsDelta = -1

            if (collectSensors) {
                val sensingStart = android.os.SystemClock.elapsedRealtime()
                kotlinx.coroutines.coroutineScope {
                    val lightDeferred = async { collectLightLevel() }
                    val accelDeferred = async { collectMotionState(Sensor.TYPE_ACCELEROMETER) }
                    val gyroDeferred = async { collectMotionState(Sensor.TYPE_GYROSCOPE) }
                    val proxDeferred = async { collectProximityState() }

                    lightLux = lightDeferred.await() ?: -1f
                    accelStats = accelDeferred.await()
                    gyroStats = gyroDeferred.await()
                    proximityNear = proxDeferred.await()
                }
                sensingMs = android.os.SystemClock.elapsedRealtime() - sensingStart
            }

            // Steps come from the always-on step counter, so they are recorded whether or not
            // the screen is on (the other sensors are only sampled with the screen on).
            stepsDelta = collectStepDelta()

            return ContextSnapshotEntity(
                timestamp = timestamp,
                accelMean = accelStats?.mean ?: -1f,
                accelVariance = accelStats?.variance ?: -1f,
                accelStd = accelStats?.std ?: -1f,
                accelMin = accelStats?.min ?: -1f,
                accelMax = accelStats?.max ?: -1f,
                accelEnergy = accelStats?.energy ?: -1f,
                gyroMean = gyroStats?.mean ?: -1f,
                gyroVariance = gyroStats?.variance ?: -1f,
                gyroStd = gyroStats?.std ?: -1f,
                gyroMin = gyroStats?.min ?: -1f,
                gyroMax = gyroStats?.max ?: -1f,
                gyroEnergy = gyroStats?.energy ?: -1f,
                lightLux = lightLux,
                proximityNear = proximityNear,
                stepsSinceLastSnapshot = stepsDelta,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                isScreenOn = isScreenOn,
                unlockCount = unlockCount,
                notificationsLastHour = notificationsLastHour,
                wifiPlace = wifiPlace,
                sensingMs = sensingMs,
            )
        }

        private suspend fun collectLightLevel(): Float? =
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine { continuation ->
                    val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
                    if (lightSensor == null) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    val listener =
                        object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent?) {
                                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                                    sensorManager.unregisterListener(this)
                                    if (continuation.isActive) {
                                        continuation.resume(event.values[0])
                                    }
                                }
                            }

                            override fun onAccuracyChanged(
                                sensor: Sensor?,
                                accuracy: Int,
                            ) {}
                        }

                    val registered = sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
                    if (!registered) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation {
                        sensorManager.unregisterListener(listener)
                    }
                }
            }

        private suspend fun collectMotionState(sensorType: Int): MotionStats? =
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine { continuation ->
                    val sensor = sensorManager.getDefaultSensor(sensorType)
                    if (sensor == null) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    val samples = mutableListOf<Float>()

                    val listener =
                        object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent?) {
                                if (event?.sensor?.type == sensorType) {
                                    val x = event.values[0]
                                    val y = event.values[1]
                                    val z = event.values[2]
                                    val magnitude = sqrt(x * x + y * y + z * z)
                                    samples.add(magnitude)

                                    if (samples.size >= 12) {
                                        sensorManager.unregisterListener(this)
                                        if (continuation.isActive) {
                                            val mean = samples.average().toFloat()
                                            var variance = 0f
                                            for (v in samples) {
                                                variance += (v - mean) * (v - mean)
                                            }
                                            variance /= samples.size
                                            val std = sqrt(variance)
                                            val min = samples.minOrNull() ?: 0f
                                            val max = samples.maxOrNull() ?: 0f
                                            val energy = samples.map { it * it }.average().toFloat()

                                            val stats =
                                                MotionStats(
                                                    mean = mean,
                                                    variance = variance,
                                                    std = std,
                                                    min = min,
                                                    max = max,
                                                    energy = energy,
                                                )
                                            continuation.resume(stats)
                                        }
                                    }
                                }
                            }

                            override fun onAccuracyChanged(
                                sensor: Sensor?,
                                accuracy: Int,
                            ) {}
                        }

                    // SENSOR_DELAY_UI (~60ms) is more battery-efficient than SENSOR_DELAY_GAME (20ms)
                    // for background 15-min sampling; still collects 12 samples within 2s timeout (BP-6)
                    val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI, sensorHandler)
                    if (!registered) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation {
                        sensorManager.unregisterListener(listener)
                    }
                }
            }

        private suspend fun collectProximityState(): Boolean? =
            withTimeoutOrNull(2000L) {
                suspendCancellableCoroutine { continuation ->
                    val proxSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
                    if (proxSensor == null) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }

                    val listener =
                        object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent?) {
                                if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
                                    sensorManager.unregisterListener(this)
                                    if (continuation.isActive) {
                                        val distance = event.values[0]
                                        val isNear = distance < proxSensor.maximumRange
                                        continuation.resume(isNear)
                                    }
                                }
                            }

                            override fun onAccuracyChanged(
                                sensor: Sensor?,
                                accuracy: Int,
                            ) {}
                        }

                    val registered = sensorManager.registerListener(listener, proxSensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
                    if (!registered) {
                        continuation.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    continuation.invokeOnCancellation {
                        sensorManager.unregisterListener(listener)
                    }
                }
            }

        private suspend fun collectStepDelta(): Int {
            if (!stepCounterMonitor.hasPermission() || !stepCounterMonitor.hasSensor()) return -1
            stepCounterMonitor.start()
            stepCounterMonitor.takeDeltaSinceLastSnapshot()?.let { return it }

            // The monitor hasn't heard from the counter yet (no steps since the app started).
            // Some phones report the current total on registration, so try a short read.
            val counter =
                withTimeoutOrNull(2000L) {
                    suspendCancellableCoroutine<Long?> { continuation ->
                        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                        if (stepSensor == null) {
                            continuation.resume(null)
                            return@suspendCancellableCoroutine
                        }
                        val listener =
                            object : SensorEventListener {
                                override fun onSensorChanged(event: SensorEvent?) {
                                    if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
                                        sensorManager.unregisterListener(this)
                                        if (continuation.isActive) continuation.resume(event.values[0].toLong())
                                    }
                                }

                                override fun onAccuracyChanged(
                                    sensor: Sensor?,
                                    accuracy: Int,
                                ) {}
                            }
                        if (!sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)) {
                            continuation.resume(null)
                            return@suspendCancellableCoroutine
                        }
                        continuation.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                    }
                }
            if (counter == null) return -1
            stepCounterMonitor.record(counter)
            return stepCounterMonitor.takeDeltaSinceLastSnapshot() ?: -1
        }
    }
