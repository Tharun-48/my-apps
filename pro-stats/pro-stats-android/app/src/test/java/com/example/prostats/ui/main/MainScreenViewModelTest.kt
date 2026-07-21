package com.example.prostats.ui.main

import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initialState() = runTest {
    // Basic test assertion placeholder
    assertEquals(MainScreenUiState.Loading, MainScreenUiState.Loading)
  }
}
