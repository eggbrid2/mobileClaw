package com.mobileclaw.ui.aipage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** File-based persistence for AI-created native pages. Thread-safe. */
class AiPageStore(filesDir: File) {

    private val dir = File(filesDir, "ai_pages").also { it.mkdirs() }
    private val gson: Gson = GsonBuilder().create()

    private val _pages = MutableStateFlow<List<AiPageDef>>(emptyList())
    val pages: StateFlow<List<AiPageDef>> = _pages.asStateFlow()

    init { reload() }

    fun save(def: AiPageDef) {
        File(dir, "${def.id}.json").writeText(gson.toJson(def))
        reload()
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        reload()
    }

    fun get(id: String): AiPageDef? =
        runCatching {
            val f = File(dir, "$id.json")
            if (f.exists()) gson.fromJson(f.readText(), AiPageDef::class.java) else null
        }.getOrNull()

    fun getAll(): List<AiPageDef> = _pages.value

    private fun reload() {
        val list = dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { gson.fromJson(f.readText(), AiPageDef::class.java) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
        _pages.value = list
    }
}
