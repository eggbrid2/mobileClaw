package com.mobileclaw.llm

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.effectiveModelBinding
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.capabilityModel

data class ResolvedRoleModel(
    val gatewayId: String = "",
    val gatewayName: String = "",
    val model: String = "",
    val localModelId: String = "",
    val inheritedDefault: Boolean = true,
) {
    val callOptions: LlmCallOptions
        get() = when {
            localModelId.isNotBlank() -> LlmCallOptions(
                localModelId = localModelId.removePrefix("local:"),
                forceLocal = true,
            )
            gatewayId.isNotBlank() || model.isNotBlank() -> LlmCallOptions(
                gatewayId = gatewayId.takeIf { it.isNotBlank() },
                model = model.takeIf { it.isNotBlank() },
            )
            else -> LlmCallOptions()
        }
}

object RoleModelResolver {
    fun resolve(role: Role, snapshot: ConfigSnapshot): ResolvedRoleModel {
        val binding = role.effectiveModelBinding() ?: return snapshot.defaultResolvedRoleModel()
        if (binding.localModelId.isNotBlank()) {
            val localId = binding.localModelId.removePrefix("local:")
            return ResolvedRoleModel(
                model = "local:$localId",
                localModelId = localId,
                inheritedDefault = false,
            )
        }

        val gateway = snapshot.findGateway(binding.gatewayId, binding.gatewayName)
        val fallbackGateway = gateway ?: snapshot.activeGateway
        val model = binding.model
            .ifBlank { gateway?.chatModel().orEmpty() }
            .ifBlank { role.modelOverride.orEmpty().takeUnless { it.startsWith("local:") }.orEmpty() }
            .ifBlank { fallbackGateway?.chatModel().orEmpty() }

        return when {
            gateway != null -> ResolvedRoleModel(
                gatewayId = gateway.id,
                gatewayName = gateway.name,
                model = model,
                inheritedDefault = false,
            )
            model.isNotBlank() -> ResolvedRoleModel(
                gatewayId = fallbackGateway?.id.orEmpty(),
                gatewayName = fallbackGateway?.name.orEmpty(),
                model = model,
                inheritedDefault = false,
            )
            else -> snapshot.defaultResolvedRoleModel()
        }
    }

    private fun ConfigSnapshot.defaultResolvedRoleModel(): ResolvedRoleModel =
        ResolvedRoleModel(
            gatewayId = activeGateway?.id.orEmpty(),
            gatewayName = activeGateway?.name.orEmpty(),
            model = model,
            localModelId = if (localModelEnabled || localNativeOnly) localModelId else "",
            inheritedDefault = true,
        )

    private fun ConfigSnapshot.findGateway(gatewayId: String, gatewayName: String): GatewayConfig? =
        gateways.firstOrNull { gatewayId.isNotBlank() && it.id == gatewayId }
            ?: gateways.firstOrNull { gatewayName.isNotBlank() && it.name.equals(gatewayName, ignoreCase = true) }

    private fun GatewayConfig.chatModel(): String =
        capabilityModel("chat") ?: model
}
