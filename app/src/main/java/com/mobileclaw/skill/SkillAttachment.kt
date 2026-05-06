package com.mobileclaw.skill

/** Structured attachment data a skill can return alongside its text output. */
sealed class SkillAttachment {
    /** A generated or captured image (base64 data URI). */
    data class ImageData(val base64: String, val prompt: String? = null) : SkillAttachment()
    /** A file written to local storage that the user can open or share. */
    data class FileData(val path: String, val name: String, val mimeType: String, val sizeBytes: Long) : SkillAttachment()
    /** An HTML page written to local storage, to be displayed in an in-app WebView. */
    data class HtmlData(val path: String, val title: String, val htmlContent: String = "") : SkillAttachment()
    /** A fetched web page — shown as a rich card in chat that the user can tap to open. */
    data class WebPage(val url: String, val title: String, val excerpt: String) : SkillAttachment()
    /** A list of web search results — shown as a tappable result list in chat. */
    data class SearchResults(val query: String, val engine: String, val pages: List<WebPage>) : SkillAttachment()
    /**
     * Shown as an inline card in chat when a skill requires Accessibility Service.
     * [skillName] is the display name of the blocked skill.
     */
    data class AccessibilityRequest(val skillName: String) : SkillAttachment()
    /** A list of local files shown as tappable cards in the chat. */
    data class FileList(val files: List<FileEntry>, val directory: String = "") : SkillAttachment() {
        data class FileEntry(val path: String, val name: String, val mimeType: String, val sizeBytes: Long)
    }
}
