package com.mobileclaw.skill

/** Structured attachment data a skill can return alongside its text output. */
sealed class SkillAttachment {
    /** A generated or captured image (base64 data URI). */
    data class ImageData(val base64: String, val prompt: String? = null) : SkillAttachment()
    /** A file written to local storage that the user can open or share. */
    data class FileData(val path: String, val name: String, val mimeType: String, val sizeBytes: Long) : SkillAttachment()
    /** An HTML page written to local storage, to be displayed in an in-app WebView. */
    data class HtmlData(val path: String, val title: String, val htmlContent: String = "") : SkillAttachment()
}
