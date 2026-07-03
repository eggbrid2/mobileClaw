package com.mobileclaw.town

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleAvatarDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AgentTownStore(private val context: Context) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val townDir: File get() = context.filesDir.resolve("agent_town").also { it.mkdirs() }
    private val assetDir: File get() = townDir.resolve("assets").also { it.mkdirs() }
    private val stateFile: File get() = townDir.resolve("town.json")

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AgentTownState> = _state.asStateFlow()

    fun assetRoot(): File = assetDir

    fun roomAssetPacks(): List<RoomAssetPack> =
        runCatching {
            context.assets.list("ai_home_assets").orEmpty()
                .mapNotNull { packId ->
                    runCatching {
                        context.assets.open("ai_home_assets/$packId/manifest.json").bufferedReader(Charsets.UTF_8).use { reader ->
                            gson.fromJson(reader, RoomAssetPack::class.java)
                        }
                    }.getOrNull()
                }
        }.getOrDefault(emptyList())

    fun roomAssets(): List<RoomAsset> =
        roomAssetPacks().flatMap { pack -> pack.assets.map { it.normalizedAsset() } }

    fun findRoomAsset(assetId: String): RoomAsset? =
        roomAssets().firstOrNull { it.id == assetId }

    fun ensureRooms(roles: List<Role>) {
        val current = _state.value
        val nextRooms = current.rooms.toMutableMap()
        var changed = false
        roles.forEachIndexed { index, role ->
            if (nextRooms[role.id] == null) {
                nextRooms[role.id] = defaultRoom(role, index)
                changed = true
            } else {
                val migrated = migrateLegacyRoom(nextRooms.getValue(role.id), role)
                if (migrated != nextRooms[role.id]) {
                    nextRooms[role.id] = migrated
                    changed = true
                }
            }
        }
        val validIds = roles.map { it.id }.toSet()
        val removed = nextRooms.keys.filterNot { it in validIds }
        removed.forEach {
            nextRooms.remove(it)
            changed = true
        }
        val nextName = if (current.townName == "MobileClaw Town") "MobileClaw Roles" else current.townName
        if (changed || nextName != current.townName) save(current.copy(townName = nextName, rooms = nextRooms, updatedAt = System.currentTimeMillis()))
    }

    fun updateTownName(name: String) {
        if (name.isBlank()) return
        save(_state.value.copy(townName = name.take(40), updatedAt = System.currentTimeMillis()))
    }

    fun updateRoom(roleId: String, transform: (AgentRoom) -> AgentRoom): AgentRoom {
        val current = _state.value
        val existing = current.rooms[roleId] ?: defaultRoomForId(roleId)
        val updated = transform(existing).normalized()
        save(current.copy(rooms = current.rooms + (roleId to updated), updatedAt = System.currentTimeMillis()))
        return updated
    }

    fun updateMap(transform: (TownMapDocument) -> TownMapDocument): TownMapDocument {
        val current = _state.value
        val updated = transform(current.map).normalized()
        save(current.copy(map = updated, updatedAt = System.currentTimeMillis()))
        return updated
    }

    fun pinMemory(roleId: String, title: String, body: String = "", source: String = "memory"): AgentRoom =
        updateRoom(roleId) { room ->
            val pin = RoomPin(
                id = stableId("memory", title),
                type = "memory",
                title = title.take(60),
                body = body.take(160),
                source = source.take(80),
            )
            room.copy(wallPins = upsert(room.wallPins, pin, RoomPin::id).takeLast(10))
        }

    fun pinArtifact(roleId: String, artifact: RoomArtifact, toDesk: Boolean = true): AgentRoom =
        updateRoom(roleId) { room ->
            val clean = artifact.copy(
                id = artifact.id.take(80),
                type = artifact.type.take(24),
                title = artifact.title.take(60),
                subtitle = artifact.subtitle.take(100),
            )
            if (toDesk) {
                room.copy(
                    deskItems = upsert(room.deskItems, clean, RoomArtifact::id).takeLast(8),
                    showcase = upsert(room.showcase, clean, RoomArtifact::id).takeLast(12),
                )
            } else {
                room.copy(showcase = upsert(room.showcase, clean, RoomArtifact::id).takeLast(12))
            }
        }

    fun pinSkill(roleId: String, tool: RoomTool): AgentRoom =
        updateRoom(roleId) { room ->
            val clean = tool.copy(
                id = tool.id.take(80),
                title = tool.title.take(60),
                category = tool.category.take(40),
            )
            room.copy(toolbox = upsert(room.toolbox, clean, RoomTool::id).takeLast(10))
        }

    fun placeFurniture(roleId: String, furniture: RoomFurniture): AgentRoom =
        updateRoom(roleId) { room ->
            val clean = furniture.normalizedFurniture()
            room.copy(furniture = upsert(room.furniture, clean, RoomFurniture::id).takeLast(24))
        }

    fun removeFurniture(roleId: String, furnitureId: String): AgentRoom =
        updateRoom(roleId) { room ->
            room.copy(furniture = room.furniture.filterNot { it.id == furnitureId }.takeLast(24))
        }

    fun resetRoom(roleId: String, role: Role? = null): AgentRoom =
        updateRoom(roleId) { defaultRoom(role ?: Role.DEFAULT.copy(id = roleId, name = roleId), 0) }

    private fun save(state: AgentTownState) {
        townDir.mkdirs()
        stateFile.writeText(gson.toJson(state), Charsets.UTF_8)
        _state.value = state
    }

    private fun load(): AgentTownState =
        runCatching {
            if (stateFile.exists()) {
                gson.fromJson(stateFile.readText(Charsets.UTF_8), AgentTownState::class.java)
                    ?: AgentTownState()
            } else {
                AgentTownState()
            }
        }.getOrDefault(AgentTownState())

    private fun defaultRoom(role: Role, index: Int): AgentRoom {
        val inferredSprite = inferHomeSprite(role, index)
        val style = when (role.id) {
            "creator" -> "neon pixel workshop"
            "phone_operator" -> "control tower"
            "coder" -> "terminal loft"
            "web_agent" -> "research kiosk"
            "skill_admin" -> "tool archive"
            "vpn_operator" -> "network bunker"
            else -> when (inferredSprite) {
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
        }
        val sprite = when (role.id) {
            "creator" -> "workshop"
            "phone_operator" -> "tower"
            "coder" -> "terminal"
            "web_agent" -> "library"
            "skill_admin" -> "warehouse"
            "vpn_operator" -> "bunker"
            else -> inferredSprite
        }
        val accent = when (role.avatar) {
            RoleAvatarDefaults.CREATOR -> "#F472B6"
            RoleAvatarDefaults.PHONE -> "#38BDF8"
            RoleAvatarDefaults.CODER -> "#A78BFA"
            RoleAvatarDefaults.WEB -> "#34D399"
            RoleAvatarDefaults.SKILL -> "#FBBF24"
            RoleAvatarDefaults.VPN -> "#60A5FA"
            else -> "#C7F43A"
        }
        return AgentRoom(
            roleId = role.id,
            houseName = "${role.name} 的家",
            style = style,
            houseSprite = sprite,
            accent = accent,
            doorSign = role.description.take(42),
            motto = when (role.id) {
                "creator" -> "把想法做成能玩的东西"
                "phone_operator" -> "我负责去手机里跑一趟"
                "coder" -> "问题先复现，再修掉"
                "web_agent" -> "先查证，再回答"
                "skill_admin" -> "把能力整理成工具"
                "vpn_operator" -> "线路通了，世界就近了"
                else -> inferHomeMotto(role, sprite)
            },
            idleLine = "我在房间里整理今天的工具。",
            workingLine = "我正在处理一个任务，房间灯亮着。",
            toolbox = role.forcedSkillIds.take(6).map { RoomTool(it, it, "forced") },
            furniture = defaultFurniture(role, sprite),
            notes = listOf("这个房间会随着我的记忆、作品和技能继续生长。"),
        ).normalized()
    }

    private fun inferHomeSprite(role: Role, index: Int): String {
        val text = listOf(role.id, role.name, role.description, role.systemPromptAddendum, role.keywords.joinToString(" ")).joinToString(" ").lowercase()
        return when {
            listOf("code", "coder", "开发", "代码", "编程", "bug", "修复", "工程").any { it in text } -> "terminal"
            listOf("image", "design", "paint", "art", "creative", "图像", "绘画", "设计", "创意").any { it in text } -> "workshop"
            listOf("web", "search", "research", "browser", "网页", "搜索", "研究", "资料").any { it in text } -> "library"
            listOf("phone", "android", "accessibility", "手机", "无障碍", "操作").any { it in text } -> "tower"
            listOf("vpn", "proxy", "network", "线路", "代理", "网络").any { it in text } -> "bunker"
            listOf("skill", "tool", "plugin", "工具", "技能", "插件").any { it in text } -> "warehouse"
            listOf("market", "shop", "store", "商品", "商店", "市场").any { it in text } -> "shop"
            listOf("write", "book", "story", "doc", "写作", "文档", "小说").any { it in text } -> "library"
            else -> listOf("studio", "cabin", "shop", "workshop", "library")[stableHash("${role.id}|${role.name}|$index") % 5]
        }
    }

    private fun inferHomeMotto(role: Role, sprite: String): String =
        when (sprite) {
            "terminal" -> "我把问题拆开，再把答案跑通"
            "workshop" -> "灵感先进工坊，再变成作品"
            "library" -> "把线索整理成可靠结论"
            "tower" -> "需要动手机时，我替你跑一趟"
            "warehouse" -> "能力都归位，调用才顺手"
            "bunker" -> "先把通路稳住，再谈速度"
            "shop" -> "把好东西摆出来，让它有用"
            "cabin" -> "我在安静处整理思路"
            else -> "${role.name.ifBlank { role.id }} 的房间会随使用生长"
        }

    private fun defaultFurniture(role: Role, sprite: String): List<RoomFurniture> {
        val base = when (sprite) {
            "terminal" -> listOf(
                RoomFurniture("terminal_wall", "terminal", 11, 2, 6, 3, "back", "triple"),
                RoomFurniture("code_console", "console", 14, 11, 3, 5, "front", "server"),
                RoomFurniture("data_cable", "cable", 6, 13, 8, 1, "front"),
            )
            "library" -> listOf(
                RoomFurniture("book_wall", "bookcase", 11, 1, 7, 5, "back"),
                RoomFurniture("reading_chair", "chair", 3, 12, 3, 3, "front", "soft"),
                RoomFurniture("note_board", "art", 2, 2, 3, 2, "back"),
            )
            "workshop" -> listOf(
                RoomFurniture("maker_board", "art", 13, 2, 4, 3, "back", "blueprint"),
                RoomFurniture("workbench", "bench", 10, 12, 5, 2, "front"),
                RoomFurniture("parts_bin", "crate", 15, 14, 2, 2, "front"),
            )
            "tower" -> listOf(
                RoomFurniture("signal_panel", "terminal", 12, 2, 5, 3, "back", "signal"),
                RoomFurniture("phone_stand", "console", 3, 12, 3, 4, "front", "phone"),
                RoomFurniture("signal_cable", "cable", 2, 13, 8, 1, "front"),
            )
            "warehouse" -> listOf(
                RoomFurniture("archive_wall", "shelf", 12, 2, 5, 4, "back", "archive"),
                RoomFurniture("storage_crates", "crate", 12, 12, 5, 3, "front", "stack"),
                RoomFurniture("tool_table", "bench", 4, 11, 4, 2, "front"),
            )
            "bunker" -> listOf(
                RoomFurniture("network_rack", "terminal", 13, 8, 4, 7, "front", "rack"),
                RoomFurniture("secure_line", "cable", 4, 13, 8, 1, "front"),
                RoomFurniture("status_lamp", "lamp", 15, 8, 2, 3, "back"),
            )
            else -> listOf(
                RoomFurniture("home_bed", "bed", 12, 11, 5, 4, "front"),
                RoomFurniture("memory_wall", "art", 12, 2, 4, 3, "back"),
                RoomFurniture("small_plant", "plant", 3, 13, 2, 3, "front"),
            )
        }
        val identity = RoomFurniture(
            id = "identity_token",
            type = if (role.id == "creator") "display" else "sign",
            x = 7,
            y = 15,
            width = 5,
            height = 1,
            layer = "front",
            variant = role.id,
        )
        return (base + identity).take(8)
    }

    private fun migrateLegacyRoom(room: AgentRoom, role: Role): AgentRoom {
        val safe = room.normalized()
        val legacyMotto = safe.motto == "我住在 MobileClaw Town" || safe.motto == "I live in MobileClaw Town"
        val legacyHouseName = safe.houseName == "${role.name} 的家" && role.name.isBlank()
        return safe.copy(
            houseName = if (legacyHouseName) "${role.id} Home" else safe.houseName,
            motto = if (legacyMotto) defaultRoom(role, 0).motto else safe.motto,
            furniture = safe.furniture.ifEmpty { defaultFurniture(role, safe.houseSprite) },
        ).normalized()
    }

    private fun defaultRoomForId(roleId: String): AgentRoom =
        defaultRoom(Role.DEFAULT.copy(id = roleId, name = roleId), 0)

    private fun AgentRoom.normalized(): AgentRoom {
        fun safe(value: String?): String = value.orEmpty()
        fun <T> safeList(value: List<T>?): List<T> = value ?: emptyList()
        return AgentRoom(
            roleId = safe(roleId).ifBlank { "role" }.take(80),
            houseName = safe(houseName).ifBlank { "${safe(roleId).ifBlank { "role" }} room" }.take(40),
            style = safe(style).ifBlank { "pixel studio" }.take(60),
            houseSprite = safe(houseSprite).ifBlank { "studio" }.take(32),
            accent = safe(accent).ifBlank { "#C7F43A" }.take(16),
            doorSign = safe(doorSign).take(80),
            motto = safe(motto).take(100),
            mood = safe(mood).ifBlank { "idle" }.take(24),
            idleLine = safe(idleLine).take(120),
            workingLine = safe(workingLine).take(120),
            wallPins = safeList(wallPins).takeLast(12),
            deskItems = safeList(deskItems).takeLast(10),
            toolbox = safeList(toolbox).takeLast(12),
            showcase = safeList(showcase).takeLast(16),
            furniture = safeList(furniture).takeLast(24).map { it.normalizedFurniture() },
            roomLayout = (roomLayout ?: RoomLayout()).normalizedRoomLayout(),
            notes = safeList(notes).takeLast(8).map { it.orEmpty().take(160) },
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun RoomFurniture.normalizedFurniture(): RoomFurniture {
        fun safe(value: String?): String = value.orEmpty()
        val safeType = safe(type).ifBlank { "decor" }.take(32)
        return copy(
            id = safe(id).ifBlank { stableId("furniture", "$safeType-$x-$y") }.take(80),
            type = safeType,
            x = x.coerceIn(0, 19),
            y = y.coerceIn(0, 19),
            width = width.coerceIn(1, 8),
            height = height.coerceIn(1, 8),
            layer = safe(layer).ifBlank { "front" }.take(16),
            variant = safe(variant).take(40),
            assetId = safe(assetId).take(80),
            color = safe(color).take(16),
        )
    }

    private fun RoomAsset.normalizedAsset(): RoomAsset {
        fun safe(value: String?): String = value.orEmpty()
        return copy(
            id = safe(id).take(100),
            name = safe(name).take(80),
            category = safe(category).take(32),
            type = safe(type).take(32),
            path = safe(path).take(240),
            tileWidth = tileWidth.coerceIn(1, 8),
            tileHeight = tileHeight.coerceIn(1, 8),
            pixelWidth = pixelWidth.coerceAtLeast(1),
            pixelHeight = pixelHeight.coerceAtLeast(1),
            anchor = safe(anchor).take(32),
            layer = safe(layer).take(16),
            orientation = safe(orientation).take(24),
            tags = tags.orEmpty().take(20).map { it.orEmpty().take(32) },
            description = safe(description).take(180),
        )
    }

    private fun RoomLayout.normalizedRoomLayout(): RoomLayout {
        val safeWidth = width.coerceIn(8, 24)
        val safeHeight = height.coerceIn(6, 20)
        return copy(
            width = safeWidth,
            height = safeHeight,
            perspective = perspective.orEmpty().ifBlank { "pokemon_3_4_top_down" }.take(40),
            floorAssetId = floorAssetId.orEmpty().ifBlank { "floor_wood_center" }.take(100),
            defaultWallAssetId = defaultWallAssetId.orEmpty().ifBlank { "wall_back_plaster_center" }.take(100),
            door = door.normalizedDoor(safeWidth, safeHeight),
            zones = zones.orEmpty().take(8).map { it.normalizedZone(safeWidth, safeHeight) },
            objects = objects.orEmpty().take(40).map { it.normalizedLayoutObject(safeWidth, safeHeight) },
        )
    }

    private fun RoomDoor.normalizedDoor(width: Int, height: Int): RoomDoor =
        copy(
            side = side.orEmpty().ifBlank { "south" }.take(16),
            x = x.coerceIn(0, width - 1),
            y = y.coerceIn(0, height - 1),
            assetId = assetId.orEmpty().ifBlank { "door_wood_south" }.take(100),
        )

    private fun RoomZone.normalizedZone(roomWidth: Int, roomHeight: Int): RoomZone =
        copy(
            id = id.orEmpty().ifBlank { stableId("zone", purpose.orEmpty()) }.take(60),
            purpose = purpose.orEmpty().ifBlank { "room" }.take(60),
            x = x.coerceIn(0, roomWidth - 1),
            y = y.coerceIn(0, roomHeight - 1),
            width = width.coerceIn(1, roomWidth),
            height = height.coerceIn(1, roomHeight),
        )

    private fun RoomLayoutObject.normalizedLayoutObject(roomWidth: Int, roomHeight: Int): RoomLayoutObject =
        copy(
            id = id.orEmpty().ifBlank { stableId("object", "${assetId.orEmpty()}-$x-$y") }.take(80),
            assetId = assetId.orEmpty().take(100),
            type = type.orEmpty().ifBlank { "decor" }.take(32),
            x = x.coerceIn(0, roomWidth - 1),
            y = y.coerceIn(0, roomHeight - 1),
            width = width.coerceIn(1, 8),
            height = height.coerceIn(1, 8),
            layer = layer.orEmpty().ifBlank { "object" }.take(16),
            facing = facing.orEmpty().ifBlank { "south" }.take(16),
            zoneId = zoneId.orEmpty().take(60),
        )

    private fun TownMapDocument.normalized(): TownMapDocument {
        fun safe(value: String?): String = value.orEmpty()
        val safeWidth = width.coerceIn(12, 80)
        val safeHeight = height.coerceIn(12, 120)
        return copy(
            version = version.coerceAtLeast(1),
            tileSize = tileSize.coerceIn(8, 48),
            width = safeWidth,
            height = safeHeight,
            theme = safe(theme).ifBlank { "classic_rpg_town" }.take(60),
            layers = layers.orEmpty().map { layer ->
            layer.copy(
                name = safe(layer.name).ifBlank { "layer" }.take(40),
                data = layer.data.orEmpty().take(safeHeight).map { row ->
                    safe(row).padEnd(safeWidth, '.').take(safeWidth)
                },
            )
        }.ifEmpty { defaultTownLayers() },
            sprites = sprites.orEmpty().take(80).map { sprite ->
            sprite.copy(
                id = safe(sprite.id).take(80),
                type = safe(sprite.type).take(40),
                roleId = safe(sprite.roleId).take(80),
                x = sprite.x.coerceIn(0, safeWidth - 1),
                y = sprite.y.coerceIn(0, safeHeight - 1),
                variant = safe(sprite.variant).take(40),
            )
        },
        )
    }

    private fun stableId(prefix: String, value: String): String =
        "${prefix}_${value.lowercase().replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"), "_").trim('_').take(40)}"

    private fun stableHash(value: String): Int {
        var hash = 1125899907
        value.forEach { ch -> hash = 31 * hash + ch.code }
        return hash and 0x7fffffff
    }

    private fun <T, K> upsert(list: List<T>, item: T, key: (T) -> K): List<T> {
        val itemKey = key(item)
        return list.filterNot { key(it) == itemKey } + item
    }
}
