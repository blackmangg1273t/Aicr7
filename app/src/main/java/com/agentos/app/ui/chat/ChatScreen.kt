package com.agentos.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentos.app.data.db.MessageEntity
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.MessageRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    messages: List<MessageEntity>,
    uiState: ChatUiState
) {
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    // auto-scroll on new content
    LaunchedEffect(messages.size, uiState.streamingAnswer, uiState.currentActivity) {
        val target = messages.size - 1 + (if (uiState.streamingAnswer != null) 1 else 0)
        if (target >= 0) listState.animateScrollToItem(target)
    }

    // imePadding: with edge-to-edge, adjustResize alone does not lift content above
    // the keyboard — the input bar must pad by the IME inset explicitly.
    Column(Modifier.fillMaxSize().imePadding()) {
        if (uiState.offline) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "Offline — AI features unavailable. Local tools still work.",
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // live activity strip
        if (uiState.running) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiState.currentAgent ?: "Main Agent",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        uiState.currentActivity?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                        uiState.planSummary?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                        uiState.streamingAnswer?.let {
                            Text(
                                "…${it.takeLast(80)}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 2
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.cancelTask() }, Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "Cancel task", Modifier.size(16.dp))
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty() && uiState.streamingAnswer == null) {
                item { Box(Modifier.fillParentMaxSize()) { EmptyChatState() } }
            }
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg, uiState)
            }
            // live streaming answer bubble
            uiState.streamingAnswer?.let { streaming ->
                item {
                    StreamingBubble(streaming)
                }
            }
        }

        // approval card
        uiState.pendingApproval?.let { approval ->
            ApprovalCard(approval, onApprove = { viewModel.resolveApproval(true) }, onDeny = { viewModel.resolveApproval(false) })
        }

        // error card
        uiState.lastError?.let { err ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        err,
                        Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                }
            }
        }

        // input bar
        Surface(shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask, search, automate…") },
                    maxLines = 4,
                    enabled = !uiState.running
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !uiState.running
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("AgentOS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your on-device AI assistant. Ask anything, or try:\n" +
                "\u2022 \"Search for the latest news about AI\"\n" +
                "\u2022 \"Open YouTube and play lo-fi music\"\n" +
                "\u2022 \"Run df -h in the terminal\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            SelectionContainer {
                Text(
                    text + " ▍",
                    Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun MessageBubble(msg: MessageEntity, uiState: ChatUiState) {
    when (msg.role) {
        MessageRole.USER.name -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                    modifier = Modifier.widthIn(max = 320.dp)
                ) {
                    Text(msg.text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        MessageRole.ASSISTANT.name -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                    modifier = Modifier.widthIn(max = 340.dp)
                ) {
                    SelectionContainer {
                        Text(msg.text, Modifier.padding(12.dp))
                    }
                }
            }
        }
        MessageRole.EVENT.name -> EventBubble(msg)
        else -> {
            Text(
                msg.text,
                Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EventBubble(msg: MessageEntity) {
    val isError = msg.severity == EventSeverity.ERROR.name
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        msg.eventType == EventType.APPROVAL_REQUIRED.name -> MaterialTheme.colorScheme.tertiary
        msg.eventType == EventType.APPROVAL_RESOLVED.name -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(
            color = color.copy(alpha = 0.10f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (msg.agentName != null) {
                    Text(
                        msg.agentName!!,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    msg.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 6
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(approval: PendingApprovalUi, onApprove: () -> Unit, onDeny: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("⚠ Approval required", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(approval.reason, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Tool: ${approval.tool}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Args: ${approval.args.take(300)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                OutlinedButton(onClick = onDeny, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Deny")
                }
                Button(onClick = onApprove) { Text("Approve") }
            }
        }
    }
}
