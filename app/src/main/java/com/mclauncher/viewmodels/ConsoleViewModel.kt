package com.mclauncher.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mclauncher.ui.screens.LogEntry
import com.mclauncher.ui.screens.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ConsoleUiState(
    val logs: List<LogEntry> = emptyList(),
    val filteredLogs: List<LogEntry> = emptyList(),
    val autoScroll: Boolean = true,
    val showInfo: Boolean = true,
    val showWarning: Boolean = true,
    val showError: Boolean = true,
    val showDebug: Boolean = false
)

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val maxLogs = 1000

    init {
        // Add initial log
        addLog(LogLevel.INFO, "Console initialized")
    }

    fun addLog(level: LogLevel, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, message)

        _uiState.update { state ->
            val newLogs = (state.logs + entry).takeLast(maxLogs)
            state.copy(
                logs = newLogs,
                filteredLogs = filterLogs(newLogs, state)
            )
        }
    }

    fun addLogs(lines: List<String>) {
        lines.forEach { line ->
            val level = when {
                line.contains("[ERROR]", ignoreCase = true) || 
                line.contains("Exception", ignoreCase = true) -> LogLevel.ERROR
                line.contains("[WARN]", ignoreCase = true) || 
                line.contains("Warning", ignoreCase = true) -> LogLevel.WARNING
                line.contains("[DEBUG]", ignoreCase = true) -> LogLevel.DEBUG
                else -> LogLevel.INFO
            }
            addLog(level, line)
        }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun toggleFilter(level: LogLevel) {
        _uiState.update { state ->
            val newState = when (level) {
                LogLevel.INFO -> state.copy(showInfo = !state.showInfo)
                LogLevel.WARNING -> state.copy(showWarning = !state.showWarning)
                LogLevel.ERROR -> state.copy(showError = !state.showError)
                LogLevel.DEBUG -> state.copy(showDebug = !state.showDebug)
            }
            newState.copy(filteredLogs = filterLogs(state.logs, newState))
        }
    }

    private fun filterLogs(logs: List<LogEntry>, state: ConsoleUiState): List<LogEntry> {
        return logs.filter { entry ->
            when (entry.level) {
                LogLevel.INFO -> state.showInfo
                LogLevel.WARNING -> state.showWarning
                LogLevel.ERROR -> state.showError
                LogLevel.DEBUG -> state.showDebug
            }
        }
    }

    fun clearLogs() {
        _uiState.update { 
            it.copy(
                logs = emptyList(),
                filteredLogs = emptyList()
            )
        }
        addLog(LogLevel.INFO, "Console cleared")
    }

    fun copyLogs() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val logsText = _uiState.value.logs.joinToString("\n") { entry ->
            "[${entry.timestamp}] [${entry.level}] ${entry.message}"
        }
        val clip = ClipData.newPlainText("Console Logs", logsText)
        clipboard.setPrimaryClip(clip)
    }

    fun exportLogs(): String {
        return _uiState.value.logs.joinToString("\n") { entry ->
            "[${entry.timestamp}] [${entry.level}] ${entry.message}"
        }
    }
}
