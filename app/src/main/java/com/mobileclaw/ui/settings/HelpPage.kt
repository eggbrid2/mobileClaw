package com.mobileclaw.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.mobileclaw.R
import com.mobileclaw.ui.ClawSymbolIcon
import com.mobileclaw.ui.LocalClawColors
import com.mobileclaw.str

private data class HelpSection(val iconKey: String, val title: String, val items: List<HelpItem>)
private data class HelpItem(val title: String, val body: String, val example: String? = null)

private val HELP_CONTENT = listOf(
    HelpSection("launch", str(R.string.help_c182e7), listOf(
        HelpItem(
            str(R.string.help_7820e3),
            str(R.string.help_tap),
            "Base URL: https://api.openai.com/v1\nAPI Key: sk-xxxxxx\nModel: gpt-4o",
        ),
        HelpItem(
            str(R.string.help_943e0c),
            str(R.string.help_2bd8c8),
            str(R.string.help_901a9c),
        ),
        HelpItem(
            str(R.string.help_44ae5c),
            str(R.string.help_3446ad),
        ),
    )),
    HelpSection("chat", str(R.string.help_70d230), listOf(
        HelpItem(
            str(R.string.help_2d3f31),
            str(R.string.help_30faf3),
            str(R.string.help_8888c3),
        ),
        HelpItem(
            str(R.string.help_send),
            str(R.string.help_tap_2),
            str(R.string.help_61a41d),
        ),
        HelpItem(
            str(R.string.help_1d1ded),
            str(R.string.help_d22a07),
            str(R.string.help_87ec05),
        ),
        HelpItem(
            str(R.string.help_stop),
            str(R.string.help_da967e),
        ),
    )),
    HelpSection("code", "Codex 电脑模式", listOf(
        HelpItem(
            "用途",
            "打开聊天页顶部的 Codex 开关后，当前会话会直接连接电脑上的 Codex CLI。你在手机里说什么，就原样发给 Codex；Codex 的执行进度和结果会回到当前聊天。",
            "例：帮我修复当前项目的编译错误",
        ),
        HelpItem(
            "电脑端准备",
            "电脑需要先安装并登录 Codex CLI，然后启动 MobileClaw Codex bridge。建议通过同一局域网或 Tailscale 访问，并设置一个长随机 token。",
            "CODEX_BRIDGE_TOKEN=长随机token CODEX_BRIDGE_HOST=0.0.0.0 CODEX_BRIDGE_PORT=52734 python3 scripts/codex_desktop_bridge.py",
        ),
        HelpItem(
            "手机端配置",
            "在用户配置中填写电脑桥地址、token 和项目目录。配置后回到聊天页打开 Codex 开关即可使用。",
            "codex_desktop_endpoint = http://电脑IP:52734\ncodex_desktop_token = 长随机token\ncodex_desktop_cwd = /电脑上的项目路径",
        ),
        HelpItem(
            "上下文与输出",
            "同一个 MobileClaw 会话会绑定同一个 Codex thread，后续消息会继续同一上下文。工作步骤会实时显示，最终正文会按块渐进展示。",
        ),
    )),
    HelpSection("profile", str(R.string.help_19122e), listOf(
        HelpItem(
            str(R.string.help_0178ef),
            str(R.string.help_84c678),
        ),
        HelpItem(
            str(R.string.help_624141),
            str(R.string.help_2899e9),
        ),
        HelpItem(
            str(R.string.help_0cb089),
            str(R.string.help_385c19),
        ),
    )),
    HelpSection("download", "发布与更新", listOf(
        HelpItem(
            "蒲公英配置",
            "MobileClaw 内置 pgyer_release 工具，可检查蒲公英新版本并下载 APK。先在用户配置中保存蒲公英 API Key 和 App Key。",
            "pgyer_api_key = 蒲公英 API Key\npgyer_app_key = 蒲公英 App Key\npgyer_install_password = 安装密码，可选",
        ),
        HelpItem(
            "检查更新",
            "在聊天中直接说“检查蒲公英更新”，Agent 会调用 pgyer_release 检查当前版本和蒲公英最新版本。",
            "检查蒲公英更新",
        ),
        HelpItem(
            "下载新版",
            "在聊天中说“下载蒲公英最新版本”，MobileClaw 会通过系统下载管理器把 APK 下载到 Downloads。",
            "下载蒲公英最新版本",
        ),
        HelpItem(
            "版本规则",
            "App 版本跟随 Git：versionName 来自 git describe，versionCode 来自 git commit 数。工作区有未提交改动时，版本名会带 dirty。",
            "例：v0.3.7-dirty / 46",
        ),
    )),
    HelpSection("roles", str(R.string.help_2a2735), listOf(
        HelpItem(
            str(R.string.chat_a6df2e),
            str(R.string.help_ed9064),
            str(R.string.help_select),
        ),
        HelpItem(
            str(R.string.chat_756af3),
            str(R.string.help_tap_3),
            str(R.string.help_a8e691),
        ),
        HelpItem(
            str(R.string.help_fddab2),
            str(R.string.help_a442fd),
        ),
    )),
    HelpSection("battery", str(R.string.help_835192), listOf(
        HelpItem(
            str(R.string.help_ed18e0),
            str(R.string.help_61bc22),
        ),
        HelpItem(
            str(R.string.help_6a7881),
            str(R.string.help_68f220),
            str(R.string.help_e18fd5),
        ),
        HelpItem(
            str(R.string.help_0324e2),
            str(R.string.help_9c3d11),
            str(R.string.help_94fec3),
        ),
        HelpItem(
            str(R.string.drawer_console),
            str(R.string.help_f3fb9b),
        ),
    )),
    HelpSection("user", str(R.string.help_27e2d7), listOf(
        HelpItem(
            str(R.string.drawer_profile),
            str(R.string.help_4e2d9f),
        ),
        HelpItem(
            str(R.string.drawer_user_config),
            str(R.string.help_fdb115),
            str(R.string.help_settings),
        ),
    )),
    HelpSection("settings", str(R.string.help_362e39), listOf(
        HelpItem(
            str(R.string.help_7ba6ff),
            str(R.string.help_826c2c),
            str(R.string.help_799b6b),
        ),
        HelpItem(
            str(R.string.help_9e7e1f),
            str(R.string.help_9d7dd6),
        ),
        HelpItem(
            str(R.string.help_fdb21c),
            str(R.string.help_b0bddd),
            str(R.string.help_290d2a),
        ),
    )),
)

