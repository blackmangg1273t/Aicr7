package com.agentos.app.data.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real headless WebView engine: loads pages, runs JS, extracts DOM content,
 * clicks elements by visible text / CSS selector, types into inputs, and
 * navigates back. All WebView work is marshalled to the main thread.
 *
 * Known real-world limits (documented, not faked):
 * - The WebView is not attached to a window, so visual rendering/pixel
 *   screenshots of pages are not guaranteed; text/DOM/JS work reliably.
 * - Sites with aggressive bot protection may block automated sessions.
 */
class BrowserEngine(private val appContext: Context) {

    companion object {
        const val TAG = "BrowserEngine"
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private val creating = AtomicBoolean(false)
    private var lastError: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun obtain(): WebView {
        webView?.let { return it }
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView must be created on main thread" }
        Logger.i(TAG, "Creating headless WebView")
        lastError = null
        val wv = WebView(appContext)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    lastError = "HTTP resource error ${error.errorCode}: ${error.description}"
                    Logger.w(TAG, lastError ?: "")
                }
            }
        }
        webView = wv
        return wv
    }

    /** Loads a URL and waits for page completion. Returns final URL or throws. */
    suspend fun open(url: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String =
        withContext(Dispatchers.Main) {
            lastError = null
            val wv = obtain()
            val done = CompletableDeferred<Result<String>>()
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, finishedUrl: String) {
                    if (!done.isCompleted) done.complete(Result.success(finishedUrl))
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && !done.isCompleted) {
                        done.complete(Result.failure(IOException("Failed to load $url: ${error.description} (code ${error.errorCode})")))
                    }
                }
            }
            Logger.i(TAG, "open($url)")
            wv.loadUrl(url)
            val outcome = withTimeoutOrNull(timeoutMs) { done.await() }
                ?: Result.failure(java.io.IOException("Page load timed out after ${timeoutMs}ms for $url"))
            outcome.getOrElse { throw it }
        }

    /** Returns {url, title, text} of the current page. */
    suspend fun readPage(maxChars: Int = 8000): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        val js = """
            (function(){
              var t = document.body ? document.body.innerText : '';
              return JSON.stringify({url: location.href, title: document.title, text: t});
            })();
        """.trimIndent().replace("\n", " ")
        val raw = evalJsInternal(wv, js, 8_000)
        parseJsonString(raw)?.let { obj ->
            val text = (obj["text"] ?: "").toString().replace(Regex("\\n{3,}"), "\n\n")
            val clipped = if (text.length > maxChars) text.take(maxChars) + "\n…[truncated]" else text
            "URL: ${obj["url"]}\nTITLE: ${obj["title"]}\n\n$clipped"
        } ?: "Page text extraction returned: ${raw.take(1000)}"
    }

    /** Clicks the first clickable element whose visible text contains [text]. */
    suspend fun clickByText(text: String): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        val js = """
            (function(){
              var needle = ${jsonStringLiteral(text)}.toLowerCase();
              var sel = 'a, button, [role=button], input[type=submit], input[type=button], [onclick], summary, label';
              var els = Array.prototype.slice.call(document.querySelectorAll(sel));
              for (var i=0;i<els.length;i++){
                var e = els[i];
                if (e.offsetParent === null) continue; // hidden
                var t = (e.innerText || e.value || e.getAttribute('aria-label') || '').toLowerCase();
                if (t && t.indexOf(needle) !== -1) {
                  var r = e.getBoundingClientRect();
                  e.scrollIntoView({block:'center'});
                  e.click();
                  return JSON.stringify({clicked:true, tag:e.tagName, text:(e.innerText||e.value||'').slice(0,80)});
                }
              }
              return JSON.stringify({clicked:false, reason:'no visible element containing text: '+needle});
            })();
        """.trimIndent().replace("\n", " ")
        val raw = evalJsInternal(wv, js, 5_000)
        parseJsonString(raw)?.let { obj ->
            if ((obj["clicked"] ?: "false").toString() == "true") {
                "Clicked ${obj["tag"]} '${obj["text"]}'. Waiting for page reaction…"
            } else {
                throw IllegalStateException(obj["reason"]?.toString() ?: "click failed")
            }
        } ?: throw IllegalStateException("click script returned no result: ${raw.take(300)}")
    }

    /** Types [value] into the first matching input (by placeholder/name/aria-label/text). */
    suspend fun typeInto(matcher: String, value: String, submit: Boolean): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        val js = """
            (function(){
              var needle = ${jsonStringLiteral(matcher)}.toLowerCase();
              var val = ${jsonStringLiteral(value)};
              var els = Array.prototype.slice.call(document.querySelectorAll('input, textarea, [contenteditable=true]'));
              function trySet(e){
                if (e.offsetParent === null) return false;
                var hay = [(e.placeholder||''),(e.name||''),(e.getAttribute('aria-label')||''),(e.id||''),(e.labels&&e.labels[0]?e.labels[0].innerText:'')].join(' ').toLowerCase();
                var t = (e.innerText||'').toLowerCase();
                if ((hay && hay.indexOf(needle)!==-1) || (t && t.indexOf(needle)!==-1) || needle==='*first*') {
                  e.focus();
                  if (e.isContentEditable) { e.textContent = val; }
                  else {
                    var proto = e.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                    setter.call(e, val);
                  }
                  e.dispatchEvent(new Event('input', {bubbles:true}));
                  e.dispatchEvent(new Event('change', {bubbles:true}));
                  return true;
                }
                return false;
              }
              for (var i=0;i<els.length;i++){ if (trySet(els[i])) {
                  var form = els[i].closest && els[i].closest('form');
                  ${if (submit) """
                  if (form) { if (typeof form.requestSubmit === 'function') { form.requestSubmit(); } else { form.submit(); } return JSON.stringify({typed:true, submitted:true}); }
                  else {
                    var fs = document.querySelector('input[type=submit], button[type=submit]');
                    if (fs) { fs.click(); return JSON.stringify({typed:true, submitted:true}); }
                  }
                  return JSON.stringify({typed:true, submitted:false});
                  """ else "return JSON.stringify({typed:true, submitted:false});"
                  }
              }}
              return JSON.stringify({typed:false, reason:'no input matching: '+needle});
            })();
        """.trimIndent().replace("\n", " ")
        val raw = evalJsInternal(wv, js, 5_000)
        parseJsonString(raw)?.let { obj ->
            if ((obj["typed"] ?: "false").toString() == "true") {
                "Typed ${value.length} chars" + (if ((obj["submitted"] ?: "false").toString() == "true") " and submitted the form" else "")
            } else {
                throw IllegalStateException(obj["reason"]?.toString() ?: "type failed")
            }
        } ?: throw IllegalStateException("type script returned no result: ${raw.take(300)}")
    }

    suspend fun goBack(): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        if (wv.canGoBack()) { wv.goBack(); "Navigated back (history size ${wv.copyBackForwardList().size})" }
        else throw IllegalStateException("No previous page in history")
    }

    suspend fun currentUrl(): String = withContext(Dispatchers.Main) { obtain().url ?: "(unknown)" }

    suspend fun evaluate(script: String): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        evalJsInternal(wv, script, 8_000).take(4000)
    }

    /* ---------------- internals ---------------- */

    private suspend fun evalJsInternal(wv: WebView, script: String, timeoutMs: Long): String {
        val deferred = CompletableDeferred<String>()
        mainHandler.post {
            try {
                wv.evaluateJavascript(script) { value -> deferred.complete(value ?: "null") }
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }
        return withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: throw IllegalStateException("JavaScript evaluation timed out")
    }

    private fun jsonStringLiteral(s: String): String {
        // Kotlin string -> JS single-quoted literal
        val escaped = s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        return "'$escaped'"
    }

    /** Evaluates the JS-quoted result string into a Map (evaluateJavascript returns a JSON-quoted string). */
    private fun parseJsonString(raw: String): Map<String, Any?>? {
        return try {
            val unquoted = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
                raw.drop(1).dropLast(1)
                    .replace("\\\\", "\u0000")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\u003c", "<")
                    .replace("\\u003e", ">")
                    .replace("\\u0026", "&")
                    .replace("\u0000", "\\")
            } else raw
            val json = kotlinx.serialization.json.Json { isLenient = true; ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(unquoted).let { it as? kotlinx.serialization.json.JsonObject }
                ?: return null
            obj.mapValues { (_, v) ->
                when (v) {
                    is kotlinx.serialization.json.JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "parseJsonString failed: ${e.message}")
            null
        }
    }

    class IOException(message: String) : Exception(message)

    fun shutdown() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }
}
