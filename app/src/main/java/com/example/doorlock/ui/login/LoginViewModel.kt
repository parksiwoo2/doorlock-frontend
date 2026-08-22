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
 * 학번 변경은 항상 [StudentIdRepository]를 거치며, DataStore 쓰기가 성공했을 때만
 * UserSession(메모리 캐시)을 갱신합니다. 즉 DataStore가 원본, UserSession은 그 결과를
 * 따라가는 캐시라는 원칙을 코드로 강제합니다.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StudentIdRepository(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun onStudentIdChange(value: String) {
        _uiState.value = _uiState.value.copy(studentId = value, errorMessage = "")
    }

    /**
     * 학번을 기기에 등록합니다. 입력이 비어 있으면 즉시 오류를 표시하고,
     * 그 외의 형식 검증(숫자 10자리)과 실제 저장은 Repository가 전담합니다.
     * 등록이 완료되면 [LoginUiState.registrationComplete] 가 true 로 바뀌며,
     * 화면(LoginScreen)이 이를 관찰해서 다음 동작(Home 이동, BLE 설정 시작)을 트리거합니다.
     */
    fun register() {
        val id = _uiState.value.studentId.trim()
        if (id.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "학번을 입력해 주세요.")
            return
        }

        viewModelScope.launch {
            repository.registerStudentId(id)
                .onSuccess {
                    // DataStore 쓰기가 성공한 뒤에만 UserSession을 갱신합니다.
                    UserSession.setUser(id)
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "",
                        registrationComplete = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "등록에 실패했습니다."
                    )
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
    val registrationComplete: Boolean = false
)
