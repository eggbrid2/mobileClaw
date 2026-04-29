package com.mobileclaw.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.mobileclaw.R
import com.mobileclaw.skill.SkillAttachment
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ExampleTasks = listOf(
    "🔍 搜索今天的科技热点新闻",
    "🌤 查询当前城市的天气情况",
    "📱 打开微信并发一条消息",
    "📸 截图并告诉我屏幕上有什么",
    "⚡ 写一段 Python 代码帮我处理数据",
)

private val CommonModels = listOf(
    "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4",
    "gpt-3.5-turbo", "o1", "o1-mini", "o3-mini",
    "deepseek-chat", "deepseek-reasoner",
    "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022",
)

// ── Main Screen ──────────────────────────────────────────────────────────────
@Composable
fun ChatScreen(
    uiState: MainUiState,
    onSendGoal: (String) -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkillManager: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onAttachImage: (String?) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onFetchModels: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
) {
    val c = LocalClawColors.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = uiState.messages.size + if (uiState.isRunning) 1 else 0
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            val base64 = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bm = BitmapFactory.decodeStream(stream)
                    val out = ByteArrayOutputStream()
                    val scale = minOf(1024f / bm.width, 1024f / bm.height, 1f)
                    val scaled = if (scale < 1f) {
                        Bitmap.createScaledBitmap(bm, (bm.width * scale).toInt(), (bm.height * scale).toInt(), true)
                            .also { bm.recycle() }
                    } else bm
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    scaled.recycle()
                    "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
            }.getOrNull()
            onAttachImage(base64)
        }
    }

    // Scroll to bottom when new items arrive or streaming content grows.
    // Use instant scroll for session loads (big jumps), animated scroll for incremental additions.
    val scrollKey = remember(uiState.streamingToken.length / 40, uiState.streamingThought.length / 40) { Unit }
    var lastScrolledCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(itemCount, uiState.activeLogLines.size, scrollKey) {
        if (itemCount > 0) {
            if (lastScrolledCount < 0 || itemCount - lastScrolledCount > 1) {
                listState.scrollToItem(itemCount - 1)
            } else {
                listState.animateScrollToItem(itemCount - 1)
            }
            lastScrolledCount = itemCount
        } else {
            lastScrolledCount = 0
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bg).imePadding()) {
        TopBar(
            isRunning = uiState.isRunning,
            isConfigured = uiState.isConfigured,
            promotableCount = uiState.promotableSkills.size,
            currentModel = uiState.currentModel,
            currentRoleAvatar = uiState.currentRole.avatar,
            availableModels = uiState.availableModels,
            onOpenDrawer = onOpenDrawer,
            onModelChange = onModelChange,
            onFetchModels = onFetchModels,
        )

        if (!uiState.isConfigured && uiState.messages.isEmpty()) {
            SetupBanner(onOpenSettings)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (uiState.messages.isEmpty() && !uiState.isRunning) {
                item {
                    EmptyState(
                        recommendations = uiState.recommendations,
                        onTaskSelected = { task -> onSendGoal(task.substringAfter(" ")) },
                        onOpenHelp = onOpenHelp,
                    )
                }
            }
            items(uiState.messages) { msg ->
                when (msg.role) {
                    MessageRole.USER  -> UserBubble(msg.text, msg.imageBase64)
                    MessageRole.AGENT -> AgentBubble(msg.text, msg.logLines, msg.attachments, onOpenHtmlViewer)
                }
            }
            if (uiState.isRunning) {
                item { ActiveTaskBubble(uiState.activeLogLines, uiState.streamingToken, uiState.streamingThought, uiState.activeAttachments, onOpenHtmlViewer) }
            }
        }

        InputBar(
            input = input,
            isRunning = uiState.isRunning,
            attachedImageBase64 = uiState.inputImageBase64,
            onInputChange = { input = it },
            onSend = { if (input.isNotBlank() || uiState.inputImageBase64 != null) { onSendGoal(input.trim()); input = "" } },
            onStop = onStop,
            onAttachImage = { imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            onRemoveImage = { onAttachImage(null) },
        )
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    isRunning: Boolean,
    isConfigured: Boolean,
    promotableCount: Int,
    currentModel: String,
    currentRoleAvatar: String,
    availableModels: List<String>,
    onOpenDrawer: () -> Unit,
    onModelChange: (String) -> Unit,
    onFetchModels: () -> Unit,
) {
    val c = LocalClawColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    var showModelPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Hamburger menu button
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = "Open drawer", tint = c.subtext)
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.3f), c.accent.copy(alpha = 0.1f))))
                    .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(currentRoleAvatar, fontSize = 17.sp)
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MobileClaw",
                    color = c.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 0.2.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Status dot + text
                    val dotColor = when {
                        isRunning    -> c.accent.copy(alpha = pulseAlpha)
                        isConfigured -> c.green
                        else         -> c.red
                    }
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(dotColor))
                    Text(
                        when {
                            isRunning    -> stringResource(R.string.status_running)
                            isConfigured -> stringResource(R.string.status_ready)
                            else         -> stringResource(R.string.status_not_configured)
                        },
                        color = dotColor.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                    )
                    // Divider
                    Box(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(10.dp)
                            .background(c.border),
                    )
                    // Model chip
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showModelPicker = true }
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            currentModel.let { m ->
                                // Abbreviate long model names
                                when {
                                    m.length > 14 -> m.take(12) + "…"
                                    else -> m
                                }
                            },
                            color = c.accent.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            letterSpacing = 0.2.sp,
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = c.accent.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }

            // Badge for promotable skills
            if (promotableCount > 0) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (promotableCount > 9) "9+" else promotableCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        HorizontalDivider(color = c.border, thickness = 0.5.dp)
    }

    if (showModelPicker) {
        ModelPickerDialog(
            currentModel = currentModel,
            availableModels = availableModels.ifEmpty { CommonModels },
            onSelect = { onModelChange(it); showModelPicker = false },
            onFetch = onFetchModels,
            onDismiss = { showModelPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerDialog(
    currentModel: String,
    availableModels: List<String>,
    onSelect: (String) -> Unit,
    onFetch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalClawColors.current

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(c.surface, RoundedCornerShape(16.dp))
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .padding(top = 20.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "选择模型",
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFetch() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Fetch models",
                        tint = c.subtext,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                availableModels.forEach { model ->
                    val isSelected = model == currentModel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model) }
                            .background(if (isSelected) c.accent.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isSelected) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(c.accent))
                        } else {
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(
                            model,
                            color = if (isSelected) c.accent else c.text,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).padding(end = 8.dp, top = 4.dp),
            ) {
                Text(stringResource(R.string.btn_cancel), color = c.subtext, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TopBarButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ── Setup Banner ─────────────────────────────────────────────────────────────
@Composable
private fun SetupBanner(onOpenSettings: () -> Unit) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (c.isDark) Color(0xFF1A1000) else Color(0xFFFFF3E0))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚡", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.setup_prompt),
            color = if (c.isDark) Color(0xFFFFCC80) else Color(0xFFBF6000),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.setup_action), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(
    recommendations: List<String>,
    onTaskSelected: (String) -> Unit,
    onOpenHelp: () -> Unit = {},
) {
    val c = LocalClawColors.current
    val tasks = if (recommendations.isNotEmpty()) recommendations else ExampleTasks

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.15f), Color.Transparent)))
                .border(1.dp, c.accent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text("🦀", fontSize = 34.sp) }

        Spacer(Modifier.height(18.dp))
        Text("MobileClaw", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.app_tagline), color = c.subtext, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))

        // Smart recommendations or example tasks
        Text(
            if (recommendations.isNotEmpty()) "✨  为你推荐" else "✨  试试这些任务",
            color = c.subtext, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        tasks.forEach { task ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.card)
                    .border(1.dp, c.borderActive, RoundedCornerShape(10.dp))
                    .clickable { onTaskSelected(task) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(task, color = c.text.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        // Help link
        Text(
            "❓ 查看使用指南",
            color = c.accent.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenHelp() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ── User Bubble ───────────────────────────────────────────────────────────────
@Composable
private fun UserBubble(text: String, imageBase64: String? = null) {
    val c = LocalClawColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (imageBase64 != null) {
                val bitmap = remember(imageBase64) {
                    runCatching {
                        val bytes = Base64.decode(
                            imageBase64.removePrefix("data:image/jpeg;base64,"),
                            Base64.NO_WRAP,
                        )
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .heightIn(max = 160.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(listOf(c.accent.copy(alpha = 0.8f), c.accent)),
                            RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(text, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

// ── Agent Bubble ──────────────────────────────────────────────────────────────
@Composable
private fun AgentBubble(
    summary: String,
    logLines: List<LogLine>,
    attachments: List<SkillAttachment> = emptyList(),
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
) {
    val c = LocalClawColors.current
    val context = LocalContext.current
    var stepsExpanded by remember { mutableStateOf(false) }
    val isSuccess = logLines.none { it.type == LogType.ERROR }
    // Filter out SUCCESS lines — they duplicate the summary text already shown above the steps panel
    val steps = logLines.filter { it.type != LogType.SUCCESS && (it.text.isNotBlank() || it.imageBase64 != null) }

    Column(modifier = Modifier.fillMaxWidth(0.93f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text("🦀", fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.agent_label), color = c.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            if (!isSuccess) { Spacer(Modifier.width(6.dp)); Text("✗", color = c.red, fontSize = 10.sp) }
        }

        MarkdownText(summary, color = c.text, fontSize = 14.sp, lineHeight = 21.sp)

        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { attachment ->
                    when (attachment) {
                        is SkillAttachment.ImageData -> GeneratedImageCard(attachment)
                        is SkillAttachment.FileData  -> FileAttachmentCard(attachment, context)
                        is SkillAttachment.HtmlData  -> HtmlAttachmentCard(attachment, onOpenHtmlViewer)
                    }
                }
            }
        }

        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))

            val hasThinking = steps.any { it.type == LogType.THINKING }
            val hasError    = steps.any { it.type == LogType.ERROR }
            val headerShape = if (stepsExpanded)
                RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
            else
                RoundedCornerShape(10.dp)

            // Step list header — tap to collapse/expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(c.surface)
                    .border(1.dp, c.border, headerShape)
                    .clickable { stepsExpanded = !stepsExpanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(when { hasError -> "⚠"; hasThinking -> "🧠"; else -> "⚙" }, fontSize = 11.sp)
                Text(
                    buildString {
                        append(stringResource(R.string.steps_label, steps.size))
                        if (hasThinking) append("  ·  含推理过程")
                    },
                    color = c.subtext,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(if (stepsExpanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.6f), fontSize = 9.sp)
            }

            AnimatedVisibility(
                visible = stepsExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(150)),
                exit  = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
            ) {
                val bodyShape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.border, bodyShape)
                        .clip(bodyShape)
                        .background(c.card)
                        .padding(vertical = 4.dp),
                ) {
                    steps.forEachIndexed { index, line ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = c.border.copy(alpha = 0.25f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        LogLineItem(line)
                    }
                }
            }
        }
    }
}

// ── Active Task Bubble ────────────────────────────────────────────────────────
@Composable
private fun ActiveTaskBubble(
    logLines: List<LogLine>,
    streamingToken: String,
    streamingThought: String = "",
    activeAttachments: List<SkillAttachment> = emptyList(),
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
) {
    val c = LocalClawColors.current
    val context = LocalContext.current
    var stepsExpanded by remember { mutableStateOf(false) }
    var thoughtExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "border")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "border",
    )

    val lastAction = logLines.lastOrNull { it.type == LogType.ACTION || it.type == LogType.THINKING }
    val currentLabel = when (lastAction?.type) {
        LogType.ACTION   -> chineseSkillLabel(lastAction.skillId)
        LogType.THINKING -> "思考中"
        else             -> if (logLines.isNotEmpty()) "执行中" else "准备中"
    }

    Column(modifier = Modifier.fillMaxWidth(0.93f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    val rad = angle * PI.toFloat() / 180f
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r = maxOf(size.width, size.height)
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            listOf(Color.Transparent, c.accent.copy(alpha = 0.7f), Color.Transparent),
                            center = Offset(cx + cos(rad) * r, cy + sin(rad) * r),
                        ),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                        style = Stroke(width = stroke),
                    )
                }
                .background(c.card, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .padding(top = 12.dp, bottom = 12.dp, start = 14.dp, end = 14.dp),
        ) {
            // Header: label + spinner + step status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🦀", fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.agent_label),
                    color = c.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(11.dp), color = c.accent, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
                if (logLines.isNotEmpty()) {
                    Text(
                        "第${logLines.size}步 · $currentLabel",
                        color = c.subtext,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (stepsExpanded) "收起 ▲" else "步骤 ▼",
                        color = c.accent.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { stepsExpanded = !stepsExpanded }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                } else {
                    Text("准备中…", color = c.subtext, fontSize = 10.sp, modifier = Modifier.weight(1f))
                }
            }

            // Steps list — collapsed by default
            AnimatedVisibility(
                visible = stepsExpanded && logLines.isNotEmpty(),
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(150)),
                exit  = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = c.border, thickness = 0.5.dp)
                    val bodyShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(bodyShape)
                            .background(c.surface)
                            .padding(vertical = 4.dp),
                    ) {
                        logLines.forEachIndexed { index, line ->
                            if (index > 0) HorizontalDivider(color = c.border.copy(alpha = 0.25f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 10.dp))
                            LogLineItem(line)
                        }
                    }
                }
            }

            // Live streaming thought — DeepSeek style, collapsed by default
            if (streamingThought.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.purple.copy(alpha = 0.06f))
                        .clickable { thoughtExpanded = !thoughtExpanded }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.width(2.dp).height(12.dp).background(c.purple.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                        Text("💭 思考中…", color = c.purple.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
                        Text("${streamingThought.length}字", color = c.purple.copy(alpha = 0.4f), fontSize = 9.sp)
                        Text(if (thoughtExpanded) "▲" else "▼", color = c.purple.copy(alpha = 0.4f), fontSize = 9.sp)
                    }
                    AnimatedVisibility(
                        visible = thoughtExpanded,
                        enter = expandVertically() + fadeIn(tween(100)),
                        exit  = shrinkVertically() + fadeOut(tween(100)),
                    ) {
                        Text(
                            streamingThought.takeLast(400),
                            color = c.purple.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // Streaming response — always visible
            if (streamingToken.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.padding(top = 4.dp).size(width = 2.dp, height = 14.dp).background(c.accent.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                    MarkdownText(text = streamingToken, color = c.text, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
                }
            }

            // Live attachment preview
            if (activeAttachments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeAttachments.forEach { attachment ->
                        when (attachment) {
                            is SkillAttachment.ImageData -> GeneratedImageCard(attachment)
                            is SkillAttachment.FileData  -> FileAttachmentCard(attachment, context)
                            is SkillAttachment.HtmlData  -> HtmlAttachmentCard(attachment, onOpenHtmlViewer)
                        }
                    }
                }
            }
        }
    }
}

