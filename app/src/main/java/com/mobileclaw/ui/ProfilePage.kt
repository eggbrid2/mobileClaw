package com.mobileclaw.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.mobileclaw.memory.db.EpisodeEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.res.stringResource
import com.mobileclaw.R
import com.mobileclaw.str

// ── Section ───────────────────────────────────────────────────────────────────

private enum class ProfileSection { PORTRAIT, MEMORY, HISTORY }

// ── Data model ────────────────────────────────────────────────────────────────

private val ProfileLineA = Color(0xFF101010)
private val ProfileLineB = Color(0xFF2A2A2A)
private val ProfileLineC = Color(0xFF505050)
private val ProfileAccent = Color(0xFF56D6BA)

data class ProfileAspect(
    val key: String,
    val label: String,
    val value: String?,
    val confidence: Float = 1f,
)

data class ProfileDimension(
    val id: String,
    val iconKey: String,
    val title: String,
    val color: Color,
    val aspects: List<ProfileAspect>,
)

private fun buildDimensions(facts: Map<String, String>): List<ProfileDimension> {
    fun f(key: String) = facts["profile.$key"]
    return listOf(
        // ── 生理 ── basic physiology
        ProfileDimension("physio", "physio", str(R.string.profile_99626b), ProfileLineA, listOf(
            ProfileAspect("profile.physio.health",     str(R.string.profile_fe9069), f("physio.health")),
            ProfileAspect("profile.physio.fitness",    str(R.string.profile_e83951), f("physio.fitness")),
            ProfileAspect("profile.physio.sleep",      str(R.string.profile_492b41), f("physio.sleep")),
            ProfileAspect("profile.physio.nutrition",  str(R.string.profile_7f6c57), f("physio.nutrition")),
            ProfileAspect("profile.physio.medical",    str(R.string.profile_9a7841), f("physio.medical")),
        )),
        // ── 性格 ── Big Five OCEAN (McCrae & Costa, 1992)
        ProfileDimension("personality", "roles", str(R.string.profile_732564), ProfileLineB, listOf(
            ProfileAspect("profile.personality.openness",          str(R.string.profile_a0dbad),   f("personality.openness")),
            ProfileAspect("profile.personality.conscientiousness",  str(R.string.profile_a4dec8),   f("personality.conscientiousness")),
            ProfileAspect("profile.personality.extraversion",       str(R.string.profile_63aa19),   f("personality.extraversion")),
            ProfileAspect("profile.personality.agreeableness",      str(R.string.profile_4b0fc5),   f("personality.agreeableness")),
            ProfileAspect("profile.personality.neuroticism",        str(R.string.profile_0e61ca), f("personality.neuroticism")),
            ProfileAspect("profile.personality.style",              str(R.string.profile_169e2e), f("personality.style")),
        )),
        // ── 认知 ── cognitive style + Holland RIASEC (1973)
        ProfileDimension("cognitive", "cognitive", str(R.string.profile_db916a), ProfileAccent, listOf(
            ProfileAspect("profile.cognitive.thinking",    str(R.string.profile_d1dbd1), f("cognitive.thinking")),
            ProfileAspect("profile.cognitive.learning",    str(R.string.profile_a8a822), f("cognitive.learning")),
            ProfileAspect("profile.cognitive.decision",    str(R.string.profile_3f6999), f("cognitive.decision")),
            ProfileAspect("profile.cognitive.perspective", str(R.string.profile_e675c8), f("cognitive.perspective")),
            ProfileAspect("profile.cognitive.creativity",  str(R.string.profile_39f56b), f("cognitive.creativity")),
            ProfileAspect("profile.cognitive.riasec",      str(R.string.profile_1a36a3), f("cognitive.riasec")),
        )),
        // ── 情绪 ── Ryff's Well-being (1989): self-acceptance, resilience
        ProfileDimension("emotional", "emotional", str(R.string.profile_6b4aaf), ProfileLineC, listOf(
            ProfileAspect("profile.emotional.stability",       str(R.string.profile_cfeb01), f("emotional.stability")),
            ProfileAspect("profile.emotional.empathy",         str(R.string.profile_f5cbda),   f("emotional.empathy")),
            ProfileAspect("profile.emotional.stress",          str(R.string.profile_b1cd26),   f("emotional.stress")),
            ProfileAspect("profile.emotional.resilience",      str(R.string.profile_7d37f3),   f("emotional.resilience")),
            ProfileAspect("profile.emotional.self_acceptance", str(R.string.profile_e7e4f0),   f("emotional.self_acceptance")),
        )),
        // ── 社交 ── social patterns + Ryff positive relations
        ProfileDimension("social", "social", str(R.string.profile_f22ca1), ProfileAccent, listOf(
            ProfileAspect("profile.social.style",         str(R.string.profile_6a4008), f("social.style")),
            ProfileAspect("profile.social.communication", str(R.string.profile_7f8c61), f("social.communication")),
            ProfileAspect("profile.social.relationships", str(R.string.profile_099e43), f("social.relationships")),
            ProfileAspect("profile.social.influence",     str(R.string.profile_0655bd), f("social.influence")),
            ProfileAspect("profile.social.boundaries",    str(R.string.profile_cdf24f),   f("social.boundaries")),
        )),
        // ── 价值 ── Schwartz Values (1992): universalism, benevolence, autonomy, achievement
        ProfileDimension("values", "values", str(R.string.profile_54d8f5), ProfileLineB, listOf(
            ProfileAspect("profile.values.core",        str(R.string.profile_b9b65b), f("values.core")),
            ProfileAspect("profile.values.goals",       str(R.string.profile_6dc3f0),   f("values.goals")),
            ProfileAspect("profile.values.principles",  str(R.string.profile_4738bf),   f("values.principles")),
            ProfileAspect("profile.values.achievement", str(R.string.profile_19cd57),   f("values.achievement")),
            ProfileAspect("profile.values.benevolence", str(R.string.profile_286564),   f("values.benevolence")),
            ProfileAspect("profile.values.autonomy",    str(R.string.profile_bbaa2b),   f("values.autonomy")),
        )),
        // ── 能力 ── capability + SDT intrinsic motivation (Deci & Ryan)
        ProfileDimension("capability", "capability", str(R.string.profile_120419), ProfileLineA, listOf(
            ProfileAspect("profile.capability.skills",     str(R.string.profile_52a965), f("capability.skills")),
            ProfileAspect("profile.capability.execution",  str(R.string.profile_3d8ec0),   f("capability.execution")),
            ProfileAspect("profile.capability.creativity", str(R.string.profile_18d1d0),   f("capability.creativity")),
            ProfileAspect("profile.capability.technical",  str(R.string.profile_7621a5), f("capability.technical")),
            ProfileAspect("profile.capability.intrinsic",  str(R.string.profile_9dc244), f("capability.intrinsic")),
        )),
        // ── 精神 ── Ryff's purpose in life + personal growth
        ProfileDimension("spiritual", "spiritual", str(R.string.profile_36fe54), ProfileLineC, listOf(
            ProfileAspect("profile.spiritual.core",       str(R.string.profile_4acb22), f("spiritual.core")),
            ProfileAspect("profile.spiritual.beliefs",    str(R.string.profile_bd0216), f("spiritual.beliefs")),
            ProfileAspect("profile.spiritual.resilience", str(R.string.profile_86b75a),   f("spiritual.resilience")),
            ProfileAspect("profile.spiritual.purpose",    str(R.string.profile_f520f5), f("spiritual.purpose")),
            ProfileAspect("profile.spiritual.growth",     str(R.string.profile_4bb4c1), f("spiritual.growth")),
        )),
    )
}

