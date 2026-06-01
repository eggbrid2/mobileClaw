package com.mobileclaw.skill.builtin

import com.google.gson.Gson
import com.mobileclaw.app.MiniApp
import com.mobileclaw.app.MiniAppPreflightValidator
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.artifact.ArtifactHistoryEntry
import com.mobileclaw.artifact.ArtifactSpec
import com.mobileclaw.server.LocalApiServer
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import com.mobileclaw.skill.SkillToolCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

/**
 * Manages HTML mini-apps that run natively in MobileClaw.
 * Each app is a self-contained HTML+JS file with a native JavaScript bridge.
 *
 * Bridge APIs available to mini-app HTML (window.Android):
 *   Android.getConfig(key)                         — read user config
 *   Android.setConfig(key, value)                  — write user config
 *   Android.getMemory(key)                         — read semantic memory
 *   Android.setMemory(key, value)                  — write semantic memory
 *   Android.readFile(name)                         — read app data file
 *   Android.writeFile(name, data)                  — write app data file
 *   Android.listFiles()                            — list app data files (JSON array)
 *   Android.deleteFile(name)                       — delete app data file
 *   Android.httpFetch(url, method, headersJson, body) — HTTP request (bypasses CORS)
 *   Android.sqlite(sql, paramsJson)                — run SQL on per-app SQLite DB (JSON result)
 *   Android.vibrate(ms)                            — vibrate device
 *   Android.showToast(message)                     — show Toast notification
 *   Android.getDeviceInfo()                        — device info JSON
 *   Android.clipboardGet()                         — read clipboard text
 *   Android.clipboardSet(text)                     — write clipboard text
 *   Android.callPython(inputJson)                  — call Python handle(input_json) → JSON string
 *   Android.setPythonBackend(code)                 — update Python backend code at runtime
 *   Android.getPythonBackend()                     — read current Python backend code
 *   Android.askAgent(message)                      — call the AI agent
 *   Android.close()                                — close the app
 *
 * Python backend contract (handle function must exist):
 *   import json, requests
 *   def handle(input_json):
 *       data = json.loads(input_json)
 *       return json.dumps({"result": ...})
 */
