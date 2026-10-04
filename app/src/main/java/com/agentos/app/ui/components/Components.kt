package com.agentos.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agentos.app.ui.theme.AgentOsColors
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import com.agentos.app.ui.theme.rememberBreathing
import kotlinx.serialization.json.JsonObject

/* ------------------------------------------------------------------ */
/* StatusDot — small semantic status indicator, pulses while active.    */
/* ------------------------------------------------------------------ */

enum class DotState { IDLE, RUNNING, DONE, ERROR, WAITING }

@Composable
fun StatusDot(state: DotState, modifier: Modifier = Modifier, size: Float = 8f) {
    val c = osColors()
    val color = when (state) {
        DotState.IDLE -> c.textMuted
        DotState.RUNNING -> c.accent
        DotState.DONE -> c.success
        DotState.ERROR -> c.error
        DotState.WAITING -> c.warning
    }
    val pulse by rememberBreathing(0.55f, 1f)
    val halo = if (state == DotState.RUNNING) pulse else 0.25f
    Canvas(modifier.size((size * 2.6f).dp)) {
        val r = this.size.minDimension / 2f
        drawCircle(color.copy(alpha = halo * 0.30f), radius = r)
        drawCircle(color, radius = r * 0.55f)
    }
}

/* ------------------------------------------------------------------ */
/* AgentBadge — small monogram chip identifying the active agent.       */
/* ------------------------------------------------------------------ */

@Composable
fun AgentBadge(agentName: String, modifier: Modifier = Modifier) {
    val c = osColors()
    val label = agentName.trim().replaceFirstChar { it.uppercase() }
    val tint = when {
        label.contains("main", true) -> c.accent
        label.contains("android", true) -> c.accentViolet
        label.contains("browser", true) -> c.accent
        label.contains("terminal", true) -> c.success
        label.contains("research", true) -> c.warning
        else -> c.accentDim
    }
    Row(
        modifier
            .clip(OsShapes.pill)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.25f), OsShapes.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(OsShapes.pill).background(tint))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/* ------------------------------------------------------------------ */
/* AuraCard — layered surface; while [active], a slow gradient border   */
/* breathes around it (the "AgentOS is working" aura).                  */
/* ------------------------------------------------------------------ */

@Composable
fun AuraCard(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable () -> Unit
) {
    val c = osColors()
    val breath by rememberBreathing(0.30f, 0.75f)
    val glow = if (active) breath else 0f
    val corner = 18.dp
    Box(
        modifier
            .clip(OsShapes.card)
            .drawBehind {
                if (glow > 0.01f) {
                    val stroke = 6.dp.toPx()
                    val brush = Brush.linearGradient(
                        listOf(
                            c.accent.copy(alpha = glow),
                            c.accentViolet.copy(alpha = glow * 0.7f)
                        )
                    )
                    drawRoundRect(
                        brush = brush,
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                    )
                }
            }
            .background(c.surfaceHigh)
            .border(1.dp, if (glow > 0.01f) c.accent.copy(alpha = glow * 0.5f) else c.border, OsShapes.card)
    ) {
        content()
    }
}

/* ------------------------------------------------------------------ */
/* SuggestionCard — interactive prompt suggestion (empty state).        */
/* ------------------------------------------------------------------ */

@Composable
fun SuggestionCard(text: String, accent: Color, onClick: () -> Unit) {
    val c = osColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.cardSmall)
            .background(c.surfaceHigh)
            .border(1.dp, c.border, OsShapes.cardSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(6.dp).clip(OsShapes.pill).background(accent))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
}

/* ------------------------------------------------------------------ */
/* Human-friendly tool naming: shell_exec -> "Shell", browser_click …   */
/* (execution visibility: users see meaning, developers see details)    */
/* ------------------------------------------------------------------ */

fun toolDisplayName(tool: String?): String = when {
    tool == null -> "Working"
    tool.startsWith("browser_") -> "Browser"
    tool.startsWith("web_") -> "Web"
    tool.startsWith("shell_") -> "Shell"
    tool.startsWith("file_") -> "Files"
    tool.startsWith("android_") || tool.startsWith("a11y_") -> "Android"
    tool.startsWith("memory") -> "Memory"
    tool.startsWith("screenshot") -> "Screenshot"
    tool.startsWith("launch_app") || tool.startsWith("open_url") -> "Apps"
    tool.startsWith("device_info") -> "Device"
    tool.startsWith("mcp") -> "MCP"
    else -> tool.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** One-line human summary of tool args (never dumps raw JSON). */
fun toolHumanSummary(tool: String?, argsJson: String?): String {
    val args: JsonObject? = runCatching {
        argsJson?.let { kotlinx.serialization.json.Json.parseToJsonElement(it) as? JsonObject }
    }.getOrNull()
    fun s(key: String): String? = args?.get(key)?.let {
        (it as? kotlinx.serialization.json.JsonPrimitive)?.content
    }
    return when {
        tool == null -> ""
        tool.startsWith("browser_open") || tool.startsWith("open_url") ->
            s("url")?.let { "Opening ${it.take(60)}" } ?: "Opening page"
        tool.startsWith("browser_click") -> s("selector")?.let { "Tapping ${it.take(40)}" } ?: "Tapping element"
        tool.startsWith("browser_type") -> "Typing text"
        tool.startsWith("browser_read") -> "Reading page"
        tool.startsWith("web_search") -> s("query")?.let { "Searching \"$it\"" } ?: "Searching the web"
        tool.startsWith("web_fetch") -> s("url")?.let { "Fetching ${it.take(60)}" } ?: "Fetching page"
        tool.startsWith("shell_exec") -> s("command")?.let { "Running ${it.take(50)}" } ?: "Running command"
        tool.startsWith("launch_app") -> s("app")?.let { "Opening $it" } ?: "Opening app"
        tool.startsWith("screenshot") -> "Capturing screen"
        tool.startsWith("android_tap") || tool.startsWith("a11y_tap") -> "Tapping screen"
        tool.startsWith("android_type") || tool.startsWith("a11y_type") -> "Typing on screen"
        tool.startsWith("android_gesture") || tool.startsWith("a11y_swipe") -> "Swiping"
        tool.startsWith("file_") ->
            s("path")?.let { "${toolDisplayName(tool)} · ${it.take(40)}" } ?: toolDisplayName(tool)
        tool.startsWith("memory") -> "Remembering context"
        tool.startsWith("mcp") -> s("tool")?.let { "MCP · $it" } ?: "MCP call"
        else -> argsJson?.take(60) ?: toolDisplayName(tool)
    }
}
