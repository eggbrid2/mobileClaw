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
| **Screen Understanding** | Set-of-Mark annotated screenshots — works on native, Flutter, React Native, WebView, and games |
| **Gesture Control** | Tap, long-press, scroll, swipe, type with pixel-perfect accuracy |
| **Web Access** | Search, fetch, browse, and run JavaScript in a hidden WebView |
| **Mini-App Builder** | AI generates full interactive HTML apps with native Android bridge (SQLite, Python, files, clipboard, device APIs) |
| **Role / Persona System** | Switch between specialist agents (Coder, Web Agent, Phone Operator, Creator) with forced skills and model overrides |
| **Multi-Agent Group Chat** | Pull any combination of AI roles into one conversation — they @mention each other, stream responses live, and stop automatically when consensus is reached |
| **Persistent Sessions** | Full conversation history in Room DB — pick up any past task exactly where you left off |
| **Dynamic Model Switching** | Agent autonomously switches LLM mid-task when it needs vision, reasoning, or image generation |
| **User Config** | Agent reads and writes your personal config — preferences persist across sessions |
| **Multi-Layer Memory** | Semantic facts · Episodic task log · Conversation history · Working context window |
| **Virtual Display** | Run apps invisibly in the background on a hidden 1080×1920 display |
| **Python Runtime** | Execute Python on-device (Chaquopy) — data processing, scraping, math, file parsing |
| **Image Generation** | Generate images via DALL-E or any compatible API, returned as attachments |
| **Privileged Server** | Self-contained shell-UID server bundled in the APK — no Shizuku needed |
| **Personalised Console** | Agent rewrites the LAN web console for each user — custom themes, widgets, and shortcuts via `console_editor` |
| **Skill Self-Extension** | Agent creates new skills from natural language; promotes them to the skill library with one tap |
| **Chain-of-Thought** | Native DeepSeek-R1 `reasoning_content` streaming; thinking shown live in UI |
| **ROM Compatible** | Handles MIUI, EMUI, ColorOS, OriginOS, One UI, and stock Android |

---

## 📐 Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                         MobileClaw App                              │
│                                                                      │
│  ┌─────────────────┐   ┌──────────────────────┐  ┌───────────────┐  │
│  │   Chat UI        │   │    Agent Runtime      │  │ Memory System │  │
│  │  (Compose)       │◄──┤  ReAct Loop           ├─►│ Semantic      │  │
│  │  Sessions        │   │  Role-aware           │  │ Episodic      │  │
│  │  Drawer Nav      │   │  Dynamic model        │  │ Conversation  │  │
│  └─────────────────┘   └──────────┬───────────┘  │ Working Window│  │
│                                   │               └───────────────┘  │
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

The agent follows a **ReAct loop**: reason about the next action → call a skill → observe the result → repeat. Each iteration hits the LLM once. The active Role determines which skills are always available and whether a specific model is forced.

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
   | Embedding Model | `text-embedding-3-small` (for episodic memory) |

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

Create your own custom roles: give them a name, emoji, system prompt, any combination of skills, and an optional model override. Save them and activate them via the drawer.

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
await Claw.sql("INSERT INTO tasks VALUES (null, ?)", ["Buy milk"])
const res = await Claw.sql("SELECT * FROM tasks")

// Python backend (on-device, no server)
const r = await Claw.python({ action: "analyze", data: [1, 2, 3, 4, 5] })

// HTTP requests (no CORS)
const r = await Claw.fetch("https://api.example.com/data", { method: "GET" })

// Device integration
Claw.toast("Saved!")
Claw.clipboard.set("copied text")
Claw.vibrate(100)

// Talk back to the agent
Claw.ask("Summarize my tasks for today")
Claw.close()
```

Mini-apps are persisted under the **Apps** section of the drawer and can be re-opened, updated, or deleted at any time.

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
| `console_editor` | Personalise the LAN console web page — full rewrite, CSS theme injection, or JS widget injection |
| `user_config` | Read/write the user's personal configuration store |
| `role_manager` | Create, update, delete, and activate agent roles |
| `session_manager` | Create, switch, rename, and delete chat sessions |
| `switch_role` | Switch the active role persona |

### Level 2 — On-Demand

| Skill | Description |
|-------|-------------|
| `quick_skill` | LLM auto-generates a new Python/HTTP skill from natural language |
| `skill_market` | Browse and install community skills from GitHub |
| `meta` | Create, update, or remove skill definitions at runtime |
| `skill_check` | List all registered skills with parameters |
| `skill_notes` | Read/write user notes and AI-generated remarks per skill |
| `check_permissions` | Inspect and request app permissions |
| `app_manager` | Create, update, delete, and launch HTML mini-apps with native Android bridge |
| `create_file` | Read and write files in app storage |
| `vd_setup` | Configure virtual display parameters |

---

## 🧠 Memory System

```
Task Goal
    │
    ▼
