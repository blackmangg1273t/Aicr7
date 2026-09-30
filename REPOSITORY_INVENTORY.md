# REPOSITORY INVENTORY — ANDROID MULTI-AGENT OS SOURCE PACK

This document records the comprehensive inventory of all 24 repositories and research assets evaluated from the 700MB open-source source pack for the Android Multi-Agent AI OS.

---

## Classification Summary

| Class | Code | Count | Description |
|---|---|---|---|
| **CORE** | A | 4 | Essential on-device engines embedded into the primary Kotlin/Compose APK |
| **COMPANION** | B | 3 | Separate native Android apps installed alongside the host for specialized privileges (SELinux / execution bypass) |
| **MCP SERVER** | C | 4 | Subsystems exposing standardized JSON-RPC Model Context Protocol tools |
| **LIBRARY** | D | 4 | Pure Android/JVM libraries linked directly into `app/build.gradle.kts` |
| **REFERENCE** | E | 3 | Source code consulted for protocol definitions, prompt structures, or architectural inspiration |
| **OPTIONAL** | F | 2 | Modular add-ons (e.g., local on-device neural inference) active only if hardware allows |
| **REJECTED** | G | 3 | Incompatible due to licensing (GPL viral copyleft in APK), desktop-only architecture, or root requirement |
| **DUPLICATE** | H | 1 | Redundant implementations eclipsed by superior alternatives |
| **TOTAL** | | **24** | |

---

## Detailed Repository Profiles

### 1. OpenCode
- **Original GitHub URL:** `https://github.com/sst/opencode` (and community forks)
- **Local Path:** `source_pack/coding/opencode`
- **Owner / Organization:** SST / Anomaly Innovations
- **Primary Language:** TypeScript / Go / Node.js
- **Framework / Runtime:** Node.js v20+ / Bun / CLI
- **License:** MIT
- **License File Location:** `source_pack/coding/opencode/LICENSE`
- **Commit SHA / Tag:** `v0.1.48` (`c8f12a9e...`)
- **Last Meaningful Update:** 2025/2026
- **Android Min / Target SDK:** N/A (Linux CLI tool)
- **ARM64 Support:** YES (Node.js Linux arm64)
- **Root Requirement:** NO ROOT
- **Termux Requirement:** YES (requires Linux POSIX environment with Node/Bun & file-system access)
- **Linux Requirement:** YES
- **Server Requirement:** Embedded HTTP / CLI IPC mode
- **Runtime Requirements:** Node.js >= 20.0.0, npm/pnpm, Git binary
- **Build System:** TypeScript / esbuild / npm
- **Important Dependencies:** `@modelcontextprotocol/sdk`, `@octokit/rest`, `diff`, `simple-git`, `commander`
- **MCP Support:** YES (Exposes and consumes MCP tools)
- **HTTP API:** YES (Provides local HTTP server mode for remote headless clients)
- **WebSocket API:** YES (Real-time diff/event streaming)
- **Intent Integration:** NO
- **AIDL/Binder Integration:** NO
- **CLI Interface:** YES (`opencode --port 4096 --headless`)
- **Main Purpose:** Autonomous terminal coding agent: reads repositories, plans changes, executes multi-file edits, checks ASTs, runs tests, and applies Git diffs.
- **Current Project Status:** Active
- **Integration Difficulty:** High (cannot run natively inside ART/JVM; requires companion execution environment or containerized Node runtime)
- **Recommended Usage:** **PRIMARY Coding Agent** executed inside a controlled Termux/Proot companion daemon, controlled by the Android app via localhost HTTP/WebSocket JSON-RPC.
- **Classification:** **COMPANION** / **MCP SERVER**

---

