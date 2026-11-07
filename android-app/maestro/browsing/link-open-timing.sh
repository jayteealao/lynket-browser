#!/usr/bin/env bash
# Link-open timing on the debug build: the time from a link intent to the first page finish of
# the web view page, over N cold opens, in Web View mode or in a native bubble.
#
#   webview    Web View mode on (off again on exit); each run opens the link through the intercept
#              activity and waits for the first "Page finished first" line of the page
#   bubble     run after webheads-check.sh <serial> <dir> bubble; each run opens the link, taps the
#              bubble as soon as it shows and waits for the same line (also records the tap time)
#   compare    compare <before.tsv> <after.tsv>: prints the medians of both files and PASS when each
#              after median is at most 1.05 x its before median, else FAIL (exit 1)
#
# Each run: force-stop, logcat cleared, a LinkOpenTiming mark, am start -W with a new query, a wait
# of up to 30 s; both stamps come from logcat -v epoch (one device clock). A run that times out is
# counted and left out, the script goes on. Rows go to <evidence-dir>/<mode>.tsv with the columns
# run, intent_to_finish_ms, tap_to_finish_ms (bubble only, else -), intercept_total_ms.
# Measure the builds to compare in one quiet window, on the same device and build type.
#
# Usage: maestro/browsing/link-open-timing.sh <serial> <evidence-dir> <webview|bubble> [runs=10]
#        maestro/browsing/link-open-timing.sh compare <before.tsv> <after.tsv>   (run from android-app/)
set -euo pipefail

USAGE="usage: link-open-timing.sh <serial> <evidence-dir> <webview|bubble> [runs=10] | compare <before.tsv> <after.tsv>"
MARGIN=1.05

# Median of the numeric column $2 (1-based) of the TSV $1, header skipped; "-" values ignored.
median() {
  awk -F'\t' -v c="$2" 'NR > 1 && $c != "-" && $c != "" { print $c }' "$1" | sort -n |
    awk '{ v[NR] = $1 } END { if (NR == 0) { print "-"; exit } if (NR % 2) print v[(NR + 1) / 2]; else print (v[NR / 2] + v[NR / 2 + 1]) / 2 }'
}

compare() {
  local before="${1:?$USAGE}" after="${2:?$USAGE}" result=PASS col name b f verdict
  for col in 2 3 4; do
    name="$(awk -F'\t' -v c="$col" 'NR == 1 { print $c }' "$before")"
    b="$(median "$before" "$col")"
    f="$(median "$after" "$col")"
    if [ "$b" = "-" ] || [ "$f" = "-" ]; then
      echo "$name: before $b, after $f (not compared)"
      continue
    fi
    verdict="$(awk -v b="$b" -v f="$f" -v m="$MARGIN" 'BEGIN { print (f <= b * m) ? "PASS" : "FAIL" }')"
    echo "$name: before median $b ms, after median $f ms, limit $(awk -v b="$b" -v m="$MARGIN" 'BEGIN { print b * m }') ms: $verdict"
    [ "$verdict" = PASS ] || result=FAIL
  done
  echo "$result"
  [ "$result" = PASS ]
}

if [ "${1:-}" = compare ]; then
  shift
  compare "$@"
  exit $?
fi

SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
RUNS="${4:-10}"
case "$MODE" in webview|bubble) ;; *) echo "$USAGE" >&2; exit 2 ;; esac
source "$(dirname "$0")/../webheads/lib.sh"
LT_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
FIXTURES="$LT_HERE/../reader/fixtures"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="timing-$MODE"
CHECK_NAME="link-open timing $MODE"
: > "$OUT/$LOG_NAME.txt"

# ---------------------------------------------------------------------------------------------
# Fixture server (the same shape as maestro/reader/reader-check.sh)

PORT=""
SERVER_PID=""
WEBVIEW_TURNED_ON=""
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
cleanup() {
  [ -n "$WEBVIEW_TURNED_ON" ] && webview_mode off || true
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------------------------
# UI helpers (the same steps as maestro/reader/reader-check.sh)

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/timing-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/timing-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/timing-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/timing-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}
centre_of() {
  { ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}
tap_on() {
  local xy _
  for _ in $(seq 1 15); do
    xy="$(centre_of "$1")"
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
wait_for() {
  local _
  for _ in $(seq 1 20); do
    ui_dump | grep -qF -- "$1" && return 0
    dismiss_anr
    sleep 2
  done
  return 1
}
scroll_to() {
  local _ w h
  read -r w h < <(screen_size)
  for _ in $(seq 1 8); do
    ui_dump | grep -qF -- "$1" && return 0
    a shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 3)) 300
    sleep 1
  done
  ui_dump | grep -qF -- "$1"
}
stored() {
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}
top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}
destination() {
  a shell am force-stop com.android.chrome
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" "$1" >/dev/null
  sleep 3
}

