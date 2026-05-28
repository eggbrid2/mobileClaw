package com.mobileclaw.ui.group

import com.mobileclaw.agent.Group
import com.mobileclaw.memory.db.GroupMessageDao
import com.mobileclaw.memory.db.GroupMessageEntity
import com.mobileclaw.skill.SkillAttachment

internal data class GroupOpenSnapshot(
    val total: Int,
    val dbMessages: List<GroupMessage>,
    val backupMessages: List<GroupMessage>,
    val mergedMessages: List<GroupMessage>,
)

internal data class GroupHistoryPage(
    val messages: List<GroupMessage>,
    val nextOffset: Int,
    val hasMore: Boolean,
)

internal class GroupConversationStore(
    private val groupMessageDao: GroupMessageDao,
    private val groupHistoryStore: GroupHistoryStore,
    private val deserializeAttachments: (String) -> List<SkillAttachment>,
    private val serializeAttachments: (List<SkillAttachment>) -> String,
) {
    suspend fun loadPreviews(groups: List<Group>): Map<String, GroupPreview> =
        groups.associate { group ->
            val dbLatest = groupMessageDao.latestForGroup(group.id)?.toGroupMessage(deserializeAttachments)
            val backupLatest = groupHistoryStore.readBackup(group.id).maxByOrNull { it.createdAt }
            val latest = listOfNotNull(dbLatest, backupLatest).maxByOrNull { it.createdAt }
            group.id to latest?.let {
                GroupPreview(
                    senderName = it.senderName,
                    text = groupPreviewText(it.text, serializeAttachments(it.attachments), deserializeAttachments),
                    createdAt = it.createdAt,
                )
            }
        }.filterValues { it != null }.mapValues { it.value!! }

    suspend fun openGroup(groupId: String, pageSize: Int): GroupOpenSnapshot {
        val total = runCatching { groupMessageDao.countForGroup(groupId) }.getOrDefault(0)
        val entities = groupMessageDao.forGroupPaged(groupId, pageSize, 0).reversed()
        val dbMessages = entities.map { it.toGroupMessage(deserializeAttachments) }
        val backupMessages = if (dbMessages.isEmpty()) groupHistoryStore.readBackup(groupId).takeLast(pageSize) else emptyList()
        val mergedMessages = groupHistoryStore.mergeHistory(dbMessages, backupMessages).takeLast(pageSize)
        return GroupOpenSnapshot(total, dbMessages, backupMessages, mergedMessages)
    }

    suspend fun loadOlder(groupId: String, pageSize: Int, offset: Int, currentMessages: List<GroupMessage>): GroupHistoryPage {
        val total = runCatching { groupMessageDao.countForGroup(groupId) }.getOrDefault(0)
        val entities = runCatching { groupMessageDao.forGroupPaged(groupId, pageSize, offset) }
            .getOrDefault(emptyList())
            .reversed()
        val older = entities.map { it.toGroupMessage(deserializeAttachments) }
        val merged = (older + currentMessages)
            .distinctBy { groupHistoryStore.dedupeKey(it) }
            .sortedBy { it.createdAt }
        return GroupHistoryPage(
            messages = merged,
            nextOffset = offset + pageSize,
            hasMore = offset + pageSize < total,
        )
    }
}

private fun GroupMessageEntity.toGroupMessage(
    deserializeAttachments: (String) -> List<SkillAttachment>,
): GroupMessage =
    runCatching {
        GroupMessage(
            id = id,
            groupId = groupId,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            text = text,
            attachments = deserializeAttachments(attachmentsJson),
            createdAt = createdAt,
        )
    }.getOrElse {
        GroupMessage(
            id = id,
            groupId = groupId,
            senderId = senderId,
            senderName = senderName,
            senderAvatar = senderAvatar,
            text = text,
            attachments = emptyList(),
            createdAt = createdAt,
        )
    }
