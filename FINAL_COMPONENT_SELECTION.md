# FINAL COMPONENT SELECTION — ANDROID MULTI-AGENT OS

This document declares the verified, binding component selections for every subsystem of the Android Multi-Agent OS. Selections are based strictly on Android OS compatibility (Android 9+ / API 28+), no-root feasibility, license compliance, RAM footprint, and execution reliability.

---

## Subsystem Selection Matrix

| Subsystem | PRIMARY Choice | BACKUP Choice | REJECTED Alternative | Technical Rationale |
|---|---|---|---|---|
| **Android Shell / Runtime** | **TermFold** (Embedded PRoot/JNI) | **Termux** (External Companion APK) | **Mobile-Harness** | TermFold embeds a non-root POSIX environment directly inside the app sandbox via PRoot syscall translation. Termux is maintained as a companion fallback for heavy packages. Mobile-Harness requires a desktop host PC and ADB tethering. |
| **Main Orchestrator** | **AgentOS Native Core** (Kotlin StateFlow / Coroutines) | **NanoAgent** (Embedded JS/Node) | **LangChain Python** | An Android-native orchestrator written in Kotlin guarantees zero-overhead thread scheduling, lifecycle awareness (stops on `onStop`/`onDestroy`), and immediate Jetpack Compose state binding. Python orchestrators consume excess RAM and cannot hook into the Android Activity lifecycle. |
| **Coding Agent** | **OpenCode Daemon** (Headless Mode) | **Aider** | **Claude Code Desktop** | OpenCode natively supports headless client/server mode with structured streaming diffs and AST tools. Aider is a Python REPL requiring heavy tree-sitter C builds on mobile. Claude Code relies on proprietary, desktop-only telemetry. |
| **OpenCode Integration** | **Localhost Loopback HTTP / SSE Bridge** | **Unix Domain Socket Bridge** | **Direct JNI In-Process Link** | OpenCode is Node.js/TypeScript and cannot link directly into the JVM ART process. A localhost HTTP/SSE loopback client connecting to OpenCode running in the companion/TermFold runtime provides 100% clean isolation and prevents process crashes. |
| **Browser Agent** | **Native Android WebView Headless Agent** | **Termux Browser Pilot** | **bwb-browser (Playwright Desktop)** | Android WebView is pre-installed on 100% of Android devices, hardware-accelerated, uses the system Chromium engine, and consumes <80MB RAM. Desktop Playwright (`bwb-browser`) inside PRoot requires 2GB RAM and crashes under Android Low Memory Killer (LMK). |
| **Android Automation** | **NeuralBridge** (Accessibility + MediaProjection) | **Aster MCP Native** | **Assists** | NeuralBridge provides a non-root, native Kotlin AccessibilityService and VirtualDisplay screen capture API. Assists was rejected due to a viral GPL-2.0 license that would force open-sourcing the entire proprietary APK. |
| **Search Subsystem** | **PageKit + Multi-Engine REST API** | **Sifthound** (Go gomobile `.so`) | **SearXNG On-Device** | Hosting a full Python SearXNG instance with Redis on-device is battery-draining and AGPL-3.0 contaminated. PageKit gives instant, pure-JVM readability parsing while queries run over lightweight HTTPS. |
| **MCP Layer** | **Android MCP Client + Aster MCP** | **Localhost JSON-RPC 2.0** | **Custom Proprietary Ad-Hoc RPC** | Adheres strictly to the official Model Context Protocol 2024-11-05 standard. Provides an extensible plug-and-play architecture for any third-party MCP server. |
| **Git Engine** | **JGit** (Pure Java JVM) | **Termux Git CLI** | **libgit2 / git2go** | JGit runs 100% in Java bytecode, requiring zero NDK compiling, zero shared library linking issues, and is licensed under the permissive BSD-style EDL-1.0 license. Core Git is GPL-2.0. |
| **Memory Subsystem** | **Jetpack Room (Metadata) + ZVec (HNSW)** | **libSQL (Turso Vector)** | **ChromaDB / Qdrant Server** | Room provides rock-solid, ACID-compliant relational SQLite persistence integrated with Android Jetpack. ZVec provides high-performance C++ HNSW vector embeddings on mobile with zero server daemon overhead. |
| **Model Router** | **AgentOS Native Kotlin Router** | **OmniRoute Headless Proxy** | **LiteLLM Python Proxy** | A native Kotlin router consumes virtually 0MB RAM, handles Gemini/Qwen/GLM/DeepSeek/OpenAI streaming SSE directly via Ktor, and stores API keys securely in Android EncryptedSharedPreferences. |
| **Local LLM Engine** | **LiteRT-LM** (Google AI Edge / OpenCL) | **LocalMind** (llama.cpp JNI) | **MLC-LLM** | LiteRT-LM is Google's official, highly-optimized runtime for Gemma 2B and FunctionGemma on Android NPU/GPU, distributed as a clean Android AAR. Activated conditionally on devices with >= 8GB RAM. |
| **Vision Agent** | **Multimodal Cloud (Gemini 2.5/Flash)** | **UI-Venus ONNX / LiteRT INT4** | **YOLOv8 Desktop PyTorch** | Mobile screens contain dense text and micro-icons. Cloud multimodal LLMs achieve 98% UI element grounding accuracy. UI-Venus INT4 serves as an offline fallback when internet connectivity is unavailable. |
| **File System Manager** | **Storage Access Framework (SAF) + Java NIO** | **TermFold POSIX Bridge** | **Raw `/sdcard` Direct Access** | Android 10+ (API 29+) Scoped Storage strictly restricts direct POSIX filesystem writes. Using DocumentFile/SAF guarantees compatibility across all Android versions from 9 through 16 without permission rejections. |
| **Agent Communication** | **Kotlin Coroutine Flow + StateFlow** | **Local BroadcastManager / FlowBus** | **Android Messenger / AIDL IPC** | In-process StateFlow allows real-time, non-blocking, reactive UI updates at 60/120 FPS in Jetpack Compose with zero serialization overhead. |
| **Security & Sandbox** | **Multi-Tier User Confirmation Interceptor** | **Android BiometricPrompt Guard** | **Unrestricted Autonomous Execution** | Explicit gating of destructive actions (file deletion, terminal execution, Git push, phone automation outside sandbox). Keeps the user firmly in the loop. |

---

## Architectural Rationale: Embedded vs. Companion Decision

The primary engineering breakthrough of this selection is the **Hybrid Dual-Runtime Architecture**:

1. **The Primary APK (`com.agentos.app`):**
   - 100% Permissive (Apache 2.0 / MIT / EDL-1.0 / Google Gemma).
   - Contains UI, Orchestrator, Model Router, MCP Client, Phone Automation (Accessibility), WebView Browser, JGit, and Room/ZVec Memory.
   - Requires NO root, zero GPL contamination, and adheres to all Google Play policies.

2. **The Companion Runtime (OpenCode / TermFold / Termux):**
   - Manages the heavy Linux userspace, Node.js runtime, package manager, and shell tools.
   - Connected exclusively via localhost loopback HTTP / SSE and MCP JSON-RPC.
   - Completely decoupled from the primary APK lifecycle and licensing.
