package com.agentos.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentos.app.data.db.TaskEntity
import com.agentos.app.data.db.TaskStepEntity
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.ui.components.AgentBadge
import com.agentos.app.ui.components.AuraCard
import com.agentos.app.ui.components.toolDisplayName
import com.agentos.app.ui.theme.AgentOsColors
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import java.util.concurrent.TimeUnit

/* ------------------------------------------------------------------ */
/* Tasks — 2026 redesign: status-grouped list with inline expansion      */
/*                                                                       */
/*  • Three sections: Running / Completed / Failed                       */
/*  • Each card shows: status icon, title, label, duration, step chip,   */
/*    current agent badge (while running), and an [Open] pill button      */
/*  • Tap Open to expand inline — plan steps, live activity,            */
/*    verification status, final result/error                            */
/*  • Failed / Cancelled / Interrupted tasks show a gradient Resume      */
/*    button that re-launches the original request through the engine    */
/* ------------------------------------------------------------------ */

/** Electric blue / violet accents — the 2026 AgentOS palette. */
private val ElectricBlue = Color(0xFF4F8AFF)
private val ElectricViolet = Color(0xFF8B5CF6)

/** Section membership — matches the redesigned TaskStatus enum. */
private val RUNNING_STATUSES = setOf(
    TaskStatus.PENDING, TaskStatus.PLANNING, TaskStatus.PLAN_READY,
    TaskStatus.RUNNING, TaskStatus.WAITING_USER, TaskStatus.RECOVERING,
    TaskStatus.REPLANNING, TaskStatus.VERIFYING
)
private val COMPLETED_STATUSES = setOf(TaskStatus.COMPLETED, TaskStatus.PARTIAL)
private val FAILED_STATUSES = setOf(TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.INTERRUPTED)

private fun parseStatus(task: TaskEntity): TaskStatus =
    runCatching { TaskStatus.valueOf(task.status) }.getOrDefault(TaskStatus.PENDING)

/** Unicode status glyph per the spec: ✓ ● ○ ✕ ⏸ ↻ ⟳ ⊕. */
private fun statusIcon(status: TaskStatus): String = when (status) {
    TaskStatus.PENDING -> "○"
    TaskStatus.PLANNING, TaskStatus.PLAN_READY, TaskStatus.RUNNING -> "●"
    TaskStatus.WAITING_USER -> "⏸"
    TaskStatus.RECOVERING -> "↻"
    TaskStatus.REPLANNING -> "⟳"
    TaskStatus.VERIFYING -> "⊕"
    TaskStatus.COMPLETED, TaskStatus.PARTIAL -> "✓"
    TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.INTERRUPTED -> "✕"
}

/** Human label per the spec ("Running", "Completed", "Needs attention", …). */
private fun statusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.PENDING -> "Pending"
    TaskStatus.PLANNING -> "Planning"
    TaskStatus.PLAN_READY -> "Plan ready"
    TaskStatus.RUNNING -> "Running"
    TaskStatus.WAITING_USER -> "Waiting for you"
    TaskStatus.RECOVERING -> "Recovering"
    TaskStatus.REPLANNING -> "Replanning"
    TaskStatus.VERIFYING -> "Verifying"
    TaskStatus.COMPLETED -> "Completed"
    TaskStatus.PARTIAL -> "Partial"
    TaskStatus.FAILED -> "Needs attention"
    TaskStatus.CANCELLED -> "Cancelled"
    TaskStatus.INTERRUPTED -> "Interrupted"
}

/** Accent color used for the icon and label, keyed by status family. */
private fun statusAccent(status: TaskStatus, c: AgentOsColors): Color = when (status) {
    TaskStatus.COMPLETED, TaskStatus.PARTIAL -> c.success
    TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.INTERRUPTED -> c.error
    TaskStatus.WAITING_USER -> c.warning
    TaskStatus.RECOVERING, TaskStatus.REPLANNING, TaskStatus.VERIFYING -> ElectricViolet
    TaskStatus.PENDING -> c.textMuted
    else -> ElectricBlue // PLANNING, PLAN_READY, RUNNING
}

