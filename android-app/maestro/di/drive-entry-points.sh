#!/usr/bin/env bash
# Fires every entry point that receives injected fields with its real intent and records what
# happened, so a missing injection shows up as a crash or an error line in the log.
#
#   1. BrowserInterceptActivity  - VIEW intent (also runs CustomTabActivity)
#   2. ShareInterceptActivity    - SEND and PROCESS_TEXT intents
#   3. BottomBarReceiver         - the Custom Tab bottom bar's "open in new tab" action,
#                                  which opens NewTabDialogActivity
#   4. NewTabDialogActivity      - Custom Tab menu -> Lynket Options -> New tab
#   5. AmpResolverActivity       - AMP turned on with its quick settings tile, then a link
#   6. MinimizeBroadcastReceiver - bottom bar off, Custom Tab menu -> Minimize
#   7. WebHeadService            - web heads turned on with their quick settings tile, then a link
#   8. Tabs and History from the Lynket options (extra evidence only)
#
# Usage: maestro/di/drive-entry-points.sh <serial> <evidence-dir>   (run from android-app/)
# Needs: the debug build installed, the overlay permission granted
# (adb shell appops set arun.com.chromer.debug SYSTEM_ALERT_WINDOW allow), the app launched once,
# Maestro, and Chrome on the emulator image.
set -uo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: drive-entry-points.sh <serial> <evidence-dir>}"
OUT="${2:?usage: drive-entry-points.sh <serial> <evidence-dir>}"
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
here="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
capture="$(hostpath "$(cd "$here/../capture" && pwd)")"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"

PKG=arun.com.chromer.debug
APP=arun.com.chromer
INTERCEPT=$APP.browsing.browserintercept.BrowserInterceptActivity
SHARE=$APP.browsing.shareintercept.ShareInterceptActivity
AMP_TILE=$APP.browsing.amp.qs.AmpTile
WEBHEAD_TILE=$APP.bubbles.webheads.qs.WebHeadTile
PREFS=shared_prefs/${PKG}_preferences.xml

a() { adb -s "$SERIAL" "$@"; }
shot() { a exec-out screencap -p > "$OUT/$1.png"; }
flow() { maestro --device "$SERIAL" test -e OUT="$OUT" "$@"; }
note() { echo "== $*" | tee -a "$OUT/drive.log"; }

open_link() {  # $1 = url, $2 = evidence name: opens it through the app's own link entry point
  a shell input keyevent HOME
  a shell am force-stop com.android.chrome
  a shell am start -W -a android.intent.action.VIEW -d "$1" -n "$PKG/$INTERCEPT" > "$OUT/$2.am.txt" 2>&1
  sleep 5
}

set_bool() {  # $1 = settings key, $2 = true|false; edits the app's settings file while it is stopped
  a shell am force-stop "$PKG"
  local tmp; tmp="$(mktemp)"
  a exec-out run-as "$PKG" cat "$PREFS" | tr -d '\r' > "$tmp"
  if grep -q "name=\"$1\"" "$tmp"; then
    sed -i "s|<boolean name=\"$1\" value=\"[a-z]*\" />|<boolean name=\"$1\" value=\"$2\" />|" "$tmp"
  else
    sed -i "s|</map>|    <boolean name=\"$1\" value=\"$2\" />\n</map>|" "$tmp"
  fi
  a exec-in run-as "$PKG" sh -c "cat > $PREFS" < "$tmp"
  rm -f "$tmp"
}

tap_resource() {  # $1 = resource id suffix: taps the centre of the first view with that id
  a shell uiautomator dump /sdcard/window.xml > /dev/null
  local bounds
  bounds="$(a exec-out cat /sdcard/window.xml | tr '>' '\n' | grep "resource-id=\"[^\"]*$1\"" \
    | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')"
  if [ -z "$bounds" ]; then echo "no view with id $1" | tee -a "$OUT/drive.log"; return 1; fi
  set -- $bounds
  a shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

a logcat -c
a logcat -b crash -c

note "1 BrowserInterceptActivity"
open_link "https://example.com/?di=1" entry-1
shot entry-1

note "2 ShareInterceptActivity (SEND, PROCESS_TEXT)"
a shell input keyevent HOME
a shell am start -W -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "https://example.com/?di=2" -n "$PKG/$SHARE" > "$OUT/entry-2-send.am.txt" 2>&1
sleep 5
shot entry-2-send
a shell input keyevent HOME
a shell am start -W -a android.intent.action.PROCESS_TEXT -t text/plain \
  --es android.intent.extra.PROCESS_TEXT "example.com" -n "$PKG/$SHARE" > "$OUT/entry-2-process-text.am.txt" 2>&1
sleep 5
shot entry-2-process-text

note "3 BottomBarReceiver -> NewTabDialogActivity"
set_bool bottombar_enabled_preference true
open_link "https://example.com/?di=3" entry-3
tap_resource bottom_bar_open_in_new_tab && sleep 3
shot entry-3

note "4 NewTabDialogActivity from the Lynket options"
open_link "https://example.com/?di=4" entry-4
flow -e ITEM="New tab" -e NAME="entry-4" "$capture/options-item.yaml" > "$OUT/entry-4.maestro.txt" 2>&1

note "5 AmpResolverActivity (AMP on through its tile)"
a shell cmd statusbar add-tile "$PKG/$AMP_TILE"
a shell cmd statusbar click-tile "$PKG/$AMP_TILE"
sleep 2
open_link "https://example.com/?di=5" entry-5
shot entry-5
a shell cmd statusbar click-tile "$PKG/$AMP_TILE"

note "6 MinimizeBroadcastReceiver (bottom bar off, menu -> Minimize)"
set_bool bottombar_enabled_preference false
open_link "https://example.com/?di=6" entry-6
flow -e ITEM="Minimize" -e NAME="entry-6" "$here/custom-tab-menu-item.yaml" > "$OUT/entry-6.maestro.txt" 2>&1
sleep 2
set_bool bottombar_enabled_preference true

note "7 WebHeadService (web heads on through its tile)"
a shell cmd statusbar add-tile "$PKG/$WEBHEAD_TILE"
a shell cmd statusbar click-tile "$PKG/$WEBHEAD_TILE"
sleep 2
open_link "https://example.com/?di=7" entry-7
shot entry-7
a shell cmd statusbar click-tile "$PKG/$WEBHEAD_TILE"

note "8 Tabs and History from the Lynket options (extra evidence)"
open_link "https://example.com/?di=8" entry-8-tabs
flow -e ITEM="Tabs" -e NAME="entry-8-tabs" "$capture/options-item.yaml" > "$OUT/entry-8-tabs.maestro.txt" 2>&1
open_link "https://example.com/?di=8h" entry-8-history
flow -e ITEM="History" -e NAME="entry-8-history" "$capture/options-item.yaml" > "$OUT/entry-8-history.maestro.txt" 2>&1

a logcat -d > "$OUT/logcat-all.txt"
a logcat -d -b crash > "$OUT/logcat-crash.txt"
grep -E "UninitializedPropertyAccessException|must be attached to an @AndroidEntryPoint|Hilt|MissingBinding|lateinit property .* has not been initialized" \
  "$OUT/logcat-all.txt" > "$OUT/logcat-di-grep.txt" || true
note "done: $(wc -l < "$OUT/logcat-di-grep.txt") DI-related log lines, $(grep -c "$PKG" "$OUT/logcat-crash.txt") crash lines for $PKG"
