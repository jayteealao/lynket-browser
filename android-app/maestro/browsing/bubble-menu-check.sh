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
# The site colour mode seeds its own settings too (maestro/seed/bubble-site-colour.xml: the toolbar
# colour follows the site; this clears the app's data) and runs the native bubble setup itself:
#
#   site-colour    a first bubble of fixtures/site-colour.html (theme colour #0B6E4F) puts the
#                  site's colour in the app's colour cache and stays open and folded, keeping the
#                  app's process (the cache lives in it; closing a page ends it); then a page of the same host that
#                  maestro/webheads/slow-page-server.py holds for HOLD_S seconds (40 by default)
#                  opens as a bubble and is expanded: while the page and its site details are still
#                  held, so only the bubble's own intent can have coloured it, the top bar and the
#                  bottom bar are sampled on a raw screenshot and must be near #0B6E4F; a held page
#                  of another host (127.0.0.1, no colour in the cache) must show both bars near the
#                  app colour #3F51B5
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
# full-window.crash.txt, and a PASS or FAIL line in bubble-menu.txt. site-colour writes
# settings-site-colour.xml and settings-site-colour-stored.tsv, the setup folder setup-site-colour/,
# site-colour.png/.xml/.raw (the expanded bubble of the site with a colour), site-colour-none.png/
# .xml/.raw (the host with no colour), the bar samples and the cache line in
# bubble-menu-site-colour.txt, site-colour-server.log (the held pages' start and end lines),
# site-colour.logcat.txt and site-colour.crash.txt, and PASS or FAIL in bubble-menu.txt. On exit it stops the server
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

USAGE="usage: bubble-menu-check.sh <serial> <evidence-dir> <full-window|item <name>|audit-menu|audit-bar|audit-bar-off|audit-action|hidden|site-colour>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
ITEM_NAME="${4:-}"
case "$MODE" in full-window|item|audit-menu|audit-bar|audit-bar-off|audit-action|hidden|site-colour) ;; *) echo "$USAGE" >&2; exit 2 ;; esac
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
PAGE_PORT=""
PAGE_SERVER_PID=""
RESULT=FAIL

cleanup() {
  case "$MODE" in full-window|hidden|site-colour) ;; *) [ "$RESULT" = PASS ] && RESULT=DONE ;; esac
  echo "$MODE${ITEM_NAME:+ $ITEM_NAME} $RESULT" >> "$OUT/bubble-menu.txt"
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
  [ -n "$PAGE_PORT" ] && a reverse --remove "tcp:$PAGE_PORT" >/dev/null 2>&1 || true
  [ -n "$PAGE_SERVER_PID" ] && kill "$PAGE_SERVER_PID" >/dev/null 2>&1 || true
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
# The line of the resumed activity in front from an activities dump on stdin: topResumedActivity
# from Android 12 on, the top-level ResumedActivity on Android 11.
resumed_line() {
  awk '/topResumedActivity/ && !top { top = $0 } /^ *ResumedActivity:/ && !res { res = $0 } END { print (top != "" ? top : res) }'
}
top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | resumed_line | awk '{ for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; break } }'
}

