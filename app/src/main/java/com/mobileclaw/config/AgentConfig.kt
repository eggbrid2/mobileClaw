package com.mobileclaw.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.UUID

private val Context.dataStore by preferencesDataStore("agent_config")

private val gson = Gson()

data class GatewayConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val embeddingModel: String = "text-embedding-3-small",
    val supportsMultimodal: Boolean = true,
    val capabilities: List<GatewayCapabilityConfig> = emptyList(),
)

data class GatewayCapabilityConfig(
    val type: String,
    val model: String,
    val enabled: Boolean = true,
    val endpoint: String = "",
    val apiKey: String = "",
)

class AgentConfig(private val context: Context) {

    private object Keys {
        // New multi-gateway keys
        val GATEWAYS = stringPreferencesKey("gateways_json")
        val ACTIVE_GATEWAY_ID = stringPreferencesKey("active_gateway_id")
        // Shared / non-gateway keys
        val LANGUAGE = stringPreferencesKey("response_language")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val UI_STYLE = stringPreferencesKey("ui_style")
        val LOCAL_MODEL_ENABLED = stringPreferencesKey("local_model_enabled")
        val LOCAL_MODEL_ID = stringPreferencesKey("local_model_id")
        val LOCAL_NATIVE_ONLY = stringPreferencesKey("local_native_only")
        val LOCAL_TOOL_CALLING_ENABLED = stringPreferencesKey("local_tool_calling_enabled")
        // Legacy single-gateway keys (read-only for migration)
        val ENDPOINT = stringPreferencesKey("llm_endpoint")
        val API_KEY = stringPreferencesKey("llm_api_key")
        val MODEL = stringPreferencesKey("llm_model")
        val EMBEDDING_MODEL = stringPreferencesKey("llm_embedding_model")
    }

    val configFlow: Flow<ConfigSnapshot> = context.dataStore.data.map { prefs ->
        val gateways = parseGateways(prefs[Keys.GATEWAYS])
            .ifEmpty { migrateFromLegacy(prefs) }
        val activeId = prefs[Keys.ACTIVE_GATEWAY_ID]
        ConfigSnapshot(
            gateways = gateways,
            activeGatewayId = activeId ?: gateways.firstOrNull()?.id,
            language = prefs[Keys.LANGUAGE]?.takeIf { it == "zh" || it == "en" } ?: "zh",
            darkTheme = (prefs[Keys.DARK_THEME] ?: "false") == "true",
            accentColor = parseAccentColor(prefs[Keys.ACCENT_COLOR]) ?: 0xFFC7F43AL,
            uiStyle = "classic",
            localModelEnabled = (prefs[Keys.LOCAL_MODEL_ENABLED] ?: "false") == "true",
            localModelId = prefs[Keys.LOCAL_MODEL_ID]?.takeIf { it.isNotBlank() } ?: "gemma4-e2b-litert",
            localNativeOnly = (prefs[Keys.LOCAL_NATIVE_ONLY] ?: "false") == "true",
            localToolCallingEnabled = (prefs[Keys.LOCAL_TOOL_CALLING_ENABLED] ?: "false") == "true",
        )
    }

    // Backward-compat properties
    val endpoint: String get() = snapshot().endpoint
    val apiKey: String get() = snapshot().apiKey
    val model: String get() = snapshot().model
    val embeddingModel: String get() = snapshot().embeddingModel
    val backend: String get() = "openai"
    val language: String get() = snapshot().language

    suspend fun update(snapshot: ConfigSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GATEWAYS] = gson.toJson(snapshot.gateways)
            if (snapshot.activeGatewayId != null) {
                prefs[Keys.ACTIVE_GATEWAY_ID] = snapshot.activeGatewayId
            }
            prefs[Keys.LANGUAGE] = snapshot.language.takeIf { it == "zh" || it == "en" } ?: "zh"
            prefs[Keys.DARK_THEME] = snapshot.darkTheme.toString()
            prefs[Keys.ACCENT_COLOR] = snapshot.accentColor.toString()
            prefs[Keys.UI_STYLE] = "classic"
            prefs[Keys.LOCAL_MODEL_ENABLED] = snapshot.localModelEnabled.toString()
            prefs[Keys.LOCAL_MODEL_ID] = snapshot.localModelId
            prefs[Keys.LOCAL_NATIVE_ONLY] = snapshot.localNativeOnly.toString()
            prefs[Keys.LOCAL_TOOL_CALLING_ENABLED] = snapshot.localToolCallingEnabled.toString()
        }
    }

    fun isConfigured() = snapshot().let { it.endpoint.isNotBlank() && it.apiKey.isNotBlank() }

    fun snapshot(): ConfigSnapshot = runBlocking { configFlow.first() }

    private fun parseGateways(json: String?): List<GatewayConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<GatewayConfig>>() {}.type
            // Older persisted JSON may deserialize nullable fields into Kotlin non-null properties.
            // Normalize each gateway immediately so later computed accessors never crash during app startup.
            (gson.fromJson<List<GatewayConfig>>(json, type) ?: emptyList()).map { it.normalized() }
        }.getOrDefault(emptyList())
    }

    private fun migrateFromLegacy(prefs: androidx.datastore.preferences.core.Preferences): List<GatewayConfig> {
        val ep = prefs[Keys.ENDPOINT] ?: ""
        val key = prefs[Keys.API_KEY] ?: ""
        if (ep.isBlank() && key.isBlank()) return emptyList()
        return listOf(
            GatewayConfig(
                name = inferGatewayName(ep),
                endpoint = ep,
                apiKey = key,
                model = prefs[Keys.MODEL] ?: "gpt-4o",
                embeddingModel = prefs[Keys.EMBEDDING_MODEL] ?: "text-embedding-3-small",
                supportsMultimodal = true,
            )
        )
    }

    private fun inferGatewayName(endpoint: String): String = when {
        endpoint.contains("openai.com") -> "OpenAI"
        endpoint.contains("groq.com") -> "Groq"
        endpoint.contains("localhost") || endpoint.contains("127.0.0.1") -> "Ollama"
        else -> context.getString(com.mobileclaw.R.string.gateway_custom)
    }

    private fun parseAccentColor(raw: String?): Long? =
        raw?.toLongOrNull()?.takeIf { it != 0L }
}

