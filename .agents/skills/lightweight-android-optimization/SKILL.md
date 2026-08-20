---
name: lightweight-android-optimization
description: Architecture rules and performance optimizations to keep Android apps fast, responsive, battery-friendly, and lightweight like official Google utility apps (Files by Google, Calculator, Clock).
---

# Lightweight Android App Optimization Skill

This skill details optimization practices for building lightweight Android applications with minimal memory footprint, zero background battery drain, and fast startup times.

## 1. Zero Background Drain
- **Avoid Long-Running Background Services**: Do not keep sticky foreground services running unless strictly required for active operations.
- **Inexact Alarms & JobScheduler**: Use `AlarmManager.setInexactRepeating()` with generous intervals (e.g. 30 minutes) instead of exact wake alarms.
- **No Wakelocks**: Never acquire permanent `PowerManager.WakeLock` for routine tracking. Let the system manage sleep states.

## 2. Efficient Coroutine & Polling Management
- **Lifecycle-Aware UI Loops**: Always bind polling loops to Compose lifecycle or use `LaunchedEffect` that cancels automatically when composables leave the composition.
- **Dispatchers.IO Offloading**: Never perform file I/O or Shizuku IPC calls on the main thread (`Dispatchers.Main`). Use `withContext(Dispatchers.IO)`.
- **Sampling Throttling**: Throttle hardware sensor and thermal sampling (e.g. 5–10s intervals) rather than polling on every frame.

## 3. Memory & APK Size Minimization
- **Lazy Lists**: Use `LazyColumn` and `items(key = ...)` for all dynamic lists so off-screen views are recycled immediately.
- **Bitmap Management**: Avoid holding large bitmap objects in memory. Recycle and downscale icons before rendering.
- **Native JNI Offloading**: Offload heavy math or computational estimations to lean native Rust/C++ libraries.
- **ProGuard / R8**: Ensure unused code and resources are stripped during release compilation.
