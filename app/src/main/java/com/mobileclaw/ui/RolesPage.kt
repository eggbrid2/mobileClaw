package com.mobileclaw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.Role

@Composable
fun RolesPage(
    availableRoles: List<Role>,
    currentRole: Role,
    onActivate: (Role) -> Unit,
    onEdit: (Role) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val c = LocalClawColors.current

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
                item { RoleSectionHeader("内置角色") }
                items(builtins, key = { it.id }) { role ->
                    RoleRow(
                        role = role,
                        isActive = role.id == currentRole.id,
                        onActivate = { onActivate(role) },
                        onEdit = { onEdit(role) },
                        onDelete = null,
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = c.border, thickness = 0.5.dp)
                }
                if (custom.isNotEmpty()) {
                    item { RoleSectionHeader("自定义角色") }
                    items(custom, key = { it.id }) { role ->
                        RoleRow(
                            role = role,
                            isActive = role.id == currentRole.id,
                            onActivate = { onActivate(role) },
                            onEdit = { onEdit(role) },
                            onDelete = { onDelete(role.id) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = c.border, thickness = 0.5.dp)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = {
                // Create a blank custom role and navigate to edit it
                onEdit(
                    Role(
                        id = "custom_${java.util.UUID.randomUUID().toString().take(8)}",
                        name = "",
                        description = "",
                        avatar = "🤖",
                        isBuiltin = false,
                    )
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = c.accent,
            contentColor = c.bg,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.role_create_title))
        }
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
        modifier = Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 16.dp, vertical = 8.dp),
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
        GradientAvatar(emoji = role.avatar, size = 40.dp, color = if (isActive) c.accent else c.subtext)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(role.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) c.accent else c.text)
            Text(role.description, fontSize = 12.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            if (role.modelOverride != null) {
                Text(role.modelOverride!!, fontSize = 10.sp, color = c.blue, modifier = Modifier.padding(top = 1.dp))
            }
        }
        if (isActive) {
            Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
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
