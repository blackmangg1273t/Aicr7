package com.agentos.app

import com.agentos.app.data.provider.ChatTurn
import com.agentos.app.data.provider.GeminiProvider
import com.agentos.app.data.provider.OpenAiCompatibleProvider
import com.agentos.app.data.provider.ProviderConfig
import com.agentos.app.data.provider.ProviderType
import com.agentos.app.data.provider.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the REAL OpenAI-compatible and Gemini streaming clients against a
 * local mock HTTP server: request shape, SSE parsing, and honest error paths.
 */
class ProviderStreamingTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() { server = MockWebServer(); server.start() }

    @After
    fun tearDown() { server.shutdown() }

    @Test
    fun openAiCompatibleStreamsAndParsesSse() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                data: {"choices":[{"delta":{"content":"Hel"}}]}

                data: {"choices":[{"delta":{"content":"lo "}}]}

                data: {"choices":[{"delta":{"content":"world"}}]}

                data: [DONE]

                """.trimIndent()
            ).setHeader("Content-Type", "text/event-stream")
        )
        val provider = OpenAiCompatibleProvider()
        val config = ProviderConfig(
            id = "test", type = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = server.url("/v1").toString(), model = "test-model"
        )
        val events = provider.stream(config, "test-key-123", listOf(ChatTurn("user", "hi"))).toList()

        val chunks = events.filterIsInstance<StreamEvent.Chunk>()
        val completed = events.filterIsInstance<StreamEvent.Completed>().first()
        assertEquals("Hello world", completed.fullText)
        assertEquals(3, chunks.size)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer test-key-123", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"test-model\""))
        assertTrue(body.contains("\"stream\":true"))
    }

    @Test
    fun openAiCompatibleReportsHttpErrorHonestly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid api key"}"""))
        val provider = OpenAiCompatibleProvider()
        val config = ProviderConfig(id = "t", baseUrl = server.url("/v1").toString(), model = "m")
        val events = provider.stream(config, "bad-key", listOf(ChatTurn("user", "hi"))).toList()
        val failed = events.filterIsInstance<StreamEvent.Failed>().first()
        assertTrue(failed.error.message!!.contains("401"))
    }

    @Test
    fun openAiCompatibleSseParserHandlesMessageShape() {
        // non-streaming style response (message instead of delta) also parses
        val text = OpenAiCompatibleProvider.parseSseData(
            """{"choices":[{"message":{"content":"full reply"}}]}"""
        )
        assertEquals("full reply", text)
        assertEquals(null, OpenAiCompatibleProvider.parseSseData("not json"))
    }

    @Test
    fun geminiStreamsAndParsesSse() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                data: {"candidates":[{"content":{"parts":[{"text":"Goo"}]}}]}

                data: {"candidates":[{"content":{"parts":[{"text":"d day"}]}}]}

                """.trimIndent()
            ).setHeader("Content-Type", "text/event-stream")
        )
        val provider = GeminiProvider()
        val config = ProviderConfig(
            id = "g", type = ProviderType.GEMINI,
            baseUrl = server.url("/").toString(), model = "gemini-2.0-flash"
        )
        val events = provider.stream(config, "gkey", listOf(ChatTurn("user", "hello"))).toList()
        val completed = events.filterIsInstance<StreamEvent.Completed>().first()
        assertEquals("Good day", completed.fullText)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.contains("/models/gemini-2.0-flash:streamGenerateContent"))
        assertTrue(recorded.path!!.contains("alt=sse"))
    }

    @Test
    fun geminiRequiresApiKey() = runBlocking {
        val provider = GeminiProvider()
        val config = ProviderConfig(id = "g", type = ProviderType.GEMINI, baseUrl = server.url("/").toString())
        val events = provider.stream(config, "", listOf(ChatTurn("user", "hi"))).toList()
        val failed = events.filterIsInstance<StreamEvent.Failed>().first()
        assertTrue(failed.error.message!!.contains("API key"))
    }
}
