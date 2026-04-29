package com.mobileclaw.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.perception.VirtualDisplayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private val PRESETS = listOf(
    Triple("OpenAI", "https://api.openai.com", "gpt-4o"),
    Triple("Groq", "https://api.groq.com/openai", "llama-3.3-70b-versatile"),
    Triple("Ollama", "http://localhost:11434/v1", "llama3.2"),
    Triple("Custom", "", ""),
)

private val LANGUAGES = listOf(
    "auto" to R.string.lang_auto,
    "zh"   to R.string.lang_zh,
    "en"   to R.string.lang_en,
    "ja"   to R.string.lang_ja,
)

@Composable
fun SettingsPage(
    config: Flow<ConfigSnapshot>,
    virtualDisplayManager: VirtualDisplayManager,
    vdTestResult: String?,
    privServerConnected: Boolean,
    onSave: (ConfigSnapshot) -> Unit,
    onBack: () -> Unit,
    onTestVirtualDisplay: () -> Unit,
    onCheckPrivServer: () -> Unit,
) {
    val c = LocalClawColors.current
    val snapshot by config.collectAsState(
        initial = ConfigSnapshot("", "", "gpt-4o", "text-embedding-3-small", "openai")
    )
    var endpoint  by remember(snapshot) { mutableStateOf(snapshot.endpoint) }
    var apiKey    by remember(snapshot) { mutableStateOf(snapshot.apiKey) }
    var model     by remember(snapshot) { mutableStateOf(snapshot.model) }
    var embModel  by remember(snapshot) { mutableStateOf(snapshot.embeddingModel) }
    var language  by remember(snapshot) { mutableStateOf(snapshot.language) }
    var darkTheme by remember(snapshot) { mutableStateOf(snapshot.darkTheme) }
    var accent    by remember(snapshot) { mutableStateOf(snapshot.accentColor) }

    val isValid = endpoint.isNotBlank() && apiKey.isNotBlank()

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back), tint = c.text)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.settings_title), color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.settings_subtitle), color = c.subtext, fontSize = 11.sp)
            }
            Button(
                onClick = {
                    onSave(ConfigSnapshot(endpoint.trim(), apiKey.trim(), model.trim(), embModel.trim(), "openai", language, darkTheme, accent))
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, disabledContainerColor = c.border),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.btn_save), color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
        }

        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Provider presets ────────────────────────────────────────────
            SettingsSection(stringResource(R.string.section_preset), c) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { (label, ep, mdl) ->
                        val active = ep.isNotEmpty() && endpoint == ep && (mdl.isEmpty() || model == mdl)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) c.accent.copy(alpha = 0.15f) else c.cardAlt)
                                .border(1.dp, if (active) c.accent.copy(alpha = 0.6f) else c.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (ep.isNotEmpty()) {
                                        endpoint = ep
                                        if (mdl.isNotEmpty()) model = mdl
                                    }
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (active) c.accent else c.subtext,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // ── Connection ──────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.section_connection), c) {
                ClawPageTextField(endpoint, { endpoint = it }, stringResource(R.string.field_endpoint), "https://api.openai.com", c)
                ClawPageTextField(apiKey, { apiKey = it }, stringResource(R.string.field_api_key), "sk-...", c, isSecret = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isValid) c.green.copy(alpha = 0.08f) else c.cardAlt,
                            RoundedCornerShape(8.dp),
                        )
                        .border(1.dp, if (isValid) c.green.copy(alpha = 0.3f) else c.border, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isValid) "✓" else "○", color = if (isValid) c.green else c.subtext, fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isValid) stringResource(R.string.status_configured) else stringResource(R.string.status_not_ready),
                        color = if (isValid) c.green else c.subtext,
                        fontSize = 13.sp,
                    )
                }
            }

            // ── Models ──────────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.section_models), c) {
                ClawPageTextField(model, { model = it }, stringResource(R.string.field_chat_model), "gpt-4o", c)
                ClawPageTextField(embModel, { embModel = it }, stringResource(R.string.field_embed_model), "text-embedding-3-small", c)
                Text(stringResource(R.string.embed_hint), color = c.subtext.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp)
            }

            // ── Language ────────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.section_language), c) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGUAGES.forEach { (code, resId) ->
                        val active = language == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) c.accent.copy(alpha = 0.15f) else c.cardAlt)
                                .border(1.dp, if (active) c.accent.copy(alpha = 0.6f) else c.border, RoundedCornerShape(8.dp))
                                .clickable { language = code }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(resId),
                                color = if (active) c.accent else c.subtext,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // ── Theme presets ────────────────────────────────────────────────
            SettingsSection(stringResource(R.string.section_theme), c) {
                // 3-column grid of preset cards
                ThemePresets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            val isActive = darkTheme == preset.darkTheme && accent == preset.accentColor
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) c.accent.copy(alpha = 0.12f) else c.cardAlt)
                                    .border(
                                        1.5.dp,
                                        if (isActive) c.accent.copy(alpha = 0.7f) else c.border,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { darkTheme = preset.darkTheme; accent = preset.accentColor }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                // Mini preview swatch
                                Box(
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 20.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(preset.previewBg)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.Center)
                                            .clip(CircleShape)
                                            .background(Color(preset.previewAccent)),
                                    )
                                }
                                Text(
                                    preset.name,
                                    color = if (isActive) c.accent else c.subtext,
                                    fontSize = 9.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                        // Fill remaining columns if last row is short
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── File Access ─────────────────────────────────────────────────
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val storageManager = remember { com.mobileclaw.config.UserStorageManager(ctx) }
            var hasFileAccess by remember { mutableStateOf(storageManager.hasAllFilesAccess()) }
            val fileAccessLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            ) { hasFileAccess = storageManager.hasAllFilesAccess() }

            SettingsSection("文件访问权限", c) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (hasFileAccess) c.green.copy(alpha = 0.07f) else c.cardAlt,
                            RoundedCornerShape(10.dp),
                        )
                        .border(
                            1.dp,
                            if (hasFileAccess) c.green.copy(alpha = 0.3f) else c.border,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasFileAccess) c.green else c.subtext.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (hasFileAccess) "已授权：可管理所有文件" else "未授权：无法访问用户文件",
                            color = if (hasFileAccess) c.green else c.subtext,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (hasFileAccess) "Agent 可读写 Downloads、Documents 等目录" else "点击授权后 Agent 可帮你管理、搜索和创建文件",
                            color = c.subtext,
                            fontSize = 11.sp,
                        )
                    }
                    if (!hasFileAccess) {
                        OutlinedButton(
                            onClick = { fileAccessLauncher.launch(storageManager.allFilesAccessSettingsIntent()) },
                            border = ButtonDefaults.outlinedButtonBorder,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
                        ) { Text("授权", fontSize = 12.sp) }
                    }
                }
            }

            // ── Virtual Display ─────────────────────────────────────────────
            SettingsSection(stringResource(R.string.section_virtual_display), c) {
                val isRunning = virtualDisplayManager.isRunning
                val displayId = virtualDisplayManager.displayId
                val isOk  = vdTestResult?.startsWith("ok:") == true
                val isFail = vdTestResult?.startsWith("error:") == true

                // Status + Test row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when { isOk -> c.green.copy(alpha = 0.07f); isFail -> c.red.copy(alpha = 0.07f); else -> c.cardAlt },
                            RoundedCornerShape(10.dp),
                        )
                        .border(
                            1.dp,
                            when { isOk -> c.green.copy(alpha = 0.3f); isFail -> c.red.copy(alpha = 0.3f); else -> c.border },
                            RoundedCornerShape(10.dp),
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(when { isOk || isRunning -> c.green; isFail -> c.red; else -> c.subtext.copy(alpha = 0.4f) }),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                isOk   -> "可用 (Display #${vdTestResult!!.substringAfter(":")})"
                                isFail -> "不可用"
                                isRunning -> "运行中 (Display #$displayId)"
                                else   -> "未启动"
                            },
                            color = when { isOk || isRunning -> c.green; isFail -> c.red; else -> c.subtext },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (isFail) {
                            Text(
                                vdTestResult!!.substringAfter(":").take(100),
                                color = c.red.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onTestVirtualDisplay,
                        border = ButtonDefaults.outlinedButtonBorder,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
                    ) {
                        Text("检测", fontSize = 12.sp)
                    }
                }

                // Privileged server card
                PrivServerCard(
                    connected = privServerConnected,
                    packageName = "com.mobileclaw",
                    onCheckServer = onCheckPrivServer,
                    c = c,
                )

                // Setup guide (auto-expands on failure, collapses on success)
                VdSetupGuide(c = c, testPassed = isOk)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    c: ClawColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = c.subtext.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        content()
    }
}

