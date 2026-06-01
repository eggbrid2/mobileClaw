package com.mobileclaw.ui.workspace

import com.mobileclaw.agent.TaskType
import com.mobileclaw.ui.ContextualTaskIntent
import com.mobileclaw.workspace.WorkspaceCheckpoint
import com.mobileclaw.workspace.WorkspaceExecutionContext
import com.mobileclaw.workspace.WorkspaceManifest
import com.mobileclaw.workspace.WorkspaceStore

internal class WorkspaceRuntimeCoordinator(
    private val workspaceStore: WorkspaceStore,
) {
    private val sessionWorkspaces = mutableMapOf<String, String>()
    private val groupWorkspaces = mutableMapOf<String, String>()

    fun ensureSessionBinding(
        sessionId: String,
        goal: String,
        taskType: TaskType,
        intent: ContextualTaskIntent,
    ) {
        if (!shouldUseWorkspace(taskType) || sessionId.isBlank()) return
        val currentWorkspaceId = resolveSessionWorkspaceId(sessionId)
        val currentWorkspace = currentWorkspaceId?.let { workspaceStore.get(it) }
        if (currentWorkspace != null && shouldReuseSessionWorkspace(currentWorkspace, goal, taskType, intent)) {
            bindWorkspaceArtifacts(sessionId, intent)
            return
        }

        val scope = "session:$sessionId"
        val existing = intent.aiPage?.let { workspaceStore.findByArtifact("ai_native_page", it.id) }
            ?: intent.miniApp?.let { workspaceStore.findByArtifact("miniapp", it.id) }
                ?.takeIf { shouldReuseSessionWorkspace(it, goal, taskType, intent) }
            ?: workspaceStore.currentForScope(scope)
                ?.takeIf { shouldReuseSessionWorkspace(it, goal, taskType, intent) }
        val workspace = existing ?: workspaceStore.createWorkspace(
            title = goal.take(40),
            goal = goal.take(400),
            scope = scope,
            tags = listOf(taskType.name.lowercase()),
        )
        sessionWorkspaces[sessionId] = workspace.id
        workspaceStore.appendNote(workspace.id, "task_goal", goal.take(2000))
        workspaceStore.writeCheckpoint(
            workspace.id,
            WorkspaceCheckpoint(
                label = "task_start",
                taskType = taskType.name,
                summary = goal.take(200),
                details = "Goal:\n${goal.take(2000)}",
            ),
        )
        bindWorkspaceArtifacts(sessionId, intent)
    }

    fun ensureGroupBinding(
        groupId: String,
        roleId: String,
        taskType: TaskType,
        goal: String,
    ): String {
        val key = "$groupId::$roleId"
        val scope = "group:$groupId:role:$roleId"
        val currentWorkspace = groupWorkspaces[key]?.let { workspaceStore.get(it) }
        if (currentWorkspace != null && shouldReuseGroupWorkspace(currentWorkspace, goal, taskType)) {
            return currentWorkspace.id
        }
        val existing = workspaceStore.currentForScope(scope)
            ?.takeIf { shouldReuseGroupWorkspace(it, goal, taskType) }
        val workspace = existing ?: workspaceStore.createWorkspace(
            title = goal.take(40).ifBlank { "$groupId-$roleId" },
            goal = goal.take(400),
            scope = scope,
            tags = listOf(taskType.name.lowercase(), "group"),
        )
        groupWorkspaces[key] = workspace.id
        workspaceStore.writeCheckpoint(
            workspace.id,
            WorkspaceCheckpoint(
                label = "group_task_start",
                taskType = taskType.name,
                summary = goal.take(200),
                details = "Goal:\n${goal.take(2000)}",
            ),
        )
        return workspace.id
    }

    fun resolveSessionWorkspaceId(sessionId: String): String? {
        sessionWorkspaces[sessionId]?.let { return it }
        val restored = workspaceStore.currentForScope("session:$sessionId")?.id ?: return null
        sessionWorkspaces[sessionId] = restored
        return restored
    }

    fun currentExecutionContext(sessionId: String): WorkspaceExecutionContext? =
        resolveSessionWorkspaceId(sessionId)?.let { workspaceId -> workspaceStore.executionContext(workspaceId) }

    fun currentWorkspaceContext(
        sessionId: String,
        semanticFacts: List<SemanticFactLike>,
    ): String {
        val summary = currentExecutionContext(sessionId)?.let { workspace ->
            buildString {
                appendLine("Workspace: ${workspace.title} (${workspace.workspaceId})")
                appendLine("Goal: ${workspace.goal}")
                appendLine("Scope: ${workspace.scope.ifBlank { "none" }}")
                workspace.taskType.takeIf { it.isNotBlank() }?.let { appendLine("Task type: $it") }
                workspace.checkpointLabel.takeIf { it.isNotBlank() }?.let { appendLine("Checkpoint: $it") }
                workspace.checkpointSummary.takeIf { it.isNotBlank() }?.let { appendLine("Checkpoint summary: ${it.take(240)}") }
                if (workspace.latestArtifactType.isNotBlank() || workspace.latestArtifactId.isNotBlank()) {
                    appendLine(
                        "Current artifact: ${workspace.latestArtifactType.ifBlank { "unknown" }} " +
                            "${workspace.latestArtifactId.ifBlank { "" }} ${workspace.latestArtifactTitle.ifBlank { "" }}".trim()
                    )
                }
                workspace.latestArtifactAction.takeIf { it.isNotBlank() }?.let { appendLine("Artifact action: $it") }
                workspace.latestEventSummary.takeIf { it.isNotBlank() }?.let { appendLine("Latest event: ${it.take(240)}") }
            }.trim()
        } ?: ""
        val scopedMemory = currentScopedMemoryContext(sessionId, semanticFacts)
        val merged = listOf(summary, scopedMemory).filter { it.isNotBlank() }.joinToString("\n")
        if (merged.isBlank()) return ""
        return "## Current Workspace\n$merged"
    }

    fun augmentGoalWithWorkspaceResume(
        sessionId: String,
        userGoal: String,
        executionGoal: String,
    ): String {
        if (!isWorkspaceResumePrompt(userGoal)) return executionGoal
        val workspaceId = resolveSessionWorkspaceId(sessionId) ?: return executionGoal
        val execution = workspaceStore.executionContext(workspaceId)
        val checkpoint = workspaceStore.latestCheckpointContent(workspaceId).orEmpty().take(2400)
        val notes = workspaceStore.latestNotes(workspaceId, limit = 2)
            .joinToString("\n\n") { (name, content) -> "[$name]\n${content.take(1200)}" }
        val recovery = buildString {
            appendLine("[workspace_resume]")
            appendLine("The user is continuing the current workspace-backed task.")
            appendLine("Resume from the latest workspace state first, not from older chat history.")
            execution?.taskType?.takeIf { it.isNotBlank() }?.let { appendLine("Workspace task type: $it") }
            if (!execution?.latestArtifactType.isNullOrBlank() || !execution?.latestArtifactId.isNullOrBlank()) {
                appendLine(
                    "Current artifact: ${execution?.latestArtifactType.orEmpty().ifBlank { "unknown" }} " +
                        "${execution?.latestArtifactId.orEmpty()} ${execution?.latestArtifactTitle.orEmpty()}".trim()
                )
            }
            execution?.latestArtifactAction?.takeIf { it.isNotBlank() }?.let { appendLine("Latest artifact action: $it") }
            when (execution?.latestArtifactType?.lowercase()) {
                "miniapp" -> {
                    appendLine("Preferred artifact tool: app_manager")
                    appendLine("artifact_type=miniapp")
                    execution.latestArtifactId.takeIf { it.isNotBlank() }?.let { appendLine("target_id=$it") }
                    execution.latestArtifactTitle.takeIf { it.isNotBlank() }?.let { appendLine("target_title=$it") }
                    appendLine("mode=patch_existing")
                }
                "ai_native_page" -> {
                    appendLine("Preferred artifact tool: ui_builder")
                    appendLine("artifact_type=ai_native_page")
                    execution.latestArtifactId.takeIf { it.isNotBlank() }?.let { appendLine("target_id=$it") }
                    execution.latestArtifactTitle.takeIf { it.isNotBlank() }?.let { appendLine("target_title=$it") }
                    appendLine("mode=patch_existing")
                }
            }
            if (checkpoint.isNotBlank()) {
                appendLine("Latest checkpoint:")
                appendLine(checkpoint)
            }
            if (notes.isNotBlank()) {
                appendLine()
                appendLine("Recent workspace notes:")
                appendLine(notes)
            }
        }.trim()
        return listOf(executionGoal.trim(), recovery).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun bindWorkspaceArtifacts(sessionId: String, intent: ContextualTaskIntent) {
        val workspaceId = resolveSessionWorkspaceId(sessionId) ?: return
        intent.aiPage?.let { workspaceStore.linkArtifact(workspaceId, "ai_native_page", it.id, it.title) }
        intent.miniApp?.let { workspaceStore.linkArtifact(workspaceId, "miniapp", it.id, it.title) }
    }

    private fun shouldReuseSessionWorkspace(
        workspace: WorkspaceManifest,
        goal: String,
        taskType: TaskType,
        intent: ContextualTaskIntent,
    ): Boolean {
        if (goal.isBlank()) return true
        if (isWorkspaceResumePrompt(goal) || isWorkspaceFollowUpPrompt(goal)) return true
        if (workspaceMatchesIntentArtifact(workspace, intent)) return true
        val sameTaskType = workspace.tags.any { it.equals(taskType.name, ignoreCase = true) }
        if ((taskType == TaskType.CHAT || taskType == TaskType.GENERAL) && sameTaskType) {
            return true
        }
        return goalsAreRelated(workspace.goal, goal)
    }

    private fun shouldReuseGroupWorkspace(
        workspace: WorkspaceManifest,
        goal: String,
        taskType: TaskType,
    ): Boolean {
        if (goal.isBlank()) return true
        if (isWorkspaceResumePrompt(goal) || isWorkspaceFollowUpPrompt(goal)) return true
        val sameTaskType = workspace.tags.any { it.equals(taskType.name, ignoreCase = true) }
        return sameTaskType && goalsAreRelated(workspace.goal, goal)
    }

    private fun workspaceMatchesIntentArtifact(workspace: WorkspaceManifest, intent: ContextualTaskIntent): Boolean {
        val linked = workspace.linkedArtifacts
        val aiPageMatch = intent.aiPage?.let { target ->
            linked.any { it.type == "ai_native_page" && (it.id == target.id || (it.title.isNotBlank() && it.title == target.title)) }
        } == true
        val miniAppMatch = intent.miniApp?.let { target ->
            linked.any { it.type == "miniapp" && (it.id == target.id || (it.title.isNotBlank() && it.title == target.title)) }
        } == true
        return aiPageMatch || miniAppMatch
    }

    private fun isWorkspaceFollowUpPrompt(text: String): Boolean {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false
        return listOf(
            "它", "这个", "这页", "这个页面", "这个ui", "这个应用", "这个app", "这个文件", "这个文档",
            "刚才", "上面", "上一版", "前面", "然后", "改下", "改一下", "修改", "调整", "优化",
            "美化", "完善", "更新", "换成", "改成", "别这样", "不是这样", "不对", "重做", "再来",
            "继续做", "接着做", "沿用", "基于", "按这个", "照这个", "it", "this", "that", "previous",
            "change it", "update it", "optimize", "not this",
        ).any { normalized.contains(it) }
    }

    private fun goalsAreRelated(existingGoal: String, newGoal: String): Boolean {
        val existingTokens = workspaceGoalTokens(existingGoal)
        val newTokens = workspaceGoalTokens(newGoal)
        if (existingTokens.isEmpty() || newTokens.isEmpty()) return false
        val overlap = existingTokens.intersect(newTokens)
        return overlap.size >= 2 || overlap.size.toFloat() / minOf(existingTokens.size, newTokens.size).toFloat() >= 0.34f
    }

    private fun workspaceGoalTokens(text: String): Set<String> =
        Regex("[\\p{L}\\p{N}_]{2,}")
            .findAll(text.lowercase())
            .map { it.value }
            .filterNot {
                it in setOf(
                    "继续", "接着", "然后", "帮我", "一个", "这个", "那个", "一下", "please",
                    "continue", "update", "change", "make", "build", "with", "that", "this",
                )
            }
            .take(24)
            .toSet()

    private fun currentScopedMemoryContext(
        sessionId: String,
        semanticFacts: List<SemanticFactLike>,
    ): String {
        val workspaceId = resolveSessionWorkspaceId(sessionId) ?: return ""
        val facts = semanticFacts.filter { it.enabled && it.key.startsWith("session.$workspaceId.") }
        if (facts.isEmpty()) return ""
        val lines = facts
            .sortedByDescending { it.updatedAt }
            .take(10)
            .map { fact ->
                val label = fact.key.substringAfter("session.$workspaceId.")
                "$label: ${fact.value.take(180)}"
            }
        return if (lines.isEmpty()) "" else "Active task memory:\n" + lines.joinToString("\n") { "- $it" }
    }

    private fun isWorkspaceResumePrompt(text: String): Boolean {
        val normalized = text.trim().lowercase()
        return normalized in setOf(
            "继续", "继续啊", "接着", "接着啊", "继续做", "继续执行", "go on", "continue",
        ) || normalized.startsWith("继续") || normalized.startsWith("接着")
    }

    private fun shouldUseWorkspace(taskType: TaskType): Boolean =
        taskType in setOf(
            TaskType.CHAT,
            TaskType.GENERAL,
            TaskType.APP_BUILD,
            TaskType.FILE_CREATE,
            TaskType.CODE_EXECUTION,
            TaskType.SKILL_MANAGEMENT,
        )
}

internal interface SemanticFactLike {
    val key: String
    val value: String
    val enabled: Boolean
    val updatedAt: Long
}