class AppManagerSkill(
    private val store: MiniAppStore,
    private val preflightValidator: MiniAppPreflightValidator,
    val openRequests: MutableSharedFlow<String>,
) : Skill {
    private val gson = Gson()

    override val meta = SkillMeta(
        id = "app_manager",
        name = "Mini App Program Builder",
        nameZh = "MiniAPP 程序生成",
        description = "Creates and manages persistent HTML+JS mini-app programs that run inside MobileClaw. " +
            "Use for explicit app/mini-app/program/game requests, custom HTML/CSS/JavaScript, canvas, complex browser rendering, SQLite, or Python backend. " +
            "For ordinary pages, dashboards, forms, settings panels, data viewers, and management screens, use ui_builder instead. " +
            "IMPORTANT: Always call action=get_guide before creating or updating an app to get the full API reference and starter template. " +
            "All Claw async methods (fetch/sql/python/shell) MUST be used with await — synchronous calls will freeze the UI. " +
            "Use Claw.log.info/warn/error/debug and Claw.log.read() for runtime diagnostics and debugging. " +
            "Actions: get_guide | create | update | analyze_change | validate | inspect_logs | list | delete | open | set_icon",
        descriptionZh = "创建和管理在 MobileClaw 中运行的持久化 HTML+JS MiniAPP 程序。仅在用户明确要求应用/小程序/程序/游戏，或需要自定义 HTML/CSS/JavaScript、Canvas、复杂浏览器渲染、SQLite、Python 后端时使用。普通页面、仪表盘、表单、管理页优先使用 ui_builder。重要：创建或更新应用前请先调用 action=get_guide 获取完整 API 参考和起始模板。",
        parameters = listOf(
            SkillParam("action", "string", "Action: 'get_guide' | 'create' | 'update' | 'analyze_change' | 'validate' | 'inspect_logs' | 'list' | 'delete' | 'open' | 'set_icon'"),
            SkillParam("id", "string", "App ID (snake_case). Required for update/delete/open. Auto-generated for create.", required = false),
            SkillParam("title", "string", "App title shown in launcher", required = false),
            SkillParam("description", "string", "Short description of what the app does", required = false),
            SkillParam("icon", "string", "Emoji icon for the app", required = false),
            SkillParam("goal", "string", "Original user goal for this app", required = false),
            SkillParam("required_features", "array", "Required features that must be preserved across updates", required = false),
            SkillParam("constraints", "array", "Constraints such as style, behavior, or platform requirements", required = false),
            SkillParam("accepted_corrections", "array", "User-approved corrections that should persist", required = false),
            SkillParam("known_bugs", "array", "Known bugs still pending", required = false),
            SkillParam("non_goals", "array", "Out-of-scope items for this app", required = false),
            SkillParam("change_request", "string", "Latest user correction or requested change", required = false),
            SkillParam("html", "string", "Complete HTML+CSS+JS. Use Claw.* APIs for all native features. For HTTP use Claw.fetch(url) — do NOT use fetch()/XMLHttpRequest (CORS blocked). For Python call Claw.python({action:'...',...}).", required = false),
            SkillParam("python", "string", "Optional Python backend. Must define handle(input_json_str)->str returning JSON. Called via Claw.python(obj) from HTML.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        categories = listOf(SkillToolCategory.ARTIFACT, SkillToolCategory.SKILL),
        tags = listOf("应用"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String
            ?: return SkillResult(false, "action is required: create | update | list | delete | open")

        return when (action) {
            "get_guide" -> SkillResult(true, APP_CREATION_GUIDE)

            "list" -> {
                val apps = store.all()
                if (apps.isEmpty()) return SkillResult(true, "No mini-apps created yet.")
                val list = apps.joinToString("\n") { a -> "• ${a.icon} ${a.id}: ${a.title} — ${a.description}" }
                SkillResult(true, "Mini-apps (${apps.size}):\n$list")
            }

            "analyze_change" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required for analyze_change")
                val app = store.get(id) ?: return SkillResult(false, "App '$id' not found.")
                val changeRequest = params["change_request"] as? String ?: return SkillResult(false, "change_request required")
                val recentLogs = store.readLogs(id, limit = 40)
                val logReport = classifyMiniAppLogs(recentLogs)
                val output = linkedMapOf(
                    "artifact_type" to "miniapp",
                    "id" to app.id,
                    "title" to app.title,
                    "goal" to app.spec.goal.ifBlank { app.description.ifBlank { app.title } },
                    "current_features" to app.spec.currentFeatures.ifEmpty { summarizeAppFeatures(app, store.readHtml(id).orEmpty(), store.readPython(id).orEmpty()) },
                    "preserve_features" to app.spec.requiredFeatures,
                    "constraints" to app.spec.constraints,
                    "accepted_corrections" to app.spec.acceptedCorrections,
                    "known_bugs" to app.spec.knownBugs,
                    "non_goals" to app.spec.nonGoals,
                    "last_diff_summary" to app.spec.lastDiffSummary,
                    "patch_focus" to inferPatchFocus(changeRequest),
                    "change_type" to inferChangeType(changeRequest),
                    "patch_brief" to "update only the smallest affected part, preserve all unrelated behavior, and keep the app runnable after the edit",
                    "change_request" to changeRequest,
                    "recent_logs" to recentLogs,
                    "error_logs" to logReport.errors,
                    "warning_logs" to logReport.warnings,
                    "needs_runtime_repair" to logReport.needsRepair,
                    "latest_log_summary" to recentLogs.takeLast(8).joinToString(" | ").take(800),
                    "recommended_mode" to "patch",
                    "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                )
                SkillResult(true, gson.toJson(output))
            }

            "validate" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required for validate")
                val app = store.get(id) ?: return SkillResult(false, "App '$id' not found.")
                val html = store.readHtml(id).orEmpty()
                val py = store.readPython(id).orEmpty()
                val preflight = preflightValidator.validate(id, html, py, MiniAppPreflightValidator.Mode.STRICT)
                val body = (html + "\n" + py).lowercase()
                val required = app.spec.requiredFeatures
                val current = app.spec.currentFeatures.ifEmpty { summarizeAppFeatures(app, html, py) }
                val recentLogs = if (preflight.recentLogs.isNotEmpty()) preflight.recentLogs else store.readLogs(id, limit = 80)
                val missingRequired = required.filter { feature ->
                    val token = feature.trim().lowercase()
                    token.isNotBlank() && token !in body
                }
                val missingCurrent = current.filter { feature ->
                    val token = feature.trim().lowercase()
                    token.isNotBlank() && token !in body
                }
                val runtimeIssues = validateMiniAppRuntime(html, py)
                val logReport = classifyMiniAppLogs(recentLogs)
                val ok = missingRequired.isEmpty() &&
                    missingCurrent.isEmpty() &&
                    runtimeIssues.isEmpty() &&
                    preflight.issues.isEmpty()
                val output = linkedMapOf(
                    "artifact_type" to "miniapp",
                    "id" to app.id,
                    "ok" to ok,
                    "required_features" to required,
                    "current_features" to current,
                    "missing_required_features" to missingRequired,
                    "missing_snapshot_features" to missingCurrent,
                    "preflight_issues" to preflight.issues,
                    "preflight_warnings" to preflight.warnings,
                    "runtime_issues" to runtimeIssues,
                    "recent_logs" to recentLogs,
                    "error_logs" to logReport.errors,
                    "warning_logs" to logReport.warnings,
                    "needs_runtime_repair" to logReport.needsRepair,
                    "latest_log_summary" to recentLogs.takeLast(10).joinToString(" | ").take(1000),
                    "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                    "summary" to if (ok) {
                        "Required features preserved and runtime validation passed."
                    } else {
                        buildString {
                            append("Artifact validation failed.")
                            if (missingRequired.isNotEmpty()) append(" Missing required features: ${missingRequired.joinToString(", ")}.")
                            if (missingCurrent.isNotEmpty()) append(" Missing snapshot features: ${missingCurrent.joinToString(", ")}.")
                            if (preflight.issues.isNotEmpty()) append(" Preflight issues: ${preflight.issues.joinToString(" | ")}.")
                            if (runtimeIssues.isNotEmpty()) append(" Runtime issues: ${runtimeIssues.joinToString(" | ")}.")
                            if (recentLogs.isNotEmpty()) append(" Recent logs are attached for debugging.")
                        }
                    }
                )
                SkillResult(ok, gson.toJson(output))
            }

            "inspect_logs" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required for inspect_logs")
                val app = store.get(id) ?: return SkillResult(false, "App '$id' not found.")
                val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 200) ?: 80
                val recentLogs = store.readLogs(id, limit = limit)
                val logReport = classifyMiniAppLogs(recentLogs)
                val output = linkedMapOf(
                    "artifact_type" to "miniapp",
                    "action" to "inspect_logs",
                    "id" to app.id,
                    "title" to app.title,
                    "recent_logs" to recentLogs,
                    "error_logs" to logReport.errors,
                    "warning_logs" to logReport.warnings,
                    "needs_runtime_repair" to logReport.needsRepair,
                    "latest_log_summary" to recentLogs.takeLast(10).joinToString(" | ").take(1000),
                    "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                    "summary" to when {
                        recentLogs.isEmpty() -> "No runtime logs recorded for '$id'."
                        logReport.needsRepair -> "Loaded ${recentLogs.size} recent runtime logs for '$id'. Error logs indicate the app still needs runtime repair."
                        else -> "Loaded ${recentLogs.size} recent runtime logs for '$id'."
                    },
                )
                SkillResult(true, gson.toJson(output))
            }

            "create" -> {
                val html = params["html"] as? String
                    ?: return SkillResult(false, "html is required for create")
                val title = params["title"] as? String ?: "Untitled App"
                val description = params["description"] as? String ?: ""
                val icon = params["icon"] as? String ?: "apps"
                val python = params["python"] as? String
                val changeRequest = params["change_request"] as? String ?: ""
                val id = (params["id"] as? String)?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
                    ?: "app_${UUID.randomUUID().toString().take(8)}"
                val app = MiniApp(
                    id = id,
                    title = title,
                    description = description,
                    icon = icon,
                    htmlPath = "",
                    spec = mergeSpec(
                        existing = null,
                        params = params,
                        title = title,
                        description = description,
                        changeRequest = changeRequest,
                        inferredCurrentFeatures = summarizeAppFeatures(title, description, html, python.orEmpty()),
                    ),
                    history = buildHistory(null, "create", changeRequest.ifBlank { description }, description.ifBlank { title }),
                )
                val preflight = preflightValidator.validate(id, html, python, MiniAppPreflightValidator.Mode.STARTUP)
                if (!preflight.ok) {
                    store.save(app, html)
                    if (!python.isNullOrBlank()) store.savePython(id, python)
                    val saved = store.get(id) ?: app
                    val logReport = classifyMiniAppLogs(preflight.recentLogs)
                    val output = linkedMapOf(
                        "artifact_type" to "miniapp",
                        "action" to "create",
                        "id" to saved.id,
                        "title" to saved.title,
                        "ok" to false,
                        "saved_as_draft" to true,
                        "open_blocked" to true,
                        "preflight_issues" to preflight.issues,
                        "preflight_warnings" to preflight.warnings,
                        "recent_logs" to preflight.recentLogs,
                        "error_logs" to logReport.errors,
                        "warning_logs" to logReport.warnings,
                        "needs_runtime_repair" to logReport.needsRepair,
                        "latest_log_summary" to preflight.recentLogs.takeLast(8).joinToString(" | ").take(800),
                        "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                        "summary" to "MiniAPP draft '$id' was saved, but preflight failed so it was not opened. Repair the reported issues, then validate and open it.",
                    )
                    return SkillResult(true, gson.toJson(output))
                }
                store.save(app, html)
                if (!python.isNullOrBlank()) store.savePython(id, python)
                openRequests.emit(id)
                val saved = store.get(id) ?: app
                val output = linkedMapOf(
                    "artifact_type" to "miniapp",
                    "action" to "create",
                    "id" to saved.id,
                    "title" to saved.title,
                    "goal" to saved.spec.goal,
                    "current_features" to saved.spec.currentFeatures,
                    "required_features" to saved.spec.requiredFeatures,
                    "last_diff_summary" to saved.spec.lastDiffSummary,
                    "change_request" to changeRequest,
                    "preflight_warnings" to preflight.warnings,
                    "preflight_recent_logs" to preflight.recentLogs,
                    "preflight_latest_log_summary" to preflight.recentLogs.takeLast(8).joinToString(" | ").take(800),
                    "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                    "opened" to true,
                    "ui_open_request_emitted" to true,
                    "suggested_next_action" to "If the user is still in chat, use the bottom-right validation preview first. If that preview shows issues, inspect_logs -> focused repair -> validate, instead of treating the preview as the final app surface.",
                    "open_hint" to "app_manager(action=open, id=$id)",
                    "summary" to "Created MiniAPP '$id'${if (python != null) " with Python backend" else ""}."
                )
                SkillResult(true, gson.toJson(output))
            }

            "update" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for update")
                val app = store.get(id)
                    ?: return SkillResult(false, "App '$id' not found. Use action=list to see available apps.")
                val html = params["html"] as? String
                val newTitle = params["title"] as? String
                val newDescription = params["description"] as? String
                val newIcon = params["icon"] as? String
                val python = params["python"] as? String
                val changeRequest = params["change_request"] as? String ?: ""
                val effectiveHtml = html ?: store.readHtml(id).orEmpty()
                val effectivePython = python ?: store.readPython(id).orEmpty()
                val preflight = preflightValidator.validate(id, effectiveHtml, effectivePython, MiniAppPreflightValidator.Mode.STARTUP)
                if (!preflight.ok) {
                    val draft = app.copy(
                        title = newTitle ?: app.title,
                        description = newDescription ?: app.description,
                        icon = newIcon ?: app.icon,
                        spec = mergeSpec(
                            existing = app.spec,
                            params = params,
                            title = newTitle ?: app.title,
                            description = newDescription ?: app.description,
                            changeRequest = changeRequest,
                            inferredCurrentFeatures = summarizeAppFeatures(
                                newTitle ?: app.title,
                                newDescription ?: app.description,
                                effectiveHtml,
                                effectivePython,
                            ),
                        ),
                        history = buildHistory(app.history, "update", changeRequest.ifBlank { newDescription ?: app.description }, (newDescription ?: app.description).ifBlank { newTitle ?: app.title }),
                    )
                    store.save(draft, effectiveHtml)
                    if (!effectivePython.isNullOrBlank()) store.savePython(id, effectivePython)
                    val saved = store.get(id) ?: draft
                    val logReport = classifyMiniAppLogs(preflight.recentLogs)
                    val output = linkedMapOf(
                        "artifact_type" to "miniapp",
                        "action" to "update",
                        "id" to saved.id,
                        "title" to saved.title,
                        "ok" to false,
                        "saved_as_draft" to true,
                        "open_blocked" to true,
                        "preflight_issues" to preflight.issues,
                        "preflight_warnings" to preflight.warnings,
                        "recent_logs" to preflight.recentLogs,
                        "error_logs" to logReport.errors,
                        "warning_logs" to logReport.warnings,
                        "needs_runtime_repair" to logReport.needsRepair,
                        "latest_log_summary" to preflight.recentLogs.takeLast(8).joinToString(" | ").take(800),
                        "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                        "summary" to "MiniAPP draft '$id' was updated, but preflight failed so it was not opened. Repair the reported issues, then validate and open it.",
                    )
                    return SkillResult(true, gson.toJson(output))
                }
                val updated = app.copy(
                    title = newTitle ?: app.title,
                    description = newDescription ?: app.description,
                    icon = newIcon ?: app.icon,
                    spec = mergeSpec(
                        existing = app.spec,
                        params = params,
                        title = newTitle ?: app.title,
                        description = newDescription ?: app.description,
                        changeRequest = changeRequest,
                        inferredCurrentFeatures = summarizeAppFeatures(
                            newTitle ?: app.title,
                            newDescription ?: app.description,
                            effectiveHtml,
                            effectivePython,
                        ),
                    ),
                    history = buildHistory(app.history, "update", changeRequest.ifBlank { newDescription ?: app.description }, (newDescription ?: app.description).ifBlank { newTitle ?: app.title }),
                )
                if (html != null) {
                    store.save(updated, html)
                } else if (newTitle != null || newDescription != null || newIcon != null) {
                    val existingHtml = store.readHtml(id) ?: "<html></html>"
                    store.save(updated, existingHtml)
                }
                if (!python.isNullOrBlank()) store.savePython(id, python)
                val saved = store.get(id) ?: updated
                val output = linkedMapOf(
                    "artifact_type" to "miniapp",
                    "action" to "update",
                    "id" to saved.id,
                    "title" to saved.title,
                    "goal" to saved.spec.goal,
                    "current_features" to saved.spec.currentFeatures,
                    "required_features" to saved.spec.requiredFeatures,
                    "last_diff_summary" to saved.spec.lastDiffSummary,
                    "change_request" to changeRequest,
                    "preflight_warnings" to preflight.warnings,
                    "preflight_recent_logs" to preflight.recentLogs,
                    "preflight_latest_log_summary" to preflight.recentLogs.takeLast(8).joinToString(" | ").take(800),
                    "debug_protocol" to MINI_APP_DEBUG_PROTOCOL,
                    "opened" to true,
                    "ui_open_request_emitted" to true,
                    "suggested_next_action" to "If the user is still in chat, use the bottom-right validation preview first. If that preview shows issues, inspect_logs -> focused repair -> validate, instead of treating the preview as the final app surface.",
                    "open_hint" to "app_manager(action=open, id=$id)",
                    "summary" to "Updated MiniAPP '$id'."
                )
                SkillResult(true, gson.toJson(output))
            }

            "delete" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for delete")
                if (store.get(id) == null) return SkillResult(false, "App '$id' not found.")
                store.delete(id)
                SkillResult(true, "App '$id' deleted.")
            }

            "open" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for open")
                val app = store.get(id) ?: return SkillResult(false, "App '$id' not found. Use action=list to see available apps.")
                openRequests.emit(id)
                val output = linkedMapOf(
                    "artifact_type" to "miniapp",
                    "action" to "open",
                    "id" to app.id,
                    "title" to app.title,
                    "opened" to true,
                    "ui_open_request_emitted" to true,
                    "suggested_next_action" to "If the user is still in chat, use the bottom-right validation preview first. If that preview shows issues, inspect_logs -> focused repair -> validate, instead of treating the preview as the final app surface.",
                    "summary" to "Opening MiniAPP '${app.id}'.",
                )
                SkillResult(true, gson.toJson(output))
            }

            "set_icon" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for set_icon")
                val iconPath = params["icon"] as? String
                    ?: return SkillResult(false, "icon (file path) is required for set_icon. Use generate_icon skill first.")
                if (!store.updateIcon(id, iconPath)) {
                    return SkillResult(false, "App '$id' not found.")
                }
                SkillResult(true, "Icon updated for app '$id'.")
            }

            else -> SkillResult(false, "Unknown action: $action. Use get_guide | create | update | analyze_change | validate | inspect_logs | list | delete | open | set_icon")
        }
    }
}

