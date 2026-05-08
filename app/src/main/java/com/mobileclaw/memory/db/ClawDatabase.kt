package com.mobileclaw.memory.db

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobileclaw.vpn.SubscriptionDao
import com.mobileclaw.vpn.SubscriptionEntity

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val goalText: String,
    val goalEmbedding: String,      // JSON float array
    val reflexionSummary: String,
    val skillsUsed: String,         // JSON string array
    val success: Boolean,
    val durationMs: Long,
    val createdAt: Long,
)

@Entity(tableName = "semantic_facts")
data class SemanticFactEntity(
    @PrimaryKey val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val role: String,               // "user" | "agent" | "observation"
    val content: String,
    val embedding: String = "[]",   // JSON float array, empty until computed
    val taskId: String? = null,
    val source: String = "chat",    // "chat" | "vlm" | "task"
    val createdAt: Long = System.currentTimeMillis(),
)

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS proxy_subscriptions (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "name TEXT NOT NULL, " +
                "url TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL, " +
                "proxiesJson TEXT NOT NULL DEFAULT '[]', " +
                "configYaml TEXT NOT NULL DEFAULT '', " +
                "selectedProxyId TEXT)"
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE proxy_subscriptions ADD COLUMN configYaml TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE group_messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]'")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS group_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "groupId TEXT NOT NULL, " +
                "senderId TEXT NOT NULL, " +
                "senderName TEXT NOT NULL, " +
                "senderAvatar TEXT NOT NULL, " +
                "text TEXT NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sessions (" +
                "id TEXT PRIMARY KEY NOT NULL, " +
                "title TEXT NOT NULL, " +
                "roleId TEXT NOT NULL DEFAULT 'general', " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS session_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "sessionId TEXT NOT NULL, " +
                "role TEXT NOT NULL, " +
                "text TEXT NOT NULL, " +
                "logLinesJson TEXT NOT NULL DEFAULT '[]', " +
                "attachmentsJson TEXT NOT NULL DEFAULT '[]', " +
                "imageBase64 TEXT, " +
                "createdAt INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [
        EpisodeEntity::class,
        SemanticFactEntity::class,
        ConversationEntity::class,
        SessionEntity::class,
        SessionMessageEntity::class,
        GroupMessageEntity::class,
        SubscriptionEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class ClawDatabase : RoomDatabase() {
    abstract fun episodeDao(): EpisodeDao
    abstract fun semanticDao(): SemanticDao
    abstract fun conversationDao(): ConversationDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionMessageDao(): SessionMessageDao
    abstract fun groupMessageDao(): GroupMessageDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        @Volatile private var INSTANCE: ClawDatabase? = null

        fun getInstance(context: Context): ClawDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClawDatabase::class.java,
                    "claw.db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build().also { INSTANCE = it }
            }
    }
}
