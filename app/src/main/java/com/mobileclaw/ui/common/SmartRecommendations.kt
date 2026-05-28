package com.mobileclaw.ui.common

import com.mobileclaw.R
import com.mobileclaw.app.MiniApp
import com.mobileclaw.memory.db.EpisodeEntity
import com.mobileclaw.str

internal fun buildSmartRecommendations(
    episodes: List<EpisodeEntity>,
    profileFacts: Map<String, String>,
    miniApps: List<MiniApp>,
    recentUserMessages: List<String>,
): List<String> {
    val result = mutableListOf<String>()

    miniApps.sortedByDescending { it.updatedAt }.take(2).forEachIndexed { idx, app ->
        result += if (idx == 0) {
            str(R.string.quick_suggest_continue_app, app.icon, app.title)
        } else {
            str(R.string.quick_suggest_add_feature, app.icon, app.title)
        }
    }

    detectEmotionSuggestion(recentUserMessages, profileFacts)?.let { result += it }

    val appKeywords = listOf(
        "app",
        str(R.string.drawer_apps),
        str(R.string.vm_20dce2),
        str(R.string.vm_1405d7),
        str(R.string.vm_f0dfc6),
        str(R.string.vm_785abc),
        str(R.string.vm_18a481),
        "html",
        str(R.string.group_create),
        str(R.string.vm_8cdf04),
    )
    val nonAppEpisodes = episodes.filter { ep ->
        val g = ep.goalText.lowercase()
        ep.goalText.isNotBlank() && ep.success && appKeywords.none { g.contains(it) }
    }
    nonAppEpisodes
        .groupBy { it.goalText.trim().take(10).lowercase() }
        .entries
        .sortedByDescending { it.value.maxOf { e -> e.createdAt } }
        .mapNotNull { (_, eps) -> transformEpisodeToSuggestion(eps.first().goalText) }
        .distinctBy { it.take(12).lowercase() }
        .take(2)
        .forEach { result += it }

    if (result.size < 3) {
        result += buildProfileSuggestions(profileFacts).take(3 - result.size)
    }

    return result.distinctBy { it.take(12).lowercase() }.take(5)
}

private fun detectEmotionSuggestion(messages: List<String>, profileFacts: Map<String, String>): String? {
    val text = messages.take(15).joinToString(" ").lowercase()
    return when {
        Regex(str(R.string.vm_7f612d)).containsMatchIn(text) -> str(R.string.vm_e391a4)
        Regex(str(R.string.vm_13e6c0)).containsMatchIn(text) -> str(R.string.vm_574a74)
        Regex(str(R.string.vm_5fa120)).containsMatchIn(text) -> str(R.string.vm_e88c4a)
        Regex(str(R.string.vm_2d4a2a)).containsMatchIn(text) -> str(R.string.vm_e53c91)
        Regex(str(R.string.vm_bfd743)).containsMatchIn(text) -> str(R.string.vm_2846ae)
        else -> {
            val emotionalVal = profileFacts.entries
                .find { it.key.contains("emotional") || it.key.contains("stability") }
                ?.value
                ?.lowercase()
            if (emotionalVal != null && Regex(str(R.string.vm_893bbc)).containsMatchIn(emotionalVal)) {
                str(R.string.vm_408d69)
            } else {
                null
            }
        }
    }
}

private fun transformEpisodeToSuggestion(goalText: String): String? {
    val lower = goalText.lowercase()
    val topic = goalText.trim().replace(Regex(str(R.string.vm_7851a0)), "").trim()
    if (topic.length < 2) return null
    val short = topic.take(15)
    return when {
        lower.contains(str(R.string.profile_search)) ||
            lower.contains(str(R.string.vm_40f58e)) ||
            lower.contains(str(R.string.vm_144a16)) ||
            lower.contains(str(R.string.vm_2f3652)) ->
            "🔍 继续深入搜索${short.replace(Regex(str(R.string.vm_8e63b3)), "").trim().take(12)}？"

        lower.contains(str(R.string.profile_72fa7c)) ||
            lower.contains(str(R.string.vm_d7656a)) ||
            lower.contains(str(R.string.vm_0d8307)) ->
            str(R.string.quick_suggest_refine, short)

        lower.contains(str(R.string.profile_4d7dc6)) ||
            lower.contains(str(R.string.vm_c8616c)) ||
            lower.contains(str(R.string.vm_6cf774)) ||
            lower.contains(str(R.string.vm_f4b06b)) ->
            str(R.string.quick_suggest_improve, short)

        lower.contains(str(R.string.vm_65f27a)) ||
            lower.contains(str(R.string.profile_aacef1)) ||
            lower.contains(str(R.string.profile_2d4653)) ->
            str(R.string.quick_suggest_learn, short)

        lower.contains(str(R.string.vm_8b3607)) -> str(R.string.vm_485c63)
        else -> str(R.string.quick_suggest_explore, short)
    }
}

