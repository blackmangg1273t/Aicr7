package com.example.mcp.tools

import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenCodeBridgeToolHandler(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private var daemonUrl: String = "http://127.0.0.1:4096"
) : McpToolHandler {

    fun setDaemonUrl(url: String) {
        daemonUrl = url
    }

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult = withContext(Dispatchers.IO) {
        when (toolName) {
            "opencode.status" -> {
                checkStatus()
            }
            "opencode.edit" -> {
                val filePath = arguments.optString("path")
                val instructions = arguments.optString("instructions")
                callDaemonEndpoint("/api/edit", JSONObject().apply {
                    put("path", filePath)
                    put("instructions", instructions)
                })
            }
            "opencode.analyze" -> {
                val repoPath = arguments.optString("path")
                callDaemonEndpoint("/api/analyze", JSONObject().apply {
                    put("path", repoPath)
                })
            }
            else -> ToolExecutionResult(false, "", "Unknown OpenCode bridge command: $toolName")
        }
    }

    private fun checkStatus(): ToolExecutionResult {
        return try {
            val req = Request.Builder().url("$daemonUrl/health").build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    ToolExecutionResult(true, "OpenCode companion daemon is active and responding on $daemonUrl")
                } else {
                    ToolExecutionResult(false, "", "OpenCode returned HTTP ${res.code}")
                }
            }
        } catch (_: Exception) {
            ToolExecutionResult(
                true,
                "OpenCode companion daemon not detected on $daemonUrl. Coding agent is operating in standalone internal AST/File mode."
            )
        }
    }

    private fun callDaemonEndpoint(endpoint: String, payload: JSONObject): ToolExecutionResult {
        return try {
            val req = Request.Builder()
                .url("$daemonUrl$endpoint")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                ToolExecutionResult(res.isSuccessful, body, if (!res.isSuccessful) body else null)
            }
        } catch (e: Exception) {
            ToolExecutionResult(
                false,
                "",
                "Connection to OpenCode companion at $daemonUrl failed: ${e.localizedMessage}. Please ensure the companion runtime is running."
            )
        }
    }

    companion object {
        val STATUS = McpToolDefinition(
            name = "opencode.status",
            description = "Checks connectivity to the OpenCode coding agent companion daemon.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{}""",
            category = "Coding"
        )
        val EDIT = McpToolDefinition(
            name = "opencode.edit",
            description = "Delegates an autonomous code editing task to OpenCode.",
            riskLevel = ToolRiskLevel.HIGH_RISK,
            schemaJson = """{"path":{"type":"string"},"instructions":{"type":"string"}}""",
            category = "Coding"
        )
        val ANALYZE = McpToolDefinition(
            name = "opencode.analyze",
            description = "Asks OpenCode to analyze the architecture and AST of a codebase.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"path":{"type":"string"}}""",
            category = "Coding"
        )
    }
}
