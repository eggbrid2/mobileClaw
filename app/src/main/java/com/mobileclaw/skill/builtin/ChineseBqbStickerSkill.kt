package com.mobileclaw.skill.builtin

import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class ChineseBqbStickerSkill(private val app: ClawApplication) : Skill {
    override val meta = SkillMeta(
        id = "sticker_bqb",
        name = "ChineseBQB Stickers",
        nameZh = "中文表情包",
        description = "Conversational expression tool: search and send Chinese meme stickers from the built-in ChineseBQB source. " +
            "Use proactively when your reply has a matching emotion or meme reaction, especially jokes, teasing, celebration, awkwardness, comfort, thanks, surprise, speechless moments, or playful group chat. " +
            "Prefer action=search with a short emotional query such as 哈哈, 笑死, 牛, 离谱, 尴尬, 无语, 摸鱼, 生气, 谢谢, 安慰, 庆祝. " +
            "Send at most one sticker in a turn, only when it genuinely fits your text; avoid stickers for serious, professional, or safety-critical answers. " +
            "Actions: random | search | categories | refresh. random/search can return an image attachment.",
        descriptionZh = "会话表达工具：从内置 ChineseBQB 搜索并发送中文表情包。不是只有用户点名才用；当回复里的情绪、梗或反应能和表情包匹配时，应主动考虑使用，例如开玩笑、吐槽、调侃、庆祝、尴尬、安慰、感谢、惊讶、无语或群聊斗图。每轮最多一个，严肃/专业/安全类回答不要乱用。",
        parameters = listOf(
            SkillParam("action", "string", "random | search | categories | refresh", required = false),
            SkillParam("query", "string", "Short emotion/meme keyword matched to the reply, e.g. 哈哈, 笑死, 牛, 离谱, 尴尬, 无语, 摸鱼, 生气, 谢谢, 安慰, 庆祝", required = false),
            SkillParam("category", "string", "Optional category filter", required = false),
            SkillParam("limit", "number", "Max search/category results, default 12", required = false),
            SkillParam("send", "boolean", "If true, download one matched sticker and attach it. Default true for random/search.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 1,
        tags = listOf("表情包", "群聊", "中文"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult = withContext(Dispatchers.IO) {
        val action = (params["action"] as? String)?.lowercase()?.ifBlank { null } ?: "random"
        val query = (params["query"] as? String)?.trim().orEmpty()
        val category = (params["category"] as? String)?.trim().orEmpty()
        val limit = ((params["limit"] as? Number)?.toInt() ?: 12).coerceIn(1, 50)
        val send = (params["send"] as? Boolean) ?: (action in setOf("random", "search"))

        runCatching {
            when (action) {
                "refresh" -> {
                    val entries = ChineseBqbStickerRepository.refresh(app)
                    SkillResult(true, "ChineseBQB index refreshed: ${entries.size} stickers, ${entries.map { it.category }.distinct().size} categories.")
                }
                "categories" -> {
                    val categories = ChineseBqbStickerRepository.categories(app, limit)
                    SkillResult(true, categories.joinToString("\n") { "- ${it.first} (${it.second})" })
                }
                "search", "random" -> {
                    val entries = ChineseBqbStickerRepository.search(app, query, category, limit = 200)
                    if (entries.isEmpty()) {
                        return@runCatching SkillResult(false, "No ChineseBQB stickers matched query='$query' category='$category'.")
                    }
                    val chosen = if (action == "random" || send) entries.random(Random(System.currentTimeMillis())) else entries.first()
                    val summary = entries.take(limit).joinToString("\n") { "- ${it.name} | ${it.category}" }
                    if (!send) {
                        SkillResult(true, "ChineseBQB matches (${entries.size}):\n$summary")
                    } else {
                        val image = ChineseBqbStickerRepository.download(app, chosen)
                        SkillResult(
                            success = true,
                            output = "Selected ChineseBQB sticker: ${chosen.name}\nCategory: ${chosen.category}\nMatched examples:\n$summary",
                            data = image,
                        )
                    }
                }
                else -> SkillResult(false, "Unknown action: $action. Use random | search | categories | refresh.")
            }
        }.getOrElse { e ->
            SkillResult(false, "ChineseBQB sticker error: ${e.message}")
        }
    }

}
