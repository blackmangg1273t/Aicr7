# MODEL CONTEXT PROTOCOL (MCP) ARCHITECTURE — ANDROID MULTI-AGENT OS

## Architectural Overview

The Model Context Protocol (MCP) serves as the universal integration bus for AgentOS. Instead of tightly coupling agents to hardware APIs, Linux processes, and browser runtimes, all tools are declared, discovered, and invoked through standardized JSON-RPC 2.0 MCP interfaces conforming to the official Model Context Protocol 2024-11-05 specification.

```
┌────────────────────────────────────────────────────────┐
│                   MAIN AGENT CORE                      │
│             (Kotlin Coroutines / Flow)                 │
└──────────────────────────┬─────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────┐
│                   MCP CLIENT ENGINE                    │
│   - Connection Pool & Transport Adapters               │
│   - Security & Permission Interceptor                  │
│   - Tool Registry & Schema Aggregator                  │
│   - Structured Result Parser                           │
└─────┬────────────────────┬────────────────────┬────────┘
      │ In-Process         │ Localhost HTTP/SSE │ Unix Socket / Intent
      ▼                    ▼                    ▼
┌──────────────┐   ┌──────────────┐     ┌──────────────┐
│  EMBEDDED    │   │  COMPANION   │     │  EXTERNAL    │
│  MCP SERVERS │   │  MCP SERVERS │     │  MCP SERVERS │
│              │   │              │     │              │
│ - Aster MCP  │   │ - OpenCode   │     │ - Termux     │
│   (Phone)    │   │   Coding     │     │   Daemon     │
│ - WebView    │   │   Server     │     │   (Linux)    │
│   Browser    │   │              │     │              │
│ - JGit Core  │   │              │     │              │
│ - Room/ZVec  │   │              │     │              │
│   Memory     │   │              │     │              │
└──────────────┘   └──────────────┘     └──────────────┘
```

---

## 1. MCP Transports on Android

Android poses unique networking and IPC constraints (e.g., lack of standard POSIX anonymous pipes across app sandboxes, SELinux policies blocking unauthorized Unix sockets, and system-level sleep/doze modes killing background TCP connections).

AgentOS implements three primary transport adapters:

### A. In-Process Coroutine Transport (`InProcessTransport`)
- **Use Case:** High-speed communication with internal Kotlin/JVM engines (Phone Automation via Aster MCP / NeuralBridge, Embedded Browser via WebView, Git via JGit, Memory via Room/ZVec).
- **Implementation:** Kotlin `Channel<McpMessage>` (buffered coroutine channels).
- **Latency:** < 0.1ms per invocation.
- **Overhead:** Zero serialization serialization penalty; objects are passed directly as typed Kotlin data structures or lightweight memory buffers.

### B. Localhost HTTP/SSE Transport (`SseClientTransport`)
- **Use Case:** Communication with the OpenCode Coding Agent companion daemon.
- **Implementation:** Ktor HTTP Client connecting to `http://127.0.0.1:4096/sse` and `POST /message`.
- **Security:** Each session generates a cryptographically secure 256-bit loopback bearer token (`X-AgentOS-Token`) written to a protected local shared storage file or passed via process launch arguments.
- **Streaming:** Server-Sent Events (SSE) stream tool progress, streaming code generation, and AST diff events directly into the Android UI.

### C. Android IPC / Socket Transport (`UnixSocketTransport`)
- **Use Case:** Communication with Termux background runtime.
- **Implementation:** Android LocalSocket (`android.net.LocalSocket`) connected to an abstract Linux domain socket, or fallback via Android `Intent` (`com.termux.RUN_COMMAND`) with a PendingIntent callback.

---

## 2. Security, Authentication & User Permission Interceptor

Every MCP tool invocation passes through the **MCP Permission Interceptor** before dispatch:

```kotlin
enum class RiskLevel {
    READ_ONLY,    // Safe: Execute immediately (e.g., search.web, memory.retrieve, git.status)
    MODERATE,     // Safe with audit: (e.g., browser.click, phone.tap, terminal.read_file)
    HIGH_RISK     // Explicit User Approval Required (e.g., terminal.exec rm, git.push, phone.launch_app)
}
```

