package com.agentos.app.data.mcp

import com.agentos.app.core.logging.Logger
import com.agentos.app.data.settings.McpServerConfig
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolRegistry
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Real MCP (Model Context Protocol) client over JSON-RPC 2.0 / HTTP.
 *
 * Implements the MCP handshake: initialize → notifications/initialized →
 * tools/list → tools/call. Supports Streamable HTTP servers that answer with
 * plain JSON or SSE frames. Discovered tools are exposed in the local
 * [ToolRegistry] under mcp_<server>_<tool> names.
 */
class McpClient(
    private val server: McpServerConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val PROTOCOL_VERSION = "2024-11-05"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private val nextId = AtomicLong(1)
    private var sessionId: String? = null
    private var serverInfo: JsonObject? = null

    val connected: Boolean get() = serverInfo != null

    private suspend fun post(body: JsonObject, expectBody: Boolean = true): JsonObject? = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(server.url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }
        server.authHeader.takeIf { it.isNotBlank() }?.let { auth ->
            requestBuilder.header(
                "Authorization",
                if (auth.startsWith("Bearer ", true) || auth.contains(" ")) auth else "Bearer $auth"
            )
        }
        client.newCall(requestBuilder.build()).execute().use { resp ->
            resp.header("Mcp-Session-Id")?.let { sessionId = it }
            if (!resp.isSuccessful) {
                throw IOException("MCP server responded HTTP ${resp.code}")
            }
            if (!expectBody) return@withContext null
            val contentType = resp.header("Content-Type") ?: ""
            val raw = if (contentType.contains("text/event-stream")) {
                // parse SSE frames for the last data: JSON payload
                val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
                val lines = mutableListOf<String>()
                var line: String? = reader.readLine()
                while (line != null) { lines.add(line); line = reader.readLine() }
                lines.lastOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim()
                    ?: throw IOException("MCP SSE response contained no data frame")
            } else {
                resp.body?.string() ?: throw IOException("MCP empty response")
            }
            json.parseToJsonElement(raw).jsonObject
        }
    }

    suspend fun initialize(): JsonObject {
        val initRequest = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", "initialize")
            put("params", buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                put("capabilities", buildJsonObject { })
                put("clientInfo", buildJsonObject {
                    put("name", "AgentOS")
                    put("version", "2.0.0")
                })
            })
        }
        val resultObj = post(initRequest)?.get("result")?.jsonObject
            ?: throw IOException("MCP initialize returned no result (is $server.url a valid MCP endpoint?)")
        serverInfo = resultObj["serverInfo"]?.jsonObject
        // notification: initialized
        post(buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }, expectBody = false)
        Logger.i("MCP", "Initialized server '${server.name}' (${serverInfo?.get("name")})")
        return resultObj
    }

    data class McpToolDef(val name: String, val description: String, val schema: JsonObject)

    suspend fun listTools(): List<McpToolDef> {
        val response = post(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", "tools/list")
            put("params", buildJsonObject { })
        }) ?: throw IOException("tools/list returned no response")
        response["error"]?.jsonObject?.let { err ->
            throw IOException("MCP error ${err["code"]}: ${err["message"]}")
        }
        val tools = response["result"]?.jsonObject?.get("tools")?.jsonArray ?: JsonArray(emptyList())
        return tools.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            McpToolDef(
                name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                description = obj["description"]?.jsonPrimitive?.content ?: "",
                schema = obj["inputSchema"] as? JsonObject ?: JsonObject(emptyMap())
            )
        }
    }

    data class McpToolCall(val text: String, val isError: Boolean)

    suspend fun callTool(name: String, arguments: JsonObject): McpToolCall {
        val response = post(buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", nextId.getAndIncrement())
            put("method", "tools/call")
            put("params", buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            })
        }) ?: throw IOException("tools/call returned no response")
        response["error"]?.jsonObject?.let { err ->
            return McpToolCall("MCP error ${err["code"]}: ${err["message"]}", isError = true)
        }
        val result = response["result"]?.jsonObject
            ?: return McpToolCall("MCP returned no result", isError = true)
        val isError = (result["isError"] as? JsonPrimitive)?.booleanOrNull ?: false
        val content = result["content"] as? JsonArray ?: JsonArray(emptyList())
        val text = content.joinToString("\n") { item ->
            (item as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: item.toString()
        }
        return McpToolCall(text.ifBlank { "(empty result)" }, isError)
    }

    fun close() {
        serverInfo = null
        sessionId = null
    }
}

/**
 * Manages MCP server connections and (de)registers their tools into the
 * local tool registry.
 */
class McpManager(
    private val registry: ToolRegistry,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    data class ServerState(val server: McpServerConfig, val client: McpClient, val toolNames: List<String>)

    private val _states = MutableStateFlow<Map<String, ServerState>>(emptyMap())
    val states: StateFlow<Map<String, ServerState>> = _states.asStateFlow()

    suspend fun connect(server: McpServerConfig): ServerState {
        disconnect(server.id, unregister = false)
        val client = McpClient(server, okHttpClient)
        client.initialize()
        val tools = client.listTools()
        val registered = mutableListOf<String>()
        for (t in tools) {
            val localName = sanitize("${server.name}_${t.name}")
            registry.register(McpProxyTool(localName, "mcp:${server.name}:${t.name}", t.description, client, t.name))
            registered.add(localName)
        }
        val state = ServerState(server, client, registered)
        _states.value = _states.value + (server.id to state)
        Logger.i("MCP", "Connected '${server.name}': ${registered.size} tools registered")
        return state
    }

    fun disconnect(serverId: String, unregister: Boolean = true) {
        val state = _states.value[serverId] ?: return
        if (unregister) state.toolNames.forEach { registry.unregisterByPrefix(it) }
        state.client.close()
        _states.value = _states.value - serverId
    }

    private fun sanitize(raw: String): String =
        "mcp_" + raw.lowercase().map { c -> if (c.isLetterOrDigit()) c else '_' }.joinToString("")

    /** A real MCP-backed tool: every execute() is a genuine JSON-RPC tools/call. */
    class McpProxyTool(
        override val name: String,
        private val remoteName: String,
        override val description: String,
        private val client: McpClient,
        private val remoteTool: String
    ) : Tool {
        override val risk = ToolRisk.MODERATE
        override val category = "MCP"
        override val inputSchema = "{} (schema negotiated from remote MCP server)"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            ctx.onActivity("Calling MCP tool $remoteName…")
            return try {
                val call = client.callTool(remoteTool, args)
                if (call.isError) ToolResult(false, call.text, "MCP tool '$remoteTool' reported an error")
                else ToolResult(true, call.text)
            } catch (e: Exception) {
                ToolResult(false, "", "MCP call failed: ${e.message}")
            }
        }
    }
}
