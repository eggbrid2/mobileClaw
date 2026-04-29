package com.mobileclaw.memory

import com.mobileclaw.memory.db.SemanticDao
import com.mobileclaw.memory.db.SemanticFactEntity

/**
 * Persistent key-value store for device knowledge and user preferences.
 * Keys use dot-notation: "app.wechat.package_name", "user.preferred_browser", etc.
 */
class SemanticMemory(private val dao: SemanticDao) {

    suspend fun set(key: String, value: String, confidence: Float = 1.0f) {
        dao.upsert(SemanticFactEntity(key = key, value = value, confidence = confidence))
    }

    suspend fun get(key: String): String? = dao.get(key)?.value

    suspend fun delete(key: String) = dao.delete(key)

    suspend fun all(): Map<String, String> = dao.all().associate { it.key to it.value }

    /** Returns a compact string summary for injection into system prompt. */
    suspend fun toPromptContext(): String {
        val facts = dao.all()
        if (facts.isEmpty()) return ""
        return "Known device facts:\n" + facts.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }
}
