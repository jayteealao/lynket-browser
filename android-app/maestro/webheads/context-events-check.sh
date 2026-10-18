#!/usr/bin/env bash
# Device check of the events between the web head service, the web head list and settings: each
# action of the web head list and the web head colour setting reaches the heads on screen. Pages
# come from maestro/reader/fixtures/, served from this host with python -m http.server on a free
# port and reached through adb reverse.
#
#   heads       web heads mode with the overlay allowed; two links give two heads and the trash
#   colour      Look and feel: the web head colour set to #00C853 through the hex field repaints
#               the heads on screen (pixels of that colour inside the head windows, counted on a
#               screenshot before and after)
#   open        a tap on the front head opens its page in a Chrome Custom Tab
#   share       a long press opens the web head list; a site's Share button opens the system chooser
#   remove-one  the list's "Close <site>" button removes that head; one row stays on the list
#   close-all-sheet
#               closing the last site from the list closes the list, every head and the service
#   close-all-notification
#               two links again, then "Tap to close all" on the notification removes every head
#   exit        the web head colour and the browsing mode the device had are restored; no crash
#               entry for the app in the crash buffer
#
# Usage: maestro/webheads/context-events-check.sh <serial> <evidence-dir>   (run from android-app/)
# Needs the debug build installed (./gradlew :lynket:installDebug), Chrome's first run done and
# Python with Pillow on this host. Each step writes PASS or FAIL to <evidence-dir>/context-events.txt.
set -euo pipefail

USAGE="usage: context-events-check.sh <serial> <evidence-dir>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
source "$(dirname "$0")/lib.sh"
CE_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
FIXTURES="$CE_HERE/../reader/fixtures"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="context-events"
CHECK_NAME="context events check"
: > "$OUT/context-events.txt"

pass() { log "PASS $1"; }
step_fail() { log "FAIL $1: $2"; FAILED=1; }
FAILED=0

NEW_HEX=00C853

# ---------------------------------------------------------------------------------------------
# Fixture server (as in maestro/browsing/website-extra-check.sh)

PORT=""
SERVER_PID=""
start_server() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$FIXTURES" > "$OUT/server.log" 2>&1 &
  SERVER_PID=$!
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  local _
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/article.html')" 2>/dev/null && break
    sleep 0.5
  done
  log "fixture server on port $PORT (adb reverse tcp:$PORT)"
}

# ---------------------------------------------------------------------------------------------
# UI and store helpers (as in maestro/browsing/website-extra-check.sh)

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/context-events-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/context-events-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/context-events-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/context-events-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

