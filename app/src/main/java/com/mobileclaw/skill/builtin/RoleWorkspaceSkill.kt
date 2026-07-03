package com.mobileclaw.skill.builtin

import com.mobileclaw.agent.RoleManager
import com.mobileclaw.agent.RoleWorkspaceStore
import com.mobileclaw.config.AgentConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillType

class RoleWorkspaceSkill(
    private val roleManager: RoleManager,
    private val store: RoleWorkspaceStore,
    private val config: AgentConfig,
    private val skillsProvider: () -> List<SkillMeta>,
) : Skill {
    override val meta = SkillMeta(
        id = "role_workspace",
        name = "Role Workspace",
        nameZh = "角色工作区",
        description = "Read and write durable markdown files for each AI role. Use this when a role needs its own core.md, skills.md, memory.md, model.md, journal.md, or skill_index.md context.",
        descriptionZh = "读写每个 AI 角色自己的持久 Markdown 工作区，包括 core.md、skills.md、memory.md、model.md、journal.md 和 skill_index.md。",
        parameters = listOf(
            SkillParam("action", "string", "ensure | snapshot | list | read | write | append | refresh_skills | record_model_config | read_model_config"),
            SkillParam("role_id", "string", "Target role id. Defaults to current active role when caller provides it in context.", required = false),
            SkillParam("file", "string", "Role workspace file path, e.g. core.md, skills.md, memory.md, journal.md", required = false),
            SkillParam("content", "string", "Content for write/append", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        categories = listOf(SkillToolCategory.MEMORY, SkillToolCategory.SKILL, SkillToolCategory.SELF_EVOLUTION),
        tags = listOf("role", "workspace", "memory", "skills"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required")
        val roleId = (params["role_id"] as? String).orEmpty().ifBlank { "general" }
        val role = roleManager.get(roleId) ?: return SkillResult(false, "Role not found: $roleId")
        return when (action) {
            "ensure" -> {
                val snapshot = store.ensure(role, skillsProvider())
                SkillResult(true, "role_workspace_ready:${snapshot.rootPath}")
            }
            "snapshot" -> {
                val snapshot = store.snapshot(role, skillsProvider())
                SkillResult(
                    true,
                    """
Role workspace: ${snapshot.rootPath}

## core.md
${snapshot.core}

## memory.md
${snapshot.memory}

## model.md
${snapshot.model}

## skills.md
${snapshot.skills}
""".trimIndent(),
                )
            }
            "list" -> {
                store.ensure(role, skillsProvider())
                SkillResult(true, store.list(role.id).joinToString("\n").ifBlank { "No files." })
            }
            "read" -> {
                val file = params["file"] as? String ?: return SkillResult(false, "file is required for read")
                store.ensure(role, skillsProvider())
                SkillResult(true, store.read(role.id, file) ?: "")
            }
            "write" -> {
                val file = params["file"] as? String ?: return SkillResult(false, "file is required for write")
                val content = params["content"] as? String ?: return SkillResult(false, "content is required for write")
                val path = store.write(role.id, file, content)
                SkillResult(true, "role_file_written:$path")
            }
            "append" -> {
                val file = params["file"] as? String ?: return SkillResult(false, "file is required for append")
                val content = params["content"] as? String ?: return SkillResult(false, "content is required for append")
                val path = store.append(role.id, file, content)
                SkillResult(true, "role_file_appended:$path")
            }
            "refresh_skills" -> {
                store.ensure(role, skillsProvider())
                val path = store.refreshSkillIndex(role.id, skillsProvider())
                SkillResult(true, "role_skill_index_refreshed:$path")
            }
            "record_model_config" -> {
                store.ensure(role, skillsProvider())
                val path = store.recordModelConfig(role, config.snapshot(), source = "role_workspace_skill")
                SkillResult(true, "role_model_config_recorded:$path")
            }
            "read_model_config" -> {
                store.ensure(role, skillsProvider())
                SkillResult(true, store.read(role.id, RoleWorkspaceStore.MODEL_MD) ?: "")
            }
            else -> SkillResult(false, "Unknown action: $action. Use ensure | snapshot | list | read | write | append | refresh_skills | record_model_config | read_model_config")
        }
    }
}
