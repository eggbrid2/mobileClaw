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
import time
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
PGYER_LEGACY_UPLOAD_URL = "https://upload.pgyer.com/apiv2/app/upload"
PGYER_GET_COS_TOKEN_URL = "https://www.pgyer.com/apiv2/app/getCOSToken"
PGYER_BUILD_INFO_URL = "https://www.pgyer.com/apiv2/app/buildInfo"
PGYER_CHECK_URL = "https://www.pgyer.com/apiv2/app/check"
_LOCAL_PROPERTIES: dict[str, str] | None = None


def run(cmd: list[str], cwd: Path = ROOT) -> str:
    env = os.environ.copy()
    env.setdefault("NO_PROXY", "*")
    env.setdefault("no_proxy", "*")
    proc = subprocess.run(
        cmd,
        cwd=cwd,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
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


def gradle_command(task: str) -> list[str]:
    cmd = ["./gradlew"]
    jbr_home = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
    if jbr_home.is_dir():
        cmd.append(f"-Dorg.gradle.java.home={jbr_home}")
    cmd.append(task)
    return cmd


def local_properties() -> dict[str, str]:
    global _LOCAL_PROPERTIES
    if _LOCAL_PROPERTIES is not None:
        return _LOCAL_PROPERTIES
    props: dict[str, str] = {}
    path = ROOT / "local.properties"
    if path.exists():
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    _LOCAL_PROPERTIES = props
    return props


def local_secret(*keys: str) -> str:
    props = local_properties()
    for key in keys:
        value = props.get(key, "").strip()
        if value:
            return value
    return ""


def default_release_notes() -> str:
    version = git_output("describe", "--tags", "--always", "--dirty") or "unknown"
    commit = git_output("rev-parse", "--short", "HEAD") or "unknown"
    branch = git_output("branch", "--show-current") or "unknown"
    return f"MobileClaw {version} ({branch}/{commit})"


def require_api_key(value: str | None) -> str:
    api_key = (
        value
        or os.environ.get("PGYER_API_KEY")
        or local_secret("pgyer.api_key", "pgyer_api_key")
        or ""
    ).strip()
    if not api_key:
        raise SystemExit("Missing Pgyer API key. Pass --api-key or set PGYER_API_KEY.")
    return api_key


def pgyer_password(value: str | None) -> str:
    return (
        value
        or os.environ.get("PGYER_INSTALL_PASSWORD")
        or local_secret("pgyer.install_password", "pgyer_install_password")
        or ""
    ).strip()


def parse_json(raw: str) -> dict[str, Any]:
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"raw": raw}


def require_pgyer_data(payload: dict[str, Any], action: str) -> dict[str, Any]:
    code = str(payload.get("code", "0"))
    if code != "0":
        message = str(payload.get("message") or payload.get("raw") or "").strip()
        raise SystemExit(f"Pgyer {action} failed: code={code} {message}".strip())
    data = payload.get("data") or {}
    if not isinstance(data, dict):
        raise SystemExit(f"Pgyer {action} returned invalid data: {payload}")
    return data


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


def legacy_upload(args: argparse.Namespace, api_key: str, apk: Path, notes: str) -> dict[str, Any]:
    cmd = [
        "curl",
        "-sS",
        "-X",
        "POST",
        PGYER_LEGACY_UPLOAD_URL,
        "-F",
        f"_api_key={api_key}",
        "-F",
        f"buildInstallType={args.install_type}",
        "-F",
        f"buildUpdateDescription={notes}",
        "-F",
        f"file=@{apk}",
    ]
    password = pgyer_password(args.password)
    if password:
        cmd += ["-F", f"buildPassword={password}"]

    payload = parse_json(run(cmd))
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload


def request_cos_token(args: argparse.Namespace, api_key: str, notes: str) -> dict[str, Any]:
    cmd = [
        "curl",
        "-sS",
        "-X",
        "POST",
        PGYER_GET_COS_TOKEN_URL,
        "-F",
        f"_api_key={api_key}",
        "-F",
        "buildType=android",
        "-F",
        f"buildInstallType={args.install_type}",
        "-F",
        f"buildUpdateDescription={notes}",
    ]
    password = pgyer_password(args.password)
    if password:
        cmd += ["-F", f"buildPassword={password}"]
    payload = parse_json(run(cmd))
    return require_pgyer_data(payload, "getCOSToken")


