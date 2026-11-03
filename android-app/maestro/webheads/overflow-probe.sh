#!/usr/bin/env bash
# Measures what Android does with native bubbles past the stack limit, on an emulator. One mode per
# run; each mode starts by clearing the app's notifications and uses a run id in its links, so a
# stale bubble cannot read as a result.
#
#   overflow-probe.sh <serial> <dir> dismiss   A drag to the dismiss target sends the delete intent
#                                              (the control); a bubble that ages out of the stack does
#                                              not. Writes dismiss.txt.
#   overflow-probe.sh <serial> <dir> return    Opens 8 links, then posts the newest overflow bubble
#                                              again in each of three silent ways. Writes return.tsv.
#   overflow-probe.sh <serial> <dir> limits    Opens links until the overflow drops a bubble. Writes
#                                              limits.tsv.
#
# Needs adb on PATH, Pillow, and the debug build (`assembleDebug`, or APK=<path>); each mode
# reinstalls it. The serial must start with emulator-, so this can never drive an attached phone.
set -uo pipefail

SERIAL="${1:-}"
OUT="${2:-}"
MODE="${3:-}"
case "$SERIAL" in
  emulator-*) ;;
  *) echo "overflow-probe: \"$SERIAL\" is not an emulator serial" >&2; exit 2 ;;
esac
case "$MODE" in
  dismiss | return | limits) ;;
  *) echo "usage: overflow-probe.sh <serial> <dir> <dismiss|return|limits>" >&2; exit 2 ;;
esac
[ -n "$OUT" ] || { echo "overflow-probe: no output folder" >&2; exit 2; }

CHECK_NAME="overflow-probe $MODE"
LOG_NAME="$MODE"
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
mkdir -p "$OUT"
OUT="$(hostpath "$OUT")"

PROBE="$PKG/arun.com.chromer.debug.BubbleProbeReceiver"
SYSTEMUI=com.android.systemui/.SystemUIService
SDK="$(a shell getprop ro.build.version.sdk | tr -d '\r')"
RUN="$(date +%s)"
APK="${APK:-$(dirname "${BASH_SOURCE[0]}")/../../lynket/build/outputs/apk/debug/lynket-debug.apk}"
LOGCAT_PID=""
STACK_IDS=()
OVERFLOW_IDS=()
OVERFLOW_COUNT=""
OPENED=""
declare -A URL_OF

finish() {
  [ -z "$LOGCAT_PID" ] || kill "$LOGCAT_PID" 2>/dev/null || true
}
trap finish EXIT

