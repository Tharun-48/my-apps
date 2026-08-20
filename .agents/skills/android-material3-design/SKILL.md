---
name: android-material3-design
description: Design system guidelines and best practices for Android Material Design 3 and Material You Dynamic Theming. Use when implementing Compose themes, dynamic Monet color palettes, tonal elevations, surface containers, typography, and edge-to-edge layouts.
---

# Android Material Design 3 & Material You Skill

This skill provides comprehensive instructions for designing and implementing Android applications using **Material Design 3 (M3)** and **Material You (Dynamic Color)** in Jetpack Compose.

## 1. Material You Dynamic Color Architecture

Material You generates dynamic tonal palettes from user wallpaper colors starting in Android 12 (API 31+).

### Color Scheme Strategy
- **Dynamic Schemes**: Use `dynamicDarkColorScheme(context)` and `dynamicLightColorScheme(context)` on Android 12+.
- **Surface Roles**:
  - `surface`: Base background for screens.
  - `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`: Replaces static drop shadows with tonal elevation.
  - `onSurface` and `onSurfaceVariant`: High-contrast text and icon colors.
- **Accent Roles**:
  - `primary` / `onPrimary` / `primaryContainer`: Key actions, active states.
  - `secondary` / `secondaryContainer`: Supporting highlights, badges, chips.
  - `tertiary` / `tertiaryContainer`: Contrasting accents for rich visual hierarchy.

```kotlin
val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    isDark -> DarkColorScheme
    else -> LightColorScheme
}
```

## 2. Dynamic Component Styling

To ensure custom UI tokens adopt system dynamic colors:
- Derive app-specific color definitions (`AppColors`) directly from the active `colorScheme`.
- Avoid hardcoding fixed hex values for backgrounds, card containers, and borders.
- Use `colorScheme.outlineVariant` or subtle alpha overlays for card borders.

## 3. Edge-to-Edge & System Bars
- Always call `enableEdgeToEdge()` in `ComponentActivity.onCreate()`.
- Use `Modifier.windowInsetsPadding()` or `Scaffold` inner padding to respect system status and navigation bars.
- Match navigation bar colors to `surfaceContainer` or `background`.

## 4. Typography & Shape Hierarchy
- **Typography**: Follow standard M3 scale: `displayLarge`, `headlineMedium`, `titleMedium`, `bodyMedium`, `labelSmall`.
- **Shapes**: Standardize corner radii using `RoundedCornerShape`:
  - Cards: `16.dp` to `20.dp`
  - Chips & Small Buttons: `8.dp` to `12.dp`
  - Floating Indicators: `24.dp` to full pill shape
