package com.mobileclaw.artifact

data class PortableArtifactPackageManifest(
    val schemaVersion: Int = 1,
    val packageType: String,
    val artifactId: String,
    val title: String = "",
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val entries: List<PortableArtifactEntry> = emptyList(),
    val dependencies: List<PortableArtifactDependency> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

data class PortableArtifactEntry(
    val path: String,
    val kind: String,
    val required: Boolean = true,
    val size: Long = 0L,
)

data class PortableArtifactDependency(
    val type: String,
    val id: String,
    val title: String = "",
    val required: Boolean = false,
)

object PortableArtifactTypes {
    const val MINI_APP = "miniapp"
    const val ROLE = "role"
    const val AI_PAGE = "ai_page"
    const val SKILL = "skill"
    const val WORKSPACE = "workspace"
}
