package com.mobileclaw.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import com.mobileclaw.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.agent.Group
import com.mobileclaw.agent.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun GroupChatScreen(
    group: Group,
    messages: List<GroupMessage>,
    availableRoles: List<Role>,
    isRunning: Boolean,
    typingAgentId: String?,
    streamingText: String,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var showMentionPicker by remember { mutableStateOf(false) }
    var showMemberDrawer by remember { mutableStateOf(false) }

    // Members: roles in this group, ordered for color assignment
    val memberRoles = group.memberRoleIds.mapNotNull { id -> availableRoles.firstOrNull { it.id == id } }
    val colorMap: Map<String, Color> = memberRoles.mapIndexed { i, r -> r.id to agentColor(i) }.toMap()
    val totalMembers = memberRoles.size + 1 // +1 for the user

    // Scroll to bottom whenever messages change or streaming text changes
    LaunchedEffect(messages.size, streamingText) {
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
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_back), tint = c.text)
                }
                Text(group.emoji, fontSize = 18.sp)
                Spacer(Modifier.size(6.dp))
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

                // Member count button → opens member drawer
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.accent.copy(alpha = 0.10f))
                        .clickable { showMemberDrawer = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        // Show up to 3 avatars overlapping
                        Box(modifier = Modifier.width((14 + (minOf(totalMembers, 3) - 1) * 10).dp)) {
                            listOf("👤").plus(memberRoles.take(2).map { it.avatar })
                                .forEachIndexed { i, av ->
                                    Text(av, fontSize = 13.sp, modifier = Modifier.offset(x = (i * 10).dp))
                                }
                        }
                        Text(
                            "$totalMembers",
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)
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
                GroupMessageBubble(
                    message = msg,
                    isUser = isUser,
                    accentColor = agentColor,
                    memberRoles = memberRoles,
                    colorMap = colorMap,
                    c = c,
                )
                Spacer(Modifier.height(6.dp))
            }

            // Streaming / typing bubble
            if (typingAgentId != null) {
                item {
                    val role = availableRoles.firstOrNull { it.id == typingAgentId }
                    val agentColor = colorMap[typingAgentId] ?: c.accent
                    TypingBubble(
                        senderName = role?.name ?: typingAgentId,
                        senderAvatar = role?.avatar ?: "🤖",
                        accentColor = agentColor,
                        streamingText = streamingText,
                        c = c,
                    )
                    Spacer(Modifier.height(6.dp))
                }
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
                    val newText = if (current.endsWith("@") || current.isEmpty()) "${current}${role.name} "
                    else "$current @${role.name} "
                    input = TextFieldValue(newText, selection = androidx.compose.ui.text.TextRange(newText.length))
                    showMentionPicker = false
                },
                onDismiss = { showMentionPicker = false },
            )
        }

        // ── Input bar ────────────────────────────────────────────────────────
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // @ button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.12f))
                    .clickable { showMentionPicker = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("@", color = c.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.cardAlt)
                    .border(1.dp, c.border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (input.text.isEmpty()) {
                    Text(stringResource(R.string.group_chat_hint), color = c.subtext, fontSize = 14.sp)
                }
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
                )
            }

            // Send button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (input.text.isNotBlank() && !isRunning) c.accent else c.subtext.copy(alpha = 0.3f))
                    .clickable(enabled = input.text.isNotBlank() && !isRunning) {
                        onSend(input.text.trim())
                        input = TextFieldValue("")
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("↑", color = c.bg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        } // end main Column

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
                        "成员 ($totalMembers)",
                        color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showMemberDrawer = false }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = c.border, thickness = 0.5.dp)

                // User row
                MemberDrawerRow(avatar = "👤", name = "你", color = c.green, description = "用户", c = c)
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)

                // Agent rows
                memberRoles.forEachIndexed { i, role ->
                    MemberDrawerRow(
                        avatar = role.avatar,
                        name = role.name,
                        color = agentColor(i),
                        description = role.description.take(60),
                        isTyping = role.id == typingAgentId,
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
private fun MemberDrawerRow(
    avatar: String,
    name: String,
    color: Color,
    description: String,
    isTyping: Boolean = false,
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
                if (isTyping) Text("typing…", color = color.copy(alpha = 0.6f), fontSize = 10.sp)
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
    memberRoles: List<Role>,
    colorMap: Map<String, Color>,
    c: ClawColors,
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt))

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
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(time, color = c.subtext.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Spacer(Modifier.height(3.dp))

            // Bubble
            Box(
                modifier = Modifier
                    .clip(
                        if (isUser) RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
                        else RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp),
                    )
                    .background(if (isUser) c.accent.copy(alpha = 0.18f) else c.card)
                    .border(
                        1.dp,
                        if (isUser) c.accent.copy(alpha = 0.3f) else c.border,
                        if (isUser) RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp)
                        else RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = buildMentionAnnotated(message.text, memberRoles, colorMap, c),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = c.text,
                )
            }
        }

        if (isUser) {
            Spacer(Modifier.size(6.dp))
            GradientAvatar(emoji = "👤", size = 32.dp, color = c.green)
        }
    }
}

// ── Typing / streaming bubble ────────────────────────────────────────────────

@Composable
private fun TypingBubble(
    senderName: String,
    senderAvatar: String,
    accentColor: Color,
    streamingText: String,
    c: ClawColors,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "cursorAlpha",
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        GradientAvatar(emoji = senderAvatar, size = 32.dp, color = accentColor)
        Spacer(Modifier.size(6.dp))

        Column(modifier = Modifier.widthIn(max = 280.dp)) {
            Text(senderName, color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (streamingText.isEmpty()) {
                    // Dots animation
                    ThinkingDots(c)
                } else {
                    Text(
                        text = buildAnnotatedString {
                            append(streamingText)
                            withStyle(SpanStyle(color = accentColor.copy(alpha = cursorAlpha))) { append("▋") }
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = c.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThinkingDots(c: ClawColors) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dotsPhase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha = if (phase.toInt() == i) 1f else 0.3f
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(c.subtext.copy(alpha = alpha)))
        }
    }
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
        Text(avatar, fontSize = 11.sp)
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
            Text(stringResource(R.string.group_mention_label), color = c.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                        Text(role.avatar, fontSize = 16.sp)
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
