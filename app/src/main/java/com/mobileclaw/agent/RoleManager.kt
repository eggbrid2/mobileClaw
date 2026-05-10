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
                f.nameWithoutExtension to runCatching { normalize(gson.fromJson(f.readText(), Role::class.java)) }.getOrNull()
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
            runCatching { normalize(gson.fromJson(file.readText(), Role::class.java)) }.getOrNull()
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

    private fun normalize(role: Role): Role {
        return role.copy(
            systemPromptAddendum = role.systemPromptAddendum ?: "",
            forcedSkillIds = role.forcedSkillIds ?: emptyList(),
            preferredTaskTypes = role.preferredTaskTypes ?: emptyList(),
            keywords = role.keywords ?: emptyList(),
            chatBubbleStyle = normalizeBubbleStyle(role.chatBubbleStyle ?: ChatBubbleStyle()),
        )
    }

    private fun normalizeBubbleStyle(style: ChatBubbleStyle): ChatBubbleStyle {
        return style.copy(
            preset = style.preset.ifBlank { "minimal" },
            renderer = style.renderer.ifBlank { "native" },
            htmlTemplate = style.htmlTemplate ?: "",
            htmlHeightDp = style.htmlHeightDp.takeIf { it > 0 } ?: 160,
            htmlAllowJs = style.htmlAllowJs,
            htmlAllowNetwork = style.htmlAllowNetwork,
            htmlTransparent = style.htmlTransparent,
            radiusTopStartDp = style.radiusTopStartDp,
            radiusTopEndDp = style.radiusTopEndDp,
            radiusBottomEndDp = style.radiusBottomEndDp,
            radiusBottomStartDp = style.radiusBottomStartDp,
            tail = style.tail.ifBlank { "soft" },
            pattern = style.pattern.ifBlank { "none" },
            decoration = style.decoration.ifBlank { "none" },
            decorationText = style.decorationText ?: "",
            decorationPosition = style.decorationPosition.ifBlank { "top_end" },
            decorationAnimation = style.decorationAnimation.ifBlank { "none" },
            decorationSizeDp = style.decorationSizeDp.takeIf { it > 0 } ?: 14,
            decorations = style.decorations.take(8).map {
                it.copy(
                    type = it.type.ifBlank { "none" },
                    text = it.text ?: "",
                    position = it.position.ifBlank { "top_end" },
                    animation = it.animation.ifBlank { "none" },
                    sizeDp = it.sizeDp.takeIf { size -> size > 0 } ?: 14,
                    color = it.color ?: "",
                )
            },
            gradient = style.gradient ?: emptyList(),
            animation = style.animation.ifBlank { "none" },
            emotion = style.emotion.ifBlank { "neutral" },
            fontFamily = style.fontFamily.ifBlank { "system" },
            fontWeight = style.fontWeight.ifBlank { "regular" },
            textAnimation = style.textAnimation.ifBlank { "none" },
            fontSizeSp = style.fontSizeSp.takeIf { it > 0 } ?: 14,
            lineHeightSp = style.lineHeightSp.takeIf { it > 0 } ?: 20,
            paddingHorizontalDp = style.paddingHorizontalDp.takeIf { it > 0 } ?: 12,
            paddingVerticalDp = style.paddingVerticalDp.takeIf { it > 0 } ?: 8,
            shadow = style.shadow.ifBlank { "none" },
            shadowColor = style.shadowColor ?: "",
            shadowAlpha = style.shadowAlpha,
            shadowElevationDp = style.shadowElevationDp,
            shadowOffsetXDp = style.shadowOffsetXDp,
            shadowOffsetYDp = style.shadowOffsetYDp,
            imageMode = style.imageMode.ifBlank { "cover" },
            schemaVersion = style.schemaVersion.takeIf { it > 0 } ?: 2,
        )
    }
}
