package com.agentos.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.agentos.app.domain.engine.TaskEngine
import org.koin.core.context.GlobalContext

/**
 * Receives Pause / Cancel / Open actions from the foreground notification and
 * routes them to the live [TaskEngine]. Open launches MainActivity on top of
 * the current app.
 *
 * Note: Pause is currently a placeholder (the engine doesn't have a true pause
 * API — cancellation is supported). For now, Pause sets the overlay status to
 * WAITING so the user sees it's not actively progressing; a future iteration
 * can wire it to a real cooperative pause.
 */
class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskForegroundService.EXTRA_TASK_ID) ?: return
        val engine = runCatching { GlobalContext.get().get<TaskEngine>() }.getOrNull() ?: return
        when (intent.action) {
            TaskForegroundService.ACTION_CANCEL -> engine.cancel(taskId)
            TaskForegroundService.ACTION_PAUSE -> {
                OverlayController.pause(context, taskId)
            }
            TaskForegroundService.ACTION_OPEN -> {
                val i = Intent(context, com.agentos.app.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(i)
            }
        }
    }
}
