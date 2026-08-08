package com.example.doorlock.ui.splash

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SplashViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState
}

data class SplashUiState(
    val isLoading: Boolean = true,
    val message: String = "자동 로그인 여부 확인 중..."
)
