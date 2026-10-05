package com.agentos.app.di

import android.content.Context
import com.agentos.app.core.network.NetworkMonitor
import com.agentos.app.core.security.SecureStore
import com.agentos.app.core.security.SecretStore
import com.agentos.app.data.accessibility.AndroidAutomationTools
import com.agentos.app.data.browser.BrowserEngine
import com.agentos.app.data.db.AppDatabase
import com.agentos.app.data.mcp.McpManager
import com.agentos.app.data.provider.GeminiProvider
import com.agentos.app.data.provider.OpenAiCompatibleProvider
import com.agentos.app.data.provider.ProviderExecutor
import com.agentos.app.data.repo.ChatRepository
import com.agentos.app.data.repo.MemoryRepository
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.data.termux.TermuxBridge
import com.agentos.app.data.tools.BrowserTools
import com.agentos.app.data.tools.DeviceInfoTool
import com.agentos.app.data.tools.LaunchAppTool
import com.agentos.app.data.tools.MemoryTools
import com.agentos.app.data.tools.OpenUrlTool
import com.agentos.app.data.tools.ScreenshotTool
import com.agentos.app.data.tools.ShellTools
import com.agentos.app.data.tools.WebFetchTool
import com.agentos.app.data.tools.WebSearchTool
import com.agentos.app.ui.agents.AgentsViewModel
import com.agentos.app.ui.chat.ChatViewModel
import com.agentos.app.ui.diagnostics.DiagnosticsViewModel
import com.agentos.app.ui.settings.SettingsViewModel
import com.agentos.app.ui.tasks.TasksViewModel
import com.agentos.app.ui.terminal.TerminalViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import com.agentos.app.domain.agent.AgentRegistry
import com.agentos.app.domain.agent.AndroidAgent
import com.agentos.app.domain.agent.BrowserAgent
import com.agentos.app.domain.agent.CodingAgent
import com.agentos.app.domain.agent.MainAgent
import com.agentos.app.domain.agent.ResearchAgent
import com.agentos.app.domain.agent.TerminalAgent
import com.agentos.app.domain.engine.TaskEngine
import com.agentos.app.domain.tools.ToolContext
import com.agentos.app.domain.tools.ToolRegistry
import com.agentos.app.service.ScreenCaptureCoordinator
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    // NOTE: do NOT declare single<Context> { androidContext() } here.
    // In Koin 4.0, startKoin { androidContext(app) } already auto-registers the
    // application Context, and Scope.androidContext() resolves get<Context>().
    // Re-declaring it shadows the built-in definition and recurses into itself
    // (StackOverflowError at first resolution = crash on every app launch).
    single<kotlinx.coroutines.CoroutineScope> {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    }
    single { SecureStore(androidContext()) }
    // Engine depends on the SecretStore interface; bind it explicitly.
    single<SecretStore> { get<SecureStore>() }
    single { NetworkMonitor(androidContext()) }
    // TaskEngine depends on the ConnectivityChecker interface; NetworkMonitor implements it.
    single<com.agentos.app.core.network.ConnectivityChecker> { get<NetworkMonitor>() }
    single { SettingsRepository(androidContext()) }
    single { OpenAiCompatibleProvider() }
    single { GeminiProvider() }
    single<com.agentos.app.data.provider.AiExecutor> {
        ProviderExecutor(
            openAiCompat = get<OpenAiCompatibleProvider>(),
            gemini = get<GeminiProvider>()
        )
    }
    single { AppDatabase.build(androidContext()) }

    single {
        val settings = get<SettingsRepository>()
        ChatRepository(get(), saveConversations = { settings.privacyFlow.first().saveConversations })
    }
    single {
        val settings = get<SettingsRepository>()
        TaskRepository(get(), saveTasks = { settings.privacyFlow.first().saveTasks })
    }
    single { MemoryRepository(get()) }

    single { BrowserEngine(androidContext()) }
    single { TermuxBridge(androidContext()) }
    single { McpManager(get()) }
    single { AndroidAutomationTools(androidContext()) }

    // The ONE canonical ToolRegistry definition: constructs the registry and
    // registers the full production tool set. Never split this into two
    // ToolRegistry-typed definitions — Koin resolves get<ToolRegistry>() to the
    // last registered definition of that type, which would recurse infinitely.
    single {
        val app = androidContext()
        val registry = ToolRegistry()
        val browserTools = BrowserTools(get<BrowserEngine>(), get<NetworkMonitor>())
        val shellTools = ShellTools(app, get<TermuxBridge>(), get<SettingsRepository>())
        val androidTools = get<AndroidAutomationTools>()
        val memoryTools = MemoryTools(get<MemoryRepository>())

        registry.registerAll(
            listOf(
                WebSearchTool(get<NetworkMonitor>()),
                WebFetchTool(get<NetworkMonitor>()),
                DeviceInfoTool(app),
                LaunchAppTool(app),
                OpenUrlTool(app),
                ScreenshotTool()
            ) + browserTools.all() + shellTools.all() + androidTools.all() + memoryTools.all()
        )
        registry
    }
}

val agentsModule = module {
    // MainAgent depends on the AiExecutor interface — resolve by interface type.
    single { MainAgent(get<com.agentos.app.data.provider.AiExecutor>()) }

    single {
        val registry = get<ToolRegistry>()
        val browserEngine = get<BrowserEngine>()
        val toolCtx = ToolContext(
            appContext = androidContext(),
            onActivity = { /* agents wire their own activity callbacks per step */ },
            screenshotRequester = { ScreenCaptureCoordinator.requestScreenshot(androidContext()) }
        )
        AgentRegistry(
            listOf(
                get<MainAgent>(),
                ResearchAgent(registry, toolCtx),
                BrowserAgent(registry, toolCtx, browserEngine),
                AndroidAgent(registry, toolCtx),
                TerminalAgent(registry, toolCtx),
                CodingAgent(registry, toolCtx)
            )
        )
    }

    single {
        TaskEngine(
            scope = get(),
            providerExecutor = get(),
            settings = get(),
            secureStore = get(),
            agentRegistry = get(),
            toolRegistry = get(),
            chatRepo = get(),
            taskRepo = get(),
            memoryRepo = get(),
            networkMonitor = get()
        ).apply {
            contextProvider = { androidContext() }
            screenshotRequester = { ScreenCaptureCoordinator.requestScreenshot(androidContext()) }
        }
    }
}

/**
 * UI layer: ViewModels. Without these definitions koinViewModel() throws
 * NoDefinitionFoundException in MainActivity's first composition — a hard
 * crash on every app launch. All constructor dependencies are singles in
 * coreModule / agentsModule.
 */
val uiModule = module {
    viewModelOf(::ChatViewModel)
    viewModelOf(::TasksViewModel)
    viewModelOf(::TerminalViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AgentsViewModel)
    viewModelOf(::DiagnosticsViewModel)
}
