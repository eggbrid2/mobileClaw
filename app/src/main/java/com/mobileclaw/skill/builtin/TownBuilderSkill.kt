package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.town.AgentTownStore
import com.mobileclaw.town.RoomArtifact
import com.mobileclaw.town.RoomFurniture
import com.mobileclaw.town.RoomTool
import com.mobileclaw.town.TownMapDocument
import com.mobileclaw.town.TownMapLayer
import com.mobileclaw.town.TownMapSprite

class TownBuilderSkill(
    private val townStore: AgentTownStore,
    private val roleManager: RoleManager,
) : Skill {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override val meta = SkillMeta(
        id = "town_builder",
        name = "AI Home Builder",
        nameZh = "AI Home 构建器",
        description = "Read and evolve an AI role's RPG home. Treat this as the dedicated Home channel, not a chat response. " +
            "Use it when a role wants to decorate its house, pin memories, showcase MiniAPP/AI Page/file/image artifacts, expose favorite tools, update mood lines, or inspect its home. " +
            "Actions: get_town, get_room, get_map, plan_room_layout, update_map_theme, replace_map, patch_tile, place_sprite, update_room, decorate_room, place_furniture, remove_furniture, pin_memory, pin_artifact, pin_skill, reset_room. " +
            "For role home work, call this tool to update structured room/map data instead of describing visual changes in chat. " +
            "Typical flow: get_room -> plan_room_layout -> update_room/decorate_room -> place_furniture -> pin_memory/pin_artifact/pin_skill.",
        descriptionZh = "读取并演化 AI 角色的 RPG Home。角色可以用它布置自己的房间、钉住记忆、展示 MiniAPP/AI 页面/文件/图片作品、公开常用技能、更新心情台词，或查看自己的 Home。",
        parameters = listOf(
            SkillParam("action", "string", "get_town | get_room | get_map | plan_room_layout | update_map_theme | replace_map | patch_tile | place_sprite | update_room | decorate_room | place_furniture | remove_furniture | pin_memory | pin_artifact | pin_skill | reset_room"),
            SkillParam("role_id", "string", "Target role id. Defaults to current/known role when possible.", required = false),
            SkillParam("house_name", "string", "Room/house display name.", required = false),
            SkillParam("style", "string", "Short room style, e.g. pixel workshop, quiet lab.", required = false),
            SkillParam("house_sprite", "string", "studio | cabin | shop | workshop | tower | terminal | library | warehouse | bunker.", required = false),
            SkillParam("accent", "string", "Accent color hex such as #C7F43A.", required = false),
            SkillParam("door_sign", "string", "Short sign displayed on the door.", required = false),
            SkillParam("motto", "string", "Role motto displayed in the room.", required = false),
            SkillParam("mood", "string", "idle | working | playful | focused | sleepy | custom.", required = false),
            SkillParam("idle_line", "string", "Line shown when the role is idle.", required = false),
            SkillParam("working_line", "string", "Line shown when the role is working.", required = false),
            SkillParam("title", "string", "Pin/artifact/tool title.", required = false),
            SkillParam("body", "string", "Pin body or artifact subtitle.", required = false),
            SkillParam("id", "string", "Artifact/tool id.", required = false),
            SkillParam("type", "string", "Artifact/pin type.", required = false),
            SkillParam("width", "number", "Furniture width in room-grid cells.", required = false),
            SkillParam("height", "number", "Furniture height in room-grid cells.", required = false),
            SkillParam("layer_name", "string", "Furniture layer: back | front.", required = false),
            SkillParam("variant", "string", "Furniture visual variant.", required = false),
            SkillParam("color", "string", "Furniture accent color hex.", required = false),
            SkillParam("map_json", "string", "Complete TownMapDocument JSON for replace_map.", required = false),
            SkillParam("layer", "string", "Map layer name for patch_tile, usually ground or objects.", required = false),
            SkillParam("x", "number", "Tile x coordinate.", required = false),
            SkillParam("y", "number", "Tile y coordinate.", required = false),
            SkillParam("tile", "string", "Single-character tile id: . grass, , grass detail, p path, w water, m wall, t tree top, T trunk, f flower, F fountain, s stairs.", required = false),
        ),
        injectionLevel = 1,
        internalTool = true,
        categories = listOf(SkillToolCategory.SELF_EVOLUTION, SkillToolCategory.MEMORY, SkillToolCategory.ARTIFACT),
        tags = listOf("home", "room", "role", "memory", "decor", "furniture", "artifact", "角色", "房间", "装修"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        townStore.ensureRooms(roleManager.all())
        val action = params["action"] as? String ?: return SkillResult(false, "action is required")
        val roleId = (params["role_id"] as? String)
            ?: roleManager.all().firstOrNull()?.id
            ?: "general"

        return when (action) {
            "get_town" -> SkillResult(true, gson.toJson(townStore.state.value))
            "get_map" -> SkillResult(true, gson.toJson(townStore.state.value.map))
            "update_map_theme" -> {
                val theme = params["theme"] as? String ?: params["style"] as? String ?: return SkillResult(false, "theme or style is required")
                val map = townStore.updateMap { it.copy(theme = theme) }
                SkillResult(true, "Updated town map theme.\n${gson.toJson(map)}")
            }
            "replace_map" -> {
                val json = params["map_json"] as? String ?: return SkillResult(false, "map_json is required")
                val parsed = runCatching { gson.fromJson(json, TownMapDocument::class.java) }.getOrNull()
                    ?: return SkillResult(false, "map_json is not a valid TownMapDocument")
                val map = townStore.updateMap { parsed }
                SkillResult(true, "Replaced town map.\n${gson.toJson(map)}")
            }
            "patch_tile" -> {
                val layerName = params["layer"] as? String ?: return SkillResult(false, "layer is required")
                val x = (params["x"] as? Number)?.toInt() ?: return SkillResult(false, "x is required")
                val y = (params["y"] as? Number)?.toInt() ?: return SkillResult(false, "y is required")
                val tile = (params["tile"] as? String)?.firstOrNull() ?: return SkillResult(false, "tile is required")
                val map = townStore.updateMap { current ->
                    val layers = current.layers.map { layer ->
                        if (layer.name != layerName || y !in layer.data.indices) return@map layer
                        val rows = layer.data.toMutableList()
                        val row = rows[y].padEnd(current.width, '.').take(current.width).toCharArray()
                        if (x in row.indices) row[x] = tile
                        rows[y] = String(row)
                        TownMapLayer(layer.name, rows)
                    }
                    current.copy(layers = layers)
                }
                SkillResult(true, "Patched tile ($layerName:$x,$y='$tile').\n${gson.toJson(map)}")
            }
            "place_sprite" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required")
                val x = (params["x"] as? Number)?.toInt() ?: return SkillResult(false, "x is required")
                val y = (params["y"] as? Number)?.toInt() ?: return SkillResult(false, "y is required")
                val sprite = TownMapSprite(
                    id = id,
                    type = params["type"] as? String ?: "decoration",
                    roleId = roleId,
                    x = x,
                    y = y,
                    variant = params["style"] as? String ?: "",
                )
                val map = townStore.updateMap { current ->
                    current.copy(sprites = current.sprites.filterNot { it.id == id } + sprite)
                }
                SkillResult(true, "Placed sprite '$id'.\n${gson.toJson(map)}")
            }
            "get_room" -> {
                val room = townStore.state.value.rooms[roleId]
                    ?: return SkillResult(false, "Room '$roleId' not found.")
                SkillResult(true, gson.toJson(room))
            }
            "plan_room_layout" -> {
                val role = roleManager.get(roleId) ?: roleManager.all().firstOrNull { it.id == roleId }
                val room = townStore.state.value.rooms[roleId]
                    ?: return SkillResult(false, "Room '$roleId' not found.")
                SkillResult(true, gson.toJson(planRoomLayout(roleId, role, room.houseSprite, room.accent)))
            }
            "update_room", "decorate_room" -> {
                val room = townStore.updateRoom(roleId) { current ->
                    current.copy(
                        houseName = (params["house_name"] as? String) ?: current.houseName,
                        style = (params["style"] as? String) ?: current.style,
                        houseSprite = (params["house_sprite"] as? String) ?: current.houseSprite,
                        accent = (params["accent"] as? String) ?: current.accent,
                        doorSign = (params["door_sign"] as? String) ?: current.doorSign,
                        motto = (params["motto"] as? String) ?: current.motto,
                        mood = (params["mood"] as? String) ?: current.mood,
                        idleLine = (params["idle_line"] as? String) ?: current.idleLine,
                        workingLine = (params["working_line"] as? String) ?: current.workingLine,
                    )
                }
                SkillResult(true, "Updated room '$roleId'.\n${gson.toJson(room)}")
            }
            "place_furniture" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required")
                val type = params["type"] as? String ?: return SkillResult(false, "type is required")
                val x = (params["x"] as? Number)?.toInt() ?: return SkillResult(false, "x is required")
                val y = (params["y"] as? Number)?.toInt() ?: return SkillResult(false, "y is required")
                val furniture = RoomFurniture(
                    id = id,
                    type = type,
                    x = x,
                    y = y,
                    width = (params["width"] as? Number)?.toInt() ?: 2,
                    height = (params["height"] as? Number)?.toInt() ?: 2,
                    layer = params["layer_name"] as? String ?: params["layer"] as? String ?: "front",
                    variant = params["variant"] as? String ?: params["style"] as? String ?: "",
                    color = params["color"] as? String ?: "",
                )
                val room = townStore.placeFurniture(roleId, furniture)
                SkillResult(true, "Placed furniture '$id' in '$roleId'.\n${gson.toJson(room)}")
            }
            "remove_furniture" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required")
                val room = townStore.removeFurniture(roleId, id)
                SkillResult(true, "Removed furniture '$id' from '$roleId'.\n${gson.toJson(room)}")
            }
            "pin_memory" -> {
                val title = params["title"] as? String ?: return SkillResult(false, "title is required")
                val room = townStore.pinMemory(
                    roleId = roleId,
                    title = title,
                    body = params["body"] as? String ?: "",
                    source = params["type"] as? String ?: "memory",
                )
                SkillResult(true, "Pinned memory to '$roleId'.\n${gson.toJson(room)}")
            }
            "pin_artifact" -> {
                val title = params["title"] as? String ?: return SkillResult(false, "title is required")
                val id = (params["id"] as? String)?.takeIf { it.isNotBlank() } ?: title.lowercase().replace(Regex("[^a-z0-9]+"), "_")
                val room = townStore.pinArtifact(
                    roleId = roleId,
                    artifact = RoomArtifact(
                        id = id,
                        type = params["type"] as? String ?: "artifact",
                        title = title,
                        subtitle = params["body"] as? String ?: "",
                    ),
                )
                SkillResult(true, "Pinned artifact to '$roleId'.\n${gson.toJson(room)}")
            }
            "pin_skill" -> {
                val id = params["id"] as? String ?: return SkillResult(false, "id is required")
                val room = townStore.pinSkill(
                    roleId = roleId,
                    tool = RoomTool(
                        id = id,
                        title = params["title"] as? String ?: id,
                        category = params["type"] as? String ?: "skill",
                    ),
                )
                SkillResult(true, "Pinned skill to '$roleId'.\n${gson.toJson(room)}")
            }
            "reset_room" -> {
                val room = townStore.resetRoom(roleId, roleManager.get(roleId))
                SkillResult(true, "Reset room '$roleId'.\n${gson.toJson(room)}")
            }
            else -> SkillResult(false, "Unknown action '$action'.")
        }
    }

    private fun planRoomLayout(
        roleId: String,
        role: com.mobileclaw.agent.Role?,
        currentSprite: String,
        currentAccent: String,
    ): Map<String, Any> {
        val text = listOf(
            roleId,
            role?.name.orEmpty(),
            role?.description.orEmpty(),
            role?.systemPromptAddendum.orEmpty(),
            role?.keywords.orEmpty().joinToString(" "),
        ).joinToString(" ").lowercase()
        val sprite = when {
            listOf("code", "coder", "开发", "代码", "编程", "bug", "修复", "工程").any { it in text } -> "terminal"
            listOf("image", "design", "paint", "art", "creative", "图像", "绘画", "设计", "创意").any { it in text } -> "workshop"
            listOf("web", "search", "research", "browser", "网页", "搜索", "研究", "资料").any { it in text } -> "library"
            listOf("phone", "android", "accessibility", "手机", "无障碍", "操作").any { it in text } -> "tower"
            listOf("vpn", "proxy", "network", "线路", "代理", "网络").any { it in text } -> "bunker"
            listOf("skill", "tool", "plugin", "工具", "技能", "插件").any { it in text } -> "warehouse"
            listOf("market", "shop", "store", "商品", "商店", "市场").any { it in text } -> "shop"
            listOf("write", "book", "story", "doc", "写作", "文档", "小说").any { it in text } -> "library"
            else -> currentSprite.ifBlank { "studio" }
        }
        val accent = currentAccent.ifBlank {
            when (sprite) {
                "terminal" -> "#A78BFA"
                "workshop" -> "#F472B6"
                "library" -> "#34D399"
                "tower" -> "#38BDF8"
                "warehouse" -> "#FBBF24"
                "bunker" -> "#60A5FA"
                else -> "#C7F43A"
            }
        }
        val style = when (sprite) {
            "terminal" -> "terminal loft"
            "workshop" -> "maker workshop"
            "library" -> "research library"
            "tower" -> "phone control tower"
            "warehouse" -> "tool archive"
            "bunker" -> "network bunker"
            "shop" -> "artifact shop"
            "cabin" -> "quiet cabin"
            else -> "cozy pixel studio"
        }
        val motto = when (sprite) {
            "terminal" -> "我把问题拆开，再把答案跑通"
            "workshop" -> "灵感先进工坊，再变成作品"
            "library" -> "把线索整理成可靠结论"
            "tower" -> "需要动手机时，我替你跑一趟"
            "warehouse" -> "能力都归位，调用才顺手"
            "bunker" -> "先把通路稳住，再谈速度"
            "shop" -> "把好东西摆出来，让它有用"
            "cabin" -> "我在安静处整理思路"
            else -> "${role?.name?.ifBlank { roleId } ?: roleId} 的房间会随使用生长"
        }
        val furniture = when (sprite) {
            "terminal" -> listOf(
                RoomFurniture("terminal_wall", "terminal", 11, 2, 6, 3, "back", "triple", accent),
                RoomFurniture("code_console", "console", 14, 11, 3, 5, "front", "server", accent),
                RoomFurniture("data_cable", "cable", 6, 13, 8, 1, "front", "signal", accent),
                RoomFurniture("identity_token", "display", 7, 15, 5, 1, "front", roleId, accent),
            )
            "workshop" -> listOf(
                RoomFurniture("maker_board", "art", 13, 2, 4, 3, "back", "blueprint", accent),
                RoomFurniture("workbench", "bench", 10, 12, 5, 2, "front", "maker", accent),
                RoomFurniture("parts_bin", "crate", 15, 14, 2, 2, "front", "parts", accent),
                RoomFurniture("identity_token", "display", 7, 15, 5, 1, "front", roleId, accent),
            )
            "library" -> listOf(
                RoomFurniture("book_wall", "bookcase", 11, 1, 7, 5, "back", "archive", accent),
                RoomFurniture("reading_chair", "chair", 3, 12, 3, 3, "front", "soft", accent),
                RoomFurniture("note_board", "art", 2, 2, 3, 2, "back", "notes", accent),
                RoomFurniture("identity_token", "sign", 7, 15, 5, 1, "front", roleId, accent),
            )
            "tower" -> listOf(
                RoomFurniture("signal_panel", "terminal", 12, 2, 5, 3, "back", "signal", accent),
                RoomFurniture("phone_stand", "console", 3, 12, 3, 4, "front", "phone", accent),
                RoomFurniture("signal_cable", "cable", 2, 13, 8, 1, "front", "line", accent),
                RoomFurniture("identity_token", "display", 7, 15, 5, 1, "front", roleId, accent),
            )
            "warehouse" -> listOf(
                RoomFurniture("archive_wall", "shelf", 12, 2, 5, 4, "back", "archive", accent),
                RoomFurniture("storage_crates", "crate", 12, 12, 5, 3, "front", "stack", accent),
                RoomFurniture("tool_table", "bench", 4, 11, 4, 2, "front", "tools", accent),
                RoomFurniture("identity_token", "sign", 7, 15, 5, 1, "front", roleId, accent),
            )
            "bunker" -> listOf(
                RoomFurniture("network_rack", "terminal", 13, 8, 4, 7, "front", "rack", accent),
                RoomFurniture("secure_line", "cable", 4, 13, 8, 1, "front", "secure", accent),
                RoomFurniture("status_lamp", "lamp", 15, 8, 2, 3, "back", "status", accent),
                RoomFurniture("identity_token", "display", 7, 15, 5, 1, "front", roleId, accent),
            )
            else -> listOf(
                RoomFurniture("home_bed", "bed", 12, 11, 5, 4, "front", "rest", accent),
                RoomFurniture("memory_wall", "art", 12, 2, 4, 3, "back", "memory", accent),
                RoomFurniture("small_plant", "plant", 3, 13, 2, 3, "front", "alive", accent),
                RoomFurniture("identity_token", "sign", 7, 15, 5, 1, "front", roleId, accent),
            )
        }
        return mapOf(
            "role_id" to roleId,
            "house_sprite" to sprite,
            "style" to style,
            "accent" to accent,
            "motto" to motto,
            "idle_line" to "我在房间里整理今天的工具。",
            "working_line" to "我正在处理一个任务，房间灯亮着。",
            "furniture" to furniture,
            "apply_flow" to listOf("decorate_room", "place_furniture for each furniture item"),
        )
    }
}
