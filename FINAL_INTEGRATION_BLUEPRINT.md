# FINAL INTEGRATION BLUEPRINT — ANDROID MULTI-AGENT OS

## Architectural Blueprint & System Specifications

This blueprint defines the engineering architecture for combining the verified components into a single, cohesive, production-grade Android Multi-Agent AI Operating System (`AgentOS`).

Target Environment:
- **Target OS:** Android 9.0+ (API Level 28 through API Level 35)
- **Architecture:** ARM64 priority (`arm64-v8a`)
- **Root Requirement:** STRICTLY ZERO ROOT REQUIRED
- **Language / UI Framework:** Kotlin 2.0+ / Jetpack Compose / Material 3

---

## 1. Top-Level Architectural Topology

```
┌────────────────────────────────────────────────────────────────────────┐
│                   PRIMARY NATIVE APPLICATION (APK)                     │
│                        Package: com.agentos.app                        │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │             Jetpack Compose Adaptive UI Layer (M3)               │  │
│  │   - Multi-Agent Workspace & Timeline Chat                        │  │
│  │   - Live Screen Automation Inspection Overlay                    │  │
│  │   - Embedded Headless / Interactive Browser Sheet                │  │
│  │   - Built-in Virtual Terminal (PTY View)                         │  │
│  │   - Human-in-the-Loop Permission & Confirmation Prompts          │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │                                  │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                 MAIN AGENT ORCHESTRATOR CORE                     │  │
│  │   - Intent Parser & Task Decomposer                              │  │
│  │   - Sub-Agent Dispatcher (Parallel / Sequential Co-routines)     │  │
│  │   - Context Window Condenser & AST Diff Validator                │  │
│  │   - Execution State Machine & Checkpoint Recovery                │  │
│  └──────┬──────────────┬──────────────┬──────────────┬──────────────┘  │
│         │              │              │              │                 │
│         ▼              ▼              ▼              ▼                 │
│    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐            │
│    │ PHONE   │    │ BROWSER │    │ SEARCH  │    │ VISION  │            │
│    │ AGENT   │    │ AGENT   │    │ AGENT   │    │ AGENT   │            │
│    └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘            │
│         │              │              │              │                 │
│         └──────────────┼──────────────┼──────────────┘                 │
│                        ▼                                               │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                      MCP CLIENT & BUS LAYER                      │  │
│  │   - Unified Schema Registry                                      │  │
│  │   - Security & Risk Policy Interceptor                           │  │
│  │   - Structured Tool Execution Pipeline                           │  │
│  └──────┬───────────────────────────────┬───────────────────────────┘  │
│         │ In-Process Channels           │ Localhost Loopback HTTP/SSE  │
│         ▼                               ▼                              │
│  ┌───────────────────────────────┐      │                              │
│  │ INTERNAL TOOL ENGINES         │      │                              │
│  │ - NeuralBridge Accessibility  │      │                              │
│  │ - MediaProjection Display     │      │                              │
│  │ - Headless WebView Engine     │      │                              │
│  │ - JGit 6.9+ Engine            │      │                              │
│  │ - Room & ZVec Memory Store    │      │                              │
│  │ - Native Model Router         │      │                              │
│  └───────────────────────────────┘      │                              │
└─────────────────────────────────────────┼──────────────────────────────┘
                                          │
                                          ▼ Localhost HTTP / SSE (Port 4096)
┌────────────────────────────────────────────────────────────────────────┐
│             COMPANION RUNTIME (TermFold / Termux Daemon)               │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                OpenCode Autonomous Coding Daemon                 │  │
│  │   - Multi-file Context Indexer & Tree-sitter AST                 │  │
│  │   - Code Generation, Patch & Diff Applicator                     │  │
│  │   - Headless HTTP / SSE JSON-RPC Server                          │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
│                                     │ Subprocess Fork / Exec           │
│                                     ▼                                  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │               Linux / POSIX Userspace Environment                │  │
│  │   - Node.js 20+ runtime & Bun                                    │  │
│  │   - Python 3.11+, Clang, Make, Test Run (pytest, jest)           │  │
│  │   - Isolated Workspace / Filesystem Sandbox                      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Specifications & Protocols

### 2.1 Android Application & UI Shell
- **Class / Entry Point:** `com.agentos.MainActivity`
- **UI Framework:** Jetpack Compose with Material 3 Design System.
- **Window Size Classes:** Full adaptive support for Compact (phones), Medium (foldables), and Expanded (tablets / DeX) with split-pane List-Detail navigation.
- **State Management:** Single-source-of-truth `MainViewModel` observing Kotlin `StateFlow<AgentUiState>` from the orchestrator.

### 2.2 Main Agent Orchestrator
- **Package:** `com.agentos.core.orchestrator`
- **Engine:** Kotlin Coroutine-based hierarchical state machine (`AgentOrchestrator`).
- **Core Responsibilities:**
  1. Receives natural language user prompts.
  2. Uses the **Model Router** with structured function-calling schemas to decompose tasks into a DAG (Directed Acyclic Graph) of subtasks.
  3. Dispatches subtasks to specialized agents:
     - Independent tasks execute in parallel via `async { ... }` coroutines.
     - Dependent tasks execute sequentially, passing state and outputs through `TaskExecutionContext`.
  4. Observes tool results from the MCP client, checks for errors, and triggers auto-retries (up to 3 attempts with exponential backoff).
  5. Pauses execution and surfaces a high-priority UI prompt when an action requires explicit human confirmation.
  6. Synthesizes a unified final markdown response with logs and generated artifacts.

### 2.3 Sub-Agent Specializations
- **Phone Agent (`com.agentos.agents.phone`):**
  - **Tool Provider:** NeuralBridge + Aster MCP.
  - **Mechanisms:** Android `AccessibilityService` (`AccessibilityNodeInfo` tree traversal, synthetic gestures via `dispatchGesture`) + `MediaProjection` for screen capture.
  - **Non-Root Operation:** Runs entirely via standard Android OS accessibility permissions. Zero root or ADB required.
- **Browser Agent (`com.agentos.agents.browser`):**
  - **Tool Provider:** Embedded Android `WebView` running in a headless `Service` or dynamic bottom sheet.
  - **Mechanisms:** Injected JavaScript bridge via `evaluateJavascript()`, DOM tree cleaning via PageKit, screenshot generation via `WebView.draw(Canvas)`.
  - **Advantage:** Low RAM consumption (<80MB vs 2GB for desktop Chromium Playwright), 100% Android 9+ compatibility.
- **Coding Agent (`com.agentos.agents.coding`):**
  - **Tool Provider:** OpenCode Headless Daemon.
  - **Mechanisms:** Managed companion process running Node.js in the TermFold/Termux environment.
  - **Communication:** Loopback HTTP/SSE on `http://127.0.0.1:4096` with `X-AgentOS-Token` authentication.
  - **Features:** Automated code reading, project tree mapping, AST edits, diff generation, test execution (`npm test`, `pytest`), and test verification.
