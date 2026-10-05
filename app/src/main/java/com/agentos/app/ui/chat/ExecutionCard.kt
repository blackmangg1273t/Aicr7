package com.agentos.app.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.ui.components.toolDisplayName
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/* ------------------------------------------------------------------ */
/* Execution Card — 2026 redesign.                                      */
/*                                                                       */
/* Three compact surfaces:                                                */
/*   1. Task Plan Card — checklist with progress (2 / 3) and View full.   */
/*   2. Execution Timeline — per-step status, agent, tool, elapsed time.  */
/*   3. Error Recovery Card — friendly message + Retry / Try another /    */
/*      Stop buttons + expandable Technical details.                      */
/*                                                                       */
/* A slow ambient blue/violet radial glow breathes on the running header. */
/* No chain-of-thought, no raw plan logs — the cards ARE the visualization. */
/* ------------------------------------------------------------------ */

@Composable
fun ExecutionCard(
    ui: ChatUiState,
    onRetry: () -> Unit,
    onTryAnother: () -> Unit,
    onStop: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = osColors()
    if (ui.plan.isEmpty() && ui.phase != ExecutionPhase.PLANNING && ui.lastError == null) return

    val running = ui.running
    val total = ui.plan.size
    val done = ui.completedSteps
    val finished = ui.finalStatus != null

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ---- (1) Task Plan Card --------------------------------------
        AnimatedVisibility(
            visible = ui.plan.isNotEmpty() || ui.phase == ExecutionPhase.PLANNING,
            enter = fadeIn(tween(OsMotion.Normal)) + expandVertically(tween(OsMotion.Normal)),
            exit = fadeOut(tween(OsMotion.Fast)) + shrinkVertically(tween(OsMotion.Fast))
        ) {
            TaskPlanCard(ui = ui, running = running, total = total, done = done)
        }

        // ---- (2) Execution Timeline ----------------------------------
        AnimatedVisibility(
            visible = ui.plan.isNotEmpty(),
            enter = fadeIn(tween(OsMotion.Normal)) + expandVertically(tween(OsMotion.Normal)),
            exit = fadeOut(tween(OsMotion.Fast)) + shrinkVertically(tween(OsMotion.Fast))
        ) {
            ExecutionTimeline(ui = ui, running = running)
        }

        // ---- (3) Friendly Error Recovery Card ------------------------
        AnimatedVisibility(
            visible = ui.lastError != null && !finished,
            enter = fadeIn(tween(OsMotion.Normal)) + expandVertically(tween(OsMotion.Normal)),
            exit = fadeOut(tween(OsMotion.Fast)) + shrinkVertically(tween(OsMotion.Fast))
        ) {
            ui.lastError?.let { raw ->
                ErrorRecoveryCard(
                    rawError = raw,
                    recovering = ui.recovering,
                    onRetry = onRetry,
                    onTryAnother = onTryAnother,
                    onStop = onStop,
                    onDismiss = onDismissError
                )
            }
        }

        // ---- (3b) Final result summary --------------------------------
        AnimatedVisibility(
            visible = finished,
            enter = fadeIn(tween(OsMotion.Normal)) + expandVertically(tween(OsMotion.Normal)),
            exit = fadeOut(tween(OsMotion.Fast)) + shrinkVertically(tween(OsMotion.Fast))
        ) {
            CompletionResult(ui = ui)
        }

        // ---- (3c) Screenshot attachments (real) -----------------------
        AnimatedVisibility(
            visible = ui.attachments.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.attachments.take(3).forEach { att ->
                        ScreenshotPreview(att, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/* ----------------------- Task Plan Card ---------------------------- */

@Composable
private fun TaskPlanCard(ui: ChatUiState, running: Boolean, total: Int, done: Int) {
    val c = osColors()
    var expanded by remember { mutableStateOf(false) }

    RunningAuraCard(active = running) {
        Column(Modifier.padding(14.dp)) {
            // header row: "Task plan" + progress chip
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Task plan",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary
                )
                Spacer(Modifier.weight(1f))
                if (ui.phase == ExecutionPhase.PLANNING) {
                    Text(
                        "Planning…",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent
                    )
                } else if (total > 0) {
                    ProgressChip(done = done, total = total)
                }
            }

            if (ui.phase == ExecutionPhase.PLANNING) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "AgentOS is breaking your request into steps…",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                // shimmer skeleton lines while planning
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(OsShapes.pill)
                                .background(c.surfaceInteractive)
                        )
                    }
                }
            } else if (ui.plan.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val stepsToShow = if (expanded) ui.plan else ui.plan.take(3)
                stepsToShow.forEach { step ->
                    PlanChecklistRow(step)
                }
                if (!expanded && ui.plan.size > 3) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "View full plan (+${ui.plan.size - 3} more)",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent,
                        modifier = Modifier
                            .clip(OsShapes.pill)
                            .clickable { expanded = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Show less",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textMuted,
                        modifier = Modifier
                            .clip(OsShapes.pill)
                            .clickable { expanded = false }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** A checklist row: ✓ done · ● running · ○ pending · ✕ failed. */
@Composable
private fun PlanChecklistRow(step: PlanStepUi) {
    val c = osColors()
    val mark = when (step.status) {
        StepStatus.COMPLETED -> "✓"
        StepStatus.RUNNING -> "●"
        StepStatus.FAILED -> "✕"
        StepStatus.AWAITING_APPROVAL -> "⏸"
        StepStatus.VERIFIED -> "✓"
        StepStatus.RECOVERING -> "↻"
        else -> "○"
    }
    val color = when (step.status) {
        StepStatus.COMPLETED, StepStatus.VERIFIED -> c.success
        StepStatus.RUNNING, StepStatus.RECOVERING -> c.accent
        StepStatus.FAILED -> c.error
        StepStatus.AWAITING_APPROVAL -> c.warning
        else -> c.textMuted
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(mark, color = color, style = MaterialTheme.typography.titleSmall)
        Text(
            step.why,
            style = MaterialTheme.typography.bodySmall,
            color = if (step.status == StepStatus.PENDING) c.textSecondary else c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (step.status == StepStatus.RUNNING) {
            Text(
                "working",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent
            )
        }
    }
}

@Composable
private fun ProgressChip(done: Int, total: Int) {
    val c = osColors()
    Row(
        Modifier
            .clip(OsShapes.pill)
            .background(c.surfaceInteractive)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "$done / $total",
            style = MonoStyle.copy(fontSize = 11.sp),
            color = c.textSecondary
        )
    }
}

/* ----------------------- Execution Timeline ------------------------ */

@Composable
private fun ExecutionTimeline(ui: ChatUiState, running: Boolean) {
    val c = osColors()
    // recompute "now" every 200ms while running so live durations update
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            kotlinx.coroutines.delay(200)
            tick = System.currentTimeMillis()
        }
    }
    val refTime = if (running) tick else remember { System.currentTimeMillis() }

    AuraCardSurface(active = running) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Execution timeline",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary
                )
                Spacer(Modifier.weight(1f))
                if (ui.attachments.isNotEmpty()) {
                    Text(
                        "${ui.attachments.size} screenshot${if (ui.attachments.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textMuted
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ui.plan.forEach { step ->
                TimelineRow(step = step, refTime = refTime, running = running)
            }
        }
    }
}