### 2. Aider
- **Original GitHub URL:** `https://github.com/paul-gauthier/aider`
- **Local Path:** `source_pack/coding/aider`
- **Owner / Organization:** Paul Gauthier
- **Primary Language:** Python 3.10+
- **Framework / Runtime:** Python, Rich, LiteLLM
- **License:** Apache 2.0
- **License File Location:** `source_pack/coding/aider/LICENSE.txt`
- **Commit SHA / Tag:** `v0.72.0` (`b34f82d1...`)
- **Last Meaningful Update:** 2025/2026
- **Android Min / Target SDK:** N/A (Python CLI)
- **ARM64 Support:** YES (CPython arm64)
- **Root Requirement:** NO ROOT
- **Termux Requirement:** YES (Requires `python`, `pip`, `git`, `tree-sitter` in Termux)
- **Linux Requirement:** YES
- **Server Requirement:** Optional web UI mode; primary is stdin/stdout
- **Runtime Requirements:** Python >= 3.10, native tree-sitter C extensions
- **Build System:** `pyproject.toml` (Setuptools / Flit)
- **Important Dependencies:** `litellm`, `prompt_toolkit`, `tree-sitter`, `gitpython`
- **MCP Support:** Partial (community MCP wrappers)
- **HTTP API:** Secondary/Experimental
- **WebSocket API:** NO
- **Intent / Binder:** NO
- **CLI Interface:** Primary (REPL / STDIN)
- **Main Purpose:** AI pair programming in terminal using Git repository maps.
- **Current Project Status:** Active
- **Integration Difficulty:** Very High on Android (heavy Python binary dependencies, tree-sitter compiling, heavy memory footprint ~500MB RAM just for Python runtime).
- **Recommended Usage:** **BACKUP** coding engine / Reference for git-repo-map algorithms. Eclipsed by OpenCode due to OpenCode's superior headless client/server protocol.
- **Classification:** **REFERENCE** / **BACKUP**

---

### 3. TermFold
- **Original GitHub URL:** `https://github.com/termfold-dev/termfold`
- **Local Path:** `source_pack/runtime/termfold`
- **Owner:** TermFold Contributors
- **Primary Language:** Kotlin / C++ (JNI)
- **Framework / Runtime:** Android NDK / PRoot wrapper
- **License:** Apache 2.0
- **License File Location:** `source_pack/runtime/termfold/LICENSE`
- **Commit SHA / Tag:** `main` (`7a89e13d...`)
- **Last Meaningful Update:** 2025
- **Android Min SDK:** 28 (Android 9)
- **Android Target SDK:** 34
- **ARM64 Support:** YES (`arm64-v8a` precompiled binaries)
- **Root Requirement:** NO ROOT (uses PRoot ptraced syscall emulation)
- **Termux Requirement:** NO (self-contained embedded terminal runtime)
- **Linux Requirement:** Embeds Alpine/Debian rootfs
- **Server Requirement:** NO
- **Runtime Requirements:** Android app storage permissions, W^X compliance on Android 10+
- **Build System:** Gradle (Kotlin DSL), CMake / NDK
- **Important Dependencies:** `libproot.so`, `libfake-execve.so`, Android Terminal Emulator JNI
- **MCP Support:** NO (provides the underlying POSIX execution environment)
- **HTTP API:** NO
- **WebSocket API:** Local PTY WebSocket bridge
- **Intent / Binder:** Basic Activity intents
- **CLI Interface:** Linux bash/sh PTY
- **Main Purpose:** Non-root terminal and Linux userspace isolation engine embedded inside an Android application.
- **Current Project Status:** Functional prototype
- **Integration Difficulty:** High (Android 10+ W^X `noexec` on `data/data` forces strict execution from APK `lib/` directory or PRoot dynamic translation).
- **Recommended Usage:** **PRIMARY on-device POSIX/Terminal runtime** for embedded shell tasks, file management, and tool execution if self-hosting is required.
- **Classification:** **CORE**

---

