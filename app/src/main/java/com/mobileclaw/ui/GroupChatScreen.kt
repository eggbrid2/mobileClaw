package com.mobileclaw.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobileclaw.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.agent.ChatBubbleStyle
import com.mobileclaw.agent.Group
import com.mobileclaw.agent.Role
import com.mobileclaw.skill.SkillAttachment
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mobileclaw.str

// ── Color palette assigned to agents by index in group ───────────────────────

private val AGENT_COLORS = listOf(
    Color(0xFF6366F1), // indigo
    Color(0xFFEC4899), // pink
    Color(0xFFF59E0B), // amber
    Color(0xFF10B981), // emerald
    Color(0xFF8B5CF6), // violet
    Color(0xFF3B82F6), // blue
    Color(0xFFEF4444), // red
)

private fun agentColor(index: Int) = AGENT_COLORS[index % AGENT_COLORS.size]

private fun isGroupImageFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.mimeType.startsWith("image/") ||
        attachment.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

private fun isGroupStickerFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.path.contains("/stickers/", ignoreCase = true) ||
        attachment.name.contains("bqb", ignoreCase = true)

private fun isGroupVisualImageAttachment(attachment: SkillAttachment): Boolean = when (attachment) {
    is SkillAttachment.ImageData -> true
    is SkillAttachment.FileData -> isGroupImageFileAttachment(attachment)
    else -> false
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun GroupChatScreen(
    group: Group,
    messages: List<GroupMessage>,
    availableRoles: List<Role>,
    isRunning: Boolean,
    typingAgentIds: Set<String>,
    workingAgentIds: Set<String>,
    onSend: (String, List<SkillAttachment>) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
) {
    val c = LocalClawColors.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var pendingAttachments by remember { mutableStateOf<List<SkillAttachment>>(emptyList()) }
    var pendingTextAppend by remember { mutableStateOf("") }
    var showMentionPicker by remember { mutableStateOf(false) }
    var showStickerSearch by remember { mutableStateOf(false) }
    var showPlusPanel by remember { mutableStateOf(false) }
    var showMemberDrawer by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            val base64 = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bm = BitmapFactory.decodeStream(stream)
                    val out = ByteArrayOutputStream()
                    val scale = minOf(1024f / bm.width, 1024f / bm.height, 1f)
                    val scaled = if (scale < 1f) {
                        android.graphics.Bitmap.createScaledBitmap(bm, (bm.width * scale).toInt(), (bm.height * scale).toInt(), true)
                    } else bm
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    if (scaled !== bm) scaled.recycle()
                    bm.recycle()
                    "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }.getOrNull()
            if (base64 != null) pendingAttachments = pendingAttachments + SkillAttachment.ImageData(base64, prompt = str(R.string.group_attachment_image))
        }
    }
    val filePicker = rememberLauncherForActivityResult(GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = run {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) c.getString(idx) else null
                        } else null
                    } ?: uri.lastPathSegment ?: "attachment"
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                val isText = mimeType.startsWith("text/") || mimeType in setOf("application/json", "application/xml", "application/javascript")
                if (mimeType.startsWith("image/")) {
                    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val out = ByteArrayOutputStream()
                    val scale = minOf(1024f / bm.width, 1024f / bm.height, 1f)
                    val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bm, (bm.width * scale).toInt(), (bm.height * scale).toInt(), true) else bm
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    if (scaled !== bm) scaled.recycle()
                    bm.recycle()
                    val base64 = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    pendingAttachments = pendingAttachments + SkillAttachment.ImageData(base64, prompt = fileName)
                } else if (isText) {
                    pendingAttachments = pendingAttachments + SkillAttachment.FileData(uri.toString(), fileName, mimeType, bytes.size.toLong())
                    val content = bytes.toString(Charsets.UTF_8).take(10_000)
                    pendingTextAppend += "\n\n[附件: $fileName]\n```\n$content\n```"
                } else {
                    pendingAttachments = pendingAttachments + SkillAttachment.FileData(uri.toString(), fileName, mimeType, bytes.size.toLong())
                }
            }
        }
    }

    // Members: roles in this group, ordered for color assignment
    val memberRoles = group.memberRoleIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }
    val colorMap: Map<String, Color> = memberRoles.mapIndexed { i, r -> r.id to agentColor(i) }.toMap()
    val totalMembers = memberRoles.size + 1 // +1 for the user

    LaunchedEffect(messages.size, typingAgentIds.size, workingAgentIds.size) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    BackHandler(enabled = showMemberDrawer) { showMemberDrawer = false }
    BackHandler(enabled = !showMemberDrawer) { onBack() }

    // Root Box: consumes all touches so nothing passes through to ChatScreen below
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                    }
                }
            },
    ) {
        // ── Main content column ───────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {
        // ── Title bar ────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = str(R.string.btn_back), tint = c.text)
                }
                GroupMemberIconButton(
                    memberRoles = memberRoles,
                    totalMembers = totalMembers,
                    c = c,
                    onClick = { showMemberDrawer = true },
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    group.name,
                    color = c.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )

                if (isRunning) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                    }
                }
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            // Thin status strip — typing and working are intentionally separate states.
            if (typingAgentIds.isNotEmpty() || workingAgentIds.isNotEmpty()) {
                val typingNames = typingAgentIds
                    .mapNotNull { id -> memberRoles.firstOrNull { it.id == id }?.name }
                    .joinToString("、")
                val workingNames = workingAgentIds
                    .mapNotNull { id -> memberRoles.firstOrNull { it.id == id }?.name }
                    .joinToString("、")
                val statusText = listOfNotNull(
                    typingNames.takeIf { it.isNotBlank() }?.let { str(R.string.typing_indicator, it) },
                    workingNames.takeIf { it.isNotBlank() }?.let { str(R.string.working_indicator, it) },
                ).joinToString(" · ")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (workingAgentIds.isNotEmpty()) c.blue.copy(alpha = 0.08f) else c.accent.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("···", color = c.accent.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        statusText,
                        color = c.subtext.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── Message list ─────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.senderId == "user"
                val agentColor = if (isUser) c.green else colorMap[msg.senderId] ?: c.accent
                val senderRole = memberRoles.firstOrNull { it.id == msg.senderId }
                GroupMessageBubble(
                    message = msg,
                    isUser = isUser,
                    accentColor = agentColor,
                    bubbleStyle = senderRole?.chatBubbleStyle,
                    memberRoles = memberRoles,
                    colorMap = colorMap,
                    onAction = { onSend(it, emptyList()) },
                    onOpenHtmlViewer = onOpenHtmlViewer,
                    onOpenBrowser = onOpenBrowser,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    c = c,
                )
                Spacer(Modifier.height(6.dp))
            }

            item { Spacer(Modifier.height(4.dp)) }
        }

        // ── @mention picker ──────────────────────────────────────────────────
        if (showMentionPicker) {
            MentionPickerSheet(
                memberRoles = memberRoles,
                colorMap = colorMap,
                c = c,
                onPick = { role ->
                    val current = input.text
                    val newText = when {
                        current.isEmpty() -> "@${role.name} "          // @ button tapped with empty field
                        current.endsWith("@") -> "${current}${role.name} "  // user typed @
                        else -> "$current @${role.name} "              // normal insertion
                    }
                    input = TextFieldValue(newText, selection = androidx.compose.ui.text.TextRange(newText.length))
                    showMentionPicker = false
                },
                onDismiss = { showMentionPicker = false },
            )
        }

        // ── Input bar ────────────────────────────────────────────────────────
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
        if (pendingAttachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pendingAttachments.forEachIndexed { index, attachment ->
                    GroupAttachmentChip(attachment, c) {
                        pendingAttachments = pendingAttachments.filterIndexed { i, _ -> i != index }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.cardAlt)
                    .border(0.5.dp, c.border, CircleShape)
                    .clickable { showStickerSearch = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("BQB", color = c.text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.cardAlt)
                    .border(1.dp, c.border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { newVal ->
                        input = newVal
                        // Auto-show mention picker when user types @
                        if (newVal.text.endsWith("@")) showMentionPicker = true
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.accent),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (input.text.isEmpty()) {
                            Text(str(R.string.group_chat_hint), color = c.subtext, fontSize = 14.sp)
                        }
                        inner()
                    },
                )
            }

            // Send button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (input.text.isNotBlank() || pendingAttachments.isNotEmpty()) c.text else if (showPlusPanel) c.text else c.cardAlt)
                    .border(0.5.dp, if (input.text.isNotBlank() || pendingAttachments.isNotEmpty() || showPlusPanel) Color.Transparent else c.border, CircleShape)
                    .clickable {
                        if (input.text.isNotBlank() || pendingAttachments.isNotEmpty()) {
                            onSend((input.text.trim() + pendingTextAppend).trim(), pendingAttachments)
                            input = TextFieldValue("")
                            pendingAttachments = emptyList()
                            pendingTextAppend = ""
                            showPlusPanel = false
                        } else {
                            showPlusPanel = !showPlusPanel
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        input.text.isNotBlank() || pendingAttachments.isNotEmpty() -> "↑"
                        showPlusPanel -> "×"
                        else -> "+"
                    },
                    color = if (input.text.isNotBlank() || pendingAttachments.isNotEmpty() || showPlusPanel) c.bg else c.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        if (showPlusPanel) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GroupInputCapabilityTile("BQB", str(R.string.sticker_button)) { showStickerSearch = true }
                GroupInputCapabilityTile("DOC", str(R.string.chat_325369)) { filePicker.launch("*/*") }
                GroupInputCapabilityTile("@", str(R.string.group_field_members)) { showMentionPicker = true }
            }
        }
        } // end main Column

        if (showStickerSearch) {
            StickerSearchSheet(
                onDismiss = { showStickerSearch = false },
                onSelected = { file ->
                    onSend("", listOf(file))
                },
            )
        }

        // ── Member drawer overlay ─────────────────────────────────────────────
        androidx.compose.animation.AnimatedVisibility(
            visible = showMemberDrawer,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showMemberDrawer = false },
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = showMemberDrawer,
            enter = androidx.compose.animation.slideInHorizontally { it },
            exit = androidx.compose.animation.slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(220.dp)
                    .background(c.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        str(R.string.members_count, totalMembers),
                        color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showMemberDrawer = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = c.border, thickness = 0.5.dp)

                // User row
                MemberDrawerRow(avatar = "👤", name = str(R.string.group_chat_df1fd9), color = c.green, description = str(R.string.group_chat_1fd02a), c = c)
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)

                // Agent rows
                memberRoles.forEachIndexed { i, role ->
                    MemberDrawerRow(
                        avatar = role.avatar,
                        name = role.name,
                        color = agentColor(i),
                        description = role.description.take(60),
                        isTyping = role.id in typingAgentIds,
                        isWorking = role.id in workingAgentIds,
                        c = c,
                    )
                    if (i < memberRoles.lastIndex) {
                        HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                    }
                }
            }
        }
    } // end root Box
}

