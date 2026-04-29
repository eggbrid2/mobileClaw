<div align="center">

```
███╗   ███╗ ██████╗ ██████╗ ██╗██╗     ███████╗ ██████╗██╗      █████╗ ██╗    ██╗
████╗ ████║██╔═══██╗██╔══██╗██║██║     ██╔════╝██╔════╝██║     ██╔══██╗██║    ██║
██╔████╔██║██║   ██║██████╔╝██║██║     █████╗  ██║     ██║     ███████║██║ █╗ ██║
██║╚██╔╝██║██║   ██║██╔══██╗██║██║     ██╔══╝  ██║     ██║     ██╔══██║██║███╗██║
██║ ╚═╝ ██║╚██████╔╝██████╔╝██║███████╗███████╗╚██████╗███████╗██║  ██║╚███╔███╔╝
╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝╚══════╝╚══════╝ ╚═════╝╚══════╝╚═╝  ╚═╝ ╚══╝╚══╝
```

**🦞 住在你 Android 里的自主 AI Agent**

*能看、能想、能动、能造、能记忆——从不停歇*

---

[![Platform](https://img.shields.io/badge/平台-Android%2011%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2.0-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/接口-OpenAI%20兼容-412991?logo=openai&logoColor=white)](https://platform.openai.com)
[![License](https://img.shields.io/badge/协议-MIT-22c55e)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-30-f97316)](https://developer.android.com/tools/releases/platforms)

**[English README →](README.md)**

</div>

---

## MobileClaw 是什么？

MobileClaw 是一个完全运行在设备本地的**自主 AI Agent**。它能看懂屏幕、执行手势、浏览网页、运行 Python 代码、生成图片、构建交互式 Mini 应用——独立完成复杂的多步骤任务，即使在你睡觉时也不停工。

> **无需云端中转，无需 Shizuku，无需 Root**（Root 为可选加分项）。  
> 只需一个兼容 OpenAI 接口的 LLM 服务，立刻开始自动化。

它不是聊天机器人，也不是宏录制器。它是一个**会思考、会决策、能自我扩展的 AI**——把你的 Android 当作运行环境，还能在运行时为自己升级能力。

---

## 🔥 它到底能做什么？

以下是你现在就可以对 MobileClaw 说的真实指令：

```
"每小时监控淘宝，价格跌破 ¥299 时通知我"

"打开微博，滚动我的关注，总结今天大家在说什么"

"帮我在 Gmail 起草并发送 10 封个性化跟进邮件"

"搜索下个月去东京的机票，比较价格，给我一张表格"

"帮我做一个带打卡连击和图表的习惯追踪 App"

"切换到程序员模式，把我备忘录里这个 Python 文件重构一下"

"截图我的微信对话，翻译成英文"
```

无需特殊设置，无需写代码，直接说就好。

---

## ✨ 核心特性一览

| 特性 | 说明 |
|------|------|
| **屏幕理解** | Set-of-Mark 标注截图，兼容原生 App、Flutter、React Native、WebView、游戏 |
| **手势控制** | 点击、长按、滑动、输入，坐标或节点 ID 双模式定位 |
| **网络访问** | 搜索、抓取、WebView 浏览、JavaScript 执行，全链路打通 |
| **Mini 应用构建器** | AI 生成完整交互式 HTML 应用，内置原生 Android 桥（SQLite、Python、文件、剪贴板、设备接口） |
| **角色 / 人格系统** | 在专家代理人之间切换（程序员、网络代理、手机操控者、创作者），带强制技能和模型覆盖 |
| **持久化会话** | 完整对话历史存储至 Room DB，随时从左侧抽屉切换到任意历史对话 |
| **动态模型切换** | Agent 在任务中途自主切换 LLM（视觉、推理、图像生成） |
| **用户配置** | Agent 可读写你的个人配置，偏好设置跨会话持久保存 |
| **多层记忆** | 语义记忆 · 情节记忆 · 对话记忆 · 工作记忆，四层协同 |
| **虚拟屏后台** | 在隐藏的 1080×1920 虚拟显示器上无感知运行目标 App |
| **Python 运行时** | 本地执行 Python（Chaquopy），数据处理、爬取、数学计算、文件解析 |
| **图像生成** | 通过 DALL-E 或兼容 API 生成图片，以附件形式返回 |
| **内置特权服务** | APK 内捆绑 Shell-UID 服务器，无需安装 Shizuku |
| **千人千面控制台** | Agent 为每位用户重写局域网控制台——自定义主题、控件和快捷指令（`console_editor`） |
| **Skill 自我扩展** | Agent 从自然语言描述创建新 Skill，一键提升到 Skill 库 |
| **思维链展示** | 原生支持 DeepSeek-R1 `reasoning_content` 流式，UI 实时呈现思考过程 |
| **全 ROM 适配** | 已适配 MIUI / EMUI / ColorOS / OriginOS / One UI / 原生系统 |

---

## 📐 架构概览

```
┌────────────────────────────────────────────────────────────────────┐
│                         MobileClaw App                              │
│                                                                      │
│  ┌─────────────────┐   ┌──────────────────────┐  ┌───────────────┐  │
│  │   聊天 UI        │   │    Agent 运行时        │  │   记忆系统    │  │
│  │  (Compose)       │◄──┤  ReAct 循环           ├─►│ 语义 · 情节  │  │
│  │  持久化会话      │   │  角色感知              │  │ 对话 · 工作  │  │
│  │  抽屉导航        │   │  动态模型切换          │  └───────────────┘  │
│  └─────────────────┘   └──────────┬───────────┘                      │
│                                   │                                   │
│  ┌────────────────────────────────▼──────────────────────────────┐   │
│  │                        Skill 注册中心                           │   │
│  │                                                                │   │
│  │  Level 0（始终）      Level 1（任务感知）    Level 2（按需）    │   │
│  │  see_screen · tap    web_* · bg_* · shell  quick_skill        │   │
│  │  scroll · input      generate_image        meta · market      │   │
│  │  memory · navigate   create_html · python  skill_notes        │   │
│  │  switch_model        role_manager          session_manager    │   │
│  │  page_control        user_config                              │   │
│  └────────────────┬───────────────────────────────────────────── ┘   │
│                   │                                                    │
│  ┌────────────────▼────────┐    ┌──────────────────────────────────┐  │
│  │      LLM 网关            │    │           感知层                  │  │
│  │  OpenAI 兼容接口         │    │  AccessibilityService + IME       │  │
│  │  流式 · 工具调用          │    │  ActionController                 │  │
│  │  视觉 · 思维链            │    │  VirtualDisplayManager            │  │
│  └─────────────────────────┘    └──────────────────┬───────────────┘  │
│                                                    │                   │
│                                   ┌────────────────▼─────────────┐    │
│                                   │  特权服务（Shell UID 进程）    │    │
│                                   │  TCP 127.0.0.1:52730          │    │
│                                   └──────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

Agent 遵循 **ReAct 循环**：推理下一步行动 → 调用 Skill → 观察结果 → 循环。每轮调用一次 LLM，当前激活的角色决定哪些 Skill 始终可用、是否强制指定模型。

---

## 🚀 快速开始

### 环境要求

- Android 11（API 30）及以上
- 可访问的 OpenAI 兼容 LLM 服务（云端或本地均可）

### 安装方式

**方式一：下载预编译 APK**

1. 前往 [Releases](../../releases) 页面下载最新 APK
2. 开启「安装未知来源应用」后直接安装

**方式二：从源码编译**

```bash
git clone https://github.com/yourname/mobileclaw.git
cd mobileclaw
./gradlew :app:assembleRelease
```

> 需要 Android Studio Ladybug 2024.2+ 和 JDK 17+

### 初始配置

1. 安装后，授予所有必要权限（辅助功能、悬浮窗、通知）
2. 打开 **设置（Settings）** 页面，填写 LLM 配置：

   | 字段 | 示例 |
   |------|------|
   | API Endpoint | `https://api.openai.com` 或本地 `http://192.168.1.x:11434` |
   | API Key | 你的服务密钥 |
   | Chat Model | `gpt-4o`（也可在 TopBar 芯片处快速切换） |
   | Embedding Model | `text-embedding-3-small`（用于情节记忆检索） |

3. 点击 **Save**，首页状态点变绿即可开始使用

---

## 🎭 角色系统

角色是专家型 AI 人格，每个角色预装了针对性的 Skill、可选的模型覆盖，以及自定义的系统提示补充。从抽屉切换角色，或直接让 Agent 在任务中途切换。

| 角色 | 头像 | 强制 Skill | 最适合 |
|------|------|------------|--------|
| **通用助手** | 🦞 | 无 | 日常任务、对话 |
| **程序员** | 👨‍💻 | `shell` | 文件操作、代码执行 |
| **网络代理** | 🌐 | `web_search`, `web_browse`, `fetch_url` | 调研、比价、爬取 |
| **手机操控者** | 📱 | `see_screen`, `tap`, `scroll` | UI 自动化、App 控制 |
| **创作者** | 🎨 | `generate_image`, `create_html` | 视觉内容、Mini 应用 |

你也可以创建完全自定义的角色：命名、选 Emoji、写系统提示、勾选任意 Skill 组合、指定专属模型。保存后，从抽屉一键激活。

---

## 📱 Mini 应用平台

MobileClaw 能在对话框内构建并运行**完整的交互式应用**——不是静态页面，而是真正可用的 App。

当你说「帮我做一个习惯追踪器」，Agent 会：

1. 生成完整的 HTML + JavaScript 应用
2. 注入原生 Android 桥（`window.Claw`）
3. 在全屏 Activity 级别的查看器中打开

`window.Claw` 桥为每个 Mini 应用提供：

```javascript
// 持久化配置
Claw.config.set("theme", "dark")
Claw.config.get("theme")         // → "dark"

// 应用沙箱文件系统
Claw.files.write("notes.txt", "Hello")
Claw.files.read("notes.txt")

// 完整 SQLite 数据库
Claw.sql("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY, title TEXT)")
Claw.sql("INSERT INTO tasks VALUES (null, ?)", ["买牛奶"])
Claw.sql("SELECT * FROM tasks").rows

// Python 后端（本地运行，无需服务器）
Claw.python({ action: "analyze", data: [1, 2, 3, 4, 5] })

// HTTP 请求（无跨域限制）
Claw.fetch("https://api.example.com/data", { method: "GET" })

// 设备集成
Claw.toast("已保存！")
Claw.clipboard.set("复制内容")
Claw.vibrate(100)

// 与 Agent 对话
Claw.ask("帮我总结今天的任务")
Claw.close()
```

Mini 应用持久保存在抽屉的 **应用（Apps）** 区域，随时可重新打开、更新或删除。

---

## 🛠️ Skill 系统

Skill 是 Agent 的行动能力单元，每个 Skill 向 LLM 暴露类型化的 JSON Schema，并在应用内沙箱执行。

### Level 0 — 始终注入

| Skill | 说明 |
|-------|------|
| `see_screen` | 带 Set-of-Mark 标注的截图，对可交互元素添加编号红圈 |
| `tap` | 通过坐标或节点 ID 点击 |
| `long_click` | 长按节点（常用于触发上下文菜单） |
| `scroll` | 上下左右滑动，支持自定义距离 |
| `input_text` | 向焦点或指定节点输入文字 |
| `navigate` | 系统导航：返回、Home、按包名启动应用 |
| `memory` | 读写持久化键值记忆（get / set / delete / list） |
| `list_apps` | 枚举已安装应用及包名 |
| `switch_model` | 为后续步骤自主切换 LLM |
| `page_control` | 导航到任意应用页面（设置、技能、角色、应用…） |

### Level 1 — 任务感知注入

| Skill | 说明 |
|-------|------|
| `web_search` | DuckDuckGo 搜索（无需 API Key） |
| `fetch_url` | 抓取 URL 并提取正文为 Markdown |
| `web_browse` | 在隐藏 WebView 中加载 URL（支持 JS 渲染页面） |
| `web_content` | 提取已加载 WebView 的页面文本 |
| `web_js` | 在 WebView 中执行 JavaScript |
| `bg_launch` | 在虚拟屏上启动 App |
| `bg_screenshot` | 截取虚拟屏画面 |
| `bg_read_screen` | 读取虚拟屏的无障碍 UI 树 |
| `bg_stop` | 释放虚拟屏 |
| `shell` | 执行任意 Shell 命令 |
| `generate_image` | 通过 DALL-E 或兼容 API 生成图片 |
| `create_html` | 构建并持久化完整交互式 Mini 应用 |
| `console_editor` | 定制局域网控制台页面——完整重写、注入 CSS 主题或注入 JS 控件（千人千面） |
| `user_config` | 读写用户个人配置项 |
| `role_manager` | 创建、更新、删除、激活角色 |
| `session_manager` | 创建、切换、重命名、删除会话 |
| `switch_role` | 切换活跃的角色人格 |

### Level 2 — 按需加载

| Skill | 说明 |
|-------|------|
| `quick_skill` | LLM 根据自然语言描述自动生成新 Skill |
| `skill_market` | 浏览并安装社区 Skill（来自 GitHub） |
| `meta` | 在运行时创建、更新或删除 Skill 定义 |
| `skill_check` | 列出所有已注册 Skill 及其参数 |
| `skill_notes` | 读写每个 Skill 的用户备注和 AI 生成摘要 |
| `check_permissions` | 查看和申请应用权限 |
| `app_manager` | 安装、卸载、强停或查询 App 信息 |
| `create_file` | 读写应用存储中的文件 |
| `vd_setup` | 配置虚拟屏参数 |

---

## 🧠 记忆系统

```
任务目标
    │
    ▼
┌──────────────────────────────────────────────┐
│            系统提示构建器                      │
│                                               │
│  ┌─────────────────┐  ┌─────────────────────┐ │
│  │   语义记忆       │  │      情节记忆         │ │
│  │  "已知事实：     │  │  "类似的过去任务      │ │
│  │   你的时区、     │  │   让你学到了：        │ │
│  │   常用 App、     │  │   下拉刷新需要往      │ │
│  │   写作风格…"     │  │   下滑到顶端…"        │ │
│  └─────────────────┘  └─────────────────────┘ │
└──────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────┐
│              工作记忆                          │
│   （滑动窗口 · 4096 Token 预算）               │
│   步骤 1 → 步骤 2 → … → 步骤 N               │
└──────────────────────────────────────────────┘
```

| 层级 | 存储 | 保留策略 | 用途 |
|------|------|----------|------|
| **语义记忆** | Room DB | 永久 | 设备事实、应用信息、用户偏好——每次任务注入 |
| **情节记忆** | Room DB + 向量 | 100 条 / 90 天 | 任务经历与反思，余弦相似度检索相关历史 |
| **对话记忆** | Room DB | 80 条消息 / 90 天 | 聊天历史，驱动用户画像自动提取 |
| **工作记忆** | 内存双端队列 | 仅当前任务 | 近期步骤保留在 LLM 上下文窗口内 |

**用户画像页面**：MobileClaw 会自动构建你的画像模型——你的习惯、目标、时区、偏好——通过 LLM 从对话历史中提取。在**画像 → 事实**页面查看和编辑。

---

## 💬 持久化会话

每次对话都以完整的消息历史、日志行和附件持久化到 Room DB。从左侧抽屉切换历史对话——每个会话都精确记住你离开时的位置。

Agent 也可以用编程方式管理会话：为任务创建新会话，发现话题后重命名，完成后归档。

---

## 🖥️ 虚拟屏技术

MobileClaw 在隐藏的 1080×1920 虚拟显示器上启动 App，对你完全无感知。Agent 通过无障碍树和截图独立读取和操作目标 App——完全不干扰主屏幕。

**三级启动策略（依次尝试）：**

```
1. 标准 API  ─── setLaunchDisplayId()  ──► 被 MIUI / ColorOS / EMUI 拦截
       │
       ▼ 失败
2.  Root 模式 ─── su -c am start --display N  ──► 需要已 Root 设备
       │
       ▼ 失败
3. 特权服务  ─── Shell UID（一次 ADB 激活）──► 全 ROM 通用，无需 Root
```

---

## 🔧 内置特权服务

无需安装 Shizuku！MobileClaw 在 APK 内捆绑了自己的特权服务器，只需在电脑上执行一次 ADB 命令激活：

```bash
adb shell 'CLASSPATH=$(pm path com.mobileclaw | cut -d: -f2) \
  /system/bin/app_process / com.mobileclaw.server.PrivilegedServer \
  </dev/null >/dev/null 2>&1 &'
```

服务进程以 **Shell UID（2000）** 身份运行，在 ColorOS 等高度限制的 ROM 上也能正常调用 `am start --display N`。通信采用 **TCP `127.0.0.1:52730`**。仅接受 `am start …` 命令，其他所有输入均被拒绝。

---

## 💬 模型兼容性

| 服务商 | 说明 |
|--------|------|
| **OpenAI** | `gpt-4o`、`gpt-4o-mini`、`o1`、`o3-mini`——完整工具调用 + 视觉 |
| **DeepSeek** | `deepseek-chat`、`deepseek-reasoner`——原生 `reasoning_content` 流式，思考过程实时展示 |
| **Anthropic** | 通过 OpenAI 兼容代理（如 litellm）接入 |
| **Ollama** | Endpoint 填写 `http://localhost:11434`，支持任意本地模型 |
| **LocalAI / vLLM** | 任意 OpenAI 兼容本地推理服务 |
| **Azure OpenAI** | Endpoint 填写 Azure 部署 URL |

无需进入设置即可切换模型：点击 TopBar 的模型芯片 → 从列表中选择，或点击 ↻ 从 API 实时拉取可用模型。也可以直接对 Agent 说：*"换成 deepseek-reasoner 做这个任务"*。

---

## 🖥️ 局域网控制台 — 千人千面

MobileClaw 在本地运行一个 HTTP 服务器（端口 52733），同一 Wi-Fi 下的任何人都能用浏览器打开。默认是一个简洁的聊天界面——但 Agent 可以**完全重写它**，为每个用户打造专属的个性化仪表盘。

```
"帮我把控制台改成暗紫色主题，并加上我最常用的 5 个任务快捷按钮"

"在顶部加一个实时时钟和今日任务列表"

"把控制台改成终端风格——等宽字体，绿字黑底"

"加一个「发送日报」按钮，点击后自动填入固定内容"
```

`console_editor` Skill 为 Agent 提供四种工具：

| 操作 | 说明 |
|------|------|
| `write` | 用完整的自定义 HTML 仪表盘替换整个页面 |
| `patch_css` | 注入 CSS，修改主题颜色、字体、布局，无需改动 HTML |
| `patch_js` | 注入 JS，添加实时控件、键盘快捷键或动态内容 |
| `reset` | 恢复出厂默认控制台页面 |

Agent 在任何编辑后都会保持 `/api/events` SSE 流、`/api/send`、`/api/sessions`、`/api/messages` 接口正常工作，控制台功能始终完整。结果就是**千人千面**——每个用户都拥有一个由 AI 根据自己的使用习惯、偏好和审美自动生成的专属控制台。

---

## 🔭 未来路线图

我们正在构建的下一批能力——欢迎贡献：

- **定时任务** — Cron 风格的任务调度，趁你睡觉执行 Agent 任务
- **多 Agent 协作** — 派生子 Agent 并行完成子任务
- **主动通知** — Agent 监控条件变化，在不被问及时主动提醒你
- **虚拟屏实时镜像** — 将虚拟屏内容串流到 UI 界面供查看
- **语音界面** — 唤醒词 + STT/TTS，说话下达任务
- **Skill 应用市场** — 社区精选 Skill 包，一键安装
- **跨设备同步** — 记忆、会话、Mini 应用跨手机共享
- **本地 LLM** — 深度集成 llama.cpp / MLC-LLM，实现完全离线运行
- **Computer Use API** — Claude 获得移动端支持后原生对接 Anthropic Computer Use
- **工作流编排** — 可视化节点编辑器，将 Skill 串联成可复用的自动化流水线

---

## 📂 项目结构

```
app/src/main/java/com/mobileclaw/
├── agent/
│   ├── AgentRuntime.kt          # ReAct 循环主体：推理→行动→观察
│   ├── AgentContext.kt          # 任务状态、步骤列表、循环检测
│   ├── Role.kt                  # 角色 / 人格数据模型
│   └── RoleManager.kt           # 内置 + 自定义角色管理
├── app/
│   └── MiniAppStore.kt          # Mini 应用元数据 + HTML 持久化 + JS 桥注入
├── llm/
│   ├── LlmGateway.kt            # 统一接口（chat + embed）
│   └── OpenAiGateway.kt         # OpenAI 兼容后端（流式、工具调用、视觉）
├── skill/
│   ├── Skill.kt                 # Skill 接口 & SkillMeta 定义
│   ├── SkillRegistry.kt         # 运行时 Skill 管理
│   ├── SkillLoader.kt           # 从存储加载/保存用户生成的 Skill
│   ├── executor/                # Python & HTTP Skill 执行器（Chaquopy）
│   └── builtin/                 # 35+ 内置 Skill
├── memory/
│   ├── SemanticMemory.kt        # 持久化键值事实存储
│   ├── EpisodicMemory.kt        # 任务经历日志 + 向量检索
│   ├── ConversationMemory.kt    # 聊天历史，驱动画像提取
│   ├── WorkingMemory.kt         # 滑动窗口上下文预算
│   ├── UserProfileExtractor.kt  # LLM 驱动的用户画像提取
│   └── db/                      # Room 数据库（实体、DAO、迁移、会话）
├── config/
│   ├── AgentConfig.kt           # DataStore 配置（端点、模型、主题等）
│   ├── UserConfig.kt            # 用户个人键值配置存储
│   └── SkillNotesStore.kt       # 每个 Skill 的备注持久化
├── perception/
│   ├── ClawAccessibilityService.kt  # 核心感知：读取 UI 树、注入手势
│   ├── ClawIME.kt                   # 输入法，用于可靠文字注入
│   ├── ActionController.kt          # 手势执行（点击、滑动、长按）
│   ├── ScreenshotController.kt      # 截图 & Set-of-Mark 标注
│   └── VirtualDisplayManager.kt    # 虚拟屏创建 & App 启动
├── server/
│   ├── PrivilegedServer.kt      # Shell-UID 服务器（app_process 入口）
│   └── PrivilegedClient.kt      # 特权命令 TCP 客户端
└── ui/
    ├── MainActivity.kt          # 根界面——ModalNavigationDrawer + 页面栈
    ├── ChatScreen.kt            # 主聊天界面（流式渲染、日志卡片、附件）
    ├── DrawerContent.kt         # 左侧抽屉——会话列表 + 角色徽章 + 导航
    ├── HtmlAttachmentViewer.kt  # 全屏 Mini 应用查看器（Activity 级 WebView）
    ├── AppLauncherPage.kt       # Mini 应用库 & 启动器
    ├── RolesPage.kt             # 角色浏览、创建、编辑
    ├── SkillsPage.kt            # Skill 浏览、备注、升级
    ├── ProfilePage.kt           # 用户画像 + 近期任务时间线
    ├── UserConfigPage.kt        # 用户配置增删改查
    ├── SettingsPage.kt          # LLM 配置、虚拟屏、特权服务
    └── ClawTheme.kt             # 深色/浅色主题 + 强调色系统
```

---

## 🔐 权限说明

| 权限 | 用途 | 是否必需 |
|------|------|:------:|
| 辅助功能服务 | 读取屏幕 UI 树、注入手势 | ✅ |
| 输入法服务 | 向任意输入框可靠输入文字 | ✅ |
| 悬浮窗 | Agent 执行中的状态浮层 | ✅ |
| 网络访问 | LLM API 调用、网页浏览、拉取模型列表 | ✅ |
| 发送通知 | 任务完成提醒 | 推荐 |
| 忽略电池优化 | 长时间后台任务不被系统杀死 | 推荐 |
| 读取应用列表 | 查找目标 App 的包名 | 推荐 |

---

## 🧩 自定义 Skill

**方式 A — 让 Agent 帮你创建**（推荐）：

直接说：*"帮我创建一个能每小时监控电量并记录日志的 Skill。"*

Agent 会自动调用 `quick_skill`，生成、测试并注册一个 Python 或 HTTP Skill。你可以在 Skill 页面一键将其提升为永久 Skill。

**方式 B — 用 Kotlin 编写：**

```kotlin
class WeatherSkill : Skill {
    override val meta = SkillMeta(
        id             = "get_weather",
        description    = "查询指定城市的当前天气",
        parameters     = listOf(
            SkillParam("city", "string", "城市名称", required = true)
        ),
        injectionLevel = 1,   // 0=始终 · 1=任务感知 · 2=按需
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val city = params["city"] as? String ?: return SkillResult.error("缺少 city 参数")
        return SkillResult.success(fetchWeatherApi(city))
    }
}
```

在 `MainViewModel.registerBuiltinSkills()` 中注册即可。

**方式 C — JSON Skill 定义**（HTTP 或 Python，无需重新编译）：

将 `.json` 文件放入 App 的 Skill 目录，或在运行时通过 `meta` Skill 直接定义。格式支持 HTTP 端点和内联 Python 脚本（通过 Chaquopy 本地执行）。

---

## 🤝 参与贡献

```bash
git checkout -b feature/my-new-skill
git commit -m "feat: 添加天气查询 Skill"
git push origin feature/my-new-skill
```

**适合新手的贡献方向：**

- 📦 常用 App 的 Skill（微信、支付宝、抖音、淘宝、高德地图）
- 🎭 专用场景的角色预设（学生、运营、程序员等）
- 🌐 更多 LLM 后端适配（Gemini 原生、Claude 原生 API）
- 🌍 多语言翻译（`values-XX/strings.xml`）
- 🐛 ROM 兼容性 Bug 修复与机型报告
- 📱 Mini 应用模板（记账、倒计时、待办、习惯打卡）

**建议在动手开发大型功能前先开一个 Issue 讨论方案。**

---

## 📄 开源协议

MIT — 详见 [LICENSE](LICENSE) 文件

---

<div align="center">

为那些相信口袋里的手机比它表现出来更聪明的人而打造 🦞

**[⬆ 回到顶部](#)** · **[English →](README.md)**

</div>
