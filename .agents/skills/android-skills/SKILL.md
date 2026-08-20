---
name: android-skills
description: Reference index and best practice mappings from the official android/skills and google/skills repositories. Covers Jetpack Compose runtime performance, Navigation 3, Material 3, R8 analysis, batterystats, and edge-to-edge layout patterns.
---

# Android & Google Official Skills Reference

This skill aggregates best practices and instructions from:
- [android/skills](https://github.com/android/skills)
- [google/skills](https://github.com/google/skills)

## 1. Core Android Architecture & State Management
- **Stateless Composables**: Keep UI components decoupled from data providers; pass state down and events up.
- **StateFlow & ProduceState**: Use `produceState` or `collectAsStateWithLifecycle` for reactive flows to prevent recomposition churn.
- **Navigation 3**: Leverage declarative navigation with type-safe routing.

## 2. Compose Performance & Runtime
- **Stable Keys**: Always provide unique, stable keys in `LazyColumn(items(..., key = { ... }))`.
- **Derived State & Remember**: Wrap expensive calculations in `remember(key1, key2)` or `derivedStateOf`.
- **Avoid Unnecessary Recompositions**: Pass lambdas instead of evaluated values when mutating progress bars or animated modifiers.

## 3. Battery & System Inspection
- **UsageStats & BatteryStats**: Query `UsageStatsManager.queryEvents` for interactive intervals; fallback to aggregate stats when event logs are unavailable.
- **Dynamic Theming**: Support `dynamicDarkColorScheme` / `dynamicLightColorScheme` for Material You compatibility.
- **Thermal & Hardware Monitoring**: Query `/sys/class/thermal/` and `PowerManager.currentThermalStatus` safely with defensive fallbacks.
