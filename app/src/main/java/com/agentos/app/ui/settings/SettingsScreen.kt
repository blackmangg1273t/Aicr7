package com.agentos.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.Intent
import android.provider.Settings as AndroidSettings
import com.agentos.app.data.provider.ProviderConfig
import com.agentos.app.data.provider.ProviderType
import com.agentos.app.core.logging.Logger
import com.agentos.app.data.settings.AgentsSettings
import com.agentos.app.data.settings.McpServerConfig
import com.agentos.app.ui.theme.AgentOsTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val providers by viewModel.providers.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val browser by viewModel.browser.collectAsState()
    val shell by viewModel.shell.collectAsState()
    val privacy by viewModel.privacy.collectAsState()
    val servers by viewModel.mcpServers.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val logs by viewModel.logs.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle("AI Providers") }
        items(providers, key = { it.id }) { provider ->
            ProviderCard(provider, ui, viewModel)
        }
        item {
            AddProviderButton(viewModel)
            PrimarySelection(providers, ui.primaryProviderId, ui.fallbackProviderId, viewModel)
        }

        item { SectionTitle("Agents") }
        item { AgentsSection(agents, viewModel) }

        item { SectionTitle("Browser") }
        item { BrowserSection(browser, viewModel) }

        item { SectionTitle("Android Automation") }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (ui.accessibilityEnabled) "Accessibility: ENABLED" else "Accessibility: DISABLED",
                        fontWeight = FontWeight.Bold,
                        color = if (ui.accessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The Android Agent needs this permission to tap, type and read the screen on your behalf.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    val context = LocalContext.current
                    Button(onClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open Accessibility settings") }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { viewModel.refreshAccessibility() }) { Text("Refresh status") }
                }
            }
        }

        item { SectionTitle("Termux / Shell") }
        item { TermuxSection(shell, ui, viewModel) }

        item { SectionTitle("MCP Servers") }
        items(servers, key = { it.id }) { server ->
            McpCard(server, ui, viewModel)
        }
        item { McpAddSection(viewModel) }

        item { SectionTitle("Privacy & Data") }
        item { PrivacySection(privacy, ui, viewModel) }

        item { SectionTitle("Logs (secrets auto-redacted)") }
        item {
            Card(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                SelectionContainer {
                    Column(Modifier.padding(10.dp)) {
                        if (logs.isEmpty()) Text("No log entries yet.", style = MaterialTheme.typography.labelSmall)
                        logs.takeLast(80).forEach { entry ->
                            Text(
                                entry.format(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = when (entry.level) {
                                    Logger.Level.ERROR -> MaterialTheme.colorScheme.error
                                    Logger.Level.WARNING -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun ProviderCard(provider: ProviderConfig, ui: SettingsUiState, viewModel: SettingsViewModel) {
    var keyInput by remember(provider.id) { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val primary = ui.primaryProviderId == provider.id
    val fallback = ui.fallbackProviderId == provider.id

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(provider.displayName, fontWeight = FontWeight.Bold)
                    Text("${provider.type.name.replace('_', ' ').lowercase()} · ${provider.model}", style = MaterialTheme.typography.labelSmall)
                    Text(provider.baseUrl, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    AssistChip(onClick = { viewModel.setPrimary(provider.id) }, label = { Text(if (primary) "★ primary" else "primary") })
                    AssistChip(onClick = { viewModel.setFallback(provider.id) }, label = { Text(if (fallback) "fallback ✓" else "fallback") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("API key: ${viewModel.keyPreview(provider.id).ifBlank { "not set" }}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            if (editing) {
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
                    TextButton(onClick = { editing = true }) { Text("Set key…") }
                    TextButton(onClick = { viewModel.deleteProvider(provider.id) }) {
                        Icon(Icons.Default.Delete, "delete", Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProviderButton(viewModel: SettingsViewModel) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ProviderType.OPENAI_COMPATIBLE) }

    if (!adding) {
        OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add provider")
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New provider", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == ProviderType.OPENAI_COMPATIBLE, onClick = { type = ProviderType.OPENAI_COMPATIBLE }, label = { Text("OpenAI-compatible") })
                FilterChip(selected = type == ProviderType.GEMINI, onClick = { type = ProviderType.GEMINI }, label = { Text("Gemini") })
            }
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                url, { url = it }, label = { Text("Base URL") },
                supportingText = { Text("e.g. https://open.bigmodel.cn/api/paas/v4 or http://127.0.0.1:11434/v1 (Ollama)") },
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
}

@Composable
private fun PrimarySelection(
    providers: List<ProviderConfig>,
    primaryId: String?,
    fallbackId: String?,
    viewModel: SettingsViewModel
) {
    Text(
        "Active: ${providers.firstOrNull { it.id == primaryId }?.displayName ?: "first enabled"} · " +
            "Fallback: ${providers.firstOrNull { it.id == fallbackId }?.displayName ?: "none"}",
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AgentsSection(agents: AgentsSettings, viewModel: SettingsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ToggleRow("Research Agent", "Web search and page reading", agents.researchEnabled) { viewModel.saveAgents(agents.copy(researchEnabled = it)) }
            ToggleRow("Browser Agent", "Headless browser automation", agents.browserEnabled) { viewModel.saveAgents(agents.copy(browserEnabled = it)) }
            ToggleRow("Android Agent", "Device UI automation (needs accessibility)", agents.androidAgentEnabled) { viewModel.saveAgents(agents.copy(androidAgentEnabled = it)) }
            ToggleRow("Terminal Agent", "Shell execution", agents.terminalEnabled) { viewModel.saveAgents(agents.copy(terminalEnabled = it)) }
            ToggleRow("Coding Agent", "File/code/git operations", agents.codingEnabled) { viewModel.saveAgents(agents.copy(codingEnabled = it)) }
        }
    }
}

@Composable
private fun BrowserSection(browser: com.agentos.app.data.settings.BrowserSettings, viewModel: SettingsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ToggleRow("Browser tools enabled", "browser_open / read / click / type", browser.enabled) { viewModel.saveBrowser(browser.copy(enabled = it)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page timeout: ${browser.pageTimeoutMs / 1000}s", Modifier.weight(1f))
                Slider(
                    value = (browser.pageTimeoutMs / 1000f),
                    onValueChange = { viewModel.saveBrowser(browser.copy(pageTimeoutMs = (it.toLong()) * 1000L)) },
                    valueRange = 5f..60f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TermuxSection(shell: com.agentos.app.data.settings.ShellSettings, ui: SettingsUiState, viewModel: SettingsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ToggleRow("Prefer Termux transport", "Use Termux for shell commands when installed", shell.preferTermux) { viewModel.saveShell(shell.copy(preferTermux = it)) }
            Button(onClick = { viewModel.testTermux() }) { Text("Test Termux connection") }
            ui.termuxStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun McpCard(server: McpServerConfig, ui: SettingsUiState, viewModel: SettingsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(server.name, fontWeight = FontWeight.Bold)
            Text(server.url, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            ui.mcpStatus[server.id]?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = { viewModel.connectMcp(server.id) }) { Text("Connect") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.deleteMcpServer(server.id) }) {
                    Icon(Icons.Default.Delete, "delete", Modifier.size(16.dp)); Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun McpAddSection(viewModel: SettingsViewModel) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf("") }

    if (!adding) {
        OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add MCP server (JSON-RPC over HTTP)")
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New MCP server", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                url, { url = it }, label = { Text("Endpoint URL") },
                supportingText = { Text("MCP Streamable HTTP endpoint, e.g. http://192.168.1.10:3000/mcp") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(auth, { auth = it }, label = { Text("Auth header value (optional, 'Bearer …')") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
}

@Composable
private fun PrivacySection(privacy: com.agentos.app.data.settings.PrivacySettings, ui: SettingsUiState, viewModel: SettingsViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ToggleRow("Save conversations", "Persist chat history locally", privacy.saveConversations) { viewModel.savePrivacy(privacy.copy(saveConversations = it)) }
            ToggleRow("Save task history", "Persist tasks and steps", privacy.saveTasks) { viewModel.savePrivacy(privacy.copy(saveTasks = it)) }
            ToggleRow("Auto memory", "Let agents store useful facts", privacy.autoMemoryEnabled) { viewModel.savePrivacy(privacy.copy(autoMemoryEnabled = it)) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.wipeConversations() }) { Text("Wipe chats") }
                OutlinedButton(onClick = { viewModel.wipeTasks() }) { Text("Wipe tasks") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { viewModel.wipeMemories() }) { Text("Wipe memory") }
                Button(onClick = { viewModel.wipeSecrets() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete all API keys")
                }
            }
            ui.wipeMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