- **Search Agent (`com.agentos.agents.search`):**
  - **Tool Provider:** Sifthound / PageKit + REST search queries (DuckDuckGo, Brave, Google).
  - **Mechanisms:** HTTPS query execution with PageKit JVM HTML-to-Markdown summarization.
- **Vision Agent (`com.agentos.agents.vision`):**
  - **Tool Provider:** Cloud Multimodal LLM (Gemini 2.5/Flash) + UI-Venus ONNX/LiteRT fallback.
  - **Pipeline:** Screen bitmap capture -> downscale to 1080p -> encode WebP -> Grounding detection -> bounding box coordinate translation for Phone Agent taps.
- **Memory Agent (`com.agentos.agents.memory`):**
  - **Tool Provider:** Room Database (Conversations, Preferences, Tasks) + ZVec NDK (HNSW semantic embeddings).

### 2.4 MCP Layer (Model Context Protocol)
- **Package:** `com.agentos.mcp`
- **Standard:** MCP 2024-11-05 JSON-RPC 2.0.
- **Client Implementation:** Kotlin Coroutine-based MCP Client supporting:
  - `InProcessTransport`: Direct Kotlin Channel dispatch for zero-overhead internal tools.
  - `SseClientTransport`: Ktor HTTP/SSE client for companion daemons (OpenCode).
  - `UnixSocketTransport`: Domain socket bridge for Termux.
