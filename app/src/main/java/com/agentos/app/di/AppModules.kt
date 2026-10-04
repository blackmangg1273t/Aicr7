package com.agentos.app.di

import android.content.Context
import com.agentos.app.core.network.NetworkMonitor
import com.agentos.app.core.security.SecureStore
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
    single<Context> { androidContext() }
    single<kotlinx.coroutines.CoroutineScope> {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    }
    single { SecureStore(androidContext()) }
    single { NetworkMonitor(androidContext()) }
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
    single { ToolRegistry() }
    single { McpManager(get()) }

    // Tool implementations — every one of these is real, executable code.
    single {
        val app = androidContext()
        val registry = get<ToolRegistry>()
        val browserTools = BrowserTools(get<BrowserEngine>(), get<NetworkMonitor>())
        val preferTermux = kotlinx.coroutines.runBlocking { get<SettingsRepository>().shellFlow.first().preferTermux }
        val shellTools = ShellTools(app, get<TermuxBridge>(), preferTermux)
        val androidTools = AndroidAutomationTools(app)
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
    single { MainAgent(get<ProviderExecutor>()) }

    single {
        val registry = get<ToolRegistry>()
        val toolCtx = ToolContext(
            appContext = androidContext(),
            onActivity = { /* agents wire their own activity callbacks per step */ },
            screenshotRequester = { ScreenCaptureCoordinator.requestScreenshot(androidContext()) }
        )
        AgentRegistry(
            listOf(
                get<MainAgent>(),
                ResearchAgent(registry, toolCtx),
                BrowserAgent(registry, toolCtx),
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
        }
    }
}
