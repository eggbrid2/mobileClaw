# MobileClaw Roadmap

English | [中文](#mobileclaw-路线图)

This roadmap focuses on making MobileClaw easier to try, safer to extend, and more useful as an open Android agent ecosystem. Dates are intentionally not promised; the project should move by stability and reproducibility rather than marketing deadlines.

## 0.1.x: Make The Project Easy To Try

- Publish installable APKs from tagged GitHub Releases.
- Keep [docs/quickstart.md](docs/quickstart.md) current with build, install, permission, and model setup steps.
- Add short demo recordings for phone control, skill execution, AI Page generation, and VPN control.
- Collect ROM compatibility reports through GitHub issue templates.
- Document known limitations for AccessibilityService, screenshots, virtual display, foreground services, VPN, and WebView mini apps.
- Add at least three reproducible recipes under `docs/recipes`.

## 0.2.x: Make Contributions Repeatable

- Add focused tests around task classification, tool policy, skill loading, dynamic skill parsing, and VPN config parsing.
- Define a stable skill package format and review checklist.
- Add sample dynamic Python and HTTP skills.
- Track known device and ROM behavior in public documentation.
- Add a contributor-facing debugging guide for screen reading, action execution, and task logs.

## 0.3.x: Make Skills An Ecosystem

- Improve skill search, installation, review, and promotion flows.
- Show provenance and permission summaries for installed skills.
- Add a documented submission path for ClawHub-compatible skills.
- Provide example roles and task recipes for phone operators, coders, researchers, creators, and VPN operators.
- Make skill failures easier to inspect and reproduce.

## Later

- Better VLM grounding and action verification.
- Better long-running task recovery and interruption.
- More reliable background virtual-display support across ROMs.
- Stronger local-model routing for text-only tasks.
- More complete privacy and security controls for screen content, files, generated tools, and local/LAN APIs.

---

# MobileClaw 路线图

[English](#mobileclaw-roadmap) | 中文

这份路线图的重点是让 MobileClaw 更容易体验、更安全地扩展，并逐步形成开放的 Android Agent 生态。这里不会承诺具体日期；项目应该按稳定性和可复现性推进，而不是按宣传节点推进。

## 0.1.x：让项目容易试用

- 通过 GitHub tag release 发布可安装 APK。
- 持续维护 [docs/quickstart.md](docs/quickstart.md)，覆盖构建、安装、权限和模型配置。
- 增加手机控制、Skill 执行、AI Page 生成、VPN 控制的短演示视频。
- 通过 GitHub Issue 模板收集 ROM 兼容性报告。
- 记录 AccessibilityService、截图、虚拟屏、前台服务、VPN、WebView mini app 的已知限制。
- 在 `docs/recipes` 下至少提供三个可复现 recipe。

## 0.2.x：让贡献可复现

- 为任务分类、工具策略、Skill 加载、动态 Skill 解析、VPN 配置解析补充聚焦测试。
- 定义稳定的 Skill 包格式和审查清单。
- 增加动态 Python Skill 和 HTTP Skill 示例。
- 在公开文档中持续记录设备和 ROM 行为。
- 增加面向贡献者的调试指南，覆盖读屏、动作执行和任务日志。

## 0.3.x：让 Skill 成为生态

- 改进 Skill 搜索、安装、审查和提升流程。
- 为已安装 Skill 展示来源和权限摘要。
- 增加 ClawHub 兼容 Skill 的提交路径说明。
- 提供手机操作员、代码专家、研究助手、创作助手、VPN 操作员等角色和任务 recipe 示例。
- 让 Skill 失败更容易检查和复现。

## 更长期

- 更好的 VLM 定位和动作验证。
- 更可靠的长任务恢复和中断。
- 更稳定的跨 ROM 后台虚拟屏支持。
- 更强的纯文本任务本地模型路由。
- 更完整的屏幕内容、文件、生成工具、本地/局域网 API 隐私和安全控制。

