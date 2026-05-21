# Recipe: Small Skill Contribution

English | [中文](#recipe小型-skill-贡献)

MobileClaw skills should be narrow, inspectable, and easy to disable. A good skill does one thing well and makes its data flow obvious.

## A Good Skill

- Has one clear purpose.
- Accepts explicit inputs.
- Returns structured, understandable output.
- Avoids hidden network requests.
- Avoids broad file, shell, or device privileges unless the user explicitly asks.
- Fails with a useful error message.
- Can remain on-demand instead of always injected.

## Contribution Checklist

- Skill name:
- Problem solved:
- Inputs:
- Output:
- External network requests:
- File access:
- Device permissions:
- Model calls:
- Failure behavior:
- Example prompt:
- Example result:
- Why this should be built in, listed in a market, or kept as a local custom skill:

## Review Questions

- What data can the skill read?
- What data can it send out?
- Can the user understand what it will do before running it?
- What happens on timeout, malformed input, or missing permissions?
- Does it need to be promoted, or can it stay on-demand?

## Example Prompt

```text
Create a skill that fetches a JSON endpoint and extracts the top-level keys.
```

Expected review notes:

- The skill should only access the URL provided by the user.
- It should use a timeout.
- It should not log the full response if it may contain secrets.
- It should return a concise list of keys and a clear error on invalid JSON.

---

# Recipe：小型 Skill 贡献

[English](#recipe-small-skill-contribution) | 中文

MobileClaw Skill 应该保持窄范围、可检查、容易禁用。好的 Skill 只做好一件事，并让数据流清楚可见。

## 好的 Skill

- 有一个明确目的。
- 接收显式输入。
- 返回结构化、可理解的输出。
- 避免隐藏网络请求。
- 除非用户明确需要，否则避免宽泛的文件、Shell 或设备权限。
- 失败时返回有用错误。
- 可以保持按需加载，而不是默认常驻注入。

## 贡献清单

- Skill 名称：
- 解决的问题：
- 输入：
- 输出：
- 外部网络请求：
- 文件访问：
- 设备权限：
- 模型调用：
- 失败行为：
- 示例提示词：
- 示例结果：
- 为什么它应该内置、进入市场，或只作为本地自定义 Skill：

## 审查问题

- 这个 Skill 可以读取什么数据？
- 它可以向外发送什么数据？
- 用户运行前能否理解它会做什么？
- 超时、输入格式错误或缺少权限时会发生什么？
- 它需要提升为常驻能力，还是可以保持按需加载？

## 示例提示词

```text
Create a skill that fetches a JSON endpoint and extracts the top-level keys.
```

预期审查要点：

- Skill 只应访问用户提供的 URL。
- 应设置超时。
- 如果响应可能包含密钥，不应记录完整响应。
- 应返回简洁 key 列表，并在 JSON 无效时给出清楚错误。

