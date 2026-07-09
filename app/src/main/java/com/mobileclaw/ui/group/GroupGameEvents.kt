package com.mobileclaw.ui.group

import com.mobileclaw.agent.GameAbilityEffect
import com.mobileclaw.agent.GameAbilityTrigger
import com.mobileclaw.agent.GameActionRecord
import com.mobileclaw.agent.GameActionStatus
import com.mobileclaw.agent.GameEvent
import com.mobileclaw.agent.GameEventType
import com.mobileclaw.agent.GamePhase
import com.mobileclaw.agent.GameProfile
import com.mobileclaw.agent.GameRuntimeState
import com.mobileclaw.agent.GameSeat
import com.mobileclaw.agent.Group
import java.util.Locale

internal const val GAME_RUNTIME_EVENT_LIMIT = 600

internal fun GameRuntimeState.withAppendedGameEvents(events: List<GameEvent>): GameRuntimeState {
    val cleanEvents = events.filter { it.id.isNotBlank() }
    if (cleanEvents.isEmpty()) return this
    return copy(events = (this.events + cleanEvents).takeLast(GAME_RUNTIME_EVENT_LIMIT))
}

internal fun Group.withAppendedGameEvents(
    events: List<GameEvent>,
    now: Long = System.currentTimeMillis(),
): Group {
    val cleanEvents = events.filter { it.id.isNotBlank() && it.type.name.isNotBlank() }
    if (cleanEvents.isEmpty()) return this
    val profile = gameProfile ?: return this
    val state = profile.runtimeState()
    return copy(
        gameProfile = profile.withRuntimeState(state.withAppendedGameEvents(cleanEvents)),
        updatedAt = now,
    )
}

internal fun GameActionRecord.toGameActionSubmittedEvent(
    profile: GameProfile?,
    now: Long = createdAt,
): GameEvent {
    val ability = profile?.abilities?.firstOrNull { it.id == draft.abilityId }
    val type = if (
        ability?.effect == GameAbilityEffect.VOTE ||
        ability?.trigger == GameAbilityTrigger.VOTE
    ) {
        GameEventType.VOTE_SUBMITTED
    } else {
        GameEventType.ACTION_SUBMITTED
    }
    val runtimeState = profile?.runtimeState()
    return GameEvent(
        id = gameEventId("submit", now, id),
        type = type,
        roundIndex = runtimeState?.roundIndex?.coerceAtLeast(1) ?: 1,
        phaseId = phaseId.ifBlank { profile?.currentPhaseId.orEmpty() },
        actorSeatId = draft.actorSeatId,
        actorRoleId = draft.actorRoleId,
        actorName = draft.actorName,
        abilityId = draft.abilityId,
        targetSeatIds = draft.targetSeatIds,
        targetActorIds = draft.targetActorIds,
        targetName = draft.targetName,
        actionRecordId = id,
        channelId = draft.channelId.ifBlank { visibilityToDefaultChannel(draft.visibility) },
        visibility = cleanGameVisibility(draft.visibility),
        resultText = draft.reason.ifBlank { draft.rawText },
        createdAt = now,
    )
}

internal fun GameActionRecord.toGameActionResolvedEvent(
    profile: GameProfile?,
    status: GameActionStatus,
    resultText: String,
    now: Long,
    index: Int,
): GameEvent {
    val runtimeState = profile?.runtimeState()
    return GameEvent(
        id = gameEventId("resolved", now, "$id-$index"),
        type = GameEventType.ACTION_RESOLVED,
        roundIndex = runtimeState?.roundIndex?.coerceAtLeast(1) ?: 1,
        phaseId = phaseId.ifBlank { profile?.currentPhaseId.orEmpty() },
        actorSeatId = draft.actorSeatId,
        actorRoleId = draft.actorRoleId,
        actorName = draft.actorName,
        abilityId = draft.abilityId,
        targetSeatIds = draft.targetSeatIds,
        targetActorIds = draft.targetActorIds,
        targetName = draft.targetName,
        actionRecordId = id,
        channelId = GROUP_CHANNEL_JUDGE,
        visibility = GROUP_VISIBILITY_JUDGE,
        resultText = resultText,
        metadataJson = """{"status":"${status.name}"}""",
        createdAt = now,
    )
}

internal fun gameRuntimeResultEvents(
    group: Group,
    publicText: String,
    resultMessages: List<GameRuntimeMessageDraft> = emptyList(),
    now: Long = System.currentTimeMillis(),
): List<GameEvent> {
    val profile = group.gameProfile
    val state = profile?.runtimeState()
    val phaseId = profile?.currentPhaseId.orEmpty()
    val roundIndex = state?.roundIndex?.coerceAtLeast(1) ?: 1
    val publicEvent = publicText
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let { text ->
            GameEvent(
                id = gameEventId("result", now, "public"),
                type = GameEventType.RESULT_PUBLISHED,
                roundIndex = roundIndex,
                phaseId = phaseId,
                channelId = GROUP_CHANNEL_PUBLIC,
                visibility = GROUP_VISIBILITY_PUBLIC,
                text = text,
                createdAt = now,
            )
        }
    val privateEvents = resultMessages
        .filter { it.text.isNotBlank() }
        .mapIndexed { index, draft ->
            GameEvent(
                id = gameEventId("result", now, "draft-$index"),
                type = GameEventType.RESULT_PUBLISHED,
                roundIndex = roundIndex,
                phaseId = phaseId,
                channelId = draft.channelId.ifBlank { visibilityToDefaultChannel(draft.visibility) },
                visibility = cleanGameVisibility(draft.visibility),
                text = draft.text.trim(),
                createdAt = now + index + 1,
            )
        }
    return listOfNotNull(publicEvent) + privateEvents
}

