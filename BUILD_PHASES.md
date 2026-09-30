# BUILD PHASES & EXECUTION STRATEGY — ANDROID MULTI-AGENT OS

This document outlines the phased build strategy for implementing the Android Multi-Agent OS. Each phase defines strict prerequisites, deliverables, risk mitigations, automated tests, and exit criteria.

---

## Phase Roadmap Overview

```
Phase 1: Foundation (Android Shell & Theme)
   │
   ▼
Phase 2: Security & Model Router (Cloud LLM streaming)
   │
   ▼
Phase 3: Core Orchestrator & State Flow (Main Agent)
   │
   ▼
Phase 4: Model Context Protocol (MCP Client Engine)
   │
   ▼
Phase 5: Phone Automation (Accessibility & Screen Capture)
   │
   ▼
Phase 6: Browser & Search Subsystems (Headless WebView & PageKit)
   │
   ▼
Phase 7: Git & Memory (JGit, Room, and ZVec)
   │
   ▼
Phase 8: Terminal & Companion Runtime (TermFold / OpenCode)
   │
   ▼
Phase 9: Vision & Multimodal Grounding
   │
   ▼
Phase 10: Human-in-the-Loop Security & Approvals
   │
   ▼
Phase 11: End-to-End Integration & Multi-Agent CUJs
   │
   ▼
Phase 12: Performance Profiling, Memory Optimization & Release Hardening
```

---

## Detailed Phase Specifications

### Phase 1: Foundation — Android Shell, Jetpack Compose UI & Theming
- **Dependencies:** Android Gradle Plugin 8.7+, Compose BOM, Material 3, AndroidX Lifecycle, Navigation Compose.
- **Expected Results:**
  - Responsive, adaptive Material 3 UI supporting compact phones, foldables, and tablets.
  - Multi-agent timeline chat view, sidebar navigation drawer, and expandable tool inspector.
  - Dark / Light dynamic color theming complying with Material Design guidelines.
- **Risks:** Janky UI recomposition on streaming text tokens.
- **Mitigation:** Use `derivedStateOf`, immutable state data classes, and `@Stable` annotations.
- **Tests:** Robolectric UI tests verifying screen rotation, back-stack navigation, and window size class changes.
- **Exit Criteria:** App compiles cleanly, renders UI at 60 FPS, passes all Robolectric UI tests.

### Phase 2: Security, Credential Storage & Model Router
- **Dependencies:** AndroidX Security Crypto (`EncryptedSharedPreferences`), Ktor 2.3+ (Client, CIO, ContentNegotiation, SSE).
- **Expected Results:**
  - Secure hardware-backed storage for API keys (Gemini, Qwen, GLM, DeepSeek).
  - Native Kotlin `ModelRouter` providing unified streaming inference and automatic provider fallback.
- **Risks:** Network dropouts during long LLM responses.
- **Mitigation:** Ktor retry policies and exponential backoff on HTTP 429/503.
- **Tests:** Unit tests with mock Ktor engine testing SSE streaming, token rate-limiting, and error fallback.
- **Exit Criteria:** Successfully streams tokens from Gemini 2.5/Flash and OpenAI-compatible endpoints with zero memory leaks.

### Phase 3: Core Orchestrator & Multi-Agent State Machine
- **Dependencies:** Kotlin Coroutines (`CoroutineScope`, `Flow`, `StateFlow`), Phase 2 Model Router.
- **Expected Results:**
  - Task decomposer converting natural language into a directed subtask execution graph.
  - Concurrent and sequential sub-agent execution engine with context propagation.
  - Checkpoint manager saving state after every subtask.
- **Risks:** Deadlocks when agents await circular outputs.
- **Mitigation:** Strict DAG (Directed Acyclic Graph) validation before task scheduling.
- **Tests:** Unit tests simulating 2-agent and 3-agent handoffs with synthetic tool calls.
- **Exit Criteria:** Decomposes complex prompts into valid subtasks and executes sequential/parallel branches accurately.

### Phase 4: Model Context Protocol (MCP) Client Engine
- **Dependencies:** Kotlinx Serialization (JSON), Phase 3 Orchestrator.
- **Expected Results:**
  - Complete MCP 2024-11-05 client implementation.
  - In-process coroutine transport and HTTP/SSE loopback transport.
  - Central tool registry with dynamic JSON Schema validation.
- **Risks:** Invalid tool parameters from LLM outputs breaking JSON parsers.
- **Mitigation:** Strict JSON Schema validation with automatic error feedback to LLM for self-correction.
- **Tests:** Unit tests verifying schema registration, tool discovery, parameter serialization, and error recovery.
- **Exit Criteria:** MCP client discovers tools, executes synthetic calls, and parses results cleanly.

### Phase 5: Android Device & Phone Automation (NeuralBridge + Aster MCP)
- **Dependencies:** Android `AccessibilityService`, `MediaProjection`, Aster MCP schemas.
- **Expected Results:**
  - Non-root Phone Agent reading accessibility node trees (`AccessibilityNodeInfo`).
  - Synthetic tap, swipe, and type dispatching via `dispatchGesture()`.
  - Non-root screen capture via `MediaProjection` and `ImageReader`.
- **Risks:** OEM-specific Android battery managers killing the Accessibility Service.
- **Mitigation:** Request battery optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and provide clear recovery UI.
- **Tests:** Instrumentation tests simulating gesture creation and hierarchy parsing.
- **Exit Criteria:** Can inspect foreground app hierarchy and click a target element without root.

