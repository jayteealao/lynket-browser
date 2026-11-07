#!/usr/bin/env bash
# The menu of a page in a native bubble, on a device with the debug build. One mode per run:
#
#   full-window  run after webheads-check.sh <serial> <dir> bubble; two fixture pages open as
#                bubbles, the expanded one shows its count, Open in full window is the first item
#                of its menu, and a tap on it shows the same page in a full-screen task of its own
#                (not a bubble's task) with its address and no count; one bubble fewer is left, each
#                open before the tap, and a page left shows no count when it is the only bubble (else
#                a count of the bubbles left); bubbles of an earlier run, such as the setup's, stay open
#
# The menu audit modes tap each item of the menu and the bottom bar of a page in an expanded
# native bubble and write one row per item to bubble-menu-audit.tsv: works or misbehaves, and why.
# Each mode seeds its own settings (maestro/seed/bubble-menu-audit.xml, which clears the app's
# data: never run it on a device with a person's data) and runs the native bubble setup of
# maestro/webheads/webheads-check.sh itself, so it needs no setup run before it:
#
#   item <name>    one item, with the bottom bar on and Share as the menu button (the names are
#                  listed by "item" with an unknown name)
#   audit-menu     Copy link, Open with, Share, History and Add to Home Screen in the menu
#   audit-action   Share with <app> and the menu button for each preferred action (a secondary
#                  browser, a favourite share app, Share); an action whose app the device lacks
#                  gets a not-tested row
#   audit-bar      New tab, Share, Tabs, Minimize and Article mode in the bottom bar
#   audit-bar-off  Tabs and Article mode in the menu, with the bottom bar off
#   hidden         with the bottom bar on, then off, the menu and the bar of a page in a bubble
#                  show no item that the committed table (lynket/src/test/resources/
#                  bubble-menu-audit.tsv) says misbehaves, show every item it says works, and
#                  start with Open in full window
#
# An item misbehaves when, after the tap, the app crashed, a full app screen opened inside the
# bubble's small window, the page could not be reached again (by Back, or by Home and a tap on
# the bubble), or nothing happened at all. A judge that cannot read the device writes undecided
# and fails the mode; it never writes works.
#
# The pages come from maestro/reader/fixtures/, served from this host on a free port through adb
# reverse, so the run needs no external site. FULL_URL replaces the first page with any address,
# for example an https site (the second page stays a fixture).
#
# Evidence: full-window-before-notifications.txt and full-window-after-notifications.txt (two
# bubble lines, then one), full-window-menu.png/.xml (the open menu), full-window-activities.txt
# (the task and windowing mode of the full window), full-window.png/.xml (the full window),
# full-window-other.png/.xml (the bubble that is left), full-window.logcat.txt and
# full-window.crash.txt, and a PASS or FAIL line in bubble-menu.txt. On exit it stops the server
# and removes the reverse. The audit modes write, per item, item-<name>-before.png/.xml (the menu
# open, before the tap), item-<name>-after.png/.xml (2 s after it), item-<name>-activities.txt,
# item-<name>-windows.txt, item-<name>-notifications.txt, item-<name>.logcat.txt and
# item-<name>.crash.txt; per setting settings-<setting>.xml and the setup folder
# setup-<setting>/; and a DONE or FAIL line per mode in bubble-menu.txt (DONE: every item has a
# works or misbehaves row, whatever the outcomes). hidden writes hidden-<setting>-menu.png/.xml
# and PASS or FAIL.
#
# Usage: maestro/browsing/bubble-menu-check.sh <serial> <evidence-dir> <mode>
#        (run from android-app/)
set -euo pipefail

USAGE="usage: bubble-menu-check.sh <serial> <evidence-dir> <full-window|item <name>|audit-menu|audit-bar|audit-bar-off|audit-action|hidden>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
ITEM_NAME="${4:-}"
case "$MODE" in full-window|item|audit-menu|audit-bar|audit-bar-off|audit-action|hidden) ;; *) echo "$USAGE" >&2; exit 2 ;; esac
source "$(dirname "$0")/../webheads/lib.sh"
BM_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
FIXTURES="$BM_HERE/../reader/fixtures"
SEED_FILE="$BM_HERE/../seed/bubble-menu-audit.xml"
TABLE="$BM_HERE/../../lynket/src/test/resources/bubble-menu-audit.tsv"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="bubble-menu-$MODE"
CHECK_NAME="bubble menu $MODE"
: > "$OUT/$LOG_NAME.txt"