// ── Main Page ─────────────────────────────────────────────────────────────────

@Composable
fun ProfilePage(
    facts: Map<String, String>,
    episodes: List<EpisodeEntity>,
    isLoading: Boolean,
    isExtracting: Boolean,
    conversationCount: Int,
    onBack: () -> Unit,
    onRefreshExtraction: () -> Unit,
    onSetFact: (key: String, value: String) -> Unit = { _, _ -> },
    personalitySummary: String = "",
    personalitySummaryLoading: Boolean = false,
    onGenerateSummary: () -> Unit = {},
    dimensionQuizzes: Map<String, List<AiQuizQuestion>> = emptyMap(),
    dimensionQuizLoading: String? = null,
    onGenerateDimensionQuiz: (dimensionId: String, title: String) -> Unit = { _, _ -> },
    onPrewarmQuizzes: (List<ProfileDimension>) -> Unit = {},
    totalSkillCount: Int = 0,
) {
    val c = LocalClawColors.current
    val dimensions = remember(facts) { buildDimensions(facts) }
    var section by remember { mutableStateOf(ProfileSection.PORTRAIT) }

    // Auto-generate personality summary when page opens with facts
    LaunchedEffect(facts.isNotEmpty()) {
        if (facts.isNotEmpty() && personalitySummary.isEmpty() && !personalitySummaryLoading) {
            onGenerateSummary()
        }
    }

    // Pre-warm all dimension quizzes in background so they're ready when user opens a dimension
    LaunchedEffect(facts.isNotEmpty()) {
        if (facts.isNotEmpty()) {
            onPrewarmQuizzes(dimensions)
        }
    }

    var openDimension by remember { mutableStateOf<ProfileDimension?>(null) }
    openDimension?.let { dim ->
        androidx.activity.compose.BackHandler { openDimension = null }
        DimensionDetailPage(
            dimension = dim,
            facts = facts,
            onBack = { openDimension = null },
            onSetFact = onSetFact,
            generatedQuiz = dimensionQuizzes[dim.id],
            isLoadingQuiz = dimensionQuizLoading == dim.id,
            onRegenerateQuiz = { onGenerateDimensionQuiz(dim.id, dim.title) },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // ── Title bar ────────────────────────────────────────────────────────
        ClawPageHeader(title = str(R.string.profile_b6c018), onBack = onBack) {
            if (isLoading || isExtracting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = c.accent, strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isExtracting, onClick = onRefreshExtraction),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = if (isExtracting) c.subtext.copy(alpha = 0.3f) else c.subtext,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // ── Tab row ──────────────────────────────────────────────────────────
        SectionTabRow(section) { section = it }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            when (section) {
                ProfileSection.PORTRAIT -> {
                    item(key = "portrait_top_spacer") { Spacer(Modifier.height(4.dp)) }
                    item(key = "portrait_summary") {
                        PersonalitySummaryCard(
                            summary = personalitySummary,
                            isLoading = personalitySummaryLoading,
                            onRefresh = onGenerateSummary,
                            conversationCount = conversationCount,
                        )
                    }
                    if (totalSkillCount > 0 || episodes.isNotEmpty()) {
                        item(key = "portrait_skill_spacer") { Spacer(Modifier.height(4.dp)) }
                        item(key = "portrait_skill_card") { SkillExplorationCard(episodes = episodes, totalSkillCount = totalSkillCount) }
                    }
                    item(key = "portrait_dim_spacer") { Spacer(Modifier.height(4.dp)) }
                    item(key = "portrait_dimensions") {
                        DimensionsListSection(
                            dimensions = dimensions,
                            facts = facts,
                            onOpenDimension = { openDimension = it },
                        )
                    }
                }
                ProfileSection.MEMORY -> {
                    item(key = "memory_spacer") { Spacer(Modifier.height(4.dp)) }
                    item(key = "memory_browser") { MemoryBrowserCard(facts) }
                }
                ProfileSection.HISTORY -> {
                    if (episodes.isEmpty()) {
                        item(key = "history_empty") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ClawIconTile("document", size = 54.dp, iconSize = 28.dp, tint = c.text, background = c.cardAlt, border = c.border)
                                    Spacer(Modifier.height(8.dp))
                                    Text(str(R.string.profile_873b17), color = c.subtext, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        item(key = "history_spacer") { Spacer(Modifier.height(4.dp)) }
                        items(episodes.size, key = { i -> "ep_${episodes[i].id}" }) { i -> EpisodeCard(episodes[i]) }
                    }
                }
            }
        }
    }
}

// ── Skill Exploration Card ────────────────────────────────────────────────────

@Composable
private fun SkillExplorationCard(episodes: List<EpisodeEntity>, totalSkillCount: Int) {
    val c = LocalClawColors.current
    val gson = remember { Gson() }

    val usedSkillIds = remember(episodes) {
        episodes.flatMap { ep ->
            runCatching { gson.fromJson(ep.skillsUsed, Array<String>::class.java).toList() }
                .getOrDefault(emptyList())
        }.toSet()
    }

    val explored = usedSkillIds.size
    val total = totalSkillCount.coerceAtLeast(explored)
    val progress = if (total > 0) explored.toFloat() / total else 0f

    val milestoneLabel = when {
        progress >= 1f   -> stringResource(R.string.profile_all)
        progress >= 0.75f -> stringResource(R.string.profile_e67499)
        progress >= 0.5f  -> stringResource(R.string.profile_272167)
        progress >= 0.25f -> stringResource(R.string.profile_73f663)
        explored > 0      -> stringResource(R.string.profile_6270ba)
        else              -> stringResource(R.string.profile_d948dc)
    }
    val milestoneColor = when {
        progress >= 0.75f -> c.accent
        progress >= 0.25f -> c.blue
        else              -> c.subtext
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_66054a), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(milestoneLabel, color = milestoneColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        // Progress bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(str(R.string.skills_used, explored), fontSize = 12.sp, color = c.subtext)
                Text(str(R.string.skills_total, total), fontSize = 12.sp, color = c.subtext)
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.border)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.8f), c.accent))
                        )
                )
            }
        }

        // Recently used skills chips
        if (usedSkillIds.isNotEmpty()) {
            val recentSkills = usedSkillIds.take(6)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                recentSkills.forEach { skillId ->
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(c.accent.copy(0.12f))
                            .border(0.5.dp, c.accent.copy(0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(skillId.replace("_", " "), fontSize = 11.sp, color = c.accent)
                    }
                }
                if (usedSkillIds.size > 6) {
                    Box(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(c.cardAlt)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("+${usedSkillIds.size - 6}", fontSize = 11.sp, color = c.subtext)
                    }
                }
            }
        }
    }
}