### Phase 6: Headless Browser & Search Subsystems
- **Dependencies:** Android `WebView`, PageKit JVM, Ktor Client.
- **Expected Results:**
  - Embedded headless WebView agent loading pages, clicking selectors, and filling forms.
  - PageKit HTML-to-Markdown summarizer extracting clean content for LLM ingestion.
  - Multi-engine search tool returning ranked search results.
- **Risks:** Heavy JavaScript-heavy Single Page Applications (SPAs) hanging the WebView.
- **Mitigation:** Strict 15-second page load timeout and DOM mutation observer.
- **Tests:** Unit tests testing PageKit against complex HTML pages; WebView navigation tests.
- **Exit Criteria:** Loads target URLs, extracts clean readable Markdown, and captures viewport screenshots in <1 second.

### Phase 7: Embedded Git Engine & Long-Term Memory
- **Dependencies:** JGit 6.9+, AndroidX Room 2.6+, ZVec NDK library (`libzvec.so`).
- **Expected Results:**
  - Full Git operations (clone, commit, diff, push, branch) in pure Java.
  - Room database storing structured conversation turns, tasks, and settings.
  - ZVec vector index performing fast cosine similarity lookups over memory embeddings.
- **Risks:** JGit memory consumption on large repositories (>500MB).
- **Mitigation:** Configure JGit window cache (`core.packedGitLimit`, `core.packedGitWindowSize`) to stay under 64MB RAM.
- **Tests:** Unit tests cloning a test repo, staging changes, creating commits, and generating unified diffs.
- **Exit Criteria:** Clean Git commits created locally; semantic memory retrieval returns relevant context in <15ms.

### Phase 8: Terminal, Linux Environment & OpenCode Integration
- **Dependencies:** TermFold (embedded PRoot) / Termux Companion, OpenCode headless daemon.
- **Expected Results:**
  - Non-root Linux terminal runtime capable of running shell commands, Node.js, and Python.
  - Headless OpenCode daemon communicating with AgentOS over localhost HTTP/SSE (port 4096).
  - Coding Agent executing multi-file AST diffs, automated edits, and test runs.
- **Risks:** Android 10+ W^X security blocking execution of downloaded binaries.
- **Mitigation:** Bundle required binaries inside APK `lib/` directory or run inside TermFold's PRoot emulation layer.
- **Tests:** Integration test executing `terminal.exec("echo hello")` and running OpenCode file patch verification.
- **Exit Criteria:** OpenCode successfully analyzes a workspace repository and applies verified patches.

### Phase 9: Vision Agent & Multimodal Grounding
- **Dependencies:** Gemini Multimodal API, Android MediaProjection, Optional LiteRT INT4.
- **Expected Results:**
  - Automated screen analysis converting visual UI elements into exact physical tap coordinates.
  - Verification loop: Action -> Screenshot -> Visual Check -> Continue/Retry.
- **Risks:** High latency when uploading full-resolution 4K phone screenshots to cloud APIs.
- **Mitigation:** Downscale screenshots to 1080p, compress to WebP format (saving 80% bandwidth), and send in <300ms.
- **Tests:** Unit tests verifying coordinate scaling across different screen densities and aspect ratios.
- **Exit Criteria:** Accurately identifies and taps target buttons across standard Android apps.

### Phase 10: Human-in-the-Loop Security & Approval System
- **Dependencies:** Jetpack Compose Modal Bottom Sheets, MCP Permission Interceptor.
- **Expected Results:**
  - Modal prompt intercepting high-risk tool calls (file deletion, Git push, banking apps).
  - User options: "Approve Once", "Approve Always", "Deny".
  - Audit logging of all approved/denied actions in Room database.
- **Risks:** Alert fatigue causing users to blindly accept prompts.
- **Mitigation:** Smart risk classification (read-only queries never prompt; only state-altering actions do).
- **Tests:** UI tests verifying coroutine pause and resume on user approval/rejection.
- **Exit Criteria:** High-risk actions are strictly blocked until user explicitly confirms via the UI.

### Phase 11: End-to-End Integration & Multi-Agent CUJs
- **Dependencies:** All previous phases.
- **Expected Results:**
  - Execution of the full end-to-end task flows (Browser -> Git -> Coding Agent -> Test -> Report).
  - Simultaneous operation of Phone, Browser, and Coding agents without resource contention.
- **Risks:** Out-of-Memory (OOM) crashes under simultaneous multi-agent workloads.
- **Mitigation:** Strict lifecycle management; idle sub-agents release memory buffers and unload WebViews.
- **Tests:** Full Critical User Journey (CUJ) automated test suite.
- **Exit Criteria:** Complex multi-step instructions complete successfully with zero crashes.

### Phase 12: Hardening, Play Policy Compliance & Optimization
- **Dependencies:** ProGuard/R8, Android Profiler, Play Policy Compliance Audit.
- **Expected Results:**
  - APK size optimized to <35MB via R8 tree-shaking and resource shrinking.
  - Zero Google Play policy violations (no broad storage permissions, no unauthorized dynamic code loading).
  - Background power consumption profiled to ensure zero battery drain when idle.
- **Tests:** Lint checks, APK size analysis, background battery drain tests.
- **Exit Criteria:** Production-ready APK passes all verification suites and builds cleanly via `compile_applet`.
