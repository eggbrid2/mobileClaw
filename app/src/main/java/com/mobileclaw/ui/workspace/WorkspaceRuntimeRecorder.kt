package com.mobileclaw.ui.workspace

import com.google.gson.JsonParser
import com.mobileclaw.agent.AgentWorkspaceUpdate
import com.mobileclaw.memory.MemoryWriter
import com.mobileclaw.ui.common.stringListOrEmpty
import com.mobileclaw.ui.common.stringOrNull
import com.mobileclaw.workspace.WorkspaceArtifactState
import com.mobileclaw.workspace.WorkspaceCheckpoint
import com.mobileclaw.workspace.WorkspaceEvent
import com.mobileclaw.workspace.WorkspaceStore

internal class WorkspaceRuntimeRecorder(
    private val workspaceStore: WorkspaceStore,
    private val memoryWriter: MemoryWriter,
    private val resolveWorkspaceId: (String) -> String?,
) {
    fun persistObservation(
        sessionId: String,
        skillId: String?,
        rawOutput: String,
    ) {
        val workspaceId = resolveWorkspaceId(sessionId) ?: return
        if (skillId.isNullOrBlank()) return
        when (skillId) {
            "ui_builder", "app_manager" -> {
                runCatching {
                    val payload = JsonParser.parseString(rawOutput).asJsonObject
                    val action = payload.stringOrNull("action").orEmpty().ifBlank { "result" }
                    workspaceStore.writeJson(workspaceId, "${skillId}_${action}", payload)
                    workspaceStore.recordEvent(
                        workspaceId,
                        WorkspaceEvent(
                            category = "artifact_observation",
                            source = skillId,
                            title = action,
                            summary = rawOutput.take(240),
                            payload = rawOutput.take(4000),
                        ),
                    )
                    val artifactId = payload.stringOrNull("id").orEmpty()
                    val artifactTitle = payload.stringOrNull("title").orEmpty()
                    val artifactType = payload.stringOrNull("artifact_type").orEmpty()
                    if (artifactId.isNotBlank() && artifactType.isNotBlank()) {
                        workspaceStore.linkArtifact(workspaceId, artifactType, artifactId, artifactTitle)
                        workspaceStore.writeArtifactState(
                            workspaceId,
                            WorkspaceArtifactState(
                                artifactType = artifactType,
                                artifactId = artifactId,
                                title = artifactTitle,
                                action = action,
                                goal = payload.stringOrNull("goal").orEmpty(),
                                currentFeatures = payload.stringListOrEmpty("current_features"),
                                requiredFeatures = payload.stringListOrEmpty("required_features"),
                                lastDiffSummary = payload.stringOrNull("last_diff_summary").orEmpty(),
                                raw = rawOutput.take(4000),
                            ),
                        )
                    }
                }
            }

            "create_file", "read_file", "list_files", "generate_document" -> {
                workspaceStore.recordEvent(
                    workspaceId,
                    WorkspaceEvent(
                        category = "file_observation",
                        source = skillId,
                        title = skillId,
                        summary = rawOutput.take(240),
                        payload = rawOutput.take(4000),
                    ),
                )
            }

            "shell", "run_python", "pip_install" -> {
                workspaceStore.recordEvent(
                    workspaceId,
                    WorkspaceEvent(
                        category = "code_observation",
                        source = skillId,
                        title = skillId,
                        summary = rawOutput.take(240),
                        payload = rawOutput.take(4000),
                    ),
                )
            }
        }
    }

    suspend fun persistRuntimeUpdate(
        sessionId: String,
        goal: String,
        update: AgentWorkspaceUpdate,
    ) {
        val workspaceId = resolveWorkspaceId(sessionId) ?: return
        memoryWriter.updateTaskState(
            scopeId = workspaceId,
            state = workspaceStateForStage(update.stage, update.success),
            detail = update.summary,
        )
        workspaceStore.writeCheckpoint(
            workspaceId,
            WorkspaceCheckpoint(
                label = update.label,
                taskType = update.taskType,
                summary = update.summary.take(300),
                details = update.details.take(4000),
            ),
        )
        workspaceStore.recordEvent(
            workspaceId,
            WorkspaceEvent(
                category = update.stage,
                source = update.skillId ?: "agent_runtime",
                title = update.label,
                summary = update.summary.take(300),
                payload = buildString {
                    appendLine("taskType=${update.taskType}")
                    update.skillId?.let { appendLine("skillId=$it") }
                    update.success?.let { appendLine("success=$it") }
                    appendLine("goal=${goal.take(1200)}")
                    if (update.details.isNotBlank()) {
                        appendLine()
                        append(update.details.take(4000))
                    }
                }.trim(),
            ),
        )
    }

    private fun workspaceStateForStage(stage: String, success: Boolean?): String = when (stage) {
        "task_started", "direct_chat_started" -> "collecting"
        "plan_created" -> "planned"
        "skill_observation", "reflection", "review_completed", "continuation_checkpoint" -> "executing"
        "validation_repair" -> "repairing"
        "task_error" -> "blocked"
        "task_completed", "direct_chat_completed" -> if (success == false) "blocked" else "completed"
        else -> "executing"
    }
}
