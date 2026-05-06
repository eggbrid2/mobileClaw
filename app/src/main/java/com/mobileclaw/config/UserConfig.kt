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

private val Context.userConfigStore by preferencesDataStore("user_config")

data class ConfigEntry(val value: String, val description: String = "")

/**
 * Flexible key-value config store the agent can read and write.
 * Stores ConfigEntry objects (value + description) in entries_v2.
 * Migrates transparently from the legacy plain-string entries key.
 */
class UserConfig(private val context: Context) {

    private val gson = Gson()
    private val V2_KEY     = stringPreferencesKey("entries_v2")
    private val LEGACY_KEY = stringPreferencesKey("entries")

    private val entryMapType = object : TypeToken<Map<String, ConfigEntry>>() {}.type
    private val legacyMapType = object : TypeToken<Map<String, String>>() {}.type

    val entriesFlow: Flow<Map<String, ConfigEntry>> = context.userConfigStore.data.map { prefs ->
        prefs[V2_KEY]?.let { parseV2(it) }
            ?: prefs[LEGACY_KEY]?.let { migrateLegacy(it) }
            ?: emptyMap()
    }

    val configFlow: Flow<Map<String, String>> = entriesFlow.map { m -> m.mapValues { it.value.value } }

    suspend fun allEntries(): Map<String, ConfigEntry> {
        // .first() takes one emission and cancels — .collect { return@collect } never cancels the flow
        val prefs = context.userConfigStore.data.first()
        return prefs[V2_KEY]?.let { parseV2(it) }
            ?: prefs[LEGACY_KEY]?.let { migrateLegacy(it) }
            ?: emptyMap()
    }

    suspend fun all(): Map<String, String> = allEntries().mapValues { it.value.value }

    suspend fun get(key: String): String? = allEntries()[key]?.value

    suspend fun getEntry(key: String): ConfigEntry? = allEntries()[key]

    suspend fun set(key: String, value: String, description: String = "") {
        context.userConfigStore.edit { prefs ->
            val current = readCurrent(prefs).toMutableMap()
            val prevDesc = current[key]?.description ?: ""
            current[key] = ConfigEntry(value, if (description.isNotBlank()) description else prevDesc)
            prefs[V2_KEY] = gson.toJson(current)
        }
    }

    suspend fun delete(key: String) {
        context.userConfigStore.edit { prefs ->
            val current = readCurrent(prefs).toMutableMap()
            current.remove(key)
            prefs[V2_KEY] = gson.toJson(current)
        }
    }

    private fun readCurrent(prefs: androidx.datastore.preferences.core.Preferences): Map<String, ConfigEntry> =
        prefs[V2_KEY]?.let { parseV2(it) }
            ?: prefs[LEGACY_KEY]?.let { migrateLegacy(it) }
            ?: emptyMap()

    private fun parseV2(json: String): Map<String, ConfigEntry>? =
        runCatching { gson.fromJson<Map<String, ConfigEntry>>(json, entryMapType) }.getOrNull()

    private fun migrateLegacy(json: String): Map<String, ConfigEntry>? =
        runCatching {
            gson.fromJson<Map<String, String>>(json, legacyMapType)
                ?.mapValues { ConfigEntry(it.value) }
        }.getOrNull()
}
