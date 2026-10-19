#!/usr/bin/env bash
# Device check of the Custom Tab opening animation (Look and feel > Opening animation) on the debug
# build. With the system transition animation scale at 10, a link to a page served from this host
# opens in a Chrome Custom Tab with the animation off, Slide Right and Slide Up (speed Short).
# JPEG screenshots are taken on the device back to back for 6 s after the link opens, and the
# open is repeated once on a screen recording. frames.tsv gives, per frame, the horizontal and
# vertical offset at which the Custom Tab's toolbar band matches the settled tab (the incoming
# tab's position); summary.txt says per case whether the tab moved in over several frames.
# Restores the animation scale and the animation settings at the end.
#
# Usage: maestro/browsing/transition-check.sh <serial> <evidence-dir> <tag>   (run from android-app/)
# The debug build must be installed with Chrome as the Custom Tab provider (the default).
set -euo pipefail

USAGE="usage: transition-check.sh <serial> <evidence-dir> <tag>"
SERIAL="${1:?$USAGE}"
OUT_ROOT="${2:?$USAGE}"
TAG="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
TC_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
OUT="$OUT_ROOT/$TAG"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=transition
CHECK_NAME="transition check"
: > "$OUT/transition.txt"

PORT=""
SERVER_PID=""
SCALE_BEFORE=""
TYPE_BEFORE=""
SPEED_BEFORE=""

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/tc-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/tc-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/tc-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/tc-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

tap_on() {  # $1 = dump fragment
  local xy _
  for _ in $(seq 1 10); do
    xy="$({ ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } | head -1 |
      sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
      awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }')"
    if [ -n "$xy" ]; then
      # shellcheck disable=SC2086
      a shell input tap $xy
      return 0
    fi
    dismiss_anr
    sleep 2
  done
  log "nothing matching $1 on screen"
  return 1
}

scroll_to() {  # $1 = dump fragment
  local _ w h
  read -r w h < <(screen_size)
  for _ in $(seq 1 8); do
    ui_dump | grep -qF -- "$1" && return 0
    a shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 3)) 300
    sleep 1
  done
  ui_dump | grep -qF -- "$1"
}

stored() {  # $1 = settings key: the value the debug dump receiver reports
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}

type_label() { case "$1" in 0) echo None ;; 1) echo "Slide Right" ;; 2) echo "Slide Up" ;; *) echo "" ;; esac; }
speed_label() { case "$1" in 1) echo Medium ;; 2) echo Short ;; *) echo "" ;; esac; }

# Chooses $2 (a label) in the list setting row $1 (a key) on Look and feel.
choose() {
  a shell am force-stop com.android.chrome
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" LookAndFeel >/dev/null
  sleep 3
  scroll_to "resource-id=\"row-$1\"" || fail "no $1 row on Look and feel"
  tap_on "resource-id=\"row-$1\""
  sleep 1
  tap_on "text=\"$2\"" || fail "no \"$2\" choice for $1"
  sleep 1
  a shell input keyevent KEYCODE_HOME
}

