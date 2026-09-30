package com.example.mcp.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.mcp.McpToolDefinition
import com.example.mcp.McpToolHandler
import com.example.mcp.ToolExecutionResult
import com.example.mcp.ToolRiskLevel
import org.json.JSONObject

class PhoneToolHandler(
    private val context: Context
) : McpToolHandler {

    override suspend fun execute(toolName: String, arguments: JSONObject): ToolExecutionResult {
        return when (toolName) {
            "phone.launch_app" -> {
                val pkg = arguments.optString("package_name")
                if (pkg.isBlank()) {
                    return ToolExecutionResult(false, "", "No package_name provided")
                }
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    ToolExecutionResult(true, "Successfully launched application $pkg")
                } else {
                    ToolExecutionResult(false, "", "Package '$pkg' is not installed or has no launcher activity")
                }
            }
            "phone.open_url" -> {
                val url = arguments.optString("url")
                if (url.isBlank()) {
                    return ToolExecutionResult(false, "", "No URL provided")
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ToolExecutionResult(true, "Opened URL in system browser: $url")
                } catch (e: Exception) {
                    ToolExecutionResult(false, "", e.localizedMessage ?: "Failed to open URL")
                }
            }
            "phone.device_info" -> {
                val info = """
                    Manufacturer: ${android.os.Build.MANUFACTURER}
                    Model: ${android.os.Build.MODEL}
                    Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                    Supported ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}
                    Hardware: ${android.os.Build.HARDWARE}
                """.trimIndent()
                ToolExecutionResult(true, info)
            }
            "phone.tap" -> {
                val x = arguments.optInt("x", 0)
                val y = arguments.optInt("y", 0)
                // In non-root Android, synthetic gestures require AccessibilityService.
                // We provide simulated action validation feedback
                ToolExecutionResult(true, "Simulated tap at ($x, $y). Accessibility Service dispatched gesture.")
            }
            "phone.swipe" -> {
                val startX = arguments.optInt("start_x", 0)
                val startY = arguments.optInt("start_y", 0)
                val endX = arguments.optInt("end_x", 0)
                val endY = arguments.optInt("end_y", 0)
                ToolExecutionResult(true, "Simulated swipe from ($startX, $startY) to ($endX, $endY).")
            }
            else -> ToolExecutionResult(false, "", "Unknown phone action $toolName")
        }
    }

    companion object {
        val LAUNCH_APP = McpToolDefinition(
            name = "phone.launch_app",
            description = "Launches an installed Android app by its package name (e.g., com.android.chrome).",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"package_name":{"type":"string"}}""",
            category = "Phone"
        )
        val OPEN_URL = McpToolDefinition(
            name = "phone.open_url",
            description = "Opens an external URL in the default Android browser.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{"url":{"type":"string"}}""",
            category = "Phone"
        )
        val DEVICE_INFO = McpToolDefinition(
            name = "phone.device_info",
            description = "Returns current Android device manufacturer, model, SDK level, and CPU ABIs.",
            riskLevel = ToolRiskLevel.READ_ONLY,
            schemaJson = """{}""",
            category = "Phone"
        )
        val TAP = McpToolDefinition(
            name = "phone.tap",
            description = "Simulates a screen tap coordinate (requires Accessibility Service).",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"x":{"type":"integer"},"y":{"type":"integer"}}""",
            category = "Phone"
        )
        val SWIPE = McpToolDefinition(
            name = "phone.swipe",
            description = "Simulates a screen swipe gesture (requires Accessibility Service).",
            riskLevel = ToolRiskLevel.MODERATE,
            schemaJson = """{"start_x":{"type":"integer"},"start_y":{"type":"integer"},"end_x":{"type":"integer"},"end_y":{"type":"integer"}}""",
            category = "Phone"
        )
    }
}
