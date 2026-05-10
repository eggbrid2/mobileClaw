package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

data class GroupMessageGroupCount(
    val groupId: String,
    val count: Int,
    val latestAt: Long?,
)

@Entity(tableName = "group_messages")
data class GroupMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val senderId: String,       // role id, or "user"
    val senderName: String,
    val senderAvatar: String,
    val text: String,
    val attachmentsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface GroupMessageDao {
    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY createdAt ASC LIMIT 200")
    suspend fun forGroup(groupId: String): List<GroupMessageEntity>

    @Insert
    suspend fun insert(msg: GroupMessageEntity): Long

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)

    @Query("SELECT MAX(createdAt) FROM group_messages WHERE groupId = :groupId")
    suspend fun lastMessageTime(groupId: String): Long?

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForGroup(groupId: String): GroupMessageEntity?

    @Query("SELECT groupId, COUNT(*) AS count, MAX(createdAt) AS latestAt FROM group_messages GROUP BY groupId ORDER BY latestAt DESC")
    suspend fun groupCounts(): List<GroupMessageGroupCount>
}
