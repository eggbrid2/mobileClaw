package com.mobileclaw.ui.group

import com.mobileclaw.agent.Group
import com.mobileclaw.agent.GroupMode
import com.mobileclaw.agent.GroupTurnStyle
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleScheduler
import com.mobileclaw.agent.TaskType

internal data class GroupTurnLaunch(
    val role: Role,
    val delayMs: Long = 0,
    val chainDepth: Int = 0,
    val longTask: Boolean = false,
    val triggerText: String = "",
    val channelId: String = GROUP_CHANNEL_PUBLIC,
    val visibility: String = GROUP_VISIBILITY_PUBLIC,
    val requireResponse: Boolean = false,
)

internal data class GroupTurnDrainBatch(
    val launches: List<GroupTurnLaunch>,
    val pendingMessages: List<String>,
)

internal class GroupTurnScheduler(
    private val taskPoolLimit: Int,
    private val initialFanOut: Int,
    private val buildMemoryContext: (String, TaskType) -> String,
    private val resolveTaskType: suspend (String) -> TaskType,
) {
    private val pendingTurns = GroupTurnQueue()

    fun snapshotPendingMessages(): List<String> = pendingTurns.snapshotQueuedUserMessages()

    fun clear() {
        pendingTurns.clear()
    }

    fun isEmpty(): Boolean = pendingTurns.isEmpty()

    suspend fun buildInitialTurns(
        group: Group,
        allMembers: List<Role>,
        userText: String,
        channelId: String = GROUP_CHANNEL_PUBLIC,
        visibility: String = GROUP_VISIBILITY_PUBLIC,
    ): List<GroupTurnLaunch> {
        val candidateMembers = group.runtimeCandidateMembers(allMembers)
        if (candidateMembers.isEmpty()) return emptyList()
        val taskType = resolveTaskType(userText)
        val schedulingText = buildMemoryContext(userText, taskType)
        if (taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH)) {
            val selected = RoleScheduler.schedule(taskType, schedulingText, candidateMembers, candidateMembers.first()).role
                .takeIf { role -> candidateMembers.any { it.id == role.id } }
                ?: candidateMembers.first()
            return listOf(
                GroupTurnLaunch(
                    role = selected,
                    delayMs = 0,
                    chainDepth = 0,
                    longTask = true,
                    triggerText = userText,
                    channelId = channelId,
                    visibility = visibility,
                    requireResponse = true,
                ),
            )
        }

        val mentioned = parseMentions(userText)
        if (mentioned.isNotEmpty()) {
            return candidateMembers
                .filter { role -> mentioned.any { mention -> role.name.contains(mention, ignoreCase = true) || mention.contains(role.name, ignoreCase = true) } }
                .ifEmpty { candidateMembers.take(1) }
                .mapIndexed { index, role ->
                    GroupTurnLaunch(
                        role = role,
                        delayMs = 0,
                        chainDepth = group.initialChainDepth(),
                        triggerText = userText,
                        channelId = channelId,
                        visibility = visibility,
                        requireResponse = index == 0,
                    )
                }
        }

        val shuffled = candidateMembers.shuffled()
        val activeCount = when {
            shuffled.size <= 1 -> 1
            shouldInviteMultipleGroupVoices(userText) -> minOf(group.maxInitialVoices().coerceAtLeast(2), shuffled.size)
            group.mode != GroupMode.FREE_CHAT -> minOf(group.maxInitialVoices(), shuffled.size)
            else -> minOf(group.maxInitialVoices(), shuffled.size)
        }
        return shuffled.take(activeCount).mapIndexed { idx, role ->
            GroupTurnLaunch(
                role = role,
                delayMs = when (idx) {
                    0 -> (200L..800L).random()
                    1 -> (1500L..3000L).random()
                    else -> (3000L..5500L).random()
                },
                chainDepth = if (idx == 0) group.initialChainDepth() else group.secondaryChainDepth(),
                triggerText = userText,
                channelId = channelId,
                visibility = visibility,
                requireResponse = idx == 0,
            )
        }
    }

    suspend fun enqueueUserTurn(
        group: Group,
        allMembers: List<Role>,
        userText: String,
        channelId: String = GROUP_CHANNEL_PUBLIC,
        visibility: String = GROUP_VISIBILITY_PUBLIC,
    ): List<String> {
        val candidateMembers = group.runtimeCandidateMembers(allMembers)
        if (candidateMembers.isEmpty()) return snapshotPendingMessages()
        val taskType = resolveTaskType(userText)
        val schedulingText = buildMemoryContext(userText, taskType)
        val mentioned = parseMentions(userText)
        val targets = when {
            taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH) -> {
                val selected = RoleScheduler.schedule(taskType, schedulingText, candidateMembers, candidateMembers.first()).role
                listOfNotNull(candidateMembers.firstOrNull { it.id == selected.id } ?: candidateMembers.firstOrNull())
            }
            mentioned.isNotEmpty() -> candidateMembers.filter { role ->
                mentioned.any { mention -> role.name.contains(mention, ignoreCase = true) || mention.contains(role.name, ignoreCase = true) }
            }.ifEmpty { candidateMembers.take(1) }
            else -> candidateMembers.shuffled().take(
                if (shouldInviteMultipleGroupVoices(userText) || group.mode != GroupMode.FREE_CHAT) {
                    minOf(group.maxInitialVoices(), candidateMembers.size)
                } else {
                    1
                },
            )
        }

        targets.forEach { role ->
            pendingTurns.addFirst(
                PendingGroupTurn(
                    roleId = role.id,
                    triggerText = userText,
                    chainDepth = if (mentioned.isNotEmpty()) 3 else group.secondaryChainDepth(),
                    priority = 100,
                    longTask = taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH),
                    requireResponse = true,
                    queuedUserText = userText,
                    channelId = channelId,
                    visibility = visibility,
                ),
            )
        }
        return snapshotPendingMessages()
    }

    fun enqueueDeferredTurn(
        role: Role,
        triggerText: String,
        chainDepth: Int,
        longTask: Boolean,
        requireResponse: Boolean,
        channelId: String = GROUP_CHANNEL_PUBLIC,
        visibility: String = GROUP_VISIBILITY_PUBLIC,
    ): List<String> {
        pendingTurns.addLast(
            PendingGroupTurn(
                roleId = role.id,
                triggerText = triggerText,
                chainDepth = chainDepth,
                priority = if (longTask) 80 else 10,
                longTask = longTask,
                requireResponse = requireResponse,
                queuedUserText = triggerText.takeIf { requireResponse && it.isNotBlank() },
                channelId = channelId,
                visibility = visibility,
            ),
        )
        return snapshotPendingMessages()
    }

    fun drainPendingTurns(
        allMembers: List<Role>,
        busyRoleIds: Set<String>,
        stopped: Boolean,
    ): GroupTurnDrainBatch {
        if (stopped) return GroupTurnDrainBatch(emptyList(), snapshotPendingMessages())

        val launches = mutableListOf<GroupTurnLaunch>()
        val allowedRoleIds = allMembers.map { it.id }.toSet()
        val availableSlots = (taskPoolLimit - busyRoleIds.size).coerceAtLeast(0)
        repeat(availableSlots) {
            val nextTurn = pendingTurns.pollHighestPriority { roleId ->
                roleId in allowedRoleIds &&
                    roleId !in busyRoleIds &&
                    launches.none { launch -> launch.role.id == roleId }
            }
                ?: return@repeat
            val nextRole = allMembers.firstOrNull { it.id == nextTurn.roleId } ?: return@repeat
            launches += GroupTurnLaunch(
                role = nextRole,
                chainDepth = nextTurn.chainDepth,
                longTask = nextTurn.longTask,
                triggerText = nextTurn.triggerText,
                channelId = nextTurn.channelId,
                visibility = nextTurn.visibility,
                requireResponse = nextTurn.requireResponse,
            )
        }
        return GroupTurnDrainBatch(launches = launches, pendingMessages = snapshotPendingMessages())
    }

    private fun Group.maxInitialVoices(): Int {
        val styleCount = when (turnStyle) {
            GroupTurnStyle.QUIET -> 1
            GroupTurnStyle.BALANCED -> if (mode == GroupMode.FREE_CHAT) initialFanOut else 2
            GroupTurnStyle.ACTIVE -> if (mode == GroupMode.FREE_CHAT) 2 else 3
        }
        return styleCount.coerceAtLeast(1)
    }

    private fun Group.initialChainDepth(): Int = when (turnStyle) {
        GroupTurnStyle.QUIET -> if (mode == GroupMode.FREE_CHAT) 2 else 3
        GroupTurnStyle.BALANCED -> if (mode == GroupMode.FREE_CHAT) 5 else 6
        GroupTurnStyle.ACTIVE -> if (mode == GroupMode.FREE_CHAT) 6 else 7
    }

    private fun Group.secondaryChainDepth(): Int = when (turnStyle) {
        GroupTurnStyle.QUIET -> 1
        GroupTurnStyle.BALANCED -> if (mode == GroupMode.FREE_CHAT) 2 else 3
        GroupTurnStyle.ACTIVE -> if (mode == GroupMode.FREE_CHAT) 3 else 4
    }

    private fun Group.runtimeCandidateMembers(allMembers: List<Role>): List<Role> {
        val allowedIds = allowedRuntimeResponderRoleIds() ?: return allMembers
        return allMembers.filter { it.id in allowedIds }
    }
}
