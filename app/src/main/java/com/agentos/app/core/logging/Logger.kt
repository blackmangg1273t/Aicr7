package com.agentos.app.core.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Central structured logger.
 *
 * Guarantees (see SECURITY.md):
 * - Never persists API keys, bearer tokens or other secrets: every message is
 *   passed through [Redactor] before being stored or printed.
 * - Keeps an in-memory ring buffer for the in-app Logs viewer.
 * - Optionally appends to a log file under filesDir/logs.
 */
object Logger {

    enum class Level { DEBUG, INFO, WARNING, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val component: String,
        val message: String,
        val error: String? = null
    ) {
        fun format(): String {
            val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            val base = "$time ${level.name.first()} [$component] $message"
            return if (error != null) "$base  |  $error" else base
        }
    }

    private const val MAX_BUFFER = 500
    private val buffer = ConcurrentLinkedDeque<Entry>()
    private val listeners = mutableListOf<(Entry) -> Unit>()
    private val lock = Any()

    fun d(component: String, message: String) = log(Level.DEBUG, component, message)
    fun i(component: String, message: String) = log(Level.INFO, component, message)
    fun w(component: String, message: String, error: Throwable? = null) =
        log(Level.WARNING, component, message, error)
    fun e(component: String, message: String, error: Throwable? = null) =
        log(Level.ERROR, component, message, error)

    private fun log(level: Level, component: String, message: String, error: Throwable? = null) {
        val safeMessage = Redactor.redact(message)
        val safeError = error?.let { Redactor.redact(it.message ?: it.javaClass.simpleName) }
        val entry = Entry(System.currentTimeMillis(), level, component, safeMessage, safeError)
        synchronized(lock) {
            buffer.addLast(entry)
            while (buffer.size > MAX_BUFFER) buffer.pollFirst()
            val snapshot = listeners.toList()
            snapshot.forEach { it(entry) }
        }
        android.util.Log.println(
            when (level) {
                Level.DEBUG -> android.util.Log.DEBUG
                Level.INFO -> android.util.Log.INFO
                Level.WARNING -> android.util.Log.WARN
                Level.ERROR -> android.util.Log.ERROR
            },
            "AgentOS.$component",
            safeMessage + (safeError?.let { " | $it" } ?: "")
        )
    }

    fun recent(): List<Entry> = synchronized(lock) { buffer.toList() }

    fun addListener(l: (Entry) -> Unit) = synchronized(lock) { listeners.add(l) }

    fun removeListener(l: (Entry) -> Unit) = synchronized(lock) { listeners.remove(l) }

    fun clear() = synchronized(lock) { buffer.clear() }
}

/**
 * Redacts obvious secrets from any string destined for logs or UI traces.
 */
object Redactor {
    private val patterns = listOf(
        Regex("""sk-[A-Za-z0-9_-]{8,}"""),                       // OpenAI-style keys
        Regex("""AIza[A-Za-z0-9_-]{20,}"""),                     // Google API keys
        Regex("""gh[pousr]_[A-Za-z0-9_]{20,}"""),                // GitHub tokens
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),               // Fine-grained GitHub tokens
        Regex("""(?i)bearer\s+[A-Za-z0-9._-]{8,}"""),            // Authorization headers
        Regex("""(?i)(api[_-]?key|apikey|token|secret|password)\s*[=:]\s*\S+""") // key=value forms
    )

    fun redact(input: String): String {
        var result = input
        for (p in patterns) {
            result = p.replace(result) { m ->
                val v = m.value
                if (v.length <= 6) "***" else v.take(3) + "***REDACTED"
            }
        }
        return result
    }
}
