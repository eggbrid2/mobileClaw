package com.mobileclaw.ui.group

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.agent.GameActionDraft
import com.mobileclaw.agent.GameActionRecord
import com.mobileclaw.agent.GameActionStatus
import com.mobileclaw.agent.GameAbility
import com.mobileclaw.agent.GameAbilityEffect
import com.mobileclaw.agent.GameAbilityTrigger
import com.mobileclaw.agent.GameActorType
import com.mobileclaw.agent.GameChannelKind
import com.mobileclaw.agent.GameEvent
import com.mobileclaw.agent.GameEventType
import com.mobileclaw.agent.GameJudgeFlowMode
import com.mobileclaw.agent.GamePhase
import com.mobileclaw.agent.GameProfile
import com.mobileclaw.agent.GameRuntimeControlDraft
import com.mobileclaw.agent.GameRuntimeState
import com.mobileclaw.agent.GameSeat
import com.mobileclaw.agent.GameSeatStatus
import com.mobileclaw.agent.GameUserActionPolicy
import com.mobileclaw.agent.GameUserRole
import com.mobileclaw.agent.Group
import java.util.Locale

private val groupGameRuntimeGson = Gson()
internal const val GAME_RUNTIME_FLAG_SPEECH_OPEN = "speechOpen"
internal const val GAME_RUNTIME_FLAG_CALLED_SEAT_ID = "calledSeatId"
internal const val GAME_RUNTIME_FLAG_ORDERED_CURSOR_SEAT_ID = "orderedCursorSeatId"
internal const val GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS = "enabledUserAbilityIds"
internal const val GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP = "systemFlowStep"
internal const val GAME_RUNTIME_FLAG_SYSTEM_SPEECH_CURSOR = "systemSpeechCursor"
internal const val GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR = "systemVoteCursor"
internal const val GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR = "systemEventCursor"
internal const val GAME_RUNTIME_FLAG_FINAL_JUDGEMENT_DONE = "finalJudgementDone"
internal const val GAME_RUNTIME_FLOW_SPEECH = "speech"
internal const val GAME_RUNTIME_FLOW_VOTE = "vote"
internal const val GAME_RUNTIME_FLOW_EVENT_ENTER = "event_enter"
internal const val GAME_RUNTIME_FLOW_EVENT_ACTORS = "event_actors"
internal const val GAME_RUNTIME_FLOW_EVENT_RESULT = "event_result"
internal const val GAME_RUNTIME_FLOW_SETTLEMENT = "settlement"
internal const val GAME_RUNTIME_FLOW_FINAL_JUDGEMENT = "final_judgement"

internal data class GameRuntimeMessageDraft(
    val text: String,
    val channelId: String = GROUP_CHANNEL_PUBLIC,
    val visibility: String = GROUP_VISIBILITY_PUBLIC,
)

internal data class GameActionResolution(
    val group: Group,
    val record: GameActionRecord,
    val publicText: String,
    val resultMessages: List<GameRuntimeMessageDraft> = emptyList(),
    val events: List<GameEvent> = emptyList(),
)

internal data class GamePhaseResolution(
    val group: Group,
    val publicText: String,
    val resultMessages: List<GameRuntimeMessageDraft> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val resolvedCount: Int = 0,
)

internal data class GameRuntimeControlResolution(
    val group: Group,
    val publicText: String,
    val calledSeat: GameSeat? = null,
    val events: List<GameEvent> = emptyList(),
)

internal data class GameRuntimeFlowResolution(
    val group: Group,
    val publicText: String,
    val resultMessages: List<GameRuntimeMessageDraft> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val calledSeat: GameSeat? = null,
    val triggerText: String = "",
)

internal fun GameProfile.runtimeState(): GameRuntimeState {
    val root = runCatching {
        JsonParser.parseString(hiddenStateJson.ifBlank { "{}" }).asJsonObject
    }.getOrNull() ?: return GameRuntimeState()
    return GameRuntimeState(
        roundIndex = root.int("roundIndex", 1).coerceAtLeast(1),
        phaseStartedAt = root.long("phaseStartedAt", 0L),
        actions = root.array("actions").mapNotNull { it.asObjectOrNull()?.toGameActionRecord() },
        events = root.array("events").mapNotNull { it.asObjectOrNull()?.toGameEvent() },
        scores = root.intMap("scores"),
        flags = root.stringMap("flags"),
    )
}

internal fun GameProfile.withRuntimeState(state: GameRuntimeState): GameProfile =
    copy(hiddenStateJson = groupGameRuntimeGson.toJson(state))

internal fun Group.pendingGameActions(): List<GameActionRecord> =
    gameProfile
        ?.runtimeState()
        ?.actions
        .orEmpty()
        .filter { it.status == GameActionStatus.PENDING }
        .sortedBy { it.createdAt }

internal fun Group.canCurrentUserHostGame(): Boolean =
    gameProfile?.userRole in setOf(
        GameUserRole.HOST,
        GameUserRole.CO_HOST,
    )

internal fun Group.usesSystemGameJudge(): Boolean =
    isGameGroup(this) &&
        judgeRoleId.isBlank() &&
        gameProfile?.autoHost == true

internal fun Group.canCurrentUserDriveGameFlow(): Boolean =
    canCurrentUserHostGame()

internal fun Group.canCurrentUserSpeakInGame(): Boolean {
    val profile = gameProfile ?: return true
    if (usesSystemGameJudge()) return isSystemGameUserSpeechTurn()
    if (profile.userRole in setOf(GameUserRole.HOST, GameUserRole.CO_HOST, GameUserRole.SPECTATOR)) return true
    val userSeat = userGameSeat() ?: return false
    if (userSeat.status !in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)) return false
    val calledSeat = currentCalledGameSeat()
    if (calledSeat != null && calledSeat.seatId != userSeat.seatId) return false
    if (profile.userActionPolicy != GameUserActionPolicy.JUDGE_CONTROLLED) return true
    return isGameSpeechOpenForPlayers()
}

internal fun Group.isSystemGameUserSpeechTurn(): Boolean {
    if (!usesSystemGameJudge()) return false
    val profile = gameProfile ?: return false
    if (profile.userRole != GameUserRole.PLAYER) return false
    val userSeat = userGameSeat() ?: return false
    if (userSeat.status !in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)) return false
    val state = profile.runtimeState()
    if (state.flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] != GAME_RUNTIME_FLOW_SPEECH) return false
    if (!state.flags.boolFlag(GAME_RUNTIME_FLAG_SPEECH_OPEN).orFalse()) return false
    return currentCalledGameSeat()?.seatId == userSeat.seatId
}

internal fun Group.currentGamePhase(): GamePhase? {
    val profile = gameProfile ?: return null
    return profile.phases.firstOrNull { it.id == profile.currentPhaseId }
        ?: profile.phases.minByOrNull { it.order }
}

internal fun Group.userGameSeat(): GameSeat? =
    gameProfile?.seats?.firstOrNull { it.actorType == GameActorType.USER && it.actorId == "user" }

internal fun Group.gameSeatForRole(roleId: String): GameSeat? =
    gameProfile?.seats?.firstOrNull { it.actorType == GameActorType.AI_ROLE && it.actorId == roleId }

internal fun Group.activeGameSeats(): List<GameSeat> =
    gameProfile
        ?.seats
        .orEmpty()
        .filter { it.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE) }

internal fun Group.currentCalledGameSeat(): GameSeat? {
    val profile = gameProfile ?: return null
    val calledSeatId = profile.runtimeState().flags[GAME_RUNTIME_FLAG_CALLED_SEAT_ID].orEmpty()
    if (calledSeatId.isBlank()) return null
    return profile.seats.firstOrNull { seat ->
        seat.seatId.equals(calledSeatId, ignoreCase = true) ||
            seat.actorId.equals(calledSeatId, ignoreCase = true) ||
            seat.displayName.equals(calledSeatId, ignoreCase = true)
    }
}

internal fun Group.isGameSpeechOpenForPlayers(): Boolean {
    val profile = gameProfile ?: return true
    val state = profile.runtimeState()
    return state.flags.boolFlag(GAME_RUNTIME_FLAG_SPEECH_OPEN)
        ?: profile.defaultSpeechOpenForPhase(currentGamePhase())
}

internal fun Group.enabledUserGameAbilityOverrideIds(): Set<String>? =
    gameProfile
        ?.runtimeState()
        ?.flags
        ?.takeIf { GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS in it }
        ?.stringSetFlag(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)

internal fun Group.allowedRuntimeResponderRoleIds(): Set<String>? {
    val profile = gameProfile ?: return null
    val calledSeat = currentCalledGameSeat() ?: return null
    val ids = buildSet {
        if (calledSeat.actorType == GameActorType.AI_ROLE && calledSeat.actorId.isNotBlank()) add(calledSeat.actorId)
        if (judgeRoleId.isNotBlank()) add(judgeRoleId)
    }
    return ids
}

