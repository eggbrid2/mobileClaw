package com.mobileclaw.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.str

// 文件类附件的打开与图片缩略图解码在单聊/群聊完全同构，抽到 common 避免继续复制。
fun openFileAttachment(context: Context, attachment: SkillAttachment.FileData) {
    val uri = if (attachment.path.startsWith("content://")) {
        Uri.parse(attachment.path)
    } else {
        val file = java.io.File(attachment.path)
        if (!file.exists()) {
            Toast.makeText(context, str(R.string.file_not_found, attachment.name), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { e ->
            Toast.makeText(context, str(R.string.share_failed, e.message?.take(80) ?: ""), Toast.LENGTH_LONG).show()
            return
        }
    }

    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
        addFlags(flags)
    }
    if (context.packageManager.queryIntentActivities(viewIntent, 0).isNotEmpty()) {
        runCatching { context.startActivity(Intent.createChooser(viewIntent, str(R.string.open_file, attachment.name))) }
        return
    }

    val genericIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*")
        addFlags(flags)
    }
    if (context.packageManager.queryIntentActivities(genericIntent, 0).isNotEmpty()) {
        runCatching { context.startActivity(Intent.createChooser(genericIntent, str(R.string.open_file, attachment.name))) }
        return
    }

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = attachment.mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(flags)
    }
    runCatching {
        context.startActivity(Intent.createChooser(shareIntent, str(R.string.share_file, attachment.name)))
    }.onFailure {
        Toast.makeText(context, str(R.string.chat_none), Toast.LENGTH_LONG).show()
    }
}

fun decodeFileAttachmentBitmap(
    context: Context,
    attachment: SkillAttachment.FileData,
    maxPx: Int,
): Bitmap? = runCatching {
    if (attachment.path.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(attachment.path))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }?.let { bitmap ->
            val scale = minOf(maxPx.toFloat() / bitmap.width, maxPx.toFloat() / bitmap.height, 1f)
            if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    .also { bitmap.recycle() }
            } else {
                bitmap
            }
        }
    } else {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(attachment.path, this)
            inSampleSize = maxOf(1, maxOf(outWidth / maxPx, outHeight / maxPx))
            inJustDecodeBounds = false
        }
        BitmapFactory.decodeFile(attachment.path, opts)
    }
}.getOrNull()

fun stripDataUriPrefix(value: String): String =
    if (value.startsWith("data:")) value.substringAfter(",") else value

fun isImageFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.mimeType.startsWith("image/") ||
        attachment.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

fun isStickerFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.path.contains("/stickers/", ignoreCase = true) ||
        attachment.name.contains("bqb", ignoreCase = true)
