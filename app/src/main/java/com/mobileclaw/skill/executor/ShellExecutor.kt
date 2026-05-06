package com.mobileclaw.skill.executor

import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.skill.SkillParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Executes arbitrary shell commands via ProcessBuilder (sh -c). */
class ShellExecutor {

    suspend fun execute(command: String, args: List<String>): SkillResult {
        return withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(30_000L) {
                runCatching {
                    val fullCmd = if (args.isEmpty()) command else "$command ${args.joinToString(" ")}"
                    val process = ProcessBuilder("sh", "-c", fullCmd)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText().take(8192)
                    val exitCode = process.waitFor()
                    if (exitCode != 0)
                        SkillResult(success = false, output = "Exit $exitCode: $output")
                    else
                        SkillResult(success = true, output = output)
                }.fold(
                    onSuccess = { it },
                    onFailure = { SkillResult(success = false, output = "Shell error: ${it.message}") }
                )
            }
            result ?: SkillResult(success = false, output = "Command timed out (30s)")
        }
    }
}

/** Builtin skill wrapping ShellExecutor for Agent use. */
class ShellSkill(private val executor: ShellExecutor = ShellExecutor()) : Skill {
    override val meta = SkillMeta(
        id = "shell",
        name = "Shell Command",
        description = "Runs any shell command via sh -c. Supports pipes, redirects, loops. NOTE: no system Python/pip — use Claw.pip() in mini-apps or the python skill for Python. Timeout 30s.",
        parameters = listOf(
            SkillParam("command", "string", "Shell command or script to execute"),
            SkillParam("args", "string", "Extra arguments appended to command (optional)", required = false),
        ),
        type = SkillType.SHELL,
        injectionLevel = 1,
        nameZh = "Shell 命令",
        descriptionZh = "执行任意 Shell 命令，支持管道和重定向。注意：没有系统 Python/pip，Python 需通过 python skill 或 Claw.pip() 使用，超时 30 秒。",
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val command = params["command"] as? String
            ?: return SkillResult(false, "command is required")
        val args = (params["args"] as? String)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        return executor.execute(command, args)
    }
}
