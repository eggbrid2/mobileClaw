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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.mobileclaw.config.GatewayConfig
import com.mobileclaw.perception.VirtualDisplayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    "auto" to R.string.lang_auto,
    "zh"   to R.string.lang_zh,
    "en"   to R.string.lang_en,
    "ja"   to R.string.lang_ja,
)

private enum class SettingsSub { GATEWAY, APPEARANCE, FILES, VIRTUAL_DISPLAY }

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
    val snapshot by config.collectAsState(initial = ConfigSnapshot())
    var gateways by remember(snapshot.gateways) { mutableStateOf(snapshot.gateways) }
    var activeGatewayId by remember(snapshot.activeGatewayId) { mutableStateOf(snapshot.activeGatewayId) }
    var language  by remember(snapshot.language) { mutableStateOf(snapshot.language) }
    var darkTheme by remember(snapshot.darkTheme) { mutableStateOf(snapshot.darkTheme) }
    var accent    by remember(snapshot.accentColor) { mutableStateOf<Long>(snapshot.accentColor) }

    var subPage by remember { mutableStateOf<SettingsSub?>(null) }

    val currentSnapshot = {
        snapshot.copy(gateways = gateways, activeGatewayId = activeGatewayId, language = language, darkTheme = darkTheme, accentColor = accent)
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isConfigured = activeGateway != null && activeGateway.endpoint.isNotBlank() && activeGateway.apiKey.isNotBlank()
                val vdRunning = virtualDisplayManager.isRunning

                SettingsHubCard(c) {
                    SettingsCategoryRow(
                        emoji = "🔌",
                        title = str(R.string.settings_849b48),
                        subtitle = if (gateways.isEmpty()) str(R.string.status_not_configured)
                                   else str(R.string.gateways_status, gateways.size, activeGateway?.name ?: "-"),
                        statusOk = isConfigured,
                        c = c,
                    ) { subPage = SettingsSub.GATEWAY }
                }

                SettingsHubCard(c) {
                    SettingsCategoryRow(
                        emoji = "🎨",
                        title = str(R.string.settings_ce650e),
                        subtitle = ThemePresets.find { it.darkTheme == darkTheme && it.accentColor == accent }?.name ?: str(R.string.settings_f1d4ff),
                        statusOk = true,
                        c = c,
                    ) { subPage = SettingsSub.APPEARANCE }
                }

                SettingsHubCard(c) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val storageManager = remember { com.mobileclaw.config.UserStorageManager(ctx) }
                    val hasFileAccess = remember { storageManager.hasAllFilesAccess() }
                    SettingsCategoryRow(
                        emoji = "📁",
                        title = str(R.string.settings_bc417e),
                        subtitle = if (hasFileAccess) str(R.string.settings_done) else str(R.string.settings_not),
                        statusOk = hasFileAccess,
                        c = c,
                    ) { subPage = SettingsSub.FILES }
                    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
                    SettingsCategoryRow(
                        emoji = "🖥️",
                        title = str(R.string.section_virtual_display),
                        subtitle = when {
                            vdTestResult?.startsWith("ok:") == true -> str(R.string.settings_ad6b70)
                            vdRunning -> str(R.string.settings_d679ae)
                            else -> str(R.string.settings_not_2)
                        },
                        statusOk = vdTestResult?.startsWith("ok:") == true || vdRunning,
                        c = c,
                    ) { subPage = SettingsSub.VIRTUAL_DISPLAY }
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
            SettingsSub.FILES -> FilesSubPage(c = c, onBack = { subPage = null })
            SettingsSub.VIRTUAL_DISPLAY -> VirtualDisplaySubPage(
                virtualDisplayManager = virtualDisplayManager,
                vdTestResult = vdTestResult,
                privServerConnected = privServerConnected,
                c = c,
                onBack = { subPage = null },
                onTestVirtualDisplay = onTestVirtualDisplay,
                onCheckPrivServer = onCheckPrivServer,
            )
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
    emoji: String,
    title: String,
    subtitle: String,
    statusOk: Boolean,
    c: ClawColors,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(c.cardAlt),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
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
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                            .padding(horizontal = 16.dp, vertical = 13.dp),
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
            .background(if (isActive) c.accent.copy(alpha = 0.08f) else c.surface)
            .border(1.dp, if (isActive) c.accent.copy(alpha = 0.5f) else c.border, RoundedCornerShape(10.dp))
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
                Text(gateway.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isActive) c.accent else c.text)
                if (gateway.supportsMultimodal) {
                    Text("👁", fontSize = 10.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, disabledContainerColor = c.border),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(str(R.string.role_save), color = Color.White, fontSize = 13.sp) }
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
                                .background(if (active) c.accent.copy(alpha = 0.15f) else c.cardAlt)
                                .border(1.dp, if (active) c.accent.copy(alpha = 0.6f) else c.border, RoundedCornerShape(8.dp))
                                .clickable {
                                    name = preset.name
                                    endpoint = preset.endpoint
                                    model = preset.model
                                    supportsMultimodal = preset.supportsMultimodal
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(preset.name, color = if (active) c.accent else c.subtext, fontSize = 11.sp,
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
private fun AppearanceSubPage(
    darkTheme: Boolean, onDarkTheme: (Boolean) -> Unit,
    accent: Long, onAccent: (Long) -> Unit,
    language: String, onLanguage: (String) -> Unit,
    c: ClawColors,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        ClawPageHeader(title = str(R.string.settings_ce650e), onBack = onBack) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(str(R.string.role_save), color = Color.White, fontSize = 13.sp) }
            Spacer(Modifier.width(4.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(str(R.string.section_theme), c) {
                ThemePresets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            val isActive = darkTheme == preset.darkTheme && accent == preset.accentColor
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) c.accent.copy(alpha = 0.12f) else c.cardAlt)
                                    .border(1.5.dp, if (isActive) c.accent.copy(alpha = 0.7f) else c.border, RoundedCornerShape(10.dp))
                                    .clickable { onDarkTheme(preset.darkTheme); onAccent(preset.accentColor) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(Modifier.size(width = 36.dp, height = 20.dp).clip(RoundedCornerShape(5.dp)).background(Color(preset.previewBg))) {
                                    Box(Modifier.size(8.dp).align(Alignment.Center).clip(CircleShape).background(Color(preset.previewAccent)))
                                }
                                Text(preset.name, color = if (isActive) c.accent else c.subtext, fontSize = 9.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            SettingsSection(str(R.string.section_language), c) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGUAGES.forEach { (code, resId) ->
                        val active = language == code
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (active) c.accent.copy(alpha = 0.15f) else c.cardAlt)
                                .border(1.dp, if (active) c.accent.copy(alpha = 0.6f) else c.border, RoundedCornerShape(8.dp))
                                .clickable { onLanguage(code) }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(resId), color = if (active) c.accent else c.subtext, fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesSubPage(c: ClawColors, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val storageManager = remember { com.mobileclaw.config.UserStorageManager(ctx) }
    var hasFileAccess by remember { mutableStateOf(storageManager.hasAllFilesAccess()) }
    val fileAccessLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { hasFileAccess = storageManager.hasAllFilesAccess() }

    Column(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        ClawPageHeader(title = stringResource(R.string.settings_bc417e), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(if (hasFileAccess) c.green.copy(alpha = 0.07f) else c.cardAlt, RoundedCornerShape(10.dp))
                    .border(1.dp, if (hasFileAccess) c.green.copy(alpha = 0.3f) else c.border, RoundedCornerShape(10.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (hasFileAccess) c.green else c.subtext.copy(alpha = 0.4f)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (hasFileAccess) stringResource(R.string.settings_done_2) else stringResource(R.string.settings_not_3),
                        color = if (hasFileAccess) c.green else c.subtext, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(if (hasFileAccess) stringResource(R.string.settings_5b5f15) else stringResource(R.string.settings_tap_2),
                        color = c.subtext, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            if (!hasFileAccess) {
                Button(
                    onClick = { fileAccessLauncher.launch(storageManager.allFilesAccessSettingsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.settings_f0bdc1), color = Color.White, fontSize = 14.sp) }
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
                    Text("📱", fontSize = 13.sp)
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
                        if (romInfo.adbNote.isNotEmpty()) "ADB 命令（${romInfo.adbNote}）:" else stringResource(R.string.settings_12a860),
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
