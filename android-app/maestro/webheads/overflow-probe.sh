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
#   overflow-probe.sh <serial> <dir> registry  Opens 24 links and, at 7, 8 and past the overflow
#                                              limit, compares the app's bubble list with the shell's
#                                              stack and overflow. Writes registry.tsv.
#   overflow-probe.sh <serial> <dir> close     Opens 7 links and closes 3 bubbles one at a time; each
#                                              close must return the newest overflow bubble, with no
#                                              alert, until the overflow is empty. Writes close.tsv.
#   overflow-probe.sh <serial> <dir> close-stack
#                                              Opens 7 links and drags the collapsed stack away; each
#                                              of its 5 closes may return one bubble, so 2 come back,
#                                              with no alert and no expansion. Writes close-stack.tsv.
#   overflow-probe.sh <serial> <dir> close-icon
#                                              Opens a link whose icon is held back (slow-icon-server.py),
#                                              lets it age out and returns it with a close; it must show
#                                              the colour disc, then the icon in place. Writes
#                                              close-icon.tsv.
#   overflow-probe.sh <serial> <dir> count     Opens 7 links and opens the third bubble, which reads
#                                              3 of 7; opens an eighth link and a bubble of the stack,
#                                              which reads its place of 8; closes another bubble with
#                                              that page on screen, and the count changes in place.
#                                              Writes count.tsv.
#   overflow-probe.sh <serial> <dir> count-long
#                                              At the largest font (restored on exit), opens 12 links
#                                              with long addresses and the newest bubble, which reads
#                                              12 of 12 with the address cut short. Writes
#                                              count-long.tsv.
#   overflow-probe.sh <serial> <dir> shade     Opens 1 link, then 7, the last with a long address, then
#                                              a page; the shade shows no Lynket row, and no text of the
#                                              app's notifications and shortcuts holds the count. Writes
#                                              shade.tsv.
#   overflow-probe.sh <serial> <dir> story     The whole behaviour on one set of bubbles: opens 8 links and
#                                              reads the count in a page, closes a bubble in the stack with
#                                              that page on screen, closes the page's own bubble from its
#                                              Manage menu, closes two more, and checks that each close
#                                              returns one bubble with no alert and that a closed bubble
#                                              stays gone. Writes story.tsv, close.tsv and shade.tsv.
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
  dismiss | return | limits | registry | close | close-stack | close-icon | count | count-long | shade | story) ;;
  *) echo "usage: overflow-probe.sh <serial> <dir> <dismiss|return|limits|registry|close|close-stack|close-icon|count|count-long|shade|story>" >&2; exit 2 ;;
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
ICON_PORT=8799
ICON_SERVER_PID=""
FONT_SCALE_BEFORE=""
STACK_IDS=()
OVERFLOW_IDS=()
OVERFLOW_COUNT=""
OPENED=""
declare -A URL_OF

finish() {
  [ -z "$LOGCAT_PID" ] || kill "$LOGCAT_PID" 2>/dev/null || true
  [ -z "$ICON_SERVER_PID" ] || { kill "$ICON_SERVER_PID" 2>/dev/null; a reverse --remove "tcp:$ICON_PORT"; } || true
  case "$FONT_SCALE_BEFORE" in
    "") ;;
    null) a shell settings delete system font_scale >/dev/null || true ;;
    *) a shell settings put system font_scale "$FONT_SCALE_BEFORE" || true ;;
  esac
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
  # A force stop leaves Android's stack and overflow in place, and an uninstall empties them, so
  # bubbles of an earlier run cannot stay.
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
  # adb itself, so the kill in finish reaches it: a subshell would leave it holding the log file.
  adb -s "$SERIAL" logcat -v brief BubbleProbe:V BubbleDismissReceiver:V BubbleReconciler:V NativeFloatingBubble:V '*:S' > "$OUT/$MODE.logcat.txt" 2>/dev/null &
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
  local w h
  expand_stack shell-start.txt
  read -r w h < <(screen_size)
  a shell input tap "$((w * 11 / 12))" "$((h * 23 / 300))"
  sleep 3
  shot return-overflow-panel
  a shell input keyevent BACK
  sleep 2
  a shell input keyevent HOME
  sleep 2
}

# Opens the stack from the shell's dump $1. The system introduction of the first bubble takes the
# first tap.
expand_stack() {
  local x y
  read -r x y < <(stack_point "$1")
  for _ in 1 2 3; do
    a shell input tap "$x" "$y"
    sleep 3
    shell_bubbles shell-expanded.txt
    grep -q 'expanded: true' "$OUT/shell-expanded.txt" && return 0
  done
  fail "the stack did not open"
}

