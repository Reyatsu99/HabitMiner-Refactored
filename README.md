# HabitMiner 📱🧠

**A Context-Aware Digital Habit & Routine Tracker for Android**

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)  
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)  
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Version](https://img.shields.io/badge/Version-1.2.0-blue.svg)](https://github.com/Reyatsu99/HabitMiner-Refactored/releases)

---

## 🎯 What is HabitMiner?
HabitMiner is an offline-first Android application designed to discover and analyze your digital habits by fusing app usage statistics with physical context sensors. 

Instead of relying on battery-draining GPS tracking or microphone recording, the modern HabitMiner engine uses a lightweight combination of:
1. **App Usage Stats**: Digital behavior via `UsageStatsManager` (Duration, Session counts).
2. **Device State**: Screen-on/unlock events and Battery state.
3. **Physical Context**: Activity Recognition (Still, Walking, Active) and Ambient Light (Lux).
4. **Notifications**: Tracks notification volumes to gauge digital interruptions.

The on-device **HabitEngine** aggregates this data to build temporal baselines (e.g., Weekday Mornings vs. Weekend Nights), discovers frequent app sequences (e.g., `Instagram → YouTube → Browser`), and detects anomalies or **Deviations** when your digital routine changes significantly.

---

## 📸 Screenshots

<p align="center">
  <img src="docs/screenshots/overview.png" alt="HabitMiner screens: Today, History, Insights, Blueprint and Data health" width="100%">
</p>

<table>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/today_overview.png" alt="Today" width="250"><br>
      <b>Today</b><br>
      <sub>Screen time against what's usual by now, today's curve vs a typical day, and top apps or categories.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/today_sleep_pickups.png" alt="Sleep &amp; pickups" width="250"><br>
      <b>Sleep &amp; pickups</b><br>
      <sub>Estimated sleep from overnight screen-off time, and how many pickups followed a notification.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/today_routines_context.png" alt="Routines &amp; surroundings" width="250"><br>
      <b>Routines &amp; surroundings</b><br>
      <sub>Your most reliable app sequences, current light, motion and battery, and the likely next app.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/today_checkin.png" alt="Quick check-in" width="250"><br>
      <b>Quick check-in</b><br>
      <sub>One-tap "what are you doing?" answers, saved as ground-truth labels for evaluation.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/history.png" alt="History timeline" width="250"><br>
      <b>History timeline</b><br>
      <sub>A 24-hour strip of app use by category, with light, motion, charging and sleep lanes.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/insights_deviations.png" alt="Unusual moments" width="250"><br>
      <b>Unusual moments</b><br>
      <sub>Deviations explained in plain language, with Expected / Unusual feedback.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/insights_routines.png" alt="Routines &amp; predictability" width="250"><br>
      <b>Routines &amp; predictability</b><br>
      <sub>Measured next-app accuracy against simple baselines, and routines grouped across time slots.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/blueprint_day_and_week.png" alt="Blueprint: day &amp; week" width="250"><br>
      <b>Blueprint: day &amp; week</b><br>
      <sub>Today vs a usual day, and a 7-day heatmap of when you use your phone (tap a square for details).</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/blueprint_day_types.png" alt="Blueprint: kinds of days" width="250"><br>
      <b>Blueprint: kinds of days</b><br>
      <sub>Days grouped with k-means, this week vs last week, and estimated sleep per night.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/blueprint_insights.png" alt="Context insights" width="250"><br>
      <b>Context insights</b><br>
      <sub>How surroundings relate to use, such as time in the dark or after midnight.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/health.png" alt="Data health" width="250"><br>
      <b>Data health</b><br>
      <sub>Collection status, battery-aware sensing mode and its cost, and live sensor status.</sub>
    </td>
    <td width="33%"></td>
  </tr>
</table>

<sub>Screens are rendered by the app's screenshot tests (Robolectric, dark theme) with two weeks of sample data, so the numbers are illustrative. Regenerate with <code>./gradlew testDebugUnitTest --tests "*ScreenshotTest*"</code>; images land in <code>android/app/build/screenshots</code>.</sub>

---

## ✨ Features

- **Dynamic App Identity**: Resolves package names to human-readable labels and intelligently filters out Launcher/Home apps from your usage statistics.
- **Predictive Modeling**: Calculates a predictability score and guesses your next likely app based on your current routine and time of day.
- **Deviation Detection**: Alerts you to unusual screen time, temporal shifts in app usage, or missing routines by calculating Z-scores against your historical baseline.
- **100% Offline & Private**: All data collection and machine learning happens on-device using Room Database. No data is sent to the cloud.
- **Battery Efficient & Resilient Monitoring**: Uses a persistent Foreground Service coupled with a fallback `WorkManager` for continuous, reliable data collection without being killed by OEM battery optimizations.
- **Robust Data Pipeline**: Intelligent session deduplication merges overlapping app events, and context sensor rate-limiting prevents database bloating.

### New in v1.2

**Pervasive sensing and inference**
- **Sleep & wake estimate**: the longest overnight stretch with the screen off, with confidence raised by charging and darkness. Also reports phone use in the hour before sleep and how much of it was in the dark.
- **Pickup triggers**: each unlock is classified as notification-driven (a notification arrived within 2 minutes) or self-initiated, with quick checks (<30 s) counted separately.
- **Context-tagged insights**: app sessions are joined with the nearest sensor reading, e.g. "45% of your Evony time is in the dark" or "used your phone while on the move 18 times".
- **Battery-aware sensing**: the sampling interval adapts (5 min when moving with the screen on, 15 min normally, 30 min when idle). Health shows how long sensors were on today.
- **Wi-Fi places (opt-in)**: screen time per place (Home / Campus / named by you), stored only as a salted hash of the access point.

**Analytics views**
- **Today**: usual-by-now comparison with a typical-day curve, apps or categories, sleep, pickups, routines, surroundings.
- **History**: a 24-hour strip of app use by category with a light/motion/charging lane and sleep, sessions summarised per app.
- **Insights → Blueprint**: week heatmap (tap for details), typical day, day types (k-means on daily usage profiles), this week vs last week, sleep, context insights, places.
- **Routines**: the same app sequence is grouped across time slots, worded as "Seen on 10 of the last 10 weekday nights".
- **Predictability**: measured next-app hit rate on the last 3 days against "most-used app" and random baselines.

**Ground-truth labels**
- **Check-ins** ("what are you doing?") up to 3 a day, 09:00–22:00, answerable from the notification.
- **Expected / Unusual** feedback on every deviation.
- Both are stored with the context at answer time and included in the ZIP export (`labels_*.csv`).

**Proactive**
- **Nudges** after 25 minutes of late-night leisure use or an hour straight in the day.
- **Weekly summary** every Sunday evening. Check-ins and nudges share a limit of 3 prompts per day; everything can be switched off in Settings.

**Quality**
- Plain-Kotlin analytics in `analytics/` with unit tests, plus screenshot tests (Robolectric) for the main screens.
- CI builds the APK and runs all tests on every push (`.github/workflows/android.yml`).

---

## 🏗️ Modern Android Architecture

HabitMiner adheres to modern Android development best practices:

- **UI Layer**: Built entirely with **Jetpack Compose** and Material Design 3.
- **State Management**: **Unidirectional Data Flow (UDF)** using `StateFlow` and `@HiltViewModel`.
- **Dependency Injection**: Fully integrated with **Dagger Hilt** to eliminate manual dependency passing and static singletons.
- **Data Layer**: **Repository Pattern** backed by **Room 2.6.1** with KSP for robust local persistence.
- **Background Tasks**: Hybrid scheduling managed via **Foreground Services** and **WorkManager 2.9.0**.
- **Code Quality**: Strict formatting enforced via **ktlint** and Spotless. **Comprehensive JUnit test suite** covering core engine logic and collection edge-cases.

```text
+---------------------+        +-------------------------+        +--------------------------+
| UI Layer (Compose)  |  <--   | ViewModel (StateFlow)   |  <--   | Domain Layer (Engines)   |
| - HomeScreen        |        | - HabitViewModel        |        | - HabitEngine            |
| - HistoryScreen     |        +-------------------------+        | - PredictionEngine       |
| - InsightsScreen    |                                           | - DeviationDetector      |
| - HealthScreen      |                                           +--------------------------+
+---------------------+                                                      ^
                                                                             |
                                                                  +--------------------------+
                                                                  | Data Layer (Repository)  |
                                                                  | - HabitRepository        |
                                                                  | - ContextRepository      |
                                                                  +--------------------------+
                                                                             ^
                                                                             |
                                                                  +--------------------------+
                                                                  | Room Database            |
                                                                  | - AppUsageEntity         |
                                                                  | - DiscoveredHabitEntity  |
                                                                  +--------------------------+
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (JDK 17 or newer).
- A physical Android device (API 26+). *Emulators may not generate realistic usage stats or sensor data.*

### Build & Run
```bash
cd android

# Format code
./gradlew spotlessApply

# Run Unit Tests
./gradlew testDebugUnitTest

# Build APK
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Shared debug key.** Debug builds are signed with `android/app/debug.keystore` (debug-only, not secret), so an APK built by CI or by any teammate installs over an existing one and keeps its data. CI publishes the latest debug APK on every push (Actions → Artifacts).

**Moving data or switching from an older build.** If Android says *"package conflicts with an existing package"*, the installed app was signed with a different key. Open the old app → Settings → **Export Data Now**, uninstall it, install the new APK, then Settings → **Import a previous export** and pick the ZIP. App usage, surroundings readings, labels and places are merged without duplicates, and routines are rebuilt.

### Permissions Required
The app requires the following permissions to function fully:
- **Usage Access**: To read app usage statistics.
- **Physical Activity**: To detect your current motion state.
- **Notifications**: To monitor digital interruptions.
- **Ignore Battery Optimizations**: Required on some OEMs to keep the background collection service alive.
- **Location (optional)**: Only if you turn on Wi-Fi places. Android requires it to reveal which Wi-Fi network you're connected to; no location is recorded.

The app provides a seamless onboarding flow via the **HealthScreen** to grant and monitor these permissions.

---

## 📄 License
Distributed under the MIT License.
