# Activity & Change Log — my-apps

This document maintains a historical log of user inputs and corresponding code changes made across the repository.

---

## Log Entries

### [2026-07-29] Shizuku Provider Integration
- **User Prompt**: *"correctly integrate shizuku into this app"* & *"it need to show here if shizuku api is integrated with this app"*
- **Summary of Changes**:
  - Added `dev.rikka.shizuku:provider:12.2.0` dependency to `app/build.gradle.kts` alongside the existing `api` dependency.
  - Added `<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />` in `AndroidManifest.xml` to ensure the app appears in Shizuku's Application Management list.
  - Declared `<provider android:name="rikka.shizuku.ShizukuProvider" ... />` inside `AndroidManifest.xml` to correctly expose the Shizuku integration for the application.
  - Recompiled Debug APK (`app-debug.apk`), updated `pro-stats/releases/` as `ProStats-v2.2.apk`, and prepared build artifacts for GitHub push.

---

### [2026-07-27] Deprecation Fixes, Android Q+ Compatibility & Refined Build Release
- **User Prompt**: *"check and fix every errors in this app, make this app refined, push it to github"*
- **Summary of Changes**:
  - Updated `DashboardScreen.kt`, `OnboardingScreen.kt`, `SotDetailScreen.kt`, `MainScreen.kt`, `HardwareMonitor.kt`, and `SystemMonitor.kt` to resolve Kotlin and Compose deprecation warnings (`LocalLifecycleOwner`, `LinearProgressIndicator` lambda overload, `AutoMirrored` arrow icon, `Sensor.TYPE_TEMPERATURE`).
  - Refactored `SystemMonitor.hasUsageStatsPermission()` to support Android Q+ `unsafeCheckOpNoThrow` with fallback to `noteOpNoThrow`.
  - Added missing `android.os.Build` import in `SystemMonitor.kt`.
  - Recompiled Release APK (`ProStats-v2.2.apk`), updated `pro-stats/releases/`, and prepared build artifacts for GitHub push.

---

### [2026-07-26] Log Folder Outside Android Directory & Crash Reporting
- **User Prompt**: *"need to create a log folder in phone to receive errors when anything happened. it should be outside the android folder"*
- **Summary of Changes**:
  - Created `AppLogger.kt` (`com.example.prostats.data.AppLogger`) targeting `/sdcard/ProStats/Logs` (outside the `/Android/` system directory).
  - Attached a global `Thread.UncaughtExceptionHandler` to log crashes, stack traces, device specs, and OS details to daily and crash-specific text files.
  - Declared `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, and `MANAGE_EXTERNAL_STORAGE` permissions with `android:requestLegacyExternalStorage="true"` in `AndroidManifest.xml`.
  - Initialized `AppLogger.init(applicationContext)` in `MainActivity.onCreate()`.
  - Added a **Log & Error Reporting** card in `SettingsScreen.kt` displaying log path, permission status, and a "Write Test Log" button.

---

### [2026-07-26] Functional Fixes, System Charge Cycles, SOT Reset & Battery mA Overlay
- **User Prompt**: *"take your time and fix functional errors in this app, needed places are in sot configured to reset after disconnect charger above 90, battery cycleneed to take from system,add overlay to see battery discharge ma"*
- **User Prompt**: *"check any functionall errors in everywhere and optimise code"*
- **Summary of Changes**:
  - **SOT Reset**: Rewrote `BatteryTrackerReceiver.kt` — removed noisy `ACTION_BATTERY_CHANGED` broadcast listener, added explicit 30-minute alarm action, and reset SOT baseline on `ACTION_POWER_DISCONNECTED` when battery level $\ge 90\%$.
  - **System Charge Cycles**: Updated `BatteryHealthEstimator.kt` to read system cycle count directly from the OS kernel via property `7` (`BATTERY_PROPERTY_CYCLE_COUNT`) on API 34+.
  - **Battery Current (mA) Overlay**: Added live battery discharge/charge current (mA) HUD overlay in `OverlayService.kt` with toggle controls in `SettingsScreen.kt`.
  - **SystemMonitor**: Removed hardcoded `1250 mA` fake fallback value. Improved `getScreenOnTimeMs()` using `queryEvents` to track `SCREEN_INTERACTIVE` / `SCREEN_NON_INTERACTIVE` events for accurate SOT.
  - **SotDetailScreen**: Replaced hardcoded `hasData = true` with dynamic evaluation. Added a periodic 15s refresh loop for live SOT/Screen-Off metrics and updated `remember` keys for `avgDailySot` and `wakelocks`.
  - **DashboardScreen**: Reduced `getHealthData()` execution frequency to ~9s (down from 1.5s) to save CPU resources. Added `(System)` tag when cycle count comes from OS APIs.
  - **AppLogger**: Suppressed `thread.id` deprecation warning cleanly.

---

### [2026-07-26] Rule Enforcement, Git Auto-Commit & Changelog Log Setup
- **User Prompt**: *"before everything do commit. add in agents.md to commit whenever i do changes also do not change version number, also keep a log of previous inputs and changes"*
- **Summary of Changes**:
  - Created `CHANGELOG.md` to track all historical user inputs and repository updates.
  - Updated `.agents/AGENTS.md` with workspace rules for auto-committing changes, maintaining `CHANGELOG.md`, and preserving version numbers.
  - Staged all changes and created git commit.
