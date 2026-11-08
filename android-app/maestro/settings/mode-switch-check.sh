#!/usr/bin/env bash
# Device check of a person switching between slide over, web heads and native bubbles, on the
# debug build. One mode per run:
#   native   On a fresh install, Native Bubbles (Beta) is chosen and its warning closed; Look &
#            feel and Browsing options show the Web heads rows grey under the note "Only used in
#            web heads mode", and Minimize behavior grey with "Minimize always opens a bubble"
#            (mode-switch-native.yaml). The same flow runs again at the largest font size (200 %)
#            into font-200/; the font size is restored on exit.
#   back     From a settings file in native bubble mode with Minimize set to open a web head,
#            Web heads is chosen: every web head row is on, no note, and Minimize shows the stored
#            choice again (mode-switch-back.yaml); the store then holds web heads on, native
#            bubbles off and Minimize 2.
#   charter  The whole loop from a settings file restored with both floating modes on: one mode,
#            Web heads, shows and is stored with the Minimize choice kept
#            (mode-switch-restored.yaml); then a link in each mode: slide over (a tab, no head, no
#            bubble), web heads with aggressive loading, a native bubble and Minimize to a bubble
#            (webheads-check.sh aggressive, bubble, bubble-minimize); then the native and back
#            flows with their stored values; then a scan of the app source for a direct read of
#            the two mode switches (when run from a checkout); last, no crash. Chrome's first-run
#            screen is turned off for the run and turned back on at the end.
#   sheet    From a slide over settings file of an older release with no sheet option stored
#            (seed/slide-over-updated.xml): "Open as a sheet" shows off under Slide over and "Side
#            sheet on wide screens" does not show; a tap turns the sheet on and the side sheet
#            option shows, on (sheet-rows.yaml); the store then holds both options on; last, no
#            crash.
#
# Usage: maestro/settings/mode-switch-check.sh <serial> <evidence-dir> <native|back|charter|sheet>
#        (from android-app/, with maestro on PATH)
# Installs the debug build over the current one; every mode clears the app's data.
set -euo pipefail

USAGE="usage: mode-switch-check.sh <serial> <evidence-dir> <native|back|charter|sheet>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
WHICH="${3:?$USAGE}"
case "$WHICH" in
  native | back | charter | sheet) ;;
  *) echo "unknown mode: $WHICH; $USAGE" >&2; exit 2 ;;
esac
source "$(dirname "$0")/../webheads/lib.sh"
HERE_SETTINGS="$(cd "$(dirname "$0")" && pwd)"
APP_ROOT="$(cd "$HERE_SETTINGS/../.." && pwd)"
APK="$APP_ROOT/lynket/build/outputs/apk/debug/lynket-debug.apk"
SEED="$HERE_SETTINGS/../seed"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$WHICH"
CHECK_NAME="mode switch $WHICH check"
: > "$OUT/$WHICH.txt"

flow() {  # $1 = flow file, $2 = evidence folder (default $OUT)
  local out="${2:-$OUT}"
  mkdir -p "$out"
  maestro --device "$SERIAL" test -e OUT="$out" --test-output-dir "$out/maestro" \
    "$(hostpath "$HERE_SETTINGS/$1")" | tee -a "$out/maestro.txt"
}

# Before a flow: a cold start on a busy emulator can raise a late "isn't responding" dialog.
settle() {
  sleep 8
  dismiss_anr
  sleep 2
  dismiss_anr
}

# Taps the node whose test tag is $1 when it shows in a UI dump; fails quietly when it does not.
tap_tag() {
  local xy
  a shell uiautomator dump /sdcard/mode-switch-ui.xml >/dev/null 2>&1 || true
  xy="$(a exec-out cat /sdcard/mode-switch-ui.xml | tr -d '\r' | tr '>' '\n' |
    grep -F "resource-id=\"$1\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }')"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  a shell input tap $xy
}

# A fresh install opens on the intro with the changelog under it: Skip, then OK.
pass_first_run() {
  local _
  for _ in $(seq 1 5); do
    tap_tag intro-skip && { sleep 2; break; }
    sleep 2
  done
  if [ -n "$(node_centre "Changelog")" ]; then tap_text "OK" || true; sleep 1; fi
}

# The settings screen in front, on a fresh back stack.
open_settings() {
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" Settings >/dev/null
  sleep 3
  if [ -n "$(node_centre "Changelog")" ]; then tap_text "OK" || true; sleep 1; fi
  settle
}

