package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.agent.Role
import com.mobileclaw.memory.db.SessionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DrawerContent(
    sessions: List<SessionEntity>,
    currentSessionId: String,
    currentRole: Role,
    currentPage: AppPage,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onNavigate: (AppPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = currentRole.avatar, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "MobileClaw",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = currentRole.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // New session button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onNewSession() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New session",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "新建对话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Divider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(4.dp))

        // Session list
        if (sessions.isNotEmpty()) {
            Text(
                text = "最近对话",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionItem(
                    session = session,
                    isSelected = session.id == currentSessionId,
                    onSelect = { onSelectSession(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }

        Divider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(4.dp))

        // Navigation items
        DrawerNavItem(
            icon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
            label = "应用",
            selected = currentPage == AppPage.APPS,
            onClick = { onNavigate(AppPage.APPS) },
        )
        DrawerNavItem(
            icon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
            label = "控制台",
            selected = currentPage == AppPage.CONSOLE,
            onClick = { onNavigate(AppPage.CONSOLE) },
        )
        DrawerNavItem(
            icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
            label = "角色",
            selected = currentPage == AppPage.ROLES,
            onClick = { onNavigate(AppPage.ROLES) },
        )
        DrawerNavItem(
            icon = { Icon(Icons.Outlined.Build, contentDescription = null) },
            label = "技能",
            selected = currentPage == AppPage.SKILLS,
            onClick = { onNavigate(AppPage.SKILLS) },
        )
        DrawerNavItem(
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = "用户画像",
            selected = currentPage == AppPage.PROFILE,
            onClick = { onNavigate(AppPage.PROFILE) },
        )
        DrawerNavItem(
            icon = { Icon(Icons.Outlined.ManageAccounts, contentDescription = null) },
            label = "用户配置",
            selected = currentPage == AppPage.USER_CONFIG,
            onClick = { onNavigate(AppPage.USER_CONFIG) },
        )
        DrawerNavItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = "设置",
            selected = currentPage == AppPage.SETTINGS,
            onClick = { onNavigate(AppPage.SETTINGS) },
        )
    }
}

@Composable
private fun SessionItem(
    session: SessionEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable { onSelect() }
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatRelativeTime(session.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete session",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerNavItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        icon = icon,
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp),
        colors = NavigationDrawerItemDefaults.colors(),
    )
}

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    return when {
        diff < 60_000L -> "刚刚"
        diff < 3600_000L -> "${diff / 60_000}分钟前"
        timestampMs >= todayStart -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
        diff < 86400_000L * 2 -> "昨天"
        diff < 86400_000L * 7 -> SimpleDateFormat("EEE", Locale.CHINESE).format(Date(timestampMs))
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestampMs))
    }
}
