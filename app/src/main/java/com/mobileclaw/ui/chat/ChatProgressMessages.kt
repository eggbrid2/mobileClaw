package com.mobileclaw.ui.chat

import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.common.sanitizeUserFacingNarration
import com.mobileclaw.str

data class AgentSenderMeta(
    val id: String,
    val name: String,
    val avatar: String,
)

internal fun buildNarrativeAgentMessages(
    summary: String,
    logLines: List<LogLine>,
    attachments: List<SkillAttachment>,
    sender: AgentSenderMeta,
    streamingToken: String = "",
    streamingThought: String = "",
    isRunning: Boolean = false,
): List<ChatMessage> {
    val messages = mutableListOf<ChatMessage>()
    val actionGroups = mutableListOf<List<LogLine>>()
    val pendingPlanningGroup = mutableListOf<LogLine>()
    var currentActionGroup = mutableListOf<LogLine>()
    var hasStartedExecution = false

    fun addTextMessage(text: String, lines: List<LogLine> = emptyList()) {
        val clean = sanitizeUserFacingNarration(text.trim())
        if (clean.isBlank() && lines.isEmpty()) return
        messages += ChatMessage(
            role = MessageRole.AGENT,
            text = clean,
            logLines = lines,
            senderRoleId = sender.id,
            senderRoleName = sender.name,
            senderRoleAvatar = sender.avatar,
        )
    }

    fun flushPlanningGroup(beforeExecution: Boolean) {
        if (pendingPlanningGroup.isEmpty()) return
        val narratives = composePlanningNarratives(
            lines = pendingPlanningGroup.toList(),
            beforeExecution = beforeExecution,
            isRunning = isRunning,
        )
        narratives.forEachIndexed { index, narrative ->
            addTextMessage(
                text = narrative,
                lines = if (index == narratives.lastIndex) pendingPlanningGroup.toList() else emptyList(),
            )
        }
        pendingPlanningGroup.clear()
    }

    fun flushActionGroup() {
        if (currentActionGroup.isEmpty()) return
        actionGroups += currentActionGroup.toList()
        addTextMessage(composeActionNarrative(currentActionGroup, isRunning), currentActionGroup.toList())
        if (actionGroups.size % 10 == 0) {
            addTextMessage(
                composeCheckpointNarrative(actionGroups.size, actionGroups.takeLast(10), isRunning),
                lines = actionGroups.takeLast(10).flatten(),
            )
        }
        currentActionGroup = mutableListOf()
    }

    logLines.forEach { line ->
        when (line.type) {
            LogType.ACTION -> {
                flushPlanningGroup(beforeExecution = !hasStartedExecution)
                flushActionGroup()
                hasStartedExecution = true
                currentActionGroup += line
            }
            LogType.OBSERVATION, LogType.ERROR, LogType.SUCCESS -> {
                if (currentActionGroup.isNotEmpty()) {
                    currentActionGroup += line
                } else {
                    flushPlanningGroup(beforeExecution = !hasStartedExecution)
                    addTextMessage(composeStandaloneNarrative(line), listOf(line))
                }
            }
            LogType.THINKING, LogType.INFO -> {
                if (currentActionGroup.isNotEmpty()) {
                    flushActionGroup()
                }
                pendingPlanningGroup += line
            }
        }
    }
    flushPlanningGroup(beforeExecution = !hasStartedExecution)
    flushActionGroup()

    val cleanedStreamingToken = streamingToken.trim()
    if (cleanedStreamingToken.isNotBlank()) {
        addTextMessage(cleanedStreamingToken)
    } else if (isRunning && streamingThought.isNotBlank() && messages.isEmpty()) {
        addTextMessage(streamingThought.takeLast(180).trim())
    }

    val cleanedSummary = summary.trim()
    if (cleanedSummary.isNotBlank() && cleanedSummary != cleanedStreamingToken) {
        val lastMessageText = messages.lastOrNull()?.text?.trim().orEmpty()
        if (lastMessageText != cleanedSummary) {
            addTextMessage(cleanedSummary)
        }
    }

    attachments.forEach { attachment ->
        messages += ChatMessage(
            role = MessageRole.AGENT,
            text = "",
            attachments = listOf(attachment),
            senderRoleId = sender.id,
            senderRoleName = sender.name,
            senderRoleAvatar = sender.avatar,
        )
    }

    if (messages.isEmpty() && !isRunning) {
        addTextMessage(if (isRunning) str(R.string.chat_working_update) else "Done.")
    }
    val deduped = messages.fold(mutableListOf<ChatMessage>()) { acc, msg ->
        val last = acc.lastOrNull()
        if (last != null &&
            last.attachments.isEmpty() &&
            msg.attachments.isEmpty() &&
            sameMeaning(last.text, msg.text)
        ) {
            acc
        } else {
            acc += msg
            acc
        }
    }
    return if (isRunning) trimRunningMessages(deduped) else deduped
}

