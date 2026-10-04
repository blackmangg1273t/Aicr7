package com.agentos.app.ui.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.data.termux.TermuxBridge
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class TerminalLine(val command: Boolean, val text: String)

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val busy: Boolean = false,
    val termuxAvailable: Boolean = false,
    val lastError: String? = null
)

/**
 * Direct, real shell access for the user (bypasses the AI). Same tool the
 * agents use: shell_exec via Termux when available, app sandbox otherwise.
 */
class TerminalViewModel(
    private val registry: ToolRegistry,
    private val termux: TermuxBridge,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _ui = MutableStateFlow(TerminalUiState())
    val ui: StateFlow<TerminalUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val available = termux.isInstalled()
            _ui.value = _ui.value.copy(
                termuxAvailable = available,
                lines = listOf(
                    TerminalLine(false, "AgentOS Terminal — real shell execution."),
                    TerminalLine(
                        false,
                        if (available) "Transport: Termux detected (full Linux userland if allow-external-apps=true)."
                        else "Transport: app sandbox shell (install Termux for a full Linux userland)."
                    )
                )
            )
        }
    }

    fun run(command: String) {
        if (command.isBlank() || _ui.value.busy) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, lines = _ui.value.lines + TerminalLine(true, "$ $command"))
            val tool = registry.get("shell_exec")
            if (tool == null) {
                _ui.value = _ui.value.copy(busy = false, lines = _ui.value.lines + TerminalLine(false, "shell_exec tool is not registered"))
                return@launch
            }
            val result = registry.execute(
                "shell_exec",
                JsonObject(mapOf("command" to kotlinx.serialization.json.JsonPrimitive(command))),
                ToolContext(appContext)
            )
            val lines = mutableListOf<TerminalLine>()
            if (result.output.isNotBlank()) result.output.lines().forEach { lines.add(TerminalLine(false, it)) }
            if (result.error != null) lines.add(TerminalLine(false, "[error] ${result.error} (${result.durationMs}ms)"))
            _ui.value = _ui.value.copy(busy = false, lines = _ui.value.lines + lines)
        }
    }

    fun testTermux() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val line = try {
                "Termux test: ${termux.testConnection()}"
            } catch (e: Exception) {
                "Termux test FAILED: ${e.message}"
            }
            _ui.value = _ui.value.copy(busy = false, lines = _ui.value.lines + TerminalLine(false, line))
        }
    }
}
