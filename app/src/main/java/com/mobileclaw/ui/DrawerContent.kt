package com.mobileclaw.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import com.mobileclaw.config.ConfigEntry
import com.mobileclaw.memory.db.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.mobileclaw.str

@Composable
fun DrawerContent(
    sessions: List<SessionEntity>,
    currentSessionId: String,
    currentRole: Role,
    userConfigEntries: Map<String, ConfigEntry>,
    userAvatarUri: String?,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onPickAvatar: () -> Unit,
    onOpenUserConfig: () -> Unit = {},
) {
    val c = LocalClawColors.current
    val context = LocalContext.current

    // Load avatar bitmap from URI
    var avatarBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(userAvatarUri) {
        avatarBitmap = if (userAvatarUri != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(userAvatarUri))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }.getOrNull()
            }
        } else null
    }

    val userName = userConfigEntries["user.name"]?.value?.takeIf { it.isNotBlank() } ?: "MobileClaw"
    val drawerBg = if (c.isDark) Color(0xFF050505) else Color.White
    val primaryButtonBg = if (c.isDark) Color.White else Color(0xFF101010)
    val primaryButtonText = if (c.isDark) Color(0xFF101010) else Color.White

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(290.dp)
            .background(drawerBg)
            .statusBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        // User info block
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tappable avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onPickAvatar() },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    GradientAvatar(avatar = currentRole.avatar, size = 40.dp, color = c.text)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            // 用户配置入口不再只挂在一小段副标题文字上，整块信息区都可点击，减少抽屉里点不中的问题。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpenUserConfig() }
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    text = userName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val configCount = userConfigEntries.size
                Text(
                    text = if (configCount > 0) "${configCount} 项配置 · ${str(R.string.drawer_user_config)}" else str(R.string.drawer_user_config),
                    fontSize = 11.sp,
                    color = c.subtext,
                    letterSpacing = 0.sp,
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = c.border, thickness = 0.5.dp)

        // New session button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(primaryButtonBg)
                .clickable { onNewSession() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = primaryButtonText,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = str(R.string.drawer_new_session),
                fontSize = 13.sp,
                color = primaryButtonText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = c.border, thickness = 0.5.dp)

        if (sessions.isNotEmpty()) {
            Text(
                text = str(R.string.drawer_recent),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = c.subtext.copy(alpha = 0.7f),
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sessions, key = { it.id }) { session ->
                SessionItem(
                    session = session,
                    isSelected = session.id == currentSessionId,
                    onSelect = { onSelectSession(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionItem(
    session: SessionEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalClawColors.current
    val rowBg = if (isSelected) {
        if (c.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF3F3F1)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .then(
                if (isSelected && !c.isDark) {
                    Modifier.border(0.6.dp, Color(0xFFE8E8E4), RoundedCornerShape(12.dp))
                } else {
                    Modifier
                }
            )
            .clickable { onSelect() }
            .padding(end = 2.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) c.text else Color.Transparent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = session.title,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = formatRelativeTime(session.updatedAt),
            fontSize = 10.sp,
            color = c.subtext.copy(alpha = 0.7f),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = c.subtext.copy(alpha = 0.4f),
            )
        }
    }
}

private fun formatRelativeTime(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    return when {
        diff < 60_000L -> str(R.string.drawer_just_now)
        diff < 3600_000L -> str(R.string.minutes_ago, diff / 60_000)
        timestampMs >= todayStart -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
        diff < 86400_000L * 2 -> str(R.string.drawer_yesterday)
        diff < 86400_000L * 7 -> SimpleDateFormat("EEE", Locale.CHINESE).format(Date(timestampMs))
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestampMs))
    }
}
