#!/usr/bin/env bash
# Device check of the article reader, the image viewer and the web view on the debug build.
# A fixed article is served from this host (python -m http.server over maestro/reader/fixtures)
# and reached on the device through adb reverse, so every run reads the same page.
#
#   legacy     before the Compose screens: the reader (article theme Light, Dark, Black), its menu,
#              the text-size card, the image viewer, the no-article path and the web view with its
#              menu; FOLD=<serial> adds the reader on the fold; writes controls.md
#   captures   the reader (Light, Dark, Black) on the phone, the loading frame, the image viewer and
#              the web view, beside the boards; FOLD=<serial> adds the reader on the fold and holds
#              a back swipe over it
#   error      a page with no article: the error state; Retry keeps it; Open in browser opens the
#              page in a Custom Tab
#   image      the header image opens the viewer; double tap zooms; Close returns; Share opens the
#              share sheet
#   webview    Web View mode: the page, a link tap, back to the first page, pull to refresh,
#              rotation keeps the page and its history with one "WebView created" line
#   bubble     run on the fold after webheads-check.sh bubble: a native bubble with the page; fold
#              and unfold keep the page with one "WebView created" line
#   recreate   night mode switched in the reader and the web view: the content stays; the process
#              killed in the background, then the web view reopened from recents restores the page
#
# Usage: maestro/reader/reader-check.sh <serial> <evidence-dir> <mode>   (run from android-app/)
# APK=<path> picks the debug APK (default: the last assembleDebug output). legacy and captures
# reinstall the app, which clears its data on that device.
set -euo pipefail

USAGE="usage: reader-check.sh <serial> <evidence-dir> <legacy|captures|error|image|webview|bubble|recreate>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
RC_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
APK="${APK:-$RC_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk}"
APP=arun.com.chromer
ARTICLE=$APP.browsing.article.ArticleActivity
WEBVIEW=$APP.browsing.webview.WebViewActivity
ARTICLE_TILE=$APP.browsing.article.qs.ArticleTile
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$MODE"
CHECK_NAME="reader $MODE check"
: > "$OUT/$MODE.txt"

# ---------------------------------------------------------------------------------------------
# Fixture server

