package com.habitminer.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.habitminer.analytics.CheckInOption
import com.habitminer.analytics.TimeUtil
import com.habitminer.analytics.UsageSummaries
import com.habitminer.engine.HabitActions
import com.habitminer.goals.Goals
import com.habitminer.ui.goals.GoalsScreen
import com.habitminer.ui.sleep.SleepScreen
import com.habitminer.ui.status.StatusNavigation
import com.habitminer.ui.status.StatusScreen
import com.habitminer.ui.theme.HabitMinerTheme
import com.habitminer.ui.today.TodayNavigation
import com.habitminer.ui.today.TodayScreen
import com.habitminer.ui.trends.AppDetailContent
import com.habitminer.ui.trends.AppDetailState
import com.habitminer.ui.trends.TrendsScreen
import com.habitminer.ui.trends.TrendsTab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * Renders each main screen with realistic sample data, in light and dark, and saves a PNG to
 * app/build/screenshots. CI publishes them with the APK for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2000dp-xhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private object NoActions : HabitActions {
        override fun checkPermissions() = Unit

        override fun loadHistoricalData() = Unit

        override fun answerCheckIn(option: CheckInOption) = Unit

        override fun dismissCheckIn() = Unit

        override fun giveDeviationFeedback(
            key: String,
            value: String,
        ) = Unit

        override fun answerNap(
            key: String,
            asleep: Boolean,
        ) = Unit

        override fun labelPeriod(
            key: String,
            from: java.time.LocalDate,
            value: String,
        ) = Unit

        override fun selectHistoryDate(timeInMillis: Long) = Unit
    }

    private object NoNav : TodayNavigation, StatusNavigation {
        override fun openSettings() = Unit

        override fun openSleep() = Unit

        override fun openApps() = Unit

        override fun openChanges() = Unit

        override fun openApp(packageName: String) = Unit

        override fun openPermissions() = Unit
    }

    private lateinit var data: SampleData.Data

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(SampleData.zone))
        // A Saturday evening.
        val now = TimeUtil.at(java.time.LocalDate.of(2026, 10, 3), 18, 45, SampleData.zone)
        data = SampleData.build(now)
        registerSensors()
    }

    /** The first two apps are on Use less with the pause working, as in the Goals shots. */
    private val guards: com.habitminer.ui.design.AppGuards
        get() =
            com.habitminer.ui.design.AppGuards(
                useLess = data.state.appSummaries.take(2).map { it.packageName }.toSet(),
                pauseEnabled = true,
                serviceOn = true,
                onGuard = { _, _ -> },
                onOpenGoals = {},
            )

    private fun shoot(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        rule.setContent {
            HabitMinerTheme(darkTheme = dark, wallpaperColors = false) {
                androidx.compose.runtime.CompositionLocalProvider(com.habitminer.ui.design.LocalAppGuards provides guards) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val out = File("build/screenshots/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun todayLight() = shoot("01_today_light", dark = false) { TodayScreen(data.state, NoActions, NoNav, intention = "Finish the lab report", dailyTargetMinutes = 240, now = data.now) }

    @Test
    fun todayDark() = shoot("02_today_dark", dark = true) { TodayScreen(data.state, NoActions, NoNav, now = data.now) }

    @Test
    fun todayCheckIn() = shoot("03_today_checkin", dark = false) { TodayScreen(data.state.copy(pendingCheckInPromptedAt = data.now), NoActions, NoNav, now = data.now) }

    @Test
    fun trendsOverview() = shoot("04_trends_overview", dark = true) { TrendsScreen(data.state, NoActions, TrendsTab.OVERVIEW, onOpenApp = {}) }

    @Test
    fun trendsApps() = shoot("05_trends_apps", dark = false) { TrendsScreen(data.state, NoActions, TrendsTab.APPS, onOpenApp = {}) }

    @Test
    fun trendsRoutines() = shoot("06_trends_routines", dark = false) { TrendsScreen(data.state, NoActions, TrendsTab.ROUTINES, onOpenApp = {}) }

    @Test
    fun trendsChanges() = shoot("07_trends_changes", dark = true) { TrendsScreen(data.state, NoActions, TrendsTab.CHANGES, onOpenApp = {}) }

    @Test
    fun trendsHistory() = shoot("08_trends_history", dark = false) { TrendsScreen(data.state, NoActions, TrendsTab.HISTORY, onOpenApp = {}) }

    @Test
    fun sleepLight() = shoot("09_sleep_light", dark = false) { SleepScreen(data.state, NoActions) }

    @Test
    fun sleepDark() = shoot("10_sleep_dark", dark = true) { SleepScreen(data.state, NoActions) }

    @Test
    fun goals() =
        shoot("11_goals", dark = false) {
            val apps = data.state.appSummaries
            GoalsScreen(
                goals =
                    Goals(
                        dailyTargetMinutes = 240,
                        useLess = apps.take(2).map { it.packageName },
                        useMore = apps.drop(5).take(1).map { it.packageName },
                        reminderMinutes = 30,
                        intention = "Finish the lab report",
                        intentionDate = java.time.LocalDate.of(2026, 10, 3),
                    ),
                apps = apps,
                dailyTotals = data.state.dailyTotals,
                todayMs = data.state.todayScreenTimeMs,
                onChange = {},
                onOpenApp = {},
                today = java.time.LocalDate.of(2026, 10, 3),
            )
        }

    @Test
    fun status() = shoot("12_status", dark = true) { StatusScreen(data.state, NoNav) }

    @Test
    fun appDetail() =
        shoot("13_app_detail", dark = false) {
            val pkg = data.state.appSummaries.first().packageName
            val detail = UsageSummaries.detail(data.sessions.filter { it.packageName == pkg }, emptyList(), java.time.LocalDate.of(2026, 10, 3), SampleData.zone)
            AppDetailContent(pkg, AppDetailState(loading = false, detail = detail, routines = listOf("Snapchat → Telegram")), onBack = {})
        }

    @Test
    fun todayInsight() =
        shoot("14_today_insight", dark = true) {
            val insight =
                com.habitminer.repository.TodayInsight(
                    id = 1,
                    key = "k",
                    family = com.habitminer.analytics.InsightFamily.RECORDS,
                    title = "4h 20m phone-free, your longest in 27 days",
                    body = "From 08:10 to 12:30 today, without unlocking.",
                    why = "Your longest stretch without using the phone while awake, against each day of the last four weeks.",
                    effect = 2.5f,
                    open = "trends",
                    notifiedAt = null,
                    feedback = null,
                )
            val state = data.state.copy(insights = data.state.insights?.copy(naps = emptyList(), insight = insight))
            TodayScreen(state, NoActions, NoNav, now = data.now)
        }

    @Test
    fun todayMood() = shoot("15_today_mood", dark = false) { TodayScreen(data.state.copy(askMood = true), NoActions, NoNav, now = data.now) }

    @Test
    fun weeklyStory() =
        shoot("16_weekly_story", dark = false) {
            val story =
                com.habitminer.analytics.WeeklyStoryBuilder.build(
                    sessions = data.sessions,
                    unlocks = data.unlocks,
                    nights = data.state.insights?.sleepNights.orEmpty(),
                    naps = data.state.insights?.confirmedNaps.orEmpty(),
                    patterns = data.state.insights?.patternGroups.orEmpty(),
                    dailyTargetMinutes = 240,
                    useLess = data.state.appSummaries.take(1).map { it.packageName }.toSet(),
                    today = java.time.LocalDate.of(2026, 10, 3),
                    now = data.now,
                    zone = SampleData.zone,
                )
            com.habitminer.ui.story.StoryContent(com.habitminer.ui.story.StoryState(loading = false, story = story), onBack = {})
        }

    @Test
    fun weeklyStoryDark() =
        shoot("17_weekly_story_dark", dark = true) {
            val story =
                com.habitminer.analytics.WeeklyStoryBuilder.build(
                    sessions = data.sessions,
                    unlocks = data.unlocks,
                    nights = data.state.insights?.sleepNights.orEmpty(),
                    naps = data.state.insights?.confirmedNaps.orEmpty(),
                    patterns = data.state.insights?.patternGroups.orEmpty(),
                    dailyTargetMinutes = 240,
                    useLess = data.state.appSummaries.take(1).map { it.packageName }.toSet(),
                    today = java.time.LocalDate.of(2026, 10, 3),
                    now = data.now,
                    zone = SampleData.zone,
                )
            com.habitminer.ui.story.StoryContent(com.habitminer.ui.story.StoryState(loading = false, story = story), onBack = {})
        }

    @Test
    fun onboarding() =
        shoot("18_onboarding", dark = true) {
            com.habitminer.ui.PermissionScreen(
                hasUsage = true,
                hasRuntime = false,
                hasNotification = false,
                onRequestUsage = {},
                onRuntimePermissionsGranted = {},
                onRequestNotification = {},
                onImport = {},
            )
        }

    @Test
    fun goalsDark() =
        shoot("19_goals_dark", dark = true) {
            val apps = data.state.appSummaries
            com.habitminer.ui.goals.GoalsScreen(
                goals = com.habitminer.goals.Goals(dailyTargetMinutes = 300, useLess = apps.take(2).map { it.packageName }, useMore = apps.drop(5).take(1).map { it.packageName }, reminderMinutes = 30),
                apps = apps,
                dailyTotals = data.state.dailyTotals,
                todayMs = data.state.todayScreenTimeMs,
                onChange = {},
                onOpenApp = {},
                today = java.time.LocalDate.of(2026, 10, 3),
            )
        }

    @Test
    fun todayImport() =
        shoot("20_today_import", dark = false) {
            TodayScreen(data.state.copy(showImportCard = true, oldAppInstalled = true), NoActions, NoNav, now = data.now)
        }

    @Test
    fun todayImported() =
        shoot("21_today_imported", dark = true) {
            TodayScreen(
                data.state.copy(
                    importStatus =
                        com.habitminer.engine.ImportStatus(
                            running = false,
                            message = "Imported 4,355 app sessions, 431 surroundings readings, 9 labels and 5 places. Your routines are being rebuilt from them.",
                        ),
                ),
                NoActions,
                NoNav,
                now = data.now,
            )
        }

    @Test
    fun sleepDay() =
        shoot("22_sleep_day", dark = true) {
            val ins = data.state.insights!!
            val date = java.time.LocalDate.of(2026, 10, 2)
            val day = ins.sleepDays.first { it.date == date }
            com.habitminer.ui.sleep.DaySleepContent(
                day = day,
                naps = ins.napRecords.filter { TimeUtil.dateOf(it.start, SampleData.zone) == date },
                questions = emptyList(),
                today = java.time.LocalDate.of(2026, 10, 3),
                zone = SampleData.zone,
                onFixNight = {},
                onAddNap = {},
                onEditNap = {},
                onRemoveNap = {},
                onAnswer = { _, _ -> },
            )
        }

    @Test
    fun todayMoodDaily() =
        shoot("23_today_mood_daily", dark = true) {
            val state = data.state.copy(moodDue = true, insights = data.state.insights?.copy(naps = emptyList()))
            TodayScreen(state, NoActions, NoNav, now = data.now)
        }

    @Test
    fun todayCaughtUp() =
        shoot("24_today_caught_up", dark = false) {
            val state = data.state.copy(widgetPrompt = true, insights = data.state.insights?.copy(naps = emptyList(), insight = null))
            TodayScreen(state, NoActions, NoNav, dailyTargetMinutes = 240, now = data.now)
        }

    @Test
    fun todayMorning() =
        shoot("25_today_morning", dark = true) {
            val wake = data.state.insights!!.lastNight!!.wakeTime
            val state = data.state.copy(insights = data.state.insights?.copy(naps = emptyList(), insight = null))
            TodayScreen(state, NoActions, NoNav, now = wake + 75 * 60_000L)
        }

    @Test
    fun todayEvening() =
        shoot("26_today_evening", dark = false) {
            val state = data.state.copy(insights = data.state.insights?.copy(naps = emptyList(), insight = null))
            TodayScreen(state, NoActions, NoNav, dailyTargetMinutes = 240, now = data.now + 3 * TimeUtil.HOUR)
        }

    @Test
    fun todayAppInsight() =
        shoot("27_today_app_insight", dark = true) {
            val app = data.state.appSummaries.drop(2).first()
            val insight =
                com.habitminer.repository.TodayInsight(
                    id = 2,
                    key = "2026-10-03|APPS|APP_SPIKE|${app.appName}",
                    family = com.habitminer.analytics.InsightFamily.APPS,
                    title = "More ${app.appName} than usual",
                    body = "1h 40m by 18:45, usually about 35 min by now. Mostly in the afternoon.",
                    why = "Compared with your usual weekend days over the last four weeks, up to this time of day.",
                    effect = 2.8f,
                    open = "changes",
                    notifiedAt = null,
                    feedback = null,
                    askFeedback = false,
                )
            val state = data.state.copy(insights = data.state.insights?.copy(naps = emptyList(), insight = insight))
            TodayScreen(state, NoActions, NoNav, now = data.now)
        }

    @Test
    fun mindfulPause() =
        shoot("28_mindful_pause", dark = true) {
            val app = data.state.appSummaries.first()
            com.habitminer.mindful.PauseScreen(
                pkg = app.packageName,
                appName = app.appName,
                seconds = 10,
                reduced = true,
                context = com.habitminer.analytics.PauseContext(visitsToday = 9, visitsLastHour = 6, usedTodayMs = 95 * 60_000L, usualVisitMs = 20 * 60_000L),
                focusUntil = null,
                onOpen = {},
                onLeave = {},
                onEndFocus = {},
            )
        }

    /** Robolectric devices have no sensors; register the five the app uses. */
    private fun registerSensors() {
        val contexts =
            listOf<android.content.Context>(
                androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>(),
                rule.activity,
            )
        for (context in contexts) {
            val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val shadow = org.robolectric.Shadows.shadowOf(sensorManager)
            listOf(
                android.hardware.Sensor.TYPE_ACCELEROMETER,
                android.hardware.Sensor.TYPE_GYROSCOPE,
                android.hardware.Sensor.TYPE_LIGHT,
                android.hardware.Sensor.TYPE_PROXIMITY,
                android.hardware.Sensor.TYPE_STEP_COUNTER,
            ).forEach { type ->
                if (sensorManager.getDefaultSensor(type) == null) {
                    shadow.addSensor(org.robolectric.shadows.ShadowSensor.newInstance(type))
                }
            }
        }
    }
}
