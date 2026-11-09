#!/usr/bin/env bash
# The motion of the screens a link opens, on a device, one mode per run:
#
#   bash maestro/shell/link-slide-check.sh <serial> <dir> <slide|off|double-tap>
#
#   slide       At a tenfold animation scale: a refused link ('https://') handed to the launcher
#               shows the link error screen sliding up over home with no fade; Back slides it down;
#               a held edge swipe drags it down with the finger; a valid fixture link leaves home
#               still until the Custom Tab covers it (its own opening motion is the Look and feel
#               setting); the reader rises from the bottom and Back moves it down.
#   off         The three animation scales at 0: the first frames after each start already show the
#               settled screen, with no slide.
#   double-tap  At a tenfold animator scale: two taps (History, then the Tabs card button) inside
#               one transition and the double-tap Maestro flow; after settling, one Back shows home.
#
# Evidence in <dir>: <mode>.txt (PASS/FAIL lines), <mode>.mp4 (a screen recording),
# frames/<mode>-*.jpg (frames taken on the device), <mode>-*.png (settled stills),
# <mode>.logcat.txt (the app's errors). Needs the debug build installed, python, and Maestro for
# double-tap. The fixture pages of maestro/reader/fixtures are served from this host.
#
# Device tools: the Android CLI takes the settled stills (android screen capture) and reads the
# layout (android layout). adb is used where the Android CLI has no equivalent:
#   - am start with a data URI and launch flags (android run takes neither);
#   - adb reverse for the fixture server;
#   - settings put of the three animation scales (the CLI has no settings command);
#   - input tap twice inside one transition and input keyevent / swipe for Back and the edge
#     gesture (android input tap waits for a selector, too slow for two taps in one transition);
#   - the on-device screencap frame loop and screenrecord (the CLI takes 2-7 s per capture and has
#     no recorder);
#   - logcat for the app's errors.
# Every scale it changes, the reverse port and the device frame folder are restored on exit,
# failures included.

set -u
SERIAL="${1:?serial}"
OUT="${2:?output dir}"
MODE="${3:?slide|off|double-tap}"

source "$(dirname "$0")/../webheads/lib.sh"
LS_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
FIXTURES="$LS_HERE/../reader/fixtures"
mkdir -p "$OUT/frames"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$MODE"
CHECK_NAME="link slide $MODE"
: > "$OUT/$MODE.txt"
APP_ID="$PKG"
READER=arun.com.chromer.browsing.article.ArticleActivity
DEVICE_FRAMES=/data/local/tmp/ls

# ---------------------------------------------------------------------------------------------
# Restore on exit

SCALES="window_animation_scale transition_animation_scale animator_duration_scale"
declare -A SAVED_SCALE
for s in $SCALES; do SAVED_SCALE[$s]="$(a shell settings get global "$s" | tr -d '\r')"; done
PORT=""
SERVER_PID=""
cleanup() {
  for s in $SCALES; do
    v="${SAVED_SCALE[$s]}"
    if [ -z "$v" ] || [ "$v" = "null" ]; then a shell settings delete global "$s" >/dev/null 2>&1; else a shell settings put global "$s" "$v"; fi
  done
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
  a shell rm -rf "$DEVICE_FRAMES" >/dev/null 2>&1 || true
}
trap cleanup EXIT

scales() { for s in $SCALES; do a shell settings put global "$s" "$1"; done; }

start_server() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$FIXTURES" > "$OUT/server-$MODE.log" 2>&1 &
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
# Evidence helpers

still() { android screen capture --device "$SERIAL" --output "$OUT/$MODE-$1.png" >/dev/null 2>&1 || shot "$MODE-$1"; }

