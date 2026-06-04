package com.mobileclaw.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mobileclaw.R
import com.mobileclaw.app.MiniApp
import com.mobileclaw.memory.db.SessionEntity
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.str

enum class ClassicTab { CHAT, WORKSPACE, AGENTS, TOOLS, ME }

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
    Column(Modifier.fillMaxSize().background(c.bg)) {
        ClassicChromeTop(
            title = title,
            tabs = tabs,
            onTab = onTab,
            leadingAction = leadingAction,
            trailingAction = trailingAction,
        )
        Box(Modifier.weight(1f)) { content() }
        ClassicBottomBar(selected = selected, onSelect = onSelect)
    }
}

private data class ClassicTabItem(val tab: ClassicTab, val icon: ImageVector, val label: String)

@Composable
private fun classicTabs() = listOf(
    ClassicTabItem(ClassicTab.CHAT, Icons.Filled.ChatBubbleOutline, str(R.string.home_859362)),
    ClassicTabItem(ClassicTab.WORKSPACE, Icons.Filled.Apps, str(R.string.classic_workspace)),
    ClassicTabItem(ClassicTab.AGENTS, Icons.Filled.Psychology, str(R.string.classic_agents)),
    ClassicTabItem(ClassicTab.TOOLS, Icons.Filled.Extension, str(R.string.classic_tools)),
    ClassicTabItem(ClassicTab.ME, Icons.Filled.Person, str(R.string.classic_me)),
)

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
            .background(c.surface)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (tabs.isEmpty()) {
            Text(
                title,
                color = c.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
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
        HorizontalDivider(color = c.border, thickness = 0.5.dp)
    }
}

private fun String.stripClassicTabEmoji(): String =
    replace(Regex("^[\\p{So}\\p{Sk}]+\\s*"), "").trim()

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
private fun ClassicBottomBar(selected: ClassicTab, onSelect: (ClassicTab) -> Unit) {
    val c = LocalClawColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = c.border.copy(alpha = 0.66f), thickness = 0.5.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 2.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                classicTabs().forEach { item ->
                    val active = selected == item.tab
                    ClassicDockItem(
                        item = item,
                        active = active,
                        onClick = { onSelect(item.tab) },
                        modifier = Modifier.weight(1f),
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
    Column(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(if (active) c.text.copy(alpha = if (c.isDark) 0.14f else 0.06f) else Color.Transparent)
            .padding(top = 5.dp, bottom = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (active) c.text else c.subtext.copy(alpha = 0.72f),
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            item.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            fontSize = if (item.label.length > 6) 9.8.sp else 10.8.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            color = if (active) c.text else c.subtext.copy(alpha = 0.78f),
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        )
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
fun ClassicMePage(
    userAvatarUri: String?,
    userName: String,
    sessionCount: Int,
    miniApps: List<MiniApp>,
    onProfile: () -> Unit,
    onVpn: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
) {
    val c = LocalClawColors.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(50.dp).clip(CircleShape).background(c.cardAlt).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = c.text, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(userName.ifBlank { str(R.string.classic_default_user) }, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(str(R.string.classic_user_stats, sessionCount, miniApps.size), color = c.subtext, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ClassicMeRow(Icons.Filled.Settings, str(R.string.drawer_settings), str(R.string.settings_ce650e), onSettings)
        ClassicMeRow(Icons.Filled.Person, str(R.string.drawer_profile), str(R.string.profile_b6c018), onProfile)
        ClassicMeRow(Icons.Filled.Terminal, str(R.string.help_9a2407), str(R.string.settings_help_entry_subtitle), onHelp)
        ClassicMeRow(Icons.Filled.Shield, "VPN", str(R.string.classic_vpn_desc), onVpn)
    }
}

@Composable
fun ClassicHubPage(
    title: String,
    subtitle: String,
    rows: List<ClassicHubRow>,
) {
    val c = LocalClawColors.current
    val accent = Color(0xFFC7F43A)
    val primaryRow = rows.firstOrNull()
    val featuredRows = rows.drop(1).take(2)
    val secondaryRows = rows.drop(3)
    Column(
        Modifier
            .fillMaxSize()
            .background(if (c.isDark) c.bg else Color(0xFFF6F6F4))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 20.dp),
    ) {
        if (primaryRow != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "现在可以做",
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
            )
            ClassicPrimaryAction(
                row = primaryRow,
                accent = accent,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        if (featuredRows.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                featuredRows.forEachIndexed { index, row ->
                    ClassicFeatureTile(
                        row = row,
                        accent = accent,
                        active = index == 0,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (featuredRows.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(20.dp))
        secondaryRows.groupBy { it.section }.forEach { (section, sectionRows) ->
            Row(
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    section.ifBlank { "常用" },
                    color = c.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${sectionRows.size}",
                    color = c.subtext.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.surface)
                    .border(0.6.dp, c.border.copy(alpha = 0.72f), RoundedCornerShape(22.dp)),
            ) {
                sectionRows.forEachIndexed { index, row ->
                    ClassicMeRow(
                        icon = row.icon,
                        title = row.title,
                        subtitle = row.subtitle,
                        onClick = row.onClick,
                        showDivider = index < sectionRows.lastIndex,
                        accent = accent,
                        highlighted = false,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

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
