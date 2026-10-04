package com.agentos.app.data.db

import androidx.room.Dao
import androidx.room.Room
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/* ---------------------- Entities ---------------------- */

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "messages", indices = [androidx.room.Index("conversationId")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val agentName: String?,
    val eventType: String?,
    val toolName: String?,
    val argsJson: String?,
    val severity: String?,
    val approvalMessageId: String?,
    val attachmentPath: String?,
    val timestamp: Long
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userRequest: String,
    val status: String,
    val finalResult: String?,
    val error: String?,
    val conversationId: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "task_steps", indices = [androidx.room.Index("taskId")])
data class TaskStepEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val index: Int,
    val agentName: String,
    val toolName: String?,
    val argsJson: String,
    val why: String,
    val status: String,
    val result: String?,
    val error: String?,
    val startedAt: Long?,
    val finishedAt: Long?
)

@Entity(tableName = "memories", indices = [androidx.room.Index(value = ["key"], unique = true)])
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val content: String,
    val category: String,
    val timestamp: Long
)

/* ---------------------- DAOs ---------------------- */

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(c: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC LIMIT 1")
    suspend fun mostRecent(): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: String): ConversationEntity?

    @Query("UPDATE conversations SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(m: MessageEntity)

    @Update
    suspend fun update(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun forConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: String): MessageEntity?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(t: TaskEntity)

    @Update
    suspend fun update(t: TaskEntity)

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Query("DELETE FROM task_steps WHERE taskId = :taskId")
    suspend fun clearSteps(taskId: String)

    @Query("DELETE FROM task_steps")
    suspend fun clearAllSteps()
}

@Dao
interface TaskStepDao {
    @Insert
    suspend fun insert(s: TaskStepEntity)

    @Update
    suspend fun update(s: TaskStepEntity)

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY `index` ASC")
    suspend fun forTask(taskId: String): List<TaskStepEntity>

    @Query("SELECT * FROM task_steps WHERE taskId = :taskId ORDER BY `index` ASC")
    fun observeForTask(taskId: String): Flow<List<TaskStepEntity>>
}

@Dao
interface MemoryDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(m: MemoryEntity)

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun byKey(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR `key` LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 5): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 20): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("DELETE FROM memories WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

/* ---------------------- Database ---------------------- */

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        TaskEntity::class,
        TaskStepEntity::class,
        MemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun taskDao(): TaskDao
    abstract fun taskStepDao(): TaskStepDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        fun build(context: android.content.Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "agentos.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
