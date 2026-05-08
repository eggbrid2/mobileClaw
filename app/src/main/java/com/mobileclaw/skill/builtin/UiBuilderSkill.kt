package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
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
        name = "UI Builder",
        description = "Create and manage native Android Compose pages with full system capabilities. " +
            "Pages support real UI components, Android APIs, HTTP, shell, notifications, sensors. " +
            "Actions: create | update | list | get | delete | open | pin_shortcut | get_guide",
        parameters = listOf(
            SkillParam("action", "string", required = true,
                description = "create | update | list | get | delete | open | pin_shortcut | get_guide"),
            SkillParam("id", "string", required = false,
                description = "Page ID in snake_case (required for all except list/get_guide)"),
            SkillParam("title", "string", required = false, description = "Display title shown in the top bar"),
            SkillParam("icon", "string", required = false, description = "Emoji icon for the page"),
            SkillParam("description", "string", required = false, description = "Short description of the page"),
            SkillParam("state", "string", required = false,
                description = "JSON object of initial state values: {\"key\": \"initial_value\"}"),
            SkillParam("layout", "string", required = false,
                description = "Component tree JSON (see get_guide for reference). Must be a valid JSON object."),
            SkillParam("actions", "string", required = false,
                description = "Actions JSON: {\"actionName\": [{\"type\":\"...\",\"key\":\"...\"},...]}"),
        ),
        injectionLevel = 1,
        type = SkillType.NATIVE,
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

                @Suppress("UNCHECKED_CAST")
                val state: Map<String, String> = params["state"]?.let { raw ->
                    runCatching {
                        val parsed = JsonParser.parseString(raw.toString()).asJsonObject
                        parsed.entrySet().associate { (k, v) -> k to v.asString }
                    }.getOrNull()
                } ?: existing?.state ?: emptyMap()

                val layout: JsonObject = params["layout"]?.let { raw ->
                    runCatching { JsonParser.parseString(raw.toString()).asJsonObject }.getOrNull()
                } ?: existing?.layout ?: JsonObject()

                val actions: JsonObject = params["actions"]?.let { raw ->
                    runCatching { JsonParser.parseString(raw.toString()).asJsonObject }.getOrNull()
                } ?: existing?.actions ?: JsonObject()

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
- Call get_guide to see this reference; call list to see existing pages.
""".trimIndent()
    }
}
