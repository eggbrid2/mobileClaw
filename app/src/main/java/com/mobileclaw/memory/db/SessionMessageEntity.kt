package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "session_messages")
data class SessionMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,               // "user" | "agent"
    val text: String,
    val logLinesJson: String = "[]",
    val attachmentsJson: String = "[]",
    val imageBase64: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface SessionMessageDao {
    @Insert
    suspend fun insert(msg: SessionMessageEntity): Long

    @Query("SELECT * FROM session_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun forSession(sessionId: String): List<SessionMessageEntity>

    @Query("SELECT * FROM session_messages WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun forSessionPaged(sessionId: String, limit: Int, offset: Int): List<SessionMessageEntity>

    @Query("SELECT COUNT(*) FROM session_messages WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: String): Int

    @Query("DELETE FROM session_messages WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