// ── Personality Summary Card ──────────────────────────────────────────────────

@Composable
private fun PersonalitySummaryCard(
    summary: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    conversationCount: Int = 0,
) {
    val c = LocalClawColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(c.accent.copy(alpha = 0.13f), c.purple.copy(alpha = 0.07f))
                )
            )
            .border(1.dp, c.accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClawSymbolIcon("settings", tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                str(R.string.profile_539d49),
                color = c.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = c.accent, strokeWidth = 1.5.dp)
            } else if (summary.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = c.subtext.copy(alpha = 0.5f), modifier = Modifier.size(15.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Text(
                    str(R.string.profile_c8a695),
                    color = c.subtext,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 21.sp,
                )
            }
            summary.isNotBlank() -> {
                Text(
                    summary,
                    color = c.text,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
            }
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        str(R.string.profile_b3319d),
                        color = c.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        str(R.string.profile_ae937e),
                        color = c.subtext,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                    if (conversationCount > 0) {
                        Text(
                            str(R.string.profile_chat_more, conversationCount),
                            color = c.accent.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accent)
                            .clickable(onClick = onRefresh)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(str(R.string.profile_303071), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ── Radar Card ────────────────────────────────────────────────────────────────

@Composable
private fun RadarCard(
    dimensions: List<ProfileDimension>,
    selectedIdx: Int?,
    onSelectDim: (Int) -> Unit,
    conversationCount: Int,
    episodeCount: Int,
    isExtracting: Boolean,
) {
    val c = LocalClawColors.current
    val knownCount = dimensions.sumOf { d -> d.aspects.count { it.value != null } }
    val totalCount = dimensions.sumOf { it.aspects.size }
    val values = dimensions.map { dim ->
        dim.aspects.count { it.value != null }.toFloat() / dim.aspects.size.coerceAtLeast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        // Stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RadarStat(str(R.string.profile_5935c2), "${knownCount * 100 / totalCount.coerceAtLeast(1)}%", c.accent)
            RadarStat(str(R.string.profile_done), "$knownCount/$totalCount", c.blue)
            RadarStat(str(R.string.home_859362), "$conversationCount", c.purple)
            RadarStat(str(R.string.profile_0e46d8), "$episodeCount", c.green)
        }

        // Radar chart
        RadarChart(values = values, dimensions = dimensions, selectedIdx = selectedIdx, onSelect = onSelectDim)

        // Hint
        Text(
            text = if (isExtracting) str(R.string.profile_loading)
                   else if (selectedIdx != null) str(R.string.dimension_selected, dimensions[selectedIdx].title)
                   else str(R.string.profile_tap),
            color = if (isExtracting) c.accent.copy(alpha = 0.7f) else c.subtext.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontStyle = if (isExtracting) FontStyle.Italic else FontStyle.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RadarStat(label: String, value: String, color: Color) {
    val c = LocalClawColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c.subtext, fontSize = 9.sp)
    }
}

// ── Radar Chart (Canvas + absolute labels) ────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun RadarChart(
    values: List<Float>,
    dimensions: List<ProfileDimension>,
    selectedIdx: Int?,
    onSelect: (Int) -> Unit,
) {
    val c = LocalClawColors.current
    val n = dimensions.size
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp),
    ) {
        val pxW = constraints.maxWidth.toFloat()
        val cx = pxW / 2f
        val cy = pxW / 2f
        val gridR    = pxW * 0.32f
        val labelR   = pxW * 0.46f
        val halfLW   = pxW * 0.115f   // half-width of label box
        val halfLH   = pxW * 0.065f   // half-height of label box

        // Chart
        Canvas(Modifier.fillMaxSize()) {
            // Grid rings
            for (level in 1..4) {
                val r = gridR * level / 4
                val path = Path()
                for (i in 0 until n) {
                    val a = (-PI / 2 + i * 2 * PI / n).toFloat()
                    val p = androidx.compose.ui.geometry.Offset(cx + cos(a) * r, cy + sin(a) * r)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, c.border.copy(alpha = 0.45f), style = Stroke(0.5.dp.toPx()))
            }
            // Axis lines
            for (i in 0 until n) {
                val a = (-PI / 2 + i * 2 * PI / n).toFloat()
                drawLine(
                    c.border.copy(alpha = 0.35f),
                    androidx.compose.ui.geometry.Offset(cx, cy),
                    androidx.compose.ui.geometry.Offset(cx + cos(a) * gridR, cy + sin(a) * gridR),
                    0.5.dp.toPx(),
                )
            }
            // Data polygon fill
            val dataPath = Path()
            for (i in 0 until n) {
                val a = (-PI / 2 + i * 2 * PI / n).toFloat()
                val r = gridR * values[i].coerceIn(0.04f, 1f)
                val p = androidx.compose.ui.geometry.Offset(cx + cos(a) * r, cy + sin(a) * r)
                if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
            }
            dataPath.close()
            drawPath(dataPath, c.accent.copy(alpha = 0.10f))
            drawPath(dataPath, c.accent.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
            // Dots
            for (i in 0 until n) {
                val a = (-PI / 2 + i * 2 * PI / n).toFloat()
                val r = gridR * values[i].coerceIn(0.04f, 1f)
                val p = androidx.compose.ui.geometry.Offset(cx + cos(a) * r, cy + sin(a) * r)
                val isSelected = selectedIdx == i
                drawCircle(
                    if (isSelected) dimensions[i].color else c.accent.copy(alpha = 0.65f),
                    (if (isSelected) 6.5f else 4f).dp.toPx(), p,
                )
                if (isSelected) drawCircle(Color.White, 2.5.dp.toPx(), p)
            }
            // Center
            drawCircle(c.accent.copy(alpha = 0.2f), 3.dp.toPx(), androidx.compose.ui.geometry.Offset(cx, cy))
        }

        // Clickable labels
        for (i in 0 until n) {
            val a = (-PI / 2 + i * 2 * PI / n).toFloat()
            val lx = cx + cos(a) * labelR
            val ly = cy + sin(a) * labelR
            val isSelected = selectedIdx == i
            val dim = dimensions[i]

            with(density) {
                Column(
                    modifier = Modifier
                        .absoluteOffset((lx - halfLW).toDp(), (ly - halfLH).toDp())
                        .size((halfLW * 2).toDp(), (halfLH * 2).toDp())
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) dim.color.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ClawSymbolIcon(dim.iconKey, tint = if (isSelected) dim.color else c.subtext, modifier = Modifier.size(14.dp))
                    Text(
                        dim.title.take(4),
                        fontSize = 7.sp,
                        lineHeight = 9.sp,
                        color = if (isSelected) dim.color else c.subtext,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ── Dimension Detail Card ─────────────────────────────────────────────────────

@Composable
private fun DimensionDetailCard(dimension: ProfileDimension, episodes: List<EpisodeEntity>) {
    val c = LocalClawColors.current
    val knownAspects = dimension.aspects.filter { it.value != null }
    val unknownAspects = dimension.aspects.filter { it.value == null }
    val relatedTasks = remember(dimension.id, episodes) { findRelatedTasks(dimension.id, episodes) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(dimension.color.copy(alpha = 0.06f))
            .border(1.dp, dimension.color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClawSymbolIcon(dimension.iconKey, tint = dimension.color, modifier = Modifier.size(20.dp))
            Text(dimension.title, color = dimension.color, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(str(R.string.aspects_known, knownAspects.size, dimension.aspects.size), color = dimension.color.copy(alpha = 0.7f), fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))

        // Known aspects
        if (knownAspects.isNotEmpty()) {
            knownAspects.forEach { aspect ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(dimension.color))
                    Text(aspect.label, color = c.subtext, fontSize = 12.sp, modifier = Modifier.width(72.dp))
                    Text(aspect.value!!, color = c.text, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                }
            }
        }

        // Unknown aspects
        if (unknownAspects.isNotEmpty()) {
            if (knownAspects.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = dimension.color.copy(alpha = 0.12f), thickness = 0.5.dp)
                Spacer(Modifier.height(6.dp))
            }
            unknownAspects.forEach { aspect ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(c.border))
                    Text(aspect.label, color = c.subtext.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.width(72.dp))
                    Text(stringResource(R.string.dimension_27b347), color = c.subtext.copy(alpha = 0.3f), fontSize = 11.sp, fontStyle = FontStyle.Italic)
                }
            }
        }

        // Related tasks
        if (relatedTasks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = dimension.color.copy(alpha = 0.12f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.profile_495e4c), color = c.subtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            relatedTasks.take(3).forEach { ep ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (ep.success) c.green else c.red))
                    Text(ep.goalText.take(48) + if (ep.goalText.length > 48) "…" else "", color = c.subtext.copy(alpha = 0.6f), fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}

private fun findRelatedTasks(dimensionId: String, episodes: List<EpisodeEntity>): List<EpisodeEntity> {
    val keywords = when (dimensionId) {
        "physio"      -> listOf(str(R.string.profile_37b6de), str(R.string.profile_c24d6f), str(R.string.profile_fbc026), str(R.string.profile_071bd0), str(R.string.profile_44ebfb), str(R.string.profile_ec4ddf), str(R.string.profile_6d5211))
        "personality" -> listOf(str(R.string.profile_ba89e6), str(R.string.profile_6d7a10), str(R.string.profile_b5502a), str(R.string.profile_3d6c39), str(R.string.profile_b29537), str(R.string.profile_689150))
        "cognitive"   -> listOf(str(R.string.profile_4ef520), str(R.string.profile_2d4653), str(R.string.profile_72fa7c), str(R.string.profile_aacef1), str(R.string.profile_fc6c4c), str(R.string.profile_search), str(R.string.profile_351912))
        "emotional"   -> listOf(str(R.string.profile_29c0a6), str(R.string.profile_0218cb), str(R.string.profile_54177d), str(R.string.profile_3945f3), str(R.string.profile_f16b17), str(R.string.profile_df6d77), str(R.string.profile_bb725f))
        "social"      -> listOf(str(R.string.profile_d38a08), str(R.string.profile_c0abbf), str(R.string.profile_9d5323), str(R.string.profile_cfbf6f), str(R.string.profile_8b8588), str(R.string.profile_c31f48), str(R.string.profile_send))
        "values"      -> listOf(str(R.string.profile_73e825), str(R.string.profile_0debf5), str(R.string.profile_021ac9), str(R.string.profile_9a018b), str(R.string.profile_aefcbf), str(R.string.profile_not), str(R.string.profile_3ad828))
        "capability"  -> listOf(str(R.string.profile_06e004), str(R.string.profile_4d7dc6), str(R.string.group_create), str(R.string.profile_1a6aa2), str(R.string.app_launcher_done), str(R.string.profile_38164c), str(R.string.profile_710510))
        "spiritual"   -> listOf(str(R.string.profile_21d68b), str(R.string.profile_70bca7), str(R.string.profile_3ad828), str(R.string.profile_4baafe), str(R.string.profile_bb725f), str(R.string.profile_29d7cb))
        else          -> emptyList()
    }
    return episodes.filter { ep -> keywords.any { kw -> ep.goalText.contains(kw) } }
}

// ── Section Tabs ──────────────────────────────────────────────────────────────

@Composable
private fun SectionTabRow(active: ProfileSection, onSelect: (ProfileSection) -> Unit) {
    val c = LocalClawColors.current
    val tabs = listOf(ProfileSection.PORTRAIT to stringResource(R.string.profile_aa49bb), ProfileSection.MEMORY to stringResource(R.string.profile_44e4d7), ProfileSection.HISTORY to stringResource(R.string.profile_c827d8))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        tabs.forEach { (sec, label) ->
            val isActive = sec == active
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(sec) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (isActive) c.accent else c.subtext,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .fillMaxWidth(if (isActive) 0.5f else 0f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(c.accent),
                )
            }
        }
    }
    HorizontalDivider(color = c.border, thickness = 0.5.dp)
}

// ── Task Insights (PORTRAIT section) ─────────────────────────────────────────

@Composable
private fun TaskInsightsCard(episodes: List<EpisodeEntity>) {
    val c = LocalClawColors.current

    if (episodes.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClawIconTile("check", size = 50.dp, iconSize = 26.dp, tint = c.text, background = c.cardAlt, border = c.border)
                Text(stringResource(R.string.profile_done_2), color = c.subtext, fontSize = 13.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
            }
        }
        return
    }

    val gson = remember { Gson() }
    val allSkills = remember(episodes) {
        episodes.flatMap { ep ->
            runCatching { gson.fromJson(ep.skillsUsed, Array<String>::class.java).toList() }
                .getOrDefault(emptyList())
        }
    }
    val skillFreq = remember(allSkills) {
        allSkills.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(7)
    }
    val successCount = episodes.count { it.success }
    val successRate = successCount.toFloat() / episodes.size
    val variety = episodes.map { it.goalText.take(6) }.distinct().size.toFloat() / episodes.size.coerceAtLeast(1)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Skill distribution
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.profile_8d884c), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            Spacer(Modifier.height(2.dp))
            val maxCount = skillFreq.firstOrNull()?.value?.toFloat() ?: 1f
            skillFreq.forEach { (skill, count) ->
                SkillBarRow(skill, count, count / maxCount, c)
            }
        }

        // Behavioral insights
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.profile_777c5c), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            Spacer(Modifier.height(2.dp))
            InsightRow(stringResource(R.string.profile_3d8ec0), "${(successRate * 100).toInt()}% 任务成功率", successRate, c.green)
            InsightRow(stringResource(R.string.profile_9460ee), "任务多样性 ${(variety * 100).toInt()}%", variety.coerceIn(0f, 1f), c.purple)
            InsightRow(stringResource(R.string.profile_b22274), "${episodes.size} 个历史任务", (episodes.size / 50f).coerceIn(0.05f, 1f), c.accent)
            InsightRow(stringResource(R.string.profile_e95b62), if (allSkills.any { it.startsWith("web_") || it == "shell" }) stringResource(R.string.profile_f2e2d4) else stringResource(R.string.profile_4711a2),
                if (allSkills.any { it.startsWith("web_") || it == "shell" }) 0.8f else 0.2f, c.blue)
        }
    }
}

