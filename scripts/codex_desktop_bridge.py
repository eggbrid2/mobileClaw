#!/usr/bin/env python3
"""
Small LAN bridge for controlling desktop Codex from MobileClaw.

Start it on the computer that has Codex installed:

  CODEX_BRIDGE_TOKEN=change-me python3 scripts/codex_desktop_bridge.py

Optional environment:
  CODEX_BRIDGE_HOST=0.0.0.0
  CODEX_BRIDGE_PORT=52734
  CODEX_BRIDGE_COMMAND='codex exec --dangerously-bypass-approvals-and-sandbox'

MobileClaw settings:
  endpoint: http://<desktop-lan-ip>:52734
  token:    same value as CODEX_BRIDGE_TOKEN
"""

from __future__ import annotations

import json
import os
import shlex
import signal
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


HOST = os.environ.get("CODEX_BRIDGE_HOST", "0.0.0.0")
PORT = int(os.environ.get("CODEX_BRIDGE_PORT", "52734"))
TOKEN = os.environ.get("CODEX_BRIDGE_TOKEN", "")
COMMAND = os.environ.get("CODEX_BRIDGE_COMMAND", "codex exec --dangerously-bypass-approvals-and-sandbox")
MAX_OUTPUT = int(os.environ.get("CODEX_BRIDGE_MAX_OUTPUT", "12000"))
CONFIG_PATH = Path(os.environ.get("CODEX_BRIDGE_CONFIG", "~/.mobileclaw_codex_bridge.json")).expanduser()
SESSION_MAP_PATH = Path(os.environ.get("CODEX_BRIDGE_SESSION_MAP", "~/.mobileclaw_codex_sessions.json")).expanduser()
DEFAULT_CODEX_CONFIG: dict[str, str] = {
    "cwd": os.environ.get("CODEX_BRIDGE_CWD", ""),
    "model": os.environ.get("CODEX_BRIDGE_MODEL", ""),
    "provider": os.environ.get("CODEX_BRIDGE_PROVIDER", ""),
    "approval": os.environ.get("CODEX_BRIDGE_APPROVAL", "never"),
    "sandbox": os.environ.get("CODEX_BRIDGE_SANDBOX", "danger-full-access"),
}

active_process: subprocess.Popen[str] | None = None
active_lock = threading.Lock()
config_lock = threading.Lock()
session_lock = threading.Lock()
codex_config: dict[str, str] = DEFAULT_CODEX_CONFIG.copy()
codex_session_map: dict[str, str] = {}


def load_codex_config() -> None:
    global codex_config
    if not CONFIG_PATH.exists():
        return
    try:
        raw = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        if isinstance(raw, dict):
            codex_config = normalize_codex_config(raw)
    except Exception as exc:
        print(f"Failed to read bridge config: {exc}")


def load_codex_session_map() -> None:
    global codex_session_map
    if not SESSION_MAP_PATH.exists():
        return
    try:
        raw = json.loads(SESSION_MAP_PATH.read_text(encoding="utf-8"))
        if isinstance(raw, dict):
            codex_session_map = {
                str(key): str(value)
                for key, value in raw.items()
                if str(key).strip() and str(value).strip()
            }
    except Exception as exc:
        print(f"Failed to read Codex session map: {exc}")


def save_codex_config() -> None:
    CONFIG_PATH.write_text(json.dumps(codex_config, ensure_ascii=False, indent=2), encoding="utf-8")


def save_codex_session_map() -> None:
    SESSION_MAP_PATH.write_text(json.dumps(codex_session_map, ensure_ascii=False, indent=2), encoding="utf-8")


def normalize_codex_config(raw: dict[str, Any]) -> dict[str, str]:
    merged = DEFAULT_CODEX_CONFIG.copy()
    for key in ("cwd", "model", "provider", "approval", "sandbox"):
        value = str(raw.get(key, "")).strip()
        if value:
            merged[key] = value
    return merged


def request_codex_config(body: dict[str, Any]) -> dict[str, str]:
    raw = body.get("config")
    if isinstance(raw, dict):
        return normalize_codex_config({**codex_config, **raw})
    return normalize_codex_config({**codex_config, **body})


def response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


def authorized(handler: BaseHTTPRequestHandler) -> bool:
    expected = f"Bearer {TOKEN}"
    if TOKEN and handler.headers.get("Authorization") == expected:
        return True
    if TOKEN:
        response(handler, 401, {"ok": False, "output": "Unauthorized"})
        return False
    response(handler, 500, {"ok": False, "output": "CODEX_BRIDGE_TOKEN is required"})
    return False


