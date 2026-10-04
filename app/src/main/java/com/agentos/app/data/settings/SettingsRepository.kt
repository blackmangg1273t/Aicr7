package com.agentos.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.agentos.app.data.provider.ProviderConfig
import com.agentos.app.data.provider.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agentos_settings")

/** MCP server configuration (JSON-RPC over HTTP). */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val authHeader: String = "",   // optional "Authorization: ..." value; stored here, not a secret-grade key
    val enabled: Boolean = true
)

/** Behavior toggles for each built-in agent. */
@Serializable
data class AgentsSettings(
    val mainEnabled: Boolean = true,
    val researchEnabled: Boolean = true,
    val browserEnabled: Boolean = true,
    val androidAgentEnabled: Boolean = true,
    val terminalEnabled: Boolean = true,
    val codingEnabled: Boolean = true
)

@Serializable
data class BrowserSettings(
    val enabled: Boolean = true,
    val pageTimeoutMs: Long = 20000,
    val maxPageChars: Int = 8000
)

@Serializable
data class ShellSettings(
    val preferTermux: Boolean = true,
    val defaultTimeoutMs: Long = 60000
)

@Serializable
data class PrivacySettings(
    val saveConversations: Boolean = true,
    val saveTasks: Boolean = true,
    val autoMemoryEnabled: Boolean = true
)

/**
 * Persistent app settings backed by DataStore. Secrets are NEVER stored here
 * (see SecureStore).
 */
class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private object Keys {
        val providers = stringPreferencesKey("providers_json")
        val primaryProviderId = stringPreferencesKey("primary_provider_id")
        val fallbackProviderId = stringPreferencesKey("fallback_provider_id")
        val agents = stringPreferencesKey("agents_json")
        val browser = stringPreferencesKey("browser_json")
        val shell = stringPreferencesKey("shell_json")
        val privacy = stringPreferencesKey("privacy_json")
        val mcpServers = stringPreferencesKey("mcp_servers_json")
    }

    /* ---------------- providers ---------------- */

    val providersFlow: Flow<List<ProviderConfig>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.providers]
        if (raw.isNullOrBlank()) defaultProviders() else runCatching {
            json.decodeFromString<List<ProviderConfig>>(raw)
        }.getOrDefault(defaultProviders())
    }

    suspend fun providers(): List<ProviderConfig> = providersFlow.first()

    suspend fun saveProviders(list: List<ProviderConfig>) {
        context.dataStore.edit { it[Keys.providers] = json.encodeToString(list) }
    }

    val primaryProviderIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.primaryProviderId] }
    val fallbackProviderIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.fallbackProviderId] }

    suspend fun setPrimaryProvider(id: String) {
        context.dataStore.edit { it[Keys.primaryProviderId] = id }
    }

    suspend fun setFallbackProvider(id: String) {
        context.dataStore.edit { it[Keys.fallbackProviderId] = id }
    }

    /* ---------------- agents / browser / shell / privacy ---------------- */

    val agentsFlow: Flow<AgentsSettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.agents]?.let { raw ->
            runCatching { json.decodeFromString<AgentsSettings>(raw) }.getOrNull()
        } ?: AgentsSettings()
    }

    suspend fun saveAgents(a: AgentsSettings) {
        context.dataStore.edit { it[Keys.agents] = json.encodeToString(a) }
    }

    val browserFlow: Flow<BrowserSettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.browser]?.let { runCatching { json.decodeFromString<BrowserSettings>(it) }.getOrNull() } ?: BrowserSettings()
    }

    suspend fun saveBrowser(b: BrowserSettings) {
        context.dataStore.edit { it[Keys.browser] = json.encodeToString(b) }
    }

    val shellFlow: Flow<ShellSettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.shell]?.let { runCatching { json.decodeFromString<ShellSettings>(it) }.getOrNull() } ?: ShellSettings()
    }

    suspend fun saveShell(s: ShellSettings) {
        context.dataStore.edit { it[Keys.shell] = json.encodeToString(s) }
    }

    val privacyFlow: Flow<PrivacySettings> = context.dataStore.data.map { prefs ->
        prefs[Keys.privacy]?.let { runCatching { json.decodeFromString<PrivacySettings>(it) }.getOrNull() } ?: PrivacySettings()
    }

    suspend fun savePrivacy(p: PrivacySettings) {
        context.dataStore.edit { it[Keys.privacy] = json.encodeToString(p) }
    }

    /* ---------------- MCP ---------------- */

    val mcpServersFlow: Flow<List<McpServerConfig>> = context.dataStore.data.map { prefs ->
        prefs[Keys.mcpServers]?.let { raw ->
            runCatching { json.decodeFromString<List<McpServerConfig>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun mcpServers(): List<McpServerConfig> = mcpServersFlow.first()

    suspend fun saveMcpServers(list: List<McpServerConfig>) {
        context.dataStore.edit { it[Keys.mcpServers] = json.encodeToString(list) }
    }

    /* ---------------- helpers ---------------- */

    suspend fun primaryProvider(): ProviderConfig? {
        val list = providers()
        val preferredId = primaryProviderIdFlow.first()
        return list.firstOrNull { it.id == preferredId && it.enabled }
            ?: list.firstOrNull { it.enabled }
    }

    suspend fun fallbackProvider(): ProviderConfig? {
        val list = providers()
        val fbId = fallbackProviderIdFlow.first()
        return list.firstOrNull { it.id == fbId && it.enabled && it.id != primaryProvider()?.id }
    }

    companion object {
        fun defaultProviders(): List<ProviderConfig> = listOf(
            ProviderConfig(
                id = "gemini-default",
                type = ProviderType.GEMINI,
                displayName = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta",
                model = "gemini-2.0-flash",
                enabled = true
            ),
            ProviderConfig(
                id = "openai-default",
                type = ProviderType.OPENAI_COMPATIBLE,
                displayName = "OpenAI-compatible",
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                enabled = false
            ),
            ProviderConfig(
                id = "glm-default",
                type = ProviderType.OPENAI_COMPATIBLE,
                displayName = "GLM (Zhipu)",
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                model = "glm-4-flash",
                enabled = false
            )
        )
    }
}
