# AgentOS Architecture

## Layers

```
┌────────────────────────────────────────────────────────────┐
│ UI (Compose): Chat · Tasks · Terminal · Settings            │
│   MVVM ViewModels — no direct API or tool access            │
├────────────────────────────────────────────────────────────┤
│ TaskEngine (domain/engine)                                  │
│   plan → approve → execute → verify → synthesize            │
│   ApprovalManager · PlanParser                              │
├────────────────────────────────────────────────────────────┤
│ AgentSystem (domain/agent)                                  │
│   Agent interface · MainAgent · Research · Browser ·        │
│   Android · Terminal · Coding · AgentRegistry               │
├────────────────────────────────────────────────────────────┤
│ ToolSystem (domain/tools + data/tools)                      │
│   Tool interface · ToolRegistry (validation/timing/log) ·   │
│   ToolRisk { SAFE, MODERATE, HIGH_RISK } · CommandGuard     │
├────────────────────────────────────────────────────────────┤
│ Execution layer                                             │
│   AIProvider abstraction (OpenAI-compatible, Gemini) +      │
│   ProviderExecutor fallback chain · BrowserEngine (WebView) │
│   TermuxBridge (RUN_COMMAND) · McpClient (JSON-RPC/HTTP)    │
│   AssistantAccessibilityService                             │
├────────────────────────────────────────────────────────────┤
│ External systems                                            │
│   AI APIs · Web · Device · Termux · MCP servers · Room DB   │
└────────────────────────────────────────────────────────────┘
```

## Task lifecycle

1. **PENDING/PLANNING** — engine builds a planning prompt: user request + agent catalog +
   tool catalog (name/risk/description/schema) + relevant long-term memories + last turns.
2. The configured provider returns strict JSON; `PlanParser` normalizes it
   (`{direct_answer, answer, steps[{agent, tool, args, why, retry_on_failure}]}`),
   drops steps for unknown agents, caps at 8 steps. One strict-retry on parse failure.
3. **RUNNING** — steps execute sequentially. The step's agent validates tool ownership
   (`allowedTools()` prefixes), then the engine gates `HIGH_RISK` tools through
   `ApprovalManager` → task state `WAITING_USER` until you approve/deny in chat.
4. Tool output is persisted on the step (Room) and mirrored as chat events.
5. Failures: retry once when `retry_on_failure`; otherwise the task becomes `FAILED`
   with the concrete error (provider HTTP codes, timeouts, sandbox violations…).
6. **Synthesis** — MainAgent streams the final answer grounded in collected tool outputs
   (`AssistantChunk` events → streaming bubble → persisted ASSISTANT message).
7. **COMPLETED / CANCELLED** — cancellation is cooperative (coroutine cancellation).

## Data (Room, `agentos.db`)

- `conversations` / `messages` — every user/assistant/system/event message with role,
  agent name, event type, tool name, args, severity, approval link.
- `tasks` / `task_steps` — full task state machine history with results, errors, timings.
- `memories` — key/content/category long-term memory (auto-memory stores task outcomes).

Settings (DataStore): provider configs, primary/fallback selection, agent toggles, browser
and shell settings, MCP server list, privacy switches. **Secrets never go here** — they live
in `SecureStore` (EncryptedSharedPreferences + Android Keystore AES-256-GCM).

## Extensibility

- **New tool**: implement `Tool` (name/description/risk/schema/execute) → register in DI.
  The planner sees it automatically via `ToolRegistry.describeForPlanner()`.
- **New agent**: implement `Agent`, add to `AgentRegistry` — planner can route to it.
- **New provider**: implement `AIProvider` (streaming `Flow<StreamEvent>`) and add it to
  `ProviderExecutor.resolve()`; add a config in Settings.
- **MCP servers**: discovered tools register dynamically as `mcp_<server>_<tool>` and are
  immediately usable by the planner — no app changes.

## Concurrency & lifecycle

- Engine runs tasks in an app-scoped `CoroutineScope` (SupervisorJob + Default).
- All tool I/O is `Dispatchers.IO`; all WebView work is marshalled to the main thread.
- `TaskForegroundService` keeps long tasks alive in background (Android 9 compatible);
  `ScreenCaptureService` satisfies MediaProjection foreground requirements.
- Room is the source of truth; the UI re-renders from DB flows + live event stream.
