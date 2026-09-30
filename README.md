# AgentOS — Personal Android Multi-Agent AI System

AgentOS is a native Android (Kotlin & Jetpack Compose) Multi-Agent AI Operating Environment designed specifically for standard, unrooted Android devices (Android 9+ / API 28+).

It combines:
- **Main Agent Orchestrator:** Goal understanding, task decomposition, sub-agent delegation, self-verification, and checkpoint tracking.
- **Model Context Protocol (MCP) Client:** Standard JSON-RPC 2.0 tool registry with risk interceptors.
- **Phone Automation Agent:** Android package management, device state inspection, and accessibility gesture primitives.
- **Browser Agent:** Headless Android WebView with DOM extraction and JavaScript execution.
- **Coding Agent & OpenCode Bridge:** Localhost HTTP/SSE companion client (`127.0.0.1:4096`) for autonomous AST code generation and diffing.
- **Terminal & Shell Environment:** Sandboxed non-root POSIX shell execution with file management.
- **Embedded Git Engine:** Repository clone, diff, log, status, and commit.
- **Web Search Engine:** Non-tracking multi-engine web search (DuckDuckGo HTML parser) and clean webpage text extraction.
- **Persistent Long-Term Memory:** Jetpack Room & SQLite database for structured facts, preferences, and task context.
- **Multi-Model Router:** Unified streaming client for Google Gemini, OpenAI, Qwen, GLM, and local on-device models.