# Fails unless the stored value of setting $1 is $2; $3 names the dump file.
expect_setting() {
  local got
  got="$(stored_setting "$1" "$3")"
  log "stored $1 = $got"
  [ "$got" = "$2" ] || fail "stored $1 is \"$got\", expected \"$2\" (see $3)"
}

install_debug() {
  [ -f "$APK" ] || fail "missing $APK; run ./gradlew :lynket:assembleDebug"
  a install -r "$(hostpath "$APK")" >/dev/null
}

# Font size, Chrome's first-run screen: restored on exit.
FONT_CHANGED=""
CHROME_CHANGED=""
CHROME_DEBUG_BEFORE=""
on_exit() {
  [ -n "$FONT_CHANGED" ] && a shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  if [ -n "$CHROME_CHANGED" ]; then
    a shell rm -f /data/local/tmp/chrome-command-line >/dev/null 2>&1 || true
    if [ -n "$CHROME_DEBUG_BEFORE" ] && [ "$CHROME_DEBUG_BEFORE" != "null" ]; then
      a shell am set-debug-app --persistent "$CHROME_DEBUG_BEFORE" >/dev/null 2>&1 || true
    else
      a shell am clear-debug-app >/dev/null 2>&1 || true
    fi
  fi
}
trap on_exit EXIT

# Chrome reads its command line from /data/local/tmp only while it is the debug app; these flags
# skip its first-run screen, which otherwise covers the tab a link opens.
chrome_without_first_run() {
  a shell pm list packages com.android.chrome | grep -q "package:com.android.chrome" || return 0
  CHROME_DEBUG_BEFORE="$(a shell settings get global debug_app | tr -d '\r')"
  CHROME_CHANGED=1
  a shell am set-debug-app --persistent com.android.chrome >/dev/null
  a shell "echo 'chrome --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line"
  a shell am force-stop com.android.chrome
  log "Chrome first-run screen off for this run"
}

# Native Bubbles (Beta) chosen on a fresh install, notifications allowed and every conversation
# allowed to bubble.
native_pass() {  # $1 = evidence folder
  local out="$1"
  mkdir -p "$out"
  a shell am force-stop "$PKG"
  a shell pm clear "$PKG" >/dev/null
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  a shell cmd notification set_bubbles "$PKG" 1
  a shell am start -W -n "$PKG/$MAIN" >/dev/null
  settle
  pass_first_run
  open_settings
  flow mode-switch-native.yaml "$out" || fail "the native bubble flow failed (see $out/maestro.txt)"
}

# The native bubble flow, then the same flow at the largest font size (200 %).
run_native() {  # $1 = evidence folder
  native_pass "$1"
  log "native bubble mode: web head rows grey under the note, Minimize grey with its bubble summary"
  FONT_CHANGED=1
  a shell settings put system font_scale 2.0
  native_pass "$1/font-200"
  a shell settings put system font_scale 1.0
  FONT_CHANGED=""
  log "native bubble mode at 200 % font: same rows and texts"
}

# Back to web heads from a native bubble set whose Minimize choice is to open a web head.
run_back() {  # $1 = evidence folder
  local out="$1" saved="$OUT"
  mkdir -p "$out"
  seed_settings "$(hostpath "$SEED/mode-native-minimize.xml")"
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  open_settings
  flow mode-switch-back.yaml "$out" || fail "the way back to web heads failed (see $out/maestro.txt)"
  OUT="$out"
  expect_setting webhead_enabled_pref true back-settings.tsv
  expect_setting native_bubbles_preference false back-settings.tsv
  expect_setting minimize_behavior_preference 2 back-settings.tsv
  OUT="$saved"
  log "back to web heads: every row on, no note, Minimize choice kept"
}

# The sheet options of slide over, for a person who updates with no sheet option stored.
run_sheet() {
  seed_settings "$(hostpath "$SEED/slide-over-updated.xml")"
  open_settings
  flow sheet-rows.yaml || fail "the sheet options flow failed (see maestro.txt)"
  expect_setting custom_tab_sheet true sheet-settings.tsv
  expect_setting custom_tab_side_sheet true sheet-settings.tsv
  a logcat -d -b crash | tr -d '\r' > "$OUT/crash.txt"
  if grep -qF "$PKG" "$OUT/crash.txt"; then fail "the crash buffer has an entry for $PKG (see crash.txt)"; fi
  log "sheet options: off under slide over, the side sheet option shows on when the sheet turns on, both stored"
}

