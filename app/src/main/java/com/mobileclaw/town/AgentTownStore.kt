package com.mobileclaw.town

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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

    fun registerSpritePack(pack: AgentSpritePack, imageDataUri: String? = null): AgentSpritePack {
        val cleanBase = pack.normalized()
        val storedImagePath = imageDataUri
            ?.takeIf { it.isNotBlank() }
            ?.let { writeSpriteSheetAsset(cleanBase, it) }
        val clean = cleanBase.copy(
            imagePath = storedImagePath ?: cleanBase.imagePath,
            updatedAt = System.currentTimeMillis(),
        )
        val current = _state.value
        save(current.copy(spritePacks = current.spritePacks + (clean.id to clean), updatedAt = System.currentTimeMillis()))
        return clean
    }

    fun assignRoleSpritePack(roleId: String, spritePackId: String): AgentRoom =
        updateRoom(roleId) { room -> room.copy(characterSpritePack = spritePackId) }

    fun assignRolePortraitPack(roleId: String, spritePackId: String): AgentRoom =
        // 肖像图单独落到 portrait 字段，避免角色详情误拿到动画 spritesheet。
        updateRoom(roleId) { room -> room.copy(portraitSpritePack = spritePackId) }

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
        // 旧版本把动态精灵和静态肖像都塞进 characterSpritePack，这里按 pack 类型拆回两个字段。
        val legacyPack = safe.characterSpritePack
            .takeIf { it.isNotBlank() }
            ?.let { _state.value.spritePacks[it] }
        val migratedPortrait = when {
            safe.portraitSpritePack.isNotBlank() -> safe.portraitSpritePack
            legacyPack?.isPortraitPack() == true -> legacyPack.id
            else -> ""
        }
        val migratedCharacter = when {
            legacyPack?.isCharacterPack() == true -> legacyPack.id
            else -> ""
        }
        return safe.copy(
            houseName = if (legacyHouseName) "${role.id} Home" else safe.houseName,
            motto = if (legacyMotto) defaultRoom(role, 0).motto else safe.motto,
            portraitSpritePack = migratedPortrait,
            characterSpritePack = migratedCharacter,
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
            // 统一收口 portrait 字段，防止旧 JSON 缺字段时出现 null/脏值。
            portraitSpritePack = safe(portraitSpritePack).take(80),
            // 动态角色精灵继续单独存放，供 Home 场景使用。
            characterSpritePack = safe(characterSpritePack).take(80),
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
            notes = safeList(notes).takeLast(8).map { it.take(160) },
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun RoomFurniture.normalizedFurniture(): RoomFurniture =
        copy(
            id = id.ifBlank { stableId("furniture", "$type-$x-$y") }.take(80),
            type = type.ifBlank { "decor" }.take(32),
            x = x.coerceIn(0, 19),
            y = y.coerceIn(0, 19),
            width = width.coerceIn(1, 8),
            height = height.coerceIn(1, 8),
            layer = layer.ifBlank { "front" }.take(16),
            variant = variant.take(40),
            color = color.take(16),
        )

    private fun TownMapDocument.normalized(): TownMapDocument = copy(
        version = version.coerceAtLeast(1),
        tileSize = tileSize.coerceIn(8, 48),
        width = width.coerceIn(12, 80),
        height = height.coerceIn(12, 120),
        theme = theme.ifBlank { "classic_rpg_town" }.take(60),
        layers = layers.map { layer ->
            layer.copy(
                name = layer.name.ifBlank { "layer" }.take(40),
                data = layer.data.take(height).map { row ->
                    row.padEnd(width, '.').take(width)
                },
            )
        }.ifEmpty { defaultTownLayers() },
        sprites = sprites.take(80).map { sprite ->
            sprite.copy(
                id = sprite.id.take(80),
                type = sprite.type.take(40),
                roleId = sprite.roleId.take(80),
                x = sprite.x.coerceIn(0, width - 1),
                y = sprite.y.coerceIn(0, height - 1),
                variant = sprite.variant.take(40),
            )
        },
    )

    private fun AgentSpritePack.normalized(): AgentSpritePack {
        val cleanId = stableId("sprite", id.ifBlank { name.ifBlank { "agent" } }).take(80)
        val cleanColumns = columns.coerceIn(1, 16)
        val cleanRows = rows.coerceIn(1, 16)
        return copy(
            id = cleanId,
            name = name.ifBlank { cleanId }.take(60),
            kind = kind.ifBlank { "character" }.take(32),
            imagePath = imagePath.take(260),
            frameWidth = frameWidth.coerceIn(8, 512),
            frameHeight = frameHeight.coerceIn(8, 512),
            columns = cleanColumns,
            rows = cleanRows,
            states = states.mapValues { (_, state) ->
                state.copy(
                    row = state.row.coerceIn(0, cleanRows - 1),
                    startColumn = state.startColumn.coerceIn(0, cleanColumns - 1),
                    frames = state.frames.coerceIn(1, cleanColumns),
                    durationMs = state.durationMs.coerceIn(80, 6000),
                )
            }.ifEmpty { defaultSpriteStates() },
            palette = palette.take(12).map { it.take(16) },
            notes = notes.take(240),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun writeSpriteSheetAsset(pack: AgentSpritePack, dataUri: String): String? = runCatching {
        val bytes = decodeDataUri(dataUri)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val expectedWidth = pack.frameWidth * pack.columns
        val expectedHeight = pack.frameHeight * pack.rows
        val normalizedRaw = if (decoded.width == expectedWidth && decoded.height == expectedHeight) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, expectedWidth, expectedHeight, false)
        }
        val normalized = removeChromaKeyBackground(normalizedRaw)
        val dir = assetDir.resolve("sprites").also { it.mkdirs() }
        val file = dir.resolve("${pack.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.png")
        file.outputStream().use { out ->
            normalized.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (normalized !== normalizedRaw) normalized.recycle()
        if (normalizedRaw !== decoded) normalizedRaw.recycle()
        decoded.recycle()
        file.absolutePath
    }.getOrNull()

    private fun AgentSpritePack.isPortraitPack(): Boolean =
        // 静态肖像必须同时满足 portrait 标记或单帧结构，避免把 character spritesheet 误判成角色图。
        kind == "portrait" || notes.contains("role_self_portrait_") || (columns == 1 && rows == 1)

    private fun AgentSpritePack.isCharacterPack(): Boolean =
        // 动态角色必须明确是 character 或多帧结构，避免把静态图误送进房间动画位。
        kind == "character" || notes.contains("role_self_sprite_") || columns > 1 || rows > 1

    private fun removeChromaKeyBackground(source: Bitmap): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(output.width * output.height)
        output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = android.graphics.Color.red(p)
            val g = android.graphics.Color.green(p)
            val b = android.graphics.Color.blue(p)
            if (g > 190 && r < 90 && b < 120) {
                pixels[i] = android.graphics.Color.TRANSPARENT
            }
        }
        output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        return output
    }

    private fun decodeDataUri(dataUri: String): ByteArray {
        val marker = "base64,"
        val index = dataUri.indexOf(marker)
        val b64 = if (index >= 0) dataUri.substring(index + marker.length) else dataUri
        return Base64.decode(b64, Base64.DEFAULT)
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
