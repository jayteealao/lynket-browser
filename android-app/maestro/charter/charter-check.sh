#!/usr/bin/env bash
# The whole path of a 2.1.3 user who updates, on a phone and a fold, in one run:
#
#   1. upgrade-check.sh on the phone: 2.1.3 seeded (with a pinned shortcut), the new debug build
#      installed over it, every value read back
#   2. a link through BrowserInterceptActivity; the router logs it with its address
#   3. Custom Tab mode: "Slide over", AMP and article mode off, Chrome chosen on the provider
#      screen (so "Use WebView" is off); a link opens in a Chrome Custom Tab with the bottom bar
#   4. options-item.yaml: the Lynket options sheet from Chrome's menu
#   5. web heads through the permission explainer and the system screen (permission.yaml, the
#      grant through appops); the stored setting reads web heads on
#   6. drag-check.sh on the phone (the person watches drag.mp4 and signs feel-checklist.md)
#   7. webheads-check.sh ac23: a native bubble opens its page
#   8. History from the home drawer: the links of steps 2 to 7, newest first, and a 2.1.3 row
#   9. the 2.1.3 pinned shortcut tapped on the launcher opens its page
#   then on the fold: the 2.1.3 seed and update (upgrade-check.sh), step 6 and step 8 again
#
# Every step writes PASS or FAIL to charter.txt and saves the crash buffer. A failed step does not
# stop the later ones; the script exits 1 when any step failed.
#
# Usage: maestro/charter/charter-check.sh <phone-serial> <fold-serial> <evidence-dir>   (from android-app/)
# Needs: adb, Maestro, host sqlite3, the 2.1.3 APK (maestro/build-old-apk.sh) and the new debug
# APK (./gradlew :lynket:assembleDebug). Both devices are reinstalled, which clears the app's data.
set -uo pipefail

USAGE="usage: charter-check.sh <phone-serial> <fold-serial> <evidence-dir>"
PHONE="${1:?$USAGE}"
FOLD="${2:?$USAGE}"
OUT="${3:?$USAGE}"
SERIAL="$PHONE"
source "$(dirname "$0")/../webheads/lib.sh"
CHARTER="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
WEBHEADS="$(hostpath "$(cd "$CHARTER/../webheads" && pwd)")"
UPGRADE="$(hostpath "$(cd "$CHARTER/../upgrade" && pwd)")"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=charter
: > "$OUT/charter.txt"
FAILED=0
# The 2.1.3 seed's first link; the 2.1.3 shortcut creator pinned it as "Example Domain".
SEED_URL="https://example.com/"
PINNED_LABEL="Example Domain"

# Maestro's driver keeps the device's UI automation after a flow ends, and a later uiautomator
# dump then hangs; stopping the driver frees it.
free_ui_automation() {
  a shell am force-stop dev.mobile.maestro >/dev/null 2>&1 || true
  a shell am force-stop dev.mobile.maestro.test >/dev/null 2>&1 || true
}

