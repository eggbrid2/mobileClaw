package com.mobileclaw.skill.builtin

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.agent.TaskRecipe
import com.mobileclaw.agent.TaskRecipeStore
import com.mobileclaw.agent.TaskReplayStore
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.ui.aipage.AiPageDef
import com.mobileclaw.ui.aipage.AiPageStore
import kotlinx.coroutines.flow.MutableSharedFlow

class TaskRecipeSkill(
    private val replayStore: TaskReplayStore,
    private val recipeStore: TaskRecipeStore,
    private val aiPageStore: AiPageStore,
    private val openPageRequests: MutableSharedFlow<String>,
    private val pendingAgentTask: MutableSharedFlow<String>,
) : Skill {
    private val gson = Gson()

    override val meta = SkillMeta(
        id = "task_recipe",
        name = "Task Replay & Recipe",
        nameZh = "任务回放与配方",
        description = "Inspect task replays, turn successful tasks into reusable recipes, run saved recipes, and create AI Page shortcuts for recipes. Actions: list_replays | get_replay | list_recipes | create_recipe | run_recipe | create_page | delete_recipe.",
        descriptionZh = "查看任务回放，把成功任务沉淀为可复用配方，运行配方，并为配方生成 AI Page 快捷入口。",
        parameters = listOf(
            SkillParam("action", "string", "list_replays | get_replay | list_recipes | create_recipe | run_recipe | create_page | delete_recipe"),
            SkillParam("id", "string", "Replay ID or recipe ID", required = false),
            SkillParam("title", "string", "Optional recipe/page title", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        tags = listOf("任务", "回放", "配方", "自动化"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required")
        return when (action) {
            "list_replays" -> listReplays()
            "get_replay" -> getReplay(params["id"] as? String)
            "list_recipes" -> listRecipes()
            "create_recipe" -> createRecipe(params["id"] as? String, params["title"] as? String)
            "run_recipe" -> runRecipe(params["id"] as? String)
            "create_page" -> createRecipePage(params["id"] as? String, params["title"] as? String)
            "delete_recipe" -> deleteRecipe(params["id"] as? String)
            else -> SkillResult(false, "Unknown action: $action")
        }
    }

    private fun listReplays(): SkillResult {
        val replays = replayStore.recent(20)
        if (replays.isEmpty()) return SkillResult(true, "No task replays yet.")
        return SkillResult(true, buildString {
            appendLine("Recent task replays:")
            replays.forEach { replay ->
                appendLine("• ${replay.id.take(8)}… | ${if (replay.success) "OK" else "FAIL"} | ${replay.taskType} | ${replay.goal.take(48)} | ${replay.steps.size} steps")
            }
        }.trimEnd())
    }

    private fun getReplay(id: String?): SkillResult {
        if (id.isNullOrBlank()) return SkillResult(false, "id is required")
        val replay = findReplay(id) ?: return SkillResult(false, "Replay not found: $id")
        return SkillResult(true, buildString {
            appendLine("${replay.goal}")
            appendLine("Status: ${if (replay.success) "success" else "failed"} · ${replay.taskType} · ${replay.steps.size} steps")
            appendLine("Summary: ${replay.summary}")
            appendLine()
            replay.steps.forEach { step ->
                appendLine("${step.index}. ${step.skillId ?: "note"} ${if (step.isError) "[error]" else ""}")
                if (!step.skillParams.isNullOrEmpty()) appendLine("   params: ${gson.toJson(step.skillParams).take(500)}")
                if (step.observation.isNotBlank()) appendLine("   obs: ${step.observation.replace('\n', ' ').take(260)}")
            }
        }.trimEnd())
    }

    private fun listRecipes(): SkillResult {
        val recipes = recipeStore.list(30)
        if (recipes.isEmpty()) return SkillResult(true, "No task recipes yet. Use create_recipe with a replay id.")
        return SkillResult(true, buildString {
            appendLine("Task recipes:")
            recipes.forEach { recipe ->
                appendLine("• ${recipe.id} | ${recipe.title} | ${recipe.taskType} | ${recipe.steps.size} steps")
            }
        }.trimEnd())
    }

    private fun createRecipe(id: String?, title: String?): SkillResult {
        if (id.isNullOrBlank()) return SkillResult(false, "Replay id is required")
        val replay = findReplay(id) ?: return SkillResult(false, "Replay not found: $id")
        val recipe = recipeStore.createFromReplay(replay, title?.takeIf { it.isNotBlank() } ?: replay.goal.take(28))
            ?: return SkillResult(false, "Replay has no successful tool steps to convert.")
        return SkillResult(true, "Recipe created: ${recipe.id} · ${recipe.title}. Run it with task_recipe(action=run_recipe, id=${recipe.id}).")
    }

    private suspend fun runRecipe(id: String?): SkillResult {
        if (id.isNullOrBlank()) return SkillResult(false, "Recipe id is required")
        val recipe = findRecipe(id) ?: return SkillResult(false, "Recipe not found: $id")
        pendingAgentTask.emit(recipeStore.buildRunPrompt(recipe))
        return SkillResult(true, "Running recipe '${recipe.title}'...")
    }

    private suspend fun createRecipePage(id: String?, title: String?): SkillResult {
        if (id.isNullOrBlank()) return SkillResult(false, "Recipe id is required")
        val recipe = findRecipe(id) ?: return SkillResult(false, "Recipe not found: $id")
        val page = buildRecipePage(recipe, title?.takeIf { it.isNotBlank() } ?: recipe.title)
        aiPageStore.save(page)
        openPageRequests.emit(page.id)
        return SkillResult(true, "Created AI Page shortcut '${page.title}' (${page.id}) for recipe '${recipe.title}'.")
    }

    private fun deleteRecipe(id: String?): SkillResult {
        if (id.isNullOrBlank()) return SkillResult(false, "Recipe id is required")
        return if (recipeStore.delete(id)) SkillResult(true, "Recipe deleted: $id") else SkillResult(false, "Recipe not found: $id")
    }

    private fun findReplay(id: String) =
        replayStore.get(id) ?: replayStore.recent(100).firstOrNull { it.id.startsWith(id) }

    private fun findRecipe(id: String) =
        recipeStore.get(id) ?: recipeStore.list(100).firstOrNull { it.id.startsWith(id) }

    private fun buildRecipePage(recipe: TaskRecipe, title: String): AiPageDef {
        val safeTitle = title.take(24)
        val layoutJson = """
{
  "type":"column",
  "padding":20,
  "gap":14,
  "children":[
    {"type":"text","content":"${escape(safeTitle)}","size":22,"bold":true},
    {"type":"card","title":"Recipe","gap":8,"children":[
      {"type":"info_rows","items":[
        {"label":"Task","value":"${escape(recipe.goal.take(80))}"},
        {"label":"Type","value":"${recipe.taskType}"},
        {"label":"Steps","value":"${recipe.steps.size}"}
      ]},
      {"type":"button","label":"Run recipe","action":"run","style":"filled"}
    ]},
    {"type":"markdown","content":"${escape(recipe.steps.joinToString("\n") { "${it.index}. ${it.skillId}" })}"}
  ]
}
        """.trimIndent()
        val actionsJson = """
{
  "run":[
    {"type":"skill_call","skill":"task_recipe","params":{"action":"run_recipe","id":"${recipe.id}"},"result_key":"run_result"},
    {"type":"toast","text":"Recipe started"}
  ]
}
        """.trimIndent()
        return AiPageDef(
            id = "recipe_${recipe.id.removePrefix("recipe_")}",
            title = safeTitle,
            icon = "bolt",
            description = "Shortcut for saved task recipe: ${recipe.title}",
            layout = JsonParser.parseString(layoutJson).asJsonObject,
            actions = JsonParser.parseString(actionsJson).asJsonObject,
        )
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