@Composable
fun TasksScreen(
    tasks: List<TaskEntity>,
    selectedTask: TaskEntity?,
    selectedSteps: List<TaskStepEntity>,
    onSelect: (String?) -> Unit,
    onResume: (String) -> Unit = {},
    allSteps: Map<String, List<TaskStepEntity>> = emptyMap()
) {
    val c = osColors()

    val grouped = remember(tasks) {
        Triple(
            tasks.filter { parseStatus(it) in RUNNING_STATUSES },
            tasks.filter { parseStatus(it) in COMPLETED_STATUSES },
            tasks.filter { parseStatus(it) in FAILED_STATUSES }
        )
    }
    val (running, completed, failed) = grouped

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Title bar + live running count
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tasks",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (running.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(OsShapes.pill)
                            .background(ElectricBlue.copy(alpha = 0.12f))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.35f), OsShapes.pill)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(Modifier.size(6.dp).clip(OsShapes.pill).background(ElectricBlue))
                        Text(
                            "${running.size} running",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricBlue
                        )
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.History,
                            contentDescription = null,
                            tint = c.textMuted,
                            modifier = Modifier.size(30.dp).alpha(0.7f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No tasks yet.\nAsk AgentOS to do something in Chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (running.isNotEmpty()) {
            item { SectionHeader("Running", running.size, ElectricBlue) }
            items(running, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    steps = stepsFor(task, selectedTask, selectedSteps, allSteps),
                    expanded = selectedTask?.id == task.id,
                    onToggle = { onSelect(if (selectedTask?.id == task.id) null else task.id) },
                    onResume = { onResume(task.id) }
                )
            }
        }

        if (completed.isNotEmpty()) {
            item { SectionHeader("Completed", completed.size, c.success) }
            items(completed, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    steps = stepsFor(task, selectedTask, selectedSteps, allSteps),
                    expanded = selectedTask?.id == task.id,
                    onToggle = { onSelect(if (selectedTask?.id == task.id) null else task.id) },
                    onResume = { onResume(task.id) }
                )
            }
        }

        if (failed.isNotEmpty()) {
            item { SectionHeader("Failed", failed.size, c.error) }
            items(failed, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    steps = stepsFor(task, selectedTask, selectedSteps, allSteps),
                    expanded = selectedTask?.id == task.id,
                    onToggle = { onSelect(if (selectedTask?.id == task.id) null else task.id) },
                    onResume = { onResume(task.id) }
                )
            }
        }
    }
}

/**
 * Resolves the steps to render for a card. For the currently-selected (expanded)
 * task we prefer the dedicated [selectedSteps] flow — it's the source of truth
 * for live step updates. For other (collapsed) cards we use the [allSteps] map
 * so the "Step X/Y" chip and current-agent badge can render before expansion.
 */
private fun stepsFor(
    task: TaskEntity,
    selectedTask: TaskEntity?,
    selectedSteps: List<TaskStepEntity>,
    allSteps: Map<String, List<TaskStepEntity>>
): List<TaskStepEntity> =
    if (selectedTask?.id == task.id) selectedSteps else allSteps[task.id] ?: emptyList()

/* ---------------------------- section header ---------------------------- */

@Composable
private fun SectionHeader(title: String, count: Int, color: Color) {
    val c = osColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(OsShapes.pill).background(color))
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
        Text("· $count", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
    }
}

/* ------------------------------ task card ------------------------------ */

