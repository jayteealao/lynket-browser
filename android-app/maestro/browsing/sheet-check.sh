#!/usr/bin/env bash
# Device check of "open as a sheet": a Slide over link opens as a Partial Custom Tab over the app it
# came from. Settings is opened first, then a fixture page through the intercept activity, as a
# link from another app arrives. One mode per run:
#   phone     Bottom sheet on a phone: the browser's window starts between 45 % and 55 % of the
#             screen height, Settings stays in view above it, a tap at 25 % of the height does not
#             reach Settings; then a tap outside the sheet, and on a fresh open a drag down, each
#             leave no sheet window (the browser decides both; a FAIL there is the browser's).
#   wide      Side sheet on a window 600 dp wide or more (an unfolded fold or a tablet): the
#             browser's window is narrower than the screen and touches its left or right edge.
#   widths    The width at launch picks the form: at 1400 px (a side sheet), at 1000 px (a bottom
#             sheet), and at full width with the side sheet option off (a bottom sheet). The screen
#             size is restored on exit.
#   off       The default settings: the tab fills the screen and the launch log line says
#             sheet=false.
#   fallback  A provider without sheets (APK=<path> installs one, such as Firefox or a Chrome older
#             than 107; PROVIDER=<package> names it): the tab fills the screen and no toast,
#             snackbar or dialog shows.
# Each mode writes <mode>.png, <mode>-windows.txt, <mode>.xml, <mode>.logcat.txt and PASS or FAIL
# per assertion to sheet.txt. Chrome's first-run screen, the screen size and the reverse port are
# restored on exit.
#
# Usage: maestro/browsing/sheet-check.sh <emulator-serial> <evidence-dir> <phone|wide|widths|off|fallback>
#        (from android-app/, after ./gradlew :lynket:assembleDebug; emulators only)
set -euo pipefail

USAGE="usage: sheet-check.sh <emulator-serial> <evidence-dir> <phone|wide|widths|off|fallback>"
if [ "$#" -ne 3 ]; then echo "$USAGE" >&2; exit 2; fi
SERIAL="$1"
OUT="$2"
WHICH="$3"
case "$WHICH" in
  phone | wide | widths | off | fallback) ;;
  *) echo "unknown mode: $WHICH; $USAGE" >&2; exit 2 ;;
esac
case "$SERIAL" in
  emulator-*) ;;
  *) echo "refusing $SERIAL: this check clears the app's data and runs on an emulator only" >&2; exit 2 ;;
esac

source "$(dirname "$0")/../webheads/lib.sh"
SC_HERE="$(cd "$(dirname "$0")" && pwd)"
APP_ROOT="$(cd "$SC_HERE/../.." && pwd)"
APK_DEBUG="$APP_ROOT/lynket/build/outputs/apk/debug/lynket-debug.apk"
SEED="$SC_HERE/../seed"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="sheet"
CHECK_NAME="sheet $WHICH check"
FAILS=0

pass() { log "PASS $WHICH: $*"; }
nope() { log "FAIL $WHICH: $*"; FAILS=$((FAILS + 1)); }

# Restored on exit: Chrome's first-run screen, the screen size, the reverse port and the server.
PORT=""
SERVER_PID=""
CHROME_CHANGED=""
CHROME_DEBUG_BEFORE=""
SIZE_CHANGED=""
on_exit() {
  [ -n "$SIZE_CHANGED" ] && a shell wm size reset >/dev/null 2>&1 || true
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
  if [ -n "$CHROME_CHANGED" ]; then
    a shell rm -f /data/local/tmp/chrome-command-line >/dev/null 2>&1 || true
    if [ -n "$CHROME_DEBUG_BEFORE" ] && [ "$CHROME_DEBUG_BEFORE" != "null" ]; then
      a shell am set-debug-app --persistent "$CHROME_DEBUG_BEFORE" >/dev/null 2>&1 || true
    else
      a shell am clear-debug-app >/dev/null 2>&1 || true
    fi
  fi
}
trap on_exit EXIT

# Chrome reads its command line from /data/local/tmp only while it is the debug app; these flags
# skip its first-run screen, which otherwise covers the tab a link opens.
chrome_without_first_run() {
  a shell pm list packages com.android.chrome | grep -q "package:com.android.chrome" || return 0
  CHROME_DEBUG_BEFORE="$(a shell settings get global debug_app | tr -d '\r')"
  CHROME_CHANGED=1
  a shell am set-debug-app --persistent com.android.chrome >/dev/null
  a shell "echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line"
  a shell am force-stop com.android.chrome
}