private fun parseArtifactStringList(value: Any?): List<String>? = when (value) {
    is List<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
    is String -> runCatching {
        com.google.gson.JsonParser.parseString(value).asJsonArray.mapNotNull { runCatching { it.asString.trim() }.getOrNull() }.filter { it.isNotBlank() }
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
    val requiredFeatures = parseArtifactStringList(params["required_features"]) ?: base.requiredFeatures
    val currentFeatures = inferredCurrentFeatures.ifEmpty { base.currentFeatures }
    val constraints = parseArtifactStringList(params["constraints"]) ?: base.constraints
    val acceptedCorrections = (base.acceptedCorrections + parseArtifactStringList(params["accepted_corrections"]).orEmpty() + listOf(changeRequest))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .takeLast(20)
    val knownBugs = parseArtifactStringList(params["known_bugs"]) ?: base.knownBugs
    val nonGoals = parseArtifactStringList(params["non_goals"]) ?: base.nonGoals
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

private fun summarizeAppFeatures(app: MiniApp, html: String, python: String): List<String> =
    summarizeAppFeatures(app.title, app.description, html, python)

private fun summarizeAppFeatures(
    title: String,
    description: String,
    html: String,
    python: String,
): List<String> {
    val body = (html + "\n" + python).lowercase()
    val inferred = linkedSetOf<String>()
    if (title.isNotBlank()) inferred += "title:${title.trim().take(40)}"
    if (description.isNotBlank()) inferred += "description"
    if (body.contains("<canvas")) inferred += "canvas"
    if (body.contains("claw.fetch") || body.contains("android.httpfetch")) inferred += "http_fetch"
    if (body.contains("claw.sql") || body.contains("android.sqlite")) inferred += "sqlite"
    if (body.contains("claw.python") || python.isNotBlank()) inferred += "python_backend"
    if (body.contains("input")) inferred += "input"
    if (body.contains("button")) inferred += "button"
    if (body.contains("form")) inferred += "form"
    if (body.contains("chart") || body.contains("svg")) inferred += "chart_or_svg"
    if (body.contains("localstorage") || body.contains("claw.files")) inferred += "local_storage"
    if (body.contains("openurl") || body.contains("launchapp") || body.contains("sharetext")) inferred += "native_bridge_action"
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

private fun validateMiniAppRuntime(html: String, python: String): List<String> {
    val issues = mutableListOf<String>()
    val loweredHtml = html.lowercase()
    val loweredPython = python.lowercase()
    val merged = "$loweredHtml\n$loweredPython"

    if (Regex("""https?://[^"'\\s)]+/v1/v1(/|$)""").containsMatchIn(merged)) {
        issues += "Found duplicated '/v1/v1' in endpoint URL. Normalize the base endpoint before appending API paths."
    }
    if (Regex("""https?://(localhost|127\.0\.0\.1)(:\d+)?/v1/""").containsMatchIn(merged)) {
        issues += "Found OpenAI-style API call pointing at localhost/127.0.0.1. MiniAPPs should use Claw.fetch or MobileClaw local APIs, not ad hoc local /v1 endpoints."
    }
    if (Regex("""(?<!claw\.)fetch\s*\(""").containsMatchIn(loweredHtml)) {
        issues += "Detected native fetch(). Use await Claw.fetch(...) instead so requests go through the app bridge."
    }
    if ("xmlhttprequest" in loweredHtml) {
        issues += "Detected XMLHttpRequest. Use await Claw.fetch(...) instead."
    }
    if (Regex("""(?<!await\s)claw\.(fetch|sql|python|shell|pip)\s*\(""").containsMatchIn(html)) {
        issues += "Detected Claw async API call without 'await'. All Claw.fetch/sql/python/shell/pip calls must be awaited."
    }
    if (Regex("""https?://127\.0\.0\.1:52732(?!/api/)""").containsMatchIn(merged)) {
        issues += "Local API server URL is malformed. Use ${LocalApiServer.BASE_URL}/api/... paths."
    }
    if (Regex("""https?://127\.0\.0\.1:52732/api/api/""").containsMatchIn(merged)) {
        issues += "Local API server path is duplicated as '/api/api/'. Use ${LocalApiServer.BASE_URL}/api/...."
    }
    if ("http://127.0.0.1:52730" in merged) {
        issues += "Detected direct privileged TCP endpoint usage. MiniAPPs should not call the privileged server directly."
    }
    return issues.distinct()
}

private data class MiniAppLogReport(
    val errors: List<String>,
    val warnings: List<String>,
) {
    val needsRepair: Boolean
        get() = errors.isNotEmpty()
}

private fun classifyMiniAppLogs(recentLogs: List<String>): MiniAppLogReport {
    if (recentLogs.isEmpty()) return MiniAppLogReport(emptyList(), emptyList())
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    recentLogs.forEach { line ->
        val text = line.trim()
        val lower = text.lowercase()
        when {
            "[error]" in lower ||
                "unhandled promise rejection" in lower ||
                "js error:" in lower ||
                "console error" in lower ||
                "preload_js_error" in lower ||
                "preload_promise_rejection" in lower -> errors += text
            "[warn]" in lower ||
                "console warning" in lower ||
                "warning" in lower -> warnings += text
        }
    }
    return MiniAppLogReport(
        errors = errors.distinct().takeLast(16),
        warnings = warnings.distinct().takeLast(16),
    )
}

private const val MINI_APP_DEBUG_PROTOCOL = "miniapp_debug_v2: 1)get_guide before edit 2)write Claw.log startup/input/network/parse/error logs 3)update the smallest affected part 4)inspect_logs immediately after update 5)validate after logs 6)if error_logs/runtime_issues/preflight_issues exist, repair once using those diagnostics instead of rewriting unrelated parts"

private val APP_CREATION_GUIDE = """
## MobileClaw Mini-App Development Guide

### ⚠️ CRITICAL RULE: ALL async operations MUST use async/await
Claw.fetch, Claw.sql, Claw.python, Claw.shell are Promises — they MUST be awaited.
Calling them without await will cause silent failures and UI freezes.

### Starter Template
```html
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
  <style>
    /* Use --app-height (injected by Claw) instead of 100vh for reliable full-screen layout */
    html,body{margin:0;padding:0;background:#1a1a2e;color:#eee;font-family:system-ui,-apple-system,sans-serif}
    body{padding:16px;min-height:calc(var(--app-height,100vh));box-sizing:border-box}
    h2{color:#a78bfa;margin:0 0 16px}
    input,textarea{background:#2a2a4e;color:#eee;border:1px solid #4c4c8f;padding:8px 12px;border-radius:8px;width:100%;box-sizing:border-box;margin-bottom:10px;font-size:14px}
    button{background:#7c3aed;color:#fff;border:none;padding:10px 20px;border-radius:8px;cursor:pointer;font-size:14px;font-weight:600}
    button:active{background:#6d28d9}
    button:disabled{background:#4c4c8f;cursor:not-allowed}
    .card{background:#1e1e3f;border:1px solid #3a3a6e;border-radius:10px;padding:12px;margin-top:12px}
    #log{font-family:monospace;font-size:12px;white-space:pre-wrap;min-height:40px;color:#a3a3c8}
    .error{color:#f87171}
  </style>
</head>
<body>
  <h2>My App</h2>
  <input id="q" placeholder="Enter input…" />
  <button id="btn" onclick="run()">Run</button>
  <div class="card"><div id="log">Ready.</div></div>
<script>
async function run(){
  var btn=document.getElementById('btn');
  var log=document.getElementById('log');
  var q=document.getElementById('q').value.trim();
  if(!q){log.textContent='Please enter something.';return;}
  btn.disabled=true;
  log.textContent='Working…';
  try{
    // Example: HTTP fetch (always await!)
    var r=await Claw.fetch('https://httpbin.org/get?q='+encodeURIComponent(q));
    log.textContent=JSON.stringify(JSON.parse(r.body),null,2);
  }catch(e){
    log.innerHTML='<span class="error">Error: '+e.message+'</span>';
  }finally{btn.disabled=false;}
}
</script>
</body>
</html>
```

### Claw API Reference

**Async (MUST await):**
| API | Returns | Description |
|-----|---------|-------------|
| await Claw.fetch(url, {method?,headers?,body?}) | {status,ok,body,headers} | HTTP request, bypasses CORS |
| await Claw.sql(query, params?) | {rows,rowCount,error?} | Per-app SQLite database |
| await Claw.python(dataObj) | any | Call Python handle(json) function |
| await Claw.shell(cmd) | {stdout,exitCode,ok} | Shell command execution |

**Sync (no await needed):**
| API | Returns | Description |
|-----|---------|-------------|
| Claw.config.get(key) / .set(key,val) | string | Persistent user config |
| Claw.memory.get(key) / .set(key,val) | string | Semantic memory |
| Claw.files.read(name) / .write(name,data) / .list() / .del(name) | varies | Per-app file storage |
| Claw.log.info/warn/error/debug(tag,msg) | void | Append structured runtime log lines to app.log |
| Claw.log.read(limit?) | string[] | Read recent runtime log lines from app.log |
| Claw.log.clear() | boolean | Clear the current app log |
| Claw.toast(msg) | void | Android Toast notification |
| Claw.vibrate(ms) | void | Device vibration |
| Claw.clipboard.get() / .set(text) | string | Clipboard access |
| Claw.device() | {manufacturer,model,sdk,screenWidth,...} | Device info |
| Claw.ask(msg) | void | Send message to the AI agent |
| Claw.close() | void | Close the app |
| Claw.setTitle(title) | void | Update the native title bar text |
| Claw.setPython(code) / .getPython() | void/string | Set/get Python backend code |
| Claw.launchApp(packageName) | {ok} or {error} | Open a third-party Android app by package name |
| Claw.openUrl(url) | {ok} or {error} | Open a URL in the system browser / associated app |
| Claw.shareText(text, title?) | {ok} or {error} | Open Android share sheet with text |

### Python Backend Contract
The Python backend must define `handle(input_json_str) -> str` returning a JSON string:
```python
import json, requests

def handle(input_json_str):
    data = json.loads(input_json_str)
    action = data.get('action', '')
    if action == 'greet':
        return json.dumps({'result': 'Hello, ' + data.get('name', 'World')})
    return json.dumps({'error': 'Unknown action'})
```
Call from JS: `var r = await Claw.python({action:'greet', name:'Alice'})`

### Layout Rules
- Use `min-height:calc(var(--app-height,100vh))` on body — NOT `100vh` (unreliable in WebView)
- `--app-height` is injected by Claw and equals the actual WebView height in pixels
- For **scrollable inner containers** (chat history, lists, etc.) use: `overflow-y:auto; -webkit-overflow-scrolling:touch; max-height:XXXpx`
- Do NOT set `overflow:hidden` on `html` or `body` — it creates scroll conflicts in Android WebView
- `native fetch()` and `XMLHttpRequest` are **blocked** (CORS) — always use `Claw.fetch()`

### Debugging Rules
- Required repair loop after every meaningful edit:
  1. `update`
  2. `inspect_logs`
  3. `validate`
  4. if `error_logs`, `runtime_issues`, or `preflight_issues` are present, do one focused repair pass using those diagnostics
- Always add runtime logs for important paths: startup, input validation, network request start/end, parsed response, empty-state fallback, and caught exceptions.
- Preferred pattern:
  - `Claw.log.info('startup', 'page mounted')`
  - `Claw.log.info('fetch', 'requesting weather for '+q)`
  - `Claw.log.error('fetch', e.message || String(e))`
- When a feature fails, inspect `Claw.log.read(80)` before rewriting the whole app.
- If preflight or runtime validation reports bridge/API issues, fix those first instead of adding more features.
- Do not rely only on visible UI text for debugging; write to `Claw.log.*` so future update passes can inspect stable diagnostics.

### Design Rules
1. Dark theme: background #1a1a2e, cards #1e1e3f, accent #7c3aed, text #eee
2. All persistent data goes in Claw.files or Claw.sql — localStorage is unreliable in WebView
3. Use Claw.fetch for ALL HTTP — never native fetch() or XMLHttpRequest (CORS blocks them)
4. Wrap async operations in try/catch, always show error state in UI
5. Disable buttons during async operations to prevent double-submission
6. Never use busy-polling loops (while/for). For recurring updates use: `setTimeout(poll, 1000)` pattern
""".trimIndent()
