package com.mobileclaw.skill

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of all available skills.
 * Skills are registered at startup (builtins) or dynamically loaded by SkillLoader.
 */
class SkillRegistry {

    private val skills = ConcurrentHashMap<String, Skill>()

    fun register(skill: Skill) {
        skills[skill.meta.id] = skill
    }

    fun unregister(id: String) {
        skills.remove(id)
    }

    fun get(id: String): Skill? = skills[id]

    fun all(): List<Skill> = skills.values.toList()

    /** Returns skills eligible for injection at the given level and below. */
    fun forInjection(maxLevel: Int = 0): List<SkillMeta> =
        skills.values
            .filter { it.meta.injectionLevel <= maxLevel }
            .map { it.meta }

    fun contains(id: String) = skills.containsKey(id)
}
