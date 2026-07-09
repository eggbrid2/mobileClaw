package com.mobileclaw.ui.group

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mobileclaw.agent.GameEvent
import com.mobileclaw.agent.GameEventType
import com.mobileclaw.agent.GameProfile
import com.mobileclaw.agent.GameUserRole
import com.mobileclaw.agent.Group
import com.mobileclaw.memory.db.GroupGameEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val GROUP_GAME_TIMELINE_LIMIT = 360

internal fun GroupGameEventEntity.toGameEvent(): GameEvent {
    val eventType = type
        .trim()
        .uppercase(Locale.ROOT)
        .let { runCatching { GameEventType.valueOf(it) }.getOrNull() }
        ?: GameEventType.RESULT_PUBLISHED
    return GameEvent(
        id = id,
        type = eventType,
        roundIndex = roundIndex.coerceAtLeast(1),
        phaseId = phaseId,
        actorSeatId = actorSeatId,
        actorRoleId = actorRoleId,
        actorName = actorName,
        abilityId = abilityId,
        targetSeatIds = targetSeatIdsJson.decodeTimelineStringList(),
        targetActorIds = targetActorIdsJson.decodeTimelineStringList(),
        targetName = targetName,
        actionRecordId = actionRecordId,
        channelId = channelId,
        visibility = visibility.ifBlank { GROUP_VISIBILITY_PUBLIC },
        text = text,
        resultText = resultText,
        metadataJson = metadataJson.ifBlank { "{}" },
        createdAt = createdAt,
    )
}

internal fun List<GameEvent>.toVisibleGameTimelineItems(
    group: Group,
    isZh: Boolean,
): List<GroupGameTimelineItem> =
    filter { event -> group.canUserSeeGameEvent(event) }
        .distinctBy { it.id }
        .sortedWith(compareBy<GameEvent> { it.createdAt }.thenBy { it.id })
        .takeLast(GROUP_GAME_TIMELINE_LIMIT)
        .map { event -> event.toGameTimelineItem(group, isZh) }

