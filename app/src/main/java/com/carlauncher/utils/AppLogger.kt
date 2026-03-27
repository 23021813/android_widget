package com.carlauncher.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

object AppLogger {
    private const val MAX_LOGS = 500
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        log(LogLevel.ERROR, tag, msg)
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        // Mirror to system logcat
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARN -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }

        // Add to in-memory list
        val entry = LogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            tag = tag,
            message = message
        )

        val currentList = _logs.value.toMutableList()
        currentList.add(entry)
        if (currentList.size > MAX_LOGS) {
            currentList.removeAt(0)
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