PORT=""
SERVER_PID=""
RESULT=FAIL

cleanup() {
  case "$MODE" in full-window|hidden) ;; *) [ "$RESULT" = PASS ] && RESULT=DONE ;; esac
  echo "$MODE${ITEM_NAME:+ $ITEM_NAME} $RESULT" >> "$OUT/bubble-menu.txt"
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# The fixture server (the same shape as maestro/browsing/link-open-timing.sh).
start_server() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$FIXTURES" > "$OUT/server-$MODE.log" 2>&1 &
  SERVER_PID=$!
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  local _
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/')" 2>/dev/null && break
    sleep 0.5
  done
  log "fixture server on port $PORT (adb reverse tcp:$PORT)"
}

# ---------------------------------------------------------------------------------------------
# UI helpers (copied from maestro/browsing/error-page-check.sh)

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/bubble-menu-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/bubble-menu-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/bubble-menu-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/bubble-menu-ui.xml | tr -d '\r'; return 0; }
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
top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
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
    # shellcheck disable=SC2086
    a shell input tap $xy
    for _ in $(seq 1 16); do
      top_activity | grep -q "EmbeddableWebViewActivity" && return 0
      sleep 0.25
    done
  done
  return 1
}

# ---------------------------------------------------------------------------------------------
# Checks

capture() {
  ui_dump > "$OUT/$1.xml"
  shot "$1"
}

# The text of the node with resource id $1 in the dump on stdin.
text_of() {
  tr '>' '\n' | grep -F "resource-id=\"$1\"" | head -1 | sed -nE 's/.* text="([^"]*)".*/\1/p'
}

# The notification ids of the app's bubbles, one per line, from a saved notification dump $1.
bubble_ids() {
  bubble_flag_lines "$1" | grep -oE '[[:space:]]id=-?[0-9]+' | tr -d ' ' | sort -u
}

notifications() {
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/$1"
}

