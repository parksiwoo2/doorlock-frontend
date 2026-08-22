package com.example.doorlock.ui.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.doorlock.data.StudentIdRepository
import com.example.doorlock.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Splash가 확인을 마친 뒤 이동해야 할 목적지. */
sealed class SplashDestination {
    /** 등록된 학번이 없음 → 학번 등록 화면으로 이동 */
    object Register : SplashDestination()

    /** 등록된 학번이 있음 → 등록 화면을 건너뛰고 Home으로 이동 */
    object Home : SplashDestination()
}

class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentIdRepository(application)

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination

    init {
        checkRegisteredStudentId()
    }

    private fun checkRegisteredStudentId() {
        viewModelScope.launch {
            val savedId = repository.studentId.first()
            if (!savedId.isNullOrBlank()) {
                // 기기에 이미 등록된 학번이 있으면 UserSession(메모리)에 채워 넣고 Home으로 바로 이동.
                UserSession.setUser(savedId)
                _uiState.value = _uiState.value.copy(message = "등록된 학번을 확인했습니다.")
                _destination.value = SplashDestination.Home
            } else {
                _uiState.value = _uiState.value.copy(message = "등록된 학번이 없습니다.")
                _destination.value = SplashDestination.Register
            }
        }
    }
}

data class SplashUiState(
    val isLoading: Boolean = true,
    val message: String = "등록된 학번을 확인 중..."
)
