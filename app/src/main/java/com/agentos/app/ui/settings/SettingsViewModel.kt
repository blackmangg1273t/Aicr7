package com.agentos.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.core.logging.Logger
import com.agentos.app.core.security.SecureStore
import com.agentos.app.data.mcp.McpManager
import com.agentos.app.data.provider.ProviderConfig
import com.agentos.app.data.provider.ProviderType
import com.agentos.app.data.repo.ChatRepository
import com.agentos.app.data.repo.MemoryRepository
import com.agentos.app.data.repo.TaskRepository
import com.agentos.app.data.settings.AgentsSettings
import com.agentos.app.data.settings.BrowserSettings
import com.agentos.app.data.settings.McpServerConfig
import com.agentos.app.data.settings.PrivacySettings
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.data.settings.ShellSettings
import com.agentos.app.data.termux.TermuxBridge
import com.agentos.app.data.accessibility.AndroidAutomationTools
import com.agentos.app.service.TaskForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SettingsUiState(
    val primaryProviderId: String? = null,
    val fallbackProviderId: String? = null,
    val keys: Map<String, String> = emptyMap(),     // masked preview only
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val foregroundServiceRunning: Boolean = false,
    val termuxStatus: String? = null,
    val mcpStatus: Map<String, String> = emptyMap(), // serverId -> "connected: N tools" / error
    val wipeMessage: String? = null,
    val mcpMessage: String? = null
)

