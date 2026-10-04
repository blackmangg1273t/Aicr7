package com.agentos.app

import com.agentos.app.core.network.NetworkMonitor
import com.agentos.app.data.browser.BrowserEngine
import com.agentos.app.data.db.AppDatabase
import com.agentos.app.data.repo.MemoryRepository
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.data.termux.TermuxBridge
import com.agentos.app.data.tools.BrowserTools
import com.agentos.app.data.tools.DeviceInfoTool
import com.agentos.app.data.tools.LaunchAppTool
import com.agentos.app.data.tools.MemoryTools
import com.agentos.app.data.tools.OpenUrlTool
import com.agentos.app.data.tools.ScreenshotTool
import com.agentos.app.data.tools.ShellTools
import com.agentos.app.data.tools.WebFetchTool
import com.agentos.app.data.tools.WebSearchTool
import com.agentos.app.data.accessibility.AndroidAutomationTools
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolRegistry
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * ToolRegistry behavior with the REAL production tool set (Robolectric supplies
 * a Context). Network-requiring tools are not invoked here — we assert
 * registration, lookup, validation, sandboxing and risk classification only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolRegistryTest {

    private val app get() = RuntimeEnvironment.getApplication()

    private fun buildRegistry(): ToolRegistry {
        val registry = ToolRegistry()
        val browserTools = BrowserTools(BrowserEngine(app), NetworkMonitor(app))
        val shellTools = ShellTools(app, TermuxBridge(app), SettingsRepository(app))
        val androidTools = AndroidAutomationTools(app)
        val memoryTools = MemoryTools(MemoryRepository(AppDatabase.build(app)))
        registry.registerAll(
            listOf(
                WebSearchTool(NetworkMonitor(app)),
                WebFetchTool(NetworkMonitor(app)),
                DeviceInfoTool(app),
                LaunchAppTool(app),
                OpenUrlTool(app),
                ScreenshotTool()
            ) + browserTools.all() + shellTools.all() + androidTools.all() + memoryTools.all()
        )
        return registry
    }

    @Test
    fun registersAllRealTools() {
        val registry = buildRegistry()
        val names = registry.all().map { it.name }.toSet()
        val expected = setOf(
            "web_search", "web_fetch",
            "browser_open", "browser_read", "browser_click", "browser_type", "browser_back", "browser_evaluate", "browser_current_url",
            "device_info", "launch_app", "open_url", "take_screenshot",
            "shell_exec", "file_read", "file_write", "file_list", "file_delete", "git",
            "android_ui_status", "ui_tap", "ui_click_text", "ui_type", "ui_scroll", "ui_back", "ui_home", "ui_snapshot",
            "memory_store", "memory_search", "memory_delete"
        )
        val missing = expected - names
        assertTrue("Missing tools: $missing", missing.isEmpty())
        assertEquals(expected.size, registry.all().size)
    }

    @Test
    fun unknownToolFails() = runBlocking {
        val registry = buildRegistry()
        val result = registry.execute("does_not_exist", JsonObject(emptyMap()), ToolContext(app))
        assertTrue(!result.success)
        assertTrue(result.error!!.contains("Unknown tool"))
    }

    @Test
    fun shellToolRejectsBlankCommand() = runBlocking {
        val registry = buildRegistry()
        val result = registry.execute("shell_exec", JsonObject(emptyMap()), ToolContext(app))
        assertTrue(!result.success)
        assertTrue(result.error!!.contains("command is required"))
    }

    @Test
    fun shellToolRejectsDangerousCommandViaGuard() = runBlocking {
        val registry = buildRegistry()
        val result = registry.execute(
            "shell_exec",
            JsonObject(mapOf("command" to JsonPrimitive("rm -rf /"))),
            ToolContext(app)
        )
        assertTrue(!result.success)
        assertTrue(result.error!!.contains("blocked by safety guard"))
    }

    @Test
    fun fileToolsStayInsideWorkspaceSandbox() = runBlocking {
        val registry = buildRegistry()
        val result = registry.execute(
            "file_read",
            JsonObject(mapOf("path" to JsonPrimitive("../../etc/passwd"))),
            ToolContext(app)
        )
        // path traversal must be blocked by sandbox check (SecurityException → ToolResult failure)
        assertTrue(!result.success)
    }

    @Test
    fun fileWriteAndListRoundTrip() = runBlocking {
        val registry = buildRegistry()
        val ctx = ToolContext(app)
        val write = registry.execute(
            "file_write",
            JsonObject(mapOf(
                "path" to JsonPrimitive("notes/hello.txt"),
                "content" to JsonPrimitive("hello agentos")
            )),
            ctx
        )
        assertTrue(write.success)
        val read = registry.execute(
            "file_read",
            JsonObject(mapOf("path" to JsonPrimitive("notes/hello.txt"))),
            ctx
        )
        assertTrue(read.success)
        assertEquals("hello agentos", read.output)
        val list = registry.execute("file_list", JsonObject(emptyMap()), ctx)
        assertTrue(list.output.contains("notes"))
    }

    @Test
    fun deviceInfoExecutesReally() = runBlocking {
        val registry = buildRegistry()
        val result = registry.execute("device_info", JsonObject(emptyMap()), ToolContext(app))
        assertTrue(result.success)
        assertTrue(result.output.contains("Android:"))
    }

    @Test
    fun highRiskToolsAreFlaggedForApproval() {
        val registry = buildRegistry()
        assertEquals(ToolRisk.HIGH_RISK, registry.get("shell_exec")!!.risk)
        assertEquals(ToolRisk.HIGH_RISK, registry.get("file_delete")!!.risk)
        assertEquals(ToolRisk.HIGH_RISK, registry.get("memory_delete")!!.risk)
        assertEquals(ToolRisk.SAFE, registry.get("web_search")!!.risk)
    }
}
