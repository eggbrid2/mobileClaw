package com.mobileclaw.memory

import com.mobileclaw.agent.TaskType
import com.mobileclaw.config.ConfigEntry
import com.mobileclaw.config.UserConfig

data class MemoryContextPacket(
    val hardRules: List<String> = emptyList(),
    val preferences: List<String> = emptyList(),
    val userFacts: List<String> = emptyList(),
    val activeTaskMemory: List<String> = emptyList(),
    val appFacts: List<String> = emptyList(),
    val corrections: List<String> = emptyList(),
    val explicitConfig: List<String> = emptyList(),
    val sourceKeys: List<String> = emptyList(),
) {
    fun isEmpty(): Boolean =
        hardRules.isEmpty() &&
            preferences.isEmpty() &&
            userFacts.isEmpty() &&
            activeTaskMemory.isEmpty() &&
            appFacts.isEmpty() &&
            corrections.isEmpty() &&
            explicitConfig.isEmpty()

    fun toPrompt(): String {
        if (isEmpty()) return ""
        return buildString {
            appendLine("## Foundational Memory")
            appendLine("All reasoning, tool choices, wording, and personalization must respect this memory layer. Explicit user configuration overrides inferred memory. Recent user corrections override older preferences.")
            appendSection("Hard rules", hardRules)
            appendSection("User preferences", preferences)
            appendSection("User facts", userFacts)
            appendSection("Active session/task memory", activeTaskMemory)
            appendSection("App/project/tool memory", appFacts)
            appendSection("Recent corrections and failure lessons", corrections)
            appendSection("Explicit user configuration", explicitConfig)
        }.trim()
    }

    private fun StringBuilder.appendSection(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        appendLine(title)
        lines.forEach { appendLine("- $it") }
    }
}

