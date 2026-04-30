package com.mobileclaw.skill

/**
 * Bundled catalog of one-tap installable skills for Chinese users.
 * All entries use free public APIs accessible without a VPN.
 */
object SkillMarket {

    data class MarketEntry(
        val emoji: String,
        val category: String,
        val def: SkillDefinition,
    )

    private val browserHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
        "Accept" to "application/json, text/plain, */*",
        "Referer" to "https://api.vvhan.com/",
    )

    val catalog: List<MarketEntry> = listOf(

        // ── 热榜资讯 ─────────────────────────────────────────────────────────

        MarketEntry("🔥", "热榜", SkillDefinition(
            meta = SkillMeta(
                id = "weibo_hot",
                name = "Weibo Hot Topics",
                nameZh = "微博热搜",
                description = "Fetches the current Weibo trending hot search list in real time.",
                descriptionZh = "获取当前微博热搜榜单，实时更新。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/hotlist/wbHot",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("📺", "热榜", SkillDefinition(
            meta = SkillMeta(
                id = "bilibili_hot",
                name = "Bilibili Hot Videos",
                nameZh = "B站热榜",
                description = "Fetches currently trending videos on Bilibili.",
                descriptionZh = "获取B站当前热门视频榜单。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/hotlist/bili",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("💡", "热榜", SkillDefinition(
            meta = SkillMeta(
                id = "zhihu_hot",
                name = "Zhihu Hot Questions",
                nameZh = "知乎热榜",
                description = "Fetches the current Zhihu hot questions list.",
                descriptionZh = "获取知乎当前热门问题榜单。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/hotlist/zhihuHot",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("🔍", "热榜", SkillDefinition(
            meta = SkillMeta(
                id = "baidu_hot",
                name = "Baidu Hot Search",
                nameZh = "百度热搜",
                description = "Fetches the current Baidu hot search trending list.",
                descriptionZh = "获取百度当前热搜榜单。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/hotlist/baiduRD",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("📱", "热榜", SkillDefinition(
            meta = SkillMeta(
                id = "douyin_hot",
                name = "Douyin Hot Topics",
                nameZh = "抖音热榜",
                description = "Fetches current trending topics on Douyin (TikTok China).",
                descriptionZh = "获取抖音当前热门话题榜单。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/hotlist/douyinHot",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("📰", "热榜", SkillDefinition(
            meta = SkillMeta(
                id = "toutiao_hot",
                name = "Toutiao Hot News",
                nameZh = "今日头条热榜",
                description = "Fetches trending news headlines from Toutiao (Today's Headlines).",
                descriptionZh = "获取今日头条当前热门新闻标题。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/hotlist/toutiao",
                headers = browserHeaders,
            ),
        )),

        // ── 生活工具 ─────────────────────────────────────────────────────────

        MarketEntry("🌤", "生活", SkillDefinition(
            meta = SkillMeta(
                id = "weather_cn",
                name = "Weather Query",
                nameZh = "天气查询",
                description = "Gets current weather for a Chinese city. Provide city name in Chinese.",
                descriptionZh = "查询指定城市的天气情况，城市名称用中文。",
                parameters = listOf(
                    SkillParam("city", "string", "City name in Chinese (e.g. 北京, 上海)"),
                ),
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("生活"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/weather?city={city}&type=week",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("🌐", "生活", SkillDefinition(
            meta = SkillMeta(
                id = "ip_lookup",
                name = "IP Address Lookup",
                nameZh = "IP地址查询",
                description = "Looks up geographic location and ISP for an IP address.",
                descriptionZh = "查询IP地址的地理位置和运营商信息。",
                parameters = listOf(
                    SkillParam("ip", "string", "IP address to look up (leave empty for current IP)", required = false),
                ),
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("网络"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/getIpInfo?ip={ip}",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("💱", "生活", SkillDefinition(
            meta = SkillMeta(
                id = "exchange_rate",
                name = "Exchange Rate",
                nameZh = "汇率查询",
                description = "Gets current CNY exchange rates for common currencies.",
                descriptionZh = "查询人民币对主要货币的实时汇率。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("生活"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/exchange",
                headers = browserHeaders,
            ),
        )),

        MarketEntry("🗓", "生活", SkillDefinition(
            meta = SkillMeta(
                id = "cn_calendar",
                name = "Chinese Calendar",
                nameZh = "万年历",
                description = "Gets today's Chinese calendar info including lunar date, solar terms, and lucky directions.",
                descriptionZh = "获取今日农历、节气、宜忌等万年历信息。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("生活"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/almanac",
                headers = browserHeaders,
            ),
        )),

        // ── 创作灵感 ─────────────────────────────────────────────────────────

        MarketEntry("✍️", "创作", SkillDefinition(
            meta = SkillMeta(
                id = "hitokoto",
                name = "Random Quote (一言)",
                nameZh = "一言",
                description = "Returns a random literary quote or inspiring sentence from the Hitokoto library.",
                descriptionZh = "从一言数据库随机返回一句文学语句或励志句子。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("创作"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://v1.hitokoto.cn/?encode=json&min_length=5&max_length=80",
                textResponsePath = "hitokoto",
            ),
        )),

        MarketEntry("📜", "创作", SkillDefinition(
            meta = SkillMeta(
                id = "poem_cn",
                name = "Random Ancient Poem",
                nameZh = "今日诗词",
                description = "Returns a random line from classic Chinese poetry.",
                descriptionZh = "随机返回一句古诗词（今日诗词）。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("创作"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://v1.jinrishici.com/all.json",
                textResponsePath = "content",
            ),
        )),

        MarketEntry("😄", "创作", SkillDefinition(
            meta = SkillMeta(
                id = "joke_cn",
                name = "Random Joke",
                nameZh = "随机笑话",
                description = "Returns a random funny joke in Chinese.",
                descriptionZh = "随机返回一个中文笑话。",
                injectionLevel = 2,
                type = SkillType.HTTP,
                isBuiltin = false,
                minApiLevel = 21,
                tags = listOf("创作"),
            ),
            httpConfig = HttpSkillConfig(
                url = "https://api.vvhan.com/api/joke",
                headers = browserHeaders,
                textResponsePath = "data.content",
            ),
        )),
    )
}
