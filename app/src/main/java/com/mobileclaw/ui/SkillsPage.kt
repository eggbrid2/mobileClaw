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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.skill.SkillDefinition
import com.mobileclaw.skill.SkillMarket
import com.mobileclaw.skill.SkillMeta
import com.mobileclaw.str

@Composable
fun SkillsPage(
    allSkills: List<SkillMeta>,
    skillNotes: Map<String, String>,
    skillNoteGenerating: String?,
    skillLevelOverrides: Map<String, Int> = emptyMap(),
    onPromote: (String) -> Unit,
    onDemote: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onSetSkillLevel: (skillId: String, level: Int) -> Unit = { _, _ -> },
    onInstallMarketSkill: (SkillDefinition) -> Unit = {},
    onSaveNote: (skillId: String, note: String) -> Unit,
    onGenerateNote: (skillId: String, name: String, description: String) -> Unit,
    onBack: () -> Unit,
    showHeader: Boolean = true,
) {
    val c = LocalClawColors.current
    var pendingPromotion by remember { mutableStateOf<SkillMeta?>(null) }
    var pendingDelete by remember { mutableStateOf<SkillMeta?>(null) }
    val installedIds = remember(allSkills) { allSkills.map { it.id }.toSet() }

    // Group by tag, then sort each group by level → name
    val tagGroups = remember(allSkills) {
        val emojiMap = mapOf(
            str(R.string.skills_34e47d) to "phone", str(R.string.skills_066ae8) to "desktop", str(R.string.skills_7ddbe1) to "web", str(R.string.skills_2a0c47) to "folder",
            str(R.string.drawer_apps) to "package", str(R.string.skills_93d695) to "appearance", str(R.string.profile_44e4d7) to "profile", str(R.string.drawer_roles) to "roles",
            str(R.string.skills_9a834e) to "chat", str(R.string.group_chat_1fd02a) to "user", str(R.string.drawer_skills) to "tools", str(R.string.skills_8a8b89) to "settings",
        )
        val tagOrder = listOf(str(R.string.skills_34e47d), str(R.string.skills_7ddbe1), str(R.string.skills_2a0c47), str(R.string.drawer_apps), str(R.string.profile_44e4d7), str(R.string.skills_93d695), str(R.string.drawer_roles), str(R.string.skills_9a834e), str(R.string.group_chat_1fd02a), str(R.string.drawer_skills), str(R.string.skills_8a8b89), str(R.string.skills_066ae8))
        val grouped = allSkills
            .flatMap { skill -> (skill.tags.ifEmpty { listOf(str(R.string.skills_0d98c7)) }).map { tag -> tag to skill } }
            .groupBy({ it.first }, { it.second })
        tagOrder.mapNotNull { tag -> grouped[tag]?.let { Triple(tag, emojiMap[tag] ?: "tools", it) } } +
            (grouped.keys - tagOrder.toSet()).sorted().mapNotNull { tag ->
                grouped[tag]?.let { Triple(tag, emojiMap[tag] ?: "tools", it) }
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
    val byLevel = remember(safeTabIndex, tagGroups, skillLevelOverrides) {
        selectedGroup?.third
            ?.groupBy { skillLevelOverrides[it.id] ?: it.injectionLevel }
            ?.toSortedMap()
    }
    val multiLevel = (byLevel?.size ?: 0) > 1

    BackHandler { onBack() }

    // Promotion confirm dialog
    pendingPromotion?.let { skill ->
        AlertDialog(
            onDismissRequest = { pendingPromotion = null },
            containerColor = c.card,
            shape = RoundedCornerShape(16.dp),
            title = { Text(str(R.string.skills_promote_title), color = c.text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.skills_promote_body, skill.name), color = c.text, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { onPromote(skill.id); pendingPromotion = null },
                    colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.bg),
                    shape = RoundedCornerShape(18.dp),
                ) { Text(str(R.string.skills_promote_confirm), maxLines = 1) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPromotion = null }) {
                    Text(str(R.string.btn_cancel), color = c.subtext)
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
            title = { Text(str(R.string.skills_delete_title), color = c.text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.skills_delete_body, skill.name), color = c.text, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { onDelete(skill.id); pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                ) { Text(str(R.string.skills_delete_confirm), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(str(R.string.btn_cancel), color = c.subtext)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .then(if (showHeader) Modifier.statusBarsPadding() else Modifier)
            .navigationBarsPadding(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        if (showHeader) Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = str(R.string.btn_back), tint = c.text)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(str(R.string.drawer_skills), color = c.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stringResource(R.string.skills_loaded, allSkills.size), color = c.subtext, fontSize = 11.sp)
            }
        }

        // ── Horizontal tab row ───────────────────────────────────────────────
        CompactScrollableTabs(
            items = tagGroups.mapIndexed { index, (tag, _, skills) ->
                CompactTabItem(tag, skills.size.toString(), safeTabIndex == index) {
                    selectedTabIndex = index
                }
            } + CompactTabItem(str(R.string.skills_0e0282), SkillMarket.catalog.size.toString(), isMarketTab) {
                selectedTabIndex = tagGroups.size
            },
            modifier = Modifier.background(c.surface),
        )
        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        // ── Tab content ──────────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
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
                        val effectiveLevel = skillLevelOverrides[skill.id] ?: skill.injectionLevel
                        SkillRow(
                            skill = skill,
                            note = skillNotes[skill.id] ?: "",
                            noteGenerating = skillNoteGenerating == skill.id,
                            effectiveLevel = effectiveLevel,
                            showPromote = effectiveLevel == 2 && !skill.isBuiltin,
                            showDemote = effectiveLevel == 1 && !skill.isBuiltin,
                            showDelete = !skill.isBuiltin,
                            c = c,
                            onPromote = { if (!skill.isBuiltin) pendingPromotion = skill },
                            onDemote = { if (!skill.isBuiltin) onDemote(skill.id) },
                            onDelete = { if (!skill.isBuiltin) pendingDelete = skill },
                            onSetLevel = { newLevel -> onSetSkillLevel(skill.id, newLevel) },
                            onSaveNote = { onSaveNote(skill.id, it) },
                            onGenerateNote = { onGenerateNote(skill.id, skill.name, skill.description) },
                        )
                    }
                }
            }
        }
    }
}

