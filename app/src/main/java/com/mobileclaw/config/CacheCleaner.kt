package com.mobileclaw.config

import android.content.Context
import androidx.annotation.StringRes
import com.mobileclaw.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CacheCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val sizeBytes: Long,
    val pathCount: Int,
)

class CacheCleaner(private val context: Context) {
    suspend fun scan(): List<CacheCategory> = withContext(Dispatchers.IO) {
        categories().map { spec ->
            val existing = spec.paths.filter { it.exists() }
            CacheCategory(
                id = spec.id,
                titleRes = spec.titleRes,
                subtitleRes = spec.subtitleRes,
                sizeBytes = existing.sumOf { it.safeSize() },
                pathCount = existing.size,
            )
        }
    }

    suspend fun clear(categoryId: String): Long = withContext(Dispatchers.IO) {
        categories().firstOrNull { it.id == categoryId }?.paths
            ?.filter { it.exists() }
            ?.sumOf { path ->
                val size = path.safeSize()
                path.safeClear()
                size
            } ?: 0L
    }

    suspend fun clearAll(): Long = withContext(Dispatchers.IO) {
        categories().sumOf { spec ->
            spec.paths.filter { it.exists() }.sumOf { path ->
                val size = path.safeSize()
                path.safeClear()
                size
            }
        }
    }

    private fun categories(): List<CacheSpec> {
        val externalCreated = context.getExternalFilesDir("created_files")
        val externalHtml = context.getExternalFilesDir("html_pages")
        return listOf(
            CacheSpec(
                id = "temp",
                titleRes = R.string.cache_temp_title,
                subtitleRes = R.string.cache_temp_subtitle,
                paths = listOfNotNull(context.cacheDir, context.externalCacheDir),
            ),
            CacheSpec(
                id = "vpn",
                titleRes = R.string.cache_vpn_title,
                subtitleRes = R.string.cache_vpn_subtitle,
                paths = context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith("mihomo-latency-") }
                    .orEmpty() + context.filesDir.listFiles()
                    ?.filter { it.name.startsWith("mihomo-runtime-") && it.extension == "yml" }
                    .orEmpty(),
            ),
            CacheSpec(
                id = "stickers",
                titleRes = R.string.cache_stickers_title,
                subtitleRes = R.string.cache_stickers_subtitle,
                paths = listOf(File(context.filesDir, "stickers")),
            ),
            CacheSpec(
                id = "documents",
                titleRes = R.string.cache_documents_title,
                subtitleRes = R.string.cache_documents_subtitle,
                paths = listOf(File(context.filesDir, "documents")),
            ),
            CacheSpec(
                id = "videos",
                titleRes = R.string.cache_videos_title,
                subtitleRes = R.string.cache_videos_subtitle,
                paths = listOf(File(context.filesDir, "videos")),
            ),
            CacheSpec(
                id = "html",
                titleRes = R.string.cache_html_title,
                subtitleRes = R.string.cache_html_subtitle,
                paths = listOfNotNull(File(context.filesDir, "html_pages"), externalHtml),
            ),
            CacheSpec(
                id = "created_files",
                titleRes = R.string.cache_created_files_title,
                subtitleRes = R.string.cache_created_files_subtitle,
                paths = listOfNotNull(File(context.filesDir, "created_files"), externalCreated),
            ),
            CacheSpec(
                id = "python_packages",
                titleRes = R.string.cache_python_title,
                subtitleRes = R.string.cache_python_subtitle,
                paths = listOf(File(context.filesDir, "pip_packages")),
            ),
        )
    }

    private data class CacheSpec(
        val id: String,
        @StringRes val titleRes: Int,
        @StringRes val subtitleRes: Int,
        val paths: List<File>,
    )
}

private fun File.safeSize(): Long = runCatching {
    if (!exists()) 0L else if (isFile) length() else walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun File.safeClear() {
    runCatching {
        if (!exists()) return
        if (isFile) {
            delete()
        } else {
            listFiles()?.forEach { child -> child.deleteRecursively() }
        }
    }
}
