package com.example

import com.example.mcp.McpToolRegistry
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import com.example.agent.MainAgentOrchestrator
import com.example.model.ModelConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentUnitTest {

    @Test
    fun testToolRegistryRegistrationAndExecution() = runBlocking {
        val registry = McpToolRegistry()
        val mockTool = McpToolDefinition(
            name = "test.echo",
            description = "Echoes input text",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = "{}",
            category = "Test"
        )
        registry.registerTool(mockTool, object : McpToolHandler {
            override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult {
                val msg = arguments.optString("message", "default")
                return ToolExecutionResult(true, "Echo: $msg")
            }
        })

        assertNotNull(registry.getTool("test.echo"))
        assertEquals(1, registry.getAllTools().size)

        val result = registry.executeTool("test.echo", JSONObject().put("message", "AgentOS"))
        assertTrue(result.success)
        assertEquals("Echo: AgentOS", result.output)
    }

    @Test
    fun testUnknownToolReturnsFailure() = runBlocking {
        val registry = McpToolRegistry()
        val result = registry.executeTool("nonexistent.tool", JSONObject())
        assertFalse(result.success)
        assertTrue(result.error?.contains("Unknown tool") == true)
    }

    @Test
    fun testRiskLevelSafetyCategorization() {
        val readOnlyTool = McpToolDefinition("safe", "Safe tool", ToolRiskLevel.READ_ONLY, "{}", "General")
        val dangerousTool = McpToolDefinition("danger", "Destructive tool", ToolRiskLevel.HIGH_RISK, "{}", "General")

        assertEquals(ToolRiskLevel.READ_ONLY, readOnlyTool.riskLevel)
        assertEquals(ToolRiskLevel.HIGH_RISK, dangerousTool.riskLevel)
    }
}
