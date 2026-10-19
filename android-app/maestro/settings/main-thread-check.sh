#!/usr/bin/env bash
# Device check for main-thread disk work on the debug build. Turns on StrictMode's disk checks
# (the debug-only strict-mode flag file) and drives the screens and paths that read or write the
# stores: the settings screens, per-app rules, a link in a Custom Tab and in the web view,
# History, the four Quick Settings tiles and a first start after the app data is cleared. Each
# section's MainThreadWait lines (a main thread waiting in blockingWait, with the caller's stack)
# and StrictMode lines go to <dir>/<section>.log, with the crash buffer in <section>.crash.txt;
# counts.tsv sums them per section and site.
#
# Usage: maestro/settings/main-thread-check.sh <serial> <evidence-dir>   (run from android-app/)
# APK=<path> picks the debug APK (default: the last assembleDebug output). The app is reinstalled,
# which clears its data on that device. Removes the flag file and restores the Custom Tab mode at
# the end.
set -euo pipefail

USAGE="usage: main-thread-check.sh <serial> <evidence-dir>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
MT_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
APK="${APK:-$MT_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk}"
APP=arun.com.chromer
HISTORY=$APP.history.HistoryActivity
TILES="$APP.bubbles.webheads.qs.WebHeadTile $APP.browsing.incognito.IncognitoTile $APP.browsing.amp.qs.AmpTile $APP.browsing.article.qs.ArticleTile"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=main-thread
CHECK_NAME="main-thread check"
: > "$OUT/main-thread.txt"

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/mt-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/mt-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/mt-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/mt-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

# Centres of every node matching $1 (a dump fragment), one "X Y" per line.
centres_of() {
  { ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

tap_on() {  # $1 = dump fragment; taps the first match, waiting up to 30 s for it
  local xy _
  for _ in $(seq 1 15); do
    xy="$(centres_of "$1" | head -1)"
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

flag_on() { a shell "run-as $PKG sh -c 'mkdir -p files/debug-faults && touch files/debug-faults/strict-mode'"; }
flag_off() { a shell "run-as $PKG rm -f files/debug-faults/strict-mode" >/dev/null 2>&1 || true; }

# Starts a section: a fresh process (StrictMode is set up in Application.onCreate) and an empty log.
begin() {  # $1 = section
  SECTION="$1"
  a shell am force-stop "$PKG"
  a logcat -c
  log "== $SECTION"
}

# Ends a section: the app's MainThreadWait and StrictMode lines, and the crash buffer.
end_section() {
  sleep 2
  a logcat -d -v threadtime -s MainThreadWait:W StrictMode:D | tr -d '\r' > "$OUT/$SECTION.log"
  a logcat -d -b crash | tr -d '\r' > "$OUT/$SECTION.crash.txt"
  log "$SECTION: $(grep -c 'Main thread waits:' "$OUT/$SECTION.log" || true) main-thread waits, $(grep -c 'StrictMode policy violation' "$OUT/$SECTION.log" || true) StrictMode violations"
  if grep -q "$PKG" "$OUT/$SECTION.crash.txt"; then log "$SECTION: the crash buffer holds an entry for $PKG"; CRASHED=1; fi
}

destination() {  # $1 = settings screen name (LynketKeys Destination)
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" "$1" >/dev/null
  sleep 4
}

# Taps the first $2 switches on screen twice each (on, then back), so each stored value returns.
toggle_switches() {  # $1 = screen label, $2 = count
  local n=0 xy
  while read -r xy; do
    [ -n "$xy" ] || continue
    n=$((n + 1)); [ "$n" -le "$2" ] || break
    # shellcheck disable=SC2086
    a shell input tap $xy; sleep 2
    # shellcheck disable=SC2086
    a shell input tap $xy; sleep 2
  done < <(centres_of 'checkable="true"')
  log "$1: toggled $(( n > $2 ? $2 : n )) switches twice"
}

webview_mode() {  # $1 = on|off, through the provider selection screen
  destination ProviderSelection
  if [ "$1" = on ]; then
    tap_on 'resource-id="provider-webview"' || return 0
    sleep 2
    tap_on 'text="OK"' || tap_on 'text="Yes"' || true
  else
    tap_on 'text="Chrome"' || true
  fi
  sleep 2
  a shell input keyevent KEYCODE_HOME
}

CRASHED=0
[ -f "$APK" ] || fail "debug APK missing at $APK"
a uninstall "$PKG" >/dev/null 2>&1 || true
a install "$APK" | tr -d '\r' | tail -1 | grep -q '^Success' || fail "install of $APK failed"
a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
flag_on
log "installed $APK; strict-mode flag on"

begin first-start
a shell am start -W -n "$PKG/$MAIN" >/dev/null
sleep 8
end_section

begin settings
for screen in BrowsingMode LookAndFeel BrowsingOptions; do
  destination "$screen"
  toggle_switches "$screen" 2
done
a shell input keyevent KEYCODE_HOME
end_section

begin per-app
destination PerApp
sleep 4
toggle_switches PerApp 2
a shell input keyevent KEYCODE_HOME
end_section

begin link-custom-tab
open_link "https://example.com/?main-thread=1"
sleep 8
a shell input keyevent KEYCODE_BACK
sleep 1
a shell input keyevent KEYCODE_HOME
end_section

begin link-web-view
webview_mode on
a shell am force-stop "$PKG"
a logcat -c
open_link "https://example.com/?main-thread=2"
sleep 8
a shell input keyevent KEYCODE_BACK
sleep 1
webview_mode off
end_section

begin history
a shell am start -W -n "$PKG/$HISTORY" >/dev/null
sleep 4
tap_on 'resource-id="history-row-menu"' && sleep 1 && tap_on 'resource-id="history-menu-remove"' && sleep 1 && tap_on 'text="Undo"' || log "history: no row to delete"
sleep 2
tap_on 'resource-id="history-row"' || log "history: no row to open"
sleep 6
a shell input keyevent KEYCODE_HOME
end_section

begin tiles
for tile in $TILES; do
  a shell cmd statusbar add-tile "$PKG/$tile" >/dev/null 2>&1 || true
  a shell cmd statusbar expand-settings >/dev/null 2>&1 || true
  sleep 6
  a shell cmd statusbar click-tile "$PKG/$tile" >/dev/null 2>&1 || true
  sleep 2
  a shell cmd statusbar click-tile "$PKG/$tile" >/dev/null 2>&1 || true
  sleep 2
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
done
end_section

begin first-start-after-clear
a shell pm clear "$PKG" >/dev/null
a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
flag_on
a logcat -c
a shell am start -W -n "$PKG/$MAIN" >/dev/null
sleep 8
end_section

flag_off
a shell input keyevent KEYCODE_HOME

# One row per section and site: how often the main thread waited there.
{
  printf 'section\tsite\tcount\n'
  for f in "$OUT"/*.log; do
    s="$(basename "$f" .log)"
    grep -oE 'Main thread waits: [^ ]+' "$f" | sed 's/Main thread waits: //; s/(.*//' | sort | uniq -c |
      awk -v s="$s" '{ printf "%s\t%s\t%s\n", s, $2, $1 }' || true
  done
} > "$OUT/counts.tsv"
log "counts per section and site: counts.tsv"
[ "$CRASHED" = 0 ] || fail "a section crashed the app"
log "$CHECK_NAME passed"
