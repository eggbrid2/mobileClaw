package com.mobileclaw.ui.group

import com.mobileclaw.agent.GameActorType
import com.mobileclaw.agent.GameChannel
import com.mobileclaw.agent.GameChannelKind
import com.mobileclaw.agent.GameProfile
import com.mobileclaw.agent.GameSeat
import com.mobileclaw.agent.GameSeatStatus
import com.mobileclaw.agent.GameUserRole
import com.mobileclaw.agent.Group
import com.mobileclaw.agent.GroupKind
import com.mobileclaw.agent.Role

internal const val GROUP_CHANNEL_PUBLIC = "public"
internal const val GROUP_CHANNEL_JUDGE = "judge"
internal const val GROUP_VISIBILITY_PUBLIC = "public"
internal const val GROUP_VISIBILITY_TEAM = "team"
internal const val GROUP_VISIBILITY_PRIVATE = "private"
internal const val GROUP_VISIBILITY_JUDGE = "judge"
internal const val GROUP_CHANNEL_FILTER_ALL = "__all"

internal data class GroupChannelFilter(
    val id: String,
    val label: String,
    val count: Int,
)

internal data class GroupMessageChannelTarget(
    val channelId: String = GROUP_CHANNEL_PUBLIC,
    val visibility: String = GROUP_VISIBILITY_PUBLIC,
)

internal fun visibleGroupMessagesForUser(group: Group, messages: List<GroupMessage>): List<GroupMessage> =
    messages.filter { canUserSeeGroupMessage(group, it) }

internal fun visibleGameChannelFiltersForUser(
    group: Group,
    messages: List<GroupMessage>,
    isZh: Boolean,
): List<GroupChannelFilter> {
    if (!isGameGroup(group)) return emptyList()
    val profile = group.gameProfile ?: return emptyList()
    val visibleMessages = visibleGroupMessagesForUser(group, messages)
    val rawChannels = linkedMapOf<String, String>()
    rawChannels[GROUP_CHANNEL_PUBLIC] = if (isZh) "公开" else "Public"
    profile.channels
        .filter { profile.canUserAccessChannel(it) }
        .forEach { rawChannels[it.id] = it.name.ifBlank { fallbackGameChannelLabel(it.id, it.kind, isZh) } }
    visibleMessages.forEach { message ->
        val channelId = message.channelId.ifBlank { GROUP_CHANNEL_PUBLIC }
        if (channelId != GROUP_CHANNEL_PUBLIC && channelId !in rawChannels) {
            rawChannels[channelId] = profile.messageChannelLabel(channelId, message.visibility, isZh)
        }
    }

    val channelFilters = rawChannels.map { (channelId, label) ->
        GroupChannelFilter(
            id = channelId,
            label = label,
            count = visibleMessages.count { it.matchesGameChannelFilter(channelId) },
        )
    }.filter { it.id == GROUP_CHANNEL_PUBLIC || it.count > 0 || profile.channels.any { channel -> channel.id == it.id } }

    val hasSelectableChannel = channelFilters.any { it.id != GROUP_CHANNEL_PUBLIC }
    if (!hasSelectableChannel) return emptyList()
    return listOf(
        GroupChannelFilter(
            id = GROUP_CHANNEL_FILTER_ALL,
            label = if (isZh) "全部" else "All",
            count = visibleMessages.size,
        ),
    ) + channelFilters
}

internal fun visibleGroupMessagesForUserChannel(
    group: Group,
    messages: List<GroupMessage>,
    channelFilterId: String,
): List<GroupMessage> {
    val visibleMessages = visibleGroupMessagesForUser(group, messages)
    if (channelFilterId == GROUP_CHANNEL_FILTER_ALL || channelFilterId.isBlank()) return visibleMessages
    return visibleMessages.filter { it.matchesGameChannelFilter(channelFilterId) }
}

internal fun messageChannelTargetForUser(
    group: Group,
    channelFilterId: String,
): GroupMessageChannelTarget {
    if (!isGameGroup(group) || channelFilterId.isBlank() || channelFilterId == GROUP_CHANNEL_FILTER_ALL) {
        return GroupMessageChannelTarget()
    }
    val profile = group.gameProfile ?: return GroupMessageChannelTarget()
    val channel = profile.channels.firstOrNull { it.id == channelFilterId }
    if (channel != null) {
        if (!profile.canUserAccessChannel(channel)) return GroupMessageChannelTarget()
        return GroupMessageChannelTarget(
            channelId = channel.id,
            visibility = channel.kind.toGroupVisibility(),
        )
    }
    if (!profile.canUserAccessAdHocChannel(channelFilterId)) return GroupMessageChannelTarget()
    return GroupMessageChannelTarget(
        channelId = channelFilterId,
        visibility = profile.fallbackVisibilityForChannel(channelFilterId),
    )
}