def upload_to_cos(endpoint: str, params: dict[str, Any], apk: Path) -> None:
    if not endpoint:
        raise SystemExit("Pgyer getCOSToken did not return an upload endpoint.")
    if not params:
        raise SystemExit("Pgyer getCOSToken did not return COS upload params.")

    cmd = [
        "curl",
        "-sS",
        "-o",
        "/dev/null",
        "-w",
        "%{http_code}",
        "--connect-timeout",
        "30",
        "--max-time",
        "1800",
        endpoint,
    ]
    for key, value in params.items():
        cmd += ["--form-string", f"{key}={value}"]
    if "x-cos-meta-file-name" not in params:
        cmd += ["--form-string", f"x-cos-meta-file-name={apk.name}"]
    cmd += ["-F", f"file=@{apk}"]
    http_code = run(cmd).strip()
    if http_code != "204":
        raise SystemExit(f"Pgyer COS upload failed with HTTP status {http_code}.")


def wait_for_build_info(api_key: str, build_key: str, timeout_seconds: int, interval_seconds: int) -> dict[str, Any]:
    if not build_key:
        raise SystemExit("Pgyer getCOSToken did not return build key.")
    deadline = time.time() + max(timeout_seconds, 1)
    interval = max(interval_seconds, 1)
    last_payload: dict[str, Any] = {}
    while True:
        payload = parse_json(run([
            "curl",
            "-sS",
            "--get",
            "--data-urlencode",
            f"_api_key={api_key}",
            "--data-urlencode",
            f"buildKey={build_key}",
            PGYER_BUILD_INFO_URL,
        ]))
        last_payload = payload
        code = str(payload.get("code", "0"))
        if code == "0":
            return payload
        if code != "1247":
            message = str(payload.get("message") or payload.get("raw") or "").strip()
            raise SystemExit(f"Pgyer buildInfo failed: code={code} {message}".strip())
        if time.time() >= deadline:
            raise SystemExit(f"Pgyer buildInfo timed out while publishing: {last_payload}")
        print("Pgyer is still publishing the APK; waiting...", file=sys.stderr)
        time.sleep(interval)


def upload(args: argparse.Namespace) -> dict[str, Any]:
    api_key = require_api_key(args.api_key)
    apk = Path(args.apk or DEFAULT_APK).expanduser()
    if not apk.is_absolute():
        apk = (ROOT / apk).resolve()
    if not apk.exists():
        raise SystemExit(f"APK does not exist: {apk}")
    verify_apk_signature(apk)
    notes = args.notes or default_release_notes()

    if args.legacy:
        return legacy_upload(args, api_key, apk, notes)

    data = request_cos_token(args, api_key, notes)
    endpoint = str(data.get("endpoint") or "").strip()
    build_key = str(data.get("key") or data.get("buildKey") or "").strip()
    params = data.get("params") or {}
    if not isinstance(params, dict):
        raise SystemExit(f"Pgyer getCOSToken returned invalid params: {params}")
    upload_to_cos(endpoint, params, apk)
    payload = wait_for_build_info(
        api_key=api_key,
        build_key=build_key,
        timeout_seconds=args.poll_timeout,
        interval_seconds=args.poll_interval,
    )
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return payload


def build_upload(args: argparse.Namespace) -> dict[str, Any]:
    task = args.gradle_task or "assembleDebug"
    run(gradle_command(task))
    if not args.apk:
        args.apk = str(default_release_apk() if "release" in task.lower() else DEFAULT_APK)
    return upload(args)


def check(args: argparse.Namespace) -> dict[str, Any]:
    api_key = require_api_key(args.api_key)
    app_key = (
        args.app_key
        or os.environ.get("PGYER_APP_KEY")
        or local_secret("pgyer.app_key", "pgyer_app_key")
        or ""
    ).strip()
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
        p.add_argument("--legacy", action="store_true", help="Use the older direct upload endpoint")
        p.add_argument("--poll-timeout", type=int, default=180, help="Seconds to wait for Pgyer buildInfo")
        p.add_argument("--poll-interval", type=int, default=4, help="Seconds between buildInfo polls")

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
