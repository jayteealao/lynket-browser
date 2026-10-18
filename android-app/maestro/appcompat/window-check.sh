#!/usr/bin/env bash
# Captures every screen, dialog and entry point of one debug build, so two builds can be compared
# with compare-captures.py. It runs the capture modes of the other device checks, each into its
# own folder, and then a night pass of its own:
#
#   browsing-captures, browsing-entry-points, browsing-recreate, browsing-amp-error
#   reader-captures, reader-recreate
#   shell-captures
#   history-tabs-captures (SEED=direct)
#   context-events (the web head list and the head colour)
#   night         home, History and Tabs in day, after `cmd uimode night yes` and after
#                 `cmd uimode night no` (screenshot and UI dump each, top activity logged)
#
# Usage: maestro/appcompat/window-check.sh <serial> <evidence-dir> <tag>   (run from android-app/)
# APK=<path> picks the debug APK; it is copied over the last assembleDebug output first, because
# some of the checks install that file. Writes <evidence-dir>/<tag>/<mode>/ and
# <evidence-dir>/<tag>/window-check.txt (one exit code per mode). Night mode and airplane mode
# are switched off at the end. A mode that fails is recorded and the next one runs.
set -uo pipefail

USAGE="usage: window-check.sh <serial> <evidence-dir> <tag>"
SERIAL="${1:?$USAGE}"
ROOT="${2:?$USAGE}"
TAG="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
WC_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
MAESTRO_DIR="$WC_HERE/.."
DEFAULT_APK="$WC_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk"
mkdir -p "$ROOT/$TAG"
OUT="$(hostpath "$(cd "$ROOT/$TAG" && pwd)")"
LOG_NAME="window-check"
CHECK_NAME="window check"
: > "$OUT/window-check.txt"

if [ -n "${APK:-}" ]; then
  [ -f "$APK" ] || fail "APK missing at $APK"
  if [ "$(hostpath "$APK")" != "$(hostpath "$DEFAULT_APK")" ]; then
    mkdir -p "$(dirname "$DEFAULT_APK")"
    cp "$APK" "$DEFAULT_APK"
  fi
fi
[ -f "$DEFAULT_APK" ] || fail "debug APK missing at $DEFAULT_APK"
log "APK $(sha256sum "$DEFAULT_APK" | cut -c1-16) on $SERIAL ($(a emu avd name 2>/dev/null | tr -d '\r' | head -1))"

restore() {
  a shell cmd uimode night no >/dev/null 2>&1 || true
  a shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
}
trap restore EXIT

run_mode() {  # $1 = folder name, rest = command
  local name="$1"; shift
  mkdir -p "$OUT/$name"
  log "== $name"
  ( "$@" ) > "$OUT/$name/run.txt" 2>&1
  local code=$?
  log "$name exit=$code"
  restore
  dismiss_anr
}

run_mode browsing-captures bash "$MAESTRO_DIR/browsing/browsing-check.sh" "$SERIAL" "$OUT/browsing-captures" captures
run_mode browsing-entry-points bash "$MAESTRO_DIR/browsing/browsing-check.sh" "$SERIAL" "$OUT/browsing-entry-points" entry-points
run_mode browsing-recreate bash "$MAESTRO_DIR/browsing/browsing-check.sh" "$SERIAL" "$OUT/browsing-recreate" recreate
run_mode browsing-amp-error bash "$MAESTRO_DIR/browsing/browsing-check.sh" "$SERIAL" "$OUT/browsing-amp-error" amp-error
run_mode reader-captures bash "$MAESTRO_DIR/reader/reader-check.sh" "$SERIAL" "$OUT/reader-captures" captures
run_mode reader-recreate bash "$MAESTRO_DIR/reader/reader-check.sh" "$SERIAL" "$OUT/reader-recreate" recreate
run_mode shell-captures bash "$MAESTRO_DIR/shell/shell-check.sh" "$SERIAL" "$OUT/shell-captures" captures
run_mode history-tabs-captures env SEED=direct bash "$MAESTRO_DIR/history-tabs/history-tabs-check.sh" "$SERIAL" "$OUT/history-tabs-captures" captures
run_mode context-events bash "$MAESTRO_DIR/webheads/context-events-check.sh" "$SERIAL" "$OUT/context-events"

# The night pass: each screen in day, night and day again.
NIGHT="$OUT/night"
mkdir -p "$NIGHT"
log "== night"
night_capture() {  # $1 = screen name, $2 = phase
  sleep 3
  local name="$1-$2"
  local display
  display="$(a shell dumpsys SurfaceFlinger --display-id 2>/dev/null | tr -d '\r' | awk 'NR == 1 { print $2 }')"
  if [ -n "$display" ]; then a exec-out screencap -p -d "$display" > "$NIGHT/$name.png"; else a exec-out screencap -p > "$NIGHT/$name.png"; fi
  a shell uiautomator dump /sdcard/window-check.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/window-check.xml | tr -d '\r' > "$NIGHT/$name.xml"
  echo "$name $(a shell dumpsys activity activities | tr -d '\r' | awk '/topResumedActivity/ { print $NF; exit }')" >> "$NIGHT/top-activity.txt"
}
night_screen() {  # $1 = name, $2 = activity
  a shell am force-stop "$PKG"
  a shell am start -W -n "$PKG/$2" >/dev/null 2>&1
  night_capture "$1" day
  a shell cmd uimode night yes >/dev/null; night_capture "$1" night
  a shell cmd uimode night no >/dev/null; night_capture "$1" day-again
}
a logcat -c -b crash >/dev/null 2>&1 || true
night_screen home "$MAIN"
night_screen history arun.com.chromer.history.HistoryActivity
night_screen tabs arun.com.chromer.tabs.ui.TabsActivity
a logcat -d -b crash | tr -d '\r' > "$NIGHT/crash.txt"
a shell am force-stop "$PKG"
if grep -q "$PKG" "$NIGHT/crash.txt"; then log "night exit=1 (crash)"; else log "night exit=0"; fi
log "done"
