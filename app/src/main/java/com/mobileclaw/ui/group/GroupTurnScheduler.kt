package com.mobileclaw.ui.group

import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleScheduler
import com.mobileclaw.agent.TaskType

internal data class GroupTurnLaunch(
    val role: Role,
    val delayMs: Long = 0,
    val chainDepth: Int = 0,
    val longTask: Boolean = false,
    val triggerText: String = "",
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

    suspend fun buildInitialTurns(allMembers: List<Role>, userText: String): List<GroupTurnLaunch> {
        val taskType = resolveTaskType(userText)
        val schedulingText = buildMemoryContext(userText, taskType)
        if (taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH)) {
            val selected = RoleScheduler.schedule(taskType, schedulingText, allMembers, allMembers.first()).role
                .takeIf { role -> allMembers.any { it.id == role.id } }
                ?: allMembers.first()
            return listOf(
                GroupTurnLaunch(
                    role = selected,
                    delayMs = 0,
                    chainDepth = 0,
                    longTask = true,
                    triggerText = userText,
                    requireResponse = true,
                ),
            )
        }

        val mentioned = parseMentions(userText)
        if (mentioned.isNotEmpty()) {
            return allMembers
                .filter { role -> mentioned.any { mention -> role.name.contains(mention, ignoreCase = true) || mention.contains(role.name, ignoreCase = true) } }
                .mapIndexed { index, role ->
                    GroupTurnLaunch(
                        role = role,
                        delayMs = 0,
                        chainDepth = 5,
                        triggerText = userText,
                        requireResponse = index == 0,
                    )
                }
        }

        val shuffled = allMembers.shuffled()
        val activeCount = when {
            shuffled.size <= 1 -> 1
            shouldInviteMultipleGroupVoices(userText) -> minOf(2, shuffled.size)
            else -> initialFanOut
        }
        return shuffled.take(activeCount).mapIndexed { idx, role ->
            GroupTurnLaunch(
                role = role,
                delayMs = when (idx) {
                    0 -> (200L..800L).random()
                    1 -> (1500L..3000L).random()
                    else -> (3000L..5500L).random()
                },
                chainDepth = if (idx == 0) 5 else 2,
                triggerText = userText,
                requireResponse = idx == 0,
            )
        }
    }

    suspend fun enqueueUserTurn(allMembers: List<Role>, userText: String): List<String> {
        val taskType = resolveTaskType(userText)
        val schedulingText = buildMemoryContext(userText, taskType)
        val mentioned = parseMentions(userText)
        val targets = when {
            taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH) -> {
                val selected = RoleScheduler.schedule(taskType, schedulingText, allMembers, allMembers.first()).role
                listOfNotNull(allMembers.firstOrNull { it.id == selected.id } ?: allMembers.firstOrNull())
            }
            mentioned.isNotEmpty() -> allMembers.filter { role ->
                mentioned.any { mention -> role.name.contains(mention, ignoreCase = true) || mention.contains(role.name, ignoreCase = true) }
            }
            else -> allMembers.shuffled().take(if (shouldInviteMultipleGroupVoices(userText)) minOf(2, allMembers.size) else 1)
        }

        targets.forEach { role ->
            pendingTurns.addFirst(
                PendingGroupTurn(
                    roleId = role.id,
                    triggerText = userText,
                    chainDepth = if (mentioned.isNotEmpty()) 3 else 1,
                    priority = 100,
                    longTask = taskType !in listOf(TaskType.CHAT, TaskType.GENERAL, TaskType.WEB_RESEARCH),
                    requireResponse = true,
                    queuedUserText = userText,
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
        val availableSlots = (taskPoolLimit - busyRoleIds.size).coerceAtLeast(0)
        repeat(availableSlots) {
            val nextTurn = pendingTurns.pollHighestPriority { roleId -> roleId !in busyRoleIds && launches.none { launch -> launch.role.id == roleId } }
                ?: return@repeat
            val nextRole = allMembers.firstOrNull { it.id == nextTurn.roleId } ?: return@repeat
            launches += GroupTurnLaunch(
                role = nextRole,
                chainDepth = nextTurn.chainDepth,
                longTask = nextTurn.longTask,
                triggerText = nextTurn.triggerText,
                requireResponse = nextTurn.requireResponse,
            )
        }
        return GroupTurnDrainBatch(launches = launches, pendingMessages = snapshotPendingMessages())
    }
}
