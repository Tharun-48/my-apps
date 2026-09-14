# Workspace Rules for my-apps

## Git & Versioning Rules
- **Git Commit & Push on Changes**: Whenever any changes or fixes are made to code or configuration files in the workspace, you MUST perform a `git add`, `git commit`, and immediately run `git push origin main` to push the changes to GitHub.
- **Do Not Change Version Number**: Do NOT increment or modify the application version number (e.g. `versionCode` or `versionName` in `build.gradle.kts`) unless explicitly instructed by the user.
- **Maintain Change Log**: Keep a detailed historical log of all user inputs/requests and corresponding code modifications in [CHANGELOG.md](file:///d:/ANTIGRAVITY/my-apps/CHANGELOG.md). Always update `CHANGELOG.md` whenever new changes are made.

## Android Build & Deployment Rules
- Whenever any changes or fixes are made to the Android application in `pro-stats/pro-stats-android`, you MUST recompile the APK and:
  1. Delete all existing APK files in `pro-stats/releases/`
  2. Copy the newly compiled APK into `pro-stats/releases/` (name it with the current app version, e.g. `ProStats-v2.1.apk`)
  3. Delete `pro-stats/ProStats-Test.apk` if it still exists (old location, no longer used)
- Ensure `pro-stats/releases/` is updated so that `auto-sync` pushes the newly compiled APK to GitHub.

## Environment Constraints
- **Hardware Limits**: The host machine is an older laptop (Intel Pentium N3710, 4-core Atom @ 1.6GHz, ~4–8GB RAM). Compiling Android applications and running heavy build tasks will take a significant amount of time. Give compilation processes ample time to complete.
- **N3710 Optimizations**: Avoid spawning more than 4 parallel agents. Prefer sequential operations over parallel where possible. Never run heavy background indexing during builds.

## AI Model & Token Rules
- **Use Ruflo by default**: Route all multi-step or complex tasks through ruflo (`npx ruflo`) instead of direct model calls. Ruflo's token-saver mode is enabled — it batches and deduplicates context to reduce cost.
- **Token Efficiency**: Keep prompts concise. Do not re-send large file contents if a diff suffices. Prefer targeted edits over full-file rewrites.
- **Model fallback order**: ruflo (free routing) → flash/haiku tier → sonnet only when strictly needed for complex reasoning.

