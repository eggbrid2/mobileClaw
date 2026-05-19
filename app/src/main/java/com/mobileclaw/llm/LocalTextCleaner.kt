package com.mobileclaw.llm

class LocalStreamingTextCleaner {
    private val raw = StringBuilder()
    private var emitted = ""

    fun append(piece: String): String {
        if (piece.isBlank()) return ""
        raw.append(piece)
        if (raw.mightEndInsideLocalControlToken()) return ""

        val cleaned = raw.toString().cleanLocalGeneratedText()
        if (cleaned.isBlank() || cleaned == emitted) return ""

        if (cleaned.startsWith(emitted)) {
            val delta = cleaned.substring(emitted.length)
            emitted = cleaned
            return delta.takeIf { it.isNotBlank() }.orEmpty()
        }

        // The role parser can rewrite the visible segment when the model echoes a prompt.
        // In that case avoid pushing a conflicting partial update; the final cleaned text
        // will still be returned as the authoritative response.
        return ""
    }

    fun finalText(): String = raw.toString().cleanLocalGeneratedText()
}

fun String.cleanLocalGeneratedText(): String {
    val normalized = normalizeLocalModelTranscript()
    val assistantText = normalized.extractLatestAssistantText()
        .stripLocalControlTokens()
        .stripRoleOnlyLines()
        .trim()

    val lines = assistantText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (shouldMergeLocalLines(assistantText, lines)) {
        return lines.joinToString("")
            .replace(Regex("""([。！？!?])"""), "$1\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    return assistantText
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun CharSequence.mightEndInsideLocalControlToken(): Boolean {
    val tail = takeLast(48).toString()
    val open = tail.lastIndexOf('<')
    if (open < 0) return false
    val close = tail.lastIndexOf('>')
    return close < open
}

private enum class LocalRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}

private data class LocalRoleSegment(
    val role: LocalRole?,
    val text: String,
)

private fun String.normalizeLocalModelTranscript(): String {
    var text = this
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    localRoleMarkers.forEach { marker ->
        text = marker.regex.replace(text) { match ->
            "\n${ROLE_BOUNDARY_PREFIX}${marker.role.name.lowercase()}\n"
        }
    }

    localControlTokenRegexes.forEach { regex ->
        text = regex.replace(text, "\n")
    }

    return text
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun String.extractLatestAssistantText(): String {
    val segments = splitLocalRoleSegments()
    val assistant = segments.latestAssistantRun()
    if (assistant.isNotBlank()) return assistant
    val nonUser = segments.lastOrNull { it.role != LocalRole.USER && it.role != LocalRole.SYSTEM && it.text.isNotBlank() }
    if (nonUser != null) return nonUser.text
    return this
}

private fun List<LocalRoleSegment>.latestAssistantRun(): String {
    val lastAssistantIndex = indexOfLast { it.role == LocalRole.ASSISTANT && it.text.isNotBlank() }
    if (lastAssistantIndex < 0) return ""
    var start = lastAssistantIndex
    while (start > 0) {
        val prev = this[start - 1]
        if (prev.role != LocalRole.ASSISTANT || prev.text.isBlank()) break
        start--
    }
    return subList(start, lastAssistantIndex + 1)
        .joinToString("") { it.text }
        .trim()
}

private fun String.splitLocalRoleSegments(): List<LocalRoleSegment> {
    val result = mutableListOf<LocalRoleSegment>()
    var currentRole: LocalRole? = null
    val buffer = StringBuilder()

    fun flush() {
        val text = buffer.toString().trim()
        if (text.isNotBlank()) result += LocalRoleSegment(currentRole, text)
        buffer.clear()
    }

    lines().forEach { rawLine ->
        val line = rawLine.trim()
        val boundaryRole = line.removePrefix(ROLE_BOUNDARY_PREFIX).takeIf { line.startsWith(ROLE_BOUNDARY_PREFIX) }
            ?.let { parseLocalRole(it) }
        val plainRole = parseStandaloneRole(line)
        val prefixedRole = parseRolePrefix(line)

        when {
            boundaryRole != null -> {
                flush()
                currentRole = boundaryRole
            }
            plainRole != null -> {
                flush()
                currentRole = plainRole
            }
            prefixedRole != null -> {
                flush()
                currentRole = prefixedRole.first
                buffer.appendLine(prefixedRole.second)
            }
            else -> buffer.appendLine(rawLine)
        }
    }
    flush()
    return result
}

private fun String.stripLocalControlTokens(): String {
    var text = this
    localControlTokenRegexes.forEach { regex ->
        text = regex.replace(text, "")
    }
    return text
}

private fun String.stripRoleOnlyLines(): String =
    lines()
        .filterNot { parseStandaloneRole(it.trim()) != null }
        .joinToString("\n")

private fun parseStandaloneRole(line: String): LocalRole? {
    val normalized = line
        .trim()
        .trim(':', '：')
        .lowercase()
    return parseLocalRole(normalized)
}

private fun parseRolePrefix(line: String): Pair<LocalRole, String>? {
    val idx = listOf(line.indexOf(':'), line.indexOf('：'))
        .filter { it >= 0 }
        .minOrNull() ?: return null
    val role = parseStandaloneRole(line.take(idx)) ?: return null
    val body = line.drop(idx + 1).trimStart()
    return role to body
}

private fun parseLocalRole(raw: String): LocalRole? = when (raw.trim().lowercase()) {
    "assistant", "model", "ai", "bot" -> LocalRole.ASSISTANT
    "user", "human" -> LocalRole.USER
    "system" -> LocalRole.SYSTEM
    "tool", "function" -> LocalRole.TOOL
    else -> null
}

private data class LocalRoleMarker(
    val role: LocalRole,
    val regex: Regex,
)

private const val ROLE_BOUNDARY_PREFIX = "__MOBILECLAW_LOCAL_ROLE__:"

private val localRoleMarkers = listOf(
    LocalRoleMarker(LocalRole.ASSISTANT, Regex("""(?i)<\|?/?(?:start_of_turn|turn|im_start|start_header_id)\|?>\s*(?:assistant|model|ai|bot)\b""")),
    LocalRoleMarker(LocalRole.USER, Regex("""(?i)<\|?/?(?:start_of_turn|turn|im_start|start_header_id)\|?>\s*(?:user|human)\b""")),
    LocalRoleMarker(LocalRole.SYSTEM, Regex("""(?i)<\|?/?(?:start_of_turn|turn|im_start|start_header_id)\|?>\s*system\b""")),
    LocalRoleMarker(LocalRole.TOOL, Regex("""(?i)<\|?/?(?:start_of_turn|turn|im_start|start_header_id)\|?>\s*(?:tool|function)\b""")),
)

private val localControlTokenRegexes = listOf(
    Regex("""(?i)<\|?/?(?:start_of_turn|end_of_turn|turn|im_start|im_end|eot_id|eos|bos|endoftext|begin_of_text|start_header_id|end_header_id)\|?>"""),
    Regex("""(?i)<\|?/?(?:eot|eom|eod|end)\|?>"""),
)

private fun shouldMergeLocalLines(text: String, lines: List<String>): Boolean {
    if (lines.size < 3) return false
    if (text.contains("```")) return false
    if (lines.any { line ->
            line.startsWith("- ") ||
                line.startsWith("* ") ||
                line.startsWith("> ") ||
                line.matches(Regex("""^\d+[.)]\s+.*"""))
        }
    ) return false

    val shortLineRatio = lines.count { it.length <= 4 }.toFloat() / lines.size
    val chineseCharCount = lines.sumOf { line -> line.count { it in '\u4e00'..'\u9fff' } }
    val charCount = lines.sumOf { it.length }.coerceAtLeast(1)
    val chineseRatio = chineseCharCount.toFloat() / charCount
    val averageLineLength = charCount.toFloat() / lines.size

    return shortLineRatio >= 0.55f || (chineseRatio >= 0.35f && averageLineLength <= 8f)
}
