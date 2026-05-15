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
import androidx.compose.material.icons.filled.Close
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
import com.mobileclaw.R
import com.mobileclaw.str

// ── Preset catalog — well-known keys with usage hints ─────────────────────────

private data class ConfigKeyMeta(val key: String, val hint: String, val example: String, val sensitive: Boolean = false)

private val CONFIG_CATALOG = listOf(
    ConfigKeyMeta("user.name",           str(R.string.user_config_2c631c),                    str(R.string.user_config_615db5)),
    ConfigKeyMeta("user.profession",     str(R.string.user_config_0ad36d),                 str(R.string.user_config_982ed6)),
    ConfigKeyMeta("user.location",       str(R.string.user_config_1b754d),                   str(R.string.user_config_e94e8b)),
    ConfigKeyMeta("user.language",       str(R.string.user_config_d15af1),                        "zh"),
    ConfigKeyMeta("user.preferences",    str(R.string.user_config_a3ff02),         str(R.string.user_config_8f357f)),
    ConfigKeyMeta("api.openai_key",      "OpenAI API Key",                                  "sk-...", sensitive = true),
    ConfigKeyMeta("api.openai_base",     str(R.string.user_config_32d606),          "https://api.openai.com/v1"),
    ConfigKeyMeta("api.deepseek_key",    "DeepSeek API Key",                                "sk-...", sensitive = true),
    ConfigKeyMeta("task.default_lang",   str(R.string.user_config_ecced9),                "zh"),
    ConfigKeyMeta("task.max_steps",      str(R.string.user_config_34e485),                        "20"),
    ConfigKeyMeta("notification.token",  str(R.string.user_config_0124ee),            "token...", sensitive = true),
    ConfigKeyMeta("app.custom_1",        str(R.string.user_config_4b0faa),         ""),
    ConfigKeyMeta("app.custom_2",        str(R.string.user_config_039747),                                  ""),
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
                androidx.compose.material3.Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
            }
            Text(str(R.string.drawer_user_config), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = { showCatalog = true }) {
                Text(str(R.string.user_config_9cdfce), color = c.accent, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent.copy(alpha = 0.1f))
                    .clickable { showAdd = true },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = str(R.string.user_config_add), tint = c.accent, modifier = Modifier.size(18.dp))
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
                str(R.string.user_config_6fc07d),
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
                ClawIconTile("settings", size = 62.dp, iconSize = 32.dp, tint = c.text, background = c.cardAlt, border = c.border)
                Spacer(Modifier.height(12.dp))
                Text(str(R.string.user_config_empty), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(str(R.string.user_config_tap), color = c.subtext, fontSize = 12.sp)
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
            title = str(R.string.user_config_add_2),
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
            title = str(R.string.edit_entry_title, entryKey),
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
                androidx.compose.material3.Icon(Icons.Default.Delete, contentDescription = str(R.string.skills_delete_confirm), tint = c.red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
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
                        label = { Text(str(R.string.user_config_158342)) },
                        placeholder = { Text(str(R.string.user_config_be31fe)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(str(R.string.user_config_1a498c)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(str(R.string.user_config_43d152)) },
                    placeholder = { Text(str(R.string.user_config_5dfe9a)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (key.isNotBlank()) onConfirm(key.trim(), value, description) }) {
                Text(if (keyReadOnly) str(R.string.role_save) else str(R.string.user_config_add), color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(R.string.btn_cancel), color = c.subtext) }
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
        title = { Text(str(R.string.user_config_719f14), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
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
                        if (alreadyAdded) Text(str(R.string.user_config_done), color = c.subtext, fontSize = 10.sp)
                    }
                    HorizontalDivider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(R.string.btn_close), color = c.subtext) }
        },
    )
}