restore() {
  [ -n "$SCALE_BEFORE" ] && a shell settings put global transition_animation_scale "$SCALE_BEFORE" >/dev/null 2>&1 || true
  if [ -n "$TYPE_BEFORE" ] && [ "$TYPE_BEFORE" != null ]; then
    choose animation_preference "$(type_label "$TYPE_BEFORE")" || true
    [ "$TYPE_BEFORE" != 0 ] && [ -n "$SPEED_BEFORE" ] && [ "$SPEED_BEFORE" != null ] && choose animation_speed_preference "$(speed_label "$SPEED_BEFORE")" || true
  fi
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap restore EXIT

PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
python -m http.server "$PORT" --bind 127.0.0.1 --directory "$TC_HERE/../reader/fixtures" > "$OUT/server.log" 2>&1 &
SERVER_PID=$!
a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
URL="http://localhost:$PORT/article.html"

SCALE_BEFORE="$(a shell settings get global transition_animation_scale | tr -d '\r')"
TYPE_BEFORE="$(stored animation_preference)"
SPEED_BEFORE="$(stored animation_speed_preference)"
log "before: transition_animation_scale=$SCALE_BEFORE animation_preference=$TYPE_BEFORE animation_speed_preference=$SPEED_BEFORE"
a shell settings put global transition_animation_scale 10
read -r W H < <(screen_size)
DISPLAY_ID="$(a shell dumpsys SurfaceFlinger --display-id 2>/dev/null | tr -d '
' | awk 'NR == 1 { print $2 }')"
cap() { if [ -n "$DISPLAY_ID" ]; then a exec-out screencap -p -d "$DISPLAY_ID"; else a exec-out screencap -p; fi; }
log "screen $W x $H; $(a shell getprop ro.build.version.sdk | tr -d '\r') API; url $URL"

# Warm-up: Chrome's first start draws late, so one open before the measured ones.
choose animation_preference None
open_link "$URL"
sleep 12
a shell input keyevent KEYCODE_BACK
sleep 2
a shell input keyevent KEYCODE_HOME
sleep 2

for case in off:0 right:1 up:2; do
  name="${case%%:*}"
  type="${case##*:}"
  choose animation_preference "$(type_label "$type")"
  [ "$type" = 0 ] || choose animation_speed_preference Short
  log "$name: stored animation_preference=$(stored animation_preference) animation_speed_preference=$(stored animation_speed_preference)"
  mkdir -p "$OUT/$name"
  a shell input keyevent KEYCODE_HOME
  sleep 3
  # Frames are taken on the device (JPEG, about 0.1 s each), named by the device clock in ms.
  a shell "rm -rf /data/local/tmp/tc && mkdir -p /data/local/tmp/tc"
  a shell "end=\$((\$(date +%s) + 7)); while [ \$(date +%s) -lt \$end ]; do screencap -d $DISPLAY_ID -j /data/local/tmp/tc/\$(date +%s%N | cut -c1-13).jpg; done" &
  CAP=$!
  sleep 1
  opened="$(a shell 'date +%s%N' | tr -d '\r' | cut -c1-13)"
  open_link "$URL"
  wait "$CAP" || true
  rm -rf "$OUT/$name/device"
  a pull /data/local/tmp/tc "$OUT/$name/device" >/dev/null
  a shell rm -rf /data/local/tmp/tc
  : > "$OUT/$name/times.tsv"
  n=0
  for f in $(ls "$OUT/$name/device" | sort -n); do
    t=$(( ${f%.jpg} - opened ))
    [ "$t" -ge 0 ] || continue
    n=$((n + 1))
    mv "$OUT/$name/device/$f" "$OUT/$name/frame-$(printf %03d $n).jpg"
    printf '%s\t%s\n' "$(printf %03d $n)" "$t" >> "$OUT/$name/times.tsv"
  done
  rm -rf "$OUT/$name/device"
  sleep 2
  cap > "$OUT/$name/final.png"
  log "$name: $n frames in 6 s after the link; top $(a shell dumpsys activity activities | tr -d '\r' | awk '/topResumedActivity/ { print $3; exit }')"
  a shell input keyevent KEYCODE_BACK
  sleep 3
  a shell input keyevent KEYCODE_HOME
  sleep 2
  # The same open once more on a screen recording, to watch.
  a shell rm -f /sdcard/transition.mp4
  a shell screenrecord --time-limit 8 /sdcard/transition.mp4 &
  REC=$!
  sleep 1
  open_link "$URL"
  wait "$REC" || true
  a pull /sdcard/transition.mp4 "$OUT/$name/transition.mp4" >/dev/null 2>&1 || log "$name: no recording"
  a shell rm -f /sdcard/transition.mp4
  a shell input keyevent KEYCODE_BACK
  sleep 3
  a shell input keyevent KEYCODE_HOME
  sleep 2
done

# The incoming tab's offset per frame: the settled tab's toolbar band (below the status bar) is
# matched against each frame shifted right (dx, at most two thirds of the width, so that a third
# of the band always overlaps) or down (dy); the best shift is the tab's position. The default
# system transition also moves windows sideways a little, so compare the cases with each other.
python - "$OUT" <<'PY'
import os, sys
import numpy as np
from PIL import Image

out = sys.argv[1]
rows = ["case\tframe\tt_ms\tdx\tdy\tbest_diff\tdiff_unshifted"]
summary = []
for case in ("off", "right", "up"):
    d = os.path.join(out, case)
    fin = np.asarray(Image.open(os.path.join(d, "final.png")).convert("L"), dtype=np.int16)[::4, ::4]
    h, w = fin.shape
    y0, y1 = int(h * 0.06), int(h * 0.12)
    band = fin[y0:y1]
    times = dict(l.strip().split("\t") for l in open(os.path.join(d, "times.tsv")) if l.strip())
    moving = 0
    largest = (0, 0)
    for f in sorted(times):
        img = np.asarray(Image.open(os.path.join(d, f"frame-{f}.jpg")).convert("L"), dtype=np.int16)[::4, ::4]
        best = (1e9, 0, 0)
        for dx in range(0, w * 2 // 3 + 1):
            diff = np.abs(img[y0:y1, dx:] - band[:, : w - dx]).mean()
            if diff < best[0]:
                best = (diff, dx, 0)
        for dy in range(1, h - y1):
            diff = np.abs(img[y0 + dy : y1 + dy] - band).mean()
            if diff < best[0]:
                best = (diff, 0, dy)
        unshifted = np.abs(img[y0:y1] - band).mean()
        dx, dy = best[1] * 4, best[2] * 4
        if best[0] < 8 and (dx > 0 or dy > 0):
            moving += 1
            largest = max(largest, (dx, dy), key=lambda o: max(o))
        rows.append(f"{case}\t{f}\t{times[f]}\t{dx}\t{dy}\t{best[0]:.1f}\t{unshifted:.1f}")
    summary.append(f"{case}: {moving} of {len(times)} frames with the tab's toolbar shifted (best match diff < 8), largest shift dx {largest[0]} dy {largest[1]}")
open(os.path.join(out, "frames.tsv"), "w").write("\n".join(rows) + "\n")
open(os.path.join(out, "summary.txt"), "w").write("\n".join(summary) + "\n")
print("\n".join(summary))
PY
log "$(cat "$OUT/summary.txt")"
a logcat -d -b crash | tr -d '\r' > "$OUT/crash.txt"
if grep -q "$PKG" "$OUT/crash.txt"; then fail "the crash buffer holds an entry for $PKG"; fi
log "$CHECK_NAME finished ($TAG)"