- **Permission Interceptor:** Every tool call is evaluated against a risk policy. Read-only tools execute automatically; state-modifying or external tools require user confirmation.

### 2.5 Embedded Git Engine
- **Package:** `com.agentos.git`
- **Library:** JGit 6.9+ (Eclipse Distribution License - BSD equivalent).
- **Capabilities:** Clone, fetch, pull, status, diff, branch, checkout, commit, push.
- **Credentials:** Secure SSH key generation (ed25519) and HTTPS Personal Access Tokens stored in `EncryptedSharedPreferences`.
- **Licensing Safety:** Pure JVM bytecode. Zero NDK compilation errors and zero GPL contamination.

### 2.6 File System & Workspace Storage
- **Workspace Directory:** App-internal private storage (`/data/user/0/com.agentos.app/files/workspace`) for maximum performance and security isolation.
- **External Export:** Android Storage Access Framework (`DocumentFile` / `Intent.ACTION_OPEN_DOCUMENT_TREE`) to import/export projects from device storage without requiring dangerous storage permissions.

---

## 3. Communication Protocols & Data Flow Matrix

| Source Component | Protocol / Transport | Destination Component | Data Format | Security / Auth |
|---|---|---|---|---|
| **UI (Compose)** | Kotlin StateFlow | **Main Orchestrator** | Typed Kotlin Data Classes | In-Process Memory |
| **Main Orchestrator** | Coroutine Channels | **MCP Client** | `McpMessage.Request` | In-Process Memory |
| **MCP Client** | Direct JNI / Method Call | **NeuralBridge (Phone)** | Kotlin Coroutine / JSON-RPC | App Signature |
| **MCP Client** | `evaluateJavascript` | **WebView (Browser)** | JSON DOM / Events | Sandbox Context |
| **MCP Client** | Localhost HTTP / SSE | **OpenCode Daemon** | JSON-RPC 2.0 over SSE | Loopback Bearer Token |
| **MCP Client** | Kotlin Method Call | **JGit Engine** | Git Command Objects | In-Process Memory |
| **MCP Client** | Room DAO / JNI | **Memory Store (Room/ZVec)** | SQLite / FloatArray Vector | App Sandbox |
| **Model Router** | HTTPS / TLS 1.3 | **Cloud LLM (Gemini/Qwen/GLM)**| JSON SSE Streaming | Encrypted API Key |
| **Model Router** | C++ NDK / OpenCL | **LiteRT-LM (Local AI)** | Direct Tensor Memory Buffers| On-Device Memory |

---

## 4. End-to-End Real Task Workflows

### Task Workflow 1: Complex Multi-Agent Task
**User Prompt:** *"Open Chrome, search for a GitHub repository, inspect its README, download the repository, open it in the coding workspace, analyze the project, modify a file, run tests, and report the result."*

```
Step 1: Main Orchestrator decomposes task into 4 stages:
        [Stage A: Browser/Phone] -> [Stage B: Git Clone] -> [Stage C: OpenCode Edit] -> [Stage D: Test & Report]

Step 2: Stage A (Web Navigation):
        - Orchestrator invokes Browser Agent via `browser.open("https://github.com/search?q=...")`
        - Browser Agent loads page, executes `browser.read()` via PageKit.
        - Extracted Markdown parsed; target repository clone URL obtained: `https://github.com/example/repo.git`.

