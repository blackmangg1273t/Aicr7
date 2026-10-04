package com.agentos.app.data.repo

import com.agentos.app.data.db.AppDatabase
import com.agentos.app.data.db.ConversationEntity
import com.agentos.app.data.db.MessageEntity
import com.agentos.app.data.db.MemoryEntity
import com.agentos.app.data.db.TaskEntity
import com.agentos.app.data.db.TaskStepEntity
import com.agentos.app.domain.model.ChatMessage
import com.agentos.app.domain.model.AgentTask
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.MessageRole
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.domain.model.TaskStep
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(private val db: AppDatabase, private val saveConversations: suspend () -> Boolean) {

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messageDao().observeConversation(conversationId)

    suspend fun ensureConversation(preferredId: String?, title: String): ConversationEntity {
        val save = runCatching { saveConversations() }.getOrDefault(true)
        if (!save) {
            // Privacy mode: keep an ephemeral in-DB conversation but wipe on demand;
            // we still need a conversation id for persistence during the session.
            Logger.i("ChatRepo", "Privacy mode: conversations will not be retained long-term")
        }
        val existing = preferredId?.let { db.conversationDao().byId(it) }
        if (existing != null) return existing
        val now = System.currentTimeMillis()
        val entity = ConversationEntity(id = UUID.randomUUID().toString(), title = title.take(60), createdAt = now, updatedAt = now)
        db.conversationDao().insert(entity)
        return entity
    }

    suspend fun addMessage(message: ChatMessage) {
        db.messageDao().insert(
            MessageEntity(
                id = message.id,
                conversationId = message.conversationId,
                role = message.role.name,
                text = message.text,
                agentName = message.agentName,
                eventType = message.eventType?.name,
                toolName = message.toolName,
                argsJson = message.argsJson,
                severity = message.severity?.name,
                approvalMessageId = message.approvalMessageId,
                attachmentPath = message.attachmentPath,
                timestamp = message.timestamp
            )
        )
        db.conversationDao().touch(message.conversationId, message.timestamp)
    }

    suspend fun history(conversationId: String, maxTurns: Int = 20): List<ChatMessage> =
        db.messageDao().forConversation(conversationId)
            .filter { it.role == MessageRole.USER.name || it.role == MessageRole.ASSISTANT.name }
            .takeLast(maxTurns)
            .map { it.toModel() }

    private fun MessageEntity.toModel() = ChatMessage(
        id = id, conversationId = conversationId,
        role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.SYSTEM),
        text = text, agentName = agentName,
        eventType = eventType?.let { runCatching { EventType.valueOf(it) }.getOrNull() },
        toolName = toolName, argsJson = argsJson,
        severity = severity?.let { runCatching { EventSeverity.valueOf(it) }.getOrNull() },
        approvalMessageId = approvalMessageId, attachmentPath = attachmentPath, timestamp = timestamp
    )

    suspend fun wipeAll() {
        db.messageDao().clearAll()
        db.conversationDao().clearAll()
        Logger.i("ChatRepo", "All conversations wiped by user")
    }
}

class TaskRepository(private val db: AppDatabase, private val saveTasks: suspend () -> Boolean) {

    suspend fun create(task: AgentTask) {
        if (runCatching { saveTasks() }.getOrDefault(true) || task.status == TaskStatus.RUNNING || task.status == TaskStatus.PLANNING) {
            db.taskDao().insert(
                TaskEntity(task.id, task.userRequest, task.status.name, task.finalResult, task.error, task.conversationId, task.createdAt, task.updatedAt)
            )
        }
    }

    suspend fun update(task: AgentTask) {
        db.taskDao().update(
            TaskEntity(task.id, task.userRequest, task.status.name, task.finalResult, task.error, task.conversationId, task.createdAt, task.updatedAt)
        )
    }

    suspend fun addStep(step: TaskStep) {
        db.taskStepDao().insert(
            TaskStepEntity(step.id, step.taskId, step.index, step.agentName, step.toolName, step.argsJson, step.why, step.status.name, step.result, step.error, step.startedAt, step.finishedAt)
        )
    }

    suspend fun updateStep(step: TaskStep) {
        db.taskStepDao().update(
            TaskStepEntity(step.id, step.taskId, step.index, step.agentName, step.toolName, step.argsJson, step.why, step.status.name, step.result, step.error, step.startedAt, step.finishedAt)
        )
    }

    fun observeTasks(): Flow<List<TaskEntity>> = db.taskDao().observeAll()
    fun observeTask(id: String): Flow<TaskEntity?> = db.taskDao().observeById(id)
    fun observeSteps(taskId: String): Flow<List<TaskStepEntity>> = db.taskStepDao().observeForTask(taskId)

    suspend fun wipeAll() {
        db.taskDao().clearAllSteps()
        db.taskDao().clearAll()
        Logger.i("TaskRepo", "All tasks wiped by user")
    }
}

class MemoryRepository(private val db: AppDatabase) {

    suspend fun store(key: String, content: String, category: String = "general") {
        db.memoryDao().insert(MemoryEntity(key = key, content = content, category = category, timestamp = System.currentTimeMillis()))
    }

    suspend fun search(query: String, limit: Int = 5): List<MemoryEntity> = db.memoryDao().search(query, limit)

    suspend fun recent(limit: Int = 20): List<MemoryEntity> = db.memoryDao().recent(limit)

    suspend fun byKey(key: String): MemoryEntity? = db.memoryDao().byKey(key)

    suspend fun delete(key: String): Int = db.memoryDao().deleteByKey(key)

    fun observeAll(): Flow<List<MemoryEntity>> = db.memoryDao().observeAll()

    suspend fun wipeAll() {
        db.memoryDao().clearAll()
    }
}
