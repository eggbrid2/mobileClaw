package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "video_generation_tasks",
    indices = [
        Index(value = ["status", "updatedAt"]),
        Index(value = ["createdAt"]),
    ],
)
data class VideoGenerationTaskEntity(
    @PrimaryKey val taskId: String,
    val prompt: String,
    val provider: String,
    val endpoint: String,
    val apiKey: String,
    val model: String = "",
    val status: String,
    val videoUrl: String = "",
    val filePath: String = "",
    val errorMessage: String = "",
    val submitResponseRaw: String = "",
    val pollResponseRaw: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface VideoGenerationTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: VideoGenerationTaskEntity)

    @Query("SELECT * FROM video_generation_tasks ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<VideoGenerationTaskEntity>

    @Query("SELECT * FROM video_generation_tasks WHERE taskId = :taskId")
    suspend fun get(taskId: String): VideoGenerationTaskEntity?

    @Query("SELECT * FROM video_generation_tasks WHERE status IN (:statuses) ORDER BY updatedAt DESC")
    suspend fun byStatuses(statuses: List<String>): List<VideoGenerationTaskEntity>

    @Query("DELETE FROM video_generation_tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}
