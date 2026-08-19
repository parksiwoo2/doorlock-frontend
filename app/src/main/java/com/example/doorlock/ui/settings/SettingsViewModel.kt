package com.example.doorlock.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.doorlock.data.StudentIdRepository
import com.example.doorlock.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentIdRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    /**
     * 등록된 학번을 이 기기에서 해제합니다.
     * UserSession(메모리)를 지우기 전에 DataStore에서 먼저 해제합니다.
     */
    fun unregister(onComplete: (Boolean) -> Unit) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val result = repository.clearStudentId()
            if (result.isSuccess) {
                UserSession.clear()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "학번 등록을 해제했습니다."
                )
                onComplete(true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "등록 해제에 실패했습니다. 다시 시도해 주세요."
                )
                onComplete(false)
            }
        }
    }
}

data class SettingsUiState(
    val appVersion: String = "1.0",
    val message: String = "",
    val isLoading: Boolean = false
)