# A drag from $1,$2 to the dismiss target, held there, as a person does it.
drag_to_dismiss() {
  local x="$1" y="$2" w h target_x target_y step
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

drag_stack_to_dismiss() {
  local x y
  read -r x y < <(stack_point "$1")
  drag_to_dismiss "$x" "$y"
}

# Opens the stack from the shell's dump $1 and drags the bubble at the left of the row away. Which
# bubble that is does not matter: the close reads its id from the log.
drag_bubble_to_dismiss() {
  local w h
  expand_stack "$1"
  read -r w h < <(screen_size)
  drag_to_dismiss "$((w / 12))" "$((h * 23 / 300))"
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

# Moves the ids that left the stack since the last read to the end of ORDER, the newest overflow
# bubble last.
update_order() {
  local id kept=() left=()
  for id in "${ORDER[@]}"; do
    if in_list "$id" "${PREVIOUS_STACK[@]}" && ! in_list "$id" "${STACK_IDS[@]}"; then left+=("$id"); else kept+=("$id"); fi
  done
  ORDER=("${kept[@]}" "${left[@]}")
  PREVIOUS_STACK=("${STACK_IDS[@]}")
}

# Whether any shot differs from the shot before the post in the band where a heads-up row draws,
# between the bubbles at the screen edges and clear of the status bar.
heads_up_in_shots() {
  python - "$OUT" "$1" <<'PY'
import sys
from PIL import Image, ImageChops
out, variant = sys.argv[1:3]
before = Image.open(f"{out}/return-{variant}-before.png").convert("RGB")
w, h = before.size
box = (int(w * 0.18), int(h * 0.04), int(w * 0.82), int(h * 0.2))
base = before.crop(box)
changed = 0.0
for n in range(1, 5):
    shot = Image.open(f"{out}/return-{variant}-shot-{n}.png").convert("RGB").crop(box)
    data = ImageChops.difference(base, shot).convert("L").point(lambda v: 255 if v > 24 else 0).histogram()
    changed = max(changed, data[255] / (base.size[0] * base.size[1]))
print("true" if changed > 0.01 else "false")
PY
}

mode_return() {
  local n id variant url found keys flyout interruptive
  ORDER=()
  for n in 1 2 3 4 5 6 7 8; do
    open_bubble "https://example.com/?probe=$RUN-$n"
    ORDER+=("$OPENED")
  done
  sleep 2
  shell_bubbles shell-start.txt
  read_state state-start.txt
  PREVIOUS_STACK=("${ORDER[@]}")
  update_order
  [ "${#STACK_IDS[@]}" -lt 8 ] || fail "all 8 links are in the stack, so the overflow is empty"
  shade_capture return-shade
  overflow_panel_capture
  printf 'sdk\tvariant\turl\tin_stack_before\tin_stack_after\tapp_bubble_flag_after\tnotification_found\tsound_key\tflyout\theads_up_in_shots\tinterruptive\n' > "$OUT/return.tsv"
  for variant in same text fresh; do
    shell_bubbles "shell-$variant-before.txt"
    read_state "state-$variant-before.txt"
    update_order
    id=""
    for n in "${ORDER[@]}"; do in_list "$n" "${STACK_IDS[@]}" || id="$n"; done
    [ -n "$id" ] || fail "no overflow bubble left for $variant"
    url="${URL_OF[$id]}"
    local before=false
    in_list "$id" "${STACK_IDS[@]}" && before=true
    shot "return-$variant-before"
    a shell "am broadcast -n $PROBE --es action repost --es url '$url' --es variant $variant" >/dev/null
    for n in 1 2 3 4; do shot "return-$variant-shot-$n"; sleep 0.2; done
    a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notification-$variant.txt"
    shell_bubbles "shell-$variant-after.txt"
    read_state "state-$variant-after.txt"
    shot "return-$variant-after"
    found="$(sed -n "s/.*repost id=$id variant=$variant found=//p" "$OUT/$MODE.logcat.txt" | tail -1)"
    keys="$(grep -E 'mSoundNotificationKey|mVibrateNotificationKey' "$OUT/notification-$variant.txt" | sed 's/^ *//' | grep -v '=null$' | tr '\n' ' ')"
    flyout="$(awk -v key="key: 0|$PKG|$id|" 'index($0, key) { on = 1 } on && /showFlyout:/ { print $2; exit }' "$OUT/shell-$variant-after.txt")"
    interruptive="$(awk -v key="key=0|$PKG|$id|" 'index($0, key) { on = 1 } on && /mIsInterruptive=/ { sub(/.*=/, ""); print; exit }' "$OUT/notification-$variant.txt")"
    local after=false
    in_list "$id" "${STACK_IDS[@]}" && after=true
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "$variant" "$url" "$before" "$after" \
      "$(bubble_flag "state-$variant-after.txt" "$id")" "${found:-false}" "${keys:-none}" "${flyout:-none}" \
      "$(heads_up_in_shots "$variant")" "${interruptive:-unknown}" >> "$OUT/return.tsv"
    log "$variant id=$id stack before=$before after=$after overflow=${OVERFLOW_COUNT:-unlisted} $(grep -m1 'expanded:' "$OUT/shell-$variant-after.txt" | sed 's/^ *//')"
  done
  log "return done"
}

mode_limits() {
  local n id total best=0 same=0 stack_limit=0 overflow_limit=0 dropped="none" ids=() opened=0 w h shortcuts dynamic
  : > "$OUT/limits-progress.txt"
  for n in $(seq 1 26); do
    open_bubble "https://example.com/?probe=$RUN-limit-$n"
    ids+=("$OPENED")
    opened=$n
    sleep 1
    shell_bubbles shell-limits.txt
    read_state state-limits.txt
    ((${#STACK_IDS[@]} > stack_limit)) && stack_limit=${#STACK_IDS[@]}
    # Android 11 lists no overflow: the app's notifications stand in for it.
    total="$(state_count state-limits.txt)"
    [ -z "$OVERFLOW_COUNT" ] || total=$((${#STACK_IDS[@]} + OVERFLOW_COUNT))
    printf 'link=%s stack=%s overflow=%s app_notifications=%s\n' "$n" "${#STACK_IDS[@]}" "${OVERFLOW_COUNT:-unlisted}" "$(state_count state-limits.txt)" >> "$OUT/limits-progress.txt"
    if ((total > best)); then
      best=$total
      same=0
    else
      same=$((same + 1))
    fi
    ((same >= 3)) && break
  done
  overflow_limit=$((best - stack_limit))
  cp "$OUT/shell-limits.txt" "$OUT/shell-limits-end.txt"
  # The oldest link that neither the stack nor the overflow holds any more.
  for id in "${ids[@]}"; do
    if ! in_list "$id" "${STACK_IDS[@]}" && { [ -z "$OVERFLOW_COUNT" ] || ! in_list "$id" "${OVERFLOW_IDS[@]}"; }; then
      if [ -n "$OVERFLOW_COUNT" ] || [ "$(bubble_flag state-limits.txt "$id")" = gone ]; then dropped="$id"; break; fi
    fi
  done
  a shell dumpsys shortcut | tr -d '\r' > "$OUT/shortcut.txt"
  shortcuts="$(grep -c 'ShortcutInfo {id=bubble_' "$OUT/shortcut.txt" || true)"
  dynamic="$(grep -c 'ShortcutInfo {id=bubble_.*\[Dyn' "$OUT/shortcut.txt" || true)"
  read -r w h < <(screen_size)
  printf 'sdk\tscreen\tdensity\tstack_limit\toverflow_limit\tshortcut_count\tdynamic_shortcut_count\tdropped_key\tlinks_opened\n' > "$OUT/limits.tsv"
  printf '%s\t%sx%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "$w" "$h" "$(a shell wm density | tr -d '\r' | awk '{ print $NF }')" \
    "$stack_limit" "$overflow_limit" "$shortcuts" "$dynamic" "$dropped" "$opened" >> "$OUT/limits.tsv"
  log "dropped id=$dropped app_bubble_flag=$( [ "$dropped" = none ] && echo none || bubble_flag state-limits.txt "$dropped") dismiss_lines=$( [ "$dropped" = none ] && echo none || dismiss_lines "$dropped")"
  log "limits done"
}

# The value of the field $1 of the Reconciled line $2.
reconciled_field() { sed -n "s/.*$1=\([^ ]*\).*/\1/p" <<< "$2"; }

# Goes home and starts a Lynket screen, which reconciles, then goes home again. A link leaves the
# home screen of the app on top, and starting that again runs no activity start, hence the first
# HOME. Leaves the fields of the new Reconciled line in REC_TOTAL, REC_OVERFLOW and REC_RETURNABLE,
# the last two as comma-joined ids.
front_reconcile() {
  local lines line=""
  a shell input keyevent HOME
  sleep 1
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  a shell am start -n "$PKG/$MAIN" >/dev/null
  for _ in $(seq 1 50); do
    line="$(tail -n +"$((lines + 1))" "$OUT/$MODE.logcat.txt" | tr -d '\r' | grep 'Reconciled: ' | tail -1)"
    [ -n "$line" ] && break
    sleep 0.3
  done
  [ -n "$line" ] || fail "no Reconciled line within 15 s of an activity start"
  a shell input keyevent HOME
  sleep 1
  REC_TOTAL="$(reconciled_field total "$line")"
  REC_OVERFLOW="$(reconciled_field overflow "$line")"
  REC_RETURNABLE="$(reconciled_field returnable "$line")"
}

# Every id that any Reconciled line so far has dropped, one per line.
dropped_ids() {
  tr -d '\r' < "$OUT/$MODE.logcat.txt" | sed -n 's/.*Reconciled: .* dropped=//p' | tr ',' '\n' | grep . | sort -u
}

# Compares the list with the shell after $1 links, and adds a row to registry.tsv.
registry_row() {
  local links="$1" stack overflow_ids=() returnable_ids=() dropped=() id app expected_total
  sleep 2
  front_reconcile
  shell_bubbles "shell-registry-$links.txt"
  read_state "state-registry-$links.txt"
  stack=${#STACK_IDS[@]}
  app="$(state_count "state-registry-$links.txt")"
  IFS=, read -r -a overflow_ids <<< "$REC_OVERFLOW"
  IFS=, read -r -a returnable_ids <<< "$REC_RETURNABLE"
  mapfile -t dropped < <(dropped_ids)
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "$links" "$REC_TOTAL" "${#overflow_ids[@]}" "$REC_OVERFLOW" \
    "$REC_RETURNABLE" "$(IFS=,; echo "${dropped[*]}")" "$(IFS=,; echo "${STACK_IDS[*]}")" "$OVERFLOW_COUNT" "$app" >> "$OUT/registry.tsv"
  log "links=$links total=$REC_TOTAL overflow=${#overflow_ids[@]} dropped=${#dropped[@]} stack=$stack shell_overflow=${OVERFLOW_COUNT:-unlisted} app_notifications=$app"

  [ "$stack" -eq 5 ] || fail "the stack of this screen holds $stack bubbles, not the 5 this mode expects"
  case "$links" in
    7) [ "$REC_TOTAL" -eq 7 ] && [ "${#overflow_ids[@]}" -eq 2 ] || fail "with 7 links the list reads $REC_TOTAL with ${#overflow_ids[@]} in the overflow" ;;
    8) [ "$REC_TOTAL" -eq 8 ] && [ "${#overflow_ids[@]}" -eq 3 ] || fail "with 8 links the list reads $REC_TOTAL with ${#overflow_ids[@]} in the overflow" ;;
  esac
  [ "${#overflow_ids[@]}" -eq "$((REC_TOTAL - stack))" ] ||
    fail "at $links links the list holds ${#overflow_ids[@]} in the overflow, but $REC_TOTAL minus the stack of $stack"
  for id in "${overflow_ids[@]}"; do
    in_list "$id" "${STACK_IDS[@]}" && fail "at $links links $id is listed in the overflow but sits in the stack"
  done
  [ "$((links - REC_TOTAL))" -eq "${#dropped[@]}" ] ||
    fail "at $links links the list holds $REC_TOTAL, and ${#dropped[@]} were dropped"
  if [ "$links" -ge 22 ]; then
    # Android 11 lists no overflow: the app's notifications stand in for it.
    expected_total="$app"
    [ -z "$OVERFLOW_COUNT" ] || expected_total=$((stack + OVERFLOW_COUNT))
    [ "$REC_TOTAL" -eq "$expected_total" ] || fail "at $links links the list holds $REC_TOTAL, and Android holds $expected_total"
    [ "${#dropped[@]}" -gt 0 ] || fail "at $links links no bubble has been dropped"
  fi
  for id in "${dropped[@]}"; do
    in_list "$id" "${STACK_IDS[@]}" "${OVERFLOW_IDS[@]}" && fail "dropped id $id is still in the shell's lists"
    in_list "$id" "${overflow_ids[@]}" "${returnable_ids[@]}" && fail "dropped id $id is still offered by the list"
    [ "$(dismiss_lines "$id")" -eq 0 ] || fail "dropped id $id has a Bubble dismissed line"
  done
  return 0
}

mode_registry() {
  local n
  printf 'sdk\tlinks\ttotal\toverflow_count\toverflow_ids\treturnable_ids\tdropped_ids_so_far\tstack_keys\tshell_overflow_count\tapp_notifications\n' > "$OUT/registry.tsv"
  for n in $(seq 1 24); do
    open_bubble "https://example.com/?probe=$RUN-registry-$n"
    case "$n" in
      7 | 8 | 21 | 22 | 23 | 24) registry_row "$n" ;;
    esac
  done
  log "registry done"
}

# The ids of the lines with the text $1 in the log after line $2, comma-joined, oldest first.
new_ids() {
  tail -n +"$(($2 + 1))" "$OUT/$MODE.logcat.txt" | tr -d '\r' | sed -n "s/.*$1 \(-\{0,1\}[0-9]*\)\$/\1/p" | paste -s -d, -
}

# What Android recorded of the bubble $1, from the shell's dump $2 and the notification dump $3.
# Leaves SOUND_KEY, FLYOUT and INTERRUPTIVE.
alert_fields() {
  SOUND_KEY="$(grep -E 'mSoundNotificationKey|mVibrateNotificationKey' "$OUT/$3" | sed 's/^ *//' | grep -v '=null$' | tr '\n' ' ')"
  FLYOUT="$(awk -v key="key: 0|$PKG|$1|" 'index($0, key) { on = 1 } on && /showFlyout:/ { print $2; exit }' "$OUT/$2")"
  INTERRUPTIVE="$(awk -v key="key=0|$PKG|$1|" 'index($0, key) { on = 1 } on && /mIsInterruptive=/ { sub(/.*=/, ""); print; exit }' "$OUT/$3")"
}

# Runs the close $1 of mode_close and adds its row to close.tsv.
close_row() {
  local close="$1" returnable=() expected="" lines closed returned overflow_ids=() returnable_ids=() flag="" expanded selected app id
  IFS=, read -r -a returnable <<< "$REC_RETURNABLE"
  [ "${#returnable[@]}" -eq 0 ] || expected="${returnable[-1]}"
  shell_bubbles "shell-close-$close-before.txt"
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  shot "close-$close-before"
  drag_bubble_to_dismiss "shell-close-$close-before.txt"
  shot "close-$close-after"
  a shell input keyevent HOME
  sleep 2
  closed="$(new_ids 'Bubble dismissed:' "$lines")"
  returned="$(new_ids 'Returned:' "$lines")"
  shell_bubbles "shell-close-$close-after.txt"
  read_state "state-close-$close.txt"
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notification-close-$close.txt"
  expanded="$(awk '/expanded:/ { print $NF; exit }' "$OUT/shell-close-$close-after.txt")"
  selected="$(sed -n 's/^ *selected: *//p' "$OUT/shell-close-$close-after.txt" | head -1 | awk -F'|' '{ print (NF > 2 ? $3 : $0) }')"
  alert_fields "${returned:-none}" "shell-close-$close-after.txt" "notification-close-$close.txt"
  [ -z "$returned" ] || flag="$(bubble_flag "state-close-$close.txt" "$returned")"
  app="$(state_count "state-close-$close.txt")"
  front_reconcile
  IFS=, read -r -a overflow_ids <<< "$REC_OVERFLOW"
  IFS=, read -r -a returnable_ids <<< "$REC_RETURNABLE"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "$close" "$closed" "$expected" "$returned" \
    "$REC_TOTAL" "$REC_OVERFLOW" "$REC_RETURNABLE" "$(IFS=,; echo "${STACK_IDS[*]}")" "$app" "${flag:-none}" "${SOUND_KEY:-none}" \
    "${FLYOUT:-none}" "${INTERRUPTIVE:-none}" "${expanded:-unknown}" "${selected:-none}" >> "$OUT/close.tsv"
  log "close=$close closed=$closed expected=${expected:-none} returned=${returned:-none} total=$REC_TOTAL overflow=${#overflow_ids[@]} stack=${#STACK_IDS[@]}"

  [[ "$closed" =~ ^-?[0-9]+$ ]] || fail "close $close has the Bubble dismissed lines \"$closed\", not one"
  [ "$returned" = "$expected" ] || fail "close $close returned \"$returned\", and the newest overflow bubble is \"$expected\""
  [ "$REC_TOTAL" -eq "$((TOTAL_BEFORE - 1))" ] || fail "close $close leaves a list of $REC_TOTAL after $TOTAL_BEFORE"
  TOTAL_BEFORE="$REC_TOTAL"
  in_list "$closed" "${CLOSED_IDS[@]}" && fail "close $close closed $closed a second time"
  CLOSED_IDS+=("$closed")
  for id in "${CLOSED_IDS[@]}"; do
    in_list "$id" "${STACK_IDS[@]}" "${OVERFLOW_IDS[@]}" "${overflow_ids[@]}" "${returnable_ids[@]}" &&
      fail "closed id $id is still listed after close $close"
    [ "$(bubble_flag "state-close-$close.txt" "$id")" = gone ] || fail "closed id $id still has a notification after close $close"
    tail -n +"$((lines + 1))" "$OUT/$MODE.logcat.txt" | grep -q "Returned: $id\$" && fail "closed id $id returned after close $close"
  done
  [ -z "$returned" ] && return 0
  in_list "$returned" "${STACK_IDS[@]}" || fail "returned id $returned is not in the stack"
  [ "$flag" = true ] || fail "returned id $returned has no bubble flag"
  [ "$selected" != "$returned" ] || fail "returned id $returned took the selection"
  return 0
}

mode_close() {
  local n close overflow_ids=()
  printf 'sdk\tclose\tclosed_id\texpected_id\treturned_ids\ttotal\toverflow_ids\treturnable_ids\tstack_keys\tapp_notifications\treturned_flag\tsound_key\tflyout\tinterruptive\texpanded\tselected\n' > "$OUT/close.tsv"
  for n in 1 2 3 4 5 6 7; do
    open_bubble "https://example.com/?probe=$RUN-close-$n"
  done
  sleep 2
  front_reconcile
  shell_bubbles shell-close-start.txt
  IFS=, read -r -a overflow_ids <<< "$REC_OVERFLOW"
  [ "${#STACK_IDS[@]}" -eq 5 ] || fail "the stack of this screen holds ${#STACK_IDS[@]} bubbles, not the 5 this mode expects"
  [ "$REC_TOTAL" -eq 7 ] && [ "${#overflow_ids[@]}" -eq 2 ] || fail "with 7 links the list reads $REC_TOTAL with ${#overflow_ids[@]} in the overflow"
  TOTAL_BEFORE="$REC_TOTAL"
  CLOSED_IDS=()
  for close in 1 2 3; do close_row "$close"; done
  log "close done"
}

mode_close_stack() {
  local n lines attempt returnable=() closed=() returned=() sound_keys="" flyouts="" interrupted="" heads_up expanded id overflow_ids=()
  printf 'sdk\tdismissed_count\treturned_ids\ttotal\tstack_keys\toverflow_ids\tsound_key\tflyout\theads_up_in_shots\tinterruptive\texpanded\n' > "$OUT/close-stack.tsv"
  for n in 1 2 3 4 5 6 7; do
    open_bubble "https://example.com/?probe=$RUN-stack-$n"
  done
  sleep 2
  front_reconcile
  IFS=, read -r -a returnable <<< "$REC_RETURNABLE"
  [ "${#returnable[@]}" -eq 2 ] || fail "with 7 links ${#returnable[@]} bubbles can return, not 2"
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  for attempt in 1 2 3; do
    shell_bubbles shell-closestack-before.txt
    shot return-closestack-before
    drag_stack_to_dismiss shell-closestack-before.txt
    [ -n "$(new_ids 'Bubble dismissed:' "$lines")" ] && break
    # The system introduction of the first bubble takes the first touch.
    a shell input keyevent BACK
    sleep 2
  done
  for n in 1 2 3 4; do shot "return-closestack-shot-$n"; sleep 0.2; done
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notification-closestack.txt"
  shell_bubbles shell-closestack-after.txt
  IFS=, read -r -a closed <<< "$(new_ids 'Bubble dismissed:' "$lines")"
  IFS=, read -r -a returned <<< "$(new_ids 'Returned:' "$lines")"
  for id in "${returned[@]}"; do
    alert_fields "$id" shell-closestack-after.txt notification-closestack.txt
    sound_keys="$sound_keys${SOUND_KEY:-}"
    flyouts="$flyouts${FLYOUT:-none} "
    interrupted="$interrupted${INTERRUPTIVE:-none} "
  done
  heads_up="$(heads_up_in_shots closestack)"
  expanded="$(awk '/expanded:/ { print $NF; exit }' "$OUT/shell-closestack-after.txt")"
  front_reconcile
  IFS=, read -r -a overflow_ids <<< "$REC_OVERFLOW"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "${#closed[@]}" "$(IFS=,; echo "${returned[*]}")" "$REC_TOTAL" \
    "$(IFS=,; echo "${STACK_IDS[*]}")" "$REC_OVERFLOW" "${sound_keys:-none}" "$flyouts" "$heads_up" "$interrupted" "${expanded:-unknown}" >> "$OUT/close-stack.tsv"
  log "drags=$attempt closed=${#closed[@]} returned=${returned[*]} total=$REC_TOTAL stack=${#STACK_IDS[@]} heads_up=$heads_up expanded=$expanded"

  [ "${#closed[@]}" -eq 5 ] || fail "the drag gave ${#closed[@]} Bubble dismissed lines, not 5"
  [ "${#returned[@]}" -eq 2 ] || fail "the drag gave ${#returned[@]} Returned lines, not 2"
  [ "${returned[0]}" = "${returnable[1]}" ] && [ "${returned[1]}" = "${returnable[0]}" ] ||
    fail "the returns ran ${returned[*]}, and the overflow newest first is ${returnable[1]} ${returnable[0]}"
  [ "$REC_TOTAL" -eq 2 ] || fail "the list holds $REC_TOTAL after the drag, not 2"
  for id in "${returned[@]}"; do
    in_list "$id" "${STACK_IDS[@]}" || fail "returned id $id is not in the stack"
  done
  [ "$expanded" = false ] || fail "the stack is expanded after the returns"
  [ -z "$sound_keys" ] || fail "a return took the sound or the vibration: $sound_keys"
  [ "$flyouts" = "false false " ] || fail "a return showed a flyout: $flyouts"
  [ "$interrupted" = "false false " ] || fail "a return was marked interruptive: $interrupted"
  [ "$heads_up" = false ] || fail "the shots show a heads-up row"
  log "close-stack done"
}

# The share of the row of the open stack that differs between the shots $1 and $2.
row_changed_share() {
  python - "$OUT/$1.png" "$OUT/$2.png" <<'PY'
import sys
from PIL import Image, ImageChops
first, second = (Image.open(path).convert("RGB") for path in sys.argv[1:3])
w, h = first.size
box = (0, int(h * 0.04), w, int(h * 0.11))
data = ImageChops.difference(first.crop(box), second.crop(box)).convert("L").point(lambda v: 255 if v > 24 else 0).histogram()
print(round(data[255] / ((box[2] - box[0]) * (box[3] - box[1])), 4))
PY
}

mode_close_icon() {
  local n id lines returned in_stack=false share copies
  python "$(hostpath "$HERE/slow-icon-server.py")" "$ICON_PORT" > "$OUT/slow-icon-server.txt" 2>&1 &
  ICON_SERVER_PID=$!
  sleep 1
  kill -0 "$ICON_SERVER_PID" 2>/dev/null || fail "the icon server could not start; is port $ICON_PORT taken?"
  a reverse "tcp:$ICON_PORT" "tcp:$ICON_PORT" >/dev/null
  printf 'sdk\tid\treturned\tin_stack\tchanged_share\n' > "$OUT/close-icon.tsv"
  open_bubble "http://localhost:$ICON_PORT/?probe=$RUN"
  id="$OPENED"
  for n in 1 2 3 4 5; do
    open_bubble "https://example.com/?probe=$RUN-icon-$n"
  done
  sleep 2
  front_reconcile
  [ "$REC_RETURNABLE" = "$id" ] || fail "the slow link $id is not the one bubble in the overflow: \"$REC_RETURNABLE\""
  shell_bubbles shell-icon-before.txt
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  drag_bubble_to_dismiss shell-icon-before.txt
  # The stack stays open: the row shows the returned bubble wherever Android puts it, and a collapsed
  # stack shows only its front bubble.
  shot close-icon-disc
  curl -s -m 5 "localhost:$ICON_PORT/release" >/dev/null || fail "the icon server did not answer"
  sleep 8
  shot close-icon-done
  a shell input keyevent HOME
  sleep 2
  returned="$(new_ids 'Returned:' "$lines")"
  shell_bubbles shell-icon-after.txt
  read_state state-icon-after.txt
  in_list "$id" "${STACK_IDS[@]}" && in_stack=true
  share="$(row_changed_share close-icon-disc close-icon-done)"
  copies="$(grep -c "state id=$id " "$OUT/state-icon-after.txt" || true)"
  printf '%s\t%s\t%s\t%s\t%s\n' "$SDK" "$id" "$returned" "$in_stack" "$share" >> "$OUT/close-icon.tsv"
  log "id=$id returned=$returned in_stack=$in_stack notifications=$copies changed_share=$share"

  [ "$returned" = "$id" ] || fail "the close returned \"$returned\", not the one bubble $id"
  [ "$in_stack" = true ] || fail "the returned bubble left the stack"
  [ "$copies" -eq 1 ] || fail "the returned bubble has $copies notifications"
  python -c "import sys; sys.exit(0 if $share > 0.002 else 1)" || fail "the row did not change when the icon arrived"
  log "close-icon done"
}

# Shows the page of the bubble $1 in the open stack, from the last read of the shell. The row has six
# slots: the stack, then the overflow button. A tap on the selected bubble would close the stack, and
# its page already shows.
tap_row_bubble() {
  local i w h selected
  selected="$(sed -n 's/^ *selected: *//p' "$OUT/shell-expanded.txt" | head -1 | awk -F'|' '{ print (NF > 2 ? $3 : $0) }')"
  if [ "$selected" = "$1" ]; then
    sleep 5
    return 0
  fi
  read -r w h < <(screen_size)
  for i in "${!STACK_IDS[@]}"; do
    if [ "${STACK_IDS[$i]}" = "$1" ]; then
      a shell input tap "$((w * (2 * i + 1) / 12))" "$((h * 23 / 300))"
      sleep 5
      return 0
    fi
  done
  fail "bubble $1 is not in the stack: ${STACK_IDS[*]}"
}

# Saves the screen as $1.png, and what a dump of every window holds of the count as $1.dump.txt with
# its text in PAGE_TEXT. The screenshot is the evidence: the dump is a second reading, and may be empty.
page_text() {
  shot "$1"
  a shell uiautomator dump --windows /sdcard/overflow-probe-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/overflow-probe-ui.xml | tr -d '\r' | tr '>' '\n' | grep -F 'webview-count' > "$OUT/$1.dump.txt" || true
  PAGE_TEXT="$(sed -n 's/.* text="\([^"]*\)".*/\1/p' "$OUT/$1.dump.txt" | head -1)"
}

# The place of the bubble $1 in OPEN_IDS, from 1, as the count reads it.
open_place() {
  local i
  for i in "${!OPEN_IDS[@]}"; do
    [ "${OPEN_IDS[$i]}" = "$1" ] && { echo "$((i + 1))"; return 0; }
  done
  fail "bubble $1 was not opened by this run"
}

# Adds a row to the .tsv of the mode: the point, the bubble, the expected text and the dump's text.
count_row() {
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "$1" "${URL_OF[$2]}" "$REC_TOTAL" "$REC_OVERFLOW" "$3" "${PAGE_TEXT:-none}" >> "$OUT/$MODE.tsv"
  log "point=$1 id=$2 total=$REC_TOTAL expected=\"$3\" dump=\"${PAGE_TEXT:-none}\""
}

# Fails when the app crashed during the run.
no_crash() {
  a logcat -d -b crash | tr -d '\r' > "$OUT/$MODE.crash.txt"
  [ ! -s "$OUT/$MODE.crash.txt" ] || fail "the crash buffer is not empty"
}

mode_count() {
  local n id lines w h closed overflow_ids=() position
  OPEN_IDS=()
  printf 'sdk\tpoint\tlink\treconciled_total\treconciled_overflow\texpected\tdump_text\n' > "$OUT/count.tsv"
  for n in 1 2 3 4 5 6 7; do
    open_bubble "https://example.com/?probe=$RUN-count-$n"
    OPEN_IDS+=("$OPENED")
  done
  sleep 2
  front_reconcile
  IFS=, read -r -a overflow_ids <<< "$REC_OVERFLOW"
  [ "$REC_TOTAL" -eq 7 ] && [ "${#overflow_ids[@]}" -eq 2 ] || fail "with 7 links the list reads $REC_TOTAL with ${#overflow_ids[@]} in the overflow"
  shell_bubbles shell-count-7.txt
  expand_stack shell-count-7.txt
  tap_row_bubble "${OPEN_IDS[2]}"
  page_text count-3of7
  count_row 3of7 "${OPEN_IDS[2]}" "3 of 7"
  a shell input keyevent HOME
  sleep 2

  open_bubble "https://example.com/?probe=$RUN-count-8"
  OPEN_IDS+=("$OPENED")
  sleep 2
  front_reconcile
  IFS=, read -r -a overflow_ids <<< "$REC_OVERFLOW"
  [ "$REC_TOTAL" -eq 8 ] && [ "${#overflow_ids[@]}" -eq 3 ] || fail "with 8 links the list reads $REC_TOTAL with ${#overflow_ids[@]} in the overflow"
  # A bubble that was read moves in the stack, so the page comes from a fresh read.
  shell_bubbles shell-count-8.txt
  expand_stack shell-count-8.txt
  id="${STACK_IDS[-1]}"
  position="$(open_place "$id")"
  tap_row_bubble "$id"
  page_text count-at8
  count_row at8 "$id" "$position of 8"

  # A close of the bubble in the first slot, with this page on screen.
  [ "${STACK_IDS[0]}" != "$id" ] || fail "the page's own bubble is in the first slot"
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  read -r w h < <(screen_size)
  drag_to_dismiss "$((w / 12))" "$((h * 23 / 300))"
  closed="$(new_ids 'Bubble dismissed:' "$lines")"
  [[ "$closed" =~ ^-?[0-9]+$ ]] || fail "the close has the Bubble dismissed lines \"$closed\", not one"
  [ "$closed" != "$id" ] || fail "the close took the page's own bubble"
  [ "$(open_place "$closed")" -lt "$position" ] && position=$((position - 1))
  shell_bubbles shell-count-closed.txt
  grep -q 'expanded: true' "$OUT/shell-count-closed.txt" || fail "the stack closed with the bubble"
  page_text count-after-close
  a shell input keyevent HOME
  sleep 2
  front_reconcile
  [ "$REC_TOTAL" -eq 7 ] || fail "the close leaves a list of $REC_TOTAL, not 7"
  count_row after-close "$id" "$position of 7"
  no_crash
  log "count done"
}

mode_count_long() {
  local n filler
  OPEN_IDS=()
  printf 'sdk\tpoint\tlink\treconciled_total\treconciled_overflow\texpected\tdump_text\n' > "$OUT/count-long.tsv"
  FONT_SCALE_BEFORE="$(a shell settings get system font_scale | tr -d '\r')"
  log "font_scale before=$FONT_SCALE_BEFORE"
  a shell settings put system font_scale 2.0
  filler="$(printf 'a%.0s' $(seq 1 220))"
  for n in $(seq 1 12); do
    open_bubble "https://example.com/?probe=$RUN-long-$n&pad=$filler"
    OPEN_IDS+=("$OPENED")
  done
  sleep 2
  front_reconcile
  [ "$REC_TOTAL" -eq 12 ] || fail "with 12 links the list reads $REC_TOTAL"
  shell_bubbles shell-long.txt
  expand_stack shell-long.txt
  tap_row_bubble "${OPEN_IDS[11]}"
  page_text count-long
  count_row long "${OPEN_IDS[11]}" "12 of 12"
  a shell input keyevent HOME
  sleep 1
  no_crash
  log "count-long done"
}

# Fails when Lynket draws a row in the shade capture $1, and when the dump did not read the shade.
no_lynket_row() {
  local rows
  grep -q 'text="Manage"' "$OUT/$1.xml" || fail "the dump of $1 did not read the shade"
  rows="$(tr '>' '\n' < "$OUT/$1.xml" | grep -E 'package="arun\.com\.chromer|text="[^"]*(Active bubbles|swipe to expand|Example Domain|example\.com|Lynket|A page is open)' | wc -l)"
  [ "$rows" -eq 0 ] || fail "$1 shows $rows Lynket rows"
}

# Saves every text of the app's notifications and shortcuts as notification-$1.txt and
# shortcut-$1.txt, and fails when one holds a count. It first shows that the dumps read the text:
# $2 records, the summary's title and text, and at least one shortcut label.
no_count_text() {
  local labels hits records
  a shell dumpsys notification --noredact | tr -d '\r' \
    | awk -v pkg="pkg=$PKG " '/NotificationRecord\(/ { on = index($0, pkg) > 0 } on' > "$OUT/notification-$1.txt"
  a shell dumpsys shortcut | tr -d '\r' \
    | awk -v pkg="Package: $PKG " '/^ *Package: / { on = index($0, pkg) > 0 } on' > "$OUT/shortcut-$1.txt"
  records="$(grep -c 'NotificationRecord(' "$OUT/notification-$1.txt" || true)"
  [ "$records" -eq "$2" ] || fail "the dump at $1 holds $records records of the app, not $2"
  grep -qF 'android.title=String (Active bubbles)' "$OUT/notification-$1.txt" || fail "the dump at $1 reads no summary title"
  grep -qF 'android.text=String (Showing bubble notifications, swipe to expand)' "$OUT/notification-$1.txt" || fail "the dump at $1 reads no summary text"
  labels="$(grep -c 'shortLabel=' "$OUT/shortcut-$1.txt" || true)"
  [ "$labels" -gt 0 ] || fail "the dump at $1 reads no shortcut label"
  hits="$(cat "$OUT/notification-$1.txt" "$OUT/shortcut-$1.txt" | grep -cE '[0-9]+ of [0-9]+' || true)"
  [ "$hits" -eq 0 ] || fail "$hits lines at $1 hold a count"
  printf '%s\t%s\t%s\t%s\t%s\n' "$SDK" "$1" "$records" "$labels" "$hits" >> "$OUT/shade.tsv"
  log "point=$1 records=$records shortcut_labels=$labels count_lines=$hits"
}

mode_shade() {
  local n filler
  printf 'sdk\tpoint\tnotification_records\tshortcut_labels\tcount_lines\n' > "$OUT/shade.tsv"
  open_bubble "https://example.com/?probe=$RUN-shade-1"
  a shell input keyevent HOME
  sleep 2
  shade_capture shade-1
  no_lynket_row shade-1
  no_count_text 1 2
  for n in 2 3 4 5 6; do
    open_bubble "https://example.com/?probe=$RUN-shade-$n"
  done
  # The worst content of the screen: a long address.
  filler="$(printf 'a%.0s' $(seq 1 220))"
  open_bubble "https://example.com/?probe=$RUN-shade-7&pad=$filler"
  sleep 2
  front_reconcile
  [ "$REC_TOTAL" -eq 7 ] || fail "with 7 links the list reads $REC_TOTAL"
  shade_capture shade-7
  no_lynket_row shade-7
  no_count_text 7 8
  # With a page on screen the count is drawn, and it must still reach nothing else. The screenshot
  # is the evidence for the shade here: a dump can read the page rather than the shade.
  shell_bubbles shell-shade-7.txt
  expand_stack shell-shade-7.txt
  tap_row_bubble "${STACK_IDS[-1]}"
  page_text shade-page-open
  shade_capture shade-page
  no_count_text page 8
  a shell input keyevent HOME
  sleep 1
  no_crash
  log "shade done"
}

# Taps the node with the text $1 in any window: the Manage menu of an open bubble is the shell's, and a
# dump of the active window can miss it.
tap_window_text() {
  local xy
  a shell uiautomator dump --windows /sdcard/overflow-probe-ui.xml >/dev/null 2>&1 || true
  xy="$(a exec-out cat /sdcard/overflow-probe-ui.xml | tr -d '\r' | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }')"
  [ -n "$xy" ] || fail "no \"$1\" in the dump of the windows"
  # shellcheck disable=SC2086
  a shell input tap $xy
}

# Fails when the dump of the page holds another count than $1. An empty dump is no reading: the
# screenshot stands.
page_reads() {
  [ -z "$PAGE_TEXT" ] || [[ "$PAGE_TEXT" == *"$1" ]] || fail "the page reads \"$PAGE_TEXT\", not \"$1\""
}

# Adds a row to story.tsv: the point, the total, the closed id, the expected and the real return, and
# the expected and the dumped count.
story_row() {
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$SDK" "$1" "$2" "$3" "$4" "$5" "$(IFS=,; echo "${STACK_IDS[*]}")" \
    "${SOUND_KEY:-none}" "${FLYOUT:-none}" "${INTERRUPTIVE:-none}" "$6" "${PAGE_TEXT:-none}" >> "$OUT/story.tsv"
  log "point=$1 total=$2 closed=${3:-none} expected_return=${4:-none} returned=${5:-none} page=\"$6\" dump=\"${PAGE_TEXT:-none}\""
}

# Reads the close named $1, made since line $2 of the log, which must return $3 and leave a list of $4:
# one closed id in CLOSED, with no alert. Leaves the ids of the stack in STACK_IDS.
story_close() {
  local total
  sleep 2
  CLOSED="$(new_ids 'Bubble dismissed:' "$2")"
  RETURNED="$(new_ids 'Returned:' "$2")"
  shell_bubbles "shell-story-$1.txt"
  read_state "state-story-$1.txt"
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notification-story-$1.txt"
  alert_fields "${RETURNED:-none}" "shell-story-$1.txt" "notification-story-$1.txt"
  total="$(reconciled_field total "$(tr -d '\r' < "$OUT/$MODE.logcat.txt" | grep 'Reconciled: ' | tail -1)")"
  [[ "$CLOSED" =~ ^-?[0-9]+$ ]] || fail "$1 has the Bubble dismissed lines \"$CLOSED\", not one"
  [ "$RETURNED" = "$3" ] || fail "$1 returned \"$RETURNED\", and the newest overflow bubble is \"$3\""
  [ "$total" -eq "$4" ] || fail "$1 leaves a list of $total, not $4"
  in_list "$RETURNED" "${STACK_IDS[@]}" || fail "returned id $RETURNED is not in the stack after $1"
  [ "$(bubble_flag "state-story-$1.txt" "$RETURNED")" = true ] || fail "returned id $RETURNED has no bubble flag after $1"
  [ "$(bubble_flag "state-story-$1.txt" "$CLOSED")" = gone ] || fail "closed id $CLOSED still has a notification after $1"
  [ -z "$SOUND_KEY" ] || fail "the return of $1 took the sound or the vibration: $SOUND_KEY"
  [ "$FLYOUT" = false ] || fail "the return of $1 showed a flyout: $FLYOUT"
  [ "$INTERRUPTIVE" = false ] || fail "the return of $1 was marked interruptive: $INTERRUPTIVE"
}

mode_story() {
  local n id lines w h page position returnable=() first
  OPEN_IDS=()
  printf 'sdk\tpoint\ttotal\tclosed_id\texpected_return\treturned_ids\tstack_keys\tsound_key\tflyout\tinterruptive\texpected_text\tdump_text\n' > "$OUT/story.tsv"
  printf 'sdk\tclose\tclosed_id\texpected_id\treturned_ids\ttotal\toverflow_ids\treturnable_ids\tstack_keys\tapp_notifications\treturned_flag\tsound_key\tflyout\tinterruptive\texpanded\tselected\n' > "$OUT/close.tsv"
  printf 'sdk\tpoint\tnotification_records\tshortcut_labels\tcount_lines\n' > "$OUT/shade.tsv"

  # Eight links, and the count of a bubble in the stack.
  for n in 1 2 3 4 5 6 7 8; do
    open_bubble "https://example.com/?probe=$RUN-story-$n"
    OPEN_IDS+=("$OPENED")
  done
  sleep 2
  front_reconcile
  IFS=, read -r -a returnable <<< "$REC_RETURNABLE"
  [ "$REC_TOTAL" -eq 8 ] && [ "${#returnable[@]}" -eq 3 ] || fail "with 8 links the list reads $REC_TOTAL with ${#returnable[@]} to return"
  shell_bubbles shell-story-8.txt
  [ "${#STACK_IDS[@]}" -eq 5 ] || fail "the stack of this screen holds ${#STACK_IDS[@]} bubbles, not the 5 this mode expects"
  expand_stack shell-story-8.txt
  page="${STACK_IDS[-1]}"
  position="$(open_place "$page")"
  tap_row_bubble "$page"
  page_text story-8
  page_reads "$position of 8"
  story_row open 8 "" "" "" "$position of 8"

  # One close in the stack with that page on screen: the count changes in place, and the newest
  # overflow bubble returns.
  [ "${STACK_IDS[0]}" != "$page" ] || fail "the page's own bubble is in the first slot"
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  read -r w h < <(screen_size)
  shot story-close-before
  drag_to_dismiss "$((w / 12))" "$((h * 23 / 300))"
  story_close close "$lines" "${returnable[-1]}" 7
  first="$CLOSED"
  [ "$first" != "$page" ] || fail "the close took the page's own bubble"
  [ "$(open_place "$first")" -lt "$position" ] && position=$((position - 1))
  grep -q 'expanded: true' "$OUT/shell-story-close.txt" || fail "the stack closed with the bubble"
  page_text story-7
  page_reads "$position of 7"
  story_row close 7 "$first" "${returnable[-1]}" "$RETURNED" "$position of 7"

  # One close on purpose, of the page on screen, through its Manage menu.
  lines="$(wc -l < "$OUT/$MODE.logcat.txt")"
  tap_window_text Manage
  sleep 2
  shot story-menu
  tap_window_text "Dismiss bubble"
  sleep 2
  story_close purpose "$lines" "${returnable[-2]}" 6
  [ "$CLOSED" = "$page" ] || fail "the menu closed $CLOSED, not the page's bubble $page"
  shot story-purpose-after
  a shell input keyevent HOME
  sleep 2
  PAGE_TEXT=""
  story_row purpose 6 "$CLOSED" "${returnable[-2]}" "$RETURNED" ""
  front_reconcile
  [ "$REC_TOTAL" -eq 6 ] || fail "the two closes leave a list of $REC_TOTAL, not 6"

  # Later closes empty the overflow; neither closed bubble comes back.
  TOTAL_BEFORE="$REC_TOTAL"
  CLOSED_IDS=("$first" "$page")
  for n in 3 4; do close_row "$n"; done
  [ -z "$REC_RETURNABLE" ] || fail "the overflow still holds $REC_RETURNABLE after four closes"
  for id in "$first" "$page"; do
    if tr -d '\r' < "$OUT/$MODE.logcat.txt" | grep -q "Returned: $id\$"; then fail "closed id $id returned"; fi
  done

  shade_capture story-shade
  no_lynket_row story-shade
  no_count_text end "$((REC_TOTAL + 1))"
  no_crash
  log "story done"
}

prepare
"mode_${MODE//-/_}"
