# Activity & Change Log — my-apps

This document maintains a historical log of user inputs and corresponding code changes made across the repository.

---

### [2026-08-21] System Battery Cycle Extraction & Universal GPU OpenGL ES Detection in System Info
- **User Prompt**: *"well fix the battery cycles try to get the total from system (android) nd also gpu in system info"*
- **Summary of Changes**:
  - **System Battery Cycle Extraction**:
    - `BatteryHealthEstimator.kt`: Upgraded `getSystemCycleCount(context)` with a 4-layer fallback strategy:
      1. Android 14+ (API 34+) `BatteryManager.getIntProperty(BATTERY_PROPERTY_CYCLE_COUNT)`.
      2. Proprietary OEM extras from `Intent.ACTION_BATTERY_CHANGED` (`android.os.extra.CYCLE_COUNT`, `battery_cycle`, `cycle_count`, `cycle`, `charge_cycle`, `total_cycle`, `battery_cycle_count`).
      3. Linux kernel sysfs power supply nodes (`/sys/class/power_supply/battery/cycle_count`, `/sys/class/power_supply/bms/cycle_count`, `/sys/class/power_supply/battery/battery_cycle`, etc.).
      4. Shizuku privileged `dumpsys battery` output parsing (`mCycleCount`, `Cycle count`, `cycle_count`) and privileged sysfs reads.
      5. Persistent caching in `SharedPreferences` as `KEY_LAST_SYSTEM_CYCLES` for reliable baseline tracking.
    - `HardwareMonitor.kt`: Added `cycleCount` and `cycleSource` (`System` vs `Estimated`) to `HwBatteryInfo`.
  - **Universal GPU Detection via Headless OpenGL ES / EGL Context**:
    - `SystemMonitor.kt`: Added headless EGL/OpenGL ES 2.0 pbuffer surface initialization (`EGL14.eglGetDisplay`, `eglInitialize`, `eglCreateContext`, `eglCreatePbufferSurface`) to extract exact hardware GPU `GL_RENDERER`, `GL_VENDOR`, and `GL_VERSION` reliably across all Android devices (Qualcomm Adreno, ARM Mali, Imagination PowerVR, Google ANGLE, etc.) without requiring a visible SurfaceView or root.
    - Added multi-platform GPU clock frequency readers across Qualcomm `/sys/class/kgsl/`, ARM Mali `/sys/class/devfreq/`, and Shizuku privileged shell fallback.
    - Updated `GpuInfo` data class to include `openGlVersion`.
  - **System Info UI Updates**:
    - `SystemInfoScreen.kt`:
      - GPU Card: Displays real hardware Renderer, Vendor, OpenGL ES version, Max Frequency, and Current Frequency.
      - Battery Hardware Card: Displays Charge Cycles with source badge (e.g. `142 (System)`) alongside Level, Health, Tech, Design Capacity, Voltage, and Temp.
  - **Build & Release**:
    - Recompiled Release APK `ProStats-v2.2.apk` and copied to `pro-stats/releases/`.

---

### [2026-08-21] Comprehensive UI/UX Redesign Using Skills (Material 3, Material You Monet & Apple HIG)
- **User Prompt**: *"redesign app using skills."*
- **Summary of Changes**:
  - **Typography & Theme System**:
    - `Type.kt`: Implemented full modern Material 3 typography scale with tight letter spacing, clean font weights, and distinct line heights.
    - `Theme.kt`: Enhanced `AppColors` data class with refined surface container roles (`surfaceContainerLow`, `surfaceContainerHigh`, `surfaceBorderSubtle`, `textTertiary`), subtle translucent hairline borders, and harmonious dynamic Monet theming.
  - **Main Dashboard (`DashboardScreen.kt`)**:
    - Redesigned TopBar with live status indicator dot and glass settings action.
    - Transformed SOT and Temperature into hero squircle tiles (`22.dp`) with colored category badges.
    - Elevated Battery Health card with large health readout, condition subtitle, system cycle tag, design vs current capacity, and real-time charging/discharging wattage chip.
    - Battery Diagnostics tile with technical specs (voltage, current, power, health, thermal throttle state).
    - CPU Cluster multi-core frequency monitor with real-time scaling visual active meter bars.
    - Dual-track rounded progress bars for CPU load and RAM allocation.
    - Apple HIG inspired high-contrast pill CTA button for process management.
  - **Running Processes (`MainScreen.kt`)**:
    - Sleek Pro Mode ADB vs Basic Mode banner with colored border accents.
    - Segmented horizontal sorting tabs (`CPU Load`, `RAM Usage`, `Recently Active`, `App Name`).
    - Squircle app icons with high-visibility CPU/RAM usage chips and fast action dialogs.
    - Stable list keys for optimal Compose runtime performance.
  - **Screen-On Time Detail (`SotDetailScreen.kt`)**:
    - Segmented pill selector for time ranges (`Since Charge`, `24h`, `7d`).
    - Smooth Bezier Canvas timeline chart with gradient area shading, reference percentage lines ($25\%, 50\%, 75\%, 100\%$), and dynamic bottom timestamps.
    - Symmetrical 2-tile row for Screen-On vs Screen-Off time.
    - Per-app battery consumption list with squircle icons, duration labels, and slim progress bars.
  - **Battery Temperature Detail (`BatteryTempDetailScreen.kt`)**:
    - Symmetrical 3-tile summary cards for Highest, Average, and Lowest temperatures with distinct accent colors.
    - Live temperature badge and smooth Canvas temperature trend graph with side Y-axis labels and bottom timestamps.
    - Thermal zones breakdown card with progress bars (Cool, Normal, Warm, Hot).
  - **System Information (`SystemInfoScreen.kt`)**:
    - Grouped category cards with leading colored icon badges (Device, SoC, GPU, Battery, Display, Memory, Storage, Network, Cameras).
    - Real-time sensor stream with dynamic live value chips.
  - **Settings & Overlays (`SettingsScreen.kt`)**:
    - Theme selector with palette preview dots (Material You, Dark, AMOLED, Light).
    - Inset toggle switches for floating HUD overlays (Temp, Current mA, Refresh Rate Hz, CPU%, RAM%).
    - Shizuku wireless ADB status card with connection state pills and direct action buttons.
  - **Onboarding (`OnboardingScreen.kt`)**:
    - Redesigned with step badge, glassmorphic permission cards with checkmarks/action pills, and high-contrast start button.
  - **Build & Release**:
    - Recompiled Release APK `ProStats-v2.2.apk` and updated `pro-stats/releases/`.

