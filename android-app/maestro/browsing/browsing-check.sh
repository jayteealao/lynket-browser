#!/usr/bin/env bash
# Device check of the browsing dialogs, the Custom Tab bottom bar and the external entry points
# on the debug build.
#
#   legacy        on the installed build, before the Compose dialogs: a bottom-bar tap and the
#                 options' Add to Home Screen (the address did not reach them), captures of the
#                 Custom Tab, the options dialog and New tab; then release 2.1.3 (existing APK,
#                 not rebuilt) for Open with, the shortcut creator and the AMP dialog; controls.md
#   captures      Custom Tab with the bottom bar, the options sheet, Open with, New tab with text,
#                 the shortcut dialog, AMP loading (phone); FOLD=<serial> also captures the options
#                 sheet on the fold and holds a back swipe over it
#   entry-points  every entry point fired with its real intent, one screenshot and one log each,
#                 and ac10.md (entry point -> intent -> what showed)
#   amp-error     AMP on, airplane mode on, a link: the error dialog; Try again keeps it; airplane
#                 mode off; Open normal page opens the page
#   bottom-bar    the bar, then each of its five actions tapped in Chrome, with what it opened
#   recreate      each dialog shown, night mode switched while it shows; it stays or closes, no crash
#
# Usage: maestro/browsing/browsing-check.sh <serial> <evidence-dir> <mode>   (run from android-app/)
# APK=<path> picks the debug APK (default: the last assembleDebug output). legacy, captures and
# entry-points reinstall the app, which clears its data on that device.
set -euo pipefail

USAGE="usage: browsing-check.sh <serial> <evidence-dir> <legacy|captures|entry-points|amp-error|bottom-bar|recreate>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
BD_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
APK="${APK:-$BD_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk}"
OLD_APK="${OLD_APK:-$BD_HERE/../../build/old-apk/lynket-2.1.3-debug.apk}"
APP=arun.com.chromer
SHARE=$APP.browsing.shareintercept.ShareInterceptActivity
ALIAS=$APP.activities.BrowserInterceptActivity
TABS=$APP.tabs.ui.TabsActivity
HISTORY=$APP.history.HistoryActivity
AMP_TILE=$APP.browsing.amp.qs.AmpTile
ARTICLE_TILE=$APP.browsing.article.qs.ArticleTile
INCOGNITO_TILE=$APP.browsing.incognito.qs.IncognitoTile
WEBHEAD_TILE=$APP.bubbles.webheads.qs.WebHeadTile
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$MODE"
CHECK_NAME="browsing $MODE check"
: > "$OUT/$MODE.txt"

# Maestro's driver keeps the UI automation after a flow ends; stopping it frees uiautomator.
free_ui_automation() {
  a shell am force-stop dev.mobile.maestro >/dev/null 2>&1 || true
  a shell am force-stop dev.mobile.maestro.test >/dev/null 2>&1 || true
}

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/browsing-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/browsing-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/browsing-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/browsing-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