@Composable
private fun SkillBarRow(skill: String, count: Int, fraction: Float, c: ClawColors) {
    val trait = when {
        skill.startsWith("web_")  -> stringResource(R.string.profile_b10f39)
        skill == "screenshot" || skill == "see_screen" || skill == "read_screen" -> stringResource(R.string.profile_a50474)
        skill == "tap" || skill == "scroll" || skill == "input_text" -> stringResource(R.string.profile_bf1087)
        skill == "navigate"       -> stringResource(R.string.profile_871b17)
        skill == "memory"         -> stringResource(R.string.profile_d0917a)
        skill == "shell"          -> stringResource(R.string.profile_82e42f)
        skill.startsWith("bg_")   -> stringResource(R.string.profile_0a48c6)
        else                      -> stringResource(R.string.profile_aa05fd)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(skill.replace("_", " "), color = c.subtext, fontSize = 10.sp, modifier = Modifier.width(88.dp))
        Box(
            modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.border),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0.5f), c.accent))),
            )
        }
        Text("$count", color = c.subtext.copy(alpha = 0.7f), fontSize = 10.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
        Text(trait, color = c.accent.copy(alpha = 0.6f), fontSize = 9.sp, modifier = Modifier.width(54.dp))
    }
}

@Composable
private fun InsightRow(label: String, detail: String, value: Float, color: Color) {
    val c = LocalClawColors.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = c.subtext, fontSize = 11.sp, modifier = Modifier.width(52.dp))
            Text(detail, color = c.text, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("${(value * 100).toInt()}%", color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.border)) {
            Box(
                Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.7f)),
            )
        }
    }
}

