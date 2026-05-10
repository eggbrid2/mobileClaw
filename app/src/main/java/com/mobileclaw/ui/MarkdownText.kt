package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Data model ────────────────────────────────────────────────────────────────

private sealed class MarkdownPart {
    data class Prose(val content: String) : MarkdownPart()
    data class Fence(val language: String, val content: String) : MarkdownPart()
    data class UiBlock(val content: String) : MarkdownPart()
}

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 21.sp,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
    onAction: ((String) -> Unit)? = null,
) {
    val c = LocalClawColors.current
    val parts = splitFenceBlocks(text)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        parts.forEach { part ->
            when (part) {
                is MarkdownPart.UiBlock -> DynamicUiRenderer(part.content, onAction ?: {})
                is MarkdownPart.Fence -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.cardAlt, RoundedCornerShape(6.dp))
                            .border(1.dp, c.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            part.content.trimEnd(),
                            color      = color.copy(alpha = 0.9f),
                            fontSize   = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                is MarkdownPart.Prose -> {
                    renderProse(part.content, color, c, fontSize, lineHeight, fontFamily, fontWeight)
                }
            }
        }
    }
}

// ── Prose renderer (lines) ────────────────────────────────────────────────────

@Composable
private fun renderProse(
    prose: String,
    textColor: Color,
    c: ClawColors,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
) {
    val lines = prose.split("\n")
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.startsWith("### ") ->
                Text(
                    inlineAnnotated(line.removePrefix("### "), c),
                    color = textColor, fontSize = (fontSize.value + 1f).sp,
                    fontWeight = strongerWeight(fontWeight, FontWeight.SemiBold),
                    fontFamily = fontFamily,
                    lineHeight = lineHeight,
                )

            line.startsWith("## ") ->
                Text(
                    inlineAnnotated(line.removePrefix("## "), c),
                    color = textColor, fontSize = (fontSize.value + 2f).sp,
                    fontWeight = strongerWeight(fontWeight, FontWeight.Bold),
                    fontFamily = fontFamily,
                    lineHeight = lineHeight,
                )

            line.startsWith("# ") ->
                Text(
                    inlineAnnotated(line.removePrefix("# "), c),
                    color = textColor, fontSize = (fontSize.value + 4f).sp,
                    fontWeight = strongerWeight(fontWeight, FontWeight.Bold),
                    fontFamily = fontFamily,
                    lineHeight = lineHeight,
                )

            line.startsWith("- ") || line.startsWith("* ") ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("•", color = textColor.copy(alpha = 0.5f), fontSize = fontSize, lineHeight = lineHeight)
                    Text(
                        inlineAnnotated(line.drop(2), c),
                        color = textColor, fontSize = fontSize, lineHeight = lineHeight,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        modifier = Modifier.weight(1f),
                    )
                }

            line.matches(Regex("""^\d+\. .*""")) -> {
                val num = line.substringBefore(". ")
                val rest = line.substringAfter(". ")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$num.", color = textColor.copy(alpha = 0.5f), fontSize = fontSize, lineHeight = lineHeight)
                    Text(
                        inlineAnnotated(rest, c),
                        color = textColor, fontSize = fontSize, lineHeight = lineHeight,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            line.startsWith("> ") ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .defaultMinSize(minHeight = lineHeight.value.dp)
                            .background(c.accent.copy(alpha = 0.5f), RoundedCornerShape(1.5.dp)),
                    )
                    Text(
                        inlineAnnotated(line.removePrefix("> "), c),
                        color = textColor.copy(alpha = 0.7f), fontSize = fontSize, lineHeight = lineHeight,
                        fontStyle = FontStyle.Italic,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        modifier = Modifier.weight(1f),
                    )
                }

            line.startsWith("---") || line.startsWith("***") || line.startsWith("___") ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(c.border),
                )

            // Markdown table: consecutive lines containing |
            line.trimStart().startsWith("|") -> {
                val tableLines = mutableListOf(line)
                while (i + 1 < lines.size && lines[i + 1].trimStart().startsWith("|")) {
                    i++
                    tableLines.add(lines[i])
                }
                MarkdownTable(tableLines, c, textColor, fontFamily, fontWeight)
            }

            line.isBlank() -> Spacer(Modifier.height(2.dp))

            else ->
                Text(
                    inlineAnnotated(line, c),
                    color = textColor, fontSize = fontSize, lineHeight = lineHeight,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                )
        }
        i++
    }
}

