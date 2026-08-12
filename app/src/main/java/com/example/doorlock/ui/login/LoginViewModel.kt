package com.example.doorlock.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.doorlock.data.StudentIdRepository
import com.example.doorlock.data.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 학번 등록 화면의 ViewModel.
 *
 * 기존에는 학번 + 비밀번호를 검증하는 "로그인" 개념이었지만,
 * 이제는 비밀번호 인증 없이 학번만 기기에 등록하는 구조로 변경되었습니다.
 * (요구사항: 비밀번호 필드/상태/로직 완전 제거)
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentIdRepository(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onStudentIdChange(value: String) {
        _uiState.value = _uiState.value.copy(studentId = value, errorMessage = "")
    }

    /**
     * 학번을 기기에 등록합니다.
     * DataStore 저장이 성공한 경우에만 UserSession을 갱신합니다.
     */
    fun register(onComplete: (Boolean) -> Unit) {
        val id = _uiState.value.studentId.trim()
        if (id.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "학번을 입력해 주세요.")
            onComplete(false)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = "")
        viewModelScope.launch {
            val result = repository.registerStudentId(id)
            if (result.isSuccess) {
                UserSession.setUser(id)
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "")
                onComplete(true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "등록에 실패했습니다. 다시 시도해 주세요."
                )
                onComplete(false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = "")
    }
}

data class LoginUiState(
    val studentId: String = "",
    val errorMessage: String = "",
    val isLoading: Boolean = false
)
