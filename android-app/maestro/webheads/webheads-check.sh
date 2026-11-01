#!/usr/bin/env bash
# Web head device checks on the debug build.
#
#   head  web heads mode with the overlay permission: a link shows a Compose head and the trash
#         window, logcat has no view tree owner error, and stopping the service removes them
#   permission  without the permission the explainer comes first and leads to the system screen; after
#         a process kill a link starts a special-use foreground service; with the start forced to
#         fail (debug marker file) the link opens in a Custom Tab with a message
#   bubble  native bubbles mode: the app's notification request, bubbles allowed for the app, then a
#         link shows a system bubble that opens the page
#   refused  web heads chosen, then the overlay permission denied: a link opens in a tab, no settings
#         page opens and no head shows
#   restart  web heads chosen: a start of the service with no link stops it with no notification; then
#         one head open, the app's process killed, and after Android restarts the service no web
#         heads notification stays and no head shows
#
# Usage: maestro/webheads/webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart>   (from android-app/)
set -euo pipefail

SERIAL="${1:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart>}"
OUT="${2:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart>}"
WHICH="${3:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart>}"
source "$(dirname "$0")/lib.sh"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$WHICH"
CHECK_NAME="web heads $WHICH check"
: > "$OUT/$WHICH.txt"

# Starts web heads mode: overlay allowed, the "Web heads" row chosen, a notification explainer
# (Android 13 and later, notifications not yet allowed) declined.
web_heads_mode() {
  a shell am force-stop "$PKG"
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  choose_mode "Web heads"
  tap_text "Not now" || true
  a shell input keyevent HOME
  sleep 1
}

check_head() {
  web_heads_mode
  a logcat -c
  open_link "https://example.com/?headcheck=1"
  sleep 5
  shot head-head
  overlay_windows > "$OUT/head-windows-running.txt"
  local running
  running="$(overlay_count)"
  log "overlay windows while running: $running"
  [ "$running" -ge 2 ] || fail "expected a head window and the trash window, found $running"
  a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' > "$OUT/head-service.txt"
  app_logcat head
  if grep -E "ViewTreeLifecycleOwner|SavedStateRegistryOwner|ViewModelStoreOwner|FATAL" "$OUT/head.logcat.txt" "$OUT/head.crash.txt"; then
    fail "view tree owner error or crash (see head.logcat.txt, head.crash.txt)"
  fi
  close_all_heads
  overlay_windows > "$OUT/head-windows-stopped.txt"
  local stopped
  stopped="$(overlay_count)"
  log "overlay windows after the service stopped: $stopped"
  [ "$stopped" -eq 0 ] || fail "$stopped overlay windows left after the service stopped"
  log "head passed"
}

check_permission() {
  a shell am force-stop "$PKG"
  a logcat -c
  # 1. No permission: the explainer, then the system screen.
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW deny
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" BrowsingMode >/dev/null
  sleep 2
  if command -v maestro >/dev/null; then
    maestro --device "$SERIAL" test --test-output-dir "$OUT/permission-flow" "$(hostpath "$HERE/permission.yaml")" | tee -a "$OUT/permission.txt"
  else
    tap_text "Web heads" || fail "no Web heads row"
    sleep 2
    shot permission-explainer
    [ -n "$(node_centre "Allow web heads to float")" ] || fail "the explainer did not show"
    tap_text "Grant" || fail "no Grant button"
    sleep 3
    shot permission-system-screen
  fi
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | tee -a "$OUT/permission.txt"
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  a shell input keyevent BACK
  sleep 2
  tap_text "Not now" || true
  app_logcat permission-permission
  [ ! -s "$OUT/permission-permission.crash.txt" ] || fail "crash during the permission flow (see permission-permission.crash.txt)"

  # 2. Process killed: a link starts the foreground service of type specialUse.
  a shell input keyevent HOME
  sleep 1
  a shell am kill "$PKG"
  a logcat -c
  open_link "https://example.com/?permission=1"
  # A loaded device can take well over 5 s to start the process; poll for up to 60 s.
  for _ in $(seq 1 60); do
    a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' > "$OUT/permission-service.txt"
    grep -q "isForeground=true" "$OUT/permission-service.txt" && break
    sleep 1
  done
  shot permission-head
  grep -qE "isForeground=true" "$OUT/permission-service.txt" || fail "the web head service is not in the foreground"
  grep -qE "(foregroundServiceType|types)=0x40000000" "$OUT/permission-service.txt" || fail "the service type is not specialUse"
  log "foreground service: specialUse"

  # 3. Start refused (debug marker file): the link opens in a Custom Tab with a message.
  close_all_heads
  a shell run-as "$PKG" sh -c "'mkdir -p files/debug-faults && touch files/debug-faults/webhead-start-fail'"
  # Never leave the forced refusal behind: a failed check would make every later start refuse.
  trap 'a shell run-as "$PKG" rm -f files/debug-faults/webhead-start-fail' EXIT
  a shell input keyevent HOME
  sleep 1
  a shell am kill "$PKG"
  a logcat -c
  open_link "https://example.com/?permission=2"
  for _ in $(seq 1 60); do
    a logcat -d | grep -q "Web heads could not start" && break
    sleep 1
  done
  shot permission-fallback
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | tee -a "$OUT/permission.txt"
  app_logcat permission-fallback
  a shell run-as "$PKG" rm -f files/debug-faults/webhead-start-fail
  grep -q "Web heads could not start" "$OUT/permission-fallback.logcat.txt" || fail "no start refusal in logcat"
  [ ! -s "$OUT/permission-fallback.crash.txt" ] || fail "crash during the fallback (see permission-fallback.crash.txt)"
  [ "$(overlay_count)" -eq 0 ] || fail "a head showed although the start was refused"
  log "permission passed"
  a shell input keyevent HOME
}