// ── Markdown table renderer ───────────────────────────────────────────────────

@Composable
private fun MarkdownTable(
    lines: List<String>,
    c: ClawColors,
    textColor: Color,
    fontFamily: FontFamily?,
    fontWeight: FontWeight?,
) {
    // Parse each line into cells
    fun parseLine(line: String): List<String> =
        line.trim().removeSuffix("|").removePrefix("|").split("|").map { it.trim() }

    // Separator rows look like |---|---| or |:--|--:|
    fun isSeparator(cells: List<String>) = cells.isNotEmpty() && cells.all { it.matches(Regex("[:\\-]+")) }

    val parsed = lines.map { parseLine(it) }
    val nonSep = parsed.filter { !isSeparator(it) }
    if (nonSep.isEmpty()) return

    val headers = nonSep.first()
    val rows = nonSep.drop(1)
    val colCount = headers.size.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, c.border, RoundedCornerShape(8.dp))
            .background(c.card, RoundedCornerShape(8.dp)),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.cardAlt, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
        ) {
            headers.forEachIndexed { idx, cell ->
                if (idx > 0) Box(Modifier.width(0.5.dp).defaultMinSize(minHeight = 1.dp).background(c.border))
                Text(
                    inlineAnnotated(cell, c),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 7.dp),
                    fontSize = 12.sp,
                    fontWeight = strongerWeight(fontWeight, FontWeight.SemiBold),
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.72f),
                    lineHeight = 16.sp,
                )
            }
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        // Data rows
        rows.forEachIndexed { rowIdx, row ->
            if (rowIdx > 0) HorizontalDivider(color = c.border.copy(alpha = 0.5f), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (rowIdx % 2 == 1) c.cardAlt.copy(alpha = 0.4f) else Color.Transparent),
            ) {
                for (ci in 0 until colCount) {
                    if (ci > 0) Box(Modifier.width(0.5.dp).defaultMinSize(minHeight = 1.dp).background(c.border.copy(alpha = 0.5f)))
                    val cell = row.getOrElse(ci) { "" }
                    Text(
                        inlineAnnotated(cell, c),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        color = textColor,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

private fun strongerWeight(configured: FontWeight?, minimum: FontWeight): FontWeight =
    configured?.takeIf { it.weight > minimum.weight } ?: minimum

// ── Inline style parser ───────────────────────────────────────────────────────

private fun inlineAnnotated(text: String, c: ClawColors): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            // Bold-italic: ***...*** (must check before ** and *)
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                } else { append(text[i]); i++ }
            }
            // Bold: **...**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // Italic: *...* (but not **)
            text[i] == '*' && !text.startsWith("**", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end > i && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            // Inline code: `...`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 12.sp,
                            color      = c.green.copy(alpha = 0.9f),
                            background = c.cardAlt,
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
        }
    }
}

// ── Code-fence splitter ───────────────────────────────────────────────────────

private fun splitFenceBlocks(text: String): List<MarkdownPart> {
    val result = mutableListOf<MarkdownPart>()
    // Match ```lang\ncontent``` or ~~~lang\ncontent~~~
    val fenceRe = Regex("```(\\w*)\\s*\\n([\\s\\S]*?)```|~~~(\\w*)\\s*\\n([\\s\\S]*?)~~~")
    var cursor = 0
    for (m in fenceRe.findAll(text)) {
        if (m.range.first > cursor) result.add(MarkdownPart.Prose(text.substring(cursor, m.range.first)))
        val lang    = (m.groupValues[1].ifBlank { m.groupValues[3] })
        val content = (m.groupValues[2].ifBlank { m.groupValues[4] })
        if (lang == "ui") result.add(MarkdownPart.UiBlock(content))
        else result.add(MarkdownPart.Fence(lang, content))
        cursor = m.range.last + 1
    }
    if (cursor < text.length) result.add(MarkdownPart.Prose(text.substring(cursor)))
    return result
}