# The shell's bubble stack position as a tap point "X Y", empty while no bubble shows. Android 12
# and later report it in the WMShell dump, Android 11 in SystemUI's own dump.
bubble_point() {
  local xy
  xy="$(stack_point WMShell)"
  [ -n "$xy" ] || xy="$(stack_point)"
  echo "$xy"
}
stack_point() {
  a shell dumpsys activity service com.android.systemui/.SystemUIService "$@" | tr -d '\r' |
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
  # A tap while the page is still starting in an expanded bubble would fold it again, so each tap
  # waits up to 10 s for the page (a cold start on Android 11 takes several seconds).
  for _ in 1 2 3; do
    # shellcheck disable=SC2086
    a shell input tap $xy
    for _ in $(seq 1 40); do
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

# The items: name, resource entry name, where it is tapped (menu, bar or button) and the label
# that finds it in the menu window when that window has no resource ids.
ITEMS="copy-link menu_copy_link menu Copy link
open-with menu_open_with menu Open with…
share menu_share menu Share
share-with menu_share_with menu Share with
history menu_history menu History
add-to-home-screen menu_add_to_home_screen menu Add to Home Screen
menu-tabs tabs menu Tabs
menu-article bottom_bar_article_view menu Article mode
button menu_action_button button Menu button
bar-new-tab bottom_bar_open_in_new_tab bar New tab
bar-share bottom_bar_share bar Share
bar-tabs bottom_bar_tabs bar Tabs
bar-minimize bottom_bar_minimize_tab bar Minimize
bar-article bottom_bar_article_view bar Article mode"
item_names() { cut -d' ' -f1 <<< "$ITEMS" | tr '\n' ' '; }
item_line() { awk -v n="$1" '$1 == n' <<< "$ITEMS"; }

# Full app screens: one of them inside the bubble's small window misbehaves. The reader of the
# same page (ArticleActivity), translucent dialogs and the system share sheet are not full app
# screens.
FULL_SCREENS='[./](HistoryActivity|TabsActivity|MainActivity|CustomTabActivity|WebViewActivity)$'
# Packages a clean-up never stops.
SYSTEM_PACKAGES='^(android|com\.android\.systemui|com\.android\.intentresolver|.*launcher.*)$'

# The resumed activity in front, "package/class t<task>", from a saved activities dump $1.
front_of() {
  resumed_line < "$1" | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+ t-?[0-9]+' | head -1 || true
}
# The windowing mode ("multi-window", "fullscreen", ...) of task $2 in the activities dump $1.
task_mode() {
  grep -m1 -E "\* Task\{[0-9a-f]+ #$2 " "$1" | grep -oE 'mode=[a-z-]+' | head -1 | cut -d= -f2 || true
}
# The titles of the windows on screen, one per line, from a saved window dump $1.
window_titles() {
  grep -oE 'Window\{[0-9a-f]+ u0 [^}]+\}' "$1" | sed -E 's/Window\{[0-9a-f]+ u0 //; s/\}$//' | sort -u || true
}
# The keys of the app's notifications, from a saved notification dump $1.
app_notification_keys() {
  grep -F "pkg=$PKG" "$1" | grep -oE 'key=[^ ]+' | sort -u || true
}
short_name() { sed -E 's/ t-?[0-9]+$//; s/.*[./]//' <<< "$1"; }

ANDROID_VERSION=""
DEVICE_NAME=""
ROWS_OK=1

# One row of the table: $1 item label, $2 id, $3 where, $4 outcome, $5 reason, $6 evidence.
audit_row() {
  [ -s "$OUT/bubble-menu-audit.tsv" ] ||
    printf 'item\tid\twhere\tsetting\tandroid\tdevice\toutcome\treason\tevidence\n' > "$OUT/bubble-menu-audit.tsv"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$SETTING" "$ANDROID_VERSION" "$DEVICE_NAME" "$4" "$5" "$6" \
    >> "$OUT/bubble-menu-audit.tsv"
  log "row: $1 ($2, $3, $SETTING): $4 - $5"
  case "$4" in works|misbehaves|not-tested) ;; *) ROWS_OK=0 ;; esac
}

# Is the page with the item's address in front, in the bubble's task? $1 = address tag, $2 = task.
page_in_front() {
  local front dump
  a shell dumpsys activity activities | tr -d '\r' > "$OUT/.front.txt"
  front="$(front_of "$OUT/.front.txt")"
  case "$front" in *EmbeddableWebViewActivity" t$2") ;; *) return 1 ;; esac
  dump="$(ui_dump)"
  grep -qF -- "$1" <<< "$(text_of webview-address <<< "$dump")"
}

# Opens the fixture page with address tag $2 as a bubble and expands it; sets bubble_id (the new
# bubble's notification id) or OPEN_PROBLEM.
open_bubble() {
  local name="$1" tag="$2" before_ids after_ids _
  OPEN_PROBLEM=""
  notifications "item-$name-start-notifications.txt"
  before_ids="$(bubble_ids "$OUT/item-$name-start-notifications.txt")"
  open_link "http://localhost:$PORT/article.html?$tag"
  for _ in $(seq 1 15); do
    sleep 2
    notifications "item-$name-open-notifications.txt"
    after_ids="$(bubble_ids "$OUT/item-$name-open-notifications.txt")"
    bubble_id="$(comm -13 <(echo "$before_ids") <(echo "$after_ids") | grep . | head -1 || true)"
    [ -n "$bubble_id" ] && break
  done
  [ -n "$bubble_id" ] || { OPEN_PROBLEM="the page did not post a bubble"; return 1; }
  expand_bubble || { OPEN_PROBLEM="the bubble did not expand"; return 1; }
  wait_for "$tag" || { OPEN_PROBLEM="the expanded bubble does not show the page"; return 1; }
}

