package com.agentos.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.agentos.app.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps long agent tasks alive when the app is
 * backgrounded (Android 9 compatible). The TaskEngine runs in the process;
 * this service maintains the foreground notification while a task runs, and
 * updates it as the task progresses.
 *
 * The notification has Open / Pause / Cancel actions, all wired to real
 * engine state via [TaskActionReceiver].
 */
class TaskForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "agentos_tasks"
        const val NOTIFICATION_ID = 1001

        const val ACTION_OPEN = "com.agentos.app.TASK_OPEN"
        const val ACTION_PAUSE = "com.agentos.app.TASK_PAUSE"
        const val ACTION_CANCEL = "com.agentos.app.TASK_CANCEL"
        const val EXTRA_TASK_ID = "task_id"

        /**
         * Best-effort flag toggled in [onCreate] / [onDestroy]. Android does not
         * expose a way to query whether a foreground service is currently
         * running, so we keep a static volatile flag here for the Diagnostics
         * screen. This is intentionally conservative: it only reflects the
         * lifecycle of *this* process's service instance.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context, text: String) {
            val intent = Intent(context, TaskForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TaskForegroundService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collector: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(com.agentos.app.R.string.task_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(com.agentos.app.R.string.task_notification_channel_desc)
                }
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification("AgentOS", "Working on your request…", null))

        // Observe OverlayController state and update the notification text.
        collector = scope.launch {
            OverlayController.state.collectLatest { states ->
                if (states.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                val s = states.values.first()
                val title = when (s.status) {
                    OverlayController.OverlayState.Status.PLANNING -> "AgentOS · Planning…"
                    OverlayController.OverlayState.Status.RUNNING -> "AgentOS · ${s.agentName ?: "Working"}"
                    OverlayController.OverlayState.Status.WAITING -> "AgentOS · Waiting for you"
                    OverlayController.OverlayState.Status.RECOVERING -> "AgentOS · Recovering…"
                    OverlayController.OverlayState.Status.COMPLETED -> "AgentOS · Completed"
                    OverlayController.OverlayState.Status.FAILED -> "AgentOS · Needs attention"
                }
                val text = buildString {
                    append(s.title)
                    if (s.stepCount > 0) {
                        append(" · Step ${s.stepIndex}/${s.stepCount}")
                    }
                    s.activity?.let { append(" · $it") }
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildNotification(title, text, s.taskId))
            }
        }
    }

    private fun buildNotification(title: String, text: String, taskId: String?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        var flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        fun actionPi(action: String, reqCode: Int): PendingIntent? {
            if (taskId == null) return null
            val i = Intent(this, TaskActionReceiver::class.java).apply {
                setAction(action)
                putExtra(EXTRA_TASK_ID, taskId)
            }
            return PendingIntent.getBroadcast(this, reqCode, i, flags)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(openPi)
            .setOnlyAlertOnce(true)

        taskId?.let {
            val pausePi = actionPi(ACTION_PAUSE, 1)
            val cancelPi = actionPi(ACTION_CANCEL, 2)
            if (pausePi != null) builder.addAction(0, "Pause", pausePi)
            if (cancelPi != null) builder.addAction(0, "Cancel", cancelPi)
        }
        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        collector?.cancel()
        scope.cancel()
    }
}
