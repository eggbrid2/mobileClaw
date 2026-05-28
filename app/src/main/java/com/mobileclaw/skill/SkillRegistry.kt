package com.mobileclaw.skill

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry of all available skills.
 * Skills are registered at startup (builtins) or dynamically loaded by SkillLoader.
 */
class SkillRegistry {

    private val skills = ConcurrentHashMap<String, Skill>()
    private val levelOverrides = ConcurrentHashMap<String, Int>()

    fun register(skill: Skill) {
        skills[skill.meta.id] = skill
    }

    fun unregister(id: String) {
        skills.remove(id)
    }

    fun get(id: String): Skill? = skills[id]

    fun all(): List<Skill> = skills.values.toList()

    fun allMetasWithTaxonomy(): List<SkillMeta> =
        skills.values.map { skill -> skill.meta.withEffectiveTaxonomy(levelOverrides[skill.meta.id]) }

    fun userVisibleMetasWithTaxonomy(): List<SkillMeta> =
        allMetasWithTaxonomy().filterNot { it.internalTool }

    fun setLevelOverride(skillId: String, level: Int) {
        levelOverrides[skillId] = level
    }

    fun removeLevelOverride(skillId: String) {
        levelOverrides.remove(skillId)
    }

    fun effectiveLevel(skillId: String): Int {
        val skill = skills[skillId] ?: return Int.MAX_VALUE
        return levelOverrides[skillId] ?: skill.meta.injectionLevel
    }

    /** Returns all SkillMeta with the effective injection level applied. */
    fun allWithEffectiveLevel(): List<SkillMeta> = skills.values.map { skill ->
        val override = levelOverrides[skill.meta.id]
        skill.meta.withEffectiveTaxonomy(override)
    }

    fun userVisibleWithEffectiveLevel(): List<SkillMeta> =
        allWithEffectiveLevel().filterNot { it.internalTool }

    /** Returns skills eligible for injection at the given level and below (respects overrides). */
    fun forInjection(maxLevel: Int = 0): List<SkillMeta> =
        skills.values
            .filter { (levelOverrides[it.meta.id] ?: it.meta.injectionLevel) <= maxLevel }
            .map { skill ->
                val override = levelOverrides[skill.meta.id]
                skill.meta.withEffectiveTaxonomy(override)
            }

    fun contains(id: String) = skills.containsKey(id)

    private fun SkillMeta.withEffectiveTaxonomy(levelOverride: Int?): SkillMeta =
        copy(
            injectionLevel = levelOverride ?: injectionLevel,
            categories = categories.ifEmpty { SkillToolTaxonomy.categoriesFor(this).toList() },
            tags = (tags + SkillToolTaxonomy.categoriesFor(this).map { it.name.lowercase() }).distinct(),
        )
}