# Taps one item in a page in an expanded bubble, then judges what happened. $1 = item name,
# $2 = label for the table (the item's label by default).
audit_item() {
  local name="$1" line id where label
  line="$(item_line "$name")"
  [ -n "$line" ] || { echo "unknown item $name; items: $(item_names)" >&2; exit 2; }
  read -r _ id where label <<< "$line"
  local find="$label"
  label="${2:-$label}"
  local ev; ev="$(basename "$OUT")/item-$name"
  [ -n "$PORT" ] || start_server
  [ -n "$ANDROID_VERSION" ] || ANDROID_VERSION="$(a shell getprop ro.build.version.release | tr -d '\r')"
  [ -n "$DEVICE_NAME" ] || DEVICE_NAME="$(a shell getprop ro.product.model | tr -d '\r') ($(a emu avd name 2>/dev/null | tr -d '\r' | head -1))"
  log "--- $name ($id, $where)"
  local tag; tag="item=$name-$(date +%s)"
  a logcat -b all -c || true

  # The page as a bubble, expanded. Android 11's shell sometimes draws no bubble for a flagged
  # notification posted soon after the app was stopped: then the app is stopped again and the
  # page opened once more.
  local bubble_id="" attempt
  for attempt in 1 2; do
    if open_bubble "$name" "$tag-$attempt"; then break; fi
    capture "item-$name-not-expanded-$attempt"
    a shell dumpsys activity activities | tr -d '\r' > "$OUT/item-$name-not-expanded-$attempt-activities.txt"
    a shell dumpsys activity service com.android.systemui/.SystemUIService | tr -d '\r' > "$OUT/item-$name-not-expanded-$attempt-systemui.txt"
    log "attempt $attempt: ${OPEN_PROBLEM:-}"
    bubble_id=""
    a shell input keyevent KEYCODE_HOME
    a shell am force-stop "$PKG"
    sleep 3
  done
  [ "$attempt" = 2 ] && [ -z "$bubble_id" ] && { audit_row "$label" "$id" "$where" undecided "$OPEN_PROBLEM" "$ev-not-expanded-2.png"; return 1; }
  tag="$tag-$attempt"
  wait_for "$tag" || { audit_row "$label" "$id" "$where" undecided "the expanded bubble does not show the page" "$ev-open-notifications.txt"; return 1; }
  a shell dumpsys activity activities | tr -d '\r' > "$OUT/item-$name-start-activities.txt"
  local start_front task
  start_front="$(front_of "$OUT/item-$name-start-activities.txt")"
  task="$(grep -oE 't-?[0-9]+$' <<< "$start_front" | tr -d t)"
  log "bubble $bubble_id, page task $task ($(task_mode "$OUT/item-$name-start-activities.txt" "$task"))"

  # The tap.
  local ref
  case "$where" in
    menu)
      tap_on 'resource-id="browsing-overflow"' || { audit_row "$label" "$id" "$where" undecided "no menu button" "$ev-start-activities.txt"; return 1; }
      wait_for "text=\"$find" || { capture "item-$name-before"; audit_row "$label" "$id" "$where" undecided "the menu has no $find item" "$ev-before.xml"; return 1; }
      ;;
  esac
  capture "item-$name-before"
  a shell dumpsys window windows | tr -d '\r' > "$OUT/item-$name-before-windows.txt"
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/item-$name-before-notifications.txt"
  if grep -qF "resource-id=\"menu-$id\"" "$OUT/item-$name-before.xml"; then
    ref="resource-id=\"menu-$id\""
  elif [ "$where" = menu ] && [ "$name" = share-with ]; then
    ref="text=\"$find"
  elif [ "$where" = menu ]; then
    ref="text=\"$find\""
  else
    audit_row "$label" "$id" "$where" undecided "no menu-$id on screen" "$ev-before.xml"; return 1
  fi
  # The topmost node that matches: the menu window opens at the top, the bottom bar has a Share too.
  local xy
  xy="$(tr '>' '\n' < "$OUT/item-$name-before.xml" | grep -F -- "$ref" |
    sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\2 \1 \3 \4/p' | sort -n | head -1 |
    awk 'NF == 4 { print int(($2 + $3) / 2), int(($1 + $4) / 2) }')"
  [ -n "$xy" ] || { audit_row "$label" "$id" "$where" undecided "nothing matching $ref on screen" "$ev-before.xml"; return 1; }
  log "tap $ref at $xy"
  # shellcheck disable=SC2086
  a shell input tap $xy
  local tapped=$SECONDS

  # Ten seconds of the device's state after the tap.
  local front fronts="" added="" seen_inside="" foreign="" titles
  window_titles "$OUT/item-$name-before-windows.txt" > "$OUT/.before-titles.txt"
  local shot_taken=""
  while [ $((SECONDS - tapped)) -lt 10 ]; do
    a shell dumpsys activity activities | tr -d '\r' > "$OUT/.poll-activities.txt"
    a shell dumpsys window windows | tr -d '\r' > "$OUT/.poll-windows.txt"
    front="$(front_of "$OUT/.poll-activities.txt")"
    if [ -n "$front" ]; then
      grep -qxF -- "$front" <<< "$fronts" || fronts="$fronts${fronts:+$'\n'}$front"
      if [ "$front" != "$start_front" ]; then
        cp "$OUT/.poll-activities.txt" "$OUT/item-$name-activities.txt"
        cp "$OUT/.poll-windows.txt" "$OUT/item-$name-windows.txt"
      fi
      local fpkg="${front%%/*}" ftask; ftask="$(grep -oE 't-?[0-9]+$' <<< "$front" | tr -d t)"
      if [ "$fpkg" = "$PKG" ] && grep -qE "$FULL_SCREENS" <<< "${front% t*}"; then
        if [ "$ftask" = "$task" ] || [ "$(task_mode "$OUT/.poll-activities.txt" "$ftask")" = multi-window ]; then
          seen_inside="$(short_name "$front")"
          cp "$OUT/.poll-activities.txt" "$OUT/item-$name-activities.txt"
        fi
      fi
      [ "$fpkg" = "$PKG" ] || grep -qE "$SYSTEM_PACKAGES" <<< "$fpkg" || foreign="$fpkg"
    fi
    titles="$(window_titles "$OUT/.poll-windows.txt" | comm -13 "$OUT/.before-titles.txt" - || true)"
    if [ -n "$titles" ]; then
      while IFS= read -r t; do grep -qxF -- "$t" <<< "$added" || added="$added${added:+$'\n'}$t"; done <<< "$titles"
      [ -s "$OUT/item-$name-windows.txt" ] || cp "$OUT/.poll-windows.txt" "$OUT/item-$name-windows.txt"
    fi
    if [ -z "$shot_taken" ] && [ $((SECONDS - tapped)) -ge 2 ]; then capture "item-$name-after"; shot_taken=1; fi
  done
  [ -n "$shot_taken" ] || capture "item-$name-after"
  [ -s "$OUT/item-$name-activities.txt" ] || cp "$OUT/.poll-activities.txt" "$OUT/item-$name-activities.txt"
  [ -s "$OUT/item-$name-windows.txt" ] || cp "$OUT/.poll-windows.txt" "$OUT/item-$name-windows.txt"
  notifications "item-$name-notifications.txt"
  log "fronts after the tap: $(tr '\n' ';' <<< "$fronts")"
  log "windows added: $(tr '\n' ';' <<< "$added")"

  # The way back to the page.
  local way="" gone=""
  bubble_ids "$OUT/item-$name-notifications.txt" | grep -qxF -- "$bubble_id" || gone=1
  if page_in_front "$tag" "$task"; then
    way="the page stays in front in the bubble"
  else
    for _ in 1 2; do
      a shell input keyevent KEYCODE_BACK
      sleep 2
      if page_in_front "$tag" "$task"; then way="Back returns to the page in the bubble"; break; fi
    done
    if [ -z "$way" ]; then
      a shell input keyevent KEYCODE_HOME
      sleep 1
      if expand_bubble && wait_for "$tag"; then way="Home and a tap on the bubble bring the page back"; fi
    fi
  fi
  log "way back: ${way:-none}${gone:+ (the bubble is gone)}"

  app_logcat "item-$name"
  local app_lines; app_lines="$(app_notification_keys "$OUT/item-$name-before-notifications.txt")"
  local changed=""
  [ "$app_lines" = "$(app_notification_keys "$OUT/item-$name-notifications.txt")" ] || changed=1

  # The judge, first match wins.
  local effect="" outcome reason
  local others; others="$(grep -vxF -- "$start_front" <<< "$fronts" || true)"
  if [ -n "$others" ]; then
    local o d=""
    while IFS= read -r o; do
      [ -n "$o" ] || continue
      local ot; ot="$(grep -oE 't-?[0-9]+$' <<< "$o" | tr -d t)"
      local place="in a $(task_mode "$OUT/item-$name-activities.txt" "$ot") task of its own"
      [ "$ot" = "$task" ] && place="inside the bubble"
      d="$d${d:+, }$(short_name "$o") of ${o%%/*} $place"
    done <<< "$others"
    effect="opens $d"
  fi
  [ -z "$added" ] || effect="$effect${effect:+; }shows the window $(tr '\n' ',' <<< "$added" | sed 's/,$//; s/,/, /g')"
  [ -z "$changed" ] || effect="$effect${effect:+; }changes the app's notifications"

  if [ ! -s "$OUT/item-$name-activities.txt" ] || [ -z "$start_front" ] || [ ! -s "$OUT/item-$name-notifications.txt" ]; then
    outcome=undecided; reason="the device state could not be read after the tap"
  elif grep -qF "$PKG" "$OUT/item-$name.crash.txt" || grep -qiE "Application Not Responding: $PKG" <<< "$added"; then
    outcome=misbehaves; reason="the app crashed or stopped responding after the tap"
  elif [ -n "$seen_inside" ]; then
    outcome=misbehaves; reason="opens a full app screen ($seen_inside) inside the bubble's small window; ${way:-no way back to the page}"
  elif [ -z "$way" ] || [ -n "$gone" ]; then
    outcome=misbehaves; reason="${effect:-nothing shows}; no way back to the page${gone:+ (its bubble is gone)}"
  elif [ -z "$effect" ]; then
    outcome=misbehaves; reason="nothing happens: no screen, window or notification change in 10 s"
  else
    outcome=works; reason="$effect; $way"
  fi
  audit_row "$label" "$id" "$where" "$outcome" "$reason" "$ev-{before,after}.png, $ev-activities.txt, $ev-windows.txt, $ev-notifications.txt"
  LAST_FOREIGN="$foreign"
  [ "$outcome" != undecided ]
}

