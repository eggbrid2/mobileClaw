package com.mobileclaw.ui

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillMarket
import com.mobileclaw.skill.SkillMeta

@Composable
fun SkillsPage(
    allSkills: List<SkillMeta>,
    skillNotes: Map<String, String>,
    skillNoteGenerating: String?,
    onPromote: (String) -> Unit,
    onDemote: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onInstallMarketSkill: (SkillDefinition) -> Unit = {},
    onSaveNote: (skillId: String, note: String) -> Unit,
    onGenerateNote: (skillId: String, name: String, description: String) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    var pendingPromotion by remember { mutableStateOf<SkillMeta?>(null) }
    var pendingDelete by remember { mutableStateOf<SkillMeta?>(null) }
    val installedIds = remember(allSkills) { allSkills.map { it.id }.toSet() }

    // Group by tag, then sort each group by level → name
    val tagGroups = remember(allSkills) {
        val emojiMap = mapOf(
            "控制" to "📱", "后台" to "🖥️", "网络" to "🌐", "文件" to "📁",
            "应用" to "📦", "创作" to "🎨", "记忆" to "🧠", "角色" to "🎭",
            "会话" to "💬", "用户" to "👤", "技能" to "🛠️", "系统" to "⚙️",
        )
        val tagOrder = listOf("控制", "网络", "文件", "应用", "记忆", "创作", "角色", "会话", "用户", "技能", "系统", "后台")
        val grouped = allSkills
            .flatMap { skill -> (skill.tags.ifEmpty { listOf("其他") }).map { tag -> tag to skill } }
            .groupBy({ it.first }, { it.second })
        tagOrder.mapNotNull { tag -> grouped[tag]?.let { Triple(tag, emojiMap[tag] ?: "🔧", it) } } +
            (grouped.keys - tagOrder.toSet()).sorted().mapNotNull { tag ->
                grouped[tag]?.let { Triple(tag, emojiMap[tag] ?: "🔧", it) }
            }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    // Clamp when tagGroups shrinks (e.g. after skill deletion empties a tag group).
    // Use safeTabIndex everywhere so ScrollableTabRow never receives an out-of-bounds value.
    val safeTabIndex = selectedTabIndex.coerceIn(0, tagGroups.size)
    LaunchedEffect(tagGroups.size) {
        if (selectedTabIndex > tagGroups.size) selectedTabIndex = tagGroups.size
    }

    // tabs: tag groups + market tab
    val tabCount = tagGroups.size + 1
    val isMarketTab = safeTabIndex == tagGroups.size

    val selectedGroup = if (!isMarketTab) tagGroups.getOrNull(safeTabIndex) else null
    val byLevel = remember(safeTabIndex, tagGroups) {
        selectedGroup?.third?.groupBy { it.injectionLevel }?.toSortedMap()
    }
    val multiLevel = (byLevel?.size ?: 0) > 1

    BackHandler { onBack() }

    // Promotion confirm dialog
    pendingPromotion?.let { skill ->
        AlertDialog(
            onDismissRequest = { pendingPromotion = null },
            containerColor = c.card,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.skills_promote_title), color = c.text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.skills_promote_body, skill.name), color = c.text, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { onPromote(skill.id); pendingPromotion = null },
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(stringResource(R.string.skills_promote_confirm), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPromotion = null }) {
                    Text(stringResource(R.string.btn_cancel), color = c.subtext)
                }
            },
        )
    }

    // Delete confirm dialog
    pendingDelete?.let { skill ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = c.card,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.skills_delete_title), color = c.text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.skills_delete_body, skill.name), color = c.text, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { onDelete(skill.id); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(stringResource(R.string.skills_delete_confirm), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.btn_cancel), color = c.subtext)
                }
            },
        )
    }

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
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_back), tint = c.text)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.skills_title), color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.skills_loaded, allSkills.size), color = c.subtext, fontSize = 11.sp)
            }
        }

        // ── Horizontal tab row ───────────────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = safeTabIndex,
            containerColor = c.surface,
            contentColor = c.accent,
            edgePadding = 4.dp,
            divider = { HorizontalDivider(color = c.border, thickness = 0.5.dp) },
        ) {
            tagGroups.forEachIndexed { index, (tag, emoji, skills) ->
                val selected = safeTabIndex == index
                Tab(
                    selected = selected,
                    onClick = { selectedTabIndex = index },
                    modifier = Modifier.height(44.dp),
                    selectedContentColor = c.accent,
                    unselectedContentColor = c.subtext,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$emoji $tag", fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        Text("${skills.size}", fontSize = 9.sp, color = if (selected) c.accent else c.subtext.copy(0.6f))
                    }
                }
            }
            // Market tab
            Tab(
                selected = isMarketTab,
                onClick = { selectedTabIndex = tagGroups.size },
                modifier = Modifier.height(44.dp),
                selectedContentColor = c.accent,
                unselectedContentColor = c.subtext,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏪 市场", fontSize = 12.sp, fontWeight = if (isMarketTab) FontWeight.SemiBold else FontWeight.Normal)
                    Text("${SkillMarket.catalog.size}", fontSize = 9.sp, color = if (isMarketTab) c.accent else c.subtext.copy(0.6f))
                }
            }
        }

        // ── Tab content ──────────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isMarketTab) {
                item(key = "__market__") {
                    SkillMarketSection(installedIds = installedIds, onInstall = onInstallMarketSkill, c = c)
                }
            } else if (byLevel != null) {
                byLevel.forEach { (level, levelSkills) ->
                    if (multiLevel) {
                        item(key = "header_$level") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp, start = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(levelLabel[level] ?: "L$level", color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(levelDesc[level] ?: "", color = c.subtext, fontSize = 10.sp)
                                Spacer(Modifier.weight(1f))
                                HorizontalDivider(modifier = Modifier.fillMaxWidth(0.45f), color = c.border.copy(0.4f), thickness = 0.5.dp)
                            }
                        }
                    }
                    lazyItems(levelSkills, key = { it.id }) { skill ->
                        SkillRow(
                            skill = skill,
                            note = skillNotes[skill.id] ?: "",
                            noteGenerating = skillNoteGenerating == skill.id,
                            showPromote = level == 2 && !skill.isBuiltin,
                            showDemote = level == 1 && !skill.isBuiltin,
                            showDelete = !skill.isBuiltin,
                            c = c,
                            onPromote = { if (!skill.isBuiltin) pendingPromotion = skill },
                            onDemote = { if (!skill.isBuiltin) onDemote(skill.id) },
                            onDelete = { if (!skill.isBuiltin) pendingDelete = skill },
                            onSaveNote = { onSaveNote(skill.id, it) },
                            onGenerateNote = { onGenerateNote(skill.id, skill.name, skill.description) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillMarketSection(
    installedIds: Set<String>,
    onInstall: (SkillDefinition) -> Unit,
    c: ClawColors,
) {
    val categories = remember { SkillMarket.catalog.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.cardAlt)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🏪", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("技能市场", color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("国内可用 · 一键安装", color = c.subtext, fontSize = 11.sp)
            }
            Text("${SkillMarket.catalog.size} 个", color = c.subtext, fontSize = 11.sp)
        }

        // Category chips
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                val selected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) c.accent else c.card)
                        .border(0.5.dp, if (selected) c.accent else c.border, RoundedCornerShape(16.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(cat, fontSize = 12.sp, color = if (selected) Color.White else c.text, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Skill cards for selected category
        val items = remember(selectedCategory) { SkillMarket.catalog.filter { it.category == selectedCategory } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { entry ->
                val installed = entry.def.meta.id in installedIds
                MarketSkillCard(entry = entry, installed = installed, onInstall = { onInstall(entry.def) }, c = c)
            }
        }
    }
}

@Composable
private fun MarketSkillCard(
    entry: SkillMarket.MarketEntry,
    installed: Boolean,
    onInstall: () -> Unit,
    c: ClawColors,
) {
    val lang = LocalConfiguration.current.locales[0].language
    val name = if (lang == "zh") entry.def.meta.nameZh ?: entry.def.meta.name else entry.def.meta.name
    val desc = if (lang == "zh") entry.def.meta.descriptionZh ?: entry.def.meta.description else entry.def.meta.description

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.card)
            .border(0.5.dp, c.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, color = c.subtext, fontSize = 11.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (installed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("已安装", fontSize = 11.sp, color = c.accent)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent)
                    .clickable(onClick = onInstall)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("安装", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Tag group block: one tag → level sub-sections ─────────────────────────

private val levelLabel = mapOf(0 to "⚡ 核心", 1 to "🔄 自动", 2 to "🎯 按需")
private val levelDesc  = mapOf(0 to "始终启用", 1 to "任务自动加载", 2 to "按需调用")

@Composable
private fun TagGroupBlock(
    tag: String,
    emoji: String,
    skills: List<SkillMeta>,
    skillNotes: Map<String, String>,
    skillNoteGenerating: String?,
    c: ClawColors,
    onPromote: (SkillMeta) -> Unit,
    onDemote: (SkillMeta) -> Unit,
    onDelete: (SkillMeta) -> Unit,
    onSaveNote: (skillId: String, note: String) -> Unit,
    onGenerateNote: (skillId: String, name: String, description: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    // Group by level, preserve display order 0 → 1 → 2
    val byLevel = remember(skills) { skills.groupBy { it.injectionLevel }.toSortedMap() }
    val multiLevel = byLevel.size > 1

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Tag header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.cardAlt)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tag, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    byLevel.entries.joinToString(" · ") { (lvl, s) -> "${levelLabel[lvl] ?: "L$lvl"} ${s.size}" },
                    color = c.subtext, fontSize = 10.sp,
                )
            }
            Text(
                "${skills.size}  ${if (expanded) "▲" else "▼"}",
                color = c.subtext, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                byLevel.forEach { (level, levelSkills) ->
                    // Level sub-header (only if multiple levels in this tag)
                    if (multiLevel) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 2.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                levelLabel[level] ?: "L$level",
                                color = c.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                levelDesc[level] ?: "",
                                color = c.subtext,
                                fontSize = 10.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(0.5f),
                                color = c.border.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                    levelSkills.forEach { skill ->
                        SkillRow(
                            skill = skill,
                            note = skillNotes[skill.id] ?: "",
                            noteGenerating = skillNoteGenerating == skill.id,
                            showPromote = level == 2 && !skill.isBuiltin,
                            showDemote = level == 1 && !skill.isBuiltin,
                            showDelete = !skill.isBuiltin,
                            c = c,
                            onPromote = { onPromote(skill) },
                            onDemote = { onDemote(skill) },
                            onDelete = { onDelete(skill) },
                            onSaveNote = { onSaveNote(skill.id, it) },
                            onGenerateNote = { onGenerateNote(skill.id, skill.name, skill.description) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillGroupBlock(
    emoji: String,
    title: String,
    subtitle: String,
    skills: List<SkillMeta>,
    showPromote: Boolean,
    skillNotes: Map<String, String>,
    skillNoteGenerating: String?,
    c: ClawColors,
    onPromote: (SkillMeta) -> Unit,
    onDelete: (SkillMeta) -> Unit = {},
    onDemote: (SkillMeta) -> Unit = {},
    onSaveNote: (skillId: String, note: String) -> Unit,
    onGenerateNote: (skillId: String, name: String, description: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.cardAlt)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = c.subtext, fontSize = 11.sp)
            }
            Text(
                "${skills.size}  ${if (expanded) "▲" else "▼"}",
                color = c.subtext,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                skills.forEach { skill ->
                    SkillRow(
                        skill = skill,
                        note = skillNotes[skill.id] ?: "",
                        noteGenerating = skillNoteGenerating == skill.id,
                        showPromote = showPromote && !skill.isBuiltin,
                        showDemote = !skill.isBuiltin && skill.injectionLevel == 1,
                        showDelete = !skill.isBuiltin,
                        c = c,
                        onPromote = { onPromote(skill) },
                        onDemote = { onDemote(skill) },
                        onDelete = { onDelete(skill) },
                        onSaveNote = { onSaveNote(skill.id, it) },
                        onGenerateNote = { onGenerateNote(skill.id, skill.name, skill.description) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    skill: SkillMeta,
    note: String,
    noteGenerating: Boolean,
    showPromote: Boolean,
    showDemote: Boolean = false,
    showDelete: Boolean = false,
    c: ClawColors,
    onPromote: () -> Unit,
    onDemote: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSaveNote: (String) -> Unit,
    onGenerateNote: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var localNote by remember { mutableStateOf(note) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val lang = LocalConfiguration.current.locales[0].language
    val displayName = if (lang == "zh") skill.nameZh ?: skill.name else skill.name
    val displayDesc = if (lang == "zh") skill.descriptionZh ?: skill.description else skill.description

    // Sync external note changes (e.g. from AI generation) into local state
    LaunchedEffect(note) {
        localNote = note
        editing = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(8.dp))
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        // ── Header row: name + type badge + delete ───────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(displayName, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(c.borderActive, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(skill.type.name.lowercase(), color = c.subtext, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            if (showDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = c.subtext.copy(alpha = 0.55f), modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        // ── Description ──────────────────────────────────────────────────────
        Text(displayDesc, color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)

        // ── Notes section ─────────────────────────────────────────────────
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = c.border.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(Modifier.height(5.dp))

        if (editing) {
            // Edit mode: BasicTextField + confirm/cancel
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = localNote,
                    onValueChange = { localNote = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(color = c.text, fontSize = 11.sp, lineHeight = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onSaveNote(localNote.trim())
                        focusManager.clearFocus()
                        editing = false
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (localNote.isEmpty()) {
                                Text("添加备注...", color = c.subtext.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            inner()
                        }
                    },
                    maxLines = 3,
                )
                Spacer(Modifier.width(4.dp))
                // Confirm
                IconButton(onClick = {
                    onSaveNote(localNote.trim())
                    focusManager.clearFocus()
                    editing = false
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                }
                // Cancel
                IconButton(onClick = {
                    localNote = note
                    focusManager.clearFocus()
                    editing = false
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(14.dp))
                }
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            // Display mode: note text + edit/AI buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.isNotBlank()) {
                    Text(
                        note,
                        color = c.subtext,
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f).clickable { editing = true },
                    )
                } else {
                    Text(
                        "添加备注...",
                        color = c.subtext.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f).clickable { editing = true },
                    )
                }
                Spacer(Modifier.width(4.dp))
                // Edit pencil (only when note exists)
                if (note.isNotBlank()) {
                    IconButton(onClick = { editing = true }, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = c.subtext.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                    }
                }
                // AI generate button
                if (noteGenerating) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = c.accent,
                            strokeWidth = 1.5.dp,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(c.accent.copy(alpha = 0.10f))
                            .border(0.5.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .clickable { onGenerateNote() }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text("✨ AI", color = c.accent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Promote / Demote buttons ──────────────────────────────────────
        if (showPromote || showDemote) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showPromote) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.accent.copy(alpha = 0.12f))
                            .border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable { onPromote() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(stringResource(R.string.skills_promote), color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (showDemote) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(c.subtext.copy(alpha = 0.08f))
                            .border(1.dp, c.border, RoundedCornerShape(6.dp))
                            .clickable { onDemote() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(stringResource(R.string.skills_demote), color = c.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
