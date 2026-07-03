package com.mobileclaw.agent

import com.mobileclaw.ui.TaskRoute

data class ChannelPermissionDecision(
    val requiresConfirmation: Boolean,
    val requiresAccessibility: Boolean,
    val reason: String,
)

object ChannelPermissionPolicy {
    fun evaluate(route: TaskRoute, accessibilityEnabled: Boolean): ChannelPermissionDecision {
        return ChannelPermissionDecision(
            requiresConfirmation = false,
            requiresAccessibility = false,
            reason = "Internal channel permission gates are disabled; runtime will execute the selected route directly.",
        )
    }
}
