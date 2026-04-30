package com.mobileclaw.agent

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Loads and persists custom roles. Built-in roles are always included. */
class RoleManager(private val context: Context) {

    private val gson = Gson()
    private val rolesDir: File get() = context.filesDir.resolve("roles").also { it.mkdirs() }

    private val _rolesFlow = MutableStateFlow(all())

    /** Emits the full role list whenever a role is saved or deleted. */
    val rolesFlow: StateFlow<List<Role>> = _rolesFlow.asStateFlow()

    fun all(): List<Role> = Role.BUILTINS + customRoles()

    fun get(id: String): Role? = all().firstOrNull { it.id == id }

    fun getDefault(): Role = Role.DEFAULT

    fun customRoles(): List<Role> = rolesDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { file ->
            runCatching { gson.fromJson(file.readText(), Role::class.java) }.getOrNull()
        } ?: emptyList()

    fun save(role: Role) {
        if (role.isBuiltin) return
        File(rolesDir, "${role.id}.json").writeText(gson.toJson(role))
        _rolesFlow.value = all()
    }

    fun delete(id: String) {
        if (Role.BUILTINS.any { it.id == id }) return
        File(rolesDir, "$id.json").takeIf { it.exists() }?.delete()
        _rolesFlow.value = all()
    }
}