@Composable
private fun TaskCard(
    task: TaskEntity,
    steps: List<TaskStepEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onResume: () -> Unit
) {
    val c = osColors()
    val status = parseStatus(task)
    val isRunning = status in RUNNING_STATUSES
    val isFailed = status in FAILED_STATUSES
    val accent = statusAccent(status, c)
    val icon = statusIcon(status)
    val label = statusLabel(status)

    val runningStep = steps.firstOrNull { it.status == StepStatus.RUNNING.name }
    val currentAgent = runningStep?.agentName
        ?: if (isRunning) steps.lastOrNull()?.agentName else null
    val doneCount = steps.count {
        it.status == StepStatus.COMPLETED.name || it.status == StepStatus.VERIFIED.name
    }
    val totalCount = steps.size

    AuraCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onToggle() },
        active = isRunning
    ) {
        Column(Modifier.padding(15.dp)) {
            // Row 1: status icon + title + step chip + Open pill
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, color = accent, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.userRequest,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        maxLines = if (expanded) 3 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
                        if (task.updatedAt > task.createdAt) {
                            Text(
                                " · ${formatDuration(task.updatedAt - task.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textMuted
                            )
                        }
                    }
                }
                if (totalCount > 0) {
                    Box(
                        Modifier
                            .clip(OsShapes.pill)
                            .background(c.surfaceInteractive)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Step $doneCount/$totalCount",
                            style = MonoStyle.copy(fontSize = 10.sp),
                            color = c.textSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .clip(OsShapes.pill)
                        .background(if (expanded) c.surfaceInteractive else ElectricBlue.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            if (expanded) c.border else ElectricBlue.copy(alpha = 0.35f),
                            OsShapes.pill
                        )
                        .clickable { onToggle() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (expanded) "Close" else "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (expanded) c.textSecondary else ElectricBlue
                    )
                }
            }

            // Current agent badge (only while a step is actually running)
            if (isRunning && currentAgent != null) {
                Spacer(Modifier.height(10.dp))
                AgentBadge(currentAgent, live = true)
            }

            // Inline expansion — full plan, activity, verification, result
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                ExpandedDetail(task, steps, status, onResume)
            } else if (isFailed) {
                // A subtle "Resume available" hint on collapsed failed cards
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("↻", color = ElectricViolet, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Tap to resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricViolet
                    )
                }
            }
        }
    }
}

/* --------------------------- expanded detail --------------------------- */

@Composable
private fun ExpandedDetail(
    task: TaskEntity,
    steps: List<TaskStepEntity>,
    status: TaskStatus,
    onResume: () -> Unit
) {
    val c = osColors()
    Column {
        // Resume button — only for failed / cancelled / interrupted tasks
        if (status in FAILED_STATUSES) {
            ResumeButton(onResume)
            Spacer(Modifier.height(12.dp))
        }

        // Current agent — explicit badge while the task is in a running family
        val runningStep = steps.firstOrNull { it.status == StepStatus.RUNNING.name }
        val activeAgent = runningStep?.agentName
            ?: if (status in RUNNING_STATUSES) steps.lastOrNull()?.agentName else null
        if (activeAgent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Current agent",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
                AgentBadge(activeAgent, live = status in RUNNING_STATUSES)
            }
            Spacer(Modifier.height(10.dp))
        }

        // Live activity (or last finished step while between steps)
        if (runningStep != null) {
            LiveActivityRow(runningStep, dimmed = false)
            Spacer(Modifier.height(12.dp))
        } else if (status in RUNNING_STATUSES && steps.isNotEmpty()) {
            steps.lastOrNull()?.let {
                LiveActivityRow(it, dimmed = true)
                Spacer(Modifier.height(12.dp))
            }
        }

        // Verification status
        val verifiedCount = steps.count { it.status == StepStatus.VERIFIED.name }
        if (status == TaskStatus.VERIFYING) {
            VerificationRow(inProgress = true, count = verifiedCount)
            Spacer(Modifier.height(12.dp))
        } else if (verifiedCount > 0) {
            VerificationRow(inProgress = false, count = verifiedCount)
            Spacer(Modifier.height(12.dp))
        }

        // Plan steps with status icons
        if (steps.isNotEmpty()) {
            Text("Plan", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(6.dp))
            steps.forEach { step ->
                StepRow(step)
                Spacer(Modifier.height(6.dp))
            }
        }

        // Error block
        task.error?.let { err ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(OsShapes.cardSmall)
                    .background(c.errorContainer)
                    .padding(12.dp)
            ) {
                Text("Reason", style = MaterialTheme.typography.labelMedium, color = c.error)
                Spacer(Modifier.height(3.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
            }
            Spacer(Modifier.height(8.dp))
        }

        // Final result
        task.finalResult?.let { result ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(OsShapes.cardSmall)
                    .background(if (status in COMPLETED_STATUSES) c.successContainer else c.surfaceHigh)
                    .border(1.dp, c.border, OsShapes.cardSmall)
                    .padding(12.dp)
            ) {
                Text(
                    "Result",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status in COMPLETED_STATUSES) c.success else c.textSecondary
                )
                Spacer(Modifier.height(3.dp))
                Text(result, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun ResumeButton(onResume: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.pill)
            .background(Brush.linearGradient(listOf(ElectricBlue, ElectricViolet)))
            .clickable { onResume() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("↻", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        Text("Resume task", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LiveActivityRow(step: TaskStepEntity, dimmed: Boolean) {
    val c = osColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.cardSmall)
            .background(c.surfaceInteractive)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (dimmed) "○" else "●",
            color = if (dimmed) c.textMuted else ElectricBlue,
            style = MaterialTheme.typography.labelMedium
        )
        Column(Modifier.weight(1f)) {
            Text(
                step.why,
                style = MaterialTheme.typography.bodySmall,
                color = if (dimmed) c.textSecondary else c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${step.agentName.replaceFirstChar { it.uppercase() }} Agent · ${
                    step.toolName?.let { toolDisplayName(it) } ?: "thinking"
                }",
                style = MonoStyle.copy(fontSize = 10.sp),
                color = c.textMuted
            )
        }
    }
}

@Composable
private fun VerificationRow(inProgress: Boolean, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.cardSmall)
            .background(ElectricViolet.copy(alpha = 0.10f))
            .border(1.dp, ElectricViolet.copy(alpha = 0.3f), OsShapes.cardSmall)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (inProgress) "⊕" else "✓",
            color = ElectricViolet,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            when {
                inProgress -> "Verifying results…"
                count > 1 -> "$count steps verified"
                else -> "Verified"
            },
            style = MaterialTheme.typography.labelMedium,
            color = ElectricViolet
        )
        Spacer(Modifier.weight(1f))
        // subtle outline marker so the row sits well next to other blocks
        Box(Modifier.size(6.dp).clip(OsShapes.pill).background(ElectricViolet.copy(alpha = 0.5f)))
    }
}

