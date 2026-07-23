package com.example.prostats.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prostats.data.ProcessItem
import com.example.prostats.data.SystemMonitor
import com.example.prostats.ui.main.MainScreenUiState.Success
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(private val systemMonitor: SystemMonitor) : ViewModel() {
    
    val uiState: StateFlow<MainScreenUiState> =
        systemMonitor.getProcessUpdates()
            .map<List<ProcessItem>, MainScreenUiState>(::Success)
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    fun forceStop(packageName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            systemMonitor.forceStopApp(packageName)
        }
    }

    fun freeze(packageName: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            systemMonitor.freezeApp(packageName)
        }
    }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: List<ProcessItem>) : MainScreenUiState
}
