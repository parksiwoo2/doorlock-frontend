package com.example.doorlock.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState
}

data class EntryRecord(
    val timestamp: String,
    val description: String
)

data class HistoryUiState(
    val records: List<EntryRecord> = listOf(
        EntryRecord("2026-08-03 18:31", "입실"),
        EntryRecord("2026-08-03 19:12", "입실")
    )
)
