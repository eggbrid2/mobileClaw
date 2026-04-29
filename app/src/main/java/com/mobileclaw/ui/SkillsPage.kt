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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import com.mobileclaw.skill.SkillMeta

@Composable
fun SkillsPage(
    allSkills: List<SkillMeta>,
    skillNotes: Map<String, String>,
    skillNoteGenerating: String?,
    onPromote: (String) -> Unit,
    onSaveNote: (skillId: String, note: String) -> Unit,
    onGenerateNote: (skillId: String, name: String, description: String) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalClawColors.current
    var pendingPromotion by remember { mutableStateOf<SkillMeta?>(null) }

    val core      = remember(allSkills) { allSkills.filter { it.injectionLevel == 0 }.sortedBy { it.name } }
    val contextual = remember(allSkills) { allSkills.filter { it.injectionLevel == 1 }.sortedBy { it.name } }
    val onDemand  = remember(allSkills) { allSkills.filter { it.injectionLevel == 2 }.sortedBy { it.name } }

    BackHandler { onBack() }

    // Promotion confirm dialog
    pendingPromotion?.let { skill ->
        AlertDialog(
            onDismissRequest = { pendingPromotion = null },
            containerColor = c.card,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.skills_promote_title), color = c.text, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.skills_promote_body, skill.name),
                    color = c.text,
                    fontSize = 14.sp,
                )
            },
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
                Text(stringResource(R.string.skills_title), color = c.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.skills_loaded, allSkills.size), color = c.subtext, fontSize = 11.sp)
            }
        }

        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (core.isNotEmpty()) {
                item {
                    SkillGroupBlock(
                        emoji = "⚡",
                        title = stringResource(R.string.skills_group_core),
                        subtitle = stringResource(R.string.skills_group_core_sub),
                        skills = core,
                        showPromote = false,
                        skillNotes = skillNotes,
                        skillNoteGenerating = skillNoteGenerating,
                        c = c,
                        onPromote = {},
                        onSaveNote = onSaveNote,
                        onGenerateNote = onGenerateNote,
                    )
                }
            }
            if (contextual.isNotEmpty()) {
                item {
                    SkillGroupBlock(
                        emoji = "🔄",
                        title = stringResource(R.string.skills_group_auto),
                        subtitle = stringResource(R.string.skills_group_auto_sub),
                        skills = contextual,
                        showPromote = false,
                        skillNotes = skillNotes,
                        skillNoteGenerating = skillNoteGenerating,
                        c = c,
                        onPromote = {},
                        onSaveNote = onSaveNote,
                        onGenerateNote = onGenerateNote,
                    )
                }
            }
            if (onDemand.isNotEmpty()) {
                item {
                    SkillGroupBlock(
                        emoji = "🎯",
                        title = stringResource(R.string.skills_group_ondemand),
                        subtitle = stringResource(R.string.skills_group_ondemand_sub),
                        skills = onDemand,
                        showPromote = true,
                        skillNotes = skillNotes,
                        skillNoteGenerating = skillNoteGenerating,
                        c = c,
                        onPromote = { skill -> if (!skill.isBuiltin) pendingPromotion = skill },
                        onSaveNote = onSaveNote,
                        onGenerateNote = onGenerateNote,
                    )
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
                        c = c,
                        onPromote = { onPromote(skill) },
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
    c: ClawColors,
    onPromote: () -> Unit,
    onSaveNote: (String) -> Unit,
    onGenerateNote: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var localNote by remember { mutableStateOf(note) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

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
        // ── Header row: name + type badge ────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(skill.name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(c.borderActive, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(skill.type.name.lowercase(), color = c.subtext, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(3.dp))
        // ── Description ──────────────────────────────────────────────────────
        Text(skill.description, color = c.subtext, fontSize = 11.sp, lineHeight = 15.sp)

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

        // ── Promote button ────────────────────────────────────────────────
        if (showPromote) {
            Spacer(Modifier.height(8.dp))
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
    }
}