---

### [2026-08-20] Material You Dynamic Theming, UI/UX Skills, SOT Auto-Reset & Graph Side Labels
- **User Prompt**: *"add this in skill and [GitHub - android/skills · GitHub](https://github.com/android/skills) , [skills/README.md at main · google/skills · GitHub](https://github.com/google/skills/blob/main/README.md) and reevaluate and review code and check for errors. things need to be fixed: 1. android material ui not working, ui doesnt adopt google material ui colour 2.get independent skills for improving ui like apple design and other design skills 3. app sure will remain lightweight as like other google apps 4.in screen on time, fix since charge needs to reset auto when its >=90, and 7days will be there 5.screen off time its in glitch, in battery temperature stats graph. show temp in side in the lines you make."*
- **Summary of Changes**:
  - **Skills Added**:
    - `.agents/skills/android-material3-design/SKILL.md`: Deep Material Design 3 and Material You dynamic Monet theming specification.
    - `.agents/skills/apple-design-system/SKILL.md`: Apple HIG craftsmanship rules (clarity, deference, depth, micro-interactions, subtle borders, high contrast).
    - `.agents/skills/lightweight-android-optimization/SKILL.md`: Rules for keeping Android utility apps ultra-lightweight, battery-efficient, and fast.
    - `.agents/skills/android-skills/SKILL.md`: Core index of official android/skills and google/skills best practices.
  - **Material You Dynamic Theming Fix**:
    - Updated `Theme.kt` (`dynamicAppColors`) to dynamically derive background, cardSurface, elevatedSurface, textPrimary, textSecondary, and borders directly from the system Monet `ColorScheme` on Android 12+ (API 31+).
  - **Screen-On & Screen-Off Time Glitch Resolution**:
    - Fixed `SystemMonitor.getScreenOnTimeMs` to handle missing `SCREEN_INTERACTIVE` events, start-time boundary heuristics, and fallback to accumulated activity foreground intervals and aggregate usage stats.
    - Strictly bounded `getScreenOffTimeMs` within `[0L, totalElapsed]` to prevent 100% glitch when SOT is calculated.
  - **SOT Auto-Reset ($\ge 90\%$) & 7-Day History Restored**:
    - Updated `BatteryTracker.kt` and `BatteryTrackerReceiver.kt` to auto-reset baseline whenever device is charging or unplugged at $\ge 90\%$.
    - Added `"7d"` toggle option to `SotDetailScreen.kt` alongside `"Since Charge"` and `"24h"`.
  - **Battery Temperature Stats Graph**:
    - Added side temperature scale labels (e.g. `30°C`, `35°C`, `40°C`, `45°C`) on the horizontal grid lines in `BatteryTempDetailScreen.kt`.
    - Added bottom X-axis timestamps to the temperature history trend graph.
  - **Build & Distribution**:
    - Recompiled Release APK `ProStats-v2.2.apk` and updated `pro-stats/releases/`.

---

