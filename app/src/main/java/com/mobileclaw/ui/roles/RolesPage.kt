package com.mobileclaw.ui.roles

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.RoleAvatarDefaults
import com.mobileclaw.agent.TaskType
import com.mobileclaw.town.AgentRoom
import com.mobileclaw.town.RoomFurniture
import com.mobileclaw.town.AgentSpritePack
import com.mobileclaw.town.AgentTownState
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.str
import java.io.File

private const val ROLE_PORTRAIT_STYLE_VERSION = "role_self_portrait_v5"
private const val ROLE_SPRITE_STYLE_VERSION = "role_self_sprite_v1"

private enum class RoleHomeSection {
    OVERVIEW,
    MEMORY,
    WORKS,
    TOOLS,
    IDENTITY,
}

private data class RoleHomePersonality(
    val seed: Int,
    val layoutShift: Int,
    val clutter: Int,
    val warm: Boolean,
    val hasPlant: Boolean,
    val hasLamp: Boolean,
    val hasCable: Boolean,
    val hasTrophy: Boolean,
)

@Composable
fun RolesPage(
    availableRoles: List<Role>,
    currentRole: Role,
    town: AgentTownState,
    workingAgentIds: Set<String> = emptySet(),
    typingAgentIds: Set<String> = emptySet(),
    rolePortraitGeneratingIds: Set<String> = emptySet(),
    onActivate: (Role) -> Unit,
    onOpenDetail: (Role) -> Unit,
    onGeneratePortrait: (Role) -> Unit,
    onEdit: (Role) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit = {},
    showHeader: Boolean = true,
) {
    val c = LocalClawColors.current
    val pageBg = if (c.isDark) Color(0xFF090908) else Color(0xFFF7F8F5)

    BackHandler { onBack() }

    val roles = remember(availableRoles, currentRole.id) {
        availableRoles.sortedWith(
            compareByDescending<Role> { it.id == currentRole.id }
                .thenBy { !it.isBuiltin }
                .thenBy { it.name.ifBlank { it.id } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(pageBg)) {
        if (showHeader) {
            RoleManagementHeader(onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (showHeader) 24.dp else 18.dp,
                vertical = if (showHeader) 10.dp else 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CurrentRolePanel(
                    role = currentRole,
                    room = town.rooms[currentRole.id],
                    isWorking = currentRole.id in workingAgentIds || currentRole.id in typingAgentIds,
                    onOpen = { onOpenDetail(currentRole) },
                )
            }
            item { Spacer(Modifier.height(2.dp)) }
            items(roles, key = { it.id }) { role ->
                RoleListCard(
                    role = role,
                    room = town.rooms[role.id],
                    isActive = role.id == currentRole.id,
                    isWorking = role.id in workingAgentIds || role.id in typingAgentIds,
                    isGeneratingPortrait = role.id in rolePortraitGeneratingIds,
                    onOpen = { onOpenDetail(role) },
                    onActivate = { onActivate(role) },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RoleManagementHeader(onBack: () -> Unit) {
    val c = LocalClawColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).size(44.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = c.text)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("角色管理", color = c.text, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("默认角色与能力偏好", color = c.subtext, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CurrentRolePanel(
    role: Role,
    room: AgentRoom?,
    isWorking: Boolean,
    onOpen: () -> Unit,
) {
    val c = LocalClawColors.current
    val accent = room?.accent?.toComposeColor() ?: accentForRole(role)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (c.isDark) Color(0xFF111315) else Color(0xFF16181A),
                        if (c.isDark) Color(0xFF292B2E) else Color(0xFF42454A),
                    )
                )
            )
            .clickable { onOpen() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(roleInitial(role), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text("当前默认", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            Text(role.name.ifBlank { str(R.string.role_card_unnamed) }, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (isWorking) str(R.string.role_card_generating) else "写作、生活、工作空间均使用此角色。",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoleListCard(
    role: Role,
    room: AgentRoom?,
    isActive: Boolean,
    isWorking: Boolean,
    isGeneratingPortrait: Boolean,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
) {
    val c = LocalClawColors.current
    val accent = room?.accent?.toComposeColor() ?: accentForRole(role)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (c.isDark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.58f))
            .clickable { onOpen() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(roleDotBrush(role, accent)),
            contentAlignment = Alignment.Center,
        ) {
            if (isWorking || isGeneratingPortrait) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = 0.88f)))
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(role.name.ifBlank { str(R.string.role_card_unnamed) }, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = roleRoleSummary(role, room),
                fontSize = 11.sp,
                color = c.subtext,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp,
            )
        }

        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .clickable(enabled = !isActive) { onActivate() }
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isActive) "默认" else "切换",
                color = c.subtext,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun roleInitial(role: Role): String =
    role.name.trim().firstOrNull()?.uppercaseChar()?.toString()
        ?: role.id.firstOrNull()?.uppercaseChar()?.toString()
        ?: "C"

private fun roleRoleSummary(role: Role, room: AgentRoom?): String =
    room?.motto?.takeIf { it.isNotBlank() }
        ?: role.preferredTaskTypes.take(3).joinToString("、") { it.roleTaskLabel() }.takeIf { it.isNotBlank() }
        ?: role.description.ifBlank { "全局助手" }

private fun roleDotBrush(role: Role, accent: Color): Brush {
    return when (role.avatar) {
        RoleAvatarDefaults.CODER -> Brush.linearGradient(listOf(Color(0xFFD97657), Color(0xFFF3C1A9)))
        RoleAvatarDefaults.WEB -> Brush.linearGradient(listOf(Color(0xFF6D76B8), Color(0xFFD8DBFF)))
        RoleAvatarDefaults.PHONE -> Brush.linearGradient(listOf(Color(0xFF5BBF9F), Color(0xFFBFE9D7)))
        RoleAvatarDefaults.CREATOR -> Brush.linearGradient(listOf(Color(0xFF8D93A4), Color(0xFFD6D8DF)))
        else -> Brush.linearGradient(
            listOf(
                if (role.isBuiltin) Color(0xFF161616) else accent,
                if (role.isBuiltin) Color(0xFF646464) else accent.copy(alpha = 0.48f),
            )
        )
    }
}

private fun TaskType.roleTaskLabel(): String = when (this) {
    TaskType.PHONE_CONTROL -> "控手机"
    TaskType.WEB_RESEARCH -> "查资料"
    TaskType.FILE_CREATE -> "写文档"
    TaskType.APP_BUILD -> "建应用"
    TaskType.IMAGE_GENERATION -> "做图片"
    TaskType.VPN_CONTROL -> "VPN"
    TaskType.SKILL_MANAGEMENT -> "管技能"
    TaskType.CODE_EXECUTION -> "写代码"
    TaskType.CHAT,
    TaskType.GENERAL -> "聊天"
}

@Composable
fun RoleDetailPage(
    role: Role,
    currentRole: Role,
    town: AgentTownState,
    isWorking: Boolean,
    isGeneratingPortrait: Boolean,
    onActivate: (Role) -> Unit,
    onGeneratePortrait: (Role) -> Unit,
    onEdit: (Role) -> Unit,
    onOpenHome: (Role) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    val room = town.rooms[role.id]
    val spritePack = room?.portraitSpritePack
        ?.takeIf { it.isNotBlank() }
        ?.let { town.spritePacks[it] }
        ?.takeIf { it.imagePath.isNotBlank() && File(it.imagePath).exists() && it.isFreshRolePortrait() }
    var selectedSection by remember(role.id) { mutableStateOf(RoleHomeSection.OVERVIEW) }

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = c.text)
            }
            Text(role.name.ifBlank { str(R.string.role_detail_title_default) }, color = c.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RoleStatusDot(active = role.id == currentRole.id, working = isWorking)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(34.dp))
                        .background(if (c.isDark) Color(0xFF080808) else Color.White)
                        .border(1.dp, c.border.copy(alpha = 0.75f), RoundedCornerShape(34.dp))
                        .padding(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(
                                if (c.isDark) {
                                    Brush.verticalGradient(listOf(Color(0xFF171717), Color(0xFF050505)))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0xFFF7F7F4), Color.White))
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TopographicLines(Modifier.matchParentSize(), if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.035f))
                        if (spritePack != null) {
                            AnimatedRoleSprite(
                                spritePack = spritePack,
                                // 详情页强制只展示静态肖像，不允许在这里播放角色动画。
                                stateName = "idle",
                                sizeDp = 240,
                                // 加一层留白，保证全身角色图有呼吸空间，不会贴边显得没展示全。
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            RolePortraitEmptyState(
                                isGenerating = isGeneratingPortrait,
                                compact = false,
                                onGenerate = { onGeneratePortrait(role) },
                                dark = c.isDark,
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
                        Text(role.name.ifBlank { str(R.string.role_card_unnamed) }, color = c.text, fontSize = 30.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (isWorking) str(R.string.role_card_generating) else room?.houseName?.ifBlank { str(R.string.role_detail_home_name) } ?: str(R.string.role_detail_home_name),
                            color = c.subtext,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }

            item {
                RoleDetailActions(
                    isActive = role.id == currentRole.id,
                    isBuiltin = role.isBuiltin,
                    onActivate = { onActivate(role) },
                    onOpenHome = { onOpenHome(role) },
                    onGeneratePortrait = { onGeneratePortrait(role) },
                    onEdit = { onEdit(role) },
                )
            }

            item {
                RoleDetailSection(
                    title = str(R.string.role_detail_home_title),
                    icon = Icons.Outlined.Home,
                    lines = listOf(
                        room?.houseName ?: str(R.string.role_detail_home_for, role.name.ifBlank { str(R.string.role_card_unnamed) }),
                        room?.style ?: str(R.string.role_detail_home_style),
                        room?.motto?.ifBlank { role.description } ?: role.description,
                    ).filter { it.isNotBlank() },
                )
            }

            item {
                RoleDetailSection(
                    title = str(R.string.role_detail_identity_title),
                    icon = Icons.Outlined.Badge,
                    lines = listOf(
                        role.description,
                        if (role.preferredTaskTypes.isNotEmpty()) {
                            str(R.string.role_detail_tasks_label, role.preferredTaskTypes.joinToString(" / ") { it.roleTaskLabel() })
                        } else "",
                        str(R.string.role_detail_model_label, role.modelOverride ?: str(R.string.role_edit_b11de2)),
                    ).filter { it.isNotBlank() },
                )
            }

            item {
                RoleDetailSection(
                    title = str(R.string.role_detail_memory_tools_title),
                    icon = Icons.Outlined.Memory,
                    lines = listOf(
                        room?.wallPins?.take(3)?.joinToString(" / ") { it.title }.orEmpty().ifBlank { str(R.string.role_detail_no_memory) },
                        room?.toolbox?.take(5)?.joinToString(" / ") { it.title }.orEmpty().ifBlank {
                            if (role.forcedSkillIds.isNotEmpty()) {
                                str(R.string.role_detail_forced_skills_label, role.forcedSkillIds.take(5).joinToString(" / "))
                            } else {
                                str(R.string.role_detail_general_reasoning)
                            }
                        },
                    ),
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun RoleHomePage(
    role: Role,
    currentRole: Role,
    town: AgentTownState,
    isWorking: Boolean,
    onBack: () -> Unit,
    onEdit: (Role) -> Unit,
) {
    val c = LocalClawColors.current
    val room = town.rooms[role.id]
    val spritePack = room?.characterSpritePack
        ?.takeIf { it.isNotBlank() }
        ?.let { town.spritePacks[it] }
        ?.takeIf { it.imagePath.isNotBlank() && File(it.imagePath).exists() && it.isFreshRolePortrait() }
    var selectedSection by remember(role.id) { mutableStateOf(RoleHomeSection.OVERVIEW) }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(if (c.isDark) Color(0xFF070707) else Color(0xFF11130F))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                RoleHomeHud(
                    role = role,
                    room = room,
                    isWorking = isWorking,
                    onBack = onBack,
                    onEdit = { onEdit(role) },
                )
            }
            item {
                RoleHomeScene(
                    role = role,
                    room = room,
                    spritePack = spritePack,
                    isActive = role.id == currentRole.id,
                    isWorking = isWorking,
                    selectedSection = selectedSection,
                    onSelectSection = { selectedSection = it },
                )
            }
            item {
                RoleHomeDialogue(room = room, isWorking = isWorking)
            }
            item {
                RoleHomeMeaningPanel(
                    room = room,
                    role = role,
                    selectedSection = selectedSection,
                    onSelectSection = { selectedSection = it },
                )
            }
            item { RoleHomeInventory(room = room, role = role, selectedSection = selectedSection) }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun RoleHomeHud(
    role: Role,
    room: AgentRoom?,
    isWorking: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = LocalClawColors.current
    val accent = room?.accent?.toComposeColor() ?: accentForRole(role)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clip(RoundedCornerShape(0.dp))
            .background(Color(0xFF10100E))
            .border(2.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(0.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameHudIconButton(onClick = onBack, label = "Back") {
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = room?.houseName ?: str(R.string.role_detail_home_for, role.name.ifBlank { role.id }),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(99.dp)).background(if (isWorking) accent else Color.White.copy(alpha = 0.55f)))
                Text(
                    text = if (isWorking) room?.workingLine?.ifBlank { role.name } ?: role.name else role.name.ifBlank { role.id },
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GameHudIconButton(onClick = onEdit, label = str(R.string.role_detail_edit)) {
            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun GameHudIconButton(
    onClick: () -> Unit,
    label: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.36f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        @Suppress("UNUSED_VARIABLE")
        val accessibilityLabel = label
        content()
    }
}

@Composable
private fun RoleHomeScene(
    role: Role,
    room: AgentRoom?,
    spritePack: AgentSpritePack?,
    isActive: Boolean,
    isWorking: Boolean,
    selectedSection: RoleHomeSection,
    onSelectSection: (RoleHomeSection) -> Unit,
) {
    val c = LocalClawColors.current
    val accent = room?.accent?.toComposeColor() ?: accentForRole(role)
    val variant = room?.houseSprite ?: "studio"
    val personality = remember(role.id, role.name, role.description, room?.style, room?.motto) {
        roleHomePersonality(role, room)
    }
    val animatedCharacterPack = spritePack?.takeIf {
        (it.columns > 1 || it.rows > 1) &&
            (it.kind == "character" || it.notes.contains(ROLE_SPRITE_STYLE_VERSION))
    }
    val portraitPack = spritePack?.takeIf { it.columns == 1 && it.rows == 1 }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFF070807))
            .border(3.dp, Color(0xFFD8D8D2))
            .padding(5.dp)
            .background(Color(0xFF121410))
            .border(1.dp, Color.Black.copy(alpha = 0.80f))
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val nx = tap.x / size.width.toFloat()
                    val ny = tap.y / size.height.toFloat()
                    val next = when {
                        nx < 0.43f && ny < 0.40f -> RoleHomeSection.IDENTITY
                        nx > 0.50f && ny < 0.38f -> RoleHomeSection.MEMORY
                        nx < 0.44f && ny > 0.38f && ny < 0.78f -> RoleHomeSection.WORKS
                        nx > 0.60f && ny > 0.48f -> RoleHomeSection.TOOLS
                        nx in 0.40f..0.60f && ny > 0.54f -> RoleHomeSection.IDENTITY
                        else -> RoleHomeSection.OVERVIEW
                    }
                    onSelectSection(next)
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val tile = w / 20f
            val wallH = tile * 7.2f
            val floorTop = wallH
            val wall = when (room?.houseSprite) {
                "terminal" -> Color(0xFF1B2330)
                "library" -> Color(0xFF2C241A)
                "tower" -> Color(0xFF172A33)
                "workshop" -> Color(0xFF302034)
                "warehouse" -> Color(0xFF2B261B)
                "bunker" -> Color(0xFF20242B)
                else -> Color(0xFF243026)
            }
            val floorA = when (room?.houseSprite) {
                "terminal" -> Color(0xFF172033)
                "library" -> Color(0xFF3A2B1E)
                "tower" -> Color(0xFF1D3540)
                "workshop" -> Color(0xFF37243A)
                "warehouse" -> Color(0xFF3A3323)
                "bunker" -> Color(0xFF2A2D32)
                else -> Color(0xFF2E3A2B)
            }
            val floorB = floorA.copy(alpha = 0.72f)

            drawRect(Color(0xFF070807), size = androidx.compose.ui.geometry.Size(w, h))
            drawRect(wall, topLeft = androidx.compose.ui.geometry.Offset(tile * 0.9f, tile * 0.9f), size = androidx.compose.ui.geometry.Size(w - tile * 1.8f, wallH - tile * 0.55f))
            drawRect(Color.White.copy(alpha = 0.055f), topLeft = androidx.compose.ui.geometry.Offset(tile * 1.4f, tile * 1.35f), size = androidx.compose.ui.geometry.Size(w - tile * 2.8f, tile * 0.18f))
            drawRect(Color.Black.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(tile * 1.4f, wallH - tile * 0.92f), size = androidx.compose.ui.geometry.Size(w - tile * 2.8f, tile * 0.34f))
            for (x in 0..20) {
                val color = if (x % 2 == 0) Color.White.copy(alpha = 0.022f) else Color.Black.copy(alpha = 0.045f)
                drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x * tile, tile * 1.55f), size = androidx.compose.ui.geometry.Size(tile, wallH - tile * 1.35f))
            }
            repeat(4) { i ->
                val px = tile * (2.4f + i * 4.15f)
                drawRect(Color.Black.copy(alpha = 0.10f), topLeft = androidx.compose.ui.geometry.Offset(px, tile * 2.0f), size = androidx.compose.ui.geometry.Size(tile * 2.6f, tile * 3.4f))
                drawRect(Color.White.copy(alpha = 0.055f), topLeft = androidx.compose.ui.geometry.Offset(px + tile * 0.16f, tile * 2.14f), size = androidx.compose.ui.geometry.Size(tile * 2.28f, tile * 0.18f))
            }

            for (y in 0..13) {
                for (x in 0..19) {
                    val distance = y / 13f
                    val edgeDarken = when {
                        x < 1 || x > 18 || y > 12 -> 0.58f
                        y < 2 -> 0.82f
                        else -> 0.96f - distance * 0.10f
                    }
                    val color = (if ((x + y) % 2 == 0) floorA else floorB).copy(alpha = edgeDarken)
                    drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x * tile, floorTop + y * tile), size = androidx.compose.ui.geometry.Size(tile, tile))
                    if ((x + y) % 5 == 0) {
                        drawRect(Color.White.copy(alpha = 0.018f), topLeft = androidx.compose.ui.geometry.Offset(x * tile + tile * 0.18f, floorTop + y * tile + tile * 0.18f), size = androidx.compose.ui.geometry.Size(tile * 0.18f, tile * 0.18f))
                    }
                }
            }

            drawRect(Color.Black.copy(alpha = 0.38f), topLeft = androidx.compose.ui.geometry.Offset(0f, floorTop - 4.dp.toPx()), size = androidx.compose.ui.geometry.Size(w, 8.dp.toPx()))
            drawRect(Color.White.copy(alpha = 0.12f), topLeft = androidx.compose.ui.geometry.Offset(tile * 1.2f, floorTop - tile * 0.55f), size = androidx.compose.ui.geometry.Size(w - tile * 2.4f, tile * 0.16f))
            drawRect(Color.Black.copy(alpha = 0.20f), topLeft = androidx.compose.ui.geometry.Offset(tile * 1.2f, h - tile * 1.55f), size = androidx.compose.ui.geometry.Size(w - tile * 2.4f, tile * 0.38f))
            drawPixelRoomFrame(tile, w, h, accent)
            drawPixelRug(tile, accent, variant)

            drawRoomFurniture(tile, wallH, h, accent, room, frontLayer = false)
            drawPersonalizedBackdrop(tile, wallH, h, accent, variant, personality, room)
            drawPixelDesk(tile, wallH, accent, variant, personality)
            drawPixelShelf(tile, accent, room?.wallPins?.size ?: 0)
            drawPixelToolbox(tile, h, accent, room?.toolbox?.size ?: 0)
            drawPixelShowcase(tile, wallH, accent, room?.showcase?.size ?: 0)
            drawRoleHomeSpecials(tile, wallH, h, accent, variant, room)
            drawRoomFurniture(tile, wallH, h, accent, room, frontLayer = true)
            drawPersonalizedForeground(tile, wallH, h, accent, variant, personality, room)
            drawHomeHotspots(tile, accent, selectedSection)

            if (isWorking) {
                drawRect(accent.copy(alpha = 0.16f), topLeft = androidx.compose.ui.geometry.Offset(tile * 2f, tile * 2f), size = androidx.compose.ui.geometry.Size(tile * 16f, tile * 16f), style = Stroke(width = 2.dp.toPx()))
            }
            if (isActive) {
                drawRect(Color.White.copy(alpha = 0.20f), topLeft = androidx.compose.ui.geometry.Offset(tile, tile), size = androidx.compose.ui.geometry.Size(w - tile * 2f, h - tile * 2f), style = Stroke(width = 1.dp.toPx()))
            }
            drawRoleHomeSelection(tile, selectedSection, accent)
        }

        if (portraitPack != null) {
            RoleHomeIdentityProjection(
                spritePack = portraitPack,
                accent = accent,
                framed = animatedCharacterPack != null,
                modifier = Modifier
                    .align(if (animatedCharacterPack != null) Alignment.TopStart else Alignment.TopEnd)
                    .padding(
                        start = if (animatedCharacterPack != null) 35.dp else 0.dp,
                        top = if (animatedCharacterPack != null) 34.dp else 54.dp,
                        end = if (animatedCharacterPack == null) 58.dp else 0.dp,
                    ),
            )
        }

        if (animatedCharacterPack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .size(118.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedRoleSprite(
                    spritePack = animatedCharacterPack,
                    stateName = if (isWorking) "working" else "idle",
                    sizeDp = 118,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            RolePixelResident(
                accent = accent,
                variant = variant,
                personality = personality,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp),
            )
        }
        RoleHomeSceneHint(
            section = selectedSection,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 18.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoleHomeSelection(
    tile: Float,
    selectedSection: RoleHomeSection,
    accent: Color,
) {
    val rect = when (selectedSection) {
        RoleHomeSection.IDENTITY -> androidx.compose.ui.geometry.Offset(tile * 2.0f, tile * 2.0f) to androidx.compose.ui.geometry.Size(tile * 4.8f, tile * 4.5f)
        RoleHomeSection.MEMORY -> androidx.compose.ui.geometry.Offset(tile * 10.7f, tile * 1.4f) to androidx.compose.ui.geometry.Size(tile * 7.2f, tile * 5.0f)
        RoleHomeSection.WORKS -> androidx.compose.ui.geometry.Offset(tile * 2.0f, tile * 8.4f) to androidx.compose.ui.geometry.Size(tile * 6.3f, tile * 4.2f)
        RoleHomeSection.TOOLS -> androidx.compose.ui.geometry.Offset(tile * 13.2f, tile * 9.0f) to androidx.compose.ui.geometry.Size(tile * 4.8f, tile * 6.4f)
        RoleHomeSection.OVERVIEW -> return
    }
    drawRect(accent.copy(alpha = 0.12f), topLeft = rect.first, size = rect.second)
    drawRect(accent.copy(alpha = 0.92f), topLeft = rect.first, size = rect.second, style = Stroke(width = 2.dp.toPx()))
}

@Composable
private fun RoleHomeIdentityProjection(
    spritePack: AgentSpritePack,
    accent: Color,
    framed: Boolean,
    modifier: Modifier = Modifier,
) {
    val projectionWidth = if (framed) 72.dp else 82.dp
    val projectionHeight = if (framed) 72.dp else 104.dp
    val frameColor = if (framed) Color(0xFF6A4A2F) else accent.copy(alpha = 0.46f)
    Box(
        modifier = modifier
            .size(width = projectionWidth, height = projectionHeight)
            .background(Color.Black.copy(alpha = 0.58f))
            .border(2.dp, Color.Black.copy(alpha = 0.75f))
            .padding(3.dp)
            .background(Color(0xFF101410))
            .border(2.dp, frameColor),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedRoleSprite(
            spritePack = spritePack,
            stateName = "idle",
            sizeDp = if (framed) 64 else 92,
            modifier = Modifier.fillMaxSize().padding(if (framed) 3.dp else 5.dp),
            // Home 墙上的身份投影也改为 Fit，避免静态肖像在小窗里继续被裁掉。
            contentScale = ContentScale.Fit,
        )
        Canvas(Modifier.matchParentSize()) {
            drawRect(Color.White.copy(alpha = 0.14f), size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
            drawRect(
                accent.copy(alpha = 0.42f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 8.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width * 0.62f, 3.dp.toPx()),
            )
            drawRect(
                Color.White.copy(alpha = 0.08f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.74f, 0f),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
            )
        }
    }
}

@Composable
private fun RoleHomeSceneHint(section: RoleHomeSection, modifier: Modifier = Modifier) {
    val label = when (section) {
        RoleHomeSection.OVERVIEW -> str(R.string.role_home_tap_hint)
        RoleHomeSection.MEMORY -> str(R.string.role_home_memory_detail)
        RoleHomeSection.WORKS -> str(R.string.role_home_work_detail)
        RoleHomeSection.TOOLS -> str(R.string.role_home_tool_detail)
        RoleHomeSection.IDENTITY -> str(R.string.role_home_identity_detail)
    }
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.28f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.86f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RolePixelResident(
    accent: Color,
    variant: String,
    personality: RoleHomePersonality,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / 13f
            val x = size.width / 2f
            val y = size.height / 2f + unit * 0.2f
            fun rect(cx: Float, cy: Float, w: Float, h: Float, color: Color) {
                drawRect(
                    color,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - w / 2f, cy - h / 2f),
                    size = androidx.compose.ui.geometry.Size(w, h),
                )
            }
            rect(x, y + unit * 5.05f, unit * 5.4f, unit * 0.56f, Color.Black.copy(alpha = 0.30f))
            val skin = if (personality.warm) Color(0xFFFFD7A0) else Color(0xFFE8BE8A)
            val hair = when (variant) {
                "terminal", "bunker" -> Color(0xFF101010)
                "tower" -> Color(0xFF1A3440)
                "workshop" -> Color(0xFF4A243A)
                "library" -> Color(0xFF4A3322)
                else -> if (personality.seed % 2 == 0) Color(0xFF251A14) else Color(0xFF332318)
            }
            val outline = Color(0xFF130F0C)
            val outfit = if (personality.warm) accent.copy(alpha = 0.88f) else accent.copy(alpha = 0.72f)
            rect(x, y - unit * 1.68f, unit * 3.55f, unit * 3.18f, outline)
            rect(x, y - unit * 1.65f, unit * 3.0f, unit * 2.8f, skin)
            rect(x, y - unit * 3.06f, unit * 4.0f, unit * 1.0f, outline)
            rect(x, y - unit * 3.02f, unit * 3.55f, unit * 0.78f, hair)
            rect(x - unit * 1.48f, y - unit * 2.24f, unit * 0.82f, unit * 1.28f, outline)
            rect(x + unit * 1.48f, y - unit * 2.24f, unit * 0.82f, unit * 1.28f, outline)
            rect(x - unit * 1.35f, y - unit * 2.25f, unit * 0.56f, unit * 1.05f, hair)
            rect(x + unit * 1.35f, y - unit * 2.25f, unit * 0.56f, unit * 1.05f, hair)
            if (personality.seed % 3 == 1) {
                rect(x - unit * 0.2f, y - unit * 3.6f, unit * 1.8f, unit * 0.42f, hair.copy(alpha = 0.95f))
            }
            if (variant == "terminal" || variant == "bunker") {
                rect(x, y - unit * 1.55f, unit * 2.0f, unit * 0.36f, accent.copy(alpha = 0.95f))
                rect(x - unit * 1.25f, y - unit * 1.15f, unit * 0.32f, unit * 0.8f, Color(0xFF111111))
                rect(x + unit * 1.25f, y - unit * 1.15f, unit * 0.32f, unit * 0.8f, Color(0xFF111111))
            } else {
                rect(x - unit * 0.58f, y - unit * 1.52f, unit * 0.34f, unit * 0.34f, Color(0xFF151515))
                rect(x + unit * 0.58f, y - unit * 1.52f, unit * 0.34f, unit * 0.34f, Color(0xFF151515))
            }
            rect(x, y - unit * 0.68f, unit * 0.9f, unit * 0.22f, Color(0xFFB86158))
            rect(x, y + unit * 1.48f, unit * 4.10f, unit * 3.88f, outline)
            rect(x, y + unit * 1.35f, unit * 3.56f, unit * 3.55f, outfit)
            rect(x, y + unit * 2.38f, unit * 3.12f, unit * 0.56f, outfit.copy(alpha = 0.58f))
            rect(x, y + unit * 0.46f, unit * 4.25f, unit * 0.58f, Color.White.copy(alpha = 0.32f))
            rect(x - unit * 1.42f, y + unit * 1.35f, unit * 0.42f, unit * 3.15f, Color.Black.copy(alpha = 0.24f))
            rect(x + unit * 1.42f, y + unit * 1.35f, unit * 0.42f, unit * 3.15f, Color.Black.copy(alpha = 0.24f))
            rect(x - unit * 2.62f, y + unit * 1.5f, unit * 0.92f, unit * 3.05f, outline)
            rect(x + unit * 2.62f, y + unit * 1.5f, unit * 0.92f, unit * 3.05f, outline)
            rect(x - unit * 2.55f, y + unit * 1.5f, unit * 0.58f, unit * 2.75f, skin)
            rect(x + unit * 2.55f, y + unit * 1.5f, unit * 0.58f, unit * 2.75f, skin)
            rect(x - unit * 1.0f, y + unit * 4.0f, unit * 0.9f, unit * 1.9f, Color(0xFF202020))
            rect(x + unit * 1.0f, y + unit * 4.0f, unit * 0.9f, unit * 1.9f, Color(0xFF202020))
            rect(x - unit * 1.18f, y + unit * 5.05f, unit * 1.35f, unit * 0.42f, Color(0xFF111111))
            rect(x + unit * 1.18f, y + unit * 5.05f, unit * 1.35f, unit * 0.42f, Color(0xFF111111))
            when (variant) {
                "tower" -> {
                    rect(x + unit * 2.95f, y - unit * 0.9f, unit * 0.42f, unit * 2.7f, accent.copy(alpha = 0.86f))
                    rect(x + unit * 3.05f, y - unit * 2.15f, unit * 1.0f, unit * 0.24f, accent.copy(alpha = 0.60f))
                }
                "workshop" -> {
                    rect(x - unit * 3.0f, y + unit * 0.7f, unit * 0.38f, unit * 2.8f, Color(0xFFFFD166))
                    rect(x - unit * 3.0f, y - unit * 0.9f, unit * 1.0f, unit * 0.42f, Color(0xFFFFD166))
                }
                "library" -> {
                    rect(x - unit * 3.05f, y + unit * 0.3f, unit * 0.82f, unit * 2.0f, Color(0xFFF6D7A7))
                    rect(x - unit * 3.05f, y - unit * 0.3f, unit * 0.82f, unit * 0.18f, accent.copy(alpha = 0.82f))
                }
            }
            if (personality.hasTrophy) {
                rect(x + unit * 3.05f, y + unit * 0.65f, unit * 0.7f, unit * 0.45f, Color(0xFFFFD166))
                rect(x + unit * 3.05f, y + unit * 1.02f, unit * 0.24f, unit * 0.48f, Color(0xFFFFD166))
            }
            drawRect(Color.White.copy(alpha = 0.08f), topLeft = androidx.compose.ui.geometry.Offset(x - unit * 2.1f, y - unit * 3.38f), size = androidx.compose.ui.geometry.Size(unit * 4.2f, unit * 7.55f), style = Stroke(width = unit * 0.12f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelRoomFrame(tile: Float, width: Float, height: Float, accent: Color) {
    val inset = tile * 0.85f
    drawRect(Color.White.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(width - inset * 2f, height - inset * 2f), style = Stroke(width = 2.dp.toPx()))
    drawRect(Color.Black.copy(alpha = 0.22f), topLeft = androidx.compose.ui.geometry.Offset(inset + tile * 0.16f, inset + tile * 0.16f), size = androidx.compose.ui.geometry.Size(width - inset * 2.32f, height - inset * 2.32f), style = Stroke(width = 1.dp.toPx()))
    listOf(
        androidx.compose.ui.geometry.Offset(inset, inset),
        androidx.compose.ui.geometry.Offset(width - inset - tile * 0.7f, inset),
        androidx.compose.ui.geometry.Offset(inset, height - inset - tile * 0.7f),
        androidx.compose.ui.geometry.Offset(width - inset - tile * 0.7f, height - inset - tile * 0.7f),
    ).forEach {
        drawRect(accent.copy(alpha = 0.28f), topLeft = it, size = androidx.compose.ui.geometry.Size(tile * 0.7f, tile * 0.7f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelRug(tile: Float, accent: Color, variant: String) {
    val x = tile * 5.6f
    val y = tile * 11.8f
    val rugColor = when (variant) {
        "terminal" -> Color(0xFF252A45)
        "tower" -> Color(0xFF173B43)
        "workshop" -> Color(0xFF432644)
        "library" -> Color(0xFF4B341E)
        "warehouse" -> Color(0xFF44371F)
        else -> Color(0xFF354829)
    }
    val rugW = tile * 8.8f
    val rugH = tile * 4.6f
    drawRect(Color.Black.copy(alpha = 0.22f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.22f, y + tile * 0.20f), size = androidx.compose.ui.geometry.Size(rugW, rugH))
    drawRect(rugColor.copy(alpha = 0.82f), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(rugW, rugH))
    drawRect(accent.copy(alpha = 0.32f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.38f, y + tile * 0.36f), size = androidx.compose.ui.geometry.Size(rugW - tile * 0.76f, tile * 0.18f))
    drawRect(Color.Black.copy(alpha = 0.25f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.38f, y + rugH - tile * 0.44f), size = androidx.compose.ui.geometry.Size(rugW - tile * 0.76f, tile * 0.18f))
    for (i in 0..4) {
        drawRect(Color.White.copy(alpha = 0.026f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * (0.8f + i * 1.52f), y + tile * 0.7f), size = androidx.compose.ui.geometry.Size(tile * 0.48f, rugH - tile * 1.4f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoomFurniture(
    tile: Float,
    wallH: Float,
    height: Float,
    accent: Color,
    room: AgentRoom?,
    frontLayer: Boolean,
) {
    val furniture = room?.furniture.orEmpty().filter {
        val front = it.layer != "back"
        front == frontLayer
    }
    furniture.forEach { item ->
        drawFurnitureItem(item, tile, wallH, height, accent)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFurnitureItem(
    item: RoomFurniture,
    tile: Float,
    wallH: Float,
    height: Float,
    accent: Color,
) {
    val itemAccent = item.color.toComposeColor() ?: accent
    val x = tile * item.x.coerceIn(0, 19)
    val y = if (item.layer == "back") {
        tile * item.y.coerceIn(0, 19)
    } else {
        wallH + tile * (item.y.coerceIn(0, 19) - 7).coerceAtLeast(0)
    }.coerceAtMost(height - tile)
    val w = tile * item.width.coerceIn(1, 8)
    val h = tile * item.height.coerceIn(1, 8)
    val wood = Color(0xFF5A3A24)
    val darkWood = Color(0xFF2B1B12)
    val metal = Color(0xFF171A1F)
    val paper = Color(0xFFF5E7C8)

    fun shadow(alpha: Float = 0.22f) {
        if (item.layer != "back") {
            drawRect(Color.Black.copy(alpha = alpha), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.18f, y + h - tile * 0.18f), size = androidx.compose.ui.geometry.Size(w, tile * 0.28f))
        }
    }

    when (item.type.lowercase()) {
        "bed" -> {
            shadow()
            drawRect(darkWood, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
            drawRect(Color(0xFF27324A), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.25f, y + tile * 0.28f), size = androidx.compose.ui.geometry.Size(w - tile * 0.5f, h - tile * 0.55f))
            drawRect(itemAccent.copy(alpha = 0.72f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.25f, y + tile * 0.28f), size = androidx.compose.ui.geometry.Size(w - tile * 0.5f, tile * 0.46f))
            drawRect(Color.White.copy(alpha = 0.26f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.55f, y + tile * 1.0f), size = androidx.compose.ui.geometry.Size(w - tile * 1.1f, tile * 0.58f))
        }
        "bookcase", "shelf" -> {
            drawRect(darkWood, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
            repeat(item.height.coerceIn(1, 6)) { row ->
                val ry = y + tile * (0.45f + row * 0.9f)
                drawRect(wood, topLeft = androidx.compose.ui.geometry.Offset(x, ry), size = androidx.compose.ui.geometry.Size(w, tile * 0.18f))
                repeat((item.width + 1).coerceIn(2, 8)) { col ->
                    val colors = listOf(itemAccent, Color(0xFFFBBF24), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFE5E7EB))
                    drawRect(colors[(row + col) % colors.size].copy(alpha = 0.72f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * (0.35f + col * 0.62f), ry - tile * 0.48f), size = androidx.compose.ui.geometry.Size(tile * 0.32f, tile * 0.48f))
                }
            }
        }
        "terminal", "console" -> {
            shadow(0.30f)
            drawRect(metal, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
            drawRect(Color(0xFF050C12), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.22f, y + tile * 0.22f), size = androidx.compose.ui.geometry.Size(w - tile * 0.44f, (h * 0.46f).coerceAtLeast(tile * 0.8f)))
            repeat(3) { i ->
                drawRect(itemAccent.copy(alpha = 0.50f + i * 0.12f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.55f, y + tile * (0.62f + i * 0.48f)), size = androidx.compose.ui.geometry.Size((w - tile * 1.4f) * (1f - i * 0.16f), tile * 0.14f))
            }
            drawRect(Color.Black.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(x, y + h * 0.62f), size = androidx.compose.ui.geometry.Size(w, tile * 0.18f))
        }
        "plant" -> {
            shadow(0.18f)
            drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.22f, y + h * 0.62f), size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.28f))
            drawRect(Color(0xFF1F4A2E), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.34f, y + h * 0.30f), size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.36f))
            drawRect(Color(0xFF2F6B3E), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.12f, y + h * 0.42f), size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.22f))
            drawRect(itemAccent.copy(alpha = 0.42f), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.56f, y + h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.46f))
        }
        "lamp" -> {
            drawRect(Color(0xFF2A2118), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.45f, y + h * 0.28f), size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.62f))
            drawRect(itemAccent.copy(alpha = 0.75f), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.20f, y), size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.22f))
            drawRect(itemAccent.copy(alpha = 0.08f), topLeft = androidx.compose.ui.geometry.Offset(x - w * 0.65f, y + h * 0.05f), size = androidx.compose.ui.geometry.Size(w * 2.2f, h * 1.4f))
        }
        "crate" -> {
            shadow(0.20f)
            repeat((item.width / 2).coerceAtLeast(1)) { col ->
                repeat((item.height / 2).coerceAtLeast(1)) { row ->
                    val cx = x + col * tile * 1.35f
                    val cy = y + row * tile * 1.08f
                    drawRect(wood, topLeft = androidx.compose.ui.geometry.Offset(cx, cy), size = androidx.compose.ui.geometry.Size(tile * 1.18f, tile * 0.92f))
                    drawRect(Color.Black.copy(alpha = 0.20f), topLeft = androidx.compose.ui.geometry.Offset(cx, cy + tile * 0.42f), size = androidx.compose.ui.geometry.Size(tile * 1.18f, tile * 0.12f))
                }
            }
        }
        "bench", "desk" -> {
            shadow()
            drawRect(wood, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h * 0.42f))
            drawRect(Color(0xFF6A4A2F), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, tile * 0.32f))
            drawRect(darkWood, topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.4f, y + h * 0.38f), size = androidx.compose.ui.geometry.Size(tile * 0.36f, h * 0.9f))
            drawRect(darkWood, topLeft = androidx.compose.ui.geometry.Offset(x + w - tile * 0.78f, y + h * 0.38f), size = androidx.compose.ui.geometry.Size(tile * 0.36f, h * 0.9f))
        }
        "art", "sign", "display" -> {
            drawRect(Color.Black.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
            drawRect(paper.copy(alpha = if (item.type == "display") 0.16f else 0.90f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.18f, y + tile * 0.16f), size = androidx.compose.ui.geometry.Size(w - tile * 0.36f, h - tile * 0.32f))
            drawRect(itemAccent.copy(alpha = 0.70f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.42f, y + h * 0.36f), size = androidx.compose.ui.geometry.Size((w - tile * 0.84f).coerceAtLeast(tile * 0.4f), tile * 0.16f))
            if (h > tile * 1.2f) {
                drawRect(Color.Black.copy(alpha = 0.20f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.42f, y + h * 0.62f), size = androidx.compose.ui.geometry.Size((w - tile * 1.3f).coerceAtLeast(tile * 0.4f), tile * 0.12f))
            }
        }
        "cable" -> {
            drawRect(itemAccent.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(x, y + h * 0.45f), size = androidx.compose.ui.geometry.Size(w, tile * 0.12f))
            drawRect(itemAccent.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(x + w * 0.52f, y - tile * 1.1f), size = androidx.compose.ui.geometry.Size(tile * 0.12f, tile * 1.24f))
        }
        else -> {
            shadow(0.18f)
            drawRect(Color.Black.copy(alpha = 0.24f), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h))
            drawRect(itemAccent.copy(alpha = 0.36f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.2f, y + tile * 0.2f), size = androidx.compose.ui.geometry.Size(w - tile * 0.4f, h - tile * 0.4f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPersonalizedBackdrop(
    tile: Float,
    wallH: Float,
    height: Float,
    accent: Color,
    variant: String,
    personality: RoleHomePersonality,
    room: AgentRoom?,
) {
    val shift = personality.layoutShift * tile * 0.28f
    val bannerX = tile * (2.1f + seededFloat(personality.seed, 3) * 1.8f)
    val bannerY = tile * (1.55f + seededFloat(personality.seed, 4) * 1.3f)
    drawRect(Color.Black.copy(alpha = 0.22f), topLeft = androidx.compose.ui.geometry.Offset(bannerX, bannerY), size = androidx.compose.ui.geometry.Size(tile * 2.8f, tile * 1.35f))
    drawRect(accent.copy(alpha = 0.30f), topLeft = androidx.compose.ui.geometry.Offset(bannerX + tile * 0.22f, bannerY + tile * 0.2f), size = androidx.compose.ui.geometry.Size(tile * 2.35f, tile * 0.18f))
    drawRect(Color.White.copy(alpha = 0.10f), topLeft = androidx.compose.ui.geometry.Offset(bannerX + tile * 0.22f, bannerY + tile * 0.62f), size = androidx.compose.ui.geometry.Size(tile * (1.0f + roomSeededCount(room, personality, 3) * 0.35f), tile * 0.14f))
    if (personality.hasLamp) {
        val lx = tile * (15.9f - personality.layoutShift * 0.42f)
        val ly = wallH + tile * 0.8f
        drawRect(Color(0xFF2A2118), topLeft = androidx.compose.ui.geometry.Offset(lx, ly), size = androidx.compose.ui.geometry.Size(tile * 0.36f, tile * 2.7f))
        drawRect(accent.copy(alpha = 0.75f), topLeft = androidx.compose.ui.geometry.Offset(lx - tile * 0.45f, ly - tile * 0.42f), size = androidx.compose.ui.geometry.Size(tile * 1.25f, tile * 0.5f))
        drawRect(accent.copy(alpha = 0.08f), topLeft = androidx.compose.ui.geometry.Offset(lx - tile * 1.6f, ly + tile * 0.1f), size = androidx.compose.ui.geometry.Size(tile * 3.6f, tile * 3.8f))
    }
    if (personality.hasCable || variant == "terminal" || variant == "tower") {
        val cableY = height - tile * (5.6f + seededFloat(personality.seed, 9))
        drawRect(accent.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(tile * 3.0f, cableY), size = androidx.compose.ui.geometry.Size(tile * (7.2f + personality.clutter), tile * 0.12f))
        drawRect(accent.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(tile * (8.0f + personality.layoutShift), cableY - tile * 1.4f), size = androidx.compose.ui.geometry.Size(tile * 0.12f, tile * 1.5f))
    }
    if (personality.warm) {
        drawRect(Color(0xFFFFD166).copy(alpha = 0.05f), topLeft = androidx.compose.ui.geometry.Offset(tile, tile), size = androidx.compose.ui.geometry.Size(tile * 18f, height - tile * 2f))
    }
    repeat(personality.clutter) { i ->
        val px = tile * (2.0f + seededFloat(personality.seed, 20 + i) * 15.5f)
        val py = wallH + tile * (2.2f + seededFloat(personality.seed, 40 + i) * 8.4f)
        drawRect(Color.Black.copy(alpha = 0.16f), topLeft = androidx.compose.ui.geometry.Offset(px + tile * 0.06f, py + tile * 0.06f), size = androidx.compose.ui.geometry.Size(tile * 0.42f, tile * 0.42f))
        drawRect(accent.copy(alpha = 0.16f + (i % 3) * 0.08f), topLeft = androidx.compose.ui.geometry.Offset(px, py), size = androidx.compose.ui.geometry.Size(tile * 0.36f, tile * 0.36f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPersonalizedForeground(
    tile: Float,
    wallH: Float,
    height: Float,
    accent: Color,
    variant: String,
    personality: RoleHomePersonality,
    room: AgentRoom?,
) {
    if (personality.hasPlant) {
        val x = tile * (2.5f + personality.layoutShift * 0.55f)
        val y = height - tile * 4.4f
        drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(x, y + tile * 1.2f), size = androidx.compose.ui.geometry.Size(tile * 1.0f, tile * 0.85f))
        drawRect(Color(0xFF23452F), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.1f, y + tile * 0.55f), size = androidx.compose.ui.geometry.Size(tile * 0.8f, tile * 0.6f))
        drawRect(accent.copy(alpha = 0.42f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.38f, y), size = androidx.compose.ui.geometry.Size(tile * 0.28f, tile * 1.0f))
    }
    if (personality.hasTrophy || (room?.showcase?.isNotEmpty() == true)) {
        val x = tile * (11.7f + seededFloat(personality.seed, 71) * 1.8f)
        val y = wallH + tile * 5.9f
        drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(x, y + tile * 0.9f), size = androidx.compose.ui.geometry.Size(tile * 1.4f, tile * 0.35f))
        drawRect(Color(0xFFFFD166), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.45f, y + tile * 0.2f), size = androidx.compose.ui.geometry.Size(tile * 0.5f, tile * 0.72f))
        drawRect(accent.copy(alpha = 0.64f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.28f, y), size = androidx.compose.ui.geometry.Size(tile * 0.84f, tile * 0.24f))
    }
    val plaqueX = tile * (7.8f + personality.layoutShift * 0.24f)
    val plaqueY = height - tile * 3.1f
    drawRect(Color.Black.copy(alpha = 0.32f), topLeft = androidx.compose.ui.geometry.Offset(plaqueX, plaqueY), size = androidx.compose.ui.geometry.Size(tile * 4.3f, tile * 0.42f))
    drawRect(accent.copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(plaqueX, plaqueY), size = androidx.compose.ui.geometry.Size(tile * (1.2f + roomSeededCount(room, personality, 4) * 0.45f), tile * 0.12f))
    if (variant == "terminal") {
        repeat(3) { i ->
            val x = tile * (3.5f + i * 0.72f)
            drawRect(Color(0xFF0A0A0A), topLeft = androidx.compose.ui.geometry.Offset(x, height - tile * (2.5f + i * 0.18f)), size = androidx.compose.ui.geometry.Size(tile * 0.52f, tile * 0.34f))
            drawRect(accent.copy(alpha = 0.72f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.1f, height - tile * (2.4f + i * 0.18f)), size = androidx.compose.ui.geometry.Size(tile * 0.24f, tile * 0.08f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelDesk(
    tile: Float,
    wallH: Float,
    accent: Color,
    variant: String,
    personality: RoleHomePersonality,
) {
    val x = tile * (2.4f + personality.layoutShift * 0.22f)
    val y = wallH - tile * 2.3f
    drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 6.2f, tile * 2.1f))
    drawRect(Color(0xFF6A4A2F), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 6.2f, tile * 0.45f))
    drawRect(Color(0xFF2B1B12), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.45f, y + tile * 1.5f), size = androidx.compose.ui.geometry.Size(tile * 0.5f, tile * 2.6f))
    drawRect(Color(0xFF2B1B12), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 5.3f, y + tile * 1.5f), size = androidx.compose.ui.geometry.Size(tile * 0.5f, tile * 2.6f))
    val screenColor = if (variant == "terminal") Color(0xFF041B14) else Color(0xFF19212D)
    drawRect(screenColor, topLeft = androidx.compose.ui.geometry.Offset(x + tile * 2.1f, y - tile * 1.8f), size = androidx.compose.ui.geometry.Size(tile * 2.3f, tile * 1.55f))
    drawRect(accent.copy(alpha = 0.85f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 2.35f, y - tile * 1.35f), size = androidx.compose.ui.geometry.Size(tile * 1.75f, tile * 0.18f))
    drawRect(accent.copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 2.35f, y - tile * 0.95f), size = androidx.compose.ui.geometry.Size(tile * 1.2f, tile * 0.18f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelShelf(tile: Float, accent: Color, count: Int) {
    val x = tile * 11.2f
    val y = tile * 2.2f
    drawRect(Color(0xFF3C2A1E), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 6.2f, tile * 0.35f))
    drawRect(Color(0xFF3C2A1E), topLeft = androidx.compose.ui.geometry.Offset(x, y + tile * 1.8f), size = androidx.compose.ui.geometry.Size(tile * 6.2f, tile * 0.35f))
    repeat(count.coerceIn(1, 6)) { i ->
        val px = x + tile * (0.35f + i * 0.82f)
        drawRect(accent.copy(alpha = 0.45f + (i % 2) * 0.25f), topLeft = androidx.compose.ui.geometry.Offset(px, y + tile * 0.55f), size = androidx.compose.ui.geometry.Size(tile * 0.45f, tile * 1.1f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelToolbox(tile: Float, height: Float, accent: Color, count: Int) {
    val x = tile * 14.2f
    val y = height - tile * 4.3f
    drawRect(Color(0xFF2B2B2B), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 3.2f, tile * 1.9f))
    drawRect(accent.copy(alpha = 0.75f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.35f, y + tile * 0.35f), size = androidx.compose.ui.geometry.Size(tile * (0.55f + count.coerceIn(0, 6) * 0.18f), tile * 0.35f))
    drawRect(Color.Black.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(x, y + tile * 0.9f), size = androidx.compose.ui.geometry.Size(tile * 3.2f, 2.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPixelShowcase(tile: Float, wallH: Float, accent: Color, count: Int) {
    val x = tile * 10.7f
    val y = wallH + tile * 2.1f
    repeat(count.coerceIn(1, 4)) { i ->
        val px = x + i * tile * 1.4f
        drawRect(Color.White.copy(alpha = 0.10f), topLeft = androidx.compose.ui.geometry.Offset(px, y), size = androidx.compose.ui.geometry.Size(tile, tile))
        drawRect(accent.copy(alpha = 0.45f), topLeft = androidx.compose.ui.geometry.Offset(px + tile * 0.18f, y + tile * 0.18f), size = androidx.compose.ui.geometry.Size(tile * 0.64f, tile * 0.64f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeHotspots(
    tile: Float,
    accent: Color,
    selectedSection: RoleHomeSection,
) {
    fun marker(x: Float, y: Float, section: RoleHomeSection, color: Color) {
        val selected = selectedSection == section
        val main = if (selected) color else Color.White.copy(alpha = 0.46f)
        val glow = if (selected) color.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.18f)
        drawRect(glow, topLeft = androidx.compose.ui.geometry.Offset(x - tile * 0.18f, y - tile * 0.18f), size = androidx.compose.ui.geometry.Size(tile * 0.92f, tile * 0.92f))
        drawRect(main, topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.24f, y), size = androidx.compose.ui.geometry.Size(tile * 0.16f, tile * 0.16f))
        drawRect(main.copy(alpha = main.alpha * 0.88f), topLeft = androidx.compose.ui.geometry.Offset(x, y + tile * 0.24f), size = androidx.compose.ui.geometry.Size(tile * 0.16f, tile * 0.16f))
        drawRect(main.copy(alpha = main.alpha * 0.72f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.48f, y + tile * 0.28f), size = androidx.compose.ui.geometry.Size(tile * 0.12f, tile * 0.12f))
        if (selected) {
            drawRect(Color.White.copy(alpha = 0.78f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.23f, y + tile * 0.23f), size = androidx.compose.ui.geometry.Size(tile * 0.18f, tile * 0.18f))
        }
    }
    marker(tile * 3.2f, tile * 6.15f, RoleHomeSection.IDENTITY, accent)
    marker(tile * 16.9f, tile * 2.7f, RoleHomeSection.MEMORY, Color(0xFF56D6BA))
    marker(tile * 4.2f, tile * 10.0f, RoleHomeSection.WORKS, Color(0xFFF472B6))
    marker(tile * 16.3f, tile * 13.0f, RoleHomeSection.TOOLS, Color(0xFFFBBF24))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoleHomeSpecials(
    tile: Float,
    wallH: Float,
    height: Float,
    accent: Color,
    variant: String,
    room: AgentRoom?,
) {
    when (variant) {
        "workshop" -> drawWorkshopHome(tile, wallH, accent, room)
        "tower" -> drawPhoneTowerHome(tile, wallH, height, accent)
        "terminal" -> drawTerminalHome(tile, wallH, height, accent, room)
        "library" -> drawLibraryHome(tile, wallH, accent, room)
        "warehouse" -> drawWarehouseHome(tile, wallH, height, accent, room)
        "bunker" -> drawBunkerHome(tile, wallH, height, accent)
        "cabin" -> drawCabinHome(tile, wallH, height, accent)
        "shop" -> drawShopHome(tile, wallH, height, accent, room)
        else -> drawStudioHome(tile, wallH, height, accent, room)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorkshopHome(tile: Float, wallH: Float, accent: Color, room: AgentRoom?) {
    val x = tile * 13.2f
    val y = wallH - tile * 3.8f
    drawRect(Color(0xFF3C2A1E), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 3.9f, tile * 3.1f))
    drawRect(Color(0xFFF5E7C8), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.35f, y + tile * 0.35f), size = androidx.compose.ui.geometry.Size(tile * 3.2f, tile * 2.2f))
    repeat((room?.showcase?.size ?: 2).coerceIn(2, 5)) { i ->
        drawRect(
            listOf(accent, Color(0xFFFFD166), Color(0xFF56D6BA), Color(0xFFF472B6), Color.White)[i],
            topLeft = androidx.compose.ui.geometry.Offset(x + tile * (0.7f + i * 0.48f), y + tile * 1.0f),
            size = androidx.compose.ui.geometry.Size(tile * 0.28f, tile * 0.28f),
        )
    }
    drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(tile * 10.6f, wallH + tile * 5.0f), size = androidx.compose.ui.geometry.Size(tile * 3.8f, tile * 1.1f))
    drawRect(accent.copy(alpha = 0.8f), topLeft = androidx.compose.ui.geometry.Offset(tile * 11.0f, wallH + tile * 5.35f), size = androidx.compose.ui.geometry.Size(tile * 2.8f, tile * 0.24f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPhoneTowerHome(tile: Float, wallH: Float, height: Float, accent: Color) {
    val x = tile * 12.2f
    val y = wallH - tile * 4.4f
    drawRect(Color(0xFF0B2530), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 4.2f, tile * 3.4f))
    repeat(3) { i ->
        drawRect(accent.copy(alpha = 0.36f + i * 0.16f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * 0.6f, y + tile * (0.7f + i * 0.72f)), size = androidx.compose.ui.geometry.Size(tile * (2.8f - i * 0.36f), tile * 0.22f))
    }
    drawRect(Color(0xFF102E39), topLeft = androidx.compose.ui.geometry.Offset(tile * 3.2f, height - tile * 5.5f), size = androidx.compose.ui.geometry.Size(tile * 3.3f, tile * 3.2f))
    drawRect(accent.copy(alpha = 0.9f), topLeft = androidx.compose.ui.geometry.Offset(tile * 4.45f, height - tile * 4.65f), size = androidx.compose.ui.geometry.Size(tile * 0.8f, tile * 1.5f))
    drawRect(Color.White.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(tile * 2.2f, wallH + tile * 2.2f), size = androidx.compose.ui.geometry.Size(tile * 3.8f, tile * 0.18f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTerminalHome(tile: Float, wallH: Float, height: Float, accent: Color, room: AgentRoom?) {
    val x = tile * 10.5f
    val y = wallH - tile * 4.8f
    repeat(3) { i ->
        drawRect(Color(0xFF050C12), topLeft = androidx.compose.ui.geometry.Offset(x + i * tile * 2.25f, y + (i % 2) * tile * 0.35f), size = androidx.compose.ui.geometry.Size(tile * 1.85f, tile * 1.35f))
        drawRect(accent.copy(alpha = 0.8f), topLeft = androidx.compose.ui.geometry.Offset(x + i * tile * 2.25f + tile * 0.22f, y + (i % 2) * tile * 0.35f + tile * 0.38f), size = androidx.compose.ui.geometry.Size(tile * 1.16f, tile * 0.16f))
    }
    val rackX = tile * 15.0f
    val rackY = height - tile * 8.0f
    drawRect(Color(0xFF101010), topLeft = androidx.compose.ui.geometry.Offset(rackX, rackY), size = androidx.compose.ui.geometry.Size(tile * 2.0f, tile * 4.5f))
    repeat((room?.toolbox?.size ?: 4).coerceIn(3, 7)) { i ->
        drawRect(accent.copy(alpha = if (i % 2 == 0) 0.8f else 0.35f), topLeft = androidx.compose.ui.geometry.Offset(rackX + tile * 0.3f, rackY + tile * (0.45f + i * 0.52f)), size = androidx.compose.ui.geometry.Size(tile * 1.3f, tile * 0.16f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLibraryHome(tile: Float, wallH: Float, accent: Color, room: AgentRoom?) {
    val x = tile * 11.0f
    val y = tile * 1.3f
    drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(tile * 6.7f, tile * 4.5f))
    repeat(3) { row ->
        drawRect(Color(0xFF2B1B12), topLeft = androidx.compose.ui.geometry.Offset(x, y + tile * (1.25f + row * 1.1f)), size = androidx.compose.ui.geometry.Size(tile * 6.7f, tile * 0.22f))
        repeat(7) { col ->
            val colors = listOf(accent, Color(0xFFFBBF24), Color(0xFF60A5FA), Color(0xFFF472B6))
            drawRect(colors[(row + col) % colors.size].copy(alpha = 0.74f), topLeft = androidx.compose.ui.geometry.Offset(x + tile * (0.42f + col * 0.82f), y + tile * (0.35f + row * 1.1f)), size = androidx.compose.ui.geometry.Size(tile * 0.38f, tile * 0.82f))
        }
    }
    drawRect(Color(0xFF25344A), topLeft = androidx.compose.ui.geometry.Offset(tile * 3.5f, wallH + tile * 4.7f), size = androidx.compose.ui.geometry.Size(tile * 2.8f, tile * 2.2f))
    drawRect(accent.copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(tile * 4.25f, wallH + tile * 5.2f), size = androidx.compose.ui.geometry.Size(tile * 1.2f, tile * 1.2f))
    if ((room?.wallPins?.size ?: 0) > 0) drawRect(Color.White.copy(alpha = 0.20f), topLeft = androidx.compose.ui.geometry.Offset(tile * 2.0f, tile * 2.2f), size = androidx.compose.ui.geometry.Size(tile * 2.6f, tile * 1.5f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWarehouseHome(tile: Float, wallH: Float, height: Float, accent: Color, room: AgentRoom?) {
    val x = tile * 11.6f
    val y = wallH + tile * 4.8f
    repeat(3) { i ->
        val px = x + i * tile * 1.55f
        drawRect(Color(0xFF6A4A2F), topLeft = androidx.compose.ui.geometry.Offset(px, y + (i % 2) * tile * 0.62f), size = androidx.compose.ui.geometry.Size(tile * 1.3f, tile * 1.1f))
        drawRect(Color.Black.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(px, y + (i % 2) * tile * 0.62f + tile * 0.48f), size = androidx.compose.ui.geometry.Size(tile * 1.3f, tile * 0.12f))
    }
    val boardX = tile * 12.2f
    val boardY = tile * 1.5f
    drawRect(Color(0xFF2B261B), topLeft = androidx.compose.ui.geometry.Offset(boardX, boardY), size = androidx.compose.ui.geometry.Size(tile * 5.0f, tile * 3.3f))
    repeat((room?.toolbox?.size ?: 5).coerceIn(4, 8)) { i ->
        drawRect(accent.copy(alpha = 0.28f + (i % 3) * 0.18f), topLeft = androidx.compose.ui.geometry.Offset(boardX + tile * (0.55f + (i % 4) * 1.0f), boardY + tile * (0.55f + (i / 4) * 1.25f)), size = androidx.compose.ui.geometry.Size(tile * 0.54f, tile * 0.54f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBunkerHome(tile: Float, wallH: Float, height: Float, accent: Color) {
    val rackX = tile * 12.2f
    val rackY = wallH + tile * 1.4f
    drawRect(Color(0xFF0E1218), topLeft = androidx.compose.ui.geometry.Offset(rackX, rackY), size = androidx.compose.ui.geometry.Size(tile * 4.6f, tile * 6.4f))
    repeat(6) { i ->
        drawRect(Color(0xFF1E2630), topLeft = androidx.compose.ui.geometry.Offset(rackX + tile * 0.35f, rackY + tile * (0.45f + i * 0.92f)), size = androidx.compose.ui.geometry.Size(tile * 3.9f, tile * 0.5f))
        drawRect(accent.copy(alpha = 0.86f), topLeft = androidx.compose.ui.geometry.Offset(rackX + tile * (0.75f + i % 3 * 0.7f), rackY + tile * (0.62f + i * 0.92f)), size = androidx.compose.ui.geometry.Size(tile * 0.22f, tile * 0.16f))
    }
    drawRect(accent.copy(alpha = 0.30f), topLeft = androidx.compose.ui.geometry.Offset(tile * 4.0f, height - tile * 5.1f), size = androidx.compose.ui.geometry.Size(tile * 8.0f, tile * 0.18f))
    drawRect(accent.copy(alpha = 0.18f), topLeft = androidx.compose.ui.geometry.Offset(tile * 6.0f, height - tile * 7.0f), size = androidx.compose.ui.geometry.Size(tile * 0.18f, tile * 3.0f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCabinHome(tile: Float, wallH: Float, height: Float, accent: Color) {
    drawRect(Color(0xFF5A3A24), topLeft = androidx.compose.ui.geometry.Offset(tile * 11.5f, tile * 1.8f), size = androidx.compose.ui.geometry.Size(tile * 4.7f, tile * 2.8f))
    drawRect(Color(0xFFF6D7A7), topLeft = androidx.compose.ui.geometry.Offset(tile * 11.95f, tile * 2.25f), size = androidx.compose.ui.geometry.Size(tile * 3.8f, tile * 1.9f))
    drawRect(accent.copy(alpha = 0.34f), topLeft = androidx.compose.ui.geometry.Offset(tile * 3.2f, height - tile * 5.8f), size = androidx.compose.ui.geometry.Size(tile * 3.2f, tile * 2.2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShopHome(tile: Float, wallH: Float, height: Float, accent: Color, room: AgentRoom?) {
    drawRect(Color(0xFF4A3322), topLeft = androidx.compose.ui.geometry.Offset(tile * 10.4f, wallH + tile * 4.5f), size = androidx.compose.ui.geometry.Size(tile * 6.3f, tile * 1.6f))
    repeat((room?.showcase?.size ?: 4).coerceIn(3, 6)) { i ->
        drawRect(accent.copy(alpha = 0.36f + (i % 2) * 0.28f), topLeft = androidx.compose.ui.geometry.Offset(tile * (10.9f + i * 0.86f), wallH + tile * 4.85f), size = androidx.compose.ui.geometry.Size(tile * 0.54f, tile * 0.54f))
    }
    drawRect(Color(0xFF1A1A18), topLeft = androidx.compose.ui.geometry.Offset(tile * 13.0f, tile * 2.0f), size = androidx.compose.ui.geometry.Size(tile * 3.5f, tile * 1.2f))
    drawRect(accent.copy(alpha = 0.82f), topLeft = androidx.compose.ui.geometry.Offset(tile * 13.4f, tile * 2.5f), size = androidx.compose.ui.geometry.Size(tile * 2.6f, tile * 0.18f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStudioHome(tile: Float, wallH: Float, height: Float, accent: Color, room: AgentRoom?) {
    drawRect(Color(0xFF23302A), topLeft = androidx.compose.ui.geometry.Offset(tile * 11.6f, tile * 1.5f), size = androidx.compose.ui.geometry.Size(tile * 4.8f, tile * 2.8f))
    repeat((room?.notes?.size ?: 3).coerceIn(3, 6)) { i ->
        drawRect(Color.White.copy(alpha = 0.18f + i * 0.04f), topLeft = androidx.compose.ui.geometry.Offset(tile * (12.1f + (i % 3) * 1.25f), tile * (1.95f + (i / 3) * 1.0f)), size = androidx.compose.ui.geometry.Size(tile * 0.8f, tile * 0.58f))
    }
    drawRect(accent.copy(alpha = 0.28f), topLeft = androidx.compose.ui.geometry.Offset(tile * 3.0f, height - tile * 6.2f), size = androidx.compose.ui.geometry.Size(tile * 3.5f, tile * 2.6f))
}

private fun roleHomePersonality(role: Role, room: AgentRoom?): RoleHomePersonality {
    val seed = stablePositiveHash("${role.id}|${role.name}|${role.description}|${room?.style}|${room?.motto}")
    return RoleHomePersonality(
        seed = seed,
        layoutShift = (seed % 5) - 2,
        clutter = 2 + (seed / 7 % 5),
        warm = seed % 3 == 0 || room?.houseSprite == "library" || room?.houseSprite == "workshop",
        hasPlant = seed % 4 == 0 || room?.houseSprite in setOf("library", "studio", "cabin"),
        hasLamp = seed % 5 != 1,
        hasCable = seed % 2 == 0 || room?.houseSprite in setOf("terminal", "tower", "bunker"),
        hasTrophy = seed % 6 == 0 || role.id == "creator",
    )
}

private fun stablePositiveHash(value: String): Int {
    var hash = 1125899907
    value.forEach { ch -> hash = 31 * hash + ch.code }
    return hash and 0x7fffffff
}

private fun seededFloat(seed: Int, salt: Int): Float {
    val mixed = stablePositiveHash("$seed:$salt")
    return (mixed % 1000) / 1000f
}

private fun roomSeededCount(room: AgentRoom?, personality: RoleHomePersonality, max: Int): Int {
    val content = (room?.wallPins?.size ?: 0) + (room?.deskItems?.size ?: 0) + (room?.showcase?.size ?: 0) + (room?.toolbox?.size ?: 0)
    return (content + personality.clutter).coerceIn(1, max)
}

@Composable
private fun RoleHomeDialogue(room: AgentRoom?, isWorking: Boolean) {
    val line = if (isWorking) room?.workingLine else room?.idleLine
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11110F))
            .border(2.dp, Color.White.copy(alpha = 0.82f))
            .padding(14.dp),
    ) {
        Text(room?.motto?.ifBlank { line.orEmpty() }.orEmpty(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, lineHeight = 20.sp)
        if (!line.isNullOrBlank()) {
            Text(line, color = Color.White.copy(alpha = 0.60f), fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RoleHomeMeaningPanel(
    room: AgentRoom?,
    role: Role,
    selectedSection: RoleHomeSection,
    onSelectSection: (RoleHomeSection) -> Unit,
) {
    val c = LocalClawColors.current
    val memories = room?.wallPins?.size ?: 0
    val works = ((room?.deskItems?.size ?: 0) + (room?.showcase?.size ?: 0)).coerceAtLeast(0)
    val tools = listOf(
        room?.toolbox?.size ?: 0,
        role.forcedSkillIds.size,
        role.preferredTaskTypes.size,
    ).maxOrNull()?.coerceAtLeast(0) ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11110F))
            .border(2.dp, Color.White.copy(alpha = 0.82f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = str(R.string.role_home_inspect_room),
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RoleHomeCommandCell(
                label = str(R.string.role_home_identity_detail),
                meta = role.name.ifBlank { role.id },
                count = null,
                color = c.accent,
                selected = selectedSection == RoleHomeSection.IDENTITY,
                onClick = { onSelectSection(RoleHomeSection.IDENTITY) },
                modifier = Modifier.weight(1f),
            )
            RoleHomeCommandCell(
                label = str(R.string.role_home_memory_detail),
                meta = str(R.string.role_home_memory_stat),
                count = memories,
                color = Color(0xFF56D6BA),
                selected = selectedSection == RoleHomeSection.MEMORY,
                onClick = { onSelectSection(RoleHomeSection.MEMORY) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            RoleHomeCommandCell(
                label = str(R.string.role_home_work_detail),
                meta = str(R.string.role_home_work_stat),
                count = works,
                color = Color(0xFFF472B6),
                selected = selectedSection == RoleHomeSection.WORKS,
                onClick = { onSelectSection(RoleHomeSection.WORKS) },
                modifier = Modifier.weight(1f),
            )
            RoleHomeCommandCell(
                label = str(R.string.role_home_tool_detail),
                meta = str(R.string.role_home_tool_stat),
                count = tools,
                color = Color(0xFFFBBF24),
                selected = selectedSection == RoleHomeSection.TOOLS,
                onClick = { onSelectSection(RoleHomeSection.TOOLS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoleHomeCommandCell(
    label: String,
    meta: String,
    count: Int?,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(if (selected) color.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) color.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawRect(Color.Black.copy(alpha = 0.36f), size = size)
            drawRect(color.copy(alpha = 0.92f), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f), size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f))
            drawRect(Color.White.copy(alpha = 0.58f), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.30f), size = androidx.compose.ui.geometry.Size(size.width * 0.20f, size.height * 0.20f))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (count != null) {
            Text(count.toString(), color = color, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun RoleHomeInventory(room: AgentRoom?, role: Role, selectedSection: RoleHomeSection) {
    val memoryItems: List<Pair<String, String>> = buildList {
        room?.wallPins?.takeLast(4)?.forEach { add(it.title to it.body) }
    }.ifEmpty {
        listOf(str(R.string.role_detail_no_memory) to str(R.string.role_home_memory_empty_hint))
    }
    val toolItems: List<Pair<String, String>> = buildList {
        room?.toolbox?.takeLast(4)?.forEach { add(it.title to it.category) }
        role.forcedSkillIds.take(4).forEach { add(it to str(R.string.role_home_builtin_tool)) }
        role.preferredTaskTypes.take(3).forEach { add(it.name.toHomeTaskLabel() to str(R.string.role_home_preferred_task)) }
    }
    val artifactItems: List<Pair<String, String>> = buildList {
        room?.deskItems?.takeLast(4)?.forEach { add(it.title to it.subtitle) }
        room?.showcase?.takeLast(4)?.forEach { add(it.title to it.subtitle) }
        room?.notes?.takeLast(2)?.forEach { add(str(R.string.role_home_growth_note) to it) }
    }.ifEmpty {
        listOf(role.description.ifBlank { room?.motto.orEmpty() } to (room?.style ?: "pixel room"))
    }
    val identityItems: List<Pair<String, String>> = buildList {
        add(role.name.ifBlank { role.id } to role.description.ifBlank { room?.motto.orEmpty() })
        room?.style?.takeIf { it.isNotBlank() }?.let { add(str(R.string.role_detail_home_style) to it) }
        room?.motto?.takeIf { it.isNotBlank() }?.let { add(str(R.string.role_home_motto) to it) }
        room?.doorSign?.takeIf { it.isNotBlank() }?.let { add(str(R.string.role_home_door_sign) to it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (selectedSection) {
            RoleHomeSection.MEMORY -> RoleHomeShelf(
                title = str(R.string.role_home_memory_detail),
                items = memoryItems,
                slotSeed = 0,
            )
            RoleHomeSection.WORKS -> RoleHomeShelf(
                title = str(R.string.role_home_work_detail),
                items = artifactItems,
                slotSeed = 4,
            )
            RoleHomeSection.TOOLS -> RoleHomeShelf(
                title = str(R.string.role_home_tool_detail),
                items = toolItems,
                slotSeed = 8,
            )
            RoleHomeSection.IDENTITY -> RoleHomeShelf(
                title = str(R.string.role_home_identity_detail),
                items = identityItems,
                slotSeed = 12,
            )
            RoleHomeSection.OVERVIEW -> {
                RoleHomeShelf(
                    title = str(R.string.role_home_tool_detail),
                    items = toolItems,
                    slotSeed = 0,
                )
                RoleHomeShelf(
                    title = str(R.string.role_home_work_detail),
                    items = artifactItems,
                    slotSeed = 4,
                )
            }
        }
    }
}

@Composable
private fun RoleHomeShelf(title: String, items: List<Pair<String, String>>, slotSeed: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF11110F))
            .border(2.dp, Color.White.copy(alpha = 0.82f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        items.take(6).ifEmpty { listOf(str(R.string.role_detail_no_content) to "") }.forEachIndexed { index, (name, desc) ->
            RoleHomeSlot(name = name, desc = desc, index = slotSeed + index)
        }
    }
}

@Composable
private fun RoleHomeSlot(name: String, desc: String, index: Int) {
    val slotColors = listOf(Color(0xFFC7F43A), Color(0xFF56D6BA), Color(0xFFFBBF24), Color(0xFFA78BFA), Color(0xFFF472B6))
    val color = slotColors[index.mod(slotColors.size)]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(26.dp)) {
            drawRect(Color.Black.copy(alpha = 0.36f), size = size)
            drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.2f), size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.52f))
            drawRect(Color.White.copy(alpha = 0.55f), topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.32f), size = androidx.compose.ui.geometry.Size(size.width * 0.18f, size.height * 0.18f))
        }
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (desc.isNotBlank()) {
                Text(desc, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RoleDetailActions(
    isActive: Boolean,
    isBuiltin: Boolean,
    onActivate: () -> Unit,
    onOpenHome: () -> Unit,
    onGeneratePortrait: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GameCardButton(
                text = if (isActive) str(R.string.role_detail_current_role) else str(R.string.role_card_set_current),
                filled = !isActive,
                onClick = onActivate,
                modifier = Modifier.weight(1f),
            )
            GameCardButton(
                text = if (isBuiltin) str(R.string.role_detail_copy) else str(R.string.role_detail_edit),
                filled = false,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
        }
        GameCardButton(
            text = str(R.string.role_detail_home_title),
            filled = true,
            onClick = onOpenHome,
            modifier = Modifier.fillMaxWidth(),
        )
        GameCardButton(
            text = str(R.string.role_portrait_regenerate_action),
            filled = false,
            onClick = onGeneratePortrait,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RoleDetailSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    lines: List<String>,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        lines.ifEmpty { listOf(str(R.string.role_detail_no_content)) }.forEach {
            Text(it, color = c.subtext, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun GameCardButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) c.text else c.surface)
            .border(1.dp, if (filled) c.text else c.border, RoundedCornerShape(999.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) c.bg else c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun RoleStatusDot(active: Boolean, working: Boolean, color: Color? = null, modifier: Modifier = Modifier) {
    val c = LocalClawColors.current
    Box(
        modifier
            .size(9.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                color ?:
                when {
                    working -> c.accent
                    active -> c.text
                    else -> c.subtext.copy(alpha = 0.35f)
                }
            )
    )
}

@Composable
private fun RoleCardPortraitPlaceholder(
    role: Role,
    accent: Color,
    isGenerating: Boolean,
) {
    val name = role.name.ifBlank { role.id.ifBlank { "AI" } }
    val initials = roleInitials(name)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val glow = accent.copy(alpha = 0.24f)
            drawCircle(
                color = glow,
                radius = size.minDimension * 0.38f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.46f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.minDimension * 0.28f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.44f),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = role.description.ifBlank { str(R.string.role_card_label) },
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AnimatedRoleSprite(
    spritePack: AgentSpritePack?,
    stateName: String,
    sizeDp: Int,
    modifier: Modifier = Modifier.size(sizeDp.dp),
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap = remember(spritePack?.imagePath) {
        val path = spritePack?.imagePath.orEmpty()
        if (path.isBlank() || !File(path).exists()) null
        else runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
    val state = spritePack?.states?.get(stateName) ?: spritePack?.states?.get("idle")
    val frameCount = state?.frames?.coerceAtLeast(1) ?: 1
    val frameProgress by rememberInfiniteTransition(label = "role_sprite").animateFloat(
        initialValue = 0f,
        targetValue = frameCount.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((state?.durationMs ?: 1000).coerceIn(160, 6000)),
            repeatMode = RepeatMode.Restart,
        ),
        label = "role_sprite_frame",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null && spritePack != null && state != null) {
            val image = bitmap.asImageBitmap()
            val isStaticPortrait = spritePack.columns == 1 && spritePack.rows == 1
            if (isStaticPortrait) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val frame = frameProgress.toInt().coerceIn(0, frameCount - 1)
                    val srcX = (state.startColumn + frame).coerceIn(0, spritePack.columns - 1) * spritePack.frameWidth
                    val srcY = state.row.coerceIn(0, spritePack.rows - 1) * spritePack.frameHeight
                    drawImage(
                        image = image,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(spritePack.frameWidth, spritePack.frameHeight),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
        }
    }
}

private fun roleInitials(name: String): String {
    val clean = name.trim()
    if (clean.isBlank()) return "AI"
    val letters = clean.codePoints().toArray()
    val first = letters.firstOrNull()?.let { String(Character.toChars(it)) } ?: "A"
    val second = letters.drop(1).firstOrNull()?.let { String(Character.toChars(it)) }
    return (first + (second ?: "")).take(2).uppercase()
}

@Composable
private fun RolePortraitEmptyState(
    isGenerating: Boolean,
    compact: Boolean,
    onGenerate: () -> Unit,
    dark: Boolean = false,
) {
    val c = LocalClawColors.current
    val fg = if (dark) Color.White else c.text
    val sub = if (dark) Color.White.copy(alpha = 0.62f) else c.subtext
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 56.dp else 86.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.72f))
                .border(1.dp, if (dark) Color.White.copy(alpha = 0.12f) else c.border.copy(alpha = 0.78f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(if (compact) 30.dp else 46.dp)) {
                val stroke = 2.dp.toPx()
                drawCircle(color = fg.copy(alpha = 0.82f), radius = size.minDimension * 0.18f, center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.32f))
                drawRoundRect(
                    color = fg.copy(alpha = 0.82f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.55f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.18f, size.width * 0.18f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
                )
            }
        }
        Text(
            text = when {
                isGenerating && compact -> str(R.string.role_portrait_generating_title)
                isGenerating -> str(R.string.role_portrait_generating_title)
                else -> str(R.string.role_portrait_empty_title)
            },
            color = fg,
            fontSize = if (compact) 12.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (!compact) {
            Text(
                text = str(R.string.role_portrait_empty_subtitle),
                color = sub,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!isGenerating) {
            Box(
                modifier = Modifier
                    .padding(top = if (compact) 10.dp else 16.dp)
                    .height(if (compact) 32.dp else 42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (dark) Color.White else c.text)
                    .clickable { onGenerate() }
                    .padding(horizontal = if (compact) 13.dp else 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = str(R.string.role_portrait_generate_action),
                    color = if (dark) Color.Black else c.bg,
                    fontSize = if (compact) 10.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun String.toComposeColor(): Color? =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrNull()

private fun String.toHomeTaskLabel(): String = when (this) {
    "CHAT" -> "Chat"
    "GENERAL" -> "General"
    "PHONE_CONTROL" -> str(R.string.role_home_task_phone)
    "WEB_RESEARCH" -> str(R.string.role_home_task_web)
    "FILE_CREATE" -> str(R.string.role_home_task_file)
    "APP_BUILD" -> str(R.string.role_home_task_app)
    "IMAGE_GENERATION" -> str(R.string.role_home_task_image)
    "VPN_CONTROL" -> str(R.string.role_home_task_vpn)
    "SKILL_MANAGEMENT" -> str(R.string.role_home_task_skill)
    "CODE_EXECUTION" -> str(R.string.role_home_task_code)
    else -> lowercase().split('_').joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
}

private fun accentForRole(role: Role): Color = when (role.avatar) {
    RoleAvatarDefaults.CREATOR -> Color(0xFFF472B6)
    RoleAvatarDefaults.PHONE -> Color(0xFF38BDF8)
    RoleAvatarDefaults.CODER -> Color(0xFFA78BFA)
    RoleAvatarDefaults.WEB -> Color(0xFF34D399)
    RoleAvatarDefaults.SKILL -> Color(0xFFFBBF24)
    RoleAvatarDefaults.VPN -> Color(0xFF60A5FA)
    else -> Color(0xFFC7F43A)
}

private fun AgentSpritePack.isFreshRolePortrait(): Boolean =
    // 角色页只认真正的静态肖像包，避免把 character spritesheet 错当成角色头像。
    notes.contains(ROLE_PORTRAIT_STYLE_VERSION) ||
        kind == "portrait" ||
        (columns == 1 && rows == 1)

@Composable
private fun TopographicLines(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val step = 22.dp.toPx()
        for (i in -3..16) {
            val y = i * step
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y + step * 1.8f), strokeWidth = 1.dp.toPx())
            drawLine(color, androidx.compose.ui.geometry.Offset(0f, y + step * 0.55f), androidx.compose.ui.geometry.Offset(size.width, y + step * 2.35f), strokeWidth = 1.dp.toPx())
        }
    }
}
