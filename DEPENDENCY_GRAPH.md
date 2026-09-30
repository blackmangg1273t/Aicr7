# DEPENDENCY GRAPH — ANDROID MULTI-AGENT OS

## Complete System Dependency Graph

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ANDROID APPLICATION                             │
│                  (Jetpack Compose UI / Navigation)                     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       MAIN ORCHESTRATOR CORE                           │
│           (AgentStateCoordinator / Coroutine Flow Bus)                 │
└───────┬───────────────────────────┬────────────────────────────┬───────┘
        │                           │                            │
        ▼                           ▼                            ▼
┌──────────────┐            ┌──────────────┐             ┌──────────────┐
│  CONVERSATION│            │  TASK DECOM- │             │ MODEL ROUTER │
│  MANAGER     │            │  POSER       │             │ (Multi-LLM)  │
└───────┬──────┘            └───────┬──────┘             └───────┬──────┘
        │                           │                            │
        └───────────────────────────┼────────────────────────────┘
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        MCP TOOL CLIENT LAYER                           │
│            - Schema Discovery / Validation                             │
│            - Permission Interceptor (Confirmation UI)                  │
│            - Transport Dispatcher                                      │
└───────┬───────────────────────────┼────────────────────────────┬───────┘
        │ In-Process                │ Localhost HTTP/SSE         │ POSIX / JNI
        ▼                           ▼                            ▼
┌──────────────────┐       ┌──────────────────┐        ┌──────────────────┐
│ INTERNAL AGENTS  │       │ CODING AGENT     │        │ SYSTEM RUNTIMES  │
│                  │       │                  │        │                  │
│ 1. Phone Agent   │       │ OpenCode Daemon  │        │ TermFold Rootfs  │
│    (NeuralBridge)│       │ (Node.js runtime)│        │ (PRoot / Alpine) │
│ 2. Browser Agent │       │                  │        │                  │
│    (WebView)     │       │ Workspace Engine │        │ Termux IPC       │
│ 3. Search Agent  │       │ (AST/Diff/Test)  │        │ (Companion APK)  │
│    (PageKit)     │       └────────┬─────────┘        └────────┬─────────┘
│ 4. Memory Agent  │                │                           │
│    (Room/ZVec)   │                │                           │
│ 5. Vision Agent  │                │                           │
│    (MediaProj.)  │                │                           │
└───────┬──────────┘                │                           │
        │                           │                           │
        ▼                           ▼                           ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          LIBRARIES & DRIVERS                           │
│                                                                        │
│ - Android Jetpack: Compose, Room 2.6+, Lifecycle, Navigation           │
│ - Networking: Ktor 2.3+ (Client, CIO, ContentNegotiation, SSE)         │
│ - Git: JGit 6.9+ (Pure JVM Git Engine)                                 │
│ - Vector Search: libzvec.so (NDK C++ HNSW / ARM NEON)                  │
│ - HTML Parser: PageKit JVM / Jsoup Core                                │
│ - Security: AndroidX Security Crypto (EncryptedSharedPreferences)       │
│ - Local Inference: LiteRT-LM AAR / OpenCL delegates (Optional)         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           AI MODEL PROVIDERS                           │
│                                                                        │
│ 1. Cloud Tier: Google Gemini 2.5/Flash, Qwen 2.5 API, GLM-4, DeepSeek  │
│ 2. Local Tier (Optional): Gemma 2 2B / FunctionGemma (LiteRT INT4)     │
│ 3. Vision Tier: Gemini Multimodal Vision API / UI-Venus ONNX Fallback  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Conflict & Optimization Analysis

### 1. Circular Dependencies
- **Identified Hazard:** An agent invoking a terminal command that tries to call the Main Agent API, or an MCP server attempting to query the Memory Agent while the Memory Agent is waiting for an MCP client response.
- **Resolution:** Strict unidirectional hierarchy enforced by architecture.
  - Agents can only call tools downwards via the MCP client.
  - Tools are completely stateless execution sinks and can NEVER call back into agents.
  - All communication is request-response over `McpMessage.Request` -> `McpMessage.Response`.

### 2. Version Conflicts
- **Android Gradle Plugin & Kotlin:** AGP 8.7+ requires Kotlin 2.0+. Room compiler (KSP) must match the Kotlin compiler version exactly.
- **Java 17 Bytecode:** Android 9 (API 28) does not support all Java 11/17 APIs natively. Desugaring (`coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.0.4"`) is enabled in `build.gradle.kts` to allow modern `java.time` and `java.util.concurrent` APIs used by JGit and Ktor.
- **Ktor vs. Retrofit:** Redundancy resolved by selecting **Ktor 2.3+ exclusively**. Ktor natively supports coroutine-native SSE (Server-Sent Events) and WebSockets, which are required for MCP streaming. Retrofit is eliminated.

### 3. Duplicate Functionality Eliminated
- **Git:** Eliminated `libgit2` and Termux native `git` from the APK in favor of **JGit** (eliminates 40MB of NDK shared binaries and avoids GPL copyleft).
- **Search:** Eliminated on-device `SearXNG` server in favor of **PageKit + REST APIs** (eliminates Python, Redis, and 600MB of runtime memory).
- **Accessibility:** Eliminated `Assists` in favor of **NeuralBridge + Aster MCP** (eliminates GPL-2.0 copyleft risk).
- **Browser:** Eliminated `bwb-browser` / `Termux Browser Pilot` (desktop Chromium / Playwright) in favor of **Native Android WebView** (saves 1.8GB of disk space and 1.5GB of RAM).
- **Model Router:** Eliminated `OmniRoute` (Node.js) and `LiteLLM` (Python) in favor of **Native Kotlin ModelRouter** (eliminates two background server processes).

### 4. License Conflict Matrix
- **Clean Permissive Core:** APK dependencies are restricted to Apache 2.0, MIT, EDL-1.0 (BSD), and Gemma Open Weights.
- **GPL Isolation:** Termux (GPL-3.0) and Core Git (GPL-2.0) are completely excluded from the APK and isolated to optional external companion apps accessed strictly via IPC.

### 5. Memory & Binary Size Impact
- **Total Primary APK Size:** ~28MB (excluding optional local LLM weights).
- **Runtime RAM Baseline:** ~110MB on Android 9+ (leaves ample room for user apps and multitasking).
- **Optional Local LLM RAM:** +1.8GB (loaded only on devices with >= 8GB RAM).