// ── Log Line ──────────────────────────────────────────────────────────────────

private fun chineseSkillLabel(skillId: String?): String = when (skillId) {
    "screenshot", "bg_screenshot"           -> "截图"
    "read_screen", "bg_read_screen", "see_screen" -> "读取屏幕"
    "tap"                                   -> "点击"
    "long_click"                            -> "长按"
    "scroll"                                -> "滚动"
    "input_text"                            -> "输入文字"
    "navigate"                              -> "导航"
    "list_apps"                             -> "查看应用列表"
    "web_search"                            -> "网页搜索"
    "fetch_url"                             -> "获取网页"
    "web_browse", "web_content"             -> "浏览网页"
    "web_js"                                -> "执行网页脚本"
    "bg_launch"                             -> "启动应用"
    "bg_stop"                               -> "停止应用"
    "vd_setup"                              -> "配置虚拟屏幕"
    "shell"                                 -> "执行命令"
    "memory"                                -> "记忆管理"
    "permission"                            -> "申请权限"
    "quick_skill", "meta"                   -> "元技能"
    "skill_check", "skill_market"           -> "技能管理"
    "generate_image"                        -> "生成图片"
    "create_file"                           -> "创建文件"
    "create_html"                           -> "创建网页应用"
    "switch_model"                          -> "切换模型"
    "switch_role"                           -> "切换角色"
    "user_config"                           -> "用户配置"
    else                                    -> skillId ?: "工具调用"
}

