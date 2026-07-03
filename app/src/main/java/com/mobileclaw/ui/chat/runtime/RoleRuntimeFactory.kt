package com.mobileclaw.ui.chat.runtime

import com.mobileclaw.agent.RoleWorkspaceStore
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.memory.SemanticMemory
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.workspace.WorkspaceStore

object RoleRuntimeFactory {
    fun createReadOnlyController(
        llm: LlmGateway,
        roleWorkspaceStore: RoleWorkspaceStore,
        semanticMemory: SemanticMemory,
        workspaceStore: WorkspaceStore,
        skillsProvider: () -> List<SkillMeta>,
        maxSteps: Int = 4,
    ): RoleRuntimeController {
        val handlers = RoleStepReadOnlyHandlers(
            roleWorkspaceStore = roleWorkspaceStore,
            semanticMemory = semanticMemory,
            workspaceStore = workspaceStore,
            skillsProvider = skillsProvider,
        ).toHandlers()
        return DefaultRoleRuntimeController(
            packetBuilder = DefaultRoleStepPacketBuilder(),
            decider = LlmRoleStepDecider(llm),
            executor = DelegatingRoleStepExecutor(handlers),
            maxSteps = maxSteps,
        )
    }
}
