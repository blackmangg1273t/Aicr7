package com.agentos.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds the live state of the floating task overlay and provides start/stop
 * signals to [OverlayService]. The UI and the engine both interact with this
 * controller; the service observes it and adds/removes the WindowManager view.
 *
 * The overlay only appears if [canDrawOverlays] is true. Otherwise, the
 * foreground notification (via [TaskForegroundService]) still shows the task
 * is running — the overlay is an enhancement, not a hard requirement.
 */
object OverlayController {

    /** State of the overlay for a single task. */
    data class OverlayState(
        val taskId: String,
        val title: String,
        val stepIndex: Int,
        val stepCount: Int,
        val agentName: String?,
        val activity: String?,
        val status: Status,
        val error: String? = null
    ) {
        enum class Status { PLANNING, RUNNING, WAITING, RECOVERING, COMPLETED, FAILED }
    }

    private val _state = MutableStateFlow<Map<String, OverlayState>>(emptyMap())
    val state: StateFlow<Map<String, OverlayState>> = _state.asStateFlow()

    fun show(ctx: Context, taskId: String, title: String, stepIndex: Int, stepCount: Int) {
        _state.update { it + (taskId to OverlayState(taskId, title, stepIndex, stepCount, null, null, OverlayState.Status.RUNNING)) }
        if (canDrawOverlays(ctx)) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        }
    }

    fun update(ctx: Context, taskId: String, stepIndex: Int, stepCount: Int, agentName: String?, activity: String?) {
        _state.update { current ->
            val s = current[taskId] ?: return@update current
            current + (taskId to s.copy(stepIndex = stepIndex, stepCount = stepCount, agentName = agentName, activity = activity))
        }
    }

    fun setStatus(ctx: Context, taskId: String, status: OverlayState.Status, error: String? = null) {
        _state.update { current ->
            val s = current[taskId] ?: return@update current
            current + (taskId to s.copy(status = status, error = error))
        }
    }

    fun hide(ctx: Context, taskId: String) {
        _state.update { it - taskId }
        // If no overlays left, stop the service.
        if (_state.value.isEmpty()) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    fun pause(ctx: Context, taskId: String) {
        // Pause not implemented as a true coroutine suspend — instead, mark as WAITING.
        setStatus(ctx, taskId, OverlayState.Status.WAITING)
    }

    fun canDrawOverlays(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true
}
