package com.agentos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentos.app.service.ScreenCaptureCoordinator
import com.agentos.app.ui.agents.AgentsScreen
import com.agentos.app.ui.agents.AgentsViewModel
import com.agentos.app.ui.chat.ChatScreen
import com.agentos.app.ui.chat.ChatViewModel
import com.agentos.app.ui.settings.SettingsScreen
import com.agentos.app.ui.settings.SettingsViewModel
import com.agentos.app.ui.tasks.TasksScreen
import com.agentos.app.ui.tasks.TasksViewModel
import com.agentos.app.ui.terminal.TerminalScreen
import com.agentos.app.ui.terminal.TerminalViewModel
import com.agentos.app.ui.theme.AgentOsTheme
import com.agentos.app.ui.theme.OsColors
import com.agentos.app.ui.theme.OsMotion
import com.agentos.app.ui.theme.OsShapes
import com.agentos.app.ui.theme.osColors
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentOsTheme {
                AgentOsRoot()
            }
        }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Default.Chat),
    TASKS("Tasks", Icons.Default.Task),
    AGENTS("Agents", Icons.Default.Assistant),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun AgentOsRoot() {
    val c = osColors()
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(850) // short branded splash, then straight to the workspace
        booted = true
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(tween(OsMotion.Normal))
        ) { SplashScreen() }

        AnimatedVisibility(
            visible = booted,
            enter = fadeIn(tween(OsMotion.Emphasis))
        ) { MainApp() }
    }
}

/** Branded splash: ✦ mark, name, tagline. */
@Composable
private fun SplashScreen() {
    val c = osColors()
    val appear by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(OsMotion.Emphasis),
        label = "splash"
    )
    Column(
        Modifier.fillMaxSize().alpha(appear),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        com.agentos.app.ui.chat.BrandMark(size = 30f)
        Spacer(Modifier.height(16.dp))
        Text("AgentOS", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text("Intelligent automation", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@Composable
fun MainApp() {
    val c = osColors()
    var tab by remember { mutableStateOf(AppTab.CHAT) }
    var terminalOpen by remember { mutableStateOf(false) }

    val chatViewModel: ChatViewModel = koinViewModel()
    val tasksViewModel: TasksViewModel = koinViewModel()
    val terminalViewModel: TerminalViewModel = koinViewModel()
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val agentsViewModel: AgentsViewModel = koinViewModel()

    val messages by chatViewModel.messages.collectAsState()
    val chatUi by chatViewModel.ui.collectAsState()

    // MediaProjection consent bridge: engine requests → system dialog → coordinator
    val consentRequested by ScreenCaptureCoordinator.uiRequest.collectAsState()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ScreenCaptureCoordinator.onConsentResult(result.resultCode, result.data)
    }
    val context = LocalContext.current
    LaunchedEffect(consentRequested) {
        if (consentRequested) {
            val mpm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            consentLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            // screen content (weight 1f) + custom bottom navigation
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    AppTab.CHAT -> ChatScreen(
                        chatViewModel, messages, chatUi,
                        onOpenSettings = { tab = AppTab.SETTINGS }
                    )
                    AppTab.TASKS -> {
                        val selectedTask by tasksViewModel.selectedTask.collectAsState()
                        val selectedSteps by tasksViewModel.selectedSteps.collectAsState()
                        val tasks by tasksViewModel.tasks.collectAsState()
                        TasksScreen(tasks, selectedTask, selectedSteps) { id -> tasksViewModel.select(id) }
                    }
                    AppTab.AGENTS -> AgentsScreen(
                        agentsViewModel,
                        onOpenTerminal = { terminalOpen = true },
                        onOpenSettings = { tab = AppTab.SETTINGS }
                    )
                    AppTab.SETTINGS -> SettingsScreen(settingsViewModel)
                }
            }
            OsNavBar(tab, onTab = { tab = it })
        }

        // full-screen terminal overlay (reached from Agents tab)
        AnimatedVisibility(
            visible = terminalOpen,
            enter = fadeIn(tween(OsMotion.Normal)) + slideInVertically(initialOffsetY = { it / 8 }, animationSpec = tween(OsMotion.Normal)),
            exit = fadeOut(tween(OsMotion.Fast))
        ) {
            BackHandler(enabled = terminalOpen) { terminalOpen = false }
            Surface(Modifier.fillMaxSize().background(c.bg), color = c.bg) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.accent,
                            modifier = Modifier
                                .clip(OsShapes.pill)
                                .clickable { terminalOpen = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    TerminalScreen(
                        terminalViewModel.ui.collectAsState().value,
                        onRun = { terminalViewModel.run(it) },
                        onTestTermux = { terminalViewModel.testTermux() }
                    )
                }
            }
        }
    }
}

/** Premium bottom navigation: pill selection, no default MaterialBar look. */
@Composable
private fun OsNavBar(tab: AppTab, onTab: (AppTab) -> Unit) {
    val c = osColors()
    Surface(color = c.surface, tonalElevation = 0.dp) {
        Column(Modifier.navigationBarsPadding().background(c.surface)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.border)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppTab.entries.forEach { t ->
                    val selected = tab == t
                    Row(
                        Modifier
                            .clip(OsShapes.pill)
                            .background(if (selected) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTab(t) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            t.icon,
                            contentDescription = t.label,
                            tint = if (selected) c.accent else c.textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                        if (selected) {
                            Text(
                                t.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = c.accent
                            )
                        }
                    }
                }
            }
        }
    }
}
