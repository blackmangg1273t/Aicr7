package com.agentos.app.data.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real headless WebView engine. Loads pages with proper SPA synchronization,
 * extracts DOM content, clicks elements by text / CSS selector, types into
 * inputs (with glob/CSS-selector matcher), waits for selectors, navigates back,
 * and runs JS. All WebView work is marshalled to the main thread.
 *
 * Known real-world limits (documented, not faked):
 * - The WebView is not attached to a window; pixel rendering is not guaranteed,
 *   text/DOM/JS work reliably.
 * - document.querySelectorAll does not pierce Shadow DOM or iframes.
 * - Sites with aggressive bot protection may block automated sessions.
 */
class BrowserEngine(private val appContext: Context) {

    companion object {
        const val TAG = "BrowserEngine"
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val DEFAULT_SETTLE_MS = 1_200L      // SPA hydration grace period
        const val DEFAULT_WAIT_FOR_MS = 8_000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Suppress("unused") private val creating = AtomicBoolean(false)
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

    /** Current page URL or "(none)". */
    suspend fun currentUrl(): String = withContext(Dispatchers.Main) {
        obtain().url ?: "(none)"
    }

    /**
     * Loads a URL and waits for [onPageFinished] + a short SPA settle delay.
     * If [waitForSelector] is provided, polls up to [waitForMs] for the selector
     * to appear in the DOM before returning. Returns the resolved URL.
     * Same-URL short-circuit avoids reloading if the page is already loaded.
     */
    suspend fun open(
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        settleMs: Long = DEFAULT_SETTLE_MS,
        waitForSelector: String? = null,
        waitForMs: Long = DEFAULT_WAIT_FOR_MS
    ): String = withContext(Dispatchers.Main) {
        lastError = null
        val wv = obtain()

        // Same-URL short-circuit (idempotency)
        if (wv.url == url) {
            Logger.i(TAG, "open($url) skipped — already loaded")
            return@withContext url
        }

        val done = CompletableDeferred<Result<String>>()
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, finishedUrl: String) {
                if (done.isCompleted) return
                // SPA hydration grace period before declaring the page ready.
                mainHandler.postDelayed({
                    if (done.isCompleted) return@postDelayed
                    if (waitForSelector == null) {
                        done.complete(Result.success(finishedUrl))
                    } else {
                        pollForSelector(view, waitForSelector, waitForMs) { found ->
                            if (!done.isCompleted) {
                                if (found) done.complete(Result.success(finishedUrl))
                                else done.complete(Result.failure(
                                    java.io.IOException("Page loaded but selector '$waitForSelector' never appeared after ${waitForMs}ms")
                                ))
                            }
                        }
                    }
                }, settleMs)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !done.isCompleted) {
                    done.complete(Result.failure(
                        java.io.IOException("Failed to load $url: ${error.description} (code ${error.errorCode})")
                    ))
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && !done.isCompleted) {
                    done.complete(Result.failure(
                        java.io.IOException("HTTP ${response.statusCode} loading $url")
                    ))
                }
            }
        }
        Logger.i(TAG, "open($url) settle=${settleMs}ms" + (waitForSelector?.let { " wait=$it/$waitForMs ms" } ?: ""))
        wv.loadUrl(url)
        val outcome = withTimeoutOrNull(timeoutMs) { done.await() }
            ?: Result.failure(java.io.IOException("Page load timed out after ${timeoutMs}ms for $url"))
        outcome.getOrElse { throw it }
    }

    /**
     * Polls for a CSS selector to appear in the DOM. Calls [cb] with true on
     * appearance or false on timeout. Runs on the main thread.
     */
    private fun pollForSelector(wv: WebView, selector: String, timeoutMs: Long, cb: (Boolean) -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val tick = object : Runnable {
            override fun run() {
                wv.evaluateJavascript(
                    "(function(){try{return document.querySelector(${jsonStringLiteral(selector)})!==null;}catch(e){return false;}})();"
                ) { res ->
                    val found = res?.trim() == "true"
                    if (found) cb(true)
                    else if (System.currentTimeMillis() >= deadline) cb(false)
                    else mainHandler.postDelayed(this, 200)
                }
            }
        }
        mainHandler.post(tick)
    }

    /** Wait for [selector] to appear in the DOM. Returns true on success, false on timeout. */
    suspend fun waitForSelector(selector: String, timeoutMs: Long = DEFAULT_WAIT_FOR_MS): Boolean =
        withContext(Dispatchers.Main) {
            val wv = obtain()
            val done = CompletableDeferred<Boolean>()
            pollForSelector(wv, selector, timeoutMs) { done.complete(it) }
            withTimeoutOrNull(timeoutMs + 1_000) { done.await() } ?: false
        }

    /** Pause for [ms] milliseconds (useful as a primitive wait between actions). */
    suspend fun delayMs(ms: Long) = delay(ms.coerceIn(0L, 30_000L))

    /**
     * Returns {url, title, text} of the current page, plus a list of visible
     * inputs and buttons so the LLM can recover from "field not found".
     */
    suspend fun readPage(maxChars: Int = 8000): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        val js = """
            (function(){
              var t = document.body ? document.body.innerText : '';
              function describeInput(e){
                return {tag:e.tagName, type:e.type||'', name:e.name||'', id:e.id||'',
                  placeholder:e.placeholder||'', aria:e.getAttribute('aria-label')||''};
              }
              var ins = Array.prototype.slice.call(document.querySelectorAll('input, textarea, [contenteditable=true]'));
              var visible = [];
              for (var i=0;i<ins.length;i++){
                var e = ins[i];
                var cs = getComputedStyle(e);
                if (cs.display==='none'||cs.visibility==='hidden') continue;
                if (e.getClientRects().length===0) continue;
                visible.push(describeInput(e));
                if (visible.length>=12) break;
              }
              return JSON.stringify({
                url: location.href, title: document.title, text: t,
                inputs: visible
              });
            })();
        """.trimIndent().replace("\n", " ")
        val raw = evalJsInternal(wv, js, 8_000)
        parseJsonString(raw)?.let { obj ->
            val text = (obj["text"] ?: "").toString().replace(Regex("\\n{3,}"), "\n\n")
            val clipped = if (text.length > maxChars) text.take(maxChars) + "\n…[truncated]" else text
            val inputs = obj["inputs"]?.toString() ?: "[]"
            "URL: ${obj["url"]}\nTITLE: ${obj["title"]}\n\n$clipped\n\nVISIBLE_INPUTS: $inputs"
        } ?: "Page text extraction returned: ${raw.take(1000)}"
    }

    /**
     * Clicks the first clickable element whose visible text contains [text]
     * (case-insensitive). On miss, throws with a diagnostic listing of the
     * visible buttons on the page.
     */
    suspend fun clickByText(text: String): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        val js = """
            (function(){
              var needle = ${jsonStringLiteral(text)}.toLowerCase();
              var sel = 'a, button, [role=button], input[type=submit], input[type=button], [onclick], summary, label';
              var els = Array.prototype.slice.call(document.querySelectorAll(sel));
              function visible(e){
                var cs = getComputedStyle(e);
                if (cs.display==='none'||cs.visibility==='hidden'||cs.visibility==='collapse') return false;
                if (e.disabled) return false;
                return e.getClientRects().length > 0;
              }
              var available = [];
              for (var i=0;i<els.length;i++){
                var e = els[i];
                if (!visible(e)) continue;
                var t = (e.innerText || e.value || e.getAttribute('aria-label') || '').toLowerCase();
                if (t) available.push(t.slice(0, 60));
                if (t && t.indexOf(needle) !== -1) {
                  e.scrollIntoView({block:'center'});
                  e.click();
                  return JSON.stringify({clicked:true, tag:e.tagName, text:(e.innerText||e.value||'').slice(0,80)});
                }
              }
              return JSON.stringify({clicked:false, reason:'no visible element containing text: '+needle, available: available.slice(0, 12)});
            })();
        """.trimIndent().replace("\n", " ")
        val raw = evalJsInternal(wv, js, 5_000)
        parseJsonString(raw)?.let { obj ->
            if ((obj["clicked"] ?: "false").toString() == "true") {
                "Clicked ${obj["tag"]} '${obj["text"]}'. Waiting for page reaction…"
            } else {
                val avail = obj["available"]?.toString() ?: "(no diagnostic)"
                throw IllegalStateException((obj["reason"]?.toString() ?: "click failed") + " | visible buttons: $avail")
            }
        } ?: throw IllegalStateException("click script returned no result: ${raw.take(300)}")
    }

    /**
     * Types [value] into the first matching input. Matcher semantics:
     *   - "*first*"                   → first visible input (default)
     *   - "*search*", "*query*"       → glob/contains (asterisks are wildcards)
     *   - "search"                    → case-insensitive substring
     *   - "#search input", "#q"       → CSS selector fast path
     * Matcher is matched against: placeholder | name | aria-label | id | label text | innerText.
     * On miss, throws with a diagnostic listing of the inputs on the page.
     */
    suspend fun typeInto(matcher: String, value: String, submit: Boolean): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        val js = """
            (function(){
              var raw = ${jsonStringLiteral(matcher)};
              var val = ${jsonStringLiteral(value)};
              var needle = raw.toLowerCase();
              var isGlob = needle.indexOf('*') !== -1 && needle !== '*first*';
              function globToRe(s){
                var esc = s.replace(/[.+?^${'$'}()|[\]\\]/g, '\\${'$'}&').replace(/\*/g, '.*');
                return new RegExp(esc, 'i');
              }
              var re = isGlob ? globToRe(needle) : null;
              function matches(hay){
                if (!hay) return false;
                if (needle === '*first*') return true;
                if (re) return re.test(hay);
                return hay.toLowerCase().indexOf(needle) !== -1;
              }
              function visible(e){
                var cs = getComputedStyle(e);
                if (cs.display==='none'||cs.visibility==='hidden'||cs.visibility==='collapse') return false;
                if (e.disabled) return false;
                return e.getClientRects().length > 0;
              }
              var els = [];
              try {
                if (/^[.#\[]?[a-zA-Z*]/.test(raw)) {
                  var q = document.querySelectorAll(raw);
                  if (q && q.length) els = Array.prototype.slice.call(q);
                }
              } catch(_){}
              if (!els.length) els = Array.prototype.slice.call(document.querySelectorAll('input, textarea, [contenteditable=true]'));
              var available = [];
              for (var i = 0; i < els.length; i++){
                var e = els[i];
                if (!visible(e)) continue;
                var ph = e.placeholder || '', nm = e.name || '', al = e.getAttribute('aria-label') || '', id = e.id || '';
                var lbl = (e.labels && e.labels[0]) ? e.labels[0].innerText : '';
                var hay = [ph, nm, al, id, lbl].join(' | ');
                available.push({tag:e.tagName, type:e.type||'', name:nm, id:id, placeholder:ph, aria:al});
                if (matches(hay) || matches(e.innerText || '')) {
                  e.focus(); e.scrollIntoView({block:'center'});
                  if (e.isContentEditable) e.textContent = val;
                  else {
                    var proto = e.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
                    Object.getOwnPropertyDescriptor(proto, 'value').set.call(e, val);
                  }
                  e.dispatchEvent(new Event('input', {bubbles:true}));
                  e.dispatchEvent(new Event('change', {bubbles:true}));
                  var form = e.closest && e.closest('form');
                  var submitted = false;
                  ${if (submit) """
                  if (form) { submitted = true; if (typeof form.requestSubmit === 'function') form.requestSubmit(); else form.submit(); }
                  else { var fs = document.querySelector('input[type=submit], button[type=submit]'); if (fs) { submitted = true; fs.click(); } }
                  """ else ""}
                  return JSON.stringify({typed:true, submitted:submitted, field:{tag:e.tagName, name:nm, id:id, placeholder:ph}});
                }
              }
              return JSON.stringify({typed:false, reason:'no input matching: '+raw, available: available.slice(0, 15)});
            })();
        """.trimIndent().replace("\n", " ")
        val raw = evalJsInternal(wv, js, 5_000)
        parseJsonString(raw)?.let { obj ->
            if ((obj["typed"] ?: "false").toString() == "true") {
                val field = obj["field"]?.toString() ?: ""
                "Typed ${value.length} chars into $field" + (if ((obj["submitted"] ?: "false").toString() == "true") " and submitted the form" else "")
            } else {
                val avail = obj["available"]?.toString() ?: "(no diagnostic)"
                throw IllegalStateException((obj["reason"]?.toString() ?: "type failed") + " | available inputs on page: $avail")
            }
        } ?: throw IllegalStateException("type script returned no result: ${raw.take(300)}")
    }

    suspend fun goBack(): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        if (wv.canGoBack()) { wv.goBack(); "Navigated back (history size ${wv.copyBackForwardList().size})" }
        else throw IllegalStateException("No previous page in history")
    }

    suspend fun evaluate(script: String): String = withContext(Dispatchers.Main) {
        val wv = obtain()
        evalJsInternal(wv, script, 8_000).take(4000)
    }

    /** Reset the WebView between tasks (clears cookies, localStorage, history). */
    suspend fun reset() = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            wv.clearHistory()
            wv.clearCache(true)
            wv.clearFormData()
        }
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

    fun shutdown() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }
}
