package com.agentos.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps long agent tasks alive when the app is
 * backgrounded (Android 9 compatible). The TaskEngine runs in the process;
 * this service only maintains the foreground notification while a task runs.
 */
class TaskForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "agentos_tasks"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, text: String) {
            val intent = Intent(context, TaskForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TaskForegroundService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
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
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Agent task running")
            .setContentText("Agents are working on your request…")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
}
