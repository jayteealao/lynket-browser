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
#
# Usage: maestro/webheads/webheads-check.sh <serial> <evidence-dir> <head|permission|bubble>   (from android-app/)
set -euo pipefail

SERIAL="${1:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble>}"
OUT="${2:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble>}"
WHICH="${3:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble>}"
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

case "$WHICH" in
  head) check_head ;;
  permission) check_permission ;;
  bubble) check_bubble ;;
  *) echo "unknown check: $WHICH" >&2; exit 2 ;;
esac
