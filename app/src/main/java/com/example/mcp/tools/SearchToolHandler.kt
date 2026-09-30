package com.example.mcp.tools

import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SearchToolHandler(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : McpToolHandler {

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult = withContext(Dispatchers.IO) {
        when (toolName) {
            "search.web" -> {
                val query = arguments.optString("query")
                if (query.isBlank()) {
                    return@withContext ToolExecutionResult(false, "", "Query cannot be blank")
                }
                val maxResults = arguments.optInt("max_results", 5)
                searchDuckDuckGo(query, maxResults)
            }
            "search.fetch" -> {
                val url = arguments.optString("url")
                if (url.isBlank()) {
                    return@withContext ToolExecutionResult(false, "", "URL cannot be blank")
                }
                fetchUrl(url)
            }
            else -> ToolExecutionResult(false, "", "Unknown search tool: $toolName")
        }
    }

    private fun searchDuckDuckGo(query: String, maxResults: Int): ToolExecutionResult {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ToolExecutionResult(false, "", "Search HTTP error ${response.code}")
                }
                val html = response.body?.string() ?: ""
                val results = parseDuckDuckGoHtml(html, maxResults)
                if (results.isEmpty()) {
                    ToolExecutionResult(true, "No direct results found for query: $query. Raw snippet: ${cleanHtmlToText(html).take(300)}")
                } else {
                    ToolExecutionResult(true, results.joinToString("\n\n---\n\n"))
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "", e.localizedMessage ?: "Search failed")
        }
    }

    private fun parseDuckDuckGoHtml(html: String, maxResults: Int): List<String> {
        val results = mutableListOf<String>()
        val resultPattern = Pattern.compile(
            "<a class=\"result__url\" href=\"(.*?)\"[^>]*>(.*?)</a>[\\s\\S]*?<a class=\"result__snippet\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = resultPattern.matcher(html)
        var count = 0
        while (matcher.find() && count < maxResults) {
            val rawUrl = matcher.group(1)?.trim() ?: ""
            val snippet = cleanHtmlToText(matcher.group(3) ?: "")
            val url = if (rawUrl.contains("uddg=")) {
                try {
                    val encoded = rawUrl.substringAfter("uddg=").substringBefore("&")
                    java.net.URLDecoder.decode(encoded, "UTF-8")
                } catch (_: Exception) { rawUrl }
            } else rawUrl

            results.add("Result #${count + 1}:\nURL: $url\nSnippet: $snippet")
            count++
        }
        return results
    }

    private fun fetchUrl(url: String): ToolExecutionResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ToolExecutionResult(false, "", "Fetch HTTP error ${response.code}")
                }
                val rawHtml = response.body?.string() ?: ""
                val text = cleanHtmlToText(rawHtml)
                ToolExecutionResult(true, text.take(6000))
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "", e.localizedMessage ?: "Failed to fetch URL")
        }
    }

    private fun cleanHtmlToText(html: String): String {
        return html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        val SEARCH_WEB = McpToolDefinition(
            name = "search.web",
            description = "Searches the web via DuckDuckGo and returns ranked titles, URLs, and snippets.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"query":{"type":"string"},"max_results":{"type":"integer"}}""",
            category = "Search"
        )
        val SEARCH_FETCH = McpToolDefinition(
            name = "search.fetch",
            description = "Fetches a web URL and extracts clean readable text content.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"url":{"type":"string"}}""",
            category = "Search"
        )
    }
}