check_bubble() {
  a shell am force-stop "$PKG"
  choose_mode "Native Bubbles (Beta)"
  # The legacy guide dialog first; dismissing it brings the notification explainer.
  a shell input keyevent BACK
  sleep 2
  if tap_text "Grant"; then
    sleep 2
    shot bubble-notification-request
    tap_text "Allow" || fail "no system notification request"
    sleep 1
  fi
  a shell input keyevent HOME
  # The per-app "all conversations can bubble" switch a person sets in system settings.
  a shell cmd notification set_bubbles "$PKG" 1
  a logcat -c
  open_link "https://example.com/?bubble=1"
  for _ in $(seq 1 10); do
    sleep 2
    a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/bubble-notifications.txt"
    grep -qE "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/bubble-notifications.txt" && break
  done
  grep -E "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/bubble-notifications.txt" | tee -a "$OUT/bubble.txt" ||
    fail "no notification of the app carries the bubble flag (see bubble-notifications.txt)"
  sleep 2
  shot bubble-bubble
  # The bubble is drawn by the system shell and is missing from UI dumps; its stack position is
  # in the shell's dump (top left of the bubble, px).
  local xy
  xy="$(a shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | tr -d '\r' |
    sed -nE 's/.*currentStackPos: *PointF\((-?[0-9.]+), *(-?[0-9.]+)\).*/\1 \2/p' | head -1 |
    awk 'NF == 2 { x = $1 < 0 ? 0 : $1; print int(x + 70), int($2 + 70) }' || true)"
  [ -n "$xy" ] || fail "no bubble stack on screen (see bubble-bubble.png)"
  log "bubble at $xy"
  # The first bubble ever shown brings the system's introduction, which takes the first tap.
  local opened=""
  for _ in 1 2 3; do
    # shellcheck disable=SC2086
    a shell input tap $xy
    sleep 4
    if a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | grep -q "EmbeddableWebViewActivity"; then
      opened=1
      break
    fi
  done
  shot bubble-bubble-open
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | tee -a "$OUT/bubble.txt"
  [ -n "$opened" ] || fail "the bubble did not open the page"
  app_logcat bubble
  a shell input keyevent HOME
  log "bubble passed"
}

check_refused() {
  web_heads_mode
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW deny
  # Never leave the permission denied behind: later checks expect web heads to draw.
  trap 'a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow' EXIT
  a logcat -c
  open_link "https://example.com/?refused=1"
  for _ in $(seq 1 60); do
    a logcat -d | grep -q "Overlay permission is off; the link opens in a tab" && break
    sleep 1
  done
  sleep 2
  shot refused-tab
  local top
  top="$(a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" || true)"
  echo "$top" | tee -a "$OUT/refused.txt"
  app_logcat refused
  grep -q "Overlay permission is off; the link opens in a tab" "$OUT/refused.logcat.txt" || fail "no refusal in logcat"
  ! echo "$top" | grep -q "com.android.settings" || fail "a settings page opened on its own"
  [ "$(overlay_count)" -eq 0 ] || fail "a head showed although the overlay permission is off"
  [ ! -s "$OUT/refused.crash.txt" ] || fail "crash during the refusal (see refused.crash.txt)"
  log "refused passed"
  a shell input keyevent HOME
}

