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
            ?.mapNotNull { runCatching { gson.fromJson(AtomicTextFile.readOrNull(it), Group::class.java).normalized() }.getOrNull() }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun get(id: String): Group? = synchronized(ioLock) {
        runCatching {
            gson.fromJson(AtomicTextFile.readOrNull(File(groupsDir, "$id.json")), Group::class.java).normalized()
        }.getOrNull()
    }

    fun save(group: Group) {
        synchronized(ioLock) {
            AtomicTextFile.write(File(groupsDir, "${group.id}.json"), gson.toJson(group.normalized()))
        }
    }

    fun delete(id: String) {
        File(groupsDir, "$id.json").delete()
    }

    fun touch(id: String) {
        val g = get(id) ?: return
        save(g.copy(updatedAt = System.currentTimeMillis()))
    }

    private fun Group.normalized(): Group {
        val cleanMembers = memberRoleIds.orEmpty().filter { it.isNotBlank() }.distinct()
        val cleanMode = mode ?: GroupMode.FREE_CHAT
        val cleanTurnStyle = turnStyle ?: GroupTurnStyle.BALANCED
        val cleanKind = kind ?: if (cleanMode == GroupMode.ROUNDED_GAME) GroupKind.GAME else GroupKind.CHAT
        val cleanJudgeRoleId = judgeRoleId.orEmpty().takeIf { it in cleanMembers }.orEmpty()
        val cleanMemberPositions = memberPositions
            .orEmpty()
            .filterKeys { it in cleanMembers }
            .mapValues { it.value.orEmpty().trim() }
            .filterValues { it.isNotBlank() }
        val normalizedMemberPositions = if (cleanKind == GroupKind.CHAT && cleanMode == GroupMode.DEBATE) {
            cleanMemberPositions.normalizedDebatePositions(cleanMembers, cleanJudgeRoleId)
        } else {
            cleanMemberPositions
        }
        val cleanGameProfile = gameProfile?.normalized(cleanMembers)
            ?: if (cleanKind == GroupKind.GAME) {
                GameProfile(
                    templateId = "custom_round",
                    templateName = "回合游戏",
                    publicRules = rules.orEmpty(),
                    autoHost = true,
                )
            } else {
                null
            }
        return copy(
            name = name.orEmpty().ifBlank { "AI Group" },
            emoji = emoji.orEmpty().ifBlank { "group" },
            memberRoleIds = cleanMembers,
            kind = cleanKind,
            mode = cleanMode,
            topic = topic.orEmpty(),
            openingPrompt = openingPrompt.orEmpty(),
            rules = rules.orEmpty(),
            roundLimit = roundLimit.takeIf { it in 1..8 } ?: 3,
            turnStyle = cleanTurnStyle,
            memberPositions = normalizedMemberPositions,
            judgeRoleId = cleanJudgeRoleId,
            gameProfile = cleanGameProfile,
            updatedAt = updatedAt.takeIf { it > 0L } ?: createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    private fun Map<String, String>.normalizedDebatePositions(
        memberRoleIds: List<String>,
        judgeRoleId: String,
    ): Map<String, String> {
        val participants = memberRoleIds.filter { it != judgeRoleId }
        if (participants.isEmpty()) return emptyMap()
        val valid = filterKeys { it in participants }
            .filterValues { it == GROUP_MEMBER_POSITION_DEBATE_PRO || it == GROUP_MEMBER_POSITION_DEBATE_CON }
        val hasPro = participants.any { valid[it] == GROUP_MEMBER_POSITION_DEBATE_PRO }
        val hasCon = participants.any { valid[it] == GROUP_MEMBER_POSITION_DEBATE_CON }
        if (hasPro && hasCon) return valid
        return participants.mapIndexed { index, roleId ->
            roleId to if (index % 2 == 0) GROUP_MEMBER_POSITION_DEBATE_PRO else GROUP_MEMBER_POSITION_DEBATE_CON
        }.toMap()
    }

    private fun GameProfile.normalized(validRoleIds: List<String>): GameProfile {
        val cleanUserRole = userRole ?: GameUserRole.HOST
        val cleanAssignment = identityAssignment ?: GameIdentityAssignment.RANDOM
        val cleanJudgeFlowMode = judgeFlowMode ?: GameJudgeFlowMode.PHASE
        val cleanUserActionPolicy = userActionPolicy ?: GameUserActionPolicy.PHASE
        val cleanSeats = seats.orEmpty().filter { seat ->
            seat.actorType == GameActorType.USER || seat.actorId in validRoleIds
        }.distinctBy { it.seatId }
        return copy(
            templateId = templateId.orEmpty(),
            templateName = templateName.orEmpty(),
            userRole = cleanUserRole,
            identityAssignment = cleanAssignment,
            seats = cleanSeats,
            identities = identities.orEmpty().distinctBy { it.id },
            abilities = abilities.orEmpty().distinctBy { it.id },
            phases = phases.orEmpty().distinctBy { it.id },
            channels = channels.orEmpty().distinctBy { it.id },
            currentPhaseId = currentPhaseId.orEmpty(),
            publicRules = publicRules.orEmpty(),
            hiddenStateJson = hiddenStateJson.orEmpty().ifBlank { "{}" },
            winConditionJson = winConditionJson.orEmpty().ifBlank { "{}" },
            judgeFlowMode = cleanJudgeFlowMode,
            userActionPolicy = cleanUserActionPolicy,
        )
    }
}
