package com.mobileclaw.ui.chat.runtime

import com.mobileclaw.agent.RoleWorkspaceStore
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.workspace.WorkspaceStore

class RoleStepReadOnlyHandlers(
    private val roleWorkspaceStore: RoleWorkspaceStore,
    private val semanticMemory: SemanticMemory,
    private val workspaceStore: WorkspaceStore,
    private val skillsProvider: () -> List<SkillMeta>,
) {
    fun toHandlers(): RoleStepActionHandlers =
        RoleStepActionHandlers(
            readRoleFile = ::readRoleFile,
            searchMemory = ::searchMemory,
            readWorkspace = ::readWorkspace,
            selectSkill = ::selectSkill,
        )

    private suspend fun readRoleFile(state: RoleRunState, decision: RoleStepDecision): RoleStepResult {
        val fileName = decision.targetPath
            .ifBlank { decision.query }
            .ifBlank { "core.md" }
            .sanitizeRelativePath()
            ?: return RoleStepResult(
                success = false,
                summary = "Rejected unsafe role file path.",
                errorMessage = "Unsafe role file path: ${decision.targetPath.ifBlank { decision.query }}",
            )
        val content = roleWorkspaceStore.read(state.input.role.id, fileName).orEmpty()
        if (content.isBlank()) {
            return RoleStepResult(
                success = false,
                summary = "Role file not found or empty: $fileName",
                errorMessage = "Role file not found or empty: $fileName",
            )
        }
        val block = "### role/$fileName\n${content.take(1800)}"
        return RoleStepResult(
            success = true,
            summary = "Read role file: $fileName",
            userSummary = "已读取角色文件：$fileName",
            content = content,
            workspaceDelta = block,
        )
    }

    private suspend fun searchMemory(state: RoleRunState, decision: RoleStepDecision): RoleStepResult {
        val query = decision.query.ifBlank { state.input.userGoal }.trim()
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }.take(8)
        val matches = semanticMemory.facts()
            .filter { fact ->
                val haystack = "${fact.key}\n${fact.value}\n${fact.type}\n${fact.scope}".lowercase()
                terms.isEmpty() || terms.any { it in haystack }
            }
            .sortedWith(compareByDescending<com.mobileclaw.memory.MemoryFact> { it.pinned }.thenByDescending { it.updatedAt })
            .take(8)
        if (matches.isNotEmpty()) {
            semanticMemory.markUsed(matches.map { it.key })
        }
        val memoryBlock = matches.joinToString("\n") { fact ->
            "- ${fact.key}: ${fact.value.take(300)}"
        }
        return RoleStepResult(
            success = true,
            summary = if (matches.isEmpty()) "No matching memory found." else "Found ${matches.size} memory facts.",
            userSummary = if (matches.isEmpty()) "没有找到相关长期记忆。" else "找到 ${matches.size} 条相关长期记忆。",
            content = memoryBlock,
            memoryDelta = memoryBlock,
        )
    }

    private suspend fun readWorkspace(state: RoleRunState, decision: RoleStepDecision): RoleStepResult {
        val workspaceId = state.input.workspaceId.orEmpty()
        if (workspaceId.isBlank()) {
            return RoleStepResult(
                success = false,
                summary = "No workspace is bound to this role run.",
                errorMessage = "No workspace id available.",
            )
        }
        val targetPath = decision.targetPath.ifBlank { decision.query }.trim()
        val content = if (targetPath.isBlank()) {
            listOf(
                workspaceStore.summarize(workspaceId),
                workspaceStore.latestCheckpointContent(workspaceId).orEmpty(),
            ).filter { it.isNotBlank() }.joinToString("\n\n")
        } else {
            val safePath = targetPath.sanitizeRelativePath()
                ?: return RoleStepResult(
                    success = false,
                    summary = "Rejected unsafe workspace path.",
                    errorMessage = "Unsafe workspace path: $targetPath",
                )
            workspaceStore.readFile(workspaceId, safePath).orEmpty()
        }
        if (content.isBlank()) {
            return RoleStepResult(
                success = false,
                summary = "Workspace context not found.",
                errorMessage = "Workspace content not found for path: ${targetPath.ifBlank { "(summary)" }}",
            )
        }
        val label = targetPath.ifBlank { "summary" }
        val block = "### workspace/$label\n${content.take(2200)}"
        return RoleStepResult(
            success = true,
            summary = "Read workspace context: $label",
            userSummary = "已读取工作空间上下文：$label",
            content = content,
            workspaceDelta = block,
        )
    }

    private suspend fun selectSkill(state: RoleRunState, decision: RoleStepDecision): RoleStepResult {
        val query = decision.query.ifBlank { state.input.userGoal }.lowercase()
        val terms = query.split(Regex("\\s+")).filter { it.length >= 2 }.take(8)
        val selected = skillsProvider()
            .filterNot { it.internalTool }
            .filter { skill ->
                val haystack = buildString {
                    appendLine(skill.id)
                    appendLine(skill.name)
                    appendLine(skill.nameZh.orEmpty())
                    appendLine(skill.description)
                    appendLine(skill.descriptionZh.orEmpty())
                    appendLine(skill.tags.joinToString(" "))
                    appendLine(skill.categories.joinToString(" ") { it.name })
                }.lowercase()
                terms.isEmpty() || terms.any { it in haystack }
            }
            .sortedBy { it.id }
            .take(12)
        val block = selected.joinToString("\n") { skill ->
            "- ${skill.id}: ${skill.name} - ${skill.description.take(220)}"
        }
        return RoleStepResult(
            success = true,
            summary = if (selected.isEmpty()) "No matching skills found." else "Selected ${selected.size} candidate skills.",
            userSummary = if (selected.isEmpty()) "没有找到匹配技能。" else "筛选出 ${selected.size} 个候选技能。",
            content = block,
            workspaceDelta = block,
            selectedToolIds = selected.map { it.id },
        )
    }

    private fun String.sanitizeRelativePath(): String? {
        val normalized = trim().replace('\\', '/')
        if (normalized.isBlank()) return ""
        if (normalized.startsWith("/") || normalized.contains("../") || normalized == "..") return null
        return normalized
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
            .takeIf { it.isNotBlank() }
    }
}
