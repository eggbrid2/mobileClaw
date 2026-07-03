package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onCheckAgain: () -> Unit,
) {
    if (!state.showDialog) return
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    val busy = state.checking || state.installing
    val hasError = state.errorMessage.isNotBlank()
    val title = when {
        state.checking -> if (isZh) "正在检测更新" else "Checking Update"
        state.installing -> if (isZh) "正在准备更新" else "Preparing Update"
        hasError -> if (isZh) "检测更新失败" else "Update Check Failed"
        state.hasNewVersion -> if (isZh) "发现新版本" else "Update Available"
        else -> if (isZh) "已经是最新版本" else "Already Up To Date"
    }
    val subtitle = when {
        state.installing -> if (isZh) "正在下载 APK，完成后会打开系统安装器" else "Downloading APK and opening the installer"
        hasError -> state.errorMessage
        state.hasNewVersion -> if (isZh) "MobileClaw 可以升级到 ${state.remoteVersion.ifBlank { "latest" }}" else "MobileClaw can update to ${state.remoteVersion.ifBlank { "latest" }}"
        else -> if (isZh) "当前安装版本已经和发布通道一致" else "Installed build matches the release channel"
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surface)
                    .border(0.8.dp, c.border.copy(alpha = 0.70f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (c.isDark) Color.White.copy(alpha = 0.10f) else Color(0xFF101010))
                            .border(0.7.dp, c.border.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                color = c.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Icon(
                                Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = if (c.isDark) Color.White else Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            title,
                            color = c.text,
                            fontSize = 20.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            color = c.subtext,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(c.cardAlt.copy(alpha = if (c.isDark) 0.54f else 0.78f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateMetaLine(
                        label = if (isZh) "当前版本" else "Current",
                        value = "${state.currentVersion.ifBlank { "-" }} (${state.currentVersionCode})",
                    )
                    UpdateMetaLine(
                        label = if (isZh) "发布版本" else "Release",
                        value = buildString {
                            append(state.remoteVersion.ifBlank { "-" })
                            state.remoteVersionCode?.let { append(" ($it)") }
                        },
                    )
                    val notes = state.releaseNotes.ifBlank {
                        if (state.hasNewVersion) {
                            if (isZh) "这个版本没有填写更新说明。" else "No release notes were provided."
                        } else {
                            ""
                        }
                    }
                    if (notes.isNotBlank()) {
                        Text(
                            notes,
                            color = c.text.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClawSecondaryButton(
                        text = if (isZh) "关闭" else "Close",
                        onClick = onDismiss,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    ClawPrimaryButton(
                        text = when {
                            state.hasNewVersion -> if (isZh) "更新" else "Update"
                            hasError -> if (isZh) "重试" else "Retry"
                            else -> if (isZh) "重新检测" else "Check Again"
                        },
                        onClick = {
                            if (state.hasNewVersion && !hasError) onInstall() else onCheckAgain()
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateMetaLine(label: String, value: String) {
    val c = LocalClawColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = c.subtext,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = c.text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
