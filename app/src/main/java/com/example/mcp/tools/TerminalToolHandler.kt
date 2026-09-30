package com.example.mcp.tools

import android.content.Context
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class TerminalToolHandler(
    private val context: Context,
    private val defaultWorkDir: File = File(context.filesDir, "workspace").apply { mkdirs() }
) : McpToolHandler {

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult {
        return when (toolName) {
            "terminal.exec" -> {
                val cmd = arguments.optString("command")
                if (cmd.isBlank()) {
                    return ToolExecutionResult(false, "", "No command provided")
                }
                val cwdPath = arguments.optString("cwd", defaultWorkDir.absolutePath)
                val cwd = File(cwdPath).takeIf { it.exists() && it.isDirectory } ?: defaultWorkDir
                val timeout = arguments.optLong("timeout_ms", 30000L)
                executeShell(cmd, cwd, timeout)
            }
            "terminal.read_file" -> {
                val path = arguments.optString("path")
                val file = resolveFile(path)
                if (!file.exists()) {
                    return ToolExecutionResult(false, "", "File not found: ${file.path}")
                }
                val content = file.readText()
                val offset = arguments.optInt("offset", 0)
                val limit = arguments.optInt("limit", 2000)
                val lines = content.lines()
                val sublines = lines.drop(offset).take(limit)
                ToolExecutionResult(true, sublines.joinToString("\n"))
            }
            "terminal.write_file" -> {
                val path = arguments.optString("path")
                val content = arguments.optString("content")
                val file = resolveFile(path)
                file.parentFile?.mkdirs()
                file.writeText(content)
                ToolExecutionResult(true, "Successfully wrote ${content.length} characters to ${file.name}")
            }
            "terminal.list_dir" -> {
                val path = arguments.optString("path", defaultWorkDir.absolutePath)
                val dir = resolveFile(path)
                if (!dir.exists() || !dir.isDirectory) {
                    return ToolExecutionResult(false, "", "Not a directory: ${dir.path}")
                }
                val entries = dir.listFiles()?.map { f ->
                    "${if (f.isDirectory) "[DIR] " else "[FILE]"} ${f.name} (${f.length()} B)"
                }?.sorted() ?: emptyList()
                ToolExecutionResult(true, entries.joinToString("\n"))
            }
            else -> ToolExecutionResult(false, "", "Unknown terminal action $toolName")
        }
    }

    private fun resolveFile(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(defaultWorkDir, path)
    }

    private fun executeShell(command: String, cwd: File, timeoutMs: Long): ToolExecutionResult {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(cwd)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            if (!finished) {
                process.destroyForcibly()
                return ToolExecutionResult(false, output.toString(), "Command timed out after ${timeoutMs}ms")
            }

            val exitCode = process.exitValue()
            ToolExecutionResult(
                success = exitCode == 0,
                output = output.toString(),
                error = if (exitCode != 0) "Process exited with code $exitCode" else null
            )
        } catch (e: Exception) {
            ToolExecutionResult(false, "", e.localizedMessage ?: "Failed to execute shell command")
        }
    }

    companion object {
        val EXEC = McpToolDefinition(
            name = "terminal.exec",
            description = "Executes a non-root shell command inside the workspace directory.",
            riskLevel = ToolRiskLevel.HIGH_RISK,
            schemaJson = """{"command":{"type":"string"},"cwd":{"type":"string"},"timeout_ms":{"type":"integer"}}""",
            category = "Terminal"
        )
        val READ_FILE = McpToolDefinition(
            name = "terminal.read_file",
            description = "Reads content from a project or workspace file.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"path":{"type":"string"},"offset":{"type":"integer"},"limit":{"type":"integer"}}""",
            category = "Terminal"
        )
        val WRITE_FILE = McpToolDefinition(
            name = "terminal.write_file",
            description = "Writes text content to a project or workspace file.",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"path":{"type":"string"},"content":{"type":"string"}}""",
            category = "Terminal"
        )
        val LIST_DIR = McpToolDefinition(
            name = "terminal.list_dir",
            description = "Lists files and directories in the workspace.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"path":{"type":"string"}}""",
            category = "Terminal"
        )
    }
}