// ── Static knowledge maps ─────────────────────────────────────────────────────

private val DIMENSION_FRAMEWORKS = mapOf(
    "physio"      to "Biopsychosocial Model",
    "personality" to "Big Five OCEAN · McCrae & Costa, 1992",
    "cognitive"   to "Kolb Learning Styles + Holland RIASEC, 1973",
    "emotional"   to "Ryff's Psychological Well-being, 1989",
    "social"      to "Social Learning Theory · Bronfenbrenner",
    "values"      to "Schwartz Basic Human Values, 1992",
    "capability"  to "SDT (Deci & Ryan, 2000) + Gardner's MI",
    "spiritual"   to "Frankl's Logotherapy + Ryff's Purpose",
)

private val ASPECT_DESCRIPTIONS = mapOf(
    "profile.physio.health"                  to str(R.string.profile_885836),
    "profile.physio.fitness"                 to str(R.string.profile_028340),
    "profile.physio.sleep"                   to str(R.string.profile_b25022),
    "profile.physio.nutrition"               to str(R.string.profile_965280),
    "profile.physio.medical"                 to str(R.string.profile_ad5bac),
    "profile.personality.openness"           to str(R.string.profile_6d0fdf),
    "profile.personality.conscientiousness"  to str(R.string.profile_05d85f),
    "profile.personality.extraversion"       to str(R.string.profile_b2759e),
    "profile.personality.agreeableness"      to str(R.string.profile_9eda87),
    "profile.personality.neuroticism"        to str(R.string.profile_1d4c00),
    "profile.personality.style"              to str(R.string.profile_9f0d50),
    "profile.cognitive.thinking"             to str(R.string.profile_389491),
    "profile.cognitive.learning"             to str(R.string.profile_a80e9a),
    "profile.cognitive.decision"             to str(R.string.profile_7bde54),
    "profile.cognitive.perspective"          to str(R.string.profile_13db69),
    "profile.cognitive.creativity"           to str(R.string.profile_e87240),
    "profile.cognitive.riasec"               to str(R.string.profile_c655b5),
    "profile.emotional.stability"            to str(R.string.profile_57dcf0),
    "profile.emotional.empathy"              to str(R.string.profile_19d77d),
    "profile.emotional.stress"               to str(R.string.profile_38deb0),
    "profile.emotional.resilience"           to str(R.string.profile_1f98e8),
    "profile.emotional.self_acceptance"      to str(R.string.profile_755395),
    "profile.social.style"                   to str(R.string.profile_550473),
    "profile.social.communication"           to str(R.string.profile_2f7e1d),
    "profile.social.relationships"           to str(R.string.profile_e156a7),
    "profile.social.influence"               to str(R.string.profile_5272ac),
    "profile.social.boundaries"              to str(R.string.profile_58bf13),
    "profile.values.core"                    to str(R.string.profile_385206),
    "profile.values.goals"                   to str(R.string.profile_fb84d7),
    "profile.values.principles"              to str(R.string.profile_f7014a),
    "profile.values.achievement"             to str(R.string.profile_b1f37c),
    "profile.values.benevolence"             to str(R.string.profile_e26524),
    "profile.values.autonomy"                to str(R.string.profile_51e22c),
    "profile.capability.skills"              to str(R.string.profile_9d2261),
    "profile.capability.execution"           to str(R.string.profile_bd1c76),
    "profile.capability.creativity"          to str(R.string.profile_d374ac),
    "profile.capability.technical"           to str(R.string.profile_5185cf),
    "profile.capability.intrinsic"           to str(R.string.profile_fc92fc),
    "profile.spiritual.core"                 to str(R.string.profile_867c43),
    "profile.spiritual.beliefs"              to str(R.string.profile_72fe6b),
    "profile.spiritual.resilience"           to str(R.string.profile_88b886),
    "profile.spiritual.purpose"              to str(R.string.profile_a3b189),
    "profile.spiritual.growth"               to str(R.string.profile_30ccd2),
)

