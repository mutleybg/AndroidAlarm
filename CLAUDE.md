# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A minimal single-alarm Android app written in **Java** (not Kotlin), using **XML layouts + AndroidX Views** (not Compose), built with **Gradle**. It supports exactly one alarm at a time: pick a time, enable it, and a full-screen activity rings until dismissed.

## Build & run

The build needs an Android SDK and a JDK **17–21** (the JDK 25 that is the machine default is newer than this Gradle supports; the SDK is not committed to the repo).

- **Preferred:** open the project folder in Android Studio, which bundles a compatible JDK and manages the SDK/emulator. Run/Debug builds and installs to a device or emulator.
- **Command line** (requires `ANDROID_HOME` or a `local.properties` with `sdk.dir=...`, and `JAVA_HOME` on JDK 17):
  - Build debug APK: `./gradlew :app:assembleDebug`
  - Unit tests: `./gradlew testDebugUnitTest` — single test: `./gradlew testDebugUnitTest --tests "com.example.androidalarm.SomeTest.method"`
  - Instrumented tests (needs a running device/emulator): `./gradlew connectedDebugAndroidTest`
  - Lint: `./gradlew lintDebug` (report under `app/build/reports/lint-results-debug.html`)
  - Install to device: `./gradlew installDebug`

Toolchain versions live in `app/build.gradle` (compileSdk/targetSdk 34, minSdk 26) and `build.gradle` (AGP 8.13.2); the Gradle version is pinned in `gradle/wrapper/gradle-wrapper.properties` (8.13).

## Architecture

The whole app lives in `app/src/main/java/com/example/androidalarm/`. The key design point is that **`AlarmScheduler` is the single source of truth** — every other class delegates to it rather than touching `AlarmManager` or persistence directly.

- **`AlarmScheduler`** (static helper) — schedules/cancels the one alarm via `AlarmManager.setAlarmClock`, and persists `hour`/`minute`/`enabled` to `SharedPreferences` ("alarm_prefs"). Persistence exists because `AlarmManager` forgets all alarms across reboots. A single fixed `REQUEST_CODE` is used so there is only ever one `PendingIntent`.
- **`MainActivity`** — the only user-facing screen: a `TimePicker` plus Set/Cancel buttons. It owns the two permission gates modern Android forces on alarm apps: `POST_NOTIFICATIONS` (runtime, Android 13+) and exact-alarm scheduling (`canScheduleExactAlarms()` / `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, Android 12+). If exact alarms aren't allowed, "Set" sends the user to system settings instead of scheduling.
- **`AlarmReceiver`** (`BroadcastReceiver`) — fired by `AlarmManager` when due. It marks the alarm disabled (the alarm is one-shot; it is intentionally **not** re-scheduled) and launches `AlarmRingActivity` with `FLAG_ACTIVITY_NEW_TASK`.
- **`AlarmRingActivity`** — full-screen ring UI. Declared in the manifest with `showWhenLocked`/`turnScreenOn` so it appears over the lock screen. Plays the default alarm ringtone and vibrates until "Dismiss" (`finish()`); both are stopped in `onDestroy`.
- **`BootReceiver`** — on `BOOT_COMPLETED`, calls `AlarmScheduler.rescheduleIfEnabled` to re-arm the persisted alarm.

UI uses **view binding** (enabled in `app/build.gradle`); reference views via the generated `ActivityMainBinding` / `ActivityRingBinding` rather than `findViewById`.

## Conventions & gotchas

- The launcher icon is a **vector drawable** (`res/drawable/ic_launcher.xml`), not the usual mipmap PNGs — there are no bitmap assets in the repo.
- "One alarm only" is a deliberate simplification baked into `AlarmScheduler` (fixed request code, single prefs record). Supporting multiple alarms means giving each a distinct request code + persisted list, and reworking `BootReceiver` to re-arm all of them.
- All permission-version checks are guarded by `Build.VERSION.SDK_INT`; keep new platform-gated APIs behind the same pattern since minSdk is 26.