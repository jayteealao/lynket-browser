#!/usr/bin/env bash
# Device check of the two places that read a page's Website extra from an intent: the web head
# service closing one site from the web head list, and the browsing screens (Web View mode)
# opening a page. Pages come from maestro/reader/fixtures/, served from this host with
# python -m http.server on a free port and reached through adb reverse.
#
#   heads     web heads mode with the overlay allowed; two links give two heads and the trash
#   open      a tap on the front head opens its page in a Chrome Custom Tab
#   close-one a long press opens the web head list; its "Close <site>" button removes that one head
#   web-view  Slide over with Web View on: a link opens WebViewActivity with the fixture article
#   exit      the browsing mode and the Web View choice the device had are restored; no crash entry
#             for the app in the crash buffer
#
# Usage: maestro/browsing/website-extra-check.sh <serial> <evidence-dir>   (run from android-app/)
# Needs the debug build installed (./gradlew :lynket:installDebug) and Chrome's first run done.
# Each step writes PASS or FAIL to <evidence-dir>/website-extra.txt.
set -euo pipefail

USAGE="usage: website-extra-check.sh <serial> <evidence-dir>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
WE_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
FIXTURES="$WE_HERE/../reader/fixtures"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="website-extra"
CHECK_NAME="website extra check"
: > "$OUT/website-extra.txt"

pass() { log "PASS $1"; }
step_fail() { log "FAIL $1: $2"; FAILED=1; }
FAILED=0

# ---------------------------------------------------------------------------------------------
# Fixture server

PORT=""
SERVER_PID=""
start_server() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$FIXTURES" > "$OUT/server.log" 2>&1 &
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
# UI and store helpers

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/website-extra-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/website-extra-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/website-extra-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/website-extra-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

