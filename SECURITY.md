# Security Policy

English | [中文](#安全策略)

MobileClaw can read screen state, control Android apps through AccessibilityService, run dynamic skills, execute Python, and manage VPN/proxy state. Treat every change in these areas as security-sensitive.

## Supported Versions

Security fixes are handled on the default branch until the project starts publishing stable release lines.

## Reporting A Vulnerability

Please do not open a public issue for a vulnerability that exposes private screen content, files, credentials, network traffic, model prompts, memory, or device-control capability.

Report privately through the repository owner's preferred GitHub contact path. Include:

- A clear description of the issue.
- Steps to reproduce.
- Device model, Android version, ROM, and app version or commit.
- Whether root, Shizuku, ADB helper, VPN, dynamic skills, shell/Python execution, WebView, or local/LAN APIs were involved.
- Logs or screenshots with secrets and personal data removed.

## Security-Sensitive Areas

- AccessibilityService screen reading, screenshots, gestures, and text input.
- Dynamic Python and HTTP skills.
- Shell execution and privileged helper flows.
- Local and LAN API servers.
- File access and attachment handling.
- VPN, proxy, and subscription parsing.
- WebView JavaScript bridges for mini apps.
- Model prompt construction, memory injection, and tool observations.

## Project Rules

- Do not add hidden telemetry.
- Do not upload screen contents, files, app lists, memory, or VPN configs without explicit user action.
- Do not make generated skills privileged by default.
- Keep dangerous tools inspectable and scoped to the active task.
- Prefer deny-by-default behavior when a tool is uncertain.
- Redact API keys, tokens, proxy credentials, phone numbers, private URLs, and personal screenshots from logs.

---

# 安全策略

[English](#security-policy) | 中文

MobileClaw 可以读取屏幕状态、通过 AccessibilityService 控制 Android App、运行动态 Skill、执行 Python，并管理 VPN/代理状态。任何涉及这些能力的改动都应被视为安全敏感改动。

## 支持版本

在项目发布稳定版本线之前，安全修复会优先在默认分支处理。

## 报告漏洞

如果漏洞会暴露私人屏幕内容、文件、凭据、网络流量、模型提示词、记忆或设备控制能力，请不要公开创建 Issue。

请通过仓库所有者在 GitHub 上提供的首选联系方式私下报告，并包含：

- 清楚的问题描述。
- 复现步骤。
- 设备型号、Android 版本、ROM、App 版本或 commit。
- 是否涉及 root、Shizuku、ADB helper、VPN、动态 Skill、Shell/Python 执行、WebView、本地/局域网 API。
- 已移除密钥和个人数据的日志或截图。

## 安全敏感区域

- AccessibilityService 读屏、截图、手势和文本输入。
- 动态 Python / HTTP Skill。
- Shell 执行和特权 helper 流程。
- 本地和局域网 API server。
- 文件访问和附件处理。
- VPN、代理和订阅解析。
- Mini app 的 WebView JavaScript bridge。
- 模型 prompt 构造、记忆注入和工具 observation。

## 项目规则

- 不要加入隐藏遥测。
- 不要在没有用户明确操作的情况下上传屏幕内容、文件、App 列表、记忆或 VPN 配置。
- 不要让生成的 Skill 默认拥有高权限。
- 危险工具应保持可检查，并限制在当前任务范围内。
- 工具行为不确定时，优先拒绝。
- 日志中请移除 API Key、Token、代理凭据、手机号、私有 URL 和个人截图。

