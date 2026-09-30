package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.AgentSubtask
import com.example.agent.AgentTask
import com.example.agent.TaskStatus
import com.example.mcp.McpToolDefinition
import com.example.mcp.ToolRiskLevel
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.model.ModelProviderType
import com.example.viewmodel.AgentUiState
import com.example.viewmodel.AgentViewModel
import com.example.viewmodel.NavigationTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentAppScreen(viewModel: AgentViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "AgentOS Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AgentOS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uiState.isRunning) "Executing multi-agent workflow..." else "All 7 Tool Engines Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            when (tab) {
                                NavigationTab.CHAT -> Icon(Icons.Default.Chat, contentDescription = tab.title)
                                NavigationTab.TASKS -> Icon(Icons.Default.FormatListNumbered, contentDescription = tab.title)
                                NavigationTab.TERMINAL -> Icon(Icons.Default.Terminal, contentDescription = tab.title)
                                NavigationTab.TOOLS -> Icon(Icons.Default.Build, contentDescription = tab.title)
                                NavigationTab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = tab.title)
                            }
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState.currentTab) {
                NavigationTab.CHAT -> ChatView(uiState, onSendGoal = { viewModel.submitGoal(it) })
                NavigationTab.TASKS -> TasksView(uiState)
                NavigationTab.TERMINAL -> TerminalView(uiState, onExecute = { viewModel.runTerminalCommand(it) })
                NavigationTab.TOOLS -> ToolsView(uiState)
                NavigationTab.SETTINGS -> SettingsView(
                    uiState = uiState,
                    onUpdateApiKey = { viewModel.updateApiKey(it) },
                    onSelectProvider = { viewModel.updateModelProvider(it) }
                )
            }

            // User Approval Modal Dialog
            uiState.pendingApproval?.let { pending ->
                AlertDialog(
                    onDismissRequest = { viewModel.resolveApproval(false) },
                    icon = { Icon(Icons.Default.Warning, contentDescription = "Security Alert", tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Human-in-the-Loop Approval") },
                    text = {
                        Column {
                            Text("The agent requested execution of high-risk tool:")
                            Text(
                                text = pending.toolName,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text("Arguments:")
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text(
                                    text = pending.argumentsJson,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.resolveApproval(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Approve")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { viewModel.resolveApproval(false) }) {
                            Text("Deny")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ChatView(uiState: AgentUiState, onSendGoal: (String) -> Unit) {
    var inputGoal by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(uiState.messages) { message ->
                MessageBubble(message)
            }
        }

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputGoal,
                    onValueChange = { inputGoal = it },
                    placeholder = { Text("What do you want me to do?") },
                    modifier = Modifier.weight(1f).testTag("goal_input_field"),
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputGoal.isNotBlank()) {
                            onSendGoal(inputGoal)
                            inputGoal = ""
                        }
                    },
                    enabled = !uiState.isRunning && inputGoal.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                        .testTag("send_goal_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val isSystem = message.sender == MessageSender.SYSTEM

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isSystem -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimary
                        isSystem -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun TasksView(uiState: AgentUiState) {
    val task = uiState.currentTask
    if (task == null && uiState.taskHistory.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No active tasks. Submit a goal to see execution steps.", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (task != null) {
            item {
                Text("Active Task Execution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(task.prompt, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status: ", style = MaterialTheme.typography.labelMedium)
                            Text(
                                task.status.name,
                                fontWeight = FontWeight.Bold,
                                color = when (task.status) {
                                    TaskStatus.VERIFIED, TaskStatus.COMPLETED -> Color(0xFF2E7D32)
                                    TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            items(task.planSteps) { subtask ->
                SubtaskItemCard(subtask)
            }
        }
    }
}

@Composable
fun SubtaskItemCard(subtask: AgentSubtask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = subtask.title,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Badge(
                    containerColor = when (subtask.status) {
                        TaskStatus.COMPLETED, TaskStatus.VERIFIED -> Color(0xFFE8F5E9)
                        TaskStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                        TaskStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        subtask.status.name,
                        color = when (subtask.status) {
                            TaskStatus.COMPLETED, TaskStatus.VERIFIED -> Color(0xFF1B5E20)
                            TaskStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Agent: ${subtask.targetAgent}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            if (subtask.toolName != null) {
                Text("Tool: ${subtask.toolName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }

            subtask.result?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it.take(200),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            subtask.verificationResult?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text("✓ $it", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun TerminalView(uiState: AgentUiState, onExecute: (String) -> Unit) {
    var commandInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            SelectionContainer {
                Text(
                    text = uiState.terminalOutput,
                    color = Color(0xFF81C784),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }
        }

        Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$ ", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("sh command...", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f).testTag("terminal_input_field"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    if (commandInput.isNotBlank()) {
                        onExecute(commandInput)
                        commandInput = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Run", tint = Color(0xFF81C784))
                }
            }
        }
    }
}

@Composable
fun ToolsView(uiState: AgentUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Registered MCP Tools (${uiState.registeredTools.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Standard Model Context Protocol JSON-RPC tool endpoints.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }

        items(uiState.registeredTools) { tool ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tool.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Badge(
                            containerColor = when (tool.riskLevel) {
                                ToolRiskLevel.READ_ONLY -> Color(0xFFE8F5E9)
                                ToolRiskLevel.MODERATE -> Color(0xFFFFF3E0)
                                ToolRiskLevel.HIGH_RISK -> Color(0xFFFFEBEE)
                            }
                        ) {
                            Text(
                                tool.riskLevel.name,
                                color = when (tool.riskLevel) {
                                    ToolRiskLevel.READ_ONLY -> Color(0xFF2E7D32)
                                    ToolRiskLevel.MODERATE -> Color(0xFFE65100)
                                    ToolRiskLevel.HIGH_RISK -> Color(0xFFC62828)
                                },
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(tool.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Text("Category: ${tool.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Long-Term SQLite/Room Memory Records", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(uiState.memoryRecords) { record ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(record, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
fun SettingsView(
    uiState: AgentUiState,
    onUpdateApiKey: (String) -> Unit,
    onSelectProvider: (ModelProviderType) -> Unit
) {
    var apiKeyText by remember(uiState.modelConfig.apiKey) { mutableStateOf(uiState.modelConfig.apiKey) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AgentOS Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AI Model Provider", fontWeight = FontWeight.Bold)
                ModelProviderType.values().forEach { provider ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.modelConfig.provider == provider,
                            onClick = { onSelectProvider(provider) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(provider.displayName, fontWeight = FontWeight.Medium)
                            Text(provider.defaultEndpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API Key", fontWeight = FontWeight.Bold)
                Text(
                    "Optional for Gemini if configured via environment; required for OpenAI / Qwen / GLM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = {
                        apiKeyText = it
                        onUpdateApiKey(it)
                    },
                    placeholder = { Text("Paste API Key...") },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    singleLine = true
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Architecture & Runtime Information", fontWeight = FontWeight.Bold)
                Text("• Operating Mode: No-Root Android Native Sandbox", style = MaterialTheme.typography.bodySmall)
                Text("• OpenCode Daemon: http://127.0.0.1:4096 (Bridge Enabled)", style = MaterialTheme.typography.bodySmall)
                Text("• Git Engine: Native JGit 6.9+ / Linux POSIX", style = MaterialTheme.typography.bodySmall)
                Text("• Browser Agent: Headless Android WebView + DOM Parser", style = MaterialTheme.typography.bodySmall)
                Text("• Target ABI: ARM64-v8a", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
