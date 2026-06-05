package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: EpisodeEntity)

    @Query("SELECT * FROM episodes ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getById(id: String): EpisodeEntity?

    @Query("DELETE FROM episodes WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface SemanticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: SemanticFactEntity)

    @Query("SELECT * FROM semantic_facts WHERE `key` = :key")
    suspend fun get(key: String): SemanticFactEntity?

    @Query("SELECT * FROM semantic_facts WHERE enabled = 1")
    suspend fun all(): List<SemanticFactEntity>

    @Query("SELECT * FROM semantic_facts")
    suspend fun allIncludingDisabled(): List<SemanticFactEntity>

    @Query("SELECT * FROM semantic_facts ORDER BY pinned DESC, updatedAt DESC, `key` ASC LIMIT :limit OFFSET :offset")
    suspend fun pageIncludingDisabled(limit: Int, offset: Int): List<SemanticFactEntity>

    @Query("SELECT * FROM semantic_facts WHERE `key` LIKE :prefix || '%' AND enabled = 1")
    suspend fun allWithPrefix(prefix: String): List<SemanticFactEntity>

    @Query("DELETE FROM semantic_facts WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("UPDATE semantic_facts SET enabled = :enabled, updatedAt = :updatedAt WHERE `key` = :key")
    suspend fun setEnabled(key: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE semantic_facts SET pinned = :pinned, updatedAt = :updatedAt WHERE `key` = :key")
    suspend fun setPinned(key: String, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE semantic_facts SET lastUsedAt = :usedAt, useCount = useCount + 1 WHERE `key` IN (:keys)")
    suspend fun markUsed(keys: List<String>, usedAt: Long = System.currentTimeMillis())
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE taskId = :taskId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentForTask(taskId: String, limit: Int = 40): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE role = 'user' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentUserMessages(limit: Int = 50): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("DELETE FROM conversations WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