def read_json(handler: BaseHTTPRequestHandler) -> dict[str, Any]:
    length = int(handler.headers.get("Content-Length", "0") or "0")
    if length <= 0:
        return {}
    return json.loads(handler.rfile.read(length).decode("utf-8"))


def mobile_session_id(body: dict[str, Any]) -> str:
    for key in ("mobile_session_id", "session_id", "sessionId"):
        value = str(body.get(key, "")).strip()
        if value:
            return value
    return ""


def split_exec_command() -> tuple[list[str], list[str]]:
    args = shlex.split(COMMAND)
    try:
        exec_index = args.index("exec")
    except ValueError:
        return args, []
    return args[: exec_index + 1], args[exec_index + 1 :]


def add_codex_exec_options(args: list[str], run_config: dict[str, str] | None = None) -> list[str]:
    cfg = normalize_codex_config({**codex_config, **(run_config or {})})
    args = list(args)
    if "--json" not in args:
        args.append("--json")
    if cfg["model"] and "--model" not in args and "-m" not in args:
        args += ["--model", cfg["model"]]
    if cfg["provider"]:
        args += ["-c", f'model_provider="{cfg["provider"]}"']
    if cfg["sandbox"] and "--sandbox" not in args and "-s" not in args and "--dangerously-bypass-approvals-and-sandbox" not in args:
        args += ["--sandbox", cfg["sandbox"]]
    if cfg["approval"] and "--dangerously-bypass-approvals-and-sandbox" not in args:
        args += ["-c", f'approval_policy="{cfg["approval"]}"']
    if cfg["approval"] == "never" and cfg["sandbox"] == "danger-full-access" and "--dangerously-bypass-approvals-and-sandbox" not in args:
        args.append("--dangerously-bypass-approvals-and-sandbox")
    return args


def codex_args(prompt: str, run_config: dict[str, str] | None = None, mobile_session: str = "") -> list[str]:
    prefix, command_options = split_exec_command()
    with session_lock:
        thread_id = codex_session_map.get(mobile_session, "") if mobile_session else ""
    options = add_codex_exec_options(command_options, run_config)
    if thread_id and prefix and prefix[-1] == "exec":
        return prefix + ["resume"] + options + [thread_id, prompt]
    return prefix + options + [prompt]


def remember_codex_thread(mobile_session: str, payload: dict[str, Any]) -> str:
    if payload.get("type") != "thread.started" or not mobile_session:
        return ""
    thread_id = str(payload.get("thread_id", "") or payload.get("threadId", "")).strip()
    if not thread_id:
        return ""
    with session_lock:
        if codex_session_map.get(mobile_session) != thread_id:
            codex_session_map[mobile_session] = thread_id
            save_codex_session_map()
    return thread_id


def remember_codex_thread_from_output(mobile_session: str, output: str) -> str:
    for line in output.splitlines():
        try:
            payload = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict):
            thread_id = remember_codex_thread(mobile_session, payload)
            if thread_id:
                return thread_id
    return ""


def parse_codex_json_event(line: str) -> tuple[str, str] | None:
    try:
        payload = json.loads(line)
    except json.JSONDecodeError:
        return None
    if not isinstance(payload, dict):
        return None
    if payload.get("type") != "item.completed":
        return None
    item = payload.get("item")
    if not isinstance(item, dict):
        return None
    item_type = item.get("type")
    if item_type == "agent_message":
        return "output", str(item.get("text", ""))
    if item_type == "error":
        message = str(item.get("message", ""))
        if "deprecated" in message.lower():
            return None
        return "error", message
    return None


def summarize_codex_progress(payload: dict[str, Any]) -> dict[str, str] | None:
    event_type = payload.get("type")
    item = payload.get("item")
    if not isinstance(item, dict):
        return None
    item_type = item.get("type")
    if item_type != "command_execution":
        return None

    command = str(item.get("command", "")).strip()
    aggregated_output = str(item.get("aggregated_output", "")).strip()
    status = str(item.get("status", "")).strip()
    exit_code = item.get("exit_code")
    if event_type == "item.started" or status == "in_progress":
        return {
            "kind": "command",
            "status": "running",
            "label": "Running command",
            "detail": short_command(command),
            "command": command,
            "text": command_label(command, running=True),
        }
    if event_type == "item.completed":
        detail = aggregated_output if aggregated_output else short_command(command)
        return {
            "kind": "command",
            "status": "completed",
            "label": "Ran command",
            "detail": trim_progress_detail(detail),
            "command": command,
            "output": trim_progress_detail(aggregated_output),
            "text": command_label(command, running=False, exit_code=exit_code),
        }
    return None


def short_command(command: str) -> str:
    short = command
    if " -lc " in short:
        short = short.split(" -lc ", 1)[1].strip().strip("'\"")
    if len(short) > 140:
        short = short[:137].rstrip() + "..."
    return short


