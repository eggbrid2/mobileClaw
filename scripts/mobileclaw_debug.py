#!/usr/bin/env python3
"""
MobileClaw device debug scaffold.

This script gives Codex/developers one stable entry point for Android device
debugging: build/install/launch, screenshot, screen XML, logcat, taps, text,
screenrecord, and scripted UI scenarios.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import shutil
import shlex
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_ROOT = ROOT / "debug_artifacts"
PACKAGE = "com.mobileclaw"
MAIN_ACTIVITY = "com.mobileclaw/.ui.MainActivity"


class DebugError(RuntimeError):
    pass


def now_id() -> str:
    return datetime.now().strftime("%Y%m%d_%H%M%S")


def run(cmd: list[str], *, cwd: Path = ROOT, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    printable = " ".join(shlex.quote(x) for x in cmd)
    if not capture:
        print(f"$ {printable}")
        return subprocess.run(cmd, cwd=cwd, check=check, text=True)
    proc = subprocess.run(
        cmd,
        cwd=cwd,
        text=True,
        encoding="utf-8",
        errors="replace",
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if check and proc.returncode != 0:
        raise DebugError(f"Command failed ({proc.returncode}): {printable}\n{proc.stdout}")
    return proc


def adb(serial: str | None, *args: str, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess[str]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += list(args)
    return run(cmd, check=check, capture=capture)


def require_serial(serial: str | None) -> str | None:
    if serial:
        return serial
    devices = list_devices()
    if len(devices) <= 1:
        return devices[0]["serial"] if devices else None
    raise DebugError(
        "Multiple adb devices are connected. Pass --serial.\n"
        + "\n".join(f"- {d['serial']} ({d['state']})" for d in devices)
    )


def list_devices() -> list[dict[str, str]]:
    proc = run(["adb", "devices", "-l"])
    rows: list[dict[str, str]] = []
    for line in proc.stdout.splitlines()[1:]:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        rows.append({"serial": parts[0], "state": parts[1] if len(parts) > 1 else "unknown", "raw": line})
    return rows


def artifact_dir(label: str) -> Path:
    path = ARTIFACT_ROOT / f"{now_id()}_{safe_name(label)}"
    path.mkdir(parents=True, exist_ok=True)
    return path


def safe_name(text: str) -> str:
    clean = re.sub(r"[^a-zA-Z0-9_.-]+", "_", text.strip())
    return clean.strip("_")[:80] or "capture"


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def capture_png(serial: str | None, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    cmd = ["adb"] + (["-s", serial] if serial else []) + ["exec-out", "screencap", "-p"]
    out.write_bytes(subprocess.check_output(cmd, cwd=ROOT))


def capture_xml(serial: str | None, out: Path) -> None:
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/window.xml", check=False)
    write_text(out, adb(serial, "shell", "cat", "/sdcard/window.xml", check=False).stdout)


def capture_core(serial: str | None, out: Path, *, package: str = PACKAGE) -> None:
    out.mkdir(parents=True, exist_ok=True)
    write_text(out / "devices.txt", run(["adb", "devices", "-l"]).stdout)
    write_text(out / "props.txt", adb(serial, "shell", "getprop", check=False).stdout)
    write_text(out / "foreground.txt", adb(serial, "shell", "dumpsys", "window", "windows", check=False).stdout[-12000:])
    write_text(out / "activity.txt", adb(serial, "shell", "dumpsys", "activity", "activities", check=False).stdout[-12000:])
    write_text(out / "package.txt", adb(serial, "shell", "dumpsys", "package", package, check=False).stdout[:12000])
    write_text(out / "logcat.txt", adb(serial, "logcat", "-d", "-v", "time", check=False).stdout)
    capture_png(serial, out / "screen.png")
    capture_xml(serial, out / "window.xml")
    write_text(out / "README.txt", f"MobileClaw debug bundle\nserial={serial}\ncreated={datetime.now().isoformat()}\n")


def build(args: argparse.Namespace) -> None:
    task = args.task or ":app:assembleDebug"
    run(["./gradlew", task], capture=False)


def install(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    if args.build:
        build(argparse.Namespace(task=":app:assembleDebug"))
    apk = args.apk or str(ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk")
    adb(serial, "install", "-r", "-d", apk, capture=False)


def launch(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    cmd = ["shell", "am", "start", "-n", args.activity or MAIN_ACTIVITY]
    if args.page:
        cmd += ["--es", "mobileclaw.debug.page", args.page]
    adb(serial, *cmd, capture=False)


def page(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    adb(
        serial,
        "shell",
        "am",
        "start",
        "-n",
        args.activity or MAIN_ACTIVITY,
        "--es",
        "mobileclaw.debug.page",
        args.page,
        capture=False,
    )


def goal(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    encoded_goal = base64.b64encode(args.goal.encode("utf-8")).decode("ascii")
    adb(
        serial,
        "shell",
        "am",
        "start",
        "-n",
        args.activity or MAIN_ACTIVITY,
        "--es",
        "mobileclaw.debug.goal.b64",
        encoded_goal,
        capture=False,
    )


def force_stop(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    adb(serial, "shell", "am", "force-stop", args.package or PACKAGE, capture=False)


def screenshot(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    out = Path(args.out) if args.out else artifact_dir("screenshot") / "screen.png"
    capture_png(serial, out)
    print(out)


def dump_xml(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    out = Path(args.out) if args.out else artifact_dir("xml") / "window.xml"
    capture_xml(serial, out)
    print(out)


def status(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    lines = []
    lines.append("== devices ==")
    lines.extend(d["raw"] for d in list_devices())
    lines.append("\n== foreground ==")
    lines.append(adb(serial, "shell", "dumpsys", "window", "windows", check=False).stdout[-5000:])
    lines.append("\n== package ==")
    lines.append(adb(serial, "shell", "dumpsys", "package", args.package or PACKAGE, check=False).stdout[:4000])
    print("\n".join(lines))


def logcat(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    if args.clear:
        adb(serial, "logcat", "-c")
    if args.out:
        out = Path(args.out)
        cmd = ["adb"] + (["-s", serial] if serial else []) + ["logcat", "-d", "-v", "time"]
        if args.filter:
            cmd += [args.filter]
        write_text(out, subprocess.check_output(cmd, cwd=ROOT, text=True, encoding="utf-8", errors="replace", stderr=subprocess.STDOUT))
        print(out)
        return
    cmd = ["adb"] + (["-s", serial] if serial else []) + ["logcat", "-v", "time"]
    if args.filter:
        cmd += [args.filter]
    run(cmd, capture=False)


def tap(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    adb(serial, "shell", "input", "tap", str(args.x), str(args.y), capture=False)


def swipe(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    adb(serial, "shell", "input", "swipe", str(args.x1), str(args.y1), str(args.x2), str(args.y2), str(args.ms), capture=False)


def text(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    escaped = args.value.replace("%", "%s").replace(" ", "%s")
    adb(serial, "shell", "input", "text", escaped, capture=False)


def key(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    adb(serial, "shell", "input", "keyevent", args.code, capture=False)


def record(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    out = Path(args.out) if args.out else artifact_dir("record") / "screen.mp4"
    out.parent.mkdir(parents=True, exist_ok=True)
    remote = f"/sdcard/mobileclaw_record_{now_id()}.mp4"
    adb(serial, "shell", "screenrecord", "--time-limit", str(args.seconds), remote, capture=False)
    adb(serial, "pull", remote, str(out), capture=False)
    adb(serial, "shell", "rm", "-f", remote, check=False)
    print(out)
    if args.gif:
        gif_out = out.with_suffix(".gif")
        convert_gif_file(
            input_video=out,
            output_gif=gif_out,
            fps=args.fps,
            width=args.width,
            start=args.start,
            duration=args.duration,
        )
        print(gif_out)


def convert_gif(args: argparse.Namespace) -> None:
    input_video = Path(args.input)
    output_gif = Path(args.out) if args.out else input_video.with_suffix(".gif")
    convert_gif_file(
        input_video=input_video,
        output_gif=output_gif,
        fps=args.fps,
        width=args.width,
        start=args.start,
        duration=args.duration,
    )
    print(output_gif)


def convert_gif_file(
    *,
    input_video: Path,
    output_gif: Path,
    fps: int,
    width: int,
    start: float,
    duration: float | None,
) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise DebugError(
            "ffmpeg is required for GIF conversion but was not found.\n"
            "Install it with: brew install ffmpeg\n"
            "Then rerun the same command."
        )
    if not input_video.exists():
        raise DebugError(f"Input video does not exist: {input_video}")
    output_gif.parent.mkdir(parents=True, exist_ok=True)

    palette = output_gif.with_suffix(".palette.png")
    vf = f"fps={fps},scale={width}:-1:flags=lanczos"
    trim_args = []
    if start > 0:
        trim_args += ["-ss", str(start)]
    if duration is not None and duration > 0:
        trim_args += ["-t", str(duration)]

    run(
        [
            ffmpeg,
            "-y",
            *trim_args,
            "-i",
            str(input_video),
            "-vf",
            f"{vf},palettegen=stats_mode=diff",
            str(palette),
        ],
        capture=False,
    )
    run(
        [
            ffmpeg,
            "-y",
            *trim_args,
            "-i",
            str(input_video),
            "-i",
            str(palette),
            "-lavfi",
            f"{vf} [x]; [x][1:v] paletteuse=dither=bayer:bayer_scale=3",
            "-loop",
            "0",
            str(output_gif),
        ],
        capture=False,
    )
    palette.unlink(missing_ok=True)


def watch(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    out = artifact_dir(args.label or "watch")
    end_at = time.time() + args.seconds
    index = 1
    while time.time() <= end_at:
        prefix = f"{index:03d}"
        print(f"[watch] capture {prefix}")
        capture_png(serial, out / f"{prefix}_screen.png")
        capture_xml(serial, out / f"{prefix}_window.xml")
        write_text(out / f"{prefix}_foreground.txt", adb(serial, "shell", "dumpsys", "window", "windows", check=False).stdout[-6000:])
        index += 1
        if time.time() < end_at:
            time.sleep(args.interval)
    print(out)


def bundle(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    out = artifact_dir(args.label or "bundle")
    capture_core(serial, out, package=args.package or PACKAGE)
    write_analysis(out, problem=args.label or "bundle", package=args.package or PACKAGE)
    print(out)


def triage(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    out = artifact_dir(args.label or "triage")
    if args.clear_logcat:
        adb(serial, "logcat", "-c", check=False)
    if args.build:
        build(argparse.Namespace(task=":app:assembleDebug"))
    if args.install:
        install(argparse.Namespace(serial=serial, apk=args.apk, build=False))
    if args.force_stop:
        adb(serial, "shell", "am", "force-stop", args.package or PACKAGE, check=False)
    if args.launch:
        adb(serial, "shell", "am", "start", "-n", args.activity or MAIN_ACTIVITY, check=False)
    if args.wait > 0:
        time.sleep(args.wait)

    if args.watch_seconds > 0:
        watch_dir = out / "watch"
        watch_dir.mkdir(parents=True, exist_ok=True)
        end_at = time.time() + args.watch_seconds
        index = 1
        while time.time() <= end_at:
            prefix = f"{index:03d}"
            capture_png(serial, watch_dir / f"{prefix}_screen.png")
            capture_xml(serial, watch_dir / f"{prefix}_window.xml")
            write_text(watch_dir / f"{prefix}_foreground.txt", adb(serial, "shell", "dumpsys", "window", "windows", check=False).stdout[-6000:])
            index += 1
            if time.time() < end_at:
                time.sleep(args.interval)

    capture_core(serial, out, package=args.package or PACKAGE)
    write_text(out / "problem.txt", args.problem or "")
    write_analysis(out, problem=args.problem or args.label or "triage", package=args.package or PACKAGE)
    print(out)


def analyze(args: argparse.Namespace) -> None:
    path = Path(args.path)
    if not path.exists():
        raise DebugError(f"Artifact path does not exist: {path}")
    write_analysis(path, problem=args.problem or path.name, package=args.package or PACKAGE)
    print(path / "analysis.md")


def write_analysis(path: Path, *, problem: str, package: str = PACKAGE) -> None:
    log = read_text(path / "logcat.txt")
    xml = read_text(path / "window.xml")
    foreground = read_text(path / "foreground.txt")
    activity = read_text(path / "activity.txt")
    package_info = read_text(path / "package.txt")
    all_crashes = extract_crashes(log)
    crashes = [crash for crash in all_crashes if package in crash]
    external_crashes = [crash for crash in all_crashes if package not in crash]
    anrs = extract_lines(log, ["ANR in", "Application Not Responding"], limit=12)
    errors = extract_lines(log, ["FATAL EXCEPTION", "AndroidRuntime", "Exception", "Error", "ClawVpn", "MobileClaw"], limit=40)
    foreground_summary = extract_foreground_summary(foreground + "\n" + activity)
    xml_summary = summarize_xml(xml)
    package_summary = summarize_package(package_info)

    lines = [
        "# MobileClaw Debug Analysis",
        "",
        f"- Problem: {problem}",
        f"- Created: {datetime.now().isoformat(timespec='seconds')}",
        f"- Artifact: `{path}`",
        "",
        "## Quick Read",
        "",
        f"- Foreground: {foreground_summary or 'unknown'}",
        f"- UI XML nodes/text: {xml_summary}",
        f"- Package: {package_summary or 'unknown'}",
        f"- App crash blocks: {len(crashes)}",
        f"- External crash blocks: {len(external_crashes)}",
        f"- ANR hints: {len(anrs)}",
        "",
        "## Likely Signals",
        "",
    ]
    if crashes:
        for i, crash in enumerate(crashes[:3], start=1):
            lines += [f"### Crash {i}", "", "```text", crash[:5000], "```", ""]
    elif errors:
        lines += ["No full crash block found. Recent relevant log lines:", "", "```text", "\n".join(errors[-40:]), "```", ""]
    else:
        lines += ["No obvious crash/error lines found in logcat.", ""]

    if external_crashes:
        lines += [
            "## External App Crashes",
            "",
            "These crashes are from other packages and are usually noise unless the reproduction intentionally operates that app.",
            "",
        ]
        for i, crash in enumerate(external_crashes[:2], start=1):
            lines += [f"### External Crash {i}", "", "```text", crash[:2000], "```", ""]

    if anrs:
        lines += ["## ANR", "", "```text", "\n".join(anrs), "```", ""]

    visible_text = extract_visible_texts(xml, limit=80)
    if visible_text:
        lines += ["## Visible Text", "", "```text", "\n".join(visible_text), "```", ""]

    lines += [
        "## Files",
        "",
        "- `screen.png`: screenshot at capture time.",
        "- `window.xml`: UIAutomator tree.",
        "- `logcat.txt`: logcat dump.",
        "- `foreground.txt`: window foreground diagnostics.",
        "- `activity.txt`: activity stack diagnostics.",
        "",
        "## Next Debug Move",
        "",
        next_debug_move(crashes, errors, visible_text, foreground_summary, package),
        "",
    ]
    write_text(path / "analysis.md", "\n".join(lines))


def extract_crashes(log: str) -> list[str]:
    if not log:
        return []
    lines = log.splitlines()
    blocks: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if "FATAL EXCEPTION" in line or "AndroidRuntime" in line and "Process:" in line:
            start = max(0, i - 2)
            end = min(len(lines), i + 90)
            blocks.append("\n".join(lines[start:end]))
            i = end
        else:
            i += 1
    return blocks


def extract_lines(text: str, needles: list[str], *, limit: int) -> list[str]:
    rows = [line for line in text.splitlines() if any(n in line for n in needles)]
    return rows[-limit:]


def extract_foreground_summary(text: str) -> str:
    patterns = [
        r"mCurrentFocus=Window\{[^ ]+ [^ ]+ ([^}]+)\}",
        r"mFocusedApp=ActivityRecord\{[^ ]+ [^ ]+ ([^ ]+)",
        r"topResumedActivity=ActivityRecord\{[^ ]+ [^ ]+ ([^ ]+)",
    ]
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            return match.group(1)
    package_match = re.search(r"(com\.mobileclaw/[A-Za-z0-9_.$/]+)", text)
    return package_match.group(1) if package_match else ""


def summarize_xml(xml: str) -> str:
    if not xml:
        return "missing"
    node_count = xml.count("<node")
    text_count = len(extract_visible_texts(xml, limit=500))
    return f"{node_count} nodes, {text_count} visible text entries"


def summarize_package(package_info: str) -> str:
    version = re.search(r"versionName=([^\s]+)", package_info)
    code = re.search(r"versionCode=([^\s]+)", package_info)
    parts = []
    if version:
        parts.append(f"versionName={version.group(1)}")
    if code:
        parts.append(f"versionCode={code.group(1)}")
    return ", ".join(parts)


def extract_visible_texts(xml: str, *, limit: int) -> list[str]:
    if not xml:
        return []
    values = []
    for attr in ("text", "content-desc", "resource-id"):
        values += re.findall(rf'{attr}="([^"]+)"', xml)
    clean = []
    seen = set()
    for value in values:
        value = (
            value.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", '"')
            .strip()
        )
        if not value or value in seen:
            continue
        seen.add(value)
        clean.append(value[:180])
        if len(clean) >= limit:
            break
    return clean


def next_debug_move(crashes: list[str], errors: list[str], visible_text: list[str], foreground: str, package: str) -> str:
    if crashes:
        return "Fix the top crash stack first, then rerun `triage --clear-logcat` to confirm the crash is gone."
    if package not in foreground:
        return "The app is not foreground. Verify launch/navigation or reproduce with `triage --launch --watch-seconds 6`."
    if not visible_text:
        return "UI XML is empty or inaccessible. Use screenshot-based analysis and check whether the current screen is WebView/canvas/system-protected."
    if errors:
        return "No fatal crash found, but relevant error lines exist. Inspect `logcat.txt` around the lines shown above."
    return "No obvious runtime failure found. Use a scenario with assertions to narrow the exact UI regression."


def scenario(args: argparse.Namespace) -> None:
    serial = require_serial(args.serial)
    path = Path(args.file)
    data = json.loads(path.read_text(encoding="utf-8"))
    steps = data.get("steps", data if isinstance(data, list) else [])
    if not isinstance(steps, list):
        raise DebugError("Scenario JSON must be a list or an object with a 'steps' list.")
    out = artifact_dir(path.stem)
    for i, step in enumerate(steps, start=1):
        if not isinstance(step, dict):
            raise DebugError(f"Step {i} must be an object.")
        action = step.get("action")
        print(f"[{i}/{len(steps)}] {action} {step}")
        if action == "launch":
            adb(serial, "shell", "am", "start", "-n", step.get("activity", MAIN_ACTIVITY))
        elif action == "tap":
            adb(serial, "shell", "input", "tap", str(step["x"]), str(step["y"]))
        elif action == "swipe":
            adb(serial, "shell", "input", "swipe", str(step["x1"]), str(step["y1"]), str(step["x2"]), str(step["y2"]), str(step.get("ms", 350)))
        elif action == "text":
            adb(serial, "shell", "input", "text", str(step["value"]).replace(" ", "%s"))
        elif action == "key":
            adb(serial, "shell", "input", "keyevent", str(step["code"]))
        elif action == "sleep":
            time.sleep(float(step.get("seconds", 1)))
        elif action == "screenshot":
            file = out / f"{i:02d}_{safe_name(step.get('name', 'screen'))}.png"
            cmd = ["adb"] + (["-s", serial] if serial else []) + ["exec-out", "screencap", "-p"]
            file.write_bytes(subprocess.check_output(cmd, cwd=ROOT))
        elif action == "xml":
            capture_xml(serial, out / f"{i:02d}_{safe_name(step.get('name', 'window'))}.xml")
        elif action == "bundle":
            bundle(argparse.Namespace(serial=serial, label=f"{path.stem}_{i:02d}", package=args.package))
        elif action == "assert_text":
            tmp = out / f"{i:02d}_assert_window.xml"
            capture_xml(serial, tmp)
            xml = read_text(tmp)
            expected = str(step["value"])
            if expected not in xml:
                failure = out / f"{i:02d}_assert_text_failed"
                capture_core(serial, failure, package=args.package)
                write_analysis(failure, problem=f"assert_text failed: {expected}", package=args.package)
                raise DebugError(f"assert_text failed: {expected}. Evidence: {failure}")
        elif action == "assert_not_text":
            tmp = out / f"{i:02d}_assert_window.xml"
            capture_xml(serial, tmp)
            xml = read_text(tmp)
            unexpected = str(step["value"])
            if unexpected in xml:
                failure = out / f"{i:02d}_assert_not_text_failed"
                capture_core(serial, failure, package=args.package)
                write_analysis(failure, problem=f"assert_not_text failed: {unexpected}", package=args.package)
                raise DebugError(f"assert_not_text failed: {unexpected}. Evidence: {failure}")
        elif action == "assert_package":
            foreground = adb(serial, "shell", "dumpsys", "window", "windows", check=False).stdout
            expected = str(step["value"])
            if expected not in foreground:
                failure = out / f"{i:02d}_assert_package_failed"
                capture_core(serial, failure, package=args.package)
                write_analysis(failure, problem=f"assert_package failed: {expected}", package=args.package)
                raise DebugError(f"assert_package failed: {expected}. Evidence: {failure}")
        else:
            raise DebugError(f"Unknown scenario action: {action}")
    print(out)


def doctor(args: argparse.Namespace) -> None:
    print(f"repo: {ROOT}")
    print(f"adb: {run(['which', 'adb'], check=False).stdout.strip() or 'missing'}")
    print(f"ffmpeg: {shutil.which('ffmpeg') or 'missing (install with: brew install ffmpeg)'}")
    java = run(["java", "-version"], check=False).stdout.splitlines()
    print(f"java: {java[0] if java else 'unknown'}")
    print("devices:")
    for d in list_devices():
        print(f"  {d['raw']}")
    serial = require_serial(args.serial) if args.serial or len(list_devices()) == 1 else None
    if serial:
        print(f"selected: {serial}")
        print(adb(serial, "shell", "wm", "size", check=False).stdout.strip())
        print(adb(serial, "shell", "wm", "density", check=False).stdout.strip())


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="MobileClaw Android debug scaffold")
    p.add_argument("--serial", "-s", help="ADB device serial. Required when multiple devices are connected.")
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("devices").set_defaults(func=lambda args: print(json.dumps(list_devices(), ensure_ascii=False, indent=2)))
    sub.add_parser("doctor").set_defaults(func=doctor)

    b = sub.add_parser("build"); b.add_argument("--task", default=":app:assembleDebug"); b.set_defaults(func=build)
    i = sub.add_parser("install"); i.add_argument("--apk"); i.add_argument("--build", action="store_true"); i.set_defaults(func=install)
    l = sub.add_parser("launch"); l.add_argument("--activity"); l.add_argument("--page", help="Debug-only AppPage to open, e.g. HOME, SKILLS, ROLES, SETTINGS."); l.set_defaults(func=launch)
    pg = sub.add_parser("page"); pg.add_argument("page", help="Open a debug AppPage via MainActivity extra."); pg.add_argument("--activity"); pg.set_defaults(func=page)
    gl = sub.add_parser("goal"); gl.add_argument("goal", help="Submit a real chat goal through MainActivity debug extra."); gl.add_argument("--activity"); gl.set_defaults(func=goal)
    fs = sub.add_parser("force-stop"); fs.add_argument("--package"); fs.set_defaults(func=force_stop)

    ss = sub.add_parser("screenshot"); ss.add_argument("--out"); ss.set_defaults(func=screenshot)
    x = sub.add_parser("xml"); x.add_argument("--out"); x.set_defaults(func=dump_xml)
    st = sub.add_parser("status"); st.add_argument("--package"); st.set_defaults(func=status)
    lc = sub.add_parser("logcat"); lc.add_argument("--out"); lc.add_argument("--filter"); lc.add_argument("--clear", action="store_true"); lc.set_defaults(func=logcat)

    t = sub.add_parser("tap"); t.add_argument("x", type=int); t.add_argument("y", type=int); t.set_defaults(func=tap)
    sw = sub.add_parser("swipe"); sw.add_argument("x1", type=int); sw.add_argument("y1", type=int); sw.add_argument("x2", type=int); sw.add_argument("y2", type=int); sw.add_argument("--ms", type=int, default=350); sw.set_defaults(func=swipe)
    tx = sub.add_parser("text"); tx.add_argument("value"); tx.set_defaults(func=text)
    k = sub.add_parser("key"); k.add_argument("code"); k.set_defaults(func=key)

    r = sub.add_parser("record")
    r.add_argument("--seconds", type=int, default=10)
    r.add_argument("--out")
    r.add_argument("--gif", action="store_true", help="Also convert the recorded MP4 to a GitHub-friendly GIF. Requires ffmpeg.")
    r.add_argument("--fps", type=int, default=12)
    r.add_argument("--width", type=int, default=420)
    r.add_argument("--start", type=float, default=0)
    r.add_argument("--duration", type=float)
    r.set_defaults(func=record)
    g = sub.add_parser("gif")
    g.add_argument("input", help="Input MP4/screenrecord file")
    g.add_argument("--out")
    g.add_argument("--fps", type=int, default=12)
    g.add_argument("--width", type=int, default=420)
    g.add_argument("--start", type=float, default=0)
    g.add_argument("--duration", type=float)
    g.set_defaults(func=convert_gif)
    w = sub.add_parser("watch"); w.add_argument("--seconds", type=int, default=20); w.add_argument("--interval", type=float, default=2.0); w.add_argument("--label"); w.set_defaults(func=watch)
    bu = sub.add_parser("bundle"); bu.add_argument("--label"); bu.add_argument("--package"); bu.set_defaults(func=bundle)
    tr = sub.add_parser("triage")
    tr.add_argument("problem", nargs="?", help="Short description of the bug/problem being investigated.")
    tr.add_argument("--label")
    tr.add_argument("--package", default=PACKAGE)
    tr.add_argument("--activity", default=MAIN_ACTIVITY)
    tr.add_argument("--apk")
    tr.add_argument("--build", action="store_true")
    tr.add_argument("--install", action="store_true")
    tr.add_argument("--launch", action="store_true")
    tr.add_argument("--force-stop", action="store_true")
    tr.add_argument("--clear-logcat", action="store_true")
    tr.add_argument("--wait", type=float, default=1.0)
    tr.add_argument("--watch-seconds", type=float, default=0)
    tr.add_argument("--interval", type=float, default=1.5)
    tr.set_defaults(func=triage)
    an = sub.add_parser("analyze")
    an.add_argument("path", help="Existing debug artifact directory")
    an.add_argument("--problem")
    an.add_argument("--package", default=PACKAGE)
    an.set_defaults(func=analyze)
    sc = sub.add_parser("scenario"); sc.add_argument("file"); sc.add_argument("--package", default=PACKAGE); sc.set_defaults(func=scenario)
    return p


def main() -> int:
    args = parser().parse_args()
    try:
        args.func(args)
        return 0
    except DebugError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
