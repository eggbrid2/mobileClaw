package com.mobileclaw.ui.chat.runtime

import com.mobileclaw.agent.RoleWorkspaceStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RoleMemoryCommitInput(
    val roleId: String,
    val goal: String,
    val summary: String,
    val taskType: String,
    val success: Boolean?,
    val controlPlan: RoleChatControlPlan,
    val source: String,
)

data class RoleMemoryCommitResult(
    val journalPath: String = "",
    val memoryPath: String = "",
    val decision: MemoryCommitDecision = MemoryCommitDecision(),
) {
    val changed: Boolean get() = journalPath.isNotBlank() || memoryPath.isNotBlank() || decision.writeUserMemory
}

data class MemoryCommitDecision(
    val writeJournal: Boolean = false,
    val writeRoleMemory: Boolean = false,
    val writeUserMemory: Boolean = false,
    val userMemoryCandidate: String = "",
    val reason: String = "",
    val importance: MemoryCommitImportance = MemoryCommitImportance.LOW,
)

enum class MemoryCommitImportance {
    LOW,
    MEDIUM,
    HIGH,
}

class RoleMemoryCommitDecider {
    fun decide(input: RoleMemoryCommitInput): MemoryCommitDecision {
        val journal = input.controlPlan.persistencePolicy.writeJournalOnCompletion
        val roleMemorySignal = input.controlPlan.persistencePolicy.allowRoleMemoryWrite && input.hasDurableRoleMemorySignal()
        val userMemorySignal = input.controlPlan.persistencePolicy.allowUserMemoryWrite && input.hasDurableUserMemorySignal()
        val importance = when {
            roleMemorySignal || userMemorySignal -> MemoryCommitImportance.HIGH
            journal && input.success == true -> MemoryCommitImportance.MEDIUM
            else -> MemoryCommitImportance.LOW
        }
        return MemoryCommitDecision(
            writeJournal = journal,
            writeRoleMemory = roleMemorySignal,
            writeUserMemory = userMemorySignal,
            userMemoryCandidate = if (userMemorySignal) input.goal.take(500) else "",
            importance = importance,
            reason = buildString {
                append("journalPolicy=$journal")
                append("; roleMemorySignal=$roleMemorySignal")
                append("; userMemorySignal=$userMemorySignal")
                append("; threshold=${input.controlPlan.persistencePolicy.memoryImportanceThreshold}")
            },
        )
    }

    private fun RoleMemoryCommitInput.hasDurableRoleMemorySignal(): Boolean =
        durableSignalNeedles.any { durableText().contains(it) }

    private fun RoleMemoryCommitInput.hasDurableUserMemorySignal(): Boolean =
        userMemoryNeedles.any { durableText().contains(it) }

    private fun RoleMemoryCommitInput.durableText(): String =
        "$goal\n$summary".lowercase(Locale.getDefault())

    private val durableSignalNeedles = listOf(
        "记住", "以后", "下次", "长期", "偏好", "习惯", "不要", "总是", "固定", "可复用", "经验",
        "remember", "from now on", "next time", "preference", "prefer", "habit", "always", "never", "reusable",
    )

    private val userMemoryNeedles = listOf(
        "我的", "我喜欢", "我不喜欢", "我希望", "我的习惯", "用户偏好",
        "my ", "i prefer", "i like", "i don't like", "my preference", "my habit",
    )
}

class RoleMemoryCommitter(
    private val roleWorkspaceStore: RoleWorkspaceStore,
    private val decider: RoleMemoryCommitDecider = RoleMemoryCommitDecider(),
) {
    fun commit(input: RoleMemoryCommitInput): RoleMemoryCommitResult {
        val decision = decider.decide(input)
        val journalPath = if (decision.writeJournal) {
            roleWorkspaceStore.append(
                roleId = input.roleId,
                fileName = "journal.md",
                content = renderJournalEntry(input),
            )
        } else {
            ""
        }
        val memoryPath = if (decision.writeRoleMemory) {
            roleWorkspaceStore.append(
                roleId = input.roleId,
                fileName = "memory.md",
                content = renderMemoryEntry(input),
            )
        } else {
            ""
        }
        return RoleMemoryCommitResult(
            journalPath = journalPath,
            memoryPath = memoryPath,
            decision = decision,
        )
    }

    private fun renderJournalEntry(input: RoleMemoryCommitInput): String = buildString {
        appendLine("## ${nowText()} · ${input.taskType}")
        appendLine("- Source: ${input.source}")
        appendLine("- Success: ${input.success?.toString() ?: "unknown"}")
        appendLine("- Goal: ${input.goal.compactLine(800)}")
        appendLine("- Summary: ${input.summary.compactLine(1200)}")
    }.trim()

    private fun renderMemoryEntry(input: RoleMemoryCommitInput): String = buildString {
        appendLine("### ${nowText()} · 任务经验")
        appendLine("- 触发条件: ${input.controlPlan.persistencePolicy.memoryImportanceThreshold}")
        appendLine("- 用户目标: ${input.goal.compactLine(500)}")
        appendLine("- 可复用经验: ${input.summary.compactLine(900)}")
    }.trim()

    private fun String.compactLine(maxChars: Int): String =
        replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxChars)

    private fun nowText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