centre_of() {  # $1 = dump fragment, for example 'content-desc="Close '
  { ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

tap_on() {
  local xy _
  for _ in $(seq 1 10); do
    xy="$(centre_of "$1")"
    if [ -n "$xy" ]; then
      # shellcheck disable=SC2086
      a shell input tap $xy
      return 0
    fi
    dismiss_anr
    sleep 2
  done
  return 1
}

wait_for() {  # $1 = dump fragment
  local _
  for _ in $(seq 1 15); do
    ui_dump | grep -qF -- "$1" && return 0
    dismiss_anr
    sleep 2
  done
  return 1
}

stored() {  # $1 = settings key: the value the debug dump receiver reports
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}

top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}

wait_top() {  # $1 = text the top resumed activity must contain
  local _
  for _ in $(seq 1 15); do
    top_activity | grep -qF -- "$1" && return 0
    sleep 1
  done
  return 1
}

wait_overlays() {  # $1 = expected number of overlay windows
  local _
  for _ in $(seq 1 15); do
    [ "$(overlay_count)" -eq "$1" ] && return 0
    sleep 1
  done
  return 1
}

# Web View on or off through the Custom Tab provider screen (the same rows reader-check.sh uses).
webview_mode() {  # $1 = on|off
  local want=false
  [ "$1" = on ] && want=true
  [ "$(stored use_webview_pref)" = "$want" ] && return 0
  a shell am force-stop com.android.chrome
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" ProviderSelection >/dev/null
  sleep 3
  if [ "$1" = on ]; then
    tap_on 'resource-id="provider-webview"' || return 1
    wait_for 'text="Are you sure?"' || true
    sleep 1
    tap_on 'text="OK"' || tap_on 'text="Yes"' || true
  else
    tap_on 'resource-id="provider-com.android.chrome"' || return 1
    wait_for 'text="Use"' || true
    tap_on 'resource-id="provider-confirm"' || tap_on 'text="Use"' || true
  fi
  sleep 2
  a shell input keyevent KEYCODE_HOME
  [ "$(stored use_webview_pref)" = "$want" ]
}

mode_title() {  # the browsing mode row title for the stored keys
  if [ "$1" = true ]; then echo "Web heads"; elif [ "$2" = true ]; then echo "Native Bubbles (Beta)"; else echo "Slide over"; fi
}

# ---------------------------------------------------------------------------------------------

start_server
ARTICLE_URL="http://localhost:$PORT/article.html"
OTHER_URL="http://localhost:$PORT/no-article.html"

WEBHEADS_BEFORE="$(stored webhead_enabled_pref)"
BUBBLES_BEFORE="$(stored native_bubbles_preference)"
WEBVIEW_BEFORE="$(stored use_webview_pref)"
ARTICLE_BEFORE="$(stored article_mode_pref)"
log "before: webhead_enabled_pref=$WEBHEADS_BEFORE native_bubbles_preference=$BUBBLES_BEFORE use_webview_pref=$WEBVIEW_BEFORE article_mode_pref=$ARTICLE_BEFORE"
[ "$ARTICLE_BEFORE" = true ] && fail "article mode is on; turn it off (Article tile) so links open a page, not the reader"

restore() {
  close_all_heads
  choose_mode "$(mode_title "$WEBHEADS_BEFORE" "$BUBBLES_BEFORE")" || true
  a shell input keyevent KEYCODE_HOME
  if [ "$WEBVIEW_BEFORE" = true ]; then webview_mode on || true; else webview_mode off || true; fi
  log "after: webhead_enabled_pref=$(stored webhead_enabled_pref) native_bubbles_preference=$(stored native_bubbles_preference) use_webview_pref=$(stored use_webview_pref)"
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap restore EXIT

a logcat -c
a logcat -b crash -c

# heads -----------------------------------------------------------------------------------------
webview_mode off || step_fail heads "Web View could not be turned off"
close_all_heads
a shell am force-stop "$PKG"
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
choose_mode "Web heads"
tap_text "Not now" || true
a shell input keyevent KEYCODE_HOME
sleep 1
open_link "$ARTICLE_URL"
sleep 4
open_link "$OTHER_URL"
sleep 5
overlay_windows > "$OUT/heads-windows.txt"
if wait_overlays 3; then pass heads; else step_fail heads "expected 3 overlay windows (two heads and the trash), found $(overlay_count)"; fi
shot heads

# open ------------------------------------------------------------------------------------------
XY="$(head_centre)"
if [ -n "$XY" ]; then
  # shellcheck disable=SC2086
  a shell input tap $XY
  if wait_top "com.android.chrome/"; then
    sleep 3
    shot open
    log "top activity after the tap: $(top_activity)"
    pass open
  else
    shot open
    step_fail open "the top activity is $(top_activity), not a Chrome Custom Tab"
  fi
  a shell input keyevent KEYCODE_BACK
  sleep 2
  a shell input keyevent KEYCODE_HOME
  sleep 2
else
  step_fail open "no front head on screen"
fi

# close-one -------------------------------------------------------------------------------------
BEFORE="$(overlay_count)"
XY="$(head_centre)"
if [ -n "$XY" ]; then
  # shellcheck disable=SC2086
  set -- $XY
  a shell input swipe "$1" "$2" "$1" "$2" 1200
  if wait_top "WebHeadContextActivity"; then
    sleep 2
    shot close-one-list
    ui_dump > "$OUT/close-one-list.xml"
    # The sheet's own "Close sheet" button comes first; a site row's button names the site.
    LABEL="$(tr '>' '\n' < "$OUT/close-one-list.xml" | grep -o 'content-desc="Close [^"]*"' | grep -v '"Close sheet"' | head -1 | sed 's/content-desc="//; s/"$//')"
    log "web heads before closing one: $BEFORE overlay windows; tapping \"$LABEL\""
    if [ -n "$LABEL" ] && tap_on "content-desc=\"$LABEL\""; then
      if wait_overlays 2; then
        sleep 1
        shot close-one
        overlay_windows > "$OUT/close-one-windows.txt"
        pass close-one
      else
        shot close-one
        step_fail close-one "expected 2 overlay windows after closing one site, found $(overlay_count)"
      fi
    else
      step_fail close-one "no Close button in the web head list"
    fi
    a shell input keyevent KEYCODE_BACK
    sleep 1
  else
    shot close-one-list
    step_fail close-one "a long press did not open the web head list (top: $(top_activity))"
  fi
else
  step_fail close-one "no front head on screen"
fi
app_logcat heads
close_all_heads

# web-view --------------------------------------------------------------------------------------
choose_mode "Slide over"
a shell input keyevent KEYCODE_HOME
if webview_mode on; then
  a shell am force-stop "$PKG"
  open_link "$ARTICLE_URL"
  if wait_top "WebViewActivity" && wait_for 'Android 17 brings resizable apps everywhere'; then
    sleep 2
    shot web-view
    ui_dump > "$OUT/web-view.xml"
    log "top activity: $(top_activity)"
    pass web-view
  else
    shot web-view
    ui_dump > "$OUT/web-view.xml"
    step_fail web-view "the top activity is $(top_activity), or the fixture headline is not on screen"
  fi
  a shell input keyevent KEYCODE_BACK
  sleep 1
else
  step_fail web-view "Web View could not be turned on"
fi
app_logcat web-view

# exit ------------------------------------------------------------------------------------------
trap - EXIT
restore
a logcat -d -b crash | tr -d '\r' > "$OUT/exit.crash.txt"
NOW_HEADS="$(stored webhead_enabled_pref)"; NOW_BUBBLES="$(stored native_bubbles_preference)"; NOW_WEBVIEW="$(stored use_webview_pref)"
if grep -qF "$PKG" "$OUT/exit.crash.txt"; then
  step_fail exit "the crash buffer holds an entry for $PKG"
elif [ "$NOW_HEADS" != "$WEBHEADS_BEFORE" ] || [ "$NOW_BUBBLES" != "$BUBBLES_BEFORE" ] || [ "$NOW_WEBVIEW" != "$WEBVIEW_BEFORE" ]; then
  step_fail exit "the browsing mode or Web View choice was not restored"
else
  pass exit
fi

[ "$FAILED" = 0 ] || { log "website extra check: FAIL"; exit 1; }
log "website extra check: PASS"
