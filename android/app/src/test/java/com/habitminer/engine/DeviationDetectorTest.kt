package com.habitminer.engine

import com.habitminer.data.AppUsageEntity
import com.habitminer.data.BaselineEntity
import com.habitminer.data.ContextSnapshotEntity
import com.habitminer.domain.AppIdentityResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Calendar

/**
 * The clock is pinned to Wednesday 2026-10-07 11:30 (a weekday morning), so the
 * detector's time-of-day logic is deterministic and every assertion actually runs.
 */
class DeviationDetectorTest {
    private lateinit var deviationDetector: DeviationDetector

    private val now: Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 7, 11, 30, 0)
        }.timeInMillis

    private val morningStart: Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 7, 9, 0, 0)
        }.timeInMillis

    @Before
    fun setup() {
        val resolver = mock(AppIdentityResolver::class.java)
        `when`(resolver.isLauncher(org.mockito.ArgumentMatchers.anyString())).thenReturn(false)
        val clock =
            object : TimeProvider() {
                override fun currentTimeMillis(): Long = now
            }
        deviationDetector = DeviationDetector(resolver, clock)
    }

    private fun baseline(
        accelEnergy: Float = 10f,
        lightLux: Float = 100f,
    ) = BaselineEntity(
        timeBin = "WEEKDAY_MORNING",
        avgScreenTimeMs = 30 * 60 * 1000L,
        stdScreenTimeMs = 5 * 60 * 1000L,
        avgSessionCount = 5f,
        stdSessionCount = 1f,
        avgUnlockCount = 5f,
        typicalCategoriesJson = "{\"Instagram\": 1800000}",
        avgAccelEnergy = accelEnergy,
        avgLightLux = lightLux,
        updatedAt = 0L,
        dataPointCount = 5,
    )

    @Test
    fun `flags excess screen time against the expected amount by now`() {
        val results = deviationDetector.detectDeviations(listOf(usage("Instagram", 60)), emptyList(), listOf(baseline()))

        val dev = results.single { it.deviationType == "EXCESS_DURATION" }
        assertTrue(dev.zScore > 1.5f)
        assertEquals("Instagram", dev.affectedCategory)
        // Occurrence time is when the usage ended, not when detection ran.
        assertEquals(morningStart + 60 * 60_000L, dev.occurredAt)
    }

    @Test
    fun `flags an app that is new to this time slot`() {
        val results = deviationDetector.detectDeviations(listOf(usage("Clash of Clans", 10)), emptyList(), listOf(baseline()))

        val dev = results.single { it.deviationType == "NEW_BEHAVIOR" }
        assertEquals("Clash of Clans", dev.affectedCategory)
        assertEquals(morningStart, dev.occurredAt)
    }

    @Test
    fun `ignores brief use of a new app`() {
        val results = deviationDetector.detectDeviations(listOf(usage("Clash of Clans", 5)), emptyList(), listOf(baseline()))

        assertFalse(results.any { it.deviationType == "NEW_BEHAVIOR" })
    }

    @Test
    fun `detects a shift from active to still`() {
        val contexts = listOf(context(9, 30, accelEnergy = 0f), context(10, 30, accelEnergy = 0f))
        val results = deviationDetector.detectDeviations(listOf(usage("Instagram", 10)), contexts, listOf(baseline(accelEnergy = 12f)))

        val dev = results.firstOrNull { it.deviationType == "CONTEXT_SHIFT" }
        assertNotNull(dev)
        assertEquals("ALL", dev!!.affectedCategory)
    }

    @Test
    fun `missing sensor readings never count as dark or still`() {
        // -1 means "no reading" (screen was off). These must not produce context shifts.
        val contexts = listOf(context(9, 30, accelEnergy = -1f, lightLux = -1f), context(10, 30, accelEnergy = -1f, lightLux = -1f))
        val results =
            deviationDetector.detectDeviations(
                listOf(usage("Instagram", 10)),
                contexts,
                listOf(baseline(accelEnergy = 12f, lightLux = 2000f)),
            )

        assertFalse(results.any { it.deviationType == "CONTEXT_SHIFT" })
    }

    private fun usage(
        appName: String,
        minutes: Long,
    ) = AppUsageEntity(
        id = 0,
        packageName = "com.test.${appName.replace(" ", "")}",
        appName = appName,
        appCategory = "TEST",
        startTime = morningStart,
        endTime = morningStart + minutes * 60_000L,
        durationMs = minutes * 60_000L,
        timeSlot = "MORNING",
        dayType = "WEEKDAY",
    )

    private fun context(
        hour: Int,
        minute: Int,
        accelEnergy: Float = 0f,
        lightLux: Float = 100f,
    ) = ContextSnapshotEntity(
        id = 0,
        timestamp =
            Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.OCTOBER, 7, hour, minute, 0)
            }.timeInMillis,
        accelMean = 0f,
        accelVariance = 0f,
        accelStd = 0f,
        accelMin = 0f,
        accelMax = 0f,
        accelEnergy = accelEnergy,
        gyroMean = 0f,
        gyroVariance = 0f,
        gyroStd = 0f,
        gyroMin = 0f,
        gyroMax = 0f,
        gyroEnergy = 0f,
        lightLux = lightLux,
        proximityNear = false,
        stepsSinceLastSnapshot = 0,
        batteryLevel = 100,
        isCharging = false,
        isScreenOn = true,
        unlockCount = 1,
        notificationsLastHour = 0,
    )
}
