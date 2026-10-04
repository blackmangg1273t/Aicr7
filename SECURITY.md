# Security

## Threat model

AgentOS can execute real actions on the device and network. The primary risks:
secret leakage, unintended destructive actions, prompt-injected tool misuse,
and path/command injection.

## Secrets

- API keys are stored **only** in `SecureStore` — EncryptedSharedPreferences with an
  Android Keystore master key (AES-256-GCM). Never in DataStore, never in code, never in
  the APK (no BuildConfig secrets, no `.env` packaging — the old approach was removed).
- Keys are excluded from Android backup / device transfer (`backup_rules.xml`,
  `data_extraction_rules.xml`).
- The logger runs every message through `Redactor` (sk-…, AIza…, ghp_/github_pat_…,
  Bearer …, key=value forms) before buffering, logging or display. Unit-tested.

## Approval gating

- `ToolRisk.HIGH_RISK` tools (`shell_exec`, `file_delete`, `memory_delete`) never run
  without an explicit user approval card in the chat. Denial stops the task and is
  recorded in the step error.
- The approval prompt shows the tool name and full arguments so the user can judge.

## Command & path safety

- `CommandGuard` blocks clearly destructive shell patterns (rm -rf /, mkfs, dd to block
  devices, fork bombs, poweroff/reboot, sudo/su, curl|sh, remount, nuking $PREFIX) —
  before and independently of approval. Unit-tested.
- File tools are sandboxed to the app workspace: canonical paths must stay inside
  `filesDir/workspace`; traversal like `../../etc/passwd` is rejected. Unit-tested.
- URLs are validated to http/https only before any fetch/open.

## Injection resistance

- Planner output is strictly parsed (JSON only, unknown agents dropped, unknown tools
  rejected by the registry, agents can't use tools outside their allowlist).
- Tool inputs are validated (`Args` helpers); missing/invalid → failure, no guessing.
- MCP servers are user-configured (your own endpoints), auth header supported, and their
  tools run with `MODERATE` risk by default so they are visible/logged.

## Local data

- Room DB lives in app-private storage. Privacy settings allow wiping chats, tasks,
  memories, logs and all secrets independently.
- `allowBackup=false`; secure prefs excluded from any backup/transfer.

## Network

- All HTTP goes through OkHttp with explicit timeouts; provider errors surface real
  HTTP codes and bodies (truncated) to the user.
- Offline state is detected via ConnectivityManager; network tools fail fast with an
  offline message instead of hanging or faking.

## Known residual risks

- A user-approved shell command is arbitrary by definition — the approval UI is the control.
- MCP servers you connect are trusted code endpoints; only add servers you control.
- EncryptedSharedPreferences relies on device Keystore integrity (factory-reset protection).
