package com.example.agent

enum class TaskStatus {
    CREATED,
    PLANNING,
    RUNNING,
    WAITING_FOR_USER,
    RETRYING,
    COMPLETED,
    VERIFIED,
    FAILED
}

data class AgentSubtask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val targetAgent: String,
    val toolName: String? = null,
    val toolArgumentsJson: String? = null,
    var status: TaskStatus = TaskStatus.CREATED,
    var result: String? = null,
    var verificationResult: String? = null,
    var error: String? = null
)

data class AgentTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val prompt: String,
    val createdAt: Long = System.currentTimeMillis(),
    var status: TaskStatus = TaskStatus.CREATED,
    val planSteps: MutableList<AgentSubtask> = mutableListOf(),
    var finalResult: String? = null,
    var verificationNotes: String? = null
)
