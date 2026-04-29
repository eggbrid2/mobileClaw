package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val roleId: String = "general",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sessions SET title = :title, updatedAt = :at WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, at: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}