# Centre of the first node whose dump line holds $1 (for example 'text="Share"').
centre_of() {
  { ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Waits (up to about 60 s on a busy emulator) for $1 and taps it.
tap_on() {
  local xy _
  for _ in $(seq 1 20); do
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

capture() {  # $1 = name: screenshot and UI tree
  shot "$1"
  ui_dump > "$OUT/$1.xml"
}

crash_check() {  # $1 = name
  app_logcat "$1"
  if grep -q "$PKG" "$OUT/$1.crash.txt"; then fail "the crash buffer holds an entry for $PKG ($1)"; fi
}

fresh_install() {  # $1 = APK
  a uninstall "$PKG" >/dev/null 2>&1 || true
  a install "$1" | tr -d '\r' | tail -1 | grep -q '^Success' || fail "install of $1 failed"
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
}

quiet_chrome() {
  a shell am force-stop com.android.chrome
  sleep 2
  tap_text "Close app" >/dev/null 2>&1 || true
}

# A link in a Custom Tab through the app's own entry point; waits for Chrome's toolbar.
open_tab() {  # $1 = url
  local attempt
  for attempt in 1 2; do
    a shell input keyevent KEYCODE_HOME
    quiet_chrome
    open_link "$1"
    wait_for 'com.android.chrome:id/menu_button' || true
    sleep 3
    # After a theme switch Chrome can raise "isn't responding" over the tab: close it and retry.
    ui_dump | grep -qF "isn't responding" || return 0
    tap_text "Close app" >/dev/null 2>&1 || true
    log "Chrome was not responding; opening $1 again"
  done
}

menu_item() {  # $1 = text of a Custom Tab menu item
  tap_on 'com.android.chrome:id/menu_button'
  sleep 2
  tap_on "text=\"$1\""
}

options_item() {  # $1 = text of a Lynket options item
  menu_item "Lynket Options"
  wait_for 'text="Open with…"'
  sleep 1
  [ -z "$1" ] || tap_on "text=\"$1\""
}

stored() {  # $1 = settings key: the value the debug dump receiver reports
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}

# Clicks a tile in the open Quick Settings panel (with the panel closed the click can be dropped).
click_tile() {  # $1 = tile class
  a shell cmd statusbar add-tile "$PKG/$1" >/dev/null 2>&1 || true
  a shell cmd statusbar expand-settings >/dev/null 2>&1 || true
  sleep 4
  a shell cmd statusbar click-tile "$PKG/$1" >/dev/null
  sleep 2
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
}

set_tile() {  # $1 = tile class, $2 = key, $3 = wanted value (true|false)
  [ "$(stored "$2")" = "$3" ] || click_tile "$1"
  [ "$(stored "$2")" = "$3" ] || fail "the tile $1 did not set $2 to $3"
}

row() {  # entry point | intent | observed
  echo "| $1 | $2 | $3 |" >> "$OUT/ac10.md"
}

top_activity() {
  # awk reads to the end: an early exit would break the pipe (a fold lists several displays) and
  # pipefail would end the script with status 141.
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}

a logcat -c
a logcat -b crash -c

case "$MODE" in
legacy)
  [ -f "$APK" ] || fail "debug APK missing at $APK"
  log "1. the installed build (dialogs before Compose)"
  open_tab "https://example.com/?bd=legacy"
  capture custom-tab
  a logcat -c
  tap_on 'text="Share"'
  sleep 3
  a logcat -d | tr -d '\r' | grep -E "BottomBarReceiver" > "$OUT/bottom-bar-share.logcat.txt" || true
  log "bottom bar Share: $(cat "$OUT/bottom-bar-share.logcat.txt" | tail -1)"
  a shell input keyevent KEYCODE_BACK
  options_item ""
  capture options
  tap_on 'text="Add to Home Screen"'
  sleep 3
  capture add-to-home-screen
  open_tab "https://example.com/?bd=legacy-2"
  options_item "New tab"
  sleep 3
  capture new-tab
  log "2. release 2.1.3: Open with, the shortcut creator and the AMP dialog"
  [ -f "$OLD_APK" ] || fail "2.1.3 APK missing at $OLD_APK (maestro/build-old-apk.sh)"
  fresh_install "$OLD_APK"
  a shell monkey -p "$PKG" 1 >/dev/null 2>&1 || true
  sleep 5
  open_tab "https://www.theverge.com/"
  options_item "Open with…"
  sleep 3
  capture open-with-2.1.3
  open_tab "https://www.theverge.com/?s=2"
  options_item "Add to Home Screen"
  sleep 6
  capture shortcut-creator-2.1.3
  tap_on 'text="CANCEL"' || a shell input keyevent KEYCODE_BACK
  click_tile "$AMP_TILE"
  a shell input keyevent KEYCODE_HOME
  quiet_chrome
  a shell am start -a android.intent.action.VIEW -d "https://www.theverge.com/?s=3" >/dev/null
  sleep 2
  capture amp-2.1.3
  a uninstall "$PKG" >/dev/null 2>&1 || true
  cat > "$OUT/controls.md" <<'EOF'
# Legacy dialog controls, in order (from the captures in this folder)

| Dialog | Controls in order |
|---|---|
| Custom Tab bottom bar | New tab, Share, Tabs, Minimize, Article (icon over label) |
| Lynket options | Settings, Tabs, New tab, History, Add to Home Screen, Open with…, Open article view |
| Open with… | title "Open with…", grid of apps (Lynket excluded) |
| New tab | search field with mic, at the bottom above the keyboard |
| Create shortcut | title "Create shortcut", icon, name field ("Name cannot be empty"), Create |
| AMP | title "Grabbing AMP link", progress, "Loading…" then "Link found!" / "AMP link not found", Skip |
EOF
  ;;

captures)
  [ -f "$APK" ] || fail "debug APK missing at $APK"
  fresh_install "$APK"
  open_tab "https://example.com/?bd=captures"
  capture custom-tab--default
  options_item ""
  capture options-sheet--default
  tap_on 'text="Open with…"'
  wait_for 'text="Open with…"'
  sleep 2
  capture open-with--default
  a shell input keyevent KEYCODE_BACK
  open_tab "https://example.com/?bd=captures-2"
  tap_on 'text="New tab"'
  wait_for 'text="Search or type URL"'
  a shell input text "navigation"
  sleep 4
  capture new-tab--default
  a shell input keyevent KEYCODE_BACK
  a shell input keyevent KEYCODE_BACK
  open_tab "https://example.com/?bd=captures-3"
  options_item "Add to Home Screen"
  wait_for 'text="Create shortcut"'
  sleep 4
  capture shortcut-creator--default
  tap_on 'text="Cancel"'
  set_tile "$AMP_TILE" amp_mode_pref true
  a shell input keyevent KEYCODE_HOME
  quiet_chrome
  open_link "https://www.theverge.com/?bd=captures-amp-$(date +%s)"
  wait_for 'text="Grabbing AMP link"' || true
  capture amp-resolver--loading
  sleep 15
  set_tile "$AMP_TILE" amp_mode_pref false
  crash_check captures
  if [ -n "${FOLD:-}" ]; then
    SERIAL="$FOLD"
    fresh_install "$APK"
    open_tab "https://example.com/?bd=fold"
    options_item ""
    capture options-sheet--fold
    read -r w h < <(screen_size)
    # A back swipe from the left edge, held half way: the sheet shows its back preview.
    a shell input motionevent DOWN 2 $((h / 2))
    for x in 40 120 200 280; do a shell input motionevent MOVE $x $((h / 2)); done
    capture options-sheet--back-held
    a shell input motionevent UP 280 $((h / 2))
    sleep 2
    capture options-sheet--after-back
    crash_check captures-fold
  fi
  ;;

entry-points)
  [ -f "$APK" ] || fail "debug APK missing at $APK"
  fresh_install "$APK"
  a shell monkey -p "$PKG" 1 >/dev/null 2>&1 || true
  sleep 6
  a shell input keyevent KEYCODE_HOME
  echo "| Entry point | Intent | Observed |" > "$OUT/ac10.md"
  echo "|---|---|---|" >> "$OUT/ac10.md"

  open_tab "https://example.com/?ep=view"
  capture ep-view; row BrowserInterceptActivity "VIEW https" "$(top_activity)"
  a shell input keyevent KEYCODE_HOME; quiet_chrome
  a shell am start -a android.intent.action.VIEW -d "https://example.com/?ep=alias" -n "$PKG/$ALIAS" >/dev/null
  sleep 10; capture ep-alias; row ".activities.BrowserInterceptActivity alias" "VIEW https" "$(top_activity)"
  a shell input keyevent KEYCODE_HOME; quiet_chrome
  # One quoted string: adb shell joins its arguments, so text with spaces must stay quoted on the device.
  a shell "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'see https://example.com/?ep=send' -n $PKG/$SHARE" >/dev/null
  sleep 10; capture ep-send; row ShareInterceptActivity "SEND text/plain (URL)" "$(top_activity)"
  a shell input keyevent KEYCODE_HOME; quiet_chrome
  a shell "am start -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT 'compose navigation' -n $PKG/$SHARE" >/dev/null
  sleep 10; capture ep-process-text; row ShareInterceptActivity "PROCESS_TEXT (words: search)" "$(top_activity)"
  a shell input keyevent KEYCODE_HOME
  a shell am start -W -n "$PKG/$MAIN" >/dev/null; sleep 4; capture ep-main; row MainActivity "MAIN launcher" "$(top_activity)"
  a shell am start -W -n "$PKG/$TABS" >/dev/null; sleep 4; capture ep-tabs; row TabsActivity "am start -n" "$(top_activity)"
  a shell am start -W -n "$PKG/$HISTORY" >/dev/null; sleep 4; capture ep-history; row HistoryActivity "am start -n (DUMP-guarded)" "$(top_activity)"

  for item in "Settings" "Tabs" "New tab" "History" "Add to Home Screen" "Open with…" "Open article view"; do
    open_tab "https://example.com/?ep=options-$(echo "$item" | tr -dc 'a-zA-Z')"
    options_item "$item"
    sleep 6
    name="ep-options-$(echo "$item" | tr -dc 'a-zA-Z' | tr 'A-Z' 'a-z')"
    capture "$name"; row "ChromerOptionsActivity -> $item" "Custom Tab menu PendingIntent" "$(top_activity)"
    a shell input keyevent KEYCODE_BACK
  done
  open_tab "https://example.com/?ep=copy"
  menu_item "Copy link"; sleep 3; capture ep-copy-link
  row CopyToClipboardReceiver "Custom Tab menu PendingIntent" "$(a logcat -d | tr -d '\r' | grep -c 'Copied' || true) copy lines"

  for action in "New tab" "Share" "Tabs" "Minimize" "Article"; do
    open_tab "https://example.com/?ep=bar-$(echo "$action" | tr -dc 'a-zA-Z')"
    a logcat -c
    tap_on "text=\"$action\""
    sleep 6
    name="ep-bar-$(echo "$action" | tr -dc 'a-zA-Z' | tr 'A-Z' 'a-z')"
    capture "$name"
    skipped="$(a logcat -d | tr -d '\r' | grep -c 'Skipped bottom bar callback' || true)"
    row "BottomBarReceiver -> $action" "bottom bar PendingIntent" "$(top_activity) (skipped callbacks: $skipped)"
    a shell input keyevent KEYCODE_BACK
  done

  for tile in "$AMP_TILE:amp_mode_pref" "$ARTICLE_TILE:article_mode_pref" "$INCOGNITO_TILE:" "$WEBHEAD_TILE:webhead_enabled_pref"; do
    cls="${tile%%:*}"; key="${tile#*:}"
    before="$( [ -n "$key" ] && stored "$key" || echo n/a )"
    click_tile "$cls"
    after="$( [ -n "$key" ] && stored "$key" || echo n/a )"
    open_tab "https://example.com/?ep=tile-${cls##*.}"
    sleep 6
    capture "ep-tile-${cls##*.}"
    row "${cls##*.}" "click-tile, then a link" "$key $before -> $after; top $(top_activity)"
    [ -z "$key" ] || click_tile "$cls"
    close_all_heads
  done
  a shell dumpsys activity services "$PKG" | tr -d '\r' > "$OUT/ep-services.txt"
  crash_check entry-points
  ;;

