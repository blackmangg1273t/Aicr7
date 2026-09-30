package com.example.mcp.tools

import android.content.Context
import androidx.room.*
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val content: String,
    val category: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 5): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 20): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

@Database(entities = [MemoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "agent_memory.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class MemoryToolHandler(
    context: Context,
    private val memoryDao: MemoryDao = AppDatabase.getDatabase(context).memoryDao()
) : McpToolHandler {

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult = withContext(Dispatchers.IO) {
        when (toolName) {
            "memory.store" -> {
                val key = arguments.optString("key")
                val content = arguments.optString("content")
                val category = arguments.optString("category", "general")
                if (key.isBlank() || content.isBlank()) {
                    return@withContext ToolExecutionResult(false, "", "Key and content are required")
                }
                memoryDao.insert(MemoryEntity(key = key, content = content, category = category))
                ToolExecutionResult(true, "Stored memory under key '$key' [category: $category]")
            }
            "memory.search" -> {
                val query = arguments.optString("query")
                val limit = arguments.optInt("limit", 5)
                val results = memoryDao.search(query, limit)
                if (results.isEmpty()) {
                    ToolExecutionResult(true, "No memories found matching '$query'")
                } else {
                    val text = results.joinToString("\n---\n") {
                        "[Key: ${it.key}] (${it.category}):\n${it.content}"
                    }
                    ToolExecutionResult(true, text)
                }
            }
            "memory.retrieve" -> {
                val key = arguments.optString("key")
                val memory = memoryDao.getByKey(key)
                if (memory != null) {
                    ToolExecutionResult(true, memory.content)
                } else {
                    ToolExecutionResult(false, "", "No memory found for key '$key'")
                }
            }
            "memory.delete" -> {
                val key = arguments.optString("key")
                val deleted = memoryDao.deleteByKey(key)
                ToolExecutionResult(true, "Deleted $deleted memory record(s) for key '$key'")
            }
            "memory.list_all" -> {
                val limit = arguments.optInt("limit", 20)
                val all = memoryDao.getAll(limit)
                if (all.isEmpty()) {
                    ToolExecutionResult(true, "Memory store is currently empty.")
                } else {
                    val text = all.joinToString("\n") {
                        "- ${it.key} [${it.category}]: ${it.content.take(80)}"
                    }
                    ToolExecutionResult(true, text)
                }
            }
            else -> ToolExecutionResult(false, "", "Unknown memory tool $toolName")
        }
    }

    companion object {
        val STORE = McpToolDefinition(
            name = "memory.store",
            description = "Stores a key-value fact, preference, or project context in long-term SQLite/Room memory.",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"key":{"type":"string"},"content":{"type":"string"},"category":{"type":"string"}}""",
            category = "Memory"
        )
        val SEARCH = McpToolDefinition(
            name = "memory.search",
            description = "Searches long-term memory for relevant facts or past results.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"query":{"type":"string"},"limit":{"type":"integer"}}""",
            category = "Memory"
        )
        val RETRIEVE = McpToolDefinition(
            name = "memory.retrieve",
            description = "Retrieves an exact memory item by key.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"key":{"type":"string"}}""",
            category = "Memory"
        )
        val DELETE = McpToolDefinition(
            name = "memory.delete",
            description = "Deletes a specific memory item by key.",
            riskLevel = ToolRiskLevel.HIGH_RISK,
            schemaJson = """{"key":{"type":"string"}}""",
            category = "Memory"
        )
        val LIST_ALL = McpToolDefinition(
            name = "memory.list_all",
            description = "Lists recent memory items.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"limit":{"type":"integer"}}""",
            category = "Memory"
        )
    }
}