internal fun Group.availableUserGameAbilities(): List<GameAbility> {
    val seat = userGameSeat() ?: return emptyList()
    val profile = gameProfile ?: return emptyList()
    if (profile.userRole != GameUserRole.PLAYER) return emptyList()
    if (seat.status !in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)) return emptyList()
    val calledSeat = currentCalledGameSeat()
    if (calledSeat != null && calledSeat.seatId != seat.seatId) return emptyList()
    val overrideIds = if (profile.userActionPolicy == GameUserActionPolicy.JUDGE_CONTROLLED) {
        enabledUserGameAbilityOverrideIds()
    } else {
        null
    }
    val systemFlowStep = profile.runtimeState().flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP].orEmpty()
    val phase = currentGamePhase()
    val systemEventAbilityIds = if (usesSystemGameJudge() && systemFlowStep == GAME_RUNTIME_FLOW_EVENT_ACTORS) {
        profile.eventAbilityIds(phase).toSet()
    } else {
        emptySet()
    }
    return availableGameAbilitiesForSeat(
        seat = seat,
        usePhaseGate = profile.userActionPolicy != GameUserActionPolicy.OPEN,
    ).filter { ability ->
        (overrideIds == null || ability.id in overrideIds) &&
            (systemEventAbilityIds.isEmpty() || ability.id in systemEventAbilityIds)
    }
}

internal fun Group.systemGameFlowStep(): String =
    gameProfile
        ?.runtimeState()
        ?.flags
        ?.get(GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP)
        .orEmpty()

internal fun Group.isSystemGameEventActionStep(): Boolean =
    usesSystemGameJudge() &&
        systemGameFlowStep() in setOf(GAME_RUNTIME_FLOW_EVENT_ENTER, GAME_RUNTIME_FLOW_EVENT_ACTORS)

internal fun Group.isSystemGameFlowFinalJudgement(): Boolean =
    systemGameFlowStep() == GAME_RUNTIME_FLOW_FINAL_JUDGEMENT

internal fun Group.systemGameFlowShouldWaitForUserAction(): Boolean {
    if (!usesSystemGameJudge()) return false
    if (isSystemGameFlowFinalJudgement()) return true
    val profile = gameProfile ?: return false
    val seat = userGameSeat() ?: return false
    if (seat.status !in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)) return false
    val state = profile.runtimeState()
    val systemStep = state.flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP].orEmpty()
    val calledSeat = currentCalledGameSeat()
    if (systemStep == GAME_RUNTIME_FLOW_SPEECH) {
        return calledSeat?.seatId == seat.seatId
    }
    val phase = currentGamePhase() ?: return false

    fun hasSubmitted(allowedIds: Set<String>): Boolean =
        state.actions.any { action ->
            action.phaseId == phase.id &&
                action.draft.actorSeatId == seat.seatId &&
                action.draft.abilityId in allowedIds &&
                action.status in setOf(GameActionStatus.PENDING, GameActionStatus.RESOLVED)
        }

    return when (systemStep) {
        GAME_RUNTIME_FLOW_VOTE -> {
            val voteIds = profile.voteAbilityIds().toSet()
            calledSeat?.seatId == seat.seatId && voteIds.isNotEmpty() && !hasSubmitted(voteIds)
        }
        GAME_RUNTIME_FLOW_EVENT_ACTORS -> {
            val abilityIds = availableUserGameAbilities().map { it.id }.toSet()
            if (abilityIds.isEmpty()) return false
            calledSeat?.seatId == seat.seatId && !hasSubmitted(abilityIds)
        }
        else -> false
    }
}

internal fun Group.markSystemGameUserSpeechSubmitted(
    now: Long = System.currentTimeMillis(),
): Group? {
    if (!usesSystemGameJudge()) return null
    val profile = gameProfile ?: return null
    val state = profile.runtimeState()
    if (state.flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] != GAME_RUNTIME_FLOW_SPEECH) return null
    val userSeat = userGameSeat() ?: return null
    val calledSeat = currentCalledGameSeat() ?: return null
    if (calledSeat.seatId != userSeat.seatId) return null
    val nextFlags = state.flags.toMutableMap()
    nextFlags.remove(GAME_RUNTIME_FLAG_CALLED_SEAT_ID)
    nextFlags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
    nextFlags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = "false"
    return copy(
        gameProfile = profile.withRuntimeState(
            state.copy(
                phaseStartedAt = now,
                flags = nextFlags,
            ),
        ),
        updatedAt = now,
    )
}

internal fun Group.availableGameAbilitiesForSeat(seat: GameSeat): List<GameAbility> {
    return availableGameAbilitiesForSeat(seat = seat, usePhaseGate = true)
}

private fun Group.availableGameAbilitiesForSeat(
    seat: GameSeat,
    usePhaseGate: Boolean,
): List<GameAbility> {
    val profile = gameProfile ?: return emptyList()
    val phase = currentGamePhase()
    val identity = profile.identities.firstOrNull { it.id == seat.identityId }
    val abilityIds = (seat.abilityIds + identity?.abilityIds.orEmpty()).distinct()
    val phaseAbilityIds = phase?.enabledAbilityIds.orEmpty().toSet()
    return profile.abilities
        .filter { ability ->
            val phaseVoteAbility = ability.trigger == GameAbilityTrigger.VOTE || ability.effect == GameAbilityEffect.VOTE
            (ability.id in abilityIds || phaseVoteAbility) &&
                (!usePhaseGate || ability.id in phaseAbilityIds) &&
                seat.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE)
        }
}

internal fun Group.targetableGameSeatsForUser(): List<GameSeat> =
    userGameSeat()?.let { targetableGameSeatsForSeat(it) }.orEmpty()

internal fun Group.targetableGameSeatsForSeat(actorSeat: GameSeat): List<GameSeat> =
    gameProfile
        ?.seats
        .orEmpty()
        .filter { it.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE) }

internal fun Group.resolvePendingGameAction(
    actionId: String,
    now: Long = System.currentTimeMillis(),
): GameActionResolution? {
    val profile = gameProfile ?: return null
    val state = profile.runtimeState()
    val record = state.actions.firstOrNull { it.id == actionId } ?: return null
    if (record.status != GameActionStatus.PENDING) return null
    val outcome = profile.resolveGameActionBatch(state, listOf(record), now)
    val updatedRecord = outcome.records.firstOrNull { it.id == actionId } ?: record
    val updatedProfile = profile
        .copy(seats = outcome.seats)
        .withRuntimeState(outcome.state)
    val updatedGroup = copy(
        gameProfile = updatedProfile,
        updatedAt = now,
    )
    val publicText = outcome.publicLines.joinToString("\n").ifBlank { updatedRecord.resultText }
    val events = gameRuntimeResultEvents(
        group = updatedGroup,
        publicText = publicText,
        resultMessages = outcome.resultMessages,
        now = now,
    )
    val eventedGroup = updatedGroup.withAppendedGameEvents(events, now)
    return GameActionResolution(
        group = eventedGroup,
        record = updatedRecord,
        publicText = publicText,
        resultMessages = outcome.resultMessages,
        events = events,
    )
}

internal fun Group.resolveCurrentPhaseGameActions(
    now: Long = System.currentTimeMillis(),
): GamePhaseResolution? {
    val profile = gameProfile ?: return null
    val phase = currentGamePhase() ?: return null
    return resolveGamePhaseActions(phase.id, now)
}

internal fun Group.resolveGamePhaseActions(
    phaseId: String,
    now: Long = System.currentTimeMillis(),
): GamePhaseResolution? {
    val profile = gameProfile ?: return null
    val phase = profile.phases.firstOrNull { it.id == phaseId } ?: currentGamePhase() ?: return null
    val state = profile.runtimeState()
    val pendingActions = state.actions
        .filter { it.status == GameActionStatus.PENDING && it.phaseId == phase.id }
        .sortedWith(compareBy<GameActionRecord> { profile.resolutionPriority(it) }.thenBy { it.createdAt })
    if (pendingActions.isEmpty()) return null

    val outcome = profile.resolveGameActionBatch(state, pendingActions, now)
    val resolvedCount = outcome.records.count { it.status == GameActionStatus.RESOLVED }
    if (resolvedCount == 0) return null
    val updatedProfile = profile
        .copy(seats = outcome.seats)
        .withRuntimeState(outcome.state)
    val workingGroup = copy(
        gameProfile = updatedProfile,
        updatedAt = now,
    )
    val phaseName = phase.name.ifBlank { phase.id.ifBlank { "当前阶段" } }
    val header = "【阶段结算】$phaseName 已处理 $resolvedCount 个行动。"
    val publicText = (listOf(header) + outcome.publicLines.distinct()).joinToString("\n")
    val events = gameRuntimeResultEvents(
        group = workingGroup,
        publicText = publicText,
        resultMessages = outcome.resultMessages,
        now = now,
    )
    val eventedGroup = workingGroup.withAppendedGameEvents(events, now)
    return GamePhaseResolution(
        group = eventedGroup,
        publicText = publicText,
        resultMessages = outcome.resultMessages,
        events = events,
        resolvedCount = resolvedCount,
    )
}

