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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

enum class ClassicTab { CHAT, SKILL, CENTER, ROLES, ME }
enum class ClassicChatTab { SINGLE, GROUP }
enum class ClassicSkillTab { LOCAL, MARKET }
enum class ClassicCenterTab { MINI_APP, AI_PAGE }

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
    ClassicTabItem(ClassicTab.SKILL, Icons.Filled.Extension, str(R.string.drawer_skills)),
    ClassicTabItem(ClassicTab.CENTER, Icons.Filled.Apps, str(R.string.classic_center)),
    ClassicTabItem(ClassicTab.ROLES, Icons.Filled.Psychology, str(R.string.drawer_roles)),
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
fun ClassicAddGroupAction(onClick: () -> Unit) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(c.text)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = str(R.string.group_new_title), tint = c.bg, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ClassicBottomBar(selected: ClassicTab, onSelect: (ClassicTab) -> Unit) {
    val c = LocalClawColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (c.isDark) c.bg else Color(0xFFF7F7F4))
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(30.dp))
                    .background(c.surface)
                    .border(0.5.dp, c.border.copy(alpha = 0.72f), RoundedCornerShape(30.dp)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                classicTabs().forEach { item ->
                    val active = selected == item.tab
                    val isCenter = item.tab == ClassicTab.CENTER
                    if (isCenter) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        ClassicDockItem(
                            item = item,
                            active = active,
                            onClick = { onSelect(item.tab) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            ClassicCenterDockItem(
                item = classicTabs().first { it.tab == ClassicTab.CENTER },
                active = selected == ClassicTab.CENTER,
                onClick = { onSelect(ClassicTab.CENTER) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
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
    val neutralActive = if (c.isDark) Color(0xFF1B1B1B) else Color(0xFFF3F3EF)
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(if (active) neutralActive else Color.Transparent)
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) c.text else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = if (active) c.bg else c.subtext,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            fontSize = if (item.label.length > 6) 9.8.sp else 10.8.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            color = if (active) c.text else c.subtext,
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
    onConsole: () -> Unit,
    onVpn: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = LocalClawColors.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(50.dp).clip(CircleShape).background(c.cardAlt).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
                    Text(userAvatarUri?.takeIf { it.isNotBlank() }?.let { "🙂" } ?: "👤", fontSize = 24.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(userName.ifBlank { str(R.string.classic_default_user) }, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(str(R.string.classic_user_stats, sessionCount, miniApps.size), color = c.subtext, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ClassicMeRow(Icons.Filled.Person, str(R.string.drawer_profile), str(R.string.profile_b6c018), onProfile)
        ClassicMeRow(Icons.Filled.Terminal, str(R.string.drawer_console), str(R.string.classic_console_desc), onConsole)
        ClassicMeRow(Icons.Filled.Shield, "VPN", str(R.string.classic_vpn_desc), onVpn)
        ClassicMeRow(Icons.Filled.Settings, str(R.string.drawer_settings), str(R.string.settings_ce650e), onSettings)
    }
}

@Composable
private fun ClassicMeRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalClawColors.current
    Row(
        Modifier.fillMaxWidth().background(c.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.cardAlt), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.text, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = c.subtext, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.subtext, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = c.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 68.dp))
}
