package com.mobileclaw.agent

import android.content.Context
import com.google.gson.Gson
import java.io.File

/** Loads and persists custom roles. Built-in roles are always included. */
class RoleManager(private val context: Context) {

    private val gson = Gson()
    private val rolesDir: File get() = context.filesDir.resolve("roles").also { it.mkdirs() }

    fun all(): List<Role> = Role.BUILTINS + customRoles()

    fun get(id: String): Role? = all().firstOrNull { it.id == id }

    fun getDefault(): Role = Role.DEFAULT

    fun customRoles(): List<Role> = rolesDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { file ->
            runCatching { gson.fromJson(file.readText(), Role::class.java) }.getOrNull()
        } ?: emptyList()

    fun save(role: Role) {
        if (role.isBuiltin) return  // never overwrite builtins
        val file = File(rolesDir, "${role.id}.json")
        file.writeText(gson.toJson(role))
    }

    fun delete(id: String) {
        if (Role.BUILTINS.any { it.id == id }) return  // protect builtins
        File(rolesDir, "$id.json").takeIf { it.exists() }?.delete()
    }
}