# After each item: Home, stop the other app the item opened, then remove the app's bubbles, so the
# next item starts from no bubble and no stack grows past the shell's limit. From Android 12 on,
# stopping the app removes them. Android 11's shell then sometimes draws no bubble for the next
# page, so there each bubble is closed with the page's own close button (stopping the app is the
# fallback); from Android 12 on that button cannot be reached when a page left for a task of its
# own leaves the stack drawn where its expanded row was.
clean_up_item() {
  local _ ids
  a shell input keyevent KEYCODE_HOME
  [ -z "${LAST_FOREIGN:-}" ] || a shell am force-stop "$LAST_FOREIGN" || true
  LAST_FOREIGN=""
  if [ "$(a shell getprop ro.build.version.sdk | tr -d '\r')" -le 30 ]; then
    for _ in 1 2 3; do
      notifications .clean-up-notifications.txt
      ids="$(bubble_ids "$OUT/.clean-up-notifications.txt" || true)"
      [ -n "$ids" ] || break
      expand_bubble && tap_on 'resource-id="webview-close"' || break
      sleep 2
    done
  fi
  notifications .clean-up-notifications.txt
  if [ -n "$(bubble_ids "$OUT/.clean-up-notifications.txt" || true)" ]; then
    a shell input keyevent KEYCODE_HOME
    a shell am force-stop "$PKG" || true
  fi
  a shell input keyevent KEYCODE_HOME
  sleep 2
}

