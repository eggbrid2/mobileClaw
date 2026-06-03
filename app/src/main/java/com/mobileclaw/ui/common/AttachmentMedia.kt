package com.mobileclaw.ui.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.str

private object VideoFrameCache {
    // Keep a small in-memory cache so chat scrolling doesn't repeatedly decode the same video poster frame.
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

// 文件类附件的打开与图片缩略图解码在单聊/群聊完全同构，抽到 common 避免继续复制。
fun openFileAttachment(context: Context, attachment: SkillAttachment.FileData) {
    val uri = resolveFileAttachmentUri(context, attachment) ?: return

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

fun resolveFileAttachmentUri(context: Context, attachment: SkillAttachment.FileData): Uri? =
    if (attachment.path.startsWith("content://")) {
        Uri.parse(attachment.path)
    } else {
        val file = java.io.File(attachment.path)
        if (!file.exists()) {
            Toast.makeText(context, str(R.string.file_not_found, attachment.name), Toast.LENGTH_SHORT).show()
            null
        } else {
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrElse { e ->
                Toast.makeText(context, str(R.string.share_failed, e.message?.take(80) ?: ""), Toast.LENGTH_LONG).show()
                null
            }
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

fun decodeVideoAttachmentFrame(
    context: Context,
    attachment: SkillAttachment.FileData,
    maxPx: Int,
): Bitmap? {
    val cacheKey = "video:${attachment.path}:${attachment.sizeBytes}:$maxPx"
    VideoFrameCache.get(cacheKey)?.let { return it }
    val uri = resolveFileAttachmentUri(context, attachment) ?: return null
    return runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val frame = retriever.frameAtTime ?: retriever.embeddedPicture?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        retriever.release()
        frame?.let { bitmap ->
            val scaled = if (bitmap.width > 0 && bitmap.height > 0) {
                val scale = minOf(maxPx.toFloat() / bitmap.width, maxPx.toFloat() / bitmap.height, 1f)
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                        .also { if (it != bitmap) bitmap.recycle() }
                } else {
                    bitmap
                }
            } else {
                bitmap
            }
            VideoFrameCache.put(cacheKey, scaled)
            scaled
        }
    }.getOrNull()
}

fun stripDataUriPrefix(value: String): String =
    if (value.startsWith("data:")) value.substringAfter(",") else value

fun isImageFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.mimeType.startsWith("image/") ||
        attachment.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

fun isVideoFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.mimeType.startsWith("video/") ||
        attachment.name.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "m4v", "webm", "mkv", "3gp")

fun isStickerFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.path.contains("/stickers/", ignoreCase = true) ||
        attachment.name.contains("bqb", ignoreCase = true)