### 4. Termux (App & Packages)
- **Original GitHub URL:** `https://github.com/termux/termux-app`
- **Local Path:** `source_pack/runtime/termux-app`
- **Owner:** Termux Organization
- **Primary Language:** Java / Kotlin / C++
- **Framework / Runtime:** Android Native Application
- **License:** GPL-3.0
- **License File Location:** `source_pack/runtime/termux-app/LICENSE.md`
- **Commit SHA / Tag:** `v0.118.1` (`f48b11c0...`)
- **Last Meaningful Update:** 2025
- **Android Min SDK:** 24 (Compatible with Android 9 / API 28)
- **Android Target SDK:** 28 (Legacy) / 34 (Modern forks)
- **ARM64 Support:** YES
- **Root Requirement:** NO ROOT
- **Termux Requirement:** IS Termux
- **Linux Requirement:** Bionic-based userland
- **Server Requirement:** Optional (`sshd`, `nodejs`, `python`)
- **Runtime Requirements:** Android OS
- **Build System:** Gradle / `termux-packages` build scripts
- **Important Dependencies:** Termux bootstrap rootfs, APT package manager
- **MCP Support:** Capable of hosting MCP servers
- **HTTP / WebSocket API:** Accessible via apps running inside it
- **Intent / Binder:** `com.termux.RUN_COMMAND` intent interface
- **CLI Interface:** Full terminal
- **Main Purpose:** Comprehensive Linux environment for Android without root.
- **Current Project Status:** Active
- **Integration Difficulty:** Medium as Companion, IMPOSSIBLE as directly embedded library due to **GPL-3.0 copyleft**.
- **Recommended Usage:** **COMPANION APP ONLY**. The main AgentOS can communicate with Termux via `RUN_COMMAND` Intents or via localhost Unix sockets/HTTP, keeping the main APK permissive (Apache/MIT) without triggering GPL license contamination.
- **Classification:** **COMPANION** (Companion-Only)

---

### 5. Mobile-Harness
- **Original GitHub URL:** `https://github.com/google/mobile-harness`
- **Local Path:** `source_pack/runtime/mobile-harness`
- **Owner:** Google LLC
- **Primary Language:** Java
- **Framework / Runtime:** Java 11+ Server / Daemon
- **License:** Apache 2.0
- **License File Location:** `source_pack/runtime/mobile-harness/LICENSE`
- **Commit SHA:** `d4812f8e...`
- **Last Meaningful Update:** 2025
- **Android Min SDK:** Requires desktop/server host JVM (Not an Android app!)
- **Target SDK:** N/A
- **ARM64 Support:** Java JVM only
- **Root Requirement:** Needs ADB or USB host connection
- **Termux Requirement:** Excessive JVM footprint
- **Linux Requirement:** Desktop Linux / Cloud
- **Server Requirement:** YES (Intended for lab test clusters)
- **Runtime Requirements:** JDK 17, ADB server, Android USB tethering
- **Build System:** Bazel
- **Main Purpose:** Device farm test infrastructure automation.
- **Current Project Status:** Active enterprise tool
- **Integration Difficulty:** Impossible on-device. Architecture expects a host machine controlling connected phones over ADB.
- **Recommended Usage:** **REJECTED**. Not an on-device Android runtime component.
- **Classification:** **REJECTED**

---

### 6. NeuralBridge
- **Original GitHub URL:** `https://github.com/neuralbridge-ai/neuralbridge-android`
- **Local Path:** `source_pack/automation/neuralbridge`
- **Owner:** NeuralBridge
- **Primary Language:** Kotlin
- **Framework / Runtime:** Android AccessibilityService + MediaProjection API
- **License:** Apache 2.0
- **License File Location:** `source_pack/automation/neuralbridge/LICENSE`
- **Commit SHA / Tag:** `v1.2.0` (`e581ab32...`)
- **Last Meaningful Update:** 2025/2026
- **Android Min SDK:** 28 (Android 9 Pie)
- **Android Target SDK:** 34
- **ARM64 Support:** YES
- **Root Requirement:** NO ROOT (pure Android Accessibility + MediaProjection)
- **Termux Requirement:** NO
- **Linux Requirement:** NO
- **Server Requirement:** NO
- **Runtime Requirements:** User grant of Accessibility Service + Screen Capture permission
- **Build System:** Gradle (Kotlin DSL)
- **Important Dependencies:** `androidx.core`, `kotlinx-coroutines-android`, `ktor-client-core`
- **MCP Support:** YES (Exposes Android UI gestures as tool schema)
- **HTTP API:** Local Ktor HTTP service (`127.0.0.1:8088`)
- **WebSocket API:** Real-time UI hierarchy and screenshot streaming
- **Intent / Binder:** Bound Service with custom AIDL interface
- **CLI Interface:** NO
- **Main Purpose:** Non-root Android UI inspection and automation: reads accessibility node hierarchy (`AccessibilityNodeInfo`), generates synthetic taps/swipes (`GestureDescription`), enters text, captures screen via `VirtualDisplay`/`ImageReader`.
- **Current Project Status:** Highly active
- **Integration Difficulty:** Low-Medium (native Kotlin, modular service design).
- **Recommended Usage:** **PRIMARY Phone Automation Subsystem**. Embed directly into the APK as an internal Accessibility Service or companion service with AIDL/Localhost MCP transport.
- **Classification:** **CORE**

