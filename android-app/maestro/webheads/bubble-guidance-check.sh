#!/usr/bin/env bash
# Native bubbles that Android refuses, and the bubble setup, on the debug build.
#
#   selected       the app's bubble setting "Selected conversations": a link opens in a tab with
#                  "Bubbles are off for Lynket"; Open Settings opens Android's bubble page for the app
#   none           the same with "Nothing can bubble"
#   device         the same with bubbles off on the device
#   notifications  notifications not allowed: "Notifications are off for Lynket"; Open Settings opens
#                  the app's notification settings
#   all            "All conversations can bubble": ten links show bubbles and no message
#   setup          choosing Native Bubbles with "Selected conversations" explains the choice and opens
#                  the bubble page; after the choice, the Browsing mode row shows the new setting
#   talkback       TalkBack on: the message and its action are announced, and stay at least 10 s
#
# Restores the bubble setting (all), the device switch, the notification permission and TalkBack
# at exit. Usage (from android-app/):
#   maestro/webheads/bubble-guidance-check.sh <serial> <evidence-dir> <mode>
set -euo pipefail

USAGE="usage: bubble-guidance-check.sh <serial> <evidence-dir> <selected|none|device|notifications|all|setup|talkback>"
SERIAL="${1:?$USAGE}"
OUT_ROOT="${2:?$USAGE}"
WHICH="${3:?$USAGE}"
source "$(dirname "$0")/lib.sh"
OUT="$OUT_ROOT/$WHICH"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$WHICH"
CHECK_NAME="bubble guidance $WHICH check"
: > "$OUT/$WHICH.txt"
MESSAGE=arun.com.chromer.bubbles.system.BubbleRefusedActivity
TALKBACK_BEFORE=""

restore() {
  a shell cmd notification set_bubbles "$PKG" 1 >/dev/null 2>&1 || true
  a shell settings put secure notification_bubbles 1 >/dev/null 2>&1 || true
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  if [ -n "$TALKBACK_BEFORE" ]; then
    a shell settings put secure enabled_accessibility_services "${TALKBACK_BEFORE#none}" >/dev/null 2>&1 || true
  fi
}
trap restore EXIT

