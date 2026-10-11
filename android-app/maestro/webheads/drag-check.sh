#!/usr/bin/env bash
# Drag check for the web heads, recorded for the person who signs feel-checklist.md.
#
#   1. web heads mode, six links (the sixth queues the oldest)
#   2. drag the master to the middle and release: it snaps to the nearer edge
#   3. fling it across the screen: it travels and snaps to the far edge
#   4. drag it onto the trash: mid-drag screenshot, the trash locks; release closes the head
#   5. long press: the list sheet opens; back closes it
#   6. tap: the page opens
#
# A screen recording of steps 2 to 6 and a screenshot after each step land in the evidence folder,
# with feel-checklist.md copied next to them.
#
# Usage: maestro/webheads/drag-check.sh <serial> <evidence-dir>   (from android-app/)
set -euo pipefail

SERIAL="${1:?usage: drag-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: drag-check.sh <serial> <evidence-dir>}"
source "$(dirname "$0")/lib.sh"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=drag
CHECK_NAME="drag check"
: > "$OUT/drag.txt"
cp "$HERE/feel-checklist.md" "$OUT/feel-checklist.md"

read -r W H < <(screen_size)
log "screen ${W}x${H}"

master() {
  local xy
  xy="$(head_centre)"
  [ -n "$xy" ] || fail "no web head on screen"
  echo "$xy"
}

a shell am force-stop "$PKG"
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
choose_mode "Web heads"
tap_text "Not now" || true
a shell input keyevent HOME
sleep 1

# 1. Six heads.
a logcat -c
# grep without -q: with pipefail, an early exit would break the pipe and read as "not found".
routed() { a logcat -d | tr -d '\r' | grep -F "Open https://example.com/?head=$1:" >/dev/null; }
for i in 1 2 3 4 5 6; do
  open_link "https://example.com/?head=$i"
  # Wait for the router line before the next link; a busy emulator can take seconds per link.
  for _ in $(seq 1 15); do routed "$i" && break; sleep 1; done
  if ! routed "$i"; then
    log "link $i was not routed within 15 s; sending it again"
    open_link "https://example.com/?head=$i"
    for _ in $(seq 1 15); do routed "$i" && break; sleep 1; done
    routed "$i" || fail "link $i never reached the router"
  fi
  sleep 1
done
sleep 2
shot drag-1-six-heads
heads=$(( $(overlay_count) - 1 ))
log "head windows: $heads (one is queued and hidden)"
[ "$heads" -eq 6 ] || fail "expected six head windows, found $heads"

nav_three_button
trap 'nav_restore' EXIT
a shell rm -f /sdcard/webheads-drag.mp4
a shell screenrecord --time-limit 120 /sdcard/webheads-drag.mp4 &
RECORDER=$!
sleep 1

dismiss_anr
# 2. Drag to the middle and release.
read -r x y < <(master)
a shell input swipe "$x" "$y" $((W / 2)) $((H / 2)) 900
sleep 2
shot drag-2-release-mid
read -r x2 _ < <(master)
log "after release mid-screen: centre x=$x2 (screen width $W)"
[ "$x2" -lt $((W / 4)) ] || [ "$x2" -gt $((W * 3 / 4)) ] || fail "the head did not snap to an edge"

dismiss_anr
# 3. Fling across.
read -r x y < <(master)
if [ "$x" -lt $((W / 2)) ]; then to=$((W - 80)); else to=80; fi
a shell input swipe "$x" "$y" "$to" "$y" 120
sleep 2
shot drag-3-fling
read -r x3 _ < <(master)
log "after fling: centre x=$x3"

dismiss_anr
# 4. Onto the trash (centre under the screen middle, a sixth of the height above the bottom).
read -r x y < <(master)
before=$(overlay_count)
a shell input swipe "$x" "$y" $((W / 2)) $((H - H / 6)) 2500 &
sleep 2.2
shot drag-4-trash-locked
wait $! || true
sleep 2
after=$(overlay_count)
log "windows before and after the trash: $before -> $after"
[ "$after" -eq $((before - 1)) ] || fail "releasing on the trash did not close a head"

dismiss_anr
# 5. Long press: the list sheet.
read -r x y < <(master)
a shell input swipe "$x" "$y" "$x" "$y" 1200
sleep 2
shot drag-5-sheet
a shell dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | tee -a "$OUT/drag.txt" |
  grep -q WebHeadContextActivity || fail "long press did not open the list"
a shell input keyevent BACK
sleep 1

dismiss_anr
# 6. Tap: the page opens.
read -r x y < <(master)
a shell input tap "$x" "$y"
sleep 4
shot drag-6-tap-opens
a shell dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | tee -a "$OUT/drag.txt"

kill "$RECORDER" 2>/dev/null || true
sleep 2
a pull /sdcard/webheads-drag.mp4 "$OUT/drag.mp4" >/dev/null
app_logcat drag
[ ! -s "$OUT/drag.crash.txt" ] || fail "crash during the drag check (see drag.crash.txt)"
a shell input keyevent HOME
log "drag check done; the person signs feel-checklist.md after watching drag.mp4"
