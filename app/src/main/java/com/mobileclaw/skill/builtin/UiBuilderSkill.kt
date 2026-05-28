package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.artifact.ArtifactHistoryEntry
import com.mobileclaw.artifact.ArtifactSpec
import com.mobileclaw.server.LocalApiServer
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.ui.aipage.AiPageDef
import com.mobileclaw.ui.aipage.AiPageStore
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * AI skill that creates and manages native Compose pages.
 *
 * Pages are stored in {filesDir}/ai_pages/{id}.json and rendered as full Android UI.
 * Unlike HTML mini-apps, native pages execute Kotlin Compose components and have
 * direct access to Android system APIs via the action DSL.
 */
class UiBuilderSkill(
    private val store: AiPageStore,
    val openRequests: MutableSharedFlow<String>,
    val pinRequests: MutableSharedFlow<String>,
) : Skill {

    private val gson = GsonBuilder().create()

    override val meta = SkillMeta(
        id = "ui_builder",
        name = "AI Native Page Builder",
        nameZh = "AI 原生页面生成",
        description = "Preferred tool for creating user-facing pages inside MobileClaw: native Android pages, dashboards, forms, settings panels, management screens, data viewers, control pages, and lightweight tools. " +
            "Use this before mini-app/HTML unless the user explicitly needs a program/game/custom HTML runtime. Never return page JSON or code in chat; call this tool. " +
            "Pages support real UI components, Android APIs, app context data, HTTP, shell, notifications, sensors. Actions: create | update | analyze_change | inspect_structure | inspect_runtime | validate | list | get | delete | open | pin_shortcut | get_guide",
        descriptionZh = "优先用于创建 MobileClaw 内的用户可见原生页面：AI 页面、仪表盘、表单、设置面板、管理页、数据查看器、控制页和轻量工具。除非用户明确需要程序/小游戏/自定义 HTML 运行时，否则优先用此工具，不要在聊天里返回页面 JSON 或代码。",
        parameters = listOf(
            SkillParam("action", "string", required = true,
                description = "create | update | analyze_change | validate | list | get | delete | open | pin_shortcut | get_guide"),
            SkillParam("limit", "number", required = false, description = "Optional limit for structure inspection output."),
            SkillParam("id", "string", required = false,
                description = "Page ID in snake_case (required for all except list/get_guide)"),
            SkillParam("title", "string", required = false, description = "Display title shown in the top bar"),
            SkillParam("icon", "string", required = false, description = "Semantic icon key for the page, e.g. page, chat, settings, weather, profile"),
            SkillParam("description", "string", required = false, description = "Short description of the page"),
            SkillParam("goal", "string", required = false, description = "Original user goal for this page"),
            SkillParam("required_features", "array", required = false, description = "Required features that must be preserved across updates"),
            SkillParam("constraints", "array", required = false, description = "Constraints such as style, behavior, or platform requirements"),
            SkillParam("accepted_corrections", "array", required = false, description = "User-approved corrections that should persist"),
            SkillParam("known_bugs", "array", required = false, description = "Known bugs still pending"),
            SkillParam("non_goals", "array", required = false, description = "Out-of-scope items for this page"),
            SkillParam("change_request", "string", required = false, description = "Latest user correction or requested change"),
            SkillParam("state", "object", required = false,
                description = "Initial state object: {\"key\": \"initial_value\"}. String JSON is also accepted."),
            SkillParam("layout", "object", required = false,
                description = "Component tree object (see get_guide for reference). String JSON is also accepted."),
            SkillParam("actions", "object", required = false,
                description = "Actions object: {\"actionName\": [{\"type\":\"...\",\"key\":\"...\"},...]}. String JSON is also accepted."),
        ),
        injectionLevel = 1,
        type = SkillType.NATIVE,
        categories = listOf(SkillToolCategory.ARTIFACT, SkillToolCategory.SKILL),
        tags = listOf("页面", "应用"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required")

        return when (action) {

            "get_guide" -> SkillResult(true, GUIDE)

            "list" -> {
                val pages = store.getAll()
                if (pages.isEmpty()) {
                    SkillResult(true, "No AI pages yet. Use ui_builder(action=create, ...) to create one.")
                } else {
                    val list = pages.joinToString("\n") { "- ${it.id}: ${it.title} ${it.icon} (v${it.version})" }
                    SkillResult(true, "AI Pages:\n$list")
                }
            }

            "get" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                val page = store.get(id) ?: return SkillResult(false, "Page not found: $id")
                SkillResult(true, gson.toJson(page))
            }

            "analyze_change" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                val page = store.get(id) ?: return SkillResult(false, "Page not found: $id")
                val changeRequest = params["change_request"] as? String ?: return SkillResult(false, "change_request required")
                val output = linkedMapOf(
                    "artifact_type" to "ai_native_page",
                    "id" to page.id,
                    "title" to page.title,
                    "goal" to page.spec.goal.ifBlank { page.description.ifBlank { page.title } },
                    "current_features" to page.spec.currentFeatures.ifEmpty { summarizePageFeatures(page) },
                    "preserve_features" to page.spec.requiredFeatures,
                    "constraints" to page.spec.constraints,
                    "accepted_corrections" to page.spec.acceptedCorrections,
                    "known_bugs" to page.spec.knownBugs,
                    "non_goals" to page.spec.nonGoals,
                    "last_diff_summary" to page.spec.lastDiffSummary,
                    "patch_focus" to inferPatchFocus(changeRequest),
                    "change_type" to inferChangeType(changeRequest),
                    "patch_brief" to "update only the smallest affected part, preserve all unrelated behavior, and keep the page runnable after the edit",
                    "change_request" to changeRequest,
                    "recommended_mode" to "patch",
                    "needs_runtime_repair" to false,
                    "debug_protocol" to AI_NATIVE_PAGE_DEBUG_PROTOCOL,
                )
                SkillResult(true, gson.toJson(output))
            }

            "inspect_structure" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                val page = store.get(id) ?: return SkillResult(false, "Page not found: $id")
                val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(3, 40) ?: 16
                val analysis = inspectNativePageStructure(page, limit)
                SkillResult(true, gson.toJson(analysis))
            }

            "inspect_runtime" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                val page = store.get(id) ?: return SkillResult(false, "Page not found: $id")
                val structure = inspectNativePageStructure(page, limit = 12)
                val runtimeIssues = validateNativePageRuntime(page.layout.toString(), page.actions.toString())
                val warnings = detectNativePageWarnings(page)
                val output = linkedMapOf(
                    "artifact_type" to "ai_native_page",
                    "action" to "inspect_runtime",
                    "id" to page.id,
                    "title" to page.title,
                    "state_keys" to page.state.keys.toList().take(20),
                    "action_names" to page.actions.keySet().toList().take(20),
                    "node_count" to structure["node_count"],
                    "component_types" to structure["component_types"],
                    "structure_warnings" to structure["structure_warnings"],
                    "runtime_issues" to runtimeIssues,
                    "warnings" to warnings,
                    "needs_runtime_repair" to (runtimeIssues.isNotEmpty() || warnings.isNotEmpty()),
                    "debug_protocol" to AI_NATIVE_PAGE_DEBUG_PROTOCOL,
                    "summary" to buildString {
                        append("Runtime inspection for '${page.id}'.")
                        if (runtimeIssues.isNotEmpty()) append(" Runtime issues: ${runtimeIssues.joinToString(" | ")}.")
                        if (warnings.isNotEmpty()) append(" Warnings: ${warnings.joinToString(" | ")}.")
                        if (runtimeIssues.isEmpty() && warnings.isEmpty()) append(" No runtime issues detected.")
                    }
                )
                SkillResult(runtimeIssues.isEmpty(), gson.toJson(output))
            }

            "validate" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                val page = store.get(id) ?: return SkillResult(false, "Page not found: $id")
                val body = (page.layout.toString() + "\n" + page.actions.toString()).lowercase()
                val required = page.spec.requiredFeatures
                val current = page.spec.currentFeatures.ifEmpty { summarizePageFeatures(page) }
                val missingRequired = required.filter { feature ->
                    val token = feature.trim().lowercase()
                    token.isNotBlank() && token !in body
                }
                val missingCurrent = current.filter { feature ->
                    val token = feature.trim().lowercase()
                    token.isNotBlank() && token !in body
                }
                val runtimeIssues = validateNativePageRuntime(page.layout.toString(), page.actions.toString())
                val ok = missingRequired.isEmpty() && missingCurrent.isEmpty() && runtimeIssues.isEmpty()
                val output = linkedMapOf(
                    "artifact_type" to "ai_native_page",
                    "id" to page.id,
                    "ok" to ok,
                    "required_features" to required,
                    "current_features" to current,
                    "missing_required_features" to missingRequired,
                    "missing_snapshot_features" to missingCurrent,
                    "runtime_issues" to runtimeIssues,
                    "needs_runtime_repair" to (runtimeIssues.isNotEmpty() || missingRequired.isNotEmpty() || missingCurrent.isNotEmpty()),
                    "debug_protocol" to AI_NATIVE_PAGE_DEBUG_PROTOCOL,
                    "summary" to if (ok) {
                        "Required features preserved and runtime validation passed."
                    } else {
                        buildString {
                            append("Artifact validation failed.")
                            if (missingRequired.isNotEmpty()) append(" Missing required features: ${missingRequired.joinToString(", ")}.")
                            if (missingCurrent.isNotEmpty()) append(" Missing snapshot features: ${missingCurrent.joinToString(", ")}.")
                            if (runtimeIssues.isNotEmpty()) append(" Runtime issues: ${runtimeIssues.joinToString(" | ")}.")
                        }
                    }
                )
                SkillResult(ok, gson.toJson(output))
            }

            "create", "update" -> {
                val id = (params["id"] as? String)?.trim()?.replace(" ", "_")
                    ?: return SkillResult(false, "id required")
                val existing = store.get(id)
                if (action == "update" && existing == null) {
                    return SkillResult(false, "Page not found: $id. Use action=create to create it first.")
                }

                val title = params["title"] as? String ?: existing?.title ?: id
                val icon = params["icon"] as? String ?: existing?.icon ?: "page"
                val description = params["description"] as? String ?: existing?.description ?: ""
                val changeRequest = params["change_request"] as? String ?: ""

                val state: Map<String, String> = params["state"]?.let { raw ->
                    parseJsonObject(raw)?.entrySet()?.associate { (k, v) -> k to jsonValueToStateString(v) }
                } ?: existing?.state ?: emptyMap()

                val layout: JsonObject = params["layout"]?.let { raw -> parseJsonObject(raw) }
                    ?: existing?.layout
                    ?: JsonObject()

                val actions: JsonObject = params["actions"]?.let { raw -> parseJsonObject(raw) }
                    ?: existing?.actions
                    ?: JsonObject()

                val inferredCurrentFeatures = summarizePageFeatures(title, description, state, layout, actions)
                val spec = mergeSpec(
                    existing = existing?.spec,
                    params = params,
                    title = title,
                    description = description,
                    changeRequest = changeRequest,
                    inferredCurrentFeatures = inferredCurrentFeatures,
                )
                val history = buildHistory(existing?.history, action, changeRequest.ifBlank { description }, spec.currentSummary)

                val def = AiPageDef(
                    id = id,
                    title = title,
                    icon = icon,
                    version = (existing?.version ?: 0) + 1,
                    description = description,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    state = state,
                    layout = layout,
                    actions = actions,
                    spec = spec,
                    history = history,
                )
                store.save(def)
                val output = linkedMapOf(
                    "artifact_type" to "ai_native_page",
                    "action" to action,
                    "id" to def.id,
                    "title" to def.title,
                    "version" to def.version,
                    "goal" to def.spec.goal,
                    "current_features" to def.spec.currentFeatures,
                    "required_features" to def.spec.requiredFeatures,
                    "last_diff_summary" to def.spec.lastDiffSummary,
                    "change_request" to changeRequest,
                    "needs_runtime_repair" to false,
                    "debug_protocol" to AI_NATIVE_PAGE_DEBUG_PROTOCOL,
                    "post_update_checklist" to listOf(
                        "run validate immediately after every meaningful update",
                        "if runtime_issues or missing features remain, do one focused repair pass instead of rewriting the page",
                    ),
                    "open_hint" to "ui_builder(action=open, id=$id)",
                    "summary" to if (action == "create") {
                        "Created AI native page '$id' at version ${def.version}."
                    } else {
                        "Updated AI native page '$id' to version ${def.version}."
                    }
                )
                SkillResult(true, gson.toJson(output))
            }

            "delete" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                if (store.get(id) == null) return SkillResult(false, "Page not found: $id")
                store.delete(id)
                SkillResult(true, "Page '$id' deleted.")
            }

            "open" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                if (store.get(id) == null) return SkillResult(false, "Page not found: $id")
                openRequests.emit(id)
                SkillResult(true, "Opening page '$id'...")
            }

            "pin_shortcut" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id required")
                if (store.get(id) == null) return SkillResult(false, "Page not found: $id")
                pinRequests.emit(id)
                SkillResult(true, "Requesting launcher shortcut for page '$id'. The user will see a dialog to confirm.")
            }

            else -> SkillResult(false, "Unknown action: $action. Use: create | update | analyze_change | inspect_structure | inspect_runtime | validate | list | get | delete | open | pin_shortcut | get_guide")
        }
    }

    private fun parseJsonObject(raw: Any): JsonObject? {
        return when (raw) {
            is JsonObject -> raw
            is JsonElement -> runCatching { raw.asJsonObject }.getOrNull()
            is String -> runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            else -> runCatching { gson.toJsonTree(raw).asJsonObject }.getOrNull()
        }
    }

    private fun jsonValueToStateString(value: JsonElement): String {
        return when {
            value.isJsonNull -> ""
            value.isJsonPrimitive -> value.asJsonPrimitive.let { primitive ->
                when {
                    primitive.isString -> primitive.asString
                    else -> primitive.toString()
                }
            }
            else -> gson.toJson(value)
        }
    }

    private fun parseStringList(value: Any?): List<String>? = when (value) {
        is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
        is String -> runCatching {
            JsonParser.parseString(value).asJsonArray.mapNotNull { runCatching { it.asString.trim() }.getOrNull() }.filter { it.isNotBlank() }
        }.getOrNull()
        else -> null
    }

    private fun mergeSpec(
        existing: ArtifactSpec?,
        params: Map<String, Any>,
        title: String,
        description: String,
        changeRequest: String,
        inferredCurrentFeatures: List<String>,
    ): ArtifactSpec {
        val base = existing ?: ArtifactSpec()
        val goal = (params["goal"] as? String)?.takeIf { it.isNotBlank() } ?: base.goal.ifBlank { description.ifBlank { title } }
        val requiredFeatures = parseStringList(params["required_features"]) ?: base.requiredFeatures
        val currentFeatures = inferredCurrentFeatures.ifEmpty { base.currentFeatures }
        val constraints = parseStringList(params["constraints"]) ?: base.constraints
        val acceptedCorrections = (base.acceptedCorrections + parseStringList(params["accepted_corrections"]).orEmpty() + listOf(changeRequest))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .takeLast(20)
        val knownBugs = parseStringList(params["known_bugs"]) ?: base.knownBugs
        val nonGoals = parseStringList(params["non_goals"]) ?: base.nonGoals
        val summary = buildString {
            append(goal)
            if (requiredFeatures.isNotEmpty()) append(" | features=${requiredFeatures.joinToString("; ")}")
            else if (currentFeatures.isNotEmpty()) append(" | current=${currentFeatures.joinToString("; ")}")
            if (changeRequest.isNotBlank()) append(" | latest_change=$changeRequest")
        }.take(400)
        val diffSummary = buildFeatureDiffSummary(base.currentFeatures, currentFeatures)
        return ArtifactSpec(
            goal = goal,
            requiredFeatures = requiredFeatures,
            currentFeatures = currentFeatures,
            constraints = constraints,
            acceptedCorrections = acceptedCorrections,
            knownBugs = knownBugs,
            nonGoals = nonGoals,
            currentSummary = summary,
            lastDiffSummary = diffSummary,
        )
    }

    private fun buildHistory(
        existing: List<ArtifactHistoryEntry>?,
        action: String,
        request: String,
        summary: String,
    ): List<ArtifactHistoryEntry> =
    ((existing ?: emptyList()) + ArtifactHistoryEntry(action = action, request = request, summary = summary)).takeLast(24)

    private fun summarizePageFeatures(page: AiPageDef): List<String> =
        summarizePageFeatures(page.title, page.description, page.state, page.layout, page.actions)

    private fun summarizePageFeatures(
        title: String,
        description: String,
        state: Map<String, String>,
        layout: JsonObject,
        actions: JsonObject,
    ): List<String> {
        val body = (layout.toString() + "\n" + actions.toString()).lowercase()
        val inferred = linkedSetOf<String>()
        if (title.isNotBlank()) inferred += "title:${title.trim().take(40)}"
        if (description.isNotBlank()) inferred += "description"
        if (state.isNotEmpty()) inferred += "state:${state.keys.take(6).joinToString("|")}"
        if (body.contains("\"type\":\"list\"")) inferred += "list"
        if (body.contains("\"type\":\"tabs\"")) inferred += "tabs"
        if (body.contains("\"type\":\"grid\"")) inferred += "grid"
        if (body.contains("\"type\":\"form\"")) inferred += "form"
        if (body.contains("\"type\":\"input\"")) inferred += "input"
        if (body.contains("\"type\":\"button\"")) inferred += "button"
        if (body.contains("\"type\":\"switch\"")) inferred += "switch"
        if (body.contains("\"type\":\"slider\"")) inferred += "slider"
        if (body.contains("\"type\":\"markdown\"")) inferred += "markdown"
        if (body.contains("\"type\":\"chart\"")) inferred += "chart"
        if (body.contains("navigate_page")) inferred += "page_navigation"
        if (body.contains("\"type\":\"http\"") || body.contains("\"type\": \"http\"")) inferred += "http_action"
        if (body.contains("\"type\":\"shell\"") || body.contains("\"type\": \"shell\"")) inferred += "shell_action"
        if (body.contains("\"type\":\"python\"") || body.contains("\"type\": \"python\"")) inferred += "python_action"
        return inferred.take(16)
    }

    private fun buildFeatureDiffSummary(previous: List<String>, current: List<String>): String {
        val prev = previous.map { it.trim() }.filter { it.isNotBlank() }
        val now = current.map { it.trim() }.filter { it.isNotBlank() }
        if (prev.isEmpty() && now.isEmpty()) return ""
        if (prev.isEmpty()) return "initial feature snapshot: ${now.joinToString(", ")}"
        val added = now.filter { it !in prev }
        val removed = prev.filter { it !in now }
        val kept = now.filter { it in prev }
        return buildString {
            append("kept=${kept.take(8).joinToString(", ").ifBlank { "none" }}")
            if (added.isNotEmpty()) append(" | added=${added.take(8).joinToString(", ")}")
            if (removed.isNotEmpty()) append(" | removed=${removed.take(8).joinToString(", ")}")
        }.take(400)
    }

    private fun inferPatchFocus(changeRequest: String): String {
        val text = changeRequest.lowercase()
        return when {
            text.contains("ui") || text.contains("样式") || text.contains("布局") || text.contains("美化") -> "ui_surface"
            text.contains("bug") || text.contains("修复") || text.contains("错误") -> "bug_fix"
            text.contains("功能") || text.contains("按钮") || text.contains("交互") || text.contains("逻辑") -> "behavior"
            text.contains("文案") || text.contains("文字") || text.contains("翻译") -> "copywriting"
            else -> "targeted_patch"
        }
    }

    private fun inferChangeType(changeRequest: String): String {
        val text = changeRequest.lowercase()
        return when {
            text.contains("新增") || text.contains("添加") || text.contains("增加") -> "extend"
            text.contains("删除") || text.contains("移除") -> "remove"
            text.contains("修复") || text.contains("bug") || text.contains("错误") -> "fix"
            text.contains("优化") || text.contains("调整") || text.contains("改") -> "refine"
            else -> "modify"
        }
    }

    private fun validateNativePageRuntime(layoutJson: String, actionsJson: String): List<String> {
        val issues = mutableListOf<String>()
        val merged = (layoutJson + "\n" + actionsJson).lowercase()

        if (Regex("""https?://[^"'\\s)]+/v1/v1(/|$)""").containsMatchIn(merged)) {
            issues += "Found duplicated '/v1/v1' in endpoint URL. Normalize the base endpoint before appending API paths."
        }
        if (Regex("""https?://127\.0\.0\.1:52732/api/api/""").containsMatchIn(merged)) {
            issues += "Local API server path is duplicated as '/api/api/'. Use ${LocalApiServer.BASE_URL}/api/...."
        }
        if (Regex("""https?://127\.0\.0\.1:52732(?!/api/)""").containsMatchIn(merged)) {
            issues += "Local API server URL is malformed. Native pages should use ${LocalApiServer.BASE_URL}/api/... paths."
        }
        if (Regex("""https?://(localhost|127\.0\.0\.1)(:\d+)?/v1/""").containsMatchIn(merged)) {
            issues += "Found OpenAI-style local /v1 endpoint in page actions. Use the built-in http action against a real API endpoint, or route through app capabilities instead."
        }
        if ("\"type\":\"http\"" in merged && "\"url\":\"/api/" in merged) {
            issues += "Found relative '/api/...' URL in native page http action. Use absolute ${LocalApiServer.BASE_URL}/api/... URL."
        }
        return issues.distinct()
    }

    private fun inspectNativePageStructure(page: AiPageDef, limit: Int): Map<String, Any> {
        val nodeTypes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val actionNames = page.actions.keySet().toList()
        val visited = countNodes(page.layout, nodeTypes)
        if (visited == 0) warnings += "Layout tree is empty."
        if (actionNames.isEmpty()) warnings += "No actions defined."
        if (page.layout.get("type") == null) warnings += "Root layout node has no 'type'."
        if ("button" in nodeTypes && actionNames.isEmpty()) warnings += "Buttons exist but no page actions are defined."
        return linkedMapOf(
            "artifact_type" to "ai_native_page",
            "action" to "inspect_structure",
            "id" to page.id,
            "title" to page.title,
            "node_count" to visited,
            "component_types" to nodeTypes.distinct().take(limit),
            "action_names" to actionNames.take(limit),
            "state_keys" to page.state.keys.take(limit),
            "structure_warnings" to warnings.distinct(),
            "debug_protocol" to AI_NATIVE_PAGE_DEBUG_PROTOCOL,
            "summary" to "Structure inspection found $visited nodes, ${nodeTypes.distinct().size} component types, and ${actionNames.size} actions.",
        )
    }

    private fun countNodes(node: JsonObject?, nodeTypes: MutableList<String>): Int {
        if (node == null || node.entrySet().isEmpty()) return 0
        node["type"]?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }?.let { nodeTypes += it }
        var count = 1
        val children = runCatching { node.getAsJsonArray("children") }.getOrNull()
        children?.forEach { child ->
            count += runCatching { countNodes(child.asJsonObject, nodeTypes) }.getOrDefault(0)
        }
        val elseChildren = runCatching { node.getAsJsonArray("else_children") }.getOrNull()
        elseChildren?.forEach { child ->
            count += runCatching { countNodes(child.asJsonObject, nodeTypes) }.getOrDefault(0)
        }
        return count
    }

    private fun detectNativePageWarnings(page: AiPageDef): List<String> {
        val warnings = mutableListOf<String>()
        val body = (page.layout.toString() + "\n" + page.actions.toString()).lowercase()
        if ("\"type\":\"input\"" in body && "\"type\":\"button\"" !in body && "\"type\":\"auto_submit\"" !in body) {
            warnings += "Inputs exist without an obvious submit or action trigger."
        }
        if ("navigate_page" in body && page.actions.keySet().isEmpty()) {
            warnings += "Navigation references exist but no actions are defined."
        }
        if (page.state.size > 30) {
            warnings += "State keys are unusually large; consider narrowing the page state."
        }
        return warnings.distinct()
    }

    companion object {
        private const val AI_NATIVE_PAGE_DEBUG_PROTOCOL = "ai_native_page_debug_v1: 1)get or analyze_change before edit 2)update the smallest affected part 3)validate immediately after update 4)if runtime_issues or missing features remain, do one focused repair pass 5)do not broad-rewrite unrelated layout/actions while fixing a narrow bug"

        val GUIDE = """
# AI Native Page Builder — Full Reference

Native pages run as real Compose UI (not WebView). They support full Android capabilities.

## Quick Start

### Create a page
ui_builder(action=create,
  id="my_page",
  title="My Page",
  icon="page",
  state={"count":"0","result":""},
  layout={"type":"column","gap":12,"padding":16,"children":[...]},
  actions={"increment":[{"type":"set_state","key":"count","value":"${'$'}{state.count + 1}"}]}
)

### Update a page
ui_builder(action=update, id="my_page", layout=..., actions=...)

### Repair loop after every edit
1. `ui_builder(action=analyze_change, ...)`
2. `ui_builder(action=update, ...)`
3. `ui_builder(action=inspect_runtime, id="...")`
4. `ui_builder(action=validate, id="...")`
5. If `runtime_issues`, `structure_warnings`, `missing_required_features`, or `missing_snapshot_features` are still present, issue one focused repair update instead of rewriting the whole page

### Open a page
ui_builder(action=open, id="my_page")

### Pin as desktop shortcut
ui_builder(action=pin_shortcut, id="my_page")

---

## Layout Components

All string values support ${'$'}{expr} template interpolation.

### Layout containers
- column: gap (dp), padding (dp), children:[...]
- row: gap (dp), children:[...] — equal-width columns
- card: title, gap, children:[...] — elevated box

### Content
- text: content, size (sp), bold, italic, color (accent/subtext/red/green/blue/#hex), align (start/center/end)
- markdown: content — renders markdown text
- badge: text, color
- divider
- spacer: size (dp)
- progress: value (0.0–1.0), label
- image: src (data:image/... base64), height (dp)

### Input
- input: key, placeholder, label, multiline (bool), default
- select: key, options:["A","B"], label
- switch: key, label — boolean toggle (${'$'}{state.key} = "true"/"false")
- slider: key, min, max, label — float range (${'$'}{state.key} = float string)

### Actions
- button: label, action (actionName), style (filled/outline/text)
- button_group: buttons:[{label,action,style?},...], style

### Compound
- metric_grid: items:[{label,value,color?},...], cols (default 2)
- info_rows: items:[{label,value,color?},...]
- table: headers:[...], rows:[[...]]
- json_view: content (JSON string), max_chars — pretty JSON inspector for app_context/skill_call outputs
- chart_bar: data:[floats], labels:[strings], title
- chart_line: data:[floats], labels:[strings], title

### Logic
- conditional: condition="${'$'}{expr}", children:[...], else_children:[...]

---

## Action Steps

Each named action is a list of steps executed sequentially:

### State
{"type":"set_state","key":"x","value":"${'$'}{input.city}"}

### Network
{"type":"http","url":"https://api.example.com/data","method":"GET","result_key":"response"}
Body stored in state[result_key]; use ${'$'}{state.response} in layout.

### Shell
{"type":"shell","cmd":"date +%Y-%m-%d","result_key":"today"}
stdout → state[result_key]

### MobileClaw App Context
Read structured app data that MobileClaw already has. Result is JSON stored in state[result_key].
Domains: all, summary, memory, chat, groups, settings, skills, roles, pages, vpn.
Sensitive config values and API keys are redacted.
{"type":"app_context","domain":"summary","limit":20,"result_key":"ctx"}
{"type":"app_context","domain":"chat","limit":10,"result_key":"chat_json"}

### Existing Skill Calls
Call any registered MobileClaw skill by id. Params are the same as the skill parameters.
Result output is stored in state[result_key]; full result fields are available via ${'$'}{result.output}, ${'$'}{result.ok}, ${'$'}{result.data}.
Use app_context(domain="skills") first if you need the current skill inventory.
{"type":"skill_call","skill":"memory","params":{"action":"list"},"result_key":"memory_out"}
{"type":"skill_call","skill":"web_search","params":{"query":"${'$'}{input.query}"},"result_key":"search_out"}

### Android System
{"type":"notify","title":"完成","body":"${'$'}{state.result}"}
{"type":"vibrate","ms":100}
{"type":"toast","text":"${'$'}{state.msg}"}
{"type":"launch_app","package":"com.tencent.wechat"}
{"type":"open_url","url":"https://example.com"}
{"type":"share","text":"${'$'}{state.result}","title":"分享"}
{"type":"clipboard_set","text":"${'$'}{state.result}"}
{"type":"clipboard_get","result_key":"pasted"}
{"type":"call_phone","number":"10086"}
{"type":"send_sms","number":"10086","body":"查询余额"}
{"type":"set_alarm","hour":9,"minute":0,"message":"早会"}
{"type":"open_map","query":"北京故宫"}
{"type":"send_intent","action":"android.intent.action.VIEW","data":"geo:39.9,116.4"}
{"type":"navigate_page","id":"other_page_id"}

---

## Expression Syntax

${'$'}{state.key}           — current page state
${'$'}{input.key}           — input field value
${'$'}{result.body}         — last HTTP response body
${'$'}{result.stdout}       — last shell stdout
${'$'}{state.count + 1}     — arithmetic
${'$'}{state.count > 5}     — comparison → "true"/"false"
${'$'}{state.x == "done"}   — equality

---

## Full Example — Weather Dashboard

ui_builder(action=create, id="weather", title="天气查询", icon="weather",
  state={"city":"北京","weather":"点击查询"},
  layout={"type":"column","gap":12,"padding":16,"children":[
    {"type":"text","content":"天气查询","bold":true,"size":18},
    {"type":"input","key":"city","placeholder":"输入城市名","label":"城市"},
    {"type":"button","label":"查询天气","action":"fetch","style":"filled"},
    {"type":"card","title":"结果","children":[
      {"type":"text","content":"${'$'}{state.weather}","size":16}
    ]}
  ]},
  actions={"fetch":[
    {"type":"set_state","key":"weather","value":"查询中..."},
    {"type":"http","url":"https://wttr.in/${'$'}{input.city}?format=3&lang=zh","method":"GET","result_key":"weather"},
    {"type":"toast","text":"查询完成"}
  ]}
)

---

## Notes
- Pages survive app restarts; state resets to initial values on each open.
- Actions run on background thread; UI updates via Compose state flow.
- AI pages can now build dashboards or tools from app memory, chats, group chats, settings, roles, skills, AI pages, and VPN summaries.
- Prefer app_context for reading MobileClaw data and skill_call for doing work through existing capabilities.
- Call get_guide to see this reference; call list to see existing pages.
""".trimIndent()
    }
}
