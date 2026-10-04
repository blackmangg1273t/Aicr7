package com.agentos.app.data.provider

import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Real SSE streaming client for any OpenAI-compatible endpoint:
 * OpenAI, GLM (bigmodel), Groq, OpenRouter, Together, Ollama, LM Studio, vLLM…
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = defaultClient()
) : AIProvider {

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private const val TAG = "Provider.OpenAI"

        /** Shared SSE line parser: returns emitted content deltas. */
        fun parseSseData(data: String): String? {
            if (data == "[DONE]") return null
            return try {
                val obj = json.parseToJsonElement(data).jsonObject
                val choices = obj["choices"]?.jsonArray ?: return null
                if (choices.isEmpty()) return null
                val delta = choices[0].jsonObject["delta"]?.jsonObject
                    ?: choices[0].jsonObject["message"]?.jsonObject
                    ?: return null
                delta["content"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                null // ignore malformed keep-alive/comment frames
            }
        }
    }

    override fun stream(config: ProviderConfig, apiKey: String, messages: List<ChatTurn>): Flow<StreamEvent> = flow {
        if (config.baseUrl.isBlank()) {
            emit(StreamEvent.Failed(IllegalArgumentException("Base URL is empty for provider '${config.displayName}'")))
            return@flow
        }
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val body = buildJsonObject {
            put("model", config.model.ifBlank { "default" })
            put("stream", true)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            put("messages", kotlinx.serialization.json.JsonArray(messages.map { m ->
                buildJsonObject {
                    put("role", m.role)
                    put("content", m.content)
                }
            }))
        }
        val reqBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")

        Logger.d(TAG, "POST ${config.baseUrl.trimEnd('/')} model=${config.model} msgs=${messages.size}")
        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(500) ?: "empty body"
                emit(StreamEvent.Failed(IOException("HTTP ${response.code} from $url: ${errBody}")))
                return@flow
            }
            val source = response.body?.byteStream() ?: run {
                emit(StreamEvent.Failed(IOException("Empty response body")))
                return@flow
            }
            val reader = BufferedReader(InputStreamReader(source))
            val full = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("data:")) {
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = parseSseData(data)
                    if (!delta.isNullOrEmpty()) {
                        full.append(delta)
                        emit(StreamEvent.Chunk(delta))
                    }
                }
                line = reader.readLine()
            }
            if (full.isBlank()) {
                emit(StreamEvent.Failed(IOException("Provider returned an empty stream (model '${config.model}')")))
            } else {
                emit(StreamEvent.Completed(full.toString()))
            }
        }
    }.catch { e ->
        emit(StreamEvent.Failed(e))
    }.flowOn(Dispatchers.IO)
}
