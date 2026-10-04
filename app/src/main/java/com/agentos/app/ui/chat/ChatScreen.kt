package com.agentos.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agentos.app.data.db.MessageEntity
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.MessageRole
import com.agentos.app.domain.model.TaskStatus
import com.agentos.app.ui.components.AgentBadge
import com.agentos.app.ui.components.DotState
import com.agentos.app.ui.components.MarkdownText
import com.agentos.app.ui.components.SuggestionCard
import com.agentos.app.ui.components.StatusDot
import com.agentos.app.ui.components.rememberConfirmHaptics
import com.agentos.app.ui.components.toolDisplayName
import com.agentos.app.ui.components.toolHumanSummary
import com.agentos.app.ui.theme.AmbientBackdrop
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import com.agentos.app.ui.theme.rememberElapsedSeconds
import com.agentos.app.ui.theme.rememberStaggeredEntrance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ------------------------------------------------------------------ */
/* Chat — premium agent workspace                                       */
/* ------------------------------------------------------------------ */

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    messages: List<MessageEntity>,
    uiState: ChatUiState,
    onOpenSettings: () -> Unit = {}
) {
    val c = osColors()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // smart auto-scroll: follow the stream only when the user is near the bottom,
    // so reading history is never hijacked.
    val nearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount <= 0 || last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(messages.size, uiState.streamingAnswer?.length, uiState.attachments.size) {
        if (nearBottom) {
            val target = (messages.size - 1 + (if (uiState.streamingAnswer != null) 1 else 0))
                .coerceAtLeast(0)
            listState.scrollToItem(target.coerceAtMost((messages.size + 1).coerceAtLeast(0)))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // global task indicator (appears while the agent works)
            AnimatedVisibility(
                visible = uiState.running && uiState.phase != ExecutionPhase.NONE,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(OsMotion.Normal)),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(OsMotion.Fast))
            ) {
                GlobalTaskPill(uiState) {
                    scope.launch {
                        val idx = messages.size + (if (uiState.streamingAnswer != null) 1 else 0)
                        runCatching { listState.animateScrollToItem(idx) }
                    }
                }
            }

            if (uiState.offline) {
                Surface(color = c.warningContainer) {
                    Text(
                        "Offline — AI features unavailable. Local tools still work.",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.warning
                    )
                }
            }

            // message timeline
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && uiState.streamingAnswer == null && uiState.phase == ExecutionPhase.NONE) {
                    item(key = "empty") {
                        EmptyState(
                            onSuggestion = { input = it },
                            onOpenSettings = onOpenSettings
                        )
                    }
                }
                itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                    val prev = messages.getOrNull(index - 1)
                    Column {
                        if (prev != null && isDifferentDay(prev.timestamp, msg.timestamp)) {
                            DateSeparator(msg.timestamp)
                            Spacer(Modifier.height(6.dp))
                        }
                        MessageItem(msg)
                    }
                }
                uiState.streamingAnswer?.let { streaming ->
                    item(key = "streaming") {
                        StreamingAnswer(streaming)
                    }
                }
                // live execution card sits inside the timeline while a task runs
                if (uiState.phase != ExecutionPhase.NONE && (uiState.plan.isNotEmpty() || uiState.phase == ExecutionPhase.PLANNING || uiState.attachments.isNotEmpty())) {
                    item(key = "execution") {
                        ExecutionCard(
                            ui = uiState,
                            onCancel = { viewModel.cancelTask() },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // approval gate (redesigned)
            uiState.pendingApproval?.let { approval ->
                ApprovalGate(
                    approval = approval,
                    onAllow = { viewModel.resolveApproval(true) },
                    onDeny = { viewModel.resolveApproval(false) }
                )
            }

            // actionable error
            uiState.lastError?.let { err ->
                ErrorBanner(err, onDismiss = { viewModel.clearError() }, onOpenSettings = onOpenSettings)
            }

            // composer
            Box(Modifier.background(c.bg).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Composer(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = !uiState.running
                )
            }
        }

        // scroll-to-bottom affordance when reading history
        AnimatedVisibility(
            visible = !nearBottom && (messages.isNotEmpty() || uiState.streamingAnswer != null),
            enter = fadeIn(tween(OsMotion.Fast)) + slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(OsMotion.Fast)),
            exit = fadeOut(tween(OsMotion.Fast)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        ) {
            Row(
                Modifier
                    .clip(OsShapes.pill)
                    .background(c.surfaceInteractive)
                    .border(1.dp, c.borderStrong, OsShapes.pill)
                    .clickable {
                        scope.launch {
                            runCatching {
                                listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("↓", style = MaterialTheme.typography.titleSmall, color = c.accent)
                Text("Latest", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
    }
}

/* ------------------------- global indicator ------------------------- */

@Composable
private fun GlobalTaskPill(ui: ChatUiState, onTap: () -> Unit) {
    val c = osColors()
    val elapsed = rememberElapsedSeconds(
        active = ui.running && ui.phase != ExecutionPhase.WAITING_APPROVAL,
        resetKey = ui.taskId
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(OsShapes.pill)
            .background(c.surfaceHigh)
            .border(1.dp, c.accent.copy(alpha = 0.25f), OsShapes.pill)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        StatusDot(DotState.RUNNING, size = 6f)
        Text(
            "AgentOS is working",
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary
        )
        Text(
            buildString {
                ui.currentAgent?.let { append("· $it ") }
                if (ui.plan.isNotEmpty()) append("· ${ui.completedSteps}/${ui.plan.size}")
                if (elapsed > 0) append(" · ${formatElapsed(elapsed)}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = c.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text("View", style = MaterialTheme.typography.labelMedium, color = c.accent)
    }
}

/* ---------------------------- empty state --------------------------- */

@Composable
private fun EmptyState(onSuggestion: (String) -> Unit, onOpenSettings: () -> Unit) {
    val c = osColors()
    val titleEntrance = rememberStaggeredEntrance(0)
    Column(
        Modifier.fillMaxSize().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.9f))
        Box(Modifier.alpha(titleEntrance.value)) {
            BrandMark(size = 34f)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "AgentOS",
            style = MaterialTheme.typography.headlineMedium,
            color = c.textPrimary,
            modifier = Modifier.alpha(titleEntrance.value)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "What do you want to do?",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(rememberStaggeredEntrance(1).value)
        )
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            SuggestionCard(
                "Search the web for the latest AI news", c.accent,
                { onSuggestion("Search the web for the latest AI news") },
                alpha = rememberStaggeredEntrance(2).value,
                icon = Icons.Rounded.Search
            )
            SuggestionCard(
                "Open YouTube and search for Minecraft", c.accentViolet,
                { onSuggestion("Open YouTube and search for Minecraft") },
                alpha = rememberStaggeredEntrance(3).value,
                icon = Icons.Rounded.Apps
            )
            SuggestionCard(
                "Control my Android — open Settings", c.accentViolet,
                { onSuggestion("Open the Settings app on my phone") },
                alpha = rememberStaggeredEntrance(4).value,
                icon = Icons.Rounded.Apps
            )
            SuggestionCard(
                "Run a shell command in the workspace", c.success,
                { onSuggestion("Run df -h and show disk usage") },
                alpha = rememberStaggeredEntrance(5).value,
                icon = Icons.Rounded.Terminal
            )
        }
        Spacer(Modifier.weight(1.1f))
        Text(
            "AI provider not configured yet? Set an API key in Settings → AI",
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(rememberStaggeredEntrance(6).value)
                .clip(OsShapes.pill)
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** ✦ brand mark with a gentle breathing glow. */
@Composable
fun BrandMark(size: Float = 28f, modifier: Modifier = Modifier) {
    val c = osColors()
    val breath by com.agentos.app.ui.theme.rememberBreathing(0.4f, 0.9f)
    androidx.compose.foundation.Canvas(modifier.size((size * 2f).dp)) {
        val r = this.size.minDimension / 2f
        drawCircle(c.accent.copy(alpha = breath * 0.22f), radius = r)
        drawCircle(c.accent.copy(alpha = breath * 0.12f), radius = r * 0.65f)
        // four-point star (✦)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val inner = r * 0.18f
        val outer = r * 0.42f
        val path = androidx.compose.ui.graphics.Path()
        for (i in 0 until 8) {
            val angle = (Math.PI / 4) * i - Math.PI / 2
            val radius = if (i % 2 == 0) outer else inner
            val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
            val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, c.accent.copy(alpha = 0.55f + breath * 0.45f))
    }
}

/* --------------------------- message items -------------------------- */

@Composable
fun MessageItem(msg: MessageEntity) {
    when (msg.role) {
        MessageRole.USER.name -> UserMessage(msg)
        MessageRole.ASSISTANT.name -> AssistantMessage(msg)
        MessageRole.EVENT.name -> EventRow(msg)
        else -> SystemNote(msg.text)
    }
}

@Composable
private fun UserMessage(msg: MessageEntity) {
    val c = osColors()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .background(c.surfaceAccent)
                .border(1.dp, c.accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            formatTime(msg.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            modifier = Modifier.padding(end = 2.dp)
        )
    }
}

@Composable
private fun AssistantMessage(msg: MessageEntity) {
    val c = osColors()
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentBadge(msg.agentName ?: "AgentOS")
            Text(
                formatTime(msg.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (copied) "Copied ✓" else "Copy",
                style = MaterialTheme.typography.labelSmall,
                color = if (copied) c.success else c.textMuted,
                modifier = Modifier
                    .clip(OsShapes.pill)
                    .clickable {
                        clipboard.setText(AnnotatedString(msg.text))
                        copied = true
                    }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        MarkdownText(msg.text, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StreamingAnswer(text: String) {
    val c = osColors()
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentBadge("AgentOS", live = true)
            BlinkingCursor()
        }
        Spacer(Modifier.height(6.dp))
        MarkdownText(text, Modifier.fillMaxWidth(), streaming = true)
    }
}

/** Soft-blinking block cursor shown while the model streams. */
@Composable
fun BlinkingCursor() {
    val c = osColors()
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Text(
        "▍",
        color = c.accent.copy(alpha = alpha),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.alpha(alpha)
    )
}

/** Timeline event: agent action, humanized. Details stay in Developer mode. */
@Composable
private fun EventRow(msg: MessageEntity) {
    val c = osColors()
    val isError = msg.severity == com.agentos.app.domain.model.EventSeverity.ERROR.name
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusDot(
            when {
                isError -> DotState.ERROR
                msg.eventType == EventType.APPROVAL_REQUIRED.name -> DotState.WAITING
                msg.eventType == EventType.AGENT_FINISHED.name || msg.eventType == EventType.TASK_STATUS.name -> DotState.DONE
                else -> DotState.IDLE
            },
            size = 5f
        )
        Column {
            msg.agentName?.let { agent ->
                Text(
                    agent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isError) c.error else c.textSecondary
                )
            }
            Text(
                msg.text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) c.error else c.textSecondary,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun SystemNote(text: String) {
    val c = osColors()
    Text(
        text,
        Modifier.fillMaxWidth(),
        style = MonoStyle,
        color = c.textMuted,
        maxLines = 4
    )
}

/* ------------------------- date & time utils ------------------------ */

@Composable
private fun DateSeparator(timestamp: Long) {
    val c = osColors()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            dayLabel(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            modifier = Modifier
                .clip(OsShapes.pill)
                .background(c.surfaceHigh)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

private fun isDifferentDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.DAY_OF_YEAR) != cb.get(java.util.Calendar.DAY_OF_YEAR) ||
        ca.get(java.util.Calendar.YEAR) != cb.get(java.util.Calendar.YEAR)
}

private fun dayLabel(ts: Long): String {
    val now = System.currentTimeMillis()
    val daysAgo = TimeUnit.MILLISECONDS.toDays(now - ts)
    return when {
        isToday(ts) -> "Today"
        daysAgo < 1L || isYesterday(ts) -> "Yesterday"
        daysAgo < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
    }
}

private fun isToday(ts: Long): Boolean = isDifferentDay(ts, System.currentTimeMillis()).not()

private fun isYesterday(ts: Long): Boolean {
    val cal = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    val c2 = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    return cal.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR) &&
        cal.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR)
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

/* --------------------------- approval gate --------------------------- */

@Composable
private fun ApprovalGate(approval: PendingApprovalUi, onAllow: () -> Unit, onDeny: () -> Unit) {
    val c = osColors()
    val haptic = rememberConfirmHaptics()
    var showDetails by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(OsShapes.card)
            .background(c.warningContainer)
            .border(1.dp, c.warning.copy(alpha = 0.4f), OsShapes.card)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = c.warning,
                modifier = Modifier.size(18.dp)
            )
            Text("Approval needed", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "AgentOS wants to:",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            toolHumanSummary(approval.tool, approval.args).ifBlank { toolDisplayName(approval.tool) },
            style = MaterialTheme.typography.titleSmall,
            color = c.warning,
            textAlign = TextAlign.Center
        )
        Text(
            toolDisplayName(approval.tool) + " · " + approval.tool,
            style = MonoStyle,
            color = c.textMuted
        )
        if (approval.reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                approval.reason,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
        }
        // raw details (what exactly will run) — opt-in, developer-friendly
        Text(
            if (showDetails) "Hide details" else "View details",
            style = MaterialTheme.typography.labelSmall,
            color = c.accent,
            modifier = Modifier
                .clip(OsShapes.pill)
                .clickable { showDetails = !showDetails }
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
        AnimatedVisibility(visible = showDetails, enter = androidx.compose.animation.expandVertically() + fadeIn(), exit = androidx.compose.animation.shrinkVertically() + fadeOut()) {
            Text(
                prettyJson(approval.args),
                style = MonoStyle,
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
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = onDeny,
                modifier = Modifier.clip(OsShapes.pill).background(c.surfaceInteractive)
            ) { Text("Deny", color = c.error) }
            TextButton(
                onClick = { haptic(); onAllow() },
                modifier = Modifier.clip(OsShapes.pill).background(c.accent)
            ) { Text("Allow", color = androidx.compose.ui.graphics.Color(0xFF0A0C12), fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun prettyJson(raw: String): String = runCatching {
    val el = kotlinx.serialization.json.Json.parseToJsonElement(raw)
    el.toString().replace(",", ",\n").replace("{", "{\n  ").replace("}", "\n}")
}.getOrDefault(raw)

/* ----------------------------- error UX ------------------------------ */

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    val c = osColors()
    val isConfigIssue = message.contains("key", true) || message.contains("provider", true) || message.contains("api", true)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(OsShapes.cardSmall)
            .background(c.errorContainer)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            androidx.compose.material3.Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = c.error,
                modifier = Modifier.size(16.dp)
            )
            Text("Task couldn't be completed", style = MaterialTheme.typography.titleSmall, color = c.error)
        }
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text("Dismiss", color = c.textSecondary) }
            if (isConfigIssue) {
                TextButton(onClick = onOpenSettings) { Text("Open Settings", color = c.accent) }
            }
        }
    }
}
