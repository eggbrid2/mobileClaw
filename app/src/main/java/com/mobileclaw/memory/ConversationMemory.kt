package com.mobileclaw.memory

import com.mobileclaw.memory.db.ConversationDao
import com.mobileclaw.memory.db.ConversationEntity
import java.util.UUID

/**
 * Stores chat messages and VLM observations for user profile extraction (RAG).
 * Messages are persisted and can be retrieved for profile analysis.
 */
class ConversationMemory(private val dao: ConversationDao) {

    suspend fun addUserMessage(text: String, taskId: String? = null) {
        if (text.isBlank()) return
        dao.insert(ConversationEntity(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = text.take(2000),
            taskId = taskId,
            source = "chat",
        ))
    }

    suspend fun addAgentMessage(text: String, taskId: String? = null) {
        if (text.isBlank()) return
        dao.insert(ConversationEntity(
            id = UUID.randomUUID().toString(),
            role = "agent",
            content = text.take(2000),
            taskId = taskId,
            source = "chat",
        ))
    }

    /** Store a VLM screen observation that may contain user-visible context. */
    suspend fun addObservation(observation: String, taskId: String? = null) {
        if (observation.isBlank()) return
        dao.insert(ConversationEntity(
            id = UUID.randomUUID().toString(),
            role = "observation",
            content = observation.take(1000),
            taskId = taskId,
            source = "vlm",
        ))
    }

    /** Returns the most recent user messages for profile extraction. */
    suspend fun recentUserMessages(limit: Int = 50): List<ConversationEntity> =
        dao.recentUserMessages(limit)

    /** Returns a combined recent message window (user + agent). */
    suspend fun recentContext(limit: Int = 80): List<ConversationEntity> =
        dao.recent(limit).reversed()

    suspend fun messageCount(): Int = dao.count()

    /** Prune messages older than 90 days to control storage. */
    suspend fun prune() {
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        dao.deleteOlderThan(ninetyDaysAgo)
    }
}
