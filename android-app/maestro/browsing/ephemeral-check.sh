#!/usr/bin/env bash
# Device check of incognito links in Slide over: with full incognito mode on (the incognito tile's
# setting), a link opens in an ephemeral Custom Tab of a browser that supports one, and in the
# app's web view otherwise. A local server (cookie-server.py, through adb reverse) logs the cookies
# the browser sends. One mode per run:
#   ephemeral  A browser with ephemeral tabs (Chrome on a current Play image): the link to /set
#              opens in Chrome, not in Lynket's web view, and the debug launch log line says
#              ephemeral=true; after Back closes the tab, /read opened in Chrome itself carries no
#              lynket_probe cookie, and Chrome's History lists "Cookie read" but not
#              "Ephemeral check"; then a control: /set and /read opened in Chrome itself carry the
#              cookie, which shows the server sees a cookie the browser kept.
#   webview    A browser without ephemeral tabs (an older Chrome, named the provider by the
#              settings file): the link opens in Lynket's web view, no Chrome activity is resumed,
#              and no toast, snackbar or dialog of Lynket shows.
# Each mode writes <mode>.png, <mode>-activities.txt, <mode>.xml, <mode>.logcat.txt, cookies.log
# and PASS or FAIL per assertion to ephemeral.txt. It also records Chrome's version and whether its
# Custom Tabs service declares ephemeral browsing. Chrome's first-run screen, the reverse port, the
# server and the app's data are restored on exit.
#
# Usage: maestro/browsing/ephemeral-check.sh <emulator-serial> <evidence-dir> <ephemeral|webview>
#        (from android-app/, after ./gradlew :lynket:assembleDebug; emulators only)
set -euo pipefail

USAGE="usage: ephemeral-check.sh <emulator-serial> <evidence-dir> <ephemeral|webview>"
if [ "$#" -ne 3 ]; then echo "$USAGE" >&2; exit 2; fi
SERIAL="$1"
OUT="$2"
WHICH="$3"
case "$WHICH" in
  ephemeral | webview) ;;
  *) echo "unknown mode: $WHICH; $USAGE" >&2; exit 2 ;;
esac
case "$SERIAL" in
  emulator-*) ;;
  *) echo "refusing $SERIAL: this check clears the app's data and runs on an emulator only" >&2; exit 2 ;;
esac

source "$(dirname "$0")/../webheads/lib.sh"
EC_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
APP_ROOT="$(cd "$EC_HERE/../.." && pwd)"
APK_DEBUG="$APP_ROOT/lynket/build/outputs/apk/debug/lynket-debug.apk"
SEED="$EC_HERE/../seed"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="ephemeral"
CHECK_NAME="ephemeral $WHICH check"
COOKIES="$OUT/cookies.log"
FAILS=0

pass() { log "PASS $WHICH: $*"; }
nope() { log "FAIL $WHICH: $*"; FAILS=$((FAILS + 1)); }

# Restored on exit: Chrome's first-run screen, the reverse port, the server and the app's data.
PORT=""
SERVER_PID=""
CHROME_CHANGED=""
CHROME_DEBUG_BEFORE=""
on_exit() {
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
  chrome_restore
  a shell pm clear "$PKG" >/dev/null 2>&1 || true
}
trap on_exit EXIT

serve_cookie_probe() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  : > "$COOKIES"
  python "$EC_HERE/cookie-server.py" "$PORT" "$COOKIES" > "$OUT/server.log" 2>&1 &
  SERVER_PID=$!
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/read')" 2>/dev/null && return 0
    sleep 0.5
  done
  fail "the cookie server did not start"
}

# The resumed activity as "<package>/<class>", from the activity dump.
resumed_activity() {
  a shell dumpsys activity activities | tr -d '\r' |
    grep -m1 -E "topResumedActivity=|mResumedActivity:" |
    sed -E 's/.* u0 ([^ }]+).*/\1/'
}

# Records the activities, a screenshot, a UI dump and the app's log lines under the name $1.
record() {
  a shell dumpsys activity activities | tr -d '\r' | grep -E "topResumedActivity=|mResumedActivity:|ResumedActivity" > "$OUT/$1-activities.txt" || true
  echo "resumed: $(resumed_activity)" >> "$OUT/$1-activities.txt"
  shot "$1"
  a shell uiautomator dump /sdcard/ephemeral-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/ephemeral-ui.xml > "$OUT/$1.xml" 2>/dev/null || true
  a logcat -d | tr -d '\r' | grep -E "Custom Tab launch|AndroidRuntime|FATAL EXCEPTION" > "$OUT/$1.logcat.txt" || true
}

# Opens $1 in Chrome itself, in its normal profile.
open_in_chrome() {
  a shell "am start -a android.intent.action.VIEW -d '$1' -p com.android.chrome" >/dev/null
  sleep 5
  dismiss_anr
}

# The lines of the cookie log for a path, the last one first.
cookie_line() { tac "$COOKIES" | grep -m1 -P "^$1\t" || true; }

