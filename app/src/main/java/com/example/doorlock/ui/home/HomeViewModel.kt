package com.example.doorlock.ui.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
}

data class HomeUiState(
    val userName: String = "사용자 이름",
    val studentId: String = "2026123456",
    val bleStatus: String = "대기 중",
    val connectionStatus: String = "BLE 감지 중"
)
