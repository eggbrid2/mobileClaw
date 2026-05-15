package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.ScreenSearchDesktop
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun clawIconForPage(page: AppPage): ImageVector = when (page) {
    AppPage.HOME -> Icons.Outlined.Home
    AppPage.CHAT -> Icons.Outlined.ChatBubbleOutline
    AppPage.SETTINGS -> Icons.Outlined.Settings
    AppPage.SKILLS -> Icons.Outlined.Extension
    AppPage.SKILL_MARKET -> Icons.Outlined.Storefront
    AppPage.PROFILE -> Icons.Outlined.Psychology
    AppPage.ROLES, AppPage.ROLE_EDIT -> Icons.Outlined.TheaterComedy
    AppPage.USER_CONFIG -> Icons.Outlined.Person
    AppPage.APPS -> Icons.Outlined.Apps
    AppPage.CONSOLE -> Icons.Outlined.Terminal
    AppPage.HELP -> Icons.Outlined.HelpOutline
    AppPage.GROUPS, AppPage.GROUP_CHAT -> Icons.Outlined.Group
    AppPage.BROWSER -> Icons.Outlined.Web
    AppPage.AI_PAGES -> Icons.Outlined.Article
    AppPage.VPN -> Icons.Outlined.Lock
}

fun clawIconForSymbol(symbol: String?): ImageVector {
    val value = symbol?.trim().orEmpty().lowercase()
    return when {
        value.isBlank() -> Icons.Filled.SmartToy
        value in setOf("💬", "chat", "message", "聊天") -> Icons.Outlined.ChatBubbleOutline
        value in setOf("🧬", "profile", "memory", "画像") -> Icons.Outlined.Psychology
        value in setOf("physio", "cognitive") -> Icons.Outlined.Psychology
        value in setOf("emotional") -> Icons.Outlined.Palette
        value in setOf("social") -> Icons.Outlined.Language
        value in setOf("values") -> Icons.Outlined.Security
        value in setOf("capability") -> Icons.Outlined.BatterySaver
        value in setOf("spiritual") -> Icons.Outlined.AutoFixHigh
        value in setOf("👥", "group", "groups", "群聊", "成员") -> Icons.Outlined.Group
        value in setOf("🎭", "role", "roles", "角色") -> Icons.Outlined.TheaterComedy
        value in setOf("📱", "📲", "phone", "mobile", "app", "apps", "手机", "应用") -> Icons.Outlined.PhoneAndroid
        value in setOf("🛠", "🛠️", "tool", "tools", "skill", "skills", "技能") -> Icons.Outlined.Build
        value in setOf("🏪", "market", "store", "市场") -> Icons.Outlined.Storefront
        value in setOf("📄", "page", "document", "file", "native_page", "页面", "文件") -> Icons.Outlined.Description
        value in setOf("⚙", "⚙️", "settings", "config", "设置") -> Icons.Outlined.Settings
        value in setOf("🖥", "🖥️", "desktop", "console", "screen", "桌面", "控制台") -> Icons.Outlined.DesktopWindows
        value in setOf("👤", "user", "person", "mine", "我的") -> Icons.Outlined.Person
        value in setOf("🔒", "lock", "vpn", "security", "安全") -> Icons.Outlined.Lock
        value in setOf("🔌", "gateway", "plug", "model", "模型") -> Icons.Outlined.PowerSettingsNew
        value in setOf("🎨", "palette", "appearance", "theme", "主题", "外观") -> Icons.Outlined.Palette
        value in setOf("🔐", "permission", "permissions", "权限") -> Icons.Outlined.Security
        value in setOf("🧹", "cache", "clean", "缓存") -> Icons.Outlined.CleaningServices
        value in setOf("❔", "?", "help", "帮助") -> Icons.Outlined.HelpOutline
        value in setOf("♿", "accessibility") -> Icons.Outlined.Accessibility
        value in setOf("🪟", "overlay", "window", "悬浮窗") -> Icons.Outlined.ScreenSearchDesktop
        value in setOf("⚡", "battery", "background", "power") -> Icons.Outlined.BatterySaver
        value in setOf("🔔", "notification", "通知") -> Icons.Outlined.Notifications
        value in setOf("🚀", "launch", "autostart", "启动") -> Icons.Outlined.RocketLaunch
        value in setOf("📥", "download", "downloads") -> Icons.Outlined.Download
        value in setOf("📁", "folder", "directory") -> Icons.Outlined.Folder
        value in setOf("🖼", "🖼️", "image", "picture", "pictures") -> Icons.Outlined.Image
        value in setOf("📸", "📷", "camera", "screenshot") -> Icons.Outlined.CameraAlt
        value in setOf("🎵", "music", "audio") -> Icons.Outlined.MusicNote
        value in setOf("🎬", "movie", "video") -> Icons.Outlined.Movie
        value in setOf("🐍", "python") -> Icons.Outlined.Code
        value in setOf("🕐", "time", "clock") -> Icons.Outlined.AutoFixHigh
        value in setOf("🔍", "search") -> Icons.Outlined.Search
        value in setOf("🌐", "web", "browser", "url") -> Icons.Outlined.Language
        value in setOf("weather", "天气") -> Icons.Outlined.Web
        value in setOf("🔗", "link") -> Icons.Outlined.Link
        value in setOf("👁", "eye", "visible") -> Icons.Outlined.Visibility
        value in setOf("✅", "check", "done") -> Icons.Outlined.CheckCircle
        value in setOf("🎮", "game") -> Icons.Outlined.SportsEsports
        value in setOf("📦", "package") -> Icons.Outlined.ShoppingBag
        else -> Icons.Outlined.InsertDriveFile
    }
}

@Composable
fun ClawSymbolIcon(
    symbol: String?,
    contentDescription: String? = null,
    tint: Color = LocalClawColors.current.text,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = clawIconForSymbol(symbol),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}

@Composable
fun ClawIconTile(
    symbol: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = LocalClawColors.current.text,
    background: Color = LocalClawColors.current.cardAlt,
    border: Color = LocalClawColors.current.border,
    cornerRadius: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .background(background, shape)
            .border(1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        ClawSymbolIcon(symbol = symbol, tint = tint, modifier = Modifier.size(iconSize))
    }
}
