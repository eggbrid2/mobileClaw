# MobileClaw Codex Desktop Bundle

This bundle is pushed by MobileClaw to a computer that already has:

- Codex CLI installed and authenticated.
- Tailscale installed.
- Tailscale SSH or normal SSH enabled.

It installs a small local Codex bridge and a local Codex plugin.

## Install

```bash
CODEX_BRIDGE_TOKEN=<token> bash install.sh
```

## Start

```bash
CODEX_BRIDGE_TOKEN=<token> ~/.mobileclaw/codex-desktop/start_bridge.sh
```

MobileClaw should then use:

```text
http://<computer-tailscale-ip>:52734
```

with the same bearer token.