---

### 7. Aster MCP
- **Original GitHub URL:** `https://github.com/aster-ai/aster-mcp-android`
- **Local Path:** `source_pack/automation/aster-mcp`
- **Owner:** Aster AI
- **Primary Language:** Kotlin / TypeScript
- **Framework / Runtime:** Android MCP Server
- **License:** MIT
- **License File Location:** `source_pack/automation/aster-mcp/LICENSE`
- **Commit SHA:** `91ab763c...`
- **Last Meaningful Update:** 2025
- **Android Min SDK:** 28
- **Target SDK:** 34
- **ARM64 Support:** YES
- **Root Requirement:** NO ROOT
- **Termux Requirement:** NO
- **Linux Requirement:** NO
- **Server Requirement:** Embedded NanoHTTPD / Ktor
- **Build System:** Gradle
- **Main Purpose:** Standardized Model Context Protocol server exposing `device.tap`, `device.type`, `device.press_key`, `device.get_screen_hierarchy`.
- **Current Project Status:** Active
- **Integration Difficulty:** Low
- **Recommended Usage:** **PRIMARY MCP Tool Provider for Device Automation**. Directly integrated into the MCP Tool Layer.
- **Classification:** **MCP SERVER**

---

### 8. Assists (Android Accessibility Helper)
- **Original GitHub URL:** `https://github.com/accessibility-tools/assists-android`
- **Local Path:** `source_pack/automation/assists`
- **Owner:** Community
- **Primary Language:** Java / Kotlin
- **License:** GPL-2.0
- **License File Location:** `source_pack/automation/assists/LICENSE`
- **Commit SHA:** `43f8e129...`
- **Main Purpose:** Utility routines for traversing AccessibilityNodeInfo trees and generating Gestures.
- **Classification:** **REJECTED** (GPL-2.0 viral license conflicts with commercial/permissive distribution of the primary APK; functionality is already cleanly provided under Apache 2.0 by NeuralBridge).

---

### 9. bwb-browser (Browser With Browser)
- **Original GitHub URL:** `https://github.com/agent-tools/bwb-browser`
- **Local Path:** `source_pack/browser/bwb-browser`
- **Owner:** BWB Team
- **Primary Language:** TypeScript / Playwright
- **Framework / Runtime:** Node.js + Headless Chromium
- **License:** MIT
- **License File Location:** `source_pack/browser/bwb-browser/LICENSE`
- **Commit SHA:** `56e43210...`
- **Android Min / Target SDK:** N/A (Desktop Chromium architecture)
- **ARM64 Support:** Linux arm64 only with desktop glibc
- **Root Requirement:** NO ROOT
- **Termux Requirement:** Requires heavy `proot-distro` Debian + X11/VNC + chromium (~1.5GB)
- **Main Purpose:** Desktop Playwright browser automation with CDPSession control.
- **Integration Difficulty:** Very High / Unrealistic for standard mobile devices. Running full desktop Chromium inside PRoot consumes >2GB RAM, drains battery rapidly, and crashes under Android 9-14 LMK (Low Memory Killer).
- **Recommended Usage:** **REFERENCE ONLY** for CDP protocol command schemas and DOM extraction logic. Eclipsed on Android by custom WebView automation.
- **Classification:** **REFERENCE**

---

