package com.mobileclaw.config

fun normalizedResponseLanguage(language: String): String =
    language.takeIf { it == "zh" || it == "en" } ?: "zh"

fun responseLanguageName(language: String): String = when (normalizedResponseLanguage(language)) {
    "en" -> "English"
    else -> "Simplified Chinese"
}

fun responseLanguageSystemInstruction(language: String): String {
    return when (normalizedResponseLanguage(language)) {
        "en" -> """
## Response Language
The app language is English. You MUST write all user-visible assistant text in English, regardless of whether the user typed Chinese, English, or another language.
Preserve code, file paths, JSON keys, tool names, tool arguments, quoted source text, and user-provided proper nouns exactly when required by the task.
If a strict output schema is requested, keep the schema intact and localize only natural-language values when appropriate.
""".trimIndent()
        else -> """
## Response Language
应用语言是中文。你必须使用简体中文输出所有用户可见的 AI 内容，无论用户输入的是中文、英文还是其他语言。
代码、文件路径、JSON key、工具名称、工具参数、引用原文、用户提供的专有名词，在任务需要时保持原样。
如果任务要求严格输出结构，保持结构不变，只在合适的位置本地化自然语言内容。
""".trimIndent()
    }
}

fun responseLanguageShortInstruction(language: String): String =
    when (normalizedResponseLanguage(language)) {
        "en" -> "Write user-visible output in English, following the app language setting."
        else -> "用户可见输出必须使用简体中文，跟随应用语言设置。"
    }
