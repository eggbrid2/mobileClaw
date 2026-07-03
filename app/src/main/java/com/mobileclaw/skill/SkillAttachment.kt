package com.mobileclaw.skill

import java.util.UUID

/** Structured attachment data a skill can return alongside its text output. */
sealed class SkillAttachment {
    abstract val instanceId: String

    /** A generated or captured image (base64 data URI). */
    data class ImageData(val base64: String, val prompt: String? = null, val localPath: String = "", override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment()
    /** A file written to local storage that the user can open or share. */
    data class FileData(val path: String, val name: String, val mimeType: String, val sizeBytes: Long, override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment()
    /** An HTML page written to local storage, to be displayed in an in-app WebView. */
    data class HtmlData(val path: String, val title: String, val htmlContent: String = "", override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment()
    /** A fetched web page — shown as a rich card in chat that the user can tap to open. */
    data class WebPage(val url: String, val title: String, val excerpt: String, override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment()
    /** A list of web search results — shown as a tappable result list in chat. */
    data class SearchResults(val query: String, val engine: String, val pages: List<WebPage>, override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment()
    /**
     * Shown as an inline card in chat when a skill requires Accessibility Service.
     * [skillName] is the display name of the blocked skill.
     */
    data class AccessibilityRequest(val skillName: String, override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment()
    /** A compact confirmation/action card with tappable actions. */
    data class ActionCard(
        val title: String,
        val body: String,
        val actions: List<Action>,
        val tone: String = "default",
        override val instanceId: String = UUID.randomUUID().toString(),
    ) : SkillAttachment() {
        data class Action(
            val label: String,
            val message: String,
            val style: String = "secondary",
        )
    }
    /** A list of local files shown as tappable cards in the chat. */
    data class FileList(val files: List<FileEntry>, val directory: String = "", override val instanceId: String = UUID.randomUUID().toString()) : SkillAttachment() {
        data class FileEntry(val path: String, val name: String, val mimeType: String, val sizeBytes: Long)
    }
}
