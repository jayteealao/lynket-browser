#!/usr/bin/env bash
# Reference captures of store release 2.1.3: every intro page, the changelog dialog, the home, the
# home menu sheet, About and Tips. Installs the existing 2.1.3 debug APK (it is not rebuilt; see
# maestro/build-old-apk.sh), runs legacy-2.1.3.yaml, then uninstalls 2.1.3 again.
#
# Usage: maestro/shell/legacy-2.1.3-captures.sh <serial> <output-dir>   (from android-app/)
# The device's debug app is uninstalled first, so use a capture emulator, not a seeded one.
set -euo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: legacy-2.1.3-captures.sh <serial> <output-dir>}"
OUT="${2:?usage: legacy-2.1.3-captures.sh <serial> <output-dir>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE/../../build/old-apk/lynket-2.1.3-debug.apk"
PKG=arun.com.chromer.debug
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
a() { adb -s "$SERIAL" "$@"; }

[ -f "$APK" ] || { echo "missing $APK; build it with maestro/build-old-apk.sh" >&2; exit 1; }
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"

a uninstall "$PKG" >/dev/null 2>&1 || true
a install "$(hostpath "$APK")" >/dev/null
a logcat -c
maestro --device "$SERIAL" test -e OUT="$OUT" --test-output-dir "$OUT/maestro" \
  "$(hostpath "$HERE/legacy-2.1.3.yaml")" | tee "$OUT/legacy-2.1.3.maestro.txt"
a logcat -d -b crash | tr -d '\r' > "$OUT/legacy-2.1.3.crash.txt"
a uninstall "$PKG" >/dev/null
echo "captures in $OUT"
