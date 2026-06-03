package com.mobileclaw.skill.builtin

import com.google.gson.GsonBuilder
import com.mobileclaw.agent.RoleManager
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.town.AgentTownStore
import com.mobileclaw.town.RoomAsset
import com.mobileclaw.town.RoomLayout

class AiHomeAssetSkill(
    private val townStore: AgentTownStore,
    private val roleManager: RoleManager,
) : Skill {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    override val meta = SkillMeta(
        id = "ai_home_assets",
        name = "AI Home Asset Catalog",
        nameZh = "AI Home 素材库",
        description = "Search and inspect RPG room image assets for AI Home decoration. " +
            "Use this before town_builder when a role wants to decorate its room with real image resources. " +
            "Actions: list_packs, list_assets, search_assets, get_asset, recommend_room_assets, usage_guide. " +
            "Return asset_id/path/tile size/layer/orientation, then place selected assets through town_builder.place_furniture.",
        descriptionZh = "查询 AI Home 的 RPG 房间素材资源。角色装修房间时先用它找床、桌子、墙体、门窗、摆饰等图片素材，再用 town_builder 写入房间结构。",
        parameters = listOf(
            SkillParam("action", "string", "list_packs | list_assets | search_assets | get_asset | recommend_room_assets | room_schema | validate_layout | usage_guide"),
            SkillParam("query", "string", "Free-text search, e.g. bed, workbench, north wall, cozy, terminal, library.", required = false),
            SkillParam("asset_id", "string", "Exact asset id for get_asset.", required = false),
            SkillParam("category", "string", "floor | structure | furniture | decor.", required = false),
            SkillParam("type", "string", "bed | bench | desk | chair | bookcase | wall | door | window | floor | lamp | plant | crate | terminal.", required = false),
            SkillParam("orientation", "string", "north | south | east | west | side | any.", required = false),
            SkillParam("role_id", "string", "Role id used by recommend_room_assets.", required = false),
            SkillParam("layout_json", "string", "RoomLayout JSON for validate_layout.", required = false),
            SkillParam("limit", "number", "Maximum assets returned. Defaults to 12.", required = false),
        ),
        injectionLevel = 1,
        internalTool = true,
        categories = listOf(SkillToolCategory.SELF_EVOLUTION, SkillToolCategory.ARTIFACT, SkillToolCategory.MEDIA),
        tags = listOf("home", "room", "rpg", "pixel", "asset", "tileset", "furniture", "房间", "素材", "装修"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: return SkillResult(false, "action is required")
        val limit = ((params["limit"] as? Number)?.toInt() ?: 12).coerceIn(1, 50)
        return when (action) {
            "list_packs" -> SkillResult(true, gson.toJson(townStore.roomAssetPacks().map { pack ->
                mapOf(
                    "id" to pack.id,
                    "name" to pack.name,
                    "version" to pack.version,
                    "tile_size" to pack.tileSize,
                    "style" to pack.style,
                    "sheet" to pack.sheet,
                    "asset_count" to pack.assets.size,
                )
            }))
            "list_assets" -> {
                val filtered = townStore.roomAssets()
                    .filterBy(params)
                    .take(limit)
                    .map { it.toAiPayload() }
                SkillResult(true, gson.toJson(filtered))
            }
            "search_assets" -> {
                val query = params["query"] as? String ?: ""
                val filtered = townStore.roomAssets()
                    .filterBy(params)
                    .rankedBy(query)
                    .take(limit)
                    .map { it.toAiPayload() }
                SkillResult(true, gson.toJson(filtered))
            }
            "get_asset" -> {
                val id = params["asset_id"] as? String ?: params["id"] as? String ?: return SkillResult(false, "asset_id is required")
                val asset = townStore.findRoomAsset(id) ?: return SkillResult(false, "Asset '$id' not found.")
                SkillResult(true, gson.toJson(asset.toAiPayload(includeUsage = true)))
            }
            "recommend_room_assets" -> {
                val roleId = params["role_id"] as? String
                    ?: roleManager.all().firstOrNull()?.id
                    ?: "general"
                val role = roleManager.get(roleId) ?: roleManager.all().firstOrNull { it.id == roleId }
                val recommendations = recommendAssetsForRole(roleId, role?.name.orEmpty(), role?.description.orEmpty(), role?.keywords.orEmpty())
                    .take(limit)
                SkillResult(true, gson.toJson(mapOf(
                    "role_id" to roleId,
                    "intent" to "Use these assets as a starting set, then adjust placements with town_builder.place_furniture.",
                    "assets" to recommendations.map { it.toAiPayload(includeUsage = true) },
                    "placement_notes" to listOf(
                        "Use asset_id exactly as returned.",
                        "Mirror asset_id into variant when supporting older room renderers.",
                        "Use tileWidth/tileHeight as furniture width/height.",
                        "Use layer and orientation to avoid placing wall assets on the floor layer.",
                    ),
                )))
            }
            "room_schema" -> SkillResult(true, gson.toJson(roomSchema()))
            "validate_layout" -> {
                val json = params["layout_json"] as? String ?: return SkillResult(false, "layout_json is required")
                val layout = runCatching { gson.fromJson(json, RoomLayout::class.java) }.getOrNull()
                    ?: return SkillResult(false, "layout_json is not valid RoomLayout JSON")
                SkillResult(true, gson.toJson(validateLayout(layout)))
            }
            "usage_guide" -> SkillResult(true, gson.toJson(usageGuide()))
            else -> SkillResult(false, "Unknown action '$action'.")
        }
    }

    private fun List<RoomAsset>.filterBy(params: Map<String, Any>): List<RoomAsset> {
        val category = (params["category"] as? String)?.lowercase()?.takeIf { it.isNotBlank() }
        val type = (params["type"] as? String)?.lowercase()?.takeIf { it.isNotBlank() }
        val orientation = (params["orientation"] as? String)?.lowercase()?.takeIf { it.isNotBlank() }
        return filter { asset ->
            (category == null || asset.category.lowercase() == category) &&
                (type == null || asset.type.lowercase() == type) &&
                (orientation == null || asset.orientation.lowercase() == orientation || asset.orientation == "any")
        }
    }

    private fun List<RoomAsset>.rankedBy(query: String): List<RoomAsset> {
        val terms = query.lowercase().split(Regex("\\s+|,|，")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return this
        return map { asset -> asset to asset.score(terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun RoomAsset.score(terms: List<String>): Int {
        val haystack = listOf(id, name, category, type, orientation, description, tags.joinToString(" ")).joinToString(" ").lowercase()
        return terms.sumOf { term ->
            when {
                id.lowercase().contains(term) -> 8
                type.lowercase().contains(term) -> 6
                tags.any { it.lowercase().contains(term) } -> 5
                haystack.contains(term) -> 3
                else -> 0
            }
        }
    }

    private fun recommendAssetsForRole(roleId: String, name: String, description: String, keywords: List<String>): List<RoomAsset> {
        val text = listOf(roleId, name, description, keywords.joinToString(" ")).joinToString(" ").lowercase()
        val preferredTypes = when {
            text.containsAny("code", "coder", "bug", "terminal", "代码", "编程", "调试") -> listOf("terminal", "desk", "chair", "cable", "lamp")
            text.containsAny("image", "design", "paint", "creative", "图像", "绘画", "设计", "创意") -> listOf("bench", "crate", "art", "plant", "lamp")
            text.containsAny("web", "search", "research", "资料", "搜索", "研究") -> listOf("bookcase", "desk", "chair", "art", "lamp")
            text.containsAny("phone", "android", "手机", "无障碍", "操作") -> listOf("terminal", "cable", "desk", "crate", "sign")
            text.containsAny("skill", "tool", "工具", "技能") -> listOf("crate", "bench", "bookcase", "sign", "lamp")
            else -> listOf("bed", "desk", "chair", "plant", "lamp", "art")
        }
        val base = townStore.roomAssets()
        val essentials = listOf("floor", "wall", "door", "window")
        return (base.filter { it.type in essentials || it.type in preferredTypes } + base.filter { it.category == "decor" })
            .distinctBy { it.id }
    }

    private fun RoomAsset.toAiPayload(includeUsage: Boolean = false): Map<String, Any> {
        val payload = linkedMapOf<String, Any>(
            "asset_id" to id,
            "name" to name,
            "category" to category,
            "type" to type,
            "path" to path,
            "android_asset_uri" to androidAssetUri,
            "tile_width" to tileWidth,
            "tile_height" to tileHeight,
            "pixel_width" to pixelWidth,
            "pixel_height" to pixelHeight,
            "layer" to layer,
            "orientation" to orientation,
            "anchor" to anchor,
            "tags" to tags,
            "description" to description,
        )
        if (includeUsage) {
            payload["town_builder_place_furniture"] = mapOf(
                "action" to "place_furniture",
                "type" to type,
                "width" to tileWidth,
                "height" to tileHeight,
                "layer_name" to layer,
                "asset_id" to id,
                "variant" to id,
            )
        }
        return payload
    }

    private fun usageGuide(): Map<String, Any> = mapOf(
        "purpose" to "AI Home room decoration uses image assets, not vague text-only furniture names.",
        "recommended_flow" to listOf(
            "Call ai_home_assets.search_assets or recommend_room_assets.",
            "Pick assets that match role identity and room direction.",
            "Call ai_home_assets.room_schema before planning a full room.",
            "Generate a structured RoomLayout with zones and objects, not a loose list of stickers.",
            "Call ai_home_assets.validate_layout before writing room changes.",
            "Call town_builder.place_furniture with asset_id, variant=asset_id, width, height, layer_name, x, y.",
            "For walls/floors/doors/windows, keep orientation meaningful so renderers can place them correctly.",
        ),
        "coordinates" to "Room grid is 20x20. Assets report tile_width/tile_height in the same unit.",
        "perspective" to "Use Pokemon-like 3/4 top-down: floor is a grid, walls occupy rear/side layers, furniture faces south unless side-wall mounted.",
        "renderer" to "Use nearest-neighbor/integer scaling. Do not blur or JPEG-compress pixel art assets.",
    )

    private fun roomSchema(): Map<String, Any> = mapOf(
        "perspective" to mapOf(
            "camera" to "pokemon_3_4_top_down",
            "x" to "left to right grid coordinate",
            "y" to "top to bottom grid coordinate; higher y renders in front",
            "layers" to listOf("floor", "wall", "object", "character", "overlay"),
        ),
        "layout_json_shape" to mapOf(
            "width" to 12,
            "height" to 10,
            "floorAssetId" to "floor_wood_center",
            "defaultWallAssetId" to "wall_back_plaster_center",
            "door" to mapOf("side" to "south", "x" to 5, "y" to 9, "assetId" to "door_wood_south"),
            "zones" to listOf(
                mapOf("id" to "sleep", "purpose" to "bed/rest", "x" to 1, "y" to 5, "width" to 4, "height" to 4),
                mapOf("id" to "work", "purpose" to "desk/tools", "x" to 6, "y" to 4, "width" to 5, "height" to 4),
                mapOf("id" to "memory", "purpose" to "wall memories", "x" to 1, "y" to 1, "width" to 10, "height" to 3),
            ),
            "objects" to listOf(
                mapOf("id" to "bed", "assetId" to "furniture_bed_single_blue", "type" to "bed", "x" to 1, "y" to 6, "width" to 3, "height" to 3, "layer" to "object", "facing" to "south", "zoneId" to "sleep"),
                mapOf("id" to "desk", "assetId" to "furniture_desk_writer", "type" to "desk", "x" to 7, "y" to 6, "width" to 3, "height" to 2, "layer" to "object", "facing" to "south", "zoneId" to "work"),
            ),
        ),
        "hard_rules" to listOf(
            "Use floor/rug tiles to build surfaces before placing furniture.",
            "Use wall/back/side/corner/baseboard tiles to build room structure.",
            "Doors and windows attach to wall layers, not random floor cells.",
            "Furniture must occupy clear object cells and should not overlap.",
            "Keep one path from the south door to the main work zone.",
            "Use y-depth sorting for object rendering; bigger y draws in front.",
        ),
    )

    private fun validateLayout(layout: RoomLayout): Map<String, Any> {
        val assets = townStore.roomAssets().associateBy { it.id }
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val width = layout.width.coerceIn(8, 24)
        val height = layout.height.coerceIn(6, 20)
        if (layout.width != width || layout.height != height) issues += "Room size must be width 8..24 and height 6..20."
        if (assets[layout.floorAssetId] == null) issues += "Unknown floorAssetId '${layout.floorAssetId}'."
        if (assets[layout.defaultWallAssetId] == null) issues += "Unknown defaultWallAssetId '${layout.defaultWallAssetId}'."
        if (assets[layout.door.assetId] == null) issues += "Unknown door asset '${layout.door.assetId}'."

        val occupied = mutableMapOf<Pair<Int, Int>, String>()
        val objects = layout.objects.orEmpty()
        objects.forEach { obj ->
            val asset = assets[obj.assetId]
            if (asset == null) {
                issues += "Object '${obj.id}' references unknown asset '${obj.assetId}'."
                return@forEach
            }
            if (obj.x < 0 || obj.y < 0 || obj.x + obj.width > width || obj.y + obj.height > height) {
                issues += "Object '${obj.id}' is outside room bounds."
            }
            if (asset.layer == "wall" && obj.layer != "wall") {
                warnings += "Object '${obj.id}' uses wall asset '${obj.assetId}' but layer is '${obj.layer}'."
            }
            if (asset.category.contains("floor") && obj.layer != "floor") {
                warnings += "Object '${obj.id}' uses floor asset '${obj.assetId}' but layer is '${obj.layer}'."
            }
            if (obj.layer == "object") {
                for (x in obj.x until (obj.x + obj.width).coerceAtMost(width)) {
                    for (y in obj.y until (obj.y + obj.height).coerceAtMost(height)) {
                        val key = x to y
                        val previous = occupied[key]
                        if (previous != null) issues += "Object '${obj.id}' overlaps '$previous' at ($x,$y)."
                        occupied[key] = obj.id
                    }
                }
            }
        }
        val doorCell = layout.door.x.coerceIn(0, width - 1) to layout.door.y.coerceIn(0, height - 1)
        if (occupied[doorCell] != null) issues += "Door cell $doorCell is blocked by '${occupied[doorCell]}'."
        if (layout.zones.orEmpty().isEmpty()) warnings += "Room has no functional zones. Add sleep/work/memory/display zones so it reads like a real home."
        if (objects.none { it.type.contains("bed", ignoreCase = true) }) warnings += "No bed/rest object found."
        if (objects.none { it.type.contains("desk", ignoreCase = true) || it.type.contains("bench", ignoreCase = true) }) warnings += "No work surface found."

        return mapOf(
            "valid" to issues.isEmpty(),
            "issues" to issues.distinct(),
            "warnings" to warnings.distinct(),
            "renderer_notes" to listOf(
                "Render floor first, then wall, then object sorted by y, then character.",
                "Do not render layout objects as free-floating stickers; snap every object to x/y grid cells.",
            ),
        )
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it) }
}
