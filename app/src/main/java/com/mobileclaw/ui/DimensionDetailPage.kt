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

// ── Quiz data per dimension ───────────────────────────────────────────────────

private data class QuizQuestion(
    val question: String,
    val answers: List<String>,
    val factKey: String,
)

private val DIMENSION_QUIZ: Map<String, List<QuizQuestion>> = mapOf(
    "physio" to listOf(
        QuizQuestion("你最近一周的运动频率？", listOf("每天运动", "每周 2-3 次", "偶尔运动", "基本不运动"), "profile.physio.fitness"),
        QuizQuestion("你的睡眠质量如何？", listOf("很好，每天 7-8 小时", "还可以，偶有失眠", "较差，经常失眠", "严重睡眠不足"), "profile.physio.sleep"),
        QuizQuestion("你的饮食习惯？", listOf("规律均衡", "基本健康但不规律", "较不健康", "不太注意饮食"), "profile.physio.nutrition"),
    ),
    "personality" to listOf(
        QuizQuestion("参加社交活动后，你通常感觉？", listOf("精力充沛，很享受", "还好，但需要恢复", "有些疲惫，偏好小圈子", "消耗大，需要大量独处"), "profile.personality.extraversion"),
        QuizQuestion("面对新事物，你的第一反应是？", listOf("充满好奇，主动尝试", "感兴趣但先观察", "谨慎，适应后才接受", "倾向于熟悉的方式"), "profile.personality.openness"),
        QuizQuestion("完成重要任务时，你的风格？", listOf("有详细计划，严格执行", "大致规划，灵活调整", "边做边想，随机应变", "等到最后再冲刺"), "profile.personality.conscientiousness"),
    ),
    "cognitive" to listOf(
        QuizQuestion("你更倾向于哪种学习方式？", listOf("系统学习，理论到实践", "边做边学，解决问题", "看视频/听讲解", "阅读文档/书籍"), "profile.cognitive.learning"),
        QuizQuestion("做重要决策时，你通常？", listOf("大量收集数据后理性分析", "依靠直觉和经验判断", "广泛征询他人意见", "快速决策，边走边调整"), "profile.cognitive.decision"),
        QuizQuestion("你的思维风格偏向？", listOf("逻辑分析，注重因果", "整体直觉，关注模式", "创意联想，跳跃思维", "务实具体，关注细节"), "profile.cognitive.thinking"),
    ),
    "emotional" to listOf(
        QuizQuestion("遇到高压或突发困难时，你通常？", listOf("保持冷静，系统处理", "有些焦虑但能应对", "情绪波动较大，需时间平复", "容易崩溃，需要支持"), "profile.emotional.stability"),
        QuizQuestion("你对他人情绪的感知程度？", listOf("极强，容易感同身受", "适中，能察觉但不过分投入", "较弱，偏理性处理", "基本不关注他人情绪"), "profile.emotional.empathy"),
        QuizQuestion("面对挫折和失败，你的恢复速度？", listOf("很快，挫折反而激励我", "需要几天但会反弹", "需要较长时间调整", "很慢，负面情绪持续较久"), "profile.emotional.resilience"),
    ),
    "social" to listOf(
        QuizQuestion("你的社交风格？", listOf("热情主动，喜欢大场合", "适中，视情况而定", "小圈子深度交往", "倾向于独处，按需社交"), "profile.social.style"),
        QuizQuestion("你更擅长哪种沟通方式？", listOf("直接表达，开门见山", "委婉表达，照顾他人感受", "倾听为主，引导对方", "书面沟通更有把握"), "profile.social.communication"),
        QuizQuestion("在人际关系中，你通常扮演？", listOf("主导者，带动氛围", "协调者，化解矛盾", "支持者，默默付出", "独立者，保持距离"), "profile.social.relationships"),
    ),
    "values" to listOf(
        QuizQuestion("对你目前最重要的是什么？", listOf("家庭与亲情", "事业成就与社会认可", "个人成长与自由", "物质安全与稳定"), "profile.values.core"),
        QuizQuestion("面对利益与原则冲突时，你会？", listOf("坚守原则，绝不妥协", "原则优先，但留有弹性", "视情况灵活处理", "偏向实际利益"), "profile.values.principles"),
        QuizQuestion("你的成就取向偏向？", listOf("超越他人，追求领先", "超越自我，持续进步", "团队共赢，一起成功", "安稳舒适，减少压力"), "profile.values.achievement"),
    ),
    "capability" to listOf(
        QuizQuestion("你的核心专业技能领域？", listOf("技术/编程/工程", "设计/创意/艺术", "管理/商业/运营", "研究/分析/学术"), "profile.capability.skills"),
        QuizQuestion("你的执行力风格？", listOf("完美主义，精益求精", "高效快速，80分完成", "持续推进，保持节奏", "灵活应变，随机调整"), "profile.capability.execution"),
        QuizQuestion("你的内在驱动力来自？", listOf("好奇心与探索欲", "竞争与超越欲望", "责任感与使命感", "认可与成就感"), "profile.capability.intrinsic"),
    ),
    "spiritual" to listOf(
        QuizQuestion("你的人生意义主要来源于？", listOf("对他人和社会的贡献", "个人成长与自我突破", "创造有价值的事物", "内心平静与真实自在"), "profile.spiritual.purpose"),
        QuizQuestion("面对无法改变的困境，你的内心？", listOf("接受现实，专注可控之处", "持续抗争，不轻易放弃", "寻找意义，转化为成长", "需要时间，慢慢释怀"), "profile.spiritual.resilience"),
        QuizQuestion("你如何看待个人信念？", listOf("有明确的世界观和价值体系", "相信科学与理性", "开放多元，持续更新", "淡泊名利，顺其自然"), "profile.spiritual.beliefs"),
    ),
)

