package com.mobileclaw.config

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages access to the user's external storage.
 *
 * On Android 11+ (API 30+): uses MANAGE_EXTERNAL_STORAGE — requires the user to
 * navigate to Settings → Special App Access → All Files Access and grant permission.
 *
 * On older versions: falls back to READ/WRITE_EXTERNAL_STORAGE which is granted at
 * install time (declared in manifest).
 */
class UserStorageManager(private val context: Context) {

    // ── Permission state ──────────────────────────────────────────────────────

    fun hasAllFilesAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true // READ/WRITE_EXTERNAL_STORAGE covers this on older Android
    }

    /** Returns an Intent that opens the system "All Files Access" settings for this app. */
    fun allFilesAccessSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    // ── Well-known directories ────────────────────────────────────────────────

    data class StorageDir(val name: String, val path: String, val emoji: String)

    fun wellKnownDirs(): List<StorageDir> {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val r = context.resources
        return listOf(
            StorageDir(r.getString(com.mobileclaw.R.string.storage_downloads), "$root/Download", "📥"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_documents), "$root/Documents", "📄"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_pictures), "$root/Pictures", "🖼️"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_screenshots), "$root/Pictures/Screenshots", "📸"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_camera), "$root/DCIM/Camera", "📷"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_music), "$root/Music", "🎵"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_movies), "$root/Movies", "🎬"),
            StorageDir(r.getString(com.mobileclaw.R.string.storage_root), root, "📱"),
        ).filter { File(it.path).exists() }
    }

    // ── File operations ───────────────────────────────────────────────────────

    data class FileEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModified: Long,
    )

    suspend fun listDir(path: String): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(path)
            require(dir.exists()) { "路径不存在: $path" }
            require(dir.isDirectory) { "不是目录: $path" }
            (dir.listFiles() ?: emptyArray()).map { f ->
                FileEntry(f.name, f.absolutePath, f.isDirectory, if (f.isFile) f.length() else 0L, f.lastModified())
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    suspend fun readFile(path: String, maxBytes: Int = 512_000): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            require(file.exists()) { "文件不存在: $path" }
            require(file.isFile) { "不是文件: $path" }
            if (file.length() > maxBytes) {
                file.inputStream().use { it.readNBytes(maxBytes).toString(Charsets.UTF_8) } +
                    "\n\n[文件过大，仅显示前 ${maxBytes / 1024} KB]"
            } else {
                file.readText()
            }
        }
    }

    suspend fun writeFile(path: String, content: String, createDirs: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (createDirs) file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
    }

    suspend fun deleteFile(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    suspend fun createDir(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { File(path).mkdirs(); Unit }
    }

    suspend fun copyFile(src: String, dst: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val srcFile = File(src)
            val dstFile = File(dst)
            require(srcFile.exists()) { "源文件不存在: $src" }
            dstFile.parentFile?.mkdirs()
            srcFile.copyTo(dstFile, overwrite = true)
            Unit
        }
    }

    suspend fun moveFile(src: String, dst: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val srcFile = File(src)
            val dstFile = File(dst)
            require(srcFile.exists()) { "源文件不存在: $src" }
            dstFile.parentFile?.mkdirs()
            if (!srcFile.renameTo(dstFile)) {
                srcFile.copyTo(dstFile, overwrite = true)
                srcFile.delete()
            }
        }
    }

    /** Recursively search for files whose name contains [query] (case-insensitive). */
    suspend fun searchFiles(rootPath: String, query: String, maxResults: Int = 50): Result<List<FileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val results = mutableListOf<FileEntry>()
            val lowerQuery = query.lowercase()
            fun walk(dir: File) {
                if (results.size >= maxResults) return
                dir.listFiles()?.forEach { f ->
                    if (results.size >= maxResults) return
                    if (f.name.lowercase().contains(lowerQuery)) {
                        results += FileEntry(f.name, f.absolutePath, f.isDirectory, if (f.isFile) f.length() else 0L, f.lastModified())
                    }
                    if (f.isDirectory) walk(f)
                }
            }
            walk(File(rootPath))
            results
        }
    }
}
