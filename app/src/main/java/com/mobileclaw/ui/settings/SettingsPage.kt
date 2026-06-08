package com.mobileclaw.ui.settings

import android.os.Build
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.JsonObject
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.mobileclaw.config.CacheCategory
import com.mobileclaw.config.CacheCleaner
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayCapabilityConfig
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.capabilityModel
import com.mobileclaw.config.supportsCapabilityMultimodal
import com.mobileclaw.llm.LocalModelInfo
import com.mobileclaw.llm.OpenAiGateway
import com.mobileclaw.memory.db.VideoGenerationTaskEntity
import com.mobileclaw.perception.VirtualDisplayManager
import com.mobileclaw.ui.ClawColors
import com.mobileclaw.ui.ClawIconTile
import com.mobileclaw.ui.ClawPageHeader
import com.mobileclaw.ui.ClawSymbolIcon
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.ui.ThemePresets
import com.mobileclaw.ui.common.openFileAttachment
import com.mobileclaw.ui.common.VideoAttachmentCard
import com.mobileclaw.skill.SkillAttachment
import com.mobileclaw.skill.builtin.VideoTaskStatuses
import com.mobileclaw.skill.builtin.VIDEO_DOWNLOAD_URL_PENDING_MESSAGE
import com.mobileclaw.skill.builtin.isVideoDownloadUrlPending
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.mobileclaw.str

// Gateway presets are grouped by actual compatibility so the quick-add UI can
// expose real providers first and leave non-standard coding products as templates.
private data class GatewayPreset(
    val name: String,
    val endpoint: String,
    val model: String,
    val embeddingModel: String = "text-embedding-3-small",
    val imageModel: String = "",
    val videoModel: String = "",
    val supportsMultimodal: Boolean = true,
    val hint: String = "",
    val group: GatewayPresetGroup = GatewayPresetGroup.DIRECT,
)

private enum class GatewayPresetGroup {
    DIRECT,
    TEMPLATE,
}

private val GATEWAY_CAPABILITY_TYPES = listOf("chat", "embedding", "image", "video")

private val GATEWAY_PRESETS = listOf(
    GatewayPreset(
        name = "OpenAI / GPT",
        endpoint = "https://api.openai.com",
        model = "gpt-4o",
        imageModel = "gpt-image-1",
        hint = "Official OpenAI-compatible endpoint. Good default for text, vision, and tools.",
    ),
    GatewayPreset(
        name = "Qwen / DashScope",
        endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model = "qwen-plus",
        embeddingModel = "text-embedding-v4",
        hint = "Alibaba DashScope OpenAI-compatible base URL.",
    ),
    GatewayPreset(
        name = "Volcengine Ark",
        endpoint = "https://ark.cn-beijing.volces.com/api/v3",
        model = "doubao-seed-1-6-250615",
        hint = "Volcengine Ark OpenAI-compatible endpoint on /api/v3.",
    ),
    GatewayPreset(
        name = "DeepSeek",
        endpoint = "https://api.deepseek.com",
        model = "deepseek-chat",
        hint = "Official DeepSeek OpenAI-compatible API base.",
        supportsMultimodal = false,
    ),
    GatewayPreset(
        name = "Moonshot / Kimi",
        endpoint = "https://api.moonshot.cn/v1",
        model = "kimi-k2.5",
        hint = "Kimi official OpenAI-compatible API with multimodal support.",
    ),
    GatewayPreset(
        name = "Zhipu / GLM",
        endpoint = "https://open.bigmodel.cn/api/paas/v4",
        model = "glm-5.1",
        hint = "Zhipu OpenAI-compatible Base URL for general API scenarios.",
    ),
    GatewayPreset(
        name = "Groq",
        endpoint = "https://api.groq.com/openai",
        model = "llama-3.3-70b-versatile",
        supportsMultimodal = false,
        hint = "Fast OpenAI-compatible text gateway.",
    ),
    GatewayPreset(
        name = "Ollama",
        endpoint = "http://localhost:11434/v1",
        model = "llama3.2",
        supportsMultimodal = false,
        hint = "Local OpenAI-compatible runtime on the device or LAN.",
    ),
    GatewayPreset(
        name = "Agnes",
        endpoint = "https://apihub.agnes-ai.com",
        model = "agnes-2.0-flash",
        imageModel = "agnes-image-2.0-flash",
        videoModel = "agnes-video-v2.0",
        hint = "Template for Agnes APIHub. Recommended defaults: agnes-2.0-flash, agnes-image-2.0-flash, agnes-video-v2.0.",
        group = GatewayPresetGroup.TEMPLATE,
    ),
    GatewayPreset(
        name = "Claude Code / CC",
        endpoint = "",
        model = "claude-sonnet-4",
        hint = "Template only. Use this when you have a compatible relay or coding gateway endpoint.",
        group = GatewayPresetGroup.TEMPLATE,
    ),
    GatewayPreset(
        name = "Cursor",
        endpoint = "",
        model = "gpt-4.1",
        hint = "Template only. Paste your compatible relay or workspace gateway endpoint after selecting it.",
        group = GatewayPresetGroup.TEMPLATE,
    ),
    GatewayPreset(
        name = "Custom",
        endpoint = "",
        model = "gpt-4o",
        hint = "Start from a blank OpenAI-style gateway.",
        group = GatewayPresetGroup.TEMPLATE,
    ),
)

private val LANGUAGES = listOf(
    "zh"   to R.string.lang_zh,
    "en"   to R.string.lang_en,
)

private enum class SettingsSub { GATEWAY, LOCAL_MODEL, APPEARANCE, PERMISSIONS, VIRTUAL_DISPLAY, CODEX_DESKTOP, CACHE, TASKS }

private const val CODEX_DESKTOP_ENDPOINT_KEY = "codex_desktop_endpoint"
private const val CODEX_DESKTOP_TOKEN_KEY = "codex_desktop_token"
private const val CODEX_DESKTOP_CWD_KEY = "codex_desktop_cwd"
private const val CODEX_DESKTOP_MODEL_KEY = "codex_desktop_model"
private const val CODEX_DESKTOP_PROVIDER_KEY = "codex_desktop_provider"
private const val CODEX_DESKTOP_APPROVAL_KEY = "codex_desktop_approval"
private const val CODEX_DESKTOP_SANDBOX_KEY = "codex_desktop_sandbox"

@Composable
private fun gatewayPresetTypeLabel(preset: GatewayPreset): String = when {
    preset.group == GatewayPresetGroup.TEMPLATE -> str(R.string.gateway_preset_template)
    preset.supportsMultimodal -> str(R.string.gateway_preset_multimodal)
    else -> str(R.string.gateway_preset_text_only)
}

@Composable
private fun gatewayCapabilityTypeLabel(type: String): String = when (type) {
    "chat" -> str(R.string.gateway_capability_chat)
    "embedding" -> str(R.string.gateway_capability_embedding)
    "image" -> str(R.string.gateway_capability_image)
    "video" -> str(R.string.gateway_capability_video)
    else -> type
}

@Composable
private fun gatewayHostLabel(endpoint: String): String =
    endpoint.removePrefix("https://").removePrefix("http://").trimEnd('/').ifBlank { str(R.string.status_not_configured) }

@Composable
private fun GatewayPill(
    text: String,
    c: ClawColors,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) c.text else c.cardAlt)
            .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (active) c.bg else c.text,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun buildPresetCapabilities(
    preset: GatewayPreset,
    fallbackEmbeddingModel: String = "text-embedding-3-small",
): List<GatewayCapabilityConfig> = buildList {
    add(GatewayCapabilityConfig(type = "chat", model = preset.model))
    add(
        GatewayCapabilityConfig(
            type = "embedding",
            model = preset.embeddingModel.ifBlank { fallbackEmbeddingModel },
        )
    )
    if (preset.imageModel.isNotBlank()) {
        add(GatewayCapabilityConfig(type = "image", model = preset.imageModel))
    }
    if (preset.videoModel.isNotBlank()) {
        add(GatewayCapabilityConfig(type = "video", model = preset.videoModel))
    }
}

private fun String.normalizedModelKey(): String =
    lowercase().replace('_', '-').replace(' ', '-')

private fun chooseRecommendedModel(
    type: String,
    preset: GatewayPreset,
    fetchedModels: List<String>,
): String? {
    if (fetchedModels.isEmpty()) return null
    val normalized = fetchedModels.associateBy { it.normalizedModelKey() }
    val preferred = when (type) {
        "chat" -> listOf(preset.model)
        "embedding" -> listOf(preset.embeddingModel, "text-embedding-3-small", "text-embedding-v4")
        "image" -> listOf(preset.imageModel, "agnes-image-2.0-flash", "agnes-image-2.1-flash", "gpt-image-1", "wanx2.1-t2i-turbo", "flux-dev")
        "video" -> listOf(preset.videoModel, "agnes-video-v2.0", "agnes-video-v1.2", "kling-v1", "veo-3")
        else -> emptyList()
    }.map { it.normalizedModelKey() }.filter { it.isNotBlank() }
    preferred.firstNotNullOfOrNull { normalized[it] }?.let { return it }

    val typeKeywords = when (type) {
        "chat" -> listOf("gpt", "qwen", "deepseek", "kimi", "glm", "claude", "llama", "doubao")
        "embedding" -> listOf("embedding", "embed")
        "image" -> listOf("image", "wanx", "flux", "sdxl", "vision-image")
        "video" -> listOf("video", "kling", "veo", "movie")
        else -> emptyList()
    }
    return fetchedModels.firstOrNull { candidate ->
        val key = candidate.normalizedModelKey()
        typeKeywords.any { it in key }
    }
}

private fun applyRecommendedModelsToCapabilities(
    capabilities: List<GatewayCapabilityConfig>,
    preset: GatewayPreset,
    fetchedModels: List<String>,
): List<GatewayCapabilityConfig> = capabilities.map { capability ->
    val recommended = chooseRecommendedModel(capability.type, preset, fetchedModels)
    if (recommended.isNullOrBlank()) capability else capability.copy(model = recommended)
}

private fun hydrateCapabilityConnections(
    capabilities: List<GatewayCapabilityConfig>,
    baseEndpoint: String,
    baseApiKey: String,
    existing: List<GatewayCapabilityConfig> = emptyList(),
): List<GatewayCapabilityConfig> {
    val existingByType = existing.associateBy { it.type.trim().lowercase() }
    val normalizedBaseEndpoint = baseEndpoint.trim()
    val normalizedBaseApiKey = baseApiKey.trim()
    return capabilities.map { capability ->
        val normalizedType = capability.type.trim().lowercase()
        val previous = existingByType[normalizedType]
        capability.copy(
            type = normalizedType,
            endpoint = capability.endpoint.trim()
                .ifBlank { previous?.endpoint?.trim().orEmpty() }
                .ifBlank { normalizedBaseEndpoint },
            apiKey = capability.apiKey.trim()
                .ifBlank { previous?.apiKey?.trim().orEmpty() }
                .ifBlank { normalizedBaseApiKey },
        )
    }
}