internal fun Group.advanceSystemGameJudgeFlow(
    now: Long = System.currentTimeMillis(),
): GameRuntimeFlowResolution? {
    val profile = gameProfile ?: return null
    if (!usesSystemGameJudge()) return null
    val state = profile.runtimeState()
    val flags = state.flags.toMutableMap()
    val currentStep = flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP].orEmpty().ifBlank { GAME_RUNTIME_FLOW_SPEECH }
    val currentRound = state.roundIndex.coerceAtLeast(1)
    val votePhase = profile.votePhase()
    val speechPhase = profile.speechPhase()
    val eventPhase = profile.eventPhase()

    fun withPhaseAndFlags(
        phase: GamePhase?,
        step: String,
        speechOpen: Boolean,
        enabledAbilityIds: List<String> = emptyList(),
        roundIndex: Int = currentRound,
        clearCall: Boolean = true,
        extraFlags: Map<String, String> = emptyMap(),
    ): Group {
        val nextFlags = flags.toMutableMap()
        nextFlags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] = step
        nextFlags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = speechOpen.toString()
        if (clearCall) nextFlags.remove(GAME_RUNTIME_FLAG_CALLED_SEAT_ID)
        if (enabledAbilityIds.isEmpty()) {
            nextFlags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
        } else {
            nextFlags[GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS] = enabledAbilityIds.distinct().joinToString(",")
        }
        nextFlags.putAll(extraFlags)
        val nextState = state.copy(
            roundIndex = roundIndex,
            phaseStartedAt = now,
            flags = nextFlags,
        )
        return copy(
            gameProfile = profile
                .copy(currentPhaseId = phase?.id.orEmpty().ifBlank { profile.currentPhaseId })
                .withRuntimeState(nextState),
            updatedAt = now,
        )
    }

    fun result(
        group: Group,
        text: String,
        resultMessages: List<GameRuntimeMessageDraft> = emptyList(),
        calledSeat: GameSeat? = null,
        triggerText: String = "",
    ): GameRuntimeFlowResolution {
        val events = gameRuntimeResultEvents(
            group = group,
            publicText = text,
            resultMessages = resultMessages,
            now = now,
        )
        val eventedGroup = group.withAppendedGameEvents(events, now)
        return GameRuntimeFlowResolution(
            group = eventedGroup,
            publicText = text,
            resultMessages = resultMessages,
            events = events,
            calledSeat = calledSeat,
            triggerText = triggerText,
        )
    }

    return when (currentStep) {
        GAME_RUNTIME_FLOW_SPEECH -> {
            val cursor = flags[GAME_RUNTIME_FLAG_SYSTEM_SPEECH_CURSOR]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val nextSeat = nextSystemSpeechSeat(startIndex = cursor)
            if (nextSeat != null) {
                val updated = callSystemSpeechSeat(
                    seat = nextSeat,
                    nextCursor = activeGameSeats().indexOfFirst { it.seatId == nextSeat.seatId } + 1,
                    now = now,
                )
                result(
                    group = updated,
                    text = "【系统法官】第 $currentRound 轮。${nextSeat.displayName.ifBlank { nextSeat.seatId }} 发言。",
                    calledSeat = nextSeat,
                    triggerText = "系统法官点名你进行第 $currentRound 轮公开发言。只基于公开信息表达判断、质询或辩解；不要提交技能，不要宣布阶段推进，发言后停下等待下一次点名。",
                )
            } else {
                val voteAbilities = profile.voteAbilityIds()
                val next = withPhaseAndFlags(
                    phase = votePhase ?: speechPhase,
                    step = GAME_RUNTIME_FLOW_VOTE,
                    speechOpen = false,
                    enabledAbilityIds = voteAbilities,
                    extraFlags = mapOf(GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR to "0"),
                )
                result(
                    group = next,
                    text = "【系统法官】第 $currentRound 轮发言结束，开始投票。",
                )
            }
        }
        GAME_RUNTIME_FLOW_VOTE -> {
            val cursor = flags[GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val nextSeat = nextSystemVoteSeat(startIndex = cursor)
            if (nextSeat != null) {
                val updated = callSystemVoteSeat(
                    seat = nextSeat,
                    nextCursor = activeGameSeats().indexOfFirst { it.seatId == nextSeat.seatId } + 1,
                    now = now,
                )
                result(
                    group = updated,
                    text = "【系统法官】${nextSeat.displayName.ifBlank { nextSeat.seatId }} 投票。",
                    calledSeat = nextSeat,
                    triggerText = "系统法官点名你投票。必须选择一个当前存活目标，并调用 game_runtime(action=\"submit_action\") 提交你的投票能力；理由只能基于公开信息，不要泄露私密身份。",
                )
            } else {
                val eventAbilityIds = profile.eventAbilityIds()
                val next = withPhaseAndFlags(
                    phase = eventPhase ?: votePhase ?: speechPhase,
                    step = GAME_RUNTIME_FLOW_EVENT_ENTER,
                    speechOpen = false,
                    enabledAbilityIds = eventAbilityIds,
                    extraFlags = mapOf(GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR to "0"),
                )
                result(
                    group = next,
                    text = "【系统法官】投票收齐，进入事件期。",
                    triggerText = "系统法官宣布进入事件期；不要公开泄露私密身份和行动，只在可见频道提交技能。",
                )
            }
        }
        GAME_RUNTIME_FLOW_EVENT_ENTER -> {
            val voteResolution = votePhase?.let { resolveGamePhaseActions(it.id, now) }
            val baseGroup = voteResolution?.group ?: this
            val nextSeat = baseGroup.nextSystemEventSeat(startIndex = 0)
            if (nextSeat != null) {
                val updated = baseGroup.callSystemEventSeat(
                    seat = nextSeat,
                    nextCursor = baseGroup.activeGameSeats().indexOfFirst { it.seatId == nextSeat.seatId } + 1,
                    now = now,
                )
                result(
                    group = updated,
                    text = listOf(
                        voteResolution?.publicText.orEmpty(),
                        "【系统法官】事件期开始，阵营行动中。",
                    ).filter { it.isNotBlank() }.joinToString("\n"),
                    resultMessages = voteResolution?.resultMessages.orEmpty(),
                    calledSeat = nextSeat,
                    triggerText = "系统法官点名你在事件期行动。只有你的身份在当前阶段开放的技能可用；若有可用技能，必须调用 game_runtime(action=\"submit_action\") 私密/团队/法官频道提交；不要在普通回复里复述目标、阵营或行动细节；否则只简短说明跳过。",
                )
            } else {
                val updated = baseGroup.withSystemFlowStep(
                    step = GAME_RUNTIME_FLOW_EVENT_RESULT,
                    speechOpen = false,
                    enabledAbilityIds = emptyList(),
                    now = now,
                )
                result(
                    group = updated,
                    text = listOf(
                        voteResolution?.publicText.orEmpty(),
                        "【系统法官】本轮没有可触发的阵营行动。",
                    ).filter { it.isNotBlank() }.joinToString("\n"),
                    resultMessages = voteResolution?.resultMessages.orEmpty(),
                )
            }
        }
        GAME_RUNTIME_FLOW_EVENT_ACTORS -> {
            val cursor = flags[GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val nextSeat = nextSystemEventSeat(startIndex = cursor)
            if (nextSeat != null) {
                val updated = callSystemEventSeat(
                    seat = nextSeat,
                    nextCursor = activeGameSeats().indexOfFirst { it.seatId == nextSeat.seatId } + 1,
                    now = now,
                )
                result(
                    group = updated,
                    text = "",
                    calledSeat = nextSeat,
                    triggerText = "系统法官点名你继续事件期行动。只有你的身份在当前阶段开放的技能可用；若有可用技能，必须调用 game_runtime(action=\"submit_action\") 私密/团队/法官频道提交；不要在普通回复里复述目标、阵营或行动细节；否则只简短说明跳过。",
                )
            } else {
                val eventResolution = (eventPhase ?: currentGamePhase())?.let { resolveGamePhaseActions(it.id, now) }
                val baseGroup = eventResolution?.group ?: this
                val updated = baseGroup.withSystemFlowStep(
                    step = GAME_RUNTIME_FLOW_SETTLEMENT,
                    speechOpen = false,
                    enabledAbilityIds = emptyList(),
                    now = now,
                )
                result(
                    group = updated,
                    text = listOf(
                        eventResolution?.publicText.orEmpty(),
                        "【系统法官】事件期结束，本轮结算完成。",
                    ).filter { it.isNotBlank() }.joinToString("\n"),
                    resultMessages = eventResolution?.resultMessages.orEmpty(),
                )
            }
        }
        GAME_RUNTIME_FLOW_EVENT_RESULT,
        GAME_RUNTIME_FLOW_SETTLEMENT,
        -> {
            val maxRound = roundLimit.coerceIn(1, 8)
            if (currentRound >= maxRound) {
                val next = withPhaseAndFlags(
                    phase = currentGamePhase(),
                    step = GAME_RUNTIME_FLOW_FINAL_JUDGEMENT,
                    speechOpen = false,
                    roundIndex = currentRound,
                    extraFlags = mapOf(GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR to "0"),
                )
                result(
                    group = next,
                    text = "【系统法官】全部 $maxRound 轮结束，开始终局裁定。",
                    triggerText = "终局判定：请根据本局胜负配置、公开发言、行动/投票记录、积分与出局状态，给出最终胜负、关键证据和简短复盘。只能在最终轮结束后裁定胜负。",
                )
            } else {
                val nextRound = currentRound + 1
                val next = withPhaseAndFlags(
                    phase = speechPhase ?: votePhase ?: eventPhase,
                    step = GAME_RUNTIME_FLOW_SPEECH,
                    speechOpen = false,
                    roundIndex = nextRound,
                    extraFlags = mapOf(
                        GAME_RUNTIME_FLAG_SYSTEM_SPEECH_CURSOR to "0",
                        GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR to "0",
                        GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR to "0",
                    ),
                )
                result(
                    group = next,
                    text = "【系统法官】第 $nextRound 轮开始。",
                )
            }
        }
        GAME_RUNTIME_FLOW_FINAL_JUDGEMENT -> {
            null
        }
        else -> {
            val next = withPhaseAndFlags(
                phase = speechPhase ?: votePhase ?: eventPhase,
                step = GAME_RUNTIME_FLOW_SPEECH,
                speechOpen = false,
                extraFlags = mapOf(
                    GAME_RUNTIME_FLAG_SYSTEM_SPEECH_CURSOR to "0",
                    GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR to "0",
                    GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR to "0",
                ),
            )
            result(
                group = next,
                text = "【系统法官】第 $currentRound 轮开始。",
            )
        }
    }
}

internal fun Group.applyGameRuntimeControl(
    control: GameRuntimeControlDraft,
    actorName: String = "",
    now: Long = System.currentTimeMillis(),
): GameRuntimeControlResolution? {
    val profile = gameProfile ?: return null
    val state = profile.runtimeState()
    val flags = state.flags.toMutableMap()
    val activeSeats = profile.seats.filter { it.status in setOf(GameSeatStatus.READY, GameSeatStatus.ALIVE) }
    val changes = mutableListOf<String>()
    var calledSeat: GameSeat? = null

    fun callSeat(seat: GameSeat) {
        flags[GAME_RUNTIME_FLAG_CALLED_SEAT_ID] = seat.seatId
        if (profile.judgeFlowMode == GameJudgeFlowMode.ORDERED) {
            flags[GAME_RUNTIME_FLAG_ORDERED_CURSOR_SEAT_ID] = seat.seatId
        }
        flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = "true"
        calledSeat = seat
        changes += "点名 ${seat.displayName.ifBlank { seat.seatId }}"
    }

    if (control.clearCalledSeat) {
        flags.remove(GAME_RUNTIME_FLAG_CALLED_SEAT_ID)
        changes += "清除点名"
    }

    if (control.nextOrderedSpeaker && activeSeats.isNotEmpty()) {
        val currentSeatId = flags[GAME_RUNTIME_FLAG_CALLED_SEAT_ID]
            ?: flags[GAME_RUNTIME_FLAG_ORDERED_CURSOR_SEAT_ID]
            ?: ""
        val currentIndex = activeSeats.indexOfFirst { it.seatId == currentSeatId }.takeIf { it >= 0 } ?: -1
        val nextSeat = activeSeats[Math.floorMod(currentIndex + 1, activeSeats.size)]
        callSeat(nextSeat)
    } else {
        profile.resolveControlSeat(control)?.let(::callSeat)
    }

    control.speechOpen?.let { open ->
        flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = open.toString()
        changes += if (open) "开放发言" else "关闭发言"
    }

    if (control.replaceEnabledAbilityIds) {
        val validAbilityIds = profile.abilities.map { it.id }.toSet()
        val cleanAbilityIds = control.enabledAbilityIds
            .map { it.trim() }
            .filter { it.isNotBlank() && it in validAbilityIds }
            .distinct()
        if (cleanAbilityIds.isEmpty()) {
            flags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
            changes += "用户按键跟随阶段"
        } else {
            flags[GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS] = cleanAbilityIds.joinToString(",")
            val abilityNames = cleanAbilityIds.joinToString("、") { id ->
                profile.abilities.firstOrNull { it.id == id }?.name ?: id
            }
            changes += "用户按键：$abilityNames"
        }
    }

    if (changes.isEmpty() && control.note.isBlank()) return null
    val updatedState = state.copy(flags = flags)
    val updated = copy(
        gameProfile = profile.withRuntimeState(updatedState),
        updatedAt = now,
    )
    val prefix = actorName.ifBlank { "法官" }
    val publicText = buildString {
        append("【法官控制】")
        append(prefix)
        val changeText = changes.distinct().joinToString(" · ")
        if (changeText.isNotBlank()) append("：").append(changeText)
        if (control.note.isNotBlank()) {
            if (changeText.isBlank()) append("：")
            append(if (changeText.isBlank()) "" else " · ")
            append(control.note.trim().take(160))
        }
        append("。")
    }
    val event = gameControlChangedEvent(
        group = updated,
        text = publicText,
        calledSeat = calledSeat ?: updated.currentCalledGameSeat(),
        actorName = prefix,
        now = now,
    )
    val eventedGroup = updated.withAppendedGameEvents(listOf(event), now)
    return GameRuntimeControlResolution(
        group = eventedGroup,
        publicText = publicText,
        calledSeat = calledSeat ?: eventedGroup.currentCalledGameSeat(),
        events = listOf(event),
    )
}

internal fun GameProfile.runtimeFlagsForPhaseStart(
    state: GameRuntimeState,
    phase: GamePhase?,
): Map<String, String> {
    val flags = state.flags.toMutableMap()
    flags.remove(GAME_RUNTIME_FLAG_CALLED_SEAT_ID)
    flags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
    flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = defaultSpeechOpenForPhase(phase).toString()
    return flags
}

internal fun GameProfile.defaultSpeechOpenForPhase(phase: GamePhase?): Boolean =
    phase == null ||
        phase.channelIds.isEmpty() ||
        phase.channelIds.any { it.equals(GROUP_CHANNEL_PUBLIC, ignoreCase = true) }

private fun GameProfile.votePhase(): GamePhase? =
    phases.firstOrNull { phase ->
        phase.id.contains("vote", ignoreCase = true) ||
            phase.name.contains("投票") ||
            phase.name.contains("vote", ignoreCase = true)
    }

private fun GameProfile.speechPhase(): GamePhase? =
    phases.firstOrNull { phase ->
        phase.channelIds.any { it.equals(GROUP_CHANNEL_PUBLIC, ignoreCase = true) } &&
            !phase.id.contains("vote", ignoreCase = true) &&
            !phase.name.contains("投票") &&
            !phase.name.contains("vote", ignoreCase = true)
    } ?: phases.firstOrNull { phase ->
        phase.id.contains("day", ignoreCase = true) ||
            phase.name.contains("白天") ||
            phase.name.contains("发言") ||
            phase.name.contains("speech", ignoreCase = true)
    }

private fun GameProfile.eventPhase(): GamePhase? =
    phases.firstOrNull { phase ->
        phase.id.contains("night", ignoreCase = true) ||
            phase.id.contains("event", ignoreCase = true) ||
            phase.id.contains("action", ignoreCase = true) ||
            phase.name.contains("夜") ||
            phase.name.contains("事件") ||
            phase.name.contains("行动") ||
            phase.name.contains("night", ignoreCase = true) ||
            phase.name.contains("event", ignoreCase = true) ||
            phase.name.contains("action", ignoreCase = true)
    } ?: phases.firstOrNull()

private fun GameProfile.voteAbilityIds(): List<String> =
    abilities
        .filter { it.trigger == GameAbilityTrigger.VOTE || it.effect == GameAbilityEffect.VOTE }
        .map { it.id }

private fun GameProfile.eventAbilityIds(phase: GamePhase? = null): List<String> {
    val phaseAbilityIds = phase?.enabledAbilityIds.orEmpty().toSet()
    return abilities
        .filterNot { it.trigger == GameAbilityTrigger.VOTE || it.effect == GameAbilityEffect.VOTE || it.effect == GameAbilityEffect.MESSAGE_PRIVATE }
        .filterNot { it.trigger == GameAbilityTrigger.PASSIVE }
        .filter { phaseAbilityIds.isEmpty() || it.id in phaseAbilityIds }
        .map { it.id }
}

private fun Group.nextSystemEventSeat(startIndex: Int): GameSeat? {
    val profile = gameProfile ?: return null
    val eventAbilityIds = profile.eventAbilityIds(currentGamePhase()).toSet()
    if (eventAbilityIds.isEmpty()) return null
    val seats = activeGameSeats()
    if (seats.isEmpty()) return null
    return seats
        .drop(startIndex.coerceIn(0, seats.size))
        .firstOrNull { seat ->
            val identityAbilityIds = profile.identities.firstOrNull { it.id == seat.identityId }?.abilityIds.orEmpty()
            (seat.abilityIds + identityAbilityIds).any { it in eventAbilityIds }
        }
}

private fun Group.nextSystemSpeechSeat(startIndex: Int): GameSeat? {
    val seats = activeGameSeats()
    if (seats.isEmpty()) return null
    return seats.drop(startIndex.coerceIn(0, seats.size)).firstOrNull()
}

private fun Group.callSystemSpeechSeat(
    seat: GameSeat,
    nextCursor: Int,
    now: Long,
): Group {
    val profile = gameProfile ?: return this
    val state = profile.runtimeState()
    val flags = state.flags.toMutableMap()
    flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] = GAME_RUNTIME_FLOW_SPEECH
    flags[GAME_RUNTIME_FLAG_CALLED_SEAT_ID] = seat.seatId
    flags[GAME_RUNTIME_FLAG_SYSTEM_SPEECH_CURSOR] = nextCursor.coerceAtLeast(0).toString()
    flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = "true"
    flags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
    flags.remove(GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR)
    flags.remove(GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR)
    val speechPhaseId = profile.speechPhase()?.id.orEmpty().ifBlank { profile.currentPhaseId }
    return copy(
        gameProfile = profile.copy(currentPhaseId = speechPhaseId).withRuntimeState(
            state.copy(
                phaseStartedAt = now,
                flags = flags,
            ),
        ),
        updatedAt = now,
    )
}

