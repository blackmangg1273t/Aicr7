package com.agentos.app.ui.agents

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentos.app.ui.components.DotState
import com.agentos.app.ui.components.StatusDot
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/**
 * Agents tab — what AgentOS can do, its agents, and a real capability
 * checklist (onboarding). Terminal is reachable from here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgentsScreen(
    viewModel: AgentsViewModel,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val c = osColors()
    val agents by viewModel.agents.collectAsState()
    val setup by viewModel.setup.collectAsState()
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Agents", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
                Text(
                    "AgentOS routes your request to the right specialist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
        }

        // capability checklist (real state)
        item {
            Column(
                Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(16.dp)
            ) {
                Text("Setup", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Make AgentOS more capable",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(10.dp))
                SetupRow("AI provider", if (setup.aiProvider) "API key configured" else "Add an API key in Settings → AI", setup.aiProvider) { onOpenSettings() }
                SetupRow("Internet", if (setup.internet) "Connected" else "Offline", setup.internet) { }
                SetupRow(
                    "Android control",
                    if (setup.androidControl) "Accessibility enabled" else "Enable Accessibility access",
                    setup.androidControl
                ) {
                    runCatching {
                        context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                SetupRow("Termux shell", if (setup.termux) "Installed — full Linux userland" else "Optional — install Termux for full shell", setup.termux) { }
                SetupRow("Browser tools", "Built-in headless browser", setup.browser) { }
            }
        }

        // terminal entry
        item {
            Row(
                Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card)
                    .clickable(onClick = onOpenTerminal).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(c.success.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) { Text("›_", style = com.agentos.app.ui.theme.MonoStyle, color = c.success) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Terminal", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Text("Direct shell access (Termux or sandbox)", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Open", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }

        // agent cards
        item { Text("Specialists", style = MaterialTheme.typography.titleMedium, color = c.textPrimary) }
        items(agents, key = { it.name }) { agent ->
            Column(
                Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) { Text("✦", color = c.accent, style = MaterialTheme.typography.titleMedium) }
                    Column {
                        Text(agent.displayName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(
                            agent.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(9.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    agent.capabilities.take(4).forEach { cap ->
                        Text(
                            cap.replace('_', ' '),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary,
                            modifier = Modifier
                                .clip(OsShapes.pill)
                                .background(c.surfaceInteractive)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, subtitle: String, done: Boolean, onClick: () -> Unit) {
    val c = osColors()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(if (done) DotState.DONE else DotState.WAITING, size = 6f)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!done) {
            Text(
                "Set up",
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
                modifier = Modifier
                    .clip(OsShapes.pill)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

