package com.agentos.app.domain.agent

import com.agentos.app.data.browser.BrowserEngine
import com.agentos.app.data.provider.ChatTurn
import com.agentos.app.domain.model.AgentTask
import com.agentos.app.domain.model.TaskStep
import com.agentos.app.domain.tools.ToolContext
import kotlinx.serialization.json.booleanOrNull
import com.agentos.app.domain.tools.ToolRegistry

/* ------------------------------------------------------------------ */
/* Research Agent — real multi-source research                         */
/* ------------------------------------------------------------------ */

class ResearchAgent(private val registry: ToolRegistry, private val toolCtx: ToolContext) : BaseAgent() {

    override val name = "research"
    override val displayName = "Research Agent"
    override val description = "Searches the web, fetches pages and compiles findings from multiple sources."
    override val capabilities = listOf("web_search", "web_fetch", "summarize_sources")

    override fun allowedTools() = listOf("web_search", "web_fetch")

    override suspend fun runStep(step: TaskStep, task: AgentTask, ctx: StepContext): String {
        requireAllowed(registry, step.toolName)
        val searchResult = execTool(registry, "web_search", step.argsJson, toolCtx)
        if (!searchResult.success) throw RuntimeException("web_search failed: ${searchResult.error}")

        // Optionally deep-fetch the first result for richer data
        val argsObj = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(step.argsJson)
                .let { it as? kotlinx.serialization.json.JsonObject }
        }.getOrNull()
        val fetchTop = (argsObj?.get("fetch_top") as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull == true
        val deep = if (fetchTop) {
            val firstUrl = searchResult.output.lineSequence()
                .firstOrNull { it.startsWith("URL: ") }?.removePrefix("URL: ")
            if (firstUrl != null) {
                ctx.onActivity("Deep-fetching first result: $firstUrl")
                val fr = execTool(registry, "web_fetch", """{"url":"$firstUrl","max_chars":4000}""", toolCtx)
                if (fr.success) "\n\n--- First result content (${firstUrl}) ---\n${fr.output}" else ""
            } else ""
        } else ""

        return searchResult.output + deep
    }
}

/* ------------------------------------------------------------------ */
/* Browser Agent — real WebView automation                             */
/* ------------------------------------------------------------------ */

class BrowserAgent(private val registry: ToolRegistry, private val toolCtx: ToolContext) : BaseAgent() {

    override val name = "browser"
    override val displayName = "Browser Agent"
    override val description = "Drives the built-in headless browser: opens pages, reads content, clicks, fills forms, navigates."
    override val capabilities = listOf("open_pages", "read_dom", "click_elements", "fill_forms", "js_evaluation")

    override fun allowedTools() = listOf("browser_", "web_fetch")

    override suspend fun runStep(step: TaskStep, task: AgentTask, ctx: StepContext): String {
        requireAllowed(registry, step.toolName)
        val result = execTool(registry, step.toolName!!, step.argsJson, toolCtx)
        if (!result.success) throw RuntimeException("${step.toolName} failed: ${result.error}")

        // After opening a page, automatically read its content if the plan expects data
        if (step.toolName == "browser_open") {
            ctx.onActivity("Extracting page content…")
            val read = execTool(registry, "browser_read", "{}", toolCtx)
            return result.output + "\n\n" + if (read.success) read.output else "(page read failed: ${read.error})"
        }
        return result.output
    }
}

/* ------------------------------------------------------------------ */
/* Android Agent — real device UI automation                           */
/* ------------------------------------------------------------------ */

class AndroidAgent(private val registry: ToolRegistry, private val toolCtx: ToolContext) : BaseAgent() {

    override val name = "android"
    override val displayName = "Android Agent"
    override val description = "Automates the phone UI: launches apps, taps, types, scrolls and reads the screen (requires user-enabled accessibility)."
    override val capabilities = listOf("launch_apps", "screen_tap", "screen_type", "screen_read", "gestures")

