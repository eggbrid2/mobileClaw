package com.mobileclaw.ui.image

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.config.hasCapability
import com.mobileclaw.ui.LocalClawColors

@Composable
fun ImageGeneratorPage(
    isRunning: Boolean,
    promptAiRunning: Boolean,
    configSnapshot: ConfigSnapshot,
    previewBase64: String,
    previewPrompt: String,
    onBack: () -> Unit,
    onGenerate: (ImageGenerationRequest) -> Unit,
    onRewritePrompt: (String, ImagePromptAiAction, (String) -> Unit) -> Unit,
) {
    val c = LocalClawColors.current
    val gateways = remember(configSnapshot) {
        configSnapshot.gateways.filter { it.hasCapability("image") }.ifEmpty { configSnapshot.gateways }
    }
    var selectedGatewayId by remember { mutableStateOf(configSnapshot.activeGateway?.id.orEmpty()) }
    LaunchedEffect(configSnapshot.activeGatewayId, gateways) {
        if (selectedGatewayId.isBlank() || gateways.none { it.id == selectedGatewayId }) {
            selectedGatewayId = configSnapshot.activeGateway?.id ?: gateways.firstOrNull()?.id.orEmpty()
        }
    }
    val selectedGateway = gateways.firstOrNull { it.id == selectedGatewayId } ?: configSnapshot.activeGateway
    val modelOptions = remember(selectedGateway) { selectedGateway.imageModelOptions() }
    var selectedModel by remember { mutableStateOf("") }
    LaunchedEffect(selectedGateway?.id, modelOptions) {
        selectedModel = modelOptions.firstOrNull().orEmpty()
    }
    var prompt by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("1024x1024") }
    var quality by remember { mutableStateOf("auto") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (c.isDark) Color(0xFF050505) else Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Header(isRunning = isRunning, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero()
            PickerBlock("网关") {
                if (gateways.isEmpty()) Text("还没有配置网关", color = c.subtext, fontSize = 12.sp)
                else ChipRow(gateways.map { it.id to it.name }, selectedGatewayId) { selectedGatewayId = it }
            }
            PickerBlock("模型") {
                if (modelOptions.isEmpty()) Text("使用网关默认图片模型", color = c.subtext, fontSize = 12.sp)
                else ChipRow(modelOptions.map { it to it }, selectedModel) { selectedModel = it }
            }
            TextField(value = prompt, onValueChange = { prompt = it }, label = "想生成什么", minLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = if (promptAiRunning) "AI 处理中" else "AI 丰富",
                    enabled = prompt.isNotBlank() && !promptAiRunning && !isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { onRewritePrompt(prompt, ImagePromptAiAction.ENRICH) { prompt = it } },
                )
                SecondaryButton(
                    text = if (promptAiRunning) "AI 处理中" else "AI 翻译",
                    enabled = prompt.isNotBlank() && !promptAiRunning && !isRunning,
                    modifier = Modifier.weight(1f),
                    onClick = { onRewritePrompt(prompt, ImagePromptAiAction.TRANSLATE) { prompt = it } },
                )
            }
            PickerBlock("尺寸") {
                ChipRow(
                    options = listOf(
                        "1024x1024" to "方图",
                        "1024x1536" to "竖图",
                        "1536x1024" to "横图",
                        "auto" to "自动",
                    ),
                    selected = size,
                    onSelect = { size = it },
                )
            }
            PickerBlock("质量") {
                ChipRow(
                    options = listOf("auto" to "自动", "low" to "低", "medium" to "中", "high" to "高"),
                    selected = quality,
                    onSelect = { quality = it },
                )
            }
            PrimaryButton(
                text = if (isRunning) "生成中" else "直接生成",
                enabled = prompt.isNotBlank() && !isRunning && selectedGateway != null,
                onClick = {
                    onGenerate(
                        ImageGenerationRequest(
                            gatewayId = selectedGateway?.id.orEmpty(),
                            gatewayName = selectedGateway?.name.orEmpty(),
                            model = selectedModel,
                            prompt = prompt,
                            size = size,
                            quality = quality,
                        )
                    )
                },
            )
            ImagePreview(previewBase64 = previewBase64, previewPrompt = previewPrompt)
        }
    }
}

private fun GatewayConfig?.imageModelOptions(): List<String> {
    if (this == null) return emptyList()
    return listOfNotNull(capabilityModel("image"), model.takeIf { it.isNotBlank() }).distinct()
}

@Composable
private fun Header(isRunning: Boolean, onBack: () -> Unit) {
    val c = LocalClawColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = c.text) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("图片生成", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (isRunning) Color(0xFFC7F43A) else c.subtext.copy(alpha = 0.45f)))
                Spacer(Modifier.width(6.dp))
                Text(if (isRunning) "图片接口运行中" else "选择参数后直接调用图片模型", color = c.subtext, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun Hero() {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF0B0B0B)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Image studio", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("选网关、模型、尺寸和质量，直接生成图片。", color = Color(0xFFA0A0A0), fontSize = 12.sp)
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
private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, selected == value) { onSelect(value) } }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalClawColors.current
    val bg = if (selected) c.text else Color.Transparent
    val fg = if (selected) if (c.isDark) Color.Black else Color.White else c.text
    Text(
        text = label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(bg)
            .border(0.7.dp, if (selected) bg else c.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

@Composable
private fun TextField(value: String, onValueChange: (String) -> Unit, label: String, minLines: Int = 1) {
    val c = LocalClawColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        minLines = minLines,
        maxLines = if (minLines > 1) 5 else 1,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
        if (text == "生成中") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = if (c.isDark) Color.Black else Color.White)
        else Text(text, color = if (c.isDark) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalClawColors.current
    Box(
        modifier = modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .border(0.8.dp, if (enabled) c.border else c.border.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) c.text else c.subtext.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ImagePreview(previewBase64: String, previewPrompt: String) {
    if (previewBase64.isBlank()) return
    val c = LocalClawColors.current
    val bitmap = remember(previewBase64) {
        runCatching {
            val raw = previewBase64.substringAfter("base64,", previewBase64)
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("最新结果", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(0.7.dp, c.border, RoundedCornerShape(22.dp)),
            contentScale = ContentScale.FillWidth,
        )
        if (previewPrompt.isNotBlank()) {
            Text(previewPrompt, color = c.subtext, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
