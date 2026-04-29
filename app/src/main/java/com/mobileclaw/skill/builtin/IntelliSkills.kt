package com.mobileclaw.skill.builtin

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mobileclaw.llm.ChatRequest
import com.mobileclaw.llm.LlmGateway
import com.mobileclaw.llm.Message
import com.mobileclaw.skill.HttpSkillConfig
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillLoader
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillRegistry
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ── skill_check ───────────────────────────────────────────────────────────────

/**
 * Returns the complete skill inventory so the agent can reason about whether
 * a new skill is needed before calling create_skill or quick_skill.
 */
class SkillCheckSkill(private val registry: SkillRegistry) : Skill {
    override val meta = SkillMeta(
        id = "skill_check",
        name = "Check Skill Inventory",
        description = "Returns the full list of available skills grouped by type and injection level. " +
            "Call this before creating a new skill to check whether the capability already exists. " +
            "Also shows dynamic (user-generated) skills that may need to be promoted.",
        parameters = listOf(
            SkillParam("task", "string", "Describe what you are trying to do (used to annotate the response)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val task = params["task"] as? String ?: ""
        val all = registry.all().sortedWith(compareBy({ it.meta.injectionLevel }, { it.meta.id }))

        val sb = StringBuilder()
        if (task.isNotBlank()) sb.appendLine("Task context: $task\n")

        sb.appendLine("## Skill Inventory (${all.size} total)\n")

        val byLevel = all.groupBy { it.meta.injectionLevel }
        val levelLabels = mapOf(0 to "Level 0 — Always injected", 1 to "Level 1 — Task-aware injection", 2 to "Level 2 — On-demand (not auto-injected)")

        levelLabels.forEach { (level, label) ->
            val skills = byLevel[level] ?: return@forEach
            sb.appendLine("### $label")
            skills.forEach { skill ->
                val tag = if (skill.meta.isBuiltin) "[builtin]" else "[dynamic/${skill.meta.type.name.lowercase()}]"
                sb.appendLine("- **${skill.meta.id}** $tag: ${skill.meta.description.take(100)}")
                if (skill.meta.parameters.isNotEmpty()) {
                    val paramsStr = skill.meta.parameters.joinToString(", ") {
                        "${it.name}(${if (it.required) "req" else "opt"})"
                    }
                    sb.appendLine("  params: $paramsStr")
                }
            }
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine("To create a new skill from a description: use **quick_skill**")
        sb.appendLine("To search & install from marketplace: use **skill_market(action=search, query=...)**")
        sb.appendLine("To build a custom skill manually: use **create_skill**")

        return SkillResult(true, sb.toString())
    }
}

// ── quick_skill ───────────────────────────────────────────────────────────────

private val QUICK_SKILL_SYSTEM_PROMPT = """
You are a skill generator for MobileClaw, an Android AI agent.
Generate a skill definition as a single JSON object. Output ONLY the JSON — no markdown fences, no explanation.

JSON schema:
{
  "id": "snake_case_id",
  "name": "Human Readable Name",
  "description": "What this skill does (1-2 sentences, shown to the LLM agent)",
  "type": "python" | "http",
  "parameters": [{"name": "param_name", "type": "string"|"number"|"boolean", "description": "...", "required": true|false}],
  "script": "Python 3 script (only for type=python). Use params dict for inputs. Print or return result as string. No subprocess/os/socket/ctypes/importlib/eval/exec/__import__.",
  "http_url": "https://... (only for type=http). Use {param_name} for URL template substitution.",
  "http_method": "GET" | "POST",
  "http_image_response_path": "JSON path to base64 image in response, e.g. 'data[0].b64_json' (optional, only if this skill returns an image)",
  "http_text_response_path": "JSON path to text output in response, e.g. 'choices[0].text' (optional, for extracting specific JSON fields)"
}

Rules:
- Prefer type=python for logic/computation; type=http for calling public APIs.
- Python: available libraries: requests, json, re, math, datetime, base64, hashlib, urllib. Forbidden: subprocess,os,socket/ctypes/importlib/eval/exec/__import__.
- HTTP: use public no-auth APIs where possible. Use {param_name} templates in the URL for dynamic values.
- For HTTP skills that return images (like image generation APIs), set http_image_response_path to the JSON path of the base64 image data.
- Keep scripts concise and focused. Output is the last printed value or the string returned.
- id must be lowercase snake_case alphanumeric only.
""".trimIndent()

/**
 * Generates and persists a new skill from a natural-language description
 * by making a focused LLM call that returns a complete SkillDefinition JSON.
 */
class QuickSkillSkill(
    private val llm: LlmGateway,
    private val loader: SkillLoader,
) : Skill {

    private val gson = Gson()

    override val meta = SkillMeta(
        id = "quick_skill",
        name = "Generate Skill from Description",
        description = "Automatically generates and saves a new skill from a natural-language description using the LLM. " +
            "Faster than create_skill — just describe what you need. The generated skill starts at level 2 and requires user promotion.",
        parameters = listOf(
            SkillParam("description", "string", "What the skill should do — be specific about inputs, outputs, and any APIs"),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val description = params["description"] as? String ?: return SkillResult(false, "description is required")

        val response = runCatching {
            llm.chat(
                ChatRequest(
                    messages = listOf(
                        Message(role = "system", content = QUICK_SKILL_SYSTEM_PROMPT),
                        Message(role = "user", content = "Generate a skill that: $description"),
                    ),
                    stream = false,
                )
            )
        }.getOrElse { return SkillResult(false, "LLM error: ${it.message}") }

        val raw = response.content?.trim() ?: return SkillResult(false, "LLM returned empty response.")

        // Strip accidental markdown fences
        val json = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val parsed = runCatching { JsonParser.parseString(json).asJsonObject }
            .getOrElse { return SkillResult(false, "LLM output is not valid JSON:\n$json") }

        return runCatching {
            val skillType = parsed["type"]?.asString ?: "python"
            val skillParams = parsed["parameters"]?.asJsonArray?.mapNotNull { el ->
                val o = el.asJsonObject
                SkillParam(
                    name = o["name"]?.asString ?: return@mapNotNull null,
                    type = o["type"]?.asString ?: "string",
                    description = o["description"]?.asString ?: "",
                    required = o["required"]?.asBoolean ?: true,
                )
            } ?: emptyList()

            val skillMeta = SkillMeta(
                id = parsed["id"]?.asString ?: return SkillResult(false, "Generated JSON missing 'id'"),
                name = parsed["name"]?.asString ?: return SkillResult(false, "Generated JSON missing 'name'"),
                description = parsed["description"]?.asString ?: description,
                parameters = skillParams,
                type = SkillType.valueOf(skillType.uppercase()),
                injectionLevel = 2,
                isBuiltin = false,
            )

            val def = when (skillType.lowercase()) {
                "python" -> {
                    val script = parsed["script"]?.asString
                        ?: return SkillResult(false, "Generated JSON missing 'script' for python type")
                    SkillDefinition(meta = skillMeta, script = script)
                }
                "http" -> {
                    val url = parsed["http_url"]?.asString
                        ?: return SkillResult(false, "Generated JSON missing 'http_url' for http type")
                    val method = parsed["http_method"]?.asString ?: "GET"
                    val imageResponsePath = parsed["http_image_response_path"]?.asString?.ifBlank { null }
                    val textResponsePath = parsed["http_text_response_path"]?.asString?.ifBlank { null }
                    SkillDefinition(
                        meta = skillMeta,
                        httpConfig = HttpSkillConfig(
                            url = url,
                            method = method,
                            imageResponsePath = imageResponsePath,
                            textResponsePath = textResponsePath,
                        ),
                    )
                }
                else -> return SkillResult(false, "Unsupported type: $skillType")
            }

            loader.persist(def)
            SkillResult(
                success = true,
                output = "Skill '${skillMeta.id}' generated and saved (level 2). " +
                    "A user must promote it to level 1 for auto-injection. " +
                    "You can call it immediately via create_skill or by its id if the registry is refreshed.",
            )
        }.getOrElse { SkillResult(false, "Failed to save skill: ${it.message}") }
    }
}

// ── skill_market ──────────────────────────────────────────────────────────────

private const val DEFAULT_MARKET_URL =
    "https://raw.githubusercontent.com/mobileclaw/skill-market/main/index.json"

/**
 * JSON entry in the market index file.
 * Each entry points to a downloadable SkillDefinition JSON file.
 */
private data class MarketEntry(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val url: String,
)

/**
 * Searches, lists, and installs skills from a community marketplace hosted on GitHub.
 * The market is a JSON array of MarketEntry objects; each entry has a `url` field
 * pointing to a complete SkillDefinition JSON.
 */
class SkillMarketSkill(
    private val loader: SkillLoader,
    private val marketUrl: String = DEFAULT_MARKET_URL,
) : Skill {

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override val meta = SkillMeta(
        id = "skill_market",
        name = "Skill Marketplace",
        description = "Searches and installs skills from the MobileClaw community marketplace. " +
            "Actions: 'search' (find skills by keyword), 'install' (download and install by id), 'list' (show all available).",
        parameters = listOf(
            SkillParam("action", "string", "'search' | 'install' | 'list'"),
            SkillParam("query", "string", "Search keyword (required for action=search)", required = false),
            SkillParam("id", "string", "Skill ID to install (required for action=install)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required: search | install | list")
        return when (action) {
            "list", "search" -> {
                val query = (params["query"] as? String)?.lowercase() ?: ""
                val entries = fetchIndex()
                val filtered = if (query.isBlank()) entries
                else entries.filter {
                    it.id.contains(query) || it.name.lowercase().contains(query) ||
                        it.description.lowercase().contains(query) ||
                        it.tags.any { t -> t.lowercase().contains(query) }
                }
                if (filtered.isEmpty()) return SkillResult(true, "No skills found for '$query'.")
                val lines = filtered.joinToString("\n") { e ->
                    "- **${e.id}**: ${e.description.take(100)}" +
                        if (e.tags.isNotEmpty()) " [${e.tags.joinToString(", ")}]" else ""
                }
                SkillResult(true, "Found ${filtered.size} skill(s):\n$lines\n\nUse skill_market(action=install, id=<id>) to install.")
            }
            "install" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required for install")
                val entries = fetchIndex()
                val entry = entries.find { it.id == id }
                    ?: return SkillResult(false, "Skill '$id' not found in marketplace. Use action=list to browse.")
                val defJson = fetchUrl(entry.url)
                val def = runCatching { gson.fromJson(defJson, SkillDefinition::class.java) }
                    .getOrElse { return SkillResult(false, "Invalid skill definition at ${entry.url}: ${it.message}") }
                runCatching { loader.persist(def) }
                    .getOrElse { return SkillResult(false, "Install failed: ${it.message}") }
                SkillResult(true, "Skill '${def.meta.id}' installed from marketplace (level ${def.meta.injectionLevel}).")
            }
            else -> SkillResult(false, "Unknown action: $action. Use search, install, or list.")
        }
    }

    private suspend fun fetchIndex(): List<MarketEntry> = withContext(Dispatchers.IO) {
        val json = fetchUrl(marketUrl)
        runCatching {
            gson.fromJson(json, Array<MarketEntry>::class.java).toList()
        }.getOrElse { throw RuntimeException("Failed to parse market index: ${it.message}") }
    }

    private suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(url)
                .header("User-Agent", "MobileClaw/1.0")
                .get().build()
            httpClient.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string()
                    if (body != null) cont.resume(body)
                    else cont.resumeWithException(RuntimeException("Empty response from $url"))
                }
            })
        }
    }
}