    override fun allowedTools() = listOf(
        "launch_app", "open_url", "take_screenshot", "device_info", "ui_", "android_ui_status"
    )

    override suspend fun runStep(step: TaskStep, task: AgentTask, ctx: StepContext): String {
        requireAllowed(registry, step.toolName)
        val result = execTool(registry, step.toolName!!, step.argsJson, toolCtx)
        if (!result.success) throw RuntimeException("${step.toolName} failed: ${result.error}")

        // Verify Android automation results when possible: after a tap/type, take a snapshot
        if (step.toolName in listOf("ui_tap", "ui_click_text", "ui_type")) {
            ctx.onActivity("Verifying screen state…")
            val snap = execTool(registry, "ui_snapshot", "{}", toolCtx)
            return result.output + if (snap.success) "\n\nScreen after action:\n${snap.output.take(1500)}" else ""
        }
        return result.output
    }
}

/* ------------------------------------------------------------------ */
/* Terminal Agent — real shell execution                               */
/* ------------------------------------------------------------------ */

class TerminalAgent(private val registry: ToolRegistry, private val toolCtx: ToolContext) : BaseAgent() {

    override val name = "terminal"
    override val displayName = "Terminal Agent"
    override val description = "Runs real shell commands (Termux when available, app sandbox otherwise) and reports stdout/stderr/exit codes."
    override val capabilities = listOf("shell_exec", "file_read", "file_write", "file_list")

    override fun allowedTools() = listOf("shell_exec", "file_", "device_info")

    override suspend fun runStep(step: TaskStep, task: AgentTask, ctx: StepContext): String {
        requireAllowed(registry, step.toolName)
        val result = execTool(registry, step.toolName!!, step.argsJson, toolCtx)
        if (!result.success) throw RuntimeException("${step.toolName} failed: ${result.error}")
        return result.output.ifBlank { "(command completed with no output)" }
    }
}

/* ------------------------------------------------------------------ */
/* Coding Agent — real file/code operations                            */
/* ------------------------------------------------------------------ */

class CodingAgent(private val registry: ToolRegistry, private val toolCtx: ToolContext) : BaseAgent() {

    override val name = "coding"
    override val displayName = "Coding Agent"
    override val description = "Reads, writes and edits files in the workspace, runs builds/tests via shell, uses git when available."
    override val capabilities = listOf("file_edit", "code_execution", "git", "error_fixing")

    override fun allowedTools() = listOf("file_", "shell_exec", "git")

    override suspend fun runStep(step: TaskStep, task: AgentTask, ctx: StepContext): String {
        requireAllowed(registry, step.toolName)
        val result = execTool(registry, step.toolName!!, step.argsJson, toolCtx)
        if (!result.success) throw RuntimeException("${step.toolName} failed: ${result.error}")
        return result.output.ifBlank { "(completed with no output)" }
    }
}

/* ------------------------------------------------------------------ */
/* Registry                                                            */
/* ------------------------------------------------------------------ */

class AgentRegistry(private val agents: List<Agent>) {

    private val byName = agents.associateBy { it.name.lowercase() }

    fun all(): List<Agent> = agents

    fun find(name: String): Agent? {
        val key = name.trim().lowercase()
        return byName[key]
            // tolerant aliases the planner might produce
            ?: byName[key.substringBefore("agent").trim()]
            ?: when {
                "browser" in key -> byName["browser"]
                "research" in key || "search" in key || "web" in key -> byName["research"]
                "android" in key || "phone" in key || "device" in key -> byName["android"]
                "terminal" in key || "shell" in key -> byName["terminal"]
                "coding" in key || "code" in key -> byName["coding"]
                "main" in key || "plan" in key -> byName["main"]
                else -> null
            }
    }

    fun catalog(): String = agents.joinToString("\n") { a ->
        "- agent \"${a.name}\" (${a.displayName}): ${a.description}. Capabilities: ${a.capabilities.joinToString(", ")}"
    }
}
