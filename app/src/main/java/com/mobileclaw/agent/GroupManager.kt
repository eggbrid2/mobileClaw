package com.mobileclaw.agent

import android.content.Context
import com.google.gson.Gson
import java.io.File

class GroupManager(private val context: Context) {

    private val gson = Gson()
    private val groupsDir: File
        get() = File(context.filesDir, "groups").also { it.mkdirs() }

    fun all(): List<Group> = groupsDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { runCatching { gson.fromJson(it.readText(), Group::class.java) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        ?: emptyList()

    fun get(id: String): Group? = runCatching {
        gson.fromJson(File(groupsDir, "$id.json").readText(), Group::class.java)
    }.getOrNull()

    fun save(group: Group) {
        File(groupsDir, "${group.id}.json").writeText(gson.toJson(group))
    }

    fun delete(id: String) {
        File(groupsDir, "$id.json").delete()
    }

    fun touch(id: String) {
        val g = get(id) ?: return
        save(g.copy(updatedAt = System.currentTimeMillis()))
    }
}
