# MobileClaw Debug Scaffold

`scripts/mobileclaw_debug.py` is a local ADB scaffold for iterative debugging on real devices. It is designed for Codex and developers to build, install, operate, record, and collect evidence from MobileClaw without adding temporary debug code to the app.

## Device Selection

When more than one device is connected, always pass `--serial`:

```bash
python3 scripts/mobileclaw_debug.py devices
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee doctor
```

## Common Commands

Build and install:

```bash
python3 scripts/mobileclaw_debug.py build
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee install --build
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee launch
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee page HOME
```

Capture debugging evidence:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee screenshot
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee xml
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee logcat --out debug_artifacts/logcat.txt
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee bundle --label crash_or_ui_bug
```

One-command triage for a user-reported problem:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee triage "floating ball appears while app is foreground" --clear-logcat --launch --watch-seconds 6
```

`triage` creates a timestamped artifact directory with screenshot, XML, foreground/activity diagnostics, logcat, and an `analysis.md` summary. Use it as the first command when investigating a bug.

Analyze an existing artifact:

```bash
python3 scripts/mobileclaw_debug.py analyze debug_artifacts/20260521_192316_smoke --problem "group chat crash"
```

Operate the phone:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee tap 540 1820
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee swipe 540 1800 540 600 --ms 450
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee text hello
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee key BACK
```

Record the screen:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee record --seconds 15
```

Record and generate a GitHub-friendly GIF:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee record --seconds 8 --gif --fps 12 --width 420
```

Convert an existing MP4 to GIF:

```bash
python3 scripts/mobileclaw_debug.py gif debug_artifacts/demo/screen.mp4 --out docs/demo.gif --fps 12 --width 420
```

GIF conversion requires `ffmpeg`:

```bash
brew install ffmpeg
```

Watch a live flow by taking repeated screenshots, XML dumps, and foreground diagnostics:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee watch --seconds 30 --interval 2 --label overlay_flow
```

## Scenario JSON

Scenarios are small JSON files that replay a repeatable UI flow and capture screenshots/XML at key points.

```json
{
  "steps": [
    { "action": "launch" },
    { "action": "sleep", "seconds": 1 },
    { "action": "screenshot", "name": "home" },
    { "action": "xml", "name": "home_tree" },
    { "action": "assert_package", "value": "com.mobileclaw" },
    { "action": "tap", "x": 540, "y": 1820 },
    { "action": "sleep", "seconds": 1 },
    { "action": "assert_text", "value": "Chat" },
    { "action": "bundle" }
  ]
}
```

Run it:

```bash
python3 scripts/mobileclaw_debug.py --serial 74c5f6ee scenario docs/scenarios/example.json
```

## Output

All default captures go under `debug_artifacts/` with timestamped folders. The important files are:

- `screen.png`: current device screenshot.
- `window.xml`: current UIAutomator accessibility tree.
- `logcat.txt`: current logcat dump.
- `foreground.txt`: foreground/window diagnostics.
- `activity.txt`: activity stack diagnostics.
- `analysis.md`: generated crash/UI summary and next suggested debug move.

This gives Codex enough evidence to inspect the current UI, identify crashes, compare coordinates, and iterate without asking the user for repeated screenshots.

## Debug Loop

The intended repair loop is:

```text
user reports issue
-> run triage
-> inspect analysis.md + screenshot/XML/logcat
-> patch code
-> compile
-> rerun triage or scenario with assertions
-> keep artifact if the fix needs a README/GitHub demo
```
