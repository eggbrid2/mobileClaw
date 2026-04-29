package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.config.ConfigEntry

// ── Preset catalog — well-known keys with usage hints ─────────────────────────

private data class ConfigKeyMeta(val key: String, val hint: String, val example: String, val sensitive: Boolean = false)

private val CONFIG_CATALOG = listOf(
    ConfigKeyMeta("user.name",           "你的真实姓名，AI 会用于称呼你",                    "张三"),
    ConfigKeyMeta("user.profession",     "职业/行业，帮助 AI 理解你的背景",                 "软件工程师"),
    ConfigKeyMeta("user.location",       "所在城市，用于天气/位置类任务",                   "上海"),
    ConfigKeyMeta("user.language",       "偏好语言代码（zh/en/ja）",                        "zh"),
    ConfigKeyMeta("user.preferences",    "个人偏好和兴趣爱好，AI 会据此个性化回复",         "喜欢音乐,偏好简洁直接的沟通风格"),
    ConfigKeyMeta("api.openai_key",      "OpenAI API Key",                                  "sk-...", sensitive = true),
    ConfigKeyMeta("api.openai_base",     "OpenAI API Base URL（自定义端点时填写）",          "https://api.openai.com/v1"),
    ConfigKeyMeta("api.deepseek_key",    "DeepSeek API Key",                                "sk-...", sensitive = true),
    ConfigKeyMeta("task.default_lang",   "任务执行时默认语言（auto/zh/en）",                "zh"),
    ConfigKeyMeta("task.max_steps",      "Agent 单次任务最大步骤数",                        "20"),
    ConfigKeyMeta("notification.token",  "推送/Webhook Token（通知类技能使用）",            "token...", sensitive = true),
    ConfigKeyMeta("app.custom_1",        "自定义配置项 1（供你的 Skill 脚本读取）",         ""),
    ConfigKeyMeta("app.custom_2",        "自定义配置项 2",                                  ""),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserConfigPage(
    entries: Map<String, ConfigEntry>,
    onSet: (key: String, value: String, description: String) -> Unit,
    onDelete: (key: String) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    var showAdd by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding(),
    ) {
        // TopBar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(Icons.Default.ArrowBack, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
            }
            Text("用户配置", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = { showCatalog = true }) {
                Text("预设", color = c.accent, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent.copy(alpha = 0.1f))
                    .clickable { showAdd = true },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "添加", tint = c.accent, modifier = Modifier.size(18.dp))
            }
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        // Hint banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.accent.copy(alpha = 0.06f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "这里的配置可被 Agent Skill 通过 user_config 工具读取。添加说明帮助 AI 理解每项用途。",
                color = c.subtext,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("⚙️", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("暂无配置项", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("点击右上角「+」添加，或「预设」选择常用配置", color = c.subtext, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(entries.entries.toList(), key = { it.key }) { (key, entry) ->
                    ConfigEntryRow(
                        entryKey = key,
                        entry = entry,
                        onEdit = { newValue, newDesc -> onSet(key, newValue, newDesc) },
                        onDelete = { onDelete(key) },
                    )
                    HorizontalDivider(color = c.border.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (showAdd) {
        EntryDialog(
            title = "添加配置项",
            initialKey = "",
            initialValue = "",
            initialDescription = "",
            onDismiss = { showAdd = false },
            onConfirm = { key, value, desc ->
                onSet(key, value, desc)
                showAdd = false
            },
        )
    }

    if (showCatalog) {
        CatalogDialog(
            existing = entries.keys,
            onDismiss = { showCatalog = false },
            onSelect = { meta ->
                onSet(meta.key, meta.example, meta.hint)
                showCatalog = false
            },
        )
    }
}

@Composable
private fun ConfigEntryRow(
    entryKey: String,
    entry: ConfigEntry,
    onEdit: (value: String, description: String) -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalClawColors.current
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        EntryDialog(
            title = "编辑 · $entryKey",
            initialKey = entryKey,
            initialValue = entry.value,
            initialDescription = entry.description,
            keyReadOnly = true,
            onDismiss = { editing = false },
            onConfirm = { _, value, desc -> onEdit(value, desc); editing = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true }
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entryKey, color = c.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (entry.description.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(entry.description, color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(Modifier.height(4.dp))
                val isSensitive = entryKey.lowercase().let {
                    it.contains("key") || it.contains("token") || it.contains("secret") || it.contains("password")
                }
                Text(
                    text = if (isSensitive) "••••••" + entry.value.takeLast(4) else entry.value,
                    color = c.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = "删除", tint = c.red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EntryDialog(
    title: String,
    initialKey: String,
    initialValue: String,
    initialDescription: String,
    keyReadOnly: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (key: String, value: String, description: String) -> Unit,
) {
    val c = LocalClawColors.current
    var key by remember { mutableStateOf(initialKey) }
    var value by remember { mutableStateOf(initialValue) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!keyReadOnly) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("键 (key)") },
                        placeholder = { Text("如 user.name 或 api.custom_key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值 (value)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("说明（可选，帮助 AI 理解用途）") },
                    placeholder = { Text("如：用于天气查询的城市默认值") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (key.isNotBlank()) onConfirm(key.trim(), value, description) }) {
                Text(if (keyReadOnly) "保存" else "添加", color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.subtext) }
        },
    )
}

@Composable
private fun CatalogDialog(
    existing: Set<String>,
    onDismiss: () -> Unit,
    onSelect: (ConfigKeyMeta) -> Unit,
) {
    val c = LocalClawColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("预设配置项", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(CONFIG_CATALOG) { meta ->
                    val alreadyAdded = meta.key in existing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (alreadyAdded) c.border.copy(alpha = 0.3f) else Color.Transparent)
                            .clickable(enabled = !alreadyAdded) { onSelect(meta) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(meta.key, color = if (alreadyAdded) c.subtext else c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(meta.hint, color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        if (alreadyAdded) Text("已添加", color = c.subtext, fontSize = 10.sp)
                    }
                    HorizontalDivider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = c.subtext) }
        },
    )
}
