# Agents & Tools

## Agent contract

Every agent implements `Agent`:

```kotlin
interface Agent {
    val name: String            // stable id used by the planner ("research")
    val displayName: String     // shown in UI ("Research Agent")
    val description: String     // shown to the planner
    val capabilities: List<String>
    fun allowedTools(): List<String>   // tool-name prefixes this agent may use
    suspend fun runStep(step, task, ctx: StepContext): String
}
```

`runStep` **must do real work** or throw. Agents verify results where meaningful
(e.g. AndroidAgent re-snapshots the screen after tap/type; BrowserAgent reads the page after
opening). Agents are never allowed to use tools outside `allowedTools()` — enforced.

## Built-in agents

| Agent | Purpose | Tools |
|---|---|---|
| **MainAgent** | AI planning, delegation, final synthesis (streamed). Never executes tools itself. | — |
| **ResearchAgent** | Web research; `fetch_top:true` deep-fetches the first result. | `web_search`, `web_fetch` |
| **BrowserAgent** | Drives the headless WebView; auto-reads pages after `browser_open`. | `browser_*`, `web_fetch` |
| **AndroidAgent** | Real device automation; verifies by screen snapshot after actions. | `launch_app`, `open_url`, `ui_*`, `android_ui_status`, `take_screenshot`, `device_info` |
| **TerminalAgent** | Real shell + file operations (Termux or app sandbox). | `shell_exec`, `file_*`, `device_info` |
| **CodingAgent** | Workspace file/code ops, git (via Termux), build/test loops. | `file_*`, `shell_exec`, `git` |

## Tool catalog (all real implementations)

### Web
- `web_search` {query, max_results} — DuckDuckGo HTML search, parsed results with URLs.
- `web_fetch` {url, max_chars} — HTTP GET + HTML→text extraction.

### Browser (headless WebView)
- `browser_open` {url} — loads page, waits for completion, returns final URL.
- `browser_read` {max_chars} — URL/title/visible text of current page.
- `browser_click` {text} — clicks first visible link/button matching text (JS DOM click).
- `browser_type` {matcher, value, submit} — fills input by placeholder/label/name; optional submit.
- `browser_back` {} · `browser_current_url` {} · `browser_evaluate` {script}.

### Device / Android
- `device_info` {} — model, Android version, battery, storage, screen.
- `launch_app` {package|search} — real PackageManager launch, name→package search.
- `open_url` {url} — system VIEW intent.
- `take_screenshot` {} — MediaProjection consent → real capture → PNG path.

### Android UI automation (requires user-enabled Accessibility service)
- `android_ui_status` {} · `ui_tap` {x,y} · `ui_click_text` {text} · `ui_type` {text, hint} ·
  `ui_scroll` {direction} · `ui_back` {} · `ui_home` {} · `ui_snapshot` {} (structured screen dump).

### Terminal & files
- `shell_exec` {command, cwd?, timeout_ms?} — **HIGH_RISK**. Termux transport when installed
  (stdout/stderr/exit_code via RUN_COMMAND result files), else app-sandbox `sh`. `CommandGuard`
  blocks destructive commands outright.
- `file_read` / `file_write` / `file_list` / `file_delete` — workspace-sandboxed
  (path traversal rejected), delete is **HIGH_RISK**.

### Coding
- `git` {action: status|log|diff|clone|commit, repo?, url?, message?, count?} — needs Termux git;
  without it returns setup guidance instead of pretending.

### Memory
- `memory_store` {key, content, category} · `memory_search` {query, limit} ·
  `memory_delete` {key} (**HIGH_RISK**).

### MCP (dynamic)
- Every connected MCP server registers `mcp_<server>_<tool>` proxies — each execute is a real
  JSON-RPC `tools/call`.

## Risk gating

- `SAFE` — runs immediately (reads, searches).
- `MODERATE` — runs, logged (writes, taps, browser clicks).
- `HIGH_RISK` — **requires explicit user approval card in chat** before execution
  (`shell_exec`, `file_delete`, `memory_delete`); denial stops the task and records why.

## Adding a new tool

```kotlin
class MyTool : Tool {
    override val name = "my_tool"
    override val description = "What it really does"
    override val risk = ToolRisk.SAFE
    override val category = "Custom"
    override val inputSchema = """{"q": "string (required)"}"""
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val q = Args.str(args, "q")
        if (q.isBlank()) return ToolResult(false, "", "q is required")
        // real work; throw or return failure honestly
        return ToolResult(true, "result")
    }
}
```
Register it in `AppModules.kt` → the planner discovers it automatically.