internal fun visibleGroupMessagesForRole(group: Group, role: Role, messages: List<GroupMessage>): List<GroupMessage> =
    messages.filter { canRoleSeeGroupMessage(group, role, it) }

internal fun canUserSeeGroupMessage(group: Group, message: GroupMessage): Boolean {
    if (message.isPublicGameMessage()) return true
    val profile = group.gameProfile ?: return false
    return when (profile.userRole) {
        GameUserRole.HOST,
        GameUserRole.CO_HOST,
        -> true
        GameUserRole.SPECTATOR -> false
        GameUserRole.PLAYER -> {
            val seat = profile.seats.firstOrNull { it.actorType == GameActorType.USER && it.actorId == "user" }
                ?: return false
            profile.canSeatSee(message, seat)
        }
    }
}

internal fun canRoleSeeGroupMessage(group: Group, role: Role, message: GroupMessage): Boolean {
    if (message.isPublicGameMessage()) return true
    if (group.kind != GroupKind.GAME && group.gameProfile == null) return false
    val profile = group.gameProfile ?: return false
    if (group.judgeRoleId.isNotBlank() && role.id == group.judgeRoleId) return true
    val seat = profile.seats.firstOrNull { it.actorType == GameActorType.AI_ROLE && it.actorId == role.id }
        ?: return false
    return profile.canSeatSee(message, seat)
}

internal fun groupChannelLabel(group: Group, message: GroupMessage, isZh: Boolean): String {
    if (message.channelId.isBlank() || message.channelId == GROUP_CHANNEL_PUBLIC) return ""
    val profile = group.gameProfile
    val channelName = profile?.channels?.firstOrNull { it.id == message.channelId }?.name.orEmpty()
    if (channelName.isNotBlank()) return channelName
    return when (message.visibility.lowercase()) {
        GROUP_VISIBILITY_TEAM -> if (isZh) "团队" else "Team"
        GROUP_VISIBILITY_PRIVATE -> if (isZh) "私密" else "Private"
        GROUP_VISIBILITY_JUDGE -> if (isZh) "法官" else "Judge"
        else -> message.channelId
    }
}

internal fun isGameGroup(group: Group): Boolean =
    group.kind == GroupKind.GAME || group.gameProfile != null

private fun GroupMessage.isPublicGameMessage(): Boolean =
    visibility.equals(GROUP_VISIBILITY_PUBLIC, ignoreCase = true) ||
        visibility.isBlank() ||
        channelId.isBlank() ||
        channelId == GROUP_CHANNEL_PUBLIC

private fun GroupMessage.matchesGameChannelFilter(channelFilterId: String): Boolean =
    when (channelFilterId) {
        GROUP_CHANNEL_FILTER_ALL -> true
        GROUP_CHANNEL_PUBLIC -> isPublicGameMessage()
        else -> channelId == channelFilterId
    }

private fun GameProfile.canUserAccessChannel(channel: GameChannel): Boolean =
    when (userRole) {
        GameUserRole.HOST,
        GameUserRole.CO_HOST,
        -> true
        GameUserRole.SPECTATOR -> channel.kind == GameChannelKind.PUBLIC
        GameUserRole.PLAYER -> {
            val seat = seats.firstOrNull { it.actorType == GameActorType.USER && it.actorId == "user" } ?: return false
            canSeatAccessChannel(channel, seat)
        }
    }

private fun GameProfile.canSeatAccessChannel(channel: GameChannel, seat: GameSeat): Boolean =
    when (channel.kind) {
        GameChannelKind.PUBLIC -> true
        GameChannelKind.TEAM -> seat.seatId in channel.memberSeatIds ||
            (channel.memberSeatIds.isEmpty() && seat.teamId.isNotBlank() && channel.id.contains(seat.teamId, ignoreCase = true))
        GameChannelKind.PRIVATE -> seat.seatId in channel.memberSeatIds
        GameChannelKind.DEAD -> seat.status == GameSeatStatus.OUT || seat.status == GameSeatStatus.SPECTATING
        GameChannelKind.JUDGE -> false
    }

private fun GameProfile.canUserAccessAdHocChannel(channelId: String): Boolean =
    when (userRole) {
        GameUserRole.HOST,
        GameUserRole.CO_HOST,
        -> channelId.isNotBlank()
        GameUserRole.SPECTATOR -> false
        GameUserRole.PLAYER -> {
            val seat = seats.firstOrNull { it.actorType == GameActorType.USER && it.actorId == "user" } ?: return false
            val isOwnPrivateChannel = channelId.startsWith("private_", ignoreCase = true) &&
                (channelId.contains(seat.seatId, ignoreCase = true) || channelId.contains(seat.actorId, ignoreCase = true))
            val isOwnTeamChannel = seat.teamId.isNotBlank() && channelId.contains(seat.teamId, ignoreCase = true)
            isOwnPrivateChannel || isOwnTeamChannel
        }
    }