private fun Group.nextSystemVoteSeat(startIndex: Int): GameSeat? {
    val profile = gameProfile ?: return null
    val voteAbilityIds = profile.voteAbilityIds().toSet()
    if (voteAbilityIds.isEmpty()) return null
    val seats = activeGameSeats()
    if (seats.isEmpty()) return null
    return seats.drop(startIndex.coerceIn(0, seats.size)).firstOrNull()
}

private fun Group.callSystemVoteSeat(
    seat: GameSeat,
    nextCursor: Int,
    now: Long,
): Group {
    val profile = gameProfile ?: return this
    val state = profile.runtimeState()
    val voteAbilityIds = profile.voteAbilityIds()
    val flags = state.flags.toMutableMap()
    flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] = GAME_RUNTIME_FLOW_VOTE
    flags[GAME_RUNTIME_FLAG_CALLED_SEAT_ID] = seat.seatId
    flags[GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR] = nextCursor.coerceAtLeast(0).toString()
    flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = "false"
    flags[GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS] = voteAbilityIds.joinToString(",")
    flags.remove(GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR)
    val votePhaseId = profile.votePhase()?.id.orEmpty().ifBlank { profile.currentPhaseId }
    return copy(
        gameProfile = profile.copy(currentPhaseId = votePhaseId).withRuntimeState(
            state.copy(
                phaseStartedAt = now,
                flags = flags,
            ),
        ),
        updatedAt = now,
    )
}