ITEMS_OK=1
run_items() {  # $@ = item names, or name=label pairs
  local n
  for n in "$@"; do
    if [[ "$n" == *=* ]]; then audit_item "${n%%=*}" "${n#*=}" || ITEMS_OK=0; else audit_item "$n" || ITEMS_OK=0; fi
    clean_up_item
  done
}
not_tested() {  # $1 item name, $2 label, $3 reason
  local line id where
  line="$(item_line "$1")"; read -r _ id where _ <<< "$line"
  [ -n "$ANDROID_VERSION" ] || ANDROID_VERSION="$(a shell getprop ro.build.version.release | tr -d '\r')"
  [ -n "$DEVICE_NAME" ] || DEVICE_NAME="$(a shell getprop ro.product.model | tr -d '\r') ($(a emu avd name 2>/dev/null | tr -d '\r' | head -1))"
  audit_row "$2" "$id" "$where" not-tested "$3" "$(basename "$OUT")/settings-$SETTING.xml"
}
audit_finish() {
  [ "$ITEMS_OK" = 1 ] && [ "$ROWS_OK" = 1 ] || fail "an item has no works or misbehaves row"
  RESULT=PASS
  log "every item has a row"
}

audit_menu() {
  audit_setup bar-on 3 bar-on
  run_items copy-link open-with share history add-to-home-screen
  audit_finish
}
audit_bar() {
  audit_setup bar-on 3 bar-on
  run_items bar-new-tab bar-share bar-tabs bar-minimize bar-article
  audit_finish
}
audit_bar_off() {
  audit_setup bar-off 3 bar-off
  run_items menu-tabs menu-article
  audit_finish
}
audit_action() {
  audit_setup bar-on 2 action=fav-share
  if [ -n "$SHARE_APP" ]; then
    run_items "share-with=Share with <app>" "button=Menu button (favourite share app)"
  else
    not_tested share-with "Share with <app>" "the device has no app that takes shared text"
    not_tested button "Menu button (favourite share app)" "the device has no app that takes shared text"
  fi
  audit_setup bar-on 1 action=browser
  if [ -n "$BROWSER" ]; then
    run_items "button=Menu button (secondary browser)"
  else
    not_tested button "Menu button (secondary browser)" "the device has no other browser"
  fi
  audit_setup bar-on 3 action=share
  run_items "button=Menu button (Share)"
  audit_finish
}
audit_one() {
  audit_setup bar-on 3 bar-on
  run_items "$ITEM_NAME"
  audit_finish
}

