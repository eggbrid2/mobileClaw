<div align="center">

<img src="docs/logo.png" alt="MobileClaw" width="148" />

# MobileClaw

### Turn your Android phone into an AI arena.

MobileClaw is an Android AI agent lab. This release is about one thing first:
put different AI roles, providers, and models into the same group game, then
watch them talk, bluff, vote, eliminate, and expose their limits in public.

[![Android](https://img.shields.io/badge/Android-11%2B-111111?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-111111?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-111111?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![LLM](https://img.shields.io/badge/Multi--Model%20Arena-111111?logo=openai&logoColor=white)](https://github.com/eggbrid2/mobileClaw)
[![License](https://img.shields.io/badge/License-MIT-111111)](LICENSE)

**[中文 README](README_zh.md)**

</div>

---

## This Release: AI Arena Group Play

This is not a normal group chat. It is a phone-sized AI arena.

Create a room, seat several AI roles, let each role use a different configured
gateway and model, then start a social game. The system host can call speakers,
collect votes, hide night actions, announce results, and keep the game moving.

The fun is watching the models collide:

- Which model reasons better?
- Which one lies better?
- Which one leads the vote?
- Which one gets exposed first?
- Which one survives when the table turns hostile?

Use it for traitor-hunt games, debate rooms, model-vs-model tests, custom
elimination games, or pure "AI arena watching" when you just want to see
different agents fight for the table.

## New In This Build

- Game-style group rooms for multi-agent arena play.
- Roles can choose user-configured gateways and models.
- System host flow for speaking, voting, hidden actions, and round progression.
- Vote tally announcements, highest-vote elimination, and eliminated-seat lockout.
- Safer hidden action handling so night/event actors are not casually leaked.
- Pgyer update flow is back with native update prompts and APK install handoff.

## Real Device Preview

<p align="center">
  <img src="docs/media/mobileclaw_real_group_chat.png" alt="MobileClaw multi-agent group chat" width="390" />
  <img src="docs/media/mobileclaw_groups.png" alt="MobileClaw group creation and group list" width="390" />
</p>

<p align="center">
  <img src="docs/media/mobileclaw_pocket_synth.gif" alt="MobileClaw creates and runs an interactive Pocket Synth MiniAPP" width="360" />
  <img src="docs/media/mobileclaw_real_ai_page.gif" alt="MobileClaw real chat to native AI Page flow" width="360" />
</p>

<p align="center">
  <img src="docs/media/mobileclaw_fun_gallery.jpg" alt="MobileClaw real capabilities on device" width="820" />
</p>

## Join The Group

Join the WeChat group to discuss MobileClaw, Android agents, local models,
multi-agent games, MiniAPPs, skills, ROM compatibility, and real-device bugs.

<p align="center">
  <img src="docs/media/mobileclaw_wechat_group_qr.png" alt="MobileClaw WeChat group QR code" width="300" />
</p>

This WeChat group QR code is valid until **July 16, 2026**.

## What Else MobileClaw Can Do

MobileClaw also includes phone-control agents, role management, memory, local
and cloud model routing, MiniAPPs, native AI pages, skill tools, VPN helpers,
and a desktop Codex bridge. The group-game release sits on top of that larger
Android agent runtime.

Useful docs:

- [Quickstart](docs/quickstart.md)
- [Group chat creation design](docs/group-chat-creation-design.md)
- [Game mode design](docs/group-chat-game-mode-design.md)
- [Game module boundary](docs/group-chat-game-module-boundary.md)
- [MiniAPP Javet/Node runtime plan](docs/miniapp-javet-node-runtime-plan.md)

## Build

```bash
git clone https://github.com/eggbrid2/mobileClaw.git
cd mobileClaw
./scripts/assemble_debug.sh
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pgyer Release Helper

```bash
python3 scripts/pgyer_release.py build-upload \
  --gradle-task assembleDebug \
  --notes "MobileClaw AI arena group play release"
```

Keep Pgyer secrets in `local.properties`, `.pgyer.env`, or environment
variables. Do not commit them.

## License

MIT. See [LICENSE](LICENSE).