data class CompactTabItem(
    val label: String,
    val meta: String = "",
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun CompactScrollableTabs(
    items: List<CompactTabItem>,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lazyItems(items) { item ->
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .widthIn(min = 74.dp, max = 132.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (item.selected) c.text else c.cardAlt)
                    .border(
                        0.5.dp,
                        if (item.selected) c.text else c.border,
                        RoundedCornerShape(19.dp),
                    )
                    .clickable(onClick = item.onClick)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.label,
                    color = if (item.selected) c.bg else c.text,
                    fontSize = 12.sp,
                    fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.meta.isNotBlank()) {
                    Text(
                        item.meta,
                        color = if (item.selected) c.bg.copy(alpha = 0.72f) else c.subtext,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
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
            ClawSymbolIcon("market", tint = c.text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(str(R.string.skill_market_5917e2), color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(str(R.string.skills_c36558), color = c.subtext, fontSize = 11.sp)
            }
            Text(str(R.string.count_items, SkillMarket.catalog.size), color = c.subtext, fontSize = 11.sp)
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
        ClawIconTile(
            symbol = entry.emoji,
            size = 38.dp,
            iconSize = 20.dp,
            tint = c.text,
            background = c.cardAlt,
            border = c.border,
        )
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
                Text(str(R.string.skill_market_done), fontSize = 11.sp, color = c.accent)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent)
                    .clickable(onClick = onInstall)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(str(R.string.skill_market_e655a4), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Tag group block: one tag → level sub-sections ─────────────────────────

private val levelLabel = mapOf(0 to str(R.string.skills_49cfc9), 1 to str(R.string.skills_ac2f6f), 2 to str(R.string.skills_4df4a4))
private val levelDesc  = mapOf(0 to str(R.string.skills_5d6895), 1 to str(R.string.skills_080ffc), 2 to str(R.string.skills_875f7e))

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
            ClawSymbolIcon(emoji, tint = c.text, modifier = Modifier.size(18.dp))
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
            ClawSymbolIcon(emoji, tint = c.text, modifier = Modifier.size(18.dp))
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
    effectiveLevel: Int = skill.injectionLevel,
    showPromote: Boolean,
    showDemote: Boolean = false,
    showDelete: Boolean = false,
    c: ClawColors,
    onPromote: () -> Unit,
    onDemote: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSetLevel: (Int) -> Unit = {},
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
                                Text(str(R.string.skills_add), color = c.subtext.copy(alpha = 0.5f), fontSize = 11.sp)
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
                        str(R.string.skills_add),
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
                            .background(c.surface)
                            .border(0.5.dp, c.border, RoundedCornerShape(4.dp))
                            .clickable { onGenerateNote() }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text("AI", color = c.text, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ── Level selector (all skills) ───────────────────────────────────
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0 to str(R.string.skills_49cfc9), 1 to str(R.string.skills_ac2f6f), 2 to str(R.string.skills_4df4a4)).forEach { (lvl, label) ->
                val selected = effectiveLevel == lvl
                val isDefault = lvl == skill.injectionLevel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (selected) c.text
                            else c.surface,
                        )
                        .border(
                            1.dp,
                            if (selected) c.text else c.border,
                            RoundedCornerShape(5.dp),
                        )
                        .clickable {
                            if (!selected) {
                                if (skill.isBuiltin) onSetLevel(lvl)
                                else when (lvl) {
                                    1 -> onPromote()
                                    2 -> onDemote()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        label + if (!isDefault && selected) " *" else "",
                        color = if (selected) c.bg else c.subtext.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            if (effectiveLevel != skill.injectionLevel) {
                // Show reset hint when overridden
                Text(
                    "↩",
                    color = c.subtext.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onSetLevel(skill.injectionLevel) },
                )
            }
        }
    }
}
