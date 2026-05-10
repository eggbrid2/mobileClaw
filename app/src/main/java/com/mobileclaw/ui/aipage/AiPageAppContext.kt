package com.mobileclaw.ui.aipage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Read-only application context bridge for AI-created native pages.
 *
 * The page runtime receives summarized, structured data instead of direct DAO access.
 * This keeps generated pages useful while avoiding accidental large database dumps.
 */
class AiPageAppContext(private val context: Context) {

    private val app: ClawApplication
        get() = context.applicationContext as ClawApplication

    private val gson = Gson()

    suspend fun snapshot(domain: String = "all", limit: Int = 20): Map<String, Any> =
        withContext(Dispatchers.IO) {
            val cappedLimit = limit.coerceIn(1, 100)
            when (domain.lowercase()) {
                "memory" -> mapOf("memory" to memory())
                "chat", "chats" -> mapOf("chat" to chat(cappedLimit))
                "groups", "group_chat", "group_chats" -> mapOf("groups" to groups(cappedLimit))
                "settings", "config" -> mapOf("settings" to settings())
                "skills" -> mapOf("skills" to skills())
                "roles" -> mapOf("roles" to roles())
                "pages", "ai_pages" -> mapOf("pages" to pages())
                "vpn" -> mapOf("vpn" to vpn(cappedLimit))
                "summary" -> summary(cappedLimit)
                else -> summary(cappedLimit) + mapOf(
                    "memory" to memory(),
                    "chat" to chat(cappedLimit),
                    "groups" to groups(cappedLimit),
                    "settings" to settings(),
                    "skills" to skills(),
                    "roles" to roles(),
                    "pages" to pages(),
                    "vpn" to vpn(cappedLimit),
                )
            }
        }

    suspend fun asJson(domain: String = "all", limit: Int = 20): String =
        gson.toJson(snapshot(domain, limit))

    private suspend fun memory(): Map<String, Any> {
        val semantic = runCatching { app.semanticMemory.all() }.getOrDefault(emptyMap())
        val recentEpisodes = runCatching { app.database.episodeDao().recent(20) }.getOrDefault(emptyList())
        val recentConversation = runCatching { app.database.conversationDao().recent(30) }.getOrDefault(emptyList())
        return mapOf(
            "semanticFacts" to semantic,
            "recentEpisodes" to recentEpisodes.map {
                mapOf(
                    "id" to it.id,
                    "goal" to it.goalText.take(500),
                    "summary" to it.reflexionSummary.take(1000),
                    "success" to it.success,
                    "durationMs" to it.durationMs,
                    "createdAt" to it.createdAt,
                )
            },
            "recentConversationMemory" to recentConversation.reversed().map {
                mapOf(
                    "role" to it.role,
                    "content" to it.content.take(1000),
                    "source" to it.source,
                    "createdAt" to it.createdAt,
                )
            },
        )
    }

    private suspend fun chat(limit: Int): Map<String, Any> {
        val sessions = runCatching { app.database.sessionDao().recent(limit) }.getOrDefault(emptyList())
        return mapOf(
            "sessions" to sessions.map { session ->
                val messageCount = runCatching { app.database.sessionMessageDao().countForSession(session.id) }.getOrDefault(0)
                val recentMessages = runCatching {
                    app.database.sessionMessageDao().forSessionPaged(session.id, 8, 0).reversed()
                }.getOrDefault(emptyList())
                mapOf(
                    "id" to session.id,
                    "title" to session.title,
                    "roleId" to session.roleId,
                    "messageCount" to messageCount,
                    "createdAt" to session.createdAt,
                    "updatedAt" to session.updatedAt,
                    "recentMessages" to recentMessages.map {
                        mapOf(
                            "role" to it.role,
                            "text" to it.text.take(1200),
                            "hasImage" to (it.imageBase64 != null),
                            "attachmentCount" to countJsonArray(it.attachmentsJson),
                            "createdAt" to it.createdAt,
                        )
                    },
                )
            },
        )
    }

    private suspend fun groups(limit: Int): Map<String, Any> {
        val roles = app.roleManager.all().associateBy { it.id }
        return mapOf(
            "groups" to app.groupManager.all().take(limit).map { group ->
                val recentMessages = runCatching { app.database.groupMessageDao().forGroup(group.id).takeLast(20) }
                    .getOrDefault(emptyList())
                mapOf(
                    "id" to group.id,
                    "name" to group.name,
                    "emoji" to group.emoji,
                    "members" to group.memberRoleIds.mapNotNull { id ->
                        roles[id]?.let { role -> mapOf("id" to role.id, "name" to role.name, "avatar" to role.avatar) }
                    },
                    "createdAt" to group.createdAt,
                    "updatedAt" to group.updatedAt,
                    "recentMessages" to recentMessages.map {
                        mapOf(
                            "senderId" to it.senderId,
                            "senderName" to it.senderName,
                            "text" to it.text.take(1200),
                            "attachmentCount" to countJsonArray(it.attachmentsJson),
                            "createdAt" to it.createdAt,
                        )
                    },
                )
            },
        )
    }

