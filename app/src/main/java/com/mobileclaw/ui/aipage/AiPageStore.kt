package com.mobileclaw.ui.aipage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobileclaw.artifact.ArtifactHistoryEntry
import com.mobileclaw.artifact.PortableArtifactEntry
import com.mobileclaw.artifact.PortableArtifactPackageManifest
import com.mobileclaw.artifact.PortableArtifactTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class AiPagePackageImportOptions(
    val preferredId: String = "",
    val overwrite: Boolean = false,
)

data class AiPagePackageImportResult(
    val page: AiPageDef,
    val originalId: String,
    val importedId: String,
    val idChanged: Boolean,
    val overwritten: Boolean,
    val warnings: List<String> = emptyList(),
)

/** File-based persistence for AI-created native pages. Thread-safe. */
class AiPageStore(private val filesDir: File) {

    private val dir = File(filesDir, "ai_pages").also { it.mkdirs() }
    private val exportsDir = File(filesDir, "workspace_exports/ai_pages").also { it.mkdirs() }
    private val gson: Gson = GsonBuilder().create()
    private val prettyGson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val _pages = MutableStateFlow<List<AiPageDef>>(emptyList())
    val pages: StateFlow<List<AiPageDef>> = _pages.asStateFlow()

    init { reload() }

    fun save(def: AiPageDef) {
        File(dir, "${def.id}.json").writeText(gson.toJson(def))
        reload()
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        reload()
    }

    fun get(id: String): AiPageDef? =
        runCatching {
            val f = File(dir, "$id.json")
            if (f.exists()) gson.fromJson(f.readText(), AiPageDef::class.java) else null
        }.getOrNull()

    fun getAll(): List<AiPageDef> = _pages.value

    fun exportPackage(id: String, targetFile: File? = null): File {
        val page = get(id) ?: throw IllegalArgumentException("AI page not found: $id")
        val pageJson = prettyGson.toJson(page)
        val manifest = PortableArtifactPackageManifest(
            packageType = PortableArtifactTypes.AI_PAGE,
            artifactId = page.id,
            title = page.title,
            entries = listOf(
                PortableArtifactEntry("manifest.json", "manifest", size = -1),
                PortableArtifactEntry("ai_page.json", "ai_page_definition", size = pageJson.toByteArray(Charsets.UTF_8).size.toLong()),
            ),
            metadata = mapOf(
                "description" to page.description,
                "icon" to page.icon,
                "format" to AI_PAGE_PACKAGE_EXTENSION,
                "version" to page.version.toString(),
            ),
        )
        val outFile = targetFile ?: File(exportsDir, "${sanitizePackageName(page.title.ifBlank { page.id })}.$AI_PAGE_PACKAGE_EXTENSION")
        outFile.parentFile?.mkdirs()
        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            zip.writeTextEntry("manifest.json", prettyGson.toJson(manifest))
            zip.writeTextEntry("ai_page.json", pageJson)
        }
        return outFile
    }

    fun importPackage(packageFile: File, options: AiPagePackageImportOptions = AiPagePackageImportOptions()): AiPagePackageImportResult {
        val warnings = mutableListOf<String>()
        ZipFile(packageFile).use { zip ->
            val manifest = zip.readJson("manifest.json", PortableArtifactPackageManifest::class.java)
                ?: throw IllegalArgumentException("Package manifest.json is missing or invalid")
            require(manifest.packageType == PortableArtifactTypes.AI_PAGE) {
                "Unsupported package type: ${manifest.packageType}"
            }
            require(manifest.schemaVersion == 1) {
                "Unsupported package schema version: ${manifest.schemaVersion}"
            }
            val packagedPage = zip.readJson("ai_page.json", AiPageDef::class.java)
                ?: throw IllegalArgumentException("ai_page.json is missing or invalid")
            val originalId = packagedPage.id.ifBlank { manifest.artifactId }
            val targetId = resolveImportId(options.preferredId, originalId, packagedPage.title, options.overwrite)
            val overwritten = options.overwrite && get(targetId) != null
            val importedPage = packagedPage.copy(
                id = targetId,
                updatedAt = System.currentTimeMillis(),
                history = packagedPage.history + ArtifactHistoryEntry(
                    action = "import",
                    request = "Imported from ${packageFile.name}",
                    summary = if (targetId == originalId) {
                        "Imported AI page package."
                    } else {
                        "Imported AI page package from original id '$originalId'."
                    },
                ),
            )
            save(importedPage)
            return AiPagePackageImportResult(
                page = get(targetId) ?: importedPage,
                originalId = originalId,
                importedId = targetId,
                idChanged = targetId != originalId,
                overwritten = overwritten,
                warnings = warnings,
            )
        }
    }

    private fun reload() {
        val list = dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { gson.fromJson(f.readText(), AiPageDef::class.java) }.getOrNull() }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
        _pages.value = list
    }

    private fun resolveImportId(preferredId: String, originalId: String, title: String, overwrite: Boolean): String {
        val base = sanitizeArtifactId(preferredId.ifBlank { originalId.ifBlank { title } })
            .ifBlank { "page_${UUID.randomUUID().toString().take(8)}" }
        if (overwrite || get(base) == null) return base
        repeat(50) { index ->
            val candidate = "${base}_${index + 2}"
            if (get(candidate) == null) return candidate
        }
        return "${base}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun ZipOutputStream.writeTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun <T> ZipFile.readJson(name: String, clazz: Class<T>): T? {
        val entry = getEntry(name) ?: return null
        return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
            runCatching { gson.fromJson(reader, clazz) }.getOrNull()
        }
    }

    private fun sanitizePackageName(raw: String): String =
        raw.trim().lowercase()
            .replace(Regex("[^a-z0-9_\\-\\u4e00-\\u9fa5]+"), "_")
            .trim('_')
            .ifBlank { "ai_page" }

    private fun sanitizeArtifactId(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(48)

    companion object {
        const val AI_PAGE_PACKAGE_EXTENSION = "mobileclaw-aipage"
    }
}
