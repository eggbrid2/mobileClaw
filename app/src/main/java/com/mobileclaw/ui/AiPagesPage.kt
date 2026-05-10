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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.ui.aipage.AiPageDef
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mobileclaw.R
import com.mobileclaw.str

/**
 * User-facing page management UI.
 * Shows all AI-created native pages with open/pin/delete actions.
 * This list is NOT editable by AI — only users can delete pages here.
 */
@Composable
fun AiPagesPage(
    pages: List<AiPageDef>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPinShortcut: (String) -> Unit,
    onBack: () -> Unit,
    showHeader: Boolean = true,
) {
    val c = LocalClawColors.current
    var deleteTarget by remember { mutableStateOf<AiPageDef?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .then(if (showHeader) Modifier.statusBarsPadding() else Modifier),
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        if (showHeader) Column(modifier = Modifier.fillMaxWidth().background(c.surface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = c.subtext,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    str(R.string.ai_pages_16bc64),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    str(R.string.count_items, pages.size),
                    fontSize = 12.sp,
                    color = c.subtext,
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        // ── Page list ─────────────────────────────────────────────────────────
        if (pages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str(R.string.ai_pages_7c9430), fontSize = 16.sp, color = c.subtext)
                    Text(str(R.string.ai_pages_232d05), fontSize = 13.sp, color = c.subtext.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                items(pages, key = { it.id }) { page ->
                    PageRow(
                        page = page,
                        onOpen = { onOpen(page.id) },
                        onPin = { onPinShortcut(page.id) },
                        onDelete = { deleteTarget = page },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(str(R.string.ai_pages_delete)) },
            text = { Text(str(R.string.delete_confirm, target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleteTarget = null
                }) { Text(str(R.string.skills_delete_confirm), color = LocalClawColors.current.red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(str(R.string.btn_cancel)) }
            },
        )
    }
}

@Composable
private fun PageRow(
    page: AiPageDef,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalClawColors.current
    val dateStr = remember(page.createdAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(page.createdAt))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(page.icon, fontSize = 22.sp)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                page.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (page.description.isNotBlank()) {
                Text(page.description, fontSize = 12.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("v${page.version} · $dateStr", fontSize = 11.sp, color = c.subtext, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpen, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Open", tint = c.accent, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onPin, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.PushPin, contentDescription = "Pin", tint = c.subtext, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = c.red.copy(alpha = 0.7f), modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}
