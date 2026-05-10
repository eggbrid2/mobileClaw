package com.mobileclaw.skill.builtin

import com.google.gson.JsonParser
import com.mobileclaw.ClawApplication
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.vpn.AppHttpProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ChineseBqbStickerRepository {
    data class Entry(
        val name: String,
        val category: String,
        val url: String,
    )

    suspend fun search(
        app: ClawApplication,
        query: String,
        category: String = "",
        limit: Int = 50,
    ): List<Entry> = withContext(Dispatchers.IO) {
        searchEntries(app, query, category, limit)
    }

    suspend fun categories(app: ClawApplication, limit: Int = 50): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        loadIndex(app).groupingBy { it.category }.eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    suspend fun refresh(app: ClawApplication): List<Entry> = withContext(Dispatchers.IO) {
        loadIndex(app, forceRefresh = true)
    }

    suspend fun download(app: ClawApplication, entry: Entry): SkillAttachment.FileData = withContext(Dispatchers.IO) {
        downloadSticker(app, entry)
    }

    private fun searchEntries(app: ClawApplication, query: String, category: String, limit: Int): List<Entry> {
        val entries = loadIndex(app)
        val q = query.lowercase()
        val c = category.lowercase()
        val filtered = entries.asSequence()
            .filter { c.isBlank() || it.category.lowercase().contains(c) }
            .filter {
                q.isBlank() ||
                    it.name.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
            }
            .take(limit)
            .toList()
        return if (filtered.isNotEmpty() || q.isBlank()) filtered else entries.shuffled().take(limit)
    }

    private fun loadIndex(app: ClawApplication, forceRefresh: Boolean = false): List<Entry> {
        val cache = File(app.filesDir, "stickers/chinese_bqb/index.json").also { it.parentFile?.mkdirs() }
        val maxAgeMs = 7L * 24 * 60 * 60 * 1000
        if (forceRefresh || !cache.exists() || System.currentTimeMillis() - cache.lastModified() > maxAgeMs) {
            val body = http.newCall(Request.Builder().url(INDEX_URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            cache.writeText(body)
        }
        val root = JsonParser.parseString(cache.readText()).asJsonObject
        return root["data"].asJsonArray.mapNotNull { el ->
            runCatching {
                val obj = el.asJsonObject
                Entry(
                    name = obj["name"]?.asString.orEmpty(),
                    category = obj["category"]?.asString.orEmpty(),
                    url = obj["url"]?.asString.orEmpty(),
                )
            }.getOrNull()
        }.filter { it.url.isNotBlank() }
    }

    private fun downloadSticker(app: ClawApplication, entry: Entry): SkillAttachment.FileData {
        val ext = entry.name.substringAfterLast('.', "jpg").lowercase().takeIf { it.length <= 5 } ?: "jpg"
        val cache = File(app.filesDir, "stickers/chinese_bqb/images/${sha1(entry.url)}.$ext").also { it.parentFile?.mkdirs() }
        if (!cache.exists()) {
            val bytes = http.newCall(Request.Builder().url(entry.url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("image HTTP ${resp.code}")
                resp.body?.bytes() ?: ByteArray(0)
            }
            if (bytes.isEmpty()) error("empty sticker image")
            cache.writeBytes(bytes)
        }
        val mime = when (ext) {
            "gif" -> "image/gif"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return SkillAttachment.FileData(
            path = cache.absolutePath,
            name = entry.name.ifBlank { cache.name },
            mimeType = mime,
            sizeBytes = cache.length(),
        )
    }

    private fun sha1(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private const val INDEX_URL = "https://raw.githubusercontent.com/zhaoolee/ChineseBQB/master/chinesebqb_github.json"
    private val http: OkHttpClient = OkHttpClient.Builder()
        .proxySelector(AppHttpProxy.proxySelector())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
