package com.example.doorlock.ui.history

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.doorlock.BleRelayService
import com.example.doorlock.RelayStatusStore
import com.example.doorlock.data.network.Occupant
import com.example.doorlock.data.network.OccupancyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * v8 명세서 기준: Backend는 status/occupantCount/inRoom을 제공하지 않는다.
 * "이 데이터가 오래됐는지"는 Backend와의 API 계약이 아니라 앱 내부에서만 판단하는 값이다.
 */
private const val OCCUPANCY_STALE_THRESHOLD_SECONDS = 60L

/**
 * "기록" 탭(현재는 "동방 재실 현황")의 ViewModel.
 *
 * 두 가지 완전히 분리된 흐름을 가진다 (v8 명세서 11번 참고).
 *  1) Backend GET /occupants → 현재 공개 재실자 목록 조회 (uiState)
 *  2) RelayStatusStore.setPresenceVisible() → 로컬 저장 + BLE 광고 payload 반영 (isPresenceVisible)
 * 두 흐름은 서로 API를 호출하지 않는다. 공개 토글은 Backend와 통신하지 않는다.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OccupancyRepository()

    private val _uiState = MutableStateFlow<OccupancyUiState>(OccupancyUiState.Loading)
    val uiState: StateFlow<OccupancyUiState> = _uiState.asStateFlow()

    private val _isPresenceVisible = MutableStateFlow(
        RelayStatusStore.isPresenceVisible(application)
    )
    val isPresenceVisible: StateFlow<Boolean> = _isPresenceVisible.asStateFlow()

    init {
        refresh()
    }

    /** GET /occupants를 호출해 현재 공개 재실자 목록을 새로 받아온다. */
    fun refresh() {
        _uiState.value = OccupancyUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val response = repository.fetchOccupants()
                val syncedAt = runCatching { Instant.parse(response.lastSyncedAt) }.getOrNull()
                val secondsSinceSync = syncedAt?.let { Instant.now().epochSecond - it.epochSecond }

                when {
                    // lastSyncedAt을 파싱 못했거나, 60초 이상 지났으면 신뢰할 수 없는 데이터로 취급.
                    secondsSinceSync == null -> OccupancyUiState.Stale
                    secondsSinceSync >= OCCUPANCY_STALE_THRESHOLD_SECONDS -> OccupancyUiState.Stale
                    else -> OccupancyUiState.Success(response.occupants)
                }
            } catch (exception: Exception) {
                OccupancyUiState.Error
            }
        }
    }

    /**
     * 재실 상태 공개 여부를 변경한다.
     * Backend API를 호출하지 않는다 — 로컬 저장(RelayStatusStore) + 활성 BLE 세션이 있으면
     * 즉시 재광고하도록 알리는 것까지가 전부다. (기존 XML MainActivity의 흐름을 그대로 유지)
     */
    fun setPresenceVisible(visible: Boolean) {
        val context = getApplication<Application>()
        RelayStatusStore.setPresenceVisible(context, visible)
        RelayStatusStore.addEvent(
            context,
            if (visible) "재실 상태 공개 켜짐" else "재실 상태 공개 꺼짐"
        )
        _isPresenceVisible.value = visible

        if (BleRelayService.isSessionActive()) {
            try {
                context.startService(
                    Intent(context, BleRelayService::class.java)
                        .setAction(BleRelayService.ACTION_VISIBILITY_CHANGED)
                )
            } catch (exception: RuntimeException) {
                RelayStatusStore.addEvent(
                    context,
                    "재실 공개 설정 BLE 반영 실패: ${exception.message}"
                )
            }
        }
    }
}

sealed class OccupancyUiState {
    data object Loading : OccupancyUiState()
    data class Success(val occupants: List<Occupant>) : OccupancyUiState()
    data object Stale : OccupancyUiState()
    data object Error : OccupancyUiState()
}