check_full_window() {
  start_server
  local stamp first second
  stamp="$(date +%s)"
  first="${FULL_URL:-http://localhost:$PORT/article.html?a=$stamp}"
  second="http://localhost:$PORT/article.html?b=$stamp"
  a logcat -c

  # (a) Two pages as bubbles. Bubbles of an earlier run (the setup leaves one) stay open and are
  # counted: the checks below compare with the count before the tap.
  notifications full-window-start-notifications.txt
  local earlier
  earlier="$(bubble_ids "$OUT/full-window-start-notifications.txt" | grep -c . || true)"
  log "bubbles already open: $earlier"
  open_link "$first"
  sleep 3
  open_link "$second"
  local _ count=0
  for _ in $(seq 1 15); do
    sleep 2
    notifications full-window-before-notifications.txt
    count="$(bubble_ids "$OUT/full-window-before-notifications.txt" | grep -c . || true)"
    [ "$count" -ge $((earlier + 2)) ] && break
  done
  log "bubbles before: $count"
  [ "$count" -ge $((earlier + 2)) ] || fail "two bubbles did not post (found $count, $earlier before)"
  local before; before="$(bubble_ids "$OUT/full-window-before-notifications.txt")"

  # (b) The expanded page and its count.
  expand_bubble || fail "no bubble to expand"
  wait_for 'resource-id="webview-count"' || fail "the expanded page shows no count"
  local dump closed_address
  dump="$(ui_dump)"
  log "count: $(text_of webview-count <<< "$dump")"
  closed_address="$(text_of webview-address <<< "$dump")"
  log "expanded page: $closed_address"

  # (c) Open in full window is the first item of the menu. The menu is a window of its own; when
  # its nodes carry no resource ids, its items are found by their labels instead.
  tap_on 'resource-id="browsing-overflow"' || fail "no menu button"
  wait_for 'text="Open in full window"' || fail "no Open in full window item"
  capture full-window-menu
  local first_item item_ref='resource-id="menu-menu_open_in_full_window"'
  if grep -qF 'resource-id="browsing-overflow-menu"' "$OUT/full-window-menu.xml"; then
    # The first item node after the menu's own node (the top bar's icon items come before it).
    first_item="$(tr '>' '\n' < "$OUT/full-window-menu.xml" | sed -n '/resource-id="browsing-overflow-menu"/,$p' |
      grep -oE 'resource-id="menu-[^"]*"' | head -1 | sed -E 's/resource-id="menu-(.*)"/\1/')"
  else
    item_ref='text="Open in full window"'
    # The highest of the menu's labels on screen.
    first_item="$(tr '>' '\n' < "$OUT/full-window-menu.xml" |
      grep -E 'text="(Open in full window|Copy link|Open with…|Share|History|Add to Home Screen|Tabs|Article mode)"' |
      sed -nE 's/.* text="([^"]*)".*bounds="\[[0-9]+,([0-9]+)\].*/\2\t\1/p' | sort -n | head -1 | cut -f2)"
  fi
  log "first menu item: $first_item"
  case "$first_item" in menu_open_in_full_window|"Open in full window") ;; *) fail "the first menu item is $first_item" ;; esac

  # (d) The tap: a full window in a task of its own.
  tap_on "$item_ref" || fail "could not tap Open in full window"
  local top=""
  for _ in $(seq 1 40); do
    top="$(top_activity)"
    case "$top" in *EmbeddableWebViewActivity*) ;; *WebViewActivity*) break ;; esac
    sleep 0.5
  done
  log "front activity: $top"
  case "$top" in *EmbeddableWebViewActivity*|"") fail "no full window in front" ;; *WebViewActivity*) ;; *) fail "the front activity is $top" ;; esac
  a shell dumpsys activity activities | tr -d '\r' > "$OUT/full-window-activities.txt"
  # The task that holds the full window, read from its "* Task{... #<id> ..." header.
  local task_block
  task_block="$(awk '/^ *\* Task\{/ { block = "" } { block = block "\n" $0 } /WebViewActivity/ && !/EmbeddableWebViewActivity/ && !found { found = 1; out = block } END { print out }' "$OUT/full-window-activities.txt")"
  local task_line; task_line="$(grep -m1 -E '\* Task\{' <<< "$task_block" || true)"
  log "full window task: $task_line"
  [ -n "$task_line" ] || fail "no task holds the full window"
  grep -q "EmbeddableWebViewActivity" <<< "$task_block" && fail "the full window shares a task with a bubble page"
  local mode_line; mode_line="$(grep -m1 -oE 'mode=fullscreen|windowingMode=1|mode=[a-z-]+|windowingMode=[0-9]+' <<< "$task_block" || true)"
  log "full window windowing mode: ${mode_line:-unknown}"
  case "$mode_line" in mode=fullscreen|windowingMode=1) ;; *) fail "the full window is not fullscreen (${mode_line:-unknown})" ;; esac

  # (e) The same address, no count.
  wait_for 'resource-id="webview-address"' || wait_for 'resource-id="webview-title"' || fail "the full window shows no page"
  dump="$(ui_dump)"
  local shown; shown="$(text_of webview-address <<< "$dump")"
  log "full window address: $shown"
  [ -z "$closed_address" ] || [ "$shown" = "$closed_address" ] || fail "the full window shows $shown, not $closed_address"
  grep -qF 'resource-id="webview-count"' <<< "$dump" && fail "the full window shows a count"
  capture full-window

  # (f) One bubble fewer than before the tap, and every bubble left was open before it.
  local after="" left=0 id
  for _ in $(seq 1 20); do
    notifications full-window-after-notifications.txt
    after="$(bubble_ids "$OUT/full-window-after-notifications.txt")"
    left="$(grep -c . <<< "$after" || true)"
    [ "$left" = $((count - 1)) ] && break
    sleep 0.5
  done
  log "bubbles after: $(tr '\n' ' ' <<< "$after")($left of $count before)"
  [ "$left" = $((count - 1)) ] || fail "not one bubble fewer ($left left of $count)"
  while read -r id; do
    [ -z "$id" ] || grep -qxF "$id" <<< "$before" || fail "the bubble $id was not open before"
  done <<< "$after"

  # (g) A page left in the stack: with one bubble left it shows no count, with more its count
  # names the bubbles left.
  a shell input keyevent KEYCODE_HOME
  sleep 1
  expand_bubble || fail "no bubble left to expand"
  wait_for 'resource-id="webview-address"' || wait_for 'resource-id="webview-title"' || fail "the other bubble shows no page"
  sleep 2
  dump="$(ui_dump)"
  local other; other="$(text_of webview-address <<< "$dump")"
  log "other page: $other"
  [ -z "$closed_address" ] || [ "$other" != "$closed_address" ] || fail "the bubble left shows the closed page"
  local other_count; other_count="$(text_of webview-count <<< "$dump")"
  log "other page count: ${other_count:-none}"
  if [ "$left" = 1 ]; then
    [ -z "$other_count" ] || fail "the one bubble left shows a count ($other_count)"
  else
    case "$other_count" in *" of $left") ;; *) fail "the count of a bubble left is ${other_count:-none}, not of $left" ;; esac
  fi
  capture full-window-other
  a shell input keyevent KEYCODE_HOME

  # (h) No crash.
  app_logcat full-window
  grep -q "$PKG" "$OUT/full-window.crash.txt" && fail "the app crashed (full-window.crash.txt)"
  RESULT=PASS
  log "PASS"
}