/** One timeline row — two lines: status + step name + agent, then elapsed time or status. */
@Composable
private fun TimelineRow(step: PlanStepUi, refTime: Long, running: Boolean) {
    val c = osColors()
    val mark = when (step.status) {
        StepStatus.COMPLETED -> "✓"
        StepStatus.RUNNING -> "●"
        StepStatus.FAILED -> "✕"
        StepStatus.AWAITING_APPROVAL -> "⏸"
        StepStatus.VERIFIED -> "✓"
        StepStatus.RECOVERING -> "↻"
        else -> "○"
    }
    val color = when (step.status) {
        StepStatus.COMPLETED, StepStatus.VERIFIED -> c.success
        StepStatus.RUNNING, StepStatus.RECOVERING -> c.accent
        StepStatus.FAILED -> c.error
        StepStatus.AWAITING_APPROVAL -> c.warning
        else -> c.textMuted
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mark, color = color, style = MaterialTheme.typography.bodyMedium)
            Text(
                step.why,
                style = MaterialTheme.typography.bodySmall,
                color = if (step.status == StepStatus.PENDING) c.textSecondary else c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                agentShortName(step.agent),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
        }
        // second line — elapsed or status
        val line2 = when (step.status) {
            StepStatus.RUNNING -> {
                val elapsedMs = (refTime - (step.startedAtMs ?: refTime)).coerceAtLeast(0)
                "${toolDisplayName(step.tool)} · Working… ${formatStepDuration(elapsedMs)}"
            }
            StepStatus.COMPLETED, StepStatus.VERIFIED -> {
                val ms = durationOf(step)
                if (ms > 0) "${formatStepDuration(ms)}" else toolDisplayName(step.tool)
            }
            StepStatus.FAILED -> "Failed"
            StepStatus.AWAITING_APPROVAL -> "Waiting for you"
            StepStatus.RECOVERING -> "Trying again…"
            StepStatus.SKIPPED -> "Skipped"
            StepStatus.PENDING -> toolDisplayName(step.tool).takeIf { step.tool != null } ?: ""
        }
        if (line2.isNotBlank()) {
            Row(
                Modifier.padding(start = 22.dp, top = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    line2,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.status == StepStatus.RUNNING) c.accent else c.textMuted
                )
            }
        }
    }
}

