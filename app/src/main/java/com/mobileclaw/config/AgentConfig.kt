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
            darkTheme = (prefs[Keys.DARK_THEME] ?: "true") == "true",
            accentColor = 0xFF2563EBL,
            uiStyle = prefs[Keys.UI_STYLE]?.takeIf { it == "desk" || it == "classic" } ?: "desk",
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
            prefs[Keys.ACCENT_COLOR] = 0xFF2563EBL.toString()
            prefs[Keys.UI_STYLE] = snapshot.uiStyle.takeIf { it == "desk" || it == "classic" } ?: "desk"
        }
    }

    fun isConfigured() = snapshot().let { it.endpoint.isNotBlank() && it.apiKey.isNotBlank() }

    fun snapshot(): ConfigSnapshot = runBlocking { configFlow.first() }

    private fun parseGateways(json: String?): List<GatewayConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<GatewayConfig>>() {}.type
            gson.fromJson<List<GatewayConfig>>(json, type) ?: emptyList()
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
}

data class ConfigSnapshot(
    val gateways: List<GatewayConfig> = emptyList(),
    val activeGatewayId: String? = null,
    val language: String = "zh",
    val darkTheme: Boolean = true,
    val accentColor: Long = 0xFF2563EBL,
    val uiStyle: String = "desk",
) {
    val activeGateway: GatewayConfig?
        get() = gateways.find { it.id == activeGatewayId } ?: gateways.firstOrNull()

    // Backward-compat computed properties
    val endpoint: String get() = activeGateway?.endpoint ?: ""
    val apiKey: String get() = activeGateway?.apiKey ?: ""
    val model: String get() = activeGateway?.model ?: "gpt-4o"
    val embeddingModel: String get() = activeGateway?.embeddingModel ?: "text-embedding-3-small"
    val backend: String get() = "openai"
    val supportsMultimodal: Boolean get() = activeGateway?.supportsMultimodal ?: true
}
