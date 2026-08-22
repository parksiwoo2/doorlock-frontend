package com.example.doorlock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class UserSessionData(
    val studentId: String
)

object UserSession {
    private val _user = MutableStateFlow<UserSessionData?>(null)
    val user: StateFlow<UserSessionData?> = _user

    fun setUser(studentId: String) {
        _user.value = UserSessionData(studentId = studentId)
    }

    fun clear() {
        _user.value = null
    }
}