private fun Group.callSystemEventSeat(
    seat: GameSeat,
    nextCursor: Int,
    now: Long,
): Group {
    val profile = gameProfile ?: return this
    val state = profile.runtimeState()
    val abilityIds = profile.eventAbilityIds(currentGamePhase())
    val flags = state.flags.toMutableMap()
    flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] = GAME_RUNTIME_FLOW_EVENT_ACTORS
    flags[GAME_RUNTIME_FLAG_CALLED_SEAT_ID] = seat.seatId
    flags[GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR] = nextCursor.coerceAtLeast(0).toString()
    flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = "false"
    if (abilityIds.isEmpty()) {
        flags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
    } else {
        flags[GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS] = abilityIds.joinToString(",")
    }
    return copy(
        gameProfile = profile.withRuntimeState(
            state.copy(
                phaseStartedAt = now,
                flags = flags,
            ),
        ),
        updatedAt = now,
    )
}

private fun GameProfile.seatHasAnyAbility(
    seat: GameSeat,
    abilityIds: Set<String>,
): Boolean {
    if (abilityIds.isEmpty()) return false
    val identityAbilityIds = identities.firstOrNull { it.id == seat.identityId }?.abilityIds.orEmpty()
    return (seat.abilityIds + identityAbilityIds).any { it in abilityIds }
}

private fun Group.withSystemFlowStep(
    step: String,
    speechOpen: Boolean,
    enabledAbilityIds: List<String>,
    now: Long,
): Group {
    val profile = gameProfile ?: return this
    val state = profile.runtimeState()
    val flags = state.flags.toMutableMap()
    flags[GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP] = step
    flags[GAME_RUNTIME_FLAG_SPEECH_OPEN] = speechOpen.toString()
    flags.remove(GAME_RUNTIME_FLAG_CALLED_SEAT_ID)
    flags.remove(GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR)
    if (enabledAbilityIds.isEmpty()) {
        flags.remove(GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS)
    } else {
        flags[GAME_RUNTIME_FLAG_ENABLED_USER_ABILITY_IDS] = enabledAbilityIds.distinct().joinToString(",")
    }
    return copy(
        gameProfile = profile.withRuntimeState(
            state.copy(
                phaseStartedAt = now,
                flags = flags,
            ),
        ),
        updatedAt = now,
    )
}

private fun GameProfile.resolveControlSeat(control: GameRuntimeControlDraft): GameSeat? {
    val seatId = control.calledSeatId.trim()
    val actorId = control.calledActorId.trim()
    val name = control.calledName.trim()
    return seats.firstOrNull { seat ->
        (seatId.isNotBlank() && (
            seat.seatId.equals(seatId, ignoreCase = true) ||
                seat.actorId.equals(seatId, ignoreCase = true) ||
                seat.displayName.equals(seatId, ignoreCase = true)
            )) ||
            (actorId.isNotBlank() && seat.actorId.equals(actorId, ignoreCase = true)) ||
            (name.isNotBlank() && (
                seat.displayName.equals(name, ignoreCase = true) ||
                    seat.displayName.contains(name, ignoreCase = true) ||
                    name.contains(seat.displayName, ignoreCase = true)
                ))
    }
}

private fun Map<String, String>.boolFlag(key: String): Boolean? =
    get(key)
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.let { value ->
            when (value) {
                "true", "1", "yes", "on", "open" -> true
                "false", "0", "no", "off", "closed", "close" -> false
                else -> null
            }
        }

private fun Boolean?.orFalse(): Boolean = this == true

private fun Map<String, String>.stringSetFlag(key: String): Set<String> =
    get(key)
        ?.split(',', '，', ';', '；', '\n')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.toSet()
        .orEmpty()

private data class GameActionBatchOutcome(
    val state: GameRuntimeState,
    val seats: List<GameSeat>,
    val records: List<GameActionRecord>,
    val publicLines: List<String>,
    val resultMessages: List<GameRuntimeMessageDraft>,
    val events: List<GameEvent>,
)

private data class GameActionInput(
    val record: GameActionRecord,
    val ability: GameAbility?,
    val targetSeats: List<GameSeat>,
)

private enum class GameEliminateEffect {
    OUT,
    SCORE,
    OUT_AND_SCORE,
    RECORD_ONLY,
}

private enum class GameVoteEffect {
    PLURALITY_OUT,
    MAJORITY_OUT,
    SCORE_TARGET,
    SCORE_ACTOR,
    RECORD_ONLY,
}

private enum class GameTiePolicy {
    NO_EFFECT,
    ALL,
    FIRST,
}

private data class GameRuleBook(
    val protectBlocksEliminate: Boolean = true,
    val preventConsecutiveProtectSameTarget: Boolean = false,
    val eliminateEffect: GameEliminateEffect = GameEliminateEffect.OUT,
    val eliminateActorPoints: Int = 0,
    val eliminateTargetPoints: Int = 0,
    val voteEffect: GameVoteEffect = GameVoteEffect.PLURALITY_OUT,
    val voteActorPoints: Int = 0,
    val voteTargetPoints: Int = 0,
    val voteTiePolicy: GameTiePolicy = GameTiePolicy.NO_EFFECT,
    val scoreLimit: Int? = null,
)