private fun GameProfile.messageChannelLabel(channelId: String, visibility: String, isZh: Boolean): String {
    val channel = channels.firstOrNull { it.id == channelId }
    if (channel != null) return channel.name.ifBlank { fallbackGameChannelLabel(channelId, channel.kind, isZh) }
    if (channelId.startsWith("private_")) {
        val seatId = channelId.removePrefix("private_")
        val seatName = seats.firstOrNull { it.seatId == seatId }?.displayName.orEmpty()
        return when {
            userRole in setOf(GameUserRole.HOST, GameUserRole.CO_HOST) && seatName.isNotBlank() ->
                if (isZh) "私密 $seatName" else "Private $seatName"
            else -> if (isZh) "私密" else "Private"
        }
    }
    return when (visibility.lowercase()) {
        GROUP_VISIBILITY_TEAM -> if (isZh) "团队" else "Team"
        GROUP_VISIBILITY_PRIVATE -> if (isZh) "私密" else "Private"
        GROUP_VISIBILITY_JUDGE -> if (isZh) "法官" else "Judge"
        else -> channelId
    }
}

private fun fallbackGameChannelLabel(channelId: String, kind: GameChannelKind, isZh: Boolean): String =
    if (channelId.isBlank()) {
        channelId
    } else when (kind) {
        GameChannelKind.PUBLIC -> if (isZh) "公开" else "Public"
        GameChannelKind.TEAM -> if (isZh) "团队" else "Team"
        GameChannelKind.PRIVATE -> if (isZh) "私密" else "Private"
        GameChannelKind.DEAD -> if (isZh) "出局" else "Out"
        GameChannelKind.JUDGE -> if (isZh) "法官" else "Judge"
    }

private fun GameChannelKind.toGroupVisibility(): String =
    when (this) {
        GameChannelKind.PUBLIC -> GROUP_VISIBILITY_PUBLIC
        GameChannelKind.TEAM -> GROUP_VISIBILITY_TEAM
        GameChannelKind.PRIVATE,
        GameChannelKind.DEAD,
        -> GROUP_VISIBILITY_PRIVATE
        GameChannelKind.JUDGE -> GROUP_VISIBILITY_JUDGE
    }

private fun GameProfile.fallbackVisibilityForChannel(channelId: String): String =
    when {
        channelId.equals(GROUP_CHANNEL_PUBLIC, ignoreCase = true) -> GROUP_VISIBILITY_PUBLIC
        channelId.equals(GROUP_CHANNEL_JUDGE, ignoreCase = true) -> GROUP_VISIBILITY_JUDGE
        channelId.startsWith("private_", ignoreCase = true) -> GROUP_VISIBILITY_PRIVATE
        userGameSeat()?.teamId?.takeIf { it.isNotBlank() }?.let { teamId ->
            channelId.contains(teamId, ignoreCase = true)
        } == true -> GROUP_VISIBILITY_TEAM
        else -> GROUP_VISIBILITY_PRIVATE
    }

private fun GameProfile.userGameSeat(): GameSeat? =
    seats.firstOrNull { it.actorType == GameActorType.USER && it.actorId == "user" }

private fun GameProfile.canSeatSee(message: GroupMessage, seat: GameSeat): Boolean {
    val visibility = message.visibility.lowercase()
    if (visibility == GROUP_VISIBILITY_JUDGE) return false
    val channelId = message.channelId.ifBlank { GROUP_CHANNEL_PUBLIC }
    val channel = channels.firstOrNull { it.id == channelId }
    if (channel == null) {
        return when (visibility) {
            GROUP_VISIBILITY_PUBLIC -> true
            GROUP_VISIBILITY_TEAM -> seat.teamId.isNotBlank() && channelId.contains(seat.teamId, ignoreCase = true)
            GROUP_VISIBILITY_PRIVATE -> channelId.contains(seat.seatId, ignoreCase = true) || channelId.contains(seat.actorId, ignoreCase = true)
            else -> false
        }
    }
    return when (channel.kind) {
        GameChannelKind.PUBLIC -> true
        GameChannelKind.TEAM -> seat.seatId in channel.memberSeatIds ||
            (channel.memberSeatIds.isEmpty() && seat.teamId.isNotBlank() && channel.id.contains(seat.teamId, ignoreCase = true))
        GameChannelKind.PRIVATE -> seat.seatId in channel.memberSeatIds
        GameChannelKind.DEAD -> seat.status == GameSeatStatus.OUT || seat.status == GameSeatStatus.SPECTATING
        GameChannelKind.JUDGE -> false
    }
}
