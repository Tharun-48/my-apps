# Workspace Rules for my-apps

## Workspace & Directory Rules
- **Repository Root**: The primary repository root is `D:\ANTIGRAVITY\my-apps`. All commands and relative paths must be anchored here.
- **Directory Hygiene & No Nested Clones**: NEVER clone or create a nested `my-apps` subdirectory inside `D:\ANTIGRAVITY\my-apps` (avoid `my-apps/my-apps`). Always verify the active directory path before running any git or directory commands.
- **Single Source of Truth**: Keep all application code in `pro-stats/pro-stats-android` and shared assets in `pro-stats/`.

## Mandatory Pre-Prompt Log Reading Protocol
- **Read Recent Logs Before Starting Any Task**: At the start of ANY turn or before processing any user prompt/input, the agent MUST inspect the recent change log in [CHANGELOG.md](file:///d:/ANTIGRAVITY/my-apps/CHANGELOG.md) to understand current state and previous decisions.
- **Token & Credit Conservation (Anti-Clunky Rule)**:
  - NEVER dump or read the entire `CHANGELOG.md` or large log files into context (this burns unnecessary quota/credits).
  - ONLY read the top 40–50 lines (the latest entry) using slice notation (`StartLine: 1, EndLine: 50`).
  - Logs must be concise, bulleted, human-readable, and free of clunky text blocks so they are effortless to read with minimal token overhead.

## Git & Versioning Rules
- **Git Commit & Push on Changes**: Whenever changes or fixes are made to code or configuration files, you MUST stage them, commit with a clear descriptive message, and run `git push origin main` to sync with GitHub.
- **Allow & Push App Files to Releases**: Compiled APK files in `pro-stats/releases/` MUST be tracked, committed, and pushed to GitHub. Use `git add -f pro-stats/releases/*.apk` to ensure binary release packages are always staged and pushed to `origin main`.
- **Do Not Change Version Number Unless Instructed**: Do NOT increment application version numbers (`versionCode` or `versionName` in `build.gradle.kts`) unless explicitly instructed by the user.
- **Maintain Activity Log**: Record every user request and code change concisely at the top of [CHANGELOG.md](file:///d:/ANTIGRAVITY/my-apps/CHANGELOG.md) with date and bullet points.

## Android Build & Deployment Rules
- Whenever any changes or fixes are made to the Android application in `pro-stats/pro-stats-android`, you MUST recompile the APK and:
  1. Delete any older APK files in `pro-stats/releases/`.
  2. Copy the newly compiled APK into `pro-stats/releases/` named with the current version (e.g. `ProStats-v2.5.apk`).
  3. Delete `pro-stats/ProStats-Test.apk` if it still exists (legacy location).
  4. Stage the APK (`git add -f pro-stats/releases/`) and push to GitHub so releases are immediately accessible.

## Diagnostic & App Logging Rules
- **Lightweight Diagnostic Logs**: All application logs written by `AppLogger` must be compact, readable, and structured. Avoid noisy unneeded telemetry dumps.
- **Storage Fallback**: The app logger must gracefully check storage permissions and automatically fall back to app-specific external files (`context.getExternalFilesDir("Logs")`) or internal storage (`context.filesDir`) without throwing exceptions.

## Environment Constraints
- **Hardware Limits**: The host machine is an older laptop (Intel Pentium N3710, 4-core Atom @ 1.6GHz, ~4–8GB RAM). Compiling Android applications and running heavy build tasks will take a significant amount of time. Give compilation processes ample time to complete.
- **N3710 Optimizations**: Avoid spawning more than 4 parallel agents. Prefer sequential operations over parallel where possible. Never run heavy background indexing during builds.

## AI Model & Token Efficiency Rules
- **Token Efficiency**: Keep prompts concise. Do not re-send large file contents if a diff suffices. Prefer targeted edits over full-file rewrites.
