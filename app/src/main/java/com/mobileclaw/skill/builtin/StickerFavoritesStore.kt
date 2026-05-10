package com.mobileclaw.skill.builtin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mobileclaw.skill.SkillAttachment

class StickerFavoritesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sticker_favorites", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<FavoriteSticker>>() {}.type

    fun all(): List<FavoriteSticker> {
        val raw = prefs.getString(KEY, "[]").orEmpty()
        return runCatching { gson.fromJson<List<FavoriteSticker>>(raw, listType) }.getOrDefault(emptyList())
    }

    fun isFavorite(path: String): Boolean = all().any { it.path == path }

    fun toggle(file: SkillAttachment.FileData): Boolean {
        val current = all().toMutableList()
        val index = current.indexOfFirst { it.path == file.path }
        val added = index < 0
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, FavoriteSticker(file.path, file.name, file.mimeType, file.sizeBytes, System.currentTimeMillis()))
        }
        prefs.edit().putString(KEY, gson.toJson(current.take(200))).apply()
        return added
    }

    data class FavoriteSticker(
        val path: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val createdAt: Long,
    ) {
        fun toFileData() = SkillAttachment.FileData(path, name, mimeType, sizeBytes)
    }

    private companion object {
        const val KEY = "items"
    }
}
