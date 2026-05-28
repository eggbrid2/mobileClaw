package com.mobileclaw.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.mobileclaw.skill.SkillAttachment

// 附件展示层的元信息工具统一放在这里，避免 chat/group 各自维护一套格式化与签名规则。
fun decodeDataUriBitmap(data: String): Bitmap? = runCatching {
    val clean = stripDataUriPrefix(data)
    val bytes = Base64.decode(clean, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

fun mimeTypeSymbol(mimeType: String): String = when {
    mimeType.startsWith("image/") -> "image"
    mimeType.startsWith("video/") -> "video"
    mimeType.startsWith("audio/") -> "audio"
    mimeType == "application/pdf" -> "file"
    mimeType.contains("json") -> "file"
    mimeType.contains("csv") || mimeType.contains("excel") || mimeType.contains("spreadsheet") -> "file"
    mimeType.contains("python") || mimeType.contains("javascript") || mimeType.contains("x-") -> "code"
    mimeType.contains("html") -> "web"
    mimeType.contains("zip") || mimeType.contains("archive") -> "package"
    else -> "file"
}

fun mimeTypeEmoji(mimeType: String): String = when {
    mimeType.startsWith("image/") -> "🖼️"
    mimeType.startsWith("video/") -> "🎬"
    mimeType.startsWith("audio/") -> "🎵"
    mimeType == "application/pdf" -> "📕"
    mimeType.contains("json") -> "🗂️"
    mimeType.contains("csv") || mimeType.contains("excel") || mimeType.contains("spreadsheet") -> "📊"
    mimeType.contains("python") || mimeType.contains("javascript") || mimeType.contains("x-") -> "💻"
    mimeType.contains("html") -> "🌐"
    mimeType.contains("zip") || mimeType.contains("archive") -> "📦"
    else -> "📄"
}

fun SkillAttachment.stableUiSignature(): String = when (this) {
    is SkillAttachment.ImageData -> "image:${base64.take(80).hashCode()}:${prompt.orEmpty().hashCode()}"
    is SkillAttachment.FileData -> "file:$path:$name:$mimeType:$sizeBytes"
    is SkillAttachment.HtmlData -> "html:$path:$title"
    is SkillAttachment.WebPage -> "web:$url:${title.hashCode()}:${excerpt.hashCode()}"
    is SkillAttachment.SearchResults -> "search:$query:$engine:${pages.joinToString("|") { it.url }.hashCode()}"
    is SkillAttachment.AccessibilityRequest -> "accessibility:$skillName"
    is SkillAttachment.ActionCard -> buildString {
        append("action:")
        append(tone)
        append(':')
        append(title.hashCode())
        append(':')
        append(body.hashCode())
        append(':')
        append(actions.joinToString("|") { "${it.label}:${it.message}:${it.style}" }.hashCode())
    }
    is SkillAttachment.FileList -> "files:$directory:${files.joinToString("|") { "${it.path}:${it.sizeBytes}" }.hashCode()}"
}
