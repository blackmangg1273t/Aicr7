# AgentOS — Full Repository Audit Report

**Date:** 2026-10-05
**Repo:** `github.com/blackmangg1273t/Aicr7` @ `3f68048` (v4.0.0)
**Auditor:** AgentOS Audit Agent (read-only deep code audit)
**Scope:** Task Engine, Planner, Agent Router, Browser Agent + Tools + Engine, Android Agent + Accessibility Service + Tools, Foreground/Overlay/Notification/Service layers, Lifecycle, Process-death recovery, Tool Registry & Schema validation, Event system, Room persistence, ViewModels/UI.

---

## Executive summary

The repository is **not** a prototype. It is a real, modular Android codebase (~3k LOC of meaningful Kotlin) with a working Koin DI graph, a real `AccessibilityService`, real headless `WebView` automation, real `Termux` and `MCP` bridges, real Room persistence, real `OkHttp` streaming providers, and a sensible `SupervisorJob + Dispatchers.Default` engine scope.

**However**, three concrete bugs in the execution glue cause every multi-step task to fail in the exact way the user describes:

1. `browser_type failed: no input matching: *search*` — the matcher wildcard `*…*` is treated as a *literal substring*, not a glob. The only special-cased wildcard is the literal string `*first*`. Every other `*foo*`-style matcher (which is what an LLM naturally emits from the existing schema example) never matches anything.
2. `ui_type failed: No active window is accessible right now` — `service.rootInActiveWindow` is read **once**, synchronously, with no retry. After `launch_app` returns (which it does the instant `startActivity` is called, before the target app's window is registered), the next `ui_*` step hits a `null` root and throws. The exception name (`AccessibilityNotEnabled`) wrongly tells the user to go enable the service, when the service is already enabled — the actual cause is a transient window-state gap.
3. **Single step failure aborts the entire task.** Any non-`CancellationException` in any step's `runStep` propagates to `TaskEngine.runTask`'s catch, marks `TaskStatus.FAILED`, and ends the task. There is **no recovery, no replanning, no skip-and-continue, no per-step verification, no idempotency check, no OBSERVE→VERIFY phase** between steps. The plan is a fixed script; the engine executes it linearly.

Three additional architectural defects compound those bugs:

4. **`TaskForegroundService` is never started.** Zero call sites in the entire codebase. The service class and manifest entry exist but the engine never calls `start()`. The engine runs in an app-scoped coroutine that survives Activity destruction but the unprotected process is killed by the system in seconds once the user backgrounds the app — exactly the user's "task dies when I leave" symptom.
5. **No floating overlay.** No `SYSTEM_ALERT_WINDOW` permission, no `WindowManager` code. Even if the process survived (it doesn't), there would be no on-screen progress indicator outside the app.
6. **No process-death recovery.** `AgentOsApp.onCreate` only starts Koin. Orphaned `RUNNING`/`PLANNING` rows in Room stay in that state forever; on next launch the user sees a "Working…" task that is actually dead.

The user's other complaints (raw plan logs, fake-looking UI, no Diagnostics, no Resume, no Pause/Cancel buttons, no verification before "Completed", no partial completion) are downstream consequences of these architectural gaps plus several missing UI features.

---

## Classification (KEEP / FIX / REFACTOR / REPLACE)

### Engine + Planner + Agents
| Component | Verdict | Justification (one line) |
|---|---|---|
| `TaskEngine` core (state, scope, jobs, events) | **KEEP** | App-scoped `SupervisorJob + Dispatchers.Default`, Koin `single`, `MutableSharedFlow` events — correct foundation. |
| `TaskEngine.runTask` execution loop | **REFACTOR** | Replace linear for-loop with **observe → decide → act → verify** loop; add recovery, replanning, per-step verification, idempotency, partial completion. |
| `TaskEngine.start()` race | **FIX** | `taskRepo.create()` and `setTaskStatus(PLANNING)` fire on independent coroutines; PENDING can overwrite PLANNING. |
| `PlanParser` extraction | **KEEP** | Robust fence/balanced-brace extraction, lenient JSON. |
| `PlanParser` validation | **FIX** | Drop unknown-agent steps silently (no event, no Logger, just `println`); never validate tool names against registry; empty non-direct plan falls through to hallucinated synthesis. |
| `ApprovalManager` happy path | **KEEP** | `CompletableDeferred<Boolean>` + Room persistence for the message is correct. |
| `ApprovalManager` robustness | **FIX** | `cancelAll()` never called on task cancel; timeout mislabeled as "Denied"; `TaskStatus.WAITING_USER` declared but never assigned; `pending` map in-memory only (lost on process death). |
| `MainAgent.plan` + `synthesize` | **REFACTOR** | Add `decideNextStep()` so the planner can react to intermediate results and replan when reality diverges from the plan. |
| `AgentRegistry` | **KEEP** | Tolerant name resolution works; optionally tighten fuzzy `contains` matching. |
| `BaseAgent.execTool` arg coercion | **FIX** | Silently coerces non-object args to `JsonObject(emptyMap())`; `Args.str` returns `""` on missing keys — masks LLM errors. |
| `Tool` interface / `ToolRegistry` | **REFACTOR** | Free-form string schema is too loose; add structured arg validation that runs **before** dispatch. |
| `ToolRisk` + approval gate | **KEEP** | Three-tier classification + engine-level approval gate is correct. |
| `Models.kt` enums | **FIX** | Add `RECOVERING`, `REPLANNING`, `VERIFYING`, `PARTIAL`; wire up `WAITING_USER` (declared, never assigned); wire up dead events (`ToolStarted`, `ToolFinished`, `AssistantChunk`). |
| Event system (SharedFlow + ChatMessage) | **KEEP** | Dual live-stream + Room persistence is good. |
| `TaskRepository` persistence | **FIX** | Add `runningTasks()` / `markInterrupted(id)` helpers; fix `create()` / `update()` race. |

### Browser
| Component | Verdict | Justification |
|---|---|---|
| `BrowserEngine.open` core (load + `onPageFinished` + timeout) | **REFACTOR** | Add same-URL short-circuit, SPA settle delay, `onReceivedHttpError`, optional `waitForSelector`. |
| `BrowserEngine.typeInto` matcher | **FIX** | Implement glob `*X*` → `.*X.*`, CSS selector fast path, `*first*` fallback; surface `available` inputs in the error so the LLM can recover. |
| `BrowserEngine.clickByText` | **FIX** | Replace `offsetParent === null` (false-positive on `position:fixed`/`sticky`) with `getComputedStyle` + `getClientRects().length > 0`; report multiple matches; add diagnostics on miss. |
| `BrowserEngine.readPage` | **FIX** | Include input values + a list of visible inputs/buttons; return structured JSON. |
| `BrowserEngine.evaluate`, `goBack`, `currentUrl` | **KEEP** | Fine. |
| `BrowserEngine.parseJsonString` | **REFACTOR** | Replace hand-rolled unescaping with `Json.decodeFromString<JsonElement>`. |
| `BrowserEngine.shutdown` | **REFACTOR** | Wire to per-task reset (or app lifecycle); add `clearHistory` + cookie/storage clear. Singleton WebView otherwise leaks across tasks. |
| `BrowserTools` set | **FIX** | Add `browser_wait` tool; document matcher contract in schema; return diagnostics on miss. |
| `BrowserAgent.runStep` | **FIX** | After `browser_open`, wait for selector or settle delay before auto-read; after `browser_type {submit:true}`, auto-read the result page. |
| `WebSearchTool`, `WebFetchTool`, `sanitizeUrl` | **KEEP** | Independent of the bug. |

### Android + Services
| Component | Verdict | Justification |
|---|---|---|
| `AssistantAccessibilityService` core (gestures, clickByText, typeText, scroll, globalAction, screenSnapshot) | **KEEP** | Correct API usage, real implementation. |
| `rootInActiveWindow` access pattern | **FIX** | Add `awaitActiveWindow()` + `awaitPackage()` with 100 ms polling and 3–5 s timeouts; replace the three throw sites. |
| `AccessibilityNotEnabled` exception | **REFACTOR** | Split into `AccessibilityNotEnabled` (permission) and `NoActiveWindowException` (transient). |
| `accessibility_service_config.xml` | **FIX** | Set `canTakeScreenshot="true"` so the Android 11+ accessibility screenshot API becomes usable as a fallback. |
| `TaskForegroundService` | **FIX** | Actually call `start()` from `TaskEngine`; update notification per step; add `Pause`/`Cancel`/`Open` actions; use `START_STICKY` (or `REDELIVER_INTENT`). |
| `ScreenCaptureService` | **FIX** | Call `start()` before `createVirtualDisplay` (Android 14+ requires this); call `stop()` after. |
| `ScreenCaptureCoordinator` consent flow | **REFACTOR** | Only `MainActivity` observes `uiRequest`; route through a `ProcessLifecycleOwner`-owned observer so consent can fire when the user is in another app. |
| `LaunchAppTool` | **REFACTOR** | Wait for foreground package; return structured result `{package, foreground_after_ms}`. |
| `AndroidAgent.runStep` | **REFACTOR** | Add post-`launch_app` settle delay; gate `ui_*` on `awaitActiveWindow()` first. |
| `ui_*` tool return values | **REFACTOR** | Switch to JSON for parseability; verify `SET_TEXT` actually changed the field by re-reading the node. |
| `TaskEngine` scope and Koin wiring | **KEEP** | Correct: app-scoped, independent of Activity. |
| `AgentOsApp.onCreate` | **FIX** | Add startup scan for orphaned `RUNNING`/`PLANNING` tasks; mark as `INTERRUPTED` with a "Resume" UI. |
| `MainActivity` lifecycle | **REFACTOR** | Add `ProcessLifecycleOwner` observer; route `OverlayController.show/hide` and `TaskForegroundService` start/stop through it. |
| `AndroidManifest.xml` | **FIX** | Add `SYSTEM_ALERT_WINDOW`; add `<receiver>` entries for notification actions; add `RECEIVE_BOOT_COMPLETED` + boot receiver (optional). |
| Permissions UX (Settings) | **REFACTOR** | Add overlay card + notification-prompt card; onboarding flow on first launch. |
| Notifications | **REFACTOR** | Per-step text updates; `Pause`/`Cancel`/`Open` actions; `BroadcastReceiver` for actions. |
| `ChatViewModel` / `TasksViewModel` | **KEEP** | Correct MVVM; engine-events-driven. Add "Resume" action in `TasksViewModel`. |

### UI
| Component | Verdict | Justification |
|---|---|---|
| Compose theme, type, motion | **KEEP** | Solid base; will tweak for premium feel. |
| Chat screen layout | **REFACTOR** | Compact user/agent messages, separate Task Plan Card, Execution Timeline, Running Aura; hide raw plan logs and chain-of-thought; friendly error UX with Retry/Try another method/Stop. |
| Tasks screen | **REFACTOR** | Running/Completed/Failed sections with proper status, agent, step count, duration, Resume button. |
| Task Details screen | **REFACTOR** | Plan + live activity + verification + agent status. |
| Settings screen | **REFACTOR** | Reorganize into AI / Agents / Automation / Connections / Privacy / Developer sections. |
| Diagnostics screen | **NEW** | Add Accessibility/Overlay/ForegroundService/Browser/TaskEngine/AIProvider status indicators + last error/recovery. |
| Terminal screen | **KEEP** | Out of scope; works. |

---

## The 15 highest-impact bugs (with file:line)

| # | Bug | Where | Symptom |
|---|---|---|---|
| 1 | `*search*` matcher treated as literal substring, not glob | `BrowserEngine.kt:153` | `browser_type failed: no input matching: *search*` |
| 2 | `browser_open` returns at `onPageFinished`, before SPA hydration | `BrowserEngine.kt:76-87`, `Agents.kt:68-72` | Type/click on YouTube/Google/Twitter finds zero inputs |
| 3 | No `browser_wait` tool, no element-poll primitive | `BrowserTools.kt:129-131` | LLM has no way to wait for dynamic content |
| 4 | `browser_*` failure returns only `e.message`, no diagnostics | `BrowserTools.kt:86` | LLM can't self-correct; loops on same matcher |
| 5 | `rootInActiveWindow` checked once, no retry | `AssistantAccessibilityService.kt:102,137,200` | `ui_type failed: No active window is accessible right now` |
| 6 | `launch_app` returns immediately, no foreground wait | `DeviceTools.kt:80` | Next `ui_*` step hits null root |
| 7 | `AndroidAgent.runStep` has no inter-step settle | `Agents.kt:92-104` | Race between launch and type |
| 8 | Single step failure aborts entire task; no recovery, no replanning | `TaskEngine.kt:301,316-329` | "Task ends after first step" |
| 9 | `PlanParser` doesn't validate tool names against registry | `PlanParser.kt:55-58` | Unknown tools surface only at execution → step fails → task fails |
| 10 | `PlanParser` drops unknown-agent steps silently (`println`, not Logger) | `PlanParser.kt:37` | Half the user's plan vanishes |
| 11 | Empty non-direct plan silently falls through to hallucinated synthesis | `TaskEngine.kt:206→307` | User gets ungrounded answer with no tool execution |
| 12 | `TaskForegroundService` is never started (zero call sites) | `TaskForegroundService.kt:25`, grep = 0 hits | Process killed when user backgrounds app → task dies |
| 13 | No `SYSTEM_ALERT_WINDOW`, no overlay code | `AndroidManifest.xml`, grep = 0 hits | No progress indicator outside the app |
| 14 | No process-death recovery; orphans stay `RUNNING` in Room forever | `AgentOsApp.kt:13-22` | User sees "Working…" task that is actually dead |
| 15 | `ApprovalManager.cancelAll()` never called; timeout mislabeled as denial | `ApprovalManager.kt:48,55,69` | Approval deferred leaks for 10 min; user told "Denied" when they did nothing |

---

## Recommended fix order

1. **Engine core** (Bug #8): replace linear for-loop in `TaskEngine.runTask` with **observe → decide → act → verify** loop, add recovery/replan/partial-completion, wire up dead events.
2. **Browser matcher + SPA wait + `browser_wait` tool + diagnostics** (Bugs #1–#4): local fix in `BrowserEngine.kt` + `BrowserTools.kt` + `Agents.kt`.
3. **`rootInActiveWindow` retry + `launch_app` sync + `AndroidAgent` settle** (Bugs #5–#7): local fix in `AssistantAccessibilityService.kt` + `DeviceTools.kt` + `Agents.kt`.
4. **Planner validation + ApprovalManager + PlanParser** (Bugs #9–#11, #15): `PlanParser.kt` + `ApprovalManager.kt` + `Models.kt`.
5. **Foreground service + notifications + action receiver** (Bug #12): `TaskForegroundService.kt` + new `TaskActionReceiver` + manifest.
6. **Floating overlay** (Bug #13): new `OverlayService` + `OverlayController` + manifest + permission UX.
7. **Process death recovery** (Bug #14): `AgentOsApp.onCreate` + `TaskRepository.runningTasks()/markInterrupted()` + Tasks UI "Resume".
8. **`ScreenCaptureService` start/stop + `canTakeScreenshot="true"`** (ancillary).
9. **UI redesign** (Chat / Tasks / Task Details / Settings / Diagnostics): final layer on top of the now-correct engine.

All fixes preserve the existing architecture (Koin, Room, MVVM, Compose, real AccessibilityService/WebView/Termux/MCP). No "rebuild from scratch".
