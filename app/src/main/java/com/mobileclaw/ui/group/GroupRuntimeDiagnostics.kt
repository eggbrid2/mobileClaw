package com.mobileclaw.ui.group

import com.mobileclaw.agent.Group
import com.mobileclaw.agent.Role
import com.mobileclaw.memory.db.GroupMessageDao

internal class GroupRuntimeDiagnostics(
    private val groupMessageDao: GroupMessageDao,
    private val groupHistoryStore: GroupHistoryStore,
    private val rolesProvider: () -> List<Role>,
) {
    suspend fun log(
        marker: String,
        groups: List<Group>,
        activeGroup: Group? = null,
        activeMessages: List<GroupMessage> = emptyList(),
        activeBackupMessages: List<GroupMessage> = emptyList(),
    ) {
        val roles = rolesProvider()
        val roleIds = roles.map { it.id }.toSet()
        val groupIds = groups.map { it.id }.toSet()
        val dbCounts = runCatching { groupMessageDao.groupCounts() }.getOrDefault(emptyList())
        val backupFiles = groupHistoryStore.historyDir().listFiles { file -> file.extension == "jsonl" }?.toList().orEmpty()
        val backupSummary = backupFiles.joinToString(limit = 20) { file ->
            val id = file.nameWithoutExtension
            "$id:${file.length()}b"
        }
        val orphanDbGroups = dbCounts.map { it.groupId }.filterNot { it in groupIds }
        val orphanBackupGroups = backupFiles.map { it.nameWithoutExtension }.filterNot { it in groupIds }
        val missingRoles = groups.associate { group ->
            group.id to group.memberRoleIds.filterNot { it in roleIds }
        }.filterValues { it.isNotEmpty() }
        val possibleHistoryMismatch = activeGroup?.takeIf { activeMessages.isEmpty() }?.let { group ->
            val sameNameGroups = groups.filter { it.name == group.name && it.id != group.id }.map { it.id }
            val orphanCandidates = (orphanDbGroups + orphanBackupGroups).distinct()
            "sameName=${sameNameGroups.joinToString(limit = 10)} orphanCandidates=${orphanCandidates.joinToString(limit = 16)}"
        }.orEmpty()
        val activeSenderSummary = activeMessages
            .groupingBy { it.senderId.ifBlank { "(blank)" } }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(limit = 16) { "${it.key}:${it.value}" }
        android.util.Log.d(
            "ClawGroup",
            "diag[$marker] groupState=it.groupState.copy(groups=${groups.size} groupIds=${groups.joinToString(limit = 20) { it.id }} " +
                "dbGroups=${dbCounts.joinToString(limit = 20) { "${it.groupId}:${it.count}@${it.latestAt}" }} " +
                "backupGroups=$backupSummary orphanDb=${orphanDbGroups.joinToString(limit = 20)} " +
                "orphanBackup=${orphanBackupGroups.joinToString(limit = 20)} missingRoles=$missingRoles " +
                "active=${activeGroup?.id.orEmpty()} activeDbOrMerged=${activeMessages.size} " +
                "activeBackup=${activeBackupMessages.size} activeSenders=$activeSenderSummary " +
                "possibleMismatch=$possibleHistoryMismatch"
        )
    }
}
