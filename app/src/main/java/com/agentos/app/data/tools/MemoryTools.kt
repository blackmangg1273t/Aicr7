package com.agentos.app.data.tools

import com.agentos.app.data.repo.MemoryRepository
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.serialization.json.JsonObject

/** Real persistent memory tools backed by Room. */
class MemoryTools(private val memory: MemoryRepository) {

    inner class StoreTool : Tool {
        override val name = "memory_store"
        override val description = "Stores a durable fact, preference or context snippet in the agent's long-term memory."
        override val risk = ToolRisk.MODERATE
        override val category = "Memory"
        override val inputSchema = """{"key": "string (required, unique identifier)", "content": "string (required)", "category": "string (optional)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val key = Args.str(args, "key").trim()
            val content = Args.str(args, "content").trim()
            if (key.isBlank() || content.isBlank()) return ToolResult(false, "", "key and content are required")
            memory.store(key, content, Args.str(args, "category", "general").ifBlank { "general" })
            return ToolResult(true, "Stored memory '$key'")
        }
    }

    inner class SearchTool : Tool {
        override val name = "memory_search"
        override val description = "Searches long-term memory by keyword."
        override val risk = ToolRisk.SAFE
        override val category = "Memory"
        override val inputSchema = """{"query": "string (required)", "limit": "int (default 5)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val query = Args.str(args, "query").trim()
            if (query.isBlank()) return ToolResult(false, "", "query is required")
            val results = memory.search(query, Args.int(args, "limit", 5))
            return if (results.isEmpty()) ToolResult(true, "No memories match '$query'")
            else ToolResult(true, results.joinToString("\n---\n") { "[${it.key}] (${it.category}): ${it.content}" })
        }
    }

    inner class DeleteTool : Tool {
        override val name = "memory_delete"
        override val description = "Deletes a memory entry by key."
        override val risk = ToolRisk.HIGH_RISK
        override val category = "Memory"
        override val inputSchema = """{"key": "string (required)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val key = Args.str(args, "key").trim()
            if (key.isBlank()) return ToolResult(false, "", "key is required")
            val n = memory.delete(key)
            return ToolResult(true, "Deleted $n memory entries for '$key'")
        }
    }

    fun all(): List<Tool> = listOf(StoreTool(), SearchTool(), DeleteTool())
}