┌──────────────────────────────────────────────┐
│            System Prompt Builder              │
│                                               │
│  ┌─────────────────┐  ┌─────────────────────┐ │
│  │  Semantic Memory │  │   Episodic Memory   │ │
│  │  "Known facts:   │  │  "Past tasks like   │ │
│  │   your timezone, │  │   this taught you:  │ │
│  │   preferred apps,│  │   swipe right on    │ │
│  │   writing style" │  │   feed to refresh…" │ │
│  └─────────────────┘  └─────────────────────┘ │
└──────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────┐
│              Working Memory                   │
│   (sliding window · 4096-token budget)        │
│   Step 1 → Step 2 → … → Step N               │
└──────────────────────────────────────────────┘
```

| Layer | Storage | Retention | Purpose |
|-------|---------|-----------|---------|
| **Semantic** | Room DB | Permanent | Device facts, app info, user preferences — injected every task |
| **Episodic** | Room DB + embeddings | 100 tasks / 90 days | Task experiences with cosine-similarity retrieval |
| **Conversation** | Room DB | 80 messages / 90 days | Chat history for user profile auto-extraction |
| **Working** | In-memory deque | Current task only | Recent steps within LLM context window |

**User Profile Page**: MobileClaw builds a live model of who you are — your habits, goals, timezone, preferences — extracted automatically from conversation history via LLM. View and edit it in **Profile → Facts**.

---

## 💬 Sessions

Every conversation is persisted to Room DB with full message history, log lines, and attachments. Switch between past conversations from the left drawer — each session remembers exactly where you left off.

The agent can also manage sessions programmatically: create a new session for a task, rename it when it discovers the topic, and archive it when done.

---

## 👥 Multi-Agent Group Chat

Stop talking to one AI at a time. Create a **Group**, invite multiple roles, and watch them collaborate — or argue — until the job is done.

```
"Start a group with Coder, Web Agent, and General.
 Research the best Python web scraping library, then implement it."
```

**How it works:**

1. **Create a group** — pick a name, emoji, and any subset of your roles
2. **Send a message** — all members see the full conversation history
3. **Agents chain naturally** — when an agent finishes, it @mentions the next relevant member; the loop continues until an agent responds without an @mention (meaning: done)
4. **@mention manually** — type `@RoleName` in your message to direct a question to a specific agent
5. **Stop any time** — hit the ■ button; the running agent finishes its current response and the loop halts

**What makes it different:**

| Feature | Detail |
|---------|--------|
| **Live streaming** | Each agent's response streams token-by-token; you see them think |
| **Loop guard** | Max 5 chained agent turns per message — prevents infinite AI debates |
| **Per-agent color coding** | Every role gets a distinct accent color so you always know who's speaking |
| **Full history context** | Each agent sees the complete conversation, not just the last message |
| **@mention highlighting** | `@mentions` are rendered in the speaker's color inside bubbles |
| **Persistent** | Group messages stored in Room DB — reopen any group and continue exactly where you left off |

**Example groups you can build:**

| Group | Members | Use case |
|-------|---------|---------|
| 🛠️ Dev Squad | Coder + Web Agent | Research → implement → test cycle |
| 🔬 Research Panel | General + Web Agent | Multi-angle deep research with cross-checking |
| 🎨 Creative Studio | Creator + General | Brainstorm → generate → review |
| 📱 Phone Squad | Phone Operator + Web Agent | Look something up then act on it |

---

## 🖥️ Virtual Display

MobileClaw launches apps on a hidden 1080×1920 virtual display, invisible to you. The agent reads and controls them via the accessibility tree and screenshots — without touching your main screen.

**Three-tier launch strategy:**

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

Runs as **shell UID (2000)** — enough to bypass ROM restrictions on `am start --display N`. Listens on **TCP 127.0.0.1:52730**. Only accepts `am start …` commands; all else is rejected.

---

## 💬 LLM Compatibility

| Provider | Notes |
|----------|-------|
| **OpenAI** | `gpt-4o`, `gpt-4o-mini`, `o1`, `o3-mini` — full tool use + vision |
| **DeepSeek** | `deepseek-chat`, `deepseek-reasoner` — native `reasoning_content` streaming shown in UI |
| **Anthropic** | Via OpenAI-compatible proxy (e.g. litellm) |
| **Ollama** | Point endpoint to `http://localhost:11434` |
| **LocalAI / vLLM** | Any OpenAI-compatible local inference server |
| **Azure OpenAI** | Set endpoint to your Azure deployment URL |

