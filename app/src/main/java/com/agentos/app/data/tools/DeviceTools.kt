package com.agentos.app.data.tools

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.storage.StorageManager
import com.agentos.app.domain.tools.Args
import com.agentos.app.domain.tools.Tool
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolResult
import com.agentos.app.domain.tools.ToolRisk
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Real device information tool. */
class DeviceInfoTool(private val appContext: Context) : Tool {
    override val name = "device_info"
    override val description = "Returns real device information: model, Android version, battery, storage, screen."
    override val risk = ToolRisk.SAFE
    override val category = "Device"
    override val inputSchema = "{}"

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPct = bm?.let {
            val level = it.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 1..100) "$level%" else "unknown"
        } ?: "unknown"
        val storage = runCatching {
            val sm = appContext.getSystemService(StorageManager::class.java)
            val uuid = StorageManager.UUID_DEFAULT
            val bytes = sm.getAllocatableBytes(uuid)
            "%.1f GB available".format(bytes / 1e9)
        }.getOrDefault("unknown")
        val metrics = appContext.resources.displayMetrics
        val info = buildString {
            appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("SoC: ${Build.HARDWARE} / ${Build.SUPPORTED_ABIS.firstOrNull()}")
            appendLine("Battery: $batteryPct")
            appendLine("Storage: $storage")
            appendLine("Screen: ${metrics.widthPixels}x${metrics.heightPixels} px")
        }
        return ToolResult(true, info)
    }
}

/** Launches an installed app by package name (real PackageManager launch). */
class LaunchAppTool(private val appContext: Context) : Tool {
    override val name = "launch_app"
    override val description = "Launches an installed Android app by package name (e.g. com.android.chrome, com.google.android.youtube)."
    override val risk = ToolRisk.MODERATE
    override val category = "Device"
    override val inputSchema = """{"package": "string (required)", "search": "string (optional: find package by app name instead)"}"""

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        var pkg = Args.str(args, "package")
        val search = Args.str(args, "search")
        val pm = appContext.packageManager

        if (pkg.isBlank() && search.isNotBlank()) {
            val needle = search.lowercase()
            val found = pm.getInstalledPackages(0).firstOrNull {
                it.applicationInfo?.loadLabel(pm)?.toString()?.lowercase()?.contains(needle) == true
            }
            pkg = found?.packageName ?: return ToolResult(false, "", "No installed app matches name '$search'")
            ctx.onActivity("Matched '$search' to package $pkg")
        }
        if (pkg.isBlank()) return ToolResult(false, "", "package or search is required")

        return try {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent == null) ToolResult(false, "", "Package '$pkg' is not installed or has no launchable activity")
            else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.onActivity("Launching $pkg…")
                appContext.startActivity(intent)
                ToolResult(true, "Launched $pkg")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "launch_app failed: ${e.message}")
        }
    }
}

/** Opens a URL with the system resolver (real VIEW intent). */
class OpenUrlTool(private val appContext: Context) : Tool {
    override val name = "open_url"
    override val description = "Opens a URL with the system browser or matching app."
    override val risk = ToolRisk.SAFE
    override val category = "Device"
    override val inputSchema = """{"url": "string (required)"}"""

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val url = sanitizeUrl(Args.str(args, "url")).getOrElse { return ToolResult(false, "", it.message ?: "Invalid URL") }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            ToolResult(true, "Opened $url with the system resolver")
        } catch (e: Exception) {
            ToolResult(false, "", "open_url failed: no app can handle $url (${e.message})")
        }
    }
}

/** Real screenshot via MediaProjection consent flow (user approves per session). */
class ScreenshotTool : Tool {
    override val name = "take_screenshot"
    override val description = "Captures the current screen. Triggers a system permission dialog the first time per session; requires user consent."
    override val risk = ToolRisk.MODERATE
    override val category = "Device"
    override val inputSchema = "{}"

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        ctx.onActivity("Requesting screen capture permission…")
        val path = ctx.screenshotRequester()
        return if (path != null) {
            ToolResult(true, "Screenshot saved: $path", artifacts = listOf(path))
        } else {
            ToolResult(false, "", "Screenshot was not captured: permission denied by user, no activity available to request consent, or unsupported device")
        }
    }
}
