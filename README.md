# AgentOS — Personal AI Multi-Agent Operating Assistant for Android

AgentOS is a real (no fake features) Android app that lets you talk to one **Main Agent**, which
understands your request, plans it with an AI model you configure, delegates steps to specialized
agents (Research, Browser, Android, Terminal, Coding), executes **real tools** (web search, real
browser automation, real Android UI automation, real shell execution, files, git, memory, MCP),
requires your approval for risky actions, and streams the final answer back to you.

> Every feature in this repository is actually implemented. Where a capability has hard platform
> limits, the app fails honestly with an actionable message instead of simulating success. See
> **Known limitations** and the in-repo `VERIFICATION_REPORT.md`.

## What is the app?

- **Chat screen**: talk to the Main Agent. While a task runs you see live agent activity
  ("Searching the web…", "Opening example.com…"), tool results, errors and approval requests.
- **Tasks screen**: full history of every task with per-step status, tool, arguments, results,
  errors and durations.
- **Terminal screen**: direct real shell access (Termux when installed, app sandbox otherwise).
- **Settings screen**: AI providers (add any OpenAI-compatible or Gemini endpoint + encrypted API
  keys), agent toggles, browser settings, accessibility status, Termux test, MCP servers, privacy
  controls and a live log viewer (secrets auto-redacted).

## Architecture (summary)

```
UI (Jetpack Compose, 4 screens)
   ↓
ChatViewModel / TasksViewModel / TerminalViewModel / SettingsViewModel (MVVM)
   ↓
TaskEngine  ── planning (AI) → approval (user) → execution → synthesis (AI, streamed)
   ↓                ↓
AgentRegistry   ApprovalManager
   ↓
Agents: MainAgent · ResearchAgent · BrowserAgent · AndroidAgent · TerminalAgent · CodingAgent
   ↓
ToolRegistry (validated, risk-classified, logged)
   ↓
Real implementations: WebView engine · Accessibility service · Termux bridge · OkHttp · Room
```

Full details: [ARCHITECTURE.md](ARCHITECTURE.md) · Agent contract: [AGENTS.md](AGENTS.md) ·
Tool catalog: [TOOLS.md](TOOLS.md) · Threat model & mitigations: [SECURITY.md](SECURITY.md)

## Setup

1. **Install**: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (Android 9+).
2. **Configure an AI provider** (required): open Settings → AI Providers → pick one → *Set key…*.
   Keys are stored encrypted (Android Keystore), never in the APK or Git.
3. Optional: install **Termux** (see below) for full Linux shell/git; enable **Accessibility** for
   Android UI automation; add **MCP servers** for extra tools.

## Android requirements

- Android 9.0+ (API 28). Compiled against SDK 34.
- Internet permission for AI/web features; accessibility service only if you enable UI automation.

## API configuration

| Provider type | Example base URL | Example model |
|---|---|---|
| Google Gemini (native) | `https://generativelanguage.googleapis.com/v1beta` | `gemini-2.0-flash` |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| GLM (Zhipu) | `https://open.bigmodel.cn/api/paas/v4` | `glm-4-flash` |
| Groq / OpenRouter / Together | respective `/v1` compatible endpoints | per provider |
| Ollama / LM Studio (local) | `http://<device-ip>:11434/v1` | `llama3.1` etc. |

Set a **primary** and optionally a **fallback** provider; on failure the engine retries with the
fallback and reports both errors if neither works. Nothing is ever faked.

## Termux setup (optional, for full shell/git/coding)

1. Install Termux (F-Droid build recommended).
2. In Termux: `mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties`
   then restart Termux once (run `termux-reload-settings`).
3. For git: `pkg install git`.
4. Verify from AgentOS: Settings → Termux → **Test connection** (or the Terminal tab chip).

## MCP setup (optional)

AgentOS speaks **MCP over Streamable HTTP (JSON-RPC 2.0)**: initialize → tools/list → tools/call.

1. Run any MCP server with an HTTP endpoint (e.g. the official TypeScript SDK's streamable HTTP
   transport on your PC, reachable from the phone).
2. Settings → MCP Servers → Add (name, URL, optional auth header) → **Connect**.
3. Discovered tools appear in the registry and the AI planner can call them.

## Browser Agent

Headless WebView: `browser_open`, `browser_read`, `browser_click` (by visible text),
`browser_type` (form fill + submit), `browser_back`, `browser_evaluate` (JS), `browser_current_url`.
Real DOM interactions; see limitations below.

## Accessibility setup (Android Agent)

Settings → Android Automation → Open Accessibility settings → enable **AgentOS Android Agent**.
Then the agent can: launch apps, tap coordinates/click elements by text, type into fields, scroll,
press back/home, and read a structured screen snapshot (`ui_*` tools). Every modifying action still
passes risk gating; high-risk ones require your explicit approval in the chat.

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit + Robolectric tests
./gradlew assembleRelease      # needs your keystore env vars (see DEVELOPMENT.md)
```

## Testing

`./gradlew testDebugUnitTest` covers: plan parsing, the task engine state machine (execution,
failure, approval-deny/approve), tool registry & sandboxing, shell command guard, log redaction,
DuckDuckGo parser, provider SSE clients (MockWebServer), MCP client flow (MockWebServer), Room
repositories.

## Known limitations (honest)

- **Screenshots** need the MediaProjection system consent dialog per session, and the app must be
  in the foreground to request it. On denial the tool fails with a clear message.
- **Accessibility typing** (`SET_TEXT`) is refused by some apps (e.g. banking apps); the agent
  reports the failure instead of pretending.
- **Browser clicking** works on standard DOM elements; heavy JS-SPA sites may behave differently.
  The WebView is headless (not attached to a window) — pixel-perfect rendering is not guaranteed,
  DOM/text/JS are.
- **Git** requires the Termux git binary (`pkg install git`); without it you get setup guidance.
- **DuckDuckGo search** parses the HTML endpoint without an API key; aggressive rate limiting or
  layout changes can degrade results (reported honestly).
- **Termux results** require `allow-external-apps=true`; otherwise commands fail with setup steps.
- Android 9 has no `AccessibilityService.takeScreenshot()` (API 30+); screenshots on Android 9/10
  use MediaProjection consent instead.
