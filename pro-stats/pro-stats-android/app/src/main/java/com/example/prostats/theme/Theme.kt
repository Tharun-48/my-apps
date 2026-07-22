package com.example.prostats.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

@Composable
fun ProStatsTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
  val themePref = prefs.getString("app_theme", "Material You") ?: "Material You"

  val isDark = when (themePref) {
      "Light" -> false
      "Dark", "Pure Black (AMOLED)" -> true
      else -> darkTheme
  }

  val colorScheme =
    when {
      themePref == "Pure Black (AMOLED)" -> DarkColorScheme.copy(
          background = androidx.compose.ui.graphics.Color.Black,
          surface = androidx.compose.ui.graphics.Color.Black
      )
      themePref == "Material You" && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      isDark -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
