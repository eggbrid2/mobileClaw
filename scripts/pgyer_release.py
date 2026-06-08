#!/usr/bin/env python3
"""
Build and publish MobileClaw APKs to Pgyer from the desktop.

Examples:
  PGYER_API_KEY=xxx python3 scripts/pgyer_release.py upload --apk app/build/outputs/apk/debug/app-debug.apk
  PGYER_API_KEY=xxx python3 scripts/pgyer_release.py build-upload --notes "Codex release"

MobileClaw app-side config keys:
  pgyer_api_key
  pgyer_app_key
  pgyer_install_password
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
PGYER_UPLOAD_URL = "https://upload.pgyer.com/apiv2/app/upload"
PGYER_CHECK_URL = "https://www.pgyer.com/apiv2/app/check"


def run(cmd: list[str], cwd: Path = ROOT) -> str:
    proc = subprocess.run(cmd, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if proc.returncode != 0:
        raise SystemExit(proc.stdout.strip() or f"Command failed: {' '.join(cmd)}")
    return proc.stdout


def optional_output(cmd: list[str], cwd: Path = ROOT) -> str:
    try:
        return subprocess.check_output(cmd, cwd=cwd, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


def git_output(*args: str) -> str:
    try:
        return subprocess.check_output(["git", *args], cwd=ROOT, text=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


def default_release_notes() -> str:
    version = git_output("describe", "--tags", "--always", "--dirty") or "unknown"
    commit = git_output("rev-parse", "--short", "HEAD") or "unknown"
    branch = git_output("branch", "--show-current") or "unknown"
    return f"MobileClaw {version} ({branch}/{commit})"


def require_api_key(value: str | None) -> str:
    api_key = (value or os.environ.get("PGYER_API_KEY") or "").strip()
    if not api_key:
        raise SystemExit("Missing Pgyer API key. Pass --api-key or set PGYER_API_KEY.")
    return api_key


def parse_json(raw: str) -> dict[str, Any]:
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"raw": raw}


def find_apksigner() -> str:
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if android_home:
        build_tools = Path(android_home) / "build-tools"
        candidates = sorted(build_tools.glob("*/apksigner"))
        if candidates:
            return str(candidates[-1])
    return shutil.which("apksigner") or ""


def verify_apk_signature(apk: Path) -> None:
    apksigner = find_apksigner()
    if not apksigner:
        if "release-unsigned" in apk.name or apk.name.endswith("-unsigned.apk"):
            raise SystemExit(
                f"Refusing to upload likely unsigned APK without apksigner available: {apk}"
            )
        print("Warning: apksigner not found; skipping APK signature verification.", file=sys.stderr)
        return

    proc = subprocess.run(
        [apksigner, "verify", "--verbose", str(apk)],
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    if proc.returncode != 0:
        raise SystemExit(
            f"APK signature verification failed for {apk}.\n{proc.stdout.strip()}"
        )


def default_release_apk() -> Path:
    release_dir = ROOT / "app" / "build" / "outputs" / "apk" / "release"
    signed = optional_output(
        [
            "find",
            str(release_dir.relative_to(ROOT)),
            "-maxdepth",
            "1",
            "-name",
            "*signed*.apk",
            "-print",
            "-quit",
        ]
    )
    if signed:
        return (ROOT / signed).resolve()
    return release_dir / "app-release-unsigned.apk"


def upload(args: argparse.Namespace) -> dict[str, Any]:
    api_key = require_api_key(args.api_key)
    apk = Path(args.apk or DEFAULT_APK).expanduser()
    if not apk.is_absolute():
        apk = (ROOT / apk).resolve()
    if not apk.exists():
        raise SystemExit(f"APK does not exist: {apk}")
    verify_apk_signature(apk)

    cmd = [
        "curl",
        "-sS",
        "-X",
        "POST",
        PGYER_UPLOAD_URL,
        "-F",
        f"_api_key={api_key}",
        "-F",
        f"buildInstallType={args.install_type}",
        "-F",
        f"buildUpdateDescription={args.notes or default_release_notes()}",
        "-F",
        f"file=@{apk}",
    ]
    password = (args.password or os.environ.get("PGYER_INSTALL_PASSWORD") or "").strip()
    if password:
        cmd += ["-F", f"buildPassword={password}"]

    payload = parse_json(run(cmd))
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload


def build_upload(args: argparse.Namespace) -> dict[str, Any]:
    task = args.gradle_task or "assembleDebug"
    run(["./gradlew", task])
    if not args.apk:
        args.apk = str(default_release_apk() if "release" in task.lower() else DEFAULT_APK)
    return upload(args)


def check(args: argparse.Namespace) -> dict[str, Any]:
    api_key = require_api_key(args.api_key)
    app_key = (args.app_key or os.environ.get("PGYER_APP_KEY") or "").strip()
    if not app_key:
        raise SystemExit("Missing Pgyer appKey. Pass --app-key or set PGYER_APP_KEY.")
    cmd = [
        "curl",
        "-sS",
        "-X",
        "POST",
        PGYER_CHECK_URL,
        "-F",
        f"_api_key={api_key}",
        "-F",
        f"appKey={app_key}",
    ]
    if args.version:
        cmd += ["-F", f"buildVersion={args.version}"]
    payload = parse_json(run(cmd))
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload


def main() -> None:
    parser = argparse.ArgumentParser(description="MobileClaw Pgyer release helper")
    sub = parser.add_subparsers(dest="cmd", required=True)

    def add_common_upload_flags(p: argparse.ArgumentParser) -> None:
        p.add_argument("--api-key")
        p.add_argument("--apk")
        p.add_argument("--notes", default="")
        p.add_argument("--install-type", default="1", help="1 public, 2 password, 3 invite")
        p.add_argument("--password")

    up = sub.add_parser("upload", help="Upload an APK to Pgyer")
    add_common_upload_flags(up)
    up.set_defaults(func=upload)

    bu = sub.add_parser("build-upload", help="Run Gradle then upload the APK to Pgyer")
    add_common_upload_flags(bu)
    bu.add_argument("--gradle-task", default="assembleDebug")
    bu.set_defaults(func=build_upload)

    ck = sub.add_parser("check", help="Check latest Pgyer build")
    ck.add_argument("--api-key")
    ck.add_argument("--app-key")
    ck.add_argument("--version")
    ck.set_defaults(func=check)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