    private suspend fun settings(): Map<String, Any> {
        val agent = app.agentConfig.snapshot()
        val userConfig = runCatching { app.userConfig.allEntries() }.getOrDefault(emptyMap())
        return mapOf(
            "agent" to mapOf(
                "language" to agent.language,
                "darkTheme" to agent.darkTheme,
                "accentColor" to agent.accentColor,
                "activeGatewayId" to agent.activeGatewayId.orEmpty(),
                "activeGatewayName" to (agent.activeGateway?.name ?: ""),
                "activeModel" to agent.model,
                "embeddingModel" to agent.embeddingModel,
                "supportsMultimodal" to agent.supportsMultimodal,
                "gatewayCount" to agent.gateways.size,
                "gateways" to agent.gateways.map {
                    mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "endpoint" to redactEndpoint(it.endpoint),
                        "model" to it.model,
                        "embeddingModel" to it.embeddingModel,
                        "supportsMultimodal" to it.supportsMultimodal,
                        "hasApiKey" to it.apiKey.isNotBlank(),
                    )
                },
            ),
            "userConfig" to userConfig.mapValues { (_, entry) ->
                mapOf("value" to redactIfSensitive(entry.value), "description" to entry.description)
            },
        )
    }

    private fun skills(): Map<String, Any> {
        val all = app.skillRegistry.allWithEffectiveLevel().sortedWith(compareBy({ it.injectionLevel }, { it.id }))
        return mapOf(
            "total" to all.size,
            "skills" to all.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "description" to it.description,
                    "type" to it.type.name.lowercase(),
                    "injectionLevel" to it.injectionLevel,
                    "isBuiltin" to it.isBuiltin,
                    "tags" to it.tags,
                    "parameters" to it.parameters.map { p ->
                        mapOf("name" to p.name, "type" to p.type, "required" to p.required, "description" to p.description)
                    },
                )
            },
        )
    }

    private fun roles(): Map<String, Any> {
        val all = app.roleManager.all()
        return mapOf(
            "total" to all.size,
            "roles" to all.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "description" to it.description,
                    "avatar" to it.avatar,
                    "forcedSkillIds" to it.forcedSkillIds,
                    "preferredTaskTypes" to it.preferredTaskTypes.map { t -> t.name },
                    "keywords" to it.keywords,
                    "isBuiltin" to it.isBuiltin,
                )
            },
        )
    }

    private fun pages(): Map<String, Any> {
        val all = app.aiPageStore.getAll()
        return mapOf(
            "total" to all.size,
            "pages" to all.map {
                mapOf(
                    "id" to it.id,
                    "title" to it.title,
                    "icon" to it.icon,
                    "version" to it.version,
                    "description" to it.description,
                    "createdAt" to it.createdAt,
                    "stateKeys" to it.state.keys.toList(),
                    "actionNames" to it.actions.keySet().toList(),
                )
            },
        )
    }

    private suspend fun vpn(limit: Int): Map<String, Any> {
        val subs = runCatching { app.database.subscriptionDao().getAll().first() }.getOrDefault(emptyList())
        return mapOf(
            "subscriptions" to subs.take(limit).map {
                val proxyCount = runCatching { JsonParser.parseString(it.proxiesJson).asJsonArray.size() }.getOrDefault(0)
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "url" to redactEndpoint(it.url),
                    "updatedAt" to it.updatedAt,
                    "proxyCount" to proxyCount,
                    "selectedProxyId" to (it.selectedProxyId ?: ""),
                )
            },
        )
    }

    private suspend fun summary(limit: Int): Map<String, Any> {
        val sessionCount = runCatching { app.database.sessionDao().count() }.getOrDefault(0)
        val conversationCount = runCatching { app.database.conversationDao().count() }.getOrDefault(0)
        val semanticCount = runCatching { app.semanticMemory.all().size }.getOrDefault(0)
        return mapOf(
            "summary" to mapOf(
                "semanticFactCount" to semanticCount,
                "conversationMemoryCount" to conversationCount,
                "chatSessionCount" to sessionCount,
                "groupCount" to app.groupManager.all().size,
                "roleCount" to app.roleManager.all().size,
                "skillCount" to app.skillRegistry.all().size,
                "aiPageCount" to app.aiPageStore.getAll().size,
                "limit" to limit,
            ),
        )
    }

    private fun countJsonArray(json: String): Int =
        runCatching { JsonParser.parseString(json).asJsonArray.size() }.getOrDefault(0)

    private fun redactEndpoint(value: String): String =
        value.replace(Regex("(?i)(key|token|secret|password|pwd)=([^&]+)"), "$1=***")

    private fun redactIfSensitive(value: String): String {
        if (value.length < 12) return value
        val lower = value.lowercase()
        return if (lower.contains("sk-") || lower.contains("token") || lower.contains("secret") || lower.contains("password")) {
            value.take(4) + "***" + value.takeLast(4)
        } else value
    }
}
