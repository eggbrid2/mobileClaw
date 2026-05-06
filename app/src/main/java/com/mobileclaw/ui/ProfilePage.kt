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

// ── Section ───────────────────────────────────────────────────────────────────

private enum class ProfileSection { PORTRAIT, MEMORY, HISTORY }

// ── Data model ────────────────────────────────────────────────────────────────

data class ProfileAspect(
    val key: String,
    val label: String,
    val value: String?,
    val confidence: Float = 1f,
)

data class ProfileDimension(
    val id: String,
    val emoji: String,
    val title: String,
    val color: Color,
    val aspects: List<ProfileAspect>,
)

private fun buildDimensions(facts: Map<String, String>): List<ProfileDimension> {
    fun f(key: String) = facts["profile.$key"]
    return listOf(
        // ── 生理 ── basic physiology
        ProfileDimension("physio", "🧬", "生理维度", Color(0xFF4CAF50), listOf(
            ProfileAspect("profile.physio.health",     "健康状况", f("physio.health")),
            ProfileAspect("profile.physio.fitness",    "体能运动", f("physio.fitness")),
            ProfileAspect("profile.physio.sleep",      "睡眠质量", f("physio.sleep")),
            ProfileAspect("profile.physio.nutrition",  "饮食习惯", f("physio.nutrition")),
            ProfileAspect("profile.physio.medical",    "医疗/过敏", f("physio.medical")),
        )),
        // ── 性格 ── Big Five OCEAN (McCrae & Costa, 1992)
        ProfileDimension("personality", "🎭", "性格维度", Color(0xFFFF9800), listOf(
            ProfileAspect("profile.personality.openness",          "开放性",   f("personality.openness")),
            ProfileAspect("profile.personality.conscientiousness",  "尽责性",   f("personality.conscientiousness")),
            ProfileAspect("profile.personality.extraversion",       "外向性",   f("personality.extraversion")),
            ProfileAspect("profile.personality.agreeableness",      "宜人性",   f("personality.agreeableness")),
            ProfileAspect("profile.personality.neuroticism",        "情绪倾向", f("personality.neuroticism")),
            ProfileAspect("profile.personality.style",              "行事风格", f("personality.style")),
        )),
        // ── 认知 ── cognitive style + Holland RIASEC (1973)
        ProfileDimension("cognitive", "💡", "认知维度", Color(0xFF2196F3), listOf(
            ProfileAspect("profile.cognitive.thinking",    "思维风格", f("cognitive.thinking")),
            ProfileAspect("profile.cognitive.learning",    "学习方式", f("cognitive.learning")),
            ProfileAspect("profile.cognitive.decision",    "决策风格", f("cognitive.decision")),
            ProfileAspect("profile.cognitive.perspective", "眼界格局", f("cognitive.perspective")),
            ProfileAspect("profile.cognitive.creativity",  "创新思维", f("cognitive.creativity")),
            ProfileAspect("profile.cognitive.riasec",      "职业类型", f("cognitive.riasec")),
        )),
        // ── 情绪 ── Ryff's Well-being (1989): self-acceptance, resilience
        ProfileDimension("emotional", "💗", "情绪维度", Color(0xFFE91E63), listOf(
            ProfileAspect("profile.emotional.stability",       "情绪稳定性", f("emotional.stability")),
            ProfileAspect("profile.emotional.empathy",         "共情能力",   f("emotional.empathy")),
            ProfileAspect("profile.emotional.stress",          "抗压能力",   f("emotional.stress")),
            ProfileAspect("profile.emotional.resilience",      "心理韧性",   f("emotional.resilience")),
            ProfileAspect("profile.emotional.self_acceptance", "自我接纳",   f("emotional.self_acceptance")),
        )),
        // ── 社交 ── social patterns + Ryff positive relations
        ProfileDimension("social", "🌐", "社会维度", Color(0xFF00BCD4), listOf(
            ProfileAspect("profile.social.style",         "社交风格", f("social.style")),
            ProfileAspect("profile.social.communication", "沟通方式", f("social.communication")),
            ProfileAspect("profile.social.relationships", "人际关系", f("social.relationships")),
            ProfileAspect("profile.social.influence",     "社会影响力", f("social.influence")),
            ProfileAspect("profile.social.boundaries",    "边界感",   f("social.boundaries")),
        )),
        // ── 价值 ── Schwartz Values (1992): universalism, benevolence, autonomy, achievement
        ProfileDimension("values", "⚖️", "价值维度", Color(0xFF9C27B0), listOf(
            ProfileAspect("profile.values.core",        "核心价值观", f("values.core")),
            ProfileAspect("profile.values.goals",       "人生目标",   f("values.goals")),
            ProfileAspect("profile.values.principles",  "底线原则",   f("values.principles")),
            ProfileAspect("profile.values.achievement", "成就取向",   f("values.achievement")),
            ProfileAspect("profile.values.benevolence", "利他倾向",   f("values.benevolence")),
            ProfileAspect("profile.values.autonomy",    "自主意志",   f("values.autonomy")),
        )),
        // ── 能力 ── capability + SDT intrinsic motivation (Deci & Ryan)
        ProfileDimension("capability", "⚡", "能力维度", Color(0xFFFF5722), listOf(
            ProfileAspect("profile.capability.skills",     "专业技能", f("capability.skills")),
            ProfileAspect("profile.capability.execution",  "执行力",   f("capability.execution")),
            ProfileAspect("profile.capability.creativity", "创造力",   f("capability.creativity")),
            ProfileAspect("profile.capability.technical",  "技术能力", f("capability.technical")),
            ProfileAspect("profile.capability.intrinsic",  "内在驱动", f("capability.intrinsic")),
        )),
        // ── 精神 ── Ryff's purpose in life + personal growth
        ProfileDimension("spiritual", "🔮", "精神维度", Color(0xFF607D8B), listOf(
            ProfileAspect("profile.spiritual.core",       "内心内核", f("spiritual.core")),
            ProfileAspect("profile.spiritual.beliefs",    "信念体系", f("spiritual.beliefs")),
            ProfileAspect("profile.spiritual.resilience", "自愈力",   f("spiritual.resilience")),
            ProfileAspect("profile.spiritual.purpose",    "人生意义", f("spiritual.purpose")),
            ProfileAspect("profile.spiritual.growth",     "个人成长", f("spiritual.growth")),
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
        ClawPageHeader(title = "了解自己", onBack = onBack) {
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
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        PersonalitySummaryCard(
                            summary = personalitySummary,
                            isLoading = personalitySummaryLoading,
                            onRefresh = onGenerateSummary,
                            conversationCount = conversationCount,
                        )
                    }
                    if (totalSkillCount > 0 || episodes.isNotEmpty()) {
                        item { Spacer(Modifier.height(4.dp)) }
                        item { SkillExplorationCard(episodes = episodes, totalSkillCount = totalSkillCount) }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        DimensionsListSection(
                            dimensions = dimensions,
                            facts = facts,
                            onOpenDimension = { openDimension = it },
                        )
                    }
                }
                ProfileSection.MEMORY -> {
                    item { Spacer(Modifier.height(4.dp)) }
                    item { MemoryBrowserCard(facts) }
                }
                ProfileSection.HISTORY -> {
                    if (episodes.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📚", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("还没有对话记录", color = c.subtext, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        item { Spacer(Modifier.height(4.dp)) }
                        items(episodes.size) { i -> EpisodeCard(episodes[i]) }
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
        progress >= 1f   -> "全部解锁 🎉"
        progress >= 0.75f -> "探索达人"
        progress >= 0.5f  -> "进阶探索者"
        progress >= 0.25f -> "初级探索者"
        explored > 0      -> "刚开始探索"
        else              -> "尚未使用任何技能"
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
            Text("🧭 功能探索", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(milestoneLabel, color = milestoneColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        HorizontalDivider(color = c.border, thickness = 0.5.dp)

        // Progress bar
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已使用 $explored 个技能", fontSize = 12.sp, color = c.subtext)
                Text("共 $total 个", fontSize = 12.sp, color = c.subtext)
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
            Text("✨", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "AI 眼中的你",
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
                    "AI 正在认识你…",
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
                        "继续和 AI 对话，它会慢慢认识你——",
                        color = c.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "你的性格、思维方式、价值观和生活习惯，都会逐渐沉淀在这里。",
                        color = c.subtext,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )
                    if (conversationCount > 0) {
                        Text(
                            "已对话 $conversationCount 次，再多聊几次就能生成你的专属描述。",
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
                        Text("生成我的描述", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
            RadarStat("完整度", "${knownCount * 100 / totalCount.coerceAtLeast(1)}%", c.accent)
            RadarStat("已知", "$knownCount/$totalCount", c.blue)
            RadarStat("对话", "$conversationCount", c.purple)
            RadarStat("任务", "$episodeCount", c.green)
        }

        // Radar chart
        RadarChart(values = values, dimensions = dimensions, selectedIdx = selectedIdx, onSelect = onSelectDim)

        // Hint
        Text(
            text = if (isExtracting) "正在分析对话，更新画像..."
                   else if (selectedIdx != null) "已选：${dimensions[selectedIdx].title}  ↓ 查看详情"
                   else "点击维度标签查看详情",
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
                    Text(dim.emoji, fontSize = 13.sp, lineHeight = 15.sp)
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
            Text(dimension.emoji, fontSize = 18.sp)
            Text(dimension.title, color = dimension.color, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${knownAspects.size}/${dimension.aspects.size} 已知", color = dimension.color.copy(alpha = 0.7f), fontSize = 10.sp)
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
                    Text("待观察", color = c.subtext.copy(alpha = 0.3f), fontSize = 11.sp, fontStyle = FontStyle.Italic)
                }
            }
        }

        // Related tasks
        if (relatedTasks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = dimension.color.copy(alpha = 0.12f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            Text("📊 关联任务", color = c.subtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
        "physio"      -> listOf("运动", "健身", "健康", "医院", "饮食", "锻炼", "睡眠")
        "personality" -> listOf("心情", "感觉", "想法", "喜欢", "讨厌", "性格")
        "cognitive"   -> listOf("学习", "研究", "分析", "了解", "探索", "搜索", "查")
        "emotional"   -> listOf("担心", "开心", "情绪", "心理", "压力", "焦虑", "放松")
        "social"      -> listOf("联系", "聊天", "朋友", "微信", "社交", "分享", "发送")
        "values"      -> listOf("目标", "计划", "价值", "工作", "生活", "未来", "意义")
        "capability"  -> listOf("代码", "写", "创建", "执行", "完成", "实现", "做")
        "spiritual"   -> listOf("思考", "感悟", "意义", "冥想", "放松", "平静")
        else          -> emptyList()
    }
    return episodes.filter { ep -> keywords.any { kw -> ep.goalText.contains(kw) } }
}

// ── Section Tabs ──────────────────────────────────────────────────────────────

@Composable
private fun SectionTabRow(active: ProfileSection, onSelect: (ProfileSection) -> Unit) {
    val c = LocalClawColors.current
    val tabs = listOf(ProfileSection.PORTRAIT to "认识自己", ProfileSection.MEMORY to "记忆", ProfileSection.HISTORY to "历史")

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
                Text("🎯", fontSize = 28.sp)
                Text("完成任务后将在此显示分析", color = c.subtext, fontSize = 13.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
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
            Text("🔧 技能使用分布", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            Text("📈 行为分析", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
            Spacer(Modifier.height(2.dp))
            InsightRow("执行力", "${(successRate * 100).toInt()}% 任务成功率", successRate, c.green)
            InsightRow("探索欲", "任务多样性 ${(variety * 100).toInt()}%", variety.coerceIn(0f, 1f), c.purple)
            InsightRow("活跃度", "${episodes.size} 个历史任务", (episodes.size / 50f).coerceIn(0.05f, 1f), c.accent)
            InsightRow("数据技能", if (allSkills.any { it.startsWith("web_") || it == "shell" }) "使用过网络/系统技能" else "尚未使用高级技能",
                if (allSkills.any { it.startsWith("web_") || it == "shell" }) 0.8f else 0.2f, c.blue)
        }
    }
}

@Composable
private fun SkillBarRow(skill: String, count: Int, fraction: Float, c: ClawColors) {
    val trait = when {
        skill.startsWith("web_")  -> "信息检索"
        skill == "screenshot" || skill == "see_screen" || skill == "read_screen" -> "视觉分析"
        skill == "tap" || skill == "scroll" || skill == "input_text" -> "界面交互"
        skill == "navigate"       -> "应用切换"
        skill == "memory"         -> "知识管理"
        skill == "shell"          -> "技术操作"
        skill.startsWith("bg_")   -> "后台执行"
        else                      -> "通用"
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
    "profile.physio.health"                  to "整体健康状况与日常身体感受",
    "profile.physio.fitness"                 to "运动习惯、体能水平与训练偏好",
    "profile.physio.sleep"                   to "睡眠规律、时长与质量",
    "profile.physio.nutrition"               to "饮食偏好、忌口与营养习惯",
    "profile.physio.medical"                 to "医疗史、过敏、慢性病与用药",
    "profile.personality.openness"           to "对新体验、创意与多元观点的接受度",
    "profile.personality.conscientiousness"  to "做事认真、有条理、自律的程度",
    "profile.personality.extraversion"       to "在社交场合中活跃、主动的程度",
    "profile.personality.agreeableness"      to "合作、信任与包容他人的倾向",
    "profile.personality.neuroticism"        to "情绪波动频率与压力下的反应",
    "profile.personality.style"              to "个人独特的行事节奏与习惯偏好",
    "profile.cognitive.thinking"             to "逻辑/直觉型，系统性/发散性思维",
    "profile.cognitive.learning"             to "偏好实践/阅读/视听/讨论等方式",
    "profile.cognitive.decision"             to "数据驱动还是直觉经验导向",
    "profile.cognitive.perspective"          to "关注宏观战略还是微观执行",
    "profile.cognitive.creativity"           to "在创新场景下的思维灵活度",
    "profile.cognitive.riasec"               to "Holland职业类型: R实际/I研究/A艺术/S社会/E企业/C事务",
    "profile.emotional.stability"            to "情绪波动频率与维持平静的能力",
    "profile.emotional.empathy"              to "感知和理解他人情绪状态的能力",
    "profile.emotional.stress"               to "面对压力时的应对策略与承受力",
    "profile.emotional.resilience"           to "从挫折和逆境中恢复的速度与方式",
    "profile.emotional.self_acceptance"      to "对自身优缺点的客观接纳程度",
    "profile.social.style"                   to "主动型/选择型/独立型等社交风格",
    "profile.social.communication"           to "直接/间接、书面/口头等沟通偏好",
    "profile.social.relationships"           to "维系亲密关系与社交网络的方式",
    "profile.social.influence"               to "在群体中影响他人想法的能力",
    "profile.social.boundaries"              to "设定并维护个人边界的意识",
    "profile.values.core"                    to "深层驱动行为的核心价值观",
    "profile.values.goals"                   to "中长期人生目标与优先级",
    "profile.values.principles"              to "不可妥协的道德底线与原则",
    "profile.values.achievement"             to "成就取向：对成功、地位的看重程度",
    "profile.values.benevolence"             to "利他倾向：关心他人福祉的程度",
    "profile.values.autonomy"                to "自主意志：独立思考与自由选择的需求",
    "profile.capability.skills"              to "专业领域技能与知识深度",
    "profile.capability.execution"           to "将计划转化为行动的效率",
    "profile.capability.creativity"          to "产生新颖想法与解决方案的能力",
    "profile.capability.technical"           to "技术工具、编程、系统操作能力",
    "profile.capability.intrinsic"           to "内在兴趣驱动 vs 外在奖励驱动",
    "profile.spiritual.core"                 to "最深层的自我认同与内心力量",
    "profile.spiritual.beliefs"              to "世界观、信仰体系与存在意义",
    "profile.spiritual.resilience"           to "精神层面的自愈与恢复能力",
    "profile.spiritual.purpose"              to "Ryff: 对生命意义与人生使命的感知",
    "profile.spiritual.growth"               to "持续自我超越与个人成长的渴望",
)

private val ASPECT_HINTS = mapOf(
    "profile.physio.health"                  to "告诉我最近身体状态如何",
    "profile.physio.fitness"                 to "描述你的运动习惯或健身计划",
    "profile.physio.sleep"                   to "说说你的睡眠时间和质量",
    "profile.physio.nutrition"               to "你有什么饮食偏好或忌口吗",
    "profile.physio.medical"                 to "有需要注意的健康状况吗",
    "profile.personality.openness"           to "你愿意尝试新鲜事物吗",
    "profile.personality.conscientiousness"  to "你怎么管理任务和时间",
    "profile.personality.extraversion"       to "你喜欢独处还是社交",
    "profile.personality.agreeableness"      to "你在冲突中倾向于配合还是坚持",
    "profile.personality.neuroticism"        to "压力大时你通常如何反应",
    "profile.personality.style"              to "描述你的工作风格",
    "profile.cognitive.thinking"             to "你更喜欢系统思维还是直觉判断",
    "profile.cognitive.learning"             to "你用什么方式学习新技能",
    "profile.cognitive.decision"             to "做重要决定时你怎么选择",
    "profile.cognitive.perspective"          to "你更关注全局规划还是执行细节",
    "profile.cognitive.creativity"           to "遇到难题你如何找到创意方案",
    "profile.cognitive.riasec"               to "你的职业或兴趣偏向是什么",
    "profile.emotional.stability"            to "你的情绪通常稳定吗",
    "profile.emotional.empathy"              to "你容易感知他人的情绪吗",
    "profile.emotional.stress"               to "高压时你如何疏解情绪",
    "profile.emotional.resilience"           to "遭遇挫折后你多久能恢复",
    "profile.emotional.self_acceptance"      to "你对自己的优缺点态度如何",
    "profile.social.style"                   to "你是主动社交还是被动回应型",
    "profile.social.communication"           to "你更擅长文字还是口头表达",
    "profile.social.relationships"           to "你重视哪类人际关系",
    "profile.social.influence"               to "你在团队中扮演什么角色",
    "profile.social.boundaries"              to "你如何设定个人边界",
    "profile.values.core"                    to "对你最重要的事情是什么",
    "profile.values.goals"                   to "你5年内最重要的目标是什么",
    "profile.values.principles"              to "你有哪些底线不可妥协",
    "profile.values.achievement"             to "成功对你意味着什么",
    "profile.values.benevolence"             to "帮助他人对你有多重要",
    "profile.values.autonomy"                to "你需要多大程度的自主空间",
    "profile.capability.skills"              to "你最擅长哪个专业领域",
    "profile.capability.execution"           to "你一般能高效完成计划吗",
    "profile.capability.creativity"          to "举例说说你的创意解决方案",
    "profile.capability.technical"           to "你熟悉哪些技术工具",
    "profile.capability.intrinsic"           to "什么驱动你去完成一件事",
    "profile.spiritual.core"                 to "你内心最深处相信什么",
    "profile.spiritual.beliefs"              to "你有宗教信仰或世界观吗",
    "profile.spiritual.resilience"           to "困境中你如何找到力量",
    "profile.spiritual.purpose"              to "你认为自己活着的意义是什么",
    "profile.spiritual.growth"               to "你如何追求个人成长",
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
            "你的各个侧面",
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
                    Text(dim.emoji, fontSize = 18.sp)
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    // Dimension name without "维度" suffix
                    val shortTitle = dim.title.removeSuffix("维度")
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
                            "和 AI 多聊聊，它会慢慢了解你这方面",
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
                Text("💭", fontSize = 36.sp)
                Spacer(Modifier.height(4.dp))
                Text("AI 还没有记住什么", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "继续使用，AI 会自动从你们的对话里提炼和记录重要信息。",
                    color = c.subtext,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    val groupLabels = mapOf("profile" to "关于你的画像", "user" to "你的偏好", "app" to "应用信息", "device" to "设备信息")
    val grouped = facts.entries.sortedBy { it.key }.groupBy { it.key.substringBefore(".", it.key) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "AI 记住的事情",
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
            Text("${entries.size} 条", color = c.subtext, fontSize = 11.sp)
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
                    Text(if (episode.success) "✓ 成功" else "✗ 失败", color = if (episode.success) c.green else c.red, fontSize = 10.sp)
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