@Composable
private fun GroupMemberIconButton(
    memberRoles: List<Role>,
    totalMembers: Int,
    c: ClawColors,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.bg)
            .border(1.dp, c.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Group,
            contentDescription = str(R.string.group_field_members),
            tint = c.text,
            modifier = Modifier.size(18.dp),
        )
        if (totalMembers > 3) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-3).dp, y = (-3).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(c.text),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = c.bg, fontSize = 8.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MemberDrawerRow(
    avatar: String,
    name: String,
    color: Color,
    description: String,
    isTyping: Boolean = false,
    isWorking: Boolean = false,
    c: ClawColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradientAvatar(emoji = avatar, size = 36.dp, color = color, fontSize = 18.sp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (isWorking) Text(str(R.string.group_status_working), color = color.copy(alpha = 0.72f), fontSize = 10.sp)
                else if (isTyping) Text(str(R.string.group_status_typing), color = color.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            if (description.isNotBlank()) {
                Text(description, color = c.subtext, fontSize = 11.sp, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = 15.sp)
            }
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@Composable
private fun GroupMessageBubble(
    message: GroupMessage,
    isUser: Boolean,
    accentColor: Color,
    bubbleStyle: ChatBubbleStyle?,
    memberRoles: List<Role>,
    colorMap: Map<String, Color>,
    onAction: (String) -> Unit,
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    c: ClawColors,
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))
    val visual = remember(isUser, bubbleStyle, accentColor, c.isDark) {
        resolveGroupBubbleVisual(isUser, bubbleStyle, accentColor, c)
    }
    val context = LocalContext.current
    val backgroundBitmap = remember(visual.backgroundImage) {
        decodeBubbleBackgroundBitmap(context, visual.backgroundImage, maxPx = 900)
    }
    val infinite = rememberInfiniteTransition(label = "group-bubble-style")
    val pulse = if (visual.animation != "none") {
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (visual.animation == "float") 2200 else 1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bubble-pulse",
        ).value
    } else {
        0f
    }
    val animatedBorder = when (visual.animation) {
        "pulse", "sparkle", "pop" -> visual.borderColor.copy(alpha = 0.55f + pulse * 0.45f)
        "breath" -> visual.accentColor.copy(alpha = 0.30f + pulse * 0.55f)
        else -> visual.borderColor
    }
    val yOffset = if (visual.animation == "float") -0.8f + pulse * 1.6f else 0f
    val xOffset = if (visual.animation == "shake") -0.8f + pulse * 1.6f else 0f
    val bubbleScale = if (visual.animation == "pop") 0.995f + pulse * 0.01f else 1f
    val bubbleRotation = if (visual.animation == "tilt") -0.35f + pulse * 0.7f else 0f
    val textAlpha = when (visual.textAnimation) {
        "fade", "breath", "shimmer" -> 0.82f + pulse * 0.18f
        else -> 1f
    }
    val textScale = if (visual.textAnimation == "pop") 0.995f + pulse * 0.01f else 1f
    val mediaOnly = message.text.isBlank() &&
        message.attachments.isNotEmpty() &&
        message.attachments.all { isGroupVisualImageAttachment(it) }
    val useHtmlBubble = !isUser &&
        bubbleStyle?.renderer?.equals("html", ignoreCase = true) == true &&
        !bubbleStyle.htmlTemplate.isNullOrBlank() &&
        message.text.isNotBlank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            GradientAvatar(emoji = message.senderAvatar, size = 32.dp, color = accentColor)
            Spacer(Modifier.size(6.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            // Sender name + time
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    message.senderName,
                    color = visual.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(time, color = c.subtext.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))

            if (mediaOnly) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                    message.attachments.forEach { attachment ->
                        GroupAttachmentCard(
                            attachment = attachment,
                            c = c,
                            onOpenHtmlViewer = onOpenHtmlViewer,
                            onOpenBrowser = onOpenBrowser,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        )
                    }
                }
            } else if (useHtmlBubble) {
                GroupHtmlMessageBubble(
                    message = message,
                    htmlTemplate = bubbleStyle?.htmlTemplate.orEmpty(),
                    visual = visual,
                    pulse = pulse,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = xOffset
                            translationY = yOffset
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            rotationZ = bubbleRotation
                        },
                )
                if (message.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
                        message.attachments.forEach { attachment ->
                            GroupAttachmentCard(
                                attachment = attachment,
                                c = c,
                                onOpenHtmlViewer = onOpenHtmlViewer,
                                onOpenBrowser = onOpenBrowser,
                                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = xOffset
                            translationY = yOffset
                            scaleX = bubbleScale
                            scaleY = bubbleScale
                            rotationZ = bubbleRotation
                        }
                ) {
                    if (visual.glowAlpha > 0f) {
                        GroupBubbleGlow(
                            visual = visual,
                            pulse = pulse,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(visual.glowPaddingDp.dp)
                            .offset(visual.shadowOffsetXDp.dp, visual.shadowOffsetYDp.dp)
                            .then(
                                when (visual.shadow) {
                                    "soft" -> Modifier.shadow(
                                        visual.shadowElevationDp.dp,
                                        visual.shape,
                                        clip = false,
                                        ambientColor = visual.shadowColor.copy(alpha = visual.shadowAlpha),
                                        spotColor = visual.shadowColor.copy(alpha = visual.shadowAlpha),
                                    )
                                    "glow" -> Modifier.shadow(
                                        visual.shadowElevationDp.dp,
                                        visual.shape,
                                        clip = false,
                                        ambientColor = visual.shadowColor.copy(alpha = visual.shadowAlpha),
                                        spotColor = visual.shadowColor.copy(alpha = visual.shadowAlpha),
                                    )
                                    else -> Modifier
                                }
                            )
                            .offset((-visual.shadowOffsetXDp).dp, (-visual.shadowOffsetYDp).dp)
                            .clip(visual.shape)
                            .then(
                                if (visual.gradientColors.size >= 2) {
                                    Modifier.background(Brush.linearGradient(visual.gradientColors))
                                } else {
                                    Modifier.background(visual.backgroundColor)
                                }
                            )
                            .border(
                                visual.borderWidthDp.dp,
                                animatedBorder,
                                visual.shape,
                            ),
                    ) {
                        if (visual.glowAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                visual.accentColor.copy(alpha = visual.glowAlpha * (0.85f + pulse * 0.55f)),
                                                visual.borderColor.copy(alpha = visual.glowAlpha * 0.62f),
                                                Color.Transparent,
                                            )
                                        ),
                                        visual.shape,
                                    )
                            )
                        }
                        if (backgroundBitmap != null) {
                            Image(
                                bitmap = backgroundBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.matchParentSize().graphicsLayer { alpha = 0.20f },
                                contentScale = when (visual.imageMode) {
                                    "stretch" -> ContentScale.FillBounds
                                    "tile" -> ContentScale.Inside
                                    else -> ContentScale.Crop
                                },
                            )
                        }
                        BubblePatternOverlay(visual, pulse)
                        Column(
                            modifier = Modifier.padding(
                                horizontal = visual.paddingHorizontalDp.dp,
                                vertical = visual.paddingVerticalDp.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (message.text.isNotBlank()) {
                                if (isUser) {
                                    Text(
                                        text = buildMentionAnnotated(animatedTextForBubble(message.text, visual.textAnimation), memberRoles, colorMap, c),
                                        fontSize = visual.fontSizeSp.sp,
                                        lineHeight = visual.lineHeightSp.sp,
                                        color = visual.textColor,
                                        fontFamily = visual.fontFamily,
                                        fontWeight = visual.fontWeight,
                                        modifier = textAnimationModifier(visual, pulse, textAlpha, textScale),
                                        maxLines = if (visual.textAnimation == "marquee") 1 else Int.MAX_VALUE,
                                        overflow = if (visual.textAnimation == "marquee") TextOverflow.Ellipsis else TextOverflow.Clip,
                                    )
                                } else {
                                    if (visual.textAnimation != "none") {
                                        Text(
                                            text = animatedTextForBubble(message.text, visual.textAnimation).let {
                                                if (visual.textAnimation == "marquee") it.replace('\n', ' ') else it
                                            },
                                            color = visual.textColor,
                                            fontSize = visual.fontSizeSp.sp,
                                            lineHeight = visual.lineHeightSp.sp,
                                            fontFamily = visual.fontFamily,
                                            fontWeight = visual.fontWeight,
                                            maxLines = if (visual.textAnimation == "marquee") 1 else Int.MAX_VALUE,
                                            overflow = if (visual.textAnimation == "marquee") TextOverflow.Ellipsis else TextOverflow.Clip,
                                            modifier = textAnimationModifier(visual, pulse, textAlpha, textScale),
                                        )
                                    } else {
                                        MarkdownText(
                                            text = animatedTextForBubble(message.text, visual.textAnimation),
                                            color = visual.textColor,
                                            fontSize = visual.fontSizeSp.sp,
                                            lineHeight = visual.lineHeightSp.sp,
                                            fontFamily = visual.fontFamily,
                                            fontWeight = visual.fontWeight,
                                            modifier = textAnimationModifier(visual, pulse, textAlpha, textScale),
                                            onAction = onAction,
                                        )
                                    }
                                }
                            }
                            message.attachments.forEach { attachment ->
                                GroupAttachmentCard(
                                    attachment = attachment,
                                    c = c,
                                    onOpenHtmlViewer = onOpenHtmlViewer,
                                    onOpenBrowser = onOpenBrowser,
                                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                )
                            }
                        }
                        if (visual.animation in setOf("pulse", "sparkle", "breath")) {
                            GroupBubbleAnimatedStroke(
                                visual = visual,
                                pulse = pulse,
                                modifier = Modifier.matchParentSize(),
                                color = animatedBorder,
                            )
                        }
                        GroupBubbleLocalAccent(
                            visual = visual,
                            pulse = pulse,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    if (visual.animation == "sparkle") {
                        GroupBubbleSparkles(
                            visual = visual,
                            pulse = pulse,
                            modifier = Modifier
                                .matchParentSize()
                                .padding(visual.glowPaddingDp.dp),
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.size(6.dp))
            GradientAvatar(emoji = "👤", size = 32.dp, color = c.green)
        }
    }
}

