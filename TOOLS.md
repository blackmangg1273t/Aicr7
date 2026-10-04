# Tool Catalog

All tools are real implementations registered in `ToolRegistry` (see AGENTS.md for the
agent→tool permission matrix and how to add tools). Risk levels drive the approval flow:
`SAFE` runs immediately, `MODERATE` runs logged, `HIGH_RISK` requires explicit user approval.

## Web
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `web_search` | `query` (req), `max_results` (default 5) | SAFE | DuckDuckGo HTML endpoint + real parser (unit-tested) |
| `web_fetch` | `url` (req), `max_chars` | SAFE | OkHttp GET + HTML→text extraction |

## Browser (headless WebView — `BrowserEngine`)
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `browser_open` | `url` (req) | SAFE | WebView load + onPageFinished/error latch, real cookie store |
| `browser_read` | `max_chars` | SAFE | JS DOM extraction of URL/title/innerText |
| `browser_click` | `text` (req) | MODERATE | JS: finds visible a/button/[role=button]/input by text → scrollIntoView + click |
| `browser_type` | `matcher`, `value` (req), `submit` | MODERATE | JS: native value setter + input/change events, optional form submit |
| `browser_back` | — | SAFE | WebView history back |
| `browser_evaluate` | `script` (req) | MODERATE | evaluateJavascript with timeout |
| `browser_current_url` | — | SAFE | current URL |

## Device / Android
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `device_info` | — | SAFE | Build, BatteryManager, StorageManager, DisplayMetrics |
| `launch_app` | `package` or `search` | MODERATE | PackageManager launch intent + name→package lookup |
| `open_url` | `url` (req) | SAFE | ACTION_VIEW with http/https validation |
| `take_screenshot` | — | MODERATE | MediaProjection consent (ActivityResult) → virtual display → ImageReader → PNG |

## Android UI automation (requires the user to enable the Accessibility service)
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `android_ui_status` | — | SAFE | Service binding + Settings.Secure check |
| `ui_tap` | `x`, `y` | MODERATE | dispatchGesture click stroke |
| `ui_click_text` | `text` (req) | MODERATE | node tree search → ACTION_CLICK → gesture fallback; auto screen-snapshot verification |
| `ui_type` | `text` (req), `hint` | MODERATE | find editable → ACTION_SET_TEXT; honest failure when app refuses |
| `ui_scroll` | `direction` | SAFE | real swipe gesture |
| `ui_back` / `ui_home` | — | MODERATE/SAFE | GLOBAL_ACTION_BACK / HOME |
| `ui_snapshot` | — | SAFE | structured dump of visible elements (id/bounds/text/flags) |

## Terminal & files
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `shell_exec` | `command` (req), `cwd`, `timeout_ms` | **HIGH_RISK** | Termux RUN_COMMAND (stdout/stderr/exit_code result files) with app-sandbox `sh` fallback; `CommandGuard` pre-check; real timeout kill |
| `file_read` | `path` (req) | SAFE | workspace sandbox, size cap 2MB |
| `file_write` | `path`, `content` (req) | MODERATE | workspace sandbox, mkdirs |
| `file_list` | `path` | SAFE | workspace listing |
| `file_delete` | `path` (req) | **HIGH_RISK** | workspace sandbox delete |

## Coding
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `git` | `action`: status\|log\|diff\|clone\|commit, `repo`, `url`, `message`, `count` | MODERATE | shell git via Termux; without git binary returns setup guidance (no faking) |

## Memory (Room-backed)
| Tool | Args | Risk | Implementation |
|---|---|---|---|
| `memory_store` | `key`, `content` (req), `category` | MODERATE | SQLite upsert |
| `memory_search` | `query` (req), `limit` | SAFE | LIKE search ordered by recency |
| `memory_delete` | `key` (req) | **HIGH_RISK** | real delete with count |

## MCP (dynamic)
| Tool name pattern | Risk | Implementation |
|---|---|---|
| `mcp_<server>_<tool>` | MODERATE | real JSON-RPC 2.0 `tools/call` to the configured server; tools discovered via `tools/list` at connect time |

## Validation pipeline (every call)

1. Planner-provided name resolved in `ToolRegistry` (unknown → failure, never guessed).
2. Agent allowlist enforced (`allowedTools()` prefixes).
3. `HIGH_RISK` → `ApprovalManager` user gate (chat approval card) **before** execution.
4. `CommandGuard` destructive-pattern check for shell commands.
5. Input validation via `Args` helpers (missing/invalid → explicit error).
6. Execution on `Dispatchers.IO` with timing; exceptions → `ToolResult(success=false, error=…)`.
7. Output persisted on the step and logged (secrets redacted).