Switch models without Settings: tap the model chip in the top bar → pick from list or hit ↻ to fetch from the API. Or just tell the agent: *"switch to deepseek-reasoner for this"*.

---

## 🖥️ LAN Console — Personalised for Every User

MobileClaw runs a local HTTP server (port 52733) that anyone on the same Wi-Fi can open in a browser. Out of the box it's a clean chat interface — but the agent can **completely rewrite it** to create a personalised dashboard for each individual user.

```
"Redesign my console with a dark purple theme and add shortcut buttons for my top 5 tasks"

"Add a live clock widget and show my today's task list at the top"

"Make the console look like a terminal — monospace font, green on black"

"Add a button 'Send daily report' that pre-fills the input"
```

The `console_editor` skill gives the agent four tools:

| Action | What it does |
|--------|-------------|
| `write` | Replace the entire page with a fully custom HTML dashboard |
| `patch_css` | Inject CSS to retheme colours, fonts, layout without touching the HTML |
| `patch_js` | Inject JS to add live widgets, keyboard shortcuts, or dynamic content |
| `reset` | Restore the factory-default console page |

The agent keeps the `/api/events` SSE stream, `/api/send`, `/api/sessions`, and `/api/messages` endpoints intact so the console stays fully functional after any edit. The result is a **千人千面** console: every user ends up with a console that matches their personality, workflow, and aesthetic — generated automatically from their conversation history and preferences.

---

## 🔭 Roadmap

Things we're building next — contributions welcome:

- **Scheduled Tasks** — cron-style task scheduling, run agent jobs while you sleep
- **Group Roles** — assign a dedicated coordinator role that plans and delegates to other group members
- **Proactive Notifications** — agent monitors conditions and alerts you without being asked
- **Live Screen Mirroring** — stream virtual display to the UI for visibility
- **Voice Interface** — wake word + STT/TTS so you can give tasks by speaking
- **Skill Marketplace** — community-curated skill packs installable in one tap
- **Cross-Device Sync** — share memory, sessions, mini-apps, and group histories across phones
- **On-Device LLM** — first-class integration with llama.cpp / MLC-LLM for fully offline operation
- **Computer Use API** — native Anthropic Computer Use support when Claude gains mobile support
- **Workflow Chains** — visual node editor to wire skills into reusable automation pipelines
- **Group Voting** — structured consensus mechanism where agents vote on options before proceeding

---

## 📂 Project Structure

