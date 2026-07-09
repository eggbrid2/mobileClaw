package com.mobileclaw.ui.group

import androidx.activity.compose.BackHandler
import com.google.gson.Gson
import com.google.gson.JsonObject
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.GameAbility
import com.mobileclaw.agent.GameAbilityEffect
import com.mobileclaw.agent.GameAbilityTrigger
import com.mobileclaw.agent.GameActorType
import com.mobileclaw.agent.GameChannel
import com.mobileclaw.agent.GameChannelKind
import com.mobileclaw.agent.GameIdentity
import com.mobileclaw.agent.GameIdentityAssignment
import com.mobileclaw.agent.GameJudgeFlowMode
import com.mobileclaw.agent.GameKnowledgeScope
import com.mobileclaw.agent.GamePhase
import com.mobileclaw.agent.GameProfile
import com.mobileclaw.agent.GameRuntimeState
import com.mobileclaw.agent.GameSeat
import com.mobileclaw.agent.GameSeatStatus
import com.mobileclaw.agent.GameUserActionPolicy
import com.mobileclaw.agent.GameUserRole
import com.mobileclaw.agent.Group
import com.mobileclaw.agent.GroupKind
import com.mobileclaw.agent.GroupMode
import com.mobileclaw.agent.GroupTurnStyle
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.effectiveModelBinding
import com.mobileclaw.ui.ClawColors
import com.mobileclaw.ui.ClawIconTile
import com.mobileclaw.ui.ClawPageHeader
import com.mobileclaw.ui.ClawPrimaryButton
import com.mobileclaw.ui.ClawSymbolIcon
import com.mobileclaw.ui.GradientAvatar
import com.mobileclaw.ui.LocalAppLanguage
import com.mobileclaw.ui.LocalClawColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.mobileclaw.str

