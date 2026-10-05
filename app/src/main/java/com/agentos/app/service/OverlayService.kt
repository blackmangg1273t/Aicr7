package com.agentos.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating overlay service. Shows a small pill at the top of the screen while
 * a task runs. Tapping it opens MainActivity on top of the current app so the
 * user can see the full task panel.
 *
 * Requires [Settings.canDrawOverlays] (SYSTEM_ALERT_WINDOW permission). If the
 * permission is not granted, the service immediately stops — the foreground
 * notification is the fallback.
 *
 * The overlay only shows the most-recent active task; if multiple tasks run
 * concurrently (uncommon), the user sees the latest.
 */
class OverlayService : Service() {

    companion object {
        const val TAG = "OverlayService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collector: Job? = null
    private var windowManager: WindowManager? = null
    private var rootView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // Start as foreground so the process has foreground priority (same channel as TaskForegroundService).
        startForeground(
            TaskForegroundService.NOTIFICATION_ID,
            NotificationCompat_noop(this)
        )
        // Note: TaskForegroundService should already be running and own the foreground
        // notification; calling startForeground here is a no-op in practice. We share
        // the notification id intentionally.

        collector = scope.launch {
            OverlayController.state.collectLatest { states ->
                if (states.isEmpty()) {
                    removeRoot()
                    stopSelf()
                    return@collectLatest
                }
                showOrUpdate(states.values.last())
            }
        }
    }

    private fun showOrUpdate(state: OverlayController.OverlayState) {
        val wm = windowManager ?: return
        if (rootView == null) {
            val view = buildPill(state)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(48)
            }
            try {
                wm.addView(view, params)
                rootView = view
            } catch (e: Exception) {
                // Can fail if permission revoked mid-flight. Stop the service.
                stopSelf()
            }
        } else {
            updatePill(rootView!!, state)
        }
    }

    private fun buildPill(state: OverlayController.OverlayState): View {
        val density = Resources.getSystem().displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (8 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padH, padV, padH, padV)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            // Custom rounded background via gradient
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(0xF9000000.toInt())
                setStroke(1, 0x33FFFFFF.toInt())
            }
            isClickable = true
            isFocusable = true
        }
        val titleView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            text = pillTitle(state)
            maxLines = 1
            maxEms = 22
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(titleView)
        container.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val i = Intent(this, com.agentos.app.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(i)
            }
            false
        }
        return container
    }

    private fun updatePill(view: View, state: OverlayController.OverlayState) {
        (view as? LinearLayout)?.let { row ->
            (row.getChildAt(0) as? TextView)?.text = pillTitle(state)
        }
    }

    private fun pillTitle(s: OverlayController.OverlayState): String {
        val icon = when (s.status) {
            OverlayController.OverlayState.Status.PLANNING -> "✦"
            OverlayController.OverlayState.Status.RUNNING -> "●"
            OverlayController.OverlayState.Status.WAITING -> "◐"
            OverlayController.OverlayState.Status.RECOVERING -> "↻"
            OverlayController.OverlayState.Status.COMPLETED -> "✓"
            OverlayController.OverlayState.Status.FAILED -> "!"
        }
        val tail = when {
            s.stepCount > 0 -> " · Step ${s.stepIndex}/${s.stepCount}"
            else -> ""
        }
        return "$icon AgentOS · ${s.title.take(36)}$tail"
    }

    private fun dp(value: Int): Int = (value * Resources.getSystem().displayMetrics.density).toInt()

    private fun removeRoot() {
        runCatching {
            rootView?.let { windowManager?.removeView(it) }
            rootView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        collector?.cancel()
        removeRoot()
        scope.cancel()
    }
}

/** Tiny helper so OverlayService can satisfy startForeground with the same notification as TaskForegroundService. */
private fun NotificationCompat_noop(ctx: Context): android.app.Notification {
    val builder = androidx.core.app.NotificationCompat.Builder(ctx, TaskForegroundService.CHANNEL_ID)
        .setContentTitle("AgentOS")
        .setContentText("Working on your request…")
        .setSmallIcon(android.R.drawable.ic_menu_manage)
        .setOngoing(true)
    return builder.build()
}