serve_fixtures() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$SC_HERE/../reader/fixtures" > "$OUT/server.log" 2>&1 &
  SERVER_PID=$!
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/article.html')" 2>/dev/null && return 0
    sleep 0.5
  done
  fail "the fixture server did not start"
}

# The focused window as "<name> <left> <top> <right> <bottom>".
focus_frame() {
  a shell dumpsys window windows | tr -d '\r' | awk '
    /^  Window #/ { name = $0; sub(/.* u0 /, "", name); sub(/}:.*/, "", name); f = "" }
    match($0, / frame=\[-?[0-9]+,-?[0-9]+\]\[-?[0-9]+,-?[0-9]+\]/) && f == "" {
      f = substr($0, RSTART + 7, RLENGTH - 7); gsub(/[\[\]]/, " ", f); gsub(/,/, " ", f); frames[name] = f
    }
    /mCurrentFocus=Window/ { focus = $0; sub(/.* u0 /, "", focus); sub(/}.*/, "", focus) }
    END { print focus, frames[focus] }'
}

# Opens Settings, then the fixture page as a link from Settings; records the windows, a screenshot,
# a UI dump and the launch log line under the name $1.
open_over_settings() {
  a shell am start -W -a android.settings.SETTINGS >/dev/null
  sleep 2
  a logcat -c
  open_link "http://localhost:$PORT/article.html"
  sleep 7
  dismiss_anr
  record "$1"
}

record() {
  a shell dumpsys window windows | tr -d '\r' | grep -E "^  Window #| frame=|mCurrentFocus" > "$OUT/$1-windows.txt" || true
  focus_frame >> "$OUT/$1-windows.txt"
  shot "$1"
  a shell uiautomator dump /sdcard/sheet-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/sheet-ui.xml > "$OUT/$1.xml" 2>/dev/null || true
  a logcat -d | tr -d '\r' | grep -E "Custom Tab launch|AndroidRuntime" > "$OUT/$1.logcat.txt" || true
}

sheet_open() { focus_frame | grep -qE "CustomTab|customtabs"; }

# Bottom sheet: top between 45 % and 55 % of the height, full width.
assert_bottom() {
  local name l t r b w h
  read -r w h < <(screen_size)
  read -r name l t r b < <(focus_frame)
  log "$1: focus $name frame $l $t $r $b on $w x $h"
  if [ -n "${t:-}" ] && [ "$((t * 100 / h))" -ge 45 ] && [ "$((t * 100 / h))" -le 55 ] && [ "$((r - l))" -ge "$((w - 2))" ]; then
    pass "$1: bottom sheet, top at $((t * 100 / h)) % of the height"
  else nope "$1: no bottom sheet at half the height"; fi
}

# Side sheet: narrower than the screen, touching its left or right edge.
assert_side() {
  local name l t r b w h
  read -r w h < <(screen_size)
  read -r name l t r b < <(focus_frame)
  log "$1: focus $name frame $l $t $r $b on $w x $h"
  if [ -n "${r:-}" ] && [ "$((r - l))" -lt "$w" ] && { [ "$l" -le 0 ] || [ "$r" -ge "$w" ]; }; then
    pass "$1: side sheet $((r - l)) px wide"
  else nope "$1: no side sheet"; fi
}

# Full screen: the focused window spans the width and starts in the top tenth.
assert_full() {
  local name l t r b w h
  read -r w h < <(screen_size)
  read -r name l t r b < <(focus_frame)
  log "$1: focus $name frame $l $t $r $b on $w x $h"
  if [ -n "${b:-}" ] && [ "$((r - l))" -ge "$((w - 2))" ] && [ "$((t * 10))" -lt "$h" ]; then
    pass "$1: full screen"
  else nope "$1: not full screen"; fi
}

[ -f "$APK_DEBUG" ] || fail "missing $APK_DEBUG; run ./gradlew :lynket:assembleDebug"
a install -r "$(hostpath "$APK_DEBUG")" >/dev/null
if [ -n "${APK:-}" ]; then a install -r "$(hostpath "$APK")" >/dev/null; fi
chrome_without_first_run
serve_fixtures
log "Chrome $(a shell dumpsys package com.android.chrome | tr -d '\r' | grep -m1 versionName | sed 's/.*versionName=//')"

