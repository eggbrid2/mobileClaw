package com.mobileclaw.skill.builtin

import com.mobileclaw.memory.db.SessionDao
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SessionRequest {
    data class Create(val title: String = "新对话") : SessionRequest()
    data class Switch(val id: String) : SessionRequest()
    data class Delete(val id: String) : SessionRequest()
    data class Rename(val id: String, val title: String) : SessionRequest()
}

class SessionManagerSkill(
    private val sessionDao: SessionDao,
    val sessionRequests: MutableSharedFlow<SessionRequest>,
) : Skill {

    override val meta = SkillMeta(
        id = "session_manager",
        name = "Session Manager",
        description = "Manage chat sessions: list recent sessions, create a new session, " +
            "switch to an existing session, rename a session, or delete a session. " +
            "Actions: list, create, switch, rename, delete.",
        parameters = listOf(
            SkillParam("action", "string", "Action: list | create | switch | rename | delete"),
            SkillParam("id", "string", "Session ID (required for switch/rename/delete)", required = false),
            SkillParam("title", "string", "Session title (optional for create, required for rename)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "会话管理",
        descriptionZh = "创建、切换和删除对话会话。",
        tags = listOf("会话"),
    )

    private val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val action = params["action"] as? String
            ?: return@withContext SkillResult(false, "action is required: list | create | switch | rename | delete")

        when (action) {
            "list" -> {
                val sessions = sessionDao.recent(limit = 30)
                if (sessions.isEmpty()) return@withContext SkillResult(true, "No sessions found.")
                val sb = StringBuilder("Recent sessions (${sessions.size}):\n")
                sessions.forEach { s ->
                    sb.append("• ${s.id.take(8)}… | ${s.title} | ${dateFmt.format(Date(s.updatedAt))}\n")
                }
                SkillResult(true, sb.toString().trimEnd())
            }

            "create" -> {
                val title = params["title"] as? String ?: "新对话"
                sessionRequests.emit(SessionRequest.Create(title))
                SkillResult(true, "New session '$title' created and activated.")
            }

            "switch" -> {
                val id = params["id"] as? String
                    ?: return@withContext SkillResult(false, "id is required for switch")
                sessionRequests.emit(SessionRequest.Switch(id))
                SkillResult(true, "Switched to session '$id'.")
            }

            "rename" -> {
                val id = params["id"] as? String
                    ?: return@withContext SkillResult(false, "id is required for rename")
                val title = params["title"] as? String
                    ?: return@withContext SkillResult(false, "title is required for rename")
                sessionRequests.emit(SessionRequest.Rename(id, title))
                SkillResult(true, "Session '$id' renamed to '$title'.")
            }

            "delete" -> {
                val id = params["id"] as? String
                    ?: return@withContext SkillResult(false, "id is required for delete")
                sessionRequests.emit(SessionRequest.Delete(id))
                SkillResult(true, "Session '$id' deleted.")
            }

            else -> SkillResult(false, "Unknown action: $action. Use list | create | switch | rename | delete")
        }
    }
}
