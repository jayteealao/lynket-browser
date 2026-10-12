#!/usr/bin/env bash
# Device check of the settings screens on the debug build:
#   - every settings screen opened by name through the main activity, captured at each scroll
#     position with its UI dump (<screen>-scroll-01.png ...)
#   - settings-screens.yaml: the root, browsing mode, look and feel (theme chips, colour sheet) and
#     browsing options rows
#   - per app settings: the loading list (with the debug slow-load fault), the full list, search
#     (per-app.yaml)
#   - provider selection with Chrome disabled: the no-provider state (provider-error.yaml); Chrome
#     is enabled again on exit, and a link then opens in a Custom Tab
#   - no crash in the crash buffer
#
# Usage: maestro/settings/settings-check.sh <serial> <evidence-dir>   (from android-app/)
# Installs the debug build over the current one (the app's data stays). Disables Chrome for the
# provider step and enables it again on exit.
set -euo pipefail

USAGE="usage: settings-check.sh <serial> <evidence-dir>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
HERE_SETTINGS="$(cd "$(dirname "$0")" && pwd)"
APK="$HERE_SETTINGS/../../lynket/build/outputs/apk/debug/lynket-debug.apk"
CHROME=com.android.chrome
FAULT_DIR=files/debug-faults
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=settings
CHECK_NAME="settings check"
: > "$OUT/settings.txt"

flow() {
  maestro --device "$SERIAL" test -e OUT="$OUT" --test-output-dir "$OUT/maestro" \
    "$(hostpath "$HERE_SETTINGS/$1")" | tee -a "$OUT/maestro.txt"
}

open_screen() {  # $1 = destination name
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" "$1" >/dev/null
  sleep 3
  dismiss_anr
}

# Before a flow: a cold start on a busy emulator can raise a late "isn't responding" dialog.
settle() {
  sleep 8
  dismiss_anr
  sleep 2
  dismiss_anr
}

ui_xml() {
  a shell rm -f /sdcard/settings-ui.xml
  a shell uiautomator dump /sdcard/settings-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/settings-ui.xml 2>/dev/null | tr -d '\r'
}

# Captures every scroll position of the screen in front, a screenshot and a UI dump each, until a
# swipe no longer changes the dump.
capture_scrolls() {  # $1 = name
  local n=1 before after w h tag
  read -r w h < <(screen_size)
  while [ "$n" -le 8 ]; do
    tag="$(printf '%s-scroll-%02d' "$1" "$n")"
    shot "$tag"
    before="$(ui_xml)"
    echo "$before" > "$OUT/$tag.xml"
    a shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 400
    sleep 2
    after="$(ui_xml)"
    [ "$before" = "$after" ] && break
    n=$((n + 1))
  done
  log "$1: $n scroll position(s)"
}

# Taps the node with this text once it shows; a busy emulator draws a dialog seconds late.
wait_tap() {  # $1 = text
  local _
  for _ in $(seq 1 10); do
    tap_text "$1" && return 0
    sleep 2
  done
  return 1
}

restore_chrome() { a shell pm enable --user 0 "$CHROME" >/dev/null 2>&1 || true; }
clear_fault() { a shell run-as "$PKG" rm -f "$FAULT_DIR/per-app-slow-load" >/dev/null 2>&1 || true; }
on_exit() { restore_chrome; clear_fault; }
trap on_exit EXIT

# 1. The debug build, data kept.
[ -f "$APK" ] || fail "missing $APK; run ./gradlew :lynket:assembleDebug"
a install -r "$(hostpath "$APK")" >/dev/null
a logcat -c
a logcat -b crash -c

# 2. Every screen at every scroll position.
for screen in Settings BrowsingMode LookAndFeel BrowsingOptions PerApp ProviderSelection; do
  open_screen "$screen"
  capture_scrolls "$(echo "$screen" | sed -E 's/([a-z])([A-Z])/\1-\2/g' | tr 'A-Z' 'a-z')"
done

# 3. The rows and their dialogs, from a fresh stack (step 2 left each screen scrolled).
a shell am force-stop "$PKG"
open_screen Settings
settle
flow settings-screens.yaml

# 4. Per app settings: the loading list first (slow-load fault), then the list and search.
# One string: adb joins its arguments and the device shell splits them again. 25 s, because a
# cold start on a busy emulator takes up to 13 s and a shorter delay ends before the first dump.
a shell "run-as $PKG sh -c 'mkdir -p $FAULT_DIR && echo 25000 > $FAULT_DIR/per-app-slow-load'"
a shell am force-stop "$PKG"
open_screen PerApp
# A slow cold start can still show the splash, or fail the dump: poll until the screen draws, and
# fail only when the app rows show without the loading list.
for _ in $(seq 1 10); do
  ui_xml > "$OUT/per-app-loading.xml"
  grep -q -e 'resource-id="loading-list"' -e 'resource-id="app-' "$OUT/per-app-loading.xml" && break
  sleep 2
done
shot per-app-loading
grep -q 'resource-id="loading-list"' "$OUT/per-app-loading.xml" || fail "no loading list while the apps load"
clear_fault
a shell pm list packages | tr -d '\r' | sed 's/^package://' | sort > "$OUT/packages.txt"
log "packages on the device: $(wc -l < "$OUT/packages.txt")"
settle
flow per-app.yaml

# 5. No provider: Chrome disabled, the error state offers WebView or Chrome.
a shell pm disable-user --user 0 "$CHROME" >/dev/null
a shell am force-stop "$PKG"
open_screen ProviderSelection
settle
shot provider-error
flow provider-error.yaml
restore_chrome
log "Chrome enabled again"

# 6. With Chrome back, Chrome is chosen again (the error flow chose WebView) and a link opens in
#    a Custom Tab.
open_screen ProviderSelection
wait_tap "Chrome" || fail "no Chrome row after enabling it"
wait_tap "Use" || fail "no Use button in the Chrome details"
sleep 2
open_link "https://example.com/"
sleep 6
a shell dumpsys activity activities | tr -d '\r' | grep -m1 -E "topResumedActivity|mResumedActivity" > "$OUT/link-activity.txt" || true
log "after the link: $(cat "$OUT/link-activity.txt")"
grep -q -e "$CHROME" -e "CustomTab" "$OUT/link-activity.txt" || fail "the link did not open in a Custom Tab"
shot link-custom-tab

# 7. No crash.
a logcat -d -b crash | tr -d '\r' > "$OUT/logcat-crash.txt"
if grep -q "$PKG" "$OUT/logcat-crash.txt"; then fail "the crash buffer holds an entry for $PKG"; fi
log "settings check passed"
