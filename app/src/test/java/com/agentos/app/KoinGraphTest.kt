package com.agentos.app

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.agentos.app.data.accessibility.AndroidAutomationTools
import com.agentos.app.domain.engine.TaskEngine
import com.agentos.app.domain.tools.ToolRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin

/**
 * Launch-crash regression tests.
 *
 * Regression context: MainActivity resolves ChatViewModel, TasksViewModel,
 * TerminalViewModel and SettingsViewModel via koinViewModel(). When these were
 * not registered in the Koin graph, the app crashed instantly on launch with
 * NoDefinitionFoundException while all 42 unit tests still passed, because no
 * test ever exercised the production Koin graph or the real Activity.
 *
 * These tests boot the REAL production modules (coreModule, agentsModule,
 * uiModule) and the REAL MainActivity so this class of failure can never
 * ship silently again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = AgentOsApp::class)
class KoinGraphTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    /** Full production launch path: Application.onCreate -> Activity onCreate/Start/Resume -> Compose composition. */
    @Test
    fun mainActivity_launches_without_crash() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            assertEquals(activity.lifecycle.currentState, Lifecycle.State.RESUMED)
        }
        scenario.close()
    }

    /** Every ViewModel MainActivity needs at first composition must be resolvable from the real graph. */
    @Test
    fun koin_graph_resolves_all_ui_viewmodels() {
        val app = ApplicationProvider.getApplicationContext<AgentOsApp>()
        // Application already started Koin in onCreate; assert all four ViewModel definitions resolve.
        val koin = GlobalContext.get()
        val chat = koin.getOrNull<com.agentos.app.ui.chat.ChatViewModel>()
        val tasks = koin.getOrNull<com.agentos.app.ui.tasks.TasksViewModel>()
        val terminal = koin.getOrNull<com.agentos.app.ui.terminal.TerminalViewModel>()
        val settings = koin.getOrNull<com.agentos.app.ui.settings.SettingsViewModel>()
        assertTrue("ChatViewModel must be registered in Koin (uiModule)", chat != null)
        assertTrue("TasksViewModel must be registered in Koin (uiModule)", tasks != null)
        assertTrue("TerminalViewModel must be registered in Koin (uiModule)", terminal != null)
        assertTrue("SettingsViewModel must be registered in Koin (uiModule)", settings != null)
    }

    /** Below the UI layer: engine, agents and the full production tool set must resolve as singles. */
    @Test
    fun koin_graph_resolves_engine_and_full_tool_registry() {
        ApplicationProvider.getApplicationContext<AgentOsApp>()
        val koin = GlobalContext.get()
        val engine = koin.getOrNull<TaskEngine>()
        val registry = koin.getOrNull<ToolRegistry>()
        val agents = koin.getOrNull<com.agentos.app.domain.agent.AgentRegistry>()
        val androidTools = koin.getOrNull<AndroidAutomationTools>()
        assertTrue("TaskEngine must resolve", engine != null)
        assertTrue("AgentRegistry must resolve", agents != null)
        assertTrue("AndroidAutomationTools must resolve as a single", androidTools != null)
        assertTrue(
            "Production ToolRegistry must contain the full tool set (>=30), got ${registry?.all()?.size}",
            registry != null && registry.all().size >= 30
        )
    }
}
