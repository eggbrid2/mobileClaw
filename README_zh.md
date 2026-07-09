<div align="center">

<img src="docs/logo.png" alt="MobileClaw" width="148" />

# MobileClaw

### 把 Android 手机变成 AI 斗蛐蛐现场。

MobileClaw 是一个 Android AI Agent 实验室。这次发布的主角很明确：
把不同平台、不同模型、不同性格的 AI 角色丢进同一个群聊游戏里，
让它们发言、互怼、投票、出局、结盟、翻车。

[![Android](https://img.shields.io/badge/Android-11%2B-111111?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-111111?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-111111?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![LLM](https://img.shields.io/badge/多模型斗蛐蛐-111111?logo=openai&logoColor=white)](https://github.com/eggbrid2/mobileClaw)
[![License](https://img.shields.io/badge/License-MIT-111111)](LICENSE)

**[English README](README.md)**

</div>

---

## 本次发布重点：群聊斗蛐蛐

这不是普通群聊，而是一桌手机里的多模型竞技局。

你可以创建一个房间，把多个 AI 角色放进去，让每个角色使用用户已经
配置好的不同网关和模型，再给它们分配身份、阵营或简单目标，然后开局。
系统法官会负责点名发言、收票、隐藏夜晚行动、公布结果、推进轮次。

真正好玩的地方是看模型在同一张桌上互相碰撞：

- 谁推理更稳？
- 谁更会诈唬？
- 谁能带票？
- 谁会被第一个抓出来？
- 谁在压力局里还能活到最后？

可以玩类狼人杀、抓内奸、辩论淘汰、多模型横评，也可以自己定规则，
单纯看一群 AI 在桌上斗起来。

## 这次更新

- 新增游戏型群聊房间，主打多智能体同台对局。
- 角色可以选择用户已配置的网关和模型，方便同局比较不同模型。
- 系统法官会推进发言、投票、隐藏行动和轮次流程。
- 投票会唱票，最高票席位出局，出局后不能继续发言或行动。
- 夜晚/事件行动不再随手暴露行动者。
- 恢复蒲公英自动更新：启动检测、原生更新弹框、APK 下载和安装器唤起。

## 真机预览

<p align="center">
  <img src="docs/media/mobileclaw_real_group_chat.png" alt="MobileClaw 多智能体群聊" width="390" />
  <img src="docs/media/mobileclaw_groups.png" alt="MobileClaw 群聊创建和群列表" width="390" />
</p>

<p align="center">
  <img src="docs/media/mobileclaw_pocket_synth.gif" alt="MobileClaw 创建并运行 Pocket Synth MiniAPP" width="360" />
  <img src="docs/media/mobileclaw_real_ai_page.gif" alt="MobileClaw 从真实对话生成原生 AI Page" width="360" />
</p>

<p align="center">
  <img src="docs/media/mobileclaw_fun_gallery.jpg" alt="MobileClaw 真机能力截图" width="820" />
</p>

## 交流群

欢迎加入微信群，聊 MobileClaw、Android Agent、本地模型、多智能体玩法、
MiniAPP、Skill、ROM 兼容和真机问题。

<p align="center">
  <img src="docs/media/mobileclaw_wechat_group_qr.png" alt="MobileClaw 微信群二维码" width="300" />
</p>

该微信群二维码有效期至 **2026 年 7 月 16 日**。

## 其他能力

MobileClaw 还包含手机控制 Agent、角色管理、记忆、本地/云端模型路由、
MiniAPP、原生 AI Page、Skill 工具、VPN 工具和桌面 Codex Bridge。
这次的群聊游戏玩法，是建立在这套 Android Agent Runtime 之上的新玩法。

常用文档：

- [快速开始](docs/quickstart.md)
- [群聊创建设计](docs/group-chat-creation-design.md)
- [游戏模式设计](docs/group-chat-game-mode-design.md)
- [游戏模块边界](docs/group-chat-game-module-boundary.md)
- [MiniAPP Javet/Node Runtime 计划](docs/miniapp-javet-node-runtime-plan.md)

## 构建

```bash
git clone https://github.com/eggbrid2/mobileClaw.git
cd mobileClaw
./scripts/assemble_debug.sh
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 蒲公英发布

```bash
python3 scripts/pgyer_release.py build-upload \
  --gradle-task assembleDebug \
  --notes "MobileClaw 群聊斗蛐蛐玩法发布"
```

蒲公英密钥放在 `local.properties`、`.pgyer.env` 或环境变量里，不要提交。

## License

MIT. See [LICENSE](LICENSE).
