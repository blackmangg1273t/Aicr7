# COMPONENTS TO BE BUILT FROM SCRATCH — ANDROID MULTI-AGENT OS

While open-source libraries provide foundational primitives (e.g., JGit for Git operations, Room for SQLite, PRoot for syscall emulation, and OpenCode for coding heuristics), **no turnkey "Android Multi-Agent OS" exists in the source pack**. 

The following 14 major components do not exist in the collected repositories and must be engineered from scratch.

---

## 1. Native Android Jetpack Compose UI & Shell
- **Description:** A Material Design 3 interface built specifically for multi-agent workflows.
- **Key Modules Needed:**
  - Multi-agent interactive conversation timeline showing hierarchical agent delegation cards.
  - Collapsible real-time execution inspector showing tool inputs, stdout/stderr streams, and AST diffs.
  - Floating PiP (Picture-in-Picture) automation overlay for monitoring on-screen phone automation gestures.
  - Embedded PTY terminal viewer with virtual keyboard shortcuts (Ctrl, Alt, Tab, Esc).
  - Split-pane List-Detail navigation for foldables, tablets, and Samsung DeX.

## 2. Main Agent Orchestration Engine (`AgentOrchestrator`)
- **Description:** Hierarchical state machine written in Kotlin Coroutines.
- **Key Modules Needed:**
  - Natural language task decomposer generating Directed Acyclic Graphs (DAGs) of subtasks.
  - Execution coordinator handling concurrent sub-agent scheduling, timeout cancellation, and dependency propagation.
  - Context window manager that condenses intermediate step logs to prevent LLM token exhaustion.
  - Self-healing retry engine that handles tool exceptions and invokes alternate fallback tools.

## 3. Native Kotlin Model Context Protocol (MCP) Client
- **Description:** A native Kotlin implementation of the official Model Context Protocol 2024-11-05 standard.
- **Key Modules Needed:**
  - Schema discovery and dynamic JSON-RPC 2.0 dispatching.
  - Transport abstraction layer supporting `InProcessTransport` (Kotlin Channel), `SseClientTransport` (Ktor), and `UnixSocketTransport` (LocalSocket).
  - Bidirectional streaming result collector.

## 4. Multi-Tier Security & Risk Interceptor
- **Description:** A centralized security gating module that intercepts every MCP tool call before execution.
- **Key Modules Needed:**
  - Risk categorization engine (Read-Only vs. Sensitive vs. Destructive).
  - Jetpack Compose permission modal with asynchronous coroutine continuation.
  - Per-session and permanent approval rules stored in encrypted preferences.
  - Blacklist guard blocking dangerous commands (`rm -rf /`, formatting commands, or unauthorized data exfiltration).

## 5. Unified Model Provider & Router (`ModelRouter`)
- **Description:** High-performance, zero-overhead Kotlin router supporting multiple LLM backends without relying on heavy Python (LiteLLM) or Node.js (OmniRoute) servers.
- **Key Modules Needed:**
  - Universal request normalizer converting agent prompts and tool definitions into provider-specific schemas (Google Gemini, OpenAI format, Qwen / Anthropic).
  - Ktor-based streaming SSE parser with token-by-token UI emission.
  - Dynamic fallback mechanism (e.g., automatically falling back from Gemini Pro to Gemini Flash or Qwen on rate limits).
  - Android Keystore AES-256 encrypted credential store.

## 6. Headless WebView Automation Bridge
- **Description:** Kotlin controller that drives Android's native WebView as a headless browser agent.
- **Key Modules Needed:**
  - Injected JavaScript runtime bridging DOM nodes to the AgentOS MCP tool interface.
  - Dynamic viewport screenshot capture rendering into memory bitmaps without displaying on screen.
  - PageKit pipeline integration for extracting readable Markdown from loaded pages.
  - Cookie and session manager allowing authenticated browsing tasks.

## 7. NeuralBridge Accessibility-to-MCP Adapter
- **Description:** Glue layer transforming raw Android Accessibility events into structured MCP tool operations.
- **Key Modules Needed:**
  - Accessibility tree sanitizer that prunes invisible layout nodes and assigns numeric target IDs to interactive buttons.
  - Gesture builder translating bounding boxes into smooth, human-like Bezier swipe and tap paths.
  - Screen capture synchronizer pairing accessibility node trees with exact frame timestamps.

## 8. Companion Process Lifecycle & Daemon Manager
- **Description:** Android Service manager controlling the lifecycle of background companion processes (TermFold / OpenCode).
- **Key Modules Needed:**
  - Automatic startup, port binding, and health checks on `http://127.0.0.1:4096`.
  - Mutual loopback authentication handshake (`X-AgentOS-Token`).
  - Android Low Memory Killer (LMK) protection using Foreground Service notifications with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.

## 9. Memory Provider Abstraction & Vector Bridge
- **Description:** Unified memory layer combining Room relational storage with the ZVec C++ HNSW vector index.
- **Key Modules Needed:**
  - JNI bindings bridging Kotlin `FloatArray` embeddings to `libzvec.so`.
  - Room DAO schema storing conversation turns, user preferences, and project-specific knowledge.
  - Hybrid retrieval algorithm combining SQLite full-text search (FTS5) with cosine vector similarity.

## 10. Workspace & File System Bridge
- **Description:** High-speed storage management layer complying with Android 10+ Scoped Storage rules.
- **Key Modules Needed:**
  - Internal sandbox file manager for Git repositories and build artifacts.
  - Storage Access Framework (SAF) document tree adapter for external import/export.
  - Virtual file tree observer streaming file changes to the UI.

## 11. Git UI & Workspace Synchronization
- **Description:** UI and orchestration bindings around JGit.
- **Key Modules Needed:**
  - Visual Git status, branch switcher, and side-by-side visual diff inspector.
  - SSH key pair generator and credential helper.
  - Conflict resolution dialogs for automated coding merges.

## 12. Vision Grounding & Coordinate Normalizer
- **Description:** Computer vision pipeline mapping multimodal model outputs to Android physical screen coordinates.
- **Key Modules Needed:**
  - Screen resolution and display density scaler (handling notch insets, navigation bars, and orientation changes).
  - Grounding box parser translating normalized `[ymin, xmin, ymax, xmax]` coordinates into physical pixel tap points.

## 13. System Setup & Onboarding Wizard
- **Description:** First-run onboarding flow guiding users through Android permission requirements.
- **Key Modules Needed:**
  - Step-by-step Accessibility Service enabler with deep links to Android Settings.
  - Screen capture (MediaProjection) permission launcher.
  - Battery optimization exclusion prompt (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
  - API key configuration wizard.

## 14. Unified Diagnostics & Logging Console
- **Description:** Developer and audit console tracking all agent operations, sub-agent handoffs, and tool executions.
- **Key Modules Needed:**
  - Circular in-memory event buffer with Room persistence.
  - Real-time log export (JSON / plain text) for debugging.