# A source line that reads one of the two mode switches outside the settings store and its rules.
source_scan() {  # $1 = output file
  local repo
  repo="$(git -C "$APP_ROOT" rev-parse --show-toplevel 2>/dev/null)" || { log "no checkout: source scan skipped"; return 0; }
  git -C "$repo" grep -nE "\.(webHeads|nativeBubbles)\(|webhead_enabled_pref|native_bubbles_preference|webHeadsEnabled" -- \
    android-app/lynket/src/main \
    ":!android-app/lynket/src/main/java/arun/com/chromer/settings/Preferences.kt" \
    ":!android-app/lynket/src/main/java/arun/com/chromer/ui/settings/SettingsKeyTable.kt" \
    ":!android-app/lynket/src/main/java/arun/com/chromer/data/preferences/SettingsSchema.kt" \
    ":!android-app/lynket/src/main/java/arun/com/chromer/data/preferences/UserPreferencesRepository.kt" \
    ":!android-app/lynket/src/main/java/arun/com/chromer/data/preferences/BrowsingModeMigration.kt" > "$1" || true
  [ ! -s "$1" ] || fail "a caller reads a mode switch directly (see $1)"
  log "source scan: no direct read of the mode switches"
}

run_charter() {
  chrome_without_first_run

  # 1. The restored set with both floating modes on.
  local part="$OUT/restored"
  mkdir -p "$part"
  seed_settings "$(hostpath "$SEED/mode-both-on.xml")"
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  a shell cmd notification set_bubbles "$PKG" 1
  open_settings
  flow mode-switch-restored.yaml "$part" || fail "the restored set flow failed (see restored/maestro.txt)"
  expect_setting webhead_enabled_pref true restored-settings.tsv
  expect_setting native_bubbles_preference false restored-settings.tsv
  expect_setting minimize_behavior_preference 2 restored-settings.tsv
  log "step 1: the restored set shows and keeps one mode, web heads, and the Minimize choice"

  # 2. Slide over: a link opens in a tab, with no head and no bubble.
  local link="https://example.com/?flow=slide"
  choose_mode "Slide over"
  a shell input keyevent HOME
  sleep 1
  a logcat -c
  open_link "$link"
  sleep 8
  shot slide-over-page
  app_logcat slide-over
  a logcat -d | tr -d '\r' | grep -F "Open $link: mode=SlideOver" > "$OUT/slide-over-route.txt" ||
    fail "no slide over router line for $link (see slide-over.logcat.txt)"
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | tee "$OUT/slide-over-activity.txt" |
    grep -qE "CustomTabActivity|WebViewActivity|com.android.chrome" || fail "the slide over link did not open a tab (see slide-over-activity.txt)"
  [ "$(overlay_count)" -eq 0 ] || fail "a web head shows in slide over mode"
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/slide-over-notifications.txt"
  [ -z "$(bubble_flag_lines "$OUT/slide-over-notifications.txt")" ] || fail "a bubble shows in slide over mode"
  a shell input keyevent HOME
  log "step 2: slide over opens the link in a tab, no head, no bubble"

  # 3. Web heads with aggressive loading, a native bubble, and Minimize to a bubble.
  local check
  for check in aggressive bubble bubble-minimize; do
    bash "$HERE/webheads-check.sh" "$SERIAL" "$OUT/$check" "$check" || fail "the $check check failed (see $check/)"
    log "step 3: $check passed"
  done

  # 4. The settings screens in native bubble mode, then the way back to web heads.
  run_native "$OUT/native"
  log "step 4: native bubble screens passed"
  run_back "$OUT/back"
  log "step 5: the way back to web heads passed"

  # 6. No direct read of the mode switches in the app source.
  source_scan "$OUT/source-scan.txt"

  # 7. No crash.
  a logcat -d -b crash | tr -d '\r' > "$OUT/crash.txt"
  if grep -qF "$PKG" "$OUT/crash.txt"; then fail "the crash buffer has an entry for $PKG (see crash.txt)"; fi
  log "step 7: no crash"
}

install_debug
a logcat -b crash -c
case "$WHICH" in
  native) run_native "$OUT" ;;
  back) run_back "$OUT" ;;
  charter) run_charter ;;
  sheet) run_sheet ;;
esac
log "$CHECK_NAME passed"
