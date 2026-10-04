package com.agentos.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.core.logging.Logger
import com.agentos.app.data.db.MessageEntity
import com.agentos.app.data.repo.ChatRepository
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.domain.engine.TaskEngine
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.MessageRole
import com.agentos.app.domain.model.TaskEvent
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.service.TaskForegroundService
import com.agentos.app.core.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val running: Boolean = false,
    val currentAgent: String? = null,
    val currentActivity: String? = null,
    val streamingAnswer: String? = null,
    val planSummary: String? = null,
    val pendingApproval: PendingApprovalUi? = null,
    val offline: Boolean = false,
    val lastError: String? = null
)

data class PendingApprovalUi(
    val stepId: String,
    val tool: String,
    val args: String,
    val reason: String
)

class ChatViewModel(
    private val engine: TaskEngine,
    private val chatRepo: ChatRepository,
    private val taskRepo: TaskRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val conversation = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = conversation.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = conversation
        .flatMapLatest { id ->
            if (id == null) kotlinx.coroutines.flow.flowOf(emptyList()) else chatRepo.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { ensureConversation() }
        viewModelScope.launch {
            // observe engine events → live UI
            engine.events.collect { ev -> onEngineEvent(ev) }
        }
        viewModelScope.launch {
            networkMonitor.isOnlineFlow.collect { online ->
                _ui.value = _ui.value.copy(offline = !online)
            }
        }
    }

    private suspend fun ensureConversation(): String {
        conversation.value?.let { return it }
        val conv = chatRepo.ensureConversation(null, "Chat")
        conversation.value = conv.id
        return conv.id
    }

    private fun onEngineEvent(ev: TaskEvent) {
        when (ev) {
            is TaskEvent.PlanningStarted -> _ui.value = _ui.value.copy(
                running = true, currentAgent = "Main Agent", currentActivity = "Planning…", streamingAnswer = null
            )
            is TaskEvent.PlanCreated -> _ui.value = _ui.value.copy(
                planSummary = if (ev.plan.directAnswer) "Direct answer" else "${ev.plan.steps.size} steps planned"
            )
            is TaskEvent.StepStarted -> _ui.value = _ui.value.copy(
                running = true, currentAgent = ev.step.agentName,
                currentActivity = ev.step.toolName?.let { "Using $it…" } ?: "Working…"
            )
            is TaskEvent.StepFinished -> _ui.value = _ui.value.copy(
                currentAgent = null, currentActivity = null
            )
            is TaskEvent.AgentActivity -> _ui.value = _ui.value.copy(currentActivity = ev.activity)
            is TaskEvent.ApprovalRequired -> _ui.value = _ui.value.copy(
                pendingApproval = PendingApprovalUi(ev.stepId, ev.tool, ev.args, ev.reason)
            )
            is TaskEvent.ApprovalResolved -> _ui.value = _ui.value.copy(pendingApproval = null)
            is TaskEvent.AssistantChunk -> _ui.value = _ui.value.copy(
                streamingAnswer = (_ui.value.streamingAnswer ?: "") + ev.text
            )
            is TaskEvent.Completed -> {
                _ui.value = _ui.value.copy(running = false, streamingAnswer = null, currentAgent = null, currentActivity = null)
            }
            is TaskEvent.Error -> _ui.value = _ui.value.copy(
                running = false, lastError = ev.message, currentAgent = null, currentActivity = null
            )
            is TaskEvent.TaskStatusChanged -> {
                if (ev.status in setOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
                    _ui.value = _ui.value.copy(running = false, streamingAnswer = null)
                }
            }
            else -> Unit
        }
    }

    fun send(text: String) {
        val request = text.trim()
        if (request.isEmpty() || _ui.value.running) return
        viewModelScope.launch {
            val convId = ensureConversation()
            chatRepo.addMessage(
                com.agentos.app.domain.model.ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = convId,
                    role = MessageRole.USER,
                    text = request
                )
            )
            _ui.value = _ui.value.copy(running = true, lastError = null, streamingAnswer = null, planSummary = null)
            engine.start(request, convId)
        }
    }

    fun resolveApproval(approved: Boolean) {
        val pending = _ui.value.pendingApproval ?: return
        engine.resolveApproval(pending.stepId, approved)
    }

    fun cancelTask() {
        val taskId = engine.activeTasks.value.entries
            .filter { it.value.status in setOf(TaskStatus.PLANNING, TaskStatus.RUNNING, TaskStatus.WAITING_USER, TaskStatus.PENDING) }
            .maxByOrNull { it.value.createdAt }?.key
        if (taskId != null) engine.cancel(taskId)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(lastError = null)
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