private data class GroupBubbleVisual(
    val backgroundColor: Color,
    val backgroundImage: String,
    val gradientColors: List<Color>,
    val textColor: Color,
    val borderColor: Color,
    val accentColor: Color,
    val shape: RoundedCornerShape,
    val pattern: String,
    val decoration: String,
    val decorationText: String,
    val decorationPosition: String,
    val decorationAnimation: String,
    val decorationSizeDp: Int,
    val animation: String,
    val emotion: String,
    val fontFamily: FontFamily?,
    val fontWeight: FontWeight,
    val textAnimation: String,
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val paddingHorizontalDp: Int,
    val paddingVerticalDp: Int,
    val shadow: String,
    val shadowColor: Color,
    val shadowAlpha: Float,
    val shadowElevationDp: Int,
    val shadowOffsetXDp: Int,
    val shadowOffsetYDp: Int,
    val htmlHeightDp: Int,
    val htmlAllowJs: Boolean,
    val htmlAllowNetwork: Boolean,
    val htmlTransparent: Boolean,
    val imageMode: String,
    val borderWidthDp: Float,
    val glowAlpha: Float,
    val glowPaddingDp: Int,
)

@Composable
private fun animatedTextForBubble(text: String, animation: String): String {
    if (animation != "typewriter") return text
    var visibleChars by remember(text) { mutableStateOf(0) }
    LaunchedEffect(text) {
        visibleChars = 0
        val max = text.length.coerceAtMost(320)
        while (visibleChars < max) {
            withFrameNanos { }
            visibleChars = (visibleChars + 2).coerceAtMost(max)
        }
    }
    return text.take(visibleChars.coerceIn(0, text.length))
}