private val DIMENSION_FRAMEWORK: Map<String, Pair<String, String>> = mapOf(
    "physio"      to ("生物心理社会模型（BPS Model）" to "身体是一切的基础。健康状态、体能、睡眠和饮食共同影响认知效率和情绪稳定性。"),
    "personality" to ("大五人格模型（Big Five / OCEAN）" to "McCrae & Costa（1992）提出的五大人格维度：开放性 O、尽责性 C、外向性 E、宜人性 A、神经质 N。"),
    "cognitive"   to ("认知风格理论 + Holland 职业兴趣模型" to "思维方式、学习策略和决策风格共同构成认知画像，影响问题解决和知识吸收的效率。"),
    "emotional"   to ("Ryff 心理幸福感模型（1989）" to "情绪稳定性、共情能力、抗压能力和心理韧性决定了在逆境中的适应性与幸福感。"),
    "social"      to ("社会心理学 + 依恋理论" to "社交风格和沟通方式影响人际质量。内外向不决定好坏，而是影响能量来源与社交策略。"),
    "values"      to ("Schwartz 价值理论（1992）" to "价值观是行为的深层驱动器，涵盖普世主义、慈善、成就、安全、自主等 10 个核心类型。"),
    "capability"  to ("自我决定理论 SDT（Deci & Ryan）" to "能力感、自主感、归属感共同支撑内在动机，驱动持续学习与高效执行。"),
    "spiritual"   to ("Ryff 人生意义 + 积极心理学" to "精神维度关注人生意义、信念体系和个人成长，是应对虚无与逆境的根本支撑。"),
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
                    "$known/${dimension.aspects.size} 已知",
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
                    "📋 维度指标",
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
                        Text("📚 理论框架", color = c.subtext, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
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
                        "🧩 自我评估",
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
                        Text("AI 生成中…", color = c.subtext.copy(alpha = 0.5f), fontSize = 10.sp)
                    } else {
                        IconButton(
                            onClick = onRegenerateQuiz,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "换一批",
                                tint = dimension.color.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Text("换一批", color = dimension.color.copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (generatedQuiz != null) "AI 为你定制的深度问题，答案自动写入画像" else "选择答案后，结果会自动写入你的画像",
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
            title = { Text("编辑 · ${editing.label}", fontSize = 15.sp) },
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
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editAspect = null }) { Text("取消") }
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
                Text("待观察", color = c.subtext.copy(alpha = 0.4f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
            }
        }
        Icon(Icons.Default.Edit, contentDescription = "编辑", tint = c.subtext.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
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
