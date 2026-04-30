package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "group_messages")
data class GroupMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val senderId: String,       // role id, or "user"
    val senderName: String,
    val senderAvatar: String,
    val text: String,
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
}
