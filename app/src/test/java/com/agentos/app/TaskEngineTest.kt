package com.agentos.app

import android.app.Application
import androidx.room.Room
import com.agentos.app.core.network.ConnectivityChecker
import com.agentos.app.core.security.SecretStore
import com.agentos.app.data.db.AppDatabase
import com.agentos.app.data.db.ConversationEntity
import com.agentos.app.data.provider.AiExecutor
import com.agentos.app.data.provider.ChatTurn
import com.agentos.app.data.provider.ProviderConfig
import com.agentos.app.data.provider.StreamEvent
import com.agentos.app.data.repo.ChatRepository
import com.agentos.app.data.repo.MemoryRepository
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.domain.agent.AgentRegistry
import com.agentos.app.domain.agent.MainAgent
import com.agentos.app.domain.engine.TaskEngine
import com.agentos.app.domain.model.TaskEvent
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-end engine tests with a scripted (fake) AI executor — fakes are
 * allowed in TESTS only; production code contains none. The engine, agents,
 * tools, Room persistence and approval flow exercised here are the REAL
 * production implementations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: TaskEngine
    private lateinit var scope: CoroutineScope
    private val appContext get() = RuntimeEnvironment.getApplication() as Application

    private val planJson = """
        {"direct_answer": false, "answer": null, "steps": [
          {"agent": "terminal", "tool": "file_write", "args": {"path": "t.txt", "content": "engine-test"}, "why": "write file", "retry_on_failure": false},
          {"agent": "terminal", "tool": "file_read", "args": {"path": "t.txt"}, "why": "read back", "retry_on_failure": false}
        ]}
    """.trimIndent()

    private val directAnswerJson = """{"direct_answer": true, "answer": null, "steps": []}"""

    private inner class FakeExecutor(private val scripted: List<String>) : AiExecutor {
        var callIndex = 0

        override fun streamWithFallback(
            primary: ProviderConfig, primaryKey: String,
            fallback: ProviderConfig?, fallbackKey: String,
            messages: List<ChatTurn>
        ) = flow {
            val script = scripted.getOrElse(callIndex) { scripted.last() }
            callIndex++
            emit(StreamEvent.Chunk(script))
            emit(StreamEvent.Completed(script))
        }
    }

    private fun buildEngine(scripted: List<String>): TaskEngine {
        db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val settings = SettingsRepository(appContext)
        val fakeExecutor = FakeExecutor(scripted)
        val secretStore = object : SecretStore {
            private val map = mutableMapOf<String, String>()
            override fun getSecret(key: String): String? = map[key] ?: "test-key"
            override fun saveSecret(key: String, value: String) { map[key] = value }
            override fun deleteSecret(key: String) { map.remove(key) }
            override fun wipeAll() { map.clear() }
        }
        val connectivity = object : ConnectivityChecker {
            override fun isOnline(): Boolean = true
        }
        val toolRegistry = ToolRegistry()
        val toolCtx = ToolContext(appContext)
        toolRegistry.registerAll(
            com.agentos.app.data.tools.ShellTools(
                appContext,
                com.agentos.app.data.termux.TermuxBridge(appContext),
                preferTermux = false
            ).all()
        )
        val mainAgent = MainAgent(object : AiExecutor {
            override fun streamWithFallback(
                primary: ProviderConfig, primaryKey: String,
                fallback: ProviderConfig?, fallbackKey: String,
                messages: List<ChatTurn>
            ) = flow<StreamEvent> { throw UnsupportedOperationException("unused in test") }
        })
        val agents = AgentRegistry(
            listOf(
                mainAgent,
                com.agentos.app.domain.agent.ResearchAgent(toolRegistry, toolCtx),
                com.agentos.app.domain.agent.BrowserAgent(toolRegistry, toolCtx),
                com.agentos.app.domain.agent.AndroidAgent(toolRegistry, toolCtx),
                com.agentos.app.domain.agent.TerminalAgent(toolRegistry, toolCtx),
                com.agentos.app.domain.agent.CodingAgent(toolRegistry, toolCtx)
            )
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return TaskEngine(
            scope = scope,
            providerExecutor = fakeExecutor,
            settings = settings,
            secureStore = secretStore,
            agentRegistry = agents,
            toolRegistry = toolRegistry,
            chatRepo = ChatRepository(db) { true },
            taskRepo = TaskRepository(db) { true },
            memoryRepo = MemoryRepository(db),
            networkMonitor = connectivity
        ).apply {
            contextProvider = { appContext }
        }
    }

    private fun newConversation(id: String) {
        runBlocking { db.conversationDao().insert(ConversationEntity(id, "test", 0L, 0L)) }
    }

    /** Subscribes to the engine event stream BEFORE the task starts. */
    private fun <T : TaskEvent> nextEvent(cls: Class<T>, taskId: String): CompletableDeferred<T> {
        val deferred = CompletableDeferred<T>()
        scope.launch {
            engine.events.collect { ev ->
                if (cls.isInstance(ev) && ev.taskId == taskId && !deferred.isCompleted) {
                    deferred.complete(cls.cast(ev))
                }
            }
        }
        return deferred
    }

    @Before
    fun setUp() {
        // fresh instances per test happen inside each test via buildEngine
    }

    @After
    fun tearDown() {
        if (::scope.isInitialized) scope.coroutineContext.cancelChildren()
        if (::db.isInitialized) db.close()
    }

    @Test
    fun executesPlanStepsAndCompletes() = runBlocking {
        engine = buildEngine(listOf(planJson, "Final answer: file contains engine-test"))
        newConversation("conv-1")
        val taskId = engine.start("write and read a file", "conv-1")

        val completed = try {
            withTimeout(30_000) {
                nextEvent(TaskEvent.Completed::class.java, taskId).await()
            }
        } catch (e: Exception) {
            com.agentos.app.core.logging.Logger.recent().takeLast(40).forEach { println("ENG-LOG: ${it.format()}") }
            throw e
        }
        assertTrue(completed.finalResult.contains("engine-test"))

        val task = db.taskDao().byId(taskId)!!
        assertEquals(TaskStatus.COMPLETED.name, task.status)
        val steps = db.taskStepDao().forTask(taskId)
        assertEquals(2, steps.size)
        assertEquals("COMPLETED", steps[0].status)
        assertEquals("COMPLETED", steps[1].status)
        assertTrue(steps[1].result!!.contains("engine-test"))
    }

    @Test
    fun directAnswerTaskCompletesWithoutSteps() = runBlocking {
        engine = buildEngine(listOf(directAnswerJson, "Hello there!"))
        newConversation("conv-2")
        val taskId = engine.start("say hi", "conv-2")

        val completed = withTimeout(30_000) {
            nextEvent(TaskEvent.Completed::class.java, taskId).await()
        }
        assertTrue(completed.finalResult.contains("Hello"))
        assertEquals(0, db.taskStepDao().forTask(taskId).size)
    }

    @Test
    fun failingStepFailsTaskWithClearError() = runBlocking {
        val failingPlan = """
            {"direct_answer": false, "steps": [
              {"agent": "terminal", "tool": "file_read", "args": {"path": "does_not_exist_xyz.txt"}, "why": "read missing", "retry_on_failure": false}
            ]}
        """.trimIndent()
        engine = buildEngine(listOf(failingPlan, "unused"))
        newConversation("conv-3")
        val taskId = engine.start("read a missing file", "conv-3")

        val error = withTimeout(30_000) {
            nextEvent(TaskEvent.Error::class.java, taskId).await()
        }
        assertTrue(error.message.contains("failed"))

        val task = db.taskDao().byId(taskId)!!
        assertEquals(TaskStatus.FAILED.name, task.status)
        val steps = db.taskStepDao().forTask(taskId)
        assertEquals("FAILED", steps[0].status)
    }

    @Test
    fun highRiskToolRequiresApprovalAndDenyStopsTask() = runBlocking {
        val riskyPlan = """
            {"direct_answer": false, "steps": [
              {"agent": "terminal", "tool": "file_delete", "args": {"path": "anything.txt"}, "why": "delete file", "retry_on_failure": false}
            ]}
        """.trimIndent()
        engine = buildEngine(listOf(riskyPlan, "unused"))
        newConversation("conv-4")
        val taskId = engine.start("delete a file", "conv-4")

        val approval = withTimeout(30_000) {
            nextEvent(TaskEvent.ApprovalRequired::class.java, taskId).await()
        }
        assertEquals("file_delete", approval.tool)
        engine.resolveApproval(approval.stepId, false)

        withTimeout(30_000) { nextEvent(TaskEvent.Error::class.java, taskId).await() }
        val task = db.taskDao().byId(taskId)!!
        assertEquals(TaskStatus.FAILED.name, task.status)
        assertTrue(task.error!!.contains("denied"))
        val steps = db.taskStepDao().forTask(taskId)
        assertEquals("FAILED", steps[0].status)
        assertTrue(steps[0].error!!.contains("denied"))
    }

    @Test
    fun highRiskApprovalAllowExecutesStep() = runBlocking {
        val riskyPlan = """
            {"direct_answer": false, "steps": [
              {"agent": "terminal", "tool": "shell_exec", "args": {"command": "echo approved-run"}, "why": "run echo", "retry_on_failure": false}
            ]}
        """.trimIndent()
        engine = buildEngine(listOf(riskyPlan, "Done."))
        newConversation("conv-5")
        val taskId = engine.start("run echo", "conv-5")

        val approval = withTimeout(30_000) {
            nextEvent(TaskEvent.ApprovalRequired::class.java, taskId).await()
        }
        engine.resolveApproval(approval.stepId, true)

        withTimeout(30_000) { nextEvent(TaskEvent.Completed::class.java, taskId).await() }
        val steps = db.taskStepDao().forTask(taskId)
        assertEquals("COMPLETED", steps[0].status)
        assertTrue(steps[0].result!!.contains("approved-run"))
    }
}
