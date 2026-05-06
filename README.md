<div align="center">

<img src="docs/logo.png" alt="MobileClaw" width="180" />

**The Autonomous AI Agent That Lives Inside Your Android**

*See the screen. Think. Act. Build. Remember. Never stop.*

---

[![Platform](https://img.shields.io/badge/Platform-Android%2011%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2.0-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![API](https://img.shields.io/badge/API-OpenAI%20Compatible-412991?logo=openai&logoColor=white)](https://platform.openai.com)
[![License](https://img.shields.io/badge/License-MIT-22c55e)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-30-f97316)](https://developer.android.com/tools/releases/platforms)

**[中文版 README →](README_zh.md)**

</div>

---

## What is MobileClaw?

MobileClaw is an **autonomous AI Agent** running entirely on-device. It reads the screen, performs gestures, browses the web, runs Python code, generates images, and builds interactive mini-apps — completing complex multi-step tasks independently, even while you sleep.

> **No cloud relay. No Shizuku. No Root** (optional).  
> Just point it at any OpenAI-compatible endpoint and let it loose.

It's not a chatbot. It's not a macro recorder. It's a **thinking, deciding, self-extending AI** that treats your Android as its operating environment — and can upgrade its own capabilities at runtime.

---

## 🔥 What Can It Actually Do?

Here are real things you can ask MobileClaw right now:

```
"Open JD.com, find the cheapest iPhone 16, and tell me if it's a good deal"

"Open Instagram, scroll my feed, summarize what my friends posted today"

"Search for flights to Tokyo next month, compare prices, give me a table"

"Build me a habit tracker app with streaks and charts"

"Switch to Coder mode and refactor this Python file in my notes"

"Take a screenshot of my WeChat conversation and translate it to English"

"Start a group with Web Agent and General, research the best AI model for coding"
```

No special setup. No coding. Just ask.

---

## ✨ Features at a Glance

| Feature | Description |
|---------|-------------|
| **Interactive Chat UI** | AI proactively embeds live buttons, forms, cards, charts, and data tables inside chat messages using a JSON DSL — the chat becomes an interactive app surface |
| **Rich Markdown** | Full markdown in AI replies: pipe tables, code fences, bold/italic/inline-code, blockquotes, ordered/unordered lists, and horizontal rules |
| **Image Save** | Tap any image in chat to view fullscreen; save to gallery with one button (supports Android 9+) |
| **Screen Understanding** | Set-of-Mark annotated screenshots — works on native, Flutter, React Native, WebView, and games |
| **Gesture Control** | Tap, long-press, scroll, swipe, type with pixel-perfect accuracy |
| **Web Access** | Search, fetch, browse, and run JavaScript in a hidden WebView |
| **Mini-App Builder** | AI generates full interactive HTML apps with native Android bridge (SQLite, Python, files, clipboard, device APIs, launch 3rd-party apps) |
| **Role / Persona System** | Switch between specialist agents (Coder, Web Agent, Phone Operator, Creator) with forced skills and model overrides |
| **Multi-Agent Group Chat** | Pull any combination of AI roles into one conversation — they @mention each other, stream responses live, and stop automatically |
| **Persistent Sessions** | Full conversation history in Room DB — pick up any past task exactly where you left off |
| **Dynamic Model Switching** | Agent autonomously switches LLM mid-task when it needs vision, reasoning, or image generation |
| **User Config** | Agent reads and writes your personal config — preferences persist across sessions |
| **Multi-Layer Memory** | Semantic facts · Episodic task log · Conversation history · Working context window |
| **Local Embedding** | Episodic memory retrieval uses on-device n-gram embedding — no embedding API required |
| **Virtual Display** | Run apps invisibly in the background on a hidden 1080×1920 display |
| **Python Runtime** | Execute Python on-device (Chaquopy) — data processing, scraping, math, file parsing |
| **Image Generation** | Generate images via DALL-E or any compatible API, returned as attachments |
| **Privileged Server** | Self-contained shell-UID server bundled in the APK — no Shizuku needed |
| **Personalised Console** | Agent rewrites the LAN web console for each user — custom themes, widgets, and shortcuts |
| **Skill Self-Extension** | Agent creates new skills from natural language; promotes to the skill library with one tap; non-builtin skills can be deleted or demoted |
| **Chain-of-Thought** | Native DeepSeek-R1 `reasoning_content` streaming; thinking shown live in UI |
| **User Profile** | AI builds a live model of who you are — habits, goals, preferences — with a skill exploration progress tracker |
| **ROM Compatible** | Handles MIUI, EMUI, ColorOS, OriginOS, One UI, and stock Android |

---

## 💬 Interactive Chat UI

The AI can embed live, interactive components directly inside its chat replies — not just plain text or static markdown.

When the AI wants to offer choices, collect input, show a table, or display a chart, it outputs a ` ```ui ` block with a compact JSON tree. MobileClaw renders it inline as native Compose components.

```
User: "Show me the top 3 search engines and let me pick one"

AI:
```ui
{"type":"column","gap":10,"children":[
  {"type":"text","content":"Choose a search engine","bold":true},
  {"type":"button","label":"Google","action":"send:Use Google"},
  {"type":"button","label":"DuckDuckGo","action":"send:Use DuckDuckGo","style":"outline"},
  {"type":"button","label":"Bing","action":"send:Use Bing","style":"outline"}
]}
```
```

**Supported component types:**

| Type | Key props |
|------|-----------|
| `column` / `row` | `gap`, `padding`, `children` |
| `card` | `title`, `children` |
| `text` | `content`, `size`, `bold`, `italic`, `color`, `align` |
| `button` | `label`, `action`, `style` (filled / outline / text) |
| `input` | `key`, `placeholder` — value referenced as `{key}` in button actions |
| `select` | `key`, `options` |
| `table` | `headers`, `rows` |
| `chart_bar` / `chart_line` | `data`, `labels`, `title` |
| `progress` | `value` (0–1), `label` |
| `badge` | `text`, `color` |
| `image` | `src` (base64 data URI), `height` |
| `divider` / `spacer` | — |

**Action protocol:**

| Prefix | Behaviour |
|--------|-----------|
| `send:text` | Sends text as the user's next message |
| `submit:template {key}` | Replaces `{key}` with the current input value, then sends |
| `copy:text` | Copies text to clipboard silently |

---

## 📐 Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         MobileClaw App                              │
│                                                                      │
│  ┌─────────────────┐   ┌──────────────────────┐  ┌───────────────┐  │
│  │   Chat UI        │   │    Agent Runtime      │  │ Memory System │  │
│  │  (Compose)       │◄──┤  ReAct Loop           ├─►│ Semantic      │  │
│  │  Dynamic UI DSL  │   │  Role-aware           │  │ Episodic      │  │
│  │  Sessions        │   │  Single system prompt │  │ Conversation  │  │
│  │  Drawer Nav      │   │  per task             │  │ Working Window│  │
│  └─────────────────┘   └──────────┬───────────┘  └───────────────┘  │
│                                   │                                   │
│  ┌────────────────────────────────▼──────────────────────────────┐  │
│  │                       Skill Registry                           │  │
│  │                                                                │  │
│  │  LEVEL 0 (always)     LEVEL 1 (task-aware)   LEVEL 2 (demand) │  │
│  │  see_screen · tap     web_* · bg_* · shell   quick_skill      │  │
│  │  scroll · input       generate_image         meta · market    │  │
│  │  memory · navigate    create_html · python   skill_notes      │  │
│  │  switch_model         role_manager           session_manager  │  │
│  │  page_control         user_config                             │  │
│  └────────────────┬───────────────────────────────────────────── ┘  │
│                   │                                                   │
│  ┌────────────────▼────────┐    ┌──────────────────────────────────┐ │
│  │      LLM Gateway        │    │         Perception Layer          │ │
│  │  OpenAI-compatible      │    │  AccessibilityService + IME       │ │
│  │  Streaming · Tools      │    │  ActionController                 │ │
│  │  Vision · Thinking      │    │  VirtualDisplayManager            │ │
│  └─────────────────────────┘    └──────────────────┬───────────────┘ │
│                                                    │                  │
│                                   ┌────────────────▼─────────────┐   │
│                                   │  PrivilegedServer (Shell UID) │   │
│                                   │  TCP 127.0.0.1:52730          │   │
│                                   └──────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

The agent follows a **ReAct loop**: reason about the next action → call a skill → observe the result → repeat. The system prompt and skill list are built once per task (not per step), keeping token usage efficient.

---

## 🚀 Getting Started

### Requirements

- Android 11 (API 30) or higher
- An OpenAI-compatible LLM endpoint (cloud or local)

### Installation

**Option 1 — Pre-built APK**

1. Download the latest APK from [Releases](../../releases)
2. Enable *Install unknown apps* and install

**Option 2 — Build from Source**

```bash
git clone https://github.com/eggbrid2/mobileClaw.git
cd mobileClaw
./gradlew :app:assembleRelease
```

> Requires Android Studio Ladybug 2024.2+ and JDK 17+

### Initial Setup

1. Launch and grant all requested permissions (Accessibility, Overlay, Notifications)
2. Open **Settings** and configure your LLM:

   | Field | Example |
   |-------|---------|
   | API Endpoint | `https://api.openai.com` or `http://192.168.1.x:11434` |
   | API Key | Your service key |
   | Chat Model | `gpt-4o` (switchable from top bar) |

   > Embedding is handled locally — no embedding model or API key required.

3. Tap **Save** — the status dot turns green when ready

---

## 🎭 Role System

Roles are specialist agent personas that come pre-loaded with targeted skills, an optional model override, and a custom system prompt addendum. Switch roles from the drawer or ask the agent to switch mid-task.

| Role | Avatar | Forced Skills | Best For |
|------|--------|---------------|----------|
| **General** | 🦀 | none | Everyday tasks, conversation |
| **Coder** | 👨‍💻 | `shell` | File operations, code execution |
| **Web Agent** | 🌐 | `web_search`, `web_browse`, `fetch_url` | Research, price tracking, scraping |
| **Phone Operator** | 📱 | `see_screen`, `tap`, `scroll` | UI automation, app control |
| **Creator** | 🎨 | `generate_image`, `create_html` | Visual content, mini-apps |

Create your own custom roles: give them a name, emoji, system prompt, any combination of skills, and an optional model override. All roles — including built-ins — are editable; built-in roles have a **Restore** button.

---

## 📱 Mini-App Platform

MobileClaw can build and run **full interactive apps** inside the chat — not just static pages.

When you ask *"Build me a habit tracker"*, the agent:

1. Generates a complete HTML + JavaScript app
2. Injects a native Android bridge (`window.Claw`)
3. Opens it in a full-screen Activity-level viewer

The `window.Claw` bridge gives every mini-app access to:

```javascript
// Persistent key-value config
Claw.config.set("theme", "dark")
Claw.config.get("theme")          // → "dark"

// App-scoped file system
Claw.files.write("notes.txt", "Hello")
Claw.files.read("notes.txt")

// Full SQLite database
await Claw.sql("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY, title TEXT)")
const res = await Claw.sql("SELECT * FROM tasks")

// Python backend (on-device, no server)
const r = await Claw.python({ action: "analyze", data: [1, 2, 3, 4, 5] })

// HTTP requests (no CORS)
const r = await Claw.fetch("https://api.example.com/data", { method: "GET" })

// Native Android integration
Claw.launchApp("com.tencent.mm")         // open WeChat (or any installed app)
Claw.openUrl("https://example.com")      // open in browser / associated app
Claw.shareText("check this out", "tip")  // Android share sheet

// Device integration
Claw.toast("Saved!")
Claw.clipboard.set("copied text")
Claw.vibrate(100)

// Talk back to the agent
Claw.ask("Summarize my tasks for today")
Claw.close()
```

> **Note:** `window.fetch` and `XMLHttpRequest` are blocked by CORS in WebView. The bridge redirects any accidental `fetch()` calls to `Claw.fetch()` with a visible warning, and global error handlers surface silent Promise rejections as Toast messages.

Mini-apps are persisted under the **Apps** section and can be re-opened, updated, or deleted at any time.

---

## 🛠️ Skill System

Skills are the agent's action primitives. Each exposes a typed JSON schema and runs sandboxed in-process.

### Level 0 — Always Injected

| Skill | Description |
|-------|-------------|
| `see_screen` | Set-of-Mark annotated screenshot with numbered tappable nodes |
| `tap` | Tap by coordinate or node ID |
| `long_click` | Long-press by node ID |
| `scroll` | Directional swipe with configurable distance |
| `input_text` | Type into the focused or specified field |
| `navigate` | System nav — Home, Back, app launch by package name |
| `memory` | get / set / delete / list persistent key-value facts |
| `list_apps` | Enumerate installed apps and package names |
| `switch_model` | Autonomously switch LLM for subsequent steps |
| `page_control` | Navigate to any app page (Settings, Skills, Roles, Apps…) |

### Level 1 — Task-Aware Injection

| Skill | Description |
|-------|-------------|
| `web_search` | DuckDuckGo search (no API key) |
| `fetch_url` | Fetch and extract page content as Markdown |
| `web_browse` | Load a URL in a hidden WebView (handles JS-rendered pages) |
| `web_content` | Extract text from the loaded WebView |
| `web_js` | Execute JavaScript in the WebView |
| `bg_launch` | Launch an app on a hidden virtual display |
| `bg_screenshot` | Capture the virtual display |
| `bg_read_screen` | Read the virtual display's accessibility tree |
| `bg_stop` | Release the virtual display |
| `shell` | Run arbitrary shell commands |
| `generate_image` | Generate images via DALL-E or compatible API |
| `generate_icon` | Generate AI app icons (DashScope/CogView/OpenAI-compat) |
| `create_html` | Build and persist a full interactive mini-app |
| `console_editor` | Personalise the LAN console — full rewrite, CSS/JS injection |
| `user_config` | Read/write the user's personal configuration store |
| `role_manager` | Create, update, delete, and activate agent roles |
| `session_manager` | Create, switch, rename, and delete chat sessions |
| `app_manager` | Create, update, delete, and launch HTML mini-apps |

> **Managing user-created skills:** Non-builtin skills can be deleted (trash icon) or demoted from Level 1 back to Level 2 (on-demand) via the Skills page. Built-in skills have a **Restore** button.

### Level 2 — On-Demand

| Skill | Description |
|-------|-------------|
| `quick_skill` | LLM auto-generates a new Python/HTTP skill from natural language |
| `skill_market` | Browse and install community skills from GitHub |
| `meta` | Create, update, or remove skill definitions at runtime |
| `skill_check` | List all registered skills with parameters |
| `skill_notes` | Read/write user notes and AI-generated remarks per skill |
| `check_permissions` | Inspect and request app permissions |
| `create_file` | Read and write files in app storage |
| `vd_setup` | Configure virtual display parameters |

---

## 🧠 Memory System

| Layer | Storage | Retention | Purpose |
|-------|---------|-----------|---------|
| **Semantic** | Room DB | Permanent | Device facts, app info, user preferences — injected every task |
| **Episodic** | Room DB + local vectors | 100 tasks / 90 days | Task experiences; retrieved by cosine similarity using on-device n-gram embedding (no API) |
| **Conversation** | Room DB | 80 messages / 90 days | Chat history for user profile auto-extraction |
| **Working** | In-memory deque | Current task only | Recent steps within token budget; screenshots capped to last 2 steps to prevent large requests |

**User Profile Page**: MobileClaw builds a live model of who you are — habits, goals, timezone, preferences — extracted automatically from conversation history. Includes a **Skill Exploration** progress bar showing how many of the available skills you've actually used, with milestone labels.

---

## 👥 Multi-Agent Group Chat

Stop talking to one AI at a time. Create a **Group**, invite multiple roles, and watch them collaborate until the job is done.

**How it works:**

1. **Create a group** — pick a name, emoji, and any subset of your roles
2. **Send a message** — all members see the full conversation history
3. **Agents chain naturally** — an agent @mentions the next relevant member; the loop continues until someone finishes without an @mention
4. **@mention manually** — type `@RoleName` to direct a question to a specific agent
5. **Stop any time** — hit the ■ button

| Feature | Detail |
|---------|--------|
| **Live streaming** | Each agent's response streams token-by-token |
| **Loop guard** | Max 5 chained agent turns per message |
| **Per-agent color coding** | Every role gets a distinct accent color |
| **Full history context** | Each agent sees the complete conversation |
| **Persistent** | Group messages stored in Room DB |

---

## 🖥️ Virtual Display

MobileClaw launches apps on a hidden 1080×1920 virtual display. Three-tier launch strategy:

```
1. Standard API  ─── setLaunchDisplayId()  ──► blocked on MIUI / ColorOS / EMUI
       │
       ▼ fails
2.  Root (su)    ─── am start --display N  ──► works if rooted
       │
       ▼ fails
3. Priv Server   ─── shell UID via ADB     ──► works on all ROMs, no root needed
```

---

## 🔧 Built-in Privileged Server

No Shizuku. The server is bundled in the APK and started once via ADB:

```bash
adb shell 'CLASSPATH=$(pm path com.mobileclaw | cut -d: -f2) \
  /system/bin/app_process / com.mobileclaw.server.PrivilegedServer \
  </dev/null >/dev/null 2>&1 &'
```

Runs as **shell UID (2000)**. Listens on **TCP 127.0.0.1:52730**. Only accepts `am start …` commands.

---

## 💬 LLM Compatibility

| Provider | Notes |
|----------|-------|
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `o3`, `o4-mini`, `gpt-4.1`, `gpt-5.x` — full tool use + vision; newer models automatically use the Responses API image format |
| **DeepSeek** | `deepseek-chat`, `deepseek-reasoner` — native `reasoning_content` streaming; inline `<think>` tags also stripped |
| **Anthropic** | Via OpenAI-compatible proxy (e.g. litellm) |
| **Ollama** | Point endpoint to `http://localhost:11434` |
| **LocalAI / vLLM** | Any OpenAI-compatible local inference server |
| **Azure OpenAI** | Set endpoint to your Azure deployment URL |

---

## 🖥️ LAN Console

MobileClaw runs a local HTTP server (port 52733). The agent can **completely rewrite it** to create a personalised dashboard for each user via the `console_editor` skill (full rewrite / CSS patch / JS patch / reset).

---

## 📂 Project Structure

```
app/src/main/java/com/mobileclaw/
├── agent/
│   ├── AgentRuntime.kt          # ReAct loop — Reason → Act → Observe
│   ├── AgentContext.kt          # Task state, step list, message builder
│   ├── Role.kt / RoleManager.kt # Role data model + CRUD (built-in override support)
│   ├── Group.kt / GroupManager.kt
│   └── ChatRouter.kt            # Fast CHAT vs AGENT classifier (no LLM call)
├── app/
│   └── MiniAppStore.kt          # Mini-app metadata + HTML + JS bridge injection
├── llm/
│   ├── LlmGateway.kt
│   └── OpenAiGateway.kt         # Streaming, tools, vision
├── skill/
│   ├── Skill.kt / SkillRegistry.kt / SkillLoader.kt
│   ├── executor/                # Python & HTTP executors (Chaquopy)
│   └── builtin/                 # 35+ built-in skills
├── memory/
│   ├── SemanticMemory.kt
│   ├── EpisodicMemory.kt        # Cosine-similarity retrieval
│   ├── LocalEmbedder.kt         # On-device n-gram embedding (no API)
│   ├── WorkingMemory.kt         # Sliding-window; counts image sizes in budget
│   ├── UserProfileExtractor.kt
│   └── db/
├── config/
│   ├── AgentConfig.kt
│   └── UserConfig.kt
├── perception/
│   ├── ClawAccessibilityService.kt
│   ├── ClawIME.kt
│   ├── ActionController.kt
│   ├── ScreenshotController.kt
│   └── VirtualDisplayManager.kt
├── server/
│   ├── PrivilegedServer.kt
│   └── PrivilegedClient.kt
└── ui/
    ├── MainActivity.kt
    ├── ChatScreen.kt
    ├── MarkdownText.kt          # Markdown renderer; routes ```ui fences to DynamicUiRenderer
    ├── DynamicUiRenderer.kt     # JSON DSL → native Compose components
    ├── DrawerContent.kt
    ├── MiniAppActivity.kt       # Full-screen mini-app WebView host
    ├── AppJsBridge.kt           # window.Android bridge (fetch, sql, python, launchApp…)
    ├── AppLauncherPage.kt
    ├── GroupsPage.kt / GroupChatScreen.kt
    ├── RolesPage.kt / RoleEditPage.kt
    ├── SkillsPage.kt
    ├── ProfilePage.kt           # User profile + skill exploration progress
    ├── SettingsPage.kt
    └── ClawTheme.kt
```

---

## 🔐 Permissions

| Permission | Purpose | Required |
|------------|---------|:--------:|
| Accessibility Service | Read screen UI tree, inject gestures | ✅ |
| Input Method Service | Reliable text input to any field | ✅ |
| System Alert Window | Agent status overlay while running | ✅ |
| Internet | LLM API, web browsing, model fetching | ✅ |
| Post Notifications | Task completion alerts | Recommended |
| Ignore Battery Optimization | Uninterrupted long-running tasks | Recommended |
| Query All Packages | Discover installed app package names | Recommended |

---

## 🧩 Writing a Custom Skill

**Option A — Ask the agent:**

*"Create a skill that reads my battery level and tells me if I should charge."*

**Option B — Write it in Kotlin:**

```kotlin
class WeatherSkill : Skill {
    override val meta = SkillMeta(
        id             = "get_weather",
        description    = "Fetch current weather for a city",
        parameters     = listOf(SkillParam("city", "string", "City name", required = true)),
        injectionLevel = 1,
    )
    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val city = params["city"] as? String ?: return SkillResult.error("city required")
        return SkillResult.success(fetchWeatherApi(city))
    }
}
```

**Option C — JSON definition** (HTTP or Python, no recompile): drop a `.json` file in the skill directory or use the `meta` skill at runtime.

---

## 🔭 Roadmap

- **Scheduled Tasks** — cron-style task scheduling
- **Group Coordinator Role** — dedicated planner that delegates to group members
- **Proactive Notifications** — agent monitors conditions and alerts without being asked
- **Live Screen Mirroring** — stream virtual display to the UI
- **Voice Interface** — wake word + STT/TTS
- **Skill Marketplace** — community-curated skill packs
- **Cross-Device Sync** — share memory, sessions, mini-apps across phones
- **On-Device LLM** — first-class llama.cpp / MLC-LLM integration
- **Workflow Chains** — visual node editor to wire skills into automation pipelines

---

## 🤝 Contributing

```bash
git checkout -b feature/my-new-skill
git commit -m "feat: add battery monitor skill"
git push origin feature/my-new-skill
```

Good first contributions: new built-in skills for popular apps, role presets, LLM backend adapters, translations (`values-XX/strings.xml`), ROM compatibility fixes, mini-app templates.

Open an issue before starting a large feature.

---

## 📄 License

MIT — see [LICENSE](LICENSE)

---

<div align="center">

<img src="docs/logo.png" alt="MobileClaw" width="80" />

Built for those who believe the phone in their pocket is smarter than it lets on.

**[⬆ Back to Top](#)** · **[中文版 →](README_zh.md)**

</div>