private fun GameProfile.resolveGameActionBatch(
    state: GameRuntimeState,
    pendingActions: List<GameActionRecord>,
    now: Long,
): GameActionBatchOutcome {
    val rules = ruleBook()
    var currentSeats = seats
    val scores = state.scores.toMutableMap()
    val flags = state.flags.toMutableMap()
    val resolvedRecords = linkedMapOf<String, GameActionRecord>()
    val resolutionEvents = mutableListOf<GameEvent>()
    val publicLines = mutableListOf<String>()
    val resultMessages = mutableListOf<GameRuntimeMessageDraft>()
    val inputs = pendingActions.map { record ->
        GameActionInput(
            record = record,
            ability = abilities.firstOrNull { it.id == record.draft.abilityId },
            targetSeats = targetSeatsFor(record.draft),
        )
    }

    fun resolve(input: GameActionInput, status: GameActionStatus, result: String) {
        val updatedRecord = input.record.copy(
            status = status,
            resultText = result,
            updatedAt = now,
        )
        resolvedRecords[input.record.id] = updatedRecord
        resolutionEvents += updatedRecord.toGameActionResolvedEvent(
            profile = this@resolveGameActionBatch,
            status = status,
            resultText = result,
            now = now,
            index = resolutionEvents.size,
        )
    }

    fun reject(input: GameActionInput, result: String) {
        resolve(input, GameActionStatus.REJECTED, result)
        publicLines += if (input.record.draft.isPublicAction()) {
            "【游戏记录】${input.actorName()} 的行动未生效：$result。"
        } else {
            "【游戏记录】一项隐藏行动未生效：$result。"
        }
    }

    inputs.filter { it.ability == null }.forEach { reject(it, "能力不存在") }
    val usableInputs = inputs.filter { it.ability != null }
    val blockedSeatIds = usableInputs
        .filter { it.ability?.effect == GameAbilityEffect.BLOCK }
        .flatMap { it.targetSeats.map { seat -> seat.seatId } }
        .toSet()
    fun isBlocked(input: GameActionInput): Boolean =
        input.ability?.effect != GameAbilityEffect.BLOCK &&
            input.record.draft.actorSeatId.isNotBlank() &&
            input.record.draft.actorSeatId in blockedSeatIds

    val protectedSeatIds = mutableSetOf<String>()
    usableInputs
        .filter { it.ability?.effect == GameAbilityEffect.PROTECT }
        .forEach { input ->
            if (isBlocked(input)) {
                reject(input, "行动者被阻止")
                return@forEach
            }
            val actorKey = "protect:last:${input.record.draft.actorSeatId}"
            val acceptedTargets = if (rules.preventConsecutiveProtectSameTarget) {
                input.targetSeats.filter { seat -> flags[actorKey] != seat.seatId }
            } else {
                input.targetSeats
            }
            protectedSeatIds += acceptedTargets.map { it.seatId }
            acceptedTargets.firstOrNull()?.let { flags[actorKey] = it.seatId }
            val result = when {
                input.targetSeats.isEmpty() -> "没有有效保护目标"
                acceptedTargets.isEmpty() -> "连续保护同一目标，保护未生效"
                else -> "保护：${acceptedTargets.joinToString("、") { seat -> seat.displayName.ifBlank { seat.seatId } }}"
            }
            resolve(input, GameActionStatus.RESOLVED, result)
            publicLines += publicHandled(input, "保护结果已记录")
            resultMessages += GameRuntimeMessageDraft(
                "【游戏结果】${input.actorName()} 的「${input.abilityName()}」已生效，$result。",
                input.privateChannel(),
                GROUP_VISIBILITY_PRIVATE,
            )
        }

    usableInputs
        .filter { it.ability?.effect == GameAbilityEffect.ELIMINATE }
        .forEach { input ->
            if (isBlocked(input)) {
                reject(input, "行动者被阻止")
                return@forEach
            }
            val protectedTargets = if (rules.protectBlocksEliminate) {
                input.targetSeats.filter { it.seatId in protectedSeatIds }
            } else {
                emptyList()
            }
            val protectedIds = protectedTargets.map { it.seatId }.toSet()
            val affectedTargets = input.targetSeats.filter { it.seatId !in protectedIds }
            val outTargets = when (rules.eliminateEffect) {
                GameEliminateEffect.OUT,
                GameEliminateEffect.OUT_AND_SCORE,
                -> affectedTargets
                GameEliminateEffect.SCORE,
                GameEliminateEffect.RECORD_ONLY,
                -> emptyList()
            }
            if (outTargets.isNotEmpty()) {
                val outIds = outTargets.map { it.seatId }.toSet()
                currentSeats = currentSeats.map { seat ->
                    if (seat.seatId in outIds) seat.copy(status = GameSeatStatus.OUT) else seat
                }
            }
            val scoreTargets = when (rules.eliminateEffect) {
                GameEliminateEffect.SCORE,
                GameEliminateEffect.OUT_AND_SCORE,
                -> affectedTargets
                GameEliminateEffect.OUT,
                GameEliminateEffect.RECORD_ONLY,
                -> emptyList()
            }
            val actorPoints = rules.eliminateActorPoints * scoreTargets.size
            if (actorPoints != 0) scores.addScore(input.record.draft.actorSeatId, actorPoints)
            if (rules.eliminateTargetPoints != 0) {
                scoreTargets.forEach { target -> scores.addScore(target.seatId, rules.eliminateTargetPoints) }
            }
            val result = buildEliminateResult(input, outTargets, protectedTargets, actorPoints)
            resolve(input, GameActionStatus.RESOLVED, result)
            publicLines += eliminatePublicLine(input, outTargets, protectedTargets, actorPoints, rules)
        }

    resolveVotes(
        inputs = usableInputs.filter { it.ability?.effect == GameAbilityEffect.VOTE },
        rules = rules,
        blocked = { input -> isBlocked(input) },
        currentSeats = currentSeats,
        updateSeats = { currentSeats = it },
        scores = scores,
        resolve = { input, status, result -> resolve(input, status, result) },
        reject = { input, result -> reject(input, result) },
        publicLines = publicLines,
    )

    usableInputs
        .filterNot { it.ability?.effect in setOf(GameAbilityEffect.PROTECT, GameAbilityEffect.ELIMINATE, GameAbilityEffect.VOTE) }
        .forEach { input ->
            if (resolvedRecords.containsKey(input.record.id)) return@forEach
            if (isBlocked(input)) {
                reject(input, "行动者被阻止")
                return@forEach
            }
            when (input.ability?.effect) {
                GameAbilityEffect.INSPECT -> {
                    val resultLines = input.targetSeats.map { seat ->
                        val identity = identities.firstOrNull { it.id == seat.identityId }
                        val identityName = identity?.name.orEmpty().ifBlank { seat.identityId.ifBlank { "未知身份" } }
                        val teamName = seat.teamId.ifBlank { identity?.teamId.orEmpty() }.ifBlank { "未知阵营" }
                        "${seat.displayName.ifBlank { seat.seatId }}：$teamName / $identityName"
                    }
                    val result = resultLines.ifEmpty { listOf("没有有效查验目标") }.joinToString("；")
                    resolve(input, GameActionStatus.RESOLVED, result)
                    publicLines += publicHandled(input, "查验结果已私密发送")
                    resultMessages += GameRuntimeMessageDraft("【查验结果】$result。", input.privateChannel(), GROUP_VISIBILITY_PRIVATE)
                }
                GameAbilityEffect.REVIVE -> {
                    val reviveIds = input.targetSeats.map { it.seatId }.toSet()
                    currentSeats = currentSeats.map { seat ->
                        if (seat.seatId in reviveIds) seat.copy(status = GameSeatStatus.ALIVE) else seat
                    }
                    val result = if (input.targetSeats.isEmpty()) {
                        "复活行动已记录"
                    } else {
                        "复活：${input.targetSeats.joinToString("、") { seat -> seat.displayName.ifBlank { seat.seatId } }}"
                    }
                    resolve(input, GameActionStatus.RESOLVED, result)
                    publicLines += publicHandled(input, result)
                }
                GameAbilityEffect.SET_FLAG -> {
                    val targetText = input.targetLabel()
                    input.targetSeats.forEach { seat ->
                        flags["flag:${seat.seatId}:${input.record.draft.abilityId}"] = input.record.draft.reason.ifBlank { "true" }
                    }
                    val result = "${input.abilityName()} 已记录：$targetText"
                    resolve(input, GameActionStatus.RESOLVED, result)
                    publicLines += publicHandled(input, result)
                }
                GameAbilityEffect.BLOCK,
                GameAbilityEffect.MESSAGE_PRIVATE,
                GameAbilityEffect.REVEAL,
                null,
                -> {
                    val result = "${input.abilityName()} 已记录：${input.targetLabel()}"
                    resolve(input, GameActionStatus.RESOLVED, result)
                    publicLines += publicHandled(input, result)
                }
                GameAbilityEffect.PROTECT,
                GameAbilityEffect.ELIMINATE,
                GameAbilityEffect.VOTE,
                -> Unit
            }
        }

    val updatedState = state.copy(
        actions = state.actions.map { existing -> resolvedRecords[existing.id] ?: existing },
        scores = scores.filterValues { it != 0 },
        flags = flags,
    ).withAppendedGameEvents(resolutionEvents)
    return GameActionBatchOutcome(
        state = updatedState,
        seats = currentSeats,
        records = resolvedRecords.values.toList(),
        publicLines = publicLines.filter { it.isNotBlank() },
        resultMessages = resultMessages,
        events = resolutionEvents,
    )
}

private fun GameProfile.resolveVotes(
    inputs: List<GameActionInput>,
    rules: GameRuleBook,
    blocked: (GameActionInput) -> Boolean,
    currentSeats: List<GameSeat>,
    updateSeats: (List<GameSeat>) -> Unit,
    scores: MutableMap<String, Int>,
    resolve: (GameActionInput, GameActionStatus, String) -> Unit,
    reject: (GameActionInput, String) -> Unit,
    publicLines: MutableList<String>,
) {
    if (inputs.isEmpty()) return
    var seatsAfterVote = currentSeats
    val validVotes = mutableListOf<Pair<GameActionInput, GameSeat>>()
    inputs.forEach { input ->
        when {
            blocked(input) -> reject(input, "行动者被阻止")
            input.targetSeats.isEmpty() -> resolve(input, GameActionStatus.RESOLVED, "投票未指定有效目标")
            else -> {
                val target = input.targetSeats.first()
                validVotes += input to target
                resolve(input, GameActionStatus.RESOLVED, "投票已计入：${target.displayName.ifBlank { target.seatId }}")
            }
        }
    }
    if (validVotes.isEmpty()) {
        publicLines += "【投票结果】没有有效投票目标。"
        return
    }

    val voteCounts = validVotes
        .groupingBy { it.second.seatId }
        .eachCount()
    val maxVotes = voteCounts.values.maxOrNull() ?: 0
    val topSeatIds = voteCounts.filterValues { it == maxVotes }.keys.toList()
    val topTargets = topSeatIds.mapNotNull { seatId -> currentSeats.firstOrNull { it.seatId == seatId } }
    val isTie = topSeatIds.size > 1
    val majorityMet = maxVotes > validVotes.size / 2
    val winners = when {
        isTie && rules.voteTiePolicy == GameTiePolicy.NO_EFFECT -> emptyList()
        isTie && rules.voteTiePolicy == GameTiePolicy.FIRST -> topTargets.take(1)
        else -> topTargets
    }
    val revealVoteTally = validVotes.all { (input, _) -> input.record.draft.isPublicAction() }
    val voteCountText = if (revealVoteTally) {
        voteCounts
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { seatName(it.key) })
            .joinToString("、") { (seatId, count) -> "${seatName(seatId)} $count 票" }
    } else {
        "共 ${validVotes.size} 票"
    }
    val ballotText = if (revealVoteTally) {
        validVotes.joinToString("、") { (input, target) ->
            "${input.actorName()} 投 ${target.displayName.ifBlank { target.seatId }}"
        }
    } else {
        ""
    }
    val voteSummary = listOf(
        ballotText.takeIf { it.isNotBlank() }?.let { "唱票：$it" },
        "票数：$voteCountText",
    ).filterNotNull().joinToString("。")

    val resultLine = when (rules.voteEffect) {
        GameVoteEffect.PLURALITY_OUT,
        GameVoteEffect.MAJORITY_OUT,
        -> {
            val canApply = winners.isNotEmpty() && (rules.voteEffect == GameVoteEffect.PLURALITY_OUT || majorityMet)
            if (canApply) {
                val winnerIds = winners.map { it.seatId }.toSet()
                seatsAfterVote = currentSeats.map { seat -> if (seat.seatId in winnerIds) seat.copy(status = GameSeatStatus.OUT) else seat }
                updateSeats(seatsAfterVote)
                "【投票结果】$voteSummary。${winners.joinToString("、") { it.displayName.ifBlank { it.seatId } }} 最高票，出局。"
            } else if (isTie) {
                "【投票结果】$voteSummary。平票，无人出局。"
            } else {
                "【投票结果】$voteSummary。未达到多数，无人出局。"
            }
        }
        GameVoteEffect.SCORE_TARGET -> {
            voteCounts.forEach { (seatId, count) -> scores.addScore(seatId, rules.voteTargetPoints * count) }
            "【投票结果】$voteSummary。目标按票数获得积分。"
        }
        GameVoteEffect.SCORE_ACTOR -> {
            validVotes.forEach { (input, _) -> scores.addScore(input.record.draft.actorSeatId, rules.voteActorPoints) }
            "【投票结果】$voteSummary。投票者获得积分。"
        }
        GameVoteEffect.RECORD_ONLY -> "【投票结果】$voteSummary。结果已记录。"
    }
    publicLines += resultLine
}