private val SkillColors = mapOf(
    "screenshot"      to 0xFF6B8EFF,   // blue-purple
    "bg_screenshot"   to 0xFF6B8EFF,
    "read_screen"     to 0xFF8B7CF8,   // purple
    "bg_read_screen"  to 0xFF8B7CF8,
    "see_screen"      to 0xFF8B7CF8,
    "tap"             to 0xFF4ECDC4,   // teal
    "long_click"      to 0xFF4ECDC4,
    "scroll"          to 0xFF4ECDC4,
    "input_text"      to 0xFF4ECDC4,
    "navigate"        to 0xFF56CF86,   // green
    "list_apps"       to 0xFF56CF86,
    "web_search"      to 0xFF64B5F6,   // light blue
    "fetch_url"       to 0xFF64B5F6,
    "web_browse"      to 0xFF64B5F6,
    "web_content"     to 0xFF64B5F6,
    "web_js"          to 0xFF64B5F6,
    "bg_launch"       to 0xFFFF8A65,   // orange
    "bg_stop"         to 0xFFFF8A65,
    "vd_setup"        to 0xFFFF8A65,
    "shell"           to 0xFF4CAF50,   // terminal green
    "memory"          to 0xFFFFD54F,   // yellow
    "permission"      to 0xFFFF7043,   // red-orange
    "quick_skill"     to 0xFFCE93D8,   // lavender
    "meta"            to 0xFFCE93D8,
    "skill_check"     to 0xFFCE93D8,
    "skill_market"    to 0xFFCE93D8,
    "generate_image"  to 0xFFFF80AB,   // pink
    "create_file"     to 0xFF80DEEA,   // cyan
    "create_html"     to 0xFFAED581,   // lime green
)

