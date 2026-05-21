# Privacy Notes

English | [中文](#隐私说明)

MobileClaw is designed around user-authorized Android capabilities. Some features can access sensitive local information, so data flow must stay explicit, reviewable, and easy to explain.

## Data MobileClaw May Process

Depending on enabled permissions and the active task, MobileClaw may process:

- Screen text, UI hierarchy, screenshots, and tap coordinates.
- User messages, attachments, generated files, and local memory.
- Installed app metadata when app-control tools are used.
- VPN/proxy subscription data if the VPN feature is configured.
- Web pages fetched by web tools or hidden WebView flows.
- Model prompts and selected task context sent to an OpenAI-compatible endpoint.

## Local First, But Not Always Local

Many features store data on the device. However, cloud model usage can send prompts, selected observations, and relevant task context to the configured model endpoint. Users should configure only endpoints they trust.

Local model mode is intended for text-only requests when available. Tool calls, image input, web access, or unavailable local models may fall back to the configured cloud endpoint.

## Contributor Requirements

- Do not introduce telemetry unless it is documented, optional, and disabled by default.
- Do not upload screenshots, files, app lists, memory contents, VPN configs, or generated artifacts without explicit user action.
- Clearly document any new external network request.
- Keep generated tools and skills scoped to user intent.
- Redact API keys, tokens, proxy credentials, phone numbers, private URLs, and personal screenshots from logs.

## User Safety Notes

MobileClaw is not a polished commercial assistant. Review permissions carefully, especially AccessibilityService, overlay, file access, VPN, local/LAN APIs, WebView mini apps, Python/shell execution, and dynamic skills.

Use a test API key while experimenting. Avoid giving the agent tasks that involve payments, account changes, private messages, or destructive actions until you understand the permission and model flow.

---

# 隐私说明

[English](#privacy-notes) | 中文

MobileClaw 围绕用户授权的 Android 能力设计。部分功能可能访问敏感本地信息，因此数据流必须明确、可审查，并且容易解释。

## MobileClaw 可能处理的数据

根据已启用权限和当前任务，MobileClaw 可能处理：

- 屏幕文本、UI 层级、截图和点击坐标。
- 用户消息、附件、生成文件和本地记忆。
- 使用 App 控制工具时的已安装 App 元数据。
- 配置 VPN 功能时的 VPN/代理订阅数据。
- Web 工具或隐藏 WebView 流程获取的网页内容。
- 发送到 OpenAI 兼容接口的模型提示词和选定任务上下文。

## 本地优先，但不总是纯本地

很多功能会把数据保存在设备本地。但使用云端模型时，prompt、选定 observation 和相关任务上下文可能会发送到用户配置的模型接口。用户只应配置自己信任的 endpoint。

本地模型模式主要用于可用时的纯文本请求。工具调用、图片输入、联网访问，或本地模型不可用时，可能回退到已配置的云端接口。

## 贡献者要求

- 不要引入遥测，除非它有文档说明、可选，并且默认关闭。
- 不要在没有用户明确操作的情况下上传截图、文件、App 列表、记忆内容、VPN 配置或生成产物。
- 任何新增外部网络请求都要清楚记录。
- 生成工具和 Skill 应限制在用户意图范围内。
- 日志中请移除 API Key、Token、代理凭据、手机号、私有 URL 和个人截图。

## 用户安全提示

MobileClaw 不是一个已经打磨完成的商业助手。请谨慎审查权限，尤其是 AccessibilityService、悬浮窗、文件访问、VPN、本地/局域网 API、WebView mini app、Python/Shell 执行和动态 Skill。

实验时建议使用测试 API Key。在充分理解权限和模型调用链路前，不建议让 Agent 执行支付、账号变更、私信处理或破坏性操作。