@Composable
fun HelpPage(onBack: () -> Unit) {
    val c = LocalClawColors.current
    var expandedSection by remember { mutableStateOf<String?>(HELP_CONTENT.firstOrNull()?.title) }

    Column(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding(),
    ) {
        // TopBar
        Column(Modifier.fillMaxWidth().background(c.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
                }
                Text(
                    stringResource(R.string.help_9a2407),
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item {
                // Intro banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.accent.copy(alpha = 0.08f))
                        .border(1.dp, c.accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Text(stringResource(R.string.help_560aac), color = c.text, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.help_df421b), color = c.subtext, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            HELP_CONTENT.forEach { section ->
                item(key = section.title) {
                    val isExpanded = expandedSection == section.title
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.card)
                            .border(1.dp, if (isExpanded) c.accent.copy(alpha = 0.3f) else c.border, RoundedCornerShape(12.dp)),
                    ) {
                        // Section header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedSection = if (isExpanded) null else section.title }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ClawSymbolIcon(section.iconKey, tint = if (isExpanded) c.accent else c.text, modifier = Modifier.size(17.dp))
                            Text(
                                section.title,
                                color = if (isExpanded) c.accent else c.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (isExpanded) "▲" else "▼", color = c.subtext.copy(alpha = 0.5f), fontSize = 9.sp)
                        }

                        if (isExpanded) {
                            HorizontalDivider(color = c.border, thickness = 0.5.dp)
                            section.items.forEachIndexed { idx, item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(item.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.body, color = c.subtext, fontSize = 12.sp, lineHeight = 18.sp)
                                    if (item.example != null) {
                                        Spacer(Modifier.height(6.dp))
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(c.surface)
                                                .border(0.5.dp, c.border, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                        ) {
                                            item.example.lines().forEach { line ->
                                                Text(line, color = c.accent.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.sp)
                                            }
                                        }
                                    }
                                }
                                if (idx < section.items.size - 1) {
                                    HorizontalDivider(color = c.border.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 14.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.help_dc024f),
                    color = c.subtext.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
