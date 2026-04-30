package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import java.util.UUID

@Composable
fun RolesPage(
    availableRoles: List<Role>,
    currentRole: Role,
    onActivate: (Role) -> Unit,
    onSave: (Role) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val c = LocalClawColors.current
    var showEditor by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<Role?>(null) }

    BackHandler { onBack() }

    val builtins = availableRoles.filter { it.isBuiltin }
    val custom = availableRoles.filter { !it.isBuiltin }

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClawPageHeader(
                title = stringResource(R.string.drawer_roles),
                onBack = onBack,
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    RoleSectionHeader("内置角色")
                }
                items(builtins, key = { it.id }) { role ->
                    RoleRow(
                        role = role,
                        isActive = role.id == currentRole.id,
                        onActivate = { onActivate(role) },
                        onEdit = null,
                        onDelete = null,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = c.border,
                        thickness = 0.5.dp,
                    )
                }
                if (custom.isNotEmpty()) {
                    item {
                        RoleSectionHeader("自定义角色")
                    }
                    items(custom, key = { it.id }) { role ->
                        RoleRow(
                            role = role,
                            isActive = role.id == currentRole.id,
                            onActivate = { onActivate(role) },
                            onEdit = { editingRole = role; showEditor = true },
                            onDelete = { onDelete(role.id) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 68.dp),
                            color = c.border,
                            thickness = 0.5.dp,
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { editingRole = null; showEditor = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = c.accent,
            contentColor = c.bg,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.role_create_title))
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
private fun RoleSectionHeader(title: String) {
    val c = LocalClawColors.current
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = c.subtext,
        letterSpacing = 0.3.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RoleRow(
    role: Role,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val c = LocalClawColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) c.accent.copy(alpha = 0.05f) else c.bg)
            .clickable { onActivate() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(
            emoji = role.avatar,
            size = 40.dp,
            color = if (isActive) c.accent else c.subtext,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = role.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) c.accent else c.text,
            )
            Text(
                text = role.description,
                fontSize = 12.sp,
                color = c.subtext,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (role.modelOverride != null) {
                Text(
                    text = role.modelOverride!!,
                    fontSize = 10.sp,
                    color = c.blue,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(18.dp),
            )
        } else {
            if (onEdit != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = c.subtext, modifier = Modifier.size(14.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = c.red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
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
        title = { Text(stringResource(if (initial == null) R.string.role_create_title else R.string.role_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = avatar,
                        onValueChange = { if (it.length <= 2) avatar = it },
                        label = { Text(stringResource(R.string.role_field_avatar)) },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.role_field_name)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.role_field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = addendum,
                    onValueChange = { addendum = it },
                    label = { Text(stringResource(R.string.role_field_system_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )
                OutlinedTextField(
                    value = forcedSkills,
                    onValueChange = { forcedSkills = it },
                    label = { Text(stringResource(R.string.role_field_skills)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = modelOverride,
                    onValueChange = { modelOverride = it },
                    label = { Text(stringResource(R.string.role_field_model)) },
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
            ) { Text(stringResource(R.string.role_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}