class SettingsViewModel(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val secureStore: SecureStore,
    private val termux: TermuxBridge,
    private val mcpManager: McpManager,
    private val chatRepo: ChatRepository,
    private val taskRepo: TaskRepository,
    private val memoryRepo: MemoryRepository,
    private val androidTools: AndroidAutomationTools
) : ViewModel() {

    val providers: StateFlow<List<ProviderConfig>> = settings.providersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val agents: StateFlow<AgentsSettings> = settings.agentsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AgentsSettings())

    val browser: StateFlow<BrowserSettings> = settings.browserFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    val shell: StateFlow<ShellSettings> = settings.shellFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ShellSettings())

    val privacy: StateFlow<PrivacySettings> = settings.privacyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrivacySettings())

    val mcpServers: StateFlow<List<McpServerConfig>> = settings.mcpServersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    private val logsFlow = MutableStateFlow(Logger.recent())
    val logs: StateFlow<List<Logger.Entry>> = logsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            settings.primaryProviderIdFlow.collect { id ->
                _ui.value = _ui.value.copy(primaryProviderId = id)
            }
        }
        viewModelScope.launch {
            settings.fallbackProviderIdFlow.collect { id ->
                _ui.value = _ui.value.copy(fallbackProviderId = id)
            }
        }
        viewModelScope.launch {
            refreshAccessibility()
            refreshOverlay(appContext)
            refreshForegroundService()
        }
        Logger.addListener { _ -> logsFlow.value = Logger.recent() }
    }

    /* -------------------- providers -------------------- */

    fun saveKey(providerId: String, key: String) {
        if (key.isBlank()) secureStore.deleteSecret("provider_key_$providerId")
        else secureStore.saveSecret("provider_key_$providerId", key)
        Logger.i("Settings", "API key updated for provider $providerId")
        refreshKeyPreview(providerId)
    }

    private fun refreshKeyPreview(providerId: String) {
        val stored = secureStore.getSecret("provider_key_$providerId")
        val masked = stored?.let { if (it.length > 8) it.take(4) + "…" + it.takeLast(4) else "•••" } ?: ""
        _ui.value = _ui.value.copy(keys = _ui.value.keys + (providerId to masked))
    }

    fun keyPreview(providerId: String): String {
        return _ui.value.keys[providerId] ?: run {
            refreshKeyPreview(providerId)
            _ui.value.keys[providerId] ?: ""
        }
    }

    fun upsertProvider(config: ProviderConfig) {
        viewModelScope.launch {
            val current = settings.providers()
            val updated = if (current.any { it.id == config.id }) current.map { if (it.id == config.id) config else it }
            else current + config
            settings.saveProviders(updated)
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            secureStore.deleteSecret("provider_key_$id")
            settings.saveProviders(settings.providers().filterNot { it.id == id })
        }
    }

    fun setPrimary(id: String) = viewModelScope.launch { settings.setPrimaryProvider(id) }
    fun setFallback(id: String) = viewModelScope.launch { settings.setFallbackProvider(id) }

    /* -------------------- agents / browser / shell -------------------- */

    fun saveAgents(a: AgentsSettings) = viewModelScope.launch { settings.saveAgents(a) }
    fun saveBrowser(b: BrowserSettings) = viewModelScope.launch { settings.saveBrowser(b) }
    fun saveShell(s: ShellSettings) = viewModelScope.launch { settings.saveShell(s) }

    fun testTermux() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(termuxStatus = "Testing…")
            val status = try {
                "OK — ${termux.testConnection()}"
            } catch (e: Exception) {
                "FAILED — ${e.message}"
            }
            _ui.value = _ui.value.copy(termuxStatus = status)
        }
    }

    fun refreshAccessibility() {
        val enabled = androidTools.isEnabledBySettings
        _ui.value = _ui.value.copy(accessibilityEnabled = enabled)
    }

    /* -------------------- overlay -------------------- */

    /**
     * Re-reads whether the system "display over other apps" permission is
     * granted. Call after the user returns from the system permission screen.
     */
    fun refreshOverlay(context: Context) {
        val enabled = canDrawOverlays(context)
        _ui.value = _ui.value.copy(overlayEnabled = enabled)
    }

    /** Version-safe overlay permission check. */
    private fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AndroidSettings.canDrawOverlays(context) else true

    /**
     * Builds the Intent that opens the system "display over other apps"
     * screen for this app. Returns null on pre-M devices where the
     * permission is implicit.
     */
    fun overlayPermissionIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return Intent(
            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /* -------------------- foreground service -------------------- */

    /** Updates the in-memory flag shown on the Background Tasks card. */
    fun refreshForegroundService() {
        _ui.value = _ui.value.copy(foregroundServiceRunning = TaskForegroundService.isRunning)
    }

    /**
     * Starts the foreground service (with a dummy "Test" payload), then stops
     * it after 2 seconds so the user can see the notification appear and
     * disappear. Refreshes UI state on both ends.
     */
    fun testForegroundService(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { TaskForegroundService.start(context, "Test") }
            delay(2_000L)
            runCatching { TaskForegroundService.stop(context) }
            // give the service a beat to run onDestroy
            delay(150L)
            refreshForegroundService()
        }
        refreshForegroundService()
    }

    /* -------------------- MCP -------------------- */

    fun addMcpServer(name: String, url: String, authHeader: String) {
        viewModelScope.launch {
            val list = settings.mcpServers()
            settings.saveMcpServers(list + McpServerConfig(id = UUID.randomUUID().toString(), name = name, url = url, authHeader = authHeader))
        }
    }

    fun deleteMcpServer(id: String) {
        viewModelScope.launch {
            mcpManager.disconnect(id)
            settings.saveMcpServers(settings.mcpServers().filterNot { it.id == id })
            _ui.value = _ui.value.copy(mcpStatus = _ui.value.mcpStatus - id)
        }
    }

    fun connectMcp(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = settings.mcpServers().firstOrNull { it.id == id } ?: return@launch
            _ui.value = _ui.value.copy(mcpStatus = _ui.value.mcpStatus + (id to "Connecting…"), mcpMessage = null)
            val status = try {
                val state = mcpManager.connect(server)
                "Connected — ${state.toolNames.size} tools: ${state.toolNames.joinToString(", ").take(120)}"
            } catch (e: Exception) {
                "FAILED — ${e.message}"
            }
            _ui.value = _ui.value.copy(mcpStatus = _ui.value.mcpStatus + (id to status))
        }
    }

    /* -------------------- privacy -------------------- */

    fun savePrivacy(p: PrivacySettings) = viewModelScope.launch { settings.savePrivacy(p) }

    fun wipeConversations() = viewModelScope.launch {
        chatRepo.wipeAll()
        _ui.value = _ui.value.copy(wipeMessage = "Conversations wiped")
    }

    fun wipeTasks() = viewModelScope.launch {
        taskRepo.wipeAll()
        _ui.value = _ui.value.copy(wipeMessage = "Task history wiped")
    }

    fun wipeMemories() = viewModelScope.launch {
        memoryRepo.wipeAll()
        _ui.value = _ui.value.copy(wipeMessage = "Long-term memory wiped")
    }

    fun wipeSecrets() = viewModelScope.launch {
        secureStore.wipeAll()
        _ui.value = _ui.value.copy(wipeMessage = "All stored API keys deleted")
    }

    fun clearWipeMessage() {
        _ui.value = _ui.value.copy(wipeMessage = null, mcpMessage = null)
    }
}
