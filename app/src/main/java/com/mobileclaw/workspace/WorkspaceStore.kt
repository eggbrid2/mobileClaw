package com.mobileclaw.workspace

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobileclaw.storage.AtomicTextFile
import java.io.File
import java.util.UUID

data class WorkspaceArtifactLink(
    val type: String,
    val id: String,
    val title: String = "",
    val linkedAt: Long = System.currentTimeMillis(),
)

data class WorkspaceRunRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val success: Boolean,
    val taskType: String = "",
)

data class WorkspaceCheckpoint(
    val timestamp: Long = System.currentTimeMillis(),
    val label: String,
    val taskType: String = "",
    val summary: String = "",
    val details: String = "",
)

data class WorkspaceArtifactState(
    val timestamp: Long = System.currentTimeMillis(),
    val artifactType: String,
    val artifactId: String,
    val title: String = "",
    val action: String = "",
    val goal: String = "",
    val currentFeatures: List<String> = emptyList(),
    val requiredFeatures: List<String> = emptyList(),
    val lastDiffSummary: String = "",
    val raw: String = "",
)

data class WorkspaceEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val source: String,
    val title: String,
    val summary: String,
    val payload: String = "",
)

data class WorkspaceManifest(
    val id: String,
    val title: String,
    val goal: String,
    val scope: String = "",
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val linkedArtifacts: List<WorkspaceArtifactLink> = emptyList(),
    val workingSet: List<String> = emptyList(),
    val lastRunSummary: String = "",
    val repairCount: Int = 0,
)

data class WorkspaceExecutionContext(
    val workspaceId: String,
    val title: String,
    val goal: String,
    val scope: String,
    val taskType: String = "",
    val checkpointLabel: String = "",
    val checkpointSummary: String = "",
    val latestArtifactType: String = "",
    val latestArtifactId: String = "",
    val latestArtifactTitle: String = "",
    val latestArtifactAction: String = "",
    val latestEventCategory: String = "",
    val latestEventSummary: String = "",
)

data class WorkspaceInspectorSnapshot(
    val manifest: WorkspaceManifest,
    val execution: WorkspaceExecutionContext? = null,
    val recentCheckpoints: List<WorkspaceCheckpoint> = emptyList(),
    val recentEvents: List<WorkspaceEvent> = emptyList(),
    val recentArtifacts: List<WorkspaceArtifactState> = emptyList(),
    val recentNotes: List<Pair<String, String>> = emptyList(),
)

