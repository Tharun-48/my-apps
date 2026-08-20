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
 * App-specific colors used throughout all screens.
 * This bridges the hardcoded color system with theme-awareness.
 */
data class AppColors(
    val background: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderColor: Color,
    val accentGreen: Color,
    val accentOrange: Color,
    val accentPurple: Color,
    val accentBlue: Color,
    val accentYellow: Color,
    val navBarColor: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf {
    darkAppColors() // default
}

// Provide easy access everywhere
object ProStatsColors {
    val current: AppColors
        @Composable
        get() = LocalAppColors.current
}

fun darkAppColors() = AppColors(
    background = Color(0xFF0A0A0C),
    cardSurface = Color(0xFF1C1C1E),
    elevatedSurface = Color(0xFF2C2C2E),
    textPrimary = Color.White,
    textSecondary = Color.Gray,
    borderColor = Color(0x1BFFFFFF),
    accentGreen = Color(0xFF4ADE80),
    accentOrange = Color(0xFFFB923C),
    accentPurple = Color(0xFFA78BFA),
    accentBlue = Color(0xFF60A5FA),
    accentYellow = Color(0xFFFBBF24),
    navBarColor = Color(0xFF121214),
    isDark = true
)

fun amoledAppColors() = AppColors(
    background = Color.Black,
    cardSurface = Color(0xFF0D0D0D),
    elevatedSurface = Color(0xFF1A1A1A),
    textPrimary = Color.White,
    textSecondary = Color(0xFFAAAAAA),
    borderColor = Color(0x11FFFFFF),
    accentGreen = Color(0xFF4ADE80),
    accentOrange = Color(0xFFFB923C),
    accentPurple = Color(0xFFA78BFA),
    accentBlue = Color(0xFF60A5FA),
    accentYellow = Color(0xFFFBBF24),
    navBarColor = Color.Black,
    isDark = true
)

fun lightAppColors() = AppColors(
    background = Color(0xFFF5F5F7),
    cardSurface = Color.White,
    elevatedSurface = Color(0xFFEEEEF0),
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF8E8E93),
    borderColor = Color(0x15000000),
    accentGreen = Color(0xFF22C55E),
    accentOrange = Color(0xFFF97316),
    accentPurple = Color(0xFF8B5CF6),
    accentBlue = Color(0xFF3B82F6),
    accentYellow = Color(0xFFF59E0B),
    navBarColor = Color.White,
    isDark = false
)

fun dynamicAppColors(colorScheme: androidx.compose.material3.ColorScheme, isDark: Boolean): AppColors {
    val cardBg = if (isDark) {
        Color(
            red = (colorScheme.surface.red * 0.4f + colorScheme.surfaceVariant.red * 0.6f),
            green = (colorScheme.surface.green * 0.4f + colorScheme.surfaceVariant.green * 0.6f),
            blue = (colorScheme.surface.blue * 0.4f + colorScheme.surfaceVariant.blue * 0.6f),
            alpha = 1.0f
        )
    } else {
        Color(
            red = (colorScheme.surface.red * 0.7f + colorScheme.surfaceVariant.red * 0.3f),
            green = (colorScheme.surface.green * 0.7f + colorScheme.surfaceVariant.green * 0.3f),
            blue = (colorScheme.surface.blue * 0.7f + colorScheme.surfaceVariant.blue * 0.3f),
            alpha = 1.0f
        )
    }

    val elevatedBg = if (isDark) {
        colorScheme.surfaceVariant
    } else {
        colorScheme.surfaceVariant
    }

    return AppColors(
        background = colorScheme.background,
        cardSurface = cardBg,
        elevatedSurface = elevatedBg,
        textPrimary = colorScheme.onBackground,
        textSecondary = colorScheme.onSurfaceVariant,
        borderColor = colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
        accentGreen = colorScheme.primary,
        accentOrange = Color(0xFFFB923C),
        accentPurple = colorScheme.secondary,
        accentBlue = colorScheme.tertiary,
        accentYellow = Color(0xFFFBBF24),
        navBarColor = colorScheme.surface,
        isDark = isDark
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    background = Color(0xFF0A0A0C),
    surface = Color(0xFF121214),
    surfaceVariant = Color(0xFF2C2C2E),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.Gray,
    outlineVariant = Color(0x26FFFFFF)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6650A4),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFF5F5F7),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEEF0),
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outlineVariant = Color(0x26000000)
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

  val colorScheme =
    when {
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
