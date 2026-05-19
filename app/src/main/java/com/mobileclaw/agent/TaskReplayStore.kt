package com.mobileclaw.agent

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.util.UUID

data class TaskReplayStep(
    val index: Int,
    val thought: String,
    val skillId: String?,
    val skillParams: Map<String, Any>?,
    val observation: String,
    val isError: Boolean,
    val timestampMs: Long,
    val hasImage: Boolean = false,
)

data class TaskReplay(
    val id: String,
    val goal: String,
    val summary: String,
    val success: Boolean,
    val taskType: String,
    val roleId: String,
    val roleName: String,
    val steps: List<TaskReplayStep>,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

data class TaskRecipeStep(
    val index: Int,
    val skillId: String,
    val paramsJson: String,
    val note: String,
)

data class TaskRecipe(
    val id: String,
    val title: String,
    val goal: String,
    val taskType: String,
    val roleId: String,
    val sourceReplayId: String,
    val steps: List<TaskRecipeStep>,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

class TaskReplayStore(filesDir: File) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dir = File(filesDir, "task_replays").also { it.mkdirs() }

    fun record(result: AgentResult, taskType: TaskType, role: Role): TaskReplay {
        val steps = result.context.steps.map { step ->
            TaskReplayStep(
                index = step.index,
                thought = step.thought.take(1200),
                skillId = step.skillId,
                skillParams = step.skillParams,
                observation = step.observation.take(3000),
                isError = step.isError,
                timestampMs = step.timestampMs,
                hasImage = !step.imageBase64.isNullOrBlank(),
            )
        }
        val firstTs = result.context.steps.firstOrNull()?.timestampMs ?: System.currentTimeMillis()
        val lastTs = result.context.steps.lastOrNull()?.timestampMs ?: firstTs
        val replay = TaskReplay(
            id = result.context.taskId,
            goal = result.context.goal,
            summary = result.summary,
            success = result.success,
            taskType = taskType.name,
            roleId = role.id,
            roleName = role.name,
            steps = steps,
            durationMs = (lastTs - firstTs).coerceAtLeast(0L),
        )
        save(replay)
        return replay
    }

    fun save(replay: TaskReplay) {
        File(dir, "${replay.id}.json").writeText(gson.toJson(replay))
        prune()
    }

    fun get(id: String): TaskReplay? =
        runCatching { gson.fromJson(File(dir, "$id.json").readText(), TaskReplay::class.java) }.getOrNull()

    fun recent(limit: Int = 50): List<TaskReplay> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file -> runCatching { gson.fromJson(file.readText(), TaskReplay::class.java) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?.take(limit)
            ?: emptyList()

    private fun prune(max: Int = 200) {
        val files = dir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(max).forEach { it.delete() }
    }
}

class TaskRecipeStore(filesDir: File) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dir = File(filesDir, "task_recipes").also { it.mkdirs() }

    fun createFromReplay(replay: TaskReplay, title: String = replay.goal.toRecipeTitle()): TaskRecipe? {
        val actionSteps = replay.steps
            .filter { !it.isError && !it.skillId.isNullOrBlank() }
            .mapNotNull { step ->
                val skillId = step.skillId ?: return@mapNotNull null
                TaskRecipeStep(
                    index = step.index,
                    skillId = skillId,
                    paramsJson = gson.toJson(step.skillParams ?: emptyMap<String, Any>()),
                    note = step.observation.take(240),
                )
            }
        if (actionSteps.isEmpty()) return null
        val existing = list().firstOrNull { it.sourceReplayId == replay.id }
        val recipe = TaskRecipe(
            id = existing?.id ?: "recipe_${UUID.randomUUID().toString().take(8)}",
            title = title,
            goal = replay.goal,
            taskType = replay.taskType,
            roleId = replay.roleId,
            sourceReplayId = replay.id,
            steps = actionSteps,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        save(recipe)
        return recipe
    }

    fun save(recipe: TaskRecipe) {
        File(dir, "${recipe.id}.json").writeText(gson.toJson(recipe))
    }

    fun get(id: String): TaskRecipe? =
        runCatching { gson.fromJson(File(dir, "$id.json").readText(), TaskRecipe::class.java) }.getOrNull()

    fun list(limit: Int = 50): List<TaskRecipe> =
        dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file -> runCatching { gson.fromJson(file.readText(), TaskRecipe::class.java) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?.take(limit)
            ?: emptyList()

    fun delete(id: String): Boolean = File(dir, "$id.json").delete()

    fun buildRunPrompt(recipe: TaskRecipe): String = buildString {
        appendLine("请执行这个已保存的 MobileClaw 任务配方。")
        appendLine("配方：${recipe.title}")
        appendLine("原始目标：${recipe.goal}")
        appendLine("任务类型：${recipe.taskType}")
        appendLine()
        appendLine("历史成功步骤仅作为参考，不要盲目照抄。当前界面或网络状态可能已经变化。")
        appendLine("请先判断当前状态；如果可以复用这些步骤，就按相同意图执行；如果不匹配，就观察后调整。")
        appendLine()
        appendLine("参考步骤：")
        recipe.steps.forEach { step ->
            appendLine("${step.index}. ${step.skillId} ${step.paramsJson.take(500)}")
            if (step.note.isNotBlank()) appendLine("   result: ${step.note.replace('\n', ' ').take(180)}")
        }
    }

    private fun String.toRecipeTitle(): String =
        trim()
            .replace(Regex("""\s+"""), " ")
            .take(28)
            .ifBlank { "Saved task recipe" }
}
