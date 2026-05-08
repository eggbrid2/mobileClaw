package com.mobileclaw.vpn

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "proxy_subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val proxiesJson: String = "[]",
    val configYaml: String = "",
    val selectedProxyId: String? = null,
)

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM proxy_subscriptions ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM proxy_subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionEntity?

    @Upsert
    suspend fun upsert(entity: SubscriptionEntity)

    @Query("DELETE FROM proxy_subscriptions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE proxy_subscriptions SET selectedProxyId = :proxyId WHERE id = :subId")
    suspend fun selectProxy(subId: String, proxyId: String?)
}