def trim_progress_detail(text: str) -> str:
    normalized = "\n".join(line.rstrip() for line in text.strip().splitlines() if line.strip())
    if len(normalized) > 500:
        return normalized[:497].rstrip() + "..."
    return normalized


def command_label(command: str, running: bool, exit_code: Any = None) -> str:
    lowered = command.lower()
    if any(token in lowered for token in ("rg --files", "find ", "ls ", "tree ")):
        action = "Exploring files" if running else "Explored files"
    elif any(token in lowered for token in ("sed -n", "cat ", "rg ", "grep ")):
        action = "Reading files" if running else "Read files"
    elif any(token in lowered for token in ("apply_patch", "python", "perl ", "node ", "ruby ")):
        action = "Editing or checking files" if running else "Edited or checked files"
    elif any(token in lowered for token in ("gradlew", "npm ", "yarn ", "pnpm ", "pytest", "cargo ", "go test")):
        action = "Running checks" if running else "Ran checks"
    else:
        action = "Running command" if running else "Ran command"

    short = short_command(command)
    if len(short) > 96:
        short = short[:93].rstrip() + "..."
    suffix = ""
    if not running and exit_code not in (None, ""):
        suffix = f" · exit {exit_code}"
    return f"{action}: {short}{suffix}"


def clean_codex_process_output(output: str) -> str:
    parts: list[str] = []
    errors: list[str] = []
    for line in output.splitlines():
        event = parse_codex_json_event(line)
        if event is None:
            continue
        event_type, text = event
        if event_type == "output" and text:
            parts.append(text)
        elif event_type == "error" and text:
            errors.append(text)
    if parts:
        return "".join(parts).strip()
    if errors:
        return "\n".join(errors).strip()
    return output.strip()