private fun textAnimationModifier(
    visual: GroupBubbleVisual,
    pulse: Float,
    alpha: Float,
    scale: Float,
): Modifier {
    val base = Modifier.graphicsLayer {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
        translationY = if (visual.textAnimation == "wave") -1.2f + pulse * 2.4f else 0f
    }
    return if (visual.textAnimation == "marquee") {
        base.basicMarquee(iterations = Int.MAX_VALUE)
    } else {
        base
    }
}

@Composable
private fun GroupHtmlMessageBubble(
    message: GroupMessage,
    htmlTemplate: String,
    visual: GroupBubbleVisual,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    val html = remember(htmlTemplate, message.text, visual.textColor, visual.accentColor) {
        buildGroupBubbleHtml(
            template = htmlTemplate,
            messageText = message.text,
            senderName = message.senderName,
            textColor = visual.textColor,
            accentColor = visual.accentColor,
        )
    }
    Box(modifier = modifier.widthIn(min = 96.dp, max = 280.dp)) {
        if (visual.glowAlpha > 0f) {
            GroupBubbleGlow(
                visual = visual,
                pulse = pulse,
                modifier = Modifier.matchParentSize(),
            )
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(if (visual.htmlTransparent) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = true
                    overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                    webViewClient = object : WebViewClient() {
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = !visual.htmlAllowNetwork
                    }
                    settings.javaScriptEnabled = visual.htmlAllowJs
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = visual.htmlAllowJs
                    settings.loadsImagesAutomatically = visual.htmlAllowNetwork
                    settings.blockNetworkImage = !visual.htmlAllowNetwork
                    settings.defaultTextEncodingName = "utf-8"
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "about:blank",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            },
            modifier = Modifier
                .padding(visual.glowPaddingDp.dp)
                .height(visual.htmlHeightDp.dp)
                .clip(visual.shape),
        )
    }
}

private fun buildGroupBubbleHtml(
    template: String,
    messageText: String,
    senderName: String,
    textColor: Color,
    accentColor: Color,
): String {
    val safeMessage = htmlEscape(messageText).replace("\n", "<br>")
    val safeSender = htmlEscape(senderName)
    val body = template
        .replace("{{message}}", safeMessage)
        .replace("{{text}}", safeMessage)
        .replace("{{sender}}", safeSender)
        .replace("{{textColor}}", textColor.toCssHex())
        .replace("{{accentColor}}", accentColor.toCssHex())
    return """
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
html,body{margin:0;padding:0;background:transparent;color:${textColor.toCssHex()};font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;overflow:hidden;}
*{box-sizing:border-box;max-width:100%;word-break:break-word;}
.mc-bubble{padding:10px 12px;border-radius:18px;background:rgba(250,250,247,.96);border:1px solid rgba(0,0,0,.08);}
@media (prefers-color-scheme: dark){.mc-bubble{background:rgba(20,20,20,.96);border-color:rgba(255,255,255,.14);}}
</style>
</head>
<body>$body</body>
</html>
""".trimIndent()
}

private fun htmlEscape(raw: String): String = raw
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private fun Color.toCssHex(): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return if (a == 255) "#%02X%02X%02X".format(r, g, b) else "rgba($r,$g,$b,${"%.2f".format(alpha)})"
}

