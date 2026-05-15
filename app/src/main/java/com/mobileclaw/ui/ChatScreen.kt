package com.mobileclaw.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.GetContent
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import com.mobileclaw.skill.SkillAttachment
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.mobileclaw.str

private val ExampleTasks = listOf(
    str(R.string.chat_e1b216),
    str(R.string.chat_df5694),
    str(R.string.chat_1bf8d4),
    str(R.string.chat_e8ab7f),
    str(R.string.chat_07f7c7),
)

// ── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: MainUiState,
    onSendGoal: (String) -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSkillManager: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onAttachImage: (String?) -> Unit = {},
    onSendImage: (String, String) -> Unit = { image, _ -> onAttachImage(image) },
    onAttachFile: (FileAttachment?) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onModelChange: (String) -> Unit = {},
    onFetchModels: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onRenameSession: (sessionId: String, title: String) -> Unit = { _, _ -> },
    onOpenDesktop: () -> Unit = {},
    onSwitchRole: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
    classicMode: Boolean = false,
) {
    val c = LocalClawColors.current
    val runState = uiState.currentRunState
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = runState.messages.size + if (runState.isRunning) 1 else 0
    val context = LocalContext.current
    val sessionTitle = remember(uiState.currentSessionId, uiState.sessions) {
        uiState.sessions.find { it.id == uiState.currentSessionId }?.title ?: "MobileClaw"
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(sessionTitle) { mutableStateOf(sessionTitle) }
    var showModelPicker by remember { mutableStateOf(false) }

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
                val isText = mimeType.startsWith("text/") ||
                    mimeType == "application/json" || mimeType == "application/xml" ||
                    mimeType == "application/javascript" || mimeType == "application/x-sh"
                if (isText) {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.use { it.bufferedReader().readText() } ?: return@runCatching
                    onAttachFile(FileAttachment(fileName, content, isText = true, mimeType = mimeType))
                } else if (mimeType.startsWith("image/")) {
                    val base64 = context.contentResolver.openInputStream(uri)?.use { stream ->
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
                    if (base64 != null) onAttachImage(base64)
                } else {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching
                    val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    onAttachFile(FileAttachment(fileName, base64Content, isText = false, mimeType = mimeType))
                }
            }
        }
    }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showStickerSearch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Scroll to bottom. Split into two effects:
    // 1) item count changes → animated scroll (new message completed)
    // 2) log lines / streaming → instant scroll to avoid jump artifacts from overlapping animations
    // scrollOffset = 3000 ensures the BOTTOM of the last item is visible (clamped to max extent).
    var lastScrolledCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            if (lastScrolledCount < 0 || itemCount - lastScrolledCount > 1) {
                listState.scrollToItem(itemCount - 1, scrollOffset = 3000)
            } else {
                listState.animateScrollToItem(itemCount - 1, scrollOffset = 3000)
            }
            lastScrolledCount = itemCount
        } else {
            lastScrolledCount = 0
        }
    }
    val scrollKey = remember(runState.streamingToken.length / 40, runState.streamingThought.length / 40) { Unit }
    LaunchedEffect(runState.activeLogLines.size, scrollKey) {
        if (itemCount > 0 && runState.isRunning) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= itemCount - 2) {
                listState.scrollToItem(itemCount - 1, scrollOffset = 3000)
            }
        }
    }

    // Load more history when user scrolls to the very top
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleIndex) {
        if (firstVisibleIndex == 0 && uiState.historyHasMore && !uiState.historyLoading && !runState.isRunning) {
            onLoadMoreHistory()
        }
    }

    // Step detail bottom sheet state
    var selectedStepLog by remember { mutableStateOf<LogLine?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(c.bg).imePadding()) {
        if (!classicMode) {
            TopBar(
                sessionTitle = sessionTitle,
                onOpenDrawer = onOpenDrawer,
                onRenameSession = { showRenameDialog = true },
                onOpenDesktop = onOpenDesktop,
            )
        }

        if (!uiState.isConfigured && runState.messages.isEmpty()) {
            SetupBanner(onOpenSettings)
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (runState.messages.isEmpty() && !runState.isRunning) {
                    item {
                        EmptyState(
                            recommendations = uiState.recommendations,
                            onTaskSelected = { task -> onSendGoal(task.substringAfter(" ")) },
                            onOpenHelp = onOpenHelp,
                        )
                    }
                }
                // History loading indicator at top
                if (uiState.historyLoading) {
                    item(key = "history_loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.subtext)
                        }
                    }
                } else if (uiState.historyHasMore) {
                    item(key = "history_hint") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            Text(str(R.string.chat_b721d3), fontSize = 11.sp, color = c.subtext.copy(alpha = 0.4f))
                        }
                    }
                }
                itemsIndexed(runState.messages, key = { idx, _ -> "msg_$idx" }) { _, msg ->
                    when (msg.role) {
                        MessageRole.USER  -> UserBubble(msg.text, msg.imageBase64)
                        MessageRole.AGENT -> {
                            val messageRole = remember(msg.senderRoleId, msg.senderRoleName, msg.senderRoleAvatar, uiState.availableRoles, uiState.currentRole) {
                                msg.senderDisplayRole(uiState.availableRoles, uiState.currentRole)
                            }
                            if (msg.text.isBlank() && msg.logLines.isEmpty() && msg.attachments.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(0.93f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AgentMessageHeader(
                                        role = messageRole,
                                        model = uiState.currentModel,
                                        onPickModel = { showModelPicker = true },
                                        onSwitchRole = onSwitchRole,
                                    )
                                    AttachmentBubble(
                                        attachments = msg.attachments,
                                        onOpenHtmlViewer = onOpenHtmlViewer,
                                        onOpenBrowser = onOpenBrowser,
                                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AgentBubble(
                                        summary = msg.text,
                                        logLines = msg.logLines,
                                        currentRole = messageRole,
                                        currentModel = uiState.currentModel,
                                        onSwitchRole = onSwitchRole,
                                        onPickModel = { showModelPicker = true },
                                        onOpenHtmlViewer = onOpenHtmlViewer,
                                        onOpenBrowser = onOpenBrowser,
                                        onSendGoal = onSendGoal,
                                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                        onSelectStep = { selectedStepLog = it },
                                    )
                                    if (msg.attachments.isNotEmpty()) {
                                        AttachmentBubble(
                                            attachments = msg.attachments,
                                            onOpenHtmlViewer = onOpenHtmlViewer,
                                            onOpenBrowser = onOpenBrowser,
                                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (runState.isRunning) {
                    item {
                        ActiveTaskBubble(
                            logLines = runState.activeLogLines,
                            streamingToken = runState.streamingToken,
                            streamingThought = runState.streamingThought,
                            currentRole = uiState.currentRole,
                            currentModel = uiState.currentModel,
                            onPickModel = { showModelPicker = true },
                            onOpenHtmlViewer = onOpenHtmlViewer,
                            onOpenBrowser = onOpenBrowser,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                            onSelectStep = { selectedStepLog = it },
                            onSendGoal = onSendGoal,
                        )
                    }
                    itemsIndexed(runState.activeAttachments, key = { idx, _ -> "active_attachment_$idx" }) { _, attachment ->
                        AttachmentBubble(
                            attachments = listOf(attachment),
                            onOpenHtmlViewer = onOpenHtmlViewer,
                            onOpenBrowser = onOpenBrowser,
                            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(c.surface.copy(alpha = 0.85f), Color.Transparent))),
            )
        }

        InputBar(
            input = input,
            isRunning = runState.isRunning,
            supportsMultimodal = uiState.supportsMultimodal,
            attachedImageBase64 = uiState.inputImageBase64,
            attachedFile = uiState.inputFileAttachment,
            showAttachMenu = showAttachMenu,
            onInputChange = { input = it },
            onSend = {
                if (input.isNotBlank() || uiState.inputImageBase64 != null || uiState.inputFileAttachment != null) {
                    onSendGoal(input.trim())
                    input = ""
                }
            },
            onStop = onStop,
            onAttachClick = { showAttachMenu = !showAttachMenu },
            onPickImage = { showAttachMenu = false; imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            onPickFile = { showAttachMenu = false; filePicker.launch("*/*") },
            onPickSticker = { showAttachMenu = false; showStickerSearch = true },
            onRemoveImage = { onAttachImage(null) },
            onRemoveFile = { onAttachFile(null) },
        )
    }

    if (showStickerSearch) {
        StickerSearchSheet(
            onDismiss = { showStickerSearch = false },
            onSelected = { file ->
                scope.launch {
                    val dataUri = stickerFileToDataUri(file)
                    if (dataUri != null) {
                        onSendImage(
                            dataUri,
                            "用户发送了一张表情包：${file.name}。请结合这张表情图片和当前聊天上下文自然回应；不要把这段提示展示或复述给用户。",
                        )
                    } else {
                        Toast.makeText(context, str(R.string.sticker_download_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    if (showModelPicker) {
        LaunchedEffect(showModelPicker, uiState.availableModels.size) {
            if (uiState.availableModels.isEmpty()) onFetchModels()
        }
        ModelPickerDialog(
            currentModel = uiState.currentModel,
            availableModels = uiState.availableModels,
            loading = uiState.modelsLoading,
            onSelect = { onModelChange(it); showModelPicker = false },
            onFetch = onFetchModels,
            onDismiss = { showModelPicker = false },
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(str(R.string.chat_3e654d)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameSession(uiState.currentSessionId, renameText.trim())
                    }
                    showRenameDialog = false
                }) { Text(str(R.string.role_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(str(R.string.btn_cancel)) }
            },
        )
    }

    selectedStepLog?.let { step ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedStepLog = null },
            sheetState = sheetState,
        ) {
            val c = LocalClawColors.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(str(R.string.chat_d0569b), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text)
                Spacer(Modifier.height(8.dp))
                val displayLines = step.details.ifEmpty {
                    if (step.text.isNotBlank()) listOf(step.text) else listOf(str(R.string.chat_cbf93e))
                }
                displayLines.forEach { detail ->
                    Text(
                        detail,
                        fontSize = 10.sp,
                        color = c.subtext.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.surface)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

// ── Top Bar ──────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    sessionTitle: String,
    onOpenDrawer: () -> Unit,
    onRenameSession: () -> Unit,
    onOpenDesktop: () -> Unit,
    classicMode: Boolean = false,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = c.subtext, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier.weight(1f).clickable(onClick = onRenameSession),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    sessionTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (!classicMode) {
                IconButton(onClick = onOpenDesktop) {
                    Icon(Icons.Outlined.GridView, contentDescription = null, tint = c.subtext, modifier = Modifier.size(22.dp))
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerDialog(
    currentModel: String,
    availableModels: List<String>,
    loading: Boolean,
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
                .padding(top = 16.dp, bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    str(R.string.chat_select),
                    color = c.text,
                    fontSize = 15.sp,
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
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = c.subtext)
                    }
                }
                if (!loading && availableModels.isEmpty()) {
                    Text(
                        str(R.string.status_not_configured),
                        color = c.subtext,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                availableModels.forEach { model ->
                    val isSelected = model == currentModel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(model) }
                            .background(if (isSelected) c.accent.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                Text(str(R.string.btn_cancel), color = c.subtext, fontSize = 13.sp)
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
            .background(if (c.isDark) Color(0xFF111111) else Color(0xFFF0F0EE))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClawSymbolIcon("battery", tint = c.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.setup_prompt),
            color = c.text,
            fontSize = 12.sp,
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
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.15f), Color.Transparent)))
                .border(1.dp, c.accent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { ClawSymbolIcon("profile", tint = c.accent, modifier = Modifier.size(28.dp)) }

        Spacer(Modifier.height(14.dp))
        Text("MobileClaw", color = c.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        Spacer(Modifier.height(4.dp))
        Text(str(R.string.app_tagline), color = c.subtext, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        // Smart recommendations or example tasks
        Text(
            if (recommendations.isNotEmpty()) str(R.string.chat_b8181c) else str(R.string.chat_72e47b),
            color = c.subtext, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        tasks.forEach { task ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.card)
                    .border(1.dp, c.borderActive, RoundedCornerShape(10.dp))
                    .clickable { onTaskSelected(task) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(task, color = c.text.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }

        Spacer(Modifier.height(14.dp))
        // Help link
        Text(
            str(R.string.chat_c5fab5),
            color = c.accent.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onOpenHelp() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// ── User Bubble ───────────────────────────────────────────────────────────────
@Composable
private fun UserBubble(text: String, imageBase64: String? = null) {
    val c = LocalClawColors.current
    var showFullscreen by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (imageBase64 != null) {
                val bitmap = remember(imageBase64) {
                    runCatching {
                        val clean = stripDataUriPrefix(imageBase64)
                        val bytes = Base64.decode(clean, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }.getOrNull()
                }
                if (bitmap != null) {
                    val ratio = remember(bitmap) {
                        (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.8f)
                    }
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showFullscreen = true },
                        contentScale = ContentScale.Fit,
                    )
                    if (showFullscreen) {
                        FullscreenImageDialog(bitmap = bitmap, onDismiss = { showFullscreen = false })
                    }
                }
            }
            if (text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF080808), RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                        .border(1.dp, if (c.isDark) c.border else Color.Transparent, RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

// ── Agent Bubble ──────────────────────────────────────────────────────────────
private fun ChatMessage.senderDisplayRole(
    availableRoles: List<Role>,
    fallback: Role,
): Role {
    availableRoles.firstOrNull { it.id == senderRoleId }?.let { return it }
    if (senderRoleId.isBlank() && senderRoleName.isBlank() && senderRoleAvatar.isBlank()) return fallback
    return fallback.copy(
        id = senderRoleId.ifBlank { fallback.id },
        name = senderRoleName.ifBlank { fallback.name },
        avatar = senderRoleAvatar.ifBlank { fallback.avatar },
    )
}

@Composable
private fun AgentMessageHeader(
    role: Role,
    model: String,
    onSwitchRole: () -> Unit = {},
    onPickModel: () -> Unit = {},
    showModel: Boolean = true,
) {
    val c = LocalClawColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        GradientAvatar(
            emoji = role.avatar,
            size = 34.dp,
            color = c.accent,
            modifier = Modifier.clickable { onSwitchRole() },
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = role.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            if (showModel && model.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = model.substringAfterLast("/").take(20) + " ▾",
                    fontSize = 10.sp,
                    color = c.subtext,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onPickModel() },
                )
            }
        }
    }
}

private val quickReplyPattern = Regex("""\[\[(.+?)]]""")

private fun parseQuickReplies(text: String): Pair<String, List<String>> {
    val options = quickReplyPattern.findAll(text).flatMap { it.groupValues[1].split("|").map(String::trim) }.toList()
    val clean = text.replace(quickReplyPattern, "").trimEnd()
    return clean to options
}

private val uiFencePattern = Regex("```ui[\\s\\S]*?```", RegexOption.IGNORE_CASE)

private fun textPreview(text: String, limit: Int = 700): String {
    val withoutUi = text.replace(uiFencePattern, "[交互内容已折叠，点“更多”查看]").trim()
    return if (withoutUi.length > limit) withoutUi.take(limit).trimEnd() + "…" else withoutUi
}

@Composable
private fun AgentBubble(
    summary: String,
    logLines: List<LogLine>,
    attachments: List<SkillAttachment> = emptyList(),
    currentRole: Role = Role.DEFAULT,
    currentModel: String = "",
    onSwitchRole: () -> Unit = {},
    onPickModel: () -> Unit = {},
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onSendGoal: (String) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onSelectStep: (LogLine) -> Unit = {},
) {
    val c = LocalClawColors.current
    val context = LocalContext.current
    var stepsExpanded by remember { mutableStateOf(false) }
    var summaryExpanded by remember(summary) { mutableStateOf(false) }
    val isSuccess = logLines.none { it.type == LogType.ERROR }
    val steps = logLines.filter { it.type != LogType.SUCCESS && (it.text.isNotBlank() || it.imageBase64 != null) }
    val (cleanSummary, quickReplies) = remember(summary) { parseQuickReplies(summary) }
    val shouldCollapseSummary = cleanSummary.length > 900 || uiFencePattern.containsMatchIn(cleanSummary)
    val visibleSummary = if (shouldCollapseSummary && !summaryExpanded) textPreview(cleanSummary) else cleanSummary

    Column(modifier = Modifier.fillMaxWidth(0.93f)) {
        // Role header: avatar + name + model chip
        Row(verticalAlignment = Alignment.CenterVertically) {
            AgentMessageHeader(
                role = currentRole,
                model = currentModel,
                onSwitchRole = onSwitchRole,
                onPickModel = onPickModel,
            )
            if (!isSuccess) {
                Spacer(Modifier.width(6.dp))
                Text("✗", color = c.red, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }

        if (visibleSummary.isNotBlank()) {
            MarkdownText(visibleSummary, color = c.text, fontSize = 13.sp, lineHeight = 18.sp, onAction = onSendGoal)
        }

        if (shouldCollapseSummary) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (summaryExpanded) "收起" else "更多",
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { summaryExpanded = !summaryExpanded }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            )
        }

        if (quickReplies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickReplies.forEach { reply ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.surface)
                            .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                            .clickable { onSendGoal(reply) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(reply, fontSize = 12.sp, color = c.text, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { attachment ->
                    when (attachment) {
                        is SkillAttachment.ImageData            -> GeneratedImageCard(attachment)
                        is SkillAttachment.FileData             -> FileAttachmentCard(attachment, context)
                        is SkillAttachment.HtmlData             -> HtmlAttachmentCard(attachment, onOpenHtmlViewer)
                        is SkillAttachment.WebPage              -> WebPageCard(attachment, onOpenBrowser)
                        is SkillAttachment.SearchResults        -> SearchResultsCard(attachment, onOpenBrowser)
                        is SkillAttachment.AccessibilityRequest -> AccessibilityCard(attachment, onOpenAccessibilitySettings)
                        is SkillAttachment.FileList             -> FileListCard(attachment, context)
                    }
                }
            }
        }

        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))

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
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(when { hasError -> "⚠"; hasThinking -> "🧠"; else -> "⚙" }, fontSize = 11.sp)
                Text(
                    buildString {
                        append(stringResource(R.string.steps_label, steps.size))
                        if (hasThinking) append(str(R.string.chat_3d188f))
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
                        LogLineItem(line, onSelectStep = onSelectStep)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentBubble(
    attachments: List<SkillAttachment>,
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(0.93f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            when (attachment) {
                is SkillAttachment.ImageData            -> GeneratedImageCard(attachment)
                is SkillAttachment.FileData             -> FileAttachmentCard(attachment, context)
                is SkillAttachment.HtmlData             -> HtmlAttachmentCard(attachment, onOpenHtmlViewer)
                is SkillAttachment.WebPage              -> WebPageCard(attachment, onOpenBrowser)
                is SkillAttachment.SearchResults        -> SearchResultsCard(attachment, onOpenBrowser)
                is SkillAttachment.AccessibilityRequest -> AccessibilityCard(attachment, onOpenAccessibilitySettings)
                is SkillAttachment.FileList             -> FileListCard(attachment, context)
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
    currentRole: Role = Role.DEFAULT,
    currentModel: String = "",
    onPickModel: () -> Unit = {},
    onOpenHtmlViewer: (SkillAttachment.HtmlData) -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit = {},
    onSelectStep: (LogLine) -> Unit = {},
    onSendGoal: (String) -> Unit = {},
) {
    val c = LocalClawColors.current
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
        LogType.THINKING -> str(R.string.chat_dc269a)
        else             -> if (logLines.isNotEmpty()) str(R.string.chat_46e386) else str(R.string.chat_f76540)
    }

    Column(modifier = Modifier.fillMaxWidth(0.93f)) {
        // Role header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            GradientAvatar(emoji = currentRole.avatar, size = 34.dp, color = c.accent)
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currentRole.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = currentModel.substringAfterLast("/").take(20) + " ▾",
                    fontSize = 10.sp,
                    color = c.subtext,
                    modifier = Modifier.clickable { onPickModel() },
                )
            }
        }

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
                .background(c.card, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
        ) {
            // Header: label + spinner + step status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ClawSymbolIcon("profile", tint = c.accent, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "MobileClaw",
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
                        str(R.string.step_label, logLines.size, currentLabel),
                        color = c.subtext,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (stepsExpanded) str(R.string.chat_c4b206) else str(R.string.chat_900d90),
                        color = c.accent.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { stepsExpanded = !stepsExpanded }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                } else {
                    Text(str(R.string.chat_dcfb68), color = c.subtext, fontSize = 10.sp, modifier = Modifier.weight(1f))
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
                            LogLineItem(line, onSelectStep = onSelectStep)
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
                        Text(str(R.string.chat_1aa9e9), color = c.purple.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
                        Text(str(R.string.chars_count, streamingThought.length), color = c.purple.copy(alpha = 0.4f), fontSize = 9.sp)
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
                    MarkdownText(text = streamingToken, color = c.text, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f), onAction = onSendGoal)
                }
            }

        }
    }
}

// ── Log Line ──────────────────────────────────────────────────────────────────

private fun chineseSkillLabel(skillId: String?): String = when (skillId) {
    "screenshot", "bg_screenshot"           -> str(R.string.chat_369abf)
    "read_screen", "bg_read_screen", "see_screen" -> str(R.string.chat_167c7b)
    "tap"                                   -> str(R.string.chat_tap)
    "long_click"                            -> str(R.string.chat_3466d5)
    "scroll"                                -> str(R.string.chat_c76c60)
    "input_text"                            -> str(R.string.chat_input)
    "navigate"                              -> str(R.string.chat_056f2d)
    "list_apps"                             -> str(R.string.chat_8532c7)
    "web_search"                            -> str(R.string.chat_0128ba)
    "fetch_url"                             -> str(R.string.chat_5753d4)
    "web_browse", "web_content"             -> str(R.string.chat_81b7cd)
    "web_js"                                -> str(R.string.chat_495d05)
    "bg_launch"                             -> str(R.string.chat_753cdb)
    "bg_stop"                               -> str(R.string.chat_stop)
    "vd_setup"                              -> str(R.string.chat_287e7d)
    "shell"                                 -> str(R.string.chat_24cc0d)
    "memory"                                -> str(R.string.chat_2c567a)
    "permission"                            -> str(R.string.chat_7c1b38)
    "quick_skill", "meta"                   -> str(R.string.chat_81c3f4)
    "skill_check", "skill_market"           -> str(R.string.chat_735ac6)
    "generate_image"                        -> str(R.string.chat_f8d248)
    "ui_builder"                            -> str(R.string.home_2d20d5)
    "app_manager"                           -> str(R.string.drawer_apps)
    "create_file"                           -> str(R.string.chat_create)
    "create_html"                           -> str(R.string.chat_create_2)
    "switch_model"                          -> str(R.string.chat_756af3)
    "switch_role"                           -> str(R.string.chat_a6df2e)
    "user_config"                           -> str(R.string.drawer_user_config)
    else                                    -> skillId ?: str(R.string.chat_850b4e)
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
    "ui_builder"      to 0xFF2563EB,   // native page blue
    "app_manager"     to 0xFFFF9F40,   // app orange
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
    onDetail: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalClawColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Main area: tap to open detail sheet (if available), fallback to toggle
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { if (onDetail != null) onDetail() else onToggle() }
                    .padding(start = 9.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    label,
                    color = labelColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                if (summary.isNotBlank()) {
                    Text(
                        summary,
                        color = c.subtext.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Expand/collapse toggle (dedicated tap area)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = c.subtext.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp),
                )
            }
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
private fun LogLineItem(line: LogLine, onSelectStep: (LogLine) -> Unit = {}) {
    val c = LocalClawColors.current

    when (line.type) {
        LogType.THINKING -> {
            var expanded by remember { mutableStateOf(false) }
            CollapsibleStepRow(
                label = stringResource(R.string.chat_f2b9df),
                summary = str(R.string.chars_count, line.text.length),
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = c.purple.copy(alpha = 0.7f),
                onDetail = { onSelectStep(line) },
            ) {
                Text(
                    line.text,
                    color = c.purple.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
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
                onDetail = { onSelectStep(line) },
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
            val label = if (hasImage) stringResource(R.string.chat_a3d484) else stringResource(R.string.chat_173c2c)
            val summary = when {
                line.text.isNotBlank() -> line.text.take(36).let { if (line.text.length > 36) "$it…" else it }
                hasImage               -> stringResource(R.string.chat_b3e19e)
                else                   -> ""
            }
            CollapsibleStepRow(
                label = label,
                summary = summary,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = c.subtext.copy(alpha = 0.65f),
                onDetail = { onSelectStep(line) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasImage) {
                        val bitmap = remember(line.imageBase64) {
                            runCatching {
                                val clean = stripDataUriPrefix(line.imageBase64!!)
                                val bytes = Base64.decode(clean, Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        var showStepFullscreen by remember { mutableStateOf(false) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(0.5.dp, c.border, RoundedCornerShape(6.dp))
                                    .clickable { showStepFullscreen = true },
                                contentScale = ContentScale.FillWidth,
                            )
                            if (showStepFullscreen) {
                                FullscreenImageDialog(bitmap = bitmap, onDismiss = { showStepFullscreen = false })
                            }
                        }
                    }
                    if (line.text.isNotBlank()) {
                        Text(line.text, color = c.subtext.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            }
        }

        LogType.SUCCESS -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("✓", color = c.green, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.green.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }

        LogType.ERROR -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("✗", color = c.red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.red.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("·", color = c.subtext, fontSize = 11.sp, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.subtext.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
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
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(top = 7.dp, bottom = 7.dp, end = 8.dp),
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
            .background(if (c.isDark) Color(0xFF0E0E0E) else Color(0xFFF6F6F4))
            .border(1.dp, c.border, RoundedCornerShape(6.dp)),
    ) {
        // Terminal title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (c.isDark) Color(0xFF151515) else Color(0xFFEDEDEA))
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(c.subtext.copy(alpha = 0.45f)))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(c.subtext.copy(alpha = 0.30f)))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.70f)))
            Spacer(Modifier.width(4.dp))
            Text("shell", color = Color(0xFF8B949E), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        // Command line
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("$", color = c.accent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Text(
                cmdPart,
                color = c.text,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
            )
        }
    }
}

// ── Attachment Cards ──────────────────────────────────────────────────────────

@Composable
private fun FullscreenImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(onClick = onDismiss),
                contentScale = ContentScale.Fit,
            )
            // Save button (bottom-center)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable {
                            saveBitmapToGallery(context, bitmap)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(R.string.chat_f235e7), fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(stringResource(R.string.btn_close), fontSize = 14.sp, color = Color.White)
                }
            }
        }
    }
}

private fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap) {
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

@Composable
private fun GeneratedImageCard(attachment: SkillAttachment.ImageData) {
    val c = LocalClawColors.current
    val bitmap = remember(attachment.base64) {
        runCatching {
            val clean = stripDataUriPrefix(attachment.base64)
            val bytes = Base64.decode(clean, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    var showFullscreen by remember { mutableStateOf(false) }
    if (bitmap != null) {
        val ratio = remember(bitmap) {
            (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.8f)
        }
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clickable { showFullscreen = true },
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = attachment.prompt,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit,
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
        if (showFullscreen) {
            FullscreenImageDialog(bitmap = bitmap, onDismiss = { showFullscreen = false })
        }
    }
}

@Composable
private fun FileAttachmentCard(attachment: SkillAttachment.FileData, context: android.content.Context) {
    val c = LocalClawColors.current
    val isImage = isImageFileAttachment(attachment)

    if (isImage) {
        ImageAttachmentCard(attachment, context, c)
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .background(c.card)
                .clickable { openFileAttachment(context, attachment) }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text(mimeTypeEmoji(attachment.mimeType), fontSize = 16.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "${formatFileSize(attachment.sizeBytes)} · ${attachment.mimeType}",
                    color = c.subtext,
                    fontSize = 11.sp,
                )
            }
            Text(stringResource(R.string.perm_open), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FileListCard(attachment: SkillAttachment.FileList, context: android.content.Context) {
    val c = LocalClawColors.current
    var expanded by remember(attachment.files, attachment.directory) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
            .background(c.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.text.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) { Text("≡", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    attachment.directory.ifBlank { str(R.string.group_label_file_list) },
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val firstNames = attachment.files.take(2).joinToString(" · ") { it.name }
                Text(
                    if (firstNames.isBlank()) str(R.string.files_count, attachment.files.size) else firstNames,
                    color = c.subtext,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(str(R.string.files_count, attachment.files.size), fontSize = 11.sp, color = c.subtext)
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = c.subtext,
                modifier = Modifier.size(17.dp),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(120)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        ) {
            Column {
                HorizontalDivider(color = c.border.copy(alpha = 0.55f), thickness = 0.5.dp)
                attachment.files.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider(color = c.border.copy(alpha = 0.35f), thickness = 0.5.dp, modifier = Modifier.padding(start = 54.dp))
                    val fileAttachment = SkillAttachment.FileData(entry.path, entry.name, entry.mimeType, entry.sizeBytes)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openFileAttachment(context, fileAttachment) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.text.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center,
                        ) { Text(mimeTypeEmoji(entry.mimeType), fontSize = 15.sp) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatFileSize(entry.sizeBytes), color = c.subtext, fontSize = 10.sp)
                        }
                        Text(stringResource(R.string.perm_open), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentCard(
    attachment: SkillAttachment.FileData,
    context: android.content.Context,
    c: ClawColors,
) {
    val bitmap by produceState<Bitmap?>(null, attachment.path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            decodeFileAttachmentBitmap(context, attachment, maxPx = 1200)
        }
    }
    var showFullscreen by remember { mutableStateOf(false) }
    val isSticker = isStickerFileAttachment(attachment)
    val maxThumbWidth = if (isSticker) 144.dp else 220.dp

    Box(
        modifier = Modifier
            .widthIn(max = maxThumbWidth)
            .clip(RoundedCornerShape(14.dp))
            .clickable { if (bitmap != null) showFullscreen = true else openFileAttachment(context, attachment) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            val ratio = remember(bitmap) {
                (bitmap!!.width.toFloat() / bitmap!!.height.coerceAtLeast(1).toFloat()).coerceIn(0.55f, 1.8f)
            }
            Image(
                bitmap = bitmap!!.asImageBitmap(),
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
        FullscreenImageDialog(bitmap = bitmap!!, onDismiss = { showFullscreen = false })
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
    val uri = if (attachment.path.startsWith("content://")) {
        android.net.Uri.parse(attachment.path)
    } else {
        val file = java.io.File(attachment.path)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, str(R.string.file_not_found, attachment.name), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Build a content URI via FileProvider
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse { e ->
            android.widget.Toast.makeText(context, str(R.string.share_failed, e.message?.take(80) ?: ""), android.widget.Toast.LENGTH_LONG).show()
            return
        }
    }

    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION

    // Try ACTION_VIEW with a chooser first
    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType)
        addFlags(flags)
    }
    val chooser = android.content.Intent.createChooser(viewIntent, str(R.string.open_file, attachment.name))

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
    val genericChooser = android.content.Intent.createChooser(genericIntent, str(R.string.open_file, attachment.name))

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
        context.startActivity(android.content.Intent.createChooser(shareIntent, str(R.string.share_file, attachment.name)))
    }.onFailure {
        android.widget.Toast.makeText(context, str(R.string.chat_none), android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun decodeFileAttachmentBitmap(
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
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { ClawSymbolIcon("web", tint = c.accent, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(str(R.string.chat_411604), color = c.subtext, fontSize = 11.sp)
        }
        Text(str(R.string.chat_607e7a), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SearchResultsCard(
    attachment: SkillAttachment.SearchResults,
    onOpenBrowser: (String) -> Unit = {},
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .background(c.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ClawSymbolIcon("search", tint = c.subtext, modifier = Modifier.size(15.dp))
            Text(
                attachment.query,
                color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${attachment.engine} · ${attachment.pages.size}",
                color = c.subtext, fontSize = 10.sp,
            )
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
        attachment.pages.forEachIndexed { index, page ->
            val domain = runCatching { java.net.URI(page.url).host ?: page.url }.getOrDefault(page.url)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBrowser(page.url) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${index + 1}",
                    color = c.subtext, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        page.title.ifBlank { domain },
                        color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(domain, color = c.accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (page.excerpt.isNotBlank()) {
                        Text(
                            page.excerpt,
                            color = c.subtext, fontSize = 11.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                        )
                    }
                }
            }
            if (index < attachment.pages.lastIndex) {
                HorizontalDivider(color = c.border.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(start = 28.dp))
            }
        }
    }
}

@Composable
private fun AccessibilityCard(
    attachment: SkillAttachment.AccessibilityRequest,
    onOpenSettings: () -> Unit = {},
) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .background(c.card)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { ClawSymbolIcon("accessibility", tint = c.accent, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                str(R.string.needs_accessibility, attachment.skillName),
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(str(R.string.chat_please), color = c.subtext, fontSize = 11.sp)
        }
        TextButton(onClick = onOpenSettings) {
            Text(str(R.string.chat_cc42dd), color = c.accent, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WebPageCard(
    attachment: SkillAttachment.WebPage,
    onOpenBrowser: (String) -> Unit = {},
) {
    val c = LocalClawColors.current
    val domain = runCatching { java.net.URI(attachment.url).host ?: attachment.url }.getOrDefault(attachment.url)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .background(c.card)
            .clickable { onOpenBrowser(attachment.url) }
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { ClawSymbolIcon("link", tint = c.accent, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                attachment.title.ifBlank { domain },
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(domain, color = c.accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (attachment.excerpt.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    attachment.excerpt,
                    color = c.subtext,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L        -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    else                 -> "${bytes / (1024 * 1024)} MB"
}

private fun stripDataUriPrefix(value: String): String =
    if (value.startsWith("data:")) value.substringAfter(",") else value

private fun isImageFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.mimeType.startsWith("image/") ||
        attachment.name.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

private fun isStickerFileAttachment(attachment: SkillAttachment.FileData): Boolean =
    attachment.path.contains("/stickers/", ignoreCase = true) ||
        attachment.name.contains("bqb", ignoreCase = true)

// ── Input Bar ─────────────────────────────────────────────────────────────────
@Composable
private fun InputBar(
    input: String,
    isRunning: Boolean,
    supportsMultimodal: Boolean,
    attachedImageBase64: String?,
    attachedFile: FileAttachment?,
    showAttachMenu: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPickSticker: () -> Unit,
    onRemoveImage: () -> Unit,
    onRemoveFile: () -> Unit,
) {
    val c = LocalClawColors.current
    val hasAttachment = attachedImageBase64 != null || attachedFile != null
    val sendEnabled = (input.isNotBlank() || hasAttachment) && !isRunning
    val buttonAlpha by animateFloatAsState(if (sendEnabled) 1f else 0.4f, label = "btn")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.bg, c.surface)))
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (attachedImageBase64 != null) {
            val bitmap = remember(attachedImageBase64) {
                runCatching {
                    val clean = stripDataUriPrefix(attachedImageBase64)
                    val bytes = Base64.decode(clean, Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
            if (bitmap != null) {
                Box(modifier = Modifier.wrapContentSize()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 66.dp, height = 52.dp)
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

        if (attachedFile != null) {
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.card)
                    .border(0.5.dp, c.border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                Text(attachedFile.name, fontSize = 12.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp))
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = c.subtext,
                    modifier = Modifier.size(14.dp).clickable { onRemoveFile() },
                )
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(c.cardAlt)
                    .border(0.5.dp, c.border, CircleShape)
                    .clickable(enabled = !isRunning) { onPickSticker() },
                contentAlignment = Alignment.Center,
            ) {
                StickerIcon(
                    tint = if (isRunning) c.subtext.copy(alpha = 0.35f) else c.text,
                    modifier = Modifier.size(20.dp),
                )
            }

            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        if (isRunning) str(R.string.input_placeholder_running)
                        else str(R.string.input_placeholder),
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
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) c.red.copy(alpha = 0.15f)
                        else if (sendEnabled) c.text
                        else if (showAttachMenu) c.text
                        else c.cardAlt
                    )
                    .border(0.5.dp, if (sendEnabled || showAttachMenu) Color.Transparent else c.border, CircleShape)
                    .clickable(enabled = true) {
                        when {
                            isRunning -> onStop()
                            sendEnabled -> onSend()
                            else -> onAttachClick()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isRunning) {
                    Icon(Icons.Default.Close, contentDescription = "Stop", tint = c.red, modifier = Modifier.size(18.dp))
                } else if (sendEnabled) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = c.bg, modifier = Modifier.size(17.dp))
                } else {
                    Text(if (showAttachMenu) "×" else "+", color = if (showAttachMenu) c.bg else c.text, fontSize = 21.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        if (showAttachMenu) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .border(0.5.dp, c.border, RoundedCornerShape(18.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InputCapabilityTile("sticker", str(R.string.sticker_button), onPickSticker, enabled = true)
                InputCapabilityTile("IMG", str(R.string.chat_20def7), onPickImage, enabled = supportsMultimodal)
                InputCapabilityTile("DOC", str(R.string.chat_325369), onPickFile, enabled = true)
            }
        }
    }
}

@Composable
private fun InputCapabilityTile(
    mark: String,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (enabled) c.cardAlt else c.cardAlt.copy(alpha = 0.45f))
                .border(0.5.dp, c.border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (mark == "sticker") {
                StickerIcon(
                    tint = if (enabled) c.text else c.subtext.copy(alpha = 0.45f),
                    modifier = Modifier.size(21.dp),
                )
            } else {
                Text(mark, color = if (enabled) c.text else c.subtext.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, color = if (enabled) c.subtext else c.subtext.copy(alpha = 0.45f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StickerIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.085f)
        val center = Offset(size.width * 0.48f, size.height * 0.48f)
        val radius = size.minDimension * 0.36f
        drawCircle(color = tint, radius = radius, center = center, style = stroke)
        drawCircle(color = tint, radius = size.minDimension * 0.035f, center = Offset(size.width * 0.36f, size.height * 0.42f))
        drawCircle(color = tint, radius = size.minDimension * 0.035f, center = Offset(size.width * 0.58f, size.height * 0.42f))
        drawArc(
            color = tint,
            startAngle = 18f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(size.width * 0.34f, size.height * 0.43f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.24f),
            style = stroke,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.72f, size.height * 0.70f),
            end = Offset(size.width * 0.86f, size.height * 0.86f),
            strokeWidth = stroke.width,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.86f, size.height * 0.86f),
            end = Offset(size.width * 0.70f, size.height * 0.82f),
            strokeWidth = stroke.width,
        )
    }
}
