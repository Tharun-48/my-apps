# Workspace Rules for my-apps

## Android Build & Deployment Rules
- Whenever any changes or fixes are made to the Android application in `pro-stats/pro-stats-android`, you MUST recompile the APK and:
  1. Delete all existing APK files in `pro-stats/releases/`
  2. Copy the newly compiled APK into `pro-stats/releases/` (name it with the appropriate version, e.g. `ProStats-v2.1.apk`)
  3. Delete `pro-stats/ProStats-Test.apk` if it still exists (old location, no longer used)
- Ensure `pro-stats/releases/` is updated so that `auto-sync` pushes the newly compiled APK to GitHub.
