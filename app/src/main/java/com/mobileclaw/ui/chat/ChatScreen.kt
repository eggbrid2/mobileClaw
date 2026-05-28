package com.mobileclaw.ui.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.window.Dialog
import com.mobileclaw.R
import com.mobileclaw.agent.Role
import com.mobileclaw.agent.normalizeRoleAvatar
import com.mobileclaw.llm.LocalModelInfo
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.ui.ClawColors
import com.mobileclaw.ui.ClawSymbolIcon
import com.mobileclaw.ui.GradientAvatar
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.ui.MainUiState
import com.mobileclaw.ui.common.decodeDataUriBitmap
import com.mobileclaw.ui.common.decodeFileAttachmentBitmap
import com.mobileclaw.ui.common.formatFileSize
import com.mobileclaw.ui.common.friendlySkillDescription
import com.mobileclaw.ui.common.isImageFileAttachment
import com.mobileclaw.ui.common.isStickerFileAttachment
import com.mobileclaw.ui.common.MarkdownText
import com.mobileclaw.ui.common.mimeTypeSymbol
import com.mobileclaw.ui.common.openFileAttachment
import com.mobileclaw.ui.common.PickedChatInput
import com.mobileclaw.ui.common.FullscreenImageDialog
import com.mobileclaw.ui.common.buildPickedAttachment
import com.mobileclaw.ui.common.imageUriToDataUri
import com.mobileclaw.ui.common.stableUiSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

