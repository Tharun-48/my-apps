# Workspace Rules for my-apps

## Android Build & Deployment Rules
- Whenever any changes or fixes are made to the Android application in `pro-stats/pro-stats-android`, you MUST recompile the APK and copy the output APK to `pro-stats/ProStats-Test.apk`.
- Ensure `ProStats-Test.apk` is updated so that `auto-sync` pushes the newly compiled APK to GitHub.
