package com.mobileclaw.ui.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.config.hasCapability
import com.mobileclaw.memory.db.VideoGenerationTaskEntity
import com.mobileclaw.skill.builtin.VideoTaskStatuses
import com.mobileclaw.skill.builtin.isVideoDownloadUrlPending
import com.mobileclaw.ui.LocalAppLanguage
import com.mobileclaw.ui.LocalClawColors

@Composable
fun VideoGeneratorPage(
    isRunning: Boolean,
    configSnapshot: ConfigSnapshot,
    videoTasks: List<VideoGenerationTaskEntity>,
    refreshingIds: Set<String>,
    refreshingAll: Boolean,
    promptAiRunning: Boolean,
    onBack: () -> Unit,
    onGenerate: (VideoGenerationRequest) -> Unit,
    onRewritePrompt: (String, VideoPromptAiAction, (String) -> Unit) -> Unit,
    onUploadFrameImage: (String, (Result<String>) -> Unit) -> Unit,
    onRefreshTask: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    val gateways = remember(configSnapshot) {
        configSnapshot.gateways.filter { it.hasCapability("video") }.ifEmpty { configSnapshot.gateways }
    }
    var selectedGatewayId by remember { mutableStateOf(configSnapshot.activeGateway?.id.orEmpty()) }
    LaunchedEffect(configSnapshot.activeGatewayId, gateways) {
        if (selectedGatewayId.isBlank() || gateways.none { it.id == selectedGatewayId }) {
            selectedGatewayId = configSnapshot.activeGateway?.id ?: gateways.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedGateway = gateways.firstOrNull { it.id == selectedGatewayId } ?: configSnapshot.activeGateway
    val modelOptions = remember(selectedGateway) { selectedGateway.videoModelOptions() }
    var selectedModel by remember { mutableStateOf("") }
    LaunchedEffect(selectedGateway?.id, modelOptions) {
        selectedModel = modelOptions.firstOrNull().orEmpty()
    }

    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("text") }
    var aspectRatio by remember { mutableStateOf("9:16") }
    var duration by remember { mutableStateOf("5") }
    var firstFrame by remember { mutableStateOf(VideoFrameUploadState()) }
    var lastFrame by remember { mutableStateOf(VideoFrameUploadState()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var negativePrompt by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }
    var frameRate by remember { mutableStateOf("24") }
    var extraBody by remember { mutableStateOf("") }
    var pickingTarget by remember { mutableStateOf(VideoImageTarget.FIRST) }

    val imagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
        val value = uri?.toString() ?: return@rememberLauncherForActivityResult
        when (pickingTarget) {
            VideoImageTarget.FIRST -> {
                firstFrame = VideoFrameUploadState(source = value, isUploading = true)
                onUploadFrameImage(value) { result ->
                    firstFrame = firstFrame.withUploadResult(source = value, result = result)
                }
            }
            VideoImageTarget.LAST -> {
                lastFrame = VideoFrameUploadState(source = value, isUploading = true)
                onUploadFrameImage(value) { result ->
                    lastFrame = lastFrame.withUploadResult(source = value, result = result)
                }
            }
        }
    }
    fun pickImage(target: VideoImageTarget) {
        pickingTarget = target
        imagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(mode) {
        when (mode) {
            "text" -> {
                firstFrame = VideoFrameUploadState()
                lastFrame = VideoFrameUploadState()
            }
            "ti2vid" -> {
                lastFrame = VideoFrameUploadState()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (c.isDark) Color(0xFF050505) else Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Header(isRunning = isRunning, refreshingAll = refreshingAll, onBack = onBack, onRefreshAll = onRefreshAll)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero()
            PickerBlock(if (isZh) "网关" else "Gateway") {
                if (gateways.isEmpty()) {
                    Text(if (isZh) "还没有配置网关" else "No gateway configured", color = c.subtext, fontSize = 12.sp)
                } else {
                    ChipRow(
                        options = gateways.map { it.id to it.name },
                        selected = selectedGatewayId,
                        onSelect = { selectedGatewayId = it },
                    )
                }
            }
            PickerBlock(if (isZh) "模型" else "Model") {
                if (modelOptions.isEmpty()) {
                    Text(if (isZh) "使用网关默认视频模型" else "Using the gateway default video model", color = c.subtext, fontSize = 12.sp)
                } else {
                    ChipRow(
                        options = modelOptions.map { it to it },
                        selected = selectedModel,
                        onSelect = { selectedModel = it },
                    )
                }
            }
            VideoTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = if (isZh) "想生成什么" else "What do you want to generate?",
                minLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = if (promptAiRunning) {
                        if (isZh) "AI 处理中" else "AI working"
                    } else {
                        if (isZh) "AI 丰富" else "Enhance"
                    },
                    enabled = prompt.isNotBlank() && !promptAiRunning && !isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRewritePrompt(prompt, VideoPromptAiAction.ENRICH) { rewritten ->
                            prompt = rewritten
                        }
                    },
                )
                SecondaryButton(
                    text = if (promptAiRunning) {
                        if (isZh) "AI 处理中" else "AI working"
                    } else {
                        if (isZh) "AI 翻译" else "Translate"
                    },
                    enabled = prompt.isNotBlank() && !promptAiRunning && !isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onRewritePrompt(prompt, VideoPromptAiAction.TRANSLATE) { rewritten ->
                            prompt = rewritten
                        }
                    },
                )
            }
            PickerBlock(if (isZh) "模式" else "Mode") {
                ChipRow(
                    options = listOf(
                        "text" to if (isZh) "文生视频" else "Text to video",
                        "ti2vid" to if (isZh) "图生视频" else "Image to video",
                        "keyframes" to if (isZh) "首尾帧" else "Keyframes",
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                )
            }
            when (mode) {
                "ti2vid" -> PickerBlock(if (isZh) "图片" else "Image") {
                    FrameButton(
                        label = if (isZh) "参考图" else "Reference image",
                        value = firstFrame,
                        onPick = { pickImage(VideoImageTarget.FIRST) },
                        onClear = { firstFrame = VideoFrameUploadState() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isZh) "图生视频只上传一张参考图，提交前会先变成公网 URL。" else "Image-to-video uploads one reference image and converts it to a public URL before submit.",
                        color = c.subtext,
                        fontSize = 11.sp,
                    )
                }
                "keyframes" -> PickerBlock(if (isZh) "图片" else "Images") {
                    FrameButton(
                        label = if (isZh) "首帧" else "First frame",
                        value = firstFrame,
                        onPick = { pickImage(VideoImageTarget.FIRST) },
                        onClear = { firstFrame = VideoFrameUploadState() },
                    )
                    Spacer(Modifier.height(10.dp))
                    FrameButton(
                        label = if (isZh) "尾帧" else "Last frame",
                        value = lastFrame,
                        onPick = { pickImage(VideoImageTarget.LAST) },
                        onClear = { lastFrame = VideoFrameUploadState() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isZh) "首尾帧模式会上传两张图，视频接口只接收 URL。" else "Keyframe mode uploads two images because the video API accepts URLs only.",
                        color = c.subtext,
                        fontSize = 11.sp,
                    )
                }
            }
            PickerBlock(if (isZh) "画幅与时长" else "Aspect and Duration") {
                ChipRow(
                    options = listOf(
                        "9:16" to if (isZh) "竖屏" else "Portrait",
                        "16:9" to if (isZh) "横屏" else "Landscape",
                        "1:1" to if (isZh) "方形" else "Square",
                    ),
                    selected = aspectRatio,
                    onSelect = { aspectRatio = it },
                )
                Spacer(Modifier.height(10.dp))
                ChipRow(
                    options = listOf("5", "8", "10", "15", "18").map { it to if (isZh) "$it 秒" else "${it}s" },
                    selected = duration,
                    onSelect = { duration = it },
                )
            }
            Text(
                text = if (showAdvanced) {
                    if (isZh) "收起高级参数" else "Hide advanced"
                } else {
                    if (isZh) "高级参数" else "Advanced"
                },
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(0.7.dp, c.border, RoundedCornerShape(18.dp))
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
            if (showAdvanced) {
                PickerBlock(if (isZh) "高级" else "Advanced") {
                    VideoTextField(value = negativePrompt, onValueChange = { negativePrompt = it }, label = if (isZh) "反向提示词" else "Negative prompt", minLines = 2)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VideoTextField(value = seed, onValueChange = { seed = it }, label = "Seed", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                        VideoTextField(value = frameRate, onValueChange = { frameRate = it }, label = "FPS", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    VideoTextField(value = extraBody, onValueChange = { extraBody = it }, label = if (isZh) "额外 JSON" else "Extra JSON", minLines = 2)
                }
            }
            val framesReady = firstFrame.isReady && lastFrame.isReady
            val frameUploadFailed = firstFrame.error.isNotBlank() || lastFrame.error.isNotBlank()
            val frameRequirementMet = when (mode) {
                "ti2vid" -> firstFrame.remoteUrl.isNotBlank()
                "keyframes" -> firstFrame.remoteUrl.isNotBlank() && lastFrame.remoteUrl.isNotBlank()
                else -> true
            }
            PrimaryButton(
                text = when {
                    isRunning -> if (isZh) "生成中" else "Generating"
                    frameUploadFailed -> if (isZh) "图片上传失败" else "Image upload failed"
                    !framesReady -> if (isZh) "图片上传中" else "Uploading image"
                    !frameRequirementMet -> if (isZh) "请先选择图片" else "Choose an image first"
                    else -> if (isZh) "直接生成" else "Generate"
                },
                enabled = prompt.isNotBlank() && !isRunning && selectedGateway != null && framesReady && frameRequirementMet,
                onClick = {
                    onGenerate(
                        buildVideoGenerationRequest(
                            gateway = selectedGateway,
                            model = selectedModel,
                            prompt = prompt,
                            mode = mode,
                            aspectRatio = aspectRatio,
                            duration = duration,
                            firstFrame = firstFrame.remoteUrl,
                            lastFrame = lastFrame.remoteUrl,
                            negativePrompt = negativePrompt,
                            seed = seed,
                            frameRate = frameRate,
                            extraBody = extraBody,
                        )
                    )
                },
            )
            RecentTasks(videoTasks, refreshingIds, onRefreshTask, onDeleteTask)
        }
    }
}

private enum class VideoImageTarget { FIRST, LAST }

enum class VideoPromptAiAction { ENRICH, TRANSLATE }

private data class VideoFrameUploadState(
    val source: String = "",
    val remoteUrl: String = "",
    val isUploading: Boolean = false,
    val error: String = "",
) {
    val hasSelection: Boolean get() = source.isNotBlank() || remoteUrl.isNotBlank()
    val isReady: Boolean get() = !hasSelection || remoteUrl.isNotBlank()
}

private fun VideoFrameUploadState.withUploadResult(source: String, result: Result<String>): VideoFrameUploadState {
    if (this.source != source) return this
    return result.fold(
        onSuccess = { copy(remoteUrl = it, isUploading = false, error = "") },
        onFailure = { copy(remoteUrl = "", isUploading = false, error = it.message ?: "上传失败") },
    )
}

private fun GatewayConfig?.videoModelOptions(): List<String> {
    if (this == null) return emptyList()
    return listOfNotNull(
        capabilityModel("video"),
        model.takeIf { it.isNotBlank() },
    ).distinct()
}

@Composable
private fun Header(
    isRunning: Boolean,
    refreshingAll: Boolean,
    onBack: () -> Unit,
    onRefreshAll: () -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = c.text)
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isZh) "视频生成" else "Video Generation", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape)
                        .background(if (isRunning) Color(0xFFC7F43A) else c.subtext.copy(alpha = 0.45f))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isRunning) {
                        if (isZh) "生成链路运行中" else "Generation pipeline running"
                    } else {
                        if (isZh) "AI 翻译提示词后调用视频模型" else "AI translates the prompt, then calls the video model"
                    },
                    color = c.subtext,
                    fontSize = 11.sp,
                )
            }
        }
        IconButton(onClick = onRefreshAll, enabled = !refreshingAll) {
            if (refreshingAll) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.text)
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null, tint = c.text)
            }
        }
    }
}