private fun durationOf(step: PlanStepUi): Long {
    val start = step.startedAtMs ?: return 0
    val end = step.finishedAtMs ?: return 0
    return (end - start).coerceAtLeast(0)
}

/** Friendly short agent label for the timeline. */
private fun agentShortName(agent: String): String = when {
    agent.contains("main", ignoreCase = true) -> "Main"
    agent.contains("android", ignoreCase = true) -> "Android"
    agent.contains("browser", ignoreCase = true) -> "Browser"
    agent.contains("terminal", ignoreCase = true) -> "Terminal"
    agent.contains("research", ignoreCase = true) -> "Research"
    else -> agent.replaceFirstChar { it.uppercase() }.take(12)
}

internal fun formatStepDuration(ms: Long): String = when {
    ms < 1_000 -> "${ms / 100}.${(ms % 100) / 10}s"
    ms < 10_000 -> String.format("%.1fs", ms / 1000.0)
    ms < 60_000 -> "${ms / 1000}s"
    ms < 3_600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
}

/* -------------------- Error Recovery Card ------------------------- */

/**
 * Friendly error card. Heuristics map raw error strings to a human message;
 * the raw error is preserved under an expandable "Technical details" toggle
 * for developers.
 */
@Composable
private fun ErrorRecoveryCard(
    rawError: String,
    recovering: Boolean,
    onRetry: () -> Unit,
    onTryAnother: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = osColors()
    val (title, hint) = friendlyError(rawError)
    var showDetails by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (showDetails) 180f else 0f, label = "errChev")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.card)
            .background(c.errorContainer)
            .border(1.dp, c.error.copy(alpha = 0.32f), OsShapes.card)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⚠", style = MaterialTheme.typography.titleSmall, color = c.error)
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (recovering) "$hint AgentOS is trying another method…" else hint,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ErrorPillButton(text = "Retry", accent = c.accent, onClick = onRetry)
            ErrorPillButton(text = "Try another method", accent = c.accentViolet, onClick = onTryAnother)
            ErrorPillButton(text = "Stop task", accent = c.error, onClick = onStop)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(OsShapes.pill)
                .clickable { showDetails = !showDetails }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = c.textMuted,
                modifier = Modifier.size(14.dp).rotate(chevron)
            )
            Text(
                if (showDetails) "Hide technical details" else "Technical details",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = showDetails,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                rawError,
                style = MonoStyle.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                color = c.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(OsShapes.cardSmall)
                    .background(c.codeBg)
                    .border(1.dp, c.border, OsShapes.cardSmall)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun ErrorPillButton(text: String, accent: Color, onClick: () -> Unit) {
    val c = osColors()
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = c.textPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(OsShapes.pill)
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.35f), OsShapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/** Heuristic mapping from raw engine errors to a friendly user-facing message. */
private fun friendlyError(raw: String): Pair<String, String> {
    val lower = raw.lowercase()
    val title = when {
        lower.contains("browser_type failed: no input matching") ||
            lower.contains("no input matching") -> "Couldn't find the form field on the page"
        lower.contains("no active window") ||
            lower.contains("no foreground") -> "The target app didn't come to foreground"
        lower.contains("network") || lower.contains("timeout") || lower.contains("unreachable") ->
            "Network issue — couldn't reach the service"
        lower.contains("permission") || lower.contains("access denied") ->
            "Missing permission to perform this action"
        lower.contains("not found") || lower.contains("no such") ->
            "Couldn't find what was requested"
        lower.contains("api key") || lower.contains("provider") || lower.contains("unauthorized") ->
            "AI provider isn't configured correctly"
        else -> "Something went wrong on this step"
    }
    val hint = when {
        lower.contains("no input matching") -> "The page layout may have changed."
        lower.contains("no active window") -> "The app may be closed or blocked."
        lower.contains("network") || lower.contains("timeout") -> "Check your connection and try again."
        else -> "AgentOS will try a different approach."
    }
    return title to hint
}

/* ----------------------- Completion result ------------------------ */

@Composable
private fun CompletionResult(ui: ChatUiState) {
    val c = osColors()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }
    val checkScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "checkPop"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.cardSmall)
            .background(
                when (ui.finalStatus) {
                    TaskStatus.COMPLETED -> c.successContainer
                    TaskStatus.CANCELLED -> c.surfaceInteractive
                    else -> c.errorContainer
                }
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .scale(if (ui.finalStatus == TaskStatus.COMPLETED) checkScale else 1f)
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when (ui.finalStatus) {
                            TaskStatus.COMPLETED -> c.success
                            TaskStatus.CANCELLED -> c.textMuted
                            else -> c.error
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (ui.finalStatus) {
                        TaskStatus.COMPLETED -> "✓"
                        TaskStatus.CANCELLED -> "—"
                        else -> "✕"
                    },
                    color = c.bg,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                when (ui.finalStatus) {
                    TaskStatus.COMPLETED -> "Task completed"
                    TaskStatus.CANCELLED -> "Task cancelled"
                    TaskStatus.FAILED -> "Task failed"
                    else -> "Finished"
                },
                style = MaterialTheme.typography.titleSmall,
                color = c.textPrimary
            )
        }
        ui.finalResult?.takeIf { it.isNotBlank() }?.let { result ->
            Spacer(Modifier.height(6.dp))
            Text(
                result,
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (copied) "Copied ✓" else "Copy result",
                style = MaterialTheme.typography.labelSmall,
                color = if (copied) c.success else c.accent,
                modifier = Modifier
                    .clip(OsShapes.pill)
                    .clickable {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(result))
                        copied = true
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

/* -------------- Running Aura Card (subtle radial glow) ------------- */

/**
 * A card surface with a slow ambient blue/violet radial gradient glow that
 * breathes while [active]. Uses [Brush.radialGradient] with low alpha and a
 * 2000ms infinite transition — restrained, not flashy.
 */
@Composable
private fun RunningAuraCard(active: Boolean, content: @Composable () -> Unit) {
    val c = osColors()
    val breath by com.agentos.app.ui.theme.rememberBreathing(0.20f, 0.65f)
    val glow = if (active) breath else 0f
    val corner = 16.dp
    Box(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.card)
            .drawBehind {
                if (glow > 0.01f) {
                    // Ambient radial aura behind the card.
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.accent.copy(alpha = glow * 0.16f),
                                c.accentViolet.copy(alpha = glow * 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.3f),
                            radius = size.maxDimension * 0.85f
                        ),
                        cornerRadius = CornerRadius(corner.toPx())
                    )
                    // Subtle top-edge highlight — sells the "alive" state.
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, c.accent.copy(alpha = glow * 0.6f), Color.Transparent)
                        ),
                        start = Offset(corner.toPx(), 1.5f),
                        end = Offset(size.width - corner.toPx(), 1.5f),
                        strokeWidth = 1.5f
                    )
                }
            }
            .background(c.surfaceHigh)
            .border(1.dp, if (glow > 0.01f) c.accent.copy(alpha = glow * 0.45f) else c.border, OsShapes.card)
    ) {
        content()
    }
}

