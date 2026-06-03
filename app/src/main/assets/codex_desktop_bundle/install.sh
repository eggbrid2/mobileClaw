#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_DIR="${MOBILECLAW_CODEX_ROOT:-$HOME/.mobileclaw/codex-desktop}"
TOKEN="${CODEX_BRIDGE_TOKEN:-}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required." >&2
  exit 1
fi

if ! command -v codex >/dev/null 2>&1; then
  echo "codex CLI is required. Install and authenticate Codex on this computer first." >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
cp -R "$SOURCE_DIR/." "$TARGET_DIR/"
chmod +x "$TARGET_DIR/start_bridge.sh"
chmod +x "$TARGET_DIR/install.sh"

if [ -n "$TOKEN" ]; then
  printf "%s" "$TOKEN" > "$TARGET_DIR/.bridge_token"
  chmod 600 "$TARGET_DIR/.bridge_token"
fi

if command -v codex >/dev/null 2>&1; then
  codex plugin marketplace add "$TARGET_DIR" >/dev/null 2>&1 || true
  codex plugin add mobileclaw-desktop --marketplace mobileclaw-local >/dev/null 2>&1 || true
fi

cat <<EOF
MobileClaw desktop bundle installed.

Location:
  $TARGET_DIR

Start bridge:
  CODEX_BRIDGE_TOKEN=<token> "$TARGET_DIR/start_bridge.sh"

Default bridge URL over Tailscale:
  http://<computer-tailscale-ip>:52734
EOF
