# Development Guide

## Requirements

- JDK 17+ (tested with OpenJDK 21)
- Android SDK: platform 34, build-tools 34.0.0, platform-tools
- Android Studio (optional — the project is plain Gradle)

## Project layout

```
app/src/main/java/com/agentos/app/
├── AgentOsApp.kt            # Application + Koin start
├── MainActivity.kt          # 4 tabs + MediaProjection consent bridge
├── core/
│   ├── logging/Logger.kt    # leveled logger + ring buffer + Redactor
│   ├── security/            # SecretStore interface + SecureStore (Keystore)
│   └── network/             # ConnectivityChecker + NetworkMonitor
├── domain/
│   ├── model/Models.kt      # Task/Step/ChatMessage/TaskEvent types
│   ├── agent/               # Agent contract + 6 agents + AgentRegistry
│   ├── tools/Tool.kt        # Tool contract + ToolRegistry + CommandGuard
│   └── engine/              # TaskEngine + PlanParser + ApprovalManager
├── data/
│   ├── provider/            # AIProvider, OpenAI-compatible, Gemini, fallback
│   ├── tools/               # real tool implementations (web/browser/shell/…)
│   ├── browser/BrowserEngine.kt   # headless WebView
│   ├── termux/TermuxBridge.kt     # RUN_COMMAND + result files
│   ├── mcp/McpClient.kt           # JSON-RPC MCP client + manager
│   ├── accessibility/             # Android automation tools
│   ├── db/AppDatabase.kt          # Room (5 entities)
│   ├── settings/SettingsRepository.kt  # DataStore
│   └── repo/                # Chat/Task/Memory repositories
├── service/                 # AccessibilityService + capture/task FGS
├── ui/                      # Compose screens + ViewModels
└── di/AppModules.kt         # Koin wiring
```

## Build & test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint                # optional
```

`local.properties` → `sdk.dir=/path/to/android-sdk` (or `ANDROID_HOME` env).

## Release builds

Provide your own keystore via environment variables and sign yourself:

```bash
keytool -genkeypair -v -keystore my-release.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then build unsigned release and sign with `apksigner` (documented flow; the repo
intentionally contains **no** keystores or credentials).

## Conventions

- **No fake features** — if a feature can't run, it fails with an actionable error.
  Never return simulated success (this is the project's core rule).
- Every tool validates inputs and returns `ToolResult(success=false, error=…)` on failure.
- New user-visible strings: keep in code is fine (single-locale for now), Arabic/English
  UI language follows the user's provider responses.
- Tests: pure-JVM where possible; Robolectric for Android-framework paths; MockWebServer
  for HTTP clients. Fakes are allowed **only** in tests.

## Debugging tips

- In-app Settings → Logs shows the last 500 entries with secrets redacted.
- `adb logcat -s AgentOS.*` mirrors the same entries.
- Termux issues: run Settings → Termux → Test connection; it prints the exact fix
  (usually `allow-external-apps=true` + restart).
- Accessibility: check `Settings → Accessibility` shows AgentOS enabled; tools return
  explicit "service not enabled" errors otherwise.

## Versioning

- `versionCode`/`versionName` in `app/build.gradle.kts`. Schema version of Room DB is 1;
  destructive migration is enabled for dev — write real migrations before shipping v2.