private fun videoTaskStatusLabel(task: VideoGenerationTaskEntity): String =
    when {
        task.status == VideoTaskStatuses.RUNNING && isVideoDownloadUrlPending(task.errorMessage) -> "等待下载地址"
        task.status == VideoTaskStatuses.SUBMITTED -> "生成中"
        task.status == VideoTaskStatuses.RUNNING -> "生成中"
        task.status == VideoTaskStatuses.TIMED_OUT -> "生成中"
        task.status == VideoTaskStatuses.COMPLETED -> "已生成"
        task.status == VideoTaskStatuses.DOWNLOADED -> "已生成"
        task.status == VideoTaskStatuses.FAILED -> "失败"
        else -> task.status
    }

@Composable
fun SettingsPage(
    config: Flow<ConfigSnapshot>,
    virtualDisplayManager: VirtualDisplayManager,
    vdTestResult: String?,
    privServerConnected: Boolean,
    onSave: (ConfigSnapshot) -> Unit,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onTestVirtualDisplay: () -> Unit,
    onCheckPrivServer: () -> Unit,
    localModels: List<LocalModelInfo>,
    onLocalModelEnabled: (Boolean) -> Unit,
    onLocalNativeOnly: (Boolean) -> Unit,
    onSelectLocalModel: (String) -> Unit,
    onDownloadLocalModel: (String, String, String) -> Unit,
    onImportLocalModel: (String, android.net.Uri) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
    videoTasks: List<VideoGenerationTaskEntity>,
    videoTaskRefreshingIds: Set<String>,
    videoTasksRefreshing: Boolean,
    onRefreshVideoTask: (String) -> Unit,
    onRefreshPendingVideoTasks: () -> Unit,
    onDeleteVideoTask: (String) -> Unit,
    launchGatewaySetup: Boolean = false,
    onLaunchGatewaySetupConsumed: () -> Unit = {},
) {
    val c = LocalClawColors.current
    val context = LocalContext.current
    val userConfig = remember(context) { com.mobileclaw.config.UserConfig(context) }
    val userConfigEntries by userConfig.entriesFlow.collectAsState(initial = emptyMap())
    val snapshot by config.collectAsState(initial = ConfigSnapshot())
    var gateways by remember(snapshot.gateways) { mutableStateOf(snapshot.gateways) }
    var activeGatewayId by remember(snapshot.activeGatewayId) { mutableStateOf(snapshot.activeGatewayId) }
    var language  by remember(snapshot.language) { mutableStateOf(snapshot.language) }
    var darkTheme by remember(snapshot.darkTheme) { mutableStateOf(snapshot.darkTheme) }
    var accent    by remember(snapshot.accentColor) { mutableStateOf(snapshot.accentColor) }
    var localEnabled by remember(snapshot.localModelEnabled) { mutableStateOf(snapshot.localModelEnabled) }
    var localNativeOnly by remember(snapshot.localNativeOnly) { mutableStateOf(snapshot.localNativeOnly) }

    var subPage by remember { mutableStateOf<SettingsSub?>(null) }

    LaunchedEffect(launchGatewaySetup) {
        if (launchGatewaySetup) {
            subPage = SettingsSub.GATEWAY
            onLaunchGatewaySetupConsumed()
        }
    }

    val currentSnapshot = {
        snapshot.copy(
            gateways = gateways,
            activeGatewayId = activeGatewayId,
            language = language,
            darkTheme = darkTheme,
            accentColor = accent,
            uiStyle = "classic",
            localModelEnabled = localEnabled,
            localNativeOnly = localNativeOnly,
        )
    }

    BackHandler {
        if (subPage != null) subPage = null else onBack()
    }

    val activeGateway = gateways.find { it.id == activeGatewayId } ?: gateways.firstOrNull()

    if (subPage == null) {
        // ── Hub list ─────────────────────────────────────────────────────────
        Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
            ClawPageHeader(title = str(R.string.drawer_settings), onBack = onBack)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val isConfigured = activeGateway != null && activeGateway.endpoint.isNotBlank() && activeGateway.apiKey.isNotBlank()
                val vdRunning = virtualDisplayManager.isRunning
                val codexConfigured = userConfigEntries[CODEX_DESKTOP_ENDPOINT_KEY]?.value.orEmpty().isNotBlank() &&
                    userConfigEntries[CODEX_DESKTOP_TOKEN_KEY]?.value.orEmpty().isNotBlank()

                SettingsHubCard(c) {
                    SettingsCategoryRow(
                        iconKey = "gateway",
                        title = str(R.string.settings_849b48),
                        subtitle = if (gateways.isEmpty()) str(R.string.status_not_configured)
                                   else str(R.string.gateways_status, gateways.size, activeGateway?.name ?: "-"),
                        statusOk = isConfigured,
                        c = c,
                    ) { subPage = SettingsSub.GATEWAY }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    val activeLocal = localModels.firstOrNull { it.id == snapshot.localModelId } ?: localModels.firstOrNull()
                    SettingsCategoryRow(
                        iconKey = "model",
                        title = str(R.string.local_model_title),
                        subtitle = when {
                            activeLocal == null -> str(R.string.status_not_configured)
                            localEnabled && activeLocal.installed -> str(R.string.local_model_enabled_status, activeLocal.name)
                            activeLocal.installed -> str(R.string.local_model_installed_status, activeLocal.name)
                            else -> str(R.string.local_model_not_downloaded_status, activeLocal.name)
                        },
                        statusOk = activeLocal?.installed == true && localEnabled,
                        c = c,
                    ) { subPage = SettingsSub.LOCAL_MODEL }
                }

                SettingsHubCard(c) {
                    SettingsCategoryRow(
                        iconKey = "appearance",
                        title = str(R.string.settings_ce650e),
                        subtitle = if (darkTheme) str(R.string.settings_theme_night_short) else str(R.string.settings_theme_day_short),
                        statusOk = true,
                        c = c,
                    ) { subPage = SettingsSub.APPEARANCE }
                }

                SettingsHubCard(c) {
                    val ctx = LocalContext.current
                    val storageManager = remember { com.mobileclaw.config.UserStorageManager(ctx) }
                    val hasFileAccess = remember { storageManager.hasAllFilesAccess() }
                    SettingsCategoryRow(
                        iconKey = "permissions",
                        title = str(R.string.settings_permissions),
                        subtitle = if (hasFileAccess) str(R.string.settings_done) else str(R.string.settings_not),
                        statusOk = hasFileAccess,
                        c = c,
                    ) { subPage = SettingsSub.PERMISSIONS }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    SettingsCategoryRow(
                        iconKey = "desktop",
                        title = str(R.string.section_virtual_display),
                        subtitle = when {
                            vdTestResult?.startsWith("ok:") == true -> str(R.string.settings_ad6b70)
                            vdRunning -> str(R.string.settings_d679ae)
                            else -> str(R.string.settings_not_2)
                        },
                        statusOk = vdTestResult?.startsWith("ok:") == true || vdRunning,
                        c = c,
                    ) { subPage = SettingsSub.VIRTUAL_DISPLAY }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    SettingsCategoryRow(
                        iconKey = "role:coder",
                        title = "Codex 桥接",
                        subtitle = if (codexConfigured) "已配置电脑端桥接" else "连接电脑上的 Codex CLI",
                        statusOk = codexConfigured,
                        c = c,
                    ) { subPage = SettingsSub.CODEX_DESKTOP }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    SettingsCategoryRow(
                        iconKey = "cache",
                        title = str(R.string.cache_title),
                        subtitle = str(R.string.cache_subtitle),
                        statusOk = true,
                        c = c,
                    ) { subPage = SettingsSub.CACHE }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    SettingsCategoryRow(
                        iconKey = "video",
                        title = "长任务",
                        subtitle = if (videoTasks.isEmpty()) "暂无视频长任务" else "共有 ${videoTasks.size} 条视频生成任务",
                        statusOk = videoTasks.any { it.status == VideoTaskStatuses.DOWNLOADED || it.status == VideoTaskStatuses.COMPLETED },
                        c = c,
                    ) { subPage = SettingsSub.TASKS }
                }
                SettingsHubCard(c) {
                    SettingsCategoryRow(
                        iconKey = "console",
                        title = str(R.string.workspace_title),
                        subtitle = str(R.string.workspace_settings_subtitle),
                        statusOk = true,
                        c = c,
                    ) { onOpenWorkspace() }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    SettingsCategoryRow(
                        iconKey = "help",
                        title = str(R.string.help_9a2407),
                        subtitle = str(R.string.settings_help_entry_subtitle),
                        statusOk = true,
                        c = c,
                    ) { onOpenHelp() }
                }
            }
        }
    } else {
        // ── Sub-pages ────────────────────────────────────────────────────────
        when (subPage) {
            SettingsSub.GATEWAY -> GatewayListSubPage(
                gateways = gateways,
                activeGatewayId = activeGatewayId,
                c = c,
                onBack = { subPage = null },
                onSave = { newList, newActiveId ->
                    gateways = newList
                    activeGatewayId = newActiveId
                    onSave(currentSnapshot())
                },
            )
            SettingsSub.APPEARANCE -> AppearanceSubPage(
                darkTheme = darkTheme, onDarkTheme = { darkTheme = it },
                accent = accent, onAccent = { accent = it },
                language = language, onLanguage = { language = it },
                c = c, onBack = { subPage = null },
                onSave = { onSave(currentSnapshot()); subPage = null },
            )
            SettingsSub.LOCAL_MODEL -> LocalModelSubPage(
                models = localModels,
                enabled = localEnabled,
                nativeOnly = localNativeOnly,
                selectedModelId = snapshot.localModelId,
                c = c,
                onBack = { subPage = null },
                onEnabled = {
                    localEnabled = it
                    if (!it) localNativeOnly = false
                    onLocalModelEnabled(it)
                    onSave(currentSnapshot())
                },
                onNativeOnly = {
                    localNativeOnly = it
                    if (it) localEnabled = true
                    onLocalNativeOnly(it)
                    onSave(currentSnapshot())
                },
                onSelect = onSelectLocalModel,
                onDownload = onDownloadLocalModel,
                onImport = onImportLocalModel,
                onDelete = onDeleteLocalModel,
            )
            SettingsSub.PERMISSIONS -> PermissionsSubPage(c = c, onBack = { subPage = null })
            SettingsSub.VIRTUAL_DISPLAY -> VirtualDisplaySubPage(
                virtualDisplayManager = virtualDisplayManager,
                vdTestResult = vdTestResult,
                privServerConnected = privServerConnected,
                c = c,
                onBack = { subPage = null },
                onTestVirtualDisplay = onTestVirtualDisplay,
                onCheckPrivServer = onCheckPrivServer,
            )
            SettingsSub.CODEX_DESKTOP -> CodexDesktopSubPage(
                userConfig = userConfig,
                c = c,
                onBack = { subPage = null },
            )
            SettingsSub.CACHE -> CacheSubPage(c = c, onBack = { subPage = null })
            SettingsSub.TASKS -> VideoTasksSubPage(
                tasks = videoTasks,
                refreshingIds = videoTaskRefreshingIds,
                refreshingAll = videoTasksRefreshing,
                c = c,
                onBack = { subPage = null },
                onRefreshTask = onRefreshVideoTask,
                onRefreshAll = onRefreshPendingVideoTasks,
                onDeleteTask = onDeleteVideoTask,
            )
            else -> Unit
        }
    }
}

