package com.example.prostats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.prostats.data.SystemMonitor
import com.example.prostats.ui.dashboard.DashboardScreen
import com.example.prostats.ui.main.MainScreen
import com.example.prostats.ui.main.MainScreenViewModel
import com.example.prostats.ui.onboarding.OnboardingScreen

@Composable
fun MainNavigation() {
  val context = LocalContext.current.applicationContext
  val systemMonitor = remember { SystemMonitor(context) }
  
  // Start on Dashboard if required permissions are already granted, otherwise start on Onboarding
  val startDestination = remember {
    if (systemMonitor.isIgnoringBatteryOptimizations() && systemMonitor.hasUsageStatsPermission()) {
      Dashboard
    } else {
      Onboarding
    }
  }

  val backStack = rememberNavBackStack(startDestination)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Onboarding> {
          OnboardingScreen(
            systemMonitor = systemMonitor,
            onStartMonitoring = { 
              // Go to dashboard and remove onboarding from backstack
              backStack.removeLastOrNull()
              backStack.add(Dashboard) 
            }
          )
        }
        entry<Dashboard> {
          DashboardScreen(
            systemMonitor = systemMonitor,
            onNavigateToProcesses = {
              backStack.add(Main)
            },
            onNavigateToSotDetail = {
              backStack.add(SotDetail)
            },
            onNavigateToSettings = {
              backStack.add(Settings)
            }
          )
        }
        entry<SotDetail> {
          com.example.prostats.ui.dashboard.SotDetailScreen(
            systemMonitor = systemMonitor,
            onNavigateBack = {
              backStack.removeLastOrNull()
            }
          )
        }
        entry<Settings> {
          com.example.prostats.ui.settings.SettingsScreen(
            systemMonitor = systemMonitor,
            onNavigateBack = {
              backStack.removeLastOrNull()
            }
          )
        }
        entry<Main> {
          val vm: MainScreenViewModel = viewModel { MainScreenViewModel(systemMonitor) }
          val state by vm.uiState.collectAsStateWithLifecycle()
          MainScreen(
            uiState = state,
            onForceStop = { vm.forceStop(it) },
            onFreeze = { vm.freeze(it) },
            onNavigateBack = {
              backStack.removeLastOrNull()
            }
          )
        }
      },
  )
}
