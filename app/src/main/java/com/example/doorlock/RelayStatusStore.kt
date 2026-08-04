package com.example.doorlock

import android.content.Context
import java.text.DateFormat
import java.util.Date

object RelayStatusStore {
    private const val preferencesName = "relay_status"
    private const val eventsKey = "events"
    private const val scanRegisteredKey = "scan_registered"
    private const val advertisingKey = "advertising"
    private const val studentIdKey = "student_id"
    private const val maxEvents = 60

    @Synchronized
    fun addEvent(context: Context, message: String) {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val timestamp = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
        val updated = (preferences.getString(eventsKey, "").orEmpty().lineSequence()
            .filter { it.isNotBlank() }
            .toList() + "[$timestamp] $message")
            .takeLast(maxEvents)
            .joinToString("\n")
        preferences.edit().putString(eventsKey, updated).apply()
    }

    fun events(context: Context): String =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(eventsKey, "")
            .orEmpty()

    fun setScanRegistered(context: Context, registered: Boolean) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(scanRegisteredKey, registered)
            .apply()
    }

    fun isScanRegistered(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(scanRegisteredKey, false)

    fun setAdvertising(context: Context, advertising: Boolean) {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(advertisingKey, advertising)
            .apply()
    }

    fun isAdvertising(context: Context): Boolean =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getBoolean(advertisingKey, false)

    fun setStudentId(context: Context, studentId: String) {
        require(studentId.length == 10 && studentId.all(Char::isDigit)) {
            "Student ID must contain exactly 10 digits."
        }
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(studentIdKey, studentId)
            .apply()
    }

    fun studentId(context: Context): String? =
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .getString(studentIdKey, null)
            ?.takeIf { it.length == 10 && it.all(Char::isDigit) }
}
