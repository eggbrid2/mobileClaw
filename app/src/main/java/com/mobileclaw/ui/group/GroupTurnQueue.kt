package com.mobileclaw.ui.group

internal data class PendingGroupTurn(
    val roleId: String,
    val triggerText: String,
    val chainDepth: Int,
    val priority: Int,
    val longTask: Boolean,
    val requireResponse: Boolean = false,
    val queuedUserText: String? = null,
)

internal class GroupTurnQueue {
    private val lock = Any()
    private val turns = ArrayDeque<PendingGroupTurn>()

    fun snapshotQueuedUserMessages(): List<String> =
        synchronized(lock) {
            turns.mapNotNull { it.queuedUserText }.distinct()
        }

    fun addFirst(turn: PendingGroupTurn) {
        synchronized(lock) {
            turns.addFirst(turn)
        }
    }

    fun addLast(turn: PendingGroupTurn) {
        synchronized(lock) {
            turns.addLast(turn)
        }
    }

    fun clear() {
        synchronized(lock) {
            turns.clear()
        }
    }

    fun isEmpty(): Boolean =
        synchronized(lock) { turns.isEmpty() }

    fun pollHighestPriority(allowRole: (String) -> Boolean): PendingGroupTurn? =
        synchronized(lock) {
            val index = turns
                .withIndex()
                .filter { (_, turn) -> allowRole(turn.roleId) }
                .maxByOrNull { (_, turn) -> turn.priority }
                ?.index ?: return@synchronized null
            turns.removeAt(index)
        }
}
