package com.mobileclaw.memory

import com.google.gson.Gson
import com.mobileclaw.agent.AgentContext
import com.mobileclaw.agent.AgentResult
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.Message
import com.mobileclaw.memory.db.EpisodeDao
import com.mobileclaw.memory.db.EpisodeEntity
import java.util.UUID
import kotlin.math.sqrt

/**
 * Stores task experiences and retrieves similar past episodes via cosine similarity.
 * Embedding is called once per task end to control API cost.
 */
class EpisodicMemory(
    private val dao: EpisodeDao,
    private val llm: LlmGateway,
) {
    private val gson = Gson()

    /** Called after a task completes. Generates reflexion summary and stores the episode. */
    suspend fun record(result: AgentResult) {
        val reflexion = generateReflexion(result)
        val embedding = runCatching { llm.embed(result.context.goal) }.getOrElse { FloatArray(0) }
        val entity = EpisodeEntity(
            id = result.context.taskId,
            goalText = result.context.goal,
            goalEmbedding = gson.toJson(embedding.toList()),
            reflexionSummary = reflexion,
            skillsUsed = gson.toJson(result.context.steps.mapNotNull { it.skillId }.distinct()),
            success = result.success,
            durationMs = result.context.steps.lastOrNull()?.let {
                it.timestampMs - (result.context.steps.firstOrNull()?.timestampMs ?: it.timestampMs)
            } ?: 0L,
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(entity)
    }

    /** Returns top-k similar past episodes for a new goal. */
    suspend fun retrieve(goal: String, topK: Int = 3): List<EpisodeEntity> {
        val queryEmbedding = runCatching { llm.embed(goal) }.getOrElse { return emptyList() }
        val all = dao.recent(limit = 100)
        return all
            .mapNotNull { ep ->
                val epEmb = runCatching {
                    gson.fromJson(ep.goalEmbedding, Array<Float>::class.java).toFloatArray()
                }.getOrNull() ?: return@mapNotNull null
                ep to cosineSimilarity(queryEmbedding, epEmb)
            }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    private suspend fun generateReflexion(result: AgentResult): String {
        val stepSummary = result.context.steps.joinToString("\n") { step ->
            "Step ${step.index}: called ${step.skillId ?: "none"} → ${step.observation.take(200)}"
        }
        val prompt = """
Task: ${result.context.goal}
Outcome: ${if (result.success) "SUCCESS" else "FAILED"}
Steps:
$stepSummary

Write a 2-3 sentence reflection: what worked, what failed, and what to do differently next time.
""".trimIndent()

        return runCatching {
            llm.chat(ChatRequest(
                messages = listOf(Message(role = "user", content = prompt)),
                stream = false,
            )).content ?: "No reflection generated."
        }.getOrElse { "Reflection failed: ${it.message}" }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) { dot += a[i] * b[i]; normA += a[i] * a[i]; normB += b[i] * b[i] }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}
