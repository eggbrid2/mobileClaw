package com.mobileclaw.agent

enum class GroupMode {
    FREE_CHAT,
    ARENA,
    DEBATE,
    WORKSHOP,
    ROUNDED_GAME,
}

enum class GroupTurnStyle {
    QUIET,
    BALANCED,
    ACTIVE,
}

enum class GroupKind {
    CHAT,
    GAME,
}

enum class GameActorType {
    USER,
    AI_ROLE,
}

enum class GameUserRole {
    HOST,
    SPECTATOR,
    PLAYER,
    CO_HOST,
}

enum class GameIdentityAssignment {
    RANDOM,
    MANUAL,
    MIXED,
}

enum class GameJudgeFlowMode {
    FREE,
    ORDERED,
    CALLED,
    PHASE,
}

enum class GameUserActionPolicy {
    OPEN,
    PHASE,
    JUDGE_CONTROLLED,
}

enum class GameSeatStatus {
    READY,
    ALIVE,
    OUT,
    SPECTATING,
}

enum class GameKnowledgeScope {
    PUBLIC_ONLY,
    SELF_SECRET,
    TEAM_SECRET,
    GLOBAL,
}

enum class GameAbilityTrigger {
    PHASE_ACTION,
    TEAM_MEETING,
    VOTE,
    ON_DEATH,
    PASSIVE,
}

enum class GameAbilityEffect {
    ELIMINATE,
    PROTECT,
    INSPECT,
    BLOCK,
    REVIVE,
    VOTE,
    MESSAGE_PRIVATE,
    SET_FLAG,
    REVEAL,
}

enum class GameChannelKind {
    PUBLIC,
    TEAM,
    PRIVATE,
    DEAD,
    JUDGE,
}

enum class GameActionStatus {
    PENDING,
    RESOLVED,
    IGNORED,
    REJECTED,
}

enum class GameEventType {
    PHASE_ADVANCED,
    ACTION_SUBMITTED,
    ACTION_RESOLVED,
    VOTE_SUBMITTED,
    RESULT_PUBLISHED,
    CONTROL_CHANGED,
    SPEAKER_CALLED,
}

data class GameAbility(
    val id: String,
    val name: String,
    val trigger: GameAbilityTrigger = GameAbilityTrigger.PHASE_ACTION,
    val effect: GameAbilityEffect = GameAbilityEffect.SET_FLAG,
    val usageLimit: Int = 1,
    val targetRule: String = "",
    val visibility: String = "private",
    val description: String = "",
)

data class GameIdentity(
    val id: String,
    val name: String,
    val teamId: String = "",
    val publicHint: String = "",
    val privatePrompt: String = "",
    val abilityIds: List<String> = emptyList(),
    val winConditionTags: List<String> = emptyList(),
)

data class GameSeat(
    val seatId: String,
    val actorType: GameActorType,
    val actorId: String,
    val displayName: String,
    val identityId: String = "",
    val teamId: String = "",
    val status: GameSeatStatus = GameSeatStatus.READY,
    val abilityIds: List<String> = emptyList(),
    val knowledgeScope: GameKnowledgeScope = GameKnowledgeScope.SELF_SECRET,
)

data class GameChannel(
    val id: String,
    val name: String,
    val kind: GameChannelKind,
    val memberSeatIds: List<String> = emptyList(),
    val visibleToHost: Boolean = true,
)

data class GamePhase(
    val id: String,
    val name: String,
    val order: Int,
    val channelIds: List<String> = emptyList(),
    val enabledAbilityIds: List<String> = emptyList(),
)

data class GameProfile(
    val templateId: String = "",
    val templateName: String = "",
    val userRole: GameUserRole = GameUserRole.HOST,
    val identityAssignment: GameIdentityAssignment = GameIdentityAssignment.RANDOM,
    val seats: List<GameSeat> = emptyList(),
    val identities: List<GameIdentity> = emptyList(),
    val abilities: List<GameAbility> = emptyList(),
    val phases: List<GamePhase> = emptyList(),
    val channels: List<GameChannel> = emptyList(),
    val currentPhaseId: String = "",
    val publicRules: String = "",
    val hiddenStateJson: String = "{}",
    val winConditionJson: String = "{}",
    val judgeFlowMode: GameJudgeFlowMode = GameJudgeFlowMode.PHASE,
    val userActionPolicy: GameUserActionPolicy = GameUserActionPolicy.PHASE,
    val autoHost: Boolean = true,
)

data class GameActionDraft(
    val abilityId: String = "",
    val actorSeatId: String = "",
    val actorRoleId: String = "",
    val actorName: String = "",
    val targetSeatIds: List<String> = emptyList(),
    val targetActorIds: List<String> = emptyList(),
    val targetName: String = "",
    val channelId: String = "judge",
    val visibility: String = "judge",
    val reason: String = "",
    val rawText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class GameRuntimeControlDraft(
    val speechOpen: Boolean? = null,
    val calledSeatId: String = "",
    val calledActorId: String = "",
    val calledName: String = "",
    val clearCalledSeat: Boolean = false,
    val nextOrderedSpeaker: Boolean = false,
    val enabledAbilityIds: List<String> = emptyList(),
    val replaceEnabledAbilityIds: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class GameActionRecord(
    val id: String = "",
    val phaseId: String = "",
    val draft: GameActionDraft = GameActionDraft(),
    val status: GameActionStatus = GameActionStatus.PENDING,
    val resultText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

data class GameEvent(
    val id: String = "",
    val type: GameEventType = GameEventType.RESULT_PUBLISHED,
    val roundIndex: Int = 1,
    val phaseId: String = "",
    val actorSeatId: String = "",
    val actorRoleId: String = "",
    val actorName: String = "",
    val abilityId: String = "",
    val targetSeatIds: List<String> = emptyList(),
    val targetActorIds: List<String> = emptyList(),
    val targetName: String = "",
    val actionRecordId: String = "",
    val channelId: String = "",
    val visibility: String = "public",
    val text: String = "",
    val resultText: String = "",
    val metadataJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)

data class GameRuntimeState(
    val roundIndex: Int = 1,
    val phaseStartedAt: Long = 0L,
    val actions: List<GameActionRecord> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val scores: Map<String, Int> = emptyMap(),
    val flags: Map<String, String> = emptyMap(),
)

data class Group(
    val id: String,
    val name: String,
    val emoji: String = "group",
    val memberRoleIds: List<String>,   // ordered; "user" is always implicitly present
    val kind: GroupKind = GroupKind.CHAT,
    val mode: GroupMode = GroupMode.FREE_CHAT,
    val topic: String = "",
    val openingPrompt: String = "",
    val rules: String = "",
    val roundLimit: Int = 3,
    val turnStyle: GroupTurnStyle = GroupTurnStyle.BALANCED,
    val autoStart: Boolean = false,
    val judgeRoleId: String = "",
    val gameProfile: GameProfile? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
