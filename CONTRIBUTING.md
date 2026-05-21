# Contributing To MobileClaw

English | [中文](#贡献-mobileclaw)

MobileClaw is an experimental Android AI agent runtime. The most valuable contributions are small, reproducible, and easy to review. A report that explains one ROM problem clearly is often more useful than a large unbounded patch.

## What We Need Most

- ROM compatibility reports for AccessibilityService, screenshots, virtual display, foreground services, and VPN.
- Reproducible phone-control recipes with the target app, Android version, task prompt, expected result, and failure mode.
- Small skills or role presets that solve one clear task.
- Focused fixes for one Android permission, model, VPN, WebView, or automation edge case.
- Documentation, screenshots, short recordings, and setup notes.

## Before You Start

1. Read [docs/quickstart.md](docs/quickstart.md).
2. Search open issues for similar reports.
3. Keep changes scoped. Avoid mixing runtime behavior, UI polish, and documentation in one PR.
4. Read [SECURITY.md](SECURITY.md) before touching screen content, dynamic skills, shell/Python execution, VPN, local APIs, or WebView bridges.

## Development Setup

Requirements:

- Android Studio Ladybug or newer
- JDK 21
- Android 11+ device or emulator
- Python 3.11 available to Chaquopy
- An OpenAI-compatible chat endpoint and API key for cloud-model testing

Build:

```bash
./gradlew :app:assembleDebug
```

Run available checks:

```bash
./gradlew test
```

Use a real device for phone-control behavior. Emulators are useful for basic UI checks, but OEM ROM behavior is often different.

## Pull Request Expectations

Every PR should include:

- What changed and why.
- How you tested it.
- Device model, Android version, ROM, and whether root or an ADB helper was used for phone-control changes.
- Screenshots or a short recording for UI, automation, and permission-flow changes.
- Known limitations, especially around AccessibilityService, screenshots, virtual display, VPN, WebView, dynamic skills, and local/LAN APIs.

## Code And Design Guidelines

- Follow the existing Kotlin and Jetpack Compose style.
- Prefer explicit task boundaries and inspectable behavior over hidden automation.
- Keep new skills narrow. A small tool with clear inputs and outputs is easier to review and safer to promote.
- Keep dangerous tools disabled or on-demand unless there is a strong reason.
- Do not add telemetry or upload screen contents without explicit user action and documentation.
- Redact API keys, tokens, proxy credentials, phone numbers, private URLs, and personal screenshots from logs.

## Good Issue Reports

A useful issue usually includes:

- MobileClaw release or commit.
- Device model, Android version, and ROM.
- Permissions granted.
- Whether root, Shizuku, or ADB helper was used.
- The exact task prompt.
- Expected behavior and actual behavior.
- Logs, screenshots, or recordings with private data removed.

Use the ROM compatibility template for device-specific behavior.

---

# 贡献 MobileClaw

[English](#contributing-to-mobileclaw) | 中文

MobileClaw 是一个实验性的 Android AI Agent Runtime。这个项目最需要的是小而可复现、容易审查的贡献。一个清楚说明某个 ROM 问题的报告，通常比一个范围过大的补丁更有价值。

## 当前最需要的贡献

- AccessibilityService、截图、虚拟屏、前台服务、VPN 的 ROM 兼容性报告。
- 可复现的手机控制 recipe：目标 App、Android 版本、任务提示词、预期结果、失败现象。
- 能解决单一明确问题的小型 Skill 或角色预设。
- 针对某个 Android 权限、模型、VPN、WebView 或自动化边界问题的聚焦修复。
- 文档、截图、短视频和安装配置说明。

## 开始之前

1. 阅读 [docs/quickstart.md](docs/quickstart.md)。
2. 搜索已有 Issue，确认是否有人报告过类似问题。
3. 控制改动范围。不要把运行时行为、UI 打磨和文档改动混在同一个 PR 里。
4. 如果会碰到屏幕内容、动态 Skill、Shell/Python 执行、VPN、本地 API 或 WebView Bridge，请先阅读 [SECURITY.md](SECURITY.md)。

## 开发环境

要求：

- Android Studio Ladybug 或更新版本
- JDK 21
- Android 11+ 设备或模拟器
- Chaquopy 可用的 Python 3.11
- 用于云端模型测试的 OpenAI 兼容 Chat 接口和 API Key

构建：

```bash
./gradlew :app:assembleDebug
```

运行已有检查：

```bash
./gradlew test
```

手机控制行为请尽量使用真机测试。模拟器适合做基础 UI 检查，但 OEM ROM 的行为经常不同。

## PR 要求

每个 PR 应包含：

- 改了什么，为什么改。
- 如何测试。
- 如果涉及手机控制：设备型号、Android 版本、ROM，以及是否使用 root 或 ADB helper。
- 如果涉及 UI、自动化或权限流程：截图或短录屏。
- 已知限制，尤其是 AccessibilityService、截图、虚拟屏、VPN、WebView、动态 Skill、本地/局域网 API。

## 代码和设计准则

- 遵循现有 Kotlin 和 Jetpack Compose 风格。
- 优先保持任务边界明确、行为可检查，避免隐藏自动化。
- 新 Skill 应保持窄范围。输入输出清楚的小工具更容易审查，也更安全。
- 危险工具默认应保持禁用或按需加载，除非有充分理由。
- 不要加入隐藏遥测，也不要在没有用户明确操作和文档说明的情况下上传屏幕内容。
- 日志中请移除 API Key、Token、代理凭据、手机号、私有 URL 和个人截图。

## 好的 Issue 报告

有效报告通常包含：

- MobileClaw release 或 commit。
- 设备型号、Android 版本、ROM。
- 已授予的权限。
- 是否使用 root、Shizuku 或 ADB helper。
- 完整任务提示词。
- 预期行为和实际行为。
- 已移除隐私信息的日志、截图或录屏。

设备相关问题请优先使用 ROM 兼容性模板。

