package com.example.mcp.tools

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class BrowserToolHandler(
    private val context: Context
) : McpToolHandler {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    private fun getOrCreateWebView(): WebView {
        if (webView == null) {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
                webViewClient = object : WebViewClient() {}
            }
        }
        return webView!!
    }

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult {
        return when (toolName) {
            "browser.open" -> {
                val url = arguments.optString("url")
                if (url.isBlank()) return ToolExecutionResult(false, "", "URL is required")
                openUrl(url)
            }
            "browser.read" -> {
                extractPageText()
            }
            "browser.evaluate" -> {
                val script = arguments.optString("script")
                evaluateJs(script)
            }
            else -> ToolExecutionResult(false, "", "Unknown browser tool $toolName")
        }
    }

    private suspend fun openUrl(url: String): ToolExecutionResult = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        val deferred = CompletableDeferred<Boolean>()

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                deferred.complete(true)
            }
        }
        wv.loadUrl(url)

        val result = withTimeoutOrNull(15000L) { deferred.await() }
        if (result == true) {
            ToolExecutionResult(true, "Successfully navigated to $url")
        } else {
            ToolExecutionResult(true, "Navigation initiated for $url (page load still in progress)")
        }
    }

    private suspend fun extractPageText(): ToolExecutionResult = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        val deferred = CompletableDeferred<String>()
        val script = "(function() { return document.body ? document.body.innerText : 'Empty'; })();"

        wv.evaluateJavascript(script) { value ->
            val clean = value?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""
            deferred.complete(clean)
        }

        val text = withTimeoutOrNull(5000L) { deferred.await() } ?: "Timed out reading page DOM"
        ToolExecutionResult(true, text.take(5000))
    }

    private suspend fun evaluateJs(script: String): ToolExecutionResult = withContext(Dispatchers.Main) {
        val wv = getOrCreateWebView()
        val deferred = CompletableDeferred<String>()

        wv.evaluateJavascript(script) { value ->
            deferred.complete(value ?: "null")
        }

        val result = withTimeoutOrNull(5000L) { deferred.await() } ?: "Timed out executing script"
        ToolExecutionResult(true, result)
    }

    companion object {
        val OPEN = McpToolDefinition(
            name = "browser.open",
            description = "Navigates the internal Android headless browser to a specified web URL.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"url":{"type":"string"}}""",
            category = "Browser"
        )
        val READ = McpToolDefinition(
            name = "browser.read",
            description = "Extracts visible readable text content from the currently open webpage.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{}""",
            category = "Browser"
        )
        val EVALUATE = McpToolDefinition(
            name = "browser.evaluate",
            description = "Executes arbitrary JavaScript in the active webpage context.",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"script":{"type":"string"}}""",
            category = "Browser"
        )
    }
}