@Composable
private fun StepRow(step: TaskStepEntity) {
    val c = osColors()
    val mark = when (step.status) {
        StepStatus.COMPLETED.name -> "✓"
        StepStatus.VERIFIED.name -> "✓"
        StepStatus.RUNNING.name -> "●"
        StepStatus.FAILED.name -> "✕"
        StepStatus.AWAITING_APPROVAL.name -> "⏸"
        StepStatus.RECOVERING.name -> "↻"
        StepStatus.SKIPPED.name -> "—"
        else -> "○"
    }
    val markColor = when (step.status) {
        StepStatus.COMPLETED.name, StepStatus.VERIFIED.name -> c.success
        StepStatus.RUNNING.name -> ElectricBlue
        StepStatus.FAILED.name -> c.error
        StepStatus.AWAITING_APPROVAL.name -> c.warning
        StepStatus.RECOVERING.name -> ElectricViolet
        StepStatus.SKIPPED.name -> c.textMuted
        else -> c.textMuted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.cardSmall)
            .background(c.surfaceHigh)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(mark, color = markColor, style = MaterialTheme.typography.titleSmall)
        Column(Modifier.weight(1f)) {
            Text(
                step.why,
                style = MaterialTheme.typography.bodySmall,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${step.agentName.replaceFirstChar { it.uppercase() }} Agent" +
                    (step.toolName?.let { " · ${toolDisplayName(it)} ($it)" } ?: ""),
                style = MonoStyle.copy(fontSize = 10.sp),
                color = c.textMuted
            )
            step.result?.let { r ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "↳ ${r.take(160)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            step.error?.let { e ->
                Spacer(Modifier.height(4.dp))
                Text(
                    e.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------ helpers ------------------------------ */

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val s = TimeUnit.MILLISECONDS.toSeconds(ms)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${ms / 60000}m ${s % 60}s"
        else -> "${TimeUnit.MILLISECONDS.toHours(ms)}h ${(s % 3600) / 60}m"
    }
}
