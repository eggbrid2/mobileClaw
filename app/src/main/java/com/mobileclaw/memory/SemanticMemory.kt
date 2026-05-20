package com.mobileclaw.memory

import com.mobileclaw.memory.db.SemanticDao
import com.mobileclaw.memory.db.SemanticFactEntity

data class MemoryFact(
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val type: String = "fact",
    val scope: String = "global",
    val source: String = "unknown",
    val sourceRef: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0L,
    val useCount: Int = 0,
    val pinned: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Persistent key-value store for device knowledge and user preferences.
 * Keys use dot-notation: "app.wechat.package_name", "user.preferred_browser", etc.
 */
class SemanticMemory(private val dao: SemanticDao) {

    suspend fun set(
        key: String,
        value: String,
        confidence: Float = 1.0f,
        type: String = inferType(key),
        scope: String = "global",
        source: String = "unknown",
        sourceRef: String = "",
        pinned: Boolean? = null,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.get(key)
        dao.upsert(
            SemanticFactEntity(
                key = key,
                value = value,
                confidence = confidence,
                type = type.ifBlank { inferType(key) },
                scope = scope.ifBlank { existing?.scope ?: "global" },
                source = source.ifBlank { existing?.source ?: "unknown" },
                sourceRef = sourceRef.ifBlank { existing?.sourceRef.orEmpty() },
                createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
                updatedAt = now,
                lastUsedAt = existing?.lastUsedAt ?: 0L,
                useCount = existing?.useCount ?: 0,
                pinned = pinned ?: existing?.pinned ?: false,
                enabled = true,
            )
        )
    }

    suspend fun get(key: String): String? = dao.get(key)?.takeIf { it.enabled }?.value

    suspend fun delete(key: String) = dao.delete(key)

    suspend fun all(): Map<String, String> = dao.all().associate { it.key to it.value }

    suspend fun facts(): List<MemoryFact> = dao.all().map { it.toMemoryFact() }

    suspend fun allFactsIncludingDisabled(): List<MemoryFact> =
        dao.allIncludingDisabled().map { it.toMemoryFact() }

    suspend fun allWithPrefix(prefix: String): Map<String, String> =
        dao.allWithPrefix(prefix).associate { it.key to it.value }

    suspend fun factsWithPrefix(prefix: String): List<MemoryFact> =
        dao.allWithPrefix(prefix).map { it.toMemoryFact() }

    suspend fun markUsed(keys: List<String>) {
        val clean = keys.distinct().filter { it.isNotBlank() }
        if (clean.isNotEmpty()) dao.markUsed(clean)
    }

    suspend fun setEnabled(key: String, enabled: Boolean) = dao.setEnabled(key, enabled)

    suspend fun setPinned(key: String, pinned: Boolean) = dao.setPinned(key, pinned)

    /** Returns a compact string summary for injection into system prompt. */
    suspend fun toPromptContext(): String {
        val facts = dao.all()
        if (facts.isEmpty()) return ""
        val profile = facts.filter { it.key.startsWith("profile.") }
        val other = facts.filterNot { it.key.startsWith("profile.") }
        return buildString {
            if (profile.isNotEmpty()) {
                appendLine("Known user profile:")
                profile.take(24).forEach { appendLine("- ${it.key.removePrefix("profile.")}: ${it.value}") }
            }
            if (other.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("Known device/app facts:")
                other.take(32).forEach { appendLine("- ${it.key}: ${it.value}") }
            }
        }.trim()
    }

    private fun SemanticFactEntity.toMemoryFact(): MemoryFact =
        MemoryFact(
            key = key,
            value = value,
            confidence = confidence,
            type = type,
            scope = scope,
            source = source,
            sourceRef = sourceRef,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastUsedAt = lastUsedAt,
            useCount = useCount,
            pinned = pinned,
            enabled = enabled,
        )

    companion object {
        fun inferType(key: String): String = when {
            key.startsWith("rule.") || key.startsWith("tool.policy.") || key.startsWith("agent.behavior.") -> "rule"
            key.startsWith("preference.") || key.startsWith("profile.preferred") || key.startsWith("profile.dislikes") -> "preference"
            key.startsWith("correction.") || key.startsWith("failure.") || key.startsWith("lesson.") -> "lesson"
            key.startsWith("profile.") || key.startsWith("user.") -> "profile"
            key.startsWith("project.") -> "project"
            key.startsWith("app.") || key.startsWith("skill.") || key.startsWith("model.") || key.startsWith("vpn.") -> "app"
            else -> "fact"
        }
    }
}