private fun resolveGroupBubbleVisual(
    isUser: Boolean,
    style: ChatBubbleStyle?,
    fallbackAccent: Color,
    c: ClawColors,
): GroupBubbleVisual {
    if (isUser) {
        val shape = RoundedCornerShape(18.dp, 6.dp, 18.dp, 18.dp)
        return GroupBubbleVisual(
            backgroundColor = Color(0xFF080808),
            backgroundImage = "",
            gradientColors = emptyList(),
            textColor = Color.White,
            borderColor = if (c.isDark) c.border else Color.Transparent,
            accentColor = Color(0xFF080808),
            shape = shape,
            pattern = "none",
            decoration = "none",
            decorationText = "",
            decorationPosition = "top_end",
            decorationAnimation = "none",
            decorationSizeDp = 14,
            animation = "none",
            emotion = "neutral",
            fontFamily = null,
            fontWeight = FontWeight.Normal,
            textAnimation = "none",
            fontSizeSp = 14,
            lineHeightSp = 20,
            paddingHorizontalDp = 12,
            paddingVerticalDp = 8,
            shadow = "none",
            shadowColor = Color.Transparent,
            shadowAlpha = 0f,
            shadowElevationDp = 0,
            shadowOffsetXDp = 0,
            shadowOffsetYDp = 0,
            htmlHeightDp = 160,
            htmlAllowJs = false,
            htmlAllowNetwork = true,
            htmlTransparent = true,
            imageMode = "cover",
            borderWidthDp = 1f,
            glowAlpha = 0f,
            glowPaddingDp = 0,
        )
    }

    val preset = style?.preset?.lowercase().orEmpty().ifBlank { "minimal" }
    val accent = style?.accentColor?.toComposeColorOrNull() ?: fallbackAccent
    val baseRadius = (style?.radiusDp ?: 18).coerceIn(0, 48)
    fun corner(value: Int?): androidx.compose.ui.unit.Dp =
        (value?.takeIf { it >= 0 } ?: baseRadius).coerceIn(0, 48).dp
    val shape = RoundedCornerShape(
        topStart = corner(style?.radiusTopStartDp),
        topEnd = corner(style?.radiusTopEndDp),
        bottomEnd = corner(style?.radiusBottomEndDp),
        bottomStart = corner(style?.radiusBottomStartDp),
    )
    val defaultBg = if (c.isDark) Color(0xFF141414) else Color(0xFFF8F8F6)
    val defaultText = if (c.isDark) Color(0xFFF4F4F4) else Color(0xFF111111)
    val bgOverride = style?.backgroundColor?.toComposeColorOrNull()
    val textOverride = style?.textColor?.toComposeColorOrNull()
    val borderOverride = style?.borderColor?.toComposeColorOrNull()
    val gradientColors = style?.gradient.orEmpty().mapNotNull { it.toComposeColorOrNull() }.take(4)

    val emotion = style?.emotion?.lowercase().orEmpty().ifBlank { "neutral" }
    val emotionAccent = when (emotion) {
        "happy", "excited" -> Color(0xFFC7F43A)
        "sad" -> Color(0xFF8A8A8A)
        "angry" -> Color(0xFF111111)
        "shy", "love" -> Color(0xFFE75A7C)
        "cool" -> Color(0xFF56D6BA)
        "sleepy" -> Color(0xFF9CA3AF)
        else -> accent
    }

    val (bg, text, border) = when (preset) {
        "ink" -> Triple(
            if (c.isDark) Color(0xFFF8F8F5) else Color(0xFF050505),
            if (c.isDark) Color(0xFF0B0B0B) else Color.White,
            Color.Transparent,
        )
        "paper" -> Triple(
            if (c.isDark) Color(0xFF20201E) else Color(0xFFFAFAF7),
            defaultText,
            if (c.isDark) Color(0xFF30302C) else Color(0xFFE8E8E2),
        )
        "outline" -> Triple(
            if (c.isDark) Color(0xFF0E0E0E) else Color.White,
            defaultText,
            emotionAccent.copy(alpha = 0.52f),
        )
        "glass" -> Triple(
            if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.035f),
            defaultText,
            if (c.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.09f),
        )
        "neon" -> Triple(
            if (c.isDark) Color(0xFF0B0D09) else Color(0xFFF9FBF2),
            defaultText,
            emotionAccent.copy(alpha = 0.72f),
        )
        "theme", "image" -> Triple(
            if (c.isDark) Color(0xFF111111) else Color(0xFFFFFFFF),
            defaultText,
            emotionAccent.copy(alpha = 0.42f),
        )
        else -> Triple(defaultBg, defaultText, c.border.copy(alpha = 0.72f))
    }

    val rawAnimation = style?.animation?.lowercase().orEmpty().ifBlank { "none" }
    val rawShadow = style?.shadow?.lowercase().orEmpty().ifBlank { "none" }
    val effectiveAnimation = when {
        rawAnimation != "none" -> rawAnimation
        preset == "neon" -> "pulse"
        emotion in setOf("happy", "excited", "love") -> "breath"
        else -> "none"
    }
    val effectiveShadow = when {
        rawShadow != "none" -> rawShadow
        preset == "neon" || effectiveAnimation in setOf("sparkle", "pulse") -> "glow"
        else -> "none"
    }

    return GroupBubbleVisual(
        backgroundColor = bgOverride ?: bg,
        backgroundImage = style?.backgroundImage.orEmpty(),
        gradientColors = gradientColors,
        textColor = textOverride ?: text,
        borderColor = borderOverride ?: border,
        accentColor = emotionAccent,
        shape = shape,
        pattern = style?.pattern?.lowercase().orEmpty().ifBlank { "none" },
        decoration = style?.decoration?.lowercase().orEmpty().ifBlank { "none" },
        decorationText = style?.decorationText.orEmpty().take(4),
        decorationPosition = style?.decorationPosition?.lowercase().orEmpty().ifBlank { "top_end" },
        decorationAnimation = style?.decorationAnimation?.lowercase().orEmpty().ifBlank { "none" },
        decorationSizeDp = (style?.decorationSizeDp ?: 14).coerceIn(8, 28),
        animation = effectiveAnimation,
        emotion = emotion,
        fontFamily = when (style?.fontFamily?.lowercase()) {
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            "rounded" -> FontFamily.SansSerif
            else -> null
        },
        fontWeight = when (style?.fontWeight?.lowercase()) {
            "light" -> FontWeight.Light
            "medium" -> FontWeight.Medium
            "semibold" -> FontWeight.SemiBold
            "bold" -> FontWeight.Bold
            "black" -> FontWeight.Black
            else -> FontWeight.Normal
        },
        textAnimation = style?.textAnimation?.lowercase().orEmpty().ifBlank { "none" },
        fontSizeSp = (style?.fontSizeSp ?: 14).coerceIn(12, 20),
        lineHeightSp = (style?.lineHeightSp ?: 20).coerceIn(16, 28),
        paddingHorizontalDp = (style?.paddingHorizontalDp ?: 12).coerceIn(8, 22),
        paddingVerticalDp = (style?.paddingVerticalDp ?: 8).coerceIn(6, 18),
        shadow = effectiveShadow,
        shadowColor = style?.shadowColor?.toComposeColorOrNull() ?: if (effectiveShadow == "glow") emotionAccent else Color.Black,
        shadowAlpha = style?.shadowAlpha?.takeIf { it >= 0f }?.coerceIn(0f, 0.8f) ?: when (effectiveShadow) {
            "glow" -> 0.36f
            "soft" -> 0.16f
            else -> 0f
        },
        shadowElevationDp = style?.shadowElevationDp?.takeIf { it >= 0 }?.coerceIn(0, 32) ?: when (effectiveShadow) {
            "glow" -> 14
            "soft" -> 5
            else -> 0
        },
        shadowOffsetXDp = (style?.shadowOffsetXDp ?: 0).coerceIn(-12, 12),
        shadowOffsetYDp = (style?.shadowOffsetYDp ?: 0).coerceIn(-12, 12),
        htmlHeightDp = (style?.htmlHeightDp ?: 160).coerceIn(80, 420),
        htmlAllowJs = style?.htmlAllowJs ?: false,
        htmlAllowNetwork = style?.htmlAllowNetwork ?: true,
        htmlTransparent = style?.htmlTransparent ?: true,
        imageMode = style?.imageMode?.lowercase().orEmpty().ifBlank { "cover" },
        borderWidthDp = when {
            preset == "neon" -> 1.6f
            effectiveAnimation in setOf("sparkle", "pulse") -> 1.35f
            border == Color.Transparent -> 0f
            else -> 1f
        },
        glowAlpha = when {
            preset == "neon" -> 0.20f
            effectiveShadow == "glow" -> 0.14f
            else -> 0f
        },
        glowPaddingDp = when {
            preset == "neon" -> 4
            effectiveShadow == "glow" -> 3
            else -> 0
        },
    )
}

