package com.agentos.app.data.provider

import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Executes a chat request against the primary provider; on hard failure
 * transparently retries against the configured fallback provider (if any).
 * Fails honestly when neither works — never fabricates content.
 */
interface AiExecutor {
    fun streamWithFallback(
        primary: ProviderConfig,
        primaryKey: String,
        fallback: ProviderConfig?,
        fallbackKey: String,
        messages: List<ChatTurn>
    ): Flow<StreamEvent>
}

class ProviderExecutor(
    private val openAiCompat: OpenAiCompatibleProvider = OpenAiCompatibleProvider(),
    private val gemini: GeminiProvider = GeminiProvider()
) : AiExecutor {

    fun resolve(type: ProviderType): AIProvider = when (type) {
        ProviderType.OPENAI_COMPATIBLE -> openAiCompat
        ProviderType.GEMINI -> gemini
    }

    /**
     * Streams from [primary], falling back to [fallback] if the primary emits
     * [StreamEvent.Failed] before producing any content.
     */
    override fun streamWithFallback(
        primary: ProviderConfig,
        primaryKey: String,
        fallback: ProviderConfig?,
        fallbackKey: String,
        messages: List<ChatTurn>
    ): Flow<StreamEvent> = flow {
        var sawContent = false
        var primaryError: Throwable? = null

        resolve(primary.type).stream(primary, primaryKey, messages).collect { ev ->
            when (ev) {
                is StreamEvent.Chunk -> { sawContent = true; emit(ev) }
                is StreamEvent.Completed -> emit(ev)
                is StreamEvent.Failed -> {
                    primaryError = ev.error
                    if (sawContent) {
                        // Mid-stream failure cannot be retried transparently.
                        emit(ev)
                        return@collect
                    }
                    // fall through to fallback below
                }
            }
        }

        if (!sawContent && primaryError != null) {
            val fb = fallback
            if (fb != null && fb.id != primary.id) {
                Logger.w("ProviderExecutor", "Primary provider failed (${primaryError?.message}); trying fallback '${fb.displayName}'")
                var fbError: Throwable? = null
                resolve(fb.type).stream(fb, fallbackKey, messages).collect { ev ->
                    when (ev) {
                        is StreamEvent.Failed -> fbError = ev.error
                        else -> emit(ev)
                    }
                }
                if (fbError != null) {
                    emit(StreamEvent.Failed(
                        RuntimeException(
                            "Primary provider failed: ${primaryError?.message}. Fallback also failed: ${fbError?.message}"
                        )
                    ))
                }
            } else {
                emit(StreamEvent.Failed(primaryError ?: RuntimeException("Unknown provider failure")))
            }
        }
    }
}
