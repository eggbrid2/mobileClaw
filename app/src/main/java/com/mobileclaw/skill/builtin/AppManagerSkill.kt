package com.mobileclaw.skill.builtin

import com.mobileclaw.app.MiniApp
import com.mobileclaw.app.MiniAppStore
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
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
    val openRequests: MutableSharedFlow<String>,
) : Skill {

    override val meta = SkillMeta(
        id = "app_manager",
        name = "Mini App Manager",
        description = "Creates and manages HTML+JS mini-apps that run inside MobileClaw. " +
            "A global 'Claw' JS object is auto-injected into every app — use it instead of window.Android directly. " +
            "Key APIs: Claw.fetch(url,{method,headers,body}) — HTTP bypassing CORS; " +
            "Claw.sql(query,[params]) — per-app SQLite; " +
            "Claw.python(dataObj) — call Python handle(); " +
            "Claw.config.get/set(key,val); Claw.memory.get/set(key,val); " +
            "Claw.files.read/write/list/del; Claw.toast(msg); Claw.ask(msg); Claw.close(). " +
            "Actions: create, update, list, delete, open.",
        parameters = listOf(
            SkillParam("action", "string", "Action: 'create' | 'update' | 'list' | 'delete' | 'open'"),
            SkillParam("id", "string", "App ID (snake_case). Required for update/delete/open. Auto-generated for create.", required = false),
            SkillParam("title", "string", "App title shown in launcher", required = false),
            SkillParam("description", "string", "Short description of what the app does", required = false),
            SkillParam("icon", "string", "Emoji icon for the app", required = false),
            SkillParam("html", "string", "Complete HTML+CSS+JS. Use Claw.* APIs for all native features. For HTTP use Claw.fetch(url) — do NOT use fetch()/XMLHttpRequest (CORS blocked). For Python call Claw.python({action:'...',...}).", required = false),
            SkillParam("python", "string", "Optional Python backend. Must define handle(input_json_str)->str returning JSON. Called via Claw.python(obj) from HTML.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String
            ?: return SkillResult(false, "action is required: create | update | list | delete | open")

        return when (action) {
            "list" -> {
                val apps = store.all()
                if (apps.isEmpty()) return SkillResult(true, "No mini-apps created yet.")
                val list = apps.joinToString("\n") { a -> "• ${a.icon} ${a.id}: ${a.title} — ${a.description}" }
                SkillResult(true, "Mini-apps (${apps.size}):\n$list")
            }

            "create" -> {
                val html = params["html"] as? String
                    ?: return SkillResult(false, "html is required for create")
                val title = params["title"] as? String ?: "Untitled App"
                val description = params["description"] as? String ?: ""
                val icon = params["icon"] as? String ?: "📱"
                val python = params["python"] as? String
                val id = (params["id"] as? String)?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
                    ?: "app_${UUID.randomUUID().toString().take(8)}"
                val app = MiniApp(id = id, title = title, description = description, icon = icon, htmlPath = "")
                store.save(app, store.injectBridge(html))
                if (!python.isNullOrBlank()) store.savePython(id, python)
                openRequests.emit(id)
                val pythonNote = if (python != null) " (with Python backend)" else ""
                SkillResult(true, "App '${app.icon} ${app.title}' created with id='$id'$pythonNote. Opening now.")
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
                val updated = app.copy(
                    title = newTitle ?: app.title,
                    description = newDescription ?: app.description,
                    icon = newIcon ?: app.icon,
                )
                if (html != null) {
                    store.save(updated, store.injectBridge(html))
                } else if (newTitle != null || newDescription != null || newIcon != null) {
                    val existingHtml = store.readHtml(id) ?: "<html></html>"
                    store.save(updated, existingHtml)
                }
                if (!python.isNullOrBlank()) store.savePython(id, python)
                val what = buildList {
                    if (html != null) add("HTML")
                    if (python != null) add("Python")
                    if (html == null && python == null) add("metadata")
                }.joinToString(" + ")
                SkillResult(true, "App '$id' $what updated. Use action=open to launch it.")
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
                if (store.get(id) == null) return SkillResult(false, "App '$id' not found. Use action=list to see available apps.")
                openRequests.emit(id)
                SkillResult(true, "Opening app '$id'.")
            }

            else -> SkillResult(false, "Unknown action: $action. Use create | update | list | delete | open")
        }
    }

}
