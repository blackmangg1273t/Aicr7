package com.agentos.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings as AndroidSettings
import com.agentos.app.core.logging.Logger
import com.agentos.app.data.provider.ProviderConfig
import com.agentos.app.data.provider.ProviderType
import com.agentos.app.data.settings.AgentsSettings
import com.agentos.app.data.settings.BrowserSettings
import com.agentos.app.data.settings.McpServerConfig
import com.agentos.app.data.settings.PrivacySettings
import com.agentos.app.data.settings.ShellSettings
import com.agentos.app.ui.components.DotState
import com.agentos.app.ui.components.StatusDot
import com.agentos.app.ui.theme.MonoStyle
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors

/* ------------------------------------------------------------------ */
/* Settings — organized sections, advanced details collapsed by default */
/* ------------------------------------------------------------------ */

private enum class SSection(val title: String, val hint: String, val openByDefault: Boolean) {
    AI("AI", "Providers, models, API keys, fallback", true),
    AGENTS("Agents", "Enable or disable each specialist", false),
    AUTOMATION("Automation", "Accessibility, overlay, background tasks, browser, Termux", false),
    CONNECTIONS("Connections", "MCP servers (JSON-RPC over HTTP)", false),
    PRIVACY("Privacy", "Memory, history, secrets", false),
    DEV("Developer", "Diagnostics, logs and tools", false)
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenDiagnostics: () -> Unit = {}) {
    val providers by viewModel.providers.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val browser by viewModel.browser.collectAsState()
    val shell by viewModel.shell.collectAsState()
    val privacy by viewModel.privacy.collectAsState()
    val servers by viewModel.mcpServers.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val c = osColors()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, color = c.textPrimary)
        Text(
            "Configure the AI, agents and automation.",
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary
        )
        Spacer(Modifier.height(14.dp))

        SettingsSection(SSection.AI) {
            providers.forEach { provider -> ProviderCard(provider, ui, viewModel) }
            AddProviderButton(viewModel)
            PrimarySelection(providers, ui.primaryProviderId, ui.fallbackProviderId, viewModel)
        }
        Spacer(Modifier.height(10.dp))
        SettingsSection(SSection.AGENTS) {
            AgentsSection(agents, viewModel)
        }
        Spacer(Modifier.height(10.dp))
        SettingsSection(SSection.AUTOMATION) {
            AccessibilityCard(ui, viewModel)
            Spacer(Modifier.height(10.dp))
            OverlayCard(ui, viewModel)
            Spacer(Modifier.height(10.dp))
            BackgroundTasksCard(ui, viewModel)
            Spacer(Modifier.height(10.dp))
            BrowserSection(browser, viewModel)
            Spacer(Modifier.height(10.dp))
            TermuxSection(shell, ui, viewModel)
        }
        Spacer(Modifier.height(10.dp))
        SettingsSection(SSection.CONNECTIONS) {
            servers.forEach { server -> McpCard(server, ui, viewModel) }
            McpAddSection(viewModel)
        }
        Spacer(Modifier.height(10.dp))
        SettingsSection(SSection.PRIVACY) {
            PrivacySection(privacy, ui, viewModel)
        }
        Spacer(Modifier.height(10.dp))
        SettingsSection(SSection.DEV) {
            DiagnosticsEntry(onOpenDiagnostics = onOpenDiagnostics)
            Spacer(Modifier.height(10.dp))
            LogsCard(logs)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun SettingsSection(section: SSection, content: @Composable () -> Unit) {
    val c = osColors()
    var open by rememberSaveable { mutableStateOf(section.openByDefault) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(OsShapes.cardSmall)
                .background(c.surfaceHigh)
                .border(1.dp, c.border, OsShapes.cardSmall)
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(section.hint, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            }
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Collapse" else "Expand",
                tint = c.textMuted,
                modifier = Modifier.rotate(if (open) 180f else 0f)
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

/* ------------------------------ providers ---------------------------- */

@Composable
private fun ProviderCard(provider: ProviderConfig, ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    var keyInput by remember(provider.id) { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val primary = ui.primaryProviderId == provider.id
    val fallback = ui.fallbackProviderId == provider.id

    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    provider.type.name.replace('_', ' ').lowercase() + " · " + provider.model,
                    style = MaterialTheme.typography.labelSmall, color = c.textSecondary
                )
                Text(
                    provider.baseUrl,
                    style = MonoStyle, color = c.textMuted, maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                AssistChip(
                    onClick = { viewModel.setPrimary(provider.id) },
                    label = { Text(if (primary) "★ primary" else "primary", style = MaterialTheme.typography.labelSmall) }
                )
                AssistChip(
                    onClick = { viewModel.setFallback(provider.id) },
                    label = { Text(if (fallback) "fallback ✓" else "fallback", style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            "API key: ${viewModel.keyPreview(provider.id).ifBlank { "not set" }}",
            style = MonoStyle, color = c.textSecondary
        )
        if (editing) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveKey(provider.id, keyInput); editing = false; keyInput = "" }) { Text("Save key") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            Row {
                TextButton(onClick = { editing = true }) { Text("Set key…", color = c.accent) }
                TextButton(onClick = { viewModel.deleteProvider(provider.id) }) {
                    Text("Delete", color = c.error, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AddProviderButton(viewModel: SettingsViewModel) {
    val c = osColors()
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(ProviderType.OPENAI_COMPATIBLE) }

    if (!adding) {
        OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add provider")
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("New provider", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == ProviderType.OPENAI_COMPATIBLE, onClick = { type = ProviderType.OPENAI_COMPATIBLE }, label = { Text("OpenAI-compatible") })
            FilterChip(selected = type == ProviderType.GEMINI, onClick = { type = ProviderType.GEMINI }, label = { Text("Gemini") })
        }
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            url, { url = it }, label = { Text("Base URL") },
            supportingText = { Text("e.g. https://api.openai.com/v1 · Gemini uses its own endpoint") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(model, { model = it }, label = { Text("Model id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            Button(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    viewModel.upsertProvider(
                        ProviderConfig(
                            id = "p_" + System.currentTimeMillis(),
                            type = type,
                            displayName = name,
                            baseUrl = url,
                            model = model.ifBlank { if (type == ProviderType.GEMINI) "gemini-2.0-flash" else "default" }
                        )
                    )
                    adding = false
                }
            ) { Text("Add") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { adding = false }) { Text("Cancel") }
        }
    }
}

@Composable
private fun PrimarySelection(providers: List<ProviderConfig>, primaryId: String?, fallbackId: String?, viewModel: SettingsViewModel) {
    val c = osColors()
    Text(
        "Active: ${providers.firstOrNull { it.id == primaryId }?.displayName ?: "first enabled"} · " +
            "Fallback: ${providers.firstOrNull { it.id == fallbackId }?.displayName ?: "none"}",
        style = MaterialTheme.typography.labelMedium,
        color = c.textSecondary
    )
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = osColors()
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Text(description, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/* ------------------------------- agents ------------------------------ */

@Composable
private fun AgentsSection(agents: AgentsSettings, viewModel: SettingsViewModel) {
    val c = osColors()
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        ToggleRow("Research Agent", "Web search and page reading", agents.researchEnabled) { viewModel.saveAgents(agents.copy(researchEnabled = it)) }
        ToggleRow("Browser Agent", "Headless browser automation", agents.browserEnabled) { viewModel.saveAgents(agents.copy(browserEnabled = it)) }
        ToggleRow("Android Agent", "Device UI automation (needs accessibility)", agents.androidAgentEnabled) { viewModel.saveAgents(agents.copy(androidAgentEnabled = it)) }
        ToggleRow("Terminal Agent", "Shell execution", agents.terminalEnabled) { viewModel.saveAgents(agents.copy(terminalEnabled = it)) }
        ToggleRow("Coding Agent", "File/code/git operations", agents.codingEnabled) { viewModel.saveAgents(agents.copy(codingEnabled = it)) }
    }
}

/* ----------------------------- automation ---------------------------- */

@Composable
private fun AccessibilityCard(ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusDot(
                if (ui.accessibilityEnabled) DotState.DONE else DotState.WAITING,
                size = 6f
            )
            Column {
                Text("Android control (Accessibility)", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    if (ui.accessibilityEnabled) "Enabled — the Android Agent can act on your device"
                    else "Disabled — Android Agent cannot tap, type or read the screen",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Enable in Settings") }
            OutlinedButton(onClick = { viewModel.refreshAccessibility() }) { Text("Refresh") }
        }
    }
}

@Composable
private fun OverlayCard(ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusDot(
                if (ui.overlayEnabled) DotState.DONE else DotState.WAITING,
                size = 6f
            )
            Column(Modifier.weight(1f)) {
                Text("Floating overlay", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    if (ui.overlayEnabled) "Enabled — a floating progress card can appear over other apps while tasks run"
                    else "Disabled — the foreground notification still works, but no floating card appears over other apps",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !ui.overlayEnabled,
                onClick = {
                    viewModel.overlayPermissionIntent(context)?.let { intent ->
                        runCatching { context.startActivity(intent) }
                    }
                }
            ) { Text("Enable overlay") }
            OutlinedButton(onClick = { viewModel.refreshOverlay(context) }) { Text("Refresh") }
        }
    }
}

@Composable
private fun BackgroundTasksCard(ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatusDot(
                if (ui.foregroundServiceRunning) DotState.RUNNING else DotState.IDLE,
                size = 6f
            )
            Column(Modifier.weight(1f)) {
                Text("Background tasks", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    "Foreground service keeps long agent tasks alive when the app is backgrounded.",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.testForegroundService(context) }) { Text("Test") }
            OutlinedButton(onClick = { viewModel.refreshForegroundService() }) { Text("Refresh") }
        }
    }
}

@Composable
private fun BrowserSection(browser: BrowserSettings, viewModel: SettingsViewModel) {
    val c = osColors()
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        ToggleRow("Browser tools", "browser open / read / click / type", browser.enabled) { viewModel.saveBrowser(browser.copy(enabled = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Page timeout: ${browser.pageTimeoutMs / 1000}s", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Slider(
                value = browser.pageTimeoutMs / 1000f,
                onValueChange = { viewModel.saveBrowser(browser.copy(pageTimeoutMs = it.toLong() * 1000L)) },
                valueRange = 5f..60f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TermuxSection(shell: ShellSettings, ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        ToggleRow("Prefer Termux transport", "Use Termux for shell commands when installed", shell.preferTermux) { viewModel.saveShell(shell.copy(preferTermux = it)) }
        Button(onClick = { viewModel.testTermux() }) { Text("Test Termux connection") }
        ui.termuxStatus?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/* ---------------------------- connections ---------------------------- */

@Composable
private fun McpCard(server: McpServerConfig, ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        Text(server.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Text(server.url, style = MonoStyle, color = c.textMuted, maxLines = 1)
        ui.mcpStatus[server.id]?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(9.dp))
        Row {
            Button(onClick = { viewModel.connectMcp(server.id) }) { Text("Connect") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { viewModel.deleteMcpServer(server.id) }) {
                Text("Remove", color = c.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun McpAddSection(viewModel: SettingsViewModel) {
    val c = osColors()
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var auth by rememberSaveable { mutableStateOf("") }

    if (!adding) {
        OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add MCP server")
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text("New MCP server", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            url, { url = it }, label = { Text("Endpoint URL") },
            supportingText = { Text("MCP Streamable HTTP endpoint, e.g. http://192.168.1.10:3000/mcp") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(auth, { auth = it }, label = { Text("Auth header value (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            Button(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = { viewModel.addMcpServer(name, url, auth); adding = false }
            ) { Text("Add") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { adding = false }) { Text("Cancel") }
        }
    }
}

/* ------------------------------ privacy ------------------------------ */

@Composable
private fun PrivacySection(privacy: PrivacySettings, ui: SettingsUiState, viewModel: SettingsViewModel) {
    val c = osColors()
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(15.dp)
    ) {
        ToggleRow("Save conversations", "Persist chat history locally", privacy.saveConversations) { viewModel.savePrivacy(privacy.copy(saveConversations = it)) }
        ToggleRow("Save task history", "Persist tasks and steps", privacy.saveTasks) { viewModel.savePrivacy(privacy.copy(saveTasks = it)) }
        ToggleRow("Auto memory", "Let agents store useful facts", privacy.autoMemoryEnabled) { viewModel.savePrivacy(privacy.copy(autoMemoryEnabled = it)) }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.wipeConversations() }) { Text("Wipe chats") }
            OutlinedButton(onClick = { viewModel.wipeTasks() }) { Text("Wipe tasks") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { viewModel.wipeMemories() }) { Text("Wipe memory") }
            Button(
                onClick = { viewModel.wipeSecrets() },
                colors = ButtonDefaults.buttonColors(containerColor = c.error)
            ) { Text("Delete API keys") }
        }
        ui.wipeMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
    }
}

/* ------------------------------ developer ---------------------------- */

@Composable
private fun DiagnosticsEntry(onOpenDiagnostics: () -> Unit) {
    val c = osColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(OsShapes.card)
            .background(c.surfaceHigh)
            .border(1.dp, c.border, OsShapes.card)
            .clickable(onClick = onOpenDiagnostics)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(c.accentViolet.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.BugReport,
                contentDescription = null,
                tint = c.accentViolet,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(
                "Live status of services, providers and the task engine",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = c.textMuted
        )
    }
}

@Composable
private fun LogsCard(logs: List<Logger.Entry>) {
    val c = osColors()
    Column(
        Modifier.fillMaxWidth().clip(OsShapes.card).background(c.surfaceHigh).border(1.dp, c.border, OsShapes.card).padding(12.dp)
    ) {
        Text("Event logs", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Column {
                if (logs.isEmpty()) Text("No log entries yet.", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                logs.takeLast(80).forEach { entry ->
                    Text(
                        entry.format(),
                        style = MonoStyle,
                        color = when (entry.level) {
                            Logger.Level.ERROR -> c.error
                            Logger.Level.WARNING -> c.warning
                            else -> c.textSecondary
                        }
                    )
                }
            }
        }
    }
}