class CodexBridgeHandler(BaseHTTPRequestHandler):
    server_version = "MobileClawCodexBridge/1.0"

    def log_message(self, fmt: str, *args: Any) -> None:
        print("%s - %s" % (self.address_string(), fmt % args))

    def do_GET(self) -> None:
        if self.path == "/config":
            if not authorized(self):
                return
            with config_lock:
                payload = codex_config.copy()
            response(self, 200, {"ok": True, "config": payload, "config_path": str(CONFIG_PATH)})
            return
        if self.path != "/health":
            response(self, 404, {"ok": False, "output": "Not found"})
            return
        if not authorized(self):
            return
        with active_lock:
            running = active_process is not None and active_process.poll() is None
        response(
            self,
            200,
            {
                "ok": True,
                "output": "Codex desktop bridge is online.",
                "running": running,
                "command": COMMAND,
                "config": codex_config.copy(),
                "time": int(time.time()),
            },
        )

    def do_POST(self) -> None:
        if not authorized(self):
            return
        if self.path == "/run":
            self.run_codex()
            return
        if self.path == "/run_stream":
            self.run_codex_stream()
            return
        if self.path == "/stop":
            self.stop_codex()
            return
        if self.path == "/config":
            self.update_config()
            return
        response(self, 404, {"ok": False, "output": "Not found"})

    def update_config(self) -> None:
        try:
            body = read_json(self)
            new_config = request_codex_config(body)
            cwd = new_config.get("cwd", "")
            if cwd and not os.path.isdir(cwd):
                response(self, 400, {"ok": False, "output": f"cwd does not exist: {cwd}"})
                return
            with config_lock:
                codex_config.clear()
                codex_config.update(new_config)
                save_codex_config()
            response(self, 200, {"ok": True, "output": "Codex config updated.", "config": codex_config.copy()})
        except Exception as exc:
            response(self, 500, {"ok": False, "output": f"Config update failed: {exc}"})

    def run_codex(self) -> None:
        global active_process
        try:
            body = read_json(self)
            prompt = str(body.get("prompt", "")).strip()
            mobile_session = mobile_session_id(body)
            run_config = request_codex_config(body)
            cwd = str(body.get("cwd", "")).strip() or run_config.get("cwd") or None
            timeout = int(body.get("timeout_seconds", 120))
            if not prompt:
                response(self, 400, {"ok": False, "output": "prompt is required"})
                return
            if cwd and not os.path.isdir(cwd):
                response(self, 400, {"ok": False, "output": f"cwd does not exist: {cwd}"})
                return
            with active_lock:
                if active_process is not None and active_process.poll() is None:
                    response(self, 409, {"ok": False, "output": "A Codex task is already running."})
                    return
                active_process = subprocess.Popen(
                    codex_args(prompt, run_config, mobile_session),
                    cwd=cwd,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    start_new_session=True,
                )
                process = active_process
            try:
                output, _ = process.communicate(timeout=timeout)
                ok = process.returncode == 0
                thread_id = remember_codex_thread_from_output(mobile_session, output or "")
                clean_output = clean_codex_process_output(output or "")
                response(
                    self,
                    200 if ok else 500,
                    {
                        "ok": ok,
                        "output": clean_output[-MAX_OUTPUT:] or f"Codex exited with {process.returncode}",
                        "exit_code": process.returncode,
                        "thread_id": thread_id,
                    },
                )
            except subprocess.TimeoutExpired:
                response(self, 202, {"ok": True, "output": "Codex task is still running.", "running": True})
            finally:
                with active_lock:
                    if active_process is process and process.poll() is not None:
                        active_process = None
        except Exception as exc:
            response(self, 500, {"ok": False, "output": f"Bridge error: {exc}"})

    def stream_event(self, payload: dict[str, Any]) -> None:
        line = json.dumps(payload, ensure_ascii=False) + "\n"
        self.wfile.write(line.encode("utf-8"))
        self.wfile.flush()

    def run_codex_stream(self) -> None:
        global active_process
        process: subprocess.Popen[str] | None = None
        try:
            body = read_json(self)
            prompt = str(body.get("prompt", "")).strip()
            mobile_session = mobile_session_id(body)
            run_config = request_codex_config(body)
            cwd = str(body.get("cwd", "")).strip() or run_config.get("cwd") or None
            if not prompt:
                response(self, 400, {"ok": False, "output": "prompt is required"})
                return
            if cwd and not os.path.isdir(cwd):
                response(self, 400, {"ok": False, "output": f"cwd does not exist: {cwd}"})
                return
            with active_lock:
                if active_process is not None and active_process.poll() is None:
                    response(self, 409, {"ok": False, "output": "A Codex task is already running."})
                    return
                active_process = subprocess.Popen(
                    codex_args(prompt, run_config, mobile_session),
                    cwd=cwd,
                    text=True,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    bufsize=1,
                    start_new_session=True,
                )
                process = active_process

            self.send_response(200)
            self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            self.stream_event({"type": "start", "ok": True, "pid": process.pid})

            output_parts: list[str] = []
            error_parts: list[str] = []
            assert process.stdout is not None
            for line in process.stdout:
                payload = None
                try:
                    parsed = json.loads(line)
                    if isinstance(parsed, dict):
                        payload = parsed
                except json.JSONDecodeError:
                    payload = None
                if payload is not None:
                    thread_id = remember_codex_thread(mobile_session, payload)
                    if thread_id:
                        self.stream_event({"type": "session", "thread_id": thread_id})
                    progress = summarize_codex_progress(payload)
                    if progress is not None:
                        self.stream_event({"type": "progress", **progress})

                event = parse_codex_json_event(line)
                if event is None:
                    continue
                event_type, text = event
                if not text:
                    continue
                if event_type == "output":
                    output_parts.append(text)
                    if sum(len(part) for part in output_parts) > MAX_OUTPUT * 2:
                        output_parts = ["".join(output_parts)[-MAX_OUTPUT:]]
                    self.stream_event({"type": "output", "text": text})
                elif event_type == "error":
                    error_parts.append(text)

            exit_code = process.wait()
            output = "".join(output_parts).strip()
            if not output and error_parts:
                output = "\n".join(error_parts).strip()
            self.stream_event({
                "type": "done",
                "ok": exit_code == 0,
                "exit_code": exit_code,
                "output": output[-MAX_OUTPUT:] or f"Codex exited with {exit_code}",
            })
        except BrokenPipeError:
            if process is not None and process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
        except Exception as exc:
            try:
                if not self.wfile.closed:
                    self.stream_event({"type": "done", "ok": False, "output": f"Bridge error: {exc}"})
            except Exception:
                pass
        finally:
            with active_lock:
                if active_process is process and (process is None or process.poll() is not None):
                    active_process = None

    def stop_codex(self) -> None:
        global active_process
        with active_lock:
            process = active_process
            active_process = None
        if process is None or process.poll() is not None:
            response(self, 200, {"ok": True, "output": "No active Codex task."})
            return
        os.killpg(process.pid, signal.SIGTERM)
        response(self, 200, {"ok": True, "output": "Stop signal sent to Codex."})


def main() -> None:
    if not TOKEN:
        raise SystemExit("Set CODEX_BRIDGE_TOKEN before starting the bridge.")
    load_codex_config()
    load_codex_session_map()
    server = ThreadingHTTPServer((HOST, PORT), CodexBridgeHandler)
    print(f"MobileClaw Codex bridge listening on http://{HOST}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