# The rebuilt menu and bar of a page in a bubble against the committed table, for one setting
# ($1 bar-on or bar-off): no item with a misbehaves row shows (by its id or its label), every item
# that works with that setting shows, and Open in full window is the first item of the menu.
hidden_check() {
  local setting="$1" bar=bar-on problems=""
  [ "$setting" = bar-off ] && bar=bar-off
  audit_setup "$bar" 3 "$setting"
  [ -n "$PORT" ] || start_server
  local bubble_id="" tag; tag="hidden=$setting-$(date +%s)"
  open_bubble "hidden-$setting" "$tag" || fail "$OPEN_PROBLEM"
  sleep 2
  capture "hidden-$setting-page"
  tap_on 'resource-id="browsing-overflow"' || fail "no menu button"
  wait_for 'text="Open in full window"' || fail "no Open in full window item"
  capture "hidden-$setting-menu"
  local both; both="$(cat "$OUT/hidden-$setting-page.xml" "$OUT/hidden-$setting-menu.xml")"

  local id label where
  while IFS=$'\t' read -r label id where; do
    [ -n "$id" ] || continue
    grep -qF "resource-id=\"menu-$id\"" <<< "$both" && problems="$problems; $id shows"
    grep -qF "text=\"$label\"" <<< "$both" && problems="$problems; $label shows"
  done < <(awk -F'\t' 'NR > 1 && $7 == "misbehaves" { print $1 "\t" $2 "\t" $3 }' "$TABLE" | sort -u)
  log "hidden ids: $(awk -F'\t' 'NR > 1 && $7 == "misbehaves" { print $2 }' "$TABLE" | sort -u | tr '\n' ' ')"

  while IFS=$'\t' read -r label id where; do
    [ -n "$id" ] || continue
    grep -qF "resource-id=\"menu-$id\"" <<< "$both" || grep -qF "text=\"$label\"" <<< "$both" ||
      problems="$problems; $label ($id) is missing"
  done < <(awk -F'\t' -v s="$setting" 'NR > 1 && $4 == s && $7 == "works" { print $1 "\t" $2 "\t" $3 }' "$TABLE" | sort -u)

  local first_item
  if grep -qF 'resource-id="browsing-overflow-menu"' "$OUT/hidden-$setting-menu.xml"; then
    first_item="$(tr '>' '\n' < "$OUT/hidden-$setting-menu.xml" | sed -n '/resource-id="browsing-overflow-menu"/,$p' |
      grep -oE 'resource-id="menu-[^"]*"' | head -1 | sed -E 's/resource-id="menu-(.*)"/\1/')"
  else
    first_item="$(tr '>' '\n' < "$OUT/hidden-$setting-menu.xml" |
      grep -E 'text="(Open in full window|Copy link|Open with…|Share|Share with [^"]*|History|Add to Home Screen|Tabs|Article mode)"' |
      sed -nE 's/.* text="([^"]*)".*bounds="\[[0-9]+,([0-9]+)\].*/\2\t\1/p' | sort -n | head -1 | cut -f2)"
  fi
  log "$setting: first menu item $first_item"
  case "$first_item" in menu_open_in_full_window|"Open in full window") ;; *) problems="$problems; the first menu item is $first_item" ;; esac
  a shell input keyevent KEYCODE_BACK
  clean_up_item
  [ -z "$problems" ] || fail "$setting:${problems}"
  log "$setting: no hidden item, every working item, Open in full window first"
}

check_hidden() {
  [ -f "$TABLE" ] || fail "no table at $TABLE"
  hidden_check bar-on
  hidden_check bar-off
  RESULT=PASS
  log "PASS"
}

case "$MODE" in
  item) [ -n "$(item_line "$ITEM_NAME")" ] || { echo "usage: bubble-menu-check.sh <serial> <evidence-dir> item <name>; items: $(item_names)" >&2; exit 2; } ;;
esac

# ---------------------------------------------------------------------------------------------
# The site colour of the bubble page

# The slow page server (copied from maestro/webheads/webheads-check.sh): /slow?s=<seconds>&id=<n>
# arrives after that many seconds, and its log names each page's start and end.
start_page_server() {
  PAGE_PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python "$BM_HERE/../webheads/slow-page-server.py" "$PAGE_PORT" > "$OUT/$MODE-server.log" 2>&1 &
  PAGE_SERVER_PID=$!
  a reverse "tcp:$PAGE_PORT" "tcp:$PAGE_PORT" >/dev/null
  local _
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PAGE_PORT/slow?s=0&id=0')" 2>/dev/null && break
    sleep 0.5
  done
  log "slow page server on port $PAGE_PORT (adb reverse tcp:$PAGE_PORT)"
}

# The colour "#RRGGBB" at x $2, y $3 of the raw screenshot $1 (adb exec-out screencap, no -p):
# width, height and format words, on Android 9 and later a colour space word, then RGBA pixels.
# The header size is the file size less the pixels, so it is not guessed.
bar_pixel() {
  python -c '
import struct, sys
data = open(sys.argv[1], "rb").read()
w, h = struct.unpack_from("<II", data, 0)
at = len(data) - w * h * 4 + (int(sys.argv[3]) * w + int(sys.argv[2])) * 4
print("#%02X%02X%02X" % tuple(data[at:at + 3]))
' "$1" "$2" "$3"
}

