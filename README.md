# HabitMiner Extended 📱🧠

**A context-aware digital habit, sleep and routine tracker for Android**

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com/)
[![Version](https://img.shields.io/badge/Version-2.4.0--dev-blue.svg)](#-whats-new-in-extended)

HabitMiner Extended is the next version of [HabitMiner](docs/archive/README_OLD.md). It installs as its own app (**HM Extended**, `com.habitminer.extended`) next to the original, can bring the original's history across, and adds a redesigned interface, better sleep and nap tracking, goals, a long list of accuracy fixes found by using it every day, shorter daily loops (2.1), and (in 2.2) **patterns**: what pulls you into your phone, what it costs you, and whether your changes are working.

Everything stays on the phone. There is no account and no server; data leaves the device only when you export it.

---

## 📸 Screenshots

<p align="center">
  <img src="docs/screenshots/extended/overview.png" alt="HabitMiner Extended: Today, Sleep, Trends, Goals and Status" width="100%">
</p>

### New in 2.2: your patterns

<table>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/trends_overview.png" alt="Your patterns" width="250"><br>
      <b>Your patterns</b><br>
      <sub>Where the time really goes, late nights and sleep, this week against last, notifications that pull you in, the app that leads into another. Each shows only when it holds for you.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/app_hooks.png" alt="How it hooks you" width="250"><br>
      <b>How it hooks you</b><br>
      <sub>On every app's page: quick checks against long sessions and when those happen, what you were in right before, and where you go next.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/goals_impact.png" alt="Is it working" width="250"><br>
      <b>Is it working?</b><br>
      <sub>For each Use less app: how often you closed it at the pause, and its daily time against your usual four weeks before. "Too early" until there are 5 days.</sub>
    </td>
  </tr>
</table>

### New in 2.1

<table>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_app_insight.png" alt="Insight with a one-tap pause" width="250"><br>
      <b>From insight to action</b><br>
      <sub>An insight about using one app more offers "Pause before…" or "Use less" in one tap. Once it's on your list, a quiet line says so instead.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/mindful_pause.png" alt="Mindful pause with context" width="250"><br>
      <b>A pause that tells you something</b><br>
      <sub>How many times you've opened the app this hour or today, and how long a visit around now usually lasts. "Close" leads; "Open anyway" waits.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_caught_up.png" alt="All caught up" width="250"><br>
      <b>All caught up</b><br>
      <sub>When nothing needs an answer, one quiet line, the next check-in, and one of your patterns. Today now ends at about a screen and a half.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_morning.png" alt="Today in the morning" width="250"><br>
      <b>Follows your day</b><br>
      <sub>For three hours after you wake, Today leads with last night. During the day, today against a usual day.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_evening.png" alt="Today in the evening" width="250"><br>
      <b>Evening wrap-up</b><br>
      <sub>From about 2½ hours before your usual bedtime (21:00 at the latest): where you are against your target, and when you usually fall asleep.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/goals.png" alt="Goals with weekly dots" width="250"><br>
      <b>5 of the last 7 days</b><br>
      <sub>Seven dots for the target and each app, today as a ring still in progress. Rolling, so Monday never resets you to zero.</sub>
    </td>
  </tr>
</table>

### All screens

<table>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today.png" alt="Today" width="250"><br>
      <b>Today</b><br>
      <sub>Rings for screen time, pickups and phone-free time, today against a usual day at this hour, your intention, and one question or insight at a time.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_dark.png" alt="Today, dark" width="250"><br>
      <b>True-black dark mode</b><br>
      <sub>One bright colour per measure, the same everywhere it appears: pink for screen time, lime for pickups, violet for sleep.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_insight.png" alt="Insight of the day" width="250"><br>
      <b>Insight of the day</b><br>
      <sub>One thing that stood out, and why. Feedback is asked for only now and then, and steers what comes up.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/sleep.png" alt="Sleep" width="250"><br>
      <b>Sleep</b><br>
      <sub>Last night with the reasons it looks like sleep, a fix-the-times sheet, and "Were you asleep?" after a nap.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/sleep_week.png" alt="Sleep this week" width="250"><br>
      <b>This week</b><br>
      <sub>Each day's night with naps stacked on top and the nap time under the total. Tap a day for its details.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/sleep_day.png" alt="A day's sleep" width="250"><br>
      <b>A day's sleep</b><br>
      <sub>The night, each nap with its own times, and the total. Fix the night, or add, change and remove naps.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/trends_overview.png" alt="Trends" width="250"><br>
      <b>Trends</b><br>
      <sub>The last 7 days against the week before, biggest changes, today against a usual day, the week heatmap, and your surroundings.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/trends_routines.png" alt="Routines" width="250"><br>
      <b>Routines</b><br>
      <sub>How predictable your next app is, measured against simple baselines, its latest guesses, and your app sequences. Tap a routine to put a pause on one of its apps.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/trends_changes.png" alt="Changes" width="250"><br>
      <b>Changes</b><br>
      <sub>Days that differ from your usual ones, in both directions, and routine changes such as an exam week.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/app_detail.png" alt="App detail" width="250"><br>
      <b>App detail</b><br>
      <sub>Time per day, the last 14 days, when you use it, what brings you there, and a one-tap pause.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/goals_dark.png" alt="Goals" width="250"><br>
      <b>Goals</b><br>
      <sub>Soft targets: a daily intention, a phone-time target, apps to use less or more, and reminders. Nothing is blocked.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/status.png" alt="Status" width="250"><br>
      <b>Status</b><br>
      <sub>Whether collection is working, a permission checklist, and what HabitMiner itself costs in battery.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/weekly_story.png" alt="Weekly Story" width="250"><br>
      <b>Weekly Story</b><br>
      <sub>Your last seven days in a few cards, a month at a glance, and one small experiment to try.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_mood.png" alt="Daily mood check" width="250"><br>
      <b>Daily mood check</b><br>
      <sub>Mood and energy in two taps, at a time you choose, so mood can be read against phone use over time.</sub>
    </td>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/today_import.png" alt="Import" width="250"><br>
      <b>Bring your data across</b><br>
      <sub>Import an export from the original HabitMiner: app use, readings, labels and places, with routines rebuilt.</sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top" width="33%">
      <img src="docs/screenshots/extended/onboarding.png" alt="First run" width="250"><br>
      <b>First run</b><br>
      <sub>What each permission is for, in plain words, before Android asks.</sub>
    </td>
  </tr>
</table>

<sub>Rendered by the app's screenshot tests (Robolectric) with two weeks of sample data, so the numbers are illustrative. Pushes to Android source branches regenerate these screenshots. Non-PR builds publish them to the matching <code>ci-out-&lt;branch-name&gt;</code> branch; pull-request builds upload Actions artifacts only. Locally: <code>./gradlew testDebugUnitTest --tests "*ScreenshotTest*"</code>, images in <code>android/app/build/screenshots</code>.</sub>

---

## ✨ Features

**Tracking**
- **Screen time, pickups and phone-free time** from Android's usage log, with the alarm screen, launchers and system screens left out. Each day counts only the time used in it, also for sessions that run past midnight.
- **Sleep** from when the phone goes untouched at night, the step counter, darkness and charging. Quick checks, snoozed alarms and alarms you sleep through don't end the night; switching off your last alarm does. Optional Health Connect sleep and calendar busy times.
- **Naps**: daytime stretches with the phone untouched and no steps prompt "Were you asleep?". Add, change and remove naps yourself, several a day.
- **Surroundings**: light, motion (accelerometer, step counter and activity recognition), charging, headphones and, if you opt in, Wi-Fi places stored only as a salted hash.
- **Notifications**: how many pickups followed a notification, per app counts only, never the text.

**Understanding**
- **Usual days**: every day is compared with up to four weeks of days of the same kind (weekday or weekend), using medians so a few odd days don't skew "usual". Today is compared at the same time of day.
- **Changes** in both directions (total time, late nights, apps missing or spiking, unlocks, sleep) and routine changes over several days, which you can label (exams, travel, unwell, holiday) to keep them out of "usual".
- **Next-app model** that learns from what usually follows the current app, the hour, recent apps, notifications, headphones and charging, with its hit rate measured against simple baselines.
- **Your patterns**: long sessions, late nights and sleep, week-on-week changes, notifications that pull you in, and apps that lead into others, each shown only when it holds ([how they were checked](docs/FINDINGS.md)).
- **Insight of the day**, **Weekly Story**, and context insights such as screen time in the dark or after midnight.

**Acting on it**
- **Goals**: a daily intention, a phone-time target, apps to use less or more with "N of the last 7 days" dots, whether each one is working, and reminders when you're over.
- **One-tap pause** from insights, routines and app pages; **gentle nudges**, an optional **mindful pause** that shows how often you've opened the app, and **focus sessions** (both off by default), a home-screen **widget**, a **Quick Settings tile** and app **shortcuts**.
- **Quick check-ins** and **Expected / Unusual** answers are saved as ground-truth labels, with the context at the time.

**Your data**
- **Export** everything as a ZIP of CSV files with a README and schema, or a **privacy-safe** export with app names and keys replaced. **Import** exports from either app.
- **Status** shows whether collection is working and HabitMiner's own CPU, wake-lock and sensor use as a share of battery.
- **Updates**: checks for a new GitHub release once a day, verifies the download and installs it when you agree.

---

## 🆕 What's new in Extended

### 2.2: from reporting to findings
Screen time, pickups and per-app time are what Android's Digital Wellbeing already shows. 2.2 adds what it can't: patterns that connect time of day, app order, notifications and sleep, found in your own data.
- **Your patterns** (Trends → Overview, and one on Today once you're caught up):
  - **Where the time goes**: when a few long sessions (15+ min) make up most of an app's time, and when they happen.
  - **Late weekday nights and sleep**: how much less you sleep on weekday nights you go to bed late (weekends are left out, because sleeping in hides the effect).
  - **This week vs the week before**, in total and for the apps that changed most.
  - **Notifications that pull you in**: only for apps where a notification leads to a long session at least twice as often as usual.
  - **How you get there**: a social, video or game app you mostly open right after another app.
  - **Checking all day**, **last night's longest late session**, and **a run of quiet nights**; plus "busiest hours" and "first app of the morning", shown only when they're clear.
- **Act on it in place**: a pause on that app (only after a set time at night when the long sessions are late), the app's notification settings, or your nights on Sleep.
- **How it hooks you** on each app's page: quick checks vs long sessions, what comes right before, first app after unlocking, and where you go next.
- **Is it working?** on Goals for each Use less app: pauses closed vs opened anyway, and daily time against the four weeks before, leaving out the unusual day that usually prompts a goal.
- Every pattern was checked on two real exports before it was built; what held up, what was dropped and why is in [docs/FINDINGS.md](docs/FINDINGS.md).

### 2.1: shorter daily loops
- **Insight → action**: insights and differences about using one app more, each routine in Trends → Routines, and every app's detail page offer **Pause before opening** or **Use less** in one tap. Apps already on your list show "Pause on before Instagram" with an Edit link instead of the same buttons again, and if Android's Accessibility switch for the pause is off, the button takes you there.
- **A mindful pause with context**: "This is your 6th time in Instagram in the last hour" and "Around this time, a visit usually lasts about 20m", from one quick query so the timer shows at once. **Close [app]** is the main button; **Open anyway** is secondary and waits for the timer.
- **A shorter Today**: one raised card at a time (nap question, mood, insight, difference or routine change), and **All caught up** with the next check-in when nothing needs an answer. The hero follows your own day: last night for three hours after you wake, the comparison with a usual day after that, and a wrap-up from about 2½ hours before your usual bedtime (21:00 at the latest). Last night lives on Sleep, Around you on Trends, and Apps today shows the top three.
- **Weekly goal counts**: "Under target on 5 of the last 7 days" with seven dots, today as a ring in progress, for the phone-time target and for each Use less / Use more app against its previous week.
- **Widget offer**: once, after two days of data, and only if the launcher supports pinning and no Today widget is placed yet, Today offers to add it.

The README for 2.0 is kept in [docs/archive/README_2.0.md](docs/archive/README_2.0.md).

### 2.0: redesign and new features
- **Vivid redesign**: activity rings on Today, one bright colour per measure, true-black dark mode, coloured icon tiles and gradient charts, five tabs (Today, Trends, Sleep, Goals, Status) each with its own colour, and the Mona Sans typeface.
- **Separate app** (`com.habitminer.extended`, "HM Extended") that can run next to the original, plus an import card on Today to bring your HabitMiner history across.
- **Sleep accuracy**: fix-a-night (and HabitMiner learns how long you usually take to fall asleep), sleep evidence, Do Not Disturb, alarm and glance signals, optional Health Connect sleep and calendar busy times.
- **Engagement**: insight of the day, questions sent at moments you're likely to answer, reminders, Weekly Story, mood check-ins.
- **Control**: widget, Quick Settings tile, shortcuts, pause and breaks, choosing data sources, and a guide for Android's restricted settings.
- **Battery**: readings driven by alarms instead of a long wake lock, an incremental usage log, one shared analysis for every screen, and a battery-cost stat on Status.
- **Export and updates**: insight log and self-health in exports, a privacy-safe option, daily release check with verified install, and a release workflow.

### Fixes from daily use (B1–B7)
Seven bugs reported while using the app day to day, all fixed and checked against two full exports. Details, and before/after numbers, are in [docs/BUGS.md](docs/BUGS.md).

| | Problem | What changed |
|---|---|---|
| B1 | A quiet alarm nobody heard counted as waking up, and as screen time | An alarm screen over the lock screen is no longer app use. A quickly snoozed alarm is a brief wake-up; one that rang out unheard isn't. |
| B2 | Day totals missed use that ran past midnight | Sessions are split at midnight, so every total agrees with the heatmap. Stored data is repaired once. |
| B3 | "On the move 307 times" | Needs the step counter or activity recognition, not a phone moved in your hand, and counts trips. |
| B4 | A nap wasn't found, no nap notification, no way to add naps | Better nap detection, "Were you asleep?" sent for any nap of an hour or more, and naps added by hand (several a day). |
| B5 | Week view lost per-day night and nap details | "This week" shows nap time per day; tap a day for the night, each nap and its times. |
| B6 | Wake-up shown in the future | The learned adjustment can't pass now or your first real use; switching off your last alarm ends the night. |
| B7 | Insight card kept asking for feedback; mood check rare | Thanks with Undo, then hidden for the day; feedback asked only now and then; mood check at a time you choose. |

On the nights with corrected times, wake-up errors went from up to 163 minutes off to at most 20.

---

## 🏗️ Architecture

- **UI**: Jetpack Compose and Material 3 (Compose BOM 2025.12, Material 3 1.4), adaptive navigation, Glance widget.
- **State**: unidirectional data flow with `StateFlow` and a Hilt `ViewModel`.
- **Data**: Room 2.8 with an exported schema, repositories, and SharedPreferences for settings.
- **Background**: a foreground service for collection with WorkManager as a fallback, and alarm-driven sensor readings.
- **Analytics**: plain Kotlin in `analytics/` (sleep and nap detection, usual days, changes, next-app model, insights), with no Android dependencies so it's unit-tested directly.
- **Build**: Kotlin 2.3, AGP 8.11, Hilt 2.57, KSP, minSdk 26, targetSdk 35, compileSdk 36, R8 for release builds.

```text
android/app/src/main/java/com/habitminer/
├── analytics/    pure Kotlin: sleep, naps, usual days, changes, routines, insights
├── collection/   usage log reader, sensors, device events (unlocks, alarms, Do Not Disturb)
├── data/         Room entities and DAOs, import/export
├── di/           Hilt dependency-injection modules
├── domain/       app identity and domain-level rules
├── engine/       analysis pipeline, sequential pattern mining and ViewModels
├── goals/        targets, intentions and reminders
├── mindful/      mindful pause and focus sessions
├── proactive/    questions, nudges, reminders, notifications
├── repository/   access to stored data for the rest of the app
├── sources/      external and optional data-source integrations
├── ui/           Compose screens and the design system
├── update/       release check and install
└── widget/       home-screen widget
```

More background on the original design is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/METHODOLOGY.md](docs/METHODOLOGY.md). See the [repository structure and branch guide](docs/REPOSITORY_GUIDE.md) for the code map and branch responsibilities.

---

## 🚀 Getting started

### Install
Pushes to Android source branches run the build and unit-test workflow. Non-PR builds publish screenshots and APKs to the corresponding `ci-out-<branch-name>` branch; pull-request builds upload APKs as workflow artifacts. Release builds are published from version tags such as `v2.4.0`.

To bring your history across: in HabitMiner, open Settings and tap **Export data**, then share the ZIP to **HM Extended** (or use **Choose file** on the Today import card).

### Build
```bash
cd android

./gradlew testDebugUnitTest      # unit and screenshot tests
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (R8)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 21 and a physical Android 8.0+ device; emulators don't produce realistic usage or sensor data.

### Permissions
- **Usage access**: which apps you use and when.
- **Notification access**: notification counts per app, never their text.
- **Physical activity**: steps and activity recognition, to tell walking from sitting.
- **Battery optimisation off**: so Android doesn't stop collection in the background.
- **Optional**: Health Connect (sleep), calendar (busy times), location (only to see the Wi-Fi network for places; no location is recorded).

---

## 📄 License
Distributed under the MIT License. The README for HabitMiner 1.3.1 is kept as [README_OLD.md](README_OLD.md), and the one for Extended 2.0 as [README_2.0.md](README_2.0.md).
