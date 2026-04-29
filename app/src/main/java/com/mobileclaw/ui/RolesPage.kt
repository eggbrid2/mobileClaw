package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.agent.Role
import java.util.UUID

@Composable
fun RolesPage(
    availableRoles: List<Role>,
    currentRole: Role,
    onActivate: (Role) -> Unit,
    onSave: (Role) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<Role?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingRole = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Create role")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(availableRoles, key = { it.id }) { role ->
                RoleCard(
                    role = role,
                    isActive = role.id == currentRole.id,
                    onActivate = { onActivate(role) },
                    onEdit = if (!role.isBuiltin) {
                        { editingRole = role; showEditor = true }
                    } else null,
                    onDelete = if (!role.isBuiltin) {
                        { onDelete(role.id) }
                    } else null,
                )
            }
        }
    }

    if (showEditor) {
        RoleEditorDialog(
            initial = editingRole,
            onDismiss = { showEditor = false },
            onSave = { role ->
                onSave(role)
                showEditor = false
            },
        )
    }
}

@Composable
private fun RoleCard(
    role: Role,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable { onActivate() }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(text = role.avatar, fontSize = 28.sp)
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = role.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = role.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (role.modelOverride != null) {
                Text(
                    text = "模型: ${role.modelOverride}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (onEdit != null || onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleEditorDialog(
    initial: Role?,
    onDismiss: () -> Unit,
    onSave: (Role) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var avatar by remember { mutableStateOf(initial?.avatar ?: "🤖") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var addendum by remember { mutableStateOf(initial?.systemPromptAddendum ?: "") }
    var modelOverride by remember { mutableStateOf(initial?.modelOverride ?: "") }
    var forcedSkills by remember { mutableStateOf(initial?.forcedSkillIds?.joinToString(", ") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "创建角色" else "编辑角色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = avatar,
                        onValueChange = { if (it.length <= 2) avatar = it },
                        label = { Text("头像") },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = addendum,
                    onValueChange = { addendum = it },
                    label = { Text("系统提示词附加（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
                OutlinedTextField(
                    value = forcedSkills,
                    onValueChange = { forcedSkills = it },
                    label = { Text("强制注入技能（逗号分隔 ID，可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = modelOverride,
                    onValueChange = { modelOverride = it },
                    label = { Text("模型覆盖（可选，如 gpt-4o）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) return@TextButton
                    val role = Role(
                        id = initial?.id ?: "custom_${UUID.randomUUID().toString().take(8)}",
                        name = name.trim(),
                        description = description.trim(),
                        avatar = avatar.trim().ifBlank { "🤖" },
                        systemPromptAddendum = addendum.trim(),
                        forcedSkillIds = forcedSkills.split(",")
                            .map { it.trim() }.filter { it.isNotBlank() },
                        modelOverride = modelOverride.trim().ifBlank { null },
                        isBuiltin = false,
                    )
                    onSave(role)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