# The id the app posts the notification of $1 as: String.hashCode of an ASCII link.
notification_id() {
  local s="$1" h=0 c i
  for ((i = 0; i < ${#s}; i++)); do
    printf -v c '%d' "'${s:i:1}"
    h=$(((31 * h + c) & 0xFFFFFFFF))
  done
  ((h >= 0x80000000)) && h=$((h - 0x100000000))
  echo "$h"
}

# Saves the shell's bubble dump as $OUT/$1 and reads the stack and the overflow. Android 12 and
# later name the overflow; Android 11 lists only the stack, so OVERFLOW_COUNT stays empty there.
shell_bubbles() {
  if [ "$SDK" -ge 31 ]; then
    a shell dumpsys activity service "$SYSTEMUI" WMShell | tr -d '\r' > "$OUT/$1"
  else
    a shell dumpsys activity service "$SYSTEMUI" Bubbles | tr -d '\r' > "$OUT/$1"
  fi
  mapfile -t STACK_IDS < <(bubble_keys stack "$OUT/$1")
  mapfile -t OVERFLOW_IDS < <(bubble_keys overflow "$OUT/$1")
  OVERFLOW_COUNT="$(awk '/overflow bubble count:/ { print $NF; exit }' "$OUT/$1")"
}

# The notification ids of the "key:" lines of the shell's dump, in the section $1.
bubble_keys() {
  awk -F'|' -v want="$1" '
    /BubbleController state:|BubbleData state:/ { part = "stack" }
    /overflow bubble count:/ { part = "overflow" }
    /summaryKeys:|Stack view state:|BubbleStackView state:/ { part = "" }
    part == want && /^ *key: / { print $3 }' "$2"
}

in_list() {
  local wanted="$1" item
  shift
  for item in "$@"; do [ "$item" = "$wanted" ] && return 0; done
  return 1
}

# Saves what the app reads of its own bubble notifications as $OUT/$1; its last line is the count.
read_state() {
  local lines
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  a shell am broadcast -n "$PROBE" --es action state >/dev/null
  for _ in $(seq 1 30); do
    if tail -n +"$((lines + 1))" "$OUT/$MODE.logcat.txt" | grep -q "state done"; then break; fi
    sleep 0.3
  done
  tail -n +"$((lines + 1))" "$OUT/$MODE.logcat.txt" | grep "BubbleProbe" > "$OUT/$1"
  grep -q "state done" "$OUT/$1" || fail "the debug build answered no state (is it installed?)"
}

state_count() { sed -n 's/.*state done count=//p' "$OUT/$1"; }

# true when the state file $1 shows the id $2 with the bubble flag, false without it, gone when the
# app has no such notification.
bubble_flag() {
  local value
  value="$(sed -n "s/.*state id=$2 bubble=\([a-z]*\).*/\1/p" "$OUT/$1")"
  echo "${value:-gone}"
}

# Opens $1 and waits until its bubble shows; leaves its id in OPENED.
open_bubble() {
  local id
  id="$(notification_id "$1")"
  URL_OF[$id]="$1"
  OPENED="$id"
  open_link "$1"
  for _ in $(seq 1 15); do
    sleep 1
    read_state state-open.txt
    [ "$(bubble_flag state-open.txt "$id")" = true ] && return 0
  done
  fail "$1 did not show as a bubble (see state-open.txt)"
}

prepare() {
  # Only an uninstall empties Android's overflow: it keeps a bubble after its notification is
  # cancelled, after a force stop and after the app's data is cleared.
  a uninstall "$PKG" >/dev/null 2>&1 || true
  a install "$(hostpath "$APK")" >/dev/null || fail "could not install $APK"
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  a shell cmd notification set_bubbles "$PKG" 1 || fail "could not allow bubbles for the app"
  a shell am force-stop "$PKG"
  choose_mode "Native Bubbles (Beta)"
  # The guide dialog of the mode.
  a shell input keyevent BACK
  sleep 1
  tap_text "Not now" || true
  a shell input keyevent HOME
  sleep 1
  a logcat -c
  a logcat -v brief BubbleProbe:V BubbleDismissReceiver:V '*:S' > "$OUT/$MODE.logcat.txt" 2>/dev/null &
  LOGCAT_PID=$!
  a shell am broadcast -n "$PROBE" --es action clear >/dev/null
  sleep 2
}

# Saves the shade with the bubbles behind it as $1.png and $1.xml.
shade_capture() {
  a shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
  sleep 2
  shot "$1"
  a shell uiautomator dump /sdcard/overflow-probe-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/overflow-probe-ui.xml | tr -d '\r' > "$OUT/$1.xml"
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
}

# Where the front bubble of the collapsed stack rests, from the shell's dump $1, as "X Y".
stack_point() {
  local x y
  read -r x y < <(awk '/restingStackPos:/ { gsub(/[^0-9.-]+/, " "); print int($1), int($2); exit }' "$OUT/$1")
  [ -n "${x:-}" ] || fail "no stack position in $1"
  echo "$((x < 0 ? 70 : x + 90)) $((y + 80))"
}

# Opens the stack and its overflow panel for a screenshot: the one view of the overflow on Android 11.
# The overflow button ends the row of 5 bubbles at the top of the screen. The system introduction of
# the first bubble takes the first tap.
overflow_panel_capture() {
  local x y w h
  read -r x y < <(stack_point shell-start.txt)
  read -r w h < <(screen_size)
  for _ in 1 2 3; do
    a shell input tap "$x" "$y"
    sleep 3
    shell_bubbles shell-expanded.txt
    grep -q 'expanded: true' "$OUT/shell-expanded.txt" && break
  done
  a shell input tap "$((w * 11 / 12))" "$((h * 23 / 300))"
  sleep 3
  shot return-overflow-panel
  a shell input keyevent BACK
  sleep 2
  a shell input keyevent HOME
  sleep 2
}

# A drag of the collapsed stack to the dismiss target, held there, as a person does it.
drag_stack_to_dismiss() {
  local x y w h target_x target_y step
  read -r x y < <(stack_point "$1")
  read -r w h < <(screen_size)
  target_x=$((w / 2))
  target_y=$((h - 240))
  a shell input motionevent DOWN "$x" "$y"
  for step in 1 2 3 4 5 6; do
    a shell input motionevent MOVE "$((x + (target_x - x) * step / 6))" "$((y + (target_y - y) * step / 6))"
  done
  sleep 1
  a shell input motionevent MOVE "$target_x" "$target_y"
  sleep 1
  a shell input motionevent UP "$target_x" "$target_y"
  sleep 3
}

dismiss_lines() { grep -c "Bubble dismissed: $1\$" "$OUT/$MODE.logcat.txt" || true; }

mode_dismiss() {
  local id aged controls=0 attempt aged_lines
  open_bubble "https://example.com/?probe=$RUN-dismiss"
  id="$OPENED"
  sleep 2
  for attempt in 1 2 3; do
    shell_bubbles "shell-dismiss-before-$attempt.txt"
    shot "dismiss-before-$attempt"
    drag_stack_to_dismiss "shell-dismiss-before-$attempt.txt"
    shot "dismiss-after-$attempt"
    controls="$(dismiss_lines "$id")"
    [ "$controls" -gt 0 ] && break
    # The system introduction of the first bubble takes the first touch.
    a shell input keyevent BACK
    sleep 2
  done
  log "sdk=$SDK control id=$id drags=$attempt dismiss_lines=$controls"
  [ "$controls" -eq 1 ] || fail "the drag did not send one delete intent"

  local ids=() n
  for n in 1 2 3 4 5 6; do
    open_bubble "https://example.com/?probe=$RUN-age-$n"
    ids+=("$OPENED")
  done
  aged="${ids[0]}"
  sleep 10
  aged_lines="$(dismiss_lines "$aged")"
  shell_bubbles shell-dismiss-aged.txt
  read_state state-dismiss-aged.txt
  shade_capture dismiss-aged-shade
  in_list "$aged" "${STACK_IDS[@]}" && fail "the oldest bubble is still in the stack"
  log "sdk=$SDK aged id=$aged dismiss_lines=$aged_lines app_bubble_flag=$(bubble_flag state-dismiss-aged.txt "$aged") stack=${#STACK_IDS[@]}"
  log "dismiss done"
}

prepare
"mode_$MODE"