# The web heads notification lines of the notification dump; empty when none is posted.
web_head_notification() {
  a shell dumpsys notification --noredact | tr -d '\r' | grep -F "channel=$WEB_HEAD_SERVICE" || true
}

# The web head service records of the app; empty when the service does not run.
web_head_service_record() {
  a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' | grep -F "ServiceRecord" || true
}

# Kills the app's process with a signal, as the system does under memory pressure. `am kill` spares a
# process that holds a foreground service, so it cannot stand in here.
kill_app() {
  local pid
  pid="$(a shell pidof "$PKG" | tr -d '\r' || true)"
  [ -n "$pid" ] || fail "no app process to kill"
  a shell run-as "$PKG" kill -9 "$pid" >/dev/null 2>&1 || true
  sleep 1
  if [ -z "$(a shell "ps -p $pid -o PID= 2>/dev/null" | tr -d '\r' || true)" ]; then
    log "process $pid killed with run-as"
    return
  fi
  a shell su 0 kill -9 "$pid" >/dev/null 2>&1 || true
  sleep 1
  if [ -z "$(a shell "ps -p $pid -o PID= 2>/dev/null" | tr -d '\r' || true)" ]; then
    log "process $pid killed with su"
    return
  fi
  fail "could not kill the app's process"
}

check_restart() {
  web_heads_mode
  # 1. A start with no link (the debug build exports the service to the shell): the service stops
  #    at once and leaves no notification.
  a logcat -c
  a shell am start -W -n "$PKG/$MAIN" >/dev/null
  sleep 2
  a shell am start-foreground-service -n "$PKG/$WEB_HEAD_SERVICE" >/dev/null
  for _ in $(seq 1 30); do
    a logcat -d | grep -q "Web heads started with no head; stopping" && break
    sleep 1
  done
  sleep 2
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/restart-empty-notifications.txt"
  a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' > "$OUT/restart-empty-service.txt"
  a logcat -d | grep -q "Web heads started with no head; stopping" || fail "a start with no link did not stop the service"
  [ -z "$(web_head_notification)" ] || fail "a web heads notification stayed after a start with no link"
  [ -z "$(web_head_service_record)" ] || fail "the service still runs after a start with no link"
  log "a start with no link stopped the service with no notification"

  # 2. One head open, with its notification.
  a shell input keyevent HOME
  sleep 1
  open_link "https://example.com/?restart=1"
  for _ in $(seq 1 60); do
    a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' | grep -q "isForeground=true" && break
    sleep 1
  done
  sleep 2
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/restart-before-notifications.txt"
  [ "$(overlay_count)" -ge 2 ] || fail "no head before the kill"
  [ -n "$(web_head_notification)" ] || fail "no head before the kill: no web heads notification"
  log "one head open with its notification"

  # 3. The process killed; Android restarts the sticky service with no link.
  local old_pid new_pid="" line=""
  old_pid="$(a shell pidof "$PKG" | tr -d '\r' || true)"
  a logcat -c
  kill_app
  for _ in $(seq 1 90); do
    new_pid="$(a shell pidof "$PKG" | tr -d '\r' || true)"
    if [ -n "$new_pid" ] && [ "$new_pid" != "$old_pid" ]; then
      if a logcat -d | grep -q "Web heads started with no head; stopping"; then
        line="Web heads started with no head; stopping"
        break
      fi
      if a logcat -d | grep -q "Web heads could not start in the foreground"; then
        line="Web heads could not start in the foreground"
        break
      fi
    fi
    sleep 1
  done
  [ -n "$line" ] || fail "Android did not restart the service within 90 s"

  # 4. The shade after the restart.
  sleep 3
  shot restart-after
  a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/restart-after-notifications.txt"
  a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' > "$OUT/restart-after-service.txt"
  app_logcat restart
  log "restart logged: $line"
  log "API level: $(a shell getprop ro.build.version.sdk | tr -d '\r')"
  [ -z "$(web_head_notification)" ] || fail "a web heads notification stayed after the restart"
  [ -z "$(web_head_service_record)" ] || fail "the service still runs after the restart"
  [ "$(overlay_count)" -eq 0 ] || fail "a head showed after the restart"
  [ ! -s "$OUT/restart.crash.txt" ] || fail "crash during the restart (see restart.crash.txt)"
  log "restart passed"
  a shell input keyevent HOME
}

case "$WHICH" in
  head) check_head ;;
  permission) check_permission ;;
  bubble) check_bubble ;;
  refused) check_refused ;;
  restart) check_restart ;;
  *) echo "unknown check: $WHICH" >&2; exit 2 ;;
esac