private fun GameProfile.ruleBook(): GameRuleBook {
    val root = runCatching {
        JsonParser.parseString(winConditionJson.ifBlank { "{}" }).asJsonObject
    }.getOrNull() ?: JsonObject()
    val scoring = root.obj("scoring")
    val eliminate = root.obj("eliminate")
    val protect = root.obj("protect")
    val vote = root.obj("vote")
    val ruleText = listOf(templateId, publicRules, winConditionJson)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    val mentionsScore = scoring?.bool("enabled") == true ||
        ruleText.contains("积分") ||
        ruleText.contains("得分") ||
        ruleText.contains("score") ||
        ruleText.contains("point")
    val noOut = ruleText.contains("不出局") ||
        ruleText.contains("不淘汰") ||
        ruleText.contains("不死亡") ||
        ruleText.contains("score only") ||
        ruleText.contains("no elimination")
    val forceOut = ruleText.contains("出局") ||
        ruleText.contains("淘汰") ||
        ruleText.contains("放逐") ||
        ruleText.contains("eliminate") ||
        ruleText.contains("exile")
    val scoreLimit = root.intOrNull("scoreLimit")
        ?: root.intOrNull("winScore")
        ?: scoring?.intOrNull("scoreLimit")
        ?: scoring?.intOrNull("winScore")
        ?: ruleText.scoreLimitFromText()
    val eliminateEffect = eliminate?.string("effect")?.toEliminateEffect()
        ?: eliminate?.string("mode")?.toEliminateEffect()
        ?: when {
            templateId == "werewolf_like" -> GameEliminateEffect.OUT
            mentionsScore && noOut -> GameEliminateEffect.SCORE
            mentionsScore && forceOut -> GameEliminateEffect.OUT_AND_SCORE
            mentionsScore -> GameEliminateEffect.OUT_AND_SCORE
            else -> GameEliminateEffect.OUT
        }
    val voteEffect = vote?.string("effect")?.toVoteEffect()
        ?: vote?.string("mode")?.toVoteEffect()
        ?: when {
            templateId == "werewolf_like" -> GameVoteEffect.PLURALITY_OUT
            ruleText.contains("投票淘汰") || ruleText.contains("投票放逐") || ruleText.contains("vote exile") -> GameVoteEffect.PLURALITY_OUT
            ruleText.contains("投票得分") || ruleText.contains("投票积分") || ruleText.contains("vote score") -> GameVoteEffect.SCORE_ACTOR
            else -> GameVoteEffect.PLURALITY_OUT
        }
    return GameRuleBook(
        protectBlocksEliminate = protect?.bool("blocksEliminate") ?: root.bool("protectBlocksEliminate") ?: true,
        preventConsecutiveProtectSameTarget = protect?.bool("preventConsecutiveSameTarget")
            ?: root.bool("preventConsecutiveProtectSameTarget")
            ?: ruleText.contains("不能连续守"),
        eliminateEffect = eliminateEffect,
        eliminateActorPoints = eliminate?.intOrNull("actorPoints")
            ?: scoring?.intOrNull("eliminateActorPoints")
            ?: scoring?.intOrNull("killPoints")
            ?: if (mentionsScore) 1 else 0,
        eliminateTargetPoints = eliminate?.intOrNull("targetPoints")
            ?: scoring?.intOrNull("eliminateTargetPoints")
            ?: 0,
        voteEffect = voteEffect,
        voteActorPoints = vote?.intOrNull("actorPoints") ?: scoring?.intOrNull("voteActorPoints") ?: if (mentionsScore) 1 else 0,
        voteTargetPoints = vote?.intOrNull("targetPoints") ?: scoring?.intOrNull("voteTargetPoints") ?: if (mentionsScore) 1 else 0,
        voteTiePolicy = vote?.string("tie")?.toTiePolicy()
            ?: vote?.string("tiePolicy")?.toTiePolicy()
            ?: if (ruleText.contains("平票全员") || ruleText.contains("tie all")) GameTiePolicy.ALL else GameTiePolicy.NO_EFFECT,
        scoreLimit = scoreLimit?.takeIf { it > 0 },
    )
}

private fun GameActionInput.actorName(): String =
    record.draft.actorName.ifBlank { record.draft.actorRoleId.ifBlank { "玩家" } }

private fun GameActionInput.abilityName(): String =
    ability?.name.orEmpty().ifBlank { ability?.id.orEmpty().ifBlank { "行动" } }

private fun GameActionInput.targetLabel(): String =
    targetSeats.map { it.displayName.ifBlank { it.seatId } }.takeIf { it.isNotEmpty() }?.joinToString("、")
        ?: record.draft.targetName.takeIf { it.isNotBlank() }
        ?: "无指定目标"

private fun GameActionInput.privateChannel(): String =
    record.draft.actorSeatId
        .takeIf { it.isNotBlank() }
        ?.let { "private_$it" }
        ?: record.draft.channelId.ifBlank { GROUP_CHANNEL_JUDGE }

private fun publicHandled(input: GameActionInput, extra: String = ""): String = buildString {
    append("【游戏记录】")
    if (input.record.draft.isPublicAction()) {
        append(input.actorName()).append(" 的「").append(input.abilityName()).append("」已处理")
        val targetNames = input.targetSeats.map { it.displayName.ifBlank { it.seatId } }.distinct()
        if (targetNames.isNotEmpty()) append("，目标：").append(targetNames.joinToString("、"))
    } else {
        append("一项隐藏行动已处理")
    }
    if (input.record.draft.isPublicAction() && extra.isNotBlank()) append("，").append(extra)
    append("。")
}

private fun buildEliminateResult(
    input: GameActionInput,
    outTargets: List<GameSeat>,
    protectedTargets: List<GameSeat>,
    actorPoints: Int,
): String {
    val parts = mutableListOf<String>()
    if (outTargets.isNotEmpty()) parts += "出局：${outTargets.joinToString("、") { it.displayName.ifBlank { it.seatId } }}"
    if (protectedTargets.isNotEmpty()) parts += "被保护：${protectedTargets.joinToString("、") { it.displayName.ifBlank { it.seatId } }}"
    if (actorPoints != 0) parts += "${input.actorName()} ${if (actorPoints > 0) "+" else ""}$actorPoints 分"
    return parts.ifEmpty { listOf(if (input.targetSeats.isEmpty()) "没有有效目标" else "行动已记录") }.joinToString("；")
}

private fun eliminatePublicLine(
    input: GameActionInput,
    outTargets: List<GameSeat>,
    protectedTargets: List<GameSeat>,
    actorPoints: Int,
    rules: GameRuleBook,
): String {
    val result = buildEliminateResult(input, outTargets, protectedTargets, actorPoints)
    return when {
        input.record.draft.isPublicAction() -> publicHandled(input, result)
        outTargets.isNotEmpty() -> "【游戏结算】${outTargets.joinToString("、") { it.displayName.ifBlank { it.seatId } }} 出局。"
        protectedTargets.isNotEmpty() -> "【游戏结算】本次隐藏击杀未造成出局。"
        rules.eliminateEffect in setOf(GameEliminateEffect.SCORE, GameEliminateEffect.OUT_AND_SCORE) && actorPoints != 0 ->
            "【游戏结算】积分已更新。"
        else -> publicHandled(input, result)
    }
}

private fun MutableMap<String, Int>.addScore(seatId: String, amount: Int) {
    if (seatId.isBlank() || amount == 0) return
    this[seatId] = (this[seatId] ?: 0) + amount
}

private fun GameProfile.seatName(seatId: String): String =
    seats.firstOrNull { it.seatId == seatId }?.displayName?.ifBlank { seatId } ?: seatId

private fun String.toEliminateEffect(): GameEliminateEffect? =
    when (trim().lowercase(Locale.ROOT)) {
        "out", "eliminate", "elimination", "kill", "淘汰", "出局", "击杀" -> GameEliminateEffect.OUT
        "score", "points", "积分", "得分" -> GameEliminateEffect.SCORE
        "out_and_score", "score_and_out", "eliminate_and_score", "击杀得分", "淘汰得分" -> GameEliminateEffect.OUT_AND_SCORE
        "record", "record_only", "none", "仅记录" -> GameEliminateEffect.RECORD_ONLY
        else -> null
    }

private fun String.toVoteEffect(): GameVoteEffect? =
    when (trim().lowercase(Locale.ROOT)) {
        "plurality_out", "plurality", "exile", "放逐", "最高票出局" -> GameVoteEffect.PLURALITY_OUT
        "majority_out", "majority", "多数出局" -> GameVoteEffect.MAJORITY_OUT
        "score_target", "target_score", "目标得分" -> GameVoteEffect.SCORE_TARGET
        "score_actor", "actor_score", "voter_score", "投票者得分" -> GameVoteEffect.SCORE_ACTOR
        "record", "record_only", "none", "仅记录" -> GameVoteEffect.RECORD_ONLY
        else -> null
    }

