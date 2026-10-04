package com.agentos.app.ui.agents

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentos.app.data.accessibility.AndroidAutomationTools
import com.agentos.app.data.provider.ProviderType
import com.agentos.app.data.settings.SettingsRepository
import com.agentos.app.data.termux.TermuxBridge
import com.agentos.app.domain.agent.AgentRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Real capability overview — every value comes from the actual system state. */
data class SetupStatus(
    val aiProvider: Boolean = false,
    val internet: Boolean = false,
    val androidControl: Boolean = false,
    val termux: Boolean = false,
    val browser: Boolean = true
)

data class AgentInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val capabilities: List<String>
)

class AgentsViewModel(
    private val context: Context,
    private val agentRegistry: AgentRegistry,
    private val settings: SettingsRepository,
    private val secureStore: com.agentos.app.core.security.SecretStore,
    private val termux: TermuxBridge,
    private val androidTools: AndroidAutomationTools,
    private val networkMonitor: com.agentos.app.core.network.NetworkMonitor
) : ViewModel() {

    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents.asStateFlow()

    private val _setup = MutableStateFlow(SetupStatus())
    val setup: StateFlow<SetupStatus> = _setup.asStateFlow()

    init {
        _agents.value = agentRegistry.all().map {
            AgentInfo(it.name, it.displayName, it.description, it.capabilities)
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val providers = settings.providers()
            val hasKey = providers.any { p ->
                p.enabled && (p.type == ProviderType.GEMINI || secureStore.getSecret("provider_key_${p.id}") != null)
            }
            _setup.value = SetupStatus(
                aiProvider = hasKey && providers.any { it.enabled },
                internet = runCatching { networkMonitor.isOnline() }.getOrDefault(false),
                androidControl = runCatching { androidTools.isEnabledBySettings }.getOrDefault(false),
                termux = runCatching { termux.isInstalled() }.getOrDefault(false),
                browser = true
            )
        }
    }
}
