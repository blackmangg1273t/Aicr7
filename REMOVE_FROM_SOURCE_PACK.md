# COMPONENTS TO REMOVE FROM SOURCE PACK — ANDROID MULTI-AGENT OS

To guarantee a clean, lightweight, commercially safe, and maintainable Android distribution, the source pack must be purged of incompatible, duplicate, and obsolete repositories. 

The following table lists all components that must be removed immediately, along with the precise technical rationale.

---

## Purge List & Technical Justifications

| Repository / Module | Location in Source Pack | Size | Rationale for Removal |
|---|---|---|---|
| **Mobile-Harness** | `source_pack/runtime/mobile-harness` | ~85MB | **Desktop Host Only:** Designed as a multi-device test farm server running on a desktop Linux PC. Requires physical USB host connections and desktop ADB servers. Completely unviable for on-device mobile execution. |
| **SearXNG** | `source_pack/search/searxng` | ~120MB | **AGPL-3.0 Copyleft & Heavy Server:** Python server requiring Redis and uWSGI. Extreme viral AGPL license creates severe commercial distribution risks. Mobile search is solved far more efficiently via client HTTPS queries to public endpoints. |
| **Assists** | `source_pack/automation/assists` | ~18MB | **GPL-2.0 Viral License:** Redundant with NeuralBridge. Bundling Assists into the APK would trigger GPL-2.0 obligations, forcing the entire proprietary Android application to be open-sourced. |
| **bwb-browser** | `source_pack/browser/bwb-browser` | ~95MB | **Desktop Chromium / Playwright:** Requires a desktop Chromium binary running inside a heavy PRoot Linux container. Consumes >2GB RAM and crashes under Android Low Memory Killer (LMK). Replaced by Android's native lightweight WebView. |
| **Termux Browser Pilot** | `source_pack/browser/termux-browser-pilot` | ~25MB | **Duplicate / Unviable Architecture:** Redundant headless browser scripts for Termux. Fails mobile power and memory constraints. |
| **OmniRoute** | `source_pack/routing/omniroute` | ~45MB | **Redundant Node.js Proxy:** Requires running a background Node.js server just to route HTTP requests. Replaced by a zero-overhead native Kotlin `ModelRouter` in the APK. |
| **LiteLLM** | `source_pack/routing/litellm` | ~60MB | **Heavy Python Dependency:** Requires a full Python 3.10+ runtime, pip, and hundreds of transitive Python packages. Unsuitable for mobile embedding. Retained strictly as reference. |
| **MLC-LLM** | `source_pack/local_ai/mlc` | ~110MB | **Redundant Local Runtime:** Complex TVM compilation pipeline and large binary overhead. Eclipsed by Google's official, highly optimized LiteRT-LM runtime. |
| **Aider** (Source Code) | `source_pack/coding/aider` | ~70MB | **Python REPL Tool:** Heavy tree-sitter C compilation overhead on Android. OpenCode was selected as the primary autonomous coding engine due to its superior headless HTTP/SSE client-server architecture. |

---

## Impact of Purge

1. **Storage Reduction:** Removes over **628MB** of bloated, incompatible, or redundant source trees and prebuilt desktop artifacts from the source pack.
2. **Legal Sanitization:** Completely eliminates AGPL-3.0 and GPL-2.0 viral copyleft liabilities from the primary codebase.
3. **RAM & Performance Optimization:** Prevents the accidental execution of background Python/Redis/Chromium servers that would exhaust mobile device memory and trigger system battery drain.
4. **Maintenance Simplicity:** Focuses engineering effort on the verified Kotlin core, official Android APIs, and the clean OpenCode companion daemon.
