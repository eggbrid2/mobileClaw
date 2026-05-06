package com.mobileclaw.agent

/**
 * Classifies user messages as conversational chat vs. agentic tasks.
 *
 * Chat path: direct LLM call, no tools, minimal system prompt, fast.
 * Agent path: full ReAct loop with skills.
 *
 * Intentionally simple and cheap — no LLM call needed.
 * Falls through to AGENT when uncertain (agent can always handle chat too).
 */
object ChatRouter {

    enum class Intent { CHAT, AGENT }

    // Strong signals that device/file/web action is required
    private val AGENT_SIGNALS = listOf(
        "打开", "启动", "运行", "执行", "安装", "下载",
        "发送消息", "发邮件", "发短信", "发微信", "打电话", "拨打",
        "点击", "截图", "输入文字", "滑动", "滚动",
        "创建文件", "读取文件", "列出文件", "写文件", "删除文件",
        "操作手机", "控制手机", "看屏幕", "读屏幕", "see_screen",
        "查天气", "查股票", "查快递", "抓取网页", "爬取",
        "搜索新闻", "搜索资讯", "搜索最新",
        "帮我打开", "帮我发", "帮我安装", "帮我下载", "帮我操作",
        "open ", "launch ", "click ", "screenshot",
    )

    // Strong signals that pure LLM knowledge suffices
    private val CHAT_SIGNALS = listOf(
        "你好", "hello", "hi ", "嗨", "早上好", "下午好", "晚上好", "早安", "晚安",
        "谢谢", "感谢", "辛苦了", "太好了", "太棒了", "不错", "很好",
        "好的", "明白", "了解", "知道了", "懂了",
        "是什么", "什么是", "为什么", "怎么理解", "如何理解", "怎么解释",
        "解释一下", "介绍一下", "讲一讲", "说说", "告诉我",
        "帮我翻译", "翻译一下", "translate",
        "写一首", "写首诗", "写个故事", "讲个笑话", "帮我起名",
        "你是谁", "你叫什么", "你能做什么", "你有什么功能",
        "总结一下", "帮我总结", "帮我写", "帮我分析", "帮我解释",
        "请问", "我想知道", "能告诉我",
    )

    fun classify(goal: String): Intent {
        val lower = goal.trim().lowercase()

        // Agent signals take strict priority
        if (AGENT_SIGNALS.any { lower.contains(it) }) return Intent.AGENT

        // Clear chat signals
        if (CHAT_SIGNALS.any { lower.contains(it) }) return Intent.CHAT

        // Structural heuristics — question marks suggest conversational intent
        val hasQuestion = lower.contains('?') || lower.contains('？')
        if (hasQuestion) return Intent.CHAT

        // Very short messages without question marks are likely commands
        if (goal.length <= 12) return Intent.AGENT

        // Longer messages tend to be conversational
        return if (goal.length >= 35) Intent.CHAT else Intent.AGENT
    }
}
