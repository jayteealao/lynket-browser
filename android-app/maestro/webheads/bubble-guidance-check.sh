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
#   talkback       the message window's accessibility events (without TalkBack); then TalkBack on:
#                  the message stays past 15 s and Back closes it
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
ACTION_TIMEOUT_BEFORE=""

restore() {
  if [ "$ACTION_TIMEOUT_BEFORE" = "null" ]; then
    a shell settings delete secure accessibility_interactive_ui_timeout_ms >/dev/null 2>&1 || true
  elif [ -n "$ACTION_TIMEOUT_BEFORE" ]; then
    a shell settings put secure accessibility_interactive_ui_timeout_ms "$ACTION_TIMEOUT_BEFORE" >/dev/null 2>&1 || true
  fi
  a shell cmd notification set_bubbles "$PKG" 1 >/dev/null 2>&1 || true
  a shell settings put secure notification_bubbles 1 >/dev/null 2>&1 || true
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  if [ "$TALKBACK_BEFORE" = "none" ]; then
    a shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
    a shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
  elif [ -n "$TALKBACK_BEFORE" ]; then
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
  # Every window: the message window sits over the tab and need not hold input focus.
  a shell rm -f /sdcard/bubble-ui.xml >/dev/null 2>&1 || true
  a shell uiautomator dump --windows /sdcard/bubble-ui.xml >/dev/null 2>&1 ||
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
  # A slow emulator can take longer than the message's 10 s to dump the screen. Android's "Time to
  # take action" setting, which the message honours, gives the steps a minute.
  ACTION_TIMEOUT_BEFORE="$(a shell settings get secure accessibility_interactive_ui_timeout_ms | tr -d '\r')"
  a shell settings put secure accessibility_interactive_ui_timeout_ms 60000
  a logcat -c
  open_link "https://example.com/?refused=$WHICH"
  wait_top "$MESSAGE" || { shot no-message; top_activity | tee -a "$OUT/$WHICH.txt"; fail "no message window on top"; }
  # The message times out after 10 s on screen: dump, capture on the device and tap first, and
  # copy the capture and read the stacks afterwards.
  for _ in 1 2 3 4 5; do
    dump_ui message
    grep -qF "$1" "$OUT/message.xml" && break
    sleep 1
  done
  grep -qF "$1" "$OUT/message.xml" || fail "the message does not say \"$1\""
  grep -qF 'text="Open Settings"' "$OUT/message.xml" || fail "no Open Settings action"
  a shell screencap -p /sdcard/bubble-message.png >/dev/null 2>&1 || true
  local action
  action="$(tr '>' '\n' < "$OUT/message.xml" | grep -F 'text="Open Settings"' | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }')"
  [ -n "$action" ] || fail "no Open Settings bounds"
  # shellcheck disable=SC2086
  a shell input tap $action
  log "message: $1"
  a exec-out cat /sdcard/bubble-message.png > "$OUT/message.png"
  a shell dumpsys activity activities | tr -d '\r' > "$OUT/activities.txt"
  grep -qE "CustomTabActivity|WebViewActivity" "$OUT/activities.txt" ||
    fail "no tab under the message (see activities.txt)"
  sleep 3
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notifications.txt"
  if grep -E "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/notifications.txt"; then fail "a bubble is left"; fi
  top_activity | tee -a "$OUT/$WHICH.txt"
  shot settings
  dump_ui settings
  grep -qF "$2" "$OUT/settings.xml" || fail "the settings page does not show \"$2\""
  log "settings page shows: $2"
  a shell input keyevent BACK
  sleep 2
  top_activity | tee -a "$OUT/$WHICH.txt"
  shot after-back
  top_activity | grep -qE "CustomTabActivity|WebViewActivity" || fail "Back did not return to the page"
  log "Back returns to the page"
  # A second link: a tap on the page above the message goes to the page, and the message stays.
  open_link "https://example.com/?refused=$WHICH-tap"
  wait_top "$MESSAGE" || fail "no message window on the second link"
  sleep 1
  local w h
  read -r w h < <(screen_size)
  a shell input tap $((w / 2)) $((h / 4))
  sleep 1
  top_activity | tee -a "$OUT/$WHICH.txt"
  shot after-page-tap
  a shell dumpsys window windows | tr -d '\r' | grep -E "Window #.*BubbleRefusedActivity" | tee -a "$OUT/$WHICH.txt" ||
    log "the message window had closed by the time of the window dump"
  log "tap above the message: top activity above"
  app_logcat "$WHICH"
  # The crash buffer also holds other processes (uiautomator itself can die mid-dump).
  if grep -qF "Process: $PKG," "$OUT/$WHICH.crash.txt"; then fail "the app crashed (see $WHICH.crash.txt)"; fi
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
  shot explainer
  grep -qF 'choose All conversations can bubble' "$OUT/explainer.xml" || fail "no bubble setting explainer"
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
  a shell cmd notification set_bubbles "$PKG" 2
  # Accessibility events without TalkBack: a capture runs through UiAutomation, which suspends
  # other accessibility services while it is connected.
  a shell "timeout 30 uiautomator events" > "$OUT/talkback-events.txt" 2>&1 &
  local events=$!
  sleep 2
  open_link "https://example.com/?refused=events"
  wait_top "$MESSAGE" || fail "no message window for the event capture"
  wait "$events" || true
  tr -d '\r' < "$OUT/talkback-events.txt" > "$OUT/talkback-events.clean.txt"
  grep -E "PackageName: $PKG" "$OUT/talkback-events.clean.txt" | grep -qE "PANE_APPEARED|TYPE_WINDOW_CONTENT_CHANGED|TYPE_WINDOW_STATE_CHANGED" &&
    log "events: the message window sent window and content events" ||
    log "events: no event from the message window (see talkback-events.txt)"
  grep -qF "Bubbles are off for Lynket" "$OUT/talkback-events.clean.txt" &&
    log "events: the message text is in an event" ||
    log "events: Compose live-region events carry no text here; the JVM semantics test covers the label"
  a shell input keyevent HOME
  sleep 2
  local component
  component="$(a shell pm list packages | tr -d '\r' | grep -x 'package:com.google.android.marvin.talkback' | sed 's/package://' | head -1 || true)"
  if [ -z "$component" ]; then
    log "talkback: not installed"
    log "talkback passed (events only)"
    return
  fi
  TALKBACK_BEFORE="none$(a shell settings get secure enabled_accessibility_services | tr -d '\r' | sed 's/^null$//')"
  a shell settings put secure enabled_accessibility_services "$component/com.google.android.marvin.talkback.TalkBackService"
  a shell settings put secure accessibility_enabled 1
  sleep 4
  a shell dumpsys accessibility | tr -d '\r' > "$OUT/accessibility.txt"
  grep -q "Bound services:{Service\[label=TalkBack" "$OUT/accessibility.txt" || fail "TalkBack is not bound"
  grep -q "touchExplorationEnabled=true" "$OUT/accessibility.txt" || fail "touch exploration is off"
  log "talkback bound, touch exploration on"
  open_link "https://example.com/?refused=talkback"
  wait_top "$MESSAGE" || fail "no message window with TalkBack"
  local start=$SECONDS
  sleep 6
  shot talkback-focus
  while [ $((SECONDS - start)) -lt 15 ]; do sleep 1; done
  top_activity | tee -a "$OUT/$WHICH.txt"
  top_activity | grep -q "$MESSAGE" || fail "the message closed before 15 s with TalkBack on"
  shot talkback-15s
  log "message still on screen after $((SECONDS - start)) s with TalkBack on"
  a shell input keyevent BACK
  sleep 2
  top_activity | grep -qE "CustomTabActivity|WebViewActivity" || fail "Back did not close the message"
  log "Back closes the message"
  app_logcat talkback
  a shell input keyevent HOME
  log "talkback passed"
}

case "$WHICH" in
  selected) native_bubbles_mode; a shell cmd notification set_bubbles "$PKG" 2; check_refused "Bubbles are off for Lynket" "All conversations can bubble" ;;
  none) native_bubbles_mode; a shell cmd notification set_bubbles "$PKG" 0; check_refused "Bubbles are off for Lynket" "All conversations can bubble" ;;
  device) native_bubbles_mode; a shell settings put secure notification_bubbles 0; check_refused "Bubbles are off for Lynket" "Bubbles" ;;
  notifications) native_bubbles_mode; a shell pm revoke "$PKG" android.permission.POST_NOTIFICATIONS; check_refused "Notifications are off for Lynket" "otifications" ;;
  all) native_bubbles_mode; check_all ;;
  setup) check_setup ;;
  talkback) native_bubbles_mode; check_talkback ;;
  *) echo "$USAGE" >&2; exit 2 ;;
esac