# Web View mode on (Use Web View on the provider screen) or off (Chrome chosen again).
webview_mode() {
  destination ProviderSelection
  if [ "$1" = on ]; then
    [ "$(stored use_webview_pref)" = true ] && { a shell input keyevent KEYCODE_HOME; return 0; }
    local attempt
    for attempt in 1 2 3; do
      [ "$attempt" = 1 ] || destination ProviderSelection
      scroll_to 'resource-id="provider-webview"' || fail "no Use Web View row"
      tap_on 'resource-id="provider-webview"'
      wait_for 'text="Are you sure?"' || true
      sleep 1
      tap_on 'text="OK"' || tap_on 'text="Yes"' || true
      sleep 2
      [ "$(stored use_webview_pref)" = true ] && break
    done
  else
    [ "$(stored use_webview_pref)" = true ] || { a shell input keyevent KEYCODE_HOME; return 0; }
    tap_on 'resource-id="provider-com.android.chrome"'
    wait_for 'text="Use"' || true
    tap_on 'resource-id="provider-confirm"' || tap_on 'text="Use"' || true
  fi
  sleep 1
  a shell input keyevent KEYCODE_HOME
  local now; now="$(stored use_webview_pref)"
  log "web view mode $1 -> use_webview_pref $now"
  if [ "$1" = on ]; then [ "$now" = true ] || fail "Web View mode did not turn on"
  else [ "$now" = true ] && fail "Web View mode did not turn off"; fi
  return 0
}

# The shell's bubble stack position as a tap point "X Y", empty while no bubble shows.
bubble_point() {
  a shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | tr -d '\r' |
    awk -v pkg="$PKG" '/currentStackPos/ { pos = $0 } index($0, pkg) { seen = 1 } END { if (seen) print pos }' |
    sed -nE 's/.*currentStackPos: *PointF\((-?[0-9.]+), *(-?[0-9.]+)\).*/\1 \2/p' | head -1 |
    awk 'NF == 2 { x = $1 < 0 ? 0 : $1; print int(x + 70), int($2 + 70) }' || true
}

# Taps the bubble as soon as it shows (polling, at most 20 s), until its page is in front.
expand_bubble() {
  local xy="" _
  for _ in $(seq 1 80); do
    xy="$(bubble_point)"
    [ -n "$xy" ] && break
    sleep 0.25
  done
  [ -n "$xy" ] || return 1
  for _ in 1 2 3; do
    a shell log -t LinkOpenTiming tap
    # shellcheck disable=SC2086
    a shell input tap $xy
    for _ in $(seq 1 16); do
      top_activity | grep -q "EmbeddableWebViewActivity" && return 0
      sleep 0.25
    done
  done
  return 1
}

# Epoch milliseconds of the first logcat line matching $1 (fixed text), empty if none.
stamp_of() {
  a logcat -d -v epoch | tr -d '\r' | grep -F -m1 -- "$1" | awk '{ split($1, s, "."); printf "%d%03d\n", s[1], substr(s[2] "000", 1, 3) }' || true
}

# ---------------------------------------------------------------------------------------------

a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard >/dev/null 2>&1 || true
start_server
if [ "$MODE" = webview ]; then
  webview_mode on
  WEBVIEW_TURNED_ON=1
fi

TSV="$OUT/$MODE.tsv"
printf 'run\tintent_to_finish_ms\ttap_to_finish_ms\tintercept_total_ms\n' > "$TSV"
TIMEOUTS=0
for i in $(seq 1 "$RUNS"); do
  a shell am force-stop "$PKG"
  sleep 1
  a logcat -c
  a shell log -t LinkOpenTiming intent
  total="$(a shell am start -W -a android.intent.action.VIEW -d "http://localhost:$PORT/article.html?run=$i" -n "$PKG/$INTERCEPT" |
    tr -d '\r' | awk -F': ' '/TotalTime/ { print $2 }')"
  tapped=""
  if [ "$MODE" = bubble ]; then
    expand_bubble && tapped=1 || log "run $i: the bubble did not open its page"
  fi
  finished=""
  for _ in $(seq 1 120); do
    finished="$(stamp_of "Page finished first")"
    [ -n "$finished" ] && break
    sleep 0.25
  done
  a logcat -d -v epoch > "$OUT/$MODE-run-$i.logcat.txt" 2>/dev/null || true
  if [ -z "$finished" ]; then
    TIMEOUTS=$((TIMEOUTS + 1))
    log "run $i: no page finish within 30 s (timed out)"
    continue
  fi
  intent="$(stamp_of "LinkOpenTiming: intent")"
  tap_ms="-"
  if [ -n "$tapped" ]; then
    tap="$(a logcat -d -v epoch | tr -d '\r' | grep -F -- "LinkOpenTiming: tap" | tail -1 |
      awk '{ split($1, s, "."); printf "%d%03d\n", s[1], substr(s[2] "000", 1, 3) }' || true)"
    [ -n "$tap" ] && tap_ms=$((finished - tap))
  fi
  printf '%s\t%s\t%s\t%s\n' "$i" "$((finished - intent))" "$tap_ms" "${total:--}" >> "$TSV"
  log "run $i: intent to finish $((finished - intent)) ms, tap to finish $tap_ms ms, intercept ${total:--} ms"
done

log "$MODE: $RUNS runs, $TIMEOUTS timed out"
log "$MODE medians: intent_to_finish_ms $(median "$TSV" 2), tap_to_finish_ms $(median "$TSV" 3), intercept_total_ms $(median "$TSV" 4)"
