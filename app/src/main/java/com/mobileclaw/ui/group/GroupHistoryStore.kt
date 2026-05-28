package com.mobileclaw.ui.group

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.agent.RoleAvatarDefaults
import com.mobileclaw.skill.SkillAttachment
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

internal class GroupHistoryStore(
    private val context: Context,
    private val gson: Gson,
    private val serializeAttachments: (List<SkillAttachment>) -> String,
    private val deserializeAttachments: (String) -> List<SkillAttachment>,
) {
    fun historyFile(groupId: String): File =
        File(context.filesDir, "group_history/$groupId.jsonl").also { it.parentFile?.mkdirs() }

    fun historyDir(): File =
        File(context.filesDir, "group_history").also { it.mkdirs() }

    fun appendBackup(message: GroupMessage) {
        runCatching {
            val payload = mapOf(
                "id" to message.id,
                "groupId" to message.groupId,
                "senderId" to message.senderId,
                "senderName" to message.senderName,
                "senderAvatar" to message.senderAvatar,
                "text" to message.text,
                "attachmentsJson" to serializeAttachments(message.attachments),
                "createdAt" to message.createdAt,
            )
            val file = historyFile(message.groupId)
            file.appendText(gson.toJson(payload) + "\n")
            compactBackupIfNeeded(file)
        }
    }

    fun readBackup(groupId: String): List<GroupMessage> =
        runCatching {
            val file = historyFile(groupId)
            if (!file.exists()) return@runCatching emptyList()
            readLastLines(file, maxLines = 300).mapNotNull { line ->
                runCatching {
                    val obj = JsonParser.parseString(line).asJsonObject
                    GroupMessage(
                        id = obj["id"]?.asLong ?: 0L,
                        groupId = obj["groupId"]?.asString ?: groupId,
                        senderId = obj["senderId"]?.asString ?: "",
                        senderName = obj["senderName"]?.asString ?: "",
                        senderAvatar = obj["senderAvatar"]?.asString ?: RoleAvatarDefaults.CUSTOM,
                        text = obj["text"]?.asString ?: "",
                        attachments = deserializeAttachments(obj["attachmentsJson"]?.asString ?: "[]"),
                        createdAt = obj["createdAt"]?.asLong ?: 0L,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

    fun mergeHistory(primary: List<GroupMessage>, backup: List<GroupMessage>, limit: Int = 300): List<GroupMessage> =
        (primary + backup)
            .distinctBy(::dedupeKey)
            .sortedBy { it.createdAt }
            .takeLast(limit)

    fun dedupeKey(message: GroupMessage): String =
        "${message.groupId}:${message.senderId}:${message.createdAt}:${message.text}:${message.attachments.size}"

    private fun compactBackupIfNeeded(file: File) {
        if (!file.exists() || file.length() < 2_000_000L) return
        runCatching {
            val recent = readLastLines(file, maxLines = 500)
            file.writeText(recent.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    private fun readLastLines(file: File, maxLines: Int): List<String> {
        if (maxLines <= 0 || !file.exists()) return emptyList()
        val lines = ArrayDeque<String>()
        RandomAccessFile(file, "r").use { raf ->
            var pointer = raf.length() - 1
            val bytes = ByteArrayOutputStream()
            while (pointer >= 0 && lines.size < maxLines) {
                raf.seek(pointer)
                val b = raf.read()
                if (b == '\n'.code) {
                    if (bytes.size() > 0) {
                        lines.addFirst(String(bytes.toByteArray().reversedArray(), Charsets.UTF_8))
                        bytes.reset()
                    }
                } else {
                    bytes.write(b)
                }
                pointer--
            }
            if (bytes.size() > 0 && lines.size < maxLines) {
                lines.addFirst(String(bytes.toByteArray().reversedArray(), Charsets.UTF_8))
            }
        }
        return lines.filter { it.isNotBlank() }
    }
}