@Composable
private fun GroupBubbleGlow(
    visual: GroupBubbleVisual,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val glow = visual.glowPaddingDp.dp.toPx().coerceAtLeast(6f)
        drawRoundRect(
            color = visual.accentColor.copy(alpha = visual.glowAlpha * (0.75f + pulse * 0.85f)),
            topLeft = androidx.compose.ui.geometry.Offset(-glow * 0.45f, -glow * 0.45f),
            size = androidx.compose.ui.geometry.Size(size.width + glow * 0.9f, size.height + glow * 0.9f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx() + glow, 22.dp.toPx() + glow),
            style = Stroke(width = glow * (1.2f + pulse * 0.8f)),
        )
        drawRoundRect(
            color = visual.borderColor.copy(alpha = visual.glowAlpha * (0.45f + pulse * 0.35f)),
            topLeft = androidx.compose.ui.geometry.Offset(-glow * 0.18f, -glow * 0.18f),
            size = androidx.compose.ui.geometry.Size(size.width + glow * 0.36f, size.height + glow * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx() + glow * 0.45f, 18.dp.toPx() + glow * 0.45f),
            style = Stroke(width = glow * 0.72f),
        )
    }
}

@Composable
private fun GroupBubbleAnimatedStroke(
    visual: GroupBubbleVisual,
    pulse: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val inset = 1.5.dp.toPx()
        val width = when (visual.animation) {
            "sparkle" -> 1.6.dp.toPx() + pulse * 1.4.dp.toPx()
            "breath" -> 1.1.dp.toPx() + pulse * 1.0.dp.toPx()
            else -> 1.2.dp.toPx() + pulse * 1.2.dp.toPx()
        }
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    color.copy(alpha = 0.25f),
                    visual.accentColor.copy(alpha = 0.90f),
                    color.copy(alpha = 0.35f),
                ),
                start = androidx.compose.ui.geometry.Offset(size.width * pulse, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width * (1f - pulse), size.height),
            ),
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx()),
            style = Stroke(width = width),
        )
    }
}

@Composable
private fun GroupBubbleSparkles(
    visual: GroupBubbleVisual,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val points = listOf(
            0.12f to 0.18f,
            0.84f to 0.22f,
            0.76f to 0.72f,
            0.22f to 0.82f,
        )
        points.forEachIndexed { index, (xRatio, yRatio) ->
            val phase = ((pulse + index * 0.23f) % 1f)
            val radius = (1.4f + phase * 3.4f).dp.toPx()
            val alpha = (0.18f + phase * 0.52f).coerceIn(0f, 0.72f)
            val center = androidx.compose.ui.geometry.Offset(
                x = size.width * xRatio + ((phase - 0.5f) * 8.dp.toPx()),
                y = size.height * yRatio - ((phase - 0.5f) * 5.dp.toPx()),
            )
            drawCircle(
                color = visual.accentColor.copy(alpha = alpha),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.75f),
                radius = radius * 0.35f,
                center = center,
            )
        }
    }
}