### 10. Termux Browser Pilot
- **Original GitHub URL:** `https://github.com/mobile-agents/termux-browser-pilot`
- **Local Path:** `source_pack/browser/termux-browser-pilot`
- **Owner:** Mobile Agents
- **Primary Language:** Python / JavaScript
- **Framework / Runtime:** Termux + Puppeteer core
- **License:** Apache 2.0
- **License File Location:** `source_pack/browser/termux-browser-pilot/LICENSE`
- **Commit SHA:** `89a23b41...`
- **Main Purpose:** Scripts to drive headless browser processes inside Termux.
- **Classification:** **DUPLICATE** / **REJECTED** (Duplicate of heavy headless browser approach; unviable for 90% of user Android devices).

---

### 11. PageKit (Web Content & DOM Extractor)
- **Original GitHub URL:** `https://github.com/pagekit-ai/pagekit-parser`
- **Local Path:** `source_pack/search/pagekit`
- **Owner:** PageKit AI
- **Primary Language:** Kotlin / Java (with Readability.js port)
- **Framework / Runtime:** JVM / Android
- **License:** MIT
- **License File Location:** `source_pack/search/pagekit/LICENSE`
- **Commit SHA:** `3341b5a2...`
- **Android Min SDK:** 26 (Android 8.0+)
- **Target SDK:** 34
- **ARM64 Support:** Architecture independent (pure bytecode)
- **Root Requirement:** NO ROOT
- **Main Purpose:** Fast HTML cleaning, boiler-plate removal, Markdown synthesis, and structured JSON extraction for feeding clean context to LLMs.
- **Integration Difficulty:** Very Low (pure JVM library).
- **Recommended Usage:** **PRIMARY Content Extraction Library** inside the Android APK for both Search results and Browser page summaries.
- **Classification:** **LIBRARY**

---

### 12. Sifthound
- **Original GitHub URL:** `https://github.com/sifthound/sifthound-core`
- **Local Path:** `source_pack/search/sifthound`
- **Owner:** Sifthound
- **Primary Language:** Go
- **Framework / Runtime:** Go runtime / CLI
- **License:** Apache 2.0
- **Commit SHA:** `a81f09c2...`
- **Main Purpose:** Metasearch engine query router that aggregates DuckDuckGo, Brave, and Google Search into normalized JSON.
- **Integration Difficulty:** Medium (can be compiled as a static C-shared library `libsifthound.so` via Go mobile/gomobile, or run as a companion CLI).
- **Recommended Usage:** **BACKUP Search Engine** / Cross-compiled native library.
- **Classification:** **LIBRARY** / **BACKUP**

---

### 13. SearXNG Integrations
- **Original GitHub URL:** `https://github.com/searxng/searxng`
- **Local Path:** `source_pack/search/searxng`
- **Owner:** SearXNG
- **Primary Language:** Python
- **License:** AGPL-3.0
- **License File Location:** `source_pack/search/searxng/LICENSE`
- **Commit SHA:** `2025.10.1`
- **Main Purpose:** Privacy-respecting metasearch server.
- **Integration Difficulty:** Very High (Python server requiring redis and uwsgi; AGPL-3.0 prevents commercial bundling).
- **Recommended Usage:** **REJECTED** for on-device hosting. The Search Agent should instead query public SearXNG / Brave / DuckDuckGo endpoints over HTTPS.
- **Classification:** **REJECTED**

---

### 14. ZVec (Vector Embedding Engine)
- **Original GitHub URL:** `https://github.com/zvec-engine/zvec`
- **Local Path:** `source_pack/memory/zvec`
- **Owner:** ZVec
- **Primary Language:** C++20 / Rust
- **Framework / Runtime:** Native NDK shared library
- **License:** Apache 2.0
- **License File Location:** `source_pack/memory/zvec/LICENSE`
- **Commit SHA:** `ef23561a...`
- **Android Min SDK:** 28 (Requires 64-bit atomic ops)
- **Target SDK:** 34
- **ARM64 Support:** YES (ARM NEON SIMD accelerated)
- **Root Requirement:** NO ROOT
- **Main Purpose:** High-performance, lightweight HNSW (Hierarchical Navigable Small World) vector indexing for mobile devices.
- **Integration Difficulty:** Medium (requires JNI wrapper and CMake build setup).
- **Recommended Usage:** **PRIMARY Vector Storage Engine** for semantic long-term memory, coupled with Room for metadata.
- **Classification:** **LIBRARY**

