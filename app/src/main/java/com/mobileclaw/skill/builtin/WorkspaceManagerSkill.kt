package com.mobileclaw.skill.builtin

import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillType
import com.mobileclaw.workspace.WorkspaceCheckpoint
import com.mobileclaw.workspace.WorkspaceStore

class WorkspaceManagerSkill(
    private val store: WorkspaceStore,
) : Skill {
    override val meta = SkillMeta(
        id = "workspace_manager",
        name = "Workspace Manager",
        nameZh = "工作空间管理",
        description = "Manage the agent's internal workspaces. Use this to create, inspect, summarize, and persist task-scoped working context, notes, checkpoints, and artifact links.",
        descriptionZh = "管理 AI 自己的内部工作空间，用于缓存任务上下文、中间笔记、检查点和产物关联。",
        parameters = listOf(
            SkillParam("action", "string", "create | get | list | summarize | current_state | write_note | write_json | read_file | link_artifact | checkpoint | record_run"),
            SkillParam("workspace_id", "string", "Workspace id", required = false),
            SkillParam("title", "string", "Workspace title", required = false),
            SkillParam("goal", "string", "Workspace goal", required = false),
            SkillParam("scope", "string", "Workspace scope, e.g. session:xxx", required = false),
            SkillParam("name", "string", "File/note/checkpoint name", required = false),
            SkillParam("content", "string", "Text content", required = false),
            SkillParam("json", "object", "JSON value to persist", required = false),
            SkillParam("relative_path", "string", "Workspace relative file path", required = false),
            SkillParam("artifact_type", "string", "Artifact type, e.g. ai_native_page or miniapp", required = false),
            SkillParam("artifact_id", "string", "Artifact id", required = false),
            SkillParam("artifact_title", "string", "Artifact title", required = false),
            SkillParam("success", "boolean", "Run success", required = false),
            SkillParam("task_type", "string", "Task type for run record", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        categories = listOf(SkillToolCategory.MEMORY, SkillToolCategory.ARTIFACT),
        tags = listOf("workspace", "context", "artifact"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required")
        return when (action) {
            "create" -> {
                val goal = params["goal"] as? String ?: return SkillResult(false, "goal is required")
                val title = params["title"] as? String ?: goal.take(40)
                val scope = params["scope"] as? String ?: ""
                val workspace = store.createWorkspace(title = title, goal = goal, scope = scope)
                SkillResult(true, """{"workspace_id":"${workspace.id}","title":${jsonString(workspace.title)},"scope":${jsonString(workspace.scope)}}""")
            }
            "get" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val workspace = store.get(id) ?: return SkillResult(false, "workspace not found")
                SkillResult(true, com.google.gson.Gson().toJson(workspace))
            }
            "list" -> SkillResult(true, com.google.gson.Gson().toJson(store.list()))
            "summarize" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                SkillResult(true, store.summarize(id))
            }
            "current_state" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val state = store.executionContext(id) ?: return SkillResult(false, "workspace not found")
                SkillResult(true, com.google.gson.Gson().toJson(state))
            }
            "write_note" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val name = params["name"] as? String ?: return SkillResult(false, "name is required")
                val content = params["content"] as? String ?: return SkillResult(false, "content is required")
                val path = store.appendNote(id, name, content)
                SkillResult(true, "note_saved:$path")
            }
            "write_json" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val name = params["name"] as? String ?: return SkillResult(false, "name is required")
                val json = params["json"] ?: return SkillResult(false, "json is required")
                val path = store.writeJson(id, name, json)
                SkillResult(true, "json_saved:$path")
            }
            "read_file" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val path = params["relative_path"] as? String ?: return SkillResult(false, "relative_path is required")
                SkillResult(true, store.readFile(id, path) ?: "")
            }
            "link_artifact" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val type = params["artifact_type"] as? String ?: return SkillResult(false, "artifact_type is required")
                val artifactId = params["artifact_id"] as? String ?: return SkillResult(false, "artifact_id is required")
                val title = params["artifact_title"] as? String ?: ""
                val workspace = store.linkArtifact(id, type, artifactId, title) ?: return SkillResult(false, "workspace not found")
                SkillResult(true, com.google.gson.Gson().toJson(workspace))
            }
            "checkpoint" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val name = params["name"] as? String ?: return SkillResult(false, "name is required")
                val content = params["content"] as? String ?: return SkillResult(false, "content is required")
                val path = store.writeCheckpoint(
                    id,
                    WorkspaceCheckpoint(
                        label = name,
                        summary = content.take(200),
                        details = content,
                    ),
                )
                SkillResult(true, "checkpoint_saved:$path")
            }
            "record_run" -> {
                val id = params["workspace_id"] as? String ?: return SkillResult(false, "workspace_id is required")
                val summary = params["content"] as? String ?: return SkillResult(false, "content is required")
                val success = params["success"] as? Boolean ?: true
                val taskType = params["task_type"] as? String ?: ""
                store.recordRun(id, summary, success, taskType)
                SkillResult(true, "run_recorded")
            }
            else -> SkillResult(false, "unknown action: $action")
        }
    }

    private fun jsonString(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""
}
