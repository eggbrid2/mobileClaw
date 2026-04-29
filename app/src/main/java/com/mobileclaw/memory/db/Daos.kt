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

    @Query("SELECT * FROM semantic_facts")
    suspend fun all(): List<SemanticFactEntity>

    @Query("SELECT * FROM semantic_facts WHERE `key` LIKE :prefix || '%'")
    suspend fun allWithPrefix(prefix: String): List<SemanticFactEntity>

    @Query("DELETE FROM semantic_facts WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE role = 'user' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentUserMessages(limit: Int = 50): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("DELETE FROM conversations WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
