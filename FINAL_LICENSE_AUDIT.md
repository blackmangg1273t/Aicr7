# FINAL LICENSE AUDIT — ANDROID MULTI-AGENT OS

## Executive Legal Analysis & Risk Architecture

This audit examines the licensing, intellectual property boundaries, and distribution constraints for all 24 projects in the 700MB source pack. 

The primary target is a **commercial-grade, distributable Android application** built using Kotlin and Jetpack Compose. 

### Core Legal Constraints for Android APK Distribution:
1. **The Android Monolithic Binary Trap:** Unlike desktop Linux where binaries can dynamically link to `.so` files at runtime or execute separate daemon binaries cleanly isolated by fork/exec, an Android APK is distributed as a single signed package (`.apk` or `.aab`). 
   - Linking Java/Kotlin `.jar` or `.aar` dependencies or compiling C/C++ libraries into the APK creates a **derivative work under copyright law**.
   - If a library is licensed under **GPL-2.0, GPL-3.0, or AGPL-3.0**, bundling it directly into the APK **forces the entire APK's source code to be disclosed under the GPL**.
2. **LGPL on Android:** While LGPL allows dynamic linking on desktop operating systems (where users can replace the shared `.so`), on Android, application sandboxing and cryptographic APK signing make it practically impossible for end users to replace an LGPL `.so` without re-signing the APK. Consequently, using LGPL libraries inside an APK poses significant legal ambiguity and risk unless distributed strictly with relinkable object code.
3. **Companion Architecture as a Legal Firewall:** To utilize powerful GPL-licensed systems (such as Termux, bash, git-core, coreutils), the architecture employs **process-level isolation over network/IPC boundaries**.
   - Termux runs in a completely separate Linux process with a distinct Android UID.
   - Communication between the main APK and Termux occurs strictly over **Inter-Process Communication (IPC)**: Android Intents (`com.termux.RUN_COMMAND`), standard Unix domain sockets, or localhost HTTP/WebSocket JSON-RPC.
   - Under established Free Software Foundation (FSF) and international copyright precedents, communication between independent programs across pipes, sockets, and command-line arguments **does NOT create a derivative work** and avoids viral copyleft contagion.

---

## Detailed Repository Legal Analysis

### 1. OpenCode
- **Declared License:** MIT
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES (Include copyright notice and permission notice)
- **Copyleft Requirements:** NONE
- **Static / Dynamic Linking Implications:** Permissive; does not infect host code.
- **Source Disclosure Requirements:** NONE
- **Trademark Restrictions:** Standard (cannot use SST/OpenCode trademarks for endorsement).
- **Proprietary Android App Compatibility:** YES, safe.
- **Recommended Distribution:** Hosted inside Termux / Companion environment as a headless Node.js service communicating via HTTP/WebSocket.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 2. Aider
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES (NOTICE file and Apache 2.0 license)
- **Copyleft Requirements:** NONE
- **Static / Dynamic Linking Implications:** Safe.
- **Patent Grant:** YES (Express grant of patent rights from contributors).
- **Proprietary Android App Compatibility:** Safe, but retained as architectural reference due to runtime weight.
- **Classification:** **REFERENCE_ONLY**

---

### 3. TermFold
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES
- **Copyleft Requirements:** NONE
- **Static / Dynamic Linking Implications:** C++ JNI code (`libproot.so`) compiled via NDK. Note: PRoot original source contains parts under GPL-2.0. If TermFold embeds vanilla PRoot, it triggers GPL-2.0 obligations for the binary component.
- **Legal Caveat:** Vanilla PRoot is GPL-2.0. Clean-room ptrace wrappers or Apache-licensed PRoot forks must be verified. If PRoot C sources are GPL-2.0, the resulting `.so` binary must either be distributed under GPL or isolated in a separate companion APK.
- **Classification:** **LEGAL_REVIEW_REQUIRED** (Safe to compile as companion binary; needs license verification of PRoot JNI upstream).

---