---

### 15. libSQL (Turso SQLite Fork)
- **Original GitHub URL:** `https://github.com/tursodatabase/libsql`
- **Local Path:** `source_pack/memory/libsql`
- **Owner:** Turso
- **Primary Language:** C / Rust
- **Framework / Runtime:** Native SQLite derivative
- **License:** MIT
- **License File Location:** `source_pack/memory/libsql/LICENSE`
- **Commit SHA:** `78d910fe...`
- **Android Min SDK:** 26
- **ARM64 Support:** YES
- **Root Requirement:** NO ROOT
- **Main Purpose:** SQLite replacement with native vector indexing (`libsql_vector`) and remote sync capability.
- **Integration Difficulty:** Medium-High (requires compiling Rust C-bindings for 4 Android ABIs).
- **Recommended Usage:** **BACKUP Storage Engine**. (Room + ZVec is preferred for initial phases due to standard Android lifecycle safety).
- **Classification:** **LIBRARY** / **BACKUP**

---

### 16. SQLite / Room (Android Jetpack)
- **Original Repository:** `androidx.room:room-runtime` (Google Maven)
- **Local Path:** `source_pack/memory/room`
- **Owner:** Google / Android Open Source Project (AOSP)
- **Primary Language:** Kotlin / Java
- **Framework / Runtime:** Android Jetpack
- **License:** Apache 2.0
- **License File Location:** `source_pack/memory/room/LICENSE.txt`
- **Android Min SDK:** 21 (Full Android 9+ support)
- **Target SDK:** 35
- **ARM64 Support:** Built into Android OS SQLite
- **Root Requirement:** NO ROOT
- **Main Purpose:** Structured relational storage for conversations, user preferences, agent execution logs, tool histories, and checkpoint tokens.
- **Integration Difficulty:** Very Low (Industry standard Android persistence).
- **Recommended Usage:** **CORE Structured Storage Engine**.
- **Classification:** **CORE**

---

### 17. OmniRoute
- **Original GitHub URL:** `https://github.com/omniroute/omniroute-proxy`
- **Local Path:** `source_pack/routing/omniroute`
- **Owner:** OmniRoute
- **Primary Language:** TypeScript / Go
- **Framework / Runtime:** Node.js / Go
- **License:** MIT
- **License File Location:** `source_pack/routing/omniroute/LICENSE`
- **Commit SHA:** `61b8f042...`
- **Android Min SDK:** N/A (Server architecture)
- **ARM64 Support:** YES
- **Root Requirement:** NO ROOT
- **Main Purpose:** Dynamic LLM request routing, fallback management, load balancing, and token rate-limit tracking across Gemini, OpenAI, Anthropic, Qwen, and DeepSeek.
- **Integration Difficulty:** High if run as Node server; Medium if rewritten in Kotlin.
- **Recommended Usage:** **REFERENCE** for provider schema transformations; rewritten into a lightweight native Kotlin `ModelRouter` in `app/src/main/java/com/agentos/router/`.
- **Classification:** **REFERENCE**

---

### 18. LiteLLM
- **Original GitHub URL:** `https://github.com/BerriAI/litellm`
- **Local Path:** `source_pack/routing/litellm`
- **Owner:** BerriAI
- **Primary Language:** Python
- **License:** MIT
- **License File Location:** `source_pack/routing/litellm/LICENSE`
- **Commit SHA:** `v1.54.0`
- **Main Purpose:** Universal interface for 100+ LLMs in Python.
- **Classification:** **REFERENCE ONLY**. Heavy Python codebase incompatible with direct Android JVM deployment.
- **Classification:** **REFERENCE**

---

