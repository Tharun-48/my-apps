package com.example.prostats

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.prostats.theme.ProStatsTheme

class MainActivity : ComponentActivity() {

  // Reactive theme state so Compose recomposes when theme pref changes
  private var themeKey by mutableStateOf(0)

  private val themeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    if (key == "app_theme") {
      themeKey++ // triggers recomposition of ProStatsTheme
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    try {
      com.example.prostats.data.BatteryTrackerReceiver.scheduleTracker(applicationContext)
      com.example.prostats.data.BatteryTracker.recordDataPoint(applicationContext)
      com.example.prostats.data.BatteryHealthEstimator.trackCycleData(applicationContext)
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Failed to schedule tracker", e)
    }

    // Register listener for live theme switching
    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    prefs.registerOnSharedPreferenceChangeListener(themeListener)

    enableEdgeToEdge()
    setContent {
      // themeKey is read here to force recomposition when theme changes
      val currentThemeKey = themeKey
      ProStatsTheme { 
        Surface(
          modifier = Modifier.fillMaxSize(), 
          color = MaterialTheme.colorScheme.background
        ) { 
          MainNavigation() 
        } 
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
    prefs.unregisterOnSharedPreferenceChangeListener(themeListener)
  }
}
