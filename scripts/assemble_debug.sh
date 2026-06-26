#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Chaquopy invokes pip from Gradle and pip inherits proxy variables from
# Android Studio or the shell. A stale local proxy makes dependency resolution
# fail as "No matching distribution found", so bypass proxies for package hosts.
export NO_PROXY="${NO_PROXY:-*}"
export no_proxy="${no_proxy:-*}"

JBR_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
JAVA_ARG=()
if [[ -d "$JBR_HOME" ]]; then
  JAVA_ARG=(-Dorg.gradle.java.home="$JBR_HOME")
fi

TASK="${1:-:app:assembleDebug}"
shift || true

exec ./gradlew "${JAVA_ARG[@]}" "$TASK" "$@"
