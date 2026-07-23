package com.example.prostats.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

@Composable
fun ProStatsTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
  val themePref = prefs.getString("app_theme", "Material You") ?: "Material You"

  val isDark = when (themePref) {
      "Light" -> false
      "Dark", "Pure Black (AMOLED)" -> true
      else -> darkTheme // Material You follows system
  }

  val colorScheme =
    when {
      themePref == "Pure Black (AMOLED)" -> DarkColorScheme.copy(
          background = Color.Black,
          surface = Color.Black
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
      else -> { // Material You — follows system light/dark
          if (isDark) darkAppColors() else lightAppColors()
      }
  }

  CompositionLocalProvider(LocalAppColors provides appColors) {
      MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
