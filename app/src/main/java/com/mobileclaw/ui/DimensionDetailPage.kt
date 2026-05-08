package com.mobileclaw.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobileclaw.R
import com.mobileclaw.str

// ── Quiz data per dimension ───────────────────────────────────────────────────

private data class QuizQuestion(
    val question: String,
    val answers: List<String>,
    val factKey: String,
)

private val DIMENSION_QUIZ: Map<String, List<QuizQuestion>> = mapOf(
    "physio" to listOf(
        QuizQuestion(str(R.string.dimension_f7128f), listOf(str(R.string.dimension_914499), str(R.string.dimension_3969d8), str(R.string.dimension_e2d313), str(R.string.dimension_a51796)), "profile.physio.fitness"),
        QuizQuestion(str(R.string.dimension_fddd0d), listOf(str(R.string.dimension_aaf597), str(R.string.dimension_d919e4), str(R.string.dimension_8e1e76), str(R.string.dimension_0ace65)), "profile.physio.sleep"),
        QuizQuestion(str(R.string.dimension_c6ef63), listOf(str(R.string.dimension_f3ba54), str(R.string.dimension_cc0f27), str(R.string.dimension_f6d5da), str(R.string.dimension_4598ac)), "profile.physio.nutrition"),
    ),
    "personality" to listOf(
        QuizQuestion(str(R.string.dimension_e96b8c), listOf(str(R.string.dimension_9e7ee6), str(R.string.dimension_bafd16), str(R.string.dimension_e30aad), str(R.string.dimension_42d61c)), "profile.personality.extraversion"),
        QuizQuestion(str(R.string.dimension_696c35), listOf(str(R.string.dimension_a0061b), str(R.string.dimension_58c570), str(R.string.dimension_9b2809), str(R.string.dimension_df4184)), "profile.personality.openness"),
        QuizQuestion(str(R.string.dimension_done), listOf(str(R.string.dimension_c2b1fd), str(R.string.dimension_8ac978), str(R.string.dimension_ab778b), str(R.string.dimension_c70631)), "profile.personality.conscientiousness"),
    ),
    "cognitive" to listOf(
        QuizQuestion(str(R.string.dimension_b47f5b), listOf(str(R.string.dimension_e82939), str(R.string.dimension_49a62e), str(R.string.dimension_7174b1), str(R.string.dimension_6e2427)), "profile.cognitive.learning"),
        QuizQuestion(str(R.string.dimension_f9cede), listOf(str(R.string.dimension_bebd03), str(R.string.dimension_a236e3), str(R.string.dimension_10a70f), str(R.string.dimension_a62843)), "profile.cognitive.decision"),
        QuizQuestion(str(R.string.dimension_b4372a), listOf(str(R.string.dimension_3bd0c2), str(R.string.dimension_1116af), str(R.string.dimension_2d68a2), str(R.string.dimension_fa26ba)), "profile.cognitive.thinking"),
    ),
    "emotional" to listOf(
        QuizQuestion(str(R.string.dimension_1325ed), listOf(str(R.string.dimension_55cdc0), str(R.string.dimension_c59119), str(R.string.dimension_e2168f), str(R.string.dimension_79cc92)), "profile.emotional.stability"),
        QuizQuestion(str(R.string.dimension_e0d663), listOf(str(R.string.dimension_bd6f06), str(R.string.dimension_a6768d), str(R.string.dimension_204292), str(R.string.dimension_a32f6d)), "profile.emotional.empathy"),
        QuizQuestion(str(R.string.dimension_ed9901), listOf(str(R.string.dimension_dd9f5e), str(R.string.dimension_56a883), str(R.string.dimension_d6babd), str(R.string.dimension_89b4e6)), "profile.emotional.resilience"),
    ),
    "social" to listOf(
        QuizQuestion(str(R.string.dimension_94d8ec), listOf(str(R.string.dimension_d5e417), str(R.string.dimension_360e1e), str(R.string.dimension_46472a), str(R.string.dimension_c2c8e9)), "profile.social.style"),
        QuizQuestion(str(R.string.dimension_d92b67), listOf(str(R.string.dimension_015c17), str(R.string.dimension_9a853b), str(R.string.dimension_772ccb), str(R.string.dimension_727218)), "profile.social.communication"),
        QuizQuestion(str(R.string.dimension_0cb2d1), listOf(str(R.string.dimension_2ea9ef), str(R.string.dimension_1d2a3f), str(R.string.dimension_1d5ba5), str(R.string.dimension_a3f6a8)), "profile.social.relationships"),
    ),
    "values" to listOf(
        QuizQuestion(str(R.string.dimension_5fef09), listOf(str(R.string.dimension_85a2bb), str(R.string.dimension_149e38), str(R.string.dimension_1eef63), str(R.string.dimension_d5851c)), "profile.values.core"),
        QuizQuestion(str(R.string.dimension_d51d34), listOf(str(R.string.dimension_63e0cb), str(R.string.dimension_a12dfd), str(R.string.dimension_913232), str(R.string.dimension_231ba7)), "profile.values.principles"),
        QuizQuestion(str(R.string.dimension_ffa5e3), listOf(str(R.string.dimension_4336a8), str(R.string.dimension_5d28e0), str(R.string.dimension_c7319e), str(R.string.dimension_788407)), "profile.values.achievement"),
    ),
    "capability" to listOf(
        QuizQuestion(str(R.string.dimension_00ef6c), listOf(str(R.string.dimension_53d0d4), str(R.string.dimension_ba5f7e), str(R.string.dimension_a851cb), str(R.string.dimension_814654)), "profile.capability.skills"),
        QuizQuestion(str(R.string.dimension_f4c59c), listOf(str(R.string.dimension_0c6ec6), str(R.string.dimension_f3dd1d), str(R.string.dimension_76a648), str(R.string.dimension_f4d7c3)), "profile.capability.execution"),
        QuizQuestion(str(R.string.dimension_66f088), listOf(str(R.string.dimension_1ab25b), str(R.string.dimension_306f1d), str(R.string.dimension_3aa5e3), str(R.string.dimension_da2ad6)), "profile.capability.intrinsic"),
    ),
    "spiritual" to listOf(
        QuizQuestion(str(R.string.dimension_47b93d), listOf(str(R.string.dimension_1c22c9), str(R.string.dimension_c1642a), str(R.string.dimension_e858d6), str(R.string.dimension_fd1720)), "profile.spiritual.purpose"),
        QuizQuestion(str(R.string.dimension_55e742), listOf(str(R.string.dimension_0ee9a0), str(R.string.dimension_7d1d63), str(R.string.dimension_6e45c6), str(R.string.dimension_f88c9e)), "profile.spiritual.resilience"),
        QuizQuestion(str(R.string.dimension_99058a), listOf(str(R.string.dimension_660470), str(R.string.dimension_f0eafb), str(R.string.dimension_383dc0), str(R.string.dimension_72f605)), "profile.spiritual.beliefs"),
    ),
)

