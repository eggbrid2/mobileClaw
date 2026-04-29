package com.mobileclaw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.permission.PermissionItem

private val ClawOrange = Color(0xFFFF6B35)
private val ClawDark = Color(0xFF080810)
private val ClawSurface = Color(0xFF0F0F1A)
private val ClawCard = Color(0xFF161624)
private val ClawCardAlt = Color(0xFF1C1C2E)
private val ClawBorder = Color(0xFF252538)
private val ClawText = Color(0xFFEEEEFF)
private val ClawSubtext = Color(0xFF7070A0)

// ── Permission Guide ──────────────────────────────────────────────────────────
@Composable
fun PermissionGuideScreen(
    pending: List<PermissionItem>,
    onOpenSettings: (PermissionItem) -> Unit,
) {
    val blocking = pending.filter { it.isBlocking }
    val optional = pending.filter { !it.isBlocking }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ClawSurface, ClawDark)))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ClawOrange.copy(alpha = 0.12f))
                .border(1.5.dp, ClawOrange.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("🦀", fontSize = 38.sp)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "MobileClaw",
            color = ClawText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Needs a few permissions to operate",
            color = ClawSubtext,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "These let the agent see and control your screen.",
            color = ClawSubtext.copy(alpha = 0.6f),
            fontSize = 12.sp,
        )

        Spacer(Modifier.height(28.dp))

        if (blocking.isNotEmpty()) {
            Text(
                "REQUIRED",
                color = ClawOrange.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            blocking.forEach { item ->
                PermissionCard(item = item, onAction = { onOpenSettings(item) })
                Spacer(Modifier.height(10.dp))
            }
        }

        if (optional.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "RECOMMENDED",
                color = ClawSubtext,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            optional.forEach { item ->
                PermissionCard(item = item, onAction = { onOpenSettings(item) })
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Your data stays on-device. MobileClaw never\nuploads your screen content without your task.",
            color = ClawSubtext.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    onAction: () -> Unit,
) {
    val borderColor = if (item.isBlocking) ClawOrange.copy(alpha = 0.3f) else ClawBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClawCard, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ClawCardAlt)
                .border(1.dp, ClawBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.icon, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = ClawText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(3.dp))
            Text(item.description, color = ClawSubtext, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        if (item.isBlocking || item.canDirectRequest) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.isBlocking) ClawOrange else ClawOrange.copy(alpha = 0.8f),
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Grant", color = Color.White, fontSize = 12.sp)
            }
        } else {
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, ClawBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Open", color = ClawSubtext, fontSize = 12.sp)
            }
        }
    }
}