PORT=""
SERVER_PID=""
start_server() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$RC_HERE/fixtures" > "$OUT/server.log" 2>&1 &
  SERVER_PID=$!
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  local _
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/article.html')" 2>/dev/null && break
    sleep 0.5
  done
  log "fixture server on port $PORT (adb reverse tcp:$PORT)"
}
stop_server() {
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap stop_server EXIT

article_url() { echo "http://localhost:$PORT/article.html"; }
no_article_url() { echo "http://localhost:$PORT/no-article.html"; }

# ---------------------------------------------------------------------------------------------
# UI helpers (the same shapes as maestro/browsing/browsing-check.sh)

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/reader-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/reader-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/reader-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/reader-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

centre_of() {  # $1 = dump fragment, for example 'text="Share"' or 'resource-id="reader-close"'
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

wait_for() {  # $1 = dump fragment
  local _
  for _ in $(seq 1 20); do
    ui_dump | grep -qF -- "$1" && return 0
    dismiss_anr
    sleep 2
  done
  log "no $1 on screen after waiting"
  return 1
}

# Swipes up until $1 is on screen (a long settings list).
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

capture() {  # $1 = name: screenshot and UI tree
  shot "$1"
  ui_dump > "$OUT/$1.xml"
}

crash_check() {  # $1 = name
  app_logcat "$1"
  if grep -q "$PKG" "$OUT/$1.crash.txt"; then fail "the crash buffer holds an entry for $PKG ($1)"; fi
}

fresh_install() {
  [ -f "$APK" ] || fail "debug APK missing at $APK"
  a uninstall "$PKG" >/dev/null 2>&1 || true
  a install "$APK" | tr -d '\r' | tail -1 | grep -q '^Success' || fail "install of $APK failed"
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  # Skip the intro on the first start.
  a shell am start -W -n "$PKG/$MAIN" >/dev/null
  sleep 3
  a shell am force-stop "$PKG"
}

stored() {  # $1 = settings key: the value the debug dump receiver reports
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}

click_tile() {  # $1 = tile class
  a shell cmd statusbar add-tile "$PKG/$1" >/dev/null 2>&1 || true
  a shell cmd statusbar expand-settings >/dev/null 2>&1 || true
  sleep 6  # a busy emulator opens Quick Settings slowly, and a click before that is lost
  a shell cmd statusbar click-tile "$PKG/$1" >/dev/null
  sleep 2
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
}

article_mode() {  # $1 = true|false
  local attempt
  for attempt in 1 2 3; do
    [ "$(stored article_mode_pref)" = "$1" ] && return 0
    [ "$attempt" = 1 ] || log "article mode still not $1; clicking the Article tile again (attempt $attempt)"
    click_tile "$ARTICLE_TILE"
  done
  [ "$(stored article_mode_pref)" = "$1" ] || fail "the Article tile did not set article mode to $1"
}

top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}

destination() {  # $1 = settings screen name (LynketKeys Destination)
  # A busy emulator raises "Chrome isn't responding" over the screen; Chrome is not needed here.
  a shell am force-stop com.android.chrome
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" "$1" >/dev/null
  sleep 3
}

# Article theme through the Look and feel screen: $1 = Light | Dark | Black | Auto label.
article_theme() {
  destination LookAndFeel
  scroll_to 'resource-id="row-article_theme_preference"' || fail "no Article theme row on Look and feel"
  tap_on 'resource-id="row-article_theme_preference"'
  sleep 1
  tap_on "text=\"$1\"" || fail "no $1 choice in the article theme dialog"
  sleep 1
  a shell input keyevent KEYCODE_HOME
  log "article theme $1 -> stored $(stored article_theme_preference)"
}

# Web View mode on (Use WebView on the provider screen) or off (Chrome chosen again).
webview_mode() {  # $1 = on|off
  destination ProviderSelection
  if [ "$1" = on ]; then
    [ "$(stored use_webview_pref)" = true ] && { a shell input keyevent KEYCODE_HOME; return 0; }
    local attempt
    for attempt in 1 2 3; do
      [ "$attempt" = 1 ] || destination ProviderSelection
      scroll_to 'resource-id="provider-webview"' || fail "no Use WebView row"
      tap_on 'resource-id="provider-webview"'
      wait_for 'text="Are you sure?"' || true
      sleep 1  # the dialog's enter animation can swallow an early tap
      # The confirm dialog's positive button reads OK (it read Yes before the Compose dialogs).
      tap_on 'text="OK"' || tap_on 'text="Yes"' || true
      sleep 2
      [ "$(stored use_webview_pref)" = true ] && break
      ui_dump > "$OUT/webview-mode-attempt-$attempt.xml" || true
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

open_reader() {  # $1 = url; opens it through the app's own entry point with article mode on
  a shell input keyevent KEYCODE_HOME
  a shell am force-stop "$PKG" || true
  open_link "$1"
  sleep 6
  dismiss_anr
}

created_count() {  # number of "WebView created" lines since the last logcat -c
  a logcat -d | tr -d '\r' | grep -c "WebView created" || true
}

back_swipe_hold() {  # $1 = name: holds a back swipe from the left edge half way and captures it
  local w h
  read -r w h < <(screen_size)
  a shell input motionevent DOWN 2 $((h / 2))
  local x
  for x in 40 90 140 190 240; do a shell input motionevent MOVE "$x" $((h / 2)); done
  sleep 1
  shot "$1"
  a shell input motionevent CANCEL 240 $((h / 2)) >/dev/null 2>&1 || a shell input motionevent UP 2 $((h / 2))
  sleep 1
}

a logcat -c
a logcat -b crash -c
start_server

# ---------------------------------------------------------------------------------------------

reader_round() {  # $1 = prefix: reader captures for each article theme on $SERIAL
  local theme
  for theme in Light Dark Black; do
    article_theme "$theme"
    open_reader "$(article_url)"
    wait_for 'Android 17 brings' || true
    capture "$1-$(echo "$theme" | tr 'A-Z' 'a-z')"
  done
  # Back to Light for the steps after; a missed tap here is logged, not fatal.
  ( article_theme Light ) || log "article theme not reset to Light"
}

case "$MODE" in
legacy)
  fresh_install
  article_mode true
  reader_round reader
  open_reader "$(article_url)"
  wait_for 'Android 17 brings' || true
  a shell input keyevent KEYCODE_MENU
  sleep 2
  tap_on 'content-desc="More options"' || true
  sleep 2
  capture reader-menu
  tap_on 'text="Text size"' || true
  sleep 2
  capture reader-text-size
  a shell input keyevent KEYCODE_BACK
  open_reader "$(article_url)"
  wait_for 'Android 17 brings' || true
  read -r W H < <(screen_size)
  a shell input tap $((W / 2)) $((H / 5))
  sleep 3
  capture image-viewer
  log "image viewer top: $(top_activity)"
  a logcat -c
  open_reader "$(no_article_url)"
  capture no-article
  a logcat -d | tr -d '\r' | grep -iE "ArticleActivity|CustomTab" > "$OUT/no-article.logcat.txt" || true
  log "no article: top $(top_activity)"
  article_mode false
  webview_mode on
  open_reader "$(article_url)"
  wait_for 'Android 17 brings' || true
  capture web-view
  tap_on 'content-desc="More options"' || true
  sleep 2
  capture web-view-menu
  a shell input keyevent KEYCODE_BACK
  webview_mode off
  if [ -n "${FOLD:-}" ]; then
    SERIAL="$FOLD" fresh_install
    SERIAL="$FOLD" a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
    SERIAL="$FOLD" article_mode true
    SERIAL="$FOLD" open_reader "$(article_url)"
    SERIAL="$FOLD" capture reader-fold
  fi
  cat > "$OUT/controls.md" <<'EOF'
# Legacy controls (HEAD before the Compose reader, from the captures in this folder)

Reader, top to bottom:
1. Toolbar: Close (start), Open full page, the preferred-action button, Text size (if room), overflow.
2. Overflow: Copy link, Open with…, Share, Share with <app> (when a favourite share app is set),
   History, Add to Home Screen; with the bottom bar off also Tabs.
3. Header image (tap opens the image viewer).
4. Title and site name.
5. Keyword chips in the accent colour (tap searched the keyword in 2.1.3; nothing at HEAD).
6. Article blocks: paragraphs, headings, quote in the accent colour, code, list items.
7. Text-size card above the bottom bar: icon, slider 0..15, close; on close, a "Save size" dialog
   when the size changed (the slider did not change the text at HEAD).
8. Bottom bar (setting on): New tab, Share, Tabs, Minimize.
9. Dimmed sides on large screens close on tap; dragging the column past 192 dp closes it.
No article: a toast "Could not extract article." and the page opens in a browsing tab.

Image viewer: the image on black, pinch zoom, no controls.

Web view: toolbar in the website colour with Close, title, address, the preferred-action button and
overflow (the reader's items without Open full page and Text size; with the bottom bar off also
Tabs and Article mode); progress bar; pull to refresh; bottom bar: New tab, Share, Tabs, Minimize,
Article mode; back goes through the page history.
EOF
  crash_check legacy
  ;;

captures)
  fresh_install
  article_mode true
  reader_round reader
  # Loading frame: captured right after the link, before the extraction finishes.
  a shell input keyevent KEYCODE_HOME
  a shell am force-stop "$PKG" || true
  open_link "$(article_url)?slow=$(date +%s)"
  sleep 1
  capture reader-loading
  wait_for 'Android 17 brings' || true
  tap_on 'resource-id="reader-header-image"' || true
  sleep 3
  capture image-viewer
  a shell input keyevent KEYCODE_BACK
  article_mode false
  webview_mode on
  open_reader "$(article_url)"
  wait_for 'resource-id="webview-top-bar"' || true
  sleep 3
  capture web-view
  webview_mode off
  if [ -n "${FOLD:-}" ]; then
    SERIAL="$FOLD" fresh_install
    SERIAL="$FOLD" a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
    SERIAL="$FOLD" article_mode true
    SERIAL="$FOLD" open_reader "$(article_url)"
    SERIAL="$FOLD" wait_for 'Android 17 brings' || true
    SERIAL="$FOLD" capture reader-fold
    SERIAL="$FOLD" back_swipe_hold reader-fold-back-swipe
  fi
  crash_check captures
  ;;

