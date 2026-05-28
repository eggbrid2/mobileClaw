package com.mobileclaw.memory.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobileclaw.vpn.SubscriptionDao
import com.mobileclaw.vpn.SubscriptionEntity

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["createdAt"])],
)
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

@Entity(
    tableName = "semantic_facts",
    indices = [
        Index(value = ["type"]),
        Index(value = ["scope"]),
        Index(value = ["enabled", "updatedAt"]),
    ],
)
data class SemanticFactEntity(
    @PrimaryKey val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    @ColumnInfo(defaultValue = "'fact'")
    val type: String = "fact",
    @ColumnInfo(defaultValue = "'global'")
    val scope: String = "global",
    @ColumnInfo(defaultValue = "'unknown'")
    val source: String = "unknown",
    @ColumnInfo(defaultValue = "''")
    val sourceRef: String = "",
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val lastUsedAt: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val useCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true,
)

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["role", "createdAt"]),
    ],
)
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

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session_messages ADD COLUMN senderRoleId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE session_messages ADD COLUMN senderRoleName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE session_messages ADD COLUMN senderRoleAvatar TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_episodes_createdAt ON episodes(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_createdAt ON conversations(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_role_createdAt ON conversations(role, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sessions_updatedAt ON sessions(updatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_session_messages_sessionId_createdAt ON session_messages(sessionId, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_group_messages_groupId_createdAt_id ON group_messages(groupId, createdAt, id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_group_messages_createdAt ON group_messages(createdAt)")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("semantic_facts", "type", "TEXT NOT NULL DEFAULT 'fact'")
        db.addColumnIfMissing("semantic_facts", "scope", "TEXT NOT NULL DEFAULT 'global'")
        db.addColumnIfMissing("semantic_facts", "source", "TEXT NOT NULL DEFAULT 'unknown'")
        db.addColumnIfMissing("semantic_facts", "sourceRef", "TEXT NOT NULL DEFAULT ''")
        db.addColumnIfMissing("semantic_facts", "createdAt", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("semantic_facts", "lastUsedAt", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("semantic_facts", "useCount", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("semantic_facts", "pinned", "INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("semantic_facts", "enabled", "INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE semantic_facts SET createdAt = updatedAt WHERE createdAt = 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_semantic_facts_type ON semantic_facts(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_semantic_facts_scope ON semantic_facts(scope)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_semantic_facts_enabled_updatedAt ON semantic_facts(enabled, updatedAt)")
    }
}

private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, definition: String) {
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIndex >= 0 && cursor.getString(nameIndex) == column) return
        }
    }
    execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
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
    version = 10,
    exportSchema = true,
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build().also { INSTANCE = it }
            }
    }
}
