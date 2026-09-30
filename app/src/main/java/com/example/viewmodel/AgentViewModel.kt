package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agent.AgentTask
import com.example.agent.MainAgentOrchestrator
import com.example.agent.TaskStatus
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolRegistry
import com.example.mcp.tools.*
import com.example.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentUiState(
    val currentTab: NavigationTab = NavigationTab.CHAT,
    val messages: List<ChatMessage> = emptyList(),
    val currentTask: AgentTask? = null,
    val taskHistory: List<AgentTask> = emptyList(),
    val registeredTools: List<McpToolDefinition> = emptyList(),
    val isRunning: Boolean = false,
    val terminalOutput: String = "AgentOS Virtual Terminal Ready.\nType commands or let the Coding Agent operate.",
    val pendingApproval: PendingApprovalRequest? = null,
    val modelConfig: ModelConfig = ModelConfig(),
    val memoryRecords: List<String> = emptyList(),
    val isCompanionConnected: Boolean = false
)

enum class NavigationTab(val title: String) {
    CHAT("Agents"),
    TASKS("Tasks"),
    TERMINAL("Terminal"),
    TOOLS("MCP Tools"),
    SETTINGS("Settings")
}

data class PendingApprovalRequest(
    val toolName: String,
    val argumentsJson: String,
    val deferred: CompletableDeferred<Boolean>
)

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val toolRegistry = McpToolRegistry()
    private val orchestrator: MainAgentOrchestrator
    private val memoryHandler: MemoryToolHandler

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    init {
        // Register all built-in MCP Tool Subsystems
        val terminalHandler = TerminalToolHandler(application)
        toolRegistry.registerTool(TerminalToolHandler.EXEC, terminalHandler)
        toolRegistry.registerTool(TerminalToolHandler.READ_FILE, terminalHandler)
        toolRegistry.registerTool(TerminalToolHandler.WRITE_FILE, terminalHandler)
        toolRegistry.registerTool(TerminalToolHandler.LIST_DIR, terminalHandler)

        val searchHandler = SearchToolHandler()
        toolRegistry.registerTool(SearchToolHandler.SEARCH_WEB, searchHandler)
        toolRegistry.registerTool(SearchToolHandler.SEARCH_FETCH, searchHandler)

        val phoneHandler = PhoneToolHandler(application)
        toolRegistry.registerTool(PhoneToolHandler.LAUNCH_APP, phoneHandler)
        toolRegistry.registerTool(PhoneToolHandler.OPEN_URL, phoneHandler)
        toolRegistry.registerTool(PhoneToolHandler.DEVICE_INFO, phoneHandler)
        toolRegistry.registerTool(PhoneToolHandler.TAP, phoneHandler)
        toolRegistry.registerTool(PhoneToolHandler.SWIPE, phoneHandler)

        val gitHandler = GitToolHandler(application)
        toolRegistry.registerTool(GitToolHandler.GIT_STATUS, gitHandler)
        toolRegistry.registerTool(GitToolHandler.GIT_CLONE, gitHandler)
        toolRegistry.registerTool(GitToolHandler.GIT_DIFF, gitHandler)
        toolRegistry.registerTool(GitToolHandler.GIT_LOG, gitHandler)
        toolRegistry.registerTool(GitToolHandler.GIT_COMMIT, gitHandler)

        memoryHandler = MemoryToolHandler(application)
        toolRegistry.registerTool(MemoryToolHandler.STORE, memoryHandler)
        toolRegistry.registerTool(MemoryToolHandler.SEARCH, memoryHandler)
        toolRegistry.registerTool(MemoryToolHandler.RETRIEVE, memoryHandler)
        toolRegistry.registerTool(MemoryToolHandler.DELETE, memoryHandler)
        toolRegistry.registerTool(MemoryToolHandler.LIST_ALL, memoryHandler)

        val browserHandler = BrowserToolHandler(application)
        toolRegistry.registerTool(BrowserToolHandler.OPEN, browserHandler)
        toolRegistry.registerTool(BrowserToolHandler.READ, browserHandler)
        toolRegistry.registerTool(BrowserToolHandler.EVALUATE, browserHandler)

        val openCodeHandler = OpenCodeBridgeToolHandler()
        toolRegistry.registerTool(OpenCodeBridgeToolHandler.STATUS, openCodeHandler)
        toolRegistry.registerTool(OpenCodeBridgeToolHandler.EDIT, openCodeHandler)
        toolRegistry.registerTool(OpenCodeBridgeToolHandler.ANALYZE, openCodeHandler)

        orchestrator = MainAgentOrchestrator(toolRegistry)

        _uiState.value = _uiState.value.copy(
            registeredTools = toolRegistry.getAllTools(),
            messages = listOf(
                ChatMessage(
                    sender = MessageSender.SYSTEM,
                    text = "Welcome to AgentOS — Personal Multi-Agent AI.\n\nAll 7 MCP tool subsystems are loaded:\n• Phone Automation (Accessibility/Intents)\n• Browser Agent (Headless WebView)\n• Coding Agent & OpenCode Bridge\n• Terminal & Shell Environment\n• Git Engine\n• Web Search Engine\n• Long-Term SQLite/Room Memory\n\nWhat goal would you like to accomplish?"
                )
            )
        )
        refreshMemoryList()
    }

    fun selectTab(tab: NavigationTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
        if (tab == NavigationTab.TOOLS) {
            refreshMemoryList()
        }
    }

    fun updateApiKey(key: String) {
        val current = _uiState.value.modelConfig
        _uiState.value = _uiState.value.copy(modelConfig = current.copy(apiKey = key))
    }

    fun updateModelProvider(provider: ModelProviderType) {
        val current = _uiState.value.modelConfig
        _uiState.value = _uiState.value.copy(
            modelConfig = current.copy(
                provider = provider,
                baseUrl = provider.defaultEndpoint
            )
        )
    }

    fun submitGoal(goal: String) {
        if (goal.isBlank() || _uiState.value.isRunning) return

        val userMsg = ChatMessage(sender = MessageSender.USER, text = goal)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            isRunning = true
        )

        viewModelScope.launch {
            val agentMsg = ChatMessage(
                sender = MessageSender.MAIN_AGENT,
                text = "Starting task orchestration...",
                isStreaming = true
            )
            _uiState.value = _uiState.value.copy(messages = _uiState.value.messages + agentMsg)

            val fullText = StringBuilder()
            orchestrator.executeGoal(
                goal = goal,
                modelConfig = _uiState.value.modelConfig,
                onSubtaskUpdate = { updatedTask ->
                    _uiState.value = _uiState.value.copy(
                        currentTask = updatedTask,
                        taskHistory = listOf(updatedTask) + _uiState.value.taskHistory.filterNot { it.id == updatedTask.id }
                    )
                },
                onRequireUserApproval = { toolName, args ->
                    val deferred = CompletableDeferred<Boolean>()
                    _uiState.value = _uiState.value.copy(
                        pendingApproval = PendingApprovalRequest(toolName, args, deferred)
                    )
                    deferred.await()
                }
            ).collect { chunk ->
                fullText.append(chunk)
                val updatedMessages = _uiState.value.messages.map { msg ->
                    if (msg.id == agentMsg.id) msg.copy(text = fullText.toString()) else msg
                }
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    terminalOutput = _uiState.value.terminalOutput + "\n" + chunk.trim()
                )
            }

            val finalMessages = _uiState.value.messages.map { msg ->
                if (msg.id == agentMsg.id) msg.copy(isStreaming = false) else msg
            }
            _uiState.value = _uiState.value.copy(
                messages = finalMessages,
                isRunning = false
            )
            refreshMemoryList()
        }
    }

    fun resolveApproval(approved: Boolean) {
        val pending = _uiState.value.pendingApproval ?: return
        pending.deferred.complete(approved)
        _uiState.value = _uiState.value.copy(pendingApproval = null)
    }

    fun runTerminalCommand(cmd: String) {
        if (cmd.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                terminalOutput = _uiState.value.terminalOutput + "\n$ " + cmd
            )
            val res = toolRegistry.executeTool("terminal.exec", org.json.JSONObject().put("command", cmd))
            val output = if (res.success) res.output else "Error: ${res.error}\n${res.output}"
            _uiState.value = _uiState.value.copy(
                terminalOutput = _uiState.value.terminalOutput + "\n" + output.trim()
            )
        }
    }

    private fun refreshMemoryList() {
        viewModelScope.launch {
            val res = toolRegistry.executeTool("memory.list_all", org.json.JSONObject())
            if (res.success) {
                _uiState.value = _uiState.value.copy(memoryRecords = res.output.lines().filter { it.isNotBlank() })
            }
        }
    }
}
