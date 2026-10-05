package com.agentos.app

import android.app.Application
import com.agentos.app.core.logging.Logger
import com.agentos.app.data.repo.ChatRepository
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.di.agentsModule
import com.agentos.app.di.coreModule
import com.agentos.app.di.uiModule
import com.agentos.app.domain.model.ChatMessage
import com.agentos.app.domain.model.EventSeverity
import com.agentos.app.domain.model.EventType
import com.agentos.app.domain.model.MessageRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.UUID

class AgentOsApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (org.koin.core.context.GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@AgentOsApp)
                modules(coreModule, agentsModule, uiModule)
            }
        }
        Logger.i("App", "AgentOS started (v${BuildConfig.VERSION_NAME}, API ${android.os.Build.VERSION.SDK_INT})")

        // Process-death recovery: any task that was RUNNING/PLANNING/etc. when
        // the previous process was killed is now orphaned. Mark it as INTERRUPTED
        // so the user sees a "Resume" option in the Tasks tab instead of a stuck
        // "Working…" row.
        appScope.launch {
            runCatching {
                val taskRepo = org.koin.core.context.GlobalContext.get().get<TaskRepository>()
                val chatRepo = org.koin.core.context.GlobalContext.get().get<ChatRepository>()
                val orphans = taskRepo.activeTasks()
                if (orphans.isNotEmpty()) {
                    val n = taskRepo.markActiveTasksInterrupted()
                    Logger.w("App", "Marked $n orphaned task(s) as INTERRUPTED")
                    orphans.forEach { t ->
                        chatRepo.addMessage(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                conversationId = t.conversationId ?: "",
                                role = MessageRole.EVENT,
                                text = "Task '${t.userRequest.take(60)}' was interrupted by a process restart. Tap Resume to retry.",
                                eventType = EventType.ERROR, severity = EventSeverity.WARNING
                            )
                        )
                    }
                }
            }.onFailure { Logger.w("App", "Recovery scan failed: ${it.message}") }
        }
    }
}