private fun composePlanningNarratives(
    lines: List<LogLine>,
    beforeExecution: Boolean,
    isRunning: Boolean,
): List<String> {
    if (lines.isEmpty()) return emptyList()
    val purposes = lines.mapNotNull { composeStandaloneNarrative(it).takeIf(String::isNotBlank) }
        .filterNot { isGenericProcessSentence(it) }
        .distinctBy { it.normalizeMeaning() }
    val nextSteps = lines
        .flatMap { line -> line.details.filter { it.startsWith("接下来：") || it.startsWith("后续计划：") } }
        .map { sanitizeUserFacingNarration(it.substringAfter("：").trim()) }
        .flatMap { it.split('；', '\n').map(String::trim) }
        .filter { it.isNotBlank() }
        .filterNot { isGenericProcessSentence(it) }
        .distinctBy { it.normalizeMeaning() }

    if (!beforeExecution) {
        val latestPurpose = purposes.lastOrNull().orEmpty()
        val adjustment = nextSteps.firstOrNull { !sameMeaning(it, latestPurpose) }
            ?: purposes.lastOrNull { !sameMeaning(it, latestPurpose) }
        return listOfNotNull(
            latestPurpose.takeIf { it.isNotBlank() },
            adjustment?.takeIf { it.isNotBlank() },
        ).distinctBy { it.normalizeMeaning() }
    }

    val firstPurpose = purposes.firstOrNull()
    val conciseSteps = nextSteps
        .filterNot { step -> firstPurpose != null && sameMeaning(step, firstPurpose) }
        .take(2)
    val opening = firstPurpose.orEmpty()
    val nextMove = conciseSteps.firstOrNull()
        ?: nextSteps.firstOrNull()
        ?: ""

    val narratives = mutableListOf<String>()
    if (opening.isNotBlank()) {
        narratives += opening
    }
    nextMove.takeIf { it.isNotBlank() && narratives.none { existing -> sameMeaning(existing, it) } }
        ?.let { narratives += it }
    if (narratives.isEmpty() && !isRunning) {
        narratives += if (isRunning) str(R.string.chat_working_update) else "Done."
    }
    return narratives.distinctBy { it.normalizeMeaning() }
}

private fun composeActionNarrative(lines: List<LogLine>, isRunning: Boolean): String {
    val action = lines.firstOrNull { it.type == LogType.ACTION }
    val purpose = lines.firstDetailValue("本步目的")
    val result = lines.lastMeaningfulResult()
    val next = lines.firstDetailValue("接下来")
    val fallback = sanitizeUserFacingNarration(action?.text?.trim().orEmpty())

    return buildString {
        append(sanitizeUserFacingNarration(purpose.ifBlank { fallback }.ifBlank { str(R.string.chat_working_update) }))
        if (result.isNotBlank() &&
            !sameMeaning(result, purpose) &&
            !sameMeaning(result, fallback) &&
            !isGenericProcessSentence(result)
        ) {
            append("\n")
            append(sanitizeUserFacingNarration(result))
        }
        if (!isRunning && next.isNotBlank() && !isGenericProcessSentence(next) && !sameMeaning(next, purpose)) {
            append("\n")
            append(sanitizeUserFacingNarration(next))
        }
    }.trim()
}

