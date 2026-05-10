package com.mobileclaw.skill.builtin

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.agent.ChatBubbleStyle
import com.mobileclaw.agent.ChatBubbleDecoration
import com.mobileclaw.agent.TaskType
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID

/**
 * Full CRUD management for agent roles/personas.
 * Use switch_role to just activate a role by id.
 */
class RoleManagerSkill(
    private val roleManager: RoleManager,
    private val roleRequests: MutableSharedFlow<String>,
) : Skill {
    private val gson = Gson()

    override val meta = SkillMeta(
        id = "role_manager",
        name = "Role Manager",
        description = "Create, list, update, delete, and activate agent roles (personas). " +
            "Each role has an avatar emoji, name, description, optional system prompt addendum, " +
            "scheduler keywords, preferred task types, forced skill IDs, optional model override, and open group chat bubble theme DSL. " +
            "Actions: list, create, update, delete, activate.",
        parameters = listOf(
            SkillParam("action", "string", "Action: list | create | update | delete | activate"),
            SkillParam("id", "string", "Role ID (snake_case). Required for update/delete/activate. Auto-generated for create.", required = false),
            SkillParam("name", "string", "Display name of the role", required = false),
            SkillParam("avatar", "string", "Emoji avatar for the role", required = false),
            SkillParam("description", "string", "Short description of the role's purpose", required = false),
            SkillParam("system_prompt", "string", "Additional system prompt text injected when this role is active", required = false),
            SkillParam("preferred_task_types", "string", "Comma-separated TaskType names this role fits, e.g. WEB_RESEARCH,CODE_EXECUTION", required = false),
            SkillParam("keywords", "string", "Comma-separated trigger keywords for role scheduling", required = false),
            SkillParam("scheduler_priority", "number", "Optional scheduling priority. Higher wins when roles are otherwise similar.", required = false),
            SkillParam("forced_skills", "string", "Comma-separated skill IDs that are always injected with this role (e.g. 'shell,web_search')", required = false),
            SkillParam("model_override", "string", "Optional model ID to use when this role is active (e.g. 'gpt-4o')", required = false),
            SkillParam("bubble_preset", "string", "Group chat bubble preset: minimal | ink | paper | outline | glass | neon", required = false),
            SkillParam("bubble_background", "string", "Optional hex color for this role's AI chat bubble, e.g. #0A0A0A or #F7F7F4", required = false),
            SkillParam("bubble_background_image", "string", "Optional local/content/data image reference for this role's bubble background.", required = false),
            SkillParam("bubble_gradient", "string", "Optional comma-separated hex colors for a native bubble gradient.", required = false),
            SkillParam("bubble_text", "string", "Optional hex color for bubble text", required = false),
            SkillParam("bubble_border", "string", "Optional hex color for bubble border", required = false),
            SkillParam("bubble_accent", "string", "Optional hex color for sender name/avatar/status accent", required = false),
            SkillParam("bubble_radius", "number", "Bubble corner radius dp, clamped to 0..48", required = false),
            SkillParam("bubble_radius_top_start", "number", "Top-start corner radius dp. Use 0 for a sharp corner.", required = false),
            SkillParam("bubble_radius_top_end", "number", "Top-end corner radius dp. Use 0 for a sharp corner.", required = false),
            SkillParam("bubble_radius_bottom_end", "number", "Bottom-end corner radius dp. Use 0 for a sharp corner.", required = false),
            SkillParam("bubble_radius_bottom_start", "number", "Bottom-start corner radius dp. Use 0 for a sharp corner.", required = false),
            SkillParam("bubble_tail", "string", "Bubble tail style: none | soft | sharp | round.", required = false),
            SkillParam("bubble_pattern", "string", "Subtle bubble pattern: none | contour | dot", required = false),
            SkillParam("bubble_animation", "string", "Bubble animation: none | pulse | breath | float | sparkle | shake | pop | tilt | bounce", required = false),
            SkillParam("bubble_shadow", "string", "Bubble shadow: none | soft | glow", required = false),
            SkillParam("bubble_shadow_color", "string", "Optional shadow/glow color hex. Defaults to black for soft, accent for glow.", required = false),
            SkillParam("bubble_shadow_alpha", "number", "Shadow opacity 0..0.8. Keep subtle for clean UI.", required = false),
            SkillParam("bubble_shadow_elevation", "number", "Shadow elevation dp 0..32.", required = false),
            SkillParam("bubble_shadow_offset_x", "number", "Shadow horizontal offset dp -12..12.", required = false),
            SkillParam("bubble_shadow_offset_y", "number", "Shadow vertical offset dp -12..12.", required = false),
            SkillParam("bubble_image_mode", "string", "Bubble background image mode: cover | tile | stretch.", required = false),
            SkillParam("bubble_renderer", "string", "Bubble renderer: native | html. Prefer native/Markdown for most role styles; use html only for special rich bubbles.", required = false),
            SkillParam("bubble_html_template", "string", "Optional HTML bubble template when bubble_renderer=html. Use {{message}}, {{sender}}, {{textColor}}, {{accentColor}} placeholders.", required = false),
            SkillParam("bubble_html_height", "number", "HTML bubble viewport height dp 80..420.", required = false),
            SkillParam("bubble_html_allow_js", "boolean", "Allow JavaScript in the HTML bubble.", required = false),
            SkillParam("bubble_html_allow_network", "boolean", "Allow network images/links in the HTML bubble.", required = false),
            SkillParam("bubble_html_transparent", "boolean", "Render HTML bubble with transparent WebView background.", required = false),
            SkillParam(
                "bubble_style_json",
                "object",
                "Full AI-generated group chat bubble theme. Prefer native renderer with Markdown support. Supported keys include renderer/htmlTemplate/htmlHeightDp/htmlAllowJs/htmlAllowNetwork/htmlTransparent, colors, radiusDp and per-corner radii, pattern, legacy decoration fields, decorations[] for multiple relative-position local decorations, animation, font fields, textAnimation, padding, shadow(none|soft|glow), shadowColor, shadowAlpha, shadowElevationDp, shadowOffsetXDp, shadowOffsetYDp, imageMode. Use HTML only when native Markdown/style fields cannot express the bubble.",
                required = false,
            ),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        nameZh = "角色管理",
        descriptionZh = "创建、更新和删除 AI 角色。",
        tags = listOf("角色"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String
            ?: return SkillResult(false, "action is required: list | create | update | delete | activate")

        return when (action) {
            "list" -> {
                val roles = roleManager.all()
                val sb = StringBuilder("Roles (${roles.size}):\n")
                roles.forEach { r ->
                    sb.append("• ${r.avatar} ${r.id}: ${r.name}${if (r.isBuiltin) " [builtin]" else ""}")
                    if (r.forcedSkillIds.isNotEmpty()) sb.append(" | forced: ${r.forcedSkillIds.joinToString(",")}")
                    if (r.modelOverride != null) sb.append(" | model: ${r.modelOverride}")
                    if (r.preferredTaskTypes.isNotEmpty()) sb.append(" | tasks: ${r.preferredTaskTypes.joinToString(",")}")
                    if (r.keywords.isNotEmpty()) sb.append(" | keywords: ${r.keywords.take(6).joinToString(",")}")
                    r.chatBubbleStyle.takeIf { it != ChatBubbleStyle() }?.let { s ->
                        sb.append(" | bubble: ${s.preset}")
                        if (s.backgroundColor.isNotBlank()) sb.append(" bg=${s.backgroundColor}")
                        if (s.backgroundImage.isNotBlank()) sb.append(" image")
                        if (s.gradient.isNotEmpty()) sb.append(" gradient=${s.gradient.size}")
                        if (s.animation != "none") sb.append(" anim=${s.animation}")
                        if (s.accentColor.isNotBlank()) sb.append(" accent=${s.accentColor}")
                    }
                    sb.append("\n  ${r.description}")
                    if (r.systemPromptAddendum.isNotBlank()) sb.append("\n  prompt: ${r.systemPromptAddendum.take(80)}…")
                    sb.append("\n")
                }
                SkillResult(true, sb.toString().trimEnd())
            }

            "create" -> {
                val name = params["name"] as? String
                    ?: return SkillResult(false, "name is required for create")
                val id = (params["id"] as? String)?.takeIf { it.matches(Regex("[a-z0-9_]+")) }
                    ?: "role_${UUID.randomUUID().toString().take(8)}"
                if (roleManager.get(id) != null) return SkillResult(false, "Role '$id' already exists. Use action=update to modify it.")
                val forcedSkills = (params["forced_skills"] as? String)
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: emptyList()
                val preferredTaskTypes = parseTaskTypes(params["preferred_task_types"] as? String)
                val keywords = parseCsv(params["keywords"] as? String)
                val role = Role(
                    id = id,
                    name = name,
                    description = params["description"] as? String ?: "",
                    avatar = params["avatar"] as? String ?: "🤖",
                    systemPromptAddendum = params["system_prompt"] as? String ?: "",
                    forcedSkillIds = forcedSkills,
                    modelOverride = (params["model_override"] as? String)?.takeIf { it.isNotBlank() },
                    preferredTaskTypes = preferredTaskTypes,
                    keywords = keywords,
                    schedulerPriority = (params["scheduler_priority"] as? Number)?.toInt() ?: 0,
                    isBuiltin = false,
                    chatBubbleStyle = bubbleStyleFromParams(params, ChatBubbleStyle()),
                )
                roleManager.save(role)
                SkillResult(true, "Role '${role.avatar} ${role.name}' created with id='$id'. Use action=activate to switch to it.")
            }

            "update" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for update")
                val existing = roleManager.get(id)
                    ?: return SkillResult(false, "Role '$id' not found. Use action=list to see available roles.")
                val styleOnlyUpdate = isBubbleStyleOnlyUpdate(params)
                if (existing.isBuiltin && !styleOnlyUpdate) {
                    return SkillResult(false, "Cannot modify builtin role '$id' except its self-owned bubble style.")
                }
                val forcedSkills = (params["forced_skills"] as? String)
                    ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: existing.forcedSkillIds
                val preferredTaskTypes = (params["preferred_task_types"] as? String)
                    ?.let { parseTaskTypes(it) }
                    ?: existing.preferredTaskTypes
                val keywords = (params["keywords"] as? String)
                    ?.let { parseCsv(it) }
                    ?: existing.keywords
                val updated = if (styleOnlyUpdate) {
                    existing.copy(chatBubbleStyle = bubbleStyleFromParams(params, existing.chatBubbleStyle))
                } else {
                    existing.copy(
                        name = params["name"] as? String ?: existing.name,
                        description = params["description"] as? String ?: existing.description,
                        avatar = params["avatar"] as? String ?: existing.avatar,
                        systemPromptAddendum = params["system_prompt"] as? String ?: existing.systemPromptAddendum,
                        forcedSkillIds = forcedSkills,
                        modelOverride = (params["model_override"] as? String)?.takeIf { it.isNotBlank() } ?: existing.modelOverride,
                        preferredTaskTypes = preferredTaskTypes,
                        keywords = keywords,
                        schedulerPriority = (params["scheduler_priority"] as? Number)?.toInt() ?: existing.schedulerPriority,
                        chatBubbleStyle = bubbleStyleFromParams(params, existing.chatBubbleStyle),
                    )
                }
                roleManager.save(updated)
                SkillResult(true, "Role '$id' updated.")
            }

            "delete" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for delete")
                val role = roleManager.get(id)
                    ?: return SkillResult(false, "Role '$id' not found.")
                if (role.isBuiltin) return SkillResult(false, "Cannot delete builtin role '$id'.")
                roleManager.delete(id)
                SkillResult(true, "Role '$id' deleted.")
            }

            "activate" -> {
                val id = params["id"] as? String
                    ?: return SkillResult(false, "id is required for activate")
                roleManager.get(id) ?: return SkillResult(false, "Role '$id' not found. Use action=list to see available roles.")
                roleRequests.emit(id)
                SkillResult(true, "Activated role '$id'.")
            }

            else -> SkillResult(false, "Unknown action: $action. Use list | create | update | delete | activate")
        }
    }

    private fun parseCsv(raw: String?): List<String> =
        raw.orEmpty()
            .split(",", "，", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun parseTaskTypes(raw: String?): List<TaskType> =
        parseCsv(raw).mapNotNull { value ->
            runCatching { TaskType.valueOf(value.uppercase()) }.getOrNull()
        }.distinct()

    private fun isBubbleStyleOnlyUpdate(params: Map<String, Any>): Boolean {
        val allowed = setOf(
            "action", "id",
            "bubble_preset", "bubble_background", "bubble_background_image", "bubble_gradient", "bubble_text", "bubble_border", "bubble_accent",
            "bubble_radius", "bubble_radius_top_start", "bubble_radius_top_end", "bubble_radius_bottom_end", "bubble_radius_bottom_start", "bubble_tail",
            "bubble_pattern", "bubble_style_json", "bubble_animation", "bubble_shadow",
            "bubble_shadow_color", "bubble_shadow_alpha", "bubble_shadow_elevation", "bubble_shadow_offset_x", "bubble_shadow_offset_y",
            "bubble_emotion", "bubble_font_family", "bubble_font_weight", "bubble_text_animation",
            "bubble_font_size", "bubble_line_height", "bubble_padding_x", "bubble_padding_y",
            "bubble_image_mode",
            "bubble_renderer", "bubble_html_template", "bubble_html_height", "bubble_html_allow_js",
            "bubble_html_allow_network", "bubble_html_transparent",
            "bubble_decoration", "bubble_decoration_text", "bubble_decoration_position", "bubble_decoration_animation", "bubble_decoration_size",
        )
        return params.keys.all { it in allowed } && params.keys.any { it.startsWith("bubble_") }
    }

    private fun bubbleStyleFromParams(params: Map<String, Any>, existing: ChatBubbleStyle): ChatBubbleStyle {
        parseBubbleStyleJson(params["bubble_style_json"], existing)?.let { return sanitizeBubbleStyle(it) }

        fun string(name: String, fallback: String): String =
            (params[name] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: fallback

        fun bool(name: String, fallback: Boolean): Boolean =
            (params[name] as? Boolean)
                ?: (params[name] as? String)?.trim()?.lowercase()?.let {
                    when (it) {
                        "true", "1", "yes", "y", "on" -> true
                        "false", "0", "no", "n", "off" -> false
                        else -> null
                    }
                }
                ?: fallback

        fun gradient(name: String, fallback: List<String>): List<String> =
            (params[name] as? String)
                ?.split(",", "，", "\n")
                ?.mapNotNull { normalizeHex(it.trim()).takeIf { hex -> hex.isNotBlank() } }
                ?.take(4)
                ?.takeIf { it.isNotEmpty() }
                ?: fallback

        val radius = (params["bubble_radius"] as? Number)?.toInt()
            ?.coerceIn(0, 48)
            ?: existing.radiusDp

        return sanitizeBubbleStyle(existing.copy(
            preset = string("bubble_preset", existing.preset).lowercase().let {
                if (it in BUBBLE_PRESETS) it else existing.preset
            },
            renderer = string("bubble_renderer", existing.renderer),
            htmlTemplate = string("bubble_html_template", existing.htmlTemplate).take(MAX_HTML_TEMPLATE_LENGTH),
            htmlHeightDp = (params["bubble_html_height"] as? Number)?.toInt() ?: existing.htmlHeightDp,
            htmlAllowJs = bool("bubble_html_allow_js", existing.htmlAllowJs),
            htmlAllowNetwork = bool("bubble_html_allow_network", existing.htmlAllowNetwork),
            htmlTransparent = bool("bubble_html_transparent", existing.htmlTransparent),
            backgroundColor = normalizeHex(string("bubble_background", existing.backgroundColor)),
            backgroundImage = string("bubble_background_image", existing.backgroundImage).take(MAX_IMAGE_REF_LENGTH),
            gradient = gradient("bubble_gradient", existing.gradient),
            textColor = normalizeHex(string("bubble_text", existing.textColor)),
            borderColor = normalizeHex(string("bubble_border", existing.borderColor)),
            accentColor = normalizeHex(string("bubble_accent", existing.accentColor)),
            radiusDp = radius,
            radiusTopStartDp = (params["bubble_radius_top_start"] as? Number)?.toInt() ?: existing.radiusTopStartDp,
            radiusTopEndDp = (params["bubble_radius_top_end"] as? Number)?.toInt() ?: existing.radiusTopEndDp,
            radiusBottomEndDp = (params["bubble_radius_bottom_end"] as? Number)?.toInt() ?: existing.radiusBottomEndDp,
            radiusBottomStartDp = (params["bubble_radius_bottom_start"] as? Number)?.toInt() ?: existing.radiusBottomStartDp,
            tail = string("bubble_tail", existing.tail),
            decoration = string("bubble_decoration", existing.decoration),
            decorationText = string("bubble_decoration_text", existing.decorationText),
            decorationPosition = string("bubble_decoration_position", existing.decorationPosition),
            decorationAnimation = string("bubble_decoration_animation", existing.decorationAnimation),
            decorationSizeDp = (params["bubble_decoration_size"] as? Number)?.toInt() ?: existing.decorationSizeDp,
            animation = string("bubble_animation", existing.animation),
            shadow = string("bubble_shadow", existing.shadow),
            shadowColor = normalizeHex(string("bubble_shadow_color", existing.shadowColor)),
            shadowAlpha = (params["bubble_shadow_alpha"] as? Number)?.toFloat() ?: existing.shadowAlpha,
            shadowElevationDp = (params["bubble_shadow_elevation"] as? Number)?.toInt() ?: existing.shadowElevationDp,
            shadowOffsetXDp = (params["bubble_shadow_offset_x"] as? Number)?.toInt() ?: existing.shadowOffsetXDp,
            shadowOffsetYDp = (params["bubble_shadow_offset_y"] as? Number)?.toInt() ?: existing.shadowOffsetYDp,
            imageMode = string("bubble_image_mode", existing.imageMode),
            emotion = string("bubble_emotion", existing.emotion),
            fontFamily = string("bubble_font_family", existing.fontFamily),
            fontWeight = string("bubble_font_weight", existing.fontWeight),
            textAnimation = string("bubble_text_animation", existing.textAnimation),
            fontSizeSp = (params["bubble_font_size"] as? Number)?.toInt() ?: existing.fontSizeSp,
            lineHeightSp = (params["bubble_line_height"] as? Number)?.toInt() ?: existing.lineHeightSp,
            paddingHorizontalDp = (params["bubble_padding_x"] as? Number)?.toInt() ?: existing.paddingHorizontalDp,
            paddingVerticalDp = (params["bubble_padding_y"] as? Number)?.toInt() ?: existing.paddingVerticalDp,
            pattern = string("bubble_pattern", existing.pattern).lowercase().let {
                if (it in BUBBLE_PATTERNS) it else existing.pattern
            },
        ))
    }

    private fun normalizeHex(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        val hex = if (value.startsWith("#")) value else "#$value"
        return if (hex.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) hex.uppercase() else ""
    }

    private fun parseBubbleStyleJson(raw: Any?, existing: ChatBubbleStyle): ChatBubbleStyle? {
        if (raw == null) return null
        val obj = when (raw) {
            is JsonElement -> runCatching { raw.asJsonObject }.getOrNull()
            is String -> runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            else -> runCatching { gson.toJsonTree(raw).asJsonObject }.getOrNull()
        } ?: return null

        fun str(name: String, fallback: String): String =
            obj[name]?.takeIf { !it.isJsonNull }?.let {
                runCatching { it.asString }.getOrNull()
            }?.trim()?.takeIf { it.isNotBlank() } ?: fallback

        fun int(name: String, fallback: Int): Int =
            obj[name]?.takeIf { !it.isJsonNull }?.let {
                runCatching { it.asInt }.getOrNull()
            } ?: fallback

        fun bool(name: String, fallback: Boolean): Boolean =
            obj[name]?.takeIf { !it.isJsonNull }?.let {
                runCatching { it.asBoolean }.getOrNull()
                    ?: runCatching {
                        when (it.asString.trim().lowercase()) {
                            "true", "1", "yes", "y", "on" -> true
                            "false", "0", "no", "n", "off" -> false
                            else -> fallback
                        }
                    }.getOrNull()
            } ?: fallback

        val gradient = obj["gradient"]?.let { rawGradient ->
            runCatching {
                rawGradient.asJsonArray.mapNotNull { normalizeHex(it.asString).takeIf { hex -> hex.isNotBlank() } }
            }.getOrDefault(emptyList())
        } ?: existing.gradient
        val decorations = parseDecorations(obj, existing.decorations)

        return existing.copy(
            preset = str("preset", existing.preset),
            renderer = str("renderer", existing.renderer),
            htmlTemplate = str("htmlTemplate", existing.htmlTemplate).take(MAX_HTML_TEMPLATE_LENGTH),
            htmlHeightDp = int("htmlHeightDp", existing.htmlHeightDp),
            htmlAllowJs = bool("htmlAllowJs", existing.htmlAllowJs),
            htmlAllowNetwork = bool("htmlAllowNetwork", existing.htmlAllowNetwork),
            htmlTransparent = bool("htmlTransparent", existing.htmlTransparent),
            backgroundColor = normalizeHex(str("backgroundColor", existing.backgroundColor)),
            backgroundImage = str("backgroundImage", existing.backgroundImage).take(MAX_IMAGE_REF_LENGTH),
            textColor = normalizeHex(str("textColor", existing.textColor)),
            borderColor = normalizeHex(str("borderColor", existing.borderColor)),
            accentColor = normalizeHex(str("accentColor", existing.accentColor)),
            radiusDp = int("radiusDp", existing.radiusDp),
            radiusTopStartDp = int("radiusTopStartDp", existing.radiusTopStartDp),
            radiusTopEndDp = int("radiusTopEndDp", existing.radiusTopEndDp),
            radiusBottomEndDp = int("radiusBottomEndDp", existing.radiusBottomEndDp),
            radiusBottomStartDp = int("radiusBottomStartDp", existing.radiusBottomStartDp),
            decoration = str("decoration", existing.decoration),
            decorationText = str("decorationText", existing.decorationText).take(4),
            decorationPosition = str("decorationPosition", existing.decorationPosition),
            decorationAnimation = str("decorationAnimation", existing.decorationAnimation),
            decorationSizeDp = int("decorationSizeDp", existing.decorationSizeDp),
            decorations = decorations,
            tail = str("tail", existing.tail),
            pattern = str("pattern", existing.pattern),
            gradient = gradient.take(4),
            animation = str("animation", existing.animation),
            emotion = str("emotion", existing.emotion),
            fontFamily = str("fontFamily", existing.fontFamily),
            fontWeight = str("fontWeight", existing.fontWeight),
            textAnimation = str("textAnimation", existing.textAnimation),
            fontSizeSp = int("fontSizeSp", existing.fontSizeSp),
            lineHeightSp = int("lineHeightSp", existing.lineHeightSp),
            paddingHorizontalDp = int("paddingHorizontalDp", existing.paddingHorizontalDp),
            paddingVerticalDp = int("paddingVerticalDp", existing.paddingVerticalDp),
            shadow = str("shadow", existing.shadow),
            shadowColor = normalizeHex(str("shadowColor", existing.shadowColor)),
            shadowAlpha = obj["shadowAlpha"]?.takeIf { !it.isJsonNull }?.let { runCatching { it.asFloat }.getOrNull() } ?: existing.shadowAlpha,
            shadowElevationDp = int("shadowElevationDp", existing.shadowElevationDp),
            shadowOffsetXDp = int("shadowOffsetXDp", existing.shadowOffsetXDp),
            shadowOffsetYDp = int("shadowOffsetYDp", existing.shadowOffsetYDp),
            imageMode = str("imageMode", existing.imageMode),
            schemaVersion = int("schemaVersion", 2),
        )
    }

    private fun parseDecorations(obj: JsonObject, existing: List<ChatBubbleDecoration>): List<ChatBubbleDecoration> {
        val raw = obj["decorations"]?.takeIf { !it.isJsonNull } ?: return existing
        return runCatching {
            raw.asJsonArray.mapNotNull { item ->
                val deco = item.asJsonObject
                ChatBubbleDecoration(
                    type = deco["type"]?.asString.orEmpty(),
                    text = deco["text"]?.asString.orEmpty(),
                    position = deco["position"]?.asString.orEmpty(),
                    x = deco["x"]?.let { runCatching { it.asFloat }.getOrNull() } ?: -1f,
                    y = deco["y"]?.let { runCatching { it.asFloat }.getOrNull() } ?: -1f,
                    animation = deco["animation"]?.asString.orEmpty(),
                    sizeDp = deco["sizeDp"]?.let { runCatching { it.asInt }.getOrNull() } ?: 14,
                    color = deco["color"]?.asString.orEmpty(),
                    alpha = deco["alpha"]?.let { runCatching { it.asFloat }.getOrNull() } ?: -1f,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun sanitizeBubbleStyle(style: ChatBubbleStyle): ChatBubbleStyle {
        fun enum(value: String, allowed: Set<String>, fallback: String): String {
            val clean = value.lowercase().trim()
            return if (clean in allowed) clean else fallback
        }

        return style.copy(
            preset = enum(style.preset, BUBBLE_PRESETS, "minimal"),
            renderer = enum(style.renderer, setOf("native", "html"), "native"),
            htmlTemplate = if (style.renderer.lowercase().trim() == "html") style.htmlTemplate.take(MAX_HTML_TEMPLATE_LENGTH) else "",
            htmlHeightDp = style.htmlHeightDp.coerceIn(80, 420),
            htmlAllowJs = style.htmlAllowJs,
            htmlAllowNetwork = style.htmlAllowNetwork,
            htmlTransparent = style.htmlTransparent,
            backgroundColor = normalizeHex(style.backgroundColor),
            textColor = normalizeHex(style.textColor),
            borderColor = normalizeHex(style.borderColor),
            accentColor = normalizeHex(style.accentColor),
            radiusDp = style.radiusDp.coerceIn(4, 36),
            radiusTopStartDp = sanitizeCornerRadius(style.radiusTopStartDp),
            radiusTopEndDp = sanitizeCornerRadius(style.radiusTopEndDp),
            radiusBottomEndDp = sanitizeCornerRadius(style.radiusBottomEndDp),
            radiusBottomStartDp = sanitizeCornerRadius(style.radiusBottomStartDp),
            tail = enum(style.tail, setOf("none", "soft", "sharp", "round"), "soft"),
            pattern = enum(style.pattern, BUBBLE_PATTERNS, "none"),
            decoration = enum(style.decoration, BUBBLE_DECORATIONS, "none"),
            decorationText = style.decorationText.take(4),
            decorationPosition = enum(style.decorationPosition, BUBBLE_DECORATION_POSITIONS, "top_end"),
            decorationAnimation = enum(style.decorationAnimation, BUBBLE_DECORATION_ANIMATIONS, "none"),
            decorationSizeDp = style.decorationSizeDp.coerceIn(8, 28),
            decorations = sanitizeDecorations(style.decorations),
            gradient = style.gradient.mapNotNull { normalizeHex(it).takeIf { hex -> hex.isNotBlank() } }.take(4),
            animation = enum(style.animation, setOf("none", "pulse", "breath", "float", "sparkle", "shake", "pop", "tilt", "bounce"), "none"),
            emotion = enum(style.emotion, setOf("neutral", "happy", "sad", "angry", "shy", "cool", "excited", "sleepy", "love"), "neutral"),
            fontFamily = enum(style.fontFamily, setOf("system", "serif", "mono", "rounded"), "system"),
            fontWeight = enum(style.fontWeight, setOf("light", "regular", "medium", "semibold", "bold", "extrabold", "heavy", "black"), "regular"),
            textAnimation = enum(style.textAnimation, setOf("none", "fade", "pop", "breath", "shimmer", "typewriter", "marquee", "wave", "glow", "neon", "flash", "jelly"), "none"),
            fontSizeSp = style.fontSizeSp.coerceIn(12, 20),
            lineHeightSp = style.lineHeightSp.coerceIn(16, 28),
            paddingHorizontalDp = style.paddingHorizontalDp.coerceIn(8, 22),
            paddingVerticalDp = style.paddingVerticalDp.coerceIn(6, 18),
            shadow = enum(style.shadow, setOf("none", "soft", "glow"), "none"),
            shadowColor = normalizeHex(style.shadowColor),
            shadowAlpha = if (style.shadowAlpha < 0f) -1f else style.shadowAlpha.coerceIn(0f, 0.8f),
            shadowElevationDp = if (style.shadowElevationDp < 0) -1 else style.shadowElevationDp.coerceIn(0, 32),
            shadowOffsetXDp = style.shadowOffsetXDp.coerceIn(-12, 12),
            shadowOffsetYDp = style.shadowOffsetYDp.coerceIn(-12, 12),
            imageMode = enum(style.imageMode, setOf("cover", "tile", "stretch"), "cover"),
            backgroundImage = style.backgroundImage.take(MAX_IMAGE_REF_LENGTH),
            schemaVersion = 2,
        )
    }

    private fun sanitizeDecorations(decorations: List<ChatBubbleDecoration>): List<ChatBubbleDecoration> {
        fun enum(value: String, allowed: Set<String>, fallback: String): String {
            val clean = value.lowercase().trim()
            return if (clean in allowed) clean else fallback
        }
        return decorations.take(8).mapNotNull { decoration ->
            val type = enum(decoration.type, BUBBLE_DECORATIONS, "none")
            if (type == "none") return@mapNotNull null
            ChatBubbleDecoration(
                type = type,
                text = decoration.text.take(6),
                position = enum(decoration.position, BUBBLE_DECORATION_POSITIONS, "top_end"),
                x = if (decoration.x in 0f..1f) decoration.x else -1f,
                y = if (decoration.y in 0f..1f) decoration.y else -1f,
                animation = enum(decoration.animation, BUBBLE_DECORATION_ANIMATIONS, "none"),
                sizeDp = decoration.sizeDp.coerceIn(8, 32),
                color = normalizeHex(decoration.color),
                alpha = if (decoration.alpha < 0f) -1f else decoration.alpha.coerceIn(0f, 1f),
            )
        }
    }

    private companion object {
        fun sanitizeCornerRadius(value: Int): Int = if (value < 0) -1 else value.coerceIn(0, 48)
        val BUBBLE_PRESETS = setOf("minimal", "ink", "paper", "outline", "glass", "neon", "theme", "image")
        val BUBBLE_PATTERNS = setOf("none", "contour", "dot", "grid", "star", "stripe")
        val BUBBLE_DECORATIONS = setOf("none", "dot", "sparkle", "heart", "star", "moon", "badge", "text", "firework", "glimmer", "aurora")
        val BUBBLE_DECORATION_POSITIONS = setOf("top_start", "top_center", "top_end", "center_start", "center_end", "bottom_start", "bottom_center", "bottom_end", "tail")
        val BUBBLE_DECORATION_ANIMATIONS = setOf("none", "pulse", "float", "sparkle", "orbit", "firework", "glimmer", "aurora")
        const val MAX_IMAGE_REF_LENGTH = 500_000
        const val MAX_HTML_TEMPLATE_LENGTH = 12_000
    }
}
