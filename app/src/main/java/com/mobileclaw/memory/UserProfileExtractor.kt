package com.mobileclaw.memory

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.memory.db.ConversationEntity
import com.mobileclaw.memory.db.EpisodeEntity

/**
 * Extracts structured user profile facts from two sources:
 *  1. Conversation history (chat RAG) — called after each task
 *  2. Task execution patterns (episode history) — called on manual refresh
 *
 * Results are stored in SemanticMemory under "profile.<dimension>.<aspect>" keys.
 */
class UserProfileExtractor(
    private val llm: LlmGateway,
    private val semanticMemory: SemanticMemory,
    private val conversationMemory: ConversationMemory,
) {
    private val gson = Gson()

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called after each task completes. Uses recent conversation history. */
    suspend fun extractAndUpdate(recentGoal: String, recentSummary: String) {
        val messages = runCatching { conversationMemory.recentUserMessages(limit = 30) }
            .getOrDefault(emptyList())
        if (messages.isEmpty() && recentGoal.isBlank()) return
        val snippet = buildConversationSnippet(messages, recentGoal, recentSummary)
        val facts = runCatching { callLlmConversation(snippet) }.getOrDefault(emptyList())
        facts.forEach { persistFact(it) }
    }

    /** Called on manual refresh. Infers profile from task execution patterns. */
    suspend fun extractFromEpisodes(episodes: List<EpisodeEntity>) {
        if (episodes.size < 3) return
        val analysis = buildEpisodeAnalysis(episodes)
        val facts = runCatching { callLlmEpisodes(analysis) }.getOrDefault(emptyList())
        facts.forEach { persistFact(it) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun persistFact(fact: ProfileFact) {
        if (fact.key.startsWith("profile.") && fact.value.isNotBlank()) {
            semanticMemory.set(fact.key, fact.value, fact.confidence.coerceIn(0f, 1f))
        }
    }

    private fun buildConversationSnippet(
        messages: List<ConversationEntity>,
        goal: String,
        summary: String,
    ): String = buildString {
        if (goal.isNotBlank()) {
            appendLine("最新任务: $goal")
            if (summary.isNotBlank()) appendLine("任务结果: $summary")
        }
        if (messages.isNotEmpty()) {
            appendLine("近期对话:")
            messages.takeLast(20).forEach { msg ->
                val prefix = when (msg.role) { "user" -> "用户"; "agent" -> "AI"; else -> "[观察]" }
                appendLine("$prefix: ${msg.content.take(200)}")
            }
        }
    }

    private fun buildEpisodeAnalysis(episodes: List<EpisodeEntity>): String {
        val total = episodes.size
        val successCount = episodes.count { it.success }

        val allSkills = episodes.flatMap { ep ->
            runCatching { gson.fromJson(ep.skillsUsed, Array<String>::class.java).toList() }
                .getOrDefault(emptyList())
        }
        val skillFreq = allSkills.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(10)

        // Skill-to-trait mapping
        val skillTraits = buildString {
            skillFreq.forEach { (skill, count) ->
                val trait = when {
                    skill.startsWith("web_")            -> "→ 信息检索能力强"
                    skill == "shell"                    -> "→ 具备技术操作能力"
                    skill == "memory"                   -> "→ 注重知识积累"
                    skill == "see_screen"               -> "→ 依赖视觉分析"
                    skill == "navigate"                 -> "→ 频繁使用多个应用"
                    skill.startsWith("bg_")             -> "→ 使用后台自动化"
                    skill == "screenshot"               -> "→ 习惯截图记录"
                    skill == "tap" || skill == "scroll" -> "→ 活跃的界面操作"
                    else                                -> ""
                }
                if (trait.isNotEmpty()) appendLine("  $skill($count)次 $trait")
            }
        }

        // Goal text analysis
        val avgLen = if (episodes.isEmpty()) 0.0 else episodes.map { it.goalText.length }.average()
        val goals = episodes.take(8).joinToString("\n") { ep ->
            "  ${if (ep.success) "✓" else "✗"} ${ep.goalText.take(50)}"
        }

        // App interaction inference
        val webGoals = episodes.count { ep ->
            ep.skillsUsed.contains("web_search") || ep.skillsUsed.contains("fetch_url") || ep.skillsUsed.contains("web_browse")
        }
        val techGoals = episodes.count { ep -> ep.skillsUsed.contains("shell") }
        val visionGoals = episodes.count { ep -> ep.skillsUsed.contains("see_screen") || ep.skillsUsed.contains("screenshot") }

        return buildString {
            appendLine("任务统计: 共 $total 个，成功率 ${successCount * 100 / total}%")
            appendLine("平均任务复杂度: ${avgLen.toInt()} 字符")
            appendLine("网络相关任务: $webGoals 个，技术操作: $techGoals 个，视觉分析: $visionGoals 个")
            appendLine("技能使用分析:")
            append(skillTraits)
            appendLine("近期任务示例:")
            appendLine(goals)
        }
    }

    // ── LLM calls ─────────────────────────────────────────────────────────────

    private val keysHint = """
可用key（选最相关的填写，每次最多6条）:
profile.physio.health / profile.physio.fitness / profile.physio.appearance / profile.physio.medical
profile.personality.temperament / profile.personality.style / profile.personality.emotion_pattern
profile.cognitive.thinking / profile.cognitive.learning / profile.cognitive.perspective
profile.emotional.stability / profile.emotional.empathy / profile.emotional.stress
profile.social.style / profile.social.communication / profile.social.relationships
profile.values.core / profile.values.goals / profile.values.principles
profile.capability.skills / profile.capability.execution / profile.capability.creativity
profile.spiritual.core / profile.spiritual.beliefs / profile.spiritual.resilience""".trimIndent()

    private suspend fun callLlmConversation(context: String): List<ProfileFact> {
        val prompt = """
你是用户画像分析师，根据以下对话记录推断用户特征。
$context

$keysHint

规则：只输出有充分依据的条目，value用简短中文（20字内），只输出JSON数组。
示例：[{"key":"profile.cognitive.thinking","value":"逻辑思维强，喜欢系统化","confidence":0.7}]""".trimIndent()

        return callLlm(prompt)
    }

    private suspend fun callLlmEpisodes(analysis: String): List<ProfileFact> {
        val prompt = """
你是用户画像分析师，根据用户的AI任务历史推断其特征。
$analysis

$keysHint

根据技能使用频率、任务类型、成功率推断用户的能力、认知和性格特征。
只输出有充分依据的条目，value用简短中文（20字内），只输出JSON数组。""".trimIndent()

        return callLlm(prompt)
    }

    private suspend fun callLlm(prompt: String): List<ProfileFact> {
        val content = runCatching {
            llm.chat(ChatRequest(
                messages = listOf(Message(role = "user", content = prompt)),
                stream = false,
            )).content
        }.getOrNull() ?: return emptyList()
        return parseFactsJson(content)
    }

    private fun parseFactsJson(raw: String): List<ProfileFact> {
        val json = raw.trim().let {
            if (it.startsWith("```")) it.lines().drop(1).dropLast(1).joinToString("\n") else it
        }
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull { elem ->
                val obj = elem.asJsonObject
                val key = obj.get("key")?.asString ?: return@mapNotNull null
                val value = obj.get("value")?.asString ?: return@mapNotNull null
                val confidence = obj.get("confidence")?.asFloat ?: 0.5f
                if (key.isBlank() || value.isBlank()) null else ProfileFact(key, value, confidence)
            }
        }.getOrDefault(emptyList())
    }

    private data class ProfileFact(val key: String, val value: String, val confidence: Float)
}