case "$WHICH" in
  phone)
    seed_settings "$SEED/sheet-on.xml"
    open_over_settings phone
    assert_bottom phone
    a shell uiautomator dump /sdcard/sheet-ui.xml >/dev/null 2>&1 || true
    a exec-out cat /sdcard/sheet-ui.xml > "$OUT/phone-before-tap.xml"
    read -r W H < <(screen_size)
    a shell input tap "$((W / 2))" "$((H / 4))"
    sleep 3
    record phone-after-tap
    if focus_frame | grep -q "com.android.settings"; then
      pass "a tap outside closed the sheet; Settings has the focus"
      # The tap closed the sheet, so it did not reach Settings only if Settings is where it was.
      a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | grep -q "SettingsHomepageActivity" \
        && pass "the tap outside did not open anything in Settings" || nope "the tap outside reached Settings"
    else
      sheet_open && pass "the tap at 25 % did not reach Settings (the sheet kept the focus)" || nope "the focus left the sheet"
      nope "a tap outside left the sheet open (browser behaviour)"
    fi
    a shell am force-stop com.android.chrome
    open_over_settings phone-drag
    read -r _ L T R B < <(focus_frame)
    a shell input swipe "$((W / 2))" "$((T + 140))" "$((W / 2))" "$((H - 16))" 250
    sleep 3
    record phone-after-drag
    if sheet_open; then nope "a drag down left the sheet open"; else pass "a drag down closed the sheet"; fi
    ;;
  wide)
    seed_settings "$SEED/sheet-on.xml"
    open_over_settings wide
    assert_side wide
    ;;
  widths)
    seed_settings "$SEED/sheet-on.xml"
    SIZE_CHANGED=1
    read -r _ PH < <(screen_size)
    a shell wm size "1400x$PH"
    a shell am force-stop com.android.chrome
    open_over_settings widths-1400
    assert_side widths-1400
    a shell wm size "1000x$PH"
    a shell am force-stop com.android.chrome
    open_over_settings widths-1000
    assert_bottom widths-1000
    a shell wm size reset
    seed_settings "$SEED/sheet-on-side-off.xml"
    a shell am force-stop com.android.chrome
    open_over_settings widths-side-off
    assert_bottom widths-side-off
    ;;
  off)
    # The sheet settings file with the sheet option left at its default (off).
    sed '/custom_tab_sheet/d' "$SEED/sheet-on.xml" > "$OUT/off-seed.xml"
    seed_settings "$OUT/off-seed.xml"
    open_over_settings off
    assert_full off
    grep -q "Custom Tab launch: sheet=false" "$OUT/off.logcat.txt" \
      && pass "the launch log says sheet=false" || nope "no launch log line with sheet=false"
    ;;
  fallback)
    [ -n "${PROVIDER:-}" ] || PROVIDER=com.android.chrome
    sed "s#</map>#    <string name=\"preferred_package\">$PROVIDER</string>\n</map>#" "$SEED/sheet-on.xml" > "$OUT/fallback-seed.xml"
    seed_settings "$OUT/fallback-seed.xml"
    log "provider $PROVIDER $(a shell dumpsys package "$PROVIDER" | tr -d '\r' | grep -m1 versionName | sed 's/.*versionName=//')"
    open_over_settings fallback
    assert_full fallback
    if grep -qE "Toast|ty=TOAST" "$OUT/fallback-windows.txt"; then nope "a toast window showed"; else pass "no toast"; fi
    if grep -qiE "snackbar" "$OUT/fallback.xml"; then nope "a snackbar showed"; else pass "no snackbar"; fi
    if grep -q "arun.com.chromer" "$OUT/fallback.xml"; then nope "a Lynket window or dialog showed"; else pass "no Lynket dialog"; fi
    ;;
esac

grep -q "FATAL EXCEPTION" "$OUT"/*.logcat.txt 2>/dev/null && nope "a crash in the log" || true
if [ "$FAILS" -gt 0 ]; then log "sheet $WHICH: $FAILS failed"; exit 1; fi
log "sheet $WHICH: all passed"