// ── Hub composables ───────────────────────────────────────────────────────────

@Composable
private fun SettingsHubCard(c: ClawColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.10f else 0.035f),
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.14f else 0.06f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (c.isDark) 0.08f else 0.70f),
                        Color.White.copy(alpha = if (c.isDark) 0.04f else 0.42f),
                    )
                )
            )
            .border(0.7.dp, Color.White.copy(alpha = if (c.isDark) 0.12f else 0.68f), RoundedCornerShape(24.dp)),
        content = content,
    )
}

private fun settingsWorkbenchBrush(c: ClawColors): Brush =
    if (c.isDark) {
        Brush.verticalGradient(listOf(Color(0xFF080807), Color(0xFF10100E), Color(0xFF080807)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFCF8), Color(0xFFF8F9F6), Color(0xFFF7F8F5)))
    }

@Composable
private fun SettingsCategoryRow(
    iconKey: String,
    title: String,
    subtitle: String,
    statusOk: Boolean,
    c: ClawColors,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha = if (c.isDark) 0.08f else 0.58f))
                .border(0.6.dp, Color.White.copy(alpha = if (c.isDark) 0.12f else 0.58f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ClawSymbolIcon(iconKey, tint = c.text.copy(alpha = 0.78f), modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = if (statusOk) c.text.copy(alpha = 0.48f) else c.red.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = c.subtext.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Sub-pages ─────────────────────────────────────────────────────────────────

@Composable
private fun GatewayListSubPage(
    gateways: List<GatewayConfig>,
    activeGatewayId: String?,
    c: ClawColors,
    onBack: () -> Unit,
    onSave: (List<GatewayConfig>, String?) -> Unit,
) {
    var list by remember(gateways) { mutableStateOf(gateways) }
    var activeId by remember(activeGatewayId) { mutableStateOf(activeGatewayId) }
    var editingGateway by remember { mutableStateOf<GatewayConfig?>(null) }
    var deleteTarget by remember { mutableStateOf<GatewayConfig?>(null) }

    if (editingGateway != null) {
        GatewayEditorSubPage(
            gateway = editingGateway!!,
            c = c,
            onBack = { editingGateway = null },
            onSave = { updated ->
                list = if (list.any { it.id == updated.id }) {
                    list.map { if (it.id == updated.id) updated else it }
                } else {
                    list + updated
                }
                if (activeId == null) activeId = updated.id
                onSave(list, activeId)
                editingGateway = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.settings_849b48), onBack = onBack) {
            Button(
                onClick = {
                editingGateway = GatewayConfig(
                    name = str(R.string.settings_7acaf4),
                    endpoint = "",
                    apiKey = "",
                    model = "gpt-4o",
                    embeddingModel = "text-embedding-3-small",
                    capabilities = buildPresetCapabilities(
                        preset = GATEWAY_PRESETS.first { it.name == "Custom" },
                    ),
                )
            },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(str(R.string.settings_ed7823), fontSize = 13.sp, maxLines = 1)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHubCard(c) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(str(R.string.gateway_list_summary_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        str(R.string.gateway_list_summary_subtitle),
                        color = c.subtext,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GatewayPill(text = str(R.string.gateway_active_label), c = c, active = true)
                        GatewayPill(text = activeId?.let { list.find { gw -> gw.id == it }?.name }.orEmpty().ifBlank { "-" }, c = c)
                        GatewayPill(text = str(R.string.gateways_status, list.size, list.find { it.id == activeId }?.name ?: "-"), c = c)
                    }
                }
            }

            if (list.isEmpty()) {
                SettingsHubCard(c) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ClawIconTile(symbol = "gateway", size = 42.dp, iconSize = 22.dp, tint = c.text, background = c.cardAlt, border = c.border)
                            Text(str(R.string.settings_0733e8), fontSize = 15.sp, color = c.text, fontWeight = FontWeight.Medium)
                            Text(str(R.string.settings_tap), fontSize = 12.sp, color = c.subtext.copy(alpha = 0.7f))
                        }
                    }
                }
            } else {
                list.forEach { gw ->
                    val isActive = gw.id == activeId || (activeId == null && gw == list.first())
                    GatewayListItem(
                        gateway = gw,
                        isActive = isActive,
                        c = c,
                        onActivate = {
                            activeId = gw.id
                            onSave(list, activeId)
                        },
                        onEdit = { editingGateway = gw },
                        onDelete = { deleteTarget = gw },
                    )
                }
            }

            SettingsHubCard(c) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(str(R.string.gateway_quick_add_title), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        str(R.string.gateway_editor_setup_body),
                        color = c.subtext,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Text(str(R.string.gateway_provider_direct), color = c.subtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GATEWAY_PRESETS.filter { it.group == GatewayPresetGroup.DIRECT }) { preset ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(c.card)
                                    .border(0.5.dp, c.border, RoundedCornerShape(14.dp))
                                    .clickable {
                                        editingGateway = GatewayConfig(
                                            name = preset.name,
                                            endpoint = preset.endpoint,
                                            apiKey = "",
                                            model = preset.model,
                                            embeddingModel = preset.embeddingModel,
                                            supportsMultimodal = preset.supportsMultimodal,
                                            capabilities = buildPresetCapabilities(preset),
                                        )
                                    }
                                    .width(188.dp)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(preset.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1)
                                    Text(gatewayHostLabel(preset.endpoint), fontSize = 11.sp, color = c.subtext, maxLines = 2)
                                    GatewayPill(text = gatewayPresetTypeLabel(preset), c = c)
                                }
                            }
                        }
                    }
                    Text(str(R.string.gateway_provider_template), color = c.subtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GATEWAY_PRESETS.filter { it.group == GatewayPresetGroup.TEMPLATE }) { preset ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(c.card)
                                    .border(0.5.dp, c.border, RoundedCornerShape(14.dp))
                                    .clickable {
                                        editingGateway = GatewayConfig(
                                            name = preset.name,
                                            endpoint = preset.endpoint,
                                            apiKey = "",
                                            model = preset.model,
                                            embeddingModel = preset.embeddingModel,
                                            supportsMultimodal = preset.supportsMultimodal,
                                            capabilities = buildPresetCapabilities(preset),
                                        )
                                    }
                                    .width(188.dp)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(preset.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1)
                                    Text(gatewayHostLabel(preset.endpoint), fontSize = 11.sp, color = c.subtext, maxLines = 2)
                                    GatewayPill(text = gatewayPresetTypeLabel(preset), c = c)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(str(R.string.settings_delete)) },
            text = { Text(str(R.string.delete_gateway_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    list = list.filter { it.id != target.id }
                    if (activeId == target.id) activeId = list.firstOrNull()?.id
                    onSave(list, activeId)
                    deleteTarget = null
                }) { Text(str(R.string.skills_delete_confirm), color = c.red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(str(R.string.btn_cancel)) }
            },
        )
    }
}