# Passes when each channel of the colours $1 and $2 ("#RRGGBB") differs by at most 8.
colour_near() {
  python -c '
import sys
a, b = (bytes.fromhex(c.lstrip("#")) for c in sys.argv[1:3])
sys.exit(0 if all(abs(x - y) <= 8 for x, y in zip(a, b)) else 1)
' "$1" "$2"
}

# The bounds "x1 y1 x2 y2" of the node with resource id $1 in the saved dump $2.
bounds_of() {
  tr '>' '\n' < "$2" | grep -F "resource-id=\"$1\"" | head -1 |
    sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p'
}

# Waits, at most 30 polls of 1 s, for at least $1 bubbles of the app in a fresh notification dump.
wait_bubbles() {
  local _
  for _ in $(seq 1 30); do
    notifications "$MODE-notifications.txt"
    [ "$(bubble_ids "$OUT/$MODE-notifications.txt" | grep -c . || true)" -ge "$1" ] && return 0
    sleep 1
  done
  return 1
}

# The address of the expanded page; a page with no title yet shows its address as the title and no
# address line.
shown_page() {
  local dump shown
  dump="$(ui_dump)"
  shown="$(text_of webview-address <<< "$dump" || true)"
  [ -n "$shown" ] || shown="$(text_of webview-title <<< "$dump" || true)"
  echo "$shown"
}

# The tap point "X Y" of the first bubble of the expanded row with $1 bubbles: the row of the
# bubbles and the overflow button is centred on the screen, one item every 63 dp, 70 dp from the top
# (measured on the API 34 emulator, 1080x2400 at 420 dpi).
newest_bubble_point() {
  local width density
  width="$(a shell wm size | tr -d '\r' | sed -nE 's/.*: ([0-9]+)x[0-9]+$/\1/p' | tail -1)"
  density="$(a shell wm density | tr -d '\r' | sed -nE 's/.*: ([0-9]+)$/\1/p' | tail -1)"
  awk -v w="$width" -v d="$density" -v n="$1" 'BEGIN { s = d / 160; print int(w / 2 - n * 63 * s / 2), int(70 * s) }'
}

# Opens the held page $1 (server id $2) as the bubble number $4, expands it while the server still
# holds it, and samples both bars into $3.png/.xml/.raw; sets SAMPLE_TOP and SAMPLE_BOTTOM. The
# earlier bubbles stay open and folded: closing the last page would end the app's process.
sample_held_page() {
  local url="$1" id="$2" name="$3" count="$4"
  a shell input keyevent KEYCODE_HOME
  open_link "$url"
  wait_bubbles "$count" || fail "no bubble for $url"
  expand_bubble || fail "no bubble to expand for $url"
  wait_for 'resource-id="webview-top-bar"' || fail "the expanded page of $url shows no top bar"
  local shown
  shown="$(shown_page)"
  log "$name: expanded page $shown"
  case "$shown" in
    *"id=$id"*) ;;
    *)
      # The stack opened on the bubble selected last; the newest bubble is first in the expanded row.
      local xy; xy="$(newest_bubble_point "$count")"
      log "$name: selecting the newest bubble at $xy"
      # shellcheck disable=SC2086
      a shell input tap $xy
      sleep 3
      wait_for 'resource-id="webview-top-bar"' || true
      shown="$(shown_page)"
      log "$name: expanded page $shown"
      ;;
  esac
  case "$shown" in *"id=$id"*) ;; *) fail "the expanded bubble shows ${shown:-no address}, not the held page $id" ;; esac
  grep -q "end id=$id\$" "$OUT/$MODE-server.log" && fail "the page $id finished before the bars were sampled"
  ui_dump > "$OUT/$name.xml"
  a exec-out screencap > "$OUT/$name.raw"
  shot "$name"
  local top bottom tx ty bx by
  read -r -a top <<< "$(bounds_of webview-top-bar "$OUT/$name.xml")"
  read -r -a bottom <<< "$(bounds_of browsing-bottom-bar "$OUT/$name.xml")"
  [ "${#top[@]}" = 4 ] || fail "no top bar bounds in $name.xml"
  [ "${#bottom[@]}" = 4 ] || fail "no bottom bar bounds in $name.xml"
  tx=$(( (top[0] + top[2]) / 2 )); ty=$(( top[3] - 3 ))
  bx=$(( (bottom[0] + bottom[2]) / 2 )); by=$(( bottom[1] + 3 ))
  SAMPLE_TOP="$(bar_pixel "$OUT/$name.raw" "$tx" "$ty")"
  SAMPLE_BOTTOM="$(bar_pixel "$OUT/$name.raw" "$bx" "$by")"
  log "$name: top bar $SAMPLE_TOP at $tx,$ty; bottom bar $SAMPLE_BOTTOM at $bx,$by"
  grep -q "end id=$id\$" "$OUT/$MODE-server.log" && fail "the page $id finished while the bars were sampled"
  log "$name: the page $id was still held when the bars were sampled"
}

