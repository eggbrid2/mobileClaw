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

private val Context.skillLevelDataStore by preferencesDataStore("skill_levels")

/** Stores per-skill injection level overrides. Overrides built-in hardcoded levels. */
class SkillLevelStore(private val context: Context) {

    private val gson = Gson()
    private val KEY = stringPreferencesKey("overrides")
    private val mapType = object : TypeToken<Map<String, Int>>() {}.type

    val overridesFlow: Flow<Map<String, Int>> = context.skillLevelDataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { gson.fromJson<Map<String, Int>>(it, mapType) }.getOrNull() }
            ?: emptyMap()
    }

    suspend fun all(): Map<String, Int> = overridesFlow.first()

    suspend fun set(skillId: String, level: Int) {
        context.skillLevelDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableMap()
            current[skillId] = level
            prefs[KEY] = gson.toJson(current)
        }
    }

    suspend fun remove(skillId: String) {
        context.skillLevelDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableMap()
            current.remove(skillId)
            prefs[KEY] = gson.toJson(current)
        }
    }

    private fun parse(json: String?): Map<String, Int> =
        json?.let { runCatching { gson.fromJson<Map<String, Int>>(it, mapType) }.getOrNull() }
            ?: emptyMap()
}
