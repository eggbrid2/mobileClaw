# MobileClaw Quickstart

English | [中文](#mobileclaw-快速开始)

This guide helps you build the app, install a debug APK, grant the minimum required permissions, and run a first safe Android agent task.

## 1. Requirements

- Android Studio Ladybug or newer
- JDK 21
- Android 11+ phone or emulator
- Python 3.11 available for Chaquopy builds
- An OpenAI-compatible chat endpoint and API key

Use a real device when testing phone control. Emulators are useful for basic UI checks, but OEM ROM restrictions often differ.

## 2. Build

```bash
git clone https://github.com/eggbrid2/mobileClaw.git
cd mobileClaw
./gradlew :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 3. Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the install fails, check that USB debugging is enabled and that your device allows installs from ADB.

## 4. Configure A Model

Open MobileClaw settings and configure an OpenAI-compatible endpoint and API key.

Use a test key while experimenting. Avoid using a production key while testing dynamic skills, WebView tools, phone automation, or VPN flows.

## 5. Grant Only The Permissions You Need

Common permissions:

- AccessibilityService: screen reading, gestures, text input, and app automation.
- Notification: foreground status and AI Page notifications.
- Overlay/background permissions: long-running visual assistant behavior.
- VPN: only needed for VPN/proxy features.
- File/media access: only needed for attachments and user storage tools.

Root is not required for basic use. Some background virtual-display flows may need ROM-specific settings, root, or an ADB-activated helper.

## 6. First Safe Tasks

Start with an observation-only task:

```text
Look at the current screen and summarize what page I am on. Do not tap anything.
```

Then try a bounded action:

```text
Open Calculator and calculate 23 + 19. Tell me the result after you verify the screen.
```

Good first tasks have visible results and low risk. Avoid payments, account changes, private messages, or destructive actions while testing.

## 7. Troubleshooting

- If screen reading fails, confirm AccessibilityService is enabled and try a raw screenshot task.
- If taps miss targets, include the screen screenshot and device resolution in your report.
- If virtual display launch fails, report the ROM and whether root or ADB helper was used.
- If VPN fails, include subscription type, Android version, and whether the VPN permission dialog appeared.
- If a model call fails, confirm endpoint URL, API key, model name, and network proxy settings.

## 8. Report Device Results

Open a ROM compatibility issue and include:

- Device model
- Android version
- ROM name/version
- MobileClaw commit or release
- Permissions granted
- Whether root or ADB helper was used
- Task prompt
- Expected behavior
- Actual behavior

Use [docs/recipes/rom-compatibility-report.md](recipes/rom-compatibility-report.md) as a checklist.

---

# MobileClaw 快速开始

[English](#mobileclaw-quickstart) | 中文

这份指南帮助你构建 App、安装 debug APK、授予最小必要权限，并运行第一个安全的 Android Agent 任务。

## 1. 环境要求

- Android Studio Ladybug 或更新版本
- JDK 21
- Android 11+ 手机或模拟器
- Chaquopy 构建可用的 Python 3.11
- 一个 OpenAI 兼容 Chat 接口和 API Key

测试手机控制时建议使用真机。模拟器适合做基础 UI 检查，但 OEM ROM 的限制通常不同。

## 2. 构建

```bash
git clone https://github.com/eggbrid2/mobileClaw.git
cd mobileClaw
./gradlew :app:assembleDebug
```

debug APK 生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 3. 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

如果安装失败，请确认已开启 USB 调试，并且设备允许通过 ADB 安装应用。

## 4. 配置模型

打开 MobileClaw 设置页，配置 OpenAI 兼容 endpoint 和 API Key。

实验时建议使用测试 Key。测试动态 Skill、WebView 工具、手机自动化或 VPN 流程时，不建议直接使用生产 Key。

## 5. 只授予需要的权限

常见权限：

- AccessibilityService：读屏、手势、文本输入和 App 自动化。
- 通知：前台状态和 AI Page 通知。
- 悬浮窗/后台相关权限：长任务和视觉助手能力。
- VPN：仅 VPN/代理功能需要。
- 文件/媒体访问：仅附件和用户存储工具需要。

基础使用不需要 root。部分后台虚拟屏流程可能需要 ROM 设置、root 或 ADB 激活的 helper。

## 6. 第一个安全任务

从只观察不操作的任务开始：

```text
Look at the current screen and summarize what page I am on. Do not tap anything.
```

然后尝试一个有边界的动作：

```text
Open Calculator and calculate 23 + 19. Tell me the result after you verify the screen.
```

好的入门任务应该有可见结果且风险低。测试阶段请避免支付、账号变更、私信处理或破坏性操作。

## 7. 排查问题

- 如果读屏失败，确认 AccessibilityService 已启用，并尝试原始截图任务。
- 如果点击位置不准，报告中请附上截图和设备分辨率。
- 如果虚拟屏启动失败，请报告 ROM，以及是否使用 root 或 ADB helper。
- 如果 VPN 失败，请提供订阅类型、Android 版本，以及是否出现 VPN 授权弹窗。
- 如果模型调用失败，请确认 endpoint URL、API Key、模型名和网络代理设置。

## 8. 报告设备结果

创建 ROM 兼容性 Issue，并包含：

- 设备型号
- Android 版本
- ROM 名称和版本
- MobileClaw commit 或 release
- 已授予权限
- 是否使用 root 或 ADB helper
- 任务提示词
- 预期行为
- 实际行为

可以使用 [docs/recipes/rom-compatibility-report.md](recipes/rom-compatibility-report.md) 作为清单。

