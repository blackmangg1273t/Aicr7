package com.agentos.app.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.ui.components.AgentBadge
import com.agentos.app.ui.components.DotState
import com.agentos.app.ui.components.StatusDot
import com.agentos.app.ui.components.toolDisplayName
import com.agentos.app.ui.components.toolHumanSummary
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/* ------------------------------------------------------------------ */
/* Live Execution Card                                                  */
/* Shows the REAL plan from the planner, with live step statuses.       */
/* No chain-of-thought: only agent / tool / status / progress.          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionCard(
    ui: ChatUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = osColors()
    if (ui.plan.isEmpty() && ui.phase != ExecutionPhase.PLANNING) return
    var showSheet by remember { mutableStateOf(false) }
    val running = ui.running
    val total = ui.plan.size
    val done = ui.completedSteps

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = c.surface,
            shape = OsShapes.sheet
        ) {
            PlanSheetContent(ui)
        }
    }

    com.agentos.app.ui.components.AuraCard(
        modifier = modifier.fillMaxWidth().clickable { showSheet = true },
        active = running
    ) {
        Column(Modifier.padding(16.dp)) {
            // header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    state = when {
                        ui.finalStatus == TaskStatus.COMPLETED -> DotState.DONE
                        ui.finalStatus != null -> DotState.ERROR
                        ui.phase == ExecutionPhase.PLANNING -> DotState.RUNNING
                        ui.phase == ExecutionPhase.WAITING_APPROVAL -> DotState.WAITING
                        running -> DotState.RUNNING
                        else -> DotState.IDLE
                    }
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            ui.phase == ExecutionPhase.PLANNING -> "Planning…"
                            ui.finalStatus == TaskStatus.COMPLETED -> "Completed"
                            ui.finalStatus == TaskStatus.CANCELLED -> "Cancelled"
                            ui.finalStatus == TaskStatus.FAILED -> "Needs attention"
                            ui.phase == ExecutionPhase.WAITING_APPROVAL -> "Waiting for you"
                            else -> "Working on your task"
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        color = c.textPrimary
                    )
                    ui.activePlanStep?.let { step ->
                        Text(
                            step.why,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 1
                        )
                    } ?: ui.currentAgent?.let {
                        Text(
                            "$it · " + (ui.currentActivity ?: "working"),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 1
                        )
                    }
                }
                if (running) {
                    Text(
                        "Cancel",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = c.textMuted,
                        modifier = Modifier
                            .clip(OsShapes.pill)
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                } else if (total > 0) {
                    Text(
                        "$done / $total",
                        style = MonoStyle,
                        color = c.textSecondary
                    )
                }
            }

            // progress bar (real ratio, no fake progress)
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                val frac by animateFloatAsState(
                    if (total == 0) 0f else done.toFloat() / total,
                    animationSpec = androidx.compose.animation.core.tween(OsMotion.Emphasis), label = "progress"
                )
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(OsShapes.pill),
                    color = if (ui.finalStatus == TaskStatus.FAILED) c.error else c.accent,
                    trackColor = c.surfaceInteractive
                )
            }

            // steps (peek: first 4, tap for full sheet)
            if (ui.plan.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ui.plan.take(4).forEach { step ->
                    StepRow(step)
                }
                if (ui.plan.size > 4) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "View plan (+${ui.plan.size - 4} more)",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = c.accent,
                        modifier = Modifier.padding(start = 30.dp, top = 2.dp)
                    )
                }
            }

            // screenshot previews (real attachments)
            AnimatedVisibility(ui.attachments.isNotEmpty(), enter = fadeIn() + expandVertically()) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.attachments.take(3).forEach { att ->
                            ScreenshotPreview(att, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** One plan step: ✓ done · ◉ running · ○ pending · ✕ failed. */
@Composable
fun StepRow(step: PlanStepUi, modifier: Modifier = Modifier) {
    val c = osColors()
    Row(
        modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val mark = when (step.status) {
            StepStatus.COMPLETED -> "✓"
            StepStatus.RUNNING -> "◉"
            StepStatus.FAILED -> "✕"
            StepStatus.AWAITING_APPROVAL -> "⏸"
            else -> "○"
        }
        val color = when (step.status) {
            StepStatus.COMPLETED -> c.success
            StepStatus.RUNNING -> c.accent
            StepStatus.FAILED -> c.error
            StepStatus.AWAITING_APPROVAL -> c.warning
            else -> c.textMuted
        }
        Text(mark, color = color, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
        Column(Modifier.weight(1f)) {
            Text(
                step.why,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = if (step.status == StepStatus.PENDING) c.textSecondary else c.textPrimary,
                maxLines = 2
            )
            if (step.status == StepStatus.RUNNING) {
                Text(
                    "${toolDisplayName(step.tool)} · working",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = c.accent
                )
            } else if (step.status == StepStatus.FAILED && step.error != null) {
                Text(
                    step.error!!.take(90),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = c.error,
                    maxLines = 2
                )
            }
        }
    }
}

/** Full plan bottom-sheet content. */
@Composable
private fun PlanSheetContent(ui: ChatUiState) {
    val c = osColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Task Plan", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "${ui.completedSteps} of ${ui.plan.size} completed" + when (ui.finalStatus) {
                TaskStatus.COMPLETED -> " · completed"
                TaskStatus.FAILED -> " · failed"
                TaskStatus.CANCELLED -> " · cancelled"
                else -> ""
            },
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = c.textSecondary
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(ui.plan) { step ->
                Column {
                    StepRow(step)
                    if (step.result != null && step.status == StepStatus.COMPLETED) {
                        Text(
                            "↳ ${step.result!!.take(140)}",
                            style = MonoStyle.copy(fontFamily = FontFamily.Monospace),
                            color = c.textMuted,
                            maxLines = 2,
                            modifier = Modifier.padding(start = 30.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val agents = ui.plan.map { it.agent }.distinct()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            agents.forEach { AgentBadge(it) }
        }
    }
}

/** Small screenshot preview decoded from the real attachment path. */
@Composable
fun ScreenshotPreview(att: AttachmentUi, modifier: Modifier = Modifier) {
    val c = osColors()
    var full by remember { mutableStateOf(false) }
    val bitmap = remember(att.path) {
        runCatching { BitmapFactory.decodeFile(att.path)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surfaceInteractive)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .clickable(enabled = bitmap != null) { full = true },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = att.caption, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
        } else {
            Text("screenshot", style = MonoStyle, color = c.textMuted)
        }
    }
    if (full && bitmap != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { full = false }) {
            Image(
                bitmap,
                contentDescription = att.caption,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
