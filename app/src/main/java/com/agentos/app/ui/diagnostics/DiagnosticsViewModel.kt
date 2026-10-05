package com.agentos.app.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.data.accessibility.AndroidAutomationTools
import com.agentos.app.data.db.AppDatabase
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.domain.engine.TaskEngine
import com.agentos.app.service.AssistantAccessibilityService
import com.agentos.app.service.OverlayController
import com.agentos.app.service.TaskForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Snapshot of every "is it alive?" indicator the user can see on the
 * Diagnostics screen. Updated by [refresh] on demand (and once at startup).
 *
 * `lastUpdated` is the wall-clock time of the most recent refresh so the
 * user can tell whether the values are stale.
 */
data class DiagnosticsState(
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val browserReady: Boolean = true,
    val taskEngineRunning: Boolean = false,
    val aiProviderConfigured: Boolean = false,
    val lastTaskStatus: String? = null,
    val lastTaskTime: Long? = null,
    val lastError: String? = null,
    val lastRecoveryNote: String? = null,
    val lastUpdated: Long = 0L
)

/**
 * View-model for the Diagnostics screen. Aggregates status from:
 *  - [AssistantAccessibilityService] (isRunning) + [AndroidAutomationTools] (isEnabledBySettings)
 *  - [OverlayController] (canDrawOverlays)
 *  - [TaskForegroundService.isRunning] static flag
 *  - [TaskEngine.activeTasks]
 *  - [SettingsRepository.primaryProvider]
 *  - Room: last task and last ERROR / RECOVERY events
 *
 * No state is *pushed* here — every field is read synchronously inside
 * [refresh]. This is intentional: the user hits "Refresh" to re-check.
 */
class DiagnosticsViewModel(
    private val appContext: Context,
    private val androidTools: AndroidAutomationTools,
    private val settings: SettingsRepository,
    private val engine: TaskEngine,
    private val db: AppDatabase
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val accessibility = AssistantAccessibilityService.isRunning() || androidTools.isEnabledBySettings
            val overlay = runCatching { OverlayController.canDrawOverlays(appContext) }.getOrDefault(false)
            val foreground = TaskForegroundService.isRunning
            val taskRunning = engine.activeTasks.value.isNotEmpty()
            val providerConfigured = runCatching { settings.primaryProvider() != null }.getOrDefault(false)

            val lastTask = runCatching { db.taskDao().recent(1).firstOrNull() }.getOrNull()
            val lastError = runCatching {
                db.messageDao().recentByEventType("ERROR", 1).firstOrNull()?.text
            }.getOrNull()
            val lastRecovery = runCatching {
                db.messageDao().recentByEventType("RECOVERY", 1).firstOrNull()?.text
            }.getOrNull()

            _state.value = DiagnosticsState(
                accessibilityEnabled = accessibility,
                overlayEnabled = overlay,
                foregroundServiceRunning = foreground,
                browserReady = true,
                taskEngineRunning = taskRunning,
                aiProviderConfigured = providerConfigured,
                lastTaskStatus = lastTask?.status,
                lastTaskTime = lastTask?.updatedAt,
                lastError = lastError,
                lastRecoveryNote = lastRecovery,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    /** Human-readable "x seconds ago" formatting for the last-refresh timestamp. */
    fun lastUpdatedLabel(): String {
        val ts = _state.value.lastUpdated
        if (ts == 0L) return "—"
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        return fmt.format(Date(ts))
    }
}
