package com.example.doorlock.ui.history

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HistoryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HistoryUiState(
            myInRoom = true,
            myVisible = true,
            occupants = listOf(
                Occupant(
                    name = "김원효",
                    studentId = "2025104972"
                ),
                Occupant(
                    name = "홍길동",
                    studentId = "2024123456"
                )
            )
        )
    )

    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun setMyVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(
            myVisible = visible
        )
    }
}

data class Occupant(
    val name: String,
    val studentId: String
)

data class HistoryUiState(
    val myInRoom: Boolean = false,
    val myVisible: Boolean = true,
    val occupants: List<Occupant> = emptyList()
)
