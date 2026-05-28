package com.mobileclaw.town

data class AgentTownState(
    val townName: String = "MobileClaw Roles",
    val weather: String = "clear",
    val timeOfDay: String = "day",
    val map: TownMapDocument = TownMapDocument(),
    val spritePacks: Map<String, AgentSpritePack> = defaultAgentSpritePacks().associateBy { it.id },
    val rooms: Map<String, AgentRoom> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class TownMapDocument(
    val version: Int = 1,
    val tileSize: Int = 16,
    val width: Int = 30,
    val height: Int = 56,
    val theme: String = "classic_rpg_town",
    val layers: List<TownMapLayer> = defaultTownLayers(),
    val sprites: List<TownMapSprite> = emptyList(),
)

data class TownMapLayer(
    val name: String,
    val data: List<String>,
)

data class TownMapSprite(
    val id: String,
    val type: String,
    val roleId: String = "",
    val x: Int,
    val y: Int,
    val variant: String = "",
)

data class AgentSpritePack(
    val id: String,
    val name: String,
    val kind: String = "character",
    val imagePath: String = "",
    val frameWidth: Int = 192,
    val frameHeight: Int = 208,
    val columns: Int = 8,
    val rows: Int = 9,
    val states: Map<String, SpriteAnimationState> = defaultSpriteStates(),
    val palette: List<String> = emptyList(),
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

data class SpriteAnimationState(
    val row: Int = 0,
    val startColumn: Int = 0,
    val frames: Int = 1,
    val durationMs: Int = 900,
    val loop: Boolean = true,
)

data class AgentRoom(
    val roleId: String,
    val houseName: String = "",
    val style: String = "pixel studio",
    val houseSprite: String = "studio",
    // portraitSpritePack 只用于角色页/详情页展示的静态角色图，不再和房间里的动态精灵混用。
    val portraitSpritePack: String = "",
    // characterSpritePack 只保留给 Home/房间里的动态角色精灵。
    val characterSpritePack: String = "",
    val accent: String = "#C7F43A",
    val doorSign: String = "",
    val motto: String = "",
    val mood: String = "idle",
    val idleLine: String = "",
    val workingLine: String = "",
    val wallPins: List<RoomPin> = emptyList(),
    val deskItems: List<RoomArtifact> = emptyList(),
    val toolbox: List<RoomTool> = emptyList(),
    val showcase: List<RoomArtifact> = emptyList(),
    val furniture: List<RoomFurniture> = emptyList(),
    val notes: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class RoomFurniture(
    val id: String,
    val type: String,
    val x: Int,
    val y: Int,
    val width: Int = 2,
    val height: Int = 2,
    val layer: String = "front",
    val variant: String = "",
    val color: String = "",
)

data class RoomPin(
    val id: String = "pin_${System.currentTimeMillis()}",
    val type: String = "memory",
    val title: String,
    val body: String = "",
    val source: String = "",
)

data class RoomArtifact(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String = "",
)

data class RoomTool(
    val id: String,
    val title: String,
    val category: String = "",
)

fun defaultSpriteStates(): Map<String, SpriteAnimationState> = mapOf(
    "idle" to SpriteAnimationState(row = 0, frames = 8, durationMs = 1200),
    "running_right" to SpriteAnimationState(row = 1, frames = 8, durationMs = 720),
    "running_left" to SpriteAnimationState(row = 2, frames = 8, durationMs = 720),
    "waving" to SpriteAnimationState(row = 3, frames = 8, durationMs = 900),
    "jumping" to SpriteAnimationState(row = 4, frames = 8, durationMs = 760),
    "failed" to SpriteAnimationState(row = 5, frames = 8, durationMs = 1100),
    "waiting" to SpriteAnimationState(row = 6, frames = 8, durationMs = 1400),
    "running" to SpriteAnimationState(row = 7, frames = 8, durationMs = 720),
    "review" to SpriteAnimationState(row = 8, frames = 8, durationMs = 1000),
    "thinking" to SpriteAnimationState(row = 6, frames = 8, durationMs = 1400),
    "working" to SpriteAnimationState(row = 8, frames = 8, durationMs = 1000),
    "happy" to SpriteAnimationState(row = 3, frames = 8, durationMs = 900),
)

fun defaultAgentSpritePacks(): List<AgentSpritePack> = listOf(
    AgentSpritePack(
        id = "builtin_creator",
        name = "Creator Atelier Sprite",
        kind = "character",
        palette = listOf("#2C241A", "#FFE0A6", "#F472B6", "#7C3AED"),
        notes = "Built-in fallback sprite. Replace with a spritesheet pack when the role generates its own look.",
    ),
    AgentSpritePack(
        id = "builtin_operator",
        name = "Phone Operator Sprite",
        kind = "character",
        palette = listOf("#2C241A", "#FFE0A6", "#38BDF8", "#0F766E"),
    ),
    AgentSpritePack(
        id = "builtin_coder",
        name = "Coder Terminal Sprite",
        kind = "character",
        palette = listOf("#2C241A", "#FFE0A6", "#A78BFA", "#111827"),
    ),
    AgentSpritePack(
        id = "builtin_general",
        name = "Town Resident Sprite",
        kind = "character",
        palette = listOf("#2C241A", "#FFE0A6", "#C7F43A", "#475569"),
    ),
)

fun defaultTownLayers(): List<TownMapLayer> {
    val width = 30
    val height = 56
    val ground = MutableList(height) { CharArray(width) { if ((it + 3) % 7 == 0) ',' else '.' } }
    val objectLayer = MutableList(height) { CharArray(width) { ' ' } }

    fun fill(layer: MutableList<CharArray>, x0: Int, y0: Int, x1: Int, y1: Int, tile: Char) {
        for (y in y0.coerceAtLeast(0)..y1.coerceAtMost(height - 1)) {
            for (x in x0.coerceAtLeast(0)..x1.coerceAtMost(width - 1)) {
                layer[y][x] = tile
            }
        }
    }

    fun path(x0: Int, y0: Int, x1: Int, y1: Int) = fill(ground, x0, y0, x1, y1, 'p')
    fun water(x0: Int, y0: Int, x1: Int, y1: Int) = fill(ground, x0, y0, x1, y1, 'w')
    fun wall(x0: Int, y0: Int, x1: Int, y1: Int) = fill(objectLayer, x0, y0, x1, y1, 'm')
    fun tree(x: Int, y: Int) {
        fill(objectLayer, x, y, x + 1, y + 1, 't')
        objectLayer[(y + 2).coerceAtMost(height - 1)][x] = 'T'
    }
    fun flower(x: Int, y: Int) { objectLayer[y.coerceIn(0, height - 1)][x.coerceIn(0, width - 1)] = 'f' }

    water(0, 4, 3, 37)
    path(13, 5, 15, 55)
    path(6, 14, 25, 15)
    path(7, 24, 20, 25)
    path(3, 35, 26, 36)
    path(3, 24, 4, 36)
    path(9, 40, 13, 49)
    path(16, 8, 17, 15)
    path(22, 14, 23, 19)

    wall(5, 8, 27, 8)
    wall(5, 8, 5, 14)
    wall(24, 8, 24, 15)
    wall(6, 22, 18, 22)
    wall(6, 22, 6, 29)
    wall(17, 30, 26, 30)
    wall(4, 42, 21, 42)

    listOf(1 to 2, 26 to 3, 25 to 31, 9 to 45, 20 to 43, 27 to 47, 2 to 51).forEach { (x, y) -> tree(x, y) }
    listOf(10 to 13, 20 to 12, 23 to 21, 8 to 27, 18 to 34, 12 to 39, 23 to 46, 5 to 52).forEach { (x, y) -> flower(x, y) }
    objectLayer[20][15] = 'F'
    objectLayer[28][17] = 's'
    objectLayer[33][19] = 's'

    return listOf(
        TownMapLayer("ground", ground.map { String(it) }),
        TownMapLayer("objects", objectLayer.map { String(it) }),
    )
}