Step 3: Stage B (Workspace Initialization & Git):
        - Orchestrator invokes Git Tool via `git.clone(url="https://github.com/example/repo.git", path="/workspace/repo")`.
        - JGit executes clone inside the private workspace directory.
        - `git.status` verifies working tree clean.

Step 4: Stage C (Coding Agent Analysis & Edits):
        - Orchestrator sends task to OpenCode Daemon via Localhost SSE:
          `{"task": "Analyze README, find target bug in main module, update logic"}`.
        - OpenCode indexes workspace AST, generates file diff.
        - MCP Interceptor detects file modification request:
          Surfaces Compose Dialog: *"OpenCode wants to modify src/main.py (+12, -4). Approve?"*
        - User taps "Approve". OpenCode applies the patch.

Step 5: Stage D (Verification & Testing):
        - OpenCode executes `terminal.exec(cmd="npm test" or "pytest", cwd="/workspace/repo")` inside TermFold.
        - Test results streamed back: `5 passed, 0 failed`.
        - JGit creates commit: `git.commit(message="Fix bug in main module")`.

Step 6: Final Response:
        - Orchestrator synthesizes diff summary, test logs, and Git commit hash, presenting them in the chat UI.
```

### Task Workflow 2: Bug Fix Autonomous Pipeline
**User Prompt:** *"Fix the NullPointerException in user profile loading."*

```
Main Agent 
  → Decomposes query 
  → Invokes Coding Agent (OpenCode)
  → OpenCode reads stack trace & scans AST via `terminal.read_file`
  → Identifies missing null check in `ProfileService.kt`
  → Generates unified diff patch
  → File System writes patch to disk
  → Executes `./gradlew test` via Terminal Tool in TermFold
  → Tests pass (Exit Code 0)
  → JGit stages and commits change
  → Main Agent summarizes fix and shows side-by-side diff in Compose UI.
```

### Task Workflow 3: Android Device Interaction
**User Prompt:** *"Open Settings and enable Dark Mode."*

```
Main Agent 
  → Invokes Phone Agent
  → Phone Agent calls `phone.launch_app(package_name="com.android.settings")`
  → Android AccessibilityService brings Settings to foreground
  → Phone Agent calls `phone.screenshot()` via MediaProjection
  → Vision Agent locates "Display" menu item coordinates (x: 540, y: 820)
  → Phone Agent calls `phone.tap(x=540, y=820)`
  → New screenshot taken -> Vision Agent locates "Dark theme" switch
  → Phone Agent calls `phone.tap(x=920, y=450)`
  → Accessibility node state confirms `isChecked = true`
  → Main Agent reports completion: "Dark mode has been enabled."
```

---

## 5. Security Architecture & Threat Model

1. **SELinux & Process Sandboxing:** All operations run within the standard Android non-root application sandbox (`UID: 10xxx`).
2. **Dynamic Execution & W^X Compliance:** On Android 10+, executing downloaded binaries directly from `/data/data` is blocked by SELinux. All native binaries (`libproot.so`, `libzvec.so`) are distributed as native shared libraries inside the APK's `lib/arm64-v8a/` directory and loaded via `System.loadLibrary()`.
3. **No Dynamic Code Loading (DCL):** Avoids runtime `.dex` or `.jar` loading, guaranteeing 100% compliance with Google Play Store Developer Program policies.
4. **Credential Isolation:** API keys (Gemini, OpenCode tokens, Git SSH keys) are encrypted using AES-256-GCM backed by the Android Keystore Hardware Security Module (HSM).
5. **Human-in-the-Loop Safeguard:** Any destructive tool execution (`terminal.exec rm`, `git.push`, `phone.tap` in banking/financial apps) triggers a mandatory modal prompt.