# Frames on the device for $2 seconds into frames/<mode>-<tag>-<ms>.jpg; runs in the background.
frames_start() {
  a shell "rm -rf $DEVICE_FRAMES/$1 && mkdir -p $DEVICE_FRAMES/$1"
  a shell "end=\$((\$(date +%s) + $2)); while [ \$(date +%s) -lt \$end ]; do screencap -j $DEVICE_FRAMES/$1/\$(date +%s%N | cut -c1-13).jpg; done" &
  FRAMES_PID=$!
}
frames_end() {
  wait "$FRAMES_PID" 2>/dev/null || true
  rm -rf "$OUT/frames/tmp-$1"
  a pull "$DEVICE_FRAMES/$1" "$OUT/frames/tmp-$1" >/dev/null 2>&1 || true
  local n=0 f
  for f in $(ls "$OUT/frames/tmp-$1" 2>/dev/null | sort -n); do
    n=$((n + 1))
    mv "$OUT/frames/tmp-$1/$f" "$OUT/frames/$MODE-$1-$(printf %03d $n).jpg"
  done
  rm -rf "$OUT/frames/tmp-$1"
  log "$1: $n frames"
}

record_start() {
  a shell rm -f /sdcard/link-slide.mp4
  a shell screenrecord --time-limit "${1:-60}" /sdcard/link-slide.mp4 &
  RECORD_PID=$!
  sleep 1
}
record_end() {
  a shell pkill -INT screenrecord >/dev/null 2>&1 || true
  wait "$RECORD_PID" 2>/dev/null || true
  sleep 1
  a pull /sdcard/link-slide.mp4 "$OUT/$MODE.mp4" >/dev/null 2>&1 || log "no recording"
  a shell rm -f /sdcard/link-slide.mp4
}

has_id() {
  a shell uiautomator dump /sdcard/ls-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/ls-ui.xml | tr -d '\r' | grep -q "resource-id=\"$1\""
}

# Centre of the node with resource id $1 from the Android CLI layout, as "X Y".
layout_centre() {
  android layout --device "$SERIAL" --full --output "$OUT/$MODE-layout.json" >/dev/null 2>&1 || return 1
  python - "$OUT/$MODE-layout.json" "$1" "${2:-}" <<'PY'
import json, re, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))
want, text = sys.argv[2], sys.argv[3]
def walk(node, inside):
    if isinstance(node, dict):
        ident = str(node.get("resourceId") or node.get("resource-id") or node.get("id") or "")
        here = inside or ident.endswith(want)
        label = str(node.get("text") or "")
        if here and (not text or label == text):
            b = node.get("bounds")
            if isinstance(b, str):
                n = [int(v) for v in re.findall(r"-?\d+", b)]
            elif isinstance(b, dict):
                n = [b.get(k) for k in ("left", "top", "right", "bottom")]
            elif isinstance(b, list):
                n = b
            else:
                n = None
            if n and len(n) == 4 and None not in n:
                print((n[0] + n[2]) // 2, (n[1] + n[3]) // 2)
                sys.exit(0)
        for v in node.values():
            walk(v, here)
    elif isinstance(node, list):
        for v in node:
            walk(v, inside)
walk(data, False)
sys.exit(1)
PY
}

start_link() { a shell "am start -W -f 0x24000000 -a android.intent.action.VIEW -d '$1' -n $PKG/$MAIN" >/dev/null; }

home() {
  a shell am force-stop "$PKG"
  a shell "am start -W -n $PKG/$MAIN" >/dev/null
  sleep 3
  tap_text "Skip" >/dev/null 2>&1 && sleep 2 || true
  tap_text "OK" >/dev/null 2>&1 && sleep 1 || true
}

crash_check() {
  a logcat -d -b crash > "$OUT/$MODE-crash.txt" 2>/dev/null || true
  a logcat -d '*:E' | tr -d '\r' | grep -F "$PKG" > "$OUT/$MODE.logcat.txt" 2>/dev/null || true
  if grep -q "$PKG" "$OUT/$MODE-crash.txt"; then fail "the app crashed (see $MODE-crash.txt)"; fi
}

# ---------------------------------------------------------------------------------------------
# Modes

