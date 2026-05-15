package com.mobileclaw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.Group
import com.mobileclaw.agent.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.mobileclaw.str

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
    val members = group.memberRoleIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }
    val timeText = remember(preview?.createdAt, group.updatedAt) {
        formatGroupListTime(preview?.createdAt ?: group.updatedAt)
    }
    val fallbackPreview = remember(members) {
        buildString {
            append(str(R.string.groups_you))
            members.take(3).forEach { append(" · ${safeAvatarGlyph(it.avatar)} ${it.name}") }
            if (members.size > 3) append(" · +${members.size - 3}")
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
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("group") }
    val selectedIds = remember { mutableStateListOf<String>() }
    val canCreate = name.isNotBlank() && selectedIds.isNotEmpty()
    val groupIcons = listOf("group", "tools", "appearance", "search", "folder", "launch", "check", "battery", "web", "profile")

    Column(Modifier.fillMaxSize().background(c.bg)) {
        ClawPageHeader(title = str(R.string.group_new_title), onBack = onBack) {
            TextButton(
                enabled = canCreate,
                onClick = {
                    if (canCreate) {
                        onCreate(
                            Group(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                emoji = emoji,
                                memberRoleIds = selectedIds.toList(),
                            ),
                        )
                    }
                },
            ) {
                Text(str(R.string.group_create), color = if (canCreate) c.text else c.subtext, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(4.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(str(R.string.group_field_name), fontSize = 12.sp, color = c.subtext, fontWeight = FontWeight.SemiBold)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.surface)
                        .border(1.dp, c.border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 15.sp),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text(str(R.string.group_field_name_hint), color = c.subtext, fontSize = 15.sp)
                        inner()
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.group_field_icon), fontSize = 12.sp, color = c.subtext, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupIcons.forEach { iconKey ->
                        val isChosen = iconKey == emoji
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isChosen) c.text else c.surface)
                                .border(1.dp, if (isChosen) c.text else c.border, CircleShape)
                                .clickable { emoji = iconKey },
                            contentAlignment = Alignment.Center,
                        ) {
                            ClawSymbolIcon(iconKey, tint = if (isChosen) c.bg else c.text, modifier = Modifier.size(21.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(str(R.string.group_field_members), fontSize = 12.sp, color = c.subtext, fontWeight = FontWeight.SemiBold)
                availableRoles.forEach { role ->
                    val selected = selectedIds.contains(role.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(1.dp, if (selected) c.text else c.border, RoundedCornerShape(16.dp))
                            .clickable {
                                if (selected) selectedIds.remove(role.id) else selectedIds.add(role.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GradientAvatar(emoji = role.avatar, size = 38.dp, color = if (selected) c.accent else c.subtext)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(role.name, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(role.description, color = c.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                if (it) selectedIds.add(role.id) else selectedIds.remove(role.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