@Composable
private fun Hero() {
    val isZh = LocalAppLanguage.current == "zh"
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0B0B0B)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Motion studio", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                if (isZh) "选参数，传图片，AI 翻译成英文后生成。" else "Choose settings, upload images, and generate video.",
                color = Color(0xFFA0A0A0),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PickerBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalClawColors.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = c.subtext, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (c.isDark) Color(0xFF101010) else Color(0xFFF7F7F5))
                .border(0.7.dp, c.border, RoundedCornerShape(18.dp))
                .padding(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            Chip(label = label, selected = selected == value, onClick = { onSelect(value) })
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalClawColors.current
    val bg = if (selected) c.text else Color.Transparent
    val fg = if (selected) {
        if (c.isDark) Color.Black else Color.White
    } else {
        c.text
    }
    Text(
        text = label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(0.7.dp, if (selected) bg else c.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

@Composable
private fun FrameButton(
    label: String,
    value: VideoFrameUploadState,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp))
                .background(if (!value.hasSelection) Color.Transparent else c.text.copy(alpha = 0.08f))
                .border(0.7.dp, c.border, RoundedCornerShape(15.dp))
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            if (value.isUploading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.text)
            } else {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    !value.hasSelection -> if (isZh) "未选择" else "Not selected"
                    value.isUploading -> if (isZh) "正在上传成 URL" else "Uploading as URL"
                    value.remoteUrl.isNotBlank() -> value.remoteUrl
                    value.error.isNotBlank() -> if (isZh) "上传失败：${value.error}" else "Upload failed: ${value.error}"
                    else -> if (isZh) "等待上传" else "Waiting to upload"
                },
                color = if (value.error.isNotBlank()) Color(0xFFB3261E) else c.subtext,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (value.hasSelection) {
            Text(if (isZh) "清除" else "Clear", color = c.subtext, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClear).padding(8.dp))
        }
    }
}

