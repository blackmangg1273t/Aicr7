package com.agentos.app.data.tools

import com.agentos.app.core.network.NetworkMonitor
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import okhttp3.OkHttpClient
import okhttp3.Request

/** Shared HTTP client for web tools. */
object WebHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    const val UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun htmlToText(html: String): String = html
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<noscript[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</(p|div|h[1-6]|li|tr)>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\n\\s*\n+"), "\n")
        .trim()
}

/** Validates a user/AI supplied URL before any network use. */
fun sanitizeUrl(raw: String): Result<String> {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("URL is empty"))
    val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) "https://$trimmed" else trimmed
    return try {
        val url = URL(withScheme)
        if (url.protocol != "http" && url.protocol != "https") {
            Result.failure(IllegalArgumentException("Only http/https URLs are allowed"))
        } else if (url.host.isNullOrBlank()) {
            Result.failure(IllegalArgumentException("URL has no host"))
        } else Result.success(withScheme)
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException("Invalid URL: ${e.message}"))
    }
}

/** Real web search via DuckDuckGo HTML endpoint (no API key required). */
class WebSearchTool(private val networkMonitor: NetworkMonitor) : Tool {
    override val name = "web_search"
    override val description =
        "Searches the web via DuckDuckGo and returns ranked results with URLs and snippets."
    override val risk = ToolRisk.SAFE
    override val category = "Web"
    override val inputSchema = """{"query": "string (required)", "max_results": "int (default 5)"}"""

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val query = Args.str(args, "query").trim()
        if (query.isEmpty()) return ToolResult(false, "", "query is required")
        if (!networkMonitor.isOnline()) return ToolResult(false, "", "Device is offline — web search unavailable")

        ctx.onActivity("Searching the web for \"$query\"…")
        val maxResults = Args.int(args, "max_results", 5).coerceIn(1, 10)
        return try {
            val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder().url(url).header("User-Agent", WebHttp.UA).build()
            WebHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return ToolResult(false, "", "Search failed: HTTP ${resp.code}")
                val html = resp.body?.string() ?: return ToolResult(false, "", "Search returned empty body")
                val results = parseDuckDuckGo(html, maxResults)
                if (results.isEmpty()) {
                    ToolResult(true, "No results parsed for \"$query\" (the search page layout may have changed or the query has no results).")
                } else {
                    ToolResult(true, results.joinToString("\n\n") { r ->
                        "${r.index}. ${r.title}\nURL: ${r.url}\n${r.snippet}"
                    })
                }
            }
        } catch (e: IOException) {
            ToolResult(false, "", "Search network error: ${e.message}")
        }
    }

    data class SearchResult(val index: Int, val title: String, val url: String, val snippet: String)

    companion object {
        fun parseDuckDuckGo(html: String, max: Int): List<SearchResult> {
            val out = mutableListOf<SearchResult>()
            // Result links look like: <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=ENCODED&amp;rut=...">
            val linkP = Pattern.compile(
                """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
                Pattern.CASE_INSENSITIVE
            )
            val snipP = Pattern.compile(
                """<a[^>]+class="result__snippet"[^>]*>([\s\S]*?)</a>""",
                Pattern.CASE_INSENSITIVE
            )
            val linkM = linkP.matcher(html)
            val snipM = snipP.matcher(html)
            val snippets = mutableListOf<String>()
            while (snipM.find() && snippets.size < max) snippets.add(WebHttp.htmlToText(snipM.group(1) ?: ""))
            var i = 0
            while (linkM.find() && out.size < max) {
                val href = linkM.group(1)?.trim() ?: continue
                val title = WebHttp.htmlToText(linkM.group(2) ?: "").ifBlank { "(untitled)" }
                val real = if (href.contains("uddg=")) {
                    val enc = href.substringAfter("uddg=").substringBefore("&")
                    runCatching { java.net.URLDecoder.decode(enc, "UTF-8") }.getOrDefault(href)
                } else if (href.startsWith("//")) "https:$href" else href
                out.add(SearchResult(++i, title, real, snippets.getOrNull(out.size) ?: ""))
            }
            return out
        }
    }
}

/** Fetches a URL and returns clean readable text (real HTTP GET + HTML strip). */
class WebFetchTool(private val networkMonitor: NetworkMonitor) : Tool {
    override val name = "web_fetch"
    override val description = "Downloads a web page and returns its readable text content."
    override val risk = ToolRisk.SAFE
    override val category = "Web"
    override val inputSchema = """{"url": "string (required)", "max_chars": "int (default 6000)"}"""

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val sanitized = sanitizeUrl(Args.str(args, "url")).getOrElse { return ToolResult(false, "", it.message ?: "Invalid URL") }
        if (!networkMonitor.isOnline()) return ToolResult(false, "", "Device is offline — web fetch unavailable")
        val maxChars = Args.int(args, "max_chars", 6000).coerceIn(200, 20000)
        ctx.onActivity("Fetching $sanitized…")
        return try {
            val request = Request.Builder().url(sanitized).header("User-Agent", WebHttp.UA).build()
            WebHttp.client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return ToolResult(false, "", "Fetch failed: HTTP ${resp.code} for $sanitized")
                val text = WebHttp.htmlToText(resp.body?.string() ?: "")
                ToolResult(true, if (text.length > maxChars) text.take(maxChars) + "\n…[truncated]" else text)
            }
        } catch (e: IOException) {
            ToolResult(false, "", "Fetch network error: ${e.message}")
        }
    }
}
