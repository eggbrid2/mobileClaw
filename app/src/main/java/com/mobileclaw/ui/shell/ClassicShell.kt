package com.mobileclaw.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mobileclaw.R
import com.mobileclaw.agent.Group
import com.mobileclaw.app.MiniApp
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.ui.aipage.AiPageDef
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.ui.group.GroupPreview
import com.mobileclaw.str

enum class ClassicTab { HOME, WORKSPACE, ME }

@Composable
fun ClassicScaffold(
    selected: ClassicTab,
    onSelect: (ClassicTab) -> Unit,
    title: String,
    tabs: List<Pair<String, Boolean>> = emptyList(),
    onTab: (Int) -> Unit = {},
    leadingAction: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalClawColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(classicAmbientBrush(c.isDark))
    ) {
        Box(Modifier.fillMaxSize()) {
            ClassicAmbientLight()
            Column(Modifier.fillMaxSize()) {
                ClassicChromeTop(
                    title = title,
                    tabs = tabs,
                    onTab = onTab,
                    leadingAction = leadingAction,
                    trailingAction = trailingAction,
                )
                Box(Modifier.weight(1f)) { content() }
            }
        }
        ClassicBottomBar(
            selected = selected,
            onSelect = onSelect,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private data class ClassicTabItem(val tab: ClassicTab, val icon: ImageVector, val label: String)

private fun classicAmbientBrush(isDark: Boolean): Brush =
    if (isDark) {
        Brush.verticalGradient(
            listOf(Color(0xFF080807), Color(0xFF10100E), Color(0xFF080807))
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFCF8),
                Color(0xFFF8F9F6),
                Color(0xFFF7F8F5),
            )
        )
    }

@Composable
private fun ClassicAmbientLight() {
    val c = LocalClawColors.current
    if (c.isDark) return
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFD69867).copy(alpha = 0.16f),
                    Color(0xFFD69867).copy(alpha = 0.0f),
                ),
                startY = 0f,
                endY = 124.dp.toPx(),
            ),
            size = androidx.compose.ui.geometry.Size(size.width, 124.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFD7B693).copy(alpha = 0.08f),
                    Color(0xFFD7B693).copy(alpha = 0.03f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.04f, size.height * 0.78f),
                radius = size.width * 0.62f,
            ),
            radius = size.width * 0.62f,
            center = Offset(size.width * 0.04f, size.height * 0.78f),
        )
    }
}

@Composable
private fun classicTabs() = listOf(
    ClassicTabItem(ClassicTab.HOME, HtmlConversationIcon, "会话"),
    ClassicTabItem(ClassicTab.WORKSPACE, HtmlWorkspaceIcon, str(R.string.classic_workspace)),
    ClassicTabItem(ClassicTab.ME, HtmlProfileIcon, str(R.string.classic_me)),
)

private fun Modifier.classicAcrylicSurface(
    shape: RoundedCornerShape,
    isDark: Boolean,
    surfaceAlpha: Float,
    shadowAlpha: Float,
): Modifier {
    return this
        .shadow(
            elevation = 22.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = shadowAlpha),
            spotColor = Color.Black.copy(alpha = shadowAlpha),
        )
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.12f else surfaceAlpha),
                    Color.White.copy(alpha = if (isDark) 0.06f else surfaceAlpha * 0.72f),
                    Color(0xFFEDEDE8).copy(alpha = if (isDark) 0.10f else 0.22f),
                )
            )
        )
        .border(0.8.dp, Color.White.copy(alpha = if (isDark) 0.22f else 0.86f), shape)
}

