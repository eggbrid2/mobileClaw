package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
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
            "Pages support real UI components, Android APIs, app context data, HTTP, shell, notifications, sensors. Actions: create | update | list | get | delete | open | pin_shortcut | get_guide",
        descriptionZh = "优先用于创建 MobileClaw 内的用户可见原生页面：AI 页面、仪表盘、表单、设置面板、管理页、数据查看器、控制页和轻量工具。除非用户明确需要程序/小游戏/自定义 HTML 运行时，否则优先用此工具，不要在聊天里返回页面 JSON 或代码。",
        parameters = listOf(
            SkillParam("action", "string", required = true,
                description = "create | update | list | get | delete | open | pin_shortcut | get_guide"),
            SkillParam("id", "string", required = false,
                description = "Page ID in snake_case (required for all except list/get_guide)"),
            SkillParam("title", "string", required = false, description = "Display title shown in the top bar"),
            SkillParam("icon", "string", required = false, description = "Emoji icon for the page"),
            SkillParam("description", "string", required = false, description = "Short description of the page"),
            SkillParam("state", "object", required = false,
                description = "Initial state object: {\"key\": \"initial_value\"}. String JSON is also accepted."),
            SkillParam("layout", "object", required = false,
                description = "Component tree object (see get_guide for reference). String JSON is also accepted."),
            SkillParam("actions", "object", required = false,
                description = "Actions object: {\"actionName\": [{\"type\":\"...\",\"key\":\"...\"},...]}. String JSON is also accepted."),
        ),
        injectionLevel = 1,
        type = SkillType.NATIVE,
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

            "create", "update" -> {
                val id = (params["id"] as? String)?.trim()?.replace(" ", "_")
                    ?: return SkillResult(false, "id required")
                val existing = store.get(id)
                if (action == "update" && existing == null) {
                    return SkillResult(false, "Page not found: $id. Use action=create to create it first.")
                }

                val title = params["title"] as? String ?: existing?.title ?: id
                val icon = params["icon"] as? String ?: existing?.icon ?: "📄"
                val description = params["description"] as? String ?: existing?.description ?: ""

                val state: Map<String, String> = params["state"]?.let { raw ->
                    parseJsonObject(raw)?.entrySet()?.associate { (k, v) -> k to jsonValueToStateString(v) }
                } ?: existing?.state ?: emptyMap()

                val layout: JsonObject = params["layout"]?.let { raw -> parseJsonObject(raw) }
                    ?: existing?.layout
                    ?: JsonObject()

                val actions: JsonObject = params["actions"]?.let { raw -> parseJsonObject(raw) }
                    ?: existing?.actions
                    ?: JsonObject()

                val def = AiPageDef(
                    id = id,
                    title = title,
                    icon = icon,
                    version = (existing?.version ?: 0) + 1,
                    description = description,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    state = state,
                    layout = layout,
                    actions = actions,
                )
                store.save(def)
                val verb = if (action == "create") "created" else "updated"
                SkillResult(true, "Page '$id' $verb (v${def.version}). Open with ui_builder(action=open, id=$id)")
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

            else -> SkillResult(false, "Unknown action: $action. Use: create | update | list | get | delete | open | pin_shortcut | get_guide")
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

    companion object {
        val GUIDE = """
# AI Native Page Builder — Full Reference

Native pages run as real Compose UI (not WebView). They support full Android capabilities.

## Quick Start

### Create a page
ui_builder(action=create,
  id="my_page",
  title="My Page",
  icon="🚀",
  state={"count":"0","result":""},
  layout={"type":"column","gap":12,"padding":16,"children":[...]},
  actions={"increment":[{"type":"set_state","key":"count","value":"${'$'}{state.count + 1}"}]}
)

### Update a page
ui_builder(action=update, id="my_page", layout=..., actions=...)

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

ui_builder(action=create, id="weather", title="天气查询", icon="🌤️",
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
