package com.mobileclaw.ui

import com.mobileclaw.agent.TaskType

data class ChannelPermissionDecision(
    val requiresConfirmation: Boolean,
    val requiresAccessibility: Boolean,
    val reason: String,
)

object ChannelPermissionPolicy {
    fun evaluate(route: TaskRoute, accessibilityEnabled: Boolean): ChannelPermissionDecision {
        val primary = route.contextualIntent.aiPrimaryChannel ?: route.taskType.toPrimaryChannel()
        val requiresAccessibility = primary == ChannelType.PHONE && !accessibilityEnabled
        val requiresConfirmation = primary in setOf(ChannelType.PHONE, ChannelType.VPN)
        val reason = when {
            requiresAccessibility -> "Phone channel requires accessibility before execution."
            primary == ChannelType.PHONE -> "Phone channel can operate the device UI."
            primary == ChannelType.VPN -> "VPN channel can change proxy/VPN state."
            else -> "No user confirmation required for this channel."
        }
        return ChannelPermissionDecision(
            requiresConfirmation = requiresConfirmation,
            requiresAccessibility = requiresAccessibility,
            reason = reason,
        )
    }

    private fun TaskType.toPrimaryChannel(): ChannelType = when (this) {
        TaskType.PHONE_CONTROL -> ChannelType.PHONE
        TaskType.WEB_RESEARCH -> ChannelType.WEB
        TaskType.FILE_CREATE, TaskType.APP_BUILD -> ChannelType.ARTIFACT
        TaskType.IMAGE_GENERATION -> ChannelType.MEDIA
        TaskType.VPN_CONTROL -> ChannelType.VPN
        TaskType.SKILL_MANAGEMENT -> ChannelType.SKILL
        TaskType.CODE_EXECUTION -> ChannelType.CODE
        TaskType.CHAT, TaskType.GENERAL -> ChannelType.CHAT
    }
}