@Composable
private fun ClassicChromeTop(
    title: String,
    tabs: List<Pair<String, Boolean>>,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingAction: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val c = LocalClawColors.current
    val chromeBg = if (c.isDark) c.bg else Color(0xFFF7F7F4)
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (tabs.isEmpty()) {
            Text(
                title,
                color = c.text,
                fontSize = 24.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
                Row(
                    Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.width(40.dp).height(36.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    leadingAction?.invoke()
                }
                Row(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(chromeBg)
                        .border(0.5.dp, c.border.copy(alpha = 0.68f), RoundedCornerShape(18.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEachIndexed { index, (label, selected) ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (selected) c.text else Color.Transparent)
                                .clickable { onTab(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label.stripClassicTabEmoji(),
                                color = if (selected) c.bg else c.subtext,
                                fontSize = if (label.length > 8) 11.sp else 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                Box(
                    Modifier.width(40.dp).height(36.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    trailingAction?.invoke()
                }
            }
        }
        if (tabs.isNotEmpty()) {
            HorizontalDivider(color = c.border.copy(alpha = 0.42f), thickness = 0.5.dp)
        }
    }
}

private fun String.stripClassicTabEmoji(): String =
    replace(Regex("^[\\p{So}\\p{Sk}]+\\s*"), "").trim()

private fun htmlStrokeIcon(
    name: String,
    paths: List<String>,
    strokeWidth: Float,
    strokeCap: StrokeCap = StrokeCap.Butt,
    strokeJoin: StrokeJoin = StrokeJoin.Miter,
): ImageVector =
    Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = strokeCap,
                strokeLineJoin = strokeJoin,
            )
        }
    }.build()

private val HtmlPlusIcon = htmlStrokeIcon(
    name = "html_plus",
    paths = listOf("M12 5v14M5 12h14"),
    strokeWidth = 1.9f,
    strokeCap = StrokeCap.Round,
)

private val HtmlGroupIcon = htmlStrokeIcon(
    name = "html_group",
    paths = listOf(
        "M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8-1a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM3.8 19c.8-3.4 2.2-5.1 4.2-5.1s3.4 1.7 4.2 5.1M13.2 14.4c2.5.2 4 1.7 4.8 4.6",
    ),
    strokeWidth = 1.7f,
    strokeCap = StrokeCap.Round,
)

private val HtmlConversationIcon = htmlStrokeIcon(
    name = "html_conversation",
    paths = listOf("M5 6.5A3.5 3.5 0 0 1 8.5 3h7A3.5 3.5 0 0 1 19 6.5v4A3.5 3.5 0 0 1 15.5 14H12l-4.5 4v-4A3.5 3.5 0 0 1 5 10.5v-4Z"),
    strokeWidth = 1.8f,
    strokeJoin = StrokeJoin.Round,
)

private val HtmlWorkspaceIcon = htmlStrokeIcon(
    name = "html_workspace",
    paths = listOf(
        "M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v11a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 17.5v-11Z",
        "M8 9h8M8 13h5",
    ),
    strokeWidth = 1.8f,
    strokeCap = StrokeCap.Round,
)

private val HtmlProfileIcon = htmlStrokeIcon(
    name = "html_profile",
    paths = listOf("M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8c.8-3.8 3.1-5.8 7-5.8s6.2 2 7 5.8"),
    strokeWidth = 1.8f,
    strokeCap = StrokeCap.Round,
)

private val HtmlImageIcon = htmlStrokeIcon(
    name = "html_image",
    paths = listOf(
        "M4 7.5A3.5 3.5 0 0 1 7.5 4h9A3.5 3.5 0 0 1 20 7.5v9a3.5 3.5 0 0 1-3.5 3.5h-9A3.5 3.5 0 0 1 4 16.5v-9Z",
        "m7 16 3.2-3.4 2.4 2.2 2-2.4L18 16",
        "M9 8.8h.01",
    ),
    strokeWidth = 1.8f,
    strokeCap = StrokeCap.Round,
    strokeJoin = StrokeJoin.Round,
)

private val HtmlVideoIcon = htmlStrokeIcon(
    name = "html_video",
    paths = listOf(
        "M4 8.5A3.5 3.5 0 0 1 7.5 5h6A3.5 3.5 0 0 1 17 8.5v7a3.5 3.5 0 0 1-3.5 3.5h-6A3.5 3.5 0 0 1 4 15.5v-7Z",
        "m17 10 3-2v8l-3-2",
    ),
    strokeWidth = 1.8f,
    strokeCap = StrokeCap.Round,
    strokeJoin = StrokeJoin.Round,
)

@Composable
fun ClassicSessionAction(onClick: () -> Unit) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (c.isDark) c.cardAlt else Color(0xFFF2F2EF))
            .border(0.5.dp, c.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Menu, contentDescription = str(R.string.skills_9a834e), tint = c.text, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun ClassicCodexAction(enabled: Boolean, onClick: () -> Unit) {
    val c = LocalClawColors.current
    Row(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) c.text else if (c.isDark) c.cardAlt else Color(0xFFF2F2EF))
            .border(0.5.dp, if (enabled) c.text else c.border, CircleShape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Terminal,
            contentDescription = "Codex",
            tint = if (enabled) c.bg else c.text,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun ClassicBottomBar(
    selected: ClassicTab,
    onSelect: (ClassicTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.20f else 0.055f),
                    spotColor = Color.Black.copy(alpha = if (c.isDark) 0.24f else 0.075f),
                )
                .clip(shape)
                .height(42.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (c.isDark) 0.12f else 0.82f),
                            Color.White.copy(alpha = if (c.isDark) 0.06f else 0.46f),
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = if (c.isDark) 0.22f else 0.80f), shape)
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = if (c.isDark) 0.08f else 0.18f),
                                Color.White.copy(alpha = if (c.isDark) 0.18f else 0.62f),
                                Color.White.copy(alpha = if (c.isDark) 0.06f else 0.16f),
                            )
                        )
                    )
            )
            Box(
                Modifier
                    .matchParentSize()
                    .border(0.6.dp, Color.White.copy(alpha = if (c.isDark) 0.22f else 0.42f), shape)
            )
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                classicTabs().forEach { item ->
                    val active = selected == item.tab
                    ClassicDockItem(
                        item = item,
                        active = active,
                        onClick = { onSelect(item.tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicDockItem(
    item: ClassicTabItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    val activeWidth = if (!active) 32.dp else (48 + item.label.length * 14).dp
    val activeContent = if (c.isDark) Color(0xFF111111) else Color.White
    val inactiveContent = if (c.isDark) c.subtext.copy(alpha = 0.72f) else Color(0xFF72726D)
    val itemShape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .width(activeWidth)
            .height(34.dp)
            .shadow(
                elevation = if (active) 7.dp else 0.dp,
                shape = itemShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (active && !c.isDark) 0.12f else 0f),
                spotColor = Color.Black.copy(alpha = if (active && !c.isDark) 0.18f else 0f),
            )
            .clip(itemShape)
            .clickable(onClick = onClick)
            .background(
                if (active) {
                    Brush.verticalGradient(
                        if (c.isDark) {
                            listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.78f))
                        } else {
                            listOf(Color(0xFF171716), Color(0xFF24231F))
                        }
                    )
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(
                if (active) 0.7.dp else 0.dp,
                if (active) {
                    if (c.isDark) Color.White.copy(alpha = 0.36f) else Color.Black.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                },
                itemShape,
            )
            .padding(horizontal = if (active) 7.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = if (active) activeContent else inactiveContent,
                modifier = Modifier.size(18.5.dp),
            )
            if (active) {
                Spacer(Modifier.width(2.dp))
                Text(
                    item.label,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    fontSize = 10.5.sp,
                    lineHeight = 10.5.sp,
                    color = activeContent,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun ClassicCenterDockItem(
    item: ClassicTabItem,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Column(
        modifier = modifier
            .width(92.dp)
            .height(90.dp)
            .zIndex(2f)
            .clickable(onClick = onClick)
            .padding(top = 0.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(if (active) c.text else c.surface)
                .border(4.dp, if (c.isDark) c.bg else Color(0xFFF7F7F4), CircleShape)
                .border(0.8.dp, if (active) c.text else c.border.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = if (active) c.bg else c.text,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            fontSize = if (item.label.length > 6) 9.6.sp else 10.6.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            color = if (active) c.text else c.subtext,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 16.dp, max = 24.dp)
                .padding(horizontal = 2.dp),
        )
    }
}

@Composable
fun ClassicHomePage(
    sessions: List<SessionEntity>,
    groups: List<Group>,
    groupPreviews: Map<String, GroupPreview>,
    currentSessionId: String,
    isConfigured: Boolean,
    onNewChat: () -> Unit,
    onOpenGroups: () -> Unit,
    onConfigureGateway: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenGroup: (Group) -> Unit,
) {
    val c = LocalClawColors.current
    var filter by remember { mutableStateOf("全部") }
    val conversationItems = remember(sessions, groups, groupPreviews) {
        buildList {
            sessions.forEach { session ->
                add(
                    ClassicConversationItem.Single(
                        id = session.id,
                        title = session.title.ifBlank { "新会话" },
                        preview = "点击进入聊天线程",
                        updatedAt = session.updatedAt,
                        session = session,
                    )
                )
            }
            groups.forEach { group ->
                val preview = groupPreviews[group.id]
                add(
                    ClassicConversationItem.GroupChat(
                        id = group.id,
                        title = group.name.ifBlank { "群聊" },
                        preview = preview?.let { "${it.senderName}: ${it.text}" } ?: "${group.memberRoleIds.size} 位成员",
                        updatedAt = preview?.createdAt ?: group.updatedAt,
                        group = group,
                    )
                )
            }
        }.sortedByDescending { it.updatedAt }
    }
    val filteredItems = when (filter) {
        "单聊" -> conversationItems.filterIsInstance<ClassicConversationItem.Single>()
        "群聊" -> conversationItems.filterIsInstance<ClassicConversationItem.GroupChat>()
        else -> conversationItems
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(start = 24.dp, end = 24.dp, top = 0.dp),
    ) {
        ClassicNewChatPanel(
            onNewChat = if (isConfigured) onNewChat else onConfigureGateway,
            onOpenGroups = if (isConfigured) onOpenGroups else onConfigureGateway,
        )
        Spacer(Modifier.height(18.dp))
        ClassicConversationFilter(
            selected = filter,
            onSelected = { filter = it },
        )
        Spacer(Modifier.height(16.dp))
        if (!isConfigured || filteredItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ClassicEmptyHome(
                    isConfigured = isConfigured,
                    onNewChat = onNewChat,
                    onConfigureGateway = onConfigureGateway,
                )
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 0.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.07f),
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (c.isDark) c.surface.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.52f))
                    .border(0.8.dp, Color.White.copy(alpha = if (c.isDark) 0.10f else 0.58f), RoundedCornerShape(24.dp)),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 132.dp),
                ) {
                    itemsIndexed(
                        items = filteredItems,
                        key = { _, item ->
                            when (item) {
                                is ClassicConversationItem.Single -> "session:${item.id}"
                                is ClassicConversationItem.GroupChat -> "group:${item.id}"
                            }
                        },
                    ) { index, item ->
                        ClassicConversationRow(
                            item = item,
                            index = index,
                            selected = item is ClassicConversationItem.Single && item.id == currentSessionId,
                            onClick = {
                                when (item) {
                                    is ClassicConversationItem.Single -> onOpenSession(item.id)
                                    is ClassicConversationItem.GroupChat -> onOpenGroup(item.group)
                                }
                            },
                            showDivider = index < filteredItems.lastIndex,
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(118.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    if (c.isDark) c.surface.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.34f),
                                    if (c.isDark) c.bg.copy(alpha = 0.92f) else Color(0xFFF7F8F5).copy(alpha = 0.92f),
                                )
                            )
                        )
                )
            }
        }
    }
}

