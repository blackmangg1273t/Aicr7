package com.agentos.app.domain.engine

import com.agentos.app.core.logging.Logger
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.ChatMessage
import com.agentos.app.domain.model.TaskEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Human-in-the-loop approval gate. The engine suspends the task until the UI
 * resolves the pending approval. Nothing high-risk executes silently.
 */
class ApprovalManager(
    private val conversationId: () -> String?,
    private val persistEvent: suspend (ChatMessage) -> Unit,
    private val emit: suspend (TaskEvent) -> Unit,
    private val timeoutMs: Long = 10 * 60 * 1000L
) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    suspend fun requestApproval(taskId: String, stepId: String, tool: String, args: String, reason: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending[stepId] = deferred

        val messageId = UUID.randomUUID().toString()
        persistEvent(
            ChatMessage(
                id = messageId,
                conversationId = conversationId() ?: "",
                role = com.agentos.app.domain.model.MessageRole.EVENT,
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
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pending.remove(stepId)
        val approved = result ?: false

        persistEvent(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId() ?: "",
                role = com.agentos.app.domain.model.MessageRole.EVENT,
                text = if (approved) "Approved: $tool" else "Denied: $tool",
                eventType = EventType.APPROVAL_RESOLVED,
                toolName = tool,
                severity = if (approved) EventSeverity.INFO else EventSeverity.WARNING
            )
        )
        emit(TaskEvent.ApprovalResolved(taskId, stepId, approved))
        return approved
    }

    fun resolve(stepId: String, approved: Boolean) {
        pending[stepId]?.complete(approved)
    }

    fun cancelAll() {
        pending.values.forEach { it.complete(false) }
        pending.clear()
    }
}
