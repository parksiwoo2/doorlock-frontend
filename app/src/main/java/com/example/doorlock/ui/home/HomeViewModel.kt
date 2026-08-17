package com.example.doorlock.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.doorlock.R
import com.example.doorlock.RelayStatusStore
import com.example.doorlock.ui.history.EntryRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 기존 XML MainActivity의 main_container에 있던 "relay_status_text" 실시간 상태 표시를
 * 복원한 것입니다. BLE 로직을 새로 만들지 않고, BLE 팀이 이미 공개해 둔 읽기 전용
 * 저장소(RelayStatusStore)를 그대로 읽기만 합니다. 갱신 주기(1초)도 기존
 * Handler.post 방식과 동일하게 맞췄습니다.
 *
 * 원치 않으면 이 폴링 로직만 제거하고 기존 정적 placeholder로 되돌리면 됩니다.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                refreshBleStatus()
                delay(1_000L)
            }
        }
    }

    private fun refreshBleStatus() {
        val app = getApplication<Application>()
        if (!RelayStatusStore.isScanRegistered(app)) {
            _uiState.value = _uiState.value.copy(
                bleStatus = "대기 중",
                connectionStatus = app.getString(R.string.status_not_configured)
            )
            return
        }

        val (statusLabel, detailText) = when (RelayStatusStore.relayPhase(app)) {
            RelayStatusStore.RelayPhase.WATCHING_0312 ->
                "감시 중" to app.getString(R.string.status_watching_0312)

            RelayStatusStore.RelayPhase.REQUESTING_OPEN ->
                "문 열림 요청 중" to app.getString(R.string.status_requesting_open)

            RelayStatusStore.RelayPhase.INSIDE_ROOM ->
                "동방 안" to app.getString(R.string.status_inside_room)
        }
        _uiState.value = _uiState.value.copy(bleStatus = statusLabel, connectionStatus = detailText)
    }
}

data class HomeUiState(
    val bleStatus: String = "대기 중",
    val connectionStatus: String = "BLE 감지 중",
    val recentRecords: List<EntryRecord> = listOf(
        EntryRecord("2026-08-03 18:31", "입실"),
        EntryRecord("2026-08-03 19:12", "퇴실")
    )
)
