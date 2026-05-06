package com.mobileclaw.agent

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Loads and persists custom roles. Built-in roles are always included.
 *  Built-ins can be overridden by saving a file with the same ID; restore() deletes the override. */
class RoleManager(private val context: Context) {

    private val gson = Gson()
    private val rolesDir: File get() = context.filesDir.resolve("roles").also { it.mkdirs() }

    private val builtinIds = Role.BUILTINS.map { it.id }.toSet()

    private val _rolesFlow = MutableStateFlow(all())

    /** Emits the full role list whenever a role is saved or deleted. */
    val rolesFlow: StateFlow<List<Role>> = _rolesFlow.asStateFlow()

    fun all(): List<Role> {
        val fileMap = rolesDir.listFiles { f -> f.extension == "json" }
            ?.associate { f ->
                f.nameWithoutExtension to runCatching { gson.fromJson(f.readText(), Role::class.java) }.getOrNull()
            } ?: emptyMap()

        // Built-ins: use override file if present, otherwise use canonical definition.
        // Always keep isBuiltin = true so the UI knows it's a built-in.
        val builtins = Role.BUILTINS.map { builtin ->
            fileMap[builtin.id]?.copy(isBuiltin = true) ?: builtin
        }

        // Custom roles: files whose ID is not a built-in ID
        val custom = fileMap.values.filterNotNull().filter { it.id !in builtinIds }

        return builtins + custom
    }

    fun get(id: String): Role? = all().firstOrNull { it.id == id }

    fun getDefault(): Role = Role.DEFAULT

    fun customRoles(): List<Role> = rolesDir.listFiles { f -> f.extension == "json" }
        ?.mapNotNull { file ->
            runCatching { gson.fromJson(file.readText(), Role::class.java) }.getOrNull()
                ?.takeIf { it.id !in builtinIds }
        } ?: emptyList()

    /** Save any role (including built-in overrides). */
    fun save(role: Role) {
        File(rolesDir, "${role.id}.json").writeText(gson.toJson(role))
        _rolesFlow.value = all()
    }

    /** Restore a built-in to its default by deleting the override file. No-op for custom roles. */
    fun restore(id: String) {
        if (id !in builtinIds) return
        File(rolesDir, "$id.json").takeIf { it.exists() }?.delete()
        _rolesFlow.value = all()
    }

    fun delete(id: String) {
        if (id in builtinIds) return  // use restore() to reset built-ins
        File(rolesDir, "$id.json").takeIf { it.exists() }?.delete()
        _rolesFlow.value = all()
    }
}