### 4. Termux (App & Bootstrap)
- **Declared License:** GPL-3.0
- **Commercial Use:** PERMITTED (under GPL-3.0 terms)
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** Full GPL-3.0 text and copyright notices.
- **Copyleft Requirements:** **STRICT VIRAL COPYLEFT**. Any software linking or embedding Termux code directly becomes subject to GPL-3.0.
- **Static / Dynamic Linking Implications:** **FATAL if embedded inside the main APK.**
- **Source Disclosure Requirements:** Full source code of the binary must be disclosed if distributed.
- **Proprietary Android App Compatibility:** **CANNOT BE EMBEDDED IN APK.**
- **Legal Isolation Solution:** Distributed as an independent, standalone **COMPANION APPLICATION**. The main app sends IPC Intents with `READ_EXTERNAL_STORAGE` or explicit permission tokens. There is zero code linking.
- **Classification:** **COMPANION_ONLY**

---

### 5. Mobile-Harness
- **Declared License:** Apache 2.0
- **Classification:** **REJECTED** (Non-mobile architecture).

---

### 6. NeuralBridge
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES (Standard Apache 2.0 attribution)
- **Copyleft Requirements:** NONE
- **Static / Dynamic Linking:** Fully compatible with proprietary Kotlin applications.
- **Patent Grant:** Express Apache 2.0 patent protection.
- **Proprietary Android App Compatibility:** 100% compliant.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 7. Aster MCP
- **Declared License:** MIT
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES (Include MIT license notice)
- **Copyleft Requirements:** NONE
- **Proprietary Android App Compatibility:** 100% compliant.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 8. Assists
- **Declared License:** GPL-2.0
- **Copyleft Requirements:** Viral copyleft on all linked Android source code.
- **Proprietary Android App Compatibility:** INCOMPATIBLE.
- **Classification:** **REJECTED** (Redundant with Apache-2.0 NeuralBridge).

---

### 9. bwb-browser
- **Declared License:** MIT
- **Commercial Use:** PERMITTED
- **Classification:** **REFERENCE_ONLY** (Retained for CDP schema definitions).

---

### 10. Termux Browser Pilot
- **Declared License:** Apache 2.0
- **Classification:** **REJECTED** (Impractical runtime architecture).

---

### 11. PageKit
- **Declared License:** MIT
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES
- **Copyleft Requirements:** NONE
- **Proprietary Android App Compatibility:** 100% compliant. Can be embedded directly as a Kotlin/JVM dependency.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 12. Sifthound
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Attribution Required:** YES
- **Proprietary Android App Compatibility:** Compliant.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 13. SearXNG
- **Declared License:** AGPL-3.0 (Affero General Public License)
- **Copyleft Requirements:** Extreme network copyleft. Any network interaction with modified code requires offering full source code to all network users.
- **Proprietary Android App Compatibility:** **HIGH LEGAL HAZARD**.
- **Classification:** **REJECTED** (Use standard client-side HTTPS queries to public endpoints).

---

### 14. ZVec
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Attribution Required:** YES
- **Copyleft Requirements:** NONE
- **Proprietary Android App Compatibility:** 100% compliant. Compiles into `libzvec.so` via Android NDK.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 15. libSQL
- **Declared License:** MIT
- **Commercial Use:** PERMITTED
- **Attribution Required:** YES
- **Proprietary Android App Compatibility:** 100% compliant.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 16. SQLite / Room (Android Jetpack)
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Attribution Required:** Standard Android Open Source Project attribution.
- **Copyleft Requirements:** NONE
- **Proprietary Android App Compatibility:** Official standard Android framework component.
- **Classification:** **APPROVED**

---

### 17. OmniRoute
- **Declared License:** MIT
- **Commercial Use:** PERMITTED
- **Attribution Required:** YES
- **Classification:** **REFERENCE_ONLY** (Pattern reimplemented in pure Kotlin).

---

### 18. LiteLLM
- **Declared License:** MIT
- **Classification:** **REFERENCE_ONLY**

---

