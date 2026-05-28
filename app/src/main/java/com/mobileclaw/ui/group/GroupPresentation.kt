package com.mobileclaw.ui.group

import com.mobileclaw.R
import com.mobileclaw.agent.ChatBubbleStyle
import com.mobileclaw.agent.Role
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.str

internal fun groupPreviewText(
    text: String,
    attachmentsJson: String,
    deserializeAttachments: (String) -> List<SkillAttachment>,
): String {
    val cleanText = text.trim().replace(Regex("\\s+"), " ")
    if (cleanText.isNotBlank()) return cleanText
    return deserializeAttachments(attachmentsJson).firstOrNull()?.let { attachment ->
        when (attachment) {
            is SkillAttachment.ImageData -> str(R.string.group_label_image)
            is SkillAttachment.FileData -> attachment.name.ifBlank { str(R.string.group_label_file) }
            is SkillAttachment.HtmlData -> attachment.title.ifBlank { str(R.string.group_label_web) }
            is SkillAttachment.WebPage -> attachment.title.ifBlank { str(R.string.group_label_link) }
            is SkillAttachment.SearchResults -> str(R.string.group_label_search)
            is SkillAttachment.FileList -> str(R.string.group_label_file_list)
            is SkillAttachment.AccessibilityRequest -> str(R.string.group_label_permission)
            is SkillAttachment.ActionCard -> attachment.title.ifBlank { "操作确认" }
        }
    }.orEmpty()
}

internal fun defaultGroupBubbleStyleFor(role: Role, index: Int): ChatBubbleStyle {
    val palettes = listOf(
        BubbleStyleSeed("#F8F8F6", "#111111", "#D7D7D2", "#111111", "minimal", "contour", "dot", "medium", "neutral", "none"),
        BubbleStyleSeed("#050505", "#FFFFFF", "#050505", "#C7F43A", "ink", "none", "sparkle", "semibold", "cool", "shimmer"),
        BubbleStyleSeed("#FFFFFF", "#121212", "#D8D8D8", "#56D6BA", "outline", "dot", "badge", "medium", "happy", "breath"),
        BubbleStyleSeed("#F7F7F2", "#171717", "#E5E5DE", "#8A8A8A", "paper", "grid", "moon", "regular", "sleepy", "fade"),
        BubbleStyleSeed("#111111", "#F8F8F5", "#242424", "#C7F43A", "glass", "stripe", "star", "medium", "excited", "pop"),
        BubbleStyleSeed("#FAFAF7", "#0C0C0C", "#DBDBD4", "#56D6BA", "minimal", "star", "heart", "semibold", "love", "wave"),
    )
    val seed = palettes[index % palettes.size]
    val radius = 16 + ((role.id.hashCode() and 0x7fffffff) % 9)
    return ChatBubbleStyle(
        preset = seed.preset,
        renderer = "native",
        backgroundColor = seed.background,
        textColor = seed.text,
        borderColor = seed.border,
        accentColor = seed.accent,
        radiusDp = radius,
        radiusTopStartDp = radius,
        radiusTopEndDp = (radius + 4).coerceAtMost(28),
        radiusBottomEndDp = radius,
        radiusBottomStartDp = (radius - 6).coerceAtLeast(6),
        pattern = seed.pattern,
        decoration = seed.decoration,
        decorationText = role.name.take(2),
        decorationPosition = if (index % 2 == 0) "top_end" else "bottom_end",
        decorationAnimation = if (index % 3 == 0) "pulse" else "none",
        emotion = seed.emotion,
        fontFamily = if (index % 3 == 1) "rounded" else "system",
        fontWeight = seed.fontWeight,
        textAnimation = seed.textAnimation,
        fontSizeSp = 14,
        lineHeightSp = 20,
        paddingHorizontalDp = 13,
        paddingVerticalDp = 9,
        shadow = if (index % 2 == 0) "soft" else "none",
        shadowColor = "#000000",
        shadowAlpha = if (index % 2 == 0) 0.12f else -1f,
        shadowElevationDp = if (index % 2 == 0) 4 else -1,
        imageMode = "cover",
    )
}

private data class BubbleStyleSeed(
    val background: String,
    val text: String,
    val border: String,
    val accent: String,
    val preset: String,
    val pattern: String,
    val decoration: String,
    val fontWeight: String,
    val emotion: String,
    val textAnimation: String,
)
