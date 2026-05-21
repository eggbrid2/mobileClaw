# Recipe: ROM Compatibility Report

English | [中文](#reciperom-兼容性报告)

Android automation behavior differs across OEM ROMs. This checklist helps contributors report useful compatibility data.

## Device

- Device model:
- Android version:
- ROM name and version:
- Security patch level:
- MobileClaw release or commit:

## Permissions And Helpers

- AccessibilityService enabled:
- Screenshot permission granted:
- Notification permission granted:
- Overlay permission granted:
- Battery optimization disabled:
- VPN permission granted:
- Root available:
- Shizuku available:
- ADB helper used:

## Feature Results

| Feature | Works | Notes |
| --- | --- | --- |
| Screen XML reading |  |  |
| Screenshot capture |  |  |
| Tap gesture |  |  |
| Scroll gesture |  |  |
| Text input |  |  |
| App launch |  |  |
| Virtual display launch |  |  |
| Background screenshot |  |  |
| VPN start/stop |  |  |
| WebView mini app |  |  |
| AI Page rendering |  |  |

## Reproduction

```text
Task prompt:

Expected:

Actual:
```

Attach screenshots or short recordings when possible. Remove private data first.

## Useful Notes

- Mention any OEM permission page that had to be changed.
- Mention whether battery optimization, autostart, floating window, or background activity settings affected behavior.
- If virtual display fails, include the exact error message if available.
- If gestures miss, include screen resolution and display scaling settings.

---

# Recipe：ROM 兼容性报告

[English](#recipe-rom-compatibility-report) | 中文

Android 自动化行为会因 OEM ROM 而不同。这个清单帮助贡献者提交有用的兼容性数据。

## 设备

- 设备型号：
- Android 版本：
- ROM 名称和版本：
- 安全补丁级别：
- MobileClaw release 或 commit：

## 权限和 helper

- AccessibilityService 已启用：
- 截图权限已授予：
- 通知权限已授予：
- 悬浮窗权限已授予：
- 电池优化已关闭：
- VPN 权限已授予：
- Root 可用：
- Shizuku 可用：
- 使用 ADB helper：

## 功能结果

| 功能 | 是否可用 | 备注 |
| --- | --- | --- |
| 屏幕 XML 读取 |  |  |
| 截图捕获 |  |  |
| 点击手势 |  |  |
| 滑动手势 |  |  |
| 文本输入 |  |  |
| App 启动 |  |  |
| 虚拟屏启动 |  |  |
| 后台截图 |  |  |
| VPN 启停 |  |  |
| WebView mini app |  |  |
| AI Page 渲染 |  |  |

## 复现信息

```text
任务提示词：

预期：

实际：
```

可以附截图或短录屏，但请先移除隐私数据。

## 有用备注

- 说明是否修改过任何 OEM 权限页。
- 说明电池优化、自启动、悬浮窗、后台活动设置是否影响行为。
- 如果虚拟屏失败，请尽量提供准确错误信息。
- 如果点击偏移，请提供屏幕分辨率和显示缩放设置。

