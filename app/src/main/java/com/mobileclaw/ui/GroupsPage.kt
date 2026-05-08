package com.mobileclaw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupsPage(
    groups: List<Group>,
    availableRoles: List<Role>,
    onOpenGroup: (Group) -> Unit,
    onCreateGroup: (Group) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    var showCreate by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = str(R.string.btn_back), tint = c.text)
                }
                Text(str(R.string.groups_title), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            if (groups.isEmpty()) {
                // Empty state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👥", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(str(R.string.groups_empty), color = c.subtext, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(str(R.string.groups_empty_hint), color = c.subtext.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    items(groups, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            availableRoles = availableRoles,
                            onClick = { onOpenGroup(group) },
                            onDelete = { onDeleteGroup(group.id) },
                            c = c,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = c.accent,
            contentColor = c.bg,
        ) {
            Icon(Icons.Default.Add, contentDescription = str(R.string.group_new_title))
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            availableRoles = availableRoles,
            onCreate = { group ->
                onCreateGroup(group)
                showCreate = false
            },
            onDismiss = { showCreate = false },
            c = c,
        )
    }
}

@Composable
private fun GroupCard(
    group: Group,
    availableRoles: List<Role>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    c: ClawColors,
) {
    val members = group.memberRoleIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .background(c.card)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(emoji = group.emoji, size = 44.dp, color = c.accent, fontSize = 22.sp)

        Spacer(Modifier.width(12.dp))

        val youLabel = str(R.string.groups_you)
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(youLabel)
                    members.forEach { append(" · ${it.avatar} ${it.name}") }
                },
                color = c.subtext,
                fontSize = 11.sp,
            )
        }

        Text(
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(group.updatedAt)),
            color = c.subtext.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete group", tint = c.subtext, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateGroupDialog(
    availableRoles: List<Role>,
    onCreate: (Group) -> Unit,
    onDismiss: () -> Unit,
    c: ClawColors,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("👥") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val groupEmojis = listOf("👥", "🛠️", "🎨", "🔬", "💼", "🚀", "🎯", "⚡", "🌐", "🧠")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.group_new_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Name
                Text(str(R.string.group_field_name), fontSize = 12.sp, color = c.subtext)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text(str(R.string.group_field_name_hint), color = c.subtext, fontSize = 14.sp)
                        inner()
                    },
                )

                // Emoji
                Text(str(R.string.group_field_icon), fontSize = 12.sp, color = c.subtext)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groupEmojis.forEach { e ->
                        val isChosen = e == emoji
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) {
                            GradientAvatar(
                                emoji = e,
                                size = 36.dp,
                                color = if (isChosen) c.accent else c.subtext.copy(alpha = 0.5f),
                                fontSize = 18.sp,
                            )
                        }
                    }
                }

                // Member selection
                Text(str(R.string.group_field_members), fontSize = 12.sp, color = c.subtext)
                Column {
                    availableRoles.forEach { role ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (selectedIds.contains(role.id)) selectedIds.remove(role.id)
                                    else selectedIds.add(role.id)
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(role.id),
                                onCheckedChange = {
                                    if (it) selectedIds.add(role.id) else selectedIds.remove(role.id)
                                },
                            )
                            GradientAvatar(emoji = role.avatar, size = 32.dp, color = c.accent)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(role.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(role.description.take(40), color = c.subtext, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selectedIds.isNotEmpty()) {
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
                enabled = name.isNotBlank() && selectedIds.isNotEmpty(),
            ) { Text(str(R.string.group_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(R.string.btn_cancel)) }
        },
    )
}
