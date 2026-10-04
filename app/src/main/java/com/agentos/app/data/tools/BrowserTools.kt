package com.agentos.app.data.tools

import com.agentos.app.core.network.NetworkMonitor
import com.agentos.app.data.browser.BrowserEngine
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.serialization.json.JsonObject

/**
 * Real browser automation tools backed by [BrowserEngine] (headless WebView).
 */
class BrowserTools(private val engine: BrowserEngine, private val networkMonitor: NetworkMonitor) {

    private fun offline() = !networkMonitor.isOnline()

    inner class OpenTool : Tool {
        override val name = "browser_open"
        override val description = "Opens a URL in the built-in headless browser and waits for the page to load."
        override val risk = ToolRisk.SAFE
        override val category = "Browser"
        override val inputSchema = """{"url": "string (required)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val url = sanitizeUrl(Args.str(args, "url")).getOrElse { return ToolResult(false, "", it.message ?: "Invalid URL") }
            if (offline()) return ToolResult(false, "", "Device is offline — browser unavailable")
            ctx.onActivity("Opening $url…")
            return try {
                val finalUrl = engine.open(url)
                ToolResult(true, "Loaded page: $finalUrl")
            } catch (e: Exception) {
                ToolResult(false, "", "browser_open failed: ${e.message}")
            }
        }
    }

    inner class ReadTool : Tool {
        override val name = "browser_read"
        override val description = "Reads the current page: URL, title and visible text."
        override val risk = ToolRisk.SAFE
        override val category = "Browser"
        override val inputSchema = """{"max_chars": "int (default 8000)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            ctx.onActivity("Reading current page…")
            return try {
                ToolResult(true, engine.readPage(Args.int(args, "max_chars", 8000)))
            } catch (e: Exception) {
                ToolResult(false, "", "browser_read failed: ${e.message} (did you open a page first?)")
            }
        }
    }

    inner class ClickTool : Tool {
        override val name = "browser_click"
        override val description = "Clicks a link/button on the current page by its visible text."
        override val risk = ToolRisk.MODERATE
        override val category = "Browser"
        override val inputSchema = """{"text": "string (required, visible button/link text)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val text = Args.str(args, "text")
            if (text.isBlank()) return ToolResult(false, "", "text is required")
            ctx.onActivity("Clicking \"$text\"…")
            return try { ToolResult(true, engine.clickByText(text)) }
            catch (e: Exception) { ToolResult(false, "", "browser_click failed: ${e.message}") }
        }
    }

    inner class TypeTool : Tool {
        override val name = "browser_type"
        override val description = "Types text into a form field on the current page and optionally submits the form."
        override val risk = ToolRisk.MODERATE
        override val category = "Browser"
        override val inputSchema = """{"matcher": "string (field hint: placeholder/label/name; '*first*' = first input)", "value": "string (required)", "submit": "bool (default false)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val matcher = Args.str(args, "matcher", "*first*").ifBlank { "*first*" }
            val value = Args.str(args, "value")
            if (value.isBlank()) return ToolResult(false, "", "value is required")
            ctx.onActivity("Typing into form field…")
            return try {
                ToolResult(true, engine.typeInto(matcher, value, Args.bool(args, "submit", false)))
            } catch (e: Exception) { ToolResult(false, "", "browser_type failed: ${e.message}") }
        }
    }

    inner class BackTool : Tool {
        override val name = "browser_back"
        override val description = "Goes back to the previous page in the browser history."
        override val risk = ToolRisk.SAFE
        override val category = "Browser"
        override val inputSchema = "{}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult =
            try { ToolResult(true, engine.goBack()) }
            catch (e: Exception) { ToolResult(false, "", "browser_back failed: ${e.message}") }
    }

    inner class EvaluateTool : Tool {
        override val name = "browser_evaluate"
        override val description = "Runs JavaScript on the current page and returns the result. Use for advanced extraction."
        override val risk = ToolRisk.MODERATE
        override val category = "Browser"
        override val inputSchema = """{"script": "string (required, JavaScript expression returning a value)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val script = Args.str(args, "script")
            if (script.isBlank()) return ToolResult(false, "", "script is required")
            return try { ToolResult(true, engine.evaluate(script)) }
            catch (e: Exception) { ToolResult(false, "", "browser_evaluate failed: ${e.message}") }
        }
    }

    inner class UrlTool : Tool {
        override val name = "browser_current_url"
        override val description = "Returns the URL of the currently open page."
        override val risk = ToolRisk.SAFE
        override val category = "Browser"
        override val inputSchema = "{}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult =
            try { ToolResult(true, engine.currentUrl()) }
            catch (e: Exception) { ToolResult(false, "", "browser_current_url failed: ${e.message}") }
    }

    fun all(): List<Tool> = listOf(
        OpenTool(), ReadTool(), ClickTool(), TypeTool(), BackTool(), EvaluateTool(), UrlTool()
    )
}
