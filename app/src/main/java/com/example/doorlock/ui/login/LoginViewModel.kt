package com.example.doorlock.ui.login

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onStudentIdChange(value: String) {
        _uiState.value = _uiState.value.copy(studentId = value)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun onChangeStudentIdClick() {
        _uiState.value = _uiState.value.copy(errorMessage = "학번 변경을 선택했습니다.")
    }
}

data class LoginUiState(
    val studentId: String = "",
    val password: String = "",
    val errorMessage: String = ""
)