If a tool is flagged as `HIGH_RISK`:
1. The orchestrator suspends the calling agent's coroutine.
2. A Jetpack Compose confirmation sheet is displayed to the user showing the tool name, target parameters, and an explanation of why the agent requested it.
3. The user can **Approve Once**, **Approve for Session**, or **Deny**.
4. If denied, the MCP client returns an explicit error to the agent: `{"error": "User rejected tool execution: [reason]"}`.

---

## 3. Tool Specifications & Schemas

### Subsystem A: Phone Automation (`phone.*`)
*Provided by: Aster MCP / NeuralBridge (Android AccessibilityService + MediaProjection)*

```json
{
  "name": "phone.tap",
  "description": "Performs a simulated finger tap at coordinate (x, y) or on an accessibility element ID.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "x": { "type": "integer", "description": "X coordinate in screen pixels" },
      "y": { "type": "integer", "description": "Y coordinate in screen pixels" },
      "element_id": { "type": "string", "description": "Optional Android View Resource ID" }
    }
  }
}
```

```json
{
  "name": "phone.swipe",
  "description": "Performs a swipe gesture across the screen.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "start_x": { "type": "integer" },
      "start_y": { "type": "integer" },
      "end_x": { "type": "integer" },
      "end_y": { "type": "integer" },
      "duration_ms": { "type": "integer", "default": 300 }
    },
    "required": ["start_x", "start_y", "end_x", "end_y"]
  }
}
```

```json
{
  "name": "phone.type",
  "description": "Enters text into the currently focused Android input field.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "text": { "type": "string" },
      "press_enter": { "type": "boolean", "default": false }
    },
    "required": ["text"]
  }
}
```

```json
{
  "name": "phone.back",
  "description": "Triggers the Android system Back navigation action.",
  "inputSchema": { "type": "object", "properties": {} }
}
```

```json
{
  "name": "phone.home",
  "description": "Triggers the Android system Home navigation action.",
  "inputSchema": { "type": "object", "properties": {} }
}
```

```json
{
  "name": "phone.launch_app",
  "description": "Launches an installed Android app by package name or activity.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "package_name": { "type": "string", "example": "com.android.chrome" }
    },
    "required": ["package_name"]
  }
}
```

```json
{
  "name": "phone.screenshot",
  "description": "Captures the current active screen via MediaProjection.",
  "inputSchema": { "type": "object", "properties": {} }
}
```

---

### Subsystem B: Browser Agent (`browser.*`)
*Provided by: Headless Android WebView with injected JavaScript bridge*

```json
{
  "name": "browser.open",
  "description": "Navigates the internal browser to a given URL.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "url": { "type": "string" }
    },
    "required": ["url"]
  }
}
```

```json
{
  "name": "browser.search",
  "description": "Navigates to search engine query.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string" }
    },
    "required": ["query"]
  }
}
```

```json
{
  "name": "browser.click",
  "description": "Clicks on an element matching a CSS selector or XPath.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "selector": { "type": "string" }
    },
    "required": ["selector"]
  }
}
```

```json
{
  "name": "browser.type",
  "description": "Types text into a form element in the browser.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "selector": { "type": "string" },
      "text": { "type": "string" }
    },
    "required": ["selector", "text"]
  }
}
```

```json
{
  "name": "browser.scroll",
  "description": "Scrolls the browser viewport.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "direction": { "type": "string", "enum": ["up", "down", "top", "bottom"] },
      "amount": { "type": "integer", "default": 500 }
    },
    "required": ["direction"]
  }
}
```

```json
{
  "name": "browser.read",
  "description": "Extracts the readable Markdown content and clean DOM tree from the current page via PageKit.",
  "inputSchema": { "type": "object", "properties": {} }
}
```

```json
{
  "name": "browser.screenshot",
  "description": "Captures a bitmap of the browser viewport.",
  "inputSchema": { "type": "object", "properties": {} }
}
```