@Composable
private fun GatewayListItem(
    gateway: GatewayConfig,
    isActive: Boolean,
    c: ClawColors,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.10f else 0.035f),
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.14f else 0.06f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (c.isDark) 0.08f else 0.70f),
                        Color.White.copy(alpha = if (c.isDark) 0.04f else 0.42f),
                    )
                )
            )
            .border(0.8.dp, if (isActive) c.text.copy(alpha = 0.82f) else Color.White.copy(alpha = if (c.isDark) 0.12f else 0.68f), RoundedCornerShape(24.dp))
            .clickable(onClick = onActivate)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ClawIconTile(
                symbol = "gateway",
                size = 38.dp,
                iconSize = 18.dp,
                tint = c.text.copy(alpha = 0.78f),
                background = Color.White.copy(alpha = if (c.isDark) 0.08f else 0.58f),
                border = Color.White.copy(alpha = if (c.isDark) 0.12f else 0.58f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(gateway.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (isActive) GatewayPill(text = str(R.string.gateway_active_label), c = c, active = true)
                    if (gateway.supportsCapabilityMultimodal()) GatewayPill(text = str(R.string.gateway_preset_multimodal), c = c)
                }
                Text(
                    gatewayHostLabel(gateway.endpoint),
                    fontSize = 11.sp,
                    color = c.subtext,
                    maxLines = 1,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val chips = gateway.capabilities.filter { it.enabled && it.model.isNotBlank() }
                    if (chips.isEmpty()) {
                        GatewayPill(text = gateway.model.takeIf { it.isNotBlank() } ?: str(R.string.status_not_configured), c = c)
                    } else {
                        chips.take(4).forEach {
                            GatewayPill(text = "${it.type}:${it.model}", c = c)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onActivate,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
                border = ButtonDefaults.outlinedButtonBorder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(str(R.string.gateway_active_label), fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
                border = ButtonDefaults.outlinedButtonBorder,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(str(R.string.settings_edit), fontSize = 11.sp)
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = c.red.copy(alpha = 0.78f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(str(R.string.skills_delete_confirm), color = c.red.copy(alpha = 0.78f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GatewayEditorSubPage(
    gateway: GatewayConfig,
    c: ClawColors,
    onBack: () -> Unit,
    onSave: (GatewayConfig) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf(gateway.name) }
    var endpoint by remember { mutableStateOf(gateway.endpoint) }
    var apiKey by remember { mutableStateOf(gateway.apiKey) }
    var model by remember { mutableStateOf(gateway.model) }
    var embModel by remember { mutableStateOf(gateway.embeddingModel) }
    var supportsMultimodal by remember { mutableStateOf(gateway.supportsMultimodal) }
    var presetHint by remember { mutableStateOf("") }
    var capabilities by remember(gateway.id) {
        mutableStateOf(
            gateway.capabilities.ifEmpty {
                buildList {
                    if (gateway.model.isNotBlank()) add(GatewayCapabilityConfig(type = "chat", model = gateway.model))
                    if (gateway.embeddingModel.isNotBlank()) add(GatewayCapabilityConfig(type = "embedding", model = gateway.embeddingModel))
                }
            }
        )
    }
    var fetchedModels by remember(gateway.id) { mutableStateOf<List<String>>(emptyList()) }
    var modelFetchLoading by remember { mutableStateOf(false) }
    var modelFetchFailed by remember { mutableStateOf(false) }
    var showAiFillDialog by remember { mutableStateOf(false) }
    var aiFillSelectedModel by remember { mutableStateOf("") }
    var aiFillLoading by remember { mutableStateOf(false) }
    var aiFillFailed by remember { mutableStateOf(false) }
    var capabilityDetailsExpanded by remember(gateway.id) { mutableStateOf(false) }

    val resolvedChatCapability = capabilities.firstOrNull { it.type.equals("chat", ignoreCase = true) }
    val resolvedChatEndpoint = resolvedChatCapability?.endpoint?.trim().orEmpty().ifBlank { endpoint.trim() }
    val resolvedChatApiKey = resolvedChatCapability?.apiKey?.trim().orEmpty().ifBlank { apiKey.trim() }
    val isValid = name.isNotBlank() && resolvedChatEndpoint.isNotBlank() && resolvedChatApiKey.isNotBlank()
    val directPresets = remember { GATEWAY_PRESETS.filter { it.group == GatewayPresetGroup.DIRECT } }
    val templatePresets = remember { GATEWAY_PRESETS.filter { it.group == GatewayPresetGroup.TEMPLATE } }
    val selectedPreset = remember(name, endpoint, model) {
        GATEWAY_PRESETS.firstOrNull { it.name == name && it.endpoint == endpoint && it.model == model }
            ?: GATEWAY_PRESETS.firstOrNull { it.endpoint == endpoint && it.name == name }
    }
    fun applyPreset(preset: GatewayPreset) {
        name = preset.name
        endpoint = preset.endpoint
        model = preset.model
        embModel = preset.embeddingModel
        supportsMultimodal = preset.supportsMultimodal
        capabilities = buildPresetCapabilities(preset, preset.embeddingModel.ifBlank { "text-embedding-3-small" })
        presetHint = preset.hint
        if (preset.endpoint.isNotBlank() && apiKey.isNotBlank() && !modelFetchLoading) {
            modelFetchLoading = true
            modelFetchFailed = false
            scope.launch {
                val models = OpenAiGateway.fetchModels(preset.endpoint, apiKey.trim())
                fetchedModels = models
                modelFetchLoading = false
                modelFetchFailed = models.isEmpty()
                if (models.isNotEmpty()) {
                    capabilities = hydrateCapabilityConnections(
                        capabilities = applyRecommendedModelsToCapabilities(capabilities, preset, models),
                        baseEndpoint = endpoint,
                        baseApiKey = apiKey,
                        existing = capabilities,
                    )
                    model = capabilities.firstOrNull { it.type == "chat" }?.model ?: model
                    embModel = capabilities.firstOrNull { it.type == "embedding" }?.model ?: embModel
                }
            }
        }
    }
    LaunchedEffect(apiKey, endpoint, selectedPreset?.name) {
        val preset = selectedPreset ?: return@LaunchedEffect
        if (apiKey.isBlank() || endpoint.isBlank() || fetchedModels.isNotEmpty() || modelFetchLoading) return@LaunchedEffect
        if (preset.endpoint.isBlank() || endpoint.trim() != preset.endpoint.trim()) return@LaunchedEffect
        modelFetchLoading = true
        modelFetchFailed = false
        val models = OpenAiGateway.fetchModels(endpoint.trim(), apiKey.trim())
        fetchedModels = models
        modelFetchLoading = false
        modelFetchFailed = models.isEmpty()
        if (models.isNotEmpty()) {
            capabilities = hydrateCapabilityConnections(
                capabilities = applyRecommendedModelsToCapabilities(capabilities, preset, models),
                baseEndpoint = endpoint,
                baseApiKey = apiKey,
                existing = capabilities,
            )
            model = capabilities.firstOrNull { it.type == "chat" }?.model ?: model
            embModel = capabilities.firstOrNull { it.type == "embedding" }?.model ?: embModel
        }
    }
    LaunchedEffect(fetchedModels, endpoint) {
        if (fetchedModels.isEmpty()) return@LaunchedEffect
        val endpointTrimmed = endpoint.trim()
        if (endpointTrimmed.isBlank()) return@LaunchedEffect
        val currentChat = capabilities.firstOrNull { it.type.equals("chat", ignoreCase = true) }?.model?.trim().orEmpty()
        if (currentChat.isNotBlank() && currentChat in fetchedModels) return@LaunchedEffect
        val preset = selectedPreset
            ?: GATEWAY_PRESETS.firstOrNull { it.endpoint.trim() == endpointTrimmed }
            ?: return@LaunchedEffect
        val corrected = hydrateCapabilityConnections(
            capabilities = applyRecommendedModelsToCapabilities(capabilities, preset, fetchedModels),
            baseEndpoint = endpoint,
            baseApiKey = apiKey,
            existing = capabilities,
        )
        if (corrected == capabilities) return@LaunchedEffect
        capabilities = corrected
        model = corrected.firstOrNull { it.type == "chat" }?.model ?: model
        embModel = corrected.firstOrNull { it.type == "embedding" }?.model ?: embModel
    }

    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = if (gateway.endpoint.isBlank()) str(R.string.settings_add) else str(R.string.settings_edit), onBack = onBack) {
            Button(
                onClick = {
                    val normalizedCapabilities = capabilities
                        .map {
                            it.copy(
                                type = it.type.trim().lowercase(),
                                model = it.model.trim(),
                                endpoint = it.endpoint.trim(),
                                apiKey = it.apiKey.trim(),
                            )
                        }
                        .filter { it.enabled && it.type.isNotBlank() && it.model.isNotBlank() }
                    val chatModel = normalizedCapabilities.firstOrNull { it.type == "chat" }?.model ?: model.trim()
                    val embeddingModel = normalizedCapabilities.firstOrNull { it.type == "embedding" }?.model ?: embModel.trim()
                    onSave(gateway.copy(
                        name = name.trim(),
                        endpoint = endpoint.trim(),
                        apiKey = apiKey.trim(),
                        model = chatModel,
                        embeddingModel = embeddingModel,
                        supportsMultimodal = supportsMultimodal || normalizedCapabilities.any { it.type == "image" || it.type == "video" },
                        capabilities = normalizedCapabilities,
                    ))
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg, disabledContainerColor = c.border),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(str(R.string.role_save), fontSize = 13.sp, maxLines = 1) }
            Spacer(Modifier.width(4.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsHubCard(c) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(str(R.string.gateway_editor_setup_title), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        str(R.string.gateway_editor_setup_body),
                        color = c.subtext,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GatewayPill(
                            text = if (isValid) str(R.string.gateway_editor_connection_ready) else str(R.string.gateway_editor_connection_missing),
                            c = c,
                            active = isValid,
                        )
                        GatewayPill(
                            text = if (capabilities.isEmpty()) str(R.string.gateway_editor_capabilities_empty)
                            else str(R.string.gateway_editor_capabilities_ready, capabilities.count { it.model.isNotBlank() }),
                            c = c,
                        )
                    }
                }
            }
            // Presets are split into direct providers and templates so users can
            // distinguish one-tap official-compatible gateways from relay-style setups.
            SettingsSection(str(R.string.gateway_editor_provider_title), c) {
                Text(
                    str(R.string.gateway_provider_direct),
                    color = c.subtext,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    directPresets.forEach { preset ->
                        val active = endpoint == preset.endpoint && model == preset.model && name == preset.name
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (active) c.text else c.surface)
                                .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(999.dp))
                                .clickable { applyPreset(preset) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                preset.name,
                                color = if (active) c.bg else c.text,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    str(R.string.gateway_provider_template),
                    color = c.subtext,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    templatePresets.forEach { preset ->
                        val active = endpoint == preset.endpoint && model == preset.model && name == preset.name
                        Box(
                            Modifier.clip(RoundedCornerShape(999.dp))
                                .background(if (active) c.text else c.surface)
                                .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(999.dp))
                                .clickable { applyPreset(preset) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                preset.name,
                                color = if (active) c.bg else c.text,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (presetHint.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        presetHint,
                        color = c.subtext,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
            SettingsSection(str(R.string.gateway_editor_connection_title), c) {
                ClawPageTextField(name, { name = it }, str(R.string.role_field_name), "OpenAI", c)
                ClawPageTextField(endpoint, { endpoint = it }, str(R.string.field_endpoint), "https://api.openai.com", c)
                ClawPageTextField(apiKey, { apiKey = it }, str(R.string.field_api_key), "sk-...", c, isSecret = true)
            }
            SettingsSection(str(R.string.gateway_editor_models_title), c) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            if (endpoint.isBlank() || apiKey.isBlank() || modelFetchLoading) return@OutlinedButton
                            modelFetchLoading = true
                            modelFetchFailed = false
                            scope.launch {
                                val models = OpenAiGateway.fetchModels(endpoint, apiKey)
                                fetchedModels = models
                                modelFetchLoading = false
                                modelFetchFailed = models.isEmpty()
                            }
                        },
                        enabled = endpoint.isNotBlank() && apiKey.isNotBlank() && !modelFetchLoading,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
                    ) {
                        Text(
                            if (modelFetchLoading) str(R.string.gateway_models_loading) else str(R.string.gateway_models_fetch),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Text(
                        if (fetchedModels.isNotEmpty()) str(R.string.gateway_models_count, fetchedModels.size)
                        else if (modelFetchFailed) str(R.string.gateway_models_fetch_failed)
                        else str(R.string.gateway_models_fetch_hint),
                        color = c.subtext,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (endpoint.isBlank() || apiKey.isBlank() || modelFetchLoading || aiFillLoading) return@OutlinedButton
                            scope.launch {
                                aiFillFailed = false
                                if (fetchedModels.isEmpty()) {
                                    modelFetchLoading = true
                                    modelFetchFailed = false
                                    val models = OpenAiGateway.fetchModels(endpoint.trim(), apiKey.trim())
                                    fetchedModels = models
                                    modelFetchLoading = false
                                    modelFetchFailed = models.isEmpty()
                                }
                                if (fetchedModels.isNotEmpty()) {
                                    aiFillSelectedModel = fetchedModels.firstOrNull { it == model }
                                        ?: fetchedModels.firstOrNull()
                                        .orEmpty()
                                    showAiFillDialog = true
                                } else {
                                    aiFillFailed = true
                                }
                            }
                        },
                        enabled = endpoint.isNotBlank() && apiKey.isNotBlank() && !modelFetchLoading && !aiFillLoading,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(str(R.string.gateway_ai_fill), fontSize = 12.sp, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            capabilities = capabilities + GatewayCapabilityConfig(
                                type = "chat",
                                model = fetchedModels.firstOrNull().orEmpty(),
                            )
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(str(R.string.gateway_capability_add), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (aiFillFailed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        str(R.string.gateway_ai_fill_failed),
                        color = c.red.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { capabilityDetailsExpanded = !capabilityDetailsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (capabilities.isEmpty()) str(R.string.gateway_editor_capabilities_empty)
                                else str(R.string.gateway_editor_capabilities_ready, capabilities.count { it.model.isNotBlank() }),
                                color = c.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                capabilities.forEach { summary ->
                                    GatewayPill(
                                        text = "${gatewayCapabilityTypeLabel(summary.type)}:${summary.model.ifBlank { "-" }}",
                                        c = c,
                                        active = true,
                                    )
                                }
                            }
                        }
                        Text(
                            if (capabilityDetailsExpanded) str(R.string.gateway_capability_hide) else str(R.string.gateway_capability_show),
                            color = c.subtext,
                            fontSize = 11.sp,
                        )
                    }
                    AnimatedVisibility(capabilityDetailsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            capabilities.forEachIndexed { index, capability ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(c.bg)
                                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                str(R.string.gateway_capability_item, index + 1),
                                                color = c.text,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            GatewayPill(text = gatewayCapabilityTypeLabel(capability.type), c = c, active = true)
                                        }
                                        IconButton(
                                            onClick = { capabilities = capabilities.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = c.red.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GATEWAY_CAPABILITY_TYPES.forEach { type ->
                                            val active = capability.type == type
                                            Box(
                                                Modifier.clip(RoundedCornerShape(999.dp))
                                                    .background(if (active) c.text else c.surface)
                                                    .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(999.dp))
                                                    .clickable {
                                                        capabilities = capabilities.toMutableList().also {
                                                            it[index] = capability.copy(type = type)
                                                        }
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                            ) {
                                                Text(
                                                    gatewayCapabilityTypeLabel(type),
                                                    color = if (active) c.bg else c.text,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                    ClawPageTextField(
                                        value = capability.model,
                                        onValueChange = {
                                            capabilities = capabilities.toMutableList().also { list ->
                                                list[index] = capability.copy(model = it)
                                            }
                                            if (capability.type == "chat") model = it
                                            if (capability.type == "embedding") embModel = it
                                        },
                                        label = str(R.string.gateway_model_label),
                                        placeholder = "gpt-4o",
                                        c = c,
                                    )
                                    ClawPageTextField(
                                        value = capability.endpoint,
                                        onValueChange = {
                                            capabilities = capabilities.toMutableList().also { list ->
                                                list[index] = capability.copy(endpoint = it)
                                            }
                                        },
                                        label = str(R.string.gateway_capability_endpoint),
                                        placeholder = str(R.string.gateway_capability_inherit_endpoint),
                                        c = c,
                                    )
                                    ClawPageTextField(
                                        value = capability.apiKey,
                                        onValueChange = {
                                            capabilities = capabilities.toMutableList().also { list ->
                                                list[index] = capability.copy(apiKey = it)
                                            }
                                        },
                                        label = str(R.string.gateway_capability_api_key),
                                        placeholder = str(R.string.gateway_capability_inherit_key),
                                        c = c,
                                        isSecret = true,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val fetchEndpoint = capability.endpoint.trim().ifBlank { endpoint.trim() }
                                            val fetchApiKey = capability.apiKey.trim().ifBlank { apiKey.trim() }
                                            if (fetchEndpoint.isBlank() || fetchApiKey.isBlank() || modelFetchLoading) return@OutlinedButton
                                            modelFetchLoading = true
                                            modelFetchFailed = false
                                            scope.launch {
                                                val models = OpenAiGateway.fetchModels(fetchEndpoint, fetchApiKey)
                                                fetchedModels = models
                                                modelFetchLoading = false
                                                modelFetchFailed = models.isEmpty()
                                            }
                                        },
                                        enabled = !modelFetchLoading &&
                                            (capability.endpoint.isNotBlank() || endpoint.isNotBlank()) &&
                                            (capability.apiKey.isNotBlank() || apiKey.isNotBlank()),
                                        shape = RoundedCornerShape(999.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(str(R.string.gateway_capability_fetch_models), fontSize = 11.sp, maxLines = 1)
                                    }
                                    if (fetchedModels.isNotEmpty()) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            fetchedModels.take(24).forEach { fetched ->
                                                val active = capability.model == fetched
                                                Box(
                                                    Modifier.clip(RoundedCornerShape(999.dp))
                                                        .background(if (active) c.text else c.surface)
                                                        .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(999.dp))
                                                        .clickable {
                                                            capabilities = capabilities.toMutableList().also { list ->
                                                                list[index] = capability.copy(model = fetched)
                                                            }
                                                            if (capability.type == "chat") model = fetched
                                                            if (capability.type == "embedding") embModel = fetched
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                                ) {
                                                    Text(
                                                        fetched,
                                                        color = if (active) c.bg else c.text,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                ClawPageTextField(model, { model = it }, str(R.string.field_chat_model), "gpt-4o", c)
                ClawPageTextField(embModel, { embModel = it }, str(R.string.field_embed_model), "text-embedding-3-small", c)
                Text(str(R.string.gateway_capability_hint), color = c.subtext.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp)
            }
            SettingsSection(str(R.string.gateway_editor_multimodal_title), c) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(str(R.string.settings_35e88c), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.text)
                        Text(str(R.string.settings_close), fontSize = 11.sp, color = c.subtext)
                    }
                    Switch(
                        checked = supportsMultimodal,
                        onCheckedChange = { supportsMultimodal = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.accent,
                            uncheckedThumbColor = c.subtext,
                            uncheckedTrackColor = c.border,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(str(R.string.settings_8f029c),
                        fontSize = 10.sp, color = c.subtext.copy(alpha = 0.6f), lineHeight = 14.sp)
                }
            }
        }
    }

    if (showAiFillDialog) {
        AlertDialog(
            onDismissRequest = { if (!aiFillLoading) showAiFillDialog = false },
            title = { Text(str(R.string.gateway_ai_fill_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        str(R.string.gateway_ai_fill_body),
                        color = c.subtext,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        str(R.string.gateway_ai_fill_preview_title),
                        color = c.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val previewTypes = buildList {
                            add("chat")
                            if (supportsMultimodal) {
                                add("image")
                                add("video")
                            }
                            add("embedding")
                        }.distinct()
                        previewTypes.forEach { type ->
                            GatewayPill(text = gatewayCapabilityTypeLabel(type), c = c, active = true)
                        }
                    }
                    Column(
                        Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        fetchedModels.forEach { candidate ->
                            val active = aiFillSelectedModel == candidate
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) c.text else c.surface)
                                    .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !aiFillLoading) { aiFillSelectedModel = candidate }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    candidate,
                                    color = if (active) c.bg else c.text,
                                    fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aiFillSelectedModel.isBlank() || aiFillLoading) return@TextButton
                        val preset = selectedPreset ?: GatewayPreset(
                            name = name,
                            endpoint = endpoint,
                            model = model,
                            embeddingModel = embModel,
                            supportsMultimodal = supportsMultimodal,
                        )
                        aiFillLoading = true
                        aiFillFailed = false
                        scope.launch {
                            val aiCapabilities = OpenAiGateway.planGatewayCapabilities(
                                endpoint = endpoint.trim(),
                                apiKey = apiKey.trim(),
                                model = aiFillSelectedModel,
                                presetName = preset.name,
                                supportsMultimodal = supportsMultimodal,
                                availableModels = fetchedModels,
                            )
                            val nextCapabilities = if (aiCapabilities.isNotEmpty()) {
                                hydrateCapabilityConnections(
                                    capabilities = aiCapabilities,
                                    baseEndpoint = endpoint,
                                    baseApiKey = apiKey,
                                    existing = capabilities,
                                )
                            } else {
                                hydrateCapabilityConnections(
                                    capabilities = applyRecommendedModelsToCapabilities(capabilities, preset, fetchedModels),
                                    baseEndpoint = endpoint,
                                    baseApiKey = apiKey,
                                    existing = capabilities,
                                )
                            }
                            capabilities = nextCapabilities
                            model = nextCapabilities.firstOrNull { it.type == "chat" }?.model ?: model
                            embModel = nextCapabilities.firstOrNull { it.type == "embedding" }?.model ?: embModel
                            aiFillLoading = false
                            showAiFillDialog = false
                            aiFillFailed = nextCapabilities.isEmpty()
                            if (nextCapabilities.isNotEmpty()) {
                                val summary = nextCapabilities.joinToString(" · ") {
                                    "${it.type}=${it.model}"
                                }
                                Toast.makeText(context, str(R.string.gateway_ai_fill_done, summary), Toast.LENGTH_LONG).show()
                                capabilityDetailsExpanded = false
                            }
                        }
                    },
                    enabled = aiFillSelectedModel.isNotBlank() && !aiFillLoading,
                ) {
                    Text(if (aiFillLoading) str(R.string.gateway_ai_fill_running) else str(R.string.gateway_ai_fill_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAiFillDialog = false },
                    enabled = !aiFillLoading,
                ) { Text(str(R.string.btn_cancel)) }
            },
        )
    }
}

@Composable
private fun LocalModelSubPage(
    models: List<LocalModelInfo>,
    enabled: Boolean,
    nativeOnly: Boolean,
    selectedModelId: String,
    c: ClawColors,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onNativeOnly: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onDownload: (String, String, String) -> Unit,
    onImport: (String, android.net.Uri) -> Unit,
    onDelete: (String) -> Unit,
) {
    var hfToken by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var selectedSourceByModel by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var importTarget by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = importTarget
        if (uri != null && target != null) onImport(target, uri)
        importTarget = null
    }
    val hasRunnableLocalModel = models.any { it.installed && it.supportsChatRuntime }

    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.local_model_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface)
                    .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(str(R.string.local_model_enable), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(str(R.string.local_model_enable_desc), color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = c.text,
                        uncheckedThumbColor = c.subtext,
                        uncheckedTrackColor = c.border,
                    ),
                )
            }

            if (hasRunnableLocalModel) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(str(R.string.local_model_native_only), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(str(R.string.local_model_native_only_desc), color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Switch(
                        checked = nativeOnly,
                        onCheckedChange = onNativeOnly,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.text,
                            uncheckedThumbColor = c.subtext,
                            uncheckedTrackColor = c.border,
                        ),
                    )
                }
            }

            ClawPageTextField(
                value = hfToken,
                onValueChange = { hfToken = it },
                label = str(R.string.local_model_hf_token),
                placeholder = "hf_...",
                c = c,
                isSecret = true,
            )
            ClawPageTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = str(R.string.local_model_custom_url),
                placeholder = "https://...",
                c = c,
            )

            models
                .filter { it.supportsChatRuntime }
                .sortedWith(compareBy<LocalModelInfo> { it.family }.thenBy { it.sizeBytes })
                .forEach { model ->
                val visionResource = models
                    .filter { it.family == model.family && it.isVisionResource }
                    .minByOrNull { it.sizeBytes }
                val pairedVisionInstalled = visionResource?.installed == true
                LocalModelItem(
                    model = model,
                    visionResource = visionResource,
                    selected = model.id == selectedModelId,
                    pairedVisionInstalled = pairedVisionInstalled,
                    selectedSourceId = selectedSourceByModel[model.id] ?: model.downloadSources.firstOrNull()?.id.orEmpty(),
                    selectedVisionSourceId = visionResource?.let { selectedSourceByModel[it.id] ?: it.downloadSources.firstOrNull()?.id.orEmpty() }.orEmpty(),
                    c = c,
                    onSelect = { onSelect(model.id) },
                    onSourceSelect = { sourceId ->
                        selectedSourceByModel = selectedSourceByModel + (model.id to sourceId)
                    },
                    onVisionSourceSelect = { resource, sourceId ->
                        selectedSourceByModel = selectedSourceByModel + (resource.id to sourceId)
                    },
                    onDownload = { url ->
                        onDownload(model.id, hfToken.trim(), url.ifBlank { customUrl.trim() })
                    },
                    onImport = {
                        importTarget = model.id
                        picker.launch(arrayOf("*/*"))
                    },
                    onDelete = { onDelete(model.id) },
                    onVisionDownload = { resource, url ->
                        onDownload(resource.id, hfToken.trim(), url.ifBlank { customUrl.trim() })
                    },
                    onVisionImport = { resource ->
                        importTarget = resource.id
                        picker.launch(arrayOf("*/*"))
                    },
                    onVisionDelete = { resource -> onDelete(resource.id) },
                )
            }
        }
    }
}

@Composable
private fun LocalModelItem(
    model: LocalModelInfo,
    visionResource: LocalModelInfo?,
    selected: Boolean,
    pairedVisionInstalled: Boolean,
    selectedSourceId: String,
    selectedVisionSourceId: String,
    c: ClawColors,
    onSelect: () -> Unit,
    onSourceSelect: (String) -> Unit,
    onVisionSourceSelect: (LocalModelInfo, String) -> Unit,
    onDownload: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onVisionDownload: (LocalModelInfo, String) -> Unit,
    onVisionImport: (LocalModelInfo) -> Unit,
    onVisionDelete: (LocalModelInfo) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.10f else 0.035f),
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.14f else 0.06f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (c.isDark) 0.08f else 0.70f),
                        Color.White.copy(alpha = if (c.isDark) 0.04f else 0.42f),
                    )
                )
            )
            .border(0.8.dp, if (selected) c.text.copy(alpha = 0.82f) else Color.White.copy(alpha = if (c.isDark) 0.12f else 0.68f), RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawIconTile(
                symbol = "model",
                size = 38.dp,
                iconSize = 20.dp,
                tint = c.text.copy(alpha = 0.78f),
                background = Color.White.copy(alpha = if (c.isDark) 0.08f else 0.58f),
                border = Color.White.copy(alpha = if (c.isDark) 0.12f else 0.58f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        model.name,
                        color = c.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            selected -> str(R.string.local_model_selected)
                            model.installed -> str(R.string.local_model_installed)
                            else -> str(R.string.local_model_not_installed)
                        },
                        color = if (selected || model.installed) c.green else c.subtext,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected || model.installed) c.green.copy(alpha = 0.10f) else c.cardAlt)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LocalModelChip(text = model.family, c = c)
                    LocalModelChip(text = localModelCapabilities(model), c = c)
                    LocalModelChip(text = str(R.string.local_model_runtime_chat), c = c)
                }
                Text(
                    str(
                        R.string.local_model_requirements,
                        formatCacheSize(model.sizeBytes),
                        model.minRamGb,
                        model.recommendedRamGb,
                    ),
                    color = c.subtext,
                    fontSize = 11.sp,
                )
                if (model.supportsChatRuntime && model.supportsVision) {
                    Text(
                        if (pairedVisionInstalled) str(R.string.local_model_vision_ready) else str(R.string.local_model_vision_missing),
                        color = if (pairedVisionInstalled) c.green else c.subtext,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (model.downloading) {
            LinearProgressIndicator(
                progress = { model.downloadProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                color = c.text,
                trackColor = c.border,
            )
        }
        if (model.error.isNotBlank()) {
            Text(model.error, color = c.red, fontSize = 11.sp, lineHeight = 15.sp)
        }
        if (visionResource != null) {
            LocalVisionResourceItem(
                resource = visionResource,
                c = c,
                selectedSourceId = selectedVisionSourceId,
                onSourceSelect = { sourceId -> onVisionSourceSelect(visionResource, sourceId) },
                onDownload = { url -> onVisionDownload(visionResource, url) },
                onImport = { onVisionImport(visionResource) },
                onDelete = { onVisionDelete(visionResource) },
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            model.downloadSources.forEach { source ->
                val active = source.id == selectedSourceId
                Surface(
                    onClick = { onSourceSelect(source.id) },
                    color = if (active) c.text else c.cardAlt,
                    contentColor = if (active) c.bg else c.text,
                    shape = RoundedCornerShape(999.dp),
                    border = if (active) null else androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                ) {
                    Text(source.name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Surface(
                onClick = { onSourceSelect("custom") },
                color = if (selectedSourceId == "custom") c.text else c.cardAlt,
                contentColor = if (selectedSourceId == "custom") c.bg else c.text,
                shape = RoundedCornerShape(999.dp),
                border = if (selectedSourceId == "custom") null else androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
            ) {
                Text(str(R.string.local_model_source_custom), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (model.supportsChatRuntime && model.installed) {
                OutlinedButton(
                    onClick = onSelect,
                    enabled = !model.downloading && !selected,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(if (selected) str(R.string.local_model_using) else str(R.string.local_model_use), fontSize = 12.sp, maxLines = 1, color = c.text) }
            }
            Button(
                onClick = {
                    val url = when (selectedSourceId) {
                        "custom" -> ""
                        else -> model.downloadSources.firstOrNull { it.id == selectedSourceId }?.url.orEmpty()
                    }
                    onDownload(url)
                },
                enabled = !model.downloading,
                colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(if (model.installed) str(R.string.local_model_redownload) else str(R.string.local_model_download), fontSize = 12.sp, maxLines = 1) }
            OutlinedButton(
                onClick = onImport,
                enabled = !model.downloading,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(str(R.string.local_model_import), fontSize = 12.sp, maxLines = 1, color = c.text) }
            if (model.installed) {
                TextButton(onClick = onDelete, enabled = !model.downloading) {
                    Text(str(R.string.local_model_delete), color = c.red, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LocalVisionResourceItem(
    resource: LocalModelInfo,
    c: ClawColors,
    selectedSourceId: String,
    onSourceSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.cardAlt.copy(alpha = 0.65f))
            .border(0.5.dp, c.border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawIconTile(
                symbol = "image",
                size = 30.dp,
                iconSize = 16.dp,
                tint = c.text,
                background = c.surface,
                border = c.border,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        str(R.string.local_model_vision_pack),
                        color = c.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (resource.installed) str(R.string.local_model_installed) else str(R.string.local_model_not_installed),
                        color = if (resource.installed) c.green else c.subtext,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                Text(resource.name, color = c.subtext, fontSize = 11.sp, maxLines = 1)
            }
        }
        if (resource.downloading) {
            LinearProgressIndicator(
                progress = { resource.downloadProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)),
                color = c.text,
                trackColor = c.border,
            )
        }
        if (resource.error.isNotBlank()) {
            Text(resource.error, color = c.red, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            resource.downloadSources.forEach { source ->
                val active = source.id == selectedSourceId
                Surface(
                    onClick = { onSourceSelect(source.id) },
                    color = if (active) c.text else c.cardAlt,
                    contentColor = if (active) c.bg else c.text,
                    shape = RoundedCornerShape(999.dp),
                    border = if (active) null else androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
                ) {
                    Text(source.name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Surface(
                onClick = { onSourceSelect("custom") },
                color = if (selectedSourceId == "custom") c.text else c.cardAlt,
                contentColor = if (selectedSourceId == "custom") c.bg else c.text,
                shape = RoundedCornerShape(999.dp),
                border = if (selectedSourceId == "custom") null else androidx.compose.foundation.BorderStroke(0.5.dp, c.border),
            ) {
                Text(str(R.string.local_model_source_custom), fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val url = when (selectedSourceId) {
                        "custom" -> ""
                        else -> resource.downloadSources.firstOrNull { it.id == selectedSourceId }?.url.orEmpty()
                    }
                    onDownload(url)
                },
                enabled = !resource.downloading,
                colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(if (resource.installed) str(R.string.local_model_redownload) else str(R.string.local_model_download), fontSize = 12.sp, maxLines = 1) }
            OutlinedButton(
                onClick = onImport,
                enabled = !resource.downloading,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(str(R.string.local_model_import), fontSize = 12.sp, maxLines = 1, color = c.text) }
            if (resource.installed) {
                TextButton(onClick = onDelete, enabled = !resource.downloading) {
                    Text(str(R.string.local_model_delete), color = c.red, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LocalModelChip(text: String, c: ClawColors) {
    Text(
        text = text,
        color = c.subtext,
        fontSize = 10.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.cardAlt)
            .border(0.5.dp, c.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

private fun localModelCapabilities(model: LocalModelInfo): String {
    val caps = buildList {
        if (model.supportsText) add(str(R.string.local_model_cap_text))
        if (model.supportsVision) add(str(R.string.local_model_cap_vision))
        if (model.supportsAudio) add(str(R.string.local_model_cap_audio))
    }
    return caps.joinToString(" / ")
}

@Composable
private fun AppearanceSubPage(
    darkTheme: Boolean, onDarkTheme: (Boolean) -> Unit,
    accent: Long, onAccent: (Long) -> Unit,
    language: String, onLanguage: (String) -> Unit,
    c: ClawColors,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.settings_ce650e), onBack = onBack) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(str(R.string.role_save), fontSize = 13.sp, maxLines = 1) }
            Spacer(Modifier.width(4.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(str(R.string.section_theme), c) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePresets.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { preset ->
                                ThemeModeCard(
                                    modifier = Modifier.weight(1f),
                                    title = themePresetTitle(preset.name),
                                    subtitle = themePresetSubtitle(preset.name),
                                    active = darkTheme == preset.darkTheme && accent == preset.accentColor,
                                    dark = preset.darkTheme,
                                    previewBg = Color(preset.previewBg),
                                    previewAccent = Color(preset.previewAccent),
                                    c = c,
                                ) {
                                    onDarkTheme(preset.darkTheme)
                                    onAccent(preset.accentColor)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            SettingsSection(str(R.string.section_language), c) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGUAGES.forEach { (code, resId) ->
                        val active = language == code
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (active) c.text else c.cardAlt)
                                .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(8.dp))
                                .clickable { onLanguage(code) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(resId), color = if (active) c.bg else c.subtext, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    active: Boolean,
    dark: Boolean,
    previewBg: Color = if (dark) Color(0xFF050505) else Color(0xFFF6F6F4),
    previewAccent: Color = if (dark) Color.White else Color(0xFF101010),
    c: ClawColors,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(12.dp))
                .background(previewBg),
        ) {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(22.dp).clip(CircleShape).background(previewAccent))
            Box(Modifier.align(Alignment.BottomStart).padding(12.dp).size(width = 78.dp, height = 10.dp).clip(RoundedCornerShape(8.dp)).background(previewAccent.copy(alpha = if (dark) 0.34f else 0.22f)))
        }
        Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun themePresetTitle(name: String): String = when (name) {
    "AI Night" -> stringResource(R.string.theme_ai_night)
    "AI Day" -> stringResource(R.string.theme_ai_day)
    "Tech Night" -> stringResource(R.string.theme_tech_night)
    "Tech Day" -> stringResource(R.string.theme_tech_day)
    else -> name
}

@Composable
private fun themePresetSubtitle(name: String): String = when (name) {
    "AI Night", "AI Day" -> stringResource(R.string.theme_ai_desc)
    "Tech Night", "Tech Day" -> stringResource(R.string.theme_tech_desc)
    else -> ""
}

@Composable
private fun PermissionsSubPage(c: ClawColors, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val storageManager = remember { com.mobileclaw.config.UserStorageManager(ctx) }
    val permissionManager = remember { com.mobileclaw.permission.PermissionManager(ctx) }
    var hasFileAccess by remember { mutableStateOf(storageManager.hasAllFilesAccess()) }
    val fileAccessLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { hasFileAccess = storageManager.hasAllFilesAccess() }
    val activityLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { }

    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = stringResource(R.string.settings_permissions), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionStatusRow("accessibility", stringResource(R.string.perm_accessibility_title), stringResource(R.string.perm_accessibility_desc), permissionManager.isAccessibilityEnabled(), true, c) {
                activityLauncher.launch(permissionManager.openAccessibilitySettings())
            }
            PermissionStatusRow("overlay", stringResource(R.string.perm_overlay_title), stringResource(R.string.perm_overlay_desc), permissionManager.isOverlayEnabled(), true, c) {
                activityLauncher.launch(permissionManager.openOverlaySettings())
            }
            PermissionStatusRow("folder", stringResource(R.string.settings_bc417e), stringResource(R.string.settings_tap_2), hasFileAccess, false, c) {
                fileAccessLauncher.launch(storageManager.allFilesAccessSettingsIntent())
            }
            PermissionStatusRow("notification", stringResource(R.string.perm_notification_title), stringResource(R.string.perm_notification_desc), permissionManager.isNotificationGranted(), false, c) {
                activityLauncher.launch(permissionManager.openAppDetails())
            }
            PermissionStatusRow("battery", stringResource(R.string.perm_background_title), stringResource(R.string.perm_background_desc), permissionManager.isBatteryOptimizationExempt(), false, c) {
                activityLauncher.launch(permissionManager.openBatteryOptimizationRequest())
            }
            permissionManager.pendingPermissions()
                .filter { it.id.startsWith("rom_") }
                .forEach { item ->
                    PermissionStatusRow(item.icon, item.title, item.description, false, item.isBlocking, c) {
                        activityLauncher.launch(permissionManager.openRomSettingFor(item))
                    }
                }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    iconKey: String,
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean,
    c: ClawColors,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (granted) c.green.copy(alpha = 0.07f) else c.surface)
            .border(1.dp, if (granted) c.green.copy(alpha = 0.28f) else c.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClawIconTile(
            symbol = iconKey,
            size = 38.dp,
            iconSize = 20.dp,
            tint = if (granted) c.green else c.text,
            background = if (granted) c.green.copy(alpha = 0.10f) else c.cardAlt,
            border = if (granted) c.green.copy(alpha = 0.22f) else c.border,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (required) str(R.string.settings_required) else str(R.string.settings_recommended), color = if (required) c.red else c.subtext, fontSize = 10.sp)
            }
            Text(description, color = c.subtext, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text(if (granted) str(R.string.settings_enabled) else str(R.string.settings_go_configure), color = if (granted) c.green else c.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CacheSubPage(c: ClawColors, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cleaner = remember { CacheCleaner(ctx.applicationContext) }
    val scope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<CacheCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var clearingId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            categories = cleaner.scan()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.cache_title), onBack = onBack) {
            TextButton(
                enabled = !loading && clearingId == null && categories.any { it.sizeBytes > 0L },
                onClick = {
                    scope.launch {
                        clearingId = "__all__"
                        cleaner.clearAll()
                        categories = cleaner.scan()
                        clearingId = null
                    }
                },
            ) {
                Text(str(R.string.cache_clear_all), color = if (clearingId == null) c.red else c.subtext, fontSize = 13.sp, maxLines = 1)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val total = categories.sumOf { it.sizeBytes }
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface)
                    .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(str(R.string.cache_total), color = c.subtext, fontSize = 12.sp)
                Text(formatCacheSize(total), color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(str(R.string.cache_notice), color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.text, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            } else {
                categories.forEach { category ->
                    CacheCategoryRow(
                        category = category,
                        c = c,
                        clearing = clearingId == category.id || clearingId == "__all__",
                        onClear = {
                            scope.launch {
                                clearingId = category.id
                                cleaner.clear(category.id)
                                categories = cleaner.scan()
                                clearingId = null
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheCategoryRow(
    category: CacheCategory,
    c: ClawColors,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(category.titleRes), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(formatCacheSize(category.sizeBytes), color = c.subtext, fontSize = 11.sp)
            }
            Text(stringResource(category.subtitleRes), color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
            Text(str(R.string.cache_paths_count, category.pathCount), color = c.subtext.copy(alpha = 0.7f), fontSize = 10.sp)
        }
        Box(
            Modifier.size(width = 64.dp, height = 34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (category.sizeBytes > 0L) c.text else c.cardAlt)
                .clickable(enabled = category.sizeBytes > 0L && !clearing) { onClear() },
            contentAlignment = Alignment.Center,
        ) {
            if (clearing) {
                CircularProgressIndicator(color = c.bg, modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp)
            } else {
                Text(str(R.string.cache_clear), color = if (category.sizeBytes > 0L) c.bg else c.subtext, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatCacheSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

@Composable
private fun CodexDesktopSubPage(
    userConfig: com.mobileclaw.config.UserConfig,
    c: ClawColors,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val http = remember {
        OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var approval by remember { mutableStateOf("never") }
    var sandbox by remember { mutableStateOf("danger-full-access") }
    var testing by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        endpoint = userConfig.get(CODEX_DESKTOP_ENDPOINT_KEY).orEmpty()
        token = userConfig.get(CODEX_DESKTOP_TOKEN_KEY).orEmpty()
        cwd = userConfig.get(CODEX_DESKTOP_CWD_KEY).orEmpty()
        model = userConfig.get(CODEX_DESKTOP_MODEL_KEY).orEmpty()
        provider = userConfig.get(CODEX_DESKTOP_PROVIDER_KEY).orEmpty()
        approval = userConfig.get(CODEX_DESKTOP_APPROVAL_KEY).orEmpty().ifBlank { "never" }
        sandbox = userConfig.get(CODEX_DESKTOP_SANDBOX_KEY).orEmpty().ifBlank { "danger-full-access" }
    }
    LaunchedEffect(saved) {
        if (saved) {
            delay(1300)
            saved = false
        }
    }

    fun saveConfig() {
        scope.launch {
            userConfig.set(CODEX_DESKTOP_ENDPOINT_KEY, endpoint.trim().trimEnd('/'), "Desktop Codex bridge URL")
            userConfig.set(CODEX_DESKTOP_TOKEN_KEY, token.trim(), "Desktop Codex bridge bearer token")
            userConfig.set(CODEX_DESKTOP_CWD_KEY, cwd.trim(), "Default desktop working directory for Codex bridge")
            userConfig.set(CODEX_DESKTOP_MODEL_KEY, model.trim(), "Default desktop Codex model")
            userConfig.set(CODEX_DESKTOP_PROVIDER_KEY, provider.trim(), "Default desktop Codex provider")
            userConfig.set(CODEX_DESKTOP_APPROVAL_KEY, approval.trim(), "Default desktop Codex approval policy")
            userConfig.set(CODEX_DESKTOP_SANDBOX_KEY, sandbox.trim(), "Default desktop Codex sandbox")
            saved = true
        }
    }

    fun syncCodexConfig() {
        scope.launch {
            saveConfig()
            syncing = true
            syncResult = null
            val url = endpoint.trim().trimEnd('/')
            val bearer = token.trim()
            syncResult = when {
                url.isBlank() || bearer.isBlank() -> "请先填写 URL 和 Token"
                else -> withContext(Dispatchers.IO) {
                    runCatching {
                        val payload = JsonObject().apply {
                            add("config", JsonObject().apply {
                                addProperty("cwd", cwd.trim())
                                addProperty("model", model.trim())
                                addProperty("provider", provider.trim())
                                addProperty("approval", approval.trim())
                                addProperty("sandbox", sandbox.trim())
                            })
                        }
                        val req = Request.Builder()
                            .url("$url/config")
                            .header("Authorization", "Bearer $bearer")
                            .post(Gson().toJson(payload).toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        http.newCall(req).execute().use { resp ->
                            val body = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) "ok: 已同步到电脑 Codex" else "error ${resp.code}: ${body.take(240)}"
                        }
                    }.getOrElse { "error: ${it.message}" }
                }
            }
            syncing = false
        }
    }

    fun testBridge() {
        scope.launch {
            testing = true
            testResult = null
            val url = endpoint.trim().trimEnd('/')
            val bearer = token.trim()
            testResult = when {
                url.isBlank() || bearer.isBlank() -> "请先填写 URL 和 Token"
                else -> withContext(Dispatchers.IO) {
                    runCatching {
                        val req = Request.Builder()
                            .url("$url/health")
                            .header("Authorization", "Bearer $bearer")
                            .get()
                            .build()
                        http.newCall(req).execute().use { resp ->
                            val body = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) "ok: ${body.take(240)}" else "error ${resp.code}: ${body.take(240)}"
                        }
                    }.getOrElse { "error: ${it.message}" }
                }
            }
            testing = false
        }
    }

    val command = remember(token) {
        val displayToken = token.ifBlank { "change-me" }
        "CODEX_BRIDGE_TOKEN=$displayToken python3 scripts/codex_desktop_bridge.py"
    }

    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = "Codex 桥接", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsSection("电脑连接", c) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("Bridge URL") },
                        placeholder = { Text("http://192.168.1.23:52734") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cwd,
                        onValueChange = { cwd = it },
                        label = { Text("默认工作目录") },
                        placeholder = { Text("/Users/you/project") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { saveConfig() },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Text(if (saved) "已保存" else "保存", fontSize = 13.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { testBridge() },
                            enabled = !testing,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.height(44.dp),
                        ) {
                            if (testing) {
                                CircularProgressIndicator(color = c.text, modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp)
                            } else {
                                Text("测试连接", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                    testResult?.let {
                        Text(
                            it,
                            color = if (it.startsWith("ok:")) c.green else c.red,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }

            SettingsSection("Codex 运行配置", c) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        placeholder = { Text("gpt-5.5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = provider,
                        onValueChange = { provider = it },
                        label = { Text("Provider") },
                        placeholder = { Text("留空使用电脑 Codex 默认 provider") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = approval,
                            onValueChange = { approval = it },
                            label = { Text("Approval") },
                            placeholder = { Text("never") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = sandbox,
                            onValueChange = { sandbox = it },
                            label = { Text("Sandbox") },
                            placeholder = { Text("danger-full-access") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { syncCodexConfig() },
                            enabled = !syncing,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                            modifier = Modifier.height(44.dp),
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(color = c.bg, modifier = Modifier.size(14.dp), strokeWidth = 1.6.dp)
                            } else {
                                Text("同步到电脑", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                        OutlinedButton(
                            onClick = { saveConfig() },
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Text(if (saved) "已保存" else "仅保存手机", fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    syncResult?.let {
                        Text(
                            it,
                            color = if (it.startsWith("ok:")) c.green else c.red,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    Text(
                        "同步后电脑端 bridge 会记住这些默认值；之后从 MobileClaw 发给 Codex 的任务会按这组配置运行。",
                        color = c.subtext.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }

            SettingsSection("电脑端启动", c) {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "在装有 Codex CLI 的电脑上，从 mobileClaw 仓库根目录运行 bridge 脚本。手机和电脑需要在同一局域网，防火墙需允许端口 52734。",
                        color = c.subtext,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.cardAlt)
                            .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(command, color = c.text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "复制",
                            color = c.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.bg)
                                .clickable { clipboardManager.setText(AnnotatedString(command)) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                    Text(
                        "保存后可在聊天里说：用电脑 Codex 修改某个项目、查看当前状态、停止电脑 Codex 任务。",
                        color = c.subtext.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualDisplaySubPage(
    virtualDisplayManager: VirtualDisplayManager,
    vdTestResult: String?,
    privServerConnected: Boolean,
    c: ClawColors,
    onBack: () -> Unit,
    onTestVirtualDisplay: () -> Unit,
    onCheckPrivServer: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(settingsWorkbenchBrush(c)).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.section_virtual_display), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val isRunning = virtualDisplayManager.isRunning
            val displayId = virtualDisplayManager.displayId
            val isOk   = vdTestResult?.startsWith("ok:") == true
            val isFail = vdTestResult?.startsWith("error:") == true

            SettingsSection(str(R.string.section_virtual_display), c) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(when { isOk -> c.green.copy(alpha = 0.07f); isFail -> c.red.copy(alpha = 0.07f); else -> c.cardAlt }, RoundedCornerShape(10.dp))
                        .border(1.dp, when { isOk -> c.green.copy(alpha = 0.3f); isFail -> c.red.copy(alpha = 0.3f); else -> c.border }, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(when { isOk || isRunning -> c.green; isFail -> c.red; else -> c.subtext.copy(alpha = 0.4f) }))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                isOk     -> str(R.string.vd_available, vdTestResult!!.substringAfter(":"))
                                isFail   -> str(R.string.settings_d1e4a7)
                                isRunning -> str(R.string.vd_running, displayId)
                                else     -> str(R.string.settings_not_2)
                            },
                            color = when { isOk || isRunning -> c.green; isFail -> c.red; else -> c.subtext },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (isFail) Text(vdTestResult!!.substringAfter(":").take(100), color = c.red.copy(alpha = 0.7f), fontSize = 10.sp, lineHeight = 14.sp)
                    }
                    OutlinedButton(onClick = onTestVirtualDisplay, border = ButtonDefaults.outlinedButtonBorder, shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent)) {
                        Text(str(R.string.settings_69d600), fontSize = 12.sp)
                    }
                }
                PrivServerCard(connected = privServerConnected, packageName = "com.mobileclaw", onCheckServer = onCheckPrivServer, c = c)
                VdSetupGuide(c = c, testPassed = isOk)
            }
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

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
        "adb shell 'CLASSPATH=\$(pm path $packageName | cut -d: -f2) /system/bin/app_process / com.mobileclaw.server.PrivilegedServer </dev/null >/dev/null 2>&1 &'"
    }

    Column(
        Modifier.fillMaxWidth()
            .background(if (connected) c.green.copy(alpha = 0.07f) else c.cardAlt, RoundedCornerShape(10.dp))
            .border(1.dp, if (connected) c.green.copy(alpha = 0.3f) else c.border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) c.green else c.subtext.copy(alpha = 0.4f)))
            Spacer(Modifier.width(8.dp))
            Text(if (connected) str(R.string.settings_f6bfdd) else str(R.string.settings_1325ab),
                color = if (connected) c.green else c.subtext, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onCheckServer, border = ButtonDefaults.outlinedButtonBorder, shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent)) {
                Text(str(R.string.settings_69d600), fontSize = 12.sp)
            }
        }
        AnimatedVisibility(!connected, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(str(R.string.settings_a86ef7),
                    color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
                Row(
                    Modifier.fillMaxWidth()
                        .background(c.bg, RoundedCornerShape(7.dp))
                        .border(1.dp, c.border.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(activationCmd, color = c.green, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(5.dp))
                            .background(if (copied) c.green.copy(alpha = 0.15f) else c.accent.copy(alpha = 0.12f))
                            .clickable { clipboardManager.setText(AnnotatedString(activationCmd)); copied = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(if (copied) str(R.string.settings_75420d) else str(R.string.settings_2a5ed5), color = if (copied) c.green else c.accent,
                            fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(str(R.string.settings_47af0e),
                    color = c.subtext.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 13.sp)
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
                str(R.string.settings_d769b1),
                str(R.string.settings_1a31ee),
                str(R.string.settings_719c3b),
                str(R.string.settings_cd97fd),
                str(R.string.settings_a8e011),
                str(R.string.settings_05089e),
                str(R.string.settings_3f177e),
            ),
            adbCommands = listOf(
                "adb shell settings put global enable_freeform_support 1",
                "adb shell settings put global force_desktop_mode_on_external_displays 1",
                str(R.string.settings_ed48be),
                "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
            ),
            adbNote = str(R.string.settings_140121),
        )
        brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") || mfr.contains("xiaomi") -> VdRomInfo(
            name = "MIUI / HyperOS（Xiaomi / Redmi / POCO）",
            manualSteps = listOf(
                str(R.string.settings_settings),
                str(R.string.settings_settings_2),
                str(R.string.settings_39e00d),
                str(R.string.settings_back),
            ),
            adbCommands = listOf("adb shell settings put global enable_freeform_support 1"),
        )
        brand.contains("huawei") || brand.contains("honor") -> VdRomInfo(
            name = "EMUI / HarmonyOS（Huawei / Honor）",
            manualSteps = listOf(
                str(R.string.settings_settings_3),
                str(R.string.settings_settings_4),
                str(R.string.settings_fcbe54),
                str(R.string.settings_back),
            ),
            adbCommands = listOf("adb shell settings put global enable_freeform_support 1"),
        )
        brand.contains("vivo") -> VdRomInfo(
            name = "OriginOS / FuntouchOS（Vivo）",
            manualSteps = listOf(
                str(R.string.settings_settings_5),
                str(R.string.settings_settings_6),
                str(R.string.settings_722368),
                str(R.string.settings_back),
            ),
            adbCommands = listOf("adb shell settings put global enable_freeform_support 1"),
        )
        brand.contains("samsung") || mfr.contains("samsung") -> VdRomInfo(
            name = "One UI（Samsung）",
            manualSteps = listOf(
                str(R.string.settings_settings_7),
                str(R.string.settings_settings_8),
                str(R.string.settings_481b15),
                str(R.string.settings_back),
            ),
            adbCommands = listOf("adb shell settings put global enable_freeform_support 1"),
        )
        else -> VdRomInfo(
            name = "Android（${Build.BRAND}）",
            manualSteps = listOf(
                str(R.string.settings_settings_3),
                str(R.string.settings_fe1097),
                str(R.string.settings_back),
            ),
            adbCommands = listOf("adb shell settings put global enable_freeform_support 1"),
        )
    }
}

@Composable
private fun VdSetupGuide(c: ClawColors, testPassed: Boolean) {
    val romInfo = remember { detectVdRomInfo() }
    var expanded by remember(testPassed) { mutableStateOf(!testPassed) }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { expanded = !expanded }
                .padding(vertical = 7.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_7d9538), color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (expanded) "▲" else "▼", color = c.subtext, fontSize = 9.sp)
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.fillMaxWidth()
                    .background(c.cardAlt, RoundedCornerShape(10.dp))
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClawSymbolIcon("phone", tint = c.subtext, modifier = Modifier.size(14.dp))
                    Text(romInfo.name, color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.settings_6b221c), color = c.subtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                    romInfo.manualSteps.forEachIndexed { i, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${i + 1}.", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                            Text(step, color = c.text, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
                HorizontalDivider(color = c.border, thickness = 0.5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (romInfo.adbNote.isNotEmpty()) stringResource(R.string.settings_adb_commands_with_note, romInfo.adbNote) else stringResource(R.string.settings_12a860),
                        color = c.subtext, fontSize = 10.sp, lineHeight = 14.sp,
                    )
                    romInfo.adbCommands.forEach { cmd ->
                        if (cmd.startsWith("#")) {
                            Text(cmd.removePrefix("# "), color = c.accent.copy(alpha = 0.8f), fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            AdbCommandRow(cmd = cmd, c = c)
                        }
                    }
                }
                Text(stringResource(R.string.settings_7f056c),
                    color = c.subtext.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 13.sp)
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
        Modifier.fillMaxWidth()
            .background(c.bg, RoundedCornerShape(7.dp))
            .border(1.dp, c.border.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cmd, color = c.green, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(5.dp))
                .background(if (copied) c.green.copy(alpha = 0.15f) else c.accent.copy(alpha = 0.12f))
                .clickable { clipboardManager.setText(AnnotatedString(cmd)); copied = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(if (copied) stringResource(R.string.settings_75420d) else stringResource(R.string.console_copy), color = if (copied) c.green else c.accent,
                fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VideoTasksSubPage(
    tasks: List<VideoGenerationTaskEntity>,
    refreshingIds: Set<String>,
    refreshingAll: Boolean,
    c: ClawColors,
    onBack: () -> Unit,
    onRefreshTask: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(c.bg).navigationBarsPadding(),
    ) {
        ClawPageHeader(title = "长任务", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onRefreshAll,
                    enabled = !refreshingAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (refreshingAll) "刷新中..." else "刷新未完成任务")
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("返回")
                }
            }
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface)
                        .border(0.5.dp, c.border, RoundedCornerShape(14.dp))
                        .padding(16.dp),
                ) {
                    Text("还没有视频长任务。生成视频超时后，会自动在这里保留任务 ID 并继续追踪。", color = c.subtext, fontSize = 12.sp, lineHeight = 18.sp)
                }
            } else {
                tasks.forEach { task ->
                    val statusLabel = videoTaskStatusLabel(task)
                    val localVideoFile = task.filePath.takeIf { it.isNotBlank() }?.let(::File)
                    val hasPlayableFile = localVideoFile?.exists() == true
                    val hasRemoteVideo = task.videoUrl.isNotBlank()
                    val isDone = task.status == VideoTaskStatuses.DOWNLOADED || task.status == VideoTaskStatuses.COMPLETED
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface)
                            .border(0.5.dp, c.border, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GatewayPill(text = statusLabel, c = c, active = isDone)
                            TextButton(onClick = { onDeleteTask(task.taskId) }) {
                                Text("删除", color = c.subtext, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = task.prompt,
                            color = c.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp,
                        )
                        when {
                            hasPlayableFile -> {
                                val videoFile = localVideoFile!!
                                VideoAttachmentCard(
                                    attachment = SkillAttachment.FileData(
                                        path = videoFile.absolutePath,
                                        name = videoFile.name,
                                        mimeType = "video/mp4",
                                        sizeBytes = videoFile.length(),
                                    ),
                                    maxWidthDp = 520.dp,
                                    cornerRadiusDp = 14.dp,
                                    onOpenExternally = {
                                        openFileAttachment(
                                            context,
                                            SkillAttachment.FileData(
                                                path = videoFile.absolutePath,
                                                name = videoFile.name,
                                                mimeType = "video/mp4",
                                                sizeBytes = videoFile.length(),
                                            )
                                        )
                                    },
                                )
                            }
                            hasRemoteVideo -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(c.cardAlt.copy(alpha = 0.35f))
                                        .border(0.5.dp, c.border, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("视频已生成", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("下载完成后会在这里直接展示播放器。", color = c.subtext, fontSize = 12.sp, lineHeight = 17.sp)
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(task.videoUrl),
                                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("打开视频")
                                    }
                                }
                            }
                            task.status == VideoTaskStatuses.FAILED -> {
                                Text(
                                    text = task.errorMessage.ifBlank { "生成失败，请重新发起视频生成。" },
                                    color = c.red,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            else -> {
                                Text(
                                    text = "视频生成中，稍后刷新即可查看结果。",
                                    color = c.subtext,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onRefreshTask(task.taskId) },
                                enabled = task.taskId !in refreshingIds && task.status != VideoTaskStatuses.DOWNLOADED,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (task.taskId in refreshingIds) "检查中..." else "刷新结果")
                            }
                        }
                    }
                }
            }
        }
    }
}

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
