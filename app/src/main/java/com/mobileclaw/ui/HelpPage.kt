package com.mobileclaw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

private data class HelpSection(val emoji: String, val title: String, val items: List<HelpItem>)
private data class HelpItem(val title: String, val body: String, val example: String? = null)

private val HELP_CONTENT = listOf(
    HelpSection("🚀", "快速开始", listOf(
        HelpItem(
            "第一步：配置 API",
            "点击左上角抽屉菜单 → ⚙️ 设置，填入 API Base URL 和 API Key（支持 OpenAI、DeepSeek 等兼容接口）。",
            "Base URL: https://api.openai.com/v1\nAPI Key: sk-xxxxxx\nModel: gpt-4o",
        ),
        HelpItem(
            "第二步：发送第一条任务",
            "在底部输入框输入你想完成的任务，按发送。MobileClaw 会自动分析并执行。",
            "例：帮我搜索今天的科技新闻摘要",
        ),
        HelpItem(
            "第三步：授权必要权限",
            "首次使用需授权无障碍、悬浮窗权限，用于界面交互和屏幕截图。按照权限引导页操作即可。",
        ),
    )),
    HelpSection("💬", "对话与任务", listOf(
        HelpItem(
            "直接描述任务",
            "无需特殊命令，用自然语言说清楚你想做什么。任务越具体，Agent 执行越精准。",
            "帮我把剪贴板里的内容翻译成英文并发给我",
        ),
        HelpItem(
            "发送图片",
            "点击输入框左边的 📎 按钮附加图片，Agent 可以分析图片内容并执行相关操作。",
            "附图后说：识别图中的文字",
        ),
        HelpItem(
            "多轮对话",
            "Agent 会记住同一会话中的上下文。可以追问、补充信息或修改要求。",
            "刚才那篇文章，帮我再简短一些",
        ),
        HelpItem(
            "停止任务",
            "任务进行中点击 ⬛ 停止按钮即可中断。已完成的步骤会保留在对话中。",
        ),
    )),
    HelpSection("🧠", "理解执行过程", listOf(
        HelpItem(
            "思考过程",
            "Agent 执行前会先思考。点击「💭 AI 推理」可展开查看推理内容，了解 Agent 为什么这样做。",
        ),
        HelpItem(
            "执行步骤",
            "每次 Agent 调用工具会产生一条步骤记录。点击步骤可展开查看详细输入输出。",
        ),
        HelpItem(
            "观察结果",
            "每步执行后 Agent 会观察结果。若有截图，会显示屏幕内容缩略图，点击可展开查看。",
        ),
    )),
    HelpSection("🎭", "角色与模型", listOf(
        HelpItem(
            "切换角色",
            "在抽屉菜单 → 🎭 角色中选择预设角色。不同角色会自动加载对应技能并调整 AI 行为。",
            "选择「🌐 网络助手」后，Agent 会优先使用网页浏览工具",
        ),
        HelpItem(
            "切换模型",
            "点击顶部模型名称芯片可快速切换模型。或者直接告诉 Agent：「请用 DeepSeek 帮我完成这个任务」",
            "支持：gpt-4o / gpt-4o-mini / deepseek-chat / deepseek-reasoner 等",
        ),
        HelpItem(
            "自定义角色",
            "在角色页面点击「+」新建角色，可设置专属系统提示词、强制加载的技能和模型偏好。",
        ),
    )),
    HelpSection("⚡", "技能与扩展", listOf(
        HelpItem(
            "内置技能",
            "Agent 默认拥有截图、网页搜索、界面交互、文件操作、Shell 命令等核心技能。",
        ),
        HelpItem(
            "动态技能（Skill）",
            "在 ⚡ 技能页面可以查看所有注册技能。Agent 可以通过 meta 工具自动创建新技能。",
            "告诉我你自己创建一个查询天气的技能",
        ),
        HelpItem(
            "Mini 应用",
            "在 📱 应用页面查看 Agent 创建的 HTML 小应用，支持 Python 后端和 SQLite 存储。",
            "帮我创建一个记账小应用",
        ),
        HelpItem(
            "控制台",
            "打开 🖥️ 控制台可查看执行日志，同一网络下的 PC 浏览器也可访问控制台 URL 进行远程操作。",
        ),
    )),
    HelpSection("👤", "用户画像与配置", listOf(
        HelpItem(
            "用户画像",
            "Agent 会在每次对话后自动提取用户信息，建立多维度画像。在 👤 画像页面可查看，也可手动编辑或做自我评估测试。",
        ),
        HelpItem(
            "用户配置",
            "在 ⚙️ 用户配置中存储你的偏好、API 密钥等。Agent 的 Skill 可以通过 user_config 工具读取这些值，为你个性化执行任务。",
            "设置 user.name = 张三 后，AI 会在对话中用你的名字称呼你",
        ),
    )),
    HelpSection("💡", "使用技巧", listOf(
        HelpItem(
            "任务描述越具体越好",
            "包含目标、约束条件和期望格式，Agent 会更精准地执行。",
            "❌ 帮我查天气\n✓ 查询上海明天的天气，用中文简短回答",
        ),
        HelpItem(
            "利用历史推荐",
            "空对话页面会显示你最近完成的任务，点击可快速重复执行类似任务。",
        ),
        HelpItem(
            "多步骤任务拆分",
            "对于复杂任务，可以描述整体目标让 Agent 自动拆分，也可以手动一步步引导。",
            "帮我完成一份竞品分析报告：先搜索，再整理，最后生成 Markdown 文件",
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
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = c.subtext, modifier = Modifier.size(20.dp))
                }
                Text(
                    "🦀 使用指南",
                    color = c.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
            }
            HorizontalDivider(color = c.border, thickness = 0.5.dp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                // Intro banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.accent.copy(alpha = 0.08f))
                        .border(1.dp, c.accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text("MobileClaw 是一个运行在 Android 上的自主 AI Agent，可以看屏幕、操作界面、搜索网页、执行代码。", color = c.text, fontSize = 13.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("只需用自然语言描述你想做的事，MobileClaw 会自动规划并完成。", color = c.subtext, fontSize = 12.sp, lineHeight = 18.sp)
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
                            Text(section.emoji, fontSize = 15.sp)
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
                    "遇到问题？在设置页面可以查看 API 配置状态和服务器连接情况。",
                    color = c.subtext.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
