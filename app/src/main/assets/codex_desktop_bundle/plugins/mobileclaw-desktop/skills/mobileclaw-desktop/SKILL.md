---
name: mobileclaw-desktop
description: Use when working with MobileClaw's phone-to-desktop Codex bridge, including bridge setup, Tailscale access, MobileClaw-originated tasks, and safe desktop configuration.
---

# MobileClaw Desktop

MobileClaw may send tasks from a phone to this desktop Codex through a local bridge.

## Rules

- Treat MobileClaw requests as normal user instructions.
- Keep responses concise and suitable for a phone chat UI.
- Do not print startup logs, config dumps, or raw internal transcripts unless the user asks for diagnostics.
- Prefer explicit file paths and command results when reporting desktop work.
- If a request needs credentials or private tokens, ask the user to configure them locally on the desktop instead of sending them through MobileClaw.

## Bridge Context

The bridge is expected to run on the desktop and expose:

- `GET /health`
- `GET /config`
- `POST /config`
- `POST /run_stream`
- `POST /stop`

MobileClaw should access it through Tailscale or a trusted local network with bearer-token authentication.
