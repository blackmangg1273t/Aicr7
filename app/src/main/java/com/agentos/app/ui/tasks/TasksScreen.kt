package com.agentos.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.agentos.app.data.db.TaskEntity
import com.agentos.app.data.db.TaskStepEntity
import com.agentos.app.domain.model.StepStatus
import com.agentos.app.domain.model.TaskStatus

@Composable
fun TasksScreen(
    tasks: List<TaskEntity>,
    selectedTask: TaskEntity?,
    selectedSteps: List<TaskStepEntity>,
    onSelect: (String?) -> Unit
) {
    if (selectedTask == null) {
        TaskList(tasks, onSelect)
    } else {
        TaskDetail(selectedTask, selectedSteps, onSelect)
    }
}

private fun statusColor(status: String): Color = when (status) {
    TaskStatus.COMPLETED.name -> Color(0xFF2E7D32)
    TaskStatus.FAILED.name, TaskStatus.CANCELLED.name -> Color(0xFFC62828)
    TaskStatus.RUNNING.name, TaskStatus.PLANNING.name -> Color(0xFF1565C0)
    TaskStatus.WAITING_USER.name -> Color(0xFFEF6C00)
    else -> Color.Gray
}

@Composable
private fun TaskList(tasks: List<TaskEntity>, onSelect: (String) -> Unit) {
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tasks yet.\nAsk something in the Chat tab.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tasks, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(task.id) }) {
                Column(Modifier.padding(14.dp)) {
                    Text(task.userRequest, maxLines = 2, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(task.status)
                        Spacer(Modifier.weight(1f))
                        Text(
                            java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(task.createdAt)),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    task.error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    Surface(color = statusColor(status).copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            status.lowercase().replace('_', ' '),
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = statusColor(status),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TaskDetail(task: TaskEntity, steps: List<TaskStepEntity>, onSelect: (String?) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { onSelect(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Task details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(task.userRequest, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row { StatusChip(task.status) }
                        task.finalResult?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Result", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        task.error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text("Error", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            items(steps, key = { it.id }) { step ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#${step.index + 1} ${step.agentName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(8.dp))
                            StatusChip2(step.status)
                        }
                        if (step.why.isNotBlank()) {
                            Text(step.why, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                        step.toolName?.let {
                            Text(
                                "tool: $it  args: ${step.argsJson.take(120)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        step.result?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it.take(800), style = MaterialTheme.typography.bodySmall, maxLines = 12)
                        }
                        step.error?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        val duration = step.startedAt?.let { s ->
                            step.finishedAt?.let { f -> "${(f - s)}ms" } ?: "running…"
                        }
                        if (duration != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(duration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip2(status: String) {
    val color = when (status) {
        StepStatus.COMPLETED.name -> Color(0xFF2E7D32)
        StepStatus.FAILED.name -> Color(0xFFC62828)
        StepStatus.RUNNING.name -> Color(0xFF1565C0)
        StepStatus.AWAITING_APPROVAL.name -> Color(0xFFEF6C00)
        StepStatus.SKIPPED.name -> Color.Gray
        else -> Color(0xFF616161)
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            status.lowercase().replace('_', ' '),
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
