package com.agentos.app.data.provider

import kotlinx.serialization.Serializable

enum class ProviderType(val displayName: String) {
    OPENAI_COMPATIBLE("OpenAI-compatible"),
    GEMINI("Google Gemini")
}

/**
 * Fully user-configurable provider. API keys are NOT stored here — they live
 * encrypted in SecureStore, keyed by [id].
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val type: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val displayName: String = "Provider",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.4,
    val maxTokens: Int = 4096,
    val enabled: Boolean = true
)

/** A single conversation turn sent to a provider. */
data class ChatTurn(
    val role: String, // "system" | "user" | "assistant"
    val content: String
)

sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Completed(val fullText: String) : StreamEvent()
    data class Failed(val error: Throwable) : StreamEvent()
}

/** Abstraction every AI backend implements. */
interface AIProvider {
    /**
     * Streams a completion for [messages]. Must emit [StreamEvent.Failed] on
     * any failure (HTTP error, auth error, network error, parse error) — never
     * fabricate content.
     */
    fun stream(config: ProviderConfig, apiKey: String, messages: List<ChatTurn>): kotlinx.coroutines.flow.Flow<StreamEvent>
}