# Centre of the first node whose resource id (a Compose test tag) is $1.
id_centre() {
  a shell uiautomator dump /sdcard/charter-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/charter-ui.xml | tr -d '\r' | tr '>' '\n' |
    grep -F "resource-id=\"$1\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}
tap_id() {
  local xy
  xy="$(id_centre "$1")"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  a shell input tap $xy
}

# The value of one settings key as stored, from the debug-only dump receiver.
setting() {  # $1 = key, $2 = dump file name
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  for _ in $(seq 1 40); do
    a logcat -d -s StoreDump:I | grep -q "done" && break
    sleep 0.5
  done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' > "$OUT/$2"
  awk -F'\t' -v k="$1" '$1 == k { print $3 }' "$OUT/$2"
}

# Runs step $1 (a function) on device $2 in a subshell, so a fail ends only that step; logs PASS
# or FAIL with the step's last line, and saves the crash buffer.
step() {
  local name="$1" serial="$2" result
  echo "== $name on $serial" >> "$OUT/charter.txt"
  adb -s "$serial" logcat -b crash -c >/dev/null 2>&1 || true
  ( SERIAL="$serial"; CHECK_NAME="$name"; set -e; "$name" )
  if [ $? -eq 0 ]; then result=PASS; else result=FAIL; fi
  adb -s "$serial" logcat -d -b crash | tr -d '\r' > "$OUT/$name-$serial.crash.txt"
  if grep -qF "$PKG" "$OUT/$name-$serial.crash.txt"; then
    result=FAIL
    echo "$name: the crash buffer holds an entry for the app" >> "$OUT/charter.txt"
  fi
  [ "$result" = PASS ] || FAILED=1
  echo "$result $name ($serial)" | tee -a "$OUT/charter.txt"
}

# 1. The 2.1.3 user updates.
step1_upgrade() {
  bash "$UPGRADE/upgrade-check.sh" "$SERIAL" "$OUT/1-upgrade-$SERIAL" || fail "the upgrade check failed (see 1-upgrade-$SERIAL/)"
  log "step 1: every 2.1.3 setting and per-app rule read back after the update"
}

# 2. A link reaches the intercept activity and the router.
step2_intercept() {
  a shell input keyevent HOME
  a logcat -c
  open_link "https://example.com/?charter=2"
  sleep 8
  shot 2-intercept
  a logcat -d | tr -d '\r' > "$OUT/2-intercept.logcat.txt"
  grep -F "$INTERCEPT" "$OUT/2-intercept.logcat.txt" | grep -q "START" || fail "BrowserInterceptActivity did not start"
  grep -F "Open https://example.com/?charter=2" "$OUT/2-intercept.logcat.txt" | tee -a "$OUT/charter.txt" | grep -q . ||
    fail "no router line for the link"
  close_all_heads
  a shell input keyevent HOME
  log "step 2: the link was intercepted and routed"
}

# 3. Custom Tab mode with Chrome, and a link in a Custom Tab with the bottom bar.
step3_custom_tab() {
  choose_mode "Slide over"
  # The 2.1.3 seed turns AMP and article mode on; they would send the link to the reader.
  [ "$(setting amp_mode_pref 3-settings-before.tsv)" = "true" ] && { tap_text "Accelerated Mobile Pages (AMP)"; sleep 1; }
  [ "$(setting article_mode_pref 3-settings-before.tsv)" = "true" ] && { tap_text "Article mode"; sleep 1; }
  [ "$(setting amp_mode_pref 3-settings-mode.tsv)" = "false" ] || fail "AMP mode is still on"
  [ "$(setting article_mode_pref 3-settings-mode.tsv)" = "false" ] || fail "article mode is still on"
  # Chrome on the provider screen; the 2.1.3 seed chose the web view.
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" ProviderSelection >/dev/null
  sleep 3
  shot 3-provider-before
  tap_id "provider-com.android.chrome" || fail "no Chrome row on the provider screen"
  sleep 2
  tap_id "provider-confirm" || fail "no Use button in the Chrome dialog"
  sleep 2
  shot 3-provider-after
  [ "$(setting use_webview_pref 3-settings-provider.tsv)" = "false" ] || fail "Use WebView is still on"
  a shell input keyevent HOME
  sleep 1
  a logcat -c
  open_link "https://example.com/?charter=3"
  sleep 10
  dismiss_anr
  shot 3-custom-tab
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | tee -a "$OUT/charter.txt" |
    grep -q "com.android.chrome" || fail "the link did not open in a Chrome Custom Tab"
  a logcat -d | tr -d '\r' | grep -F "Open https://example.com/?charter=3" > "$OUT/3-route.logcat.txt" || fail "no router line for the link"
  [ -n "$(node_centre "Minimize")" ] || fail "the Custom Tab shows no Lynket bottom bar"
  log "step 3: Custom Tab open with the bottom bar"
}

# 4. The Lynket options sheet from Chrome's menu (the tab of step 3 is still open).
step4_options() {
  maestro --device "$SERIAL" test -e OUT="$OUT/4-options" "$(hostpath "$WEBHEADS/../browsing/options-item.yaml")" \
    > "$OUT/4-options.maestro.txt" 2>&1 || { cat "$OUT/4-options.maestro.txt" >> "$OUT/charter.txt"; fail "options-item.yaml failed"; }
  free_ui_automation
  a shell input keyevent HOME
  log "step 4: the options sheet showed its seven items and opened Settings"
}

# 5. Web heads through the explainer, then the grant.
step5_permission() {
  a shell am force-stop "$PKG"
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW deny
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" BrowsingMode >/dev/null
  sleep 2
  maestro --device "$SERIAL" test --test-output-dir "$OUT/5-permission" "$(hostpath "$WEBHEADS/permission.yaml")" \
    > "$OUT/5-permission.maestro.txt" 2>&1 || { cat "$OUT/5-permission.maestro.txt" >> "$OUT/charter.txt"; fail "permission.yaml failed"; }
  free_ui_automation
  # The person switches "Allow display over other apps" on; appops does the same here.
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  a shell input keyevent BACK
  sleep 3
  tap_text "Not now" || true
  local on
  on="$(setting webhead_enabled_pref 5-settings.tsv)"
  log "step 5: webhead_enabled_pref after the grant = $on"
  [ "$on" = "true" ] || fail "web heads did not turn on after the grant"
  a shell input keyevent HOME
}

# 6. The drag recording (the drag benchmark is not repeated here).
step6_drag() {
  bash "$WEBHEADS/drag-check.sh" "$SERIAL" "$OUT/6-drag-$SERIAL" || fail "the drag check failed (see 6-drag-$SERIAL/)"
  close_all_heads
  a shell input keyevent HOME
  log "step 6: drag recorded; feel-checklist.md waits for the person's signature"
}

# 7. A native bubble.
step7_bubble() {
  bash "$WEBHEADS/webheads-check.sh" "$SERIAL" "$OUT/7-bubble" ac23 || fail "the native bubble check failed (see 7-bubble/)"
  a shell input keyevent HOME
  log "step 7: a native bubble opened its page"
}

# 8. History from the drawer; the rows are read from the database.
HISTORY_URLS=""
step8_history() {
  local tmp="$OUT/8-history-$SERIAL" prev=0 url at
  mkdir -p "$tmp"
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 >/dev/null
  sleep 3
  maestro --device "$SERIAL" test -e OUT="$tmp" -e NAME=history "$CHARTER/history-from-drawer.yaml" \
    > "$tmp/maestro.txt" 2>&1 || { cat "$tmp/maestro.txt" >> "$OUT/charter.txt"; fail "history-from-drawer.yaml failed"; }
  free_ui_automation
  for f in chromer.db chromer.db-wal chromer.db-shm; do
    a exec-out run-as "$PKG" sh -c "cat databases/$f 2>/dev/null" > "$tmp/$f" || true
  done
  sqlite3 -separator $'\t' "$tmp/chromer.db" "select url, created_at from history order by created_at desc;" |
    tr -d '\r' > "$tmp/rows.tsv" || fail "the history database could not be read"
  log "step 8: $(wc -l < "$tmp/rows.tsv") history rows on $SERIAL"
  grep -qxF "$SEED_URL" < <(cut -f1 "$tmp/rows.tsv") || fail "no 2.1.3 row ($SEED_URL) in history"
  # The links of this run, oldest first, each newer than the one before.
  for url in $HISTORY_URLS; do
    at="$(awk -F'\t' -v u="$url" '$1 == u { print $2 }' "$tmp/rows.tsv")"
    [ -n "$at" ] || fail "no history row for $url"
    [ "$at" -ge "$prev" ] || fail "$url is listed out of order"
    prev="$at"
    log "  $url $at"
  done
  log "step 8: history shows the 2.1.3 row and the links of this run in order"
}

# 9. The 2.1.3 pinned shortcut on the launcher.
step9_pinned() {
  a shell input keyevent HOME
  sleep 2
  shot 9-home
  a logcat -c
  tap_text "$PINNED_LABEL" || fail "no \"$PINNED_LABEL\" shortcut on the launcher"
  sleep 8
  dismiss_anr
  shot 9-opened
  a logcat -d | tr -d '\r' > "$OUT/9-pinned.logcat.txt"
  grep -E "START .*dat=https://example.com/" "$OUT/9-pinned.logcat.txt" | grep -qF "$INTERCEPT" ||
    fail "the shortcut did not open its link through Lynket"
  close_all_heads
  a shell input keyevent HOME
  log "step 9: the 2.1.3 pinned shortcut opened $SEED_URL"
}

step step1_upgrade "$PHONE"
step step2_intercept "$PHONE"
step step3_custom_tab "$PHONE"
step step4_options "$PHONE"
step step5_permission "$PHONE"
step step6_drag "$PHONE"
step step7_bubble "$PHONE"
HISTORY_URLS="https://example.com/?charter=2 https://example.com/?charter=3 https://example.com/?head=1 https://example.com/?head=6 https://example.com/?ac23=1"
step step8_history "$PHONE"
step step9_pinned "$PHONE"

# The fold: the 2.1.3 seed and update there, so its history has a 2.1.3 row, then steps 6 and 8.
step step1_upgrade "$FOLD"
step step6_drag "$FOLD"
HISTORY_URLS="https://example.com/?head=1 https://example.com/?head=6"
step step8_history "$FOLD"

echo "== summary" >> "$OUT/charter.txt"
grep -E "^(PASS|FAIL) " "$OUT/charter.txt" | sort | uniq -c >> "$OUT/charter.txt"
[ "$FAILED" -eq 0 ] && echo "charter check passed" | tee -a "$OUT/charter.txt" || echo "charter check failed" | tee -a "$OUT/charter.txt"
exit "$FAILED"
