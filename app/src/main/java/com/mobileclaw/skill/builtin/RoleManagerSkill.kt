package com.mobileclaw.skill.builtin

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.agent.TaskType
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

/**
 * Full CRUD management for agent roles/personas.
 * Use switch_role to just activate a role by id.
 */
class RoleManagerSkill(
    private val roleManager: RoleManager,
    private val roleRequests: MutableSharedFlow<String>,
) : Skill {

    override val meta = SkillMeta(
        id = "role_manager",
        name = "Role Manager",
        description = "Create, list, update, delete, and activate agent roles (personas). " +
            "Each role has an avatar emoji, name, description, optional system prompt addendum, " +
            "scheduler keywords, preferred task types, forced skill IDs, and optional model override. " +
            "Actions: list, create, update, delete, activate.",
        parameters = listOf(
            SkillParam("action", "string", "Action: list | create | update | delete | activate"),
            SkillParam("id", "string", "Role ID (snake_case). Required for update/delete/activate. Auto-generated for create.", required = false),
            SkillParam("name", "string", "Display name of the role", required = false),
            SkillParam("avatar", "string", "Emoji avatar for the role", required = false),
            SkillParam("description", "string", "Short description of the role's purpose", required = false),
            SkillParam("system_prompt", "string", "Additional system prompt text injected when this role is active", required = false),
            SkillParam("preferred_task_types", "string", "Comma-separated TaskType names this role fits, e.g. WEB_RESEARCH,CODE_EXECUTION", required = false),
            SkillParam("keywords", "string", "Comma-separated trigger keywords for role scheduling", required = false),
            SkillParam("scheduler_priority", "number", "Optional scheduling priority. Higher wins when roles are otherwise similar.", required = false),
            SkillParam("forced_skills", "string", "Comma-separated skill IDs that are always injected with this role (e.g. 'shell,web_search')", required = false),
            SkillParam("model_override", "string", "Optional model ID to use when this role is active (e.g. 'gpt-4o')", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "角色管理",
        descriptionZh = "创建、更新和删除 AI 角色。",
        tags = listOf("角色"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String
            ?: return SkillResult(false, "action is required: list | create | update | delete | activate")

        return when (action) {
            "list" -> {
                val roles = roleManager.all()
                val sb = StringBuilder("Roles (${roles.size}):\n")
                roles.forEach { r ->
                    sb.append("• ${r.avatar} ${r.id}: ${r.name}${if (r.isBuiltin) " [builtin]" else ""}")
                    if (r.forcedSkillIds.isNotEmpty()) sb.append(" | forced: ${r.forcedSkillIds.joinToString(",")}")
                    if (r.modelOverride != null) sb.append(" | model: ${r.modelOverride}")
                    if (r.preferredTaskTypes.isNotEmpty()) sb.append(" | tasks: ${r.preferredTaskTypes.joinToString(",")}")
                    if (r.keywords.isNotEmpty()) sb.append(" | keywords: ${r.keywords.take(6).joinToString(",")}")
                    sb.append("\n  ${r.description}")
                    if (r.systemPromptAddendum.isNotBlank()) sb.append("\n  prompt: ${r.systemPromptAddendum.take(80)}…")
                    sb.append("\n")
                }
                SkillResult(true, sb.toString().trimEnd())
            }

            "create" -> {
                val name = params["name"] as? String
                    ?: return SkillResult(false, "name is required for create")
                val id = (params["id"] as? String)?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
                    ?: "role_${UUID.randomUUID().toString().take(8)}"
                if (roleManager.get(id) != null) return SkillResult(false, "Role '$id' already exists. Use action=update to modify it.")
                val forcedSkills = (params["forced_skills"] as? String)
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: emptyList()
                val preferredTaskTypes = parseTaskTypes(params["preferred_task_types"] as? String)
                val keywords = parseCsv(params["keywords"] as? String)
                val role = Role(
                    id = id,
                    name = name,
                    description = params["description"] as? String ?: "",
                    avatar = params["avatar"] as? String ?: "🤖",
                    systemPromptAddendum = params["system_prompt"] as? String ?: "",
                    forcedSkillIds = forcedSkills,
                    modelOverride = (params["model_override"] as? String)?.takeIf { it.isNotBlank() },
                    preferredTaskTypes = preferredTaskTypes,
                    keywords = keywords,
                    schedulerPriority = (params["scheduler_priority"] as? Number)?.toInt() ?: 0,
                    isBuiltin = false,
                )
                roleManager.save(role)
                SkillResult(true, "Role '${role.avatar} ${role.name}' created with id='$id'. Use action=activate to switch to it.")
            }

            "update" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for update")
                val existing = roleManager.get(id)
                    ?: return SkillResult(false, "Role '$id' not found. Use action=list to see available roles.")
                if (existing.isBuiltin) return SkillResult(false, "Cannot modify builtin role '$id'.")
                val forcedSkills = (params["forced_skills"] as? String)
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: existing.forcedSkillIds
                val preferredTaskTypes = (params["preferred_task_types"] as? String)
                    ?.let { parseTaskTypes(it) }
                    ?: existing.preferredTaskTypes
                val keywords = (params["keywords"] as? String)
                    ?.let { parseCsv(it) }
                    ?: existing.keywords
                val updated = existing.copy(
                    name = params["name"] as? String ?: existing.name,
                    description = params["description"] as? String ?: existing.description,
                    avatar = params["avatar"] as? String ?: existing.avatar,
                    systemPromptAddendum = params["system_prompt"] as? String ?: existing.systemPromptAddendum,
                    forcedSkillIds = forcedSkills,
                    modelOverride = (params["model_override"] as? String)?.takeIf { it.isNotBlank() } ?: existing.modelOverride,
                    preferredTaskTypes = preferredTaskTypes,
                    keywords = keywords,
                    schedulerPriority = (params["scheduler_priority"] as? Number)?.toInt() ?: existing.schedulerPriority,
                )
                roleManager.save(updated)
                SkillResult(true, "Role '$id' updated.")
            }

            "delete" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for delete")
                val role = roleManager.get(id)
                    ?: return SkillResult(false, "Role '$id' not found.")
                if (role.isBuiltin) return SkillResult(false, "Cannot delete builtin role '$id'.")
                roleManager.delete(id)
                SkillResult(true, "Role '$id' deleted.")
            }

            "activate" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for activate")
                roleManager.get(id) ?: return SkillResult(false, "Role '$id' not found. Use action=list to see available roles.")
                roleRequests.emit(id)
                SkillResult(true, "Activated role '$id'.")
            }

            else -> SkillResult(false, "Unknown action: $action. Use list | create | update | delete | activate")
        }
    }

    private fun parseCsv(raw: String?): List<String> =
        raw.orEmpty()
            .split(",", "，", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun parseTaskTypes(raw: String?): List<TaskType> =
        parseCsv(raw).mapNotNull { value ->
            runCatching { TaskType.valueOf(value.uppercase()) }.getOrNull()
        }.distinct()
}
