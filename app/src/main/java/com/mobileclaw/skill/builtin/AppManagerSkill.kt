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
        name = "Mini App Program Builder",
        nameZh = "MiniAPP 程序生成",
        description = "Creates and manages persistent HTML+JS mini-app programs that run inside MobileClaw. " +
            "Use for explicit app/mini-app/program/game requests, custom HTML/CSS/JavaScript, canvas, complex browser rendering, SQLite, or Python backend. " +
            "For ordinary pages, dashboards, forms, settings panels, data viewers, and management screens, use ui_builder instead. " +
            "IMPORTANT: Always call action=get_guide before creating or updating an app to get the full API reference and starter template. " +
            "All Claw async methods (fetch/sql/python/shell) MUST be used with await — synchronous calls will freeze the UI.",
        descriptionZh = "创建和管理在 MobileClaw 中运行的持久化 HTML+JS MiniAPP 程序。仅在用户明确要求应用/小程序/程序/游戏，或需要自定义 HTML/CSS/JavaScript、Canvas、复杂浏览器渲染、SQLite、Python 后端时使用。普通页面、仪表盘、表单、管理页优先使用 ui_builder。重要：创建或更新应用前请先调用 action=get_guide 获取完整 API 参考和起始模板。",
        parameters = listOf(
            SkillParam("action", "string", "Action: 'get_guide' | 'create' | 'update' | 'list' | 'delete' | 'open' | 'set_icon'"),
            SkillParam("id", "string", "App ID (snake_case). Required for update/delete/open. Auto-generated for create.", required = false),
            SkillParam("title", "string", "App title shown in launcher", required = false),
            SkillParam("description", "string", "Short description of what the app does", required = false),
            SkillParam("icon", "string", "Emoji icon for the app", required = false),
            SkillParam("html", "string", "Complete HTML+CSS+JS. Use Claw.* APIs for all native features. For HTTP use Claw.fetch(url) — do NOT use fetch()/XMLHttpRequest (CORS blocked). For Python call Claw.python({action:'...',...}).", required = false),
            SkillParam("python", "string", "Optional Python backend. Must define handle(input_json_str)->str returning JSON. Called via Claw.python(obj) from HTML.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
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

            else -> SkillResult(false, "Unknown action: $action. Use get_guide | create | update | list | delete | open | set_icon")
        }
    }
}

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

### Design Rules
1. Dark theme: background #1a1a2e, cards #1e1e3f, accent #7c3aed, text #eee
2. All persistent data goes in Claw.files or Claw.sql — localStorage is unreliable in WebView
3. Use Claw.fetch for ALL HTTP — never native fetch() or XMLHttpRequest (CORS blocks them)
4. Wrap async operations in try/catch, always show error state in UI
5. Disable buttons during async operations to prevent double-submission
6. Never use busy-polling loops (while/for). For recurring updates use: `setTimeout(poll, 1000)` pattern
""".trimIndent()
