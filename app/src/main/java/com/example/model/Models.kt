package com.example.model

enum class ModelProviderType(val displayName: String, val defaultEndpoint: String) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    OPENAI_COMPATIBLE("OpenAI Compatible", "https://api.openai.com/v1"),
    QWEN("Qwen (DashScope)", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
    GLM("Zhipu GLM", "https://open.bigmodel.cn/api/paas/v4"),
    LOCAL_DEVICE("Local On-Device", "http://127.0.0.1:8080")
}

data class ModelConfig(
    val id: String = "gemini-2.5-flash",
    val name: String = "Gemini 2.5 Flash",
    val provider: ModelProviderType = ModelProviderType.GEMINI,
    val apiKey: String = "",
    val baseUrl: String = ModelProviderType.GEMINI.defaultEndpoint,
    val temperature: Float = 0.4f,
    val maxTokens: Int = 4096,
    val isEnabled: Boolean = true
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val agentName: String? = null,
    val toolCallInfo: ToolCallInfo? = null,
    val isStreaming: Boolean = false
)

enum class MessageSender {
    USER,
    SYSTEM,
    MAIN_AGENT,
    SUB_AGENT
}

data class ToolCallInfo(
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String? = null,
    val isSuccess: Boolean = true,
    val durationMs: Long = 0
)

sealed class ModelStreamEvent {
    data class Chunk(val text: String) : ModelStreamEvent()
    data class Completed(val fullText: String, val finishReason: String? = null) : ModelStreamEvent()
    data class Error(val throwable: Throwable) : ModelStreamEvent()
}