private sealed class ClassicConversationItem {
    abstract val id: String
    abstract val title: String
    abstract val preview: String
    abstract val updatedAt: Long

    data class Single(
        override val id: String,
        override val title: String,
        override val preview: String,
        override val updatedAt: Long,
        val session: SessionEntity,
    ) : ClassicConversationItem()

    data class GroupChat(
        override val id: String,
        override val title: String,
        override val preview: String,
        override val updatedAt: Long,
        val group: Group,
    ) : ClassicConversationItem()
}

@Composable
private fun ClassicNewChatPanel(
    onNewChat: () -> Unit,
    onOpenGroups: () -> Unit,
) {
    val c = LocalClawColors.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .classicAcrylicSurface(
                shape = shape,
                isDark = c.isDark,
                surfaceAlpha = 0.62f,
                shadowAlpha = 0.075f,
            )
            .padding(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onNewChat)
                    .padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .shadow(6.dp, RoundedCornerShape(14.dp), clip = false)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.34f)))
                        )
                        .border(0.8.dp, c.text.copy(alpha = 0.055f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(HtmlPlusIcon, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("新建会话", color = c.text, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("输入问题或任务", color = c.text.copy(alpha = 0.46f), fontSize = 11.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(30.dp)
                    .background(c.text.copy(alpha = 0.07f)),
            )
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .clickable(onClick = onOpenGroups),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(HtmlGroupIcon, contentDescription = null, tint = c.text.copy(alpha = 0.64f), modifier = Modifier.size(19.dp))
                Spacer(Modifier.height(3.dp))
                Text("群聊", color = c.text.copy(alpha = 0.64f), fontSize = 10.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClassicConversationFilter(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = if (c.isDark) 0.10f else 0.46f))
            .border(0.8.dp, c.text.copy(alpha = if (c.isDark) 0.14f else 0.045f), RoundedCornerShape(17.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("全部", "单聊", "群聊").forEach { item ->
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (selected == item) {
                            Brush.verticalGradient(
                                if (c.isDark) {
                                    listOf(Color.White.copy(alpha = 0.94f), Color.White.copy(alpha = 0.78f))
                                } else {
                                    listOf(Color(0xFF171716), Color(0xFF24231F))
                                }
                            )
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable { onSelected(item) }
                    .widthIn(min = 54.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item,
                    color = if (selected == item) {
                        if (c.isDark) Color(0xFF111111) else Color.White
                    } else {
                        c.text.copy(alpha = 0.48f)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ClassicConversationRow(
    item: ClassicConversationItem,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val c = LocalClawColors.current
    val avatarColors = classicConversationAvatarColors(index, c.isDark)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(avatarColors.first)
                .border(0.8.dp, avatarColors.second, RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.title.firstOrNull()?.uppercaseChar()?.toString() ?: "C",
                color = avatarColors.third,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatClassicSessionTime(item.updatedAt),
                    color = c.text.copy(alpha = 0.36f),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (selected) "当前打开的会话" else item.preview,
                color = c.text.copy(alpha = 0.48f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(
            color = c.border.copy(alpha = 0.45f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 70.dp, end = 0.dp),
        )
    }
}

private fun classicConversationAvatarColors(
    index: Int,
    dark: Boolean,
): Triple<Color, Color, Color> {
    if (dark) {
        return Triple(Color(0xFF242424), Color.White.copy(alpha = 0.10f), Color.White)
    }
    return when (index % 6) {
        0 -> Triple(Color(0xFF493E36), Color.White.copy(alpha = 0.74f), Color.White)
        1 -> Triple(Color(0xFFF4D1AD), Color(0xFFFFE8D2), Color(0xFF6B3F24))
        2 -> Triple(Color(0xFFECEBFF), Color(0xFFF7F6FF), Color(0xFF37407D))
        3 -> Triple(Color(0xFFEAF4EC), Color(0xFFF7FFF8), Color(0xFF4E675D))
        4 -> Triple(Color(0xFF3E3935), Color.White.copy(alpha = 0.70f), Color.White)
        else -> Triple(Color(0xFFEAF0FF), Color(0xFFF7F9FF), Color(0xFF2B335F))
    }
}

@Composable
private fun ClassicEmptyHome(
    isConfigured: Boolean,
    onNewChat: () -> Unit,
    onConfigureGateway: () -> Unit,
) {
    val c = LocalClawColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface.copy(alpha = 0.66f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = c.subtext, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "暂无会话",
            color = c.text,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(c.text)
                .clickable(onClick = if (isConfigured) onNewChat else onConfigureGateway)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isConfigured) "新建会话" else "去配置网关",
                color = c.bg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun formatClassicSessionTime(updatedAt: Long): String {
    val delta = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "刚刚"
        delta < hour -> "${delta / minute} 分钟前"
        delta < day -> "${delta / hour} 小时前"
        delta < 7 * day -> "${delta / day} 天前"
        else -> "更早"
    }
}

@Composable
fun ClassicMePage(
    userAvatarUri: String?,
    userName: String,
    sessionCount: Int,
    miniApps: List<MiniApp>,
    roleCount: Int,
    preferenceCount: Int,
    gatewayOnline: Boolean,
    onProfile: () -> Unit,
    onRoles: () -> Unit,
    onUserConfig: () -> Unit,
    onVpn: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onSkillMarket: () -> Unit,
    onConsole: () -> Unit,
    onGatewayConfig: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val c = LocalClawColors.current
    val displayName = userName.ifBlank { str(R.string.classic_default_user) }
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    val gatewayText = if (gatewayOnline) "网关在线" else "等待配置"
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(22.dp),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.035f),
                    spotColor = Color.Black.copy(alpha = 0.055f),
                )
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (c.isDark) 0.12f else 0.88f),
                            Color(0xFFFFF4E2).copy(alpha = if (c.isDark) 0.08f else 0.34f),
                            Color(0xFFF6F7F4).copy(alpha = if (c.isDark) 0.08f else 0.50f),
                        )
                    )
                )
                .border(0.8.dp, Color(0xFFE8C69A).copy(alpha = if (c.isDark) 0.12f else 0.36f), RoundedCornerShape(22.dp))
                .clickable(onClick = onProfile)
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF191C1A), Color(0xFF4E544D))))
                    .border(0.7.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = Color(0xFFF6F8F6), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    displayName,
                    color = c.text,
                    fontSize = 17.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$gatewayText · 默认空间 · 资料已同步",
                    color = c.text.copy(alpha = 0.46f),
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .height(27.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = if (c.isDark) 0.12f else 0.58f))
                    .clickable(onClick = onProfile)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("编辑", color = c.text.copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClassicMeMeter(label = "角色", value = roleCount.coerceAtLeast(0).toString(), modifier = Modifier.weight(1f))
            ClassicMeMeter(label = "空间", value = (miniApps.size + sessionCount).coerceAtLeast(0).toString(), modifier = Modifier.weight(1f))
            ClassicMeMeter(label = "偏好", value = preferenceCount.coerceAtLeast(0).toString(), modifier = Modifier.weight(1f))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            ClassicMeRoleCard(
                label = "角色管理",
                title = "Claw",
                subtitle = "默认角色 · 写作 · 生活助手",
                onClick = onRoles,
                modifier = Modifier.weight(1.24f),
            )
            ClassicMePortraitCard(
                label = "用户画像",
                title = "偏好与记忆",
                onClick = onProfile,
                modifier = Modifier.weight(0.76f),
            )
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ClassicMeTile("用户配置", "模型 · 输入 · 生成", onUserConfig, Modifier.weight(1f))
                ClassicMeTile("设置", "安全 · 通知 · 外观", onSettings, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ClassicMeTile("使用指南", "入门 · 问题", onHelp, Modifier.weight(1f))
                ClassicMeTile("网关配置", "连接 · 隐私 · 数据", onGatewayConfig, Modifier.weight(1f), quiet = true)
            }
        }

        ClassicMeUpdateCard(
            onCheckUpdate = onCheckUpdate,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ClassicMeServiceButton("Skill 市场", onSkillMarket, Modifier.weight(1f))
            ClassicMeServiceButton("控制台", onConsole, Modifier.weight(1f))
            ClassicMeServiceButton("VPN", onVpn, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ClassicMeMeter(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Column(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (c.isDark) 0.08f else 0.34f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = c.text.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(value, color = c.text.copy(alpha = 0.86f), fontSize = 15.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ClassicMeRoleCard(
    label: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF161917), Color(0xFF373F3A).copy(alpha = 0.92f))
                )
            )
            .border(0.7.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 14.dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = Color(0xFF56D6BA).copy(alpha = 0.32f),
                radius = 54.dp.toPx(),
                center = Offset(size.width * 0.82f, size.height * 0.08f),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(22.dp))
            Text(title, color = Color.White.copy(alpha = 0.96f), fontSize = 24.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.54f), fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ClassicMePortraitCard(
    label: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Column(
        modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = if (c.isDark) 0.10f else 0.66f),
                        Color(0xFFFFF1D9).copy(alpha = if (c.isDark) 0.06f else 0.30f),
                        Color(0xFFF4F6F3).copy(alpha = if (c.isDark) 0.08f else 0.36f),
                    )
                )
            )
            .border(0.8.dp, Color(0xFFE8C69A).copy(alpha = if (c.isDark) 0.10f else 0.34f), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 14.dp),
    ) {
        Text(label, color = c.text.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Text(title, color = c.text.copy(alpha = 0.86f), fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClassicMeUpdateCard(
    onCheckUpdate: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFFF7E8).copy(alpha = if (c.isDark) 0.08f else 0.76f),
                        Color(0xFFF3D6AC).copy(alpha = if (c.isDark) 0.06f else 0.34f),
                        Color.White.copy(alpha = if (c.isDark) 0.06f else 0.54f),
                    )
                )
            )
            .border(0.8.dp, Color(0xFFE2B56F).copy(alpha = if (c.isDark) 0.10f else 0.38f), RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                "检测更新",
                color = c.text.copy(alpha = 0.90f),
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "检查是否有可用的新版本",
                color = c.text.copy(alpha = 0.46f),
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ClassicMeMiniButton("检测", onCheckUpdate)
    }
}