internal fun gamePhaseAdvancedEvent(
    group: Group,
    phase: GamePhase?,
    roundIndex: Int,
    text: String,
    now: Long = System.currentTimeMillis(),
): GameEvent =
    GameEvent(
        id = gameEventId("phase", now, phase?.id.orEmpty().ifBlank { roundIndex.toString() }),
        type = GameEventType.PHASE_ADVANCED,
        roundIndex = roundIndex.coerceAtLeast(1),
        phaseId = phase?.id.orEmpty().ifBlank { group.gameProfile?.currentPhaseId.orEmpty() },
        channelId = GROUP_CHANNEL_PUBLIC,
        visibility = GROUP_VISIBILITY_PUBLIC,
        text = text.trim(),
        createdAt = now,
    )

internal fun gameControlChangedEvent(
    group: Group,
    text: String,
    calledSeat: GameSeat?,
    actorName: String,
    now: Long = System.currentTimeMillis(),
): GameEvent {
    val profile = group.gameProfile
    val state = profile?.runtimeState()
    return GameEvent(
        id = gameEventId(if (calledSeat != null) "speaker" else "control", now, calledSeat?.seatId.orEmpty()),
        type = if (calledSeat != null) GameEventType.SPEAKER_CALLED else GameEventType.CONTROL_CHANGED,
        roundIndex = state?.roundIndex?.coerceAtLeast(1) ?: 1,
        phaseId = profile?.currentPhaseId.orEmpty(),
        actorName = actorName,
        targetSeatIds = calledSeat?.seatId?.let(::listOf).orEmpty(),
        targetActorIds = calledSeat?.actorId?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
        targetName = calledSeat?.displayName.orEmpty(),
        channelId = GROUP_CHANNEL_PUBLIC,
        visibility = GROUP_VISIBILITY_PUBLIC,
        text = text.trim(),
        createdAt = now,
    )
}

internal fun List<GameEvent>.toGameRuntimeMessageDrafts(
    profile: GameProfile?,
    includeActionSubmissions: Boolean = false,
): List<GameRuntimeMessageDraft> =
    mapNotNull { it.toGameRuntimeMessageDraft(profile, includeActionSubmissions) }
        .distinctBy { "${it.channelId}\u0000${it.visibility}\u0000${it.text}" }

private fun GameEvent.toGameRuntimeMessageDraft(
    profile: GameProfile?,
    includeActionSubmissions: Boolean,
): GameRuntimeMessageDraft? {
    val message = when (type) {
        GameEventType.ACTION_SUBMITTED,
        GameEventType.VOTE_SUBMITTED,
        -> if (includeActionSubmissions) text.ifBlank { formatActionSubmitEvent(profile) } else ""
        GameEventType.PHASE_ADVANCED,
        GameEventType.RESULT_PUBLISHED,
        GameEventType.CONTROL_CHANGED,
        GameEventType.SPEAKER_CALLED,
        -> text
        GameEventType.ACTION_RESOLVED -> text
    }.trim()
    if (message.isBlank()) return null
    return GameRuntimeMessageDraft(
        text = message,
        channelId = channelId.ifBlank { visibilityToDefaultChannel(visibility) },
        visibility = cleanGameVisibility(visibility),
    )
}

private fun GameEvent.formatActionSubmitEvent(profile: GameProfile?): String {
    val abilityName = profile?.abilities?.firstOrNull { it.id == abilityId }?.name
        ?: abilityId.ifBlank { "行动" }
    val actor = actorName.ifBlank { actorRoleId.ifBlank { actorSeatId.ifBlank { "玩家" } } }
    val targets = targetSeatIds
        .map { id -> profile?.seats?.firstOrNull { it.seatId == id }?.displayName?.ifBlank { id } ?: id }
        .plus(targetActorIds)
        .plus(targetName)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    val title = if (type == GameEventType.VOTE_SUBMITTED) "【投票】" else "【游戏行动】"
    return buildString {
        append(title)
        append(actor)
        append(" 提交「")
        append(abilityName)
        append("」")
        if (targets.isNotEmpty()) append("，目标：").append(targets.joinToString("、"))
        if (resultText.isNotBlank()) {
            val label = if (type == GameEventType.VOTE_SUBMITTED) "理由" else "备注"
            append("，").append(label).append("：").append(resultText.take(160))
        }
        append("。")
    }
}

private fun cleanGameVisibility(value: String): String =
    value.trim().lowercase(Locale.ROOT).takeIf {
        it in setOf(
            GROUP_VISIBILITY_PUBLIC,
            GROUP_VISIBILITY_TEAM,
            GROUP_VISIBILITY_PRIVATE,
            GROUP_VISIBILITY_JUDGE,
        )
    } ?: GROUP_VISIBILITY_JUDGE

private fun visibilityToDefaultChannel(visibility: String): String =
    when (cleanGameVisibility(visibility)) {
        GROUP_VISIBILITY_PUBLIC -> GROUP_CHANNEL_PUBLIC
        GROUP_VISIBILITY_TEAM -> GROUP_VISIBILITY_TEAM
        GROUP_VISIBILITY_PRIVATE -> GROUP_VISIBILITY_PRIVATE
        else -> GROUP_CHANNEL_JUDGE
    }

private fun gameEventId(prefix: String, now: Long, suffix: String): String {
    val cleanSuffix = suffix
        .ifBlank { "event" }
        .replace(Regex("""[^A-Za-z0-9_\-]"""), "_")
        .take(48)
    return "ge_${prefix}_${now}_$cleanSuffix"
}
