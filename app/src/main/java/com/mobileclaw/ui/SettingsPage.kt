package com.mobileclaw.ui

import android.os.Build
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mobileclaw.config.ConfigSnapshot
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.config.CacheCategory
import com.mobileclaw.config.CacheCleaner
import com.mobileclaw.llm.LocalModelInfo
import com.mobileclaw.perception.VirtualDisplayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import com.mobileclaw.str

private data class GatewayPreset(val name: String, val endpoint: String, val model: String, val supportsMultimodal: Boolean = true)

private val GATEWAY_PRESETS = listOf(
    GatewayPreset("OpenAI", "https://api.openai.com", "gpt-4o"),
    GatewayPreset("Groq", "https://api.groq.com/openai", "llama-3.3-70b-versatile", supportsMultimodal = false),
    GatewayPreset("Ollama", "http://localhost:11434/v1", "llama3.2", supportsMultimodal = false),
    GatewayPreset("Codex (o3)", "https://api.openai.com", "o3", supportsMultimodal = false),
)

private val LANGUAGES = listOf(
    "zh"   to R.string.lang_zh,
    "en"   to R.string.lang_en,
)

private enum class SettingsSub { GATEWAY, LOCAL_MODEL, APPEARANCE, PERMISSIONS, VIRTUAL_DISPLAY, CACHE }

@Composable
fun SettingsPage(
    config: Flow<ConfigSnapshot>,
    virtualDisplayManager: VirtualDisplayManager,
    vdTestResult: String?,
    privServerConnected: Boolean,
    onSave: (ConfigSnapshot) -> Unit,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit,
    onTestVirtualDisplay: () -> Unit,
    onCheckPrivServer: () -> Unit,
    localModels: List<LocalModelInfo>,
    onLocalModelEnabled: (Boolean) -> Unit,
    onSelectLocalModel: (String) -> Unit,
    onDownloadLocalModel: (String, String, String) -> Unit,
    onImportLocalModel: (String, android.net.Uri) -> Unit,
    onDeleteLocalModel: (String) -> Unit,
) {
    val c = LocalClawColors.current
    val snapshot by config.collectAsState(initial = ConfigSnapshot())
    var gateways by remember(snapshot.gateways) { mutableStateOf(snapshot.gateways) }
    var activeGatewayId by remember(snapshot.activeGatewayId) { mutableStateOf(snapshot.activeGatewayId) }
    var language  by remember(snapshot.language) { mutableStateOf(snapshot.language) }
    var darkTheme by remember(snapshot.darkTheme) { mutableStateOf(snapshot.darkTheme) }
    var accent    by remember(snapshot.accentColor) { mutableStateOf(snapshot.accentColor) }
    var uiStyle   by remember(snapshot.uiStyle) { mutableStateOf(snapshot.uiStyle) }
    var localEnabled by remember(snapshot.localModelEnabled) { mutableStateOf(snapshot.localModelEnabled) }

    var subPage by remember { mutableStateOf<SettingsSub?>(null) }

    val currentSnapshot = {
        snapshot.copy(gateways = gateways, activeGatewayId = activeGatewayId, language = language, darkTheme = darkTheme, accentColor = accent, uiStyle = uiStyle, localModelEnabled = localEnabled)
    }

    BackHandler {
        if (subPage != null) subPage = null else onBack()
    }

    val activeGateway = gateways.find { it.id == activeGatewayId } ?: gateways.firstOrNull()

    if (subPage == null) {
        // ── Hub list ─────────────────────────────────────────────────────────
        Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
            ClawPageHeader(title = str(R.string.drawer_settings), onBack = onBack)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                val isConfigured = activeGateway != null && activeGateway.endpoint.isNotBlank() && activeGateway.apiKey.isNotBlank()
                val vdRunning = virtualDisplayManager.isRunning

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
                        iconKey = "cache",
                        title = str(R.string.cache_title),
                        subtitle = str(R.string.cache_subtitle),
                        statusOk = true,
                        c = c,
                    ) { subPage = SettingsSub.CACHE }
                }
                SettingsHubCard(c) {
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
                uiStyle = uiStyle, onUiStyle = { uiStyle = it },
                c = c, onBack = { subPage = null },
                onSave = { onSave(currentSnapshot()); subPage = null },
            )
            SettingsSub.LOCAL_MODEL -> LocalModelSubPage(
                models = localModels,
                enabled = localEnabled,
                selectedModelId = snapshot.localModelId,
                c = c,
                onBack = { subPage = null },
                onEnabled = {
                    localEnabled = it
                    onLocalModelEnabled(it)
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
            SettingsSub.CACHE -> CacheSubPage(c = c, onBack = { subPage = null })
            null -> {}
        }
    }
}