// ── Privileged Server Card ────────────────────────────────────────────────────

@Composable
private fun PrivServerCard(
    connected: Boolean,
    packageName: String,
    onCheckServer: () -> Unit,
    c: ClawColors,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    val activationCmd = remember(packageName) {
        // Single quotes: prevents local shell (zsh/bash on Mac/Linux) from expanding $(...).
        // The device's own shell evaluates pm path correctly.
        // Redirect stdin/stdout/stderr instead of nohup — nohup is unavailable on some Android shells.
        "adb shell 'CLASSPATH=\$(pm path $packageName | cut -d: -f2) /system/bin/app_process / com.mobileclaw.server.PrivilegedServer </dev/null >/dev/null 2>&1 &'"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (connected) c.green.copy(alpha = 0.07f) else c.cardAlt,
                RoundedCornerShape(10.dp),
            )
            .border(1.dp, if (connected) c.green.copy(alpha = 0.3f) else c.border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Status row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(if (connected) c.green else c.subtext.copy(alpha = 0.4f))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (connected) "特权服务已激活" else "特权服务未激活",
                color = if (connected) c.green else c.subtext,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onCheckServer,
                border = ButtonDefaults.outlinedButtonBorder,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
            ) {
                Text("检测", fontSize = 12.sp)
            }
        }

        // Activation guide (only when not connected)
        AnimatedVisibility(
            visible = !connected,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "无需 Root — 在电脑终端执行以下命令激活（重启手机后需重新执行一次）:",
                    color = c.subtext,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bg, RoundedCornerShape(7.dp))
                        .border(1.dp, c.border.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        activationCmd,
                        color = c.green,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (copied) c.green.copy(alpha = 0.15f) else c.accent.copy(alpha = 0.12f))
                            .clickable { clipboardManager.setText(AnnotatedString(activationCmd)); copied = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (copied) "✓ 已复制" else "一键复制",
                            color = if (copied) c.green else c.accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "激活后点击「检测」确认状态。激活无需 Root，且不影响系统安全。",
                    color = c.subtext.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

// ── Virtual Display Setup Guide ───────────────────────────────────────────────

private data class VdRomInfo(
    val name: String,
    val manualSteps: List<String>,
    val adbCommands: List<String>,
    val adbNote: String = "",
)

private fun detectVdRomInfo(): VdRomInfo {
    val brand = Build.BRAND.lowercase()
    val mfr   = Build.MANUFACTURER.lowercase()
    return when {
        brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") || mfr.contains("oppo") -> VdRomInfo(
            name = "ColorOS（OPPO / Realme / OnePlus）",
            manualSteps = listOf(
                "ColorOS 12+ 在框架层拦截虚拟屏启动，系统设置项无法解决",
                "方案① Root: 安装 Magisk 获取 Root，应用将自动通过 su 调用",
                "方案② Shizuku (推荐，无需 Root):",
                "  a. 手机安装 Shizuku app（搜索「Shizuku」）",
                "  b. 打开 Shizuku，它会自动生成激活脚本",
                "  c. 连接电脑，执行下方 ADB 命令激活",
                "  d. 手机上 Shizuku 弹窗确认 → 授权本应用 → 重试",
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
                "adb shell settings put global force_desktop_mode_on_external_displays 1",
                "# ↓ 安装并打开 Shizuku app 后再执行此命令",
                "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
            ),
            adbNote = "ColorOS 安全策略硬拦截，需 Root 或 Shizuku",
        )
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") || mfr.contains("xiaomi") -> VdRomInfo(
            name = "MIUI / HyperOS（Xiaomi / Redmi / POCO）",
            manualSteps = listOf(
                "设置 → 我的设备 → 全部参数，连点「MIUI 版本」7 次",
                "设置 → 更多设置 → 开发者选项",
                "开启【自由窗口】",
                "返回本应用，点击「检测」按钮验证",
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
            ),
        )
        brand.contains("huawei") || brand.contains("honor") -> VdRomInfo(
            name = "EMUI / HarmonyOS（Huawei / Honor）",
            manualSteps = listOf(
                "设置 → 关于手机 → 版本号，连点 7 次",
                "设置 → 系统 → 开发者选项",
                "开启【多窗口】和【自由窗口】",
                "返回本应用，点击「检测」按钮验证",
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
            ),
        )
        brand.contains("vivo") -> VdRomInfo(
            name = "OriginOS / FuntouchOS（Vivo）",
            manualSteps = listOf(
                "设置 → 通用 → 关于手机 → 版本号，连点 7 次",
                "设置 → 通用 → 开发者选项",
                "开启【多任务显示】",
                "返回本应用，点击「检测」按钮验证",
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
            ),
        )
        brand.contains("samsung") || mfr.contains("samsung") -> VdRomInfo(
            name = "One UI（Samsung）",
            manualSteps = listOf(
                "设置 → 关于手机 → 软件信息 → 版本号，连点 7 次",
                "设置 → 开发者选项",
                "开启【强制活动可调整大小】和【自由窗口模式】",
                "返回本应用，点击「检测」按钮验证",
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
            ),
        )
        else -> VdRomInfo(
            name = "Android（${Build.BRAND}）",
            manualSteps = listOf(
                "设置 → 关于手机 → 版本号，连点 7 次",
                "开发者选项 → 开启【自由窗口】或【多窗口】",
                "返回本应用，点击「检测」按钮验证",
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
            ),
        )
    }
}

