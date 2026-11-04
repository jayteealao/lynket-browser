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
  dismiss | return | limits | registry) ;;
  *) echo "usage: overflow-probe.sh <serial> <dir> <dismiss|return|limits|registry>" >&2; exit 2 ;;
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
  a logcat -v brief BubbleProbe:V BubbleDismissReceiver:V BubbleReconciler:V '*:S' > "$OUT/$MODE.logcat.txt" 2>/dev/null &
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

# Starts a Lynket screen, which reconciles, then goes home. Leaves the fields of the new Reconciled
# line in REC_TOTAL, REC_OVERFLOW and REC_RETURNABLE, the last two as comma-joined ids.
front_reconcile() {
  local lines line=""
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

prepare
"mode_$MODE"