# Centre of the first node with the resource id $1, from a UI dump; empty if none.
id_centre() {
  a shell uiautomator dump /sdcard/ephemeral-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/ephemeral-ui.xml | tr -d '\r' | tr '>' '\n' |
    grep -F "resource-id=\"$1\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Opens Chrome's History in its own window and dumps it to history.xml; returns 1 when the address
# bar could not be found.
dump_chrome_history() {
  local xy
  xy="$(id_centre com.android.chrome:id/url_bar)"
  [ -n "$xy" ] || xy="$(id_centre com.android.chrome:id/search_box_text)"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  a shell input tap $xy
  sleep 1
  a shell input text "chrome://history"
  a shell input keyevent KEYCODE_ENTER
  sleep 4
  shot history
  a shell uiautomator dump /sdcard/ephemeral-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/ephemeral-ui.xml > "$OUT/history.xml" 2>/dev/null || true
}

[ -f "$APK_DEBUG" ] || fail "missing $APK_DEBUG; run ./gradlew :lynket:assembleDebug"
a install -r "$(hostpath "$APK_DEBUG")" >/dev/null
chrome_without_first_run
serve_cookie_probe
log "Chrome $(a shell dumpsys package com.android.chrome | tr -d '\r' | grep -m1 versionName | sed 's/.*versionName=//')"
if a shell dumpsys package com.android.chrome | tr -d '\r' | grep -q "EphemeralBrowsing"; then
  EPHEMERAL_DECLARED=1
  log "Chrome's Custom Tabs service declares ephemeral browsing"
else
  EPHEMERAL_DECLARED=""
  log "Chrome's Custom Tabs service does not declare ephemeral browsing"
fi

case "$WHICH" in
  ephemeral)
    [ -n "$EPHEMERAL_DECLARED" ] && pass "Chrome declares ephemeral browsing" || nope "Chrome does not declare ephemeral browsing"
    seed_settings "$SEED/incognito-full.xml"
    a shell am force-stop com.android.chrome
    a logcat -c
    open_link "http://localhost:$PORT/set"
    sleep 7
    dismiss_anr
    record ephemeral
    TOP="$(resumed_activity)"
    log "resumed after the link: $TOP"
    case "$TOP" in
      *WebViewActivity*) nope "the link opened in Lynket's web view" ;;
      com.android.chrome/*) pass "the link opened in Chrome" ;;
      *) nope "the link did not open in Chrome ($TOP)" ;;
    esac
    grep -q "Custom Tab launch: .*ephemeral=true" "$OUT/ephemeral.logcat.txt" \
      && pass "the launch log says ephemeral=true" || nope "no launch log line with ephemeral=true"
    grep -qP "^/set\t" "$COOKIES" && pass "the tab loaded /set" || nope "the tab never asked for /set"

    a shell input keyevent KEYCODE_BACK
    sleep 2
    open_in_chrome "http://localhost:$PORT/read"
    record ephemeral-read
    READ="$(cookie_line /read)"
    log "read after the tab closed: $READ"
    case "$READ" in
      "") nope "Chrome never asked for /read" ;;
      *lynket_probe*) nope "Chrome kept the cookie of the ephemeral tab" ;;
      *) pass "no cookie of the ephemeral tab in Chrome's normal profile" ;;
    esac

    # History before the control, which visits "Ephemeral check" in the normal profile itself.
    if dump_chrome_history; then
      grep -q "Cookie read" "$OUT/history.xml" && pass "History lists the normal visit" || nope "History does not list the normal visit (the dump may not show History)"
      grep -q "Ephemeral check" "$OUT/history.xml" && nope "History lists the ephemeral tab's page" || pass "History does not list the ephemeral tab's page"
    else
      nope "Chrome's address bar was not found; History not read"
    fi

    open_in_chrome "http://localhost:$PORT/set"
    open_in_chrome "http://localhost:$PORT/read"
    record ephemeral-control
    CONTROL="$(cookie_line /read)"
    log "control read: $CONTROL"
    case "$CONTROL" in
      *lynket_probe=1*) pass "the control read carries the cookie Chrome kept" ;;
      *) nope "the control read carries no cookie; the probe cannot see a kept cookie" ;;
    esac
    ;;
  webview)
    [ -z "$EPHEMERAL_DECLARED" ] && pass "Chrome does not declare ephemeral browsing" || nope "Chrome declares ephemeral browsing; this mode needs one that does not"
    seed_settings "$SEED/incognito-full-provider.xml"
    a shell am force-stop com.android.chrome
    a logcat -c
    open_link "http://localhost:$PORT/set"
    sleep 7
    dismiss_anr
    record webview
    TOP="$(resumed_activity)"
    log "resumed after the link: $TOP"
    case "$TOP" in
      "$PKG"/*WebViewActivity*) pass "the link opened in Lynket's web view" ;;
      *) nope "the link did not open in Lynket's web view ($TOP)" ;;
    esac
    case "$TOP" in
      com.android.chrome/*) nope "a Chrome activity is resumed" ;;
      *) pass "no Chrome activity is resumed" ;;
    esac
    a shell dumpsys window windows | tr -d '\r' | grep -E "^  Window #" > "$OUT/webview-windows.txt" || true
    if grep -qE "Toast|ty=TOAST" "$OUT/webview-windows.txt"; then nope "a toast window showed"; else pass "no toast"; fi
    if grep -qiE "snackbar" "$OUT/webview.xml"; then nope "a snackbar showed"; else pass "no snackbar"; fi
    if grep -qE "AlertDialog|BubbleRefusedActivity" "$OUT/webview-windows.txt"; then nope "a Lynket dialog showed"; else pass "no Lynket dialog"; fi
    ;;
esac

grep -q "FATAL EXCEPTION" "$OUT"/*.logcat.txt 2>/dev/null && nope "a crash in the log" || true
if [ "$FAILS" -gt 0 ]; then log "ephemeral $WHICH: $FAILS failed"; exit 1; fi
log "ephemeral $WHICH: all passed"