@Composable
private fun ClassicMeMiniButton(
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (label == "检查") Color(0xFF171716) else Color.White.copy(alpha = 0.62f))
            .border(
                0.7.dp,
                if (label == "检查") Color.Black.copy(alpha = 0.08f) else Color(0xFFE2B56F).copy(alpha = 0.28f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (label == "检查") Color.White else c.text.copy(alpha = 0.70f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ClassicMeTile(
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
) {
    val c = LocalClawColors.current
    Column(
        modifier
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (quiet) {
                    Color(0xFFF7E7CF).copy(alpha = if (c.isDark) 0.08f else 0.36f)
                } else {
                    Color(0xFFFFF8EC).copy(alpha = if (c.isDark) 0.08f else 0.46f)
                }
            )
            .border(0.8.dp, Color(0xFFE2B56F).copy(alpha = if (c.isDark) 0.10f else 0.30f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
    ) {
        Text(label, color = c.text.copy(alpha = 0.86f), fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(subtitle, color = c.text.copy(alpha = 0.42f), fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClassicMeServiceButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFFFF3DE).copy(alpha = if (c.isDark) 0.08f else 0.42f))
            .border(0.7.dp, Color(0xFFE2B56F).copy(alpha = if (c.isDark) 0.08f else 0.24f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = c.text.copy(alpha = 0.60f),
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ClassicHubPage(
    miniApps: List<MiniApp>,
    aiPages: List<AiPageDef>,
    onOpenApp: (String) -> Unit,
    onOpenAiPage: (String) -> Unit,
    onOpenWorkspace: () -> Unit,
    onGenerateImage: () -> Unit,
    onGenerateVideo: () -> Unit,
) {
    var filter by remember { mutableStateOf("全部") }
    val items = remember(miniApps, aiPages) {
        buildList {
            miniApps.forEach { add(ClassicWorkspaceItem.MiniAppItem(it)) }
            aiPages.forEach { add(ClassicWorkspaceItem.NativePageItem(it)) }
        }.sortedByDescending { it.createdAt }
    }
    val filteredItems = remember(items, filter) {
        when (filter) {
            "MiniAPP" -> items.filterIsInstance<ClassicWorkspaceItem.MiniAppItem>()
            "Native" -> items.filterIsInstance<ClassicWorkspaceItem.NativePageItem>()
            else -> items
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 118.dp),
    ) {
        ClassicWorkspaceEntry(count = items.size, onClick = onOpenWorkspace)
        Spacer(Modifier.height(14.dp))
        ClassicWorkbenchGenerate(
            onGenerateImage = onGenerateImage,
            onGenerateVideo = onGenerateVideo,
        )
        Spacer(Modifier.height(22.dp))
        ClassicWorkbenchBar(count = items.size)
        Spacer(Modifier.height(10.dp))
        ClassicWorkbenchFilter(selected = filter, onSelected = { filter = it })
        Spacer(Modifier.height(12.dp))
        ClassicWorkspaceList(
            items = filteredItems,
            onOpenApp = onOpenApp,
            onOpenAiPage = onOpenAiPage,
        )
    }
}

@Composable
private fun ClassicWorkspaceEntry(
    count: Int,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.12f else 0.035f),
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.18f else 0.055f),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(if (c.isDark) Color(0xFF171716) else Color(0xFFFAF7EF))
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val paper = Color.White
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to paper.copy(alpha = if (c.isDark) 0.09f else 0.92f),
                        0.58f to Color(0xFFF5EFE5).copy(alpha = if (c.isDark) 0.07f else 0.72f),
                        1.00f to Color(0xFFECE3D7).copy(alpha = if (c.isDark) 0.05f else 0.55f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        paper.copy(alpha = if (c.isDark) 0.08f else 0.78f),
                        paper.copy(alpha = if (c.isDark) 0.03f else 0.22f),
                    )
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFD2975E).copy(alpha = if (c.isDark) 0.08f else 0.16f),
                        0.64f to Color(0xFFD2975E).copy(alpha = if (c.isDark) 0.03f else 0.05f),
                        1.00f to Color.Transparent,
                    ),
                    center = Offset(size.width, size.height),
                    radius = size.width * 0.54f,
                ),
                radius = size.width * 0.54f,
                center = Offset(size.width, size.height),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "我的工作空间",
                    color = c.text.copy(alpha = 0.94f),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$count 项内容",
                    color = c.text.copy(alpha = 0.45f),
                    fontSize = 11.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = if (c.isDark) 0.12f else 0.58f))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("进入", color = c.text.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ClassicWorkbenchGenerate(
    onGenerateImage: () -> Unit,
    onGenerateVideo: () -> Unit,
) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 116.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(30.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.14f else 0.045f),
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.20f else 0.085f),
            )
            .clip(RoundedCornerShape(30.dp))
            .background(if (c.isDark) Color(0xFF171716) else Color(0xFFF8F3EA))
            .border(0.7.dp, Color.White.copy(alpha = if (c.isDark) 0.14f else 0.62f), RoundedCornerShape(30.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val paper = Color.White
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFFCFAF5).copy(alpha = if (c.isDark) 0.08f else 0.92f),
                        0.54f to Color(0xFFF2E4D1).copy(alpha = if (c.isDark) 0.06f else 0.78f),
                        1.00f to Color(0xFFF2F8F3).copy(alpha = if (c.isDark) 0.05f else 0.78f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFE8C69A).copy(alpha = if (c.isDark) 0.12f else 0.42f),
                        0.58f to Color(0xFFE8C69A).copy(alpha = if (c.isDark) 0.04f else 0.13f),
                        1.00f to Color.Transparent,
                    ),
                    center = Offset(size.width, 0f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width, 0f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0xFFCFE2EA).copy(alpha = if (c.isDark) 0.10f else 0.36f),
                        0.64f to Color(0xFFCFE2EA).copy(alpha = if (c.isDark) 0.03f else 0.10f),
                        1.00f to Color.Transparent,
                    ),
                    center = Offset(0f, size.height * 0.10f),
                    radius = size.width * 0.54f,
                ),
                radius = size.width * 0.54f,
                center = Offset(0f, size.height * 0.10f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFDDEADE).copy(alpha = if (c.isDark) 0.06f else 0.24f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.22f, size.height * 1.22f),
                    radius = size.width * 0.78f,
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.22f, size.height * 1.22f),
            )
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to paper.copy(alpha = if (c.isDark) 0.10f else 0.50f),
                        0.38f to Color.White.copy(alpha = 0f),
                        1.00f to paper.copy(alpha = if (c.isDark) 0.05f else 0.20f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height * 0.86f),
                )
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                "生成",
                color = c.text.copy(alpha = 0.44f),
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "把想法变成素材",
                color = c.text.copy(alpha = 0.90f),
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClassicGenerateAction(
                    label = "生图片",
                    icon = HtmlImageIcon,
                    onClick = onGenerateImage,
                    highlighted = true,
                    modifier = Modifier.weight(1f),
                )
                ClassicGenerateAction(
                    label = "生视频",
                    icon = HtmlVideoIcon,
                    onClick = onGenerateVideo,
                    highlighted = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ClassicGenerateAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(
                if (highlighted) {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.76f), Color(0xFFFFFAF2).copy(alpha = 0.54f)))
                } else {
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.58f), Color.White.copy(alpha = 0.42f)))
                }
            )
            .border(0.6.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(19.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.text.copy(alpha = 0.86f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c.text.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ClassicWorkbenchBar(count: Int) {
    val c = LocalClawColors.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            "空间内容",
            color = c.text,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "MiniAPP 与 Native 页面 · $count",
            color = c.text.copy(alpha = 0.42f),
            fontSize = 11.5.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ClassicWorkbenchFilter(
    selected: String,
    onSelected: (String) -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = if (c.isDark) 0.10f else 0.38f))
            .border(0.6.dp, Color.White.copy(alpha = if (c.isDark) 0.14f else 0.58f), RoundedCornerShape(18.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("全部", "MiniAPP", "Native").forEach { item ->
            Box(
                Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (selected == item) {
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.86f), Color.White.copy(alpha = 0.62f)))
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .clickable { onSelected(item) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item,
                    color = if (selected == item) c.text else c.text.copy(alpha = 0.48f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ClassicWorkspaceList(
    items: List<ClassicWorkspaceItem>,
    onOpenApp: (String) -> Unit,
    onOpenAiPage: (String) -> Unit,
) {
    val c = LocalClawColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(26.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.035f),
                spotColor = Color.Black.copy(alpha = 0.06f),
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (c.isDark) 0.10f else 0.70f), Color.White.copy(alpha = if (c.isDark) 0.06f else 0.42f))
                )
            )
            .border(0.7.dp, Color.White.copy(alpha = if (c.isDark) 0.12f else 0.68f), RoundedCornerShape(26.dp))
            .padding(vertical = 5.dp),
    ) {
        if (items.isEmpty()) {
            Text(
                "暂无内容",
                color = c.text.copy(alpha = 0.42f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp),
            )
        } else {
            items.forEachIndexed { index, item ->
                ClassicWorkspaceRow(
                    item = item,
                    index = index,
                    showDivider = index < items.lastIndex,
                    onClick = {
                        when (item) {
                            is ClassicWorkspaceItem.MiniAppItem -> onOpenApp(item.app.id)
                            is ClassicWorkspaceItem.NativePageItem -> onOpenAiPage(item.page.id)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ClassicWorkspaceRow(
    item: ClassicWorkspaceItem,
    index: Int,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalClawColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = classicWorkspaceIconColors(index, item)
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.first)
                .border(0.7.dp, Color.White.copy(alpha = 0.48f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.kind.first().toString(),
                color = colors.second,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    color = c.text,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    item.kind,
                    color = c.text.copy(alpha = 0.36f),
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                item.subtitle,
                color = c.text.copy(alpha = 0.48f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.text.copy(alpha = 0.32f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(
            color = c.text.copy(alpha = 0.058f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 67.dp),
        )
    }
}

private fun classicWorkspaceIconColors(index: Int, item: ClassicWorkspaceItem): Pair<Color, Color> {
    val kind = item.kind
    return when {
        kind == "MiniAPP" && index % 3 == 0 -> Color(0xFFF3D1B6).copy(alpha = 0.78f) to Color(0xFF87553E)
        kind == "MiniAPP" -> Color(0xFFDCECDF).copy(alpha = 0.82f) to Color(0xFF376651)
        index % 3 == 1 -> Color(0xFFE1E2EC).copy(alpha = 0.84f) to Color(0xFF4E5366)
        else -> Color.White.copy(alpha = 0.72f) to Color(0xFF171716).copy(alpha = 0.70f)
    }
}

private sealed class ClassicWorkspaceItem {
    abstract val title: String
    abstract val subtitle: String
    abstract val kind: String
    abstract val createdAt: Long

    data class MiniAppItem(val app: MiniApp) : ClassicWorkspaceItem() {
        override val title: String = app.title.ifBlank { "未命名 MiniAPP" }
        override val subtitle: String = classicWorkspaceSubtitle(
            primary = app.description.ifBlank { app.spec.goal }.ifBlank { "MiniAPP" },
            createdAt = app.createdAt,
        )
        override val kind: String = "MiniAPP"
        override val createdAt: Long = app.createdAt
    }

    data class NativePageItem(val page: AiPageDef) : ClassicWorkspaceItem() {
        override val title: String = page.title.ifBlank { "未命名页面" }
        override val subtitle: String = classicWorkspaceSubtitle(
            primary = page.description.ifBlank { page.spec.goal }.ifBlank { "Native Page" },
            createdAt = page.createdAt,
        )
        override val kind: String = "Native"
        override val createdAt: Long = page.createdAt
    }
}

private fun classicWorkspaceSubtitle(primary: String, createdAt: Long): String =
    "${primary.take(36)} · ${formatClassicSessionTime(createdAt)}"

@Composable
private fun ClassicPrimaryAction(
    row: ClassicHubRow,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF080808))
            .border(0.7.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
            .clickable(onClick = row.onClick)
            .padding(18.dp),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = accent.copy(alpha = 0.14f),
                radius = size.minDimension * 0.58f,
                center = Offset(size.width * 0.88f, size.height * 0.16f),
            )
            drawLine(
                color = accent,
                start = Offset(size.width - 76f, size.height - 25f),
                end = Offset(size.width - 26f, size.height - 25f),
                strokeWidth = 4f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(18f, size.height - 18f),
                end = Offset(size.width * 0.55f, size.height - 18f),
                strokeWidth = 1.2f,
            )
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(row.icon, contentDescription = null, tint = Color(0xFF080808), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    row.title,
                    color = Color.White,
                    fontSize = 19.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.subtitle,
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 230.dp),
                )
            }
            Box(
                Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(15.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("进入", color = Color.White.copy(alpha = 0.88f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ClassicFeatureTile(
    row: ClassicHubRow,
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalClawColors.current
    val bg = c.surface
    val fg = c.text
    val sub = c.subtext.copy(alpha = 0.86f)
    Box(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(
                0.7.dp,
                if (active) accent.copy(alpha = 0.62f) else c.border.copy(alpha = 0.72f),
                RoundedCornerShape(22.dp),
            )
            .clickable(onClick = row.onClick)
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (active) accent else Color(0xFFF3F3F1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        row.icon,
                        contentDescription = null,
                        tint = Color(0xFF080808),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    row.section.ifBlank { "入口" },
                    color = if (active) c.text else c.subtext,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    row.title,
                    color = fg,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.subtitle,
                    color = sub,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

data class ClassicHubRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val section: String = "",
    val onClick: () -> Unit,
)

@Composable
private fun ClassicMeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
    accent: Color = Color(0xFFC7F43A),
    highlighted: Boolean = false,
) {
    val c = LocalClawColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        highlighted -> accent.copy(alpha = if (c.isDark) 0.22f else 0.32f)
                        c.isDark -> c.cardAlt
                        else -> Color(0xFFF3F3F1)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) Color(0xFF121212) else c.text.copy(alpha = 0.78f),
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = c.text, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = c.subtext.copy(alpha = 0.82f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (highlighted) {
            Box(
                Modifier
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("常用", color = Color(0xFF050505), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(7.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.subtext.copy(alpha = 0.58f), modifier = Modifier.size(17.dp))
    }
    if (showDivider) {
        HorizontalDivider(color = c.border.copy(alpha = 0.62f), thickness = 0.5.dp, modifier = Modifier.padding(start = 66.dp, end = 14.dp))
    }
}