private val groupPageGson = Gson()

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun GroupsPage(
    groups: List<Group>,
    groupPreviews: Map<String, GroupPreview> = emptyMap(),
    availableRoles: List<Role>,
    onOpenGroup: (Group) -> Unit,
    onCreateGroup: (Group) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onBack: () -> Unit,
    showHeader: Boolean = true,
    createRequestKey: Int = 0,
    showCreateFab: Boolean = true,
) {
    val c = LocalClawColors.current
    var showCreatePage by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Group?>(null) }
    var handledCreateRequestKey by remember { mutableStateOf(createRequestKey) }

    LaunchedEffect(createRequestKey) {
        if (createRequestKey != handledCreateRequestKey) {
            handledCreateRequestKey = createRequestKey
            showCreatePage = true
        }
    }

    BackHandler {
        if (showCreatePage) showCreatePage = false else onBack()
    }

    if (showCreatePage) {
        CreateGroupPage(
            availableRoles = availableRoles,
            onCreate = { group ->
                onCreateGroup(group)
                showCreatePage = false
            },
            onBack = { showCreatePage = false },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().then(if (showHeader) Modifier.statusBarsPadding() else Modifier)) {
            // Title bar
            if (showHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = str(R.string.btn_back), tint = c.text)
                    }
                    Text(str(R.string.groups_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showCreatePage = true }) {
                        Icon(Icons.Default.Add, contentDescription = str(R.string.group_new_title), tint = c.text)
                    }
                }
                HorizontalDivider(color = c.border, thickness = 0.5.dp)
            }

            if (groups.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ClawIconTile("group", size = 56.dp, iconSize = 28.dp, tint = c.text, background = c.cardAlt, border = c.border)
                        Spacer(Modifier.height(10.dp))
                        Text(str(R.string.groups_empty), color = c.subtext, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(5.dp))
                        Text(str(R.string.groups_empty_hint), color = c.subtext.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().background(c.surface)) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            preview = groupPreviews[group.id],
                            availableRoles = availableRoles,
                            onClick = { onOpenGroup(group) },
                            onDelete = { deleteTarget = group },
                            c = c,
                        )
                        HorizontalDivider(
                            color = c.border.copy(alpha = 0.42f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 76.dp),
                        )
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
        if (!showHeader && showCreateFab) {
            FloatingActionButton(
                onClick = { showCreatePage = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = c.text,
                contentColor = c.bg,
            ) {
                Icon(Icons.Default.Add, contentDescription = str(R.string.group_new_title))
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(str(R.string.skills_delete_confirm), color = c.text, fontWeight = FontWeight.SemiBold) },
            text = { Text(str(R.string.delete_confirm, target.name), color = c.text, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(target.id)
                    deleteTarget = null
                }) { Text(str(R.string.skills_delete_confirm), color = c.red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(str(R.string.btn_cancel), color = c.subtext) }
            },
            containerColor = c.surface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: Group,
    preview: GroupPreview?,
    availableRoles: List<Role>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    c: ClawColors,
) {
    val isZh = LocalAppLanguage.current == "zh"
    val members = group.memberRoleIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }
    val timeText = remember(preview?.createdAt, group.updatedAt) {
        formatGroupListTime(preview?.createdAt ?: group.updatedAt)
    }
    val fallbackPreview = remember(group.kind, group.mode, group.topic, group.gameProfile, members, isZh) {
        if (group.kind == GroupKind.GAME || group.gameProfile != null) {
            groupGameListSummary(group, isZh)
        } else if (group.mode != GroupMode.FREE_CHAT || group.topic.isNotBlank()) {
            listOf(groupModeLabel(group.mode, isZh), group.topic.trim()).filter { it.isNotBlank() }.joinToString(" · ")
        } else {
            buildString {
                append(str(R.string.groups_you))
                members.take(3).forEach { append(" · ${it.name}") }
                if (members.size > 3) append(" · +${members.size - 3}")
            }
        }
    }
    val previewText = preview?.let { "${it.senderName}: ${it.text.ifBlank { str(R.string.group_label_file) }}" } ?: fallbackPreview

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete,
            )
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.cardAlt)
                    .border(1.dp, c.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ClawSymbolIcon(group.emoji, tint = c.text, modifier = Modifier.size(24.dp))
            }
            if (preview != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c.green)
                        .border(1.5.dp, c.surface, CircleShape),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.name,
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    timeText,
                    color = c.subtext.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                text = previewText,
                color = c.subtext.copy(alpha = 0.82f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatGroupListTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now)) ==
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))
    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateGroupPage(
    availableRoles: List<Role>,
    onCreate: (Group) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    val defaultTemplate = remember(isZh) { groupModeTemplate(GroupMode.FREE_CHAT, isZh) }
    val gameTemplates = remember(isZh) { gameTemplateDrafts(isZh) }
    var groupKind by remember { mutableStateOf(GroupKind.CHAT) }
    var mode by remember { mutableStateOf(GroupMode.FREE_CHAT) }
    var selectedGameTemplateId by remember { mutableStateOf(gameTemplates.first().id) }
    var gameRuleConfig by remember { mutableStateOf(gameTemplates.first().ruleConfig) }
    var name by remember { mutableStateOf(defaultTemplate.defaultName) }
    var emoji by remember { mutableStateOf(defaultTemplate.icon) }
    var topic by remember { mutableStateOf("") }
    var openingPrompt by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf(defaultTemplate.rules) }
    var roundLimit by remember { mutableStateOf(defaultTemplate.roundLimit) }
    var turnStyle by remember { mutableStateOf(defaultTemplate.turnStyle) }
    var autoStart by remember { mutableStateOf(defaultTemplate.autoStart) }
    var judgeRoleId by remember { mutableStateOf("") }
    var userGameRole by remember { mutableStateOf(GameUserRole.HOST) }
    var identityAssignment by remember { mutableStateOf(GameIdentityAssignment.RANDOM) }
    var judgeFlowMode by remember { mutableStateOf(GameJudgeFlowMode.PHASE) }
    var userActionPolicy by remember { mutableStateOf(GameUserActionPolicy.PHASE) }
    var gameAbilities by remember { mutableStateOf(gameTemplates.first().abilities) }
    var gameIdentityDrafts by remember { mutableStateOf(gameTemplates.first().identities.toGameIdentityDrafts()) }
    val selectedIds = remember { mutableStateListOf<String>() }
    val manualIdentityByActorId = remember { mutableStateMapOf<String, String>() }
    val templates = remember(isZh) { groupModeTemplates(isZh) }
    val gameTemplate = remember(selectedGameTemplateId, isZh) {
        gameTemplates.firstOrNull { it.id == selectedGameTemplateId } ?: gameTemplates.first()
    }
    val needsTopic = if (groupKind == GroupKind.GAME) {
        gameTemplate.requiresTopic
    } else {
        mode in setOf(GroupMode.ARENA, GroupMode.DEBATE, GroupMode.ROUNDED_GAME)
    }
    val gameJudgeRoleId = if (groupKind == GroupKind.GAME) judgeRoleId.takeIf { it in selectedIds }.orEmpty() else ""
    val gamePlayerRoleIds = if (groupKind == GroupKind.GAME) {
        selectedIds.filter { it != gameJudgeRoleId }
    } else {
        selectedIds.toList()
    }
    val gamePlayerSeatCount = gamePlayerRoleIds.size + if (groupKind == GroupKind.GAME && userGameRole == GameUserRole.PLAYER) 1 else 0
    val gameIdentitySlotCount = gameIdentityDrafts.sumOf { it.count.coerceAtLeast(1) }
    val hasGameJudge = groupKind != GroupKind.GAME ||
        userGameRole == GameUserRole.HOST ||
        gameJudgeRoleId.isNotBlank() ||
        (gameJudgeRoleId.isBlank() && userGameRole != GameUserRole.HOST)
    val hasGamePlayers = groupKind != GroupKind.GAME || gamePlayerSeatCount > 0
    val hasGameIdentities = groupKind != GroupKind.GAME || gameIdentityDrafts.isNotEmpty()
    val effectiveOpeningPrompt = remember(groupKind, mode, selectedGameTemplateId, topic, openingPrompt, userGameRole, isZh) {
        openingPrompt.trim().ifBlank {
            if (groupKind == GroupKind.GAME) {
                defaultGameOpeningPrompt(gameTemplate, topic.trim(), userGameRole, isZh)
            } else {
                defaultGroupOpeningPrompt(mode, topic.trim(), isZh)
            }
        }
    }
    val canCreate = name.isNotBlank() &&
        selectedIds.isNotEmpty() &&
        (!needsTopic || topic.isNotBlank()) &&
        hasGameJudge &&
        hasGamePlayers &&
        hasGameIdentities

    fun applyGameTemplate(template: GameTemplateDraft) {
        val currentTemplate = gameTemplates.firstOrNull { it.id == selectedGameTemplateId }
        val shouldReplaceName = name.isBlank() || currentTemplate?.defaultName == name || templates.any { it.defaultName == name }
        groupKind = GroupKind.GAME
        mode = GroupMode.ROUNDED_GAME
        selectedGameTemplateId = template.id
        if (shouldReplaceName) name = template.defaultName
        emoji = template.icon
        rules = template.publicRules
        gameRuleConfig = template.ruleConfig
        judgeFlowMode = GameJudgeFlowMode.PHASE
        userActionPolicy = GameUserActionPolicy.PHASE
        gameAbilities = template.abilities
        gameIdentityDrafts = template.identities.toGameIdentityDrafts()
        roundLimit = template.defaultRounds
        turnStyle = GroupTurnStyle.ACTIVE
        autoStart = true
        judgeRoleId = ""
        openingPrompt = ""
        manualIdentityByActorId
            .filterValues { identityId -> template.identities.none { it.id == identityId } }
            .keys
            .toList()
            .forEach { manualIdentityByActorId.remove(it) }
    }

    fun applyTemplate(nextMode: GroupMode) {
        val currentTemplate = groupModeTemplate(mode, isZh)
        val template = groupModeTemplate(nextMode, isZh)
        val shouldReplaceName = name.isBlank() || name == currentTemplate.defaultName
        groupKind = GroupKind.CHAT
        mode = nextMode
        if (shouldReplaceName) name = template.defaultName
        emoji = template.icon
        rules = template.rules
        roundLimit = template.roundLimit
        turnStyle = template.turnStyle
        autoStart = template.autoStart
        openingPrompt = ""
        if (nextMode == GroupMode.FREE_CHAT) judgeRoleId = ""
    }

    fun applyGroupKind(nextKind: GroupKind) {
        if (groupKind == nextKind) return
        if (nextKind == GroupKind.GAME) {
            applyGameTemplate(gameTemplate)
        } else {
            groupKind = GroupKind.CHAT
            identityAssignment = GameIdentityAssignment.RANDOM
            userGameRole = GameUserRole.HOST
            applyTemplate(GroupMode.FREE_CHAT)
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        ClawPageHeader(title = str(R.string.group_new_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CreateGroupSection(
                title = if (isZh) "类型" else "Type",
                meta = if (groupKind == GroupKind.GAME) {
                    if (isZh) "游戏型" else "Game"
                } else {
                    if (isZh) "闲聊型" else "Chat"
                },
                c = c,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupSmallPill(
                        text = if (isZh) "闲聊型" else "Chat",
                        selected = groupKind == GroupKind.CHAT,
                        c = c,
                        onClick = { applyGroupKind(GroupKind.CHAT) },
                    )
                    GroupSmallPill(
                        text = if (isZh) "游戏型" else "Game",
                        selected = groupKind == GroupKind.GAME,
                        c = c,
                        onClick = { applyGroupKind(GroupKind.GAME) },
                    )
                }
            }

            CreateGroupSection(
                title = if (groupKind == GroupKind.GAME) {
                    if (isZh) "游戏模板" else "Game"
                } else {
                    if (isZh) "玩法" else "Mode"
                },
                meta = if (groupKind == GroupKind.GAME) gameTemplate.title else groupModeLabel(mode, isZh),
                c = c,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (groupKind == GroupKind.GAME) {
                        gameTemplates.forEach { template ->
                            GroupModeChip(
                                title = template.title,
                                subtitle = template.subtitle,
                                icon = template.icon,
                                selected = template.id == selectedGameTemplateId,
                                c = c,
                                onClick = { applyGameTemplate(template) },
                            )
                        }
                    } else {
                        templates.filter { it.mode != GroupMode.ROUNDED_GAME }.forEach { template ->
                            GroupModeChip(
                                title = template.title,
                                subtitle = template.subtitle,
                                icon = template.icon,
                                selected = template.mode == mode,
                                c = c,
                                onClick = { applyTemplate(template.mode) },
                            )
                        }
                    }
                }
            }

            CreateGroupSection(
                title = if (isZh) "内容" else "Content",
                meta = if (needsTopic) {
                    if (isZh) "题目必填" else "Topic required"
                } else {
                    if (isZh) "可选开场" else "Optional opening"
                },
                c = c,
            ) {
                GroupCreateTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = str(R.string.group_field_name_hint),
                    c = c,
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                GroupCreateTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    placeholder = if (groupKind == GroupKind.GAME) {
                        gameTemplate.topicPlaceholder
                    } else {
                        when (mode) {
                            GroupMode.ARENA -> if (isZh) "这局比什么？例如：谁能设计出最稳的 MiniAPP 架构" else "What should this arena compare?"
                            GroupMode.DEBATE -> if (isZh) "辩题，例如：本地模型是否更适合移动端智能体" else "Debate topic"
                            GroupMode.WORKSHOP -> if (isZh) "协作目标，例如：一起拆一个产品方案" else "Workshop goal"
                            GroupMode.ROUNDED_GAME -> if (isZh) "游戏目标，例如：三轮推理，找出最可靠的方案" else "Game objective"
                            GroupMode.FREE_CHAT -> if (isZh) "话题，可留空" else "Topic, optional"
                        }
                    },
                    c = c,
                    singleLine = false,
                    minHeight = 74.dp,
                )
                Spacer(Modifier.height(10.dp))
                GroupCreateTextField(
                    value = openingPrompt,
                    onValueChange = { openingPrompt = it },
                    placeholder = if (isZh) "开场消息，留空则自动生成" else "Opening message, auto-generated if blank",
                    c = c,
                    singleLine = false,
                    minHeight = 78.dp,
                )
                Spacer(Modifier.height(10.dp))
                GroupCreateTextField(
                    value = rules,
                    onValueChange = { rules = it },
                    placeholder = if (isZh) "规则与评分方式" else "Rules and judging notes",
                    c = c,
                    singleLine = false,
                    minHeight = 96.dp,
                )
            }

            CreateGroupSection(
                title = if (isZh) "成员" else "Members",
                meta = if (isZh) "${selectedIds.size} 位已选" else "${selectedIds.size} selected",
                c = c,
            ) {
                if (availableRoles.isEmpty()) {
                    Text(
                        if (isZh) "还没有可用角色" else "No roles available",
                        color = c.subtext,
                        fontSize = 13.sp,
                    )
                } else {
                    availableRoles.forEachIndexed { index, role ->
                        val selected = selectedIds.contains(role.id)
                        GroupRoleSelectionRow(
                            role = role,
                            selected = selected,
                            modelLabel = roleGroupModelLabel(role, isZh),
                            c = c,
                            onToggle = {
                                if (selected) {
                                    selectedIds.remove(role.id)
                                    if (judgeRoleId == role.id) judgeRoleId = ""
                                } else {
                                    selectedIds.add(role.id)
                                }
                            },
                        )
                        if (index != availableRoles.lastIndex) {
                            HorizontalDivider(
                                color = c.border.copy(alpha = 0.42f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 52.dp),
                            )
                        }
                    }
                }
            }

            if (groupKind == GroupKind.GAME) {
                CreateGroupSection(
                    title = if (isZh) "流程执行者" else "Host",
                    meta = if (gameJudgeRoleId.isBlank() && userGameRole == GameUserRole.HOST) {
                        if (isZh) "你是法官" else "You host"
                    } else if (gameJudgeRoleId.isBlank()) {
                        if (isZh) "系统法官" else "System host"
                    } else {
                        availableRoles.firstOrNull { it.id == gameJudgeRoleId }?.name ?: gameUserRoleLabel(userGameRole, isZh)
                    },
                    c = c,
                ) {
                    GameFlowExecutorPicker(
                        selectedRoleIds = selectedIds.toList(),
                        availableRoles = availableRoles,
                        judgeRoleId = gameJudgeRoleId,
                        userGameRole = userGameRole,
                        c = c,
                        isZh = isZh,
                        onJudgeRoleChange = { nextJudgeId ->
                            judgeRoleId = nextJudgeId
                            if (nextJudgeId.isBlank()) {
                                userGameRole = GameUserRole.HOST
                                manualIdentityByActorId.remove("user")
                            } else if (userGameRole == GameUserRole.HOST) {
                                userGameRole = GameUserRole.PLAYER
                            }
                        },
                        onUserRoleChange = { nextRole ->
                            userGameRole = nextRole
                            if (nextRole == GameUserRole.HOST) {
                                judgeRoleId = ""
                                manualIdentityByActorId.remove("user")
                            }
                        },
                    )
                }

                CreateGroupSection(
                    title = if (isZh) "法官流程" else "Judge flow",
                    meta = judgeFlowMode.label(isZh),
                    c = c,
                ) {
                    GameJudgeFlowPolicyEditor(
                        judgeFlowMode = judgeFlowMode,
                        userActionPolicy = userActionPolicy,
                        c = c,
                        isZh = isZh,
                        onJudgeFlowModeChange = { judgeFlowMode = it },
                        onUserActionPolicyChange = { userActionPolicy = it },
                    )
                }

                CreateGroupSection(
                    title = if (isZh) "身份池" else "Identity deck",
                    meta = if (isZh) "$gameIdentitySlotCount 张 / $gamePlayerSeatCount 玩家" else "$gameIdentitySlotCount cards / $gamePlayerSeatCount players",
                    c = c,
                ) {
                    GameAbilityPoolEditor(
                        abilities = gameAbilities,
                        c = c,
                        isZh = isZh,
                        onChange = { nextAbilities ->
                            gameAbilities = nextAbilities
                            val validIds = nextAbilities.map { it.id }.toSet()
                            gameIdentityDrafts = gameIdentityDrafts.map { draft ->
                                draft.copy(abilityIds = draft.abilityIds.filter { it in validIds })
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    GameIdentityDeckEditor(
                        identities = gameIdentityDrafts,
                        abilities = gameAbilities,
                        playerSeatCount = gamePlayerSeatCount,
                        c = c,
                        isZh = isZh,
                        onChange = { gameIdentityDrafts = it },
                    )
                }

                CreateGroupSection(
                    title = if (isZh) "身份分配" else "Assignment",
                    meta = gameIdentityAssignmentLabel(identityAssignment, isZh),
                    c = c,
                ) {
                    RuleSectionLabel(text = if (isZh) "抽签方式" else "Mode", c = c)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameIdentityAssignment.values().forEach { assignment ->
                            GroupSmallPill(
                                text = gameIdentityAssignmentLabel(assignment, isZh),
                                selected = identityAssignment == assignment,
                                c = c,
                                onClick = { identityAssignment = assignment },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (identityAssignment) {
                            GameIdentityAssignment.RANDOM -> if (isZh) "创建群聊时会按身份池洗牌抽签；法官不参与抽签。" else "The deck is shuffled when the group is created. The host is not a player seat."
                            GameIdentityAssignment.MANUAL -> if (isZh) "每个玩家都需要手动指定身份；未指定的席位不会获得隐藏身份。" else "Assign each player manually. Unassigned seats stay without hidden identity."
                            GameIdentityAssignment.MIXED -> if (isZh) "已指定的席位固定身份，其余玩家从身份池随机抽签。" else "Manual picks stay fixed; the remaining player seats draw from the deck."
                        },
                        color = c.subtext,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                    if (identityAssignment != GameIdentityAssignment.RANDOM) {
                        Spacer(Modifier.height(12.dp))
                        GameSeatIdentityPicker(
                            selectedRoleIds = gamePlayerRoleIds,
                            availableRoles = availableRoles,
                            userGameRole = userGameRole,
                            identities = gameIdentityDrafts,
                            manualIdentityByActorId = manualIdentityByActorId,
                            c = c,
                            isZh = isZh,
                        )
                    }
                }

                CreateGroupSection(
                    title = if (isZh) "规则配置" else "Rules",
                    meta = gameRuleConfig.summaryLabel(isZh),
                    c = c,
                ) {
                    GameRuleConfigEditor(
                        config = gameRuleConfig,
                        c = c,
                        isZh = isZh,
                        onChange = { gameRuleConfig = it },
                    )
                }
            }

            CreateGroupSection(
                title = if (isZh) "节奏" else "Flow",
                meta = groupTurnStyleLabel(turnStyle, isZh),
                c = c,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupTurnStyle.values().forEach { style ->
                        GroupSmallPill(
                            text = groupTurnStyleLabel(style, isZh),
                            selected = turnStyle == style,
                            c = c,
                            onClick = { turnStyle = style },
                        )
                    }
                }
                if (mode != GroupMode.FREE_CHAT) {
                    Spacer(Modifier.height(12.dp))
                    RoundLimitStepper(
                        value = roundLimit,
                        c = c,
                        isZh = isZh,
                        onChange = { roundLimit = it.coerceIn(1, 8) },
                    )
                }
                if (groupKind == GroupKind.CHAT && mode in setOf(GroupMode.ARENA, GroupMode.DEBATE, GroupMode.ROUNDED_GAME)) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isZh) "裁判" else "Judge",
                        color = c.subtext,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupSmallPill(
                            text = if (isZh) "不指定" else "None",
                            selected = judgeRoleId.isBlank(),
                            c = c,
                            onClick = { judgeRoleId = "" },
                        )
                        selectedIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }.forEach { role ->
                            GroupSmallPill(
                                text = role.name,
                                selected = judgeRoleId == role.id,
                                c = c,
                                onClick = { judgeRoleId = role.id },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GroupCreateToggleRow(
                    title = if (isZh) "创建后直接开局" else "Start after create",
                    subtitle = effectiveOpeningPrompt.take(48),
                    checked = autoStart,
                    c = c,
                    onClick = { autoStart = !autoStart },
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            ClawPrimaryButton(
                text = str(R.string.group_create),
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!canCreate) return@ClawPrimaryButton
                    onCreate(
                        Group(
                            id = UUID.randomUUID().toString(),
                            name = name.trim(),
                            emoji = emoji,
                            memberRoleIds = selectedIds.toList(),
                            kind = groupKind,
                            mode = if (groupKind == GroupKind.GAME) GroupMode.ROUNDED_GAME else mode,
                            topic = topic.trim(),
                            openingPrompt = effectiveOpeningPrompt,
                            rules = rules.trim().ifBlank {
                                if (groupKind == GroupKind.GAME) gameTemplate.publicRules else groupModeTemplate(mode, isZh).rules
                            },
                            roundLimit = roundLimit.coerceIn(1, 8),
                            turnStyle = turnStyle,
                            autoStart = autoStart,
                            judgeRoleId = judgeRoleId.takeIf { selectedIds.contains(it) }.orEmpty(),
                            gameProfile = if (groupKind == GroupKind.GAME) {
                                buildGameProfile(
                                    template = gameTemplate,
                                    selectedRoleIds = selectedIds.toList(),
                                    availableRoles = availableRoles,
                                    judgeRoleId = gameJudgeRoleId,
                                    userRole = userGameRole,
                                    identityAssignment = identityAssignment,
                                    judgeFlowMode = judgeFlowMode,
                                    userActionPolicy = userActionPolicy,
                                    identityDrafts = gameIdentityDrafts,
                                    abilities = gameAbilities,
                                    manualIdentityByActorId = manualIdentityByActorId.toMap(),
                                    publicRules = rules.trim().ifBlank { gameTemplate.publicRules },
                                    ruleConfig = gameRuleConfig,
                                    roundLimit = roundLimit.coerceIn(1, 8),
                                    isZh = isZh,
                                )
                            } else {
                                null
                            },
                        ),
                    )
                },
            )
            if (!canCreate) {
                Spacer(Modifier.height(7.dp))
                Text(
                    when {
                        selectedIds.isEmpty() -> if (isZh) "至少选择一个角色" else "Select at least one role"
                        needsTopic && topic.isBlank() -> if (isZh) "先写清本局题目" else "Add a topic first"
                        !hasGameJudge -> if (isZh) "先分配一个法官/流程执行者" else "Assign a host first"
                        !hasGamePlayers -> if (isZh) "至少需要一个玩家席位" else "Add at least one player seat"
                        !hasGameIdentities -> if (isZh) "至少配置一个身份" else "Add at least one identity"
                        else -> if (isZh) "群名不能为空" else "Group name is required"
                    },
                    color = c.subtext.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

private data class GroupModeTemplate(
    val mode: GroupMode,
    val title: String,
    val subtitle: String,
    val defaultName: String,
    val icon: String,
    val rules: String,
    val roundLimit: Int,
    val turnStyle: GroupTurnStyle,
    val autoStart: Boolean,
)

private data class GameTemplateDraft(
    val id: String,
    val title: String,
    val subtitle: String,
    val defaultName: String,
    val icon: String,
    val topicPlaceholder: String,
    val publicRules: String,
    val ruleConfig: GameRuleConfigDraft = GameRuleConfigDraft(),
    val defaultRounds: Int,
    val requiresTopic: Boolean,
    val identities: List<GameIdentity>,
    val abilities: List<GameAbility>,
    val phases: List<GamePhase>,
    val channels: List<GameChannel>,
)

private data class GameIdentityDraft(
    val id: String,
    val name: String,
    val teamId: String = "",
    val publicHint: String = "",
    val privatePrompt: String = "",
    val abilityIds: List<String> = emptyList(),
    val count: Int = 1,
)

private fun List<GameIdentity>.toGameIdentityDrafts(): List<GameIdentityDraft> =
    map { identity ->
        GameIdentityDraft(
            id = identity.id.ifBlank { UUID.randomUUID().toString() },
            name = identity.name,
            teamId = identity.teamId,
            publicHint = identity.publicHint,
            privatePrompt = identity.privatePrompt,
            abilityIds = identity.abilityIds,
            count = 1,
        )
    }

private fun GameIdentityDraft.toGameIdentity(validAbilityIds: Set<String>, isZh: Boolean): GameIdentity =
    GameIdentity(
        id = id.ifBlank { UUID.randomUUID().toString() },
        name = name.trim().ifBlank { if (isZh) "玩家" else "Player" },
        teamId = teamId.trim(),
        publicHint = publicHint.trim(),
        privatePrompt = privatePrompt.trim(),
        abilityIds = abilityIds.filter { it in validAbilityIds }.distinct(),
    )

private enum class GameEliminateRuleDraft {
    OUT,
    SCORE,
    OUT_AND_SCORE,
    RECORD_ONLY,
}

private enum class GameVoteRuleDraft {
    PLURALITY_OUT,
    MAJORITY_OUT,
    SCORE_TARGET,
    SCORE_ACTOR,
    RECORD_ONLY,
}

private enum class GameTiePolicyDraft {
    NO_EFFECT,
    ALL,
    FIRST,
}

private data class GameRuleConfigDraft(
    val eliminateEffect: GameEliminateRuleDraft = GameEliminateRuleDraft.OUT,
    val protectBlocksEliminate: Boolean = true,
    val preventConsecutiveProtectSameTarget: Boolean = false,
    val eliminateActorPoints: Int = 0,
    val eliminateTargetPoints: Int = 0,
    val voteEffect: GameVoteRuleDraft = GameVoteRuleDraft.PLURALITY_OUT,
    val voteActorPoints: Int = 0,
    val voteTargetPoints: Int = 0,
    val voteTiePolicy: GameTiePolicyDraft = GameTiePolicyDraft.NO_EFFECT,
    val scoreLimit: Int = 0,
    val finalJudgePrompt: String = "",
)

private fun gameTemplateDrafts(isZh: Boolean): List<GameTemplateDraft> {
    val werewolfAbilities = listOf(
        GameAbility(
            id = "wolf_kill",
            name = if (isZh) "刀人" else "Night kill",
            trigger = GameAbilityTrigger.TEAM_MEETING,
            effect = GameAbilityEffect.ELIMINATE,
            usageLimit = 1,
            targetRule = if (isZh) "夜晚狼人阵营共同选择一名非狼人目标" else "Wolf team chooses one non-wolf target at night",
            visibility = "team",
            description = if (isZh) "狼人小会议后锁定击杀目标。" else "Resolve after the wolf team meeting.",
        ),
        GameAbility(
            id = "wolf_meeting",
            name = if (isZh) "狼人会议" else "Wolf meeting",
            trigger = GameAbilityTrigger.TEAM_MEETING,
            effect = GameAbilityEffect.MESSAGE_PRIVATE,
            usageLimit = -1,
            targetRule = if (isZh) "仅狼人阵营可见" else "Visible to wolf team only",
            visibility = "team",
            description = if (isZh) "狼人夜间私密讨论频道。" else "Private night discussion channel for wolves.",
        ),
        GameAbility(
            id = "seer_inspect",
            name = if (isZh) "查验" else "Inspect",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.INSPECT,
            usageLimit = 1,
            targetRule = if (isZh) "夜晚选择一名玩家查看阵营" else "Choose one player at night to inspect team",
            visibility = "private",
            description = if (isZh) "结果只给预言家和法官视野。" else "Result is visible only to seer and host.",
        ),
        GameAbility(
            id = "hunter_shot",
            name = if (isZh) "开枪" else "Shot",
            trigger = GameAbilityTrigger.ON_DEATH,
            effect = GameAbilityEffect.ELIMINATE,
            usageLimit = 1,
            targetRule = if (isZh) "符合触发条件时带走一名玩家" else "Eliminate one player when death trigger allows",
            visibility = "public",
            description = if (isZh) "猎人死亡触发的一次性行动。" else "One-shot action triggered by hunter death.",
        ),
        GameAbility(
            id = "guard_protect",
            name = if (isZh) "守护" else "Protect",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.PROTECT,
            usageLimit = 1,
            targetRule = if (isZh) "夜晚选择一名玩家保护" else "Choose one player at night to protect",
            visibility = "private",
            description = if (isZh) "模板可限制不能连续守同一人。" else "Template may prevent protecting the same target twice.",
        ),
        GameAbility(
            id = "day_vote",
            name = if (isZh) "投票" else "Vote",
            trigger = GameAbilityTrigger.VOTE,
            effect = GameAbilityEffect.VOTE,
            usageLimit = 1,
            targetRule = if (isZh) "白天公开投票放逐" else "Public daytime exile vote",
            visibility = "public",
            description = if (isZh) "白天讨论后结算。" else "Resolved after public discussion.",
        ),
    )
    val werewolfIdentities = listOf(
        GameIdentity(
            id = "wolf",
            name = if (isZh) "狼人" else "Wolf",
            teamId = "wolf",
            publicHint = if (isZh) "夜晚行动，白天伪装" else "Acts at night, hides by day",
            privatePrompt = if (isZh) "你属于狼人阵营。你知道狼人队友，可以在狼人会议中协商刀人目标。白天不要暴露身份。" else "You are on the wolf team. You know wolf teammates and can coordinate night kill in private.",
            abilityIds = listOf("wolf_meeting", "wolf_kill", "day_vote"),
            winConditionTags = listOf("wolf_majority"),
        ),
        GameIdentity(
            id = "seer",
            name = if (isZh) "预言家" else "Seer",
            teamId = "village",
            publicHint = if (isZh) "每夜查验一人" else "Inspects one player each night",
            privatePrompt = if (isZh) "你属于好人阵营。每夜可查验一名玩家阵营，白天要谨慎传递信息。" else "You are village. Inspect one player's team each night and communicate carefully by day.",
            abilityIds = listOf("seer_inspect", "day_vote"),
            winConditionTags = listOf("village_survive"),
        ),
        GameIdentity(
            id = "hunter",
            name = if (isZh) "猎人" else "Hunter",
            teamId = "village",
            publicHint = if (isZh) "死亡时可能带走一人" else "May shoot on death",
            privatePrompt = if (isZh) "你属于好人阵营。符合条件死亡时可开枪带走一人。" else "You are village. If death trigger allows, you may shoot one player.",
            abilityIds = listOf("hunter_shot", "day_vote"),
            winConditionTags = listOf("village_survive"),
        ),
        GameIdentity(
            id = "guard",
            name = if (isZh) "守卫" else "Guard",
            teamId = "village",
            publicHint = if (isZh) "夜晚保护一人" else "Protects one player at night",
            privatePrompt = if (isZh) "你属于好人阵营。夜晚选择一名玩家守护，避免被击杀。" else "You are village. Protect one player at night.",
            abilityIds = listOf("guard_protect", "day_vote"),
            winConditionTags = listOf("village_survive"),
        ),
        GameIdentity(
            id = "villager",
            name = if (isZh) "平民" else "Villager",
            teamId = "village",
            publicHint = if (isZh) "没有夜间技能，依靠发言和投票" else "No night action, relies on speech and vote",
            privatePrompt = if (isZh) "你属于好人阵营。你没有夜间技能，白天通过发言、逻辑和投票找出狼人。" else "You are village. Use public reasoning and vote to find wolves.",
            abilityIds = listOf("day_vote"),
            winConditionTags = listOf("village_survive"),
        ),
    )

    val deductionAbilities = listOf(
        GameAbility(
            id = "submit_theory",
            name = if (isZh) "提交推理" else "Submit theory",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.SET_FLAG,
            usageLimit = 1,
            visibility = "public",
            description = if (isZh) "每轮提交一个可被质询的结论。" else "Submit one challengeable theory each round.",
        ),
        GameAbility(
            id = "challenge",
            name = if (isZh) "质询" else "Challenge",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.REVEAL,
            usageLimit = 1,
            visibility = "public",
            description = if (isZh) "点名一个漏洞或要求补证据。" else "Point out a gap or request evidence.",
        ),
        GameAbility(
            id = "final_vote",
            name = if (isZh) "最终投票" else "Final vote",
            trigger = GameAbilityTrigger.VOTE,
            effect = GameAbilityEffect.VOTE,
            usageLimit = 1,
            visibility = "public",
            description = if (isZh) "选择本轮最可信方案。" else "Vote for the most credible theory.",
        ),
    )
    val deductionIdentities = listOf(
        GameIdentity(
            id = "detective",
            name = if (isZh) "侦探" else "Detective",
            teamId = "reason",
            publicHint = if (isZh) "主导证据链" else "Builds evidence chain",
            privatePrompt = if (isZh) "你负责构建主推理链，优先给清晰证据和结论。" else "Build the primary reasoning chain with evidence and conclusions.",
            abilityIds = listOf("submit_theory", "challenge", "final_vote"),
        ),
        GameIdentity(
            id = "skeptic",
            name = if (isZh) "怀疑者" else "Skeptic",
            teamId = "reason",
            publicHint = if (isZh) "寻找漏洞" else "Finds gaps",
            privatePrompt = if (isZh) "你负责质询漏洞、反例和证据不足之处。" else "Challenge gaps, counterexamples, and weak evidence.",
            abilityIds = listOf("challenge", "submit_theory", "final_vote"),
        ),
        GameIdentity(
            id = "strategist",
            name = if (isZh) "策略家" else "Strategist",
            teamId = "reason",
            publicHint = if (isZh) "整合行动方案" else "Synthesizes action",
            privatePrompt = if (isZh) "你负责把推理结论转成可执行策略。" else "Turn reasoning into actionable strategy.",
            abilityIds = listOf("submit_theory", "final_vote"),
        ),
    )

    val scoreKillAbilities = listOf(
        GameAbility(
            id = "score_hit",
            name = if (isZh) "击杀" else "Hit",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.ELIMINATE,
            usageLimit = 1,
            targetRule = if (isZh) "每轮选择一名目标；未被保护则行动者得分，目标不出局" else "Pick one target each round; score if not protected, target stays in",
            visibility = "public",
            description = if (isZh) "用于积分击杀、悬赏追猎、电子斗蛐蛐类玩法。" else "For score-kill, bounty, and arena-style games.",
        ),
        GameAbility(
            id = "score_guard",
            name = if (isZh) "护盾" else "Shield",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.PROTECT,
            usageLimit = 1,
            targetRule = if (isZh) "每轮选择一名目标，本轮挡一次击杀得分" else "Pick one target to block score-kill this round",
            visibility = "private",
            description = if (isZh) "保护和击杀在阶段结算时一起处理。" else "Protection and hits resolve together at phase settlement.",
        ),
        GameAbility(
            id = "score_vote",
            name = if (isZh) "投票" else "Vote",
            trigger = GameAbilityTrigger.VOTE,
            effect = GameAbilityEffect.VOTE,
            usageLimit = 1,
            targetRule = if (isZh) "公开投票给本轮焦点目标；有效投票者得分" else "Publicly vote for a focus target; valid voters score",
            visibility = "public",
            description = if (isZh) "投票只计分，不让任何人出局。" else "Votes score without eliminating seats.",
        ),
    )
    val scoreKillIdentities = listOf(
        GameIdentity(
            id = "score_player",
            name = if (isZh) "积分玩家" else "Score player",
            teamId = "score",
            publicHint = if (isZh) "击杀得分，保护挡分，终局看积分和有效行动" else "Score by hits; final verdict weighs points and valid actions",
            privatePrompt = if (isZh) "你是积分击杀玩家。目标不是淘汰别人，而是在公开推理和目标选择中尽快得分，同时用护盾阻止别人得分。" else "You are a score-kill player. Win by scoring through public target choices while using shields to block others.",
            abilityIds = listOf("score_hit", "score_guard", "score_vote"),
            winConditionTags = listOf("score_limit"),
        ),
    )

    val customAbilities = listOf(
        GameAbility(
            id = "custom_action",
            name = if (isZh) "行动" else "Action",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.SET_FLAG,
            usageLimit = 1,
            visibility = "public",
        ),
        GameAbility(
            id = "custom_hit",
            name = if (isZh) "击杀/作用" else "Hit/effect",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.ELIMINATE,
            usageLimit = 1,
            targetRule = if (isZh) "按本局规则对一个目标产生出局、计分或记录效果" else "Apply the configured out, score, or record effect to one target",
            visibility = "public",
        ),
        GameAbility(
            id = "custom_protect",
            name = if (isZh) "保护" else "Protect",
            trigger = GameAbilityTrigger.PHASE_ACTION,
            effect = GameAbilityEffect.PROTECT,
            usageLimit = 1,
            targetRule = if (isZh) "按本局规则保护一个目标" else "Protect one target according to this game's rules",
            visibility = "private",
        ),
        GameAbility(
            id = "custom_vote",
            name = if (isZh) "投票" else "Vote",
            trigger = GameAbilityTrigger.VOTE,
            effect = GameAbilityEffect.VOTE,
            usageLimit = 1,
            visibility = "public",
        ),
    )
    val customIdentities = listOf(
        GameIdentity(
            id = "player",
            name = if (isZh) "玩家" else "Player",
            teamId = "player",
            publicHint = if (isZh) "通用玩家" else "General player",
            privatePrompt = if (isZh) "你是本局通用玩家，按规则目标行动。" else "You are a general player. Follow the game goal and rules.",
            abilityIds = listOf("custom_action", "custom_hit", "custom_protect", "custom_vote"),
        ),
        GameIdentity(
            id = "observer",
            name = if (isZh) "观察者" else "Observer",
            teamId = "observer",
            publicHint = if (isZh) "观察和复盘" else "Observe and review",
            privatePrompt = if (isZh) "你负责观察局势、记录证据和给出复盘。" else "Observe the game, record evidence, and review.",
            abilityIds = listOf("custom_action", "custom_vote"),
        ),
    )

    return listOf(
        GameTemplateDraft(
            id = "werewolf_like",
            title = if (isZh) "狼人杀类" else "Werewolf-like",
            subtitle = if (isZh) "身份/阵营" else "Hidden roles",
            defaultName = if (isZh) "AI 狼人杀实验" else "AI Werewolf Lab",
            icon = "game",
            topicPlaceholder = if (isZh) "故事背景，可留空，例如：移动端 AI 小镇夜晚发生异常" else "Story setup, optional",
            publicRules = if (isZh) "隐藏身份、阵营博弈、夜晚私密行动、白天公开讨论投票。狼人阵营尝试取得人数优势，好人阵营通过推理和技能找出狼人。" else "Hidden roles, team conflict, private night actions, public daytime discussion and vote.",
            ruleConfig = GameRuleConfigDraft(
                eliminateEffect = GameEliminateRuleDraft.OUT,
                protectBlocksEliminate = true,
                preventConsecutiveProtectSameTarget = true,
                voteEffect = GameVoteRuleDraft.PLURALITY_OUT,
                voteTiePolicy = GameTiePolicyDraft.NO_EFFECT,
                finalJudgePrompt = if (isZh) {
                    "最终轮结束后，AI 根据存活状态、阵营目标、投票放逐、夜间行动和公开推理证据判断阵营胜负；中途狼人归零或人数优势只作为终局证据，不提前结束。"
                } else {
                    "After the final round, AI judges the winning side using survival state, team goals, exiles, night actions, and public reasoning evidence. Mid-game wolf count or majority is evidence only, not an early win."
                },
            ),
            defaultRounds = 6,
            requiresTopic = false,
            identities = werewolfIdentities,
            abilities = werewolfAbilities,
            phases = listOf(
                GamePhase("night", if (isZh) "夜晚" else "Night", 1, listOf("wolf_team", "judge"), listOf("wolf_meeting", "wolf_kill", "seer_inspect", "guard_protect")),
                GamePhase("day", if (isZh) "白天" else "Day", 2, listOf("public"), emptyList()),
                GamePhase("vote", if (isZh) "投票" else "Vote", 3, listOf("public"), listOf("day_vote")),
            ),
            channels = listOf(
                GameChannel("public", if (isZh) "公开" else "Public", GameChannelKind.PUBLIC),
                GameChannel("wolf_team", if (isZh) "狼人会议" else "Wolf meeting", GameChannelKind.TEAM),
                GameChannel("judge", if (isZh) "法官日志" else "Judge log", GameChannelKind.JUDGE),
            ),
        ),
        GameTemplateDraft(
            id = "score_kill_game",
            title = if (isZh) "积分击杀" else "Score kill",
            subtitle = if (isZh) "保护/计分" else "Shield/score",
            defaultName = if (isZh) "AI 积分击杀局" else "AI Score Kill",
            icon = "game",
            topicPlaceholder = if (isZh) "可选设定，例如：用逻辑、创意或说服力锁定目标" else "Optional setup, e.g. target by logic, creativity, or persuasion",
            publicRules = if (isZh) {
                "每轮先公开发言，再提交击杀或护盾。有效击杀让行动者 +1 分，目标不出局；护盾会挡掉本轮针对目标的击杀得分。投票阶段有效投票者 +1 分。满 5 分是终局重要依据，最终胜负由 AI 在最后一轮后裁定。"
            } else {
                "Each round has public speech, then hit or shield actions. A valid hit gives the actor +1 point and does not eliminate the target; shield blocks hit points against that target. In vote phase, valid voters gain +1 point. Reaching 5 points is key final evidence; AI adjudicates the winner after the final round."
            },
            ruleConfig = GameRuleConfigDraft(
                eliminateEffect = GameEliminateRuleDraft.SCORE,
                protectBlocksEliminate = true,
                eliminateActorPoints = 1,
                voteEffect = GameVoteRuleDraft.SCORE_ACTOR,
                voteActorPoints = 1,
                voteTiePolicy = GameTiePolicyDraft.NO_EFFECT,
                scoreLimit = 5,
                finalJudgePrompt = if (isZh) {
                    "最终轮结束后，AI 优先比较积分，其次看有效击杀、有效保护、投票质量和公开理由。达到 5 分只是强证据，不在中途自动胜利。"
                } else {
                    "After the final round, AI primarily compares points, then valid hits, shields, vote quality, and public reasoning. Reaching 5 points is strong evidence, not an automatic mid-game win."
                },
            ),
            defaultRounds = 5,
            requiresTopic = false,
            identities = scoreKillIdentities,
            abilities = scoreKillAbilities,
            phases = listOf(
                GamePhase("action", if (isZh) "行动" else "Action", 1, listOf("public", "judge"), listOf("score_hit", "score_guard")),
                GamePhase("vote", if (isZh) "投票" else "Vote", 2, listOf("public"), listOf("score_vote")),
            ),
            channels = listOf(
                GameChannel("public", if (isZh) "公开" else "Public", GameChannelKind.PUBLIC),
                GameChannel("judge", if (isZh) "法官日志" else "Judge log", GameChannelKind.JUDGE),
            ),
        ),
        GameTemplateDraft(
            id = "deduction_duel",
            title = if (isZh) "推理对局" else "Deduction",
            subtitle = if (isZh) "逻辑比拼" else "Logic duel",
            defaultName = if (isZh) "AI 推理对局" else "AI Deduction Duel",
            icon = "search",
            topicPlaceholder = if (isZh) "本局要推理/比较的问题，例如：谁能找出方案中的最大风险" else "Question or case to reason about",
            publicRules = if (isZh) "多轮推理、质询、修正和最终投票。每位玩家需要提出证据，不能只给结论。" else "Run rounds of theory, challenge, revision, and final vote. Each player must provide evidence.",
            ruleConfig = GameRuleConfigDraft(
                voteEffect = GameVoteRuleDraft.RECORD_ONLY,
                finalJudgePrompt = if (isZh) {
                    "最终轮结束后，AI 根据推理准确性、证据质量、质询有效性、修正能力和最终投票理由判断胜者。"
                } else {
                    "After the final round, AI judges the winner by reasoning accuracy, evidence quality, challenge effectiveness, revision ability, and final vote rationale."
                },
            ),
            defaultRounds = 3,
            requiresTopic = true,
            identities = deductionIdentities,
            abilities = deductionAbilities,
            phases = listOf(
                GamePhase("theory", if (isZh) "立论" else "Theory", 1, listOf("public"), listOf("submit_theory")),
                GamePhase("challenge", if (isZh) "质询" else "Challenge", 2, listOf("public"), listOf("challenge")),
                GamePhase("vote", if (isZh) "投票" else "Vote", 3, listOf("public"), listOf("final_vote")),
            ),
            channels = listOf(GameChannel("public", if (isZh) "公开" else "Public", GameChannelKind.PUBLIC)),
        ),
        GameTemplateDraft(
            id = "custom_game",
            title = if (isZh) "自定义游戏" else "Custom",
            subtitle = if (isZh) "通用规则" else "Generic",
            defaultName = if (isZh) "AI 游戏局" else "AI Game",
            icon = "launch",
            topicPlaceholder = if (isZh) "写下这局的目标或故事" else "Describe this game's goal or story",
            publicRules = if (isZh) "按轮次行动、公开发言、可投票或由法官判定。具体身份和能力可后续继续编辑。" else "Round-based actions, public speech, voting or host judgment. Identities and abilities can be edited later.",
            ruleConfig = GameRuleConfigDraft(
                voteEffect = GameVoteRuleDraft.PLURALITY_OUT,
                finalJudgePrompt = if (isZh) {
                    "最终轮结束后，AI 按用户写下的公开规则、身份目标、行动结果、投票记录、积分和证据链综合判断胜负；每轮只结算事件。"
                } else {
                    "After the final round, AI judges the winner from the user's public rules, identity goals, action results, votes, points, and evidence chain. Each round only resolves events."
                },
            ),
            defaultRounds = 4,
            requiresTopic = false,
            identities = customIdentities,
            abilities = customAbilities,
            phases = listOf(
                GamePhase("action", if (isZh) "行动" else "Action", 1, listOf("public", "judge"), listOf("custom_action", "custom_hit", "custom_protect")),
                GamePhase("vote", if (isZh) "投票" else "Vote", 2, listOf("public"), listOf("custom_vote")),
            ),
            channels = listOf(
                GameChannel("public", if (isZh) "公开" else "Public", GameChannelKind.PUBLIC),
                GameChannel("judge", if (isZh) "法官日志" else "Judge log", GameChannelKind.JUDGE),
            ),
        ),
    )
}

private fun groupModeTemplates(isZh: Boolean): List<GroupModeTemplate> = listOf(
    GroupModeTemplate(
        mode = GroupMode.FREE_CHAT,
        title = if (isZh) "自由群聊" else "Free chat",
        subtitle = if (isZh) "日常" else "Casual",
        defaultName = if (isZh) "AI 群聊" else "AI Group",
        icon = "group",
        rules = if (isZh) "自然聊天，少抢答；被点名或有明确新观点时再接话。" else "Chat naturally. Reply when mentioned or when you have a clear new angle.",
        roundLimit = 3,
        turnStyle = GroupTurnStyle.BALANCED,
        autoStart = false,
    ),
    GroupModeTemplate(
        mode = GroupMode.ARENA,
        title = if (isZh) "模型竞技" else "Arena",
        subtitle = if (isZh) "评分" else "Score",
        defaultName = if (isZh) "模型竞技场" else "Model Arena",
        icon = "game",
        rules = if (isZh) "每轮围绕同一题目独立作答。裁判按逻辑、清晰度、实用性、创造力给出观察和胜负，不用讨好任何模型。" else "Each round answers the same topic independently. Judge logic, clarity, usefulness, and creativity without favoring any model.",
        roundLimit = 3,
        turnStyle = GroupTurnStyle.ACTIVE,
        autoStart = true,
    ),
    GroupModeTemplate(
        mode = GroupMode.DEBATE,
        title = if (isZh) "辩论赛" else "Debate",
        subtitle = if (isZh) "攻防" else "Rounds",
        defaultName = if (isZh) "AI 辩论局" else "AI Debate",
        icon = "check",
        rules = if (isZh) "围绕辩题进行多轮攻防。每条发言要有立场、理由和回应对象，裁判最后总结强弱点。" else "Run several debate rounds. Each reply needs a stance, reasons, and a target; the judge summarizes strengths and weaknesses.",
        roundLimit = 3,
        turnStyle = GroupTurnStyle.ACTIVE,
        autoStart = true,
    ),
    GroupModeTemplate(
        mode = GroupMode.WORKSHOP,
        title = if (isZh) "协作间" else "Workshop",
        subtitle = if (isZh) "共创" else "Build",
        defaultName = if (isZh) "协作工作间" else "AI Workshop",
        icon = "tools",
        rules = if (isZh) "成员按各自专长拆目标、补风险、给方案。避免重复表态，优先产出可执行结论。" else "Members use their specialties to split goals, surface risks, and produce actionable outcomes.",
        roundLimit = 3,
        turnStyle = GroupTurnStyle.BALANCED,
        autoStart = true,
    ),
    GroupModeTemplate(
        mode = GroupMode.ROUNDED_GAME,
        title = if (isZh) "回合游戏" else "Round game",
        subtitle = if (isZh) "实验" else "Game",
        defaultName = if (isZh) "多智能体游戏" else "Agent Game",
        icon = "launch",
        rules = if (isZh) "按轮次行动。每轮角色先给判断或策略，再根据其他人发言修正；裁判记录关键证据、冲突和本轮排名。" else "Play by rounds. Roles make a move or judgment, revise after others, and the judge tracks evidence, conflicts, and ranking.",
        roundLimit = 4,
        turnStyle = GroupTurnStyle.ACTIVE,
        autoStart = true,
    ),
)

private fun groupModeTemplate(mode: GroupMode, isZh: Boolean): GroupModeTemplate =
    groupModeTemplates(isZh).first { it.mode == mode }

private fun groupModeLabel(mode: GroupMode, isZh: Boolean): String = when (mode) {
    GroupMode.FREE_CHAT -> if (isZh) "自由" else "Free"
    GroupMode.ARENA -> if (isZh) "竞技" else "Arena"
    GroupMode.DEBATE -> if (isZh) "辩论" else "Debate"
    GroupMode.WORKSHOP -> if (isZh) "协作" else "Workshop"
    GroupMode.ROUNDED_GAME -> if (isZh) "回合" else "Rounds"
}

private fun groupGameListSummary(group: Group, isZh: Boolean): String {
    val profile = group.gameProfile
    val kind = if (isZh) "游戏" else "Game"
    val template = profile?.templateName.orEmpty().ifBlank { if (isZh) "自定义" else "Custom" }
    val userRole = profile?.userRole?.let { gameUserRoleLabel(it, isZh) }.orEmpty()
    val flow = profile?.judgeFlowMode?.label(isZh).orEmpty()
    val seats = profile?.seats?.size?.takeIf { it > 0 }?.let {
        if (isZh) "$it 席" else "$it seats"
    }.orEmpty()
    val topic = group.topic.trim()
    return listOf(kind, template, flow, userRole, seats, topic).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun groupTurnStyleLabel(style: GroupTurnStyle, isZh: Boolean): String = when (style) {
    GroupTurnStyle.QUIET -> if (isZh) "安静" else "Quiet"
    GroupTurnStyle.BALANCED -> if (isZh) "平衡" else "Balanced"
    GroupTurnStyle.ACTIVE -> if (isZh) "活跃" else "Active"
}

private fun defaultGroupOpeningPrompt(mode: GroupMode, topic: String, isZh: Boolean): String {
    val cleanTopic = topic.ifBlank {
        if (isZh) "用户还没有指定题目，请先用一句话确认本局目标。" else "The user has not set a topic yet; first confirm the goal in one sentence."
    }
    return when (mode) {
        GroupMode.FREE_CHAT -> if (topic.isBlank()) {
            if (isZh) "大家自然开聊，先抛一个轻松但有内容的话题。" else "Start naturally with a light but meaningful topic."
        } else {
            if (isZh) "围绕「$cleanTopic」自然聊聊。" else "Chat naturally about \"$cleanTopic\"."
        }
        GroupMode.ARENA -> if (isZh) "本局题目：$cleanTopic。请各位独立作答，裁判记录差异并给出本轮观察。" else "Arena topic: $cleanTopic. Each member answers independently; the judge tracks differences."
        GroupMode.DEBATE -> if (isZh) "辩题：$cleanTopic。请先亮明立场，再进入第一轮攻防。" else "Debate topic: $cleanTopic. State positions first, then start round one."
        GroupMode.WORKSHOP -> if (isZh) "协作目标：$cleanTopic。请各自按专长拆解问题，并产出下一步行动。" else "Workshop goal: $cleanTopic. Break it down by specialty and produce next actions."
        GroupMode.ROUNDED_GAME -> if (isZh) "游戏目标：$cleanTopic。现在开始第一轮，每位角色给出判断、证据或行动。" else "Game objective: $cleanTopic. Start round one with each role's judgment, evidence, or action."
    }
}

private fun roleGroupModelLabel(role: Role, isZh: Boolean): String {
    val binding = role.effectiveModelBinding()?.normalized()
        ?: return if (isZh) "默认网关" else "Default gateway"
    return when {
        binding.localModelId.isNotBlank() -> (if (isZh) "本地" else "Local") + " / " + binding.localModelId
        binding.gatewayName.isNotBlank() && binding.model.isNotBlank() -> "${binding.gatewayName} / ${binding.model}"
        binding.gatewayId.isNotBlank() && binding.model.isNotBlank() -> "${binding.gatewayId} / ${binding.model}"
        binding.gatewayName.isNotBlank() -> binding.gatewayName + " / " + if (isZh) "默认模型" else "Default model"
        binding.gatewayId.isNotBlank() -> binding.gatewayId + " / " + if (isZh) "默认模型" else "Default model"
        binding.model.isNotBlank() -> (if (isZh) "默认网关" else "Default gateway") + " / " + binding.model
        else -> if (isZh) "默认网关" else "Default gateway"
    }
}

private fun gameUserRoleLabel(role: GameUserRole, isZh: Boolean): String = when (role) {
    GameUserRole.HOST -> if (isZh) "法官" else "Host"
    GameUserRole.SPECTATOR -> if (isZh) "旁观者" else "Spectator"
    GameUserRole.PLAYER -> if (isZh) "玩家" else "Player"
    GameUserRole.CO_HOST -> if (isZh) "共同主持" else "Co-host"
}

private fun gameIdentityAssignmentLabel(assignment: GameIdentityAssignment, isZh: Boolean): String = when (assignment) {
    GameIdentityAssignment.RANDOM -> if (isZh) "随机" else "Random"
    GameIdentityAssignment.MANUAL -> if (isZh) "手动" else "Manual"
    GameIdentityAssignment.MIXED -> if (isZh) "混合" else "Mixed"
}

private fun GameJudgeFlowMode.label(isZh: Boolean): String = when (this) {
    GameJudgeFlowMode.FREE -> if (isZh) "自由发言" else "Free"
    GameJudgeFlowMode.ORDERED -> if (isZh) "按序发言" else "Ordered"
    GameJudgeFlowMode.CALLED -> if (isZh) "点名发言" else "Called"
    GameJudgeFlowMode.PHASE -> if (isZh) "阶段流程" else "Phased"
}

private fun GameJudgeFlowMode.description(isZh: Boolean): String = when (this) {
    GameJudgeFlowMode.FREE -> if (isZh) "玩家可自然接话；法官只负责关键结算。" else "Players speak naturally; the host mainly settles results."
    GameJudgeFlowMode.ORDERED -> if (isZh) "法官按席位顺序点名，每个玩家完成发言/行动后再推进。" else "The host calls seats in order before advancing."
    GameJudgeFlowMode.CALLED -> if (isZh) "法官可以指定某个玩家发言或行动，适合抓内奸、质询、审问类玩法。" else "The host calls specific seats, useful for traitor-hunt or interrogation games."
    GameJudgeFlowMode.PHASE -> if (isZh) "法官按阶段开放发言、行动、投票和结算按钮。" else "The host advances phases and each phase opens its own controls."
}

private fun GameUserActionPolicy.label(isZh: Boolean): String = when (this) {
    GameUserActionPolicy.OPEN -> if (isZh) "全开放" else "Open"
    GameUserActionPolicy.PHASE -> if (isZh) "按阶段" else "By phase"
    GameUserActionPolicy.JUDGE_CONTROLLED -> if (isZh) "法官控制" else "Judge"
}

private fun GameUserActionPolicy.description(isZh: Boolean): String = when (this) {
    GameUserActionPolicy.OPEN -> if (isZh) "用户发言和自己的技能按钮始终可用。" else "User speech and ability buttons stay available."
    GameUserActionPolicy.PHASE -> if (isZh) "技能按钮只按当前阶段开放，公开发言不锁。" else "Ability buttons follow the current phase; public speech is not locked."
    GameUserActionPolicy.JUDGE_CONTROLLED -> if (isZh) "用户发言和技能都跟随法官阶段；非公开阶段会收起发言入口。" else "Speech and abilities follow host phases; non-public phases lock public speech."
}

private fun GameAbilityEffect.label(isZh: Boolean): String = when (this) {
    GameAbilityEffect.ELIMINATE -> if (isZh) "击杀/出局" else "Eliminate"
    GameAbilityEffect.PROTECT -> if (isZh) "保护" else "Protect"
    GameAbilityEffect.INSPECT -> if (isZh) "查验" else "Inspect"
    GameAbilityEffect.BLOCK -> if (isZh) "封锁" else "Block"
    GameAbilityEffect.REVIVE -> if (isZh) "复活" else "Revive"
    GameAbilityEffect.VOTE -> if (isZh) "投票" else "Vote"
    GameAbilityEffect.MESSAGE_PRIVATE -> if (isZh) "私聊/会议" else "Private"
    GameAbilityEffect.SET_FLAG -> if (isZh) "记录状态" else "Flag"
    GameAbilityEffect.REVEAL -> if (isZh) "公开揭示" else "Reveal"
}

private fun GameAbilityEffect.defaultTrigger(): GameAbilityTrigger = when (this) {
    GameAbilityEffect.VOTE -> GameAbilityTrigger.VOTE
    GameAbilityEffect.MESSAGE_PRIVATE -> GameAbilityTrigger.TEAM_MEETING
    GameAbilityEffect.REVIVE,
    GameAbilityEffect.BLOCK,
    GameAbilityEffect.PROTECT,
    GameAbilityEffect.INSPECT,
    GameAbilityEffect.ELIMINATE,
    GameAbilityEffect.SET_FLAG,
    GameAbilityEffect.REVEAL,
    -> GameAbilityTrigger.PHASE_ACTION
}

private fun GameAbilityEffect.defaultVisibility(): String = when (this) {
    GameAbilityEffect.VOTE,
    GameAbilityEffect.REVEAL,
    -> "public"
    GameAbilityEffect.MESSAGE_PRIVATE -> "team"
    GameAbilityEffect.INSPECT,
    GameAbilityEffect.PROTECT,
    GameAbilityEffect.BLOCK,
    GameAbilityEffect.REVIVE,
    GameAbilityEffect.ELIMINATE,
    GameAbilityEffect.SET_FLAG,
    -> "private"
}

private fun gameAbilityVisibilityLabel(value: String, isZh: Boolean): String = when (value.lowercase()) {
    "public" -> if (isZh) "公开" else "Public"
    "private" -> if (isZh) "私密" else "Private"
    "team" -> if (isZh) "团队" else "Team"
    "judge" -> if (isZh) "法官" else "Judge"
    else -> value
}

private fun defaultGameOpeningPrompt(
    template: GameTemplateDraft,
    topic: String,
    userRole: GameUserRole,
    isZh: Boolean,
): String {
    val topicLine = topic.ifBlank { if (isZh) "按模板规则开始本局。" else "Start this game with the template rules." }
    return if (isZh) {
        "游戏模板：${template.title}。本局设定：$topicLine。用户身份：${gameUserRoleLabel(userRole, true)}。请按公开规则开局，先确认阶段、席位和可见信息。"
    } else {
        "Game template: ${template.title}. Setup: $topicLine. User role: ${gameUserRoleLabel(userRole, false)}. Start by confirming phase, seats, and visible information."
    }
}

private fun buildGameProfile(
    template: GameTemplateDraft,
    selectedRoleIds: List<String>,
    availableRoles: List<Role>,
    judgeRoleId: String,
    userRole: GameUserRole,
    identityAssignment: GameIdentityAssignment,
    judgeFlowMode: GameJudgeFlowMode,
    userActionPolicy: GameUserActionPolicy,
    identityDrafts: List<GameIdentityDraft>,
    abilities: List<GameAbility>,
    manualIdentityByActorId: Map<String, String>,
    publicRules: String,
    ruleConfig: GameRuleConfigDraft,
    roundLimit: Int,
    isZh: Boolean,
): GameProfile {
    val cleanAbilities = abilities
        .map { ability ->
            ability.copy(
                name = ability.name.trim().ifBlank { if (isZh) "技能" else "Ability" },
                visibility = ability.visibility.trim().lowercase().takeIf { it in setOf("public", "private", "team", "judge") } ?: "public",
            )
        }
        .distinctBy { it.id }
    val validAbilityIds = cleanAbilities.map { it.id }.toSet()
    val cleanIdentityDrafts = identityDrafts
        .filter { it.name.isNotBlank() }
        .ifEmpty { template.identities.toGameIdentityDrafts() }
    val identities = cleanIdentityDrafts
        .map { it.toGameIdentity(validAbilityIds, isZh) }
        .distinctBy { it.id }
    val playerRoleIds = selectedRoleIds.filter { it != judgeRoleId }
    val playerCount = playerRoleIds.size + if (userRole == GameUserRole.PLAYER) 1 else 0
    val identityDeck = buildIdentityDrawDeck(
        identities = identities,
        drafts = cleanIdentityDrafts,
        playerCount = playerCount,
        shuffle = identityAssignment != GameIdentityAssignment.MANUAL,
    )
    var randomCursor = 0
    fun nextRandomIdentity(): GameIdentity? {
        if (identityDeck.isEmpty()) return null
        val identity = identityDeck[randomCursor % identityDeck.size]
        randomCursor += 1
        return identity
    }
    fun identityFor(actorId: String): GameIdentity? {
        val manualId = manualIdentityByActorId[actorId].orEmpty()
        if (identityAssignment != GameIdentityAssignment.RANDOM && manualId.isNotBlank()) {
            return identities.firstOrNull { it.id == manualId }
        }
        return if (identityAssignment == GameIdentityAssignment.MANUAL) {
            null
        } else {
            nextRandomIdentity()
        }
    }

    val aiSeats = playerRoleIds.mapIndexedNotNull { index, roleId ->
        val role = availableRoles.firstOrNull { it.id == roleId } ?: return@mapIndexedNotNull null
        val identity = identityFor(roleId)
        GameSeat(
            seatId = "seat_ai_${index + 1}",
            actorType = GameActorType.AI_ROLE,
            actorId = role.id,
            displayName = role.name,
            identityId = identity?.id.orEmpty(),
            teamId = identity?.teamId.orEmpty(),
            status = GameSeatStatus.READY,
            abilityIds = identity?.abilityIds.orEmpty(),
            knowledgeScope = if (identity?.teamId == "wolf") GameKnowledgeScope.TEAM_SECRET else GameKnowledgeScope.SELF_SECRET,
        )
    }
    val userSeat = if (userRole == GameUserRole.PLAYER) {
        val identity = identityFor("user")
        listOf(
            GameSeat(
                seatId = "seat_user",
                actorType = GameActorType.USER,
                actorId = "user",
                displayName = if (isZh) "你" else "You",
                identityId = identity?.id.orEmpty(),
                teamId = identity?.teamId.orEmpty(),
                status = GameSeatStatus.READY,
                abilityIds = identity?.abilityIds.orEmpty(),
                knowledgeScope = if (identity?.teamId == "wolf") GameKnowledgeScope.TEAM_SECRET else GameKnowledgeScope.SELF_SECRET,
            ),
        )
    } else {
        emptyList()
    }

    val seats = aiSeats + userSeat
    val phases = materializeGamePhases(template.phases, cleanAbilities)
    val systemJudge = judgeRoleId.isBlank() && userRole != GameUserRole.HOST
    val initialPhase = if (systemJudge) {
        initialSystemJudgePhase(phases)
    } else {
        phases.minByOrNull { it.order }
    }

    val profile = GameProfile(
        templateId = template.id,
        templateName = template.title,
        userRole = userRole,
        identityAssignment = identityAssignment,
        seats = seats,
        identities = identities,
        abilities = cleanAbilities,
        phases = phases,
        channels = materializeGameChannels(template.channels, seats),
        currentPhaseId = initialPhase?.id.orEmpty(),
        publicRules = publicRules,
        hiddenStateJson = "{}",
        winConditionJson = ruleConfig.toWinConditionJson(),
        judgeFlowMode = judgeFlowMode,
        userActionPolicy = userActionPolicy,
        autoHost = systemJudge || userRole != GameUserRole.HOST,
    )
    return if (systemJudge) {
        profile.withRuntimeState(
            GameRuntimeState(
                roundIndex = 1,
                phaseStartedAt = System.currentTimeMillis(),
                flags = mapOf(
                    GAME_RUNTIME_FLAG_SYSTEM_FLOW_STEP to GAME_RUNTIME_FLOW_SPEECH,
                    GAME_RUNTIME_FLAG_SPEECH_OPEN to "false",
                    GAME_RUNTIME_FLAG_SYSTEM_SPEECH_CURSOR to "0",
                    GAME_RUNTIME_FLAG_SYSTEM_VOTE_CURSOR to "0",
                    GAME_RUNTIME_FLAG_SYSTEM_EVENT_CURSOR to "0",
                ),
            ),
        )
    } else {
        profile
    }
}

private fun initialSystemJudgePhase(phases: List<GamePhase>): GamePhase? =
    phases.firstOrNull { phase ->
        phase.channelIds.any { it.equals("public", ignoreCase = true) } &&
            !phase.id.contains("vote", ignoreCase = true) &&
            !phase.name.contains("投票") &&
            !phase.name.contains("vote", ignoreCase = true)
    } ?: phases.firstOrNull { phase ->
        phase.id.contains("day", ignoreCase = true) ||
            phase.name.contains("白天") ||
            phase.name.contains("发言") ||
            phase.name.contains("speech", ignoreCase = true)
    } ?: phases.minByOrNull { it.order }

private fun buildIdentityDrawDeck(
    identities: List<GameIdentity>,
    drafts: List<GameIdentityDraft>,
    playerCount: Int,
    shuffle: Boolean,
): List<GameIdentity> {
    if (identities.isEmpty() || playerCount <= 0) return emptyList()
    val byId = identities.associateBy { it.id }
    val slots = drafts.flatMap { draft ->
        val identity = byId[draft.id] ?: return@flatMap emptyList()
        List(draft.count.coerceAtLeast(1)) { identity }
    }.ifEmpty { identities }
    val filled = when {
        slots.size == 1 && playerCount > 1 -> List(playerCount) { slots.first() }
        slots.size < playerCount -> slots + List(playerCount - slots.size) { index -> slots[index % slots.size] }
        else -> slots
    }
    return if (shuffle) filled.shuffled() else filled
}

private fun GameRuleConfigDraft.toWinConditionJson(): String {
    val root = JsonObject()
    root.addProperty("scoreLimit", scoreLimit.coerceAtLeast(0))
    root.add("scoring", JsonObject().apply {
        addProperty("enabled", usesScore())
        addProperty("eliminateActorPoints", eliminateActorPoints.coerceIn(-20, 20))
        addProperty("eliminateTargetPoints", eliminateTargetPoints.coerceIn(-20, 20))
        addProperty("voteActorPoints", voteActorPoints.coerceIn(-20, 20))
        addProperty("voteTargetPoints", voteTargetPoints.coerceIn(-20, 20))
    })
    root.add("eliminate", JsonObject().apply {
        addProperty("effect", eliminateEffect.jsonValue())
        addProperty("actorPoints", eliminateActorPoints.coerceIn(-20, 20))
        addProperty("targetPoints", eliminateTargetPoints.coerceIn(-20, 20))
    })
    root.add("protect", JsonObject().apply {
        addProperty("blocksEliminate", protectBlocksEliminate)
        addProperty("preventConsecutiveSameTarget", preventConsecutiveProtectSameTarget)
    })
    root.add("vote", JsonObject().apply {
        addProperty("effect", voteEffect.jsonValue())
        addProperty("actorPoints", voteActorPoints.coerceIn(-20, 20))
        addProperty("targetPoints", voteTargetPoints.coerceIn(-20, 20))
        addProperty("tiePolicy", voteTiePolicy.jsonValue())
    })
    root.add("finalJudgement", JsonObject().apply {
        addProperty("mode", "ai_after_final_round")
        addProperty("timing", "after_round_limit")
        addProperty("criteria", finalJudgePrompt.trim().ifBlank {
            "最终轮结束后，AI 根据本局公开规则、行动/投票记录、积分、出局状态和证据链判断最终胜负；每轮结算不提前判胜。"
        })
    })
    return groupPageGson.toJson(root)
}

private fun GameRuleConfigDraft.usesScore(): Boolean =
    scoreLimit > 0 ||
        eliminateActorPoints != 0 ||
        eliminateTargetPoints != 0 ||
        voteActorPoints != 0 ||
        voteTargetPoints != 0 ||
        eliminateEffect in setOf(GameEliminateRuleDraft.SCORE, GameEliminateRuleDraft.OUT_AND_SCORE) ||
        voteEffect in setOf(GameVoteRuleDraft.SCORE_TARGET, GameVoteRuleDraft.SCORE_ACTOR)

private fun GameEliminateRuleDraft.jsonValue(): String = when (this) {
    GameEliminateRuleDraft.OUT -> "out"
    GameEliminateRuleDraft.SCORE -> "score"
    GameEliminateRuleDraft.OUT_AND_SCORE -> "out_and_score"
    GameEliminateRuleDraft.RECORD_ONLY -> "record_only"
}

private fun GameVoteRuleDraft.jsonValue(): String = when (this) {
    GameVoteRuleDraft.PLURALITY_OUT -> "plurality_out"
    GameVoteRuleDraft.MAJORITY_OUT -> "majority_out"
    GameVoteRuleDraft.SCORE_TARGET -> "score_target"
    GameVoteRuleDraft.SCORE_ACTOR -> "score_actor"
    GameVoteRuleDraft.RECORD_ONLY -> "record_only"
}

private fun GameTiePolicyDraft.jsonValue(): String = when (this) {
    GameTiePolicyDraft.NO_EFFECT -> "no_effect"
    GameTiePolicyDraft.ALL -> "all"
    GameTiePolicyDraft.FIRST -> "first"
}

private fun GameRuleConfigDraft.withEliminateEffect(effect: GameEliminateRuleDraft): GameRuleConfigDraft =
    when (effect) {
        GameEliminateRuleDraft.SCORE -> copy(
            eliminateEffect = effect,
            eliminateActorPoints = eliminateActorPoints.takeIf { it != 0 } ?: 1,
            eliminateTargetPoints = 0,
        )
        GameEliminateRuleDraft.OUT_AND_SCORE -> copy(
            eliminateEffect = effect,
            eliminateActorPoints = eliminateActorPoints.takeIf { it != 0 } ?: 1,
        )
        GameEliminateRuleDraft.OUT,
        GameEliminateRuleDraft.RECORD_ONLY,
        -> copy(
            eliminateEffect = effect,
            eliminateActorPoints = 0,
            eliminateTargetPoints = 0,
        )
    }

private fun GameRuleConfigDraft.withVoteEffect(effect: GameVoteRuleDraft): GameRuleConfigDraft =
    when (effect) {
        GameVoteRuleDraft.SCORE_ACTOR -> copy(
            voteEffect = effect,
            voteActorPoints = voteActorPoints.takeIf { it != 0 } ?: 1,
            voteTargetPoints = 0,
        )
        GameVoteRuleDraft.SCORE_TARGET -> copy(
            voteEffect = effect,
            voteActorPoints = 0,
            voteTargetPoints = voteTargetPoints.takeIf { it != 0 } ?: 1,
        )
        GameVoteRuleDraft.PLURALITY_OUT,
        GameVoteRuleDraft.MAJORITY_OUT,
        GameVoteRuleDraft.RECORD_ONLY,
        -> copy(
            voteEffect = effect,
            voteActorPoints = 0,
            voteTargetPoints = 0,
        )
    }

private fun GameRuleConfigDraft.summaryLabel(isZh: Boolean): String {
    val score = scoreLimit.takeIf { it > 0 }?.let { if (isZh) "$it 分参考" else "$it pts ref" }
    return listOfNotNull(
        eliminateEffect.label(isZh),
        voteEffect.label(isZh),
        score,
    ).joinToString(" · ")
}

private fun GameEliminateRuleDraft.label(isZh: Boolean): String = when (this) {
    GameEliminateRuleDraft.OUT -> if (isZh) "出局" else "Out"
    GameEliminateRuleDraft.SCORE -> if (isZh) "计分" else "Score"
    GameEliminateRuleDraft.OUT_AND_SCORE -> if (isZh) "出局+计分" else "Out+score"
    GameEliminateRuleDraft.RECORD_ONLY -> if (isZh) "仅记录" else "Record"
}

private fun GameVoteRuleDraft.label(isZh: Boolean): String = when (this) {
    GameVoteRuleDraft.PLURALITY_OUT -> if (isZh) "最高票出局" else "Plurality out"
    GameVoteRuleDraft.MAJORITY_OUT -> if (isZh) "多数出局" else "Majority out"
    GameVoteRuleDraft.SCORE_TARGET -> if (isZh) "目标计分" else "Target score"
    GameVoteRuleDraft.SCORE_ACTOR -> if (isZh) "投票者计分" else "Voter score"
    GameVoteRuleDraft.RECORD_ONLY -> if (isZh) "仅记录" else "Record"
}

private fun GameTiePolicyDraft.label(isZh: Boolean): String = when (this) {
    GameTiePolicyDraft.NO_EFFECT -> if (isZh) "平票无效" else "Tie none"
    GameTiePolicyDraft.ALL -> if (isZh) "平票全生效" else "Tie all"
    GameTiePolicyDraft.FIRST -> if (isZh) "取先到" else "First"
}

private fun materializeGameChannels(channels: List<GameChannel>, seats: List<GameSeat>): List<GameChannel> =
    channels.map { channel ->
        if (channel.memberSeatIds.isNotEmpty()) {
            channel
        } else {
            val memberSeatIds = when (channel.kind) {
                GameChannelKind.PUBLIC -> seats.map { it.seatId }
                GameChannelKind.TEAM -> seats
                    .filter { seat -> seat.teamId.isNotBlank() && channel.id.contains(seat.teamId, ignoreCase = true) }
                    .map { it.seatId }
                GameChannelKind.PRIVATE,
                GameChannelKind.DEAD,
                GameChannelKind.JUDGE,
                -> emptyList()
            }
            channel.copy(memberSeatIds = memberSeatIds)
        }
    }

private fun materializeGamePhases(phases: List<GamePhase>, abilities: List<GameAbility>): List<GamePhase> {
    if (phases.isEmpty()) return phases
    val votePhaseId = phases.firstOrNull { phase ->
        phase.id.contains("vote", ignoreCase = true) || phase.name.contains("投票") || phase.name.contains("vote", ignoreCase = true)
    }?.id
    val actionPhaseId = phases.firstOrNull { phase ->
        !phase.id.equals(votePhaseId.orEmpty(), ignoreCase = true) &&
            (
                phase.id.contains("action", ignoreCase = true) ||
                    phase.id.contains("night", ignoreCase = true) ||
                    phase.name.contains("行动") ||
                    phase.name.contains("夜") ||
                    phase.name.contains("action", ignoreCase = true) ||
                    phase.name.contains("night", ignoreCase = true)
                )
    }?.id ?: phases.first().id

    return phases.map { phase ->
        val additions = abilities.filter { ability ->
            when {
                ability.trigger == GameAbilityTrigger.VOTE || ability.effect == GameAbilityEffect.VOTE -> phase.id == (votePhaseId ?: actionPhaseId)
                else -> phase.id == actionPhaseId
            }
        }.map { it.id }
        phase.copy(enabledAbilityIds = (phase.enabledAbilityIds + additions).distinct())
    }
}

@Composable
private fun CreateGroupSection(
    title: String,
    meta: String,
    c: ClawColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(0.7.dp, c.border, RoundedCornerShape(22.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = c.text,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Text(
                meta,
                color = c.subtext.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun GroupModeChip(
    title: String,
    subtitle: String,
    icon: String,
    selected: Boolean,
    c: ClawColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.text else c.cardAlt)
            .border(0.7.dp, if (selected) c.text else c.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClawSymbolIcon(icon, tint = if (selected) c.bg else c.text, modifier = Modifier.size(18.dp))
        Column {
            Text(
                title,
                color = if (selected) c.bg else c.text,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = if (selected) c.bg.copy(alpha = 0.72f) else c.subtext,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GroupCreateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    c: ClawColors,
    singleLine: Boolean,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(c.cardAlt)
            .border(0.6.dp, c.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        singleLine = singleLine,
        cursorBrush = SolidColor(c.text),
        textStyle = TextStyle(color = c.text, fontSize = 14.sp, lineHeight = 19.sp),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = c.subtext.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun GroupRoleSelectionRow(
    role: Role,
    selected: Boolean,
    modelLabel: String,
    c: ClawColors,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(avatar = role.avatar, size = 40.dp, color = if (selected) c.accent else c.subtext)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                role.name,
                color = c.text,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(role.description, modelLabel).filter { it.isNotBlank() }.joinToString(" · "),
                color = c.subtext,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameFlowExecutorPicker(
    selectedRoleIds: List<String>,
    availableRoles: List<Role>,
    judgeRoleId: String,
    userGameRole: GameUserRole,
    c: ClawColors,
    isZh: Boolean,
    onJudgeRoleChange: (String) -> Unit,
    onUserRoleChange: (GameUserRole) -> Unit,
) {
    RuleSectionLabel(text = if (isZh) "谁负责推进流程" else "Flow executor", c = c)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupSmallPill(
            text = if (isZh) "我来当法官" else "I host",
            selected = judgeRoleId.isBlank() && userGameRole == GameUserRole.HOST,
            c = c,
            onClick = {
                onUserRoleChange(GameUserRole.HOST)
                onJudgeRoleChange("")
            },
        )
        GroupSmallPill(
            text = if (isZh) "系统法官" else "System host",
            selected = judgeRoleId.isBlank() && userGameRole != GameUserRole.HOST,
            c = c,
            onClick = {
                onJudgeRoleChange("")
                onUserRoleChange(GameUserRole.PLAYER)
            },
        )
        selectedRoleIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }.forEach { role ->
            GroupSmallPill(
                text = if (isZh) "${role.name} 做法官" else "${role.name} hosts",
                selected = judgeRoleId == role.id,
                c = c,
                onClick = { onJudgeRoleChange(role.id) },
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    Text(
        if (judgeRoleId.isBlank() && userGameRole == GameUserRole.HOST) {
            if (isZh) "你是法官，只负责开局、阶段推进、结算和公开结果，不参与隐藏身份抽签。" else "You host the flow and do not draw a hidden identity."
        } else if (judgeRoleId.isBlank()) {
            if (isZh) "系统法官会按席位点名推进发言、逐个收票、事件期、公开结果和本轮结算；最终轮结束后再交给 AI 做终局判定。你可以作为玩家抽身份。" else "The system host calls each seat for speech, collects votes one by one, advances event windows, public results, and round settlement; after the final round, AI makes the final verdict. You can still play."
        } else {
            if (isZh) "选中的角色是流程执行者；你可以作为玩家抽身份，或只旁观/共同主持。" else "The selected role hosts the flow. You can play, watch, or co-host."
        },
        color = c.subtext,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    if (judgeRoleId.isNotBlank() || userGameRole != GameUserRole.HOST) {
        Spacer(Modifier.height(12.dp))
        RuleSectionLabel(text = if (isZh) "你的参与方式" else "Your seat", c = c)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val roles = if (judgeRoleId.isBlank()) {
                listOf(GameUserRole.PLAYER, GameUserRole.SPECTATOR)
            } else {
                listOf(GameUserRole.PLAYER, GameUserRole.SPECTATOR, GameUserRole.CO_HOST)
            }
            roles.forEach { role ->
                GroupSmallPill(
                    text = gameUserRoleLabel(role, isZh),
                    selected = userGameRole == role,
                    c = c,
                    onClick = { onUserRoleChange(role) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameJudgeFlowPolicyEditor(
    judgeFlowMode: GameJudgeFlowMode,
    userActionPolicy: GameUserActionPolicy,
    c: ClawColors,
    isZh: Boolean,
    onJudgeFlowModeChange: (GameJudgeFlowMode) -> Unit,
    onUserActionPolicyChange: (GameUserActionPolicy) -> Unit,
) {
    RuleSectionLabel(text = if (isZh) "说话/轮次方式" else "Speech flow", c = c)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameJudgeFlowMode.values().forEach { mode ->
            GroupSmallPill(
                text = mode.label(isZh),
                selected = judgeFlowMode == mode,
                c = c,
                onClick = { onJudgeFlowModeChange(mode) },
            )
        }
    }
    Spacer(Modifier.height(9.dp))
    Text(
        judgeFlowMode.description(isZh),
        color = c.subtext,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(12.dp))
    RuleSectionLabel(text = if (isZh) "用户功能按键" else "User controls", c = c)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameUserActionPolicy.values().forEach { policy ->
            GroupSmallPill(
                text = policy.label(isZh),
                selected = userActionPolicy == policy,
                c = c,
                onClick = { onUserActionPolicyChange(policy) },
            )
        }
    }
    Spacer(Modifier.height(9.dp))
    Text(
        userActionPolicy.description(isZh),
        color = c.subtext,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameAbilityPoolEditor(
    abilities: List<GameAbility>,
    c: ClawColors,
    isZh: Boolean,
    onChange: (List<GameAbility>) -> Unit,
) {
    RuleSectionLabel(text = if (isZh) "技能池" else "Ability pool", c = c)
    abilities.forEachIndexed { index, ability ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        GameAbilityEditorRow(
            ability = ability,
            canRemove = abilities.size > 1,
            c = c,
            isZh = isZh,
            onChange = { next ->
                onChange(abilities.map { if (it.id == ability.id) next else it })
            },
            onRemove = {
                onChange(abilities.filterNot { it.id == ability.id })
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    GroupSmallPill(
        text = if (isZh) "+ 新增技能" else "+ Ability",
        selected = false,
        c = c,
        onClick = {
            val id = "custom_${UUID.randomUUID().toString().take(8)}"
            onChange(
                abilities + GameAbility(
                    id = id,
                    name = if (isZh) "新技能" else "New ability",
                    trigger = GameAbilityTrigger.PHASE_ACTION,
                    effect = GameAbilityEffect.SET_FLAG,
                    usageLimit = 1,
                    visibility = "public",
                    description = if (isZh) "按本局规则执行。" else "Resolve by this game's rules.",
                ),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameAbilityEditorRow(
    ability: GameAbility,
    canRemove: Boolean,
    c: ClawColors,
    isZh: Boolean,
    onChange: (GameAbility) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(c.cardAlt)
            .border(0.6.dp, c.border, RoundedCornerShape(17.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                GroupCreateTextField(
                    value = ability.name,
                    onValueChange = { onChange(ability.copy(name = it.take(24))) },
                    placeholder = if (isZh) "技能名，例如：刀人" else "Ability name",
                    c = c,
                    singleLine = true,
                    minHeight = 38.dp,
                )
            }
            if (canRemove) {
                GroupSmallPill(
                    text = if (isZh) "删除" else "Delete",
                    selected = false,
                    c = c,
                    onClick = onRemove,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameAbilityEffect.values().forEach { effect ->
                GroupSmallPill(
                    text = effect.label(isZh),
                    selected = ability.effect == effect,
                    c = c,
                    onClick = {
                        onChange(
                            ability.copy(
                                effect = effect,
                                trigger = effect.defaultTrigger(),
                                visibility = effect.defaultVisibility(),
                            ),
                        )
                    },
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("public", "private", "team", "judge").forEach { visibility ->
                GroupSmallPill(
                    text = gameAbilityVisibilityLabel(visibility, isZh),
                    selected = ability.visibility.equals(visibility, ignoreCase = true),
                    c = c,
                    onClick = { onChange(ability.copy(visibility = visibility)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameIdentityDeckEditor(
    identities: List<GameIdentityDraft>,
    abilities: List<GameAbility>,
    playerSeatCount: Int,
    c: ClawColors,
    isZh: Boolean,
    onChange: (List<GameIdentityDraft>) -> Unit,
) {
    val slotCount = identities.sumOf { it.count.coerceAtLeast(1) }
    RuleSectionLabel(text = if (isZh) "玩家身份卡" else "Player identities", c = c)
    Text(
        if (slotCount < playerSeatCount && identities.size > 1) {
            if (isZh) "身份卡少于玩家时，未覆盖的玩家会从已有身份中补抽。" else "If the deck is short, remaining seats draw from existing identities."
        } else {
            if (isZh) "每张身份卡可设置阵营、数量和拥有的技能。" else "Each identity can define team, count, and abilities."
        },
        color = c.subtext,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    Spacer(Modifier.height(8.dp))
    identities.forEachIndexed { index, identity ->
        if (index > 0) Spacer(Modifier.height(10.dp))
        GameIdentityEditorRow(
            identity = identity,
            abilities = abilities,
            canRemove = identities.size > 1,
            c = c,
            isZh = isZh,
            onChange = { next ->
                onChange(identities.map { if (it.id == identity.id) next else it })
            },
            onRemove = {
                onChange(identities.filterNot { it.id == identity.id })
            },
        )
    }
    Spacer(Modifier.height(10.dp))
    GroupSmallPill(
        text = if (isZh) "+ 新增身份" else "+ Identity",
        selected = false,
        c = c,
        onClick = {
            val id = "identity_${UUID.randomUUID().toString().take(8)}"
            onChange(
                identities + GameIdentityDraft(
                    id = id,
                    name = if (isZh) "新身份" else "New identity",
                    teamId = if (isZh) "阵营" else "team",
                    publicHint = if (isZh) "公开提示" else "Public hint",
                    privatePrompt = if (isZh) "私密身份说明" else "Private identity note",
                    abilityIds = abilities.take(1).map { it.id },
                    count = 1,
                ),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameIdentityEditorRow(
    identity: GameIdentityDraft,
    abilities: List<GameAbility>,
    canRemove: Boolean,
    c: ClawColors,
    isZh: Boolean,
    onChange: (GameIdentityDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(c.cardAlt)
            .border(0.6.dp, c.border, RoundedCornerShape(17.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                GroupCreateTextField(
                    value = identity.name,
                    onValueChange = { onChange(identity.copy(name = it.take(24))) },
                    placeholder = if (isZh) "身份名，例如：内奸" else "Identity name",
                    c = c,
                    singleLine = true,
                    minHeight = 38.dp,
                )
            }
            GroupSmallPill("-", selected = false, c = c, onClick = { onChange(identity.copy(count = (identity.count - 1).coerceAtLeast(1))) })
            Text(
                "x${identity.count.coerceAtLeast(1)}",
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            GroupSmallPill("+", selected = false, c = c, onClick = { onChange(identity.copy(count = (identity.count + 1).coerceAtMost(20))) })
        }
        GroupCreateTextField(
            value = identity.teamId,
            onValueChange = { onChange(identity.copy(teamId = it.take(24))) },
            placeholder = if (isZh) "阵营，例如：好人 / 内奸 / 狼人" else "Team, e.g. village / traitor",
            c = c,
            singleLine = true,
            minHeight = 38.dp,
        )
        GroupCreateTextField(
            value = identity.privatePrompt,
            onValueChange = { onChange(identity.copy(privatePrompt = it.take(220))) },
            placeholder = if (isZh) "私密身份说明，只有拿到该身份的玩家知道" else "Private identity note",
            c = c,
            singleLine = false,
            minHeight = 54.dp,
        )
        if (abilities.isNotEmpty()) {
            RuleSectionLabel(text = if (isZh) "拥有技能" else "Abilities", c = c)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                abilities.forEach { ability ->
                    val selected = ability.id in identity.abilityIds
                    GroupSmallPill(
                        text = ability.name.ifBlank { ability.id },
                        selected = selected,
                        c = c,
                        onClick = {
                            val nextIds = if (selected) {
                                identity.abilityIds.filterNot { it == ability.id }
                            } else {
                                identity.abilityIds + ability.id
                            }
                            onChange(identity.copy(abilityIds = nextIds.distinct()))
                        },
                    )
                }
            }
        }
        if (canRemove) {
            GroupSmallPill(
                text = if (isZh) "删除身份" else "Delete identity",
                selected = false,
                c = c,
                onClick = onRemove,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameSeatIdentityPicker(
    selectedRoleIds: List<String>,
    availableRoles: List<Role>,
    userGameRole: GameUserRole,
    identities: List<GameIdentityDraft>,
    manualIdentityByActorId: MutableMap<String, String>,
    c: ClawColors,
    isZh: Boolean,
) {
    val actors = selectedRoleIds.mapNotNull { roleId ->
        availableRoles.firstOrNull { it.id == roleId }?.let { role -> role.id to role.name }
    } + if (userGameRole == GameUserRole.PLAYER) listOf("user" to if (isZh) "你" else "You") else emptyList()

    if (actors.isEmpty()) {
        Text(
            if (isZh) "选择成员后可分配身份" else "Select members to assign identities",
            color = c.subtext,
            fontSize = 12.sp,
        )
        return
    }

    actors.forEachIndexed { index, (actorId, actorName) ->
        if (index > 0) {
            HorizontalDivider(color = c.border.copy(alpha = 0.42f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            actorName,
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupSmallPill(
                text = if (isZh) "随机" else "Random",
                selected = manualIdentityByActorId[actorId].isNullOrBlank(),
                c = c,
                onClick = { manualIdentityByActorId.remove(actorId) },
            )
            identities.forEach { identity ->
                GroupSmallPill(
                    text = identity.name,
                    selected = manualIdentityByActorId[actorId] == identity.id,
                    c = c,
                    onClick = { manualIdentityByActorId[actorId] = identity.id },
                )
            }
        }
        if (index != actors.lastIndex) Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameRuleConfigEditor(
    config: GameRuleConfigDraft,
    c: ClawColors,
    isZh: Boolean,
    onChange: (GameRuleConfigDraft) -> Unit,
) {
    RuleSectionLabel(text = if (isZh) "行动效果" else "Action effect", c = c)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameEliminateRuleDraft.values().forEach { effect ->
            GroupSmallPill(
                text = effect.label(isZh),
                selected = config.eliminateEffect == effect,
                c = c,
                onClick = { onChange(config.withEliminateEffect(effect)) },
            )
        }
    }
    if (config.eliminateEffect in setOf(GameEliminateRuleDraft.SCORE, GameEliminateRuleDraft.OUT_AND_SCORE)) {
        Spacer(Modifier.height(10.dp))
        RuleNumberStepper(
            title = if (isZh) "行动者得分" else "Actor points",
            value = config.eliminateActorPoints,
            c = c,
            range = 0..20,
            onChange = { onChange(config.copy(eliminateActorPoints = it)) },
        )
        Spacer(Modifier.height(8.dp))
        RuleNumberStepper(
            title = if (isZh) "目标得分" else "Target points",
            value = config.eliminateTargetPoints,
            c = c,
            range = 0..20,
            onChange = { onChange(config.copy(eliminateTargetPoints = it)) },
        )
    }

    Spacer(Modifier.height(12.dp))
    GroupCreateToggleRow(
        title = if (isZh) "保护挡行动效果" else "Protect blocks effect",
        subtitle = if (isZh) "可挡出局、击杀得分或同类效果" else "Blocks out, hit score, or similar effects",
        checked = config.protectBlocksEliminate,
        c = c,
        onClick = { onChange(config.copy(protectBlocksEliminate = !config.protectBlocksEliminate)) },
    )
    Spacer(Modifier.height(8.dp))
    GroupCreateToggleRow(
        title = if (isZh) "禁止连续守同一目标" else "No repeated protect",
        subtitle = if (isZh) "适合守卫类玩法，可关闭做自由护盾" else "For guard-like games; disable for free shields",
        checked = config.preventConsecutiveProtectSameTarget,
        c = c,
        onClick = { onChange(config.copy(preventConsecutiveProtectSameTarget = !config.preventConsecutiveProtectSameTarget)) },
    )

    Spacer(Modifier.height(14.dp))
    RuleSectionLabel(text = if (isZh) "投票效果" else "Vote effect", c = c)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GameVoteRuleDraft.values().forEach { effect ->
            GroupSmallPill(
                text = effect.label(isZh),
                selected = config.voteEffect == effect,
                c = c,
                onClick = { onChange(config.withVoteEffect(effect)) },
            )
        }
    }
    if (config.voteEffect in setOf(GameVoteRuleDraft.PLURALITY_OUT, GameVoteRuleDraft.MAJORITY_OUT)) {
        Spacer(Modifier.height(10.dp))
        RuleSectionLabel(text = if (isZh) "平票处理" else "Tie policy", c = c)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GameTiePolicyDraft.values().forEach { policy ->
                GroupSmallPill(
                    text = policy.label(isZh),
                    selected = config.voteTiePolicy == policy,
                    c = c,
                    onClick = { onChange(config.copy(voteTiePolicy = policy)) },
                )
            }
        }
    }
    if (config.voteEffect == GameVoteRuleDraft.SCORE_ACTOR) {
        Spacer(Modifier.height(10.dp))
        RuleNumberStepper(
            title = if (isZh) "投票者得分" else "Voter points",
            value = config.voteActorPoints,
            c = c,
            range = 0..20,
            onChange = { onChange(config.copy(voteActorPoints = it)) },
        )
    } else if (config.voteEffect == GameVoteRuleDraft.SCORE_TARGET) {
        Spacer(Modifier.height(10.dp))
        RuleNumberStepper(
            title = if (isZh) "目标每票得分" else "Target points/vote",
            value = config.voteTargetPoints,
            c = c,
            range = 0..20,
            onChange = { onChange(config.copy(voteTargetPoints = it)) },
        )
    }

    Spacer(Modifier.height(12.dp))
    RuleNumberStepper(
        title = if (isZh) "终局积分参考" else "Final score ref",
        value = config.scoreLimit,
        c = c,
        range = 0..50,
        zeroLabel = if (isZh) "关闭" else "Off",
        onChange = { onChange(config.copy(scoreLimit = it)) },
    )
    Spacer(Modifier.height(12.dp))
    RuleSectionLabel(text = if (isZh) "终局判定标准" else "Final criteria", c = c)
    GroupCreateTextField(
        value = config.finalJudgePrompt,
        onValueChange = { onChange(config.copy(finalJudgePrompt = it)) },
        placeholder = if (isZh) {
            "最终轮结束后，AI 根据哪些证据判断胜负？例如积分、阵营目标、有效行动、投票理由、公开推理..."
        } else {
            "After the final round, what should AI use to decide the winner? Points, team goals, valid actions, votes, reasoning..."
        },
        c = c,
        singleLine = false,
        minHeight = 86.dp,
    )
}

@Composable
private fun RuleSectionLabel(text: String, c: ClawColors) {
    Text(
        text,
        color = c.subtext,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GroupSmallPill(
    text: String,
    selected: Boolean,
    c: ClawColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) c.text else c.cardAlt)
            .border(0.6.dp, if (selected) c.text else c.border, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) c.bg else c.text,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoundLimitStepper(
    value: Int,
    c: ClawColors,
    isZh: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(c.cardAlt)
            .border(0.6.dp, c.border, RoundedCornerShape(17.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isZh) "轮数" else "Rounds",
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        GroupSmallPill("-", selected = false, c = c, onClick = { onChange(value - 1) })
        Text(
            value.toString(),
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        GroupSmallPill("+", selected = false, c = c, onClick = { onChange(value + 1) })
    }
}

@Composable
private fun RuleNumberStepper(
    title: String,
    value: Int,
    c: ClawColors,
    range: IntRange,
    zeroLabel: String? = null,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(c.cardAlt)
            .border(0.6.dp, c.border, RoundedCornerShape(17.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        GroupSmallPill("-", selected = false, c = c, onClick = { onChange((value - 1).coerceIn(range.first, range.last)) })
        Text(
            zeroLabel?.takeIf { value == 0 } ?: value.toString(),
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        GroupSmallPill("+", selected = false, c = c, onClick = { onChange((value + 1).coerceIn(range.first, range.last)) })
    }
}

@Composable
private fun GroupCreateToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    c: ClawColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(c.cardAlt)
            .border(0.6.dp, c.border, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = c.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (checked) c.text else c.surface)
                .border(0.6.dp, if (checked) c.text else c.border, RoundedCornerShape(13.dp))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) c.bg else c.subtext.copy(alpha = 0.38f)),
            )
        }
    }
}