### 19. LiteRT-LM (Formerly MediaPipe GenAI / TF-Lite)
- **Original GitHub URL:** `https://github.com/google-ai-edge/litert`
- **Local Path:** `source_pack/local_ai/litert-lm`
- **Owner:** Google AI Edge
- **Primary Language:** C++ / Java / Kotlin
- **Framework / Runtime:** Android AAR (`com.google.ai.edge.litert:litert-lm`)
- **License:** Apache 2.0
- **License File Location:** `source_pack/local_ai/litert-lm/LICENSE`
- **Commit SHA:** `v1.0.1` (`1934ef02...`)
- **Android Min SDK:** 28 (Pie)
- **Target SDK:** 34
- **ARM64 Support:** YES (ARMv8.2-A FP16 + Qualcomm Adreno OpenCL + MediaTek APU)
- **Root Requirement:** NO ROOT
- **Main Purpose:** Official Google runtime for executing quantized on-device LLMs (Gemma 2 2B, FunctionGemma) with NPU/GPU acceleration via OpenCL / Vulkan delegates.
- **Integration Difficulty:** Low-Medium (standard Gradle AAR dependency).
- **Recommended Usage:** **PRIMARY Local AI Model Engine (Optional Module)**. Activated only on devices with >= 8GB RAM.
- **Classification:** **OPTIONAL**

---

### 20. LocalMind (Android Local LLM Wrapper)
- **Original GitHub URL:** `https://github.com/localmind/localmind-android`
- **Local Path:** `source_pack/local_ai/localmind`
- **Owner:** LocalMind
- **Primary Language:** Kotlin / C++ (llama.cpp JNI)
- **Framework / Runtime:** llama.cpp NDK bindings
- **License:** MIT
- **License File Location:** `source_pack/local_ai/localmind/LICENSE`
- **Commit SHA:** `445ab120...`
- **Android Min SDK:** 28
- **ARM64 Support:** YES
- **Root Requirement:** NO ROOT
- **Main Purpose:** GGUF model loader and CPU/GPU llama.cpp runner for Android.
- **Integration Difficulty:** Medium.
- **Recommended Usage:** **BACKUP Local AI Engine** for non-Gemma GGUF models (e.g. Qwen 2.5 1.5B GGUF).
- **Classification:** **OPTIONAL** / **BACKUP**

---

### 21. MLC-LLM
- **Original GitHub URL:** `https://github.com/mlc-ai/mlc-llm`
- **Local Path:** `source_pack/local_ai/mlc`
- **Owner:** MLC AI
- **Primary Language:** C++ / TVM / Kotlin
- **License:** Apache 2.0
- **Commit SHA:** `v0.1.0`
- **Main Purpose:** Vulkan-accelerated LLM runtime for Android.
- **Classification:** **REFERENCE** (Larger binary size and complex TVM compiler pipeline compared to LiteRT-LM).

---

### 22. UI-Venus (Mobile GUI Vision Model)
- **Original GitHub URL:** `https://github.com/venus-multimodal/ui-venus`
- **Local Path:** `source_pack/vision/ui-venus`
- **Owner:** Venus Multimodal Lab
- **Primary Language:** Python / PyTorch / ONNX
- **License:** Apache 2.0
- **License File Location:** `source_pack/vision/ui-venus/LICENSE`
- **Commit SHA:** `312def40...`
- **Main Purpose:** 1.5B parameter Vision-Language Model trained to detect UI buttons, input boxes, icons, and bounding boxes from mobile screenshots.
- **Integration Difficulty:** High for on-device; Zero difficulty if deployed as an API or quantized via LiteRT INT4.
- **Recommended Usage:** **PRIMARY Vision Spec for Cloud/Local Vision Agent**. Uses Gemini 2.5/Flash as primary multimodal cloud detector, with UI-Venus ONNX/LiteRT INT8 as optional local fallback.
- **Classification:** **REFERENCE** / **OPTIONAL**

---

### 23. FunctionGemma (Fine-Tuned Function Calling Model)
- **Original GitHub URL:** `https://huggingface.co/google/function-gemma`
- **Local Path:** `source_pack/local_ai/function-gemma`
- **Owner:** Google LLC
- **Primary Language:** Model Weights / SentencePiece
- **License:** Gemma Terms of Use (Permissive, open weights)
- **Commit SHA:** `main`
- **Main Purpose:** 2B parameter model fine-tuned specifically to output clean JSON tool calls conforming to MCP schemas.
- **Integration Difficulty:** Low when run via LiteRT-LM.
- **Recommended Usage:** **PRIMARY Local Function Calling Model**.
- **Classification:** **OPTIONAL**

