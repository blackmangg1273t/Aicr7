package com.agentos.app.ui.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentos.app.data.db.TaskEntity
import com.agentos.app.data.db.TaskStepEntity
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.ui.components.AuraCard
import com.agentos.app.ui.components.DotState
import com.agentos.app.ui.components.StatusDot
import com.agentos.app.ui.components.toolDisplayName
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/* Tasks — live overview + history with filtering                       */
/* ------------------------------------------------------------------ */

private enum class TaskFilter(val label: String) {
    ALL("All"), ACTIVE("Running"), DONE("Completed"), FAILED("Failed")
}

private fun TaskEntity.isActive() =
    status in setOf(TaskStatus.PENDING.name, TaskStatus.PLANNING.name, TaskStatus.RUNNING.name, TaskStatus.WAITING_USER.name)

@Composable
fun TasksScreen(
    tasks: List<TaskEntity>,
    selectedTask: TaskEntity?,
    selectedSteps: List<TaskStepEntity>,
    onSelect: (String?) -> Unit
) {
    if (selectedTask == null) TaskList(tasks, onSelect) else TaskDetail(selectedTask, selectedSteps) { onSelect(null) }
}

@Composable
private fun TaskList(tasks: List<TaskEntity>, onSelect: (String?) -> Unit) {
    val c = osColors()
    var filter by remember { mutableStateOf(TaskFilter.ALL) }

    val filtered = remember(tasks, filter) {
        when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.ACTIVE -> tasks.filter { it.isActive() }
            TaskFilter.DONE -> tasks.filter { it.status == TaskStatus.COMPLETED.name }
            TaskFilter.FAILED -> tasks.filter { it.status == TaskStatus.FAILED.name || it.status == TaskStatus.CANCELLED.name }
        }
    }
    val active = tasks.filter { it.isActive() }
    val recent = filtered.filterNot { it.isActive() }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tasks", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (active.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StatusDot(DotState.RUNNING, size = 5f)
                    Text("${active.size} running", style = MaterialTheme.typography.labelMedium, color = c.accent)
                }
            }
        }

        // filter chips
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskFilter.entries.forEach { f ->
                val selected = filter == f
                val bg by animateColorAsState(
                    if (selected) c.accent.copy(alpha = 0.16f) else c.surfaceHigh,
                    tween(OsMotion.Fast), label = "chipBg"
                )
                Text(
                    f.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) c.accent else c.textSecondary,
                    modifier = Modifier
                        .clip(OsShapes.pill)
                        .background(bg)
                        .border(1.dp, if (selected) c.accent.copy(alpha = 0.35f) else c.border, OsShapes.pill)
                        .clickable { filter = f }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    com.agentos.app.ui.chat.BrandMark(size = 20f)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No tasks yet.\nAsk AgentOS to do something in Chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (active.isNotEmpty() && filter != TaskFilter.DONE && filter != TaskFilter.FAILED) {
                item { SectionLabel("Running") }
                items(active, key = { it.id }) { TaskRow(it, onSelect, active = true) }
            }
            if (recent.isNotEmpty()) {
                item { SectionLabel(if (filter == TaskFilter.ALL) "Recent" else "") }
                items(recent, key = { it.id }) { TaskRow(it, onSelect, active = false) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = osColors()
    if (text.isNotBlank()) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = c.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onSelect: (String?) -> Unit, active: Boolean) {
    val c = osColors()
    val status = task.status
    val dot = when (status) {
        TaskStatus.COMPLETED.name -> DotState.DONE
        TaskStatus.FAILED.name -> DotState.ERROR
        TaskStatus.CANCELLED.name -> DotState.IDLE
        TaskStatus.WAITING_USER.name -> DotState.WAITING
        else -> DotState.RUNNING
    }
    if (active) {
        AuraCard(Modifier.fillMaxWidth().clickable { onSelect(task.id) }, active = true) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(dot)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.userRequest,
                        style = MaterialTheme.typography.titleSmall,
                        color = c.textPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        when (status) {
                            TaskStatus.PLANNING.name -> "Planning…"
                            TaskStatus.WAITING_USER.name -> "Waiting for you"
                            else -> "Working…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.accent
                    )
                }
                Text("✦", color = c.accent, style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(OsShapes.cardSmall)
                .background(c.surfaceHigh)
                .clickable { onSelect(task.id) }
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(dot, size = 6f)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.userRequest,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatTime(task.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textMuted
                )
            }
            Text(
                when (status) {
                    TaskStatus.COMPLETED.name -> "✓"
                    TaskStatus.FAILED.name -> "✕"
                    TaskStatus.CANCELLED.name -> "—"
                    else -> "●"
                },
                color = when (status) {
                    TaskStatus.COMPLETED.name -> c.success
                    TaskStatus.FAILED.name -> c.error
                    else -> c.textMuted
                },
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

/* ---------------------------- task detail ---------------------------- */

@Composable
private fun TaskDetail(task: TaskEntity, steps: List<TaskStepEntity>, onBack: () -> Unit) {
    val c = osColors()
    val dot = when (task.status) {
        TaskStatus.COMPLETED.name -> DotState.DONE
        TaskStatus.FAILED.name -> DotState.ERROR
        TaskStatus.CANCELLED.name -> DotState.IDLE
        TaskStatus.WAITING_USER.name -> DotState.WAITING
        else -> DotState.RUNNING
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "←  Back",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier
                        .clip(OsShapes.pill)
                        .clickable { onBack() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        item {
            Column {
                Text(task.userRequest, style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    StatusDot(dot, size = 6f)
                    Text(
                        when (task.status) {
                            TaskStatus.PLANNING.name -> "Planning"
                            TaskStatus.RUNNING.name -> "Working"
                            TaskStatus.WAITING_USER.name -> "Waiting for you"
                            TaskStatus.COMPLETED.name -> "Completed"
                            TaskStatus.FAILED.name -> "Needs attention"
                            TaskStatus.CANCELLED.name -> "Cancelled"
                            else -> "Ready"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textSecondary
                    )
                    Text("· ${formatTime(task.createdAt)}", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
        }
        task.error?.let { err ->
            item {
                Column(
                    Modifier.fillMaxWidth().clip(OsShapes.cardSmall).background(c.errorContainer).padding(14.dp)
                ) {
                    Text("Reason", style = MaterialTheme.typography.labelMedium, color = c.error)
                    Spacer(Modifier.height(3.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                }
            }
        }
        if (steps.isNotEmpty()) {
            item { Text("Plan & execution", style = MaterialTheme.typography.titleMedium, color = c.textPrimary) }
            items(steps, key = { it.id }) { step ->
                val mark = when (step.status) {
                    StepStatus.COMPLETED.name -> "✓"
                    StepStatus.RUNNING.name -> "◉"
                    StepStatus.FAILED.name -> "✕"
                    StepStatus.AWAITING_APPROVAL.name -> "⏸"
                    StepStatus.SKIPPED.name -> "—"
                    else -> "○"
                }
                val markColor = when (step.status) {
                    StepStatus.COMPLETED.name -> c.success
                    StepStatus.RUNNING.name -> c.accent
                    StepStatus.FAILED.name -> c.error
                    StepStatus.AWAITING_APPROVAL.name -> c.warning
                    else -> c.textMuted
                }
                Column(
                    Modifier.fillMaxWidth().clip(OsShapes.cardSmall).background(c.surfaceHigh).padding(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(mark, color = markColor, style = MaterialTheme.typography.titleSmall)
                        Column(Modifier.weight(1f)) {
                            Text(step.why, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${step.agentName.replaceFirstChar { it.uppercase() }} Agent" +
                                    (step.toolName?.let { " · ${toolDisplayName(it)} ($it)" } ?: ""),
                                style = MonoStyle,
                                color = c.textMuted
                            )
                        }
                    }
                    step.result?.let { r ->
                        Spacer(Modifier.height(6.dp))
                        Text("↳ ${r.take(160)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    step.error?.let { e ->
                        Spacer(Modifier.height(6.dp))
                        Text(e.take(160), style = MaterialTheme.typography.bodySmall, color = c.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        task.finalResult?.let { result ->
            item { Text("Result", style = MaterialTheme.typography.titleMedium, color = c.textPrimary) }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
                ) {
                    Text(result, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(ts))
