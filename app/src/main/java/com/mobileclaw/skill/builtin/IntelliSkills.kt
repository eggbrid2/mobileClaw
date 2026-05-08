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
import com.mobileclaw.vpn.AppHttpProxy
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
        nameZh = "查看技能库",
        descriptionZh = "列出当前已有的技能及其状态。",
        tags = listOf("技能"),
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
        nameZh = "快速生成技能",
        descriptionZh = "根据描述自动生成新技能。",
        tags = listOf("技能"),
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

/**
 * Searches and installs skills from multiple real skill markets:
 * - clawhub: https://clawhub.ai — official OpenClaw registry (13k+ skills, no auth needed for search)
 * - skillsmp: https://skillsmp.com — aggregator (270k+ skills)
 * - local: bundled catalog in SkillMarket.kt
 *
 * ClawHub/SkillsMP skills are SKILL.md (markdown prompt-injection). On install,
 * the content is stored as a Python skill that returns the full SKILL.md when invoked.
 * Set injectionLevel=0 or 1 after install to auto-inject into the system prompt.
 */
class SkillMarketSkill(
    private val loader: SkillLoader,
) : Skill {

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    override val meta = SkillMeta(
        id = "skill_market",
        name = "Skill Marketplace",
        description = "Search and install skills from real skill markets.\n" +
            "market= 'clawhub' (OpenClaw官方, 13k+ skills) | 'skillsmp' (聚合270k+) | 'local' (内置推荐)\n" +
            "Actions: 'search' (find by keyword), 'install' (download+install by slug), 'list' (browse local)",
        parameters = listOf(
            SkillParam("action", "string", "'search' | 'install' | 'list'"),
            SkillParam("market", "string", "'clawhub' | 'skillsmp' | 'local' (default: clawhub)", required = false),
            SkillParam("query", "string", "Search keyword (for action=search)", required = false),
            SkillParam("slug", "string", "Skill slug to install, e.g. 'username/skill-name' (for action=install)", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "技能市场",
        descriptionZh = "从 ClawHub、SkillsMP 等真实技能市场搜索并安装技能。",
        tags = listOf("技能"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required: search | install | list")
        val market = (params["market"] as? String)?.lowercase() ?: "clawhub"

        return when (action) {
            "search" -> {
                val query = params["query"] as? String ?: return SkillResult(false, "query is required for search")
                when (market) {
                    "clawhub"  -> searchClawHub(query)
                    "skillsmp" -> searchSkillsMP(query)
                    "local"    -> searchLocal(query)
                    else -> SkillResult(false, "Unknown market: $market. Use clawhub, skillsmp, or local.")
                }
            }
            "list" -> searchLocal("")
            "install" -> {
                val slug = params["slug"] as? String ?: return SkillResult(false, "slug is required for install (e.g. 'username/skill-name')")
                when (market) {
                    "clawhub"  -> installFromClawHub(slug)
                    "local"    -> installLocal(slug)
                    else -> SkillResult(false, "Install from '$market' not yet supported. Use clawhub or local.")
                }
            }
            else -> SkillResult(false, "Unknown action: $action. Use search, install, or list.")
        }
    }

    // ── ClawHub ───────────────────────────────────────────────────────────────

    private suspend fun searchClawHub(query: String): SkillResult = withContext(Dispatchers.IO) {
        val url = "https://clawhub.ai/api/v1/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=12"
        val json = runCatching { fetchUrl(url) }.getOrElse {
            return@withContext SkillResult(false, "ClawHub search failed: ${it.message}")
        }
        runCatching {
            val obj = JsonParser.parseString(json).asJsonObject
            val items = obj.getAsJsonArray("items") ?: obj.getAsJsonArray("results")
                ?: return@withContext SkillResult(false, "Unexpected response: ${json.take(200)}")
            if (items.size() == 0) return@withContext SkillResult(true, "No ClawHub skills found for '$query'.")
            val lines = mutableListOf("ClawHub results for '$query' (${items.size()} found):\n")
            items.forEach { el ->
                val o = el.asJsonObject
                val slug = o["slug"]?.asString ?: o["name"]?.asString ?: "unknown"
                val desc = o["description"]?.asString?.take(100) ?: ""
                val stars = o["stars"]?.asInt ?: o["stargazers"]?.asInt ?: 0
                lines += "• **$slug**${if (stars > 0) " ⭐$stars" else ""}: $desc"
            }
            lines += "\nInstall: skill_market(action=install, market=clawhub, slug='username/skill-name')"
            SkillResult(true, lines.joinToString("\n"))
        }.getOrElse { SkillResult(false, "Parse error: ${it.message}\nRaw: ${json.take(300)}") }
    }

    private suspend fun installFromClawHub(slug: String): SkillResult = withContext(Dispatchers.IO) {
        // Try multiple sources: ClawHub API, then GitHub raw
        val content = runCatching {
            val url = "https://clawhub.ai/api/v1/skills/$slug"
            val json = fetchUrl(url)
            val obj = JsonParser.parseString(json).asJsonObject
            obj["content"]?.asString ?: obj["readme"]?.asString ?: json
        }.getOrElse {
            runCatching {
                fetchUrl("https://raw.githubusercontent.com/openclaw/skills/main/skills/$slug/SKILL.md")
            }.getOrElse {
                return@withContext SkillResult(false, "Cannot fetch skill '$slug' from ClawHub or GitHub: ${it.message}")
            }
        }

        val skillId = slug.replace("/", "_").replace("-", "_").replace(Regex("[^a-z0-9_]"), "")
        val firstLine = content.lines().firstOrNull { it.startsWith("#") }?.removePrefix("#")?.trim() ?: slug
        val desc = content.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: firstLine

        val contentEscaped = content.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
        val script = "print(\"\"\"$contentEscaped\"\"\")"

        val def = SkillDefinition(
            meta = SkillMeta(
                id = "ch_$skillId".take(64),
                name = firstLine.take(60),
                description = "ClawHub: $slug\n$desc",
                type = SkillType.PYTHON,
                injectionLevel = 2,
                isBuiltin = false,
                nameZh = firstLine.take(60),
                tags = listOf("技能"),
            ),
            script = script,
        )
        runCatching { loader.persist(def) }.getOrElse {
            return@withContext SkillResult(false, "Install failed: ${it.message}")
        }
        SkillResult(true, "Installed ClawHub skill '${def.meta.id}' (level 2, on-demand). Promote to level 0 or 1 to auto-inject into prompts.")
    }

    // ── SkillsMP ──────────────────────────────────────────────────────────────

    private suspend fun searchSkillsMP(query: String): SkillResult = withContext(Dispatchers.IO) {
        // SkillsMP public search (no key needed for read)
        val url = "https://skillsmp.com/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=10"
        val json = runCatching { fetchUrl(url) }.getOrElse {
            return@withContext SkillResult(false, "SkillsMP search failed: ${it.message}. Try market=clawhub instead.")
        }
        runCatching {
            val obj = JsonParser.parseString(json).asJsonObject
            val items = obj.getAsJsonArray("skills") ?: obj.getAsJsonArray("items")
                ?: return@withContext SkillResult(false, "Unexpected SkillsMP response: ${json.take(200)}")
            if (items.size() == 0) return@withContext SkillResult(true, "No SkillsMP skills found for '$query'.")
            val lines = mutableListOf("SkillsMP results for '$query':\n")
            items.forEach { el ->
                val o = el.asJsonObject
                val name = o["name"]?.asString ?: o["slug"]?.asString ?: "unknown"
                val desc = o["description"]?.asString?.take(100) ?: ""
                lines += "• **$name**: $desc"
            }
            SkillResult(true, lines.joinToString("\n"))
        }.getOrElse { SkillResult(false, "SkillsMP parse error: ${it.message}") }
    }

    // ── Local bundled catalog ─────────────────────────────────────────────────

    private fun searchLocal(query: String): SkillResult {
        val q = query.lowercase()
        val entries = com.mobileclaw.skill.SkillMarket.catalog.filter { entry ->
            q.isBlank() ||
                entry.def.meta.id.contains(q) ||
                (entry.def.meta.nameZh ?: "").contains(q) ||
                entry.def.meta.name.lowercase().contains(q) ||
                entry.category.contains(q)
        }
        if (entries.isEmpty()) return SkillResult(true, "No local market skills found for '$query'.")
        val lines = mutableListOf("Local bundled skills${if (q.isNotBlank()) " matching '$q'" else ""}:\n")
        entries.groupBy { it.category }.forEach { (cat, catEntries) ->
            lines += "【$cat】"
            catEntries.forEach { e ->
                val installed = runCatching { loader.isInstalled(e.def.meta.id) }.getOrDefault(false)
                val tag = if (installed) " ✓已安装" else ""
                lines += "  ${e.emoji} ${e.def.meta.nameZh ?: e.def.meta.name}$tag — ${e.def.meta.descriptionZh ?: e.def.meta.description.take(60)}"
                lines += "    → install: skill_market(action=install, market=local, slug='${e.def.meta.id}')"
            }
        }
        return SkillResult(true, lines.joinToString("\n"))
    }

    private fun installLocal(slug: String): SkillResult {
        val entry = com.mobileclaw.skill.SkillMarket.catalog.find { it.def.meta.id == slug }
            ?: return SkillResult(false, "Local skill '$slug' not found. Use action=list to browse.")
        return runCatching {
            loader.persist(entry.def)
            SkillResult(true, "Installed local skill '${entry.def.meta.nameZh ?: entry.def.meta.name}' (${slug}).")
        }.getOrElse { SkillResult(false, "Install failed: ${it.message}") }
    }

    // ── Shared HTTP ───────────────────────────────────────────────────────────

    private suspend fun fetchUrl(url: String): String = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val req = Request.Builder().url(url)
                .header("User-Agent", "MobileClaw/1.0 (Android; OpenClaw-compatible)")
                .header("Accept", "application/json")
                .get().build()
            httpClient.newCall(req).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) =
                    cont.resumeWithException(e)
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        cont.resumeWithException(RuntimeException("HTTP ${response.code}: ${body.take(200)}"))
                    } else if (body.isBlank()) {
                        cont.resumeWithException(RuntimeException("Empty response from $url"))
                    } else {
                        cont.resume(body)
                    }
                }
            })
        }
    }
}
