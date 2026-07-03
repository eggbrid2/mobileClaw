package com.mobileclaw.ui.chat.runtime

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleWorkspaceSnapshot
import com.mobileclaw.agent.RoleWorkspaceStore
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.skill.SkillMeta

data class RoleRuntimeProfile(
    val role: Role,
    val workspace: RoleWorkspaceSnapshot,
    val protocol: RoleExecutionProtocol,
    val skills: List<SkillMeta>,
    val workspacePrompt: String,
    val compiledPrompt: String,
) {
    val roleId: String get() = role.id
    val workspaceRootPath: String get() = workspace.rootPath
}

class CurrentRoleRuntimeAdapter(
    private val roleWorkspaceStore: RoleWorkspaceStore,
) {
    fun adapt(
        role: Role,
        skills: List<SkillMeta>,
        config: ConfigSnapshot,
        source: String,
    ): RoleRuntimeProfile {
        roleWorkspaceStore.recordModelConfig(role, config, source = source)
        val snapshot = roleWorkspaceStore.snapshot(role, skills)
        val protocol = RoleExecutionProtocolParser.parse(role.id, snapshot.chatProtocol)
        val workspacePrompt = roleWorkspaceStore.promptBlock(snapshot, skills, includeChatProtocol = false)
        val compiledPrompt = buildString {
            appendLine("## Compiled Role Protocol")
            appendLine(protocol.toPromptSummary())
            appendLine()
            appendLine(workspacePrompt)
        }.trim()
        return RoleRuntimeProfile(
            role = role,
            workspace = snapshot,
            protocol = protocol,
            skills = skills,
            workspacePrompt = workspacePrompt,
            compiledPrompt = compiledPrompt,
        )
    }
}

class RoleChatRuntimeBridge(
    private val roleWorkspaceStore: RoleWorkspaceStore,
) {
    private val currentRoleAdapter = CurrentRoleRuntimeAdapter(roleWorkspaceStore)

    fun adaptCurrentRole(
        role: Role,
        skills: List<SkillMeta>,
        config: ConfigSnapshot,
        source: String,
    ): RoleRuntimeProfile =
        currentRoleAdapter.adapt(
            role = role,
            skills = skills,
            config = config,
            source = source,
        )

    fun buildControlPlan(profile: RoleRuntimeProfile): RoleChatControlPlan =
        RoleChatControlPlanCompiler.compile(profile)

    fun buildPromptContext(plan: RoleChatControlPlan): String {
        val roleId = plan.roleProfile.roleId
        val fileBudget = (plan.contextPolicy.maxRoleContextChars / plan.contextPolicy.readRoleFiles.size.coerceAtLeast(1))
            .coerceIn(600, 1800)
        val selectedFiles = plan.contextPolicy.readRoleFiles
            .mapNotNull { fileName ->
                val content = roleWorkspaceStore.read(roleId, fileName).orEmpty().trim()
                if (content.isBlank()) null else "### $fileName\n${content.take(fileBudget)}"
            }
            .joinToString("\n\n")
        return buildString {
            appendLine("## Compiled Role Runtime Context")
            appendLine(plan.roleProfile.protocol.toPromptSummary())
            if (selectedFiles.isNotBlank()) {
                appendLine()
                appendLine("## Selected Role Workspace Files")
                appendLine(selectedFiles)
            }
            appendLine()
            appendLine(plan.toPromptBlock(maxChars = 1800))
        }.trim().take(plan.contextPolicy.maxRoleContextChars + 1800)
    }

    fun buildDirectChatPromptContext(plan: RoleChatControlPlan): String {
        val profile = plan.roleProfile
        val core = roleWorkspaceStore.read(profile.roleId, "core.md").orEmpty()
        val memory = roleWorkspaceStore.read(profile.roleId, "memory.md").orEmpty()
        val protocol = profile.protocol
        return buildString {
            appendLine("## Active Role Runtime")
            appendLine("- Role id: ${profile.roleId}")
            appendLine("- Role name: ${profile.role.name}")
            appendLine("- Short follow-up mode: ${plan.intentPolicy.shortFollowUpMode}")
            appendLine("- Current message priority: ${plan.intentPolicy.currentMessagePriority}")
            appendLine("- Artifact reference mode: ${plan.intentPolicy.artifactReferenceMode}")
            appendLine("- Response style: ${plan.responsePolicy.style}")
            appendLine("- Completion summary: ${plan.responsePolicy.completionSummaryMode}")
            appendLine("- Avoid capability listing: ${plan.responsePolicy.avoidCapabilityListing}")
            appendLine("- Allow UI blocks: ${plan.responsePolicy.allowUiBlocks}")
            extractSection(core, "## 工作方法")
                .ifBlank { profile.role.systemPromptAddendum }
                .take(700)
                .takeIf { it.isNotBlank() }
                ?.let {
                    appendLine()
                    appendLine("### Working Style")
                    appendLine(it)
                }
            listOf(protocol.inputUnderstanding, protocol.responsePolicy)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .take(700)
                .takeIf { it.isNotBlank() }
                ?.let {
                    appendLine()
                    appendLine("### Chat Rules")
                    appendLine(it)
                }
            extractSection(memory, "## 稳定偏好")
                .take(500)
                .takeIf { it.isNotBlank() }
                ?.let {
                    appendLine()
                    appendLine("### Stable Role Memory")
                    appendLine(it)
                }
            appendLine()
            appendLine("Do not list capabilities unless the user explicitly asks. If the latest message is an execution request, the runtime should route to tools instead of answering with a capability menu.")
            if (!plan.responsePolicy.allowUiBlocks) {
                appendLine("Do not output UI blocks unless a higher-priority system instruction explicitly requires one.")
            }
            if (plan.intentPolicy.currentMessagePriority) {
                appendLine("The latest user message is the source of truth; use older context only to resolve references.")
            }
        }.trim().take(2200)
    }

    fun buildRoleWorkspaceContext(
        role: Role,
        skills: List<SkillMeta>,
        config: ConfigSnapshot,
        source: String,
    ): String =
        adaptCurrentRole(
            role = role,
            skills = skills,
            config = config,
            source = source,
        ).compiledPrompt

    private fun extractSection(markdown: String, heading: String): String {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.trim() == heading }
        if (start < 0) return ""
        return lines.drop(start + 1)
            .takeWhile { !it.startsWith("## ") }
            .joinToString("\n")
            .trim()
    }
}