class WorkspaceStore(filesDir: File) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val ioLock = Any()
    private val root = File(filesDir, "workspaces").also { it.mkdirs() }

    fun createWorkspace(
        title: String,
        goal: String,
        scope: String = "",
        tags: List<String> = emptyList(),
    ): WorkspaceManifest {
        val id = "ws_${UUID.randomUUID().toString().take(8)}"
        val manifest = WorkspaceManifest(
            id = id,
            title = title.ifBlank { goal.take(40).ifBlank { id } },
            goal = goal,
            scope = scope,
            tags = tags.distinct(),
        )
        ensureDirs(id)
        save(manifest)
        return manifest
    }

    fun get(id: String): WorkspaceManifest? = synchronized(ioLock) {
        runCatching { gson.fromJson(AtomicTextFile.readOrNull(File(workspaceDir(id), "manifest.json")), WorkspaceManifest::class.java) }.getOrNull()
    }

    fun list(limit: Int = 50): List<WorkspaceManifest> = synchronized(ioLock) {
        root.listFiles { file -> file.isDirectory }
            ?.mapNotNull { dir -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(File(dir, "manifest.json")), WorkspaceManifest::class.java) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?.take(limit)
            ?: emptyList()
    }

    fun currentForScope(scope: String): WorkspaceManifest? =
        list(limit = 100).firstOrNull { it.scope == scope && it.status == "active" }

    fun findByArtifact(type: String, artifactId: String): WorkspaceManifest? =
        list(limit = 100).firstOrNull { manifest ->
            manifest.linkedArtifacts.any { it.type == type && it.id == artifactId }
        }

    fun appendNote(id: String, name: String, content: String): String = synchronized(ioLock) {
        val file = File(notesDir(id), sanitizeName(name) + ".md")
        val current = AtomicTextFile.readOrNull(file)
        AtomicTextFile.write(file, if (current.isNullOrBlank()) content else "$current\n\n$content")
        touch(id)
        file.absolutePath
    }

    fun writeJson(id: String, name: String, value: Any): String = synchronized(ioLock) {
        val file = File(scratchDir(id), sanitizeName(name) + ".json")
        AtomicTextFile.write(file, gson.toJson(value))
        touch(id, workingSetAdd = file.relativeTo(workspaceDir(id)).path)
        file.absolutePath
    }

    fun writeBytes(id: String, relativeDir: String, name: String, bytes: ByteArray): String = synchronized(ioLock) {
        ensureDirs(id)
        val dir = File(workspaceDir(id), sanitizeName(relativeDir)).also { it.mkdirs() }
        val file = File(dir, sanitizeFileName(name))
        file.writeBytes(bytes)
        touch(id, workingSetAdd = file.relativeTo(workspaceDir(id)).path)
        file.absolutePath
    }

    fun readFile(id: String, relativePath: String): String? = synchronized(ioLock) {
        runCatching { AtomicTextFile.readOrNull(File(workspaceDir(id), relativePath)) }.getOrNull()
    }

    fun linkArtifact(id: String, type: String, artifactId: String, title: String = ""): WorkspaceManifest? {
        val manifest = get(id) ?: return null
        val updated = manifest.copy(
            updatedAt = System.currentTimeMillis(),
            linkedArtifacts = (manifest.linkedArtifacts.filterNot { it.type == type && it.id == artifactId } +
                WorkspaceArtifactLink(type = type, id = artifactId, title = title)).takeLast(20),
        )
        save(updated)
        return updated
    }

    fun writeCheckpoint(id: String, checkpoint: WorkspaceCheckpoint): String = synchronized(ioLock) {
        val file = File(checkpointsDir(id), "${checkpoint.timestamp}_${sanitizeName(checkpoint.label)}.json")
        AtomicTextFile.write(file, gson.toJson(checkpoint))
        touch(id, workingSetAdd = file.relativeTo(workspaceDir(id)).path)
        file.absolutePath
    }

    fun writeArtifactState(id: String, artifactState: WorkspaceArtifactState): String = synchronized(ioLock) {
        val name = "${artifactState.artifactType}_${artifactState.artifactId}_${artifactState.action.ifBlank { "state" }}"
        val file = File(artifactsDir(id), sanitizeName(name) + ".json")
        AtomicTextFile.write(file, gson.toJson(artifactState))
        touch(id, workingSetAdd = file.relativeTo(workspaceDir(id)).path)
        file.absolutePath
    }

    fun recordEvent(id: String, event: WorkspaceEvent): String = synchronized(ioLock) {
        val file = File(eventsDir(id), "${event.timestamp}_${sanitizeName(event.category)}_${sanitizeName(event.source)}.json")
        AtomicTextFile.write(file, gson.toJson(event))
        touch(id, workingSetAdd = file.relativeTo(workspaceDir(id)).path)
        file.absolutePath
    }

    fun recordRun(id: String, summary: String, success: Boolean, taskType: String = "") {
        synchronized(ioLock) {
            val file = File(runsDir(id), "${System.currentTimeMillis()}.json")
            AtomicTextFile.write(file, gson.toJson(WorkspaceRunRecord(summary = summary, success = success, taskType = taskType)))
            val manifest = get(id) ?: return
            save(manifest.copy(updatedAt = System.currentTimeMillis(), lastRunSummary = summary))
        }
    }

    fun summarize(id: String): String {
        val manifest = get(id) ?: return ""
        val latestCheckpoint = checkpointsDir(id).listFiles()?.maxByOrNull { it.lastModified() }?.name.orEmpty()
        return buildString {
            appendLine("Workspace: ${manifest.title} (${manifest.id})")
            appendLine("Goal: ${manifest.goal}")
            appendLine("Scope: ${manifest.scope.ifBlank { "none" }}")
            appendLine("Artifacts: ${manifest.linkedArtifacts.joinToString { "${it.type}:${it.id}" }.ifBlank { "none" }}")
            appendLine("Working set: ${manifest.workingSet.joinToString().ifBlank { "none" }}")
            appendLine("Last run: ${manifest.lastRunSummary.ifBlank { "none" }}")
            append("Latest checkpoint: ${latestCheckpoint.ifBlank { "none" }}")
        }.trim()
    }

    fun latestCheckpointContent(id: String): String? =
        latestCheckpoint(id)?.let { checkpoint ->
            buildString {
                appendLine("Label: ${checkpoint.label}")
                if (checkpoint.taskType.isNotBlank()) appendLine("Task type: ${checkpoint.taskType}")
                if (checkpoint.summary.isNotBlank()) appendLine("Summary: ${checkpoint.summary}")
                if (checkpoint.details.isNotBlank()) {
                    appendLine()
                    append(checkpoint.details)
                }
            }.trim()
        }

    fun latestNotes(id: String, limit: Int = 3): List<Pair<String, String>> = synchronized(ioLock) {
        notesDir(id).listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.mapNotNull { file -> runCatching { file.name to AtomicTextFile.readOrNull(file).orEmpty() }.getOrNull() }
            ?: emptyList()
    }

    fun latestCheckpoint(id: String): WorkspaceCheckpoint? = synchronized(ioLock) {
        checkpointsDir(id).listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.let { file -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), WorkspaceCheckpoint::class.java) }.getOrNull() }
    }

    fun recentCheckpoints(id: String, limit: Int = 8): List<WorkspaceCheckpoint> = synchronized(ioLock) {
        checkpointsDir(id).listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.mapNotNull { file -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), WorkspaceCheckpoint::class.java) }.getOrNull() }
            ?: emptyList()
    }

    fun latestArtifactState(id: String): WorkspaceArtifactState? = synchronized(ioLock) {
        artifactsDir(id).listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.let { file -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), WorkspaceArtifactState::class.java) }.getOrNull() }
    }

    fun recentArtifactStates(id: String, limit: Int = 8): List<WorkspaceArtifactState> = synchronized(ioLock) {
        artifactsDir(id).listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.mapNotNull { file -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), WorkspaceArtifactState::class.java) }.getOrNull() }
            ?: emptyList()
    }

    fun latestEvent(id: String): WorkspaceEvent? = synchronized(ioLock) {
        eventsDir(id).listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.let { file -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), WorkspaceEvent::class.java) }.getOrNull() }
    }

    fun recentEvents(id: String, limit: Int = 12): List<WorkspaceEvent> = synchronized(ioLock) {
        eventsDir(id).listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.take(limit)
            ?.mapNotNull { file -> runCatching { gson.fromJson(AtomicTextFile.readOrNull(file), WorkspaceEvent::class.java) }.getOrNull() }
            ?: emptyList()
    }

    fun executionContext(id: String): WorkspaceExecutionContext? {
        val manifest = get(id) ?: return null
        val checkpoint = latestCheckpoint(id)
        val artifact = latestArtifactState(id)
        val event = latestEvent(id)
        return WorkspaceExecutionContext(
            workspaceId = manifest.id,
            title = manifest.title,
            goal = manifest.goal,
            scope = manifest.scope,
            taskType = checkpoint?.taskType.orEmpty(),
            checkpointLabel = checkpoint?.label.orEmpty(),
            checkpointSummary = checkpoint?.summary.orEmpty(),
            latestArtifactType = artifact?.artifactType.orEmpty(),
            latestArtifactId = artifact?.artifactId.orEmpty(),
            latestArtifactTitle = artifact?.title.orEmpty(),
            latestArtifactAction = artifact?.action.orEmpty(),
            latestEventCategory = event?.category.orEmpty(),
            latestEventSummary = event?.summary.orEmpty(),
        )
    }

    fun inspectorSnapshot(id: String): WorkspaceInspectorSnapshot? {
        val manifest = get(id) ?: return null
        return WorkspaceInspectorSnapshot(
            manifest = manifest,
            execution = executionContext(id),
            recentCheckpoints = recentCheckpoints(id),
            recentEvents = recentEvents(id),
            recentArtifacts = recentArtifactStates(id),
            recentNotes = latestNotes(id, limit = 4),
        )
    }

    private fun touch(id: String, workingSetAdd: String? = null) {
        val manifest = get(id) ?: return
        val newWorkingSet = (manifest.workingSet + listOfNotNull(workingSetAdd)).distinct().takeLast(16)
        save(manifest.copy(updatedAt = System.currentTimeMillis(), workingSet = newWorkingSet))
    }

    private fun save(manifest: WorkspaceManifest) {
        ensureDirs(manifest.id)
        AtomicTextFile.write(File(workspaceDir(manifest.id), "manifest.json"), gson.toJson(manifest))
    }

    private fun workspaceDir(id: String) = File(root, id)
    private fun notesDir(id: String) = File(workspaceDir(id), "notes").also { it.mkdirs() }
    private fun scratchDir(id: String) = File(workspaceDir(id), "scratch").also { it.mkdirs() }
    private fun artifactsDir(id: String) = File(workspaceDir(id), "artifacts").also { it.mkdirs() }
    private fun runsDir(id: String) = File(workspaceDir(id), "runs").also { it.mkdirs() }
    private fun checkpointsDir(id: String) = File(workspaceDir(id), "checkpoints").also { it.mkdirs() }
    private fun eventsDir(id: String) = File(workspaceDir(id), "events").also { it.mkdirs() }

    private fun ensureDirs(id: String) {
        listOf(
            workspaceDir(id),
            notesDir(id),
            scratchDir(id),
            File(workspaceDir(id), "outputs"),
            artifactsDir(id),
            runsDir(id),
            File(workspaceDir(id), "cache"),
            checkpointsDir(id),
            File(workspaceDir(id), "indexes"),
            eventsDir(id),
        ).forEach { it.mkdirs() }
    }

    private fun sanitizeName(raw: String): String =
        raw.trim().lowercase().replace(Regex("[^a-z0-9_\\-]+"), "_").trim('_').ifBlank { "untitled" }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim().lowercase()
        val base = trimmed.substringBeforeLast('.', missingDelimiterValue = trimmed)
        val ext = trimmed.substringAfterLast('.', missingDelimiterValue = "")
            .replace(Regex("[^a-z0-9]+"), "")
            .take(8)
        return listOf(sanitizeName(base), ext.takeIf { it.isNotBlank() })
            .filterNotNull()
            .joinToString(".")
            .ifBlank { "untitled" }
    }
}