@Composable
private fun VdSetupGuide(c: ClawColors, testPassed: Boolean) {
    val romInfo = remember { detectVdRomInfo() }
    var expanded by remember(testPassed) { mutableStateOf(!testPassed) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Collapsible header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 7.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⚙ 设置向导",
                color = c.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "▲" else "▼", color = c.subtext, fontSize = 9.sp)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.cardAlt, RoundedCornerShape(10.dp))
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ROM badge
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📱", fontSize = 13.sp)
                    Text(romInfo.name, color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Manual steps
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("手动设置步骤:", color = c.subtext, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                    romInfo.manualSteps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}.", color = c.accent, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                            Text(step, color = c.text, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }

                HorizontalDivider(color = c.border, thickness = 0.5.dp)

                // ADB commands
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (romInfo.adbNote.isNotEmpty()) "ADB 命令（${romInfo.adbNote}）:"
                        else "如手动设置无效，连接电脑执行 ADB 命令:",
                        color = c.subtext,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                    romInfo.adbCommands.forEach { cmd ->
                        if (cmd.startsWith("#")) {
                            // Section label, not a runnable command
                            Text(
                                cmd.removePrefix("# "),
                                color = c.accent.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        } else {
                            AdbCommandRow(cmd = cmd, c = c)
                        }
                    }
                }

                Text(
                    "执行命令后无需重启手机，直接点击上方「检测」按钮验证。",
                    color = c.subtext.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun AdbCommandRow(cmd: String, c: ClawColors) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg, RoundedCornerShape(7.dp))
            .border(1.dp, c.border.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            cmd,
            color = c.green,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(if (copied) c.green.copy(alpha = 0.15f) else c.accent.copy(alpha = 0.12f))
                .clickable { clipboardManager.setText(AnnotatedString(cmd)); copied = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                if (copied) "✓ 已复制" else "复制",
                color = if (copied) c.green else c.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClawPageTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    c: ClawColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isSecret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = c.subtext, fontSize = 11.sp) },
        placeholder = { Text(placeholder, color = c.subtext.copy(alpha = 0.4f), fontSize = 12.sp) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        visualTransformation = if (isSecret && value.length > 8) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent.copy(alpha = 0.7f),
            unfocusedBorderColor = c.border,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
            cursorColor = c.accent,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedLabelColor = c.accent,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
    )
}
