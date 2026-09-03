package com.example.prostats.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * App-specific design tokens combining Material 3 surface containers with
 * Apple HIG clarity, translucent depth, and vibrant accent contrasts.
 */
data class AppColors(
    val background: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val surfaceContainerLow: Color,
    val surfaceContainerHigh: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderColor: Color,
    val borderColorSubtle: Color,
    val accentGreen: Color,
    val accentOrange: Color,
    val accentPurple: Color,
    val accentBlue: Color,
    val accentYellow: Color,
    val navBarColor: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf {
    darkAppColors()
}

// Provide easy access everywhere
object ProStatsColors {
    val current: AppColors
        @Composable
        get() = LocalAppColors.current
}

fun darkAppColors() = AppColors(
    background = Color(0xFF0C0D10),
    cardSurface = Color(0xFF16181D),
    elevatedSurface = Color(0xFF20232A),
    surfaceContainerLow = Color(0xFF121317),
    surfaceContainerHigh = Color(0xFF282C34),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    textTertiary = Color(0xFF6B7280),
    borderColor = Color(0x1FFFFFFF), // Hairline translucent border
    borderColorSubtle = Color(0x0EFFFFFF),
    accentGreen = Color(0xFF34D399),
    accentOrange = Color(0xFFFB923C),
    accentPurple = Color(0xFFA78BFA),
    accentBlue = Color(0xFF60A5FA),
    accentYellow = Color(0xFFFBBF24),
    navBarColor = Color(0xFF101216),
    isDark = true
)

fun amoledAppColors() = AppColors(
    background = Color.Black,
    cardSurface = Color(0xFF0F0F12),
    elevatedSurface = Color(0xFF1A1A1E),
    surfaceContainerLow = Color(0xFF070708),
    surfaceContainerHigh = Color(0xFF24242A),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    textTertiary = Color(0xFF71717A),
    borderColor = Color(0x24FFFFFF),
    borderColorSubtle = Color(0x12FFFFFF),
    accentGreen = Color(0xFF4ADE80),
    accentOrange = Color(0xFFFB923C),
    accentPurple = Color(0xFFA78BFA),
    accentBlue = Color(0xFF38BDF8),
    accentYellow = Color(0xFFFBBF24),
    navBarColor = Color.Black,
    isDark = true
)

fun lightAppColors() = AppColors(
    background = Color(0xFFF6F8FA),
    cardSurface = Color(0xFFFFFFFF),
    elevatedSurface = Color(0xFFF0F2F5),
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainerHigh = Color(0xFFE4E7EB),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    textTertiary = Color(0xFF94A3B8),
    borderColor = Color(0x18000000),
    borderColorSubtle = Color(0x0A000000),
    accentGreen = Color(0xFF10B981),
    accentOrange = Color(0xFFF97316),
    accentPurple = Color(0xFF8B5CF6),
    accentBlue = Color(0xFF2563EB),
    accentYellow = Color(0xFFD97706),
    navBarColor = Color(0xFFFFFFFF),
    isDark = false
)

fun dynamicAppColors(colorScheme: androidx.compose.material3.ColorScheme, isDark: Boolean): AppColors {
    return AppColors(
        background = colorScheme.surface,
        cardSurface = colorScheme.surfaceContainerLow,
        elevatedSurface = colorScheme.surfaceContainerHigh,
        surfaceContainerLow = colorScheme.surfaceContainerLow,
        surfaceContainerHigh = colorScheme.surfaceContainerHigh,
        textPrimary = colorScheme.onSurface,
        textSecondary = colorScheme.onSurfaceVariant,
        textTertiary = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        borderColor = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
        borderColorSubtle = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.18f else 0.22f),
        accentGreen = colorScheme.primary,
        accentOrange = Color(0xFFFB923C),
        accentPurple = colorScheme.secondary,
        accentBlue = colorScheme.tertiary,
        accentYellow = Color(0xFFFBBF24),
        navBarColor = colorScheme.surfaceContainer,
        isDark = isDark
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF86EFAC),
    secondary = Color(0xFFDDD6FE),
    tertiary = Color(0xFFBAE6FD),
    background = Color(0xFF0C0D10),
    surface = Color(0xFF16181D),
    surfaceVariant = Color(0xFF222630),
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outlineVariant = Color(0x28FFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    secondary = Color(0xFF7C3AED),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEFF2),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    outlineVariant = Color(0x24000000)
)

@Composable
fun ProStatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Reactively observe the theme preference so Compose recomposes on change
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var themePref by remember { mutableStateOf(prefs.getString("app_theme", "Material You") ?: "Material You") }

    // Register a SharedPreferences listener to update state on any theme change
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "app_theme") {
                themePref = prefs.getString("app_theme", "Material You") ?: "Material You"
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isDark = when (themePref) {
        "Light" -> false
        "Dark", "Pure Black (AMOLED)" -> true
        else -> darkTheme // Material You follows system
    }

    val colorScheme = when {
        themePref == "Pure Black (AMOLED)" -> DarkColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414)
        )
        themePref == "Material You" && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    // Select app-specific color palette based on theme
    val appColors = when (themePref) {
        "Pure Black (AMOLED)" -> amoledAppColors()
        "Light" -> lightAppColors()
        "Dark" -> darkAppColors()
        else -> { // Material You — dynamic palette from system Monet ColorScheme
            dynamicAppColors(colorScheme, isDark)
        }
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
