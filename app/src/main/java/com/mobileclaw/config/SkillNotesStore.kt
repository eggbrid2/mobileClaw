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

private val Context.skillNotesDataStore by preferencesDataStore("skill_notes")

class SkillNotesStore(private val context: Context) {

    private val gson = Gson()
    private val KEY = stringPreferencesKey("notes")
    private val mapType = object : TypeToken<Map<String, String>>() {}.type

    val notesFlow: Flow<Map<String, String>> = context.skillNotesDataStore.data.map { prefs ->
        prefs[KEY]?.let { runCatching { gson.fromJson<Map<String, String>>(it, mapType) }.getOrNull() }
            ?: emptyMap()
    }

    suspend fun all(): Map<String, String> = notesFlow.first()

    suspend fun set(skillId: String, note: String) {
        context.skillNotesDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableMap()
            current[skillId] = note
            prefs[KEY] = gson.toJson(current)
        }
    }

    suspend fun delete(skillId: String) {
        context.skillNotesDataStore.edit { prefs ->
            val current = parse(prefs[KEY]).toMutableMap()
            current.remove(skillId)
            prefs[KEY] = gson.toJson(current)
        }
    }

    private fun parse(json: String?): Map<String, String> =
        json?.let { runCatching { gson.fromJson<Map<String, String>>(it, mapType) }.getOrNull() }
            ?: emptyMap()
}