private fun GameEvent.toGameTimelineItem(
    group: Group,
    isZh: Boolean,
): GroupGameTimelineItem {
    val profile = group.gameProfile
    val phaseName = profile?.phaseLabel(phaseId).orEmpty()
    val channelLabel = profile.eventChannelLabel(channelId, visibility, isZh)
    val meta = listOf(
        if (isZh) "第 ${roundIndex.coerceAtLeast(1)} 轮" else "Round ${roundIndex.coerceAtLeast(1)}",
        phaseName,
        visibility.timelineVisibilityLabel(isZh),
        timelineTime(createdAt),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    return GroupGameTimelineItem(
        id = id.ifBlank { "${type.name}_${createdAt}_${actionRecordId}_${phaseId}" },
        type = type,
        title = timelineTitle(group, isZh),
        body = timelineBody(group, isZh),
        meta = meta,
        channelLabel = channelLabel,
        visibility = visibility.ifBlank { GROUP_VISIBILITY_PUBLIC },
        createdAt = createdAt,
    )
}

private fun Group.canUserSeeGameEvent(event: GameEvent): Boolean {
    if (!isGameGroup(this)) return true
    if (event.isPublicTimelineEvent()) return true
    val profile = gameProfile ?: return false
    return when (profile.userRole) {
        GameUserRole.HOST,
        GameUserRole.CO_HOST,
        -> true
        GameUserRole.SPECTATOR -> false
        GameUserRole.PLAYER -> {
            val synthetic = GroupMessage(
                groupId = id,
                senderId = event.actorRoleId,
                senderName = event.actorName,
                senderAvatar = "",
                text = event.text.ifBlank { event.resultText },
                channelId = event.channelId.ifBlank { event.visibility.defaultTimelineChannel() },
                visibility = event.visibility.ifBlank { GROUP_VISIBILITY_PUBLIC },
                createdAt = event.createdAt,
            )
            canUserSeeGroupMessage(this, synthetic)
        }
    }
}

private fun GameEvent.isPublicTimelineEvent(): Boolean =
    visibility.equals(GROUP_VISIBILITY_PUBLIC, ignoreCase = true) ||
        visibility.isBlank() ||
        channelId.isBlank() ||
        channelId.equals(GROUP_CHANNEL_PUBLIC, ignoreCase = true)

private fun GameEvent.timelineTitle(group: Group, isZh: Boolean): String {
    val profile = group.gameProfile
    val actor = actorName
        .ifBlank { profile?.seatLabel(actorSeatId).orEmpty() }
        .ifBlank { actorRoleId }
        .ifBlank { if (isZh) "玩家" else "Player" }
    val abilityName = profile?.abilities?.firstOrNull { it.id == abilityId }?.name
        ?: abilityId
    val phaseName = profile?.phaseLabel(phaseId).orEmpty()
    return when (type) {
        GameEventType.PHASE_ADVANCED -> {
            val phase = phaseName.ifBlank { if (isZh) "下一阶段" else "Next phase" }
            if (isZh) "进入 $phase" else "Entered $phase"
        }
        GameEventType.ACTION_SUBMITTED -> {
            if (abilityName.isNotBlank()) {
                if (isZh) "$actor 提交 $abilityName" else "$actor submitted $abilityName"
            } else if (isZh) "$actor 提交行动" else "$actor submitted an action"
        }
        GameEventType.VOTE_SUBMITTED -> if (isZh) "$actor 完成投票" else "$actor voted"
        GameEventType.ACTION_RESOLVED -> {
            if (abilityName.isNotBlank()) {
                if (isZh) "$abilityName 已处理" else "$abilityName resolved"
            } else if (isZh) "行动已处理" else "Action resolved"
        }
        GameEventType.RESULT_PUBLISHED -> if (isZh) "公布结果" else "Result published"
        GameEventType.CONTROL_CHANGED -> if (isZh) "流程控制更新" else "Flow control updated"
        GameEventType.SPEAKER_CALLED -> {
            val target = targetName.ifBlank { profile?.seatLabel(targetSeatIds.firstOrNull().orEmpty()).orEmpty() }
            if (target.isNotBlank()) {
                if (isZh) "点名 $target" else "Called $target"
            } else if (isZh) "点名发言" else "Speaker called"
        }
    }
}

private fun GameEvent.timelineBody(group: Group, isZh: Boolean): String {
    val directText = text.ifBlank { resultText }.trim()
    if (directText.isNotBlank()) return directText
    val profile = group.gameProfile
    val abilityName = profile?.abilities?.firstOrNull { it.id == abilityId }?.name
        ?: abilityId
    val targets = (targetSeatIds.mapNotNull { profile?.seatLabel(it)?.ifBlank { null } } +
        targetActorIds +
        listOf(targetName))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return listOf(
        abilityName.takeIf { it.isNotBlank() }?.let { if (isZh) "能力：$it" else "Ability: $it" },
        targets.takeIf { it.isNotEmpty() }?.joinToString("、")?.let {
            if (isZh) "目标：$it" else "Target: $it"
        },
    ).filterNotNull().joinToString(" · ")
}

private fun GameProfile?.eventChannelLabel(
    channelId: String,
    visibility: String,
    isZh: Boolean,
): String {
    val cleanChannel = channelId.trim()
    if (cleanChannel.isBlank() || cleanChannel == GROUP_CHANNEL_PUBLIC) return ""
    val channel = this?.channels?.firstOrNull { it.id == cleanChannel }
    if (channel != null) return channel.name.ifBlank { cleanChannel }
    return when (visibility.lowercase(Locale.ROOT)) {
        GROUP_VISIBILITY_TEAM -> if (isZh) "团队" else "Team"
        GROUP_VISIBILITY_PRIVATE -> if (isZh) "私密" else "Private"
        GROUP_VISIBILITY_JUDGE -> if (isZh) "法官" else "Host"
        else -> cleanChannel
    }
}

private fun GameProfile.phaseLabel(phaseId: String): String =
    phases.firstOrNull { it.id == phaseId }?.name.orEmpty()

private fun GameProfile.seatLabel(seatId: String): String =
    seats.firstOrNull { it.seatId == seatId }?.displayName.orEmpty()

private fun String.timelineVisibilityLabel(isZh: Boolean): String =
    when (lowercase(Locale.ROOT)) {
        GROUP_VISIBILITY_TEAM -> if (isZh) "团队可见" else "Team"
        GROUP_VISIBILITY_PRIVATE -> if (isZh) "私密" else "Private"
        GROUP_VISIBILITY_JUDGE -> if (isZh) "法官" else "Host"
        else -> if (isZh) "公开" else "Public"
    }

private fun String.defaultTimelineChannel(): String =
    when (lowercase(Locale.ROOT)) {
        GROUP_VISIBILITY_TEAM -> GROUP_VISIBILITY_TEAM
        GROUP_VISIBILITY_PRIVATE -> GROUP_VISIBILITY_PRIVATE
        GROUP_VISIBILITY_JUDGE -> GROUP_CHANNEL_JUDGE
        else -> GROUP_CHANNEL_PUBLIC
    }

private fun timelineTime(createdAt: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(createdAt))

private fun String.decodeTimelineStringList(): List<String> =
    runCatching {
        JsonParser.parseString(ifBlank { "[]" })
            .takeIf { it.isJsonArray }
            ?.asJsonArray
            ?.mapNotNull { it.asCleanString() }
            .orEmpty()
    }.getOrDefault(emptyList())

private fun JsonElement.asCleanString(): String? =
    runCatching { asString.trim().takeIf { it.isNotBlank() } }.getOrNull()
