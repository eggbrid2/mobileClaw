package com.mobileclaw.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.chat.FileAttachment
import java.io.ByteArrayOutputStream
import kotlin.math.max

private const val MAX_IMAGE_EDGE_PX = 1280
private const val MAX_TEXT_ATTACHMENT_BYTES = 1_000_000L
private const val MAX_BINARY_ATTACHMENT_BYTES = 8_000_000L
private const val GROUP_INLINE_TEXT_LIMIT = 10_000

// 聊天与群聊都需要“图片/文本/二进制附件识别”这套能力，抽到 common 后页面只保留交互编排。
sealed class PickedChatInput {
    data class Image(val base64: String) : PickedChatInput()
    data class File(val attachment: FileAttachment) : PickedChatInput()
}

// 群聊发送附件时除了附件本体，还可能附带一段内联文本摘要，因此单独保留一个结果模型。
data class GroupPickedAttachment(
    val attachments: List<SkillAttachment>,
    val textAppend: String = "",
)

fun imageUriToDataUri(
    context: Context,
    uri: Uri,
    maxEdgePx: Int = MAX_IMAGE_EDGE_PX,
    quality: Int = 82,
): String? {
    val original = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: return null
    val longestEdge = max(original.width, original.height)
    val bitmap = if (longestEdge > maxEdgePx) {
        val scale = maxEdgePx.toFloat() / longestEdge.toFloat()
        val width = max(1, (original.width * scale).toInt())
        val height = max(1, (original.height * scale).toInt())
        Bitmap.createScaledBitmap(original, width, height, true).also {
            if (it !== original) original.recycle()
        }
    } else {
        original
    }
    return try {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    } finally {
        bitmap.recycle()
    }
}

fun buildPickedAttachment(context: Context, uri: Uri): PickedChatInput? {
    val (name, reportedSize) = queryDisplayNameAndSize(context, uri)
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    if (mimeType.startsWith("image/")) {
        return imageUriToDataUri(context, uri)?.let { PickedChatInput.Image(it) }
    }

    if (isTextMime(mimeType) || isTextFileName(name)) {
        if (reportedSize != null && reportedSize > MAX_TEXT_ATTACHMENT_BYTES) return null
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return null
        if (text.toByteArray().size > MAX_TEXT_ATTACHMENT_BYTES) return null
        return PickedChatInput.File(FileAttachment(name = name, content = text, isText = true, mimeType = mimeType))
    }

    if (reportedSize != null && reportedSize > MAX_BINARY_ATTACHMENT_BYTES) return null
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MAX_BINARY_ATTACHMENT_BYTES) return null
    return PickedChatInput.File(
        FileAttachment(
            name = name,
            content = Base64.encodeToString(bytes, Base64.NO_WRAP),
            isText = false,
            mimeType = mimeType,
        )
    )
}

fun buildGroupPickedAttachment(context: Context, uri: Uri): GroupPickedAttachment? {
    val (name, _) = queryDisplayNameAndSize(context, uri)
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return when {
        mimeType.startsWith("image/") -> {
            val base64 = imageBytesToDataUri(bytes, quality = 80) ?: return null
            GroupPickedAttachment(attachments = listOf(SkillAttachment.ImageData(base64, prompt = name)))
        }
        isTextMime(mimeType) || isTextFileName(name) -> {
            val content = bytes.toString(Charsets.UTF_8).take(GROUP_INLINE_TEXT_LIMIT)
            GroupPickedAttachment(
                attachments = listOf(SkillAttachment.FileData(uri.toString(), name, mimeType, bytes.size.toLong())),
                textAppend = "\n\n[附件: $name]\n```\n$content\n```",
            )
        }
        else -> GroupPickedAttachment(
            attachments = listOf(SkillAttachment.FileData(uri.toString(), name, mimeType, bytes.size.toLong()))
        )
    }
}

private fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String, Long?> {
    var name: String? = null
    var size: Long? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return (name ?: uri.lastPathSegment ?: "attachment") to size
}

private fun isTextMime(mimeType: String): Boolean =
    mimeType.startsWith("text/") ||
        mimeType in setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-sh",
            "application/x-yaml",
            "application/yaml",
        )

private fun isTextFileName(name: String): Boolean =
    name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in setOf(
        "txt", "md", "markdown", "json", "xml", "csv", "tsv", "yaml", "yml", "js", "ts", "kt", "java", "py", "sh", "html", "css",
    )

private fun imageBytesToDataUri(bytes: ByteArray, maxEdgePx: Int = 1024, quality: Int = 80): String? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val scale = minOf(maxEdgePx.toFloat() / bitmap.width, maxEdgePx.toFloat() / bitmap.height, 1f)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else {
        bitmap
    }
    return try {
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    } finally {
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
    }
}
