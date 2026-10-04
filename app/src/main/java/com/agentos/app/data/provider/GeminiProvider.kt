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
 * Native Google Generative Language API (Gemini) streaming client using SSE.
 */
class GeminiProvider(
    private val client: OkHttpClient = OpenAiCompatibleProvider.defaultClient()
) : AIProvider {

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private const val TAG = "Provider.Gemini"
        const val DEFAULT_BASE = "https://generativelanguage.googleapis.com/v1beta"

        fun extractText(data: String): String? {
            return try {
                val obj = json.parseToJsonElement(data).jsonObject
                val candidates = obj["candidates"]?.jsonArray ?: return null
                if (candidates.isEmpty()) return null
                val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
                    ?: return null
                parts.joinToString("") { p ->
                    p.jsonObject["text"]?.jsonPrimitive?.content ?: ""
                }.ifBlank { null }
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun stream(config: ProviderConfig, apiKey: String, messages: List<ChatTurn>): Flow<StreamEvent> = flow {
        if (apiKey.isBlank()) {
            emit(StreamEvent.Failed(IllegalArgumentException("Gemini API key is not configured")))
            return@flow
        }
        val model = config.model.ifBlank { "gemini-2.0-flash" }
        val base = config.baseUrl.ifBlank { DEFAULT_BASE }.trimEnd('/')
        val url = "$base/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

        val systemMsg = messages.firstOrNull { it.role == "system" }
        val body = buildJsonObject {
            if (systemMsg != null) {
                put("systemInstruction", buildJsonObject {
                    put("parts", kotlinx.serialization.json.JsonArray(kotlinx.serialization.json.JsonObject(
                        mapOf("text" to kotlinx.serialization.json.JsonPrimitive(systemMsg.content))
                    ).let { listOf(it) }))
                })
            }
            put("contents", kotlinx.serialization.json.JsonArray(messages.filter { it.role != "system" }.map { m ->
                buildJsonObject {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", kotlinx.serialization.json.JsonArray(
                        listOf(buildJsonObject { put("text", m.content) })
                    ))
                }
            }))
            put("generationConfig", buildJsonObject {
                put("temperature", config.temperature)
                put("maxOutputTokens", config.maxTokens)
            })
        }

        Logger.d(TAG, "POST ${base} model=$model msgs=${messages.size}")
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()?.take(500) ?: "empty body"
                emit(StreamEvent.Failed(IOException("Gemini HTTP ${response.code}: $errBody")))
                return@flow
            }
            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            val full = StringBuilder()
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("data:")) {
                    val data = trimmed.removePrefix("data:").trim()
                    val text = extractText(data)
                    if (!text.isNullOrEmpty()) {
                        full.append(text)
                        emit(StreamEvent.Chunk(text))
                    }
                }
                line = reader.readLine()
            }
            if (full.isBlank()) {
                emit(StreamEvent.Failed(IOException("Gemini returned an empty stream (model '$model')")))
            } else {
                emit(StreamEvent.Completed(full.toString()))
            }
        }
    }.catch { e ->
        emit(StreamEvent.Failed(e))
    }.flowOn(Dispatchers.IO)
}
