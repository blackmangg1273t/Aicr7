package com.agentos.app.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.ui.components.AgentBadge
import com.agentos.app.ui.components.DotState
import com.agentos.app.ui.components.GradientProgressBar
import com.agentos.app.ui.components.StatusDot
import com.agentos.app.ui.components.toolDisplayName
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsSprings
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import com.agentos.app.ui.theme.rememberElapsedSeconds

/* ------------------------------------------------------------------ */
/* Live Execution Card                                                  */
/* Shows the REAL plan from the planner, with live step statuses,       */
/* true elapsed time and expandable step results.                       */
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
    val finished = ui.finalStatus != null

    // real elapsed time, frozen when the task ends
    val elapsed = rememberElapsedSeconds(
        active = running && ui.phase != ExecutionPhase.WAITING_APPROVAL,
        resetKey = ui.taskId
    )

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
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary
                    )
                    ui.activePlanStep?.let { step ->
                        Text(
                            step.why,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } ?: ui.currentAgent?.let {
                        Text(
                            "$it · " + (ui.currentActivity ?: "working"),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // elapsed chip (real)
                if (elapsed > 0 && !finished) {
                    Row(
                        Modifier
                            .clip(OsShapes.pill)
                            .background(c.surfaceInteractive)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = c.textMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            formatElapsed(elapsed),
                            style = MonoStyle.copy(fontSize = 10.sp),
                            color = c.textSecondary
                        )
                    }
                }
                if (running) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.labelMedium,
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
                GradientProgressBar(
                    progress = if (total == 0) 0f else done.toFloat() / total,
                    error = ui.finalStatus == TaskStatus.FAILED
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
                        style = MaterialTheme.typography.labelMedium,
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

            // completion result reveal (real final result)
            AnimatedVisibility(
                visible = finished,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically()
            ) {
                CompletionResult(ui)
            }
        }
    }
}

/** Animated completion section: spring check + final answer with copy. */
@Composable
private fun CompletionResult(ui: ChatUiState) {
    val c = osColors()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }
    val checkScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = OsSprings.celebrate(),
        label = "checkPop"
    )
    Spacer(Modifier.height(12.dp))
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
                    .size(20.dp)
                    .clip(CircleShape)
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
            Spacer(Modifier.height(8.dp))
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

/** One plan step: ✓ done · ◉ running · ○ pending · ✕ failed — tap to expand results. */
@Composable
fun StepRow(step: PlanStepUi, modifier: Modifier = Modifier) {
    val c = osColors()
    var expanded by rememberSaveable(step.index) { mutableStateOf(false) }
    val hasDetail = step.result != null || step.error != null || step.tool != null
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    val runningNow = step.status == StepStatus.RUNNING

    Column(modifier.fillMaxWidth().clickable(enabled = hasDetail) { expanded = !expanded }) {
        Row(
            Modifier.padding(vertical = 4.dp),
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
            Text(mark, color = color, style = MaterialTheme.typography.titleSmall)
            Column(Modifier.weight(1f)) {
                Text(
                    step.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.status == StepStatus.PENDING) c.textSecondary else c.textPrimary,
                    maxLines = 2
                )
                if (runningNow) {
                    Text(
                        "${toolDisplayName(step.tool)} · working",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accent
                    )
                } else if (step.status == StepStatus.FAILED && step.error != null) {
                    Text(
                        step.error!!.take(90),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.error,
                        maxLines = 2
                    )
                } else if (step.status == StepStatus.COMPLETED && step.result != null && !expanded) {
                    Text(
                        "↳ ${step.result!!.take(70)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hasDetail) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = c.textMuted,
                    modifier = Modifier.size(16.dp).rotate(chevron).alpha(0.7f)
                )
            }
        }
        // expanded detail: tool + full result/error (real data)
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, bottom = 6.dp)
                    .clip(OsShapes.cardSmall)
                    .background(c.surface)
                    .border(1.dp, c.border, OsShapes.cardSmall)
                    .padding(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AgentBadge(step.agent)
                    step.tool?.let {
                        Text(
                            it,
                            style = MonoStyle.copy(fontSize = 10.sp),
                            color = c.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                step.result?.let { r ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        r,
                        style = MonoStyle.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                        color = c.textSecondary,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                step.error?.let { e ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e,
                        style = MonoStyle.copy(fontSize = 10.sp),
                        color = c.error,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Full plan bottom-sheet content. */
@Composable
private fun PlanSheetContent(ui: ChatUiState) {
    val c = osColors()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Text("Task Plan", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "${ui.completedSteps} of ${ui.plan.size} completed" + when (ui.finalStatus) {
                TaskStatus.COMPLETED -> " · completed"
                TaskStatus.FAILED -> " · failed"
                TaskStatus.CANCELLED -> " · cancelled"
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary
        )
        Spacer(Modifier.height(14.dp))
        if (ui.plan.isNotEmpty()) {
            GradientProgressBar(progress = ui.completedSteps.toFloat() / ui.plan.size, error = ui.finalStatus == TaskStatus.FAILED)
            Spacer(Modifier.height(14.dp))
        }
        LazyColumnSafe(ui)
        Spacer(Modifier.height(16.dp))
        val agents = ui.plan.map { it.agent }.distinct()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            agents.forEach { AgentBadge(it) }
        }
    }
}

@Composable
private fun LazyColumnSafe(ui: ChatUiState) {
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.heightIn(max = 420.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(ui.plan.size) { idx ->
            val step = ui.plan[idx]
            Column {
                StepRow(step)
            }
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

internal fun formatElapsed(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}
