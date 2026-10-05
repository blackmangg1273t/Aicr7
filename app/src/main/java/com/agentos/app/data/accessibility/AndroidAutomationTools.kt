package com.agentos.app.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import com.agentos.app.service.AssistantAccessibilityService
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.serialization.json.JsonObject

/**
 * Real Android UI automation tools. All of them fail with actionable errors
 * when the user has not enabled the accessibility service — nothing is simulated.
 *
 * When the accessibility service is enabled but a transient gap in the active
 * window occurs (e.g. just after launch_app), the tools poll briefly via the
 * service's awaitActiveWindow() helper instead of failing instantly.
 */
class AndroidAutomationTools(private val appContext: Context) {

    private fun checkService(): AssistantAccessibilityService = AssistantAccessibilityService.require()

    val isEnabledBySettings: Boolean
        get() {
            val setting = Settings.Secure.getString(
                appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return setting.contains(appContext.packageName)
        }

    inner class StatusTool : Tool {
        override val name = "android_ui_status"
        override val description = "Reports whether Android UI automation (accessibility) is enabled and usable."
        override val risk = ToolRisk.SAFE
        override val category = "Android"
        override val inputSchema = "{}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val enabled = AssistantAccessibilityService.isRunning() || isEnabledBySettings
            return ToolResult(
                true,
                if (enabled) "Android UI automation: ENABLED and ready"
                else "Android UI automation: DISABLED. The user must enable 'AgentOS Android Agent' in Settings > Accessibility."
            )
        }
    }

    inner class TapTool : Tool {
        override val name = "ui_tap"
        override val description = "Taps real screen coordinates (x, y) using the accessibility gesture API."
        override val risk = ToolRisk.MODERATE
        override val category = "Android"
        override val inputSchema = """{"x": "int (required)", "y": "int (required)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val x = Args.int(args, "x"); val y = Args.int(args, "y")
            if (x <= 0 || y <= 0) return ToolResult(false, "", "x and y must be positive screen coordinates")
            ctx.onActivity("Tapping ($x, $y)…")
            return try {
                if (checkService().tap(x, y)) ToolResult(true, "Tapped ($x, $y)")
                else ToolResult(false, "", "Gesture dispatch was cancelled by the system")
            } catch (e: AssistantAccessibilityService.AccessibilityNotEnabled) {
                ToolResult(false, "", "Accessibility not enabled. Open Settings > Accessibility and enable 'AgentOS Android Agent'.")
            } catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_tap failed") }
        }
    }

    inner class ClickByTextTool : Tool {
        override val name = "ui_click_text"
        override val description = "Finds an on-screen element by visible text and clicks it (real accessibility click or gesture fallback). Waits briefly for an active window first."
        override val risk = ToolRisk.MODERATE
        override val category = "Android"
        override val inputSchema = """{"text": "string (required)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val text = Args.str(args, "text")
            if (text.isBlank()) return ToolResult(false, "", "text is required")
            ctx.onActivity("Clicking element \"$text\"…")
            return try { ToolResult(true, checkService().clickByText(text)) }
            catch (e: AssistantAccessibilityService.NoActiveWindowException) {
                ToolResult(false, "", "No active window right now. The target app may not have launched — try launch_app first, then retry.")
            }
            catch (e: AssistantAccessibilityService.AccessibilityNotEnabled) {
                ToolResult(false, "", "Accessibility not enabled. Open Settings > Accessibility and enable 'AgentOS Android Agent'.")
            }
            catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_click_text failed") }
        }
    }

    inner class TypeTool : Tool {
        override val name = "ui_type"
        override val description = "Types text into the focused/matching editable field on screen via accessibility SET_TEXT. Optionally match by hint/label/id. Waits briefly for an active window first."
        override val risk = ToolRisk.MODERATE
        override val category = "Android"
        override val inputSchema = """{"text": "string (required)", "hint": "string (optional: match field by label/hint/id)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val text = Args.str(args, "text")
            if (text.isBlank()) return ToolResult(false, "", "text is required")
            ctx.onActivity("Typing into field…")
            return try { ToolResult(true, checkService().typeText(text, Args.str(args, "hint").ifBlank { null })) }
            catch (e: AssistantAccessibilityService.NoActiveWindowException) {
                ToolResult(false, "", "No active window right now. The target app may not have launched — try launch_app first, then retry.")
            }
            catch (e: AssistantAccessibilityService.AccessibilityNotEnabled) {
                ToolResult(false, "", "Accessibility not enabled. Open Settings > Accessibility and enable 'AgentOS Android Agent'.")
            }
            catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_type failed") }
        }
    }

    inner class ScrollTool : Tool {
        override val name = "ui_scroll"
        override val description = "Performs a real swipe scroll gesture on the current screen."
        override val risk = ToolRisk.SAFE
        override val category = "Android"
        override val inputSchema = """{"direction": "string: down|up (default down)"}"""

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
            val dir = Args.str(args, "direction", "down")
            if (dir !in listOf("down", "up")) return ToolResult(false, "", "direction must be 'down' or 'up'")
            return try { ToolResult(true, checkService().scroll(dir)) }
            catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_scroll failed") }
        }
    }

    inner class BackTool : Tool {
        override val name = "ui_back"
        override val description = "Performs the system BACK action."
        override val risk = ToolRisk.MODERATE
        override val category = "Android"
        override val inputSchema = "{}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult =
            try { ToolResult(true, checkService().globalAction(AccessibilityService.GLOBAL_ACTION_BACK, "Back")) }
            catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_back failed") }
    }

    inner class HomeTool : Tool {
        override val name = "ui_home"
        override val description = "Performs the system HOME action."
        override val risk = ToolRisk.SAFE
        override val category = "Android"
        override val inputSchema = "{}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult =
            try { ToolResult(true, checkService().globalAction(AccessibilityService.GLOBAL_ACTION_HOME, "Home")) }
            catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_home failed") }
    }

    inner class SnapshotTool : Tool {
        override val name = "ui_snapshot"
        override val description = "Captures a structured snapshot of the current screen: visible elements, ids, bounds, text. Use it to decide what to click. Waits briefly for an active window first."
        override val risk = ToolRisk.SAFE
        override val category = "Android"
        override val inputSchema = "{}"

        override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult =
            try { ToolResult(true, checkService().screenSnapshot()) }
            catch (e: AssistantAccessibilityService.NoActiveWindowException) {
                ToolResult(false, "", "No active window right now. The target app may not have launched — try launch_app first, then retry.")
            }
            catch (e: Exception) { ToolResult(false, "", e.message ?: "ui_snapshot failed") }
    }

    fun all(): List<Tool> = listOf(
        StatusTool(), TapTool(), ClickByTextTool(), TypeTool(), ScrollTool(), BackTool(), HomeTool(), SnapshotTool()
    )
}
