package com.example.doorlock.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.doorlock.BleRelayService
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
     * DataStore 삭제가 성공했을 때만 UserSession을 비우고, 남아있을 수 있는
     * BLE 세션(광고/스캔)에는 기존에 공개된 STOP 액션만 전달합니다
     * (BLE 쪽 로직 자체는 수정하지 않음).
     */
    fun onUnregisterClicked() {
        viewModelScope.launch {
            repository.clearStudentId()
                .onSuccess {
                    UserSession.clear()
                    stopAnyActiveBleSession()
                    _uiState.value = _uiState.value.copy(
                        message = "학번 등록을 해제했습니다.",
                        unregistered = true
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        message = error.message ?: "등록 해제에 실패했습니다."
                    )
                }
        }
    }

    private fun stopAnyActiveBleSession() {
        val context = getApplication<Application>()
        val intent = Intent(context, BleRelayService::class.java)
            .setAction(BleRelayService.ACTION_STOP_ADVERTISING)
        context.startService(intent)
    }
}

data class SettingsUiState(
    val appVersion: String = "1.0",
    val message: String = "",
    val unregistered: Boolean = false
)
