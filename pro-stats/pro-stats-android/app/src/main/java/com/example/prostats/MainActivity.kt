package com.example.prostats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.prostats.theme.ProStatsTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      com.example.prostats.data.BatteryTrackerReceiver.scheduleTracker(applicationContext)
      com.example.prostats.data.BatteryTracker.recordDataPoint(applicationContext)
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "Failed to schedule tracker", e)
    }

    enableEdgeToEdge()
    setContent {
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
}