@Composable
private fun CollapsibleStepRow(
    label: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    labelColor: Color,
    content: @Composable () -> Unit,
) {
    val c = LocalClawColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
            )
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    color = c.subtext.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(if (expanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.4f), fontSize = 9.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(100)),
            exit  = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(100)),
        ) {
            content()
        }
    }
}

@Composable
private fun LogLineItem(line: LogLine) {
    val c = LocalClawColors.current

    when (line.type) {
        LogType.THINKING -> {
            var expanded by remember { mutableStateOf(false) }
            CollapsibleStepRow(
                label = "💭 思考过程",
                summary = "${line.text.length}字",
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = c.purple.copy(alpha = 0.7f),
            ) {
                Text(
                    line.text,
                    color = c.purple.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        LogType.ACTION -> {
            var expanded by remember { mutableStateOf(false) }
            val skillLabel = chineseSkillLabel(line.skillId)
            val accentLong = line.skillId?.let { SkillColors[it] }
            val labelColor = (if (accentLong != null) Color(accentLong) else c.blue).copy(alpha = 0.85f)
            val summary = line.text.take(36).let { if (line.text.length > 36) "$it…" else it }
            CollapsibleStepRow(
                label = "⚙ $skillLabel",
                summary = summary,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = labelColor,
            ) {
                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    if (line.skillId == "shell") ShellCommandCard(line.text)
                    else SkillActionCard(line.text, line.skillId)
                }
            }
        }

        LogType.OBSERVATION -> {
            var expanded by remember { mutableStateOf(false) }
            val hasImage = line.imageBase64 != null
            val label = if (hasImage) "📷 屏幕截图" else "📊 执行结果"
            val summary = when {
                line.text.isNotBlank() -> line.text.take(36).let { if (line.text.length > 36) "$it…" else it }
                hasImage               -> "查看截图"
                else                   -> ""
            }
            CollapsibleStepRow(
                label = label,
                summary = summary,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = c.subtext.copy(alpha = 0.65f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasImage) {
                        val bitmap = remember(line.imageBase64) {
                            runCatching {
                                val clean = line.imageBase64!!
                                    .removePrefix("data:image/jpeg;base64,")
                                    .removePrefix("data:image/png;base64,")
                                val bytes = Base64.decode(clean, Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(0.5.dp, c.border, RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                    if (line.text.isNotBlank()) {
                        Text(line.text, color = c.subtext.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 15.sp)
                    }
                }
            }
        }

        LogType.SUCCESS -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("✓", color = c.green, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.green.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }

        LogType.ERROR -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("✗", color = c.red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.red.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("·", color = c.subtext, fontSize = 11.sp, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.subtext.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkillActionCard(text: String, skillId: String?) {
    val c = LocalClawColors.current
    val accentLong = skillId?.let { SkillColors[it] }
    val accentColor = if (accentLong != null) Color(accentLong) else c.blue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.07f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(accentColor.copy(alpha = 0.8f), RoundedCornerShape(1.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = accentColor.copy(alpha = 0.9f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
        )
    }
}

@Composable
private fun ShellCommandCard(text: String) {
    val c = LocalClawColors.current
    // Extract the command part after the emoji prefix
    val cmdPart = text.removePrefix("⚡ Running: ").trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (c.isDark) Color(0xFF0D1117) else Color(0xFF1C1C1E))
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp)),
    ) {
        // Terminal title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF21262D))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF5F57)))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF28CA41)))
            Spacer(Modifier.width(4.dp))
            Text("shell", color = Color(0xFF8B949E), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        // Command line
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("$", color = Color(0xFF56CF86), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(
                cmdPart,
                color = Color(0xFFE6EDF3),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
            )
        }
    }
}

// ── Attachment Cards ──────────────────────────────────────────────────────────

@Composable
private fun GeneratedImageCard(attachment: SkillAttachment.ImageData) {
    val c = LocalClawColors.current
    val bitmap = remember(attachment.base64) {
        runCatching {
            val clean = attachment.base64
                .removePrefix("data:image/png;base64,")
                .removePrefix("data:image/jpeg;base64,")
            val bytes = Base64.decode(clean, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.border, RoundedCornerShape(12.dp))
                .background(c.card),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = attachment.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clip(RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp)),
                contentScale = ContentScale.FillWidth,
            )
            if (!attachment.prompt.isNullOrBlank()) {
                Text(
                    attachment.prompt,
                    color = c.subtext,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FileAttachmentCard(attachment: SkillAttachment.FileData, context: android.content.Context) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .background(c.card)
            .clickable { openFileAttachment(context, attachment) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.blue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text(mimeTypeEmoji(attachment.mimeType), fontSize = 18.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${formatFileSize(attachment.sizeBytes)} · ${attachment.mimeType}",
                color = c.subtext,
                fontSize = 11.sp,
            )
        }
        Text("打开", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun mimeTypeEmoji(mimeType: String): String = when {
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

private fun openFileAttachment(context: android.content.Context, attachment: SkillAttachment.FileData) {
    val file = java.io.File(attachment.path)
    if (!file.exists()) {
        android.widget.Toast.makeText(context, "文件不存在: ${attachment.name}", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    // Build a content URI via FileProvider
    val uri = runCatching {
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    if (uri == null) {
        android.widget.Toast.makeText(context, "无法读取文件", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

    // Try ACTION_VIEW with a chooser first
    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType)
        addFlags(flags)
    }
    val chooser = android.content.Intent.createChooser(viewIntent, "打开 ${attachment.name}")

    val hasApp = context.packageManager.queryIntentActivities(viewIntent, 0).isNotEmpty()
    if (hasApp) {
        runCatching { context.startActivity(chooser) }
        return
    }

    // No app found for specific MIME type — fall back to generic chooser with */*
    val genericIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*")
        addFlags(flags)
    }
    val genericChooser = android.content.Intent.createChooser(genericIntent, "打开 ${attachment.name}")

    val hasGenericApp = context.packageManager.queryIntentActivities(genericIntent, 0).isNotEmpty()
    if (hasGenericApp) {
        runCatching { context.startActivity(genericChooser) }
        return
    }

    // Final fallback: share/send
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = attachment.mimeType.ifBlank { "*/*" }
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(flags)
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(shareIntent, "分享 ${attachment.name}"))
    }.onFailure {
        android.widget.Toast.makeText(context, "没有找到可以打开此文件的应用", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun HtmlAttachmentCard(
    attachment: SkillAttachment.HtmlData,
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
) {
    val c = LocalClawColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .background(c.card)
            .clickable { onOpenHtmlViewer(attachment) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Text("🌐", fontSize = 18.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("HTML 页面 · 点击查看", color = c.subtext, fontSize = 11.sp)
        }
        Text("查看", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L        -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    else                 -> "${bytes / (1024 * 1024)} MB"
}

// ── Input Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun InputBar(
    input: String,
    isRunning: Boolean,
    attachedImageBase64: String?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachImage: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    val c = LocalClawColors.current
    val sendEnabled = (input.isNotBlank() || attachedImageBase64 != null) && !isRunning
    val buttonAlpha by animateFloatAsState(if (sendEnabled) 1f else 0.4f, label = "btn")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.bg, c.surface)))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (attachedImageBase64 != null) {
            val bitmap = remember(attachedImageBase64) {
                runCatching {
                    val bytes = Base64.decode(
                        attachedImageBase64.removePrefix("data:image/jpeg;base64,"),
                        Base64.NO_WRAP,
                    )
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (bitmap != null) {
                Box(modifier = Modifier.wrapContentSize()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 72.dp, height = 56.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(c.card)
                            .border(1.dp, c.border, CircleShape)
                            .clickable { onRemoveImage() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            IconButton(
                onClick = onAttachImage,
                enabled = !isRunning,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach image",
                    tint = if (isRunning) c.subtext.copy(alpha = 0.3f) else c.subtext,
                    modifier = Modifier.size(20.dp),
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        if (isRunning) stringResource(R.string.input_placeholder_running)
                        else stringResource(R.string.input_placeholder),
                        color = c.subtext,
                        fontSize = 13.sp,
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent.copy(alpha = 0.7f),
                    unfocusedBorderColor = c.border,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                    cursorColor = c.accent,
                    disabledBorderColor = c.border,
                    disabledTextColor = c.subtext,
                    focusedContainerColor = c.card,
                    unfocusedContainerColor = c.card,
                    disabledContainerColor = c.card,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) c.red.copy(alpha = 0.15f)
                        else if (sendEnabled) c.accent
                        else c.card
                    )
                    .clickable(enabled = true) { if (isRunning) onStop() else if (sendEnabled) onSend() },
                contentAlignment = Alignment.Center,
            ) {
                if (isRunning) {
                    Icon(Icons.Default.Close, contentDescription = "Stop", tint = c.red, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (sendEnabled) Color.White else c.subtext.copy(alpha = buttonAlpha),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
