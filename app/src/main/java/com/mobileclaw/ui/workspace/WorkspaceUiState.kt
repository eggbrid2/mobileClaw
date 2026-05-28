package com.mobileclaw.ui.workspace

import com.mobileclaw.memory.MemoryFact
import com.mobileclaw.workspace.WorkspaceInspectorSnapshot

// Workspace 观察与记忆状态属于 workspace feature，本地聚合后更利于后续迁页。
data class WorkspaceUiState(
    val snapshot: WorkspaceInspectorSnapshot? = null,
    val facts: List<MemoryFact> = emptyList(),
)
