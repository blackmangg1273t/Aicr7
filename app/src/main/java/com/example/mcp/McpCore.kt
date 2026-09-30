package com.example.mcp

import org.json.JSONObject

enum class ToolRiskLevel {
    READ_ONLY,    // Safe: runs immediately (e.g., search, memory retrieval, status)
    MODERATE,     // Safe with notice: runs automatically but logged
    HIGH_RISK     // Destructive: requires explicit user confirmation (e.g. file delete, git push, shell rm)
}

data class McpToolDefinition(
    val name: String,
    val description: String,
    val riskLevel: ToolRiskLevel,
    val schemaJson: String,
    val category: String
)

data class ToolExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val artifacts: List<String> = emptyList(),
    val durationMs: Long = 0
)

interface McpToolHandler {
    suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult
}

class McpToolRegistry {
    private val tools = mutableMapOf<String, Pair<McpToolDefinition, McpToolHandler>>()

    fun registerTool(tool: McpToolDefinition, handler: McpToolHandler) {
        tools[tool.name] = Pair(tool, handler)
    }

    fun getTool(name: String): McpToolDefinition? = tools[name]?.first

    fun getAllTools(): List<McpToolDefinition> = tools.values.map { it.first }

    suspend fun executeTool(name: String, arguments: JSONObject): ToolExecutionResult {
        val entry = tools[name] ?: return ToolExecutionResult(
            success = false,
            output = "",
            error = "Unknown tool '$name'"
        )
        val startTime = System.currentTimeMillis()
        return try {
            val result = entry.second.execute(name, arguments)
            result.copy(durationMs = System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                output = "",
                error = e.localizedMessage ?: "Execution failed",
                durationMs = System.currentTimeMillis() - startTime
            )
        }
    }
}