@Composable
private fun BoxScope.GroupBubbleLocalAccent(
    visual: GroupBubbleVisual,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    val mark = when (visual.decoration) {
        "dot" -> "•"
        "sparkle" -> "✦"
        "heart" -> "♡"
        "star" -> "★"
        "moon" -> "☾"
        "badge" -> visual.decorationText.ifBlank { "AI" }
        "text" -> visual.decorationText
        else -> ""
    }
    if (mark.isBlank()) return
    val align = when (visual.decorationPosition) {
        "top_start" -> Alignment.TopStart
        "bottom_start" -> Alignment.BottomStart
        "bottom_end" -> Alignment.BottomEnd
        "tail" -> Alignment.BottomStart
        else -> Alignment.TopEnd
    }
    val offsetX = when (visual.decorationPosition) {
        "top_start", "bottom_start", "tail" -> (-5).dp
        else -> 5.dp
    }
    val offsetY = when (visual.decorationPosition) {
        "bottom_start", "bottom_end", "tail" -> 5.dp
        else -> (-5).dp
    }
    val floatY = if (visual.decorationAnimation in setOf("float", "sparkle")) (-1.5f + pulse * 3f) else 0f
    val scale = when (visual.decorationAnimation) {
        "pulse", "sparkle" -> 0.92f + pulse * 0.16f
        else -> 1f
    }
    Box(
        modifier = modifier
            .align(align)
            .offset(offsetX, offsetY)
            .graphicsLayer {
                translationY = floatY
                scaleX = scale
                scaleY = scale
                alpha = if (visual.decorationAnimation == "sparkle") 0.55f + pulse * 0.45f else 0.88f
            }
            .clip(RoundedCornerShape(999.dp))
            .background(visual.accentColor.copy(alpha = 0.12f))
            .border(0.5.dp, visual.accentColor.copy(alpha = 0.34f), RoundedCornerShape(999.dp))
            .padding(horizontal = if (mark.length > 1) 6.dp else 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            mark,
            color = visual.accentColor,
            fontSize = visual.decorationSizeDp.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = visual.decorationSizeDp.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BoxScope.BubblePatternOverlay(visual: GroupBubbleVisual, pulse: Float) {
    val text = when (visual.pattern) {
        "dot" -> "· · · · · · · · · · · ·"
        "grid" -> "＋  ＋  ＋  ＋  ＋"
        "star" -> "✦   ✧   ✦"
        "stripe" -> "//// //// ////"
        "contour" -> "⌁  ⌁  ⌁  ⌁"
        else -> when (visual.emotion) {
            "happy", "excited" -> "✦   ✦   ✦"
            "sad" -> "···   ···"
            "angry" -> "//// ////"
            "shy", "love" -> "♡   ♡   ♡"
            "sleepy" -> "zZ   zZ"
            else -> ""
        }
    }
    if (text.isBlank()) return
    Text(
        text = text,
        color = visual.accentColor.copy(alpha = if (visual.animation == "sparkle") 0.10f + pulse * 0.16f else 0.12f),
        fontSize = if (visual.animation == "sparkle") (18f + pulse * 5f).sp else 20.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                translationX = if (visual.animation == "sparkle") -6f + pulse * 12f else 0f
                translationY = if (visual.animation == "sparkle") -2f + pulse * 4f else 0f
            }
            .padding(8.dp),
        maxLines = 3,
    )
}

private fun decodeBubbleBackgroundBitmap(
    context: android.content.Context,
    source: String,
    maxPx: Int,
): Bitmap? = runCatching {
    val raw = source.trim()
    if (raw.isBlank()) return@runCatching null
    val bitmap = when {
        raw.startsWith("data:image") -> {
            val bytes = Base64.decode(raw.substringAfter("base64,", raw), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        raw.startsWith("content://") -> {
            context.contentResolver.openInputStream(android.net.Uri.parse(raw))?.use { BitmapFactory.decodeStream(it) }
        }
        raw.startsWith("file://") -> BitmapFactory.decodeFile(android.net.Uri.parse(raw).path)
        raw.startsWith("/") -> BitmapFactory.decodeFile(raw)
        else -> null
    } ?: return@runCatching null

    val scale = minOf(maxPx.toFloat() / bitmap.width, maxPx.toFloat() / bitmap.height, 1f)
    if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            .also { bitmap.recycle() }
    } else {
        bitmap
    }
}.getOrNull()

private fun String.toComposeColorOrNull(): Color? {
    val raw = trim()
    if (!raw.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) return null
    return runCatching {
        val hex = raw.removePrefix("#")
        val value = hex.toLong(16)
        if (hex.length == 6) Color(0xFF000000 or value) else Color(value)
    }.getOrNull()
}

@Composable
private fun GroupAttachmentChip(
    attachment: SkillAttachment,
    c: ClawColors,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.cardAlt)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable { onRemove() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(groupAttachmentLabel(attachment), color = c.text, fontSize = 11.sp, maxLines = 1)
        Text("×", color = c.subtext, fontSize = 12.sp)
    }
}

@Composable
private fun GroupInputCapabilityTile(
    mark: String,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.cardAlt)
                .border(0.5.dp, c.border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(mark, color = c.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(label, color = c.subtext, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupAttachmentCard(
    attachment: SkillAttachment,
    c: ClawColors,
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val context = LocalContext.current
    when (attachment) {
        is SkillAttachment.ImageData -> {
            val bitmap = remember(attachment.base64) {
                runCatching {
                    val raw = attachment.base64.substringAfter("base64,", attachment.base64)
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (bitmap != null) {
                var showFullscreen by remember { mutableStateOf(false) }
                val ratio = remember(bitmap) {
                    (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.8f)
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = 230.dp)
                        .clickable { showFullscreen = true },
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = attachment.prompt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    if (!attachment.prompt.isNullOrBlank()) {
                        Text(
                            attachment.prompt,
                            color = c.subtext,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
                if (showFullscreen) {
                    GroupFullscreenImageDialog(bitmap = bitmap, onDismiss = { showFullscreen = false })
                }
            } else {
                GroupAttachmentTextCard(str(R.string.group_attachment_image), c)
            }
        }
        is SkillAttachment.FileData -> GroupFileAttachmentCard(attachment, context, c)
        is SkillAttachment.HtmlData -> GroupAttachmentTextCard(str(R.string.group_attachment_web, attachment.title), c, action = str(R.string.chat_607e7a)) {
            onOpenHtmlViewer(attachment)
        }
        is SkillAttachment.WebPage -> GroupAttachmentTextCard(str(R.string.group_attachment_link, attachment.title), c, action = str(R.string.chat_607e7a)) {
            onOpenBrowser(attachment.url)
        }
        is SkillAttachment.SearchResults -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GroupAttachmentTextCard(str(R.string.group_attachment_search, attachment.query, attachment.pages.size), c)
                attachment.pages.take(3).forEach { page ->
                    GroupAttachmentTextCard(page.title.ifBlank { page.url }, c, action = str(R.string.chat_607e7a)) {
                        onOpenBrowser(page.url)
                    }
                }
            }
        }
        is SkillAttachment.FileList -> GroupFileListCard(attachment, context, c)
        is SkillAttachment.AccessibilityRequest -> GroupAttachmentTextCard(str(R.string.group_attachment_permission, attachment.skillName), c, action = str(R.string.perm_open)) {
            onOpenAccessibilitySettings()
        }
    }
}

@Composable
private fun GroupAttachmentTextCard(
    text: String,
    c: ClawColors,
    action: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.cardAlt)
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = c.text, fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!action.isNullOrBlank()) {
            Text(action, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GroupFileAttachmentCard(
    attachment: SkillAttachment.FileData,
    context: android.content.Context,
    c: ClawColors,
) {
    val isImage = isGroupImageFileAttachment(attachment)
    if (isImage) {
        GroupImageFileCard(attachment, context, c)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.cardAlt)
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .clickable { openGroupFileAttachment(context, attachment) }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(groupMimeEmoji(attachment.mimeType), fontSize = 17.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.name, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${groupFormatFileSize(attachment.sizeBytes)} · ${attachment.mimeType}", color = c.subtext, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text(str(R.string.perm_open), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GroupImageFileCard(
    attachment: SkillAttachment.FileData,
    context: android.content.Context,
    c: ClawColors,
) {
    val bitmap = remember(attachment.path) {
        decodeGroupAttachmentBitmap(context, attachment, maxPx = 900)
    }
    var showFullscreen by remember { mutableStateOf(false) }
    val isSticker = isGroupStickerFileAttachment(attachment)
    val maxThumbWidth = if (isSticker) 144.dp else 210.dp
    Box(
        modifier = Modifier
            .widthIn(max = maxThumbWidth)
            .clip(RoundedCornerShape(14.dp))
            .clickable { if (bitmap != null) showFullscreen = true else openGroupFileAttachment(context, attachment) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            val ratio = remember(bitmap) {
                (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.8f)
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = attachment.name,
                modifier = Modifier
                    .width(maxThumbWidth)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(if (isSticker) 8.dp else 14.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("图", color = c.subtext, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(28.dp))
        }
    }
    if (bitmap != null && showFullscreen) {
        GroupFullscreenImageDialog(bitmap = bitmap, onDismiss = { showFullscreen = false })
    }
}

@Composable
private fun GroupFileListCard(
    attachment: SkillAttachment.FileList,
    context: android.content.Context,
    c: ClawColors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.cardAlt)
            .border(1.dp, c.border, RoundedCornerShape(8.dp)),
    ) {
        Text(
            str(R.string.group_attachment_file_list, attachment.files.size),
            color = c.subtext,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
        attachment.files.take(8).forEach { entry ->
            val file = SkillAttachment.FileData(entry.path, entry.name, entry.mimeType, entry.sizeBytes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openGroupFileAttachment(context, file) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(groupMimeEmoji(entry.mimeType), fontSize = 14.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.name, color = c.text, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(groupFormatFileSize(entry.sizeBytes), color = c.subtext, fontSize = 10.sp)
                }
                Text(str(R.string.perm_open), color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun GroupFullscreenImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onDismiss),
                contentScale = ContentScale.Fit,
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GroupDialogButton(str(R.string.chat_f235e7)) { saveGroupBitmapToGallery(context, bitmap) }
                GroupDialogButton(str(R.string.btn_close), onDismiss)
            }
        }
    }
}

@Composable
private fun GroupDialogButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

private fun saveGroupBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
    val filename = "MobileClaw_${System.currentTimeMillis()}.jpg"
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MobileClaw")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { context.contentResolver.openOutputStream(it) }?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val file = java.io.File(dir, filename)
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
        Toast.makeText(context, str(R.string.chat_1292d3), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, str(R.string.save_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
    }
}

private fun openGroupFileAttachment(context: android.content.Context, attachment: SkillAttachment.FileData) {
    val uri = if (attachment.path.startsWith("content://")) {
        android.net.Uri.parse(attachment.path)
    } else {
        val file = java.io.File(attachment.path)
        if (!file.exists()) {
            Toast.makeText(context, str(R.string.file_not_found, attachment.name), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { e ->
            Toast.makeText(context, str(R.string.share_failed, e.message?.take(80) ?: ""), Toast.LENGTH_LONG).show()
            return
        }
    }
    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
        addFlags(flags)
    }
    if (context.packageManager.queryIntentActivities(viewIntent, 0).isNotEmpty()) {
        runCatching { context.startActivity(android.content.Intent.createChooser(viewIntent, str(R.string.open_file, attachment.name))) }
        return
    }
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = attachment.mimeType.ifBlank { "*/*" }
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(flags)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(shareIntent, str(R.string.share_file, attachment.name)))
    }.onFailure {
        Toast.makeText(context, str(R.string.chat_none), Toast.LENGTH_LONG).show()
    }
}

private fun decodeGroupAttachmentBitmap(
    context: android.content.Context,
    attachment: SkillAttachment.FileData,
    maxPx: Int,
): Bitmap? = runCatching {
    if (attachment.path.startsWith("content://")) {
        context.contentResolver.openInputStream(android.net.Uri.parse(attachment.path))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }?.let { bitmap ->
            val scale = minOf(maxPx.toFloat() / bitmap.width, maxPx.toFloat() / bitmap.height, 1f)
            if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    .also { bitmap.recycle() }
            } else {
                bitmap
            }
        }
    } else {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(attachment.path, this)
            inSampleSize = maxOf(1, maxOf(outWidth / maxPx, outHeight / maxPx))
            inJustDecodeBounds = false
        }
        BitmapFactory.decodeFile(attachment.path, opts)
    }
}.getOrNull()

private fun groupMimeEmoji(mimeType: String): String = when {
    mimeType.startsWith("image/") -> "🖼️"
    mimeType.startsWith("video/") -> "🎬"
    mimeType.startsWith("audio/") -> "🎵"
    mimeType == "application/pdf" -> "📕"
    mimeType.contains("json") -> "🗂️"
    mimeType.contains("csv") || mimeType.contains("excel") || mimeType.contains("spreadsheet") -> "📊"
    mimeType.contains("python") || mimeType.contains("javascript") || mimeType.contains("x-") -> "💻"
    mimeType.contains("html") -> "🌐"
    mimeType.contains("zip") || mimeType.contains("archive") -> "📦"
    else -> "📄"
}

private fun groupFormatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}

private fun groupAttachmentLabel(attachment: SkillAttachment): String = when (attachment) {
    is SkillAttachment.ImageData -> str(R.string.group_label_image)
    is SkillAttachment.FileData -> attachment.name.ifBlank { str(R.string.group_label_file) }
    is SkillAttachment.HtmlData -> attachment.title.ifBlank { str(R.string.group_label_web) }
    is SkillAttachment.WebPage -> attachment.title.ifBlank { str(R.string.group_label_link) }
    is SkillAttachment.SearchResults -> str(R.string.group_label_search)
    is SkillAttachment.FileList -> str(R.string.group_label_file_list)
    is SkillAttachment.AccessibilityRequest -> str(R.string.group_label_permission)
}

// ── Member chip ───────────────────────────────────────────────────────────────

@Composable
private fun MemberChip(avatar: String, name: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(safeAvatarGlyph(avatar), fontSize = 11.sp, maxLines = 1)
        Text(name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

// ── @mention picker sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MentionPickerSheet(
    memberRoles: List<Role>,
    colorMap: Map<String, Color>,
    c: ClawColors,
    onPick: (Role) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(12.dp),
    ) {
        Column {
            Text(str(R.string.group_mention_label), color = c.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                memberRoles.forEach { role ->
                    val color = colorMap[role.id] ?: c.accent
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(color.copy(alpha = 0.12f))
                            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable { onPick(role) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GradientAvatar(emoji = role.avatar, size = 22.dp, color = color)
                        Text(role.name, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── @mention annotated string builder ────────────────────────────────────────

private fun buildMentionAnnotated(
    text: String,
    memberRoles: List<Role>,
    colorMap: Map<String, Color>,
    c: ClawColors,
) = buildAnnotatedString {
    val mentionRegex = Regex("@([\\w\\u4e00-\\u9fff·]+)")
    var last = 0
    mentionRegex.findAll(text).forEach { match ->
        if (match.range.first > last) append(text.substring(last, match.range.first))
        val mentionName = match.groupValues[1]
        val role = memberRoles.firstOrNull { it.name == mentionName || it.name.contains(mentionName) || mentionName.contains(it.name) }
        val color = if (role != null) colorMap[role.id] ?: c.accent else c.accent
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold, background = color.copy(alpha = 0.1f))) {
            append(match.value)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