```
app/src/main/java/com/mobileclaw/
├── agent/
│   ├── AgentRuntime.kt          # ReAct loop — Reason → Act → Observe
│   ├── AgentContext.kt          # Task state, step list, loop-guard
│   ├── Role.kt                  # Role / persona data model
│   ├── RoleManager.kt           # Built-in + custom role management
│   ├── Group.kt                 # Group data model (name, emoji, member role IDs)
│   └── GroupManager.kt          # JSON-backed group CRUD (filesDir/groups/)
├── app/
│   └── MiniAppStore.kt          # Mini-app metadata + HTML persistence + JS bridge injection
├── llm/
│   ├── LlmGateway.kt            # Unified interface (chat + embed)
│   └── OpenAiGateway.kt         # OpenAI-compatible backend (streaming, tools, vision)
├── skill/
│   ├── Skill.kt                 # Skill interface & SkillMeta
│   ├── SkillRegistry.kt         # Runtime skill management
│   ├── SkillLoader.kt           # Load/save user-generated skills
│   ├── executor/                # Python & HTTP skill executor (Chaquopy)
│   └── builtin/                 # 35+ built-in skills
├── memory/
│   ├── SemanticMemory.kt        # Persistent key-value fact store
│   ├── EpisodicMemory.kt        # Task experience log with embedding retrieval
│   ├── ConversationMemory.kt    # Chat history for profile extraction
│   ├── WorkingMemory.kt         # Sliding-window context budget
│   ├── UserProfileExtractor.kt  # LLM-powered user profile extraction
│   └── db/                      # Room DB — entities, DAOs, migrations, sessions, group messages
├── config/
│   ├── AgentConfig.kt           # DataStore-backed config (endpoint, model, theme…)
│   ├── UserConfig.kt            # User's personal key-value config store
│   └── SkillNotesStore.kt       # Per-skill notes persistence
├── perception/
│   ├── ClawAccessibilityService.kt  # Core perception — reads UI trees, injects gestures
│   ├── ClawIME.kt                   # Input Method for reliable text injection
│   ├── ActionController.kt          # Gesture execution (tap, scroll, long-press)
│   ├── ScreenshotController.kt      # Frame capture & Set-of-Mark annotation
│   └── VirtualDisplayManager.kt    # Hidden display creation & app launching
├── server/
│   ├── PrivilegedServer.kt      # Shell-UID server (app_process entry point)
│   └── PrivilegedClient.kt      # TCP client for privileged commands
└── ui/
    ├── MainActivity.kt          # Root — ModalNavigationDrawer + page stack
    ├── ChatScreen.kt            # Main chat (streaming, log cards, attachments)
    ├── DrawerContent.kt         # Left drawer — sessions + role badge + nav
    ├── HtmlAttachmentViewer.kt  # Full-screen mini-app viewer (Activity-level WebView)
    ├── AppLauncherPage.kt       # Mini-app library & launcher
    ├── GroupsPage.kt            # Group list, creation dialog, group cards
    ├── GroupChatScreen.kt       # Multi-agent streaming chat — @mentions, color coding, stop button
    ├── RolesPage.kt             # Role browser, creator, editor
    ├── SkillsPage.kt            # Skill browser, notes, promotion
    ├── ProfilePage.kt           # User profile facts + episode timeline
    ├── UserConfigPage.kt        # User config CRUD
    ├── SettingsPage.kt          # LLM config, virtual display, privileged server
    └── ClawTheme.kt             # Dark/light theme + accent color system
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

**Option A — Ask the agent** (recommended):

*"Create a skill that reads my battery level and tells me if I should charge."*

The agent calls `quick_skill`, generates a Python/HTTP skill, tests it, and registers it — all automatically. You can then promote it to permanent from the Skills page.

**Option B — Write it in Kotlin:**

```kotlin
class WeatherSkill : Skill {
    override val meta = SkillMeta(
        id             = "get_weather",
        description    = "Fetch current weather for a city",
        parameters     = listOf(
            SkillParam("city", "string", "City name", required = true)
        ),
        injectionLevel = 1,   // 0=always, 1=task-aware, 2=on-demand
    )

    override suspend fun execute(params: Map<String, Any>): SkillResult {
        val city = params["city"] as? String ?: return SkillResult.error("city required")
        return SkillResult.success(fetchWeatherApi(city))
    }
}
```

Register in `MainViewModel.registerBuiltinSkills()`.

**Option C — JSON definition** (HTTP or Python, no recompile):

Drop a `.json` file in the skill directory or use the `meta` skill at runtime. Supports both HTTP endpoints and inline Python (executed via Chaquopy on-device).

---

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=eggbrid2/mobileClaw&type=Date)](https://star-history.com/#eggbrid2/mobileClaw&Date)

---

## 🤝 Contributing

```bash
git checkout -b feature/my-new-skill
git commit -m "feat: add battery monitor skill"
git push origin feature/my-new-skill
```

**Good first contributions:**

- 📦 New built-in skills for common apps (WhatsApp, YouTube, Maps, Calendar)
- 🎭 New role presets for specialized use cases
- 🌐 Additional LLM backends (Gemini native, Claude native)
- 🌍 Translations (`values-XX/strings.xml`)
- 🐛 ROM compatibility fixes and reports
- 📝 Mini-app templates and example tasks

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