### 19. LiteRT-LM (Google AI Edge)
- **Declared License:** Apache 2.0
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES
- **Copyleft Requirements:** NONE
- **Proprietary Android App Compatibility:** Official Google on-device AI runtime; safe for commercial distribution.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 20. LocalMind
- **Declared License:** MIT (llama.cpp upstream is MIT)
- **Commercial Use:** PERMITTED
- **Attribution Required:** YES
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 21. MLC-LLM
- **Declared License:** Apache 2.0
- **Classification:** **REFERENCE_ONLY**

---

### 22. UI-Venus
- **Declared License:** Apache 2.0 (Code) / Open Model License (Weights)
- **Commercial Use:** PERMITTED
- **Attribution Required:** YES
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 23. FunctionGemma
- **Declared License:** Gemma Terms of Use (Open Weights)
- **Commercial Use:** PERMITTED (Subject to Google Gemma Acceptable Use Policy)
- **Attribution Required:** YES
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

### 24. JGit
- **Declared License:** EDL-1.0 (Eclipse Distribution License - BSD 3-Clause variant)
- **Commercial Use:** PERMITTED
- **Modification:** PERMITTED
- **Redistribution:** PERMITTED
- **Attribution Required:** YES
- **Copyleft Requirements:** **NONE**. (Crucial: Unlike core Git which is GPL-2.0, JGit was specifically created under EDL-1.0 to allow commercial embedding in proprietary JVM software without viral licensing).
- **Proprietary Android App Compatibility:** 100% compliant.
- **Classification:** **APPROVED_WITH_ATTRIBUTION**

---

## License Classification Matrix

| Project | License Type | Distribution Category | Verdict |
|---|---|---|---|
| **Room** | Apache 2.0 | Embedded APK | **APPROVED** |
| **NeuralBridge** | Apache 2.0 | Embedded APK | **APPROVED_WITH_ATTRIBUTION** |
| **Aster MCP** | MIT | Embedded APK | **APPROVED_WITH_ATTRIBUTION** |
| **PageKit** | MIT | Embedded APK | **APPROVED_WITH_ATTRIBUTION** |
| **ZVec** | Apache 2.0 | Embedded APK (`.so`) | **APPROVED_WITH_ATTRIBUTION** |
| **JGit** | EDL-1.0 (BSD) | Embedded APK | **APPROVED_WITH_ATTRIBUTION** |
| **LiteRT-LM** | Apache 2.0 | Embedded APK (Optional AAR) | **APPROVED_WITH_ATTRIBUTION** |
| **OpenCode** | MIT | Companion / Termux Daemon | **APPROVED_WITH_ATTRIBUTION** |
| **Sifthound** | Apache 2.0 | Embedded / JNI | **APPROVED_WITH_ATTRIBUTION** |
| **LocalMind** | MIT | Optional JNI | **APPROVED_WITH_ATTRIBUTION** |
| **FunctionGemma** | Gemma Terms | Optional Model Weights | **APPROVED_WITH_ATTRIBUTION** |
| **UI-Venus** | Apache 2.0 | Optional Model Weights | **APPROVED_WITH_ATTRIBUTION** |
| **libSQL** | MIT | Backup Embedded Engine | **APPROVED_WITH_ATTRIBUTION** |
| **TermFold** | Apache 2.0 (PRoot GPL?) | Embedded / Subprocess | **LEGAL_REVIEW_REQUIRED** |
| **Termux** | GPL-3.0 | Companion App Only | **COMPANION_ONLY** |
| **Aider** | Apache 2.0 | Reference / Prompting | **REFERENCE_ONLY** |
| **bwb-browser** | MIT | Reference / CDP Protocol | **REFERENCE_ONLY** |
| **OmniRoute** | MIT | Reference / Architecture | **REFERENCE_ONLY** |
| **LiteLLM** | MIT | Reference / Schema | **REFERENCE_ONLY** |
| **MLC-LLM** | Apache 2.0 | Reference | **REFERENCE_ONLY** |
| **Assists** | GPL-2.0 | Incompatible | **REJECTED** |
| **SearXNG** | AGPL-3.0 | Incompatible | **REJECTED** |
| **Termux Browser Pilot** | Apache 2.0 | Redundant / Heavy | **REJECTED** |
| **Mobile-Harness** | Apache 2.0 | Non-Android | **REJECTED** |
