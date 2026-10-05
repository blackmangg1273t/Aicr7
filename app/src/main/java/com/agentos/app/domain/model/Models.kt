package com.agentos.app.domain.model

import kotlinx.serialization.json.JsonObject

/* ------------------------------------------------------------------ */
/* Task & plan                                                         */
/* ------------------------------------------------------------------ */

enum class TaskStatus {
    PENDING, PLANNING, PLAN_READY, RUNNING, WAITING_USER, RECOVERING,
    REPLANNING, VERIFYING, COMPLETED, PARTIAL, FAILED, CANCELLED, INTERRUPTED
}

enum class StepStatus {
    PENDING, RUNNING, AWAITING_APPROVAL, COMPLETED, FAILED, SKIPPED, RECOVERING, VERIFIED
}

/** Structured plan produced by the Main Agent through the AI provider. */
data class AgentPlan(
    val directAnswer: Boolean,
    val answerText: String?,
    val steps: List<PlanStepRequest>
)

/** One executable step requested by the planner. */
data class PlanStepRequest(
    val agent: String,
    val tool: String?,
    val args: JsonObject,
    val why: String,
    val retryOnFailure: Boolean = false
)

/* ------------------------------------------------------------------ */
/* Runtime task / step                                                 */
/* ------------------------------------------------------------------ */

data class AgentTask(
    val id: String,
    val userRequest: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val finalResult: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val conversationId: String? = null,
    val completedSteps: Int = 0,
    val totalSteps: Int = 0,
    val currentAgent: String? = null
)

data class TaskStep(
    val id: String,
    val taskId: String,
    val index: Int,
    val agentName: String,
    val toolName: String? = null,
    val argsJson: String = "{}",
    val why: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
    val error: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val attempt: Int = 0,
    val verified: Boolean = false
)

/* ------------------------------------------------------------------ */
/* Chat                                                                */
/* ------------------------------------------------------------------ */

enum class MessageRole { USER, ASSISTANT, SYSTEM, EVENT }

enum class EventType {
    PLANNING, AGENT_STARTED, AGENT_FINISHED, TOOL_STARTED, TOOL_FINISHED,
    APPROVAL_REQUIRED, APPROVAL_RESOLVED, STEP_STATUS, TASK_STATUS,
    ERROR, SCREENSHOT, INFO, RECOVERY, REPLAN, VERIFICATION, PARTIAL
}

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val text: String,
    val agentName: String? = null,
    val eventType: EventType? = null,
    val toolName: String? = null,
    val argsJson: String? = null,
    val severity: EventSeverity? = null,
    val approvalMessageId: String? = null,  // set on APPROVAL_REQUIRED messages
    val attachmentPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class EventSeverity { INFO, WARNING, ERROR }

/* ------------------------------------------------------------------ */
/* Live execution events (UI stream)                                   */
/* ------------------------------------------------------------------ */

sealed class TaskEvent {
    abstract val taskId: String

    data class TaskStatusChanged(override val taskId: String, val status: TaskStatus) : TaskEvent()
    data class PlanningStarted(override val taskId: String) : TaskEvent()
    data class PlanCreated(override val taskId: String, val plan: AgentPlan) : TaskEvent()
    data class StepStarted(override val taskId: String, val step: TaskStep) : TaskEvent()
    data class StepFinished(override val taskId: String, val step: TaskStep) : TaskEvent()
    data class AgentActivity(override val taskId: String, val agent: String, val activity: String) : TaskEvent()
    data class ToolStarted(override val taskId: String, val stepId: String, val tool: String, val args: String) : TaskEvent()
    data class ToolFinished(override val taskId: String, val stepId: String, val tool: String, val success: Boolean, val output: String, val error: String?) : TaskEvent()
    data class ApprovalRequired(override val taskId: String, val stepId: String, val tool: String, val args: String, val reason: String) : TaskEvent()
    data class ApprovalResolved(override val taskId: String, val stepId: String, val approved: Boolean, val reason: String = "user") : TaskEvent()
    data class AssistantChunk(override val taskId: String, val text: String) : TaskEvent()
    data class Error(override val taskId: String, val message: String, val recoverable: Boolean = false) : TaskEvent()
    data class Completed(override val taskId: String, val finalResult: String, val partial: Boolean = false) : TaskEvent()
    data class Attachment(override val taskId: String, val path: String, val caption: String) : TaskEvent()

    /** Recovery / replanning lifecycle events. */
    data class RecoveryStarted(override val taskId: String, val stepId: String, val reason: String, val attempt: Int) : TaskEvent()
    data class RecoveryCompleted(override val taskId: String, val stepId: String, val succeeded: Boolean, val note: String) : TaskEvent()
    data class ReplanStarted(override val taskId: String, val reason: String) : TaskEvent()
    data class ReplanCompleted(override val taskId: String, val newPlan: AgentPlan, val reason: String) : TaskEvent()
    data class VerificationStarted(override val taskId: String, val stepId: String, val what: String) : TaskEvent()
    data class VerificationCompleted(override val taskId: String, val stepId: String, val verified: Boolean, val note: String) : TaskEvent()
}
