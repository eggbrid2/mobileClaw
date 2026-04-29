package com.mobileclaw.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("agent_config")

class AgentConfig(private val context: Context) {

    private object Keys {
        val ENDPOINT = stringPreferencesKey("llm_endpoint")
        val API_KEY = stringPreferencesKey("llm_api_key")
        val MODEL = stringPreferencesKey("llm_model")
        val EMBEDDING_MODEL = stringPreferencesKey("llm_embedding_model")
        val BACKEND = stringPreferencesKey("llm_backend")
        val LANGUAGE = stringPreferencesKey("response_language")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
    }

    val endpoint: String get() = runBlocking { context.dataStore.data.first()[Keys.ENDPOINT] ?: "" }
    val apiKey: String get() = runBlocking { context.dataStore.data.first()[Keys.API_KEY] ?: "" }
    val model: String get() = runBlocking { context.dataStore.data.first()[Keys.MODEL] ?: "gpt-4o" }
    val embeddingModel: String get() = runBlocking { context.dataStore.data.first()[Keys.EMBEDDING_MODEL] ?: "text-embedding-3-small" }
    val backend: String get() = runBlocking { context.dataStore.data.first()[Keys.BACKEND] ?: "openai" }
    val language: String get() = runBlocking { context.dataStore.data.first()[Keys.LANGUAGE] ?: "auto" }

    val configFlow: Flow<ConfigSnapshot> = context.dataStore.data.map { prefs ->
        ConfigSnapshot(
            endpoint = prefs[Keys.ENDPOINT] ?: "",
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: "gpt-4o",
            embeddingModel = prefs[Keys.EMBEDDING_MODEL] ?: "text-embedding-3-small",
            backend = prefs[Keys.BACKEND] ?: "openai",
            language = prefs[Keys.LANGUAGE] ?: "auto",
            darkTheme = (prefs[Keys.DARK_THEME] ?: "true") == "true",
            accentColor = prefs[Keys.ACCENT_COLOR]?.toLongOrNull() ?: 0xFFFF6B35L,
        )
    }

    suspend fun update(snapshot: ConfigSnapshot) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ENDPOINT] = snapshot.endpoint
            prefs[Keys.API_KEY] = snapshot.apiKey
            prefs[Keys.MODEL] = snapshot.model
            prefs[Keys.EMBEDDING_MODEL] = snapshot.embeddingModel
            prefs[Keys.BACKEND] = snapshot.backend
            prefs[Keys.LANGUAGE] = snapshot.language
            prefs[Keys.DARK_THEME] = snapshot.darkTheme.toString()
            prefs[Keys.ACCENT_COLOR] = snapshot.accentColor.toString()
        }
    }

    fun isConfigured() = endpoint.isNotBlank() && apiKey.isNotBlank()

    fun snapshot(): ConfigSnapshot = runBlocking { configFlow.first() }
}

data class ConfigSnapshot(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val embeddingModel: String,
    val backend: String,
    val language: String = "auto",
    val darkTheme: Boolean = true,
    val accentColor: Long = 0xFFFF6B35L,
)