slide() {
  start_server
  home
  a logcat -c
  scales 10
  record_start 170
  frames_start error-open 12
  sleep 1
  start_link 'https://'
  frames_end error-open
  has_id link-error && log "PASS the link error screen shows" || fail "no link error screen"
  still error-settled
  frames_start error-back 10
  sleep 1
  a shell input keyevent 4
  frames_end error-back
  has_id link-error && fail "Back left the link error screen" || log "PASS Back closed the link error screen"
  start_link 'https://'
  sleep 12
  frames_start error-gesture 8
  sleep 1
  # A held edge swipe from the left edge; the screen moves down with it.
  a shell input swipe 2 1200 500 1200 1500
  frames_end error-gesture
  still after-gesture
  frames_start valid-link 14
  sleep 1
  start_link "http://127.0.0.1:$PORT/article.html"
  frames_end valid-link
  still valid-link-settled
  a shell input keyevent 4
  sleep 12
  frames_start reader-open 12
  sleep 1
  a shell "am start -W -n $PKG/$READER -d http://127.0.0.1:$PORT/article.html" >/dev/null
  frames_end reader-open
  still reader-settled
  frames_start reader-close 10
  sleep 1
  a shell input keyevent 4
  frames_end reader-close
  record_end
  crash_check
  log "slide done: compare the frames with the approved frames of the link transition"
}

off() {
  start_server
  home
  a logcat -c
  scales 0
  record_start 60
  start_link 'https://'
  a shell "screencap -j $DEVICE_FRAMES-off-1.jpg; sleep 0.05; screencap -j $DEVICE_FRAMES-off-2.jpg" 2>/dev/null || true
  a pull "$DEVICE_FRAMES-off-1.jpg" "$OUT/frames/off-error-1.jpg" >/dev/null 2>&1 || true
  a pull "$DEVICE_FRAMES-off-2.jpg" "$OUT/frames/off-error-2.jpg" >/dev/null 2>&1 || true
  has_id link-error && log "PASS the link error screen shows with animations off" || fail "no link error screen"
  still error
  a shell input keyevent 4
  sleep 2
  a shell "am start -W -n $PKG/$READER -d http://127.0.0.1:$PORT/article.html" >/dev/null
  a shell "screencap -j $DEVICE_FRAMES-off-3.jpg; sleep 0.05; screencap -j $DEVICE_FRAMES-off-4.jpg" 2>/dev/null || true
  a pull "$DEVICE_FRAMES-off-3.jpg" "$OUT/frames/off-reader-1.jpg" >/dev/null 2>&1 || true
  a pull "$DEVICE_FRAMES-off-4.jpg" "$OUT/frames/off-reader-2.jpg" >/dev/null 2>&1 || true
  still reader
  a shell rm -f "$DEVICE_FRAMES-off-"*.jpg
  a shell input keyevent 4
  sleep 2
  start_link "http://127.0.0.1:$PORT/article.html"
  a shell "screencap -j $DEVICE_FRAMES-off-5.jpg" 2>/dev/null || true
  a pull "$DEVICE_FRAMES-off-5.jpg" "$OUT/frames/off-valid-link-1.jpg" >/dev/null 2>&1 || true
  sleep 3
  still valid-link
  record_end
  crash_check
  log "off done: the first frame after each start shows the settled screen"
}

double_tap() {
  start_server
  # A page opened once puts a recent on home, which shows the History button.
  open_link "http://127.0.0.1:$PORT/article.html"
  sleep 6
  home
  a logcat -c
  a shell settings put global animator_duration_scale 10
  local history tabs
  history="$(layout_centre home-history)" || fail "no History button in the layout"
  tabs="$(layout_centre home-tabs-card Tabs || true)"
  log "History at $history, Tabs button at ${tabs:-none}"
  record_start 40
  frames_start double-tap 6
  # shellcheck disable=SC2086
  if [ -n "$tabs" ]; then
    a shell "input tap $history; sleep 0.3; input tap $tabs"
  else
    a shell "input tap $history; sleep 0.3; input tap $history"
  fi
  frames_end double-tap
  sleep 8
  a shell input keyevent 4
  sleep 8
  has_id home-history && log "PASS one Back after the two taps shows home" || fail "one Back did not reach home"
  still after-back
  maestro --device "$SERIAL" test -e APP_ID="$APP_ID" "$LS_HERE/double-tap.yaml" > "$OUT/double-tap.maestro.txt" 2>&1 \
    && log "PASS the double-tap flow" || fail "the double-tap flow failed (see double-tap.maestro.txt)"
  record_end
  crash_check
}

case "$MODE" in
  slide) slide ;;
  off) off ;;
  double-tap) double_tap ;;
  *) fail "unknown mode $MODE" ;;
esac
