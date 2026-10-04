package com.agentos.app.domain.tools

import android.content.Context
import com.agentos.app.core.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

enum class ToolRisk { SAFE, MODERATE, HIGH_RISK }

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val artifacts: List<String> = emptyList(),
    val durationMs: Long = 0
)

/** Execution context handed to tools. */
class ToolContext(
    val appContext: Context,
    /** Reports human-readable activity, e.g. "Opening website…" */
    val onActivity: (String) -> Unit = {},
    /** Requests a screenshot permission flow; returns bytes or null if denied/unsupported. */
    val screenshotRequester: suspend () -> String? = { null }
)

/**
 * A real, executable tool. Anything that cannot actually run must NOT be
 * registered (project rule: NO FAKE FEATURES).
 */
interface Tool {
    val name: String
    val description: String
    val risk: ToolRisk
    val category: String
    /** Compact JSON-schema-ish description shown to the AI planner. */
    val inputSchema: String
    suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult
}

/**
 * Registry with validation, timing and logging. Tools are executed through
 * this registry only.
 */
class ToolRegistry {

    private val tools = LinkedHashMap<String, Tool>()

    fun register(tool: Tool) {
        require(tool.name.isNotBlank())
        tools[tool.name] = tool
        Logger.i("ToolRegistry", "Registered tool '${tool.name}' [risk=${tool.risk}]")
    }

    fun registerAll(list: List<Tool>) = list.forEach(::register)

    fun get(name: String): Tool? = tools[name]

    fun all(): List<Tool> = tools.values.toList()

    fun unregisterByPrefix(prefix: String) {
        tools.keys.filter { it.startsWith(prefix) }.forEach { tools.remove(it) }
    }

    suspend fun execute(name: String, args: JsonObject, ctx: ToolContext): ToolResult {
        val tool = tools[name] ?: return ToolResult(false, "", "Unknown tool '$name'")
        val start = System.currentTimeMillis()
        return try {
            val result = withContext(Dispatchers.IO) { tool.execute(args, ctx) }
            val timed = result.copy(durationMs = System.currentTimeMillis() - start)
            Logger.i(
                "ToolRegistry",
                "Tool '$name' ${if (timed.success) "succeeded" else "failed"} in ${timed.durationMs}ms" +
                    (timed.error?.let { " err=$it" } ?: "")
            )
            timed
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Logger.e("ToolRegistry", "Tool '$name' crashed", e)
            ToolResult(false, "", "Tool '$name' failed: $msg", durationMs = System.currentTimeMillis() - start)
        }
    }

    /** Builds the planner-facing capability block (name, risk, description, schema). */
    fun describeForPlanner(): String = tools.values.joinToString("\n") { t ->
        "- ${t.name} (risk=${t.risk}, category=${t.category}): ${t.description} | args: ${t.inputSchema}"
    }
}

/* ------------------------------------------------------------------ */
/* Arg helpers                                                         */
/* ------------------------------------------------------------------ */

object Args {
    fun str(args: JsonObject, key: String, default: String = ""): String =
        (args[key] as? JsonPrimitive)?.content ?: default
    fun int(args: JsonObject, key: String, default: Int = 0): Int =
        (args[key] as? JsonPrimitive)?.intOrNull ?: default
    fun long(args: JsonObject, key: String, default: Long = 0L): Long =
        (args[key] as? JsonPrimitive)?.longOrNull ?: default
    fun double(args: JsonObject, key: String, default: Double = 0.0): Double =
        (args[key] as? JsonPrimitive)?.doubleOrNull ?: default
    fun bool(args: JsonObject, key: String, default: Boolean = false): Boolean =
        (args[key] as? JsonPrimitive)?.booleanOrNull ?: default
}

/* ------------------------------------------------------------------ */
/* Shell command guard                                                 */
/* ------------------------------------------------------------------ */

/**
 * Blocks obviously destructive shell commands from reaching the shell tool.
 * High-risk commands additionally require user approval at the engine level.
 */
object CommandGuard {

    private val BLOCKED = listOf(
        Regex("""rm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)*(/|~)\S*(\s|$)"""),                   // rm -rf / or /path
        Regex("""rm\s+(-[a-zA-Z]*[rf][a-zA-Z]*\s+)*(~|\x24HOME)\S*(\s|$)"""),     // rm -rf ~ or $HOME path
        Regex("""mkfs(\.\w+)?\s"""),                                                // format fs
        Regex("""dd\s+.*of=/dev/(sd|hd|mmcblk|nvme)"""),                            // dd to disk
        Regex(""":\(\)\s*\{\s*:\|:\s*&\s*\}\s*;"""),                                // fork bomb
        Regex("""(?i)\b(shutdown|reboot|poweroff|halt)\b"""),
        Regex("""(?i)chmod\s+-R\s+777\s+(/|~)"""),
        Regex("""(?i)\b(su|sudo)\b\s"""),
        Regex("""(?i)curl\s+[^\|]*\|\s*(sudo\s+)?(ba)?sh"""),                       // curl|sh
        Regex("""(?i)wget\s+[^\|]*\|\s*(sudo\s+)?(ba)?sh"""),
        Regex("""(?i)mount\s+-o\s+remount"""),                                       // remount /
        Regex("""(?i)>\s*/dev/sd[a-z]"""),
        Regex("""(?i)apt\s+(-y\s+)?(remove|autoremove)\s+.*termux"""),
        Regex("""(?i)rm\s+-rf\s+\x24PREFIX"""),                                        // nuke termux env
    )

    fun check(command: String): String? {
        for (p in BLOCKED) {
            val m = p.find(command.trim())
            if (m != null) return "Command blocked by safety guard: matches destructive pattern '${m.value}'"
        }
        return null
    }
}
