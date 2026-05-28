package com.mobileclaw.skill.builtin

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import com.google.gson.GsonBuilder
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.town.AgentSpritePack
import com.mobileclaw.town.AgentTownStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HouseArtistSkill(
    private val townStore: AgentTownStore,
    private val roleManager: RoleManager,
) : Skill {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override val meta = SkillMeta(
        id = "house_artist",
        name = "AI House Artist",
        nameZh = "AI 动态画像画师",
        description = "Creates an AI role's own animated desktop-pet style portrait as a structured spritesheet. " +
            "The quality bar is polished collectible game-character art with clear full-body silhouette and strong role identity. " +
            "Use this when an AI needs to generate, validate, register, or assign its own moving body/avatar. " +
            "Best workflow: get_schema -> plan_role_visual_brief -> plan_character_reference -> generate_image -> plan_state_row for each state -> generate_image -> compose_sprite_pack -> preview_sprite_pack -> review_sprite_image with QUALITY_SCORE -> register_sprite_pack -> set_role_sprite_pack. " +
            "Fallback workflow: plan_sprite_pack -> generate_image -> register_sprite_pack. " +
            "Do not output a single static avatar when the user asks for an AI's living portrait.",
        descriptionZh = "为 AI 自己生成可动画像。按桌宠思路生成 spritesheet、状态机元数据，并登记到角色。",
        parameters = listOf(
            SkillParam("action", "string", "get_schema | plan_role_visual_brief | plan_character_reference | plan_state_row | compose_sprite_pack | preview_sprite_pack | plan_sprite_pack | review_sprite_image | register_sprite_pack | validate_sprite_pack | list_packs | set_role_sprite_pack"),
            SkillParam("role_id", "string", "Target role id.", required = false),
            SkillParam("style", "string", "Visual direction, e.g. polished desktop pet, black-white AI mascot, RPG helper.", required = false),
            SkillParam("state", "string", "Animation state for plan_state_row: idle | running_right | running_left | waving | jumping | failed | waiting | running | review.", required = false),
            SkillParam("reference_note", "string", "Short description of the approved character reference for keeping state rows consistent.", required = false),
            SkillParam("metadata_json", "string", "AgentSpritePack JSON for register/validate actions.", required = false),
            SkillParam("image_data_uri", "string", "Optional data:image/png;base64,... for a generated spritesheet.", required = false),
            SkillParam("state_rows_json", "string", "JSON object mapping state names to data:image/... rows for compose_sprite_pack.", required = false),
            SkillParam("sprite_pack_id", "string", "Sprite pack id for assigning a role.", required = false),
        ),
        injectionLevel = 1,
        internalTool = true,
        categories = listOf(SkillToolCategory.SELF_EVOLUTION, SkillToolCategory.MEDIA, SkillToolCategory.ARTIFACT),
        tags = listOf("house", "portrait", "desktop pet", "spritesheet", "pixel", "AI画像", "动态头像"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        townStore.ensureRooms(roleManager.all())
        val action = params["action"] as? String ?: return@withContext SkillResult(false, "action is required")
        val roleId = (params["role_id"] as? String)
            ?: roleManager.all().firstOrNull()?.id
            ?: "general"
        val role = roleManager.get(roleId) ?: roleManager.all().firstOrNull { it.id == roleId }
        val style = (params["style"] as? String)?.takeIf { it.isNotBlank() } ?: "polished desktop pet character"
        val visualBrief = roleVisualBrief(roleId, role, style)

        when (action) {
            "get_schema" -> SkillResult(true, schemaText())
            "plan_role_visual_brief" -> {
                SkillResult(true, gson.toJson(visualBrief))
            }
            "plan_character_reference" -> {
                SkillResult(true, gson.toJson(characterReferencePlan(visualBrief)))
            }
            "plan_state_row" -> {
                val state = params["state"] as? String ?: return@withContext SkillResult(false, "state is required")
                val reference = params["reference_note"] as? String ?: ""
                SkillResult(true, gson.toJson(stateRowPlan(visualBrief, state, reference)))
            }
            "plan_sprite_pack" -> {
                val packId = "sprite_${roleId.replace(Regex("[^a-zA-Z0-9_]+"), "_")}"
                val pack = AgentSpritePack(
                    id = packId,
                    name = "${visualBrief.roleName} Sprite Sheet",
                    kind = "character",
                    frameWidth = 192,
                    frameHeight = 208,
                    columns = 8,
                    rows = 9,
                    palette = visualBrief.palette,
                    notes = "Generated by house_artist for role '$roleId'. ${visualBrief.shortConcept}",
                )
                SkillResult(true, gson.toJson(spritePlan(visualBrief, pack)))
            }
            "validate_sprite_pack" -> {
                val json = params["metadata_json"] as? String ?: return@withContext SkillResult(false, "metadata_json is required")
                val pack = parseSpritePack(json) ?: return@withContext SkillResult(false, "metadata_json is not a valid AgentSpritePack")
                SkillResult(true, validateSpritePack(pack).joinToString("\n").ifBlank { "Sprite pack is valid." })
            }
            "review_sprite_image" -> {
                val json = params["metadata_json"] as? String ?: return@withContext SkillResult(false, "metadata_json is required")
                val imageDataUri = params["image_data_uri"] as? String ?: return@withContext SkillResult(false, "image_data_uri is required")
                val pack = parseSpritePack(json) ?: return@withContext SkillResult(false, "metadata_json is not a valid AgentSpritePack")
                SkillResult(true, reviewSpriteImage(pack, imageDataUri).joinToString("\n").ifBlank { "Sprite image passed structural review." })
            }
            "preview_sprite_pack" -> {
                val json = params["metadata_json"] as? String ?: return@withContext SkillResult(false, "metadata_json is required")
                val pack = parseSpritePack(json) ?: return@withContext SkillResult(false, "metadata_json is not a valid AgentSpritePack")
                val imageDataUri = (params["image_data_uri"] as? String)
                    ?: pack.imagePath.takeIf { it.isNotBlank() }?.let { fileToDataUri(File(it)) }
                    ?: return@withContext SkillResult(false, "image_data_uri is required when metadata_json.imagePath is empty")
                previewSpritePack(pack, imageDataUri)
            }
            "compose_sprite_pack" -> {
                val json = params["metadata_json"] as? String ?: return@withContext SkillResult(false, "metadata_json is required")
                val rowsJson = params["state_rows_json"] as? String ?: return@withContext SkillResult(false, "state_rows_json is required")
                val pack = parseSpritePack(json) ?: return@withContext SkillResult(false, "metadata_json is not a valid AgentSpritePack")
                composeSpritePack(pack, rowsJson)
            }
            "register_sprite_pack" -> {
                val json = params["metadata_json"] as? String ?: return@withContext SkillResult(false, "metadata_json is required")
                val pack = parseSpritePack(json) ?: return@withContext SkillResult(false, "metadata_json is not a valid AgentSpritePack")
                val issues = validateSpritePack(pack)
                if (issues.any { it.startsWith("ERROR") }) return@withContext SkillResult(false, issues.joinToString("\n"))
                val saved = townStore.registerSpritePack(pack, params["image_data_uri"] as? String)
                SkillResult(true, "Registered sprite pack '${saved.id}'.\n${gson.toJson(saved)}")
            }
            "set_role_sprite_pack" -> {
                val packId = params["sprite_pack_id"] as? String ?: return@withContext SkillResult(false, "sprite_pack_id is required")
                if (townStore.state.value.spritePacks[packId] == null) return@withContext SkillResult(false, "Sprite pack '$packId' not found.")
                val room = townStore.assignRoleSpritePack(roleId, packId)
                SkillResult(true, "Assigned sprite pack '$packId' to '$roleId'.\n${gson.toJson(room)}")
            }
            "list_packs" -> {
                val state = townStore.state.value
                SkillResult(true, gson.toJson(mapOf("spritePacks" to state.spritePacks)))
            }
            else -> SkillResult(false, "Unknown action '$action'.")
        }
    }

    private fun schemaText(): String = """
        Animated AI portrait protocol v1:
        1. Output one polished desktop-pet spritesheet, 8 columns x 9 rows, 192x208 px per frame.
        2. Runtime canvas is 1536x1872. Each row is one animation state.
        3. Rows: idle, running_right, running_left, waving, jumping, failed, waiting, running, review.
        4. The app crops frames by AgentSpritePack metadata and plays the state animation.
        5. Use a clean character silhouette, strong expression, coherent lighting, and no text baked into frames.
        6. Prefer an embodied AI character feeling: expressive, alive, role-specific, and high-resolution enough to look polished.
        7. The character must be role-specific. Always call plan_role_visual_brief first and keep its silhouette, palette, props, and personality anchors.
        8. For best quality, do not ask the image model to invent 72 frames at once. First approve one character reference, then generate one 8-frame row per state with the same character, then compose the final sheet.
        9. compose_sprite_pack accepts state_rows_json with 9 generated row images and deterministically builds the final spritesheet.
        10. Image generation must use the fixed chroma-key background #00FF00. compose_sprite_pack removes that background into transparency.
        11. preview_sprite_pack creates an HTML animation preview for idle, waving, waiting, review, and failed before publishing.
        12. Quality bar: the result should feel like the AI role's own chosen body, not a generic avatar, animal mascot, black block, icon, emoji, crude big-head doll, three-view sheet, or UI placeholder.
    """.trimIndent()

    private fun characterReferencePlan(brief: RoleVisualBrief): Map<String, Any> {
        val prompt = """
            Create one polished embodied character portrait for this exact AI role.

            ${brief.promptBlock()}

            Visual target:
            - One single full-body character, front-facing 3/4 view, collectible game-character quality.
            - Full body means head-to-toe visible: both feet, both hands, cape/clothing edges, and signature prop must fit inside the image with 12-18% margin.
            - Default to a role-shaped human-like companion, spirit, mage, operator, designer, engineer, or abstract humanoid. Do not default to robot, android, mecha, sci-fi armor, dog, cat, fox, pet, or random animal unless the role description explicitly says it is that body.
            - Use a readable silhouette at small size, selective outline, hue-shifted palette, and a clean pose that works as an AI role portrait.
            - Fixed background: solid flat chroma-key green #00FF00, no texture, no shadow, no gradient.
            - Do not use #00FF00 or any near-neon green in the character, prop, effects, eyes, clothing, or outline.
            - The character must look like this role chose its own body, not a generic robot mascot.
            - Use the role-specific silhouette, palette, prop, facial expression, and room influence above.
            - Make it high-quality and charming like a real playable RPG/support character from a polished game.
            - Keep details animation-friendly: one clear prop, readable outfit/body shape, no tiny noisy accessories.
            - It should feel like a lovable AI companion with identity, not a sterile mannequin, faceless armor suit, product render, or generic sci-fi shell.

            Hard rejects:
            - Do not draw a generic robot, android, mecha, generic chibi person, plastic mannequin, faceless armor, big-head doll, black block body, logo icon, sticker head, emoji, pet, dog, cat, fox, wolf, or random animal unless explicitly requested by the role.
            - Do not draw bust portraits, half-body portraits, cropped feet, cropped hands, cropped props, cropped cape, or any character cut off by the frame.
            - Do not draw a character sheet, lineup, three-view, turnaround, multiple variants, multiple poses, comparison grid, concept sheet, or model sheet.
            - Do not add text, labels, signatures, UI panels, room background, frame borders, transparent background, or cropped body parts.
            - Do not ignore the role description or turn every role into the same mascot.
        """.trimIndent()
        return mapOf(
            "nextTool" to "generate_image",
            "recommendedModel" to "gpt-image-2 or another high-quality image model; avoid low-quality free models for the final character",
            "imageSize" to "1024x1024",
            "assetType" to "agent_character_reference",
            "roleId" to brief.roleId,
            "visualBrief" to brief,
            "prompt" to prompt,
            "afterImage" to "Use the generated reference as the visual contract. Summarize its stable role-specific traits into reference_note, then call house_artist.plan_state_row for each animation state. If it looks generic, ugly, or unrelated to the role, regenerate before proceeding.",
        )
    }

    private fun stateRowPlan(brief: RoleVisualBrief, state: String, reference: String): Map<String, Any> {
        val motion = when (state) {
            "idle" -> "8-frame idle loop: subtle breathing, tiny blink, calm alive posture"
            "running_right" -> "8-frame run cycle facing right, readable body motion"
            "running_left" -> "8-frame run cycle facing left, same character mirrored naturally"
            "waving" -> "8-frame friendly waving loop, warm and expressive"
            "jumping" -> "8-frame excited jump loop with squash and stretch"
            "failed" -> "8-frame failed or sad loop, gentle and cute rather than ugly"
            "waiting" -> "8-frame waiting/thinking loop, small head tilt or processing gesture"
            "running" -> "8-frame energetic forward run or hurry loop"
            "review" -> "8-frame focused working/review loop, inspecting or typing motion"
            else -> "8-frame expressive desktop-pet loop for state '$state'"
        }
        val prompt = """
            Create exactly one horizontal animation row for the same embodied AI character "${brief.roleName}".

            ${brief.promptBlock()}

            Approved character reference traits:
            ${reference.ifBlank { brief.referenceSeed }}

            Motion: $motion.
            Canvas: 1536x208 pixels. Grid: 8 columns x 1 row. Each frame is 192x208 pixels.
            Requirements:
            - Same character identity in every frame; do not redesign the role between frames.
            - Preserve full-body proportions from the approved reference; do not turn it into a large head with a tiny body unless the reference itself is a deliberate non-human mascot.
            - Keep the role prop and silhouette visible but simple.
            - Center inside each cell with at least 8 px transparent margin.
            - Fixed background: solid flat chroma-key green #00FF00 across the whole row, no texture, no shadow, no gradient.
            - Do not use #00FF00 or any near-neon green in the character, prop, effects, eyes, clothing, or outline.
            - No text, no UI, no scene, no cropped body parts, no frame borders.
            - Make the motion expressive but restrained; use subpixel-style shifts, blinks, and prop accents.
        """.trimIndent()
        return mapOf(
            "nextTool" to "generate_image",
            "recommendedModel" to "use the same high-quality model/provider as the reference image",
            "imageSize" to "1536x208",
            "assetType" to "agent_state_row",
            "roleId" to brief.roleId,
            "state" to state,
            "visualBrief" to brief,
            "prompt" to prompt,
            "afterImage" to "Preview the 8 frames. If identity drifts, cropped limbs appear, or motion is unclear, regenerate this row. When all 9 rows are approved, call house_artist.compose_sprite_pack, then preview_sprite_pack.",
        )
    }

    private fun spritePlan(brief: RoleVisualBrief, pack: AgentSpritePack): Map<String, Any> {
        val prompt = """
            Create a polished embodied AI character spritesheet for this exact AI role.

            ${brief.promptBlock()}

            Exact runtime canvas: 1536x1872 pixels. Grid: 8 columns x 9 rows. Each frame is 192x208 pixels.
            Rows:
            row 0 idle, subtle breathing and blink;
            row 1 running right;
            row 2 running left;
            row 3 waving or greeting;
            row 4 jumping or excited motion;
            row 5 failed or sad/error state;
            row 6 waiting or thinking;
            row 7 running/front energetic loop;
            row 8 review/working/focused state.
            Requirements:
            - One consistent character across all frames.
            - Role-specific silhouette, palette, prop, and expression must stay consistent.
            - Use polished AI character/game-sprite proportions; avoid animals unless explicit, crude big-head dolls, black block bodies, generic mascot blobs, and placeholder silhouettes.
            - Centered in every cell with safe transparent margins.
            - Fixed background: solid flat chroma-key green #00FF00 in every cell, no texture, no shadow, no gradient.
            - Do not use #00FF00 or any near-neon green in the character, prop, effects, eyes, clothing, or outline.
            - No cropped body parts, no text, no UI, no room background, no random props that change identity between frames.
            - Avoid generic robots and generic chibi humans; this must visually communicate "${brief.shortConcept}".
            The grid must fill the whole image with equal cells and no decorative margins. The app will normalize the returned image into a 1536x1872 runtime spritesheet.
        """.trimIndent()
        return mapOf(
            "nextTool" to "generate_image",
            "imageSize" to "1536x1872",
            "normalizedCanvas" to "1536x1872",
            "assetType" to "agent_spritesheet",
            "roleId" to brief.roleId,
            "visualBrief" to brief,
            "prompt" to prompt,
            "metadataJson" to gson.toJson(pack),
            "afterImage" to "First call house_artist.review_sprite_image with metadata_json and image_data_uri. Only call register_sprite_pack if review has no ERROR and no major WARN.",
        )
    }

    private data class RoleVisualBrief(
        val roleId: String,
        val roleName: String,
        val roleDescription: String,
        val style: String,
        val shortConcept: String,
        val silhouette: String,
        val personality: String,
        val signatureProp: String,
        val roomInfluence: String,
        val avatarHint: String,
        val bubbleStyleHint: String,
        val palette: List<String>,
        val keywords: List<String>,
        val taskTypes: List<String>,
        val referenceSeed: String,
    ) {
        fun promptBlock(): String = """
            Role visual brief:
            - Role id: $roleId
            - Name: $roleName
            - Role description: $roleDescription
            - Style direction: $style
            - Core concept: $shortConcept
            - Silhouette: $silhouette
            - Personality: $personality
            - Signature prop: $signatureProp
            - Room influence: $roomInfluence
            - Existing avatar hint: $avatarHint
            - Chat bubble style hint: $bubbleStyleHint
            - Palette: ${palette.joinToString(", ")}
            - Keywords: ${keywords.joinToString(", ")}
            - Task types: ${taskTypes.joinToString(", ")}
        """.trimIndent()
    }

    private fun roleVisualBrief(roleId: String, role: Role?, style: String): RoleVisualBrief {
        val resolvedRole = role ?: Role.DEFAULT.copy(id = roleId, name = roleId)
        val room = townStore.state.value.rooms[roleId]
        val roleText = listOf(
            resolvedRole.name,
            resolvedRole.description,
            resolvedRole.keywords.joinToString(" "),
            resolvedRole.preferredTaskTypes.joinToString(" ") { it.name },
            room?.houseName.orEmpty(),
            room?.style.orEmpty(),
            room?.motto.orEmpty(),
            room?.doorSign.orEmpty(),
            resolvedRole.avatar,
            resolvedRole.chatBubbleStyle.preset,
            resolvedRole.chatBubbleStyle.emotion,
            resolvedRole.chatBubbleStyle.accentColor,
        ).joinToString(" ").lowercase()
        val promptText = resolvedRole.systemPromptAddendum.lowercase()
        val category = roleVisualCategory(roleId, roleText, promptText)
        val palette = when (category) {
            "coder" ->
                listOf("#18191D", "#A78BFA", "#7BF0BF", "#FFE2B2", "#F6F1E7")
            "phone" ->
                listOf("#101820", "#38BDF8", "#C7F43A", "#F8FAFC", "#334155")
            "web" ->
                listOf("#10231C", "#34D399", "#93C5FD", "#FFF7D6", "#1F2937")
            "creator" ->
                listOf("#251522", "#F472B6", "#FBBF24", "#A78BFA", "#FFF1F2")
            "ui_design" ->
                listOf("#21171C", "#FF8FAB", "#FFF4E8", "#F9A8D4", "#111111")
            "skill" ->
                listOf("#241A0A", "#FBBF24", "#60A5FA", "#FDE68A", "#111827")
            "vpn" ->
                listOf("#0B1220", "#60A5FA", "#22D3EE", "#E0F2FE", "#1E293B")
            else -> listOf("#18191D", "#C7F43A", "#FFE2B2", "#F6F1E7", "#111111")
        }
        val silhouette = when (category) {
            "coder" ->
                "compact terminal engineer-mage: square visor, short tech coat, cable bracelet, bracket-shaped shoulder lights, clear typing hands"
            "phone" ->
                "nimble phone pilot: small headset, touch-glove hands, screen-shaped backpack, alert stance"
            "web" ->
                "research scout: lens visor, map cape, small browser-tab satchel, curious leaning posture"
            "creator" ->
                "artist builder: paint-light antenna, creator apron, floating stylus or tiny canvas companion"
            "ui_design" ->
                "minimal UI atelier spirit: cream-paper cloak, soft pink trim, tiny layout-grid wings, elegant pointer hands"
            "skill" ->
                "tool archivist: key-ring backpack, catalog tabs, neat librarian/toolkeeper silhouette"
            "vpn" ->
                "network runner: signal scarf, shield core, node-orbit accents, steady guardian posture"
            else -> "role-shaped AI humanoid companion: simple body, expressive face, one unique accessory from its role"
        }
        val prop = when (category) {
            "coder" -> "tiny terminal slab with angle-bracket glow"
            "phone" -> "touch pointer glove and miniature phone screen"
            "web" -> "magnifying-glass browser tab"
            "creator" -> "glowing stylus and color chip"
            "ui_design" -> "mini design grid card with a soft heart sparkle accent"
            "skill" -> "small toolbox/archive card"
            "vpn" -> "signal shield or orbiting network node"
            else -> "one small object clearly tied to this role's job"
        }
        val personality = when (category) {
            "coder" -> "focused, precise, calm, slightly serious"
            "creator" -> "playful, expressive, visually bold, curious"
            "ui_design" -> "tasteful, restrained, warm, detail-obsessed"
            "phone" -> "quick, alert, reliable, action-ready"
            "web" -> "observant, source-minded, investigative"
            "vpn" -> "steady, protective, technical"
            else -> "friendly, useful, quietly intelligent"
        }
        val roomInfluence = listOfNotNull(
            room?.houseName?.takeIf { it.isNotBlank() },
            room?.style?.takeIf { it.isNotBlank() },
            room?.motto?.takeIf { it.isNotBlank() },
            room?.doorSign?.takeIf { it.isNotBlank() },
        ).joinToString(" / ").ifBlank { "no room data yet; infer from role description" }
        val concept = when (category) {
            "coder" -> "${resolvedRole.name}: tiny terminal engineer"
            "phone" -> "${resolvedRole.name}: Android field operator"
            "web" -> "${resolvedRole.name}: web research scout"
            "creator" -> "${resolvedRole.name}: creative artifact maker"
            "ui_design" -> "${resolvedRole.name}: refined mobile UI atelier companion"
            "skill" -> "${resolvedRole.name}: skill archivist"
            "vpn" -> "${resolvedRole.name}: network guardian"
            else -> "${resolvedRole.name}: ${resolvedRole.description.ifBlank { "personal AI companion" }.take(32)}"
        }
        return RoleVisualBrief(
            roleId = roleId,
            roleName = resolvedRole.name,
            roleDescription = resolvedRole.description.ifBlank { roleId },
            style = style,
            shortConcept = concept,
            silhouette = silhouette,
            personality = personality,
            signatureProp = prop,
            roomInfluence = roomInfluence,
            avatarHint = resolvedRole.avatar.take(120),
            bubbleStyleHint = listOf(
                resolvedRole.chatBubbleStyle.preset,
                resolvedRole.chatBubbleStyle.renderer,
                resolvedRole.chatBubbleStyle.emotion,
                resolvedRole.chatBubbleStyle.accentColor,
                resolvedRole.chatBubbleStyle.decoration,
            ).filter { it.isNotBlank() && it != "none" }.joinToString(" / ").ifBlank { "no custom bubble style yet" },
            palette = palette,
            keywords = resolvedRole.keywords.take(12),
            taskTypes = resolvedRole.preferredTaskTypes.map { it.name }.take(8),
            referenceSeed = "$concept; $silhouette; $personality; prop=$prop; palette=${palette.joinToString("/")}; room=$roomInfluence",
        )
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it.lowercase()) }

    private fun roleVisualCategory(roleId: String, roleText: String, promptText: String): String = when {
        roleId == "coder" -> "coder"
        roleId == "phone_operator" -> "phone"
        roleId == "web_agent" -> "web"
        roleId == "creator" -> "creator"
        roleId == "skill_admin" -> "skill"
        roleId == "vpn_operator" -> "vpn"
        roleId == "general" -> "general"
        roleText.containsAny("ui", "界面", "审美", "设计", "动效", "产品", "气泡", "移动端") ||
            promptText.containsAny("ui", "界面", "审美", "设计", "动效", "产品", "气泡", "移动端") -> "ui_design"
        roleText.containsAny("code", "coder", "terminal", "脚本", "编程", "代码", "调试") -> "coder"
        roleText.containsAny("phone", "android", "screen", "手机", "点击", "滑动", "操控") -> "phone"
        roleText.containsAny("web", "search", "browse", "research", "网络", "搜索", "网页") -> "web"
        roleText.containsAny("image", "creator", "creative", "生成", "图片", "创意", "页面") -> "creator"
        roleText.containsAny("skill_admin", "技能管理员", "创建技能", "技能市场", "整理 skill", "skill inventory") -> "skill"
        roleText.containsAny("vpn", "proxy", "代理", "节点", "线路") -> "vpn"
        else -> "general"
    }

    private fun parseSpritePack(json: String): AgentSpritePack? =
        runCatching { gson.fromJson(json, AgentSpritePack::class.java) }.getOrNull()

    private fun validateSpritePack(pack: AgentSpritePack): List<String> = buildList {
        if (pack.id.isBlank()) add("ERROR: id is required.")
        if (pack.frameWidth !in 8..512 || pack.frameHeight !in 8..512) add("ERROR: frame size must be 8..512.")
        if (pack.columns !in 1..16 || pack.rows !in 1..16) add("ERROR: columns/rows must be 1..16.")
        listOf("idle", "running_right", "running_left", "waving", "jumping", "failed", "waiting", "running", "review").forEach { state ->
            if (pack.states[state] == null) add("WARN: missing state '$state'.")
        }
        pack.states.forEach { (name, state) ->
            if (state.row !in 0 until pack.rows) add("ERROR: state '$name' row is outside spritesheet rows.")
            if (state.startColumn + state.frames > pack.columns) add("ERROR: state '$name' frames exceed column count.")
        }
        if (pack.imagePath.isBlank()) add("WARN: imagePath is empty. Register again with image_data_uri or imagePath after generation.")
    }

    private fun composeSpritePack(pack: AgentSpritePack, rowsJson: String): SkillResult {
        val stateRows = runCatching {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(rowsJson, Map::class.java) as Map<String, Any>
        }.getOrNull() ?: return SkillResult(false, "state_rows_json must be a JSON object mapping state names to data URIs.")

        val rowOrder = listOf("idle", "running_right", "running_left", "waving", "jumping", "failed", "waiting", "running", "review")
        val expectedW = pack.frameWidth * pack.columns
        val expectedH = pack.frameHeight * pack.rows
        val sheet = android.graphics.Bitmap.createBitmap(expectedW, expectedH, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        rowOrder.forEachIndexed { index, state ->
            val dataUri = stateRows[state]?.toString()?.takeIf { it.isNotBlank() }
            if (dataUri == null) {
                missing += state
                return@forEachIndexed
            }
            val rowBitmap = runCatching {
                val bytes = decodeDataUri(dataUri)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
            if (rowBitmap == null) {
                missing += "$state(unreadable)"
                return@forEachIndexed
            }
            val normalized = if (rowBitmap.width == expectedW && rowBitmap.height == pack.frameHeight) {
                rowBitmap
            } else {
                warnings += "$state row normalized from ${rowBitmap.width}x${rowBitmap.height} to ${expectedW}x${pack.frameHeight}."
                android.graphics.Bitmap.createScaledBitmap(rowBitmap, expectedW, pack.frameHeight, false)
            }
            val keyed = removeChromaKeyBackground(normalized)
            canvas.drawBitmap(keyed, 0f, (index * pack.frameHeight).toFloat(), null)
            keyed.recycle()
            if (normalized !== rowBitmap) normalized.recycle()
            rowBitmap.recycle()
        }

        if (missing.isNotEmpty()) {
            sheet.recycle()
            return SkillResult(false, "Missing or invalid rows: ${missing.joinToString(", ")}")
        }

        val dir = File(townStore.assetRoot(), "composed").also { it.mkdirs() }
        val file = File(dir, "${pack.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_${System.currentTimeMillis()}.png")
        file.outputStream().use { out -> sheet.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
        sheet.recycle()

        val updatedPack = pack.copy(imagePath = file.absolutePath)
        val review = reviewSpriteFile(updatedPack, file)
        val output = buildString {
            appendLine("Composed spritesheet: ${file.absolutePath}")
            appendLine("Canvas: ${expectedW}x${expectedH}, rows=${pack.rows}, columns=${pack.columns}")
            if (warnings.isNotEmpty()) appendLine(warnings.joinToString("\n"))
            if (review.isNotEmpty()) appendLine(review.joinToString("\n"))
            appendLine("metadataJson:")
            append(gson.toJson(updatedPack))
        }
        return SkillResult(
            success = review.none { it.startsWith("ERROR") },
            output = output,
            data = SkillAttachment.FileData(file.absolutePath, file.name, "image/png", file.length()),
        )
    }

    private fun previewSpritePack(pack: AgentSpritePack, imageDataUri: String): SkillResult {
        val states = listOf("idle", "waving", "waiting", "review", "failed")
            .filter { pack.states[it] != null }
            .ifEmpty { pack.states.keys.take(5) }
        val expectedW = pack.frameWidth * pack.columns
        val expectedH = pack.frameHeight * pack.rows
        val cssBlocks = states.joinToString("\n") { state ->
            val anim = pack.states.getValue(state)
            val endX = -(pack.frameWidth * (anim.startColumn + anim.frames - 1))
            """
            .sprite-$state {
              background-position: ${-pack.frameWidth * anim.startColumn}px ${-pack.frameHeight * anim.row}px;
              animation: play-$state ${anim.durationMs}ms steps(${anim.frames}) infinite;
            }
            @keyframes play-$state {
              from { background-position: ${-pack.frameWidth * anim.startColumn}px ${-pack.frameHeight * anim.row}px; }
              to { background-position: ${endX - pack.frameWidth}px ${-pack.frameHeight * anim.row}px; }
            }
            """.trimIndent()
        }
        val cards = states.joinToString("\n") { state ->
            """
            <section class="card">
              <div class="sprite sprite-$state"></div>
              <div class="label">$state</div>
            </section>
            """.trimIndent()
        }
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>${pack.name} Preview</title>
              <style>
                body { margin:0; padding:24px; background:#111; color:#f6f6f6; font-family:system-ui,sans-serif; }
                h1 { font-size:22px; margin:0 0 6px; }
                .meta { color:#aaa; margin-bottom:20px; font-size:13px; }
                .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:16px; }
                .card { background:#1b1b1b; border:1px solid #333; border-radius:14px; padding:16px; display:flex; flex-direction:column; align-items:center; gap:10px; }
                .sprite {
                  width:${pack.frameWidth}px;
                  height:${pack.frameHeight}px;
                  image-rendering:pixelated;
                  background-image:url('$imageDataUri');
                  background-repeat:no-repeat;
                  background-size:${expectedW}px ${expectedH}px;
                }
                .label { font-size:13px; color:#ddd; }
                $cssBlocks
              </style>
            </head>
            <body>
              <h1>${pack.name}</h1>
              <div class="meta">${expectedW}x${expectedH}, ${pack.columns}x${pack.rows}, ${pack.frameWidth}x${pack.frameHeight} per frame</div>
              <div class="grid">$cards</div>
            </body>
            </html>
        """.trimIndent()
        val dir = File(townStore.assetRoot(), "previews").also { it.mkdirs() }
        val file = File(dir, "${pack.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_preview.html")
        file.writeText(html, Charsets.UTF_8)
        return SkillResult(
            success = true,
            output = "Sprite preview created: ${file.absolutePath}",
            data = SkillAttachment.HtmlData(file.absolutePath, "${pack.name} Preview", html),
        )
    }

    private fun reviewSpriteImage(pack: AgentSpritePack, imageDataUri: String): List<String> = buildList {
        val bytes = runCatching { decodeDataUri(imageDataUri) }.getOrNull()
        if (bytes == null) {
            add("ERROR: image_data_uri is not valid base64 image data.")
            return@buildList
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            add("ERROR: image_data_uri cannot be decoded as an image.")
            return@buildList
        }
        val expectedW = pack.frameWidth * pack.columns
        val expectedH = pack.frameHeight * pack.rows
        if (bitmap.width != expectedW || bitmap.height != expectedH) {
            add("WARN: image is ${bitmap.width}x${bitmap.height}; runtime will normalize to ${expectedW}x${expectedH}. Best quality comes from generating the exact canvas.")
        }
        if (!bitmap.hasAlpha()) {
            add("WARN: image has no alpha channel. Desktop pet sheets should be transparent PNG/WebP, not a flat background.")
        }
        val scaledRaw = if (bitmap.width == expectedW && bitmap.height == expectedH) bitmap else android.graphics.Bitmap.createScaledBitmap(bitmap, expectedW, expectedH, false)
        val scaled = removeChromaKeyBackground(scaledRaw)
        var score = 100
        val fullPixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(fullPixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val transparentPixels = fullPixels.count { (it ushr 24) <= 24 }
        val transparentRatio = transparentPixels.toFloat() / fullPixels.size.toFloat()
        if (transparentRatio < 0.18f) {
            add("WARN: transparent area is only ${(transparentRatio * 100).toInt()}%. Desktop pets usually need a transparent background; this may be a poster-like image.")
            score -= 18
        }
        var emptyFrames = 0
        var clippedFrames = 0
        val areas = mutableListOf<Int>()
        val centers = mutableListOf<Pair<Float, Float>>()
        for (row in 0 until pack.rows) {
            for (col in 0 until pack.columns) {
                val x0 = col * pack.frameWidth
                val y0 = row * pack.frameHeight
                val bounds = nonTransparentBounds(scaled, x0, y0, pack.frameWidth, pack.frameHeight)
                if (bounds == null) {
                    emptyFrames++
                    continue
                }
                val (left, top, right, bottom) = bounds
                areas += (right - left + 1) * (bottom - top + 1)
                centers += ((left + right) / 2f) to ((top + bottom) / 2f)
                val margin = 4
                if (left <= margin || top <= margin || right >= pack.frameWidth - 1 - margin || bottom >= pack.frameHeight - 1 - margin) {
                    clippedFrames++
                }
            }
        }
        if (emptyFrames > 0) {
            add("ERROR: $emptyFrames empty frames detected.")
            score -= emptyFrames * 8
        }
        if (clippedFrames > 0) {
            add("WARN: $clippedFrames frames touch the cell edge; character may be cropped.")
            score -= (clippedFrames * 2).coerceAtMost(24)
        }
        if (areas.isNotEmpty()) {
            val min = areas.minOrNull() ?: 0
            val max = areas.maxOrNull() ?: 0
            if (min > 0 && max > min * 4) {
                add("WARN: frame occupied area varies too much ($min..$max). This often means identity drift or bad grid alignment.")
                score -= 18
            }
        }
        if (centers.size > 1) {
            val avgX = centers.map { it.first }.average().toFloat()
            val avgY = centers.map { it.second }.average().toFloat()
            val maxDrift = centers.maxOf { kotlin.math.abs(it.first - avgX) + kotlin.math.abs(it.second - avgY) }
            if (maxDrift > pack.frameWidth * 0.42f) {
                add("WARN: character center drifts too much across frames (${maxDrift.toInt()}px). Animation may look jumpy or inconsistent.")
                score -= 16
            }
        }
        add("QUALITY_SCORE: ${score.coerceIn(0, 100)}/100")
        scaled.recycle()
        if (scaledRaw !== bitmap) scaledRaw.recycle()
        bitmap.recycle()
    }

    private fun removeChromaKeyBackground(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val out = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in pixels.indices) {
            val color = pixels[i]
            pixels[i] = if (isChromaKeyGreen(color)) Color.TRANSPARENT else color
        }
        out.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private fun isChromaKeyGreen(color: Int): Boolean {
        val alpha = color ushr 24
        if (alpha <= 24) return true
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        return green >= 190 && red <= 90 && blue <= 90 && green - red >= 120 && green - blue >= 120
    }

    private fun reviewSpriteFile(pack: AgentSpritePack, file: File): List<String> {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return listOf("ERROR: composed spritesheet cannot be decoded.")
        val bytes = runCatching {
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
        bitmap.recycle()
        return bytes?.let { reviewSpriteImage(pack, it) } ?: listOf("ERROR: composed spritesheet cannot be reviewed.")
    }

    private fun nonTransparentBounds(bitmap: android.graphics.Bitmap, x0: Int, y0: Int, w: Int, h: Int): IntArray? {
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x0, y0, w, h)
        pixels.forEachIndexed { index, color ->
            val alpha = color ushr 24
            if (alpha > 24) {
                val x = index % w
                val y = index / w
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < left || bottom < top) null else intArrayOf(left, top, right, bottom)
    }

    private fun decodeDataUri(dataUri: String): ByteArray {
        val marker = "base64,"
        val index = dataUri.indexOf(marker)
        val b64 = if (index >= 0) dataUri.substring(index + marker.length) else dataUri
        return Base64.decode(b64, Base64.DEFAULT)
    }

    private fun fileToDataUri(file: File): String? = runCatching {
        if (!file.exists() || !file.isFile) return@runCatching null
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        "data:$mime;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
    }.getOrNull()
}
