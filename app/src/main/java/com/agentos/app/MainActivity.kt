package com.agentos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentos.app.service.ScreenCaptureCoordinator
import com.agentos.app.ui.chat.ChatScreen
import com.agentos.app.ui.chat.ChatViewModel
import com.agentos.app.ui.settings.SettingsScreen
import com.agentos.app.ui.settings.SettingsViewModel
import com.agentos.app.ui.tasks.TasksScreen
import com.agentos.app.ui.tasks.TasksViewModel
import com.agentos.app.ui.terminal.TerminalScreen
import com.agentos.app.ui.terminal.TerminalViewModel
import com.agentos.app.ui.theme.AgentOsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentOsTheme {
                MainApp()
            }
        }
    }
}

enum class AppTab(val label: String) { CHAT("Chat"), TASKS("Tasks"), TERMINAL("Terminal"), SETTINGS("Settings") }

@Composable
fun MainApp() {
    var tab by remember { mutableStateOf(AppTab.CHAT) }

    val chatViewModel: ChatViewModel = koinViewModel()
    val tasksViewModel: TasksViewModel = koinViewModel()
    val terminalViewModel: TerminalViewModel = koinViewModel()
    val settingsViewModel: SettingsViewModel = koinViewModel()

    val messages by chatViewModel.messages.collectAsState()
    val chatUi by chatViewModel.ui.collectAsState()

    // MediaProjection consent bridge: engine requests → system dialog → coordinator
    val consentRequested by ScreenCaptureCoordinator.uiRequest.collectAsState()
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        ScreenCaptureCoordinator.onConsentResult(result.resultCode, result.data)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(consentRequested) {
        if (consentRequested) {
            val mpm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            consentLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            when (t) {
                                AppTab.CHAT -> Icon(Icons.Default.Chat, t.label)
                                AppTab.TASKS -> Icon(Icons.Default.Task, t.label)
                                AppTab.TERMINAL -> Icon(Icons.Default.Terminal, t.label)
                                AppTab.SETTINGS -> Icon(Icons.Default.Settings, t.label)
                            }
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        // Apply the Scaffold insets to ALL tab screens. Without this, each screen
        // renders full-bleed UNDER the bottom NavigationBar — e.g. the chat input
        // bar was completely hidden behind it, so the screen looked empty and the
        // user could not type or send anything.
        Box(Modifier.padding(padding)) {
            when (tab) {
                AppTab.CHAT -> ChatScreen(chatViewModel, messages, chatUi)
                AppTab.TASKS -> {
                    val selectedTask by tasksViewModel.selectedTask.collectAsState()
                    val selectedSteps by tasksViewModel.selectedSteps.collectAsState()
                    val tasks by tasksViewModel.tasks.collectAsState()
                    TasksScreen(tasks, selectedTask, selectedSteps) { id -> tasksViewModel.select(id) }
                }
                AppTab.TERMINAL -> {
                    val termUi by terminalViewModel.ui.collectAsState()
                    TerminalScreen(termUi, onRun = { terminalViewModel.run(it) }, onTestTermux = { terminalViewModel.testTermux() })
                }
                AppTab.SETTINGS -> SettingsScreen(settingsViewModel)
            }
        }
    }
}
