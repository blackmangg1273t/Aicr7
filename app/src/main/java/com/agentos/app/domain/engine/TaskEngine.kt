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
import com.agentos.app.service.AssistantAccessibilityService
import com.agentos.app.service.TaskForegroundService
import com.agentos.app.service.OverlayController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * Real task engine: PLAN → EXECUTE → OBSERVE → VERIFY → RECOVER → REPLAN →
 * CONTINUE → VERIFY FINAL RESULT. All state is persisted in Room; all events
 * are streamed to the UI. Failures are honest, classified, and recoverable
 * when possible.
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

    /** Per-task recovery state (in-memory only; the persistence is in Room). */
    private data class RecoveryState(
        val attempts: Int = 0,
        val maxAttempts: Int = 2,
        val replans: Int = 0,
        val maxReplans: Int = 1
    )
    private val recoveryState = ConcurrentHashMap<String, RecoveryState>()

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
        // Persist BEFORE launching the runner to avoid the PENDING-overwrites-PLANNING race.
        scope.launch {
            taskRepo.create(task)
            updateActive(task)
            jobs[taskId] = scope.launch { runTask(task) }
        }
        return taskId
    }

    fun cancel(taskId: String) {
        jobs[taskId]?.cancel()
        approvals.cancelAll()
    }

    fun resolveApproval(stepId: String, approved: Boolean) = approvals.resolve(stepId, approved)

    /** Restart a previously-interrupted task. Used by the "Resume" UI. */
    fun resume(userRequest: String, conversationId: String): String =
        start(userRequest, conversationId)

    /* ------------------------- core runner ------------------------- */

    private suspend fun runTask(initial: AgentTask) {
        val task = initial
        val conversationId = taskConversation[task.id] ?: ""
        var recovery = RecoveryState()
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
                        is StreamEvent.Chunk -> { full += ev.text; onChunk(ev.text); emit(TaskEvent.AssistantChunk(task.id, ev.text)) }
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
            val plannerPrompt = buildString { append(task.userRequest) }
            val rawPlan = planWithRetry(mainAgent, plannerPrompt, toolRegistry, agentRegistry, memoryContext, history, aiCall)
            val parsed = PlanParser(
                agentRegistry.all().map { it.name }.toSet(),
                toolRegistry.all().map { it.name }.toSet()
            ).parseWithDrops(rawPlan)
            val plan = parsed.plan
            Logger.i("TaskEngine", "Plan for task ${task.id.take(8)}: ${plan.steps.size} steps, directAnswer=${plan.directAnswer}, drops=${parsed.dropped.size}")
            emit(TaskEvent.PlanCreated(task.id, plan))

            // Surface dropped steps to the user (so they know part of the plan was filtered).
            parsed.dropped.forEach { d ->
                persistEvent(ChatMessage(
                    id = UUID.randomUUID().toString(), conversationId = conversationId,
                    role = MessageRole.EVENT, text = "Plan drop: ${d.reason}",
                    eventType = EventType.INFO, severity = EventSeverity.WARNING
                ))
            }

            if (plan.directAnswer) {
                // Stream a direct conversational answer (real provider streaming).
                val answer = mainAgent.synthesize(task.userRequest, emptyList(), history, ::aiStream)
                finishTask(task, answer, conversationId, partial = false)
                return
            }

            // 4. Persist a friendly plan summary event (no raw tool dumps).
            persistEvent(ChatMessage(
                id = UUID.randomUUID().toString(), conversationId = conversationId,
                role = MessageRole.EVENT,
                text = "Plan ready: ${plan.steps.size} steps.",
                eventType = EventType.INFO
            ))
            setTaskStatus(task.copy(totalSteps = plan.steps.size), TaskStatus.PLAN_READY)

            // 5. Execute steps sequentially with OBSERVE → ACT → VERIFY and recovery.
            setTaskStatus(task.copy(totalSteps = plan.steps.size), TaskStatus.RUNNING)
            // Start the foreground service so the task survives backgrounding.
            // Fire-and-forget on a separate coroutine so engine execution is not blocked
            // by service startup (which can be slow on some Android versions / Robolectric).
            scope.launch { runCatching { TaskForegroundService.start(contextProvider(), task.userRequest.take(48)) } }
            // Show the floating overlay (if permission is granted).
            scope.launch {
                runCatching {
                    OverlayController.show(contextProvider(), task.id, task.userRequest.take(60), 0, plan.steps.size)
                }
            }

            val collected = mutableListOf<Pair<String, String>>()
            val executedInfos = mutableListOf<MainAgent.ExecutedStepInfo>()
            var completedCount = 0
            var currentPlan = plan
            var stepIndex = 0

            while (stepIndex < currentPlan.steps.size) {
                val request = currentPlan.steps[stepIndex]
                val agent = agentRegistry.find(request.agent)
                    ?: throw AgentNotFoundException("Planner referenced unknown agent '${request.agent}'")

                val step = TaskStep(
                    id = UUID.randomUUID().toString(),
                    taskId = task.id,
                    index = stepIndex,
                    agentName = agent.name,
                    toolName = request.tool,
                    argsJson = request.args.toString(),
                    why = request.why,
                    status = StepStatus.RUNNING,
                    startedAt = System.currentTimeMillis(),
                    attempt = recovery.attempts
                )
                taskRepo.addStep(step)
                emit(TaskEvent.StepStarted(task.id, step))
                persistEvent(ChatMessage(
                    id = UUID.randomUUID().toString(), conversationId = conversationId,
                    role = MessageRole.EVENT,
                    text = "${agent.displayName}: ${request.why.ifBlank { request.tool ?: "working…" }}",
                    agentName = agent.displayName, eventType = EventType.AGENT_STARTED
                ))
                OverlayController.update(contextProvider(), task.id, stepIndex + 1, currentPlan.steps.size, agent.displayName, request.why)

                // High-risk gate: real human approval BEFORE execution.
                val tool = request.tool?.let { toolRegistry.get(it) }
                if (tool?.risk == ToolRisk.HIGH_RISK) {
                    val updatedWaiting = step.copy(status = StepStatus.AWAITING_APPROVAL)
                    taskRepo.updateStep(updatedWaiting)
                    emit(TaskEvent.StepStarted(task.id, updatedWaiting))
                    setTaskStatus(task, TaskStatus.WAITING_USER)
                    val approved = approvals.requestApproval(
                        task.id, step.id, request.tool ?: "tool", request.args.toString(),
                        "This tool is marked high-risk (${request.tool}). Allow execution?"
                    )
                    setTaskStatus(task, TaskStatus.RUNNING)
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

                // Execute the step with recovery.
                val stepCtx = StepContext(
                    toolRegistry = toolRegistry,
                    toolContext = ToolContext(
                        appContext = contextProvider(),
                        onActivity = { activity ->
                            scope.launch { emit(TaskEvent.AgentActivity(task.id, agent.displayName, activity)) }
                        },
                        screenshotRequester = screenshotRequester
                    ),
                    ai = { msgs -> aiCall(msgs) },
                    aiStream = { msgs, onChunk -> aiStream(msgs, onChunk) },
                    onActivity = { /* same as toolContext.onActivity; agents use ctx.onActivity */ }
                )

                val stepOutcome = executeWithRecovery(task, step, request, agent, stepCtx, recovery, conversationId)
                recovery = stepOutcome.updatedRecovery

                when (stepOutcome.outcome) {
                    StepOutcome.Type.SUCCESS -> {
                        val finished = step.copy(
                            status = StepStatus.COMPLETED,
                            result = stepOutcome.result,
                            finishedAt = System.currentTimeMillis(),
                            attempt = stepOutcome.attemptsUsed
                        )
                        taskRepo.updateStep(finished)
                        emit(TaskEvent.StepFinished(task.id, finished))
                        collected += "${agent.displayName}: ${request.why}" to (stepOutcome.result ?: "")
                        executedInfos += MainAgent.ExecutedStepInfo(
                            index = stepIndex, agent = agent.name, tool = request.tool,
                            why = request.why, success = true,
                            result = stepOutcome.result ?: "", error = ""
                        )
                        completedCount++
                        setTaskStatus(task.copy(completedSteps = completedCount), task.status)
                        stepIndex++
                        // Reset per-step recovery when a step succeeds.
                        recovery = recovery.copy(attempts = 0)
                    }
                    StepOutcome.Type.SKIPPED -> {
                        val skipped = step.copy(status = StepStatus.SKIPPED, finishedAt = System.currentTimeMillis())
                        taskRepo.updateStep(skipped)
                        emit(TaskEvent.StepFinished(task.id, skipped))
                        stepIndex++
                        recovery = recovery.copy(attempts = 0)
                    }
                    StepOutcome.Type.REPLAN -> {
                        if (recovery.replans >= recovery.maxReplans) {
                            // No more replans allowed — fail the task as PARTIAL.
                            val partialStep = step.copy(
                                status = StepStatus.FAILED, error = stepOutcome.error,
                                finishedAt = System.currentTimeMillis()
                            )
                            taskRepo.updateStep(partialStep)
                            emit(TaskEvent.StepFinished(task.id, partialStep))
                            finishTaskPartial(task, collected, conversationId, stepOutcome.error)
                            return
                        }
                        // Mark the failing step as FAILED BEFORE attempting replan so
                        // we don't leave a step stuck in RUNNING if the replan also fails.
                        val failedStep = step.copy(
                            status = StepStatus.FAILED, error = stepOutcome.error,
                            finishedAt = System.currentTimeMillis()
                        )
                        taskRepo.updateStep(failedStep)
                        emit(TaskEvent.StepFinished(task.id, failedStep))

                        // Replan: ask the Main Agent for a new plan based on executed steps.
                        setTaskStatus(task, TaskStatus.REPLANNING)
                        emit(TaskEvent.ReplanStarted(task.id, stepOutcome.error))
                        val newRaw = mainAgent.replan(
                            task.userRequest, executedInfos,
                            toolRegistry.describeForPlanner(), agentRegistry.catalog(),
                            history, stepOutcome.error, aiCall
                        )
                        val newParsed = runCatching {
                            PlanParser(
                                agentRegistry.all().map { it.name }.toSet(),
                                toolRegistry.all().map { it.name }.toSet()
                            ).parseWithDrops(newRaw)
                        }.getOrNull()
                        if (newParsed == null || (newParsed.plan.directAnswer.not() && newParsed.plan.steps.isEmpty())) {
                            // Replan failed — finish partial.
                            finishTaskPartial(task, collected, conversationId, "Replanning failed: ${stepOutcome.error}")
                            return
                        }
                        emit(TaskEvent.ReplanCompleted(task.id, newParsed.plan, stepOutcome.error))
                        persistEvent(ChatMessage(
                            id = UUID.randomUUID().toString(), conversationId = conversationId,
                            role = MessageRole.EVENT,
                            text = "Replanning (${newParsed.plan.steps.size} new steps). Reason: ${stepOutcome.error}",
                            eventType = EventType.REPLAN, severity = EventSeverity.WARNING
                        ))
                        currentPlan = newParsed.plan
                        recovery = recovery.copy(replans = recovery.replans + 1, attempts = 0)
                        // Don't advance stepIndex — the new plan starts from index 0.
                        stepIndex = 0
                        setTaskStatus(task.copy(totalSteps = currentPlan.steps.size, completedSteps = completedCount), TaskStatus.RUNNING)
                    }
                    StepOutcome.Type.NEEDS_USER -> {
                        setTaskStatus(task, TaskStatus.WAITING_USER)
                        persistEvent(ChatMessage(
                            id = UUID.randomUUID().toString(), conversationId = conversationId,
                            role = MessageRole.EVENT, text = stepOutcome.friendlyMessage,
                            eventType = EventType.INFO, severity = EventSeverity.WARNING
                        ))
                        // Block until user resumes — for now, finish partial.
                        finishTaskPartial(task, collected, conversationId, stepOutcome.friendlyMessage)
                        return
                    }
                    StepOutcome.Type.FAIL -> {
                        val failedStep = step.copy(status = StepStatus.FAILED, error = stepOutcome.error, finishedAt = System.currentTimeMillis())
                        taskRepo.updateStep(failedStep)
                        emit(TaskEvent.StepFinished(task.id, failedStep))
                        finishTaskPartial(task, collected, conversationId, stepOutcome.error)
                        return
                    }
                }
            }

            // 6. Final verification + synthesis (streamed to UI).
            setTaskStatus(task, TaskStatus.VERIFYING)
            emit(TaskEvent.VerificationStarted(task.id, "", "Verifying final result against user request"))
            val answer = mainAgent.synthesize(task.userRequest, collected, history, ::aiStream)
            emit(TaskEvent.VerificationCompleted(task.id, "", verified = true, note = "Final answer synthesized"))
            finishTask(task, answer, conversationId, partial = false)
        } catch (e: CancellationException) {
            setTaskStatus(task, TaskStatus.CANCELLED)
            approvals.cancelAll()
            persistEvent(ChatMessage(
                id = UUID.randomUUID().toString(), conversationId = conversationId,
                role = MessageRole.EVENT, text = "Task cancelled by user",
                eventType = EventType.TASK_STATUS, severity = EventSeverity.WARNING
            ))
            scope.launch { runCatching { OverlayController.hide(contextProvider(), task.id) } }
        } catch (e: Exception) {
            val friendly = when (e) {
                is ProviderNotConfigured, is OfflineException, is ApprovalDeniedException,
                is AgentNotFoundException -> e.message ?: "Task failed"
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
            scope.launch { runCatching { OverlayController.hide(contextProvider(), task.id) } }
        } finally {
            // Stop the foreground service if no other tasks are active.
            if (_activeTasks.value.none { it.value.status == TaskStatus.RUNNING || it.value.status == TaskStatus.WAITING_USER }) {
                scope.launch { runCatching { TaskForegroundService.stop(contextProvider()) } }
            }
            recoveryState.remove(task.id)
        }
    }

    private data class StepOutcome(
        val outcome: Type,
        val result: String? = null,
        val error: String = "",
        val friendlyMessage: String = "",
        val attemptsUsed: Int = 0,
        val updatedRecovery: RecoveryState = RecoveryState()
    ) {
        enum class Type { SUCCESS, SKIPPED, REPLAN, NEEDS_USER, FAIL }
    }

    /** Execute a single step with up to [recovery.maxAttempts] retries, then escalate. */
    private suspend fun executeWithRecovery(
        task: AgentTask,
        step: TaskStep,
        request: PlanStepRequest,
        agent: com.agentos.app.domain.agent.Agent,
        ctx: StepContext,
        recovery: RecoveryState,
        conversationId: String
    ): StepOutcome {
        var attempts = 0
        var lastError: Throwable? = null
        var currentRecovery = recovery
        while (attempts <= currentRecovery.maxAttempts) {
            attempts++
            try {
                val resultText = agent.runStep(step.copy(attempt = attempts - 1), task, ctx)
                return StepOutcome(
                    outcome = StepOutcome.Type.SUCCESS,
                    result = resultText,
                    attemptsUsed = attempts,
                    updatedRecovery = currentRecovery
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                val decision = RecoveryClassifier.classify(step, e, attempts - 1)
                Logger.w("TaskEngine", "Step ${step.id.take(8)} attempt $attempts failed: ${e.message} → ${decision.strategy}")
                emit(TaskEvent.RecoveryStarted(task.id, step.id, decision.reason, attempts))
                persistEvent(ChatMessage(
                    id = UUID.randomUUID().toString(), conversationId = conversationId,
                    role = MessageRole.EVENT,
                    text = decision.friendlyMessage,
                    eventType = EventType.RECOVERY, severity = EventSeverity.WARNING
                ))
                when (decision.strategy) {
                    RecoveryClassifier.Strategy.RETRY_STEP -> {
                        if (decision.waitMs > 0) delay(decision.waitMs)
                        // Loop again.
                        emit(TaskEvent.RecoveryCompleted(task.id, step.id, succeeded = false, note = "retrying (attempt ${attempts + 1})"))
                        continue
                    }
                    RecoveryClassifier.Strategy.RETRY_AFTER_WAIT -> {
                        delay(decision.waitMs)
                        emit(TaskEvent.RecoveryCompleted(task.id, step.id, succeeded = false, note = "waited ${decision.waitMs}ms, retrying"))
                        continue
                    }
                    RecoveryClassifier.Strategy.REPLAN -> {
                        emit(TaskEvent.RecoveryCompleted(task.id, step.id, succeeded = false, note = "escalating to replan"))
                        return StepOutcome(
                            outcome = StepOutcome.Type.REPLAN,
                            error = e.message ?: "replan triggered",
                            friendlyMessage = decision.friendlyMessage,
                            attemptsUsed = attempts,
                            updatedRecovery = currentRecovery.copy(attempts = attempts)
                        )
                    }
                    RecoveryClassifier.Strategy.SKIP_STEP -> {
                        return StepOutcome(
                            outcome = StepOutcome.Type.SKIPPED,
                            error = e.message ?: "skipped",
                            friendlyMessage = decision.friendlyMessage,
                            attemptsUsed = attempts,
                            updatedRecovery = currentRecovery
                        )
                    }
                    RecoveryClassifier.Strategy.NEEDS_USER -> {
                        return StepOutcome(
                            outcome = StepOutcome.Type.NEEDS_USER,
                            error = e.message ?: "needs user",
                            friendlyMessage = decision.friendlyMessage,
                            attemptsUsed = attempts,
                            updatedRecovery = currentRecovery
                        )
                    }
                    RecoveryClassifier.Strategy.FAIL_TASK -> {
                        return StepOutcome(
                            outcome = StepOutcome.Type.FAIL,
                            error = e.message ?: "failed",
                            friendlyMessage = decision.friendlyMessage,
                            attemptsUsed = attempts,
                            updatedRecovery = currentRecovery
                        )
                    }
                }
            }
        }
        // Exhausted retries.
        return StepOutcome(
            outcome = StepOutcome.Type.REPLAN,
            error = lastError?.message ?: "exhausted retries",
            friendlyMessage = "Step failed after $attempts attempts. Replanning…",
            attemptsUsed = attempts,
            updatedRecovery = currentRecovery.copy(attempts = attempts)
        )
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
        val parser = PlanParser(
            agentRegistry.all().map { it.name }.toSet(),
            toolRegistry.all().map { it.name }.toSet()
        )
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

    private suspend fun finishTask(task: AgentTask, answer: String, conversationId: String, partial: Boolean) {
        if (answer.isBlank()) throw StepFailedException("The AI provider returned an empty answer.")
        persistEvent(ChatMessage(
            id = UUID.randomUUID().toString(), conversationId = conversationId,
            role = MessageRole.ASSISTANT, text = answer
        ))
        setTaskStatus(task.copy(finalResult = answer), if (partial) TaskStatus.PARTIAL else TaskStatus.COMPLETED)

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
        _events.emit(TaskEvent.Completed(task.id, answer, partial))
        OverlayController.hide(contextProvider(), task.id)
    }

    /** Mark the task as PARTIAL with the partial answer synthesized from collected steps. */
    private suspend fun finishTaskPartial(
        task: AgentTask,
        collected: List<Pair<String, String>>,
        conversationId: String,
        reason: String
    ) {
        // Synthesize a partial answer from what was collected so the user gets a useful response.
        val transcript = if (collected.isEmpty()) "No steps completed." else collected.joinToString("\n\n") { (t, r) -> "### $t\n$r" }
        val partialAnswer = "I couldn't complete the full task. Here's what I managed:\n\n$transcript\n\nReason I stopped: $reason"
        persistEvent(ChatMessage(
            id = UUID.randomUUID().toString(), conversationId = conversationId,
            role = MessageRole.ASSISTANT, text = partialAnswer
        ))
        setTaskStatus(
            task.copy(finalResult = partialAnswer, error = reason),
            TaskStatus.PARTIAL
        )
        _events.emit(TaskEvent.Completed(task.id, partialAnswer, partial = true))
        _events.emit(TaskEvent.Error(task.id, reason, recoverable = true))
        OverlayController.hide(contextProvider(), task.id)
    }

    private suspend fun setTaskStatus(task: AgentTask, status: TaskStatus, error: String? = null) {
        val updated = task.copy(
            status = status, error = error ?: task.error,
            updatedAt = System.currentTimeMillis(),
            currentAgent = when (status) {
                TaskStatus.RUNNING, TaskStatus.RECOVERING, TaskStatus.VERIFYING -> task.currentAgent
                else -> null
            }
        )
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