class MemoryContextBuilder(
    private val semanticMemory: SemanticMemory,
    private val userConfig: UserConfig,
) {
    suspend fun build(
        userMessage: String,
        taskType: TaskType,
        activeSessionScopeId: String? = null,
        inMemoryUserConfigEntries: Map<String, ConfigEntry> = emptyMap(),
        inMemoryFacts: Map<String, String> = emptyMap(),
    ): MemoryContextPacket {
        val fromSnapshot = inMemoryFacts.isNotEmpty()
        val facts = if (fromSnapshot) {
            inMemoryFacts.map { (key, value) -> MemoryFact(key = key, value = value) }
        } else {
            runCatching { semanticMemory.facts() }.getOrDefault(emptyList())
        }
        val configs = inMemoryUserConfigEntries.ifEmpty {
            runCatching { userConfig.allEntries() }.getOrDefault(emptyMap())
        }
        val packet = buildFromSnapshots(userMessage, taskType, configs, facts, activeSessionScopeId)
        if (!fromSnapshot) {
            runCatching { semanticMemory.markUsed(packet.sourceKeys) }
        }
        return packet
    }

    fun buildFromSnapshots(
        userMessage: String,
        taskType: TaskType,
        userConfigEntries: Map<String, ConfigEntry>,
        facts: Map<String, String>,
        activeSessionScopeId: String? = null,
    ): MemoryContextPacket = buildFromSnapshots(
        userMessage = userMessage,
        taskType = taskType,
        userConfigEntries = userConfigEntries,
        facts = facts.map { (key, value) -> MemoryFact(key = key, value = value) },
        activeSessionScopeId = activeSessionScopeId,
    )

    fun buildFromSnapshots(
        userMessage: String,
        taskType: TaskType,
        userConfigEntries: Map<String, ConfigEntry>,
        facts: List<MemoryFact>,
        activeSessionScopeId: String? = null,
    ): MemoryContextPacket {
        val query = userMessage.lowercase()
        val relevantFacts = facts
            .filter { it.enabled }
            .filter { fact -> isRelevantMemory(fact.key, fact.value, query, taskType, activeSessionScopeId) }
            .sortedWith(compareByDescending<MemoryFact> { memoryPriority(it, taskType) }.thenByDescending { it.updatedAt }.thenBy { it.key })
            .take(48)

        val explicitConfig = userConfigEntries.entries
            .filter { (key, entry) -> shouldExposeUserConfig(key, entry.value) && isRelevantConfig(key, entry.value, query, taskType) }
            .sortedWith(compareByDescending<Map.Entry<String, ConfigEntry>> { configPriority(it.key, taskType) }.thenBy { it.key })
            .take(24)
            .map { (key, entry) ->
                val desc = entry.description.takeIf { it.isNotBlank() }?.let { " (${it.take(60)})" }.orEmpty()
                "$key: ${entry.value.take(180)}$desc"
            }

        return MemoryContextPacket(
            hardRules = relevantFacts
                .filter { it.key.startsWith("rule.") || it.key.startsWith("tool.policy.") || it.key.startsWith("agent.behavior.") }
                .map { "${it.key.removePrefix("rule.")}: ${it.value.take(220)}" }
                .take(12),
            preferences = relevantFacts
                .filter { it.key.startsWith("preference.") || it.key.startsWith("profile.preferred") || it.key.startsWith("profile.dislikes") }
                .map { "${it.key.removePrefix("preference.").removePrefix("profile.")}: ${it.value.take(220)}" }
                .take(14),
            userFacts = relevantFacts
                .filter { it.key.startsWith("profile.") || it.key.startsWith("user.") }
                .filterNot { it.key.startsWith("profile.preferred") || it.key.startsWith("profile.dislikes") }
                .map { "${it.key.removePrefix("profile.").removePrefix("user.")}: ${it.value.take(180)}" }
                .take(18),
            activeTaskMemory = relevantFacts
                .filter { it.key.startsWith("session.") }
                .map { formatSessionMemory(it) }
                .take(16),
            appFacts = relevantFacts
                .filter { it.key.startsWith("project.") || it.key.startsWith("app.") || it.key.startsWith("skill.") || it.key.startsWith("model.") || it.key.startsWith("vpn.") }
                .map { "${it.key}: ${it.value.take(220)}" }
                .take(18),
            corrections = relevantFacts
                .filter { it.key.startsWith("correction.") || it.key.startsWith("failure.") || it.key.startsWith("lesson.") }
                .map { "${it.key}: ${it.value.take(220)}" }
                .take(12),
            explicitConfig = explicitConfig,
            sourceKeys = relevantFacts.map { it.key },
        )
    }

    private fun isRelevantMemory(key: String, value: String, query: String, taskType: TaskType, activeSessionScopeId: String?): Boolean {
        if (value.isBlank()) return false
        if (isSensitiveKey(key)) return false
        if (key.startsWith("rule.") || key.startsWith("tool.policy.") || key.startsWith("agent.behavior.")) return true
        if (key.startsWith("profile.") || key.startsWith("user.") || key.startsWith("preference.")) return true
        if (key.startsWith("session.")) return activeSessionScopeId?.let { key.startsWith("session.$it.") } == true
        return when (taskType) {
            TaskType.PHONE_CONTROL -> key.startsWith("app.") || key.startsWith("tool.phone.") || key.startsWith("failure.phone.") || query.contains("手机")
            TaskType.APP_BUILD -> key.startsWith("project.") || key.startsWith("preference.ui") || key.startsWith("ui.") || key.startsWith("failure.ui") || key.startsWith("skill.ui")
            TaskType.FILE_CREATE -> key.startsWith("preference.document") || key.startsWith("project.") || key.startsWith("failure.document")
            TaskType.IMAGE_GENERATION -> key.startsWith("preference.image") || key.startsWith("preference.ui") || key.startsWith("failure.image")
            TaskType.WEB_RESEARCH -> key.startsWith("preference.research") || key.startsWith("failure.web")
            TaskType.VPN_CONTROL -> key.startsWith("vpn.") || key.startsWith("failure.vpn")
            TaskType.SKILL_MANAGEMENT -> key.startsWith("skill.") || key.startsWith("agent.")
            TaskType.CODE_EXECUTION -> key.startsWith("project.") || key.startsWith("preference.code") || key.startsWith("failure.code")
            TaskType.CHAT, TaskType.GENERAL -> key.startsWith("correction.") || key.startsWith("failure.") || key.startsWith("lesson.")
        }
    }

    private fun isRelevantConfig(key: String, value: String, query: String, taskType: TaskType): Boolean {
        if (value.isBlank() || isSensitiveKey(key)) return false
        if (key.startsWith("user.") || key.startsWith("profile.") || key.startsWith("preference.") || key.startsWith("persona.")) return true
        if (key == "task.default_lang" || key == "task.tone") return true
        return when (taskType) {
            TaskType.APP_BUILD -> key.startsWith("ui.") || key.startsWith("project.")
            TaskType.IMAGE_GENERATION -> key.startsWith("image.") || key.startsWith("ui.")
            TaskType.FILE_CREATE -> key.startsWith("document.") || key.startsWith("office.")
            TaskType.WEB_RESEARCH -> key.startsWith("research.") || query.contains("搜索")
            TaskType.PHONE_CONTROL -> key.startsWith("phone.") || key.startsWith("app.")
            else -> false
        }
    }

    private fun memoryPriority(fact: MemoryFact, taskType: TaskType): Int {
        val key = fact.key
        var score = 0
        if (key.startsWith("rule.") || key.startsWith("tool.policy.") || key.startsWith("agent.behavior.")) score += 100
        if (key.startsWith("session.")) score += 110
        if (fact.pinned) score += 120
        if (key.startsWith("correction.") || key.startsWith("failure.") || key.startsWith("lesson.")) score += 80
        if (key.startsWith("preference.") || key.startsWith("profile.preferred")) score += 60
        if (key.startsWith("profile.") || key.startsWith("user.")) score += 45
        if (taskType == TaskType.APP_BUILD && (key.startsWith("ui.") || key.startsWith("project.") || key.contains("design"))) score += 25
        if (taskType == TaskType.PHONE_CONTROL && (key.startsWith("tool.phone.") || key.startsWith("app."))) score += 25
        score += (fact.confidence.coerceIn(0f, 1f) * 20).toInt()
        score += fact.useCount.coerceAtMost(20)
        if (fact.lastUsedAt > 0L) {
            val usedAgeDays = ((System.currentTimeMillis() - fact.lastUsedAt).coerceAtLeast(0L) / (24L * 60 * 60 * 1000)).toInt()
            score += when {
                usedAgeDays <= 1 -> 8
                usedAgeDays <= 7 -> 5
                usedAgeDays <= 30 -> 2
                else -> 0
            }
        }
        val ageDays = ((System.currentTimeMillis() - fact.updatedAt).coerceAtLeast(0L) / (24L * 60 * 60 * 1000)).toInt()
        score += when {
            ageDays <= 1 -> 12
            ageDays <= 7 -> 8
            ageDays <= 30 -> 4
            else -> 0
        }
        return score
    }

    private fun configPriority(key: String, taskType: TaskType): Int {
        var score = 0
        if (key.startsWith("user.") || key.startsWith("preference.")) score += 70
        if (key == "task.default_lang" || key == "task.tone") score += 50
        if (taskType == TaskType.APP_BUILD && (key.startsWith("ui.") || key.startsWith("project."))) score += 30
        return score
    }

    private fun shouldExposeUserConfig(key: String, value: String): Boolean =
        value.isNotBlank() && !isSensitiveKey(key)

    private fun formatSessionMemory(fact: MemoryFact): String {
        val suffix = fact.key.substringAfter("session.", "").substringAfter('.', "")
        return when {
            suffix.startsWith("task.goal") -> "current goal: ${fact.value.take(180)}"
            suffix.startsWith("task.summary") -> "latest summary: ${fact.value.take(180)}"
            suffix.startsWith("task.type") -> "task type: ${fact.value.take(80)}"
            suffix.startsWith("task.state") -> "task state: ${fact.value.take(80)}"
            suffix.startsWith("task.state_detail") -> "task state detail: ${fact.value.take(180)}"
            suffix.startsWith("task.status") -> "task status: ${fact.value.take(80)}"
            else -> "${suffix}: ${fact.value.take(180)}"
        }
    }

    private fun isSensitiveKey(key: String): Boolean {
        val lower = key.lowercase()
        return listOf("key", "token", "secret", "password", "credential", "apikey", "api_key").any { lower.contains(it) }
    }
}