private fun String.toTiePolicy(): GameTiePolicy? =
    when (trim().lowercase(Locale.ROOT)) {
        "none", "no_effect", "noeffect", "无人", "无效" -> GameTiePolicy.NO_EFFECT
        "all", "全员", "全部" -> GameTiePolicy.ALL
        "first", "首个", "先到" -> GameTiePolicy.FIRST
        else -> null
    }

private fun String.scoreLimitFromText(): Int? {
    val patterns = listOf(
        Regex("""(?:达到|满|先到|获得)\s*(\d+)\s*(?:分|积分)"""),
        Regex("""(?:score\s*limit|win\s*score|first\s*to)\s*(\d+)""", RegexOption.IGNORE_CASE),
    )
    return patterns.firstNotNullOfOrNull { regex ->
        regex.find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}

private fun GameProfile.resolutionPriority(record: GameActionRecord): Int =
    when (abilities.firstOrNull { it.id == record.draft.abilityId }?.effect) {
        GameAbilityEffect.PROTECT -> 10
        GameAbilityEffect.BLOCK -> 15
        GameAbilityEffect.REVIVE -> 20
        GameAbilityEffect.ELIMINATE -> 30
        GameAbilityEffect.INSPECT -> 40
        GameAbilityEffect.VOTE -> 50
        GameAbilityEffect.REVEAL -> 60
        GameAbilityEffect.MESSAGE_PRIVATE -> 70
        GameAbilityEffect.SET_FLAG -> 80
        null -> 90
    }

private fun GameRuntimeState.protectedTargetSeatIds(phaseId: String, profile: GameProfile): Set<String> =
    actions
        .filter { it.phaseId == phaseId && it.status == GameActionStatus.RESOLVED }
        .filter { record ->
            profile.abilities.firstOrNull { it.id == record.draft.abilityId }?.effect == GameAbilityEffect.PROTECT
        }
        .flatMap { it.draft.targetSeatIds }
        .toSet()

private fun GameProfile.targetSeatsFor(draft: GameActionDraft): List<GameSeat> {
    val targetSeatIds = draft.targetSeatIds.toSet()
    val targetActorIds = draft.targetActorIds.toSet()
    return seats
        .filter { seat ->
            seat.seatId in targetSeatIds ||
                seat.actorId in targetActorIds ||
                (draft.targetName.isNotBlank() && seat.displayName.equals(draft.targetName, ignoreCase = true))
        }
        .distinctBy { it.seatId }
}

private fun GameActionDraft.isPublicAction(): Boolean =
    visibility.equals(GROUP_VISIBILITY_PUBLIC, ignoreCase = true) ||
        channelId.equals(GROUP_CHANNEL_PUBLIC, ignoreCase = true)

internal fun Group.channelForUserGameAbility(ability: GameAbility): String {
    return channelForGameAbility(userGameSeat(), ability)
}

internal fun Group.channelForGameAbility(seat: GameSeat?, ability: GameAbility): String {
    val profile = gameProfile ?: return GROUP_CHANNEL_PUBLIC
    return when (ability.visibility.lowercase(Locale.ROOT)) {
        GROUP_VISIBILITY_PUBLIC -> GROUP_CHANNEL_PUBLIC
        GROUP_VISIBILITY_TEAM -> profile.channels.firstOrNull { channel ->
            channel.kind == GameChannelKind.TEAM && seat != null && seat.seatId in channel.memberSeatIds
        }?.id ?: seat?.teamId?.takeIf { it.isNotBlank() }?.let { "team_$it" } ?: GROUP_CHANNEL_JUDGE
        GROUP_VISIBILITY_PRIVATE -> seat?.seatId?.let { "private_$it" } ?: GROUP_CHANNEL_JUDGE
        GROUP_VISIBILITY_JUDGE -> GROUP_CHANNEL_JUDGE
        else -> GROUP_CHANNEL_JUDGE
    }
}

internal fun GameActionRecord.compactGameActionTitle(profile: GameProfile?): String {
    val abilityName = profile?.abilities?.firstOrNull { it.id == draft.abilityId }?.name
        ?: draft.abilityId.ifBlank { "action" }
    val actorName = draft.actorName.ifBlank { draft.actorRoleId.ifBlank { "AI" } }
    return "$actorName · $abilityName"
}

internal fun GameActionRecord.compactGameActionMeta(profile: GameProfile?): String {
    val phaseName = profile?.phases?.firstOrNull { it.id == phaseId }?.name.orEmpty()
    val targets = (draft.targetSeatIds + draft.targetActorIds + listOf(draft.targetName))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return listOfNotNull(
        phaseName.takeIf { it.isNotBlank() },
        targets.takeIf { it.isNotEmpty() }?.joinToString("、"),
        draft.reason.take(36).takeIf { it.isNotBlank() },
    ).joinToString(" / ")
}

private fun JsonObject.toGameActionRecord(): GameActionRecord {
    val draftObj = get("draft")?.asObjectOrNull()
    val status = get("status")?.asStringOrNull()
        ?.uppercase(Locale.ROOT)
        ?.let { runCatching { GameActionStatus.valueOf(it) }.getOrNull() }
        ?: GameActionStatus.PENDING
    return GameActionRecord(
        id = string("id"),
        phaseId = string("phaseId"),
        draft = draftObj?.toGameActionDraft() ?: GameActionDraft(),
        status = status,
        resultText = string("resultText"),
        createdAt = long("createdAt", System.currentTimeMillis()),
        updatedAt = long("updatedAt", long("createdAt", System.currentTimeMillis())),
    )
}

private fun JsonObject.toGameActionDraft(): GameActionDraft =
    GameActionDraft(
        abilityId = string("abilityId"),
        actorSeatId = string("actorSeatId"),
        actorRoleId = string("actorRoleId"),
        actorName = string("actorName"),
        targetSeatIds = stringList("targetSeatIds"),
        targetActorIds = stringList("targetActorIds"),
        targetName = string("targetName"),
        channelId = string("channelId").ifBlank { GROUP_CHANNEL_JUDGE },
        visibility = string("visibility").ifBlank { GROUP_VISIBILITY_JUDGE },
        reason = string("reason"),
        rawText = string("rawText"),
        createdAt = long("createdAt", System.currentTimeMillis()),
    )

private fun JsonObject.toGameEvent(): GameEvent {
    val type = string("type")
        .uppercase(Locale.ROOT)
        .let { runCatching { GameEventType.valueOf(it) }.getOrNull() }
        ?: GameEventType.RESULT_PUBLISHED
    return GameEvent(
        id = string("id"),
        type = type,
        roundIndex = int("roundIndex", 1).coerceAtLeast(1),
        phaseId = string("phaseId"),
        actorSeatId = string("actorSeatId"),
        actorRoleId = string("actorRoleId"),
        actorName = string("actorName"),
        abilityId = string("abilityId"),
        targetSeatIds = stringList("targetSeatIds"),
        targetActorIds = stringList("targetActorIds"),
        targetName = string("targetName"),
        actionRecordId = string("actionRecordId"),
        channelId = string("channelId"),
        visibility = string("visibility").ifBlank { GROUP_VISIBILITY_PUBLIC },
        text = string("text"),
        resultText = string("resultText"),
        metadataJson = string("metadataJson").ifBlank { "{}" },
        createdAt = long("createdAt", System.currentTimeMillis()),
    )
}

private fun JsonObject.string(name: String): String =
    get(name)?.asStringOrNull().orEmpty()

private fun JsonObject.int(name: String, fallback: Int): Int =
    get(name)?.asIntOrNull() ?: fallback

private fun JsonObject.intOrNull(name: String): Int? =
    get(name)?.asIntOrNull()

private fun JsonObject.long(name: String, fallback: Long): Long =
    get(name)?.asLongOrNull() ?: fallback

private fun JsonObject.bool(name: String): Boolean? =
    get(name)?.asBooleanOrNull()

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.asObjectOrNull()

private fun JsonObject.array(name: String): List<JsonElement> =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

private fun JsonObject.intMap(name: String): Map<String, Int> =
    get(name)
        ?.asObjectOrNull()
        ?.entrySet()
        ?.mapNotNull { (key, value) -> value.asIntOrNull()?.let { key to it } }
        ?.toMap()
        .orEmpty()

private fun JsonObject.stringMap(name: String): Map<String, String> =
    get(name)
        ?.asObjectOrNull()
        ?.entrySet()
        ?.mapNotNull { (key, value) -> value.asStringOrNull()?.let { key to it } }
        ?.toMap()
        .orEmpty()

private fun JsonObject.stringList(name: String): List<String> =
    get(name)?.let { element ->
        when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull()?.trim() }
            else -> element.asStringOrNull()?.split(',', '，', ';', '；', '\n')?.map { it.trim() }
        }
    }
        .orEmpty()
        .filter { it.isNotBlank() }
        .distinct()

private fun JsonElement.asObjectOrNull(): JsonObject? =
    if (isJsonObject) asJsonObject else null

private fun JsonElement.asStringOrNull(): String? =
    runCatching { if (isJsonPrimitive) asString else null }.getOrNull()

private fun JsonElement.asIntOrNull(): Int? =
    runCatching { if (isJsonPrimitive) asInt else null }.getOrNull()

private fun JsonElement.asLongOrNull(): Long? =
    runCatching { if (isJsonPrimitive) asLong else null }.getOrNull()

private fun JsonElement.asBooleanOrNull(): Boolean? =
    runCatching { if (isJsonPrimitive) asBoolean else null }.getOrNull()