amp-error)
  set_tile "$AMP_TILE" amp_mode_pref true
  a shell cmd connectivity airplane-mode enable
  sleep 4
  a shell input keyevent KEYCODE_HOME
  quiet_chrome
  open_link "https://www.theverge.com/?bd=offline-$(date +%s)"  # a fresh address: a cached page would skip the lookup
  wait_for 'text="Try again"' || fail "no error dialog offline"
  capture amp-error
  tap_on 'text="Try again"'
  sleep 6
  wait_for 'text="Open normal page"' || fail "the error dialog left while still offline"
  capture amp-error-after-retry
  a shell cmd connectivity airplane-mode disable
  sleep 6
  tap_on 'text="Open normal page"'
  sleep 10
  capture amp-open-normal
  top="$(top_activity)"  # read once: the log line and the check must judge the same state
  log "after Open normal page: $top"
  [[ "$top" == *com.android.chrome* ]] || fail "Open normal page did not open the page"
  set_tile "$AMP_TILE" amp_mode_pref false
  crash_check amp-error
  ;;

bottom-bar)
  for action in "New tab" "Share" "Tabs" "Minimize" "Article"; do
    open_tab "https://example.com/?bd=bar-$(echo "$action" | tr -dc 'a-zA-Z')"
    [ "$action" != "New tab" ] || capture bottom-bar
    a shell dumpsys activity recents | tr -d '\r' | grep -E "Recent #" > "$OUT/recents-before-$(echo "$action" | tr -dc 'a-zA-Z').txt" || true
    a logcat -c
    tap_on "text=\"$action\""
    sleep 6
    name="bar-$(echo "$action" | tr -dc 'a-zA-Z' | tr 'A-Z' 'a-z')"
    capture "$name"
    a logcat -d | tr -d '\r' | grep -E "BottomBarReceiver|START u0" > "$OUT/$name.logcat.txt" || true
    if grep -q "Skipped bottom bar callback" "$OUT/$name.logcat.txt"; then fail "$action: the callback carried no address or action"; fi
    log "$action -> $(top_activity)"
    a shell input keyevent KEYCODE_BACK
  done
  crash_check bottom-bar
  ;;

