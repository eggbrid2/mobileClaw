package com.mobileclaw.skill.builtin

import com.mobileclaw.agent.GameActionDraft
import com.mobileclaw.agent.GameRuntimeControlDraft
import com.mobileclaw.skill.Skill
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.skill.SkillParam
import com.mobileclaw.skill.SkillResult
import com.mobileclaw.skill.SkillToolCategory
import com.mobileclaw.skill.SkillType

class GameRuntimeSkill : Skill {
    override val meta = SkillMeta(
        id = "game_runtime",
        name = "Game Runtime",
        description = "Submit structured hidden-role or round-game actions without exposing private information in public chat. " +
            "Use this in game-type group chats for actions such as night kill, inspect, protect, vote, challenge, or custom phase action. " +
            "The action is queued for the host/judge runtime. A judge can also control speech, called speakers, and user action buttons. " +
            "Do not repeat private action details in the public reply.",
        parameters = listOf(
            SkillParam("action", "string", "submit_action | set_control | call_speaker | next_speaker"),
            SkillParam("ability_id", "string", "For submit_action: game ability id, such as wolf_kill, seer_inspect, guard_protect, day_vote, submit_theory.", required = false),
            SkillParam("target_seat_ids", "array", "Optional target seat ids", required = false),
            SkillParam("target_actor_ids", "array", "Optional target role/user ids", required = false),
            SkillParam("target_name", "string", "Optional human-readable target name when ids are unknown", required = false),
            SkillParam("channel_id", "string", "Optional game channel id. Defaults to judge for hidden actions.", required = false),
            SkillParam("visibility", "string", "public | team | private | judge. Defaults to judge.", required = false),
            SkillParam("reason", "string", "Short reason or evidence for the action", required = false),
            SkillParam("raw_text", "string", "Optional original natural-language action text", required = false),
            SkillParam("speech_open", "boolean", "For set_control/call_speaker/next_speaker: whether players may speak.", required = false),
            SkillParam("called_seat_id", "string", "For call_speaker/set_control: seat id to call.", required = false),
            SkillParam("called_actor_id", "string", "For call_speaker/set_control: role id or user actor id to call.", required = false),
            SkillParam("called_name", "string", "For call_speaker/set_control: display name to call when ids are unknown.", required = false),
            SkillParam("clear_called_speaker", "boolean", "For set_control: clear the currently called speaker.", required = false),
            SkillParam("enabled_ability_ids", "array", "For set_control: replace the user's currently enabled button ability ids. Empty means follow current phase.", required = false),
            SkillParam("note", "string", "Short public judge-control note.", required = false),
        ),
        type = SkillType.NATIVE,
        injectionLevel = 2,
        internalTool = true,
        nameZh = "游戏运行时",
        descriptionZh = "在游戏型群聊中提交结构化行动，不把私密行动暴露到公开聊天。",
        categories = listOf(SkillToolCategory.CHAT, SkillToolCategory.SYSTEM),
        tags = listOf("game", "group", "runtime", "hidden-role"),
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val action = params["action"] as? String ?: "submit_action"
        if (action in setOf("set_control", "call_speaker", "next_speaker")) {
            val draft = GameRuntimeControlDraft(
                speechOpen = params.boolOrNull("speech_open")
                    ?: if (action in setOf("call_speaker", "next_speaker")) true else null,
                calledSeatId = ((params["called_seat_id"] as? String) ?: (params["target_seat_id"] as? String)).orEmpty().trim(),
                calledActorId = ((params["called_actor_id"] as? String) ?: (params["target_actor_id"] as? String)).orEmpty().trim(),
                calledName = ((params["called_name"] as? String) ?: (params["target_name"] as? String)).orEmpty().trim(),
                clearCalledSeat = params.boolOrNull("clear_called_speaker") == true,
                nextOrderedSpeaker = action == "next_speaker",
                enabledAbilityIds = params.stringList("enabled_ability_ids"),
                replaceEnabledAbilityIds = params.containsKey("enabled_ability_ids"),
                note = ((params["note"] as? String) ?: (params["reason"] as? String)).orEmpty().trim(),
            )
            return SkillResult(
                success = true,
                output = "Game runtime control queued. The host UI will update speech, called speaker, or user buttons.",
                data = draft,
            )
        }
        if (action != "submit_action") {
            return SkillResult(false, "Unsupported game_runtime action: $action. Use submit_action, set_control, call_speaker, or next_speaker.")
        }
        val abilityId = (params["ability_id"] as? String).orEmpty().trim()
        if (abilityId.isBlank()) return SkillResult(false, "ability_id is required.")

        val visibility = (params["visibility"] as? String)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in setOf("public", "team", "private", "judge") }
            ?: "judge"
        val channelId = (params["channel_id"] as? String)
            ?.trim()
            ?.ifBlank { null }
            ?: if (visibility == "public") "public" else visibility

        val draft = GameActionDraft(
            abilityId = abilityId,
            targetSeatIds = params.stringList("target_seat_ids"),
            targetActorIds = params.stringList("target_actor_ids"),
            targetName = (params["target_name"] as? String).orEmpty().trim(),
            channelId = channelId,
            visibility = visibility,
            reason = (params["reason"] as? String).orEmpty().trim(),
            rawText = (params["raw_text"] as? String).orEmpty().trim(),
        )
        return SkillResult(
            success = true,
            output = "Game action queued for ${visibility} channel: ${draft.abilityId}. Do not expose private details in public chat.",
            data = draft,
        )
    }

    private fun Map<String, Any>.stringList(key: String): List<String> {
        val value = this[key] ?: return emptyList()
        return when (value) {
            is Iterable<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }.distinct()
            is Array<*> -> value.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }.distinct()
            is String -> value
                .split(',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            else -> listOf(value.toString().trim()).filter { it.isNotBlank() }
        }
    }

    private fun Map<String, Any>.boolOrNull(key: String): Boolean? {
        val value = this[key] ?: return null
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "on", "open" -> true
                "false", "0", "no", "off", "closed", "close" -> false
                else -> null
            }
            else -> null
        }
    }
}
