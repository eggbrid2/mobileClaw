package com.mobileclaw.memory.db

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "group_game_events",
    indices = [
        Index(value = ["groupId", "createdAt", "id"]),
        Index(value = ["groupId", "type", "createdAt"]),
        Index(value = ["actionRecordId"]),
    ],
)
data class GroupGameEventEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val type: String,
    @ColumnInfo(defaultValue = "1")
    val roundIndex: Int = 1,
    @ColumnInfo(defaultValue = "''")
    val phaseId: String = "",
    @ColumnInfo(defaultValue = "''")
    val actorSeatId: String = "",
    @ColumnInfo(defaultValue = "''")
    val actorRoleId: String = "",
    @ColumnInfo(defaultValue = "''")
    val actorName: String = "",
    @ColumnInfo(defaultValue = "''")
    val abilityId: String = "",
    @ColumnInfo(defaultValue = "'[]'")
    val targetSeatIdsJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'")
    val targetActorIdsJson: String = "[]",
    @ColumnInfo(defaultValue = "''")
    val targetName: String = "",
    @ColumnInfo(defaultValue = "''")
    val actionRecordId: String = "",
    @ColumnInfo(defaultValue = "''")
    val channelId: String = "",
    @ColumnInfo(defaultValue = "'public'")
    val visibility: String = "public",
    @ColumnInfo(defaultValue = "''")
    val text: String = "",
    @ColumnInfo(defaultValue = "''")
    val resultText: String = "",
    @ColumnInfo(defaultValue = "'{}'")
    val metadataJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface GroupGameEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<GroupGameEventEntity>)

    @Query("SELECT * FROM group_game_events WHERE groupId = :groupId ORDER BY createdAt ASC, id ASC")
    suspend fun forGroup(groupId: String): List<GroupGameEventEntity>

    @Query("SELECT * FROM group_game_events WHERE groupId = :groupId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun latestForGroup(groupId: String, limit: Int): List<GroupGameEventEntity>

    @Query("DELETE FROM group_game_events WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