private data class HistoryScrollAnchor(
    val index: Int,
    val offset: Int,
    val messageCount: Int,
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
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = withContext(Dispatchers.IO) { imageUriToDataUri(context, uri) }
                onAttachImage(base64)
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) { buildPickedAttachment(context, uri) }
                when (picked) {
                    is PickedChatInput.Image -> onAttachImage(picked.base64)
                    is PickedChatInput.File -> onAttachFile(picked.attachment)
                    null -> Toast.makeText(context, str(R.string.chat_file_too_large), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showStickerSearch by remember { mutableStateOf(false) }
    var historyScrollAnchor by remember(uiState.currentSessionId) { mutableStateOf<HistoryScrollAnchor?>(null) }

    // Scroll to bottom. Split into two effects:
    // 1) item count changes → animated scroll (new message completed)
    // 2) log lines / streaming → instant scroll to avoid jump artifacts from overlapping animations
    // scrollOffset = 3000 ensures the BOTTOM of the last item is visible (clamped to max extent).
    var lastScrolledCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(itemCount) {
        val anchor = historyScrollAnchor
        if (anchor != null && runState.messages.size > anchor.messageCount) {
            val inserted = runState.messages.size - anchor.messageCount
            listState.scrollToItem(anchor.index + inserted, anchor.offset)
            historyScrollAnchor = null
            lastScrolledCount = itemCount
            return@LaunchedEffect
        }
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
            historyScrollAnchor = HistoryScrollAnchor(
                index = listState.firstVisibleItemIndex,
                offset = listState.firstVisibleItemScrollOffset,
                messageCount = runState.messages.size,
            )
            onLoadMoreHistory()
        }
    }
    val showJumpToLatest by remember(itemCount) {
        derivedStateOf {
            if (itemCount <= 2) false
            else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible < itemCount - 2
            }
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
                            onTaskSelected = { task -> onSendGoal(task) },
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
                itemsIndexed(runState.messages, key = { idx, msg -> msg.stableUiKey(idx) }) { idx, msg ->
                    when (msg.role) {
                        MessageRole.USER  -> UserBubble(msg.text, msg.imageBase64, uiState.userAvatarUri)
                        MessageRole.AGENT -> {
                            val messageRole = remember(msg.senderRoleId, msg.senderRoleName, msg.senderRoleAvatar, uiState.availableRoles, uiState.currentRole) {
                                msg.senderDisplayRole(uiState.availableRoles, uiState.currentRole)
                            }
                            val previous = runState.messages.getOrNull(idx - 1)
                            val showHeader = previous?.role != MessageRole.AGENT || !previous.sameSenderAs(msg)
                            if (msg.text.isBlank() && msg.logLines.isEmpty() && msg.attachments.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(0.93f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (showHeader) {
                                        AgentMessageHeader(
                                            role = messageRole,
                                            model = uiState.currentModel,
                                            onPickModel = { showModelPicker = true },
                                            onSwitchRole = onSwitchRole,
                                        )
                                    }
                                    AttachmentBubble(
                                        attachments = msg.attachments,
                                        onOpenHtmlViewer = onOpenHtmlViewer,
                                        onOpenBrowser = onOpenBrowser,
                                        onSendGoal = onSendGoal,
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
                                        showHeader = showHeader,
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
                                            onSendGoal = onSendGoal,
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
                            onSendGoal = onSendGoal,
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
            if (showJumpToLatest) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (c.isDark) Color(0xFF151515) else Color.White)
                            .border(0.6.dp, c.border.copy(alpha = 0.9f), CircleShape)
                            .clickable {
                                scope.launch {
                                    if (itemCount > 0) listState.animateScrollToItem(itemCount - 1, scrollOffset = 3000)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = c.text,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                str(R.string.chat_latest),
                                color = c.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            }
                        }
                    }
                }
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
            localModels = uiState.localModels,
            loading = uiState.modelsLoading,
            onSelect = { onModelChange(it); showModelPicker = false },
            onFetch = onFetchModels,
            onDismiss = { showModelPicker = false },
        )
    }

    if (showRenameDialog) {
        BasicAlertDialog(
            onDismissRequest = { showRenameDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(if (c.isDark) Color(0xFF101010) else Color.White, RoundedCornerShape(22.dp))
                    .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(str(R.string.chat_3e654d), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.text.copy(alpha = 0.5f),
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.text,
                        unfocusedTextColor = c.text,
                        cursorColor = c.text,
                        focusedContainerColor = if (c.isDark) Color(0xFF151515) else Color(0xFFF8F8F6),
                        unfocusedContainerColor = if (c.isDark) Color(0xFF151515) else Color(0xFFF8F8F6),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { showRenameDialog = false }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(str(R.string.btn_cancel), color = c.subtext, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(c.text)
                            .clickable {
                                if (renameText.isNotBlank()) {
                                    onRenameSession(uiState.currentSessionId, renameText.trim())
                                }
                                showRenameDialog = false
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(str(R.string.role_save), color = c.bg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    selectedStepLog?.let { step ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { selectedStepLog = null },
            sheetState = sheetState,
        ) {
            val c = LocalClawColors.current
            val detailView = remember(step) { buildStepDetailView(step) }
            var debugExpanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 标题先给用户一个“这是哪类进展”的心智锚点，减少机械日志感。
                Text(detailView.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text)
                Spacer(Modifier.height(8.dp))
                if (detailView.purpose.isNotBlank()) {
                    // 第一块固定回答“当前在处理什么”，让用户先知道 AI 现在的目标。
                    DetailLabelRow("当前在处理", detailView.purpose)
                    Spacer(Modifier.height(8.dp))
                }
                if (detailView.result.isNotBlank()) {
                    // 第二块固定回答“当前判断”，让用户看到此刻结论而不是内部执行话术。
                    DetailLabelRow("当前判断", detailView.result)
                    Spacer(Modifier.height(8.dp))
                }
                if (detailView.next.isNotBlank()) {
                    // 第三块固定回答“接下来”，把下一步显式告诉用户，减少等待焦虑。
                    DetailLabelRow("接下来", detailView.next)
                    Spacer(Modifier.height(8.dp))
                }
                detailView.supportLines.forEach { (label, content) ->
                    DetailLabelRow(label, content)
                    Spacer(Modifier.height(8.dp))
                }
                if (detailView.debugLines.isNotEmpty()) {
                    Text(
                        // 把“调试信息”改成“技术细节”，明确它是给需要深入看的用户准备的二级信息。
                        if (debugExpanded) "收起技术细节" else "查看技术细节",
                        color = c.subtext,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { debugExpanded = !debugExpanded }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    )
                    AnimatedVisibility(debugExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            detailView.debugLines.forEach { detail ->
                                Text(
                                    detail,
                                    fontSize = 10.sp,
                                    color = c.subtext.copy(alpha = 0.75f),
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
        }
    }
}

private data class StepDetailView(
    // 用更用户化的标题标记当前弹框属于哪类进展，而不是暴露内部日志类型。
    val title: String,
    // purpose 专门回答“现在在处理什么”，避免让用户自己从技术细节里反推意图。
    val purpose: String,
    // result 专门回答“当前判断到了什么”，避免只展示空泛的成功/失败字样。
    val result: String,
    // next 单独承载下一步，让弹框形成“当前处理 -> 当前判断 -> 接下来”的稳定阅读顺序。
    val next: String,
    // supportLines 承载“这样安排/补充判断”这类正文辅助信息，避免它们被塞进技术细节区。
    val supportLines: List<Pair<String, String>>,
    // debugLines 保留给点开后的技术细节，不再默认抢占主信息区。
    val debugLines: List<String>,
)

private fun buildStepDetailView(step: LogLine): StepDetailView {
    // details 为空时回退到主文本，确保每个弹框至少能产出一套可读内容。
    val detailLines = step.details.ifEmpty { listOf(step.text).filter { it.isNotBlank() } }
    // 从结构化 detail 里抽出“本步目的”，优先使用运行时已经整理好的用户向描述。
    val purpose = detailLines.firstOrNull { it.startsWith("本步目的：") }
        ?.removePrefix("本步目的：")
        ?.trim()
        .orEmpty()
    // 从结构化 detail 里抽出“本步结果”，把系统原始返回和用户可见判断分离。
    val result = detailLines.firstOrNull { it.startsWith("本步结果：") }
        ?.removePrefix("本步结果：")
        ?.trim()
        .orEmpty()
    // next 单独读取“接下来/后续计划”，避免下一步信息混在结果里显得机械。
    val next = detailLines.firstOrNull { it.startsWith("接下来：") || it.startsWith("后续计划：") }
        ?.substringAfter("：")
        ?.trim()
        .orEmpty()
    val supportLines = buildList {
        detailLines.firstOrNull { it.startsWith("这样安排：") }
            ?.removePrefix("这样安排：")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { add("这样安排" to it) }
        detailLines.firstOrNull { it.startsWith("补充判断：") }
            ?.removePrefix("补充判断：")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { add("补充判断" to it) }
    }
    // 主视图只保留用户真正关心的三段信息，其余内容全部下沉成可展开的技术细节。
    val debugLines = detailLines.filterNot {
        it.startsWith("本步目的：") ||
            it.startsWith("本步结果：") ||
            it.startsWith("这样安排：") ||
            it.startsWith("补充判断：") ||
            it.startsWith("接下来：") ||
            it.startsWith("后续计划：") ||
            it.startsWith("完整结果")
    }.takeIf { it.isNotEmpty() } ?: listOf(step.text).filter { it.isNotBlank() }
    // 标题统一改成“进展/问题/完成情况”这类用户语言，避免出现机械的系统词汇。
    val title = when (step.type) {
        LogType.ACTION -> "执行进展"
        LogType.OBSERVATION -> "处理进展"
        LogType.THINKING -> "思路进展"
        LogType.SUCCESS -> "完成情况"
        LogType.ERROR -> "问题详情"
        else -> "当前进展"
    }
    return StepDetailView(
        // 如果运行时没提供 purpose，就回退到本地生成的人话描述，保证弹框总是可读。
        title = title,
        purpose = purpose.ifBlank { fallbackReadablePurpose(step) },
        // 如果运行时没提供 result，就回退到本地生成的用户向判断描述。
        result = result.ifBlank { fallbackReadableResult(step) },
        // next 保持独立，让用户可以快速扫到“后面会发生什么”。
        next = next,
        // supportLines 放在正文主信息区，但优先级低于“处理/判断/接下来”。
        supportLines = supportLines,
        // 技术细节继续保留，但退居二级信息层。
        debugLines = debugLines,
    )
}

private fun fallbackReadablePurpose(step: LogLine): String = when (step.type) {
    // ACTION 回退到技能中文名，至少让用户知道系统在调用哪类能力。
    LogType.ACTION -> chineseSkillLabel(step.skillId)
    // OBSERVATION 改成“看画面/读结果的目的”而不是“已读取”，让用户理解为什么要看。
    LogType.OBSERVATION -> if (step.imageBase64 != null) "查看当前画面，确认任务是否推进到了正确位置" else "读取当前返回结果，判断这一步是否真的解决了问题"
    // THINKING 继续保留“正在整理下一步”的语义，但不暴露内部链路名词。
    LogType.THINKING -> step.text.ifBlank { "正在根据当前任务整理下一步" }
    // SUCCESS/ERROR 使用用户能理解的阶段语义，不用内部术语。
    LogType.SUCCESS -> "任务阶段完成"
    LogType.ERROR -> "当前步骤遇到问题"
    else -> step.text.ifBlank { "继续推进当前任务" }
}

private fun fallbackReadableResult(step: LogLine): String = when (step.type) {
    // ACTION 告诉用户“这一步已经发出”，弱化机械的执行态细节。
    LogType.ACTION -> "这一步已经发出，正在等待结果返回"
    // OBSERVATION 回答“拿到结果后现在怎么判断”，而不是只说“已读取结果”。
    LogType.OBSERVATION -> if (step.imageBase64 != null) "已经看到当前画面，正在判断是否符合目标" else "已经拿到结果，正在判断是否继续修正"
    // THINKING 明确这一步产出的是“下一步依据”，让思考态更像在推进任务。
    LogType.THINKING -> step.text.ifBlank { "已经更新下一步的处理依据" }
    LogType.SUCCESS -> step.text.ifBlank { "已完成" }
    // ERROR 改成“正在换一种方式继续”，减少“失败”这种让用户无从判断的字样。
    LogType.ERROR -> step.text.ifBlank { "这里遇到问题，正在准备换一种方式继续" }
    else -> step.text.ifBlank { "已经记录当前进展" }
}

private fun conciseUserProgress(text: String, limit: Int = 42): String {
    // 先把多行和结构化前缀压成一句，避免步骤摘要像控制台日志一样碎。
    val normalized = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("调试：") }
        .joinToString(" ")
        .replace("本步目的：", "")
        .replace("本步结果：", "")
        .replace("这样安排：", "")
        .replace("补充判断：", "")
        .replace("接下来：", "")
        .replace("后续计划：", "")
        .trim()
    // 列表摘要必须短，只保留一眼能懂的进展信息。
    return when {
        normalized.isBlank() -> ""
        normalized.length <= limit -> normalized
        else -> normalized.take(limit).trimEnd() + "…"
    }
}

private fun stepRowSummary(line: LogLine): String {
    // 先优先使用结构化“结果/目的/下一步”，因为这些字段已经是给用户看的语义层信息。
    val structured = line.details.firstNotNullOfOrNull { detail ->
        when {
            detail.startsWith("本步结果：") -> detail.removePrefix("本步结果：").trim()
            detail.startsWith("本步目的：") -> detail.removePrefix("本步目的：").trim()
            detail.startsWith("补充判断：") -> detail.removePrefix("补充判断：").trim()
            detail.startsWith("这样安排：") -> detail.removePrefix("这样安排：").trim()
            detail.startsWith("接下来：") -> detail.removePrefix("接下来：").trim()
            detail.startsWith("后续计划：") -> detail.removePrefix("后续计划：").trim()
            else -> null
        }
    }.orEmpty()
    val fallback = when (line.type) {
        // THINKING 摘要直接回答“现在在想什么”，不要再显示字符数这种纯技术信息。
        LogType.THINKING -> line.text.ifBlank { "正在整理下一步" }
        // ACTION 摘要优先展示用户视角的目的，而不是工具名本身。
        LogType.ACTION -> fallbackReadablePurpose(line)
        // OBSERVATION 摘要优先回答“现在判断到了什么”。
        LogType.OBSERVATION -> fallbackReadableResult(line)
        LogType.SUCCESS -> line.text.ifBlank { "这一段已经完成" }
        LogType.ERROR -> line.text.ifBlank { "这一段遇到问题，正在调整" }
        else -> line.text
    }
    // 统一走一遍压缩函数，确保所有步骤行高度和阅读节奏稳定。
    return conciseUserProgress(structured.ifBlank { fallback })
}

@Composable
private fun DetailLabelRow(title: String, content: String) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = c.subtext.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(content, color = c.text, fontSize = 12.sp, lineHeight = 16.sp)
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
    localModels: List<LocalModelInfo>,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onFetch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalClawColors.current
    val localByModelId = remember(localModels) { localModels.associateBy { it.modelId } }
    val localById = remember(localModels) { localModels.associateBy { it.id } }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(if (c.isDark) Color(0xFF101010) else Color.White, RoundedCornerShape(22.dp))
                .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(22.dp))
                .padding(top = 16.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c.text.copy(alpha = 0.05f))
                        .clickable { onFetch() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = str(R.string.chat_fetch_models),
                        tint = c.subtext,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            HorizontalDivider(color = c.border, thickness = 0.5.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
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
                    val local = localByModelId[model] ?: localById[model.removePrefix("local:")]
                    val isLocalModel = local != null || model.startsWith("local:")
                    val source = if (isLocalModel) str(R.string.chat_model_source_local) else str(R.string.chat_model_source_cloud)
                    val ability = when {
                        local?.supportsVision == true -> str(R.string.local_model_cap_vision)
                        local?.supportsText == true -> str(R.string.local_model_cap_text)
                        else -> str(R.string.local_model_cap_text)
                    }
                    val title = local?.name ?: model.substringAfterLast("/").substringAfter("local:")
                    val subtitle = "$source · $ability"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(model) }
                            .background(
                                if (isSelected) c.text.copy(alpha = if (c.isDark) 0.14f else 0.07f)
                                else Color.Transparent
                            )
                            .border(
                                0.5.dp,
                                if (isSelected) c.text.copy(alpha = 0.16f) else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isLocalModel) c.accent.copy(alpha = 0.13f)
                                    else c.blue.copy(alpha = 0.11f)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            ClawSymbolIcon(
                                symbol = if (isLocalModel) "model" else "web",
                                tint = if (isLocalModel) c.accent else c.blue,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                title,
                                color = c.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                subtitle,
                                color = c.subtext,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isSelected) {
                            ClawSymbolIcon("check", tint = c.accent, modifier = Modifier.size(18.dp))
                        }
                    }
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
        modifier = Modifier.fillMaxWidth().padding(top = 42.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.15f), Color.Transparent)))
                .border(1.dp, c.accent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { ClawSymbolIcon("profile", tint = c.accent, modifier = Modifier.size(30.dp)) }

        Spacer(Modifier.height(16.dp))
        Text("MobileClaw", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        Spacer(Modifier.height(5.dp))
        Text(str(R.string.app_tagline), color = c.subtext, fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        Text(
            if (recommendations.isNotEmpty()) str(R.string.chat_b8181c) else str(R.string.chat_72e47b),
            color = c.subtext,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tasks.take(3).forEach { task ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (c.isDark) Color(0xFF141414) else Color.White)
                        .border(0.6.dp, c.border.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                        .clickable { onTaskSelected(cleanTaskLabel(task)) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text(
                        cleanTaskLabel(task),
                        color = c.text.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable { onOpenHelp() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ClawSymbolIcon("help", tint = c.subtext, modifier = Modifier.size(15.dp))
            Text(str(R.string.chat_c5fab5), color = c.subtext, fontSize = 12.sp)
        }
    }
}

private fun cleanTaskLabel(task: String): String =
    task.replace(Regex("""^[^\p{L}\p{N}]+"""), "").trim()

// ── User Bubble ───────────────────────────────────────────────────────────────
@Composable
private fun UserBubble(text: String, imageBase64: String? = null, userAvatarUri: String? = null) {
    val c = LocalClawColors.current
    var showFullscreen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (imageBase64 != null) {
                val bitmap = remember(imageBase64) { decodeDataUriBitmap(imageBase64) }
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
        Spacer(Modifier.width(6.dp))
        GradientAvatar(avatar = userAvatarUri.orEmpty(), size = 28.dp, color = c.text)
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
        avatar = normalizeRoleAvatar(senderRoleId.ifBlank { fallback.id }, senderRoleAvatar.ifBlank { fallback.avatar }),
    )
}

private fun ChatMessage.sameSenderAs(other: ChatMessage): Boolean =
    senderRoleId.ifBlank { senderRoleName } == other.senderRoleId.ifBlank { other.senderRoleName }

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
            avatar = role.avatar,
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
    showHeader: Boolean = true,
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
    val shouldCollapseSummary = cleanSummary.length > 420 || uiFencePattern.containsMatchIn(cleanSummary)
    val visibleSummary = if (shouldCollapseSummary && !summaryExpanded) textPreview(cleanSummary) else cleanSummary

    Column(modifier = Modifier.fillMaxWidth(0.93f)) {
        // Role header: avatar + name + model chip
        if (showHeader || !isSuccess) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showHeader) {
                    AgentMessageHeader(
                        role = currentRole,
                        model = currentModel,
                        onSwitchRole = onSwitchRole,
                        onPickModel = onPickModel,
                    )
                }
                if (!isSuccess) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(c.red.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("!", color = c.red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (visibleSummary.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (c.isDark) Color(0xFF141414) else Color(0xFFF8F8F6))
                    .border(
                        0.6.dp,
                        if (c.isDark) c.border.copy(alpha = 0.75f) else Color(0xFFE9E9E4),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                MarkdownText(visibleSummary, color = c.text, fontSize = 13.sp, lineHeight = 18.sp, onAction = onSendGoal)
            }
        }

        if (shouldCollapseSummary) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (summaryExpanded) str(R.string.chat_collapse_simple) else str(R.string.chat_more_simple),
                color = c.subtext,
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
                        is SkillAttachment.ActionCard           -> ActionCardAttachment(attachment, onSendGoal)
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
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                hasError -> c.red.copy(alpha = 0.12f)
                                hasThinking -> c.accent.copy(alpha = 0.12f)
                                else -> c.text.copy(alpha = 0.06f)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hasError) {
                        Text("!", color = c.red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    } else {
                        ClawSymbolIcon(
                            symbol = if (hasThinking) "profile" else "tool",
                            tint = if (hasThinking) c.accent else c.subtext,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    buildString {
                        append(stringResource(R.string.steps_label, steps.size))
                        if (hasThinking) append(str(R.string.chat_3d188f))
                    },
                    color = c.subtext,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (stepsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = c.subtext.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
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
    onSendGoal: (String) -> Unit = {},
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
                is SkillAttachment.ActionCard           -> ActionCardAttachment(attachment, onSendGoal)
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

    val lastAction = logLines.lastOrNull { it.type == LogType.ACTION || it.type == LogType.THINKING }
    val latestProgress = logLines.asReversed()
        .firstOrNull { it.text.isNotBlank() && it.type != LogType.SUCCESS }
        ?.text
        ?.replace(Regex("\\s+"), " ")
        ?.take(120)
        .orEmpty()
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
            GradientAvatar(avatar = currentRole.avatar, size = 34.dp, color = c.accent)
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
                .clip(RoundedCornerShape(16.dp))
                .background(if (c.isDark) Color(0xFF141414) else Color(0xFFF8F8F6))
                .border(0.8.dp, c.accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp),
        ) {
            // Header: label + spinner + step status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), color = c.accent, strokeWidth = 1.6.dp)
                }
                Spacer(Modifier.width(8.dp))
                if (logLines.isNotEmpty()) {
                    Text(
                        currentLabel,
                        color = c.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (stepsExpanded) str(R.string.chat_c4b206) else str(R.string.chat_900d90),
                        color = c.subtext,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.text.copy(alpha = 0.05f))
                            .clickable { stepsExpanded = !stepsExpanded }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                } else {
                    Text(str(R.string.chat_dcfb68), color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }

            if (logLines.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    str(R.string.step_label, logLines.size, currentLabel),
                    color = c.subtext,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 28.dp),
                )
                if (latestProgress.isNotBlank() && !stepsExpanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        latestProgress,
                        color = c.subtext.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 28.dp, end = 4.dp),
                    )
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
                            .background(if (c.isDark) Color(0xFF0F0F0F) else Color.White)
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
                        .background(c.text.copy(alpha = 0.045f))
                        .clickable { thoughtExpanded = !thoughtExpanded }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    // 运行中思考也只显示“正在做什么”，不再显示字符统计这类无助于理解的噪音。
                    val liveThoughtSummary = conciseUserProgress(streamingThought.takeLast(160), limit = 28)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.width(2.dp).height(12.dp).background(c.accent.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
                        Text(str(R.string.chat_1aa9e9), color = c.subtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, modifier = Modifier.weight(1f))
                        if (liveThoughtSummary.isNotBlank()) {
                            Text(liveThoughtSummary, color = c.subtext.copy(alpha = 0.65f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (thoughtExpanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.65f), fontSize = 9.sp)
                    }
                    AnimatedVisibility(
                        visible = thoughtExpanded,
                        enter = expandVertically() + fadeIn(tween(100)),
                        exit  = shrinkVertically() + fadeOut(tween(100)),
                    ) {
                        Text(
                            streamingThought.takeLast(400),
                            color = c.subtext,
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

private fun skillIconSymbol(skillId: String?): String = when (skillId) {
    "screenshot", "bg_screenshot" -> "screenshot"
    "read_screen", "bg_read_screen", "see_screen" -> "eye"
    "tap", "long_click", "scroll", "input_text", "navigate" -> "phone"
    "list_apps", "app_manager" -> "apps"
    "web_search", "fetch_url", "web_browse", "web_content", "web_js" -> "web"
    "bg_launch", "bg_stop", "vd_setup" -> "desktop"
    "shell" -> "code"
    "memory" -> "memory"
    "permission" -> "permission"
    "skill_check", "skill_market", "quick_skill", "meta" -> "skill"
    "generate_image" -> "image"
    "ui_builder" -> "page"
    "create_file", "create_html" -> "file"
    "switch_model" -> "model"
    "switch_role" -> "role"
    "user_config" -> "settings"
    else -> "tool"
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
    iconSymbol: String,
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
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(labelColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClawSymbolIcon(iconSymbol, tint = labelColor, modifier = Modifier.size(12.dp))
                }
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
            // 思考卡片的摘要改成“当前思路”，避免把字符数这种机械指标展示给用户。
            val summary = stepRowSummary(line)
            CollapsibleStepRow(
                label = stringResource(R.string.chat_f2b9df),
                summary = summary,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = c.accent.copy(alpha = 0.78f),
                iconSymbol = "profile",
                onDetail = { onSelectStep(line) },
            ) {
                Text(
                    line.text,
                    color = c.subtext,
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
            // 执行动作的摘要统一走用户向提炼函数，避免出现旧的截断工具描述。
            val summary = stepRowSummary(line)
            CollapsibleStepRow(
                label = skillLabel,
                summary = summary,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = labelColor,
                iconSymbol = skillIconSymbol(line.skillId),
                onDetail = { onSelectStep(line) },
            ) {
                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    if (line.skillId == "shell") ShellCommandCard(friendlySkillDescription(line.skillId ?: "", emptyMap()), line.text)
                    else SkillActionCard(summary.ifBlank { skillLabel })
                }
            }
        }

        LogType.OBSERVATION -> {
            var expanded by remember { mutableStateOf(false) }
            val hasImage = line.imageBase64 != null
            val label = if (hasImage) stringResource(R.string.chat_a3d484) else stringResource(R.string.chat_173c2c)
            // 观察结果优先展示“当前判断”，这样用户能立刻知道这一步看到了什么。
            val summary = stepRowSummary(line).ifBlank {
                if (hasImage) stringResource(R.string.chat_b3e19e) else ""
            }
            CollapsibleStepRow(
                label = label,
                summary = summary,
                expanded = expanded,
                onToggle = { expanded = !expanded },
                labelColor = c.subtext.copy(alpha = 0.65f),
                iconSymbol = if (hasImage) "screenshot" else "eye",
                onDetail = { onSelectStep(line) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (hasImage) {
                        val bitmap = remember(line.imageBase64) { decodeDataUriBitmap(line.imageBase64!!) }
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
                    val readable = line.details.firstOrNull { it.startsWith("本步结果：") }
                        ?.removePrefix("本步结果：")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        // 展开内容也优先用用户向结果描述，避免展示原始返回句式。
                        ?: fallbackReadableResult(line).takeIf { it.isNotBlank() }
                    if (readable != null) {
                        Text(readable, color = c.subtext.copy(alpha = 0.78f), fontSize = 11.sp, lineHeight = 15.sp)
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
                ClawSymbolIcon("check", tint = c.green, modifier = Modifier.width(14.dp).padding(top = 1.dp).size(12.dp))
                Text(line.text, color = c.green.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }

        LogType.ERROR -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("!", color = c.red, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(14.dp).padding(top = 1.dp))
                Text(line.text, color = c.red.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(modifier = Modifier.width(14.dp).padding(top = 6.dp)) {
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(c.subtext.copy(alpha = 0.45f)))
                }
                Text(line.text, color = c.subtext.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkillActionCard(text: String) {
    val c = LocalClawColors.current
    val accentColor = c.accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface),
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
private fun ShellCommandCard(summary: String, command: String) {
    val c = LocalClawColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (c.isDark) Color(0xFF0E0E0E) else Color(0xFFF6F6F4))
            .border(1.dp, c.border, RoundedCornerShape(8.dp)),
    ) {
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
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("目的", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                summary,
                color = c.text,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
        if (command.isNotBlank()) {
            Text(
                "命令细节已隐藏，点开步骤详情可查看调试信息。",
                color = c.subtext.copy(alpha = 0.62f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(start = 9.dp, end = 9.dp, bottom = 7.dp),
            )
        }
    }
}

// ── Attachment Cards ──────────────────────────────────────────────────────────

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
    val bitmap = remember(attachment.base64) { decodeDataUriBitmap(attachment.base64) }
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
                .clip(RoundedCornerShape(16.dp))
                .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .background(if (c.isDark) Color(0xFF141414) else Color.White)
                .clickable { openFileAttachment(context, attachment) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.blue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                ClawSymbolIcon(
                    symbol = mimeTypeSymbol(attachment.mimeType),
                    tint = c.blue,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${formatFileSize(attachment.sizeBytes)} · ${attachment.mimeType}",
                    color = c.subtext,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ClawSymbolIcon("link", tint = c.subtext, modifier = Modifier.size(17.dp))
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
            .clip(RoundedCornerShape(16.dp))
            .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0xFF141414) else Color.White),
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
            ) { ClawSymbolIcon("folder", tint = c.text, modifier = Modifier.size(18.dp)) }
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
                        ) {
                            ClawSymbolIcon(
                                symbol = mimeTypeSymbol(entry.mimeType),
                                tint = c.text,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, color = c.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatFileSize(entry.sizeBytes), color = c.subtext, fontSize = 10.sp)
                        }
                        ClawSymbolIcon("link", tint = c.subtext, modifier = Modifier.size(15.dp))
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (c.isDark) Color(0xFF141414) else Color.White)
                    .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClawSymbolIcon("image", tint = c.subtext, modifier = Modifier.size(18.dp))
                Text(str(R.string.chat_image_unavailable), color = c.subtext, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (bitmap != null && showFullscreen) {
        FullscreenImageDialog(bitmap = bitmap!!, onDismiss = { showFullscreen = false })
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
            .clip(RoundedCornerShape(16.dp))
            .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0xFF141414) else Color.White)
            .clickable { onOpenHtmlViewer(attachment) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { ClawSymbolIcon("web", tint = c.accent, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(attachment.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(str(R.string.chat_411604), color = c.subtext, fontSize = 11.sp)
        }
        ClawSymbolIcon("link", tint = c.subtext, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SearchResultsCard(
    attachment: SkillAttachment.SearchResults,
    onOpenBrowser: (String) -> Unit = {},
) {
    val c = LocalClawColors.current
    var expanded by remember(attachment.query, attachment.pages.size) { mutableStateOf(false) }
    val visiblePages = if (expanded) attachment.pages else attachment.pages.take(3)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0xFF141414) else Color.White),
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
        visiblePages.forEachIndexed { index, page ->
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
                    Text(domain, color = c.subtext, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (page.excerpt.isNotBlank()) {
                        Text(
                            page.excerpt,
                            color = c.subtext, fontSize = 11.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
                        )
                    }
                }
            }
            if (index < visiblePages.lastIndex) {
                HorizontalDivider(color = c.border.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(start = 28.dp))
            }
        }
        if (attachment.pages.size > 3) {
            HorizontalDivider(color = c.border.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(start = 28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (expanded) str(R.string.chat_collapse_results) else str(R.string.chat_show_more_results, attachment.pages.size - visiblePages.size),
                    color = c.subtext,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = c.subtext,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionCardAttachment(
    attachment: SkillAttachment.ActionCard,
    onSendGoal: (String) -> Unit = {},
) {
    val c = LocalClawColors.current
    val actionStateKey = remember(attachment) { attachment.stableUiSignature() }
    var selectedActionMessage by remember(actionStateKey) { mutableStateOf<String?>(null) }
    val accent = when (attachment.tone) {
        "phone" -> Color(0xFF56D6BA)
        "role" -> Color(0xFFC7F43A)
        "warning" -> Color(0xFFFFB020)
        else -> c.accent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(0.7.dp, if (c.isDark) c.border.copy(alpha = 0.9f) else Color(0xFFE7E7E2), RoundedCornerShape(18.dp))
            .background(if (c.isDark) Color(0xFF111111) else Color.White)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.copy(alpha = if (c.isDark) 0.18f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                ClawSymbolIcon(
                    when (attachment.tone) {
                        "phone" -> "phone"
                        "role" -> "role"
                        "warning" -> "permission"
                        else -> "spark"
                    },
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    attachment.title,
                    color = c.text,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (attachment.body.isNotBlank()) {
                    Text(
                        attachment.body,
                        color = c.subtext,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            attachment.actions.forEach { action ->
                val primary = action.style == "primary"
                val handled = selectedActionMessage != null
                val selected = selectedActionMessage == action.message
                val buttonBg = when {
                    selected -> accent.copy(alpha = if (c.isDark) 0.22f else 0.16f)
                    handled -> Color.Transparent
                    primary -> c.text
                    else -> Color.Transparent
                }
                val buttonBorder = when {
                    selected -> accent
                    primary && !handled -> c.text
                    else -> c.border.copy(alpha = 0.75f)
                }
                val label = when {
                    selected && action.label.contains("取消") -> "已取消"
                    selected -> "已提交"
                    else -> action.label
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(buttonBg)
                        .border(
                            0.8.dp,
                            buttonBorder,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(enabled = !handled) {
                            selectedActionMessage = action.message
                            onSendGoal(action.message)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = when {
                            selected -> accent
                            handled -> c.subtext.copy(alpha = 0.55f)
                            primary -> c.bg
                            else -> c.text
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            .clip(RoundedCornerShape(16.dp))
            .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0xFF141414) else Color.White)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
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
            .clip(RoundedCornerShape(16.dp))
            .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .background(if (c.isDark) Color(0xFF141414) else Color.White)
            .clickable { onOpenBrowser(attachment.url) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(12.dp))
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
            Text(domain, color = c.subtext, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private fun ChatMessage.stableUiKey(index: Int): String = buildString {
    append(role.name)
    append(':')
    append(senderRoleId)
    append(':')
    append(text.hashCode())
    append(':')
    append(imageBase64?.hashCode() ?: 0)
    append(':')
    append(attachments.size)
    append(':')
    append(attachments.joinToString("|") { it.stableUiSignature() }.hashCode())
    append(':')
    append(logLines.size)
    append(':')
    append(index)
}

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.0f), c.bg, c.surface)))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (attachedImageBase64 != null) {
            val bitmap = remember(attachedImageBase64) { decodeDataUriBitmap(attachedImageBase64) }
            if (bitmap != null) {
                val ratio = remember(bitmap) {
                    (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.7f, 1.8f)
                }
                Box(modifier = Modifier.wrapContentSize()) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .widthIn(max = 86.dp)
                            .heightIn(max = 66.dp)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit,
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
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (c.isDark) Color(0xFF141414) else Color.White)
                    .border(0.6.dp, c.border.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.text.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    ClawSymbolIcon(mimeTypeSymbol(attachedFile.mimeType), tint = c.text, modifier = Modifier.size(16.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(attachedFile.name, fontSize = 12.sp, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(attachedFile.mimeType, fontSize = 10.sp, color = c.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { onRemoveFile() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(14.dp))
                }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (c.isDark) Color(0xFF171717) else Color.White)
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
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.text.copy(alpha = 0.55f),
                    unfocusedBorderColor = c.border,
                    focusedTextColor = c.text,
                    unfocusedTextColor = c.text,
                    cursorColor = c.text,
                    disabledBorderColor = c.border,
                    disabledTextColor = c.subtext,
                    focusedContainerColor = if (c.isDark) Color(0xFF141414) else Color.White,
                    unfocusedContainerColor = if (c.isDark) Color(0xFF141414) else Color.White,
                    disabledContainerColor = if (c.isDark) Color(0xFF141414) else Color.White,
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) c.red.copy(alpha = 0.15f)
                        else if (sendEnabled) c.text
                        else if (showAttachMenu) c.text
                        else if (c.isDark) Color(0xFF171717) else Color.White
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
                    Icon(Icons.Default.Close, contentDescription = str(R.string.chat_stop), tint = c.red, modifier = Modifier.size(18.dp))
                } else if (sendEnabled) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = str(R.string.sticker_send), tint = c.bg, modifier = Modifier.size(17.dp))
                } else {
                    Text(if (showAttachMenu) "×" else "+", color = if (showAttachMenu) c.bg else c.text, fontSize = 21.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        if (showAttachMenu) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (c.isDark) Color(0xFF111111) else Color.White)
                    .border(0.5.dp, c.border, RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InputCapabilityTile("sticker", str(R.string.sticker_button), onPickSticker, enabled = true)
                InputCapabilityTile("image", str(R.string.chat_20def7), onPickImage, enabled = supportsMultimodal)
                InputCapabilityTile("file", str(R.string.chat_325369), onPickFile, enabled = true)
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
                .size(46.dp)
                .clip(CircleShape)
                .background(if (enabled) c.text.copy(alpha = 0.055f) else c.text.copy(alpha = 0.025f))
                .border(0.5.dp, c.border.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (mark == "sticker") {
                StickerIcon(
                    tint = if (enabled) c.text else c.subtext.copy(alpha = 0.45f),
                    modifier = Modifier.size(21.dp),
                )
            } else {
                ClawSymbolIcon(
                    symbol = mark,
                    tint = if (enabled) c.text else c.subtext.copy(alpha = 0.45f),
                    modifier = Modifier.size(21.dp),
                )
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