@Composable
private fun VideoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = LocalClawColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        minLines = minLines,
        maxLines = if (minLines > 1) 5 else 1,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
            focusedBorderColor = c.text,
            unfocusedBorderColor = c.border,
            focusedLabelColor = c.text,
            unfocusedLabelColor = c.subtext,
            cursorColor = c.text,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (enabled) c.text else c.subtext.copy(alpha = 0.28f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (c.isDark) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentTasks(
    tasks: List<VideoGenerationTaskEntity>,
    refreshingIds: Set<String>,
    onRefreshTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    val c = LocalClawColors.current
    val isZh = LocalAppLanguage.current == "zh"
    Column {
        Text(if (isZh) "最近任务" else "Recent Tasks", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        if (tasks.isEmpty()) {
            Text(if (isZh) "暂无视频任务" else "No video tasks yet", color = c.subtext, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            return
        }
        tasks.take(5).forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider(color = c.border, thickness = 0.5.dp)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.prompt, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(videoTaskStatusLabel(task, isZh), color = c.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onRefreshTask(task.taskId) }, enabled = task.taskId !in refreshingIds) {
                    if (task.taskId in refreshingIds) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.text)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = { onDeleteTask(task.taskId) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun videoTaskStatusLabel(task: VideoGenerationTaskEntity, isZh: Boolean): String = when {
    task.status == VideoTaskStatuses.RUNNING && isVideoDownloadUrlPending(task.errorMessage) -> if (isZh) "等待下载地址" else "Waiting for download URL"
    task.status == VideoTaskStatuses.SUBMITTED -> if (isZh) "生成中" else "Generating"
    task.status == VideoTaskStatuses.RUNNING -> if (isZh) "生成中" else "Generating"
    task.status == VideoTaskStatuses.TIMED_OUT -> if (isZh) "后台追踪中" else "Tracking in background"
    task.status == VideoTaskStatuses.COMPLETED -> if (isZh) "已生成" else "Generated"
    task.status == VideoTaskStatuses.DOWNLOADED -> if (isZh) "已下载" else "Downloaded"
    task.status == VideoTaskStatuses.FAILED -> if (isZh) "失败：${task.errorMessage.take(80)}" else "Failed: ${task.errorMessage.take(80)}"
    else -> task.status
}

@Composable
private fun SecondaryButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
            .border(0.8.dp, if (enabled) c.border else c.border.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) c.text else c.subtext.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun buildVideoGenerationRequest(
    gateway: GatewayConfig?,
    model: String,
    prompt: String,
    mode: String,
    aspectRatio: String,
    duration: String,
    firstFrame: String,
    lastFrame: String,
    negativePrompt: String,
    seed: String,
    frameRate: String,
    extraBody: String,
): VideoGenerationRequest = VideoGenerationRequest(
    gatewayId = gateway?.id.orEmpty(),
    gatewayName = gateway?.name.orEmpty(),
    model = model,
    prompt = prompt,
    mode = mode,
    aspectRatio = aspectRatio,
    duration = duration,
    firstFrameUrl = if (mode == "ti2vid" || mode == "keyframes") firstFrame else "",
    lastFrameUrl = if (mode == "keyframes") lastFrame else "",
    negativePrompt = negativePrompt,
    seed = seed,
    frameRate = frameRate,
    extraBody = extraBody,
)