// ── Hub composables ───────────────────────────────────────────────────────────

@Composable
private fun SettingsHubCard(c: ClawColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(0.5.dp, c.border, RoundedCornerShape(12.dp)),
        content = content,
    )
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(c.cardAlt),
            contentAlignment = Alignment.Center,
        ) {
            ClawSymbolIcon(iconKey, tint = c.text, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.text)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = if (statusOk) c.subtext else c.red.copy(alpha = 0.7f),
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

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.settings_849b48), onBack = onBack) {
            TextButton(onClick = {
                editingGateway = GatewayConfig(name = str(R.string.settings_7acaf4), endpoint = "", apiKey = "", model = "gpt-4o")
            }) { Text(str(R.string.settings_ed7823), color = c.accent, fontSize = 13.sp) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(R.string.settings_0733e8), fontSize = 15.sp, color = c.subtext)
                        Text(str(R.string.settings_tap), fontSize = 12.sp, color = c.subtext.copy(alpha = 0.6f))
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

            // Presets section
            if (list.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(str(R.string.settings_1de35a), color = c.subtext.copy(alpha = 0.7f), fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                GATEWAY_PRESETS.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface)
                            .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
                            .clickable {
                                editingGateway = GatewayConfig(
                                    name = preset.name,
                                    endpoint = preset.endpoint,
                                    apiKey = "",
                                    model = preset.model,
                                    supportsMultimodal = preset.supportsMultimodal,
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.text)
                            Text(preset.endpoint.removePrefix("https://").removePrefix("http://").take(36),
                                fontSize = 11.sp, color = c.subtext)
                        }
                        Text(if (preset.supportsMultimodal) str(R.string.settings_8fabe3) else str(R.string.settings_90f268),
                            fontSize = 10.sp, color = c.subtext)
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
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .border(1.dp, if (isActive) c.text else c.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onActivate)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (isActive) c.green else c.subtext.copy(alpha = 0.25f))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(gateway.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                if (gateway.supportsMultimodal) {
                    ClawSymbolIcon("eye", tint = c.subtext, modifier = Modifier.size(12.dp))
                }
            }
            Text(gateway.endpoint.removePrefix("https://").removePrefix("http://").take(32).ifBlank { str(R.string.status_not_configured) },
                fontSize = 11.sp, color = c.subtext)
            Text(gateway.model.take(28), fontSize = 11.sp, color = c.subtext.copy(alpha = 0.7f))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = c.subtext, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = c.red.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
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
    var name by remember { mutableStateOf(gateway.name) }
    var endpoint by remember { mutableStateOf(gateway.endpoint) }
    var apiKey by remember { mutableStateOf(gateway.apiKey) }
    var model by remember { mutableStateOf(gateway.model) }
    var embModel by remember { mutableStateOf(gateway.embeddingModel) }
    var supportsMultimodal by remember { mutableStateOf(gateway.supportsMultimodal) }

    val isValid = endpoint.isNotBlank() && apiKey.isNotBlank() && name.isNotBlank()

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        ClawPageHeader(title = if (gateway.endpoint.isBlank()) str(R.string.settings_add) else str(R.string.settings_edit), onBack = onBack) {
            Button(
                onClick = {
                    onSave(gateway.copy(
                        name = name.trim(),
                        endpoint = endpoint.trim(),
                        apiKey = apiKey.trim(),
                        model = model.trim(),
                        embeddingModel = embModel.trim(),
                        supportsMultimodal = supportsMultimodal,
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
            // Presets
            SettingsSection(str(R.string.settings_cac718), c) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GATEWAY_PRESETS.size) { i ->
                        val preset = GATEWAY_PRESETS[i]
                        val active = endpoint == preset.endpoint && model == preset.model
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(if (active) c.text else c.cardAlt)
                                .border(1.dp, if (active) c.text else c.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    name = preset.name
                                    endpoint = preset.endpoint
                                    model = preset.model
                                    supportsMultimodal = preset.supportsMultimodal
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(preset.name, color = if (active) c.bg else c.subtext, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
            SettingsSection(str(R.string.settings_9e5ffa), c) {
                ClawPageTextField(name, { name = it }, str(R.string.role_field_name), "OpenAI", c)
                ClawPageTextField(endpoint, { endpoint = it }, str(R.string.field_endpoint), "https://api.openai.com", c)
                ClawPageTextField(apiKey, { apiKey = it }, str(R.string.field_api_key), "sk-...", c, isSecret = true)
            }
            SettingsSection(str(R.string.section_models), c) {
                ClawPageTextField(model, { model = it }, str(R.string.field_chat_model), "gpt-4o", c)
                ClawPageTextField(embModel, { embModel = it }, str(R.string.field_embed_model), "text-embedding-3-small", c)
                Text(str(R.string.embed_hint), color = c.subtext.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp)
            }
            SettingsSection(str(R.string.settings_14c651), c) {
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
}

@Composable
private fun LocalModelSubPage(
    models: List<LocalModelInfo>,
    enabled: Boolean,
    selectedModelId: String,
    c: ClawColors,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
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

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
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

            models.forEach { model ->
                LocalModelItem(
                    model = model,
                    selected = model.id == selectedModelId,
                    selectedSourceId = selectedSourceByModel[model.id] ?: model.downloadSources.firstOrNull()?.id.orEmpty(),
                    c = c,
                    onSelect = { onSelect(model.id) },
                    onSourceSelect = { sourceId ->
                        selectedSourceByModel = selectedSourceByModel + (model.id to sourceId)
                    },
                    onDownload = { url ->
                        onDownload(model.id, hfToken.trim(), url.ifBlank { customUrl.trim() })
                    },
                    onImport = {
                        importTarget = model.id
                        picker.launch(arrayOf("*/*"))
                    },
                    onDelete = { onDelete(model.id) },
                )
            }
        }
    }
}

@Composable
private fun LocalModelItem(
    model: LocalModelInfo,
    selected: Boolean,
    selectedSourceId: String,
    c: ClawColors,
    onSelect: () -> Unit,
    onSourceSelect: (String) -> Unit,
    onDownload: (String) -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(0.5.dp, c.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClawIconTile(
                symbol = "model",
                size = 38.dp,
                iconSize = 20.dp,
                tint = c.text,
                background = c.cardAlt,
                border = c.border,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.name, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LocalModelChip(text = model.family, c = c)
                    LocalModelChip(text = localModelCapabilities(model), c = c)
                    LocalModelChip(
                        text = if (model.supportsChatRuntime) str(R.string.local_model_runtime_chat) else str(R.string.local_model_runtime_package),
                        c = c,
                    )
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
                Text(
                    when {
                        selected -> str(R.string.local_model_selected)
                        model.installed -> str(R.string.local_model_installed)
                        else -> str(R.string.local_model_not_installed)
                    },
                    color = if (selected || model.installed) c.green else c.subtext,
                    fontSize = 11.sp,
                )
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
    uiStyle: String, onUiStyle: (String) -> Unit,
    c: ClawColors,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
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
            SettingsSection(str(R.string.settings_ui_style), c) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeModeCard(
                        modifier = Modifier.weight(1f),
                        title = str(R.string.settings_style_desk),
                        subtitle = str(R.string.settings_style_desk_desc),
                        active = uiStyle == "desk",
                        dark = darkTheme,
                        c = c,
                    ) { onUiStyle("desk") }
                    ThemeModeCard(
                        modifier = Modifier.weight(1f),
                        title = str(R.string.settings_style_classic),
                        subtitle = str(R.string.settings_style_classic_desc),
                        active = uiStyle == "classic",
                        dark = false,
                        c = c,
                    ) { onUiStyle("classic") }
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

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
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

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
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
private fun VirtualDisplaySubPage(
    virtualDisplayManager: VirtualDisplayManager,
    vdTestResult: String?,
    privServerConnected: Boolean,
    c: ClawColors,
    onBack: () -> Unit,
    onTestVirtualDisplay: () -> Unit,
    onCheckPrivServer: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
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
