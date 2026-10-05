package com.agentos.app.domain.engine

import com.agentos.app.core.logging.Logger
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.ChatMessage
import com.agentos.app.domain.model.MessageRole
import com.agentos.app.domain.model.TaskEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Human-in-the-loop approval gate. The engine suspends the task until the UI
 * resolves the pending approval. Nothing high-risk executes silently.
 *
 * Behavior:
 *  - Persists an APPROVAL_REQUIRED chat message (so the UI shows a card).
 *  - Awaits a CompletableDeferred up to [timeoutMs].
 *  - Distinguishes "user denied" from "timeout" in the persisted resolution
 *    message and the [TaskEvent.ApprovalResolved] reason.
 *  - [cancelAll] resolves all pending approvals with `false` and reason
 *    "task_cancelled" — must be called by the engine when a task is cancelled
 *    so the deferred doesn't leak for [timeoutMs].
 */
class ApprovalManager(
    private val conversationId: () -> String?,
    private val persistEvent: suspend (ChatMessage) -> Unit,
    private val emit: suspend (TaskEvent) -> Unit,
    private val timeoutMs: Long = 10 * 60 * 1000L
) {
    enum class Resolution { APPROVED, DENIED, TIMEOUT, CANCELLED }

    private data class Pending(
        val deferred: CompletableDeferred<Resolution>,
        val taskId: String,
        val tool: String
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    /** Reason enum: "user" (deny/approve), "timeout", "task_cancelled". */
    suspend fun requestApproval(
        taskId: String, stepId: String, tool: String, args: String, reason: String
    ): Boolean {
        val deferred = CompletableDeferred<Resolution>()
        pending[stepId] = Pending(deferred, taskId, tool)

        val messageId = UUID.randomUUID().toString()
        persistEvent(
            ChatMessage(
                id = messageId,
                conversationId = conversationId() ?: "",
                role = MessageRole.EVENT,
                text = "Approval required: execute '$tool'?",
                eventType = EventType.APPROVAL_REQUIRED,
                toolName = tool,
                argsJson = args,
                severity = EventSeverity.WARNING,
                approvalMessageId = stepId
            )
        )
        emit(TaskEvent.ApprovalRequired(taskId, stepId, tool, args, reason))

        Logger.i("Approval", "Waiting for user approval: task=$taskId tool=$tool")
        val outcome = withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: Resolution.TIMEOUT
        pending.remove(stepId)

        val approved = outcome == Resolution.APPROVED
        val reasonStr = when (outcome) {
            Resolution.APPROVED -> "approved"
            Resolution.DENIED -> "denied"
            Resolution.TIMEOUT -> "timed out after ${timeoutMs / 1000}s (no response)"
            Resolution.CANCELLED -> "task cancelled"
        }
        val persistText = when (outcome) {
            Resolution.APPROVED -> "Approved: $tool"
            Resolution.DENIED -> "Denied: $tool"
            Resolution.TIMEOUT -> "Approval timed out for: $tool (no response in ${timeoutMs / 1000}s)"
            Resolution.CANCELLED -> "Approval cancelled for: $tool (task stopped)"
        }
        persistEvent(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId() ?: "",
                role = MessageRole.EVENT,
                text = persistText,
                eventType = EventType.APPROVAL_RESOLVED,
                toolName = tool,
                severity = if (approved) EventSeverity.INFO else EventSeverity.WARNING
            )
        )
        emit(TaskEvent.ApprovalResolved(taskId, stepId, approved, reasonStr))
        return approved
    }

    fun resolve(stepId: String, approved: Boolean) {
        pending[stepId]?.deferred?.complete(
            if (approved) Resolution.APPROVED else Resolution.DENIED
        )
    }

    /**
     * Cancels all pending approvals. Should be called by the engine when the
     * task is cancelled or fails before approval is resolved.
     */
    fun cancelAll() {
        pending.values.forEach { it.deferred.complete(Resolution.CANCELLED) }
        pending.clear()
    }

    /** Pending approvals for a task (used for diagnostics). */
    fun pendingCount(): Int = pending.size
}
