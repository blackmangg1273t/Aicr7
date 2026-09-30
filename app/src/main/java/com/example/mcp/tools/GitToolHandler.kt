package com.example.mcp.tools

import android.content.Context
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import org.json.JSONObject
import java.io.File

class GitToolHandler(
    private val context: Context,
    private val workspaceDir: File = File(context.filesDir, "workspace").apply { mkdirs() }
) : McpToolHandler {

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult {
        return when (toolName) {
            "git.status" -> {
                val repoPath = arguments.optString("path", workspaceDir.absolutePath)
                executeGitCommand("git status", File(repoPath))
            }
            "git.clone" -> {
                val url = arguments.optString("url")
                if (url.isBlank()) {
                    return ToolExecutionResult(false, "", "Git clone URL is missing")
                }
                val dirName = arguments.optString("dir_name", url.substringAfterLast("/").removeSuffix(".git"))
                val targetDir = File(workspaceDir, dirName)
                executeGitCommand("git clone $url ${targetDir.name}", workspaceDir)
            }
            "git.diff" -> {
                val repoPath = arguments.optString("path", workspaceDir.absolutePath)
                executeGitCommand("git diff", File(repoPath))
            }
            "git.log" -> {
                val repoPath = arguments.optString("path", workspaceDir.absolutePath)
                val count = arguments.optInt("count", 5)
                executeGitCommand("git log -n $count --oneline", File(repoPath))
            }
            "git.commit" -> {
                val repoPath = arguments.optString("path", workspaceDir.absolutePath)
                val message = arguments.optString("message", "Commit from AgentOS")
                executeGitCommand("git add -A && git commit -m \"$message\"", File(repoPath))
            }
            else -> ToolExecutionResult(false, "", "Unknown git tool $toolName")
        }
    }

    private fun executeGitCommand(command: String, workingDir: File): ToolExecutionResult {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(if (workingDir.exists()) workingDir else workspaceDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            ToolExecutionResult(
                success = exitCode == 0,
                output = output.ifBlank { "Git command completed successfully (exit code $exitCode)." },
                error = if (exitCode != 0) output else null
            )
        } catch (e: Exception) {
            ToolExecutionResult(false, "", e.localizedMessage ?: "Git command failed")
        }
    }

    companion object {
        val GIT_STATUS = McpToolDefinition(
            name = "git.status",
            description = "Checks the Git status (modified, untracked, staged files) of a workspace repository.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"path":{"type":"string"}}""",
            category = "Git"
        )
        val GIT_CLONE = McpToolDefinition(
            name = "git.clone",
            description = "Clones a remote Git repository into the workspace.",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"url":{"type":"string"},"dir_name":{"type":"string"}}""",
            category = "Git"
        )
        val GIT_DIFF = McpToolDefinition(
            name = "git.diff",
            description = "Shows unstaged or staged code diffs.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"path":{"type":"string"}}""",
            category = "Git"
        )
        val GIT_LOG = McpToolDefinition(
            name = "git.log",
            description = "Displays the recent commit log.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"path":{"type":"string"},"count":{"type":"integer"}}""",
            category = "Git"
        )
        val GIT_COMMIT = McpToolDefinition(
            name = "git.commit",
            description = "Stages and commits changes in the repository.",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"path":{"type":"string"},"message":{"type":"string"}}""",
            category = "Git"
        )
    }
}