centre_of() {  # $1 = dump fragment, for example 'content-desc="Close '
  { ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

tap_on() {
  local xy _
  for _ in $(seq 1 10); do
    xy="$(centre_of "$1")"
    if [ -n "$xy" ]; then
      # shellcheck disable=SC2086
      a shell input tap $xy
      return 0
    fi
    dismiss_anr
    sleep 2
  done
  return 1
}

wait_for() {  # $1 = dump fragment
  local _
  for _ in $(seq 1 15); do
    ui_dump | grep -qF -- "$1" && return 0
    dismiss_anr
    sleep 2
  done
  return 1
}

dump_store() {
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r'
}

stored() {  # $1 = settings key: the stored value ("null" when absent)
  dump_store | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}

effective() {  # $1 = settings key: the value the app reads (the default when absent)
  dump_store | awk -F'\t' -v k="$1" '$1 == k { print $4 }'
}

top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}

wait_top() {  # $1 = text the top resumed activity must contain
  local _
  for _ in $(seq 1 15); do
    top_activity | grep -qF -- "$1" && return 0
    sleep 1
  done
  return 1
}

wait_not_top() {  # $1 = text the top resumed activity must no longer contain
  local _
  for _ in $(seq 1 15); do
    top_activity | grep -qF -- "$1" || return 0
    sleep 1
  done
  return 1
}

wait_overlays() {  # $1 = expected number of overlay windows
  local _
  for _ in $(seq 1 15); do
    [ "$(overlay_count)" -eq "$1" ] && return 0
    sleep 1
  done
  return 1
}

mode_title() {  # the browsing mode row title for the stored keys
  if [ "$1" = true ]; then echo "Web heads"; elif [ "$2" = true ]; then echo "Native Bubbles (Beta)"; else echo "Slide over"; fi
}

service_running() {
  a shell dumpsys activity services "$PKG" | tr -d '\r' | grep -qF "$WEB_HEAD_SERVICE"
}

# Pixels within 12 per channel of #$2 inside the touchable overlay windows (the heads) on the
# screenshot $1.png; also saves a crop of each head window as $1-head<N>.png.
colour_pixels() {
  overlay_windows | awk '$1 == 1 { print $2, $3, $4, $5 }' > "$OUT/$1-heads.txt"
  python -I - "$OUT/$1.png" "$OUT/$1-heads.txt" "$2" <<'PY'
import sys
from PIL import Image
shot, boxes, hexcode = sys.argv[1], sys.argv[2], sys.argv[3]
target = tuple(int(hexcode[i:i + 2], 16) for i in (0, 2, 4))
img = Image.open(shot).convert("RGB")
total = 0
for n, line in enumerate(open(boxes).read().split("\n")):
    if not line.strip():
        continue
    l, t, r, b = (int(v) for v in line.split())
    l, t, r, b = max(l, 0), max(t, 0), min(r, img.width), min(b, img.height)
    if r <= l or b <= t:
        continue
    crop = img.crop((l, t, r, b))
    crop.save(shot[:-4] + "-head%d.png" % n)
    total += sum(1 for p in crop.getdata() if all(abs(p[i] - target[i]) <= 12 for i in range(3)))
print(total)
PY
}

# Look and feel: the web head colour row, then the hex field of the colour sheet.
set_head_colour() {  # $1 = RRGGBB
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" LookAndFeel >/dev/null
  sleep 3
  # The row sits at the bottom of the first screen, partly under the gesture bar: scroll first.
  a shell input swipe 540 1700 540 700 400
  sleep 1
  a shell input swipe 540 1700 540 700 400
  sleep 1
  tap_on 'resource-id="row-webhead_color"' || return 1
  # The sheet is its own window, which does not show test tags as resource ids.
  wait_for 'class="android.widget.EditText"' || return 1
  tap_on 'class="android.widget.EditText"' || return 1
  sleep 1
  a shell input keyevent KEYCODE_MOVE_END
  a shell input keyevent 67 67 67 67 67 67 67 67
  a shell input text "$1"
  sleep 1
  tap_on 'text="Done"' || return 1
  sleep 2
  a shell input keyevent KEYCODE_HOME
  sleep 2
}

hex_of() {  # $1 = colour as a signed int
  python -c "import sys; print('%06X' % (int(sys.argv[1]) & 0xFFFFFF))" "$1"
}

long_press_front_head() {
  local xy
  xy="$(head_centre)"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  set -- $xy
  a shell input swipe "$1" "$2" "$1" "$2" 1200
  wait_top "WebHeadContextActivity"
}

site_buttons() {  # $1 = Close|Share: the content descriptions of the site rows' buttons
  ui_dump | tr '>' '\n' | grep -o "content-desc=\"$1 [^\"]*\"" | grep -v '"Close sheet"' | sed 's/content-desc="//; s/"$//' || true
}

open_two_links() {
  open_link "$ARTICLE_URL"
  sleep 4
  open_link "$OTHER_URL"
  sleep 8
}

# ---------------------------------------------------------------------------------------------

start_server
ARTICLE_URL="http://localhost:$PORT/article.html"
OTHER_URL="http://localhost:$PORT/no-article.html"

WEBHEADS_BEFORE="$(stored webhead_enabled_pref)"
BUBBLES_BEFORE="$(stored native_bubbles_preference)"
ARTICLE_BEFORE="$(stored article_mode_pref)"
COLOUR_STORED_BEFORE="$(stored webhead_color)"
COLOUR_BEFORE="$(effective webhead_color)"
CLOSE_ON_OPEN="$(effective webhead_close_onclick_pref)"
log "before: webhead_enabled_pref=$WEBHEADS_BEFORE native_bubbles_preference=$BUBBLES_BEFORE article_mode_pref=$ARTICLE_BEFORE webhead_color=$COLOUR_STORED_BEFORE (reads $COLOUR_BEFORE, #$(hex_of "$COLOUR_BEFORE")) webhead_close_onclick_pref=$CLOSE_ON_OPEN"
[ "$ARTICLE_BEFORE" = true ] && fail "article mode is on; turn it off (Article tile) so links open a page, not the reader"
[ "$CLOSE_ON_OPEN" = true ] && fail "close-on-open is on; the open step expects the head to stay"
[ "$(hex_of "$COLOUR_BEFORE")" = "$NEW_HEX" ] && fail "the web head colour is already #$NEW_HEX"

restore() {
  close_all_heads
  [ "$(effective webhead_color)" = "$COLOUR_BEFORE" ] || set_head_colour "$(hex_of "$COLOUR_BEFORE")" || true
  choose_mode "$(mode_title "$WEBHEADS_BEFORE" "$BUBBLES_BEFORE")" || true
  # Native bubbles show their warning, then the notification explainer.
  tap_text "OK" >/dev/null 2>&1 && sleep 2 || true
  tap_text "Not now" >/dev/null 2>&1 || true
  a shell input keyevent KEYCODE_HOME
  log "after: webhead_enabled_pref=$(stored webhead_enabled_pref) native_bubbles_preference=$(stored native_bubbles_preference) webhead_color=$(stored webhead_color) (reads $(effective webhead_color))"
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap restore EXIT

a logcat -c
a logcat -b crash -c

# heads -----------------------------------------------------------------------------------------
close_all_heads
a shell am force-stop "$PKG"
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
choose_mode "Web heads"
tap_text "Not now" || true
a shell input keyevent KEYCODE_HOME
sleep 1
open_two_links
overlay_windows > "$OUT/heads-windows.txt"
if wait_overlays 3; then pass heads; else step_fail heads "expected 3 overlay windows (two heads and the trash), found $(overlay_count)"; fi
shot heads

# colour ----------------------------------------------------------------------------------------
shot colour-before
BEFORE_PX="$(colour_pixels colour-before "$NEW_HEX")"
if set_head_colour "$NEW_HEX"; then
  shot colour-after
  AFTER_PX="$(colour_pixels colour-after "$NEW_HEX")"
  log "colour: #$NEW_HEX pixels inside the head windows: before $BEFORE_PX, after $AFTER_PX; stored webhead_color=$(stored webhead_color)"
  if [ "$BEFORE_PX" -lt 50 ] && [ "$AFTER_PX" -ge 400 ]; then pass colour; else step_fail colour "expected fewer than 50 pixels before and at least 400 after"; fi
else
  shot colour-after
  step_fail colour "the colour sheet could not be driven"
fi
app_logcat colour

# open ------------------------------------------------------------------------------------------
XY="$(head_centre)"
if [ -n "$XY" ]; then
  # shellcheck disable=SC2086
  a shell input tap $XY
  if wait_top "com.android.chrome/"; then
    sleep 3
    shot open
    log "top activity after the tap: $(top_activity)"
    pass open
  else
    shot open
    step_fail open "the top activity is $(top_activity), not a Chrome Custom Tab"
  fi
  a shell input keyevent KEYCODE_BACK
  sleep 2
  a shell input keyevent KEYCODE_HOME
  sleep 2
else
  step_fail open "no front head on screen"
fi

# share -----------------------------------------------------------------------------------------
if long_press_front_head; then
  sleep 2
  LABEL="$(site_buttons Share | head -1)"
  log "share: tapping \"$LABEL\""
  if [ -n "$LABEL" ] && tap_on "content-desc=\"$LABEL\"" &&
    { wait_top "ChooserActivity" || wait_top "com.android.intentresolver" || wait_top "android/com.android.internal.app"; }; then
    sleep 2
    shot share
    log "top activity after Share: $(top_activity)"
    pass share
  else
    shot share
    step_fail share "no chooser on top (top: $(top_activity))"
  fi
  a shell input keyevent KEYCODE_BACK
  sleep 2
  top_activity | grep -qF WebHeadContextActivity && { a shell input keyevent KEYCODE_BACK; sleep 2; }
else
  shot share
  step_fail share "a long press did not open the web head list (top: $(top_activity))"
fi

# remove-one ------------------------------------------------------------------------------------
if long_press_front_head; then
  sleep 2
  shot remove-one-list
  LABEL="$(site_buttons Close | head -1)"
  log "remove-one: $(overlay_count) overlay windows; tapping \"$LABEL\""
  if [ -n "$LABEL" ] && tap_on "content-desc=\"$LABEL\"" && wait_overlays 2; then
    sleep 2
    ROWS="$(site_buttons Close | grep -c . || true)"
    shot remove-one
    overlay_windows > "$OUT/remove-one-windows.txt"
    log "remove-one: rows left on the list: $ROWS; top: $(top_activity)"
    if [ "$ROWS" -eq 1 ] && top_activity | grep -qF WebHeadContextActivity; then pass remove-one; else step_fail remove-one "expected the list on top with one row, found $ROWS rows"; fi
  else
    shot remove-one
    step_fail remove-one "expected 2 overlay windows after closing one site, found $(overlay_count)"
  fi
else
  shot remove-one-list
  step_fail remove-one "a long press did not open the web head list (top: $(top_activity))"
fi

# close-all-sheet -------------------------------------------------------------------------------
top_activity | grep -qF WebHeadContextActivity || long_press_front_head || true
LABEL="$(site_buttons Close | tail -1)"
log "close-all-sheet: tapping \"$LABEL\""
if [ -n "$LABEL" ] && tap_on "content-desc=\"$LABEL\""; then
  if wait_overlays 0 && wait_not_top "WebHeadContextActivity"; then
    sleep 3
    shot close-all-sheet
    if service_running; then step_fail close-all-sheet "the web head service still runs"; else pass close-all-sheet; fi
  else
    shot close-all-sheet
    step_fail close-all-sheet "found $(overlay_count) overlay windows, top: $(top_activity)"
  fi
else
  shot close-all-sheet
  step_fail close-all-sheet "no Close button in the web head list"
fi
app_logcat sheet
a shell input keyevent KEYCODE_HOME
sleep 1

# close-all-notification ------------------------------------------------------------------------
open_two_links
if wait_overlays 3; then
  close_all_heads
  if wait_overlays 0; then
    sleep 2
    shot close-all-notification
    if service_running; then step_fail close-all-notification "the web head service still runs"; else pass close-all-notification; fi
  else
    shot close-all-notification
    step_fail close-all-notification "found $(overlay_count) overlay windows after Tap to close all"
  fi
else
  step_fail close-all-notification "expected 3 overlay windows after two links, found $(overlay_count)"
fi
app_logcat notification

# exit ------------------------------------------------------------------------------------------
trap - EXIT
restore
a logcat -d -b crash | tr -d '\r' > "$OUT/exit.crash.txt"
NOW_HEADS="$(stored webhead_enabled_pref)"; NOW_BUBBLES="$(stored native_bubbles_preference)"; NOW_COLOUR="$(effective webhead_color)"
if grep -qF "$PKG" "$OUT/exit.crash.txt"; then
  step_fail exit "the crash buffer holds an entry for $PKG"
elif [ "$NOW_HEADS" != "$WEBHEADS_BEFORE" ] || [ "$NOW_BUBBLES" != "$BUBBLES_BEFORE" ] || [ "$NOW_COLOUR" != "$COLOUR_BEFORE" ]; then
  step_fail exit "the browsing mode or the web head colour was not restored"
else
  pass exit
fi

[ "$FAILED" = 0 ] || { log "context events check: FAIL"; exit 1; }
log "context events check: PASS"
