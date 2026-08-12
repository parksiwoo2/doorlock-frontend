package com.example.doorlock.ui.home

import androidx.lifecycle.ViewModel
import com.example.doorlock.ui.history.EntryRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState
}

data class HomeUiState(
    val bleStatus: String = "대기 중",
    val connectionStatus: String = "BLE 감지 중",
    val recentRecords: List<EntryRecord> = listOf(
        EntryRecord("2026-08-03 18:31", "입실"),
        EntryRecord("2026-08-03 19:12", "퇴실")
    )
)
