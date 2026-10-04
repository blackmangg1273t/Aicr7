package com.agentos.app.domain.engine

import com.agentos.app.core.logging.Logger
import com.agentos.app.core.network.ConnectivityChecker
import com.agentos.app.core.security.SecretStore
import com.agentos.app.data.provider.AiExecutor
import com.agentos.app.data.provider.ChatTurn
import com.agentos.app.data.provider.StreamEvent
import com.agentos.app.data.repo.ChatRepository
import com.agentos.app.data.repo.MemoryRepository
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.domain.agent.AgentRegistry
import com.agentos.app.domain.agent.MainAgent
import com.agentos.app.domain.agent.StepContext
import com.agentos.app.domain.model.AgentTask
import com.agentos.app.domain.model.ChatMessage
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.MessageRole
import com.agentos.app.domain.model.PlanStepRequest
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskEvent
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.domain.model.TaskStep
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolRegistry
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Real task engine: plan (AI) → approve (user, when risky) → execute (agents +
 * tools) → verify → synthesize (AI, streamed). All state is persisted in Room;
 * all events are streamed to the UI. Failures are honest and actionable.
 */
class TaskEngine(
    private val scope: CoroutineScope,
    private val providerExecutor: AiExecutor,
    private val settings: SettingsRepository,
    private val secureStore: SecretStore,
    private val agentRegistry: AgentRegistry,
    private val toolRegistry: ToolRegistry,
    private val chatRepo: ChatRepository,
    private val taskRepo: TaskRepository,
    private val memoryRepo: MemoryRepository,
    private val networkMonitor: ConnectivityChecker
) {
    private val _events = MutableSharedFlow<TaskEvent>(extraBufferCapacity = 512)
    val events: SharedFlow<TaskEvent> = _events.asSharedFlow()

    private val _activeTasks = MutableStateFlow<Map<String, AgentTask>>(emptyMap())
    val activeTasks: StateFlow<Map<String, AgentTask>> = _activeTasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val taskConversation = ConcurrentHashMap<String, String>()
    private val eventMutex = Mutex()

    private val approvals = ApprovalManager(
        conversationId = { taskConversation.values.lastOrNull() ?: "" },
        persistEvent = { msg -> scope.launch { chatRepo.addMessage(msg) } },
        emit = { ev -> _events.emit(ev) }
    )

    /* ------------------------- public API ------------------------- */

    fun start(userRequest: String, conversationId: String): String {
        val taskId = UUID.randomUUID().toString()
        taskConversation[taskId] = conversationId
        val task = AgentTask(
            id = taskId,
            userRequest = userRequest,
            status = TaskStatus.PENDING,
            conversationId = conversationId
        )
        scope.launch {
            taskRepo.create(task)
            updateActive(task)
        }
        jobs[taskId] = scope.launch { runTask(task) }
        return taskId
    }

    fun cancel(taskId: String) {
        jobs[taskId]?.cancel()
    }

    fun resolveApproval(stepId: String, approved: Boolean) = approvals.resolve(stepId, approved)

    /* ------------------------- core runner ------------------------- */

    private suspend fun runTask(initial: AgentTask) {
        val task = initial
        val conversationId = taskConversation[task.id] ?: ""
        try {
            setTaskStatus(task, TaskStatus.PLANNING)

            // 1. Provider must be configured — honest failure otherwise.
            val primary = settings.primaryProvider()
            val fallback = settings.fallbackProvider()
            if (primary == null) {
                throw ProviderNotConfigured(
                    "No AI provider is configured. Open Settings → AI Providers, pick one and add its API key."
                )
            }
            val primaryKey = secureStore.getSecret(providerKey(primary.id)) ?: ""
            val fallbackKey = fallback?.let { secureStore.getSecret(providerKey(it.id)) ?: "" } ?: ""
            if (primaryKey.isBlank() && fallbackKey.isBlank()) {
                throw ProviderNotConfigured(
                    "Provider '${primary.displayName}' has no API key. Open Settings → AI Providers and set it."
                )
            }

            if (!networkMonitor.isOnline()) {
                throw OfflineException(
                    "The device is offline. AI planning needs internet; local tools still work from the Terminal tab."
                )
            }

            val aiCall: suspend (List<ChatTurn>) -> String = { messages ->
                var out = ""
                var err: Throwable? = null
                providerExecutor.streamWithFallback(primary, primaryKey, fallback, fallbackKey, messages).collect { ev ->
                    when (ev) {
                        is StreamEvent.Chunk -> out += ev.text
                        is StreamEvent.Completed -> if (out.isBlank()) out = ev.fullText
                        is StreamEvent.Failed -> if (err == null) err = ev.error
                    }
                }
                err?.let { throw it }
                out
            }

            suspend fun aiStream(messages: List<ChatTurn>, onChunk: (String) -> Unit): String {
                var full = ""
                var failure: Throwable? = null
                providerExecutor.streamWithFallback(primary, primaryKey, fallback, fallbackKey, messages).collect { ev ->
                    when (ev) {
                        is StreamEvent.Chunk -> { full += ev.text; onChunk(ev.text) }
                        is StreamEvent.Completed -> { if (full.isBlank()) full = ev.fullText }
                        is StreamEvent.Failed -> failure = ev.error
                    }
                }
                failure?.let { throw it }
                return full
            }

            // 2. Memory + history context
            val memoryContext = if (settings.privacyFlow.first().autoMemoryEnabled) {
                memoryRepo.search(task.userRequest.take(60), 5).joinToString("\n") { "[${it.key}] ${it.content.take(160)}" }
            } else ""
            val history = chatRepo.history(conversationId)
                .map { ChatTurn(if (it.role == MessageRole.USER) "user" else "assistant", it.text) }

            // 3. AI planning
            emit(TaskEvent.PlanningStarted(task.id))
            persistEvent(ChatMessage(
                id = UUID.randomUUID().toString(), conversationId = conversationId,
                role = MessageRole.EVENT, text = "Planning with ${primary.displayName} (${primary.model})…",
                eventType = EventType.PLANNING
            ))

            val mainAgent = agentRegistry.all().filterIsInstance<MainAgent>().first()
            val plannerPrompt = buildString {
                append(task.userRequest)
            }
            val rawPlan = planWithRetry(mainAgent, plannerPrompt, toolRegistry, agentRegistry, memoryContext, history, aiCall)
            val plan = PlanParser(agentRegistry.all().map { it.name }.toSet()).parse(rawPlan)
            Logger.i("TaskEngine", "Plan for task ${task.id.take(8)}: ${plan.steps.size} steps, directAnswer=${plan.directAnswer}")
            emit(TaskEvent.PlanCreated(task.id, plan))

            if (plan.directAnswer) {
                // Stream a direct conversational answer (real provider streaming).
                val answer = mainAgent.synthesize(task.userRequest, emptyList(), history, ::aiStream)
                finishTask(task, answer, conversationId)
                return
            }

            // 4. Persist plan summary event
            val planSummary = plan.steps.mapIndexed { i, s ->
                "${i + 1}. [${s.agent}] ${s.tool ?: "cognitive"} — ${s.why}"
            }.joinToString("\n")
            persistEvent(ChatMessage(
                id = UUID.randomUUID().toString(), conversationId = conversationId,
                role = MessageRole.EVENT, text = "Plan (${plan.steps.size} steps):\n$planSummary",
                eventType = EventType.INFO
            ))

            // 5. Execute steps sequentially
            setTaskStatus(task, TaskStatus.RUNNING)
            val collected = mutableListOf<Pair<String, String>>()

            for ((index, request) in plan.steps.withIndex()) {
                val agent = agentRegistry.find(request.agent)
                    ?: throw AgentNotFoundException("Planner referenced unknown agent '${request.agent}'")

                val step = TaskStep(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    index = index,
                    agentName = agent.name,
                    toolName = request.tool,
                    argsJson = request.args.toString(),
                    why = request.why,
                    status = StepStatus.RUNNING,
                    startedAt = System.currentTimeMillis()
                )
                taskRepo.addStep(step)
                emit(TaskEvent.StepStarted(task.id, step))
                persistEvent(ChatMessage(
                    id = UUID.randomUUID().toString(), conversationId = conversationId,
                    role = MessageRole.EVENT,
                    text = "${agent.displayName}: ${request.why.ifBlank { request.tool ?: "working…" }}",
                    agentName = agent.displayName, eventType = EventType.AGENT_STARTED
                ))

                // High-risk gate: real human approval BEFORE execution
                val tool = request.tool?.let { toolRegistry.get(it) }
                if (tool?.risk == ToolRisk.HIGH_RISK) {
                    val updatedWaiting = step.copy(status = StepStatus.AWAITING_APPROVAL)
                    taskRepo.updateStep(updatedWaiting)
                    emit(TaskEvent.StepStarted(task.id, updatedWaiting))
                    val approved = approvals.requestApproval(
                        task.id, step.id, request.tool, request.args.toString(),
                        "This tool is marked high-risk (${request.tool}). Allow execution?"
                    )
                    if (!approved) {
                        val denied = step.copy(
                            status = StepStatus.FAILED,
                            error = "User denied approval",
                            finishedAt = System.currentTimeMillis()
                        )
                        taskRepo.updateStep(denied)
                        emit(TaskEvent.StepFinished(task.id, denied))
                        throw ApprovalDeniedException("Execution stopped: you denied approval for '${request.tool}'.")
                    }
                }

                val stepCtx = StepContext(
                    toolRegistry = toolRegistry,
                    toolContext = ToolContext(
                        appContext = contextProvider(),
                        onActivity = { activity ->
                            scope.launch {
                                emit(TaskEvent.AgentActivity(task.id, agent.displayName, activity))
                            }
                        },
                        screenshotRequester = screenshotRequester
                    ),
                    ai = { msgs -> aiCall(msgs) },
                    aiStream = { msgs, onChunk -> aiStream(msgs, onChunk) },
                    onActivity = { /* same as toolContext.onActivity; agents use ctx.onActivity */ }
                )

                var attempt = 0
                var resultText: String? = null
                var lastError: Exception? = null
                while (attempt < (if (request.retryOnFailure) 2 else 1)) {
                    attempt++
                    try {
                        resultText = agent.runStep(step, task, stepCtx)
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        if (attempt < (if (request.retryOnFailure) 2 else 1)) {
                            Logger.w("TaskEngine", "Step ${step.id.take(8)} attempt $attempt failed: ${e.message}; retrying")
                        }
                    }
                }

                val finishedStep: TaskStep
                if (resultText != null) {
                    finishedStep = step.copy(status = StepStatus.COMPLETED, result = resultText, finishedAt = System.currentTimeMillis())
                    taskRepo.updateStep(finishedStep)
                    collected += "${agent.displayName}: ${request.why}" to (resultText ?: "")
                } else {
                    val msg = lastError?.message ?: "unknown failure"
                    finishedStep = step.copy(status = StepStatus.FAILED, error = msg, finishedAt = System.currentTimeMillis())
                    taskRepo.updateStep(finishedStep)
                    emit(TaskEvent.StepFinished(task.id, finishedStep))
                    persistEvent(ChatMessage(
                        id = UUID.randomUUID().toString(), conversationId = conversationId,
                        role = MessageRole.EVENT, text = "Step failed: ${msg}",
                        agentName = agent.displayName, eventType = EventType.ERROR, severity = EventSeverity.ERROR
                    ))
                    throw StepFailedException("Step ${index + 1} (${request.tool ?: agent.name}) failed: $msg")
                }
                emit(TaskEvent.StepFinished(task.id, finishedStep))
            }

            // 6. Final synthesis (streamed to UI)
            val answer = mainAgent.synthesize(task.userRequest, collected, history, ::aiStream)
            finishTask(task, answer, conversationId)
        } catch (e: CancellationException) {
            setTaskStatus(task, TaskStatus.CANCELLED)
            persistEvent(ChatMessage(
                id = UUID.randomUUID().toString(), conversationId = conversationId,
                role = MessageRole.EVENT, text = "Task cancelled by user",
                eventType = EventType.TASK_STATUS, severity = EventSeverity.WARNING
            ))
        } catch (e: Exception) {
            val friendly = when (e) {
                is ProviderNotConfigured, is OfflineException, is ApprovalDeniedException, is StepFailedException, is AgentNotFoundException -> e.message ?: "Task failed"
                else -> "Task failed: ${e.message ?: e.javaClass.simpleName}"
            }
            Logger.e("TaskEngine", "Task ${task.id.take(8)} failed", e)
            setTaskStatus(task, TaskStatus.FAILED, error = friendly)
            persistEvent(ChatMessage(
                id = UUID.randomUUID().toString(), conversationId = conversationId,
                role = MessageRole.EVENT, text = friendly,
                eventType = EventType.ERROR, severity = EventSeverity.ERROR
            ))
            _events.emit(TaskEvent.Error(task.id, friendly))
        }
    }

    /* ------------------------- helpers ------------------------- */

    private suspend fun planWithRetry(
        mainAgent: MainAgent,
        request: String,
        toolRegistry: ToolRegistry,
        agentRegistry: AgentRegistry,
        memoryContext: String,
        history: List<ChatTurn>,
        aiCall: suspend (List<ChatTurn>) -> String
    ): String {
        val firstAttempt = mainAgent.plan(
            request, toolRegistry.describeForPlanner(), agentRegistry.catalog(), memoryContext, history, aiCall
        )
        val parser = PlanParser(agentRegistry.all().map { it.name }.toSet())
        return try {
            parser.parse(firstAttempt)
            firstAttempt
        } catch (e: PlanParser.PlanParseException) {
            Logger.w("TaskEngine", "First plan unparseable (${e.message}); retrying with stricter instruction")
            mainAgent.plan(
                "$request\n\nIMPORTANT: Respond with ONLY the JSON object. No prose, no code fences.",
                toolRegistry.describeForPlanner(), agentRegistry.catalog(), memoryContext, history, aiCall
            )
        }
    }

    private suspend fun finishTask(task: AgentTask, answer: String, conversationId: String) {
        if (answer.isBlank()) throw StepFailedException("The AI provider returned an empty answer.")
        persistEvent(ChatMessage(
            id = UUID.randomUUID().toString(), conversationId = conversationId,
            role = MessageRole.ASSISTANT, text = answer
        ))
        setTaskStatus(task.copy(finalResult = answer), TaskStatus.COMPLETED)

        // auto-memory: remember task outcome (real persistence)
        if (settings.privacyFlow.first().autoMemoryEnabled) {
            runCatching {
                memoryRepo.store(
                    "task_${task.id.take(8)}",
                    "Request: ${task.userRequest.take(200)}\nOutcome: ${answer.take(300)}",
                    category = "tasks"
                )
            }
        }
        _events.emit(TaskEvent.Completed(task.id, answer))
    }

    private suspend fun setTaskStatus(task: AgentTask, status: TaskStatus, error: String? = null) {
        val updated = task.copy(status = status, error = error ?: task.error, updatedAt = System.currentTimeMillis())
        taskRepo.update(updated)
        updateActive(updated)
        _events.emit(TaskEvent.TaskStatusChanged(task.id, status))
    }

    private fun updateActive(task: AgentTask) {
        _activeTasks.value = _activeTasks.value + (task.id to task)
    }

    private suspend fun persistEvent(message: ChatMessage) {
        chatRepo.addMessage(message)
    }

    private suspend fun emit(event: TaskEvent) {
        eventMutex.withLock { _events.emit(event) }
    }

    fun providerKey(providerId: String) = "provider_key_$providerId"

    /* ------------- injected platform hooks (set at DI time) ------------- */

    lateinit var contextProvider: () -> android.content.Context
    var screenshotRequester: suspend () -> String? = { null }

    /* ------------------------- exceptions ------------------------- */

    class ProviderNotConfigured(message: String) : Exception(message)
    class OfflineException(message: String) : Exception(message)
    class ApprovalDeniedException(message: String) : Exception(message)
    class StepFailedException(message: String) : Exception(message)
    class AgentNotFoundException(message: String) : Exception(message)
}
