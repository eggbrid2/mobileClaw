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
                    process.waitFor()
                    output
                }.fold(
                    onSuccess = { SkillResult(success = true, output = it) },
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
        description = "Runs any shell command or script via sh -c. Supports pipes, redirects, loops, pip install, etc. Timeout 30s.",
        parameters = listOf(
            SkillParam("command", "string", "Shell command or script (e.g. 'pip install requests && python -c \"import requests; print(requests.__version__)\"')"),
            SkillParam("args", "string", "Extra arguments appended to command (optional)", required = false),
        ),
        type = SkillType.SHELL,
        injectionLevel = 1,
        nameZh = "Shell 命令",
        descriptionZh = "执行任意 Shell 命令或脚本，支持管道、pip install 等，超时 30 秒。",
        tags = listOf("系统"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val command = params["command"] as? String
            ?: return SkillResult(false, "command is required")
        val args = (params["args"] as? String)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        return executor.execute(command, args)
    }
}