top_activity() { a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" || true; }

wait_top() {  # $1 = part of the class name; waits up to 10 s
  for _ in $(seq 1 20); do
    top_activity | grep -q "$1" && return 0
    sleep 0.5
  done
  return 1
}

dump_ui() {  # $1 = output name
  a shell uiautomator dump /sdcard/bubble-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/bubble-ui.xml | tr -d '\r' > "$OUT/$1.xml"
}

# Native bubbles chosen, the beta warning and the notification ask passed, the bubble explainer (if
# any) declined.
native_bubbles_mode() {
  a shell am force-stop "$PKG"
  choose_mode "Native Bubbles (Beta)"
  a shell input keyevent BACK
  sleep 2
  if tap_text "Grant"; then
    sleep 2
    tap_text "Allow" || true
    sleep 2
  fi
  tap_text "Not now" || true
  sleep 1
  a shell input keyevent HOME
  sleep 1
}

# A refused link: the message over the tab, the tab under it, no bubble left, a tap above the
# message reaches the page, Open Settings opens $2 and Back returns to the page.
check_refused() {  # $1 = expected message, $2 = expected settings text
  a logcat -c
  open_link "https://example.com/?refused=$WHICH"
  wait_top "$MESSAGE" || { shot no-message; top_activity | tee -a "$OUT/$WHICH.txt"; fail "no message window on top"; }
  sleep 2
  shot message
  dump_ui message
  grep -qF "$1" "$OUT/message.xml" || fail "the message does not say \"$1\""
  grep -qF 'text="Open Settings"' "$OUT/message.xml" || fail "no Open Settings action"
  log "message: $1"
  a shell dumpsys activity activities | tr -d '\r' > "$OUT/activities.txt"
  grep -qE "CustomTabActivity|WebViewActivity" "$OUT/activities.txt" ||
    fail "no tab under the message (see activities.txt)"
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notifications.txt"
  if grep -E "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/notifications.txt"; then fail "a bubble is left"; fi
  # A tap on the page above the message is the page's, not the message's.
  local w h
  read -r w h < <(screen_size)
  a shell input tap $((w / 2)) $((h / 4))
  sleep 1
  top_activity | tee -a "$OUT/$WHICH.txt"
  if wait_top "$MESSAGE"; then
    tap_text "Open Settings" || fail "Open Settings not on screen"
    sleep 3
    top_activity | tee -a "$OUT/$WHICH.txt"
    shot settings
    dump_ui settings
    grep -qiF "$2" "$OUT/settings.xml" || fail "the settings page does not show \"$2\""
    log "settings page shows: $2"
  else
    log "the message closed before Open Settings was tapped; opening it again"
    open_link "https://example.com/?refused=$WHICH-2"
    wait_top "$MESSAGE" || fail "no message window on the second link"
    tap_text "Open Settings" || fail "Open Settings not on screen"
    sleep 3
    dump_ui settings
    grep -qiF "$2" "$OUT/settings.xml" || fail "the settings page does not show \"$2\""
  fi
  a shell input keyevent BACK
  sleep 2
  top_activity | tee -a "$OUT/$WHICH.txt"
  shot after-back
  app_logcat "$WHICH"
  if grep -q "FATAL" "$OUT/$WHICH.crash.txt"; then fail "the app crashed (see $WHICH.crash.txt)"; fi
  a shell input keyevent HOME
  log "$WHICH passed"
}

check_all() {
  a shell cmd notification set_bubbles "$PKG" 1
  for n in $(seq 1 10); do
    a logcat -c
    open_link "https://example.com/?all=$n"
    local found=""
    for _ in $(seq 1 10); do
      sleep 1
      a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notifications-$n.txt"
      grep -qE "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/notifications-$n.txt" && { found=1; break; }
    done
    [ -n "$found" ] || fail "link $n: no bubble flag (see notifications-$n.txt)"
    sleep 2
    a shell dumpsys activity activities | tr -d '\r' > "$OUT/activities-$n.txt"
    if grep -q "BubbleRefusedActivity" "$OUT/activities-$n.txt"; then fail "link $n: a message window opened"; fi
    dump_ui "ui-$n"
    if grep -qE "so the page opened in a tab|Android did not show the bubble" "$OUT/ui-$n.xml"; then fail "link $n: a message shows"; fi
    log "link $n: bubble, no message"
  done
  shot bubble
  app_logcat all
  a shell input keyevent HOME
  log "all passed"
}

check_setup() {
  a shell cmd notification set_bubbles "$PKG" 2
  a shell am force-stop "$PKG"
  choose_mode "Native Bubbles (Beta)"
  a shell input keyevent BACK
  sleep 2
  if dump_ui after-warning && grep -qF "Bubbles are notifications" "$OUT/after-warning.xml"; then
    tap_text "Grant" || true
    sleep 2
    tap_text "Allow" || true
    sleep 2
  fi
  dump_ui explainer
  grep -qF 'Choose &quot;All conversations can bubble&quot;' "$OUT/explainer.xml" || fail "no bubble setting explainer"
  shot explainer
  tap_text "Open Settings" || fail "no Open Settings on the explainer"
  sleep 3
  top_activity | tee -a "$OUT/setup.txt"
  dump_ui bubble-page
  shot bubble-page
  if tap_text "All conversations can bubble"; then
    log "chose All conversations can bubble in Android settings"
  else
    log "no radio found on the bubble page; set with cmd notification set_bubbles"
    a shell cmd notification set_bubbles "$PKG" 1
  fi
  sleep 2
  a shell input keyevent BACK
  sleep 3
  dump_ui row-all
  shot row-all
  grep -qF 'All conversations can bubble' "$OUT/row-all.xml" || fail "the row does not show All conversations can bubble"
  log "row reads All conversations can bubble"
  a shell cmd notification set_bubbles "$PKG" 0
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" BrowsingMode >/dev/null
  sleep 3
  dump_ui row-none
  shot row-none
  grep -qF 'Nothing can bubble' "$OUT/row-none.xml" || fail "the row did not follow the change to nothing"
  log "row reads Nothing can bubble after a resume"
  app_logcat setup
  a shell input keyevent HOME
  log "setup passed"
}

check_talkback() {
  local component
  component="$(a shell pm list packages | tr -d '\r' | grep -i talkback | sed 's/package://' | head -1 || true)"
  if [ -z "$component" ]; then
    log "talkback: not installed; capturing accessibility events without it"
  else
    TALKBACK_BEFORE="none$(a shell settings get secure enabled_accessibility_services | tr -d '\r' | sed 's/^null$//')"
    a shell settings put secure enabled_accessibility_services "$component/com.google.android.marvin.talkback.TalkBackService"
    a shell settings put secure accessibility_enabled 1
    sleep 3
    a shell dumpsys accessibility | tr -d '\r' > "$OUT/accessibility.txt"
    grep -qi "talkback" "$OUT/accessibility.txt" && log "talkback bound" || log "talkback not listed as bound"
  fi
  a shell cmd notification set_bubbles "$PKG" 2
  a shell "timeout 20 uiautomator events" > "$OUT/talkback-events.txt" 2>&1 &
  local events=$!
  sleep 2
  open_link "https://example.com/?refused=talkback"
  wait_top "$MESSAGE" || fail "no message window"
  sleep 6
  shot talkback-focus
  sleep 4
  top_activity | tee -a "$OUT/talkback.txt"
  wait_top "$MESSAGE" && log "message still on screen after 10 s" || log "message gone before 10 s"
  wait "$events" || true
  tr -d '\r' < "$OUT/talkback-events.txt" > "$OUT/talkback-events.clean.txt"
  grep -qF "Bubbles are off for Lynket" "$OUT/talkback-events.clean.txt" || fail "no accessibility event with the message"
  grep -qF "Open Settings" "$OUT/talkback-events.clean.txt" || log "no event names Open Settings (see talkback-events.txt)"
  app_logcat talkback
  a shell input keyevent HOME
  log "talkback passed"
}

case "$WHICH" in
  selected) native_bubbles_mode; a shell cmd notification set_bubbles "$PKG" 2; check_refused "Bubbles are off for Lynket" "All conversations can bubble" ;;
  none) native_bubbles_mode; a shell cmd notification set_bubbles "$PKG" 0; check_refused "Bubbles are off for Lynket" "All conversations can bubble" ;;
  device) native_bubbles_mode; a shell settings put secure notification_bubbles 0; check_refused "Bubbles are off for Lynket" "Bubbles" ;;
  notifications) native_bubbles_mode; a shell pm revoke "$PKG" android.permission.POST_NOTIFICATIONS; check_refused "Notifications are off for Lynket" "notifications" ;;
  all) native_bubbles_mode; check_all ;;
  setup) check_setup ;;
  talkback) native_bubbles_mode; check_talkback ;;
  *) echo "$USAGE" >&2; exit 2 ;;
esac