private fun composeStandaloneNarrative(line: LogLine): String {
    val purpose = line.details.firstValue("本步目的")
    val result = line.details.firstValue("本步结果")
    return sanitizeUserFacingNarration(when (line.type) {
        LogType.THINKING, LogType.INFO -> purpose.ifBlank { line.text }
        LogType.OBSERVATION -> result.ifBlank { line.text }
        LogType.ERROR, LogType.SUCCESS -> line.text.ifBlank { result }
        LogType.ACTION -> purpose.ifBlank { line.text }
    }.trim())
}

private fun composeCheckpointNarrative(
    completedSteps: Int,
    groups: List<List<LogLine>>,
    isRunning: Boolean,
): String {
    val finished = groups.map { composeActionNarrative(it, isRunning = false) }.filter { it.isNotBlank() }
    val first = finished.firstOrNull().orEmpty().lineSequence().firstOrNull().orEmpty()
    val last = finished.lastOrNull().orEmpty().lineSequence().firstOrNull().orEmpty()
    return buildString {
        append("已经处理了 $completedSteps 步")
        if (first.isNotBlank()) {
            append("\n")
            append(first)
        }
        if (last.isNotBlank() && !sameMeaning(last, first)) {
            append("\n")
            append(last)
        }
        if (isRunning) {
            append("\n")
            append("后面的部分还在继续。")
        }
    }.trim()
}

private fun List<LogLine>.firstDetailValue(prefix: String): String =
    asSequence()
        .flatMap { it.details.asSequence() }
        .firstOrNull { it.startsWith("$prefix：") }
        ?.substringAfter("：")
        ?.trim()
        .orEmpty()

private fun List<String>.firstValue(prefix: String): String =
    firstOrNull { it.startsWith("$prefix：") }
        ?.substringAfter("：")
        ?.trim()
        .orEmpty()

private fun List<LogLine>.lastMeaningfulResult(): String {
    val resultFromDetails = asReversed()
        .asSequence()
        .flatMap { it.details.asReversed().asSequence() }
        .firstOrNull { it.startsWith("本步结果：") }
        ?.substringAfter("：")
        ?.trim()
        .orEmpty()
    if (resultFromDetails.isNotBlank()) return resultFromDetails
    return asReversed()
        .firstOrNull { it.type == LogType.OBSERVATION || it.type == LogType.ERROR || it.type == LogType.SUCCESS }
        ?.text
        ?.trim()
        .orEmpty()
}

private fun sameMeaning(a: String, b: String): Boolean =
    a.normalizeMeaning() == b.normalizeMeaning()

private fun String.normalizeMeaning(): String =
    trim().replace(Regex("\\s+"), "")

private fun trimRunningMessages(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.size <= 5) return messages
    val planning = messages.takeWhile { it.attachments.isEmpty() && it.logLines.isEmpty() }
    val tail = messages.takeLast(4)
    return (planning.takeLast(1) + tail).distinctBy { it.text.normalizeMeaning() + "#${it.attachments.size}" }
}

private fun isGenericProcessSentence(text: String): Boolean {
    val normalized = text.normalizeMeaning()
    if (normalized.isBlank()) return true
    val genericFragments = listOf(
        "继续推进",
        "继续处理",
        "继续执行",
        "继续往下做",
        "确认结果",
        "确认是否",
        "判断是否",
        "再决定是否",
        "根据返回结果继续",
        "按新的判断继续推进",
        "换好了下一步思路",
        "整理任务类型和执行方式",
        "正在整理当前进展",
        "正在根据当前结果选择下一步",
        "正在确定本轮任务的执行方式",
        "理清本轮要先做什么后做什么",
        "整理出本轮的处理顺序",
        "还在继续处理这件事",
    )
    return genericFragments.any { normalized.contains(it.normalizeMeaning()) }
}