---

### Subsystem C: Terminal & Linux Execution (`terminal.*`)
*Provided by: TermFold (Embedded PRoot) / Termux Companion*

```json
{
  "name": "terminal.exec",
  "description": "Executes a shell command in the Linux environment.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "command": { "type": "string" },
      "cwd": { "type": "string", "description": "Working directory" },
      "timeout_ms": { "type": "integer", "default": 30000 }
    },
    "required": ["command"]
  }
}
```

```json
{
  "name": "terminal.read_file",
  "description": "Reads content from a file in the workspace.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "path": { "type": "string" },
      "offset": { "type": "integer", "default": 0 },
      "limit": { "type": "integer", "default": 2000 }
    },
    "required": ["path"]
  }
}
```

```json
{
  "name": "terminal.write_file",
  "description": "Writes or updates content to a file.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "path": { "type": "string" },
      "content": { "type": "string" }
    },
    "required": ["path", "content"]
  }
}
```

---

### Subsystem D: Git Engine (`git.*`)
*Provided by: JGit (Java Native)*

```json
{
  "name": "git.status",
  "description": "Returns modified, untracked, and staged files in the current repository.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "repo_path": { "type": "string" }
    },
    "required": ["repo_path"]
  }
}
```

```json
{
  "name": "git.diff",
  "description": "Generates a unified diff of unstaged or staged changes.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "repo_path": { "type": "string" },
      "cached": { "type": "boolean", "default": false }
    },
    "required": ["repo_path"]
  }
}
```

```json
{
  "name": "git.commit",
  "description": "Stages all changes and creates a new Git commit.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "repo_path": { "type": "string" },
      "message": { "type": "string" },
      "author_name": { "type": "string" },
      "author_email": { "type": "string" }
    },
    "required": ["repo_path", "message"]
  }
}
```

```json
{
  "name": "git.push",
  "description": "Pushes local commits to the remote Git repository.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "repo_path": { "type": "string" },
      "remote": { "type": "string", "default": "origin" },
      "branch": { "type": "string", "default": "main" }
    },
    "required": ["repo_path"]
  }
}
```

---

### Subsystem E: Search Engine (`search.*`)
*Provided by: Sifthound / PageKit + Public REST Search Endpoints*

```json
{
  "name": "search.web",
  "description": "Performs a web search across search engines and returns normalized top results.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string" },
      "max_results": { "type": "integer", "default": 5 }
    },
    "required": ["query"]
  }
}
```

```json
{
  "name": "search.fetch",
  "description": "Fetches a web URL, parses HTML, strips junk, and returns clean Markdown.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "url": { "type": "string" }
    },
    "required": ["url"]
  }
}
```

---

### Subsystem F: Memory Engine (`memory.*`)
*Provided by: Room (Metadata) + ZVec (HNSW Vector Index)*

```json
{
  "name": "memory.store",
  "description": "Stores a long-term memory item with key, category, text content, and optional vector embedding.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "key": { "type": "string" },
      "category": { "type": "string", "enum": ["preference", "fact", "project", "agent_state"] },
      "content": { "type": "string" }
    },
    "required": ["key", "content"]
  }
}
```

```json
{
  "name": "memory.retrieve",
  "description": "Retrieves memories matching a query by semantic similarity or exact key lookup.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string" },
      "category": { "type": "string" },
      "limit": { "type": "integer", "default": 5 }
    },
    "required": ["query"]
  }
}
```

```json
{
  "name": "memory.delete",
  "description": "Deletes a stored memory item.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "key": { "type": "string" }
    },
    "required": ["key"]
  }
}
```

---

## 4. MCP Tool Result Format

All tools return standard JSON-RPC results:
```json
{
  "jsonrpc": "2.0",
  "id": "req-10492",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Successfully tapped coordinates (540, 1120). Target app 'com.android.chrome' launched."
      },
      {
        "type": "image",
        "data": "iVBORw0KGgoAAAANSUhEUgAA...",
        "mimeType": "image/webp"
      }
    ],
    "isError": false
  }
}
```