/** Lighter aura surface used by the Execution Timeline — no extra border highlight. */
@Composable
private fun AuraCardSurface(active: Boolean, content: @Composable () -> Unit) {
    val c = osColors()
    val breath by com.agentos.app.ui.theme.rememberBreathing(0.15f, 0.50f)
    val glow = if (active) breath else 0f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.card)
            .drawBehind {
                if (glow > 0.01f) {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                c.accentViolet.copy(alpha = glow * 0.12f),
                                c.accent.copy(alpha = glow * 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.5f),
                            radius = size.maxDimension * 0.75f
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                }
            }
            .background(c.surface)
            .border(1.dp, if (glow > 0.01f) c.borderStrong else c.border, OsShapes.card)
    ) {
        content()
    }
}

private fun CornerRadius(px: Float) = androidx.compose.ui.geometry.CornerRadius(px, px)

/* ----------------------- Screenshot preview ----------------------- */

/** Small screenshot preview decoded from the real attachment path. */
@Composable
private fun ScreenshotPreview(att: AttachmentUi, modifier: Modifier = Modifier) {
    val c = osColors()
    val bitmap = remember(att.path) {
        runCatching { BitmapFactory.decodeFile(att.path)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceInteractive)
            .border(1.dp, c.border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = att.caption, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
        } else {
            Text("screenshot", style = MonoStyle, color = c.textMuted)
        }
    }
}
