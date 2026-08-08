package com.example.doorlock.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun onLogoutClicked() {
        _uiState.value = _uiState.value.copy(message = "로그아웃을 실행했습니다.")
    }
}

data class SettingsUiState(
    val appVersion: String = "1.0",
    val message: String = ""
)
