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
import com.agentos.app.domain.model.PlanStepRequest
import com.agentos.app.domain.model.StepStatus
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

/** One plan step as shown in the execution card (real data from the planner). */
data class PlanStepUi(
    val index: Int,
    val agent: String,
    val tool: String?,
    val why: String,
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
    val error: String? = null
)

/** Screenshot / file attachment captured during execution. */
data class AttachmentUi(val path: String, val caption: String)

/** High-level execution phase (drives aura + status text). */
enum class ExecutionPhase { NONE, PLANNING, EXECUTING, WAITING_APPROVAL, DONE }

data class ChatUiState(
    val running: Boolean = false,
    val currentAgent: String? = null,
    val currentActivity: String? = null,
    val streamingAnswer: String? = null,
    val planSummary: String? = null,
    val pendingApproval: PendingApprovalUi? = null,
    val offline: Boolean = false,
    val lastError: String? = null,
    // ---- execution visibility (all real, from engine events) ----
    val taskId: String? = null,
    val phase: ExecutionPhase = ExecutionPhase.NONE,
    val plan: List<PlanStepUi> = emptyList(),
    val attachments: List<AttachmentUi> = emptyList(),
    val startedAtMs: Long? = null,         // wall time the task actually began
    val finalStatus: TaskStatus? = null,   // COMPLETED / FAILED / CANCELLED once done
    val finalResult: String? = null
) {
    val completedSteps: Int get() = plan.count { it.status == StepStatus.COMPLETED }
    val failedSteps: Boolean get() = plan.any { it.status == StepStatus.FAILED }
    val activePlanStep: PlanStepUi? get() = plan.firstOrNull { it.status == StepStatus.RUNNING }
}

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

    private fun updatePlan(taskId: String?, stepIndex: Int, transform: (PlanStepUi) -> PlanStepUi) {
        if (taskId != null && taskId != _ui.value.taskId) return
        _ui.value = _ui.value.copy(plan = _ui.value.plan.map { if (it.index == stepIndex) transform(it) else it })
    }

    private fun onEngineEvent(ev: TaskEvent) {
        when (ev) {
            is TaskEvent.PlanningStarted -> _ui.value = _ui.value.copy(
                running = true,
                taskId = ev.taskId,
                phase = ExecutionPhase.PLANNING,
                currentAgent = "Main Agent",
                currentActivity = "Understanding your request…",
                streamingAnswer = null,
                plan = emptyList(),
                attachments = emptyList(),
                startedAtMs = System.currentTimeMillis(),
                finalStatus = null,
                finalResult = null,
                planSummary = null
            )
            is TaskEvent.PlanCreated -> _ui.value = _ui.value.copy(
                phase = if (ev.plan.directAnswer) ExecutionPhase.PLANNING else ExecutionPhase.EXECUTING,
                plan = if (ev.plan.directAnswer) emptyList() else ev.plan.steps.mapIndexed { idx, s: PlanStepRequest ->
                    PlanStepUi(
                        index = idx,
                        agent = s.agent,
                        tool = s.tool,
                        why = s.why.ifBlank { s.tool ?: "Step ${idx + 1}" }
                    )
                },
                planSummary = if (ev.plan.directAnswer) "Direct answer" else "${ev.plan.steps.size} steps planned",
                currentActivity = if (ev.plan.directAnswer) "Composing answer…" else null
            )
            is TaskEvent.StepStarted -> {
                updatePlan(ev.taskId, ev.step.index) { it.copy(status = StepStatus.RUNNING, error = null) }
                _ui.value = _ui.value.copy(
                    running = true,
                    taskId = ev.taskId,
                    phase = if (_ui.value.phase == ExecutionPhase.PLANNING) ExecutionPhase.EXECUTING else _ui.value.phase,
                    currentAgent = ev.step.agentName,
                    currentActivity = ev.step.why.ifBlank { ev.step.toolName?.let { t -> "Using $t…" } ?: "Working…" }
                )
            }
            is TaskEvent.StepFinished -> {
                updatePlan(ev.taskId, ev.step.index) {
                    it.copy(
                        status = ev.step.status,
                        result = ev.step.result,
                        error = ev.step.error
                    )
                }
                _ui.value = _ui.value.copy(
                    currentAgent = null,
                    currentActivity = null
                )
            }
            is TaskEvent.AgentActivity -> _ui.value = _ui.value.copy(currentActivity = ev.activity)
            is TaskEvent.ToolStarted -> _ui.value = _ui.value.copy(
                currentActivity = "Running ${ev.tool.replace('_', ' ')}…"
            )
            is TaskEvent.ToolFinished -> {
                val note = if (ev.success) ev.output.take(80) else (ev.error ?: "failed")
                _ui.value = _ui.value.copy(currentActivity = null)
                Logger.d("ChatViewModel", "tool ${ev.tool}: $note")
            }
            is TaskEvent.ApprovalRequired -> _ui.value = _ui.value.copy(
                pendingApproval = PendingApprovalUi(ev.stepId, ev.tool, ev.args, ev.reason),
                phase = ExecutionPhase.WAITING_APPROVAL
            )
            is TaskEvent.ApprovalResolved -> _ui.value = _ui.value.copy(
                pendingApproval = null,
                phase = if (_ui.value.running) ExecutionPhase.EXECUTING else _ui.value.phase
            )
            is TaskEvent.AssistantChunk -> _ui.value = _ui.value.copy(
                streamingAnswer = (_ui.value.streamingAnswer ?: "") + ev.text
            )
            is TaskEvent.Attachment -> _ui.value = _ui.value.copy(
                attachments = _ui.value.attachments + AttachmentUi(ev.path, ev.caption)
            )
            is TaskEvent.Completed -> _ui.value = _ui.value.copy(
                running = false,
                streamingAnswer = null,
                currentAgent = null,
                currentActivity = null,
                phase = ExecutionPhase.DONE,
                finalStatus = TaskStatus.COMPLETED,
                finalResult = ev.finalResult
            )
            is TaskEvent.Error -> _ui.value = _ui.value.copy(
                lastError = ev.message,
                currentAgent = null,
                currentActivity = null,
                running = if (ev.recoverable) _ui.value.running else false,
                phase = if (ev.recoverable) _ui.value.phase else ExecutionPhase.DONE,
                finalStatus = if (ev.recoverable) _ui.value.finalStatus else TaskStatus.FAILED
            )
            is TaskEvent.TaskStatusChanged -> {
                when (ev.status) {
                    TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED -> _ui.value = _ui.value.copy(
                        running = false,
                        streamingAnswer = null,
                        phase = ExecutionPhase.DONE,
                        finalStatus = ev.status
                    )
                    else -> Unit
                }
            }
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
            _ui.value = _ui.value.copy(
                running = true,
                lastError = null,
                streamingAnswer = null,
                planSummary = null,
                plan = emptyList(),
                attachments = emptyList(),
                finalStatus = null,
                finalResult = null,
                taskId = null,
                startedAtMs = System.currentTimeMillis(),
                phase = ExecutionPhase.PLANNING,
                currentAgent = "Main Agent",
                currentActivity = "Understanding your request…"
            )
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

    fun clearExecution() {
        _ui.value = _ui.value.copy(phase = ExecutionPhase.NONE, plan = emptyList(), attachments = emptyList(), finalStatus = null, finalResult = null, startedAtMs = null)
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
