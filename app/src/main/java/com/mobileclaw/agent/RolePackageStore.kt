package com.mobileclaw.agent

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mobileclaw.artifact.PortableArtifactEntry
import com.mobileclaw.artifact.PortableArtifactPackageManifest
import com.mobileclaw.artifact.PortableArtifactTypes
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class RolePackageImportOptions(
    val preferredId: String = "",
    val overwrite: Boolean = false,
)

data class RolePackageImportResult(
    val role: Role,
    val originalId: String,
    val importedId: String,
    val idChanged: Boolean,
    val overwritten: Boolean,
    val warnings: List<String> = emptyList(),
)

class RolePackageStore(
    private val context: Context,
    private val roleManager: RoleManager,
    private val roleWorkspaceStore: RoleWorkspaceStore,
) {
    private val gson: Gson = Gson()
    private val prettyGson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val ioLock = Any()
    private val exportsDir: File get() = File(context.filesDir, "workspace_exports/roles").also { it.mkdirs() }
    private val avatarDir: File get() = File(context.filesDir, "role_avatars").also { it.mkdirs() }

    fun exportPackage(roleId: String, targetFile: File? = null): File = synchronized(ioLock) {
        val role = roleManager.get(roleId) ?: throw IllegalArgumentException("Role not found: $roleId")
        roleWorkspaceStore.ensure(role)
        val workspaceFiles = roleWorkspaceStore.list(role.id)
        val avatarAsset = readAvatarAsset(role.avatar)
        val entries = buildList {
            add(PortableArtifactEntry("manifest.json", "manifest", size = -1))
            add(PortableArtifactEntry("role.json", "role_definition", size = -1))
            workspaceFiles.forEach { relative ->
                val contentSize = roleWorkspaceStore.read(role.id, relative).orEmpty().toByteArray(Charsets.UTF_8).size.toLong()
                add(PortableArtifactEntry("role_workspace/$relative", "role_workspace_file", required = false, size = contentSize))
            }
            avatarAsset?.let { add(PortableArtifactEntry(it.path, "avatar_asset", required = false, size = it.bytes.size.toLong())) }
        }
        val manifest = PortableArtifactPackageManifest(
            packageType = PortableArtifactTypes.ROLE,
            artifactId = role.id,
            title = role.name,
            entries = entries,
            dependencies = role.forcedSkillIds.map { skillId ->
                com.mobileclaw.artifact.PortableArtifactDependency(type = "skill", id = skillId, required = false)
            },
            metadata = mapOf(
                "description" to role.description,
                "format" to ROLE_PACKAGE_EXTENSION,
                "includesTown" to "false",
                "includesAvatarAsset" to (avatarAsset != null).toString(),
            ),
        )
        val outFile = targetFile ?: File(exportsDir, "${sanitizePackageName(role.name.ifBlank { role.id })}.$ROLE_PACKAGE_EXTENSION")
        outFile.parentFile?.mkdirs()
        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            zip.writeTextEntry("manifest.json", prettyGson.toJson(manifest))
            zip.writeTextEntry("role.json", prettyGson.toJson(role))
            workspaceFiles.forEach { relative ->
                zip.writeTextEntry("role_workspace/$relative", roleWorkspaceStore.read(role.id, relative).orEmpty())
            }
            avatarAsset?.let { zip.writeBytesEntry(it.path, it.bytes) }
        }
        outFile
    }

    fun importPackage(packageFile: File, options: RolePackageImportOptions = RolePackageImportOptions()): RolePackageImportResult =
        synchronized(ioLock) {
            val warnings = mutableListOf<String>()
            ZipFile(packageFile).use { zip ->
                val manifest = zip.readJson("manifest.json", PortableArtifactPackageManifest::class.java)
                    ?: throw IllegalArgumentException("Package manifest.json is missing or invalid")
                require(manifest.packageType == PortableArtifactTypes.ROLE) {
                    "Unsupported package type: ${manifest.packageType}"
                }
                require(manifest.schemaVersion == 1) {
                    "Unsupported package schema version: ${manifest.schemaVersion}"
                }
                val packagedRole = zip.readJson("role.json", Role::class.java)
                    ?: throw IllegalArgumentException("role.json is missing or invalid")
                val originalId = packagedRole.id.ifBlank { manifest.artifactId }
                val targetId = resolveImportId(options.preferredId, originalId, packagedRole.name, options.overwrite)
                val existing = roleManager.get(targetId)
                if (options.overwrite && existing?.isBuiltin == true) {
                    throw IllegalArgumentException("Cannot overwrite builtin role: $targetId")
                }
                val overwritten = options.overwrite && existing != null
                val importedAvatar = restoreAvatarAsset(zip, targetId, warnings)
                    ?: normalizeRoleAvatar(targetId, packagedRole.avatar)
                val importedRole = packagedRole.copy(
                    id = targetId,
                    avatar = importedAvatar,
                    isBuiltin = false,
                )
                roleManager.save(importedRole)
                importWorkspace(zip, targetId, warnings)
                RolePackageImportResult(
                    role = roleManager.get(targetId) ?: importedRole,
                    originalId = originalId,
                    importedId = targetId,
                    idChanged = targetId != originalId,
                    overwritten = overwritten,
                    warnings = warnings,
                )
            }
        }

    private fun resolveImportId(preferredId: String, originalId: String, title: String, overwrite: Boolean): String {
        val base = sanitizeRoleId(preferredId.ifBlank { originalId.ifBlank { title } })
            .ifBlank { "role_${UUID.randomUUID().toString().take(8)}" }
        val existing = roleManager.get(base)
        if (existing == null || (overwrite && !existing.isBuiltin)) return base
        repeat(50) { index ->
            val candidate = "${base}_${index + 2}"
            if (roleManager.get(candidate) == null) return candidate
        }
        return "${base}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun importWorkspace(zip: ZipFile, targetId: String, warnings: MutableList<String>) {
        zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith("role_workspace/") }
            .forEach { entry ->
                val relative = entry.name.removePrefix("role_workspace/")
                if (!isSafeRelativePath(relative)) {
                    warnings += "Skipped unsafe workspace path: ${entry.name}"
                    return@forEach
                }
                val content = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                roleWorkspaceStore.write(targetId, relative, content)
            }
    }

    private fun restoreAvatarAsset(zip: ZipFile, targetId: String, warnings: MutableList<String>): String? {
        val entry = zip.entries().asSequence()
            .firstOrNull { !it.isDirectory && it.name.startsWith("assets/avatar.") }
            ?: return null
        val ext = entry.name.substringAfterLast('.', "png").lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "png" }
        val outFile = File(avatarDir, "${sanitizeRoleId(targetId)}.$ext")
        return runCatching {
            outFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
            "file://${outFile.absolutePath}"
        }.onFailure {
            warnings += "Failed to restore avatar asset: ${it.message.orEmpty()}"
        }.getOrNull()
    }

    private fun readAvatarAsset(avatar: String): AvatarAsset? {
        val value = avatar.trim()
        if (value.isBlank() || value.startsWith("role:")) return null
        return when {
            value.startsWith("data:", ignoreCase = true) -> readDataUriAvatar(value)
            value.startsWith("content://", ignoreCase = true) -> readContentAvatar(Uri.parse(value))
            value.startsWith("file://", ignoreCase = true) -> readFileAvatar(File(Uri.parse(value).path.orEmpty()))
            value.startsWith("/") -> readFileAvatar(File(value))
            else -> null
        }
    }

    private fun readDataUriAvatar(value: String): AvatarAsset? {
        val comma = value.indexOf(',')
        if (comma <= 0) return null
        val header = value.substring(0, comma)
        if (!header.contains(";base64", ignoreCase = true)) return null
        val mime = header.substringAfter("data:", "").substringBefore(";").ifBlank { "image/png" }
        val ext = extensionFromMime(mime) ?: "png"
        val bytes = runCatching { Base64.decode(value.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return null
        return AvatarAsset("assets/avatar.$ext", bytes)
    }

    private fun readContentAvatar(uri: Uri): AvatarAsset? {
        val mime = context.contentResolver.getType(uri)
        val ext = extensionFromMime(mime) ?: "png"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return AvatarAsset("assets/avatar.$ext", bytes)
    }

    private fun readFileAvatar(file: File): AvatarAsset? {
        if (!file.exists() || !file.isFile) return null
        val ext = file.extension.lowercase().filter { it.isLetterOrDigit() }.take(8).ifBlank { "png" }
        return AvatarAsset("assets/avatar.$ext", file.readBytes())
    }

    private fun extensionFromMime(mime: String?): String? =
        mime?.substringAfter('/', "")?.lowercase()?.let { raw ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf { it.isNotBlank() }
                ?: raw.takeIf { it in setOf("png", "jpg", "jpeg", "webp", "gif") }
        }

    private fun isSafeRelativePath(path: String): Boolean {
        val clean = path.replace('\\', '/')
        return clean.isNotBlank() && clean.split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun ZipOutputStream.writeTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.writeBytesEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
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
            .ifBlank { "role" }

    private fun sanitizeRoleId(raw: String): String =
        raw.trim().lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(48)

    private data class AvatarAsset(val path: String, val bytes: ByteArray)

    companion object {
        const val ROLE_PACKAGE_EXTENSION = "mobileclaw-role"
    }
}
