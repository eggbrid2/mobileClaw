# Recipe: Phone Control Smoke Test

English | [中文](#recipe手机控制冒烟测试)

Use this recipe to verify basic screen reading, app launch, tapping, and result verification on a device.

## Device Notes

Record these before testing:

- Device:
- Android version:
- ROM:
- MobileClaw version or commit:
- Root, Shizuku, or ADB helper:
- Permissions granted:
- Screen resolution:

## Task 1: Read Current Screen

Prompt:

```text
Look at the current screen and summarize what page I am on. Do not tap anything.
```

Expected result:

- The agent describes the visible app or launcher.
- No tap, scroll, or navigation happens.

Failure signals:

- The agent reports an unrelated screen.
- The agent taps or navigates despite the instruction.
- Accessibility XML is empty and screenshot fallback also fails.

## Task 2: Open An App

Prompt:

```text
Open Calculator, wait until it is visible, and tell me when it is ready.
```

Expected result:

- Calculator opens.
- The agent verifies the screen before responding.

Failure signals:

- App launch fails.
- The wrong app opens.
- The agent responds before observing the launched app.

## Task 3: Perform A Simple Action

Prompt:

```text
In Calculator, calculate 23 + 19 and tell me the verified result.
```

Expected result:

- The app shows `42`.
- The agent reports the result after observing the screen.

Failure signals:

- Taps miss keys.
- Text input or gestures do not work.
- The agent reports a result without verifying the screen.

## Failure Report

If the test fails, include:

- Which task failed.
- Whether XML reading, screenshot reading, app launch, tap, scroll, or input failed.
- The visible screen before and after the failure.
- Relevant logs with secrets removed.
- Device model, Android version, ROM, and permission state.

---

# Recipe：手机控制冒烟测试

[English](#recipe-phone-control-smoke-test) | 中文

使用这个 recipe 验证设备上的基础读屏、启动 App、点击和结果校验能力。

## 设备信息

测试前记录：

- 设备：
- Android 版本：
- ROM：
- MobileClaw 版本或 commit：
- Root、Shizuku 或 ADB helper：
- 已授予权限：
- 屏幕分辨率：

## 任务 1：读取当前屏幕

提示词：

```text
Look at the current screen and summarize what page I am on. Do not tap anything.
```

预期结果：

- Agent 描述当前可见 App 或桌面。
- 不发生点击、滑动或导航。

失败信号：

- Agent 报告了不相关的屏幕。
- Agent 在明确要求不点击的情况下进行了点击或导航。
- Accessibility XML 为空，截图兜底也失败。

## 任务 2：打开 App

提示词：

```text
Open Calculator, wait until it is visible, and tell me when it is ready.
```

预期结果：

- 计算器打开。
- Agent 在回复前先观察并确认屏幕。

失败信号：

- App 启动失败。
- 打开了错误 App。
- Agent 没有观察已启动 App 就直接回复。

## 任务 3：执行简单动作

提示词：

```text
In Calculator, calculate 23 + 19 and tell me the verified result.
```

预期结果：

- App 显示 `42`。
- Agent 观察屏幕后报告结果。

失败信号：

- 点击按键偏移。
- 文本输入或手势不可用。
- Agent 没有验证屏幕就报告结果。

## 失败报告

如果测试失败，请包含：

- 哪个任务失败。
- 是 XML 读取、截图读取、App 启动、点击、滑动还是输入失败。
- 失败前后的可见屏幕。
- 已移除密钥的相关日志。
- 设备型号、Android 版本、ROM 和权限状态。