error)
  article_mode true
  open_reader "$(no_article_url)"
  wait_for 'resource-id="error-state"' || fail "no error state for a page with no article"
  capture reader-error
  ui_dump | grep -qF 'text="Retry"' || fail "no Retry on the error state"
  ui_dump | grep -qF 'text="Open in browser"' || fail "no Open in browser on the error state"
  tap_on 'text="Retry"'
  sleep 5
  wait_for 'resource-id="error-state"' || fail "Retry did not end in the error state again"
  capture reader-error-after-retry
  a logcat -c
  tap_on 'text="Open in browser"'
  sleep 6
  dismiss_anr
  capture open-in-browser
  top="$(top_activity)"
  log "after Open in browser: top $top"
  a shell dumpsys activity activities | tr -d '\r' | grep -E "CustomTabActivity|com.android.chrome" | head -5 > "$OUT/open-in-browser.activities.txt" || true
  grep -q . "$OUT/open-in-browser.activities.txt" || fail "Open in browser did not open a Custom Tab"
  crash_check error
  ;;

image)
  article_mode true
  open_reader "$(article_url)"
  wait_for 'resource-id="reader-header-image"' || fail "no header image in the reader"
  tap_on 'resource-id="reader-header-image"'
  wait_for 'resource-id="image-viewer"' || fail "the image viewer did not open"
  capture image-before-zoom
  read -r W H < <(screen_size)
  # Both taps in one shell call with a device-side gap: two adb round trips on a busy host can
  # exceed the double-tap timeout (300 ms), and taps closer than the double-tap minimum (40 ms)
  # do not count as a double tap either.
  a shell "input tap $((W / 2)) $((H / 2)); sleep 0.12; input tap $((W / 2)) $((H / 2))"
  sleep 2
  capture image-after-zoom
  ui_dump | grep -qF 'resource-id="image-zoomed"' && log "double tap zoomed in" || fail "double tap did not zoom"
  tap_on 'resource-id="image-share"'
  sleep 3
  capture image-share
  log "after Share: top $(top_activity)"
  a shell input keyevent KEYCODE_BACK
  sleep 1
  tap_on 'resource-id="image-close"'
  sleep 2
  top="$(top_activity)"
  log "after Close: top $top"
  echo "$top" | grep -q "ArticleActivity" || fail "Close did not return to the reader"
  crash_check image
  ;;