private val DIMENSION_FRAMEWORK: Map<String, Pair<String, String>> = mapOf(
    "physio"      to (str(R.string.dimension_d0afdf) to str(R.string.dimension_e930d2)),
    "personality" to (str(R.string.dimension_b59201) to str(R.string.dimension_971207)),
    "cognitive"   to (str(R.string.dimension_3bfb84) to str(R.string.dimension_89510d)),
    "emotional"   to (str(R.string.dimension_ae7f7a) to str(R.string.dimension_471f92)),
    "social"      to (str(R.string.dimension_26397d) to str(R.string.dimension_85ae46)),
    "values"      to (str(R.string.dimension_eaa0b0) to str(R.string.dimension_a480ff)),
    "capability"  to (str(R.string.dimension_3ae13a) to str(R.string.dimension_640488)),
    "spiritual"   to (str(R.string.dimension_ffc6f7) to str(R.string.dimension_f7fc48)),
)

// ── Main Page ─────────────────────────────────────────────────────────────────

@Composable
fun DimensionDetailPage(
    dimension: ProfileDimension,
    facts: Map<String, String>,
    onBack: () -> Unit,
    onSetFact: (key: String, value: String) -> Unit,
    generatedQuiz: List<AiQuizQuestion>? = null,
    isLoadingQuiz: Boolean = false,
    onRegenerateQuiz: () -> Unit = {},
) {
    val c = LocalClawColors.current
    val staticQuizQuestions = remember(dimension.id) { DIMENSION_QUIZ[dimension.id] ?: emptyList() }
    val framework = remember(dimension.id) { DIMENSION_FRAMEWORK[dimension.id] }
    var editAspect by remember { mutableStateOf<ProfileAspect?>(null) }

    // Auto-load AI quiz when dimension opens and none cached yet
    LaunchedEffect(dimension.id) {
        if (generatedQuiz == null && !isLoadingQuiz) {
            onRegenerateQuiz()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(c.bg),
    ) {
        // TopBar
        Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
                }
                Text(dimension.emoji, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    dimension.title,
                    color = dimension.color,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val known = dimension.aspects.count { facts[it.key] != null }
                Text(
                    str(R.string.aspects_known, known, dimension.aspects.size),
                    color = dimension.color.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── Aspects list ─────────────────────────────────────────────
            item {
                Text(
                    str(R.string.dimension_c7008e),
                    color = c.subtext,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(dimension.aspects) { aspect ->
                val value = facts[aspect.key]
                AspectRow(
                    aspect = aspect,
                    value = value,
                    color = dimension.color,
                    onEdit = { editAspect = aspect },
                )
            }

            // ── Framework ────────────────────────────────────────────────
            if (framework != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(dimension.color.copy(alpha = 0.05f))
                            .border(1.dp, dimension.color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text(str(R.string.dimension_f8111e), color = c.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(framework.first, color = dimension.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(framework.second, color = c.subtext, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            // ── Quiz section ─────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        str(R.string.dimension_d4454a),
                        color = c.subtext,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (isLoadingQuiz) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = dimension.color,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(str(R.string.dimension_926106), color = c.subtext.copy(alpha = 0.5f), fontSize = 10.sp)
                    } else {
                        IconButton(
                            onClick = onRegenerateQuiz,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = str(R.string.dimension_0e0b0d),
                                tint = dimension.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Text(str(R.string.dimension_0e0b0d), color = dimension.color.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (generatedQuiz != null) str(R.string.dimension_adfbef) else str(R.string.dimension_select),
                    color = c.subtext.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            // AI-generated quiz (preferred) or static fallback
            if (isLoadingQuiz) {
                item { QuizLoadingSkeleton(dimension.color) }
            } else if (generatedQuiz != null) {
                items(generatedQuiz) { q ->
                    AiQuizCard(
                        question = q,
                        currentValue = facts[q.factKey],
                        color = dimension.color,
                        onSelect = { answer -> onSetFact(q.factKey, answer) },
                    )
                }
            } else {
                items(staticQuizQuestions) { q ->
                    QuizCard(
                        question = q,
                        currentValue = facts[q.factKey],
                        color = dimension.color,
                        onSelect = { answer -> onSetFact(q.factKey, answer) },
                    )
                }
            }
        }
    }

    // Aspect manual edit dialog
    val editing = editAspect
    if (editing != null) {
        var editValue by remember(editing.key) { mutableStateOf(facts[editing.key] ?: "") }
        AlertDialog(
            onDismissRequest = { editAspect = null },
            title = { Text(str(R.string.edit_label_title, editing.label), fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text(editing.label) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editValue.isNotBlank()) onSetFact(editing.key, editValue)
                    editAspect = null
                }) { Text(str(R.string.role_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editAspect = null }) { Text(str(R.string.btn_cancel)) }
            },
        )
    }
}

// ── Aspect Row ────────────────────────────────────────────────────────────────

@Composable
private fun AspectRow(
    aspect: ProfileAspect,
    value: String?,
    color: Color,
    onEdit: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (value != null) color else c.border),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(aspect.label, color = c.subtext, fontSize = 12.sp)
            if (value != null) {
                Spacer(Modifier.height(2.dp))
                Text(value, color = c.text, fontSize = 13.sp, lineHeight = 18.sp)
            } else {
                Text(str(R.string.dimension_27b347), color = c.subtext.copy(alpha = 0.4f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            }
        }
        Icon(Icons.Default.Edit, contentDescription = str(R.string.dimension_edit), tint = c.subtext.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
    }
    HorizontalDivider(color = c.border.copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

// ── Loading skeleton ─────────────────────────────────────────────────────────

@Composable
private fun QuizLoadingSkeleton(color: Color) {
    val c = LocalClawColors.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        repeat(3) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(c.border.copy(alpha = 0.5f)),
                )
                Spacer(Modifier.height(10.dp))
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f - it * 0.05f)
                            .height(11.dp)
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.border.copy(alpha = 0.3f)),
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── AI Quiz Card (with hint support) ─────────────────────────────────────────

@Composable
private fun AiQuizCard(
    question: AiQuizQuestion,
    currentValue: String?,
    color: Color,
    onSelect: (String) -> Unit,
) {
    val c = LocalClawColors.current
    var expanded by remember { mutableStateOf(currentValue == null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .border(1.dp, if (currentValue != null) color.copy(alpha = 0.3f) else c.border, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentValue != null) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            }
            Text(
                question.question,
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                lineHeight = 18.sp,
            )
            Text(if (expanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.5f), fontSize = 9.sp)
        }

        // Hint (visible when expanded)
        if (expanded && question.hint.isNotBlank()) {
            Text(
                "💡 ${question.hint}",
                color = color.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            )
        }

        if (currentValue != null && !expanded) {
            Text(
                "✓  $currentValue",
                color = color.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
                question.answers.forEachIndexed { idx, answer ->
                    val isSelected = answer == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) color.copy(alpha = 0.4f) else c.border.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                onSelect(answer)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${('A' + idx)}",
                            color = if (isSelected) color else c.subtext,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            answer,
                            color = if (isSelected) color else c.text,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) Text("✓", color = color, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── Static Quiz Card ──────────────────────────────────────────────────────────

@Composable
private fun QuizCard(
    question: QuizQuestion,
    currentValue: String?,
    color: Color,
    onSelect: (String) -> Unit,
) {
    val c = LocalClawColors.current
    var expanded by remember { mutableStateOf(currentValue == null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .border(1.dp, if (currentValue != null) color.copy(alpha = 0.3f) else c.border, RoundedCornerShape(12.dp)),
    ) {
        // Question header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentValue != null) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            }
            Text(
                question.question,
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                lineHeight = 18.sp,
            )
            Text(
                if (expanded) "▲" else "▼",
                color = c.subtext.copy(alpha = 0.5f),
                fontSize = 9.sp,
            )
        }

        // Current answer (collapsed state)
        if (currentValue != null && !expanded) {
            Text(
                "✓  $currentValue",
                color = color.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }

        // Answers (expanded)
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
                question.answers.forEachIndexed { idx, answer ->
                    val isSelected = answer == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) color.copy(alpha = 0.4f) else c.border.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                onSelect(answer)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${('A' + idx)}",
                            color = if (isSelected) color else c.subtext,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            answer,
                            color = if (isSelected) color else c.text,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) Text("✓", color = color, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
