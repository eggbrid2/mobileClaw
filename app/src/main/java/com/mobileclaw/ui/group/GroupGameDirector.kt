package com.mobileclaw.ui.group

import com.mobileclaw.agent.GameActorType
import com.mobileclaw.agent.GameAbilityEffect
import com.mobileclaw.agent.GameAbilityTrigger
import com.mobileclaw.agent.GameSeatStatus
import com.mobileclaw.agent.Group
import com.mobileclaw.agent.Role

/**
 * Game groups are phase-driven. Keep them out of the free-chat scheduler so
 * roles do not randomly pile onto hidden-role rounds.
 */
internal object GroupGameDirector {
    fun buildInitialTurns(
        group: Group,
        allMembers: List<Role>,
        triggerText: String,
        channelId: String,
        visibility: String,
    ): List<GroupTurnLaunch>? {
        if (!isGameGroup(group)) return null
        val profile = group.gameProfile ?: return emptyList()
        val candidates = group.gameRuntimeCandidateMembers(allMembers)
        if (candidates.isEmpty()) return emptyList()

        val calledSeat = group.currentCalledGameSeat()
        if (calledSeat != null) {
            return calledSeat.toLaunchRole(candidates)?.let { role ->
                listOf(
                    GameTurnLaunchSpec(
                        role = role,
                        triggerText = triggerText.ifBlank { "法官点名你发言或行动。" },
                        requireResponse = true,
                        channelId = channelId,
                        visibility = visibility,
                    ).toLaunch(),
                )
            }.orEmpty()
        }

        val mentioned = parseMentions(triggerText)
        val mentionedRoles = candidates.filter { role ->
            mentioned.any { mention ->
                role.name.contains(mention, ignoreCase = true) ||
                    mention.contains(role.name, ignoreCase = true)
            }
        }

        val judge = group.judgeRoleId
            .takeIf { it.isNotBlank() }
            ?.let { judgeId -> allMembers.firstOrNull { it.id == judgeId } }
        if (judge != null) {
            return listOf(
                GameTurnLaunchSpec(
                    role = judge,
                    triggerText = triggerText,
                    requireResponse = true,
                    channelId = GROUP_CHANNEL_PUBLIC,
                    visibility = GROUP_VISIBILITY_PUBLIC,
                ).toLaunch(),
            )
        }

        if (!group.isGameSpeechOpenForPlayers()) return emptyList()

        if (mentionedRoles.isNotEmpty()) {
            return mentionedRoles.map { role ->
                GameTurnLaunchSpec(
                    role = role,
                    triggerText = triggerText,
                    requireResponse = true,
                    channelId = channelId,
                    visibility = visibility,
                ).toLaunch()
            }
        }

        if (triggerText.isSystemSpeechCall()) {
            return profile.aiPlayerRolesInSeatOrder(candidates).mapIndexed { index, role ->
                GameTurnLaunchSpec(
                    role = role,
                    triggerText = triggerText,
                    delayMs = (index * 900L).coerceAtMost(5_000L),
                    requireResponse = true,
                    channelId = GROUP_CHANNEL_PUBLIC,
                    visibility = GROUP_VISIBILITY_PUBLIC,
                ).toLaunch()
            }
        }

        if (triggerText.isSystemVoteCall()) {
            return group.aiPlayerRolesWithVoteInSeatOrder(candidates).mapIndexed { index, role ->
                GameTurnLaunchSpec(
                    role = role,
                    triggerText = triggerText,
                    delayMs = (index * 700L).coerceAtMost(4_500L),
                    requireResponse = true,
                    channelId = GROUP_CHANNEL_PUBLIC,
                    visibility = GROUP_VISIBILITY_PUBLIC,
                ).toLaunch()
            }
        }

        if (triggerText.invitesGameTableResponse() && channelId == GROUP_CHANNEL_PUBLIC) {
            return profile.aiPlayerRolesInSeatOrder(candidates)
                .take(1)
                .map { role ->
                    GameTurnLaunchSpec(
                        role = role,
                        triggerText = triggerText,
                        requireResponse = true,
                        channelId = channelId,
                        visibility = visibility,
                    ).toLaunch()
                }
        }

        return emptyList()
    }

    fun shouldAllowOrganicContinuation(group: Group): Boolean =
        !isGameGroup(group)

    private data class GameTurnLaunchSpec(
        val role: Role,
        val delayMs: Long = 0L,
        val triggerText: String,
        val channelId: String,
        val visibility: String,
        val requireResponse: Boolean,
    ) {
        fun toLaunch(): GroupTurnLaunch =
            GroupTurnLaunch(
                role = role,
                delayMs = delayMs,
                chainDepth = 0,
                longTask = false,
                triggerText = triggerText,
                channelId = channelId,
                visibility = visibility,
                requireResponse = requireResponse,
            )
    }
}

private fun Group.gameRuntimeCandidateMembers(allMembers: List<Role>): List<Role> {
    val allowedIds = allowedRuntimeResponderRoleIds()
    val raw = if (allowedIds == null) allMembers else allMembers.filter { it.id in allowedIds }
    val profile = gameProfile ?: return raw
    val activeAiRoleIds = profile.seats
        .filter {
            it.actorType == GameActorType.AI_ROLE &&
                it.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)
        }
        .map { it.actorId }
        .toSet()
    return raw.filter { role -> role.id == judgeRoleId || role.id in activeAiRoleIds }
}

private fun com.mobileclaw.agent.GameSeat.toLaunchRole(candidates: List<Role>): Role? =
    if (actorType == GameActorType.AI_ROLE) candidates.firstOrNull { it.id == actorId } else null

private fun com.mobileclaw.agent.GameProfile.aiPlayerRolesInSeatOrder(candidates: List<Role>): List<Role> {
    val byId = candidates.associateBy { it.id }
    return seats
        .filter {
            it.actorType == GameActorType.AI_ROLE &&
                it.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)
        }
        .mapNotNull { byId[it.actorId] }
}

private fun Group.aiPlayerRolesWithVoteInSeatOrder(candidates: List<Role>): List<Role> {
    val profile = gameProfile ?: return emptyList()
    val byId = candidates.associateBy { it.id }
    return profile.seats
        .filter {
            it.actorType == GameActorType.AI_ROLE &&
                it.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)
        }
        .filter { seat ->
            availableGameAbilitiesForSeat(seat).any { ability ->
                ability.trigger == GameAbilityTrigger.VOTE || ability.effect == GameAbilityEffect.VOTE
            }
        }
        .mapNotNull { byId[it.actorId] }
}

private fun String.isSystemSpeechCall(): Boolean {
    val text = trim()
    if (!text.contains("系统法官")) return false
    return text.contains("首次发言") ||
        text.contains("发言开放") ||
        text.contains("公开信息发言") ||
        text.contains("Speech", ignoreCase = true)
}

private fun String.isSystemVoteCall(): Boolean {
    val text = trim()
    if (!text.contains("系统法官")) return false
    return text.contains("进入投票") ||
        text.contains("提交投票") ||
        text.contains("your vote", ignoreCase = true) ||
        text.contains("vote", ignoreCase = true)
}

private fun String.invitesGameTableResponse(): Boolean {
    val text = trim()
    if (text.isBlank()) return false
    return listOf(
        "所有人",
        "大家",
        "依次",
        "发言",
        "说说",
        "怎么看",
        "表态",
        "质询",
        "辩解",
        "all players",
    ).any { text.contains(it, ignoreCase = true) }
}
