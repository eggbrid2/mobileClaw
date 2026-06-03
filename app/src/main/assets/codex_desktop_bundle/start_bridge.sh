#!/usr/bin/env bash
set -euo pipefail

ROOT="${MOBILECLAW_CODEX_ROOT:-$HOME/.mobileclaw/codex-desktop}"
TOKEN="${CODEX_BRIDGE_TOKEN:-}"
PORT="${CODEX_BRIDGE_PORT:-52734}"
HOST="${CODEX_BRIDGE_HOST:-0.0.0.0}"

if [ -z "$TOKEN" ] && [ -f "$ROOT/.bridge_token" ]; then
  TOKEN="$(cat "$ROOT/.bridge_token")"
fi

if [ -z "$TOKEN" ]; then
  echo "CODEX_BRIDGE_TOKEN is required. Pass it as an env var or create $ROOT/.bridge_token." >&2
  exit 1
fi

export CODEX_BRIDGE_TOKEN="$TOKEN"
export CODEX_BRIDGE_PORT="$PORT"
export CODEX_BRIDGE_HOST="$HOST"
export CODEX_BRIDGE_CONFIG="${CODEX_BRIDGE_CONFIG:-$HOME/.mobileclaw_codex_bridge.json}"
export CODEX_BRIDGE_COMMAND="${CODEX_BRIDGE_COMMAND:-codex exec --dangerously-bypass-approvals-and-sandbox}"

exec python3 "$ROOT/bin/mobileclaw-codex-bridge.py"