check_site_colour() {
  local hold="${HOLD_S:-40}" site="#0B6E4F" primary="#3F51B5"
  # (a) The settings, the bubble setup (judged by its bubble: its own last test can fail on another
  # app's line after the bubble posted). The setup's bubble stays open and is folded with Home: the
  # colour cache lives in the app's process, and stopping the app with a bubble expanded leaves the
  # stack drawn where the shell no longer takes taps.
  cp "$BM_HERE/../seed/bubble-site-colour.xml" "$OUT/settings-site-colour.xml"
  # Clearing the app's data under an expanded stack leaves it drawn where it takes no taps.
  a shell input keyevent KEYCODE_HOME
  sleep 1
  seed_settings "$OUT/settings-site-colour.xml"
  bash "$BM_HERE/../webheads/webheads-check.sh" "$SERIAL" "$OUT/setup-site-colour" bubble > "$OUT/setup-site-colour.log" 2>&1 || true
  notifications setup-site-colour-notifications.txt
  [ -n "$(bubble_flag_lines "$OUT/setup-site-colour-notifications.txt")" ] ||
    fail "the native bubble setup posted no bubble (see setup-site-colour.log)"
  local stored
  stored="$(stored_setting dynamic_color settings-site-colour-stored.tsv)"
  [ "$stored" = true ] || fail "the stored dynamic colour setting is ${stored:-none}, not true"
  stored="$(awk -F'\t' '$1 == "dynamic_color_web" { print $3 }' "$OUT/settings-site-colour-stored.tsv")"
  [ "$stored" = true ] || fail "the stored setting based on web is ${stored:-none}, not true"
  a shell input keyevent KEYCODE_HOME
  local earlier
  earlier="$(bubble_ids "$OUT/setup-site-colour-notifications.txt" | grep -c . || true)"
  log "bubbles of the setup left open: $earlier"

  # (b) The warm-up: a bubble of the fixture page puts its colour in the cache. It stays open and
  # folded: closing a bubble's page removes its task, and Android then ends the app's process.
  FIXTURES="$BM_HERE/fixtures"
  start_server
  a logcat -c
  open_link "http://localhost:$PORT/site-colour.html?warm=$(date +%s)"
  local _ cached=""
  for _ in $(seq 1 30); do
    cached="$(a logcat -d | tr -d '\r' | grep -E 'Saved color -?[0-9]+ for host localhost' | head -1 || true)"
    [ -n "$cached" ] && break
    sleep 1
  done
  [ -n "$cached" ] || fail "the site colour of the fixture page did not reach the cache"
  log "cache: $cached"
  wait_bubbles $((earlier + 1)) || fail "no bubble for the fixture page"
  [ -n "$(a shell pidof "$PKG" | tr -d '\r' || true)" ] || fail "the app's process stopped after the warm-up, so its colour cache is gone"

  # (c) The same host, held: both bars in the site colour while nothing but the bubble's intent can
  # have coloured them.
  start_page_server
  sample_held_page "http://localhost:$PAGE_PORT/slow?s=$hold&id=1" 1 site-colour $((earlier + 2))
  colour_near "$SAMPLE_TOP" "$site" || fail "the top bar is $SAMPLE_TOP, not near $site"
  colour_near "$SAMPLE_BOTTOM" "$site" || fail "the bottom bar is $SAMPLE_BOTTOM, not near $site"

  # (d) Another host with no colour in the cache: both bars in the app colour.
  sample_held_page "http://127.0.0.1:$PAGE_PORT/slow?s=$hold&id=2" 2 site-colour-none $((earlier + 3))
  colour_near "$SAMPLE_TOP" "$primary" || fail "the top bar of a site with no colour is $SAMPLE_TOP, not near $primary"
  colour_near "$SAMPLE_BOTTOM" "$primary" || fail "the bottom bar of a site with no colour is $SAMPLE_BOTTOM, not near $primary"
  a shell input keyevent KEYCODE_HOME

  # (e) No crash.
  app_logcat site-colour
  grep -q "$PKG" "$OUT/site-colour.crash.txt" && fail "the app crashed (site-colour.crash.txt)"
  RESULT=PASS
  log "PASS"
}

case "$MODE" in
  full-window) check_full_window ;;
  item) audit_one ;;
  audit-menu) audit_menu ;;
  audit-bar) audit_bar ;;
  audit-bar-off) audit_bar_off ;;
  audit-action) audit_action ;;
  hidden) check_hidden ;;
  site-colour) check_site_colour ;;
esac