recreate)
  show_and_flip() {  # $1 = name; the dialog is on screen
    dismiss_anr
    capture "$1-before"
    a shell cmd uimode night yes; sleep 5; dismiss_anr
    capture "$1-night"
    a shell cmd uimode night no; sleep 5; dismiss_anr
    capture "$1-day"
    log "$1 after night mode switches: $(top_activity)"
  }
  open_tab "https://example.com/?bd=recreate-1"
  options_item ""
  show_and_flip options
  a shell input keyevent KEYCODE_BACK
  open_tab "https://example.com/?bd=recreate-2"
  options_item "Open with…"
  sleep 3
  show_and_flip open-with
  a shell input keyevent KEYCODE_BACK
  open_tab "https://example.com/?bd=recreate-3"
  tap_on 'text="New tab"'
  dismiss_anr
  wait_for 'text="Search or type URL"'
  tap_on 'text="Search or type URL"'
  a shell input text "kept"
  show_and_flip new-tab
  ui_dump | grep -qF 'text="kept"' && log "new tab kept its text" || log "new tab text not seen after the switch"
  a shell input keyevent KEYCODE_BACK; a shell input keyevent KEYCODE_BACK
  open_tab "https://example.com/?bd=recreate-4"
  options_item "Add to Home Screen"
  wait_for 'text="Create shortcut"' || true
  show_and_flip shortcut
  a shell input keyevent KEYCODE_BACK
  crash_check recreate
  ;;

*) fail "$USAGE" ;;
esac

log "$MODE: done"