data class ConfigSnapshot(
    val gateways: List<GatewayConfig> = emptyList(),
    val activeGatewayId: String? = null,
    val language: String = "zh",
    val darkTheme: Boolean = false,
    val accentColor: Long = 0xFFC7F43AL,
    val uiStyle: String = "classic",
    val localModelEnabled: Boolean = false,
    val localModelId: String = "gemma4-e2b-litert",
    val localNativeOnly: Boolean = false,
    val localToolCallingEnabled: Boolean = false,
) {
    val activeGateway: GatewayConfig?
        get() = gateways.find { it.id == activeGatewayId } ?: gateways.firstOrNull()

    // Backward-compat computed properties
    val endpoint: String get() = activeGateway?.endpoint ?: ""
    val apiKey: String get() = activeGateway?.apiKey ?: ""
    val chatEndpoint: String get() = activeGateway?.capabilityEndpoint("chat") ?: ""
    val chatApiKey: String get() = activeGateway?.capabilityApiKey("chat") ?: ""
    val model: String get() = if (localModelEnabled || localNativeOnly) "local:$localModelId" else chatModel
    val cloudModel: String get() = chatModel
    val chatModel: String get() = activeGateway?.capabilityModel("chat") ?: activeGateway?.model ?: "gpt-4o"
    val imageModel: String? get() = activeGateway?.capabilityModel("image")
    val videoModel: String? get() = activeGateway?.capabilityModel("video")
    val embeddingModel: String get() = activeGateway?.capabilityModel("embedding") ?: activeGateway?.embeddingModel ?: "text-embedding-3-small"
    val backend: String get() = "openai"
    val supportsMultimodal: Boolean get() = activeGateway?.supportsCapabilityMultimodal() ?: true
}

fun GatewayConfig.capabilityModel(type: String): String? =
    safeCapabilities().firstOrNull {
        it.enabled && it.type.equals(type, ignoreCase = true) && it.model.isNotBlank()
    }?.model

fun GatewayConfig.capabilityConfig(type: String): GatewayCapabilityConfig? =
    safeCapabilities().firstOrNull {
        it.enabled && it.type.equals(type, ignoreCase = true)
    }

fun GatewayConfig.hasCapability(type: String): Boolean =
    safeCapabilities().any { it.enabled && it.type.equals(type, ignoreCase = true) && it.model.isNotBlank() }

fun GatewayConfig.supportsCapabilityMultimodal(): Boolean =
    supportsMultimodal || hasCapability("image") || hasCapability("video")

fun GatewayConfig.capabilityEndpoint(type: String): String =
    capabilityConfig(type)?.endpoint?.takeIf { it.isNotBlank() } ?: endpoint

fun GatewayConfig.capabilityApiKey(type: String): String =
    capabilityConfig(type)?.apiKey?.takeIf { it.isNotBlank() } ?: apiKey

// Gson can still materialize null into Kotlin non-null fields from older saved config payloads.
// Keep one normalization path for persisted data and one defensive accessor path for already-loaded objects.
private fun GatewayConfig.normalized(): GatewayConfig = copy(
    capabilities = safeCapabilities().map { it.normalized() }
)

private fun GatewayCapabilityConfig.normalized(): GatewayCapabilityConfig = GatewayCapabilityConfig(
    type = type.orEmpty(),
    model = model.orEmpty(),
    enabled = enabled,
    endpoint = endpoint.orEmpty(),
    apiKey = apiKey.orEmpty(),
)

private fun GatewayConfig.safeCapabilities(): List<GatewayCapabilityConfig> =
    runCatching { capabilities }.getOrNull().orEmpty()
