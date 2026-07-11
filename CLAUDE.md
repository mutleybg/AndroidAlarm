# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A minimal Android alarm app written in **Java** (not Kotlin), using **XML layouts + AndroidX Views** (not Compose), built with **Gradle**. It shows **two independent alarms**, each on half the screen: type an hour and minute on a numeric keypad, pick which weekdays it repeats on, and flip it on/off. When an alarm is due a full-screen activity rings until dismissed. The entire UI is **Bulgarian**, forced regardless of device locale.

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

- **`AlarmScheduler`** (static helper) — manages `AlarmScheduler.ALARM_COUNT` (2) alarms, each addressed by an `index`. Persists per-alarm `hour`/`minute`/`days`/`enabled` to `SharedPreferences` ("alarm_prefs") under `a<index>_`-prefixed keys, and schedules via `AlarmManager.setAlarmClock`. Each alarm gets a distinct `PendingIntent` (request code `REQUEST_CODE_BASE + index`) and carries its index in `EXTRA_INDEX`. `days` is a **7-bit weekday mask, Monday-first** (bit 0 = Monday … bit 6 = Sunday). `nextTrigger(hour, minute, daysMask)` computes the fire time: with no days it's the next occurrence of the time (today or tomorrow); with days it's the nearest future selected weekday. `save()` only persists; `apply()` schedules-or-cancels to match the saved `enabled` flag; `onFired()` re-arms a repeating alarm or disables a one-shot one.
- **`MainActivity`** — the only user-facing screen: two `AlarmPanelView`s stacked (see `activity_main.xml`). No Set button — every edit auto-saves and re-schedules that alarm via `onPanelChanged(index)`. It owns the two permission gates modern Android forces on alarm apps: `POST_NOTIFICATIONS` (runtime, Android 13+) and exact-alarm scheduling (`canScheduleExactAlarms()` / `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, Android 12+). If exact alarms aren't allowed when a switch is turned on, the switch is reverted and the user is sent to system settings.
- **`AlarmPanelView`** (custom `LinearLayout`) — one alarm's controls: title, Hour/Min numeric `EditText`s (numeric keypad, no scroller), a `MaterialSwitch`, and seven day-toggle circles built programmatically into `daysRow`. Inflates `view_alarm_panel.xml` (a `<merge>`) via view binding. Exposes getters (`getHour`/`getMinute`/`getDaysMask`/`isAlarmEnabled`) and `setState(...)`, and emits a single `OnChangeListener.onChanged()` on any user edit. A `loading` flag suppresses callbacks while state is loaded in. **Note:** its accessors are named `isAlarmEnabled()`/`setSwitchChecked()` on purpose — do not name them `isEnabled`/`setEnabled`, which would override `View`'s own methods and break touch handling.
- **`AlarmReceiver`** (`BroadcastReceiver`) — fired by `AlarmManager` when due. Reads `EXTRA_INDEX`, calls `AlarmScheduler.onFired(index)` (re-arm if repeating, disable if one-shot), and launches `AlarmRingActivity` with `FLAG_ACTIVITY_NEW_TASK`.
- **`AlarmRingActivity`** — full-screen ring UI. Declared in the manifest with `showWhenLocked`/`turnScreenOn` so it appears over the lock screen. Plays the default alarm ringtone and vibrates until "Dismiss" (`finish()`); both are stopped in `onDestroy`.
- **`BootReceiver`** — on `BOOT_COMPLETED`, calls `AlarmScheduler.rescheduleAllEnabled` to re-arm every enabled alarm.
- **`BaseActivity` / `LocaleHelper`** — every activity extends `BaseActivity`, which wraps the base context with a Bulgarian `Configuration` (`LocaleHelper.wrap`, language `"bg"`), forcing the in-app UI to Bulgarian on any device locale.

UI uses **view binding** (enabled in `app/build.gradle`); reference views via the generated `ActivityMainBinding` / `ActivityRingBinding` / `ViewAlarmPanelBinding` rather than `findViewById`.

### Localization

Default resources in `res/values/` are **English** (the base language); `res/values-bg/` holds the **Bulgarian** translation, and the app forces `"bg"` at runtime so Bulgarian always shows. `app_name` is the Bulgarian brand name **"Аларма"** in every locale (it's the launcher label, which the system resolves by device locale, so it's kept constant). Day letters come from the `day_letters` string-array (`М Т В Т Ф С С` in English, `П В С Ч П С Н` in Bulgarian), read Monday-first to match the weekday mask.

## Conventions & gotchas

- The launcher icon is a **vector drawable** (`res/drawable/ic_launcher.xml`), not the usual mipmap PNGs — there are no bitmap assets in the repo.
- The number of alarms is `AlarmScheduler.ALARM_COUNT`. Adding more means adding another `AlarmPanelView` to `activity_main.xml` and wiring it in `MainActivity`; the scheduler and receivers already loop/route by `index`.
- The weekday mask is **Monday-first** everywhere (bit 0 = Monday). `AlarmScheduler.nextTrigger` and the `day_letters` array both follow this; keep them in sync. `nextTrigger` is deliberately static and side-effect-free so it's unit-testable (see `AlarmSchedulerTest`).
- All permission-version checks are guarded by `Build.VERSION.SDK_INT`; keep new platform-gated APIs behind the same pattern since minSdk is 26.
- Day-toggle circles and the numeric-field boxes are drawn with `res/drawable/day_circle_bg.xml` (a `state_selected` selector) and `res/drawable/number_field_bg.xml`; beige/outline colors live in `res/values/colors.xml`.