# ---------------------------------------------------------------------------------------------
# The menu audit

# The first activity of another app that takes the intent "$@", as package/activity; empty when
# the device has none. $PREFER names packages to pick first, in order.
other_app_for() {
  local found pkg
  found="$(a shell cmd package query-activities --brief "$@" | tr -d '\r' |
    grep -oE '^ *[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$' | tr -d ' ' | grep -v "^$PKG/" || true)"
  for pkg in ${PREFER:-}; do
    grep -m1 "^$pkg/" <<< "$found" && return 0
  done
  head -1 <<< "$found"
}

BROWSER=""
SHARE_APP=""
SETTING=""

# Seeds the settings of one audit setting and sets up native bubbles. $1 = bar-on or bar-off,
# $2 = the preferred action of the menu button (1 secondary browser, 2 favourite share app,
# 3 Share), $3 = the setting's name in the table (bar-on, bar-off, action=browser, ...).
audit_setup() {
  local bar="$1" action="$2" bar_value=true
  SETTING="$3"
  [ "$bar" = bar-off ] && bar_value=false
  BROWSER="$(other_app_for -a android.intent.action.VIEW -d https://example.com)"
  SHARE_APP="$(PREFER="com.google.android.apps.messaging com.google.android.gm" other_app_for -a android.intent.action.SEND -t text/plain)"
  log "setting $SETTING: bottom bar $bar_value, preferred action $action, browser ${BROWSER:-none}, share app ${SHARE_APP:-none}"
  sed -e "s|@BOTTOM_BAR@|$bar_value|" -e "s|@ACTION@|$action|" -e "s|@BROWSER@|$BROWSER|" -e "s|@FAV_SHARE@|$SHARE_APP|" \
    "$SEED_FILE" > "$OUT/settings-$SETTING.xml"
  seed_settings "$OUT/settings-$SETTING.xml"
  # The setup's own last test can fail on another app's line after the bubble posted, so the
  # bubble itself is the pass.
  bash "$BM_HERE/../webheads/webheads-check.sh" "$SERIAL" "$OUT/setup-$SETTING" bubble > "$OUT/setup-$SETTING.log" 2>&1 || true
  notifications "setup-$SETTING-notifications.txt"
  [ -n "$(bubble_flag_lines "$OUT/setup-$SETTING-notifications.txt")" ] ||
    fail "the native bubble setup posted no bubble (see setup-$SETTING.log)"
  local stored
  stored="$(stored_setting bottombar_enabled_preference "settings-$SETTING-stored.tsv")"
  [ "$stored" = "$bar_value" ] || fail "the stored bottom bar setting is ${stored:-none}, not $bar_value"
  stored="$(awk -F'\t' '$1 == "preferred_action_preference" { print $3 }' "$OUT/settings-$SETTING-stored.tsv")"
  [ "$stored" = "$action" ] || fail "the stored preferred action is ${stored:-none}, not $action"
  log "setting $SETTING stored"
}

case "$MODE" in
  full-window) check_full_window ;;
esac
