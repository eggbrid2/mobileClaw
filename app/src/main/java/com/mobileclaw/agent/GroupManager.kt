package com.mobileclaw.agent

import android.content.Context
import com.google.gson.Gson
import com.mobileclaw.storage.AtomicTextFile
import java.io.File

class GroupManager(private val context: Context) {

    private val gson = Gson()
    private val ioLock = Any()
    private val groupsDir: File
        get() = File(context.filesDir, "groups").also { it.mkdirs() }

    fun all(): List<Group> = synchronized(ioLock) {
        groupsDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { gson.fromJson(AtomicTextFile.readOrNull(it), Group::class.java) }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun get(id: String): Group? = synchronized(ioLock) {
        runCatching {
            gson.fromJson(AtomicTextFile.readOrNull(File(groupsDir, "$id.json")), Group::class.java)
        }.getOrNull()
    }

    fun save(group: Group) {
        synchronized(ioLock) {
            AtomicTextFile.write(File(groupsDir, "${group.id}.json"), gson.toJson(group))
        }
    }

    fun delete(id: String) {
        File(groupsDir, "$id.json").delete()
    }

    fun touch(id: String) {
        val g = get(id) ?: return
        save(g.copy(updatedAt = System.currentTimeMillis()))
    }
}