private fun buildProfileSuggestions(profileFacts: Map<String, String>): List<String> {
    val profession = profileFacts.entries
        .find { it.key.contains("profession") || it.key.contains("job") || it.key.contains("occupation") }
        ?.value
        ?.lowercase()
        ?: ""
    val interests = profileFacts.entries
        .filter { it.key.contains("interest") || it.key.contains("hobby") || it.key.contains(str(R.string.vm_12081d)) }
        .joinToString(" ") { it.value.lowercase() }

    val suggestions = mutableListOf<String>()

    when {
        profession.contains(str(R.string.vm_3ff3c3)) ||
            profession.contains(str(R.string.vm_1405d7)) ||
            profession.contains(str(R.string.vm_22c8a6)) ||
            profession.contains("dev") -> {
            suggestions += str(R.string.vm_b762d9)
            suggestions += str(R.string.vm_search)
        }
        profession.contains(str(R.string.vm_b08890)) -> {
            suggestions += str(R.string.vm_22fc10)
            suggestions += str(R.string.vm_bd62aa)
        }
        profession.contains(str(R.string.vm_35d996)) ||
            profession.contains(str(R.string.profile_4ef520)) ||
            profession.contains(str(R.string.vm_8640cb)) -> {
            suggestions += str(R.string.vm_fb0c6d)
            suggestions += str(R.string.vm_search_2)
        }
        profession.contains(str(R.string.vm_47df87)) ||
            profession.contains(str(R.string.home_552cac)) ||
            profession.contains(str(R.string.vm_916801)) -> {
            suggestions += str(R.string.vm_search_3)
            suggestions += str(R.string.vm_8f3a74)
        }
        profession.contains(str(R.string.vm_d58e85)) ||
            profession.contains(str(R.string.vm_104ec0)) ||
            profession.contains(str(R.string.vm_content)) -> {
            suggestions += str(R.string.vm_b43302)
            suggestions += str(R.string.vm_search_4)
        }
        profession.contains(str(R.string.vm_60f89a)) ||
            profession.contains(str(R.string.vm_aef5b4)) ||
            profession.contains(str(R.string.vm_b8fe8d)) -> {
            suggestions += str(R.string.vm_search_5)
            suggestions += str(R.string.vm_7636a9)
        }
    }

    when {
        interests.contains(str(R.string.vm_687a7e)) || interests.contains(str(R.string.vm_2f3703)) ->
            suggestions += str(R.string.vm_da0dfd)
        interests.contains(str(R.string.profile_c24d6f)) ||
            interests.contains(str(R.string.profile_37b6de)) ||
            interests.contains(str(R.string.vm_7b385d)) ->
            suggestions += str(R.string.vm_1fd770)
        interests.contains(str(R.string.vm_874834)) || interests.contains(str(R.string.vm_0ebbd8)) ->
            suggestions += str(R.string.vm_26a2c0)
        interests.contains(str(R.string.vm_c89227)) ||
            interests.contains(str(R.string.vm_c5df2d)) ||
            interests.contains(str(R.string.vm_e69a3d)) ->
            suggestions += str(R.string.vm_27bf8d)
        interests.contains(str(R.string.vm_ba0821)) ->
            suggestions += str(R.string.vm_search_6)
        interests.contains(str(R.string.vm_4c8bd0)) ->
            suggestions += str(R.string.vm_fee3c0)
    }

    return suggestions.take(3)
}
