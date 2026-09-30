package com.example.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ModelRouter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    fun streamGenerate(
        config: ModelConfig,
        prompt: String,
        systemInstruction: String? = null
    ): Flow<ModelStreamEvent> = flow {
        try {
            when (config.provider) {
                ModelProviderType.GEMINI -> {
                    streamGemini(config, prompt, systemInstruction, this)
                }
                ModelProviderType.OPENAI_COMPATIBLE,
                ModelProviderType.QWEN,
                ModelProviderType.GLM,
                ModelProviderType.LOCAL_DEVICE -> {
                    streamOpenAiCompatible(config, prompt, systemInstruction, this)
                }
            }
        } catch (e: Exception) {
            emit(ModelStreamEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamGemini(
        config: ModelConfig,
        prompt: String,
        systemInstruction: String?,
        collector: kotlinx.coroutines.flow.FlowCollector<ModelStreamEvent>
    ) {
        val apiKey = config.apiKey.ifBlank { System.getenv("GEMINI_API_KEY") ?: "" }
        val model = if (config.id.isNotBlank()) config.id else "gemini-2.5-flash"
        val url = "${config.baseUrl}/models/$model:streamGenerateContent?key=$apiKey&alt=sse"

        val rootJson = JSONObject().apply {
            if (!systemInstruction.isNullOrBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                })
            }
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", config.temperature)
                put("maxOutputTokens", config.maxTokens)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                collector.emit(ModelStreamEvent.Error(RuntimeException("Gemini API Error (${response.code}): $errorBody")))
                return
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var line: String?
            val fullText = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                val cur = line?.trim() ?: continue
                if (cur.startsWith("data: ")) {
                    val data = cur.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val candidates = json.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val candidate = candidates.getJSONObject(0)
                            val content = candidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                val text = parts.getJSONObject(0).optString("text", "")
                                if (text.isNotEmpty()) {
                                    fullText.append(text)
                                    collector.emit(ModelStreamEvent.Chunk(text))
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            collector.emit(ModelStreamEvent.Completed(fullText.toString()))
        }
    }

    private suspend fun streamOpenAiCompatible(
        config: ModelConfig,
        prompt: String,
        systemInstruction: String?,
        collector: kotlinx.coroutines.flow.FlowCollector<ModelStreamEvent>
    ) {
        val url = "${config.baseUrl}/chat/completions"
        val rootJson = JSONObject().apply {
            put("model", config.id.ifBlank { "default" })
            put("stream", true)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)

            val messages = JSONArray()
            if (!systemInstruction.isNullOrBlank()) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
            put("messages", messages)
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(rootJson.toString().toRequestBody("application/json".toMediaType()))

        if (config.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                collector.emit(ModelStreamEvent.Error(RuntimeException("API Error (${response.code}): $errorBody")))
                return
            }

            val reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            var line: String?
            val fullText = StringBuilder()

            while (reader.readLine().also { line = it } != null) {
                val cur = line?.trim() ?: continue
                if (cur.startsWith("data: ")) {
                    val data = cur.substring(6).trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                fullText.append(content)
                                collector.emit(ModelStreamEvent.Chunk(content))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            collector.emit(ModelStreamEvent.Completed(fullText.toString()))
        }
    }
}
