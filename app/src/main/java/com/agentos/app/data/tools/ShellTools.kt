package com.agentos.app.data.tools

import android.content.Context
import com.agentos.app.core.logging.Logger
import com.agentos.app.data.termux.TermuxBridge
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.CommandGuard
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Shell/file tools. Real execution paths:
 * - "termux": via TermuxBridge RUN_COMMAND (full Linux userland when configured)
 * - "local": app-sandbox ProcessBuilder with toybox utilities (always available)
 */
class ShellTools(
    private val appContext: Context,
    private val termux: TermuxBridge,
    private val settings: com.agentos.app.data.settings.SettingsRepository
) {

    val workspace: File = File(appContext.filesDir, "workspace").apply { mkdirs() }

    fun resolveInWorkspace(path: String): File {
        val f = File(path)
        val candidate = if (f.isAbsolute) f else File(workspace, path)
        val canonical = candidate.canonicalFile
        // Path-sandboxing: file tools never escape the workspace
        if (!canonical.path.startsWith(workspace.canonicalPath)) {
            throw SecurityException("Path escapes workspace sandbox: $path")
        }
        return canonical
    }

    inner class ShellExecTool : Tool {
        override val name = "shell_exec"
        override val description =
            "Executes a shell command. Uses Termux (full Linux userland) when available, otherwise the app sandbox shell. Output, errors and exit code are captured."
        override val risk = ToolRisk.HIGH_RISK
        override val category = "Terminal"
        override val inputSchema = """{"command": "string (required)", "cwd": "string (optional working dir inside workspace)", "timeout_ms": "int (default 60000)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val command = Args.str(args, "command").trim()
            if (command.isBlank()) return ToolResult(false, "", "command is required")
            CommandGuard.check(command)?.let { return ToolResult(false, "", it) }
            val timeout = Args.long(args, "timeout_ms", 60_000L).coerceIn(1_000, 300_000)
            val cwdRel = Args.str(args, "cwd")

            ctx.onActivity("Executing: ${command.take(60)}")
            return withContext(Dispatchers.IO) {
                // Try Termux first when preferred & installed
                // Read the live setting at execution time (IO context) so toggling
                // "prefer Termux" in Settings applies immediately, without an app restart
                val preferTermux = settings.shellFlow.first().preferTermux
                if (preferTermux && termux.isInstalled()) {
                    try {
                        val workdir = if (cwdRel.isNotBlank()) termuxWorkdir(cwdRel) else null
                        val r = termux.execute(command, workdir, timeout)
                        val out = buildString {
                            if (r.stdout.isNotBlank()) append(r.stdout)
                            if (r.stderr.isNotBlank()) append(if (isEmpty()) "" else "\n").append("[stderr] ").append(r.stderr)
                        }
                        if (r.timedOut) {
                            ToolResult(false, out, "Command timed out after ${timeout}ms (Termux)")
                        } else if (r.success()) {
                            ToolResult(true, out.ifBlank { "(no output, exit 0)" })
                        } else {
                            ToolResult(false, out, "Command exited with code ${r.exitCode}")
                        }
                    } catch (e: TermuxBridge.TermuxUnavailable) {
                        Logger.w("ShellTools", "Termux unavailable: ${e.message}; falling back to local shell")
                        localExec(command, cwdRel, timeout)
                    }
                } else {
                    localExec(command, cwdRel, timeout)
                }
            }
        }
    }

    private fun termuxWorkdir(cwdRel: String): String? {
        val inside = resolveInWorkspace(cwdRel)
        return if (inside.exists() && inside.isDirectory) inside.absolutePath else null
    }

    private fun localExec(command: String, cwdRel: String, timeout: Long): ToolResult {
        return try {
            val cwd = if (cwdRel.isNotBlank()) runCatching { resolveInWorkspace(cwdRel) }.getOrNull()?.takeIf { it.isDirectory } ?: workspace else workspace
            val process = ProcessBuilder("sh", "-c", command)
                .directory(cwd)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                ToolResult(false, stdout.take(4000), "Command timed out after ${timeout}ms (local shell)")
            } else {
                val exit = process.exitValue()
                val out = buildString {
                    if (stdout.isNotBlank()) append(stdout)
                    if (stderr.isNotBlank()) append(if (isEmpty()) "" else "\n").append("[stderr] ").append(stderr)
                }
                if (exit == 0) ToolResult(true, out.ifBlank { "(no output, exit 0)" })
                else ToolResult(false, out, "Command exited with code $exit")
            }
        } catch (e: IOException) {
            ToolResult(false, "", "Local shell failed to start: ${e.message}")
        } catch (e: InterruptedException) {
            ToolResult(false, "", "Command interrupted")
        }
    }

    /* ------------------------- file tools ------------------------- */

    inner class FileReadTool : Tool {
        override val name = "file_read"
        override val description = "Reads a text file from the agent workspace."
        override val risk = ToolRisk.SAFE
        override val category = "Files"
        override val inputSchema = """{"path": "string (required, relative to workspace)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val path = Args.str(args, "path")
            if (path.isBlank()) return ToolResult(false, "", "path is required")
            return try {
                val f = resolveInWorkspace(path)
                if (!f.exists()) return ToolResult(false, "", "File not found: $path")
                if (f.length() > 2_000_000) return ToolResult(false, "", "File too large (${f.length()} bytes)")
                ToolResult(true, f.readText())
            } catch (e: SecurityException) { ToolResult(false, "", e.message ?: "Access denied") }
            catch (e: Exception) { ToolResult(false, "", "file_read failed: ${e.message}") }
        }
    }

    inner class FileWriteTool : Tool {
        override val name = "file_write"
        override val description = "Creates or overwrites a text file in the agent workspace."
        override val risk = ToolRisk.MODERATE
        override val category = "Files"
        override val inputSchema = """{"path": "string (required)", "content": "string (required)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val path = Args.str(args, "path")
            if (path.isBlank()) return ToolResult(false, "", "path is required")
            val content = Args.str(args, "content")
            return try {
                val f = resolveInWorkspace(path)
                f.parentFile?.mkdirs()
                f.writeText(content)
                ToolResult(true, "Wrote ${content.length} chars to $path")
            } catch (e: SecurityException) { ToolResult(false, "", e.message ?: "Access denied") }
            catch (e: Exception) { ToolResult(false, "", "file_write failed: ${e.message}") }
        }
    }

    inner class FileListTool : Tool {
        override val name = "file_list"
        override val description = "Lists files and folders in the agent workspace (or a subfolder)."
        override val risk = ToolRisk.SAFE
        override val category = "Files"
        override val inputSchema = """{"path": "string (optional, default '.')"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val path = Args.str(args, "path", ".").ifBlank { "." }
            return try {
                val dir = resolveInWorkspace(path)
                if (!dir.isDirectory) return ToolResult(false, "", "Not a directory: $path")
                val entries = dir.listFiles()
                    ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
                    ?.joinToString("\n") { f ->
                        if (f.isDirectory) "[dir]  ${f.name}/" else "[file] ${f.name}  (${f.length()} B)"
                    } ?: ""
                ToolResult(true, entries.ifBlank { "(empty)" })
            } catch (e: SecurityException) { ToolResult(false, "", e.message ?: "Access denied") }
            catch (e: Exception) { ToolResult(false, "", "file_list failed: ${e.message}") }
        }
    }

    inner class FileDeleteTool : Tool {
        override val name = "file_delete"
        override val description = "Deletes a file inside the agent workspace. Requires user approval."
        override val risk = ToolRisk.HIGH_RISK
        override val category = "Files"
        override val inputSchema = """{"path": "string (required)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val path = Args.str(args, "path")
            if (path.isBlank()) return ToolResult(false, "", "path is required")
            return try {
                val f = resolveInWorkspace(path)
                if (!f.exists()) return ToolResult(false, "", "File not found: $path")
                val ok = f.delete()
                if (ok) ToolResult(true, "Deleted $path")
                else ToolResult(false, "", "Could not delete $path (directory not empty?)")
            } catch (e: SecurityException) { ToolResult(false, "", e.message ?: "Access denied") }
        }
    }

    inner class GitTool : Tool {
        override val name = "git"
        override val description =
            "Runs a git command (status|log|diff|clone|commit) in the workspace. Git binary comes from Termux; without Termux this fails with setup guidance."
        override val risk = ToolRisk.MODERATE
        override val category = "Coding"
        override val inputSchema = """{"action": "string: status|log|diff|clone|commit", "repo": "string (optional, workspace-relative)", "url": "string (clone only)", "message": "string (commit only)", "count": "int (log only, default 5)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val action = Args.str(args, "action")
            val repo = Args.str(args, "repo")
            val repoDir = if (repo.isNotBlank()) runCatching { resolveInWorkspace(repo) }.getOrNull() else workspace
            val command = when (action) {
                "status" -> "git status"
                "log" -> "git log -n ${Args.int(args, "count", 5)} --oneline"
                "diff" -> "git diff"
                "clone" -> {
                    val url = Args.str(args, "url")
                    if (url.isBlank()) return ToolResult(false, "", "url is required for git clone")
                    "git clone ${url.takeWhile { it.isLetterOrDigit() || it in "https:/.-_" }}"
                }
                "commit" -> {
                    val msg = Args.str(args, "message", "AgentOS commit").replace("\"", "")
                    "git add -A && git commit -m \"$msg\""
                }
                else -> return ToolResult(false, "", "Unknown git action '$action' (use status|log|diff|clone|commit)")
            }
            ctx.onActivity("git $action…")
            val shellResult = ShellExecTool().execute(
                JsonObject(mutableMapOf<String, kotlinx.serialization.json.JsonElement>().apply {
                    put("command", kotlinx.serialization.json.JsonPrimitive(command))
                    if (repoDir != null && action != "clone") put("cwd", kotlinx.serialization.json.JsonPrimitive(repoDir.absolutePath))
                }),
                ctx
            )
            return when {
                shellResult.error?.contains("not found", true) == true || shellResult.error?.contains("No such file", true) == true ->
                    ToolResult(false, shellResult.output,
                        "Git is not available. Install Termux, then inside Termux run: pkg install git. Original error: ${shellResult.error}")
                else -> shellResult
            }
        }
    }

    fun all(): List<Tool> = listOf(
        ShellExecTool(), FileReadTool(), FileWriteTool(), FileListTool(), FileDeleteTool(), GitTool()
    )
}