---

### 24. JGit (Pure Java Git Library)
- **Original Repository:** `org.eclipse.jgit:org.eclipse.jgit` (Maven Central)
- **Local Path:** `source_pack/git/jgit`
- **Owner:** Eclipse Foundation
- **Primary Language:** Java
- **Framework / Runtime:** Pure JVM bytecode
- **License:** EDL-1.0 (Eclipse Distribution License - 3-clause BSD equivalent)
- **License File Location:** `source_pack/git/jgit/LICENSE.txt`
- **Android Min SDK:** 26 (Requires Java 8 java.nio)
- **Target SDK:** 35
- **ARM64 Support:** Architecture-independent
- **Root Requirement:** NO ROOT
- **Main Purpose:** Git clone, checkout, branch, commit, diff, status, log, and push in 100% pure Java without external binary dependencies.
- **Integration Difficulty:** Low (standard Android Gradle dependency).
- **Recommended Usage:** **PRIMARY Embedded Git Engine** inside the Android APK.
- **Classification:** **CORE** / **LIBRARY**

---

## Inventory Summary Table

| # | Name | Classification | License | Android Min SDK | Root Req | Embedded in APK? |
|---|---|---|---|---|---|---|
| 1 | **OpenCode** | COMPANION / MCP | MIT | N/A (Linux) | NO | NO (Runs in Termux / Companion) |
| 2 | **Aider** | REFERENCE / BACKUP | Apache 2.0 | N/A (Python) | NO | NO |
| 3 | **TermFold** | CORE | Apache 2.0 | 28 | NO | YES (JNI / NDK) |
| 4 | **Termux** | COMPANION | GPL-3.0 | 28 | NO | NO (Companion APK) |
| 5 | **Mobile-Harness** | REJECTED | Apache 2.0 | Host PC | YES (ADB) | NO |
| 6 | **NeuralBridge** | CORE | Apache 2.0 | 28 | NO | YES (Internal Service) |
| 7 | **Aster MCP** | MCP SERVER | MIT | 28 | NO | YES (Internal Service) |
| 8 | **Assists** | REJECTED | GPL-2.0 | 26 | NO | NO (License Conflict) |
| 9 | **bwb-browser** | REFERENCE | MIT | Desktop | NO | NO |
| 10 | **Termux Browser Pilot** | REJECTED | Apache 2.0 | Linux | NO | NO (Unviable RAM/LMK) |
| 11 | **PageKit** | LIBRARY | MIT | 26 | NO | YES |
| 12 | **Sifthound** | LIBRARY / BACKUP | Apache 2.0 | N/A (Go) | NO | Optional (`.so`) |
| 13 | **SearXNG** | REJECTED | AGPL-3.0 | Server | NO | NO (Use public API) |
| 14 | **ZVec** | LIBRARY | Apache 2.0 | 28 | NO | YES (JNI / NDK) |
| 15 | **libSQL** | LIBRARY / BACKUP | MIT | 26 | NO | Optional |
| 16 | **Room (AOSP)** | CORE | Apache 2.0 | 21 | NO | YES |
| 17 | **OmniRoute** | REFERENCE | MIT | Node/Go | NO | Replaced by Kotlin native |
| 18 | **LiteLLM** | REFERENCE | MIT | Python | NO | Reference only |
| 19 | **LiteRT-LM** | OPTIONAL | Apache 2.0 | 28 | NO | YES (AAR) |
| 20 | **LocalMind** | OPTIONAL / BACKUP | MIT | 28 | NO | Optional |
| 21 | **MLC-LLM** | REFERENCE | Apache 2.0 | 28 | NO | Reference only |
| 22 | **UI-Venus** | REFERENCE / OPTIONAL | Apache 2.0 | N/A | NO | Model weights / API |
| 23 | **FunctionGemma** | OPTIONAL | Gemma Terms | N/A | NO | Model weights |
| 24 | **JGit** | CORE / LIBRARY | BSD / EDL-1.0 | 26 | NO | YES |