private val ASPECT_HINTS = mapOf(
    "profile.physio.health"                  to str(R.string.profile_d30fc4),
    "profile.physio.fitness"                 to str(R.string.profile_desc),
    "profile.physio.sleep"                   to str(R.string.profile_374896),
    "profile.physio.nutrition"               to str(R.string.profile_8ea1fc),
    "profile.physio.medical"                 to str(R.string.profile_30e068),
    "profile.personality.openness"           to str(R.string.profile_c65d6b),
    "profile.personality.conscientiousness"  to str(R.string.profile_3565ae),
    "profile.personality.extraversion"       to str(R.string.profile_bf5d9f),
    "profile.personality.agreeableness"      to str(R.string.profile_84061a),
    "profile.personality.neuroticism"        to str(R.string.profile_118b76),
    "profile.personality.style"              to str(R.string.profile_desc_2),
    "profile.cognitive.thinking"             to str(R.string.profile_98b33b),
    "profile.cognitive.learning"             to str(R.string.profile_409c21),
    "profile.cognitive.decision"             to str(R.string.profile_4455bb),
    "profile.cognitive.perspective"          to str(R.string.profile_14da70),
    "profile.cognitive.creativity"           to str(R.string.profile_f34d6d),
    "profile.cognitive.riasec"               to str(R.string.profile_de2f9d),
    "profile.emotional.stability"            to str(R.string.profile_7c652f),
    "profile.emotional.empathy"              to str(R.string.profile_d171d4),
    "profile.emotional.stress"               to str(R.string.profile_bb25b5),
    "profile.emotional.resilience"           to str(R.string.profile_e1c2be),
    "profile.emotional.self_acceptance"      to str(R.string.profile_4b1691),
    "profile.social.style"                   to str(R.string.profile_6ab36a),
    "profile.social.communication"           to str(R.string.profile_d205dc),
    "profile.social.relationships"           to str(R.string.profile_a1c1d8),
    "profile.social.influence"               to str(R.string.profile_e0766a),
    "profile.social.boundaries"              to str(R.string.profile_ddbb32),
    "profile.values.core"                    to str(R.string.profile_17072e),
    "profile.values.goals"                   to str(R.string.profile_40d83b),
    "profile.values.principles"              to str(R.string.profile_ea4fce),
    "profile.values.achievement"             to str(R.string.profile_success),
    "profile.values.benevolence"             to str(R.string.profile_190bc4),
    "profile.values.autonomy"                to str(R.string.profile_2c17dc),
    "profile.capability.skills"              to str(R.string.profile_8f2bca),
    "profile.capability.execution"           to str(R.string.profile_ac6c92),
    "profile.capability.creativity"          to str(R.string.profile_b87e8a),
    "profile.capability.technical"           to str(R.string.profile_160be8),
    "profile.capability.intrinsic"           to str(R.string.profile_bd8aaf),
    "profile.spiritual.core"                 to str(R.string.profile_cb65da),
    "profile.spiritual.beliefs"              to str(R.string.profile_2de177),
    "profile.spiritual.resilience"           to str(R.string.profile_261ace),
    "profile.spiritual.purpose"              to str(R.string.profile_e4b61a),
    "profile.spiritual.growth"               to str(R.string.profile_33ecfa),
)