webview)
  article_mode false
  webview_mode on
  a logcat -c
  open_reader "$(article_url)"
  wait_for 'resource-id="webview-top-bar"' || fail "the web view did not open"
  sleep 3
  capture webview-page
  echo "$(top_activity)" | grep -q "WebViewActivity" || fail "the top activity is not the web view"
  tap_on 'text="adaptive layouts in Compose"' || log "the link was not found in the page tree"
  sleep 5
  capture webview-link
  a shell input keyevent KEYCODE_BACK
  sleep 3
  capture webview-back
  ui_dump | grep -qF "localhost:$PORT/article.html" && log "back returned to the article" || log "address after back not seen in the tree"
  read -r W H < <(screen_size)
  a shell input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H * 3 / 4)) 400
  sleep 3
  capture webview-refresh
  log "requests after the pull: $(grep -c 'GET /article.html' "$OUT/server.log" || true)"
  a shell settings put system accelerometer_rotation 0
  a shell settings put system user_rotation 1
  sleep 4
  capture webview-rotated
  a shell settings put system user_rotation 0
  sleep 3
  log "WebView created lines: $(created_count)"
  [ "$(created_count)" = 1 ] || fail "rotation created a second WebView"
  webview_mode off
  crash_check webview
  ;;

bubble)
  # webheads-check.sh bubble leaves the bubble collapsed. Folding locks the screen, as on a real
  # device, and the system collapses an open bubble then; the page must survive in the same
  # activity, so each step expands the bubble again from the shell's stack position.
  expand_bubble() {
    a shell input keyevent KEYCODE_WAKEUP
    a shell wm dismiss-keyguard
    sleep 2
    local xy _
    xy="$(a shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | tr -d '\r' |
      sed -nE 's/.*currentStackPos: *PointF\((-?[0-9.]+), *(-?[0-9.]+)\).*/\1 \2/p' | head -1 |
      awk 'NF == 2 { x = $1 < 0 ? 0 : $1; print int(x + 70), int($2 + 70) }' || true)"
    [ -n "$xy" ] || fail "no bubble on screen; run webheads-check.sh <serial> <dir> bubble first"
    for _ in 1 2 3; do
      top_activity | grep -q "EmbeddableWebViewActivity" && return 0
      # shellcheck disable=SC2086
      a shell input tap $xy
      sleep 4
    done
    top_activity | grep -q "EmbeddableWebViewActivity"
  }
  bubble_record() {  # the activity record of the bubble's page (same record = same activity)
    a shell dumpsys activity activities | tr -d '\r' | grep -m1 -oE "ActivityRecord\{[0-9a-f]+ u0 $PKG/[^ ]*EmbeddableWebViewActivity" || true
  }
  a shell input keyevent KEYCODE_WAKEUP
  a shell wm dismiss-keyguard
  a shell input keyevent KEYCODE_HOME
  sleep 2
  a logcat -c
  expand_bubble || fail "the bubble did not open the page"
  before="$(bubble_record)"
  log "top before fold: $(top_activity) ($before)"
  capture bubble-unfolded
  a emu fold >/dev/null 2>&1 || log "emu fold not supported here"
  sleep 4
  expand_bubble || fail "the bubble did not open the page on the folded screen"
  capture bubble-folded
  log "top folded: $(top_activity) ($(bubble_record))"
  a emu unfold >/dev/null 2>&1 || true
  sleep 4
  expand_bubble || fail "the bubble did not open the page after unfold"
  capture bubble-unfolded-again
  after="$(bubble_record)"
  log "top after unfold: $(top_activity) ($after)"
  [ -n "$before" ] && [ "$before" = "$after" ] || fail "the bubble's page activity changed across fold and unfold"
  grep -qF 'resource-id="webview-top-bar"' "$OUT/bubble-unfolded-again.xml" || fail "no web view in the bubble after unfold"
  log "WebView created lines after fold and unfold: $(created_count)"
  [ "$(created_count)" = 0 ] || fail "fold or unfold created a new WebView"
  crash_check bubble
  ;;

recreate)
  article_mode true
  open_reader "$(article_url)"
  wait_for 'Android 17 brings' || true
  a shell cmd uimode night yes
  sleep 4
  capture reader-night
  ui_dump | grep -qF 'Android 17 brings' && log "reader kept the article" || fail "reader lost the article after the night switch"
  a shell cmd uimode night no
  sleep 2
  article_mode false
  webview_mode on
  a logcat -c
  open_reader "$(article_url)"
  wait_for 'resource-id="webview-top-bar"' || true
  a shell cmd uimode night yes
  sleep 4
  capture webview-night
  a shell cmd uimode night no
  sleep 2
  log "WebView created lines after the night switch: $(created_count)"
  a shell input keyevent KEYCODE_HOME
  sleep 2
  a shell am kill "$PKG"
  sleep 2
  a shell input keyevent KEYCODE_APP_SWITCH
  sleep 3
  read -r W H < <(screen_size)
  a shell input tap $((W / 2)) $((H / 2))
  sleep 5
  capture webview-restored
  log "after restore: top $(top_activity)"
  webview_mode off
  crash_check recreate
  ;;

*)
  fail "$USAGE"
  ;;
esac

log "$CHECK_NAME passed"
