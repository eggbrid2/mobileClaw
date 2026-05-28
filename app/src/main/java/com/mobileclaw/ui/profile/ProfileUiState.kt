package com.mobileclaw.ui.profile

import com.mobileclaw.memory.MemoryFact
import com.mobileclaw.memory.db.EpisodeEntity
import com.mobileclaw.ui.chat.AiQuizQuestion

// 画像与记忆页的运行态聚合到 profile feature，避免 MainUiState 持续膨胀。
data class ProfileUiState(
    val facts: Map<String, String> = emptyMap(),
    val semanticFacts: List<MemoryFact> = emptyList(),
    val recentEpisodes: List<EpisodeEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isExtracting: Boolean = false,
    val conversationCount: Int = 0,
    val personalitySummary: String = "",
    val personalitySummaryLoading: Boolean = false,
    val dimensionQuizzes: Map<String, List<AiQuizQuestion>> = emptyMap(),
    val dimensionQuizLoading: String? = null,
)
