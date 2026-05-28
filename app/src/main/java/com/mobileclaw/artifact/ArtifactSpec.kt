package com.mobileclaw.artifact

data class ArtifactSpec(
    val goal: String = "",
    val requiredFeatures: List<String> = emptyList(),
    val currentFeatures: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val acceptedCorrections: List<String> = emptyList(),
    val knownBugs: List<String> = emptyList(),
    val nonGoals: List<String> = emptyList(),
    val currentSummary: String = "",
    val lastDiffSummary: String = "",
)

data class ArtifactHistoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val action: String = "",
    val request: String = "",
    val summary: String = "",
)
