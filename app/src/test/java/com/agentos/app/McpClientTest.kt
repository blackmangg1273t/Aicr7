package com.agentos.app

import com.agentos.app.data.mcp.McpClient
import com.agentos.app.data.settings.McpServerConfig
import com.agentos.app.domain.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests the real MCP JSON-RPC client against a scripted MCP server:
 * initialize → notifications/initialized → tools/list → tools/call,
 * plus dynamic registration into the ToolRegistry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private inner class McpDispatcher : Dispatcher() {
        var id = 0
        override fun dispatch(request: RecordedRequest): MockResponse {
            val body = request.body.clone().readUtf8()  // clone: keep body for takeRequest()
            val req = json.parseToJsonElement(body).let { it as? kotlinx.serialization.json.JsonObject }
            val method = req?.get("method")?.toString()?.removeSurrounding("\"")
            return when (method) {
                "initialize" -> jsonOk("""
                    {"protocolVersion":"2024-11-05",
                     "capabilities":{"tools":{}},
                     "serverInfo":{"name":"test-mcp","version":"1.0"}}
                """.trimIndent(), sessionId = "sess-42")
                "tools/list" -> jsonOk("""
                    {"tools":[
                      {"name":"echo","description":"Echoes text back","inputSchema":{"type":"object","properties":{"text":{"type":"string"}}}},
                      {"name":"add","description":"Adds numbers","inputSchema":{"type":"object"}}
                    ]}
                """.trimIndent())
                "tools/call" -> {
                    val params = req!!["params"]!!.let { it as kotlinx.serialization.json.JsonObject }
                    val tool = params["name"].toString().removeSurrounding("\"")
                    jsonOk("""{"content":[{"type":"text","text":"result of $tool"}],"isError":false}""")
                }
                "notifications/initialized" -> MockResponse().setResponseCode(202)
                else -> MockResponse().setResponseCode(400)
            }
        }

        private fun jsonOk(resultJson: String, sessionId: String? = null): MockResponse {
            val idCounter = 1
            val response = """{"jsonrpc":"2.0","id":$idCounter,"result":$resultJson}"""
            val builder = MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(response)
            sessionId?.let { builder.setHeader("Mcp-Session-Id", it) }
            return builder
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = McpDispatcher()
        server.start()
    }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun fullMcpFlowAndDynamicRegistration() = runBlocking {
        val config = McpServerConfig(id = "s1", name = "testserver", url = server.url("/mcp").toString())
        val client = McpClient(config)
        client.initialize()

        val tools = client.listTools()
        assertEquals(2, tools.size)
        assertEquals("echo", tools[0].name)

        val call = client.callTool("echo", buildJsonObject { put("text", "hello") })
        assertTrue(!call.isError)
        assertEquals("result of echo", call.text)

        val recorded = server.takeRequest()
        assertEquals("/mcp", recorded.path)
        val initBody = recorded.body.readUtf8()
        assertTrue(initBody.contains("\"method\":\"initialize\""))
        assertTrue(initBody.contains("AgentOS"))
    }

    @Test
    fun mcpManagerRegistersProxyToolsInRegistry() = runBlocking {
        val registry = ToolRegistry()
        val manager = com.agentos.app.data.mcp.McpManager(registry)
        val config = McpServerConfig(id = "s1", name = "testserver", url = server.url("/mcp").toString())
        val state = manager.connect(config)

        assertEquals(2, state.toolNames.size)
        val proxy = registry.get("mcp_testserver_echo")
        assertEquals(true, proxy != null)
        val ctx = com.agentos.app.domain.tools.ToolContext(RuntimeEnvironment.getApplication())
        val result = registry.execute(
            "mcp_testserver_echo",
            buildJsonObject { put("text", "hi") },
            ctx
        )
        assertTrue(result.success)
        assertEquals("result of echo", result.output)
        manager.disconnect("s1")
    }
}