// ── Dimensions List (PORTRAIT section) ────────────────────────────────────────

@Composable
private fun DimensionsListSection(
    dimensions: List<ProfileDimension>,
    facts: Map<String, String>,
    onOpenDimension: (ProfileDimension) -> Unit = {},
) {
    val c = LocalClawColors.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            str(R.string.profile_c93e41),
            color = c.subtext,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        dimensions.forEach { dim ->
            val knownAspects = dim.aspects.filter { it.value != null }
            val previewValue = knownAspects.firstOrNull()?.value

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .clickable { onOpenDimension(dim) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Emoji circle in dimension color
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(dim.color.copy(alpha = 0.12f))
                        .border(1.dp, dim.color.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ClawSymbolIcon(dim.iconKey, tint = dim.color, modifier = Modifier.size(20.dp))
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Dimension name without "维度" suffix
                    val shortTitle = dim.title.removeSuffix(str(R.string.profile_f29c54))
                    Text(shortTitle, color = dim.color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (previewValue != null) {
                        // Show the actual first known value
                        Text(
                            previewValue,
                            color = c.text,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            str(R.string.profile_86c7b6),
                            color = c.subtext.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                // Arrow
                Text(
                    "›",
                    color = if (knownAspects.isNotEmpty()) dim.color.copy(alpha = 0.7f) else c.subtext.copy(alpha = 0.3f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
}

// ── Memory Browser (MEMORY section) ──────────────────────────────────────────

@Composable
private fun MemoryBrowserCard(facts: Map<String, String>) {
    val c = LocalClawColors.current

    if (facts.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClawIconTile("profile", size = 56.dp, iconSize = 30.dp, tint = c.text, background = c.cardAlt, border = c.border)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.profile_05dcde), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.profile_221c5b),
                    color = c.subtext,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val groupLabels = mapOf("profile" to stringResource(R.string.profile_d0a47e), "user" to stringResource(R.string.profile_4fe012), "app" to stringResource(R.string.profile_f3c144), "device" to stringResource(R.string.profile_b967fd))
    val grouped = facts.entries.sortedBy { it.key }.groupBy { it.key.substringBefore(".", it.key) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.profile_cf1e47),
            color = c.subtext,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        grouped.forEach { (prefix, entries) ->
            MemoryGroup(label = groupLabels[prefix] ?: prefix, entries = entries)
        }
    }
}

@Composable
private fun MemoryGroup(label: String, entries: List<Map.Entry<String, String>>) {
    val c = LocalClawColors.current
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(str(R.string.entries_count, entries.size), color = c.subtext, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
            Text(if (expanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.45f), fontSize = 9.sp)
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                Spacer(Modifier.height(4.dp))
                entries.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .background(c.cardAlt).padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(key.substringAfterLast(".").replace("_", " "), color = c.subtext, fontSize = 11.sp, modifier = Modifier.width(90.dp))
                        Text(value, color = c.text, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Episode Card (HISTORY section) ────────────────────────────────────────────

@Composable
private fun EpisodeCard(episode: EpisodeEntity) {
    val c = LocalClawColors.current
    val gson = remember { Gson() }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var expanded by remember { mutableStateOf(false) }

    val skills = remember(episode.skillsUsed) {
        runCatching { gson.fromJson(episode.skillsUsed, Array<String>::class.java).toList() }
            .getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(c.card)
            .border(1.dp, (if (episode.success) c.green else c.red).copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.padding(top = 4.dp).size(7.dp).clip(CircleShape).background(if (episode.success) c.green else c.red))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(episode.goalText, color = c.text, fontSize = 13.sp, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Date(episode.createdAt)), color = c.subtext.copy(alpha = 0.55f), fontSize = 10.sp)
                    if (episode.durationMs > 0) {
                        Text("${episode.durationMs / 1000}s", color = c.subtext.copy(alpha = 0.4f), fontSize = 10.sp)
                    }
                    Text(if (episode.success) stringResource(R.string.profile_100b2b) else stringResource(R.string.profile_3dc27d), color = if (episode.success) c.green else c.red, fontSize = 10.sp)
                }
            }
            Text(if (expanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.35f), fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
        }

        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(color = c.border.copy(alpha = 0.4f), thickness = 0.5.dp)
                if (episode.reflexionSummary.isNotBlank()) {
                    Text(
                        "💭 ${episode.reflexionSummary}",
                        color = c.subtext,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontStyle = FontStyle.Italic,
                    )
                }
                if (skills.isNotEmpty()) {
                    Text(
                        skills.take(8).joinToString("  ·  ") { it.replace("_", " ") },
                        color = c.accent.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}
