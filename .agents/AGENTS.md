# Workspace Rules for my-apps

## Git & Versioning Rules
- **Git Commit on Changes**: Whenever any changes or fixes are made to code or configuration files in the workspace, you MUST perform a `git add` and `git commit` to commit the changes.
- **Do Not Change Version Number**: Do NOT increment or modify the application version number (e.g. `versionCode` or `versionName` in `build.gradle.kts`) unless explicitly instructed by the user.
- **Maintain Change Log**: Keep a detailed historical log of all user inputs/requests and corresponding code modifications in [CHANGELOG.md](file:///c:/Users/SoloWanderer/Documents/antigravity/my-apps/CHANGELOG.md). Always update `CHANGELOG.md` whenever new changes are made.

## Android Build & Deployment Rules
- Whenever any changes or fixes are made to the Android application in `pro-stats/pro-stats-android`, you MUST recompile the APK and:
  1. Delete all existing APK files in `pro-stats/releases/`
  2. Copy the newly compiled APK into `pro-stats/releases/` (name it with the current app version, e.g. `ProStats-v2.1.apk`)
  3. Delete `pro-stats/ProStats-Test.apk` if it still exists (old location, no longer used)
- Ensure `pro-stats/releases/` is updated so that `auto-sync` pushes the newly compiled APK to GitHub.

## Environment Constraints
- **Hardware Limits**: The host machine is an older laptop (Pentium N3700 processor). Compiling Android applications and running heavy build tasks will take a significant amount of time. Give compilation processes ample time to complete.