### [2026-08-15] Enforce Immediate Git Push Rule
- **User Prompt**: *"not updated to github bro"* & *"its uploaded but you need to check with girhub i guess like i said it released 24 mins ago but only showed after asked to you"*
- **Summary of Changes**:
  - Pushed unpushed local commit (`40c685e`) directly to `origin/main` on GitHub.
  - Updated [.agents/AGENTS.md](file:///c:/Users/SoloWanderer/Documents/antigravity/my-apps/.agents/AGENTS.md) rule to strictly require `git push origin main` immediately after every commit so changes are always instantly synced to GitHub without delay.

---

### [2026-08-12] SOT Baseline Reset Fix & 24 Hours History Range
- **User Prompt**: *"in screen on time and battery, in battery charge history the reset cycle is resetting in 24 hours instead of charge getting >=90, also remove 7d and in the 7d combined setting, set everything to 24 hours. since charge should follow / reset when charge <=90"*
- **Summary of Changes**:
  - **SOT Reset Logic Fix**: Removed continuous periodic baseline resets while charging from `SotDetailScreen.kt` and `BatteryTrackerReceiver.kt` alarm handler. Baseline reset is now executed strictly when charger is disconnected at >= 90% battery level via `ACTION_POWER_DISCONNECTED`.
  - **Time Range Update**: Replaced `7d` history option with `24h` in `SotDetailScreen.kt`. Updated graph toggle options, subtitle descriptions, and data querying logic from 7 days to 24 hours.
  - **APK Rebuild**: Recompiled Android release APK `ProStats-v2.2.apk` and updated `pro-stats/releases/`.

---

### [2026-07-29] Automated Build Sync & Git Integration
- **User Prompt**: *"automate push and comit when build app , if it is there, fix it cause its not working"*
- **Summary of Changes**:
  - Registered `autoCopyApkAndGitSync` Gradle task in `app/build.gradle.kts` linked to `assembleDebug` and `assembleRelease`.
  - Automatically copies output APK to `pro-stats/releases/ProStats-v2.2.apk`, cleans old releases, stages changes with `git add -A`, commits with a timestamped build message, and pushes to `origin main`.
  - Updated `auto-sync.ps1` script to dynamically locate `git` in system PATH.

---

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

### [2026-08-08] Rust Core Logic Rewrite & MSVC Setup
- **User Prompt**: "continue operation... rust rewrite!"
- **Summary of Changes**:
  - **Toolchain Setup**: Installed Rust, MSVC Build Tools, and Android NDK.
  - **Android Architecture Targets**: Added targets for rmeabi-v7a, rm64-v8a, x86, and x86_64.
  - **Rust Crate**: Initialized a new Cargo project core-rs configured for cdylib and the jni crate.
  - **Native Migration**: Migrated calculateHealthScore from Kotlin to a Rust JNI native function.
  - **Integration**: Compiled the Rust code into .so shared libraries using cargo-ndk and configured BatteryHealthEstimator.kt to load core_rs.
  - **Build**: Successfully compiled Android APK ProStats-v2.2.apk containing all architecture libraries.

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
# # #   C h a n g e d 
 -   F i x e d   b a t t e r y   c y c l e   a u t o - r e s e t   n o t   t r i g g e r i n g   o n   A n d r o i d   8 +   b y   c o r r e c t i n g   t h e   i n t e n t   a c t i o n   s t r i n g   i n   t h e   m a n i f e s t . 
 -   A d d e d   a   2 4 h   v s   7 d   t o g g l e   f o r   t h e   B a t t e r y   C h a r g e   H i s t o r y   g r a p h   o n   t h e   S c r e e n - o n   T i m e   &   B a t t e r y   s c r e e n   ( d e f a u l t s   t o   2 4 h ) . 
 -   M o d i f i e d   A p p   B a t t e r y   C o n s u m p t i o n   t o   p r e c i s e l y   q u e r y   a c t i v i t y   e v e n t s   s i n c e   t h e   e x a c t   u n p l u g   t i m e s t a m p ,   e n s u r i n g   a c c u r a t e   ' s i n g l e   u n p l u g g e d '   d a t a . 
  
 
## [Unreleased]
- Fixed issue where battery charge history baseline was not auto-refreshing in the UI when device reached 100% and unplugged.
- Redesigned 'App Battery Consumption' list row to prevent appName and packageName overlapping if they are identical.
- Added explicit time range label (e.g. 'Since unplugged' or 'Last 7 days') to the 'App Battery Consumption' header.


- Added auto-reset logic to the battery tracker alarm so the cycle continuously auto-resets while the phone stays at 100% on the charger.


- Added instant real-time auto-reset check directly in the UI screen so stats reset immediately while viewing if device is fully charged.


## [v2.3]
- Fixed horizontal scroll & tab transition lag across dashboard by optimizing HorizontalPager and page switching.
- Added interactive Battery Temperature detail screen when clicking Battery Temperature card.
- Implemented Highest, Normal/Avg, and Lowest temperature stats for Today and 7 Days.
- Added temperature history trend chart and thermal zones breakdown.


