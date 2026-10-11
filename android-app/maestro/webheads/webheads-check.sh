#!/usr/bin/env bash
# Web head device checks on the debug build.
#
#   ac19  web heads mode with the overlay permission: a link shows a Compose head and the trash
#         window, logcat has no view tree owner error, and stopping the service removes them
#   ac22  without the permission the explainer comes first and leads to the system screen; after
#         a process kill a link starts a special-use foreground service; with the start forced to
#         fail (debug marker file) the link opens in a Custom Tab with a message
#   ac23  native bubbles mode: the app's notification request, bubbles allowed for the app, then a
#         link shows a system bubble that opens the page
#
# Usage: maestro/webheads/webheads-check.sh <serial> <evidence-dir> <ac19|ac22|ac23>   (from android-app/)
set -euo pipefail

SERIAL="${1:?usage: webheads-check.sh <serial> <evidence-dir> <ac19|ac22|ac23>}"
OUT="${2:?usage: webheads-check.sh <serial> <evidence-dir> <ac19|ac22|ac23>}"
WHICH="${3:?usage: webheads-check.sh <serial> <evidence-dir> <ac19|ac22|ac23>}"
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

ac19() {
  web_heads_mode
  a logcat -c
  open_link "https://example.com/?ac19=1"
  sleep 5
  shot ac19-head
  overlay_windows > "$OUT/ac19-windows-running.txt"
  local running
  running="$(overlay_count)"
  log "overlay windows while running: $running"
  [ "$running" -ge 2 ] || fail "expected a head window and the trash window, found $running"
  a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' > "$OUT/ac19-service.txt"
  app_logcat ac19
  if grep -E "ViewTreeLifecycleOwner|SavedStateRegistryOwner|ViewModelStoreOwner|FATAL" "$OUT/ac19.logcat.txt" "$OUT/ac19.crash.txt"; then
    fail "view tree owner error or crash (see ac19.logcat.txt, ac19.crash.txt)"
  fi
  close_all_heads
  overlay_windows > "$OUT/ac19-windows-stopped.txt"
  local stopped
  stopped="$(overlay_count)"
  log "overlay windows after the service stopped: $stopped"
  [ "$stopped" -eq 0 ] || fail "$stopped overlay windows left after the service stopped"
  log "ac19 passed"
}

ac22() {
  a shell am force-stop "$PKG"
  a logcat -c
  # 1. No permission: the explainer, then the system screen.
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW deny
  a shell am start -W -n "$PKG/$BROWSING_MODE" >/dev/null
  sleep 2
  if command -v maestro >/dev/null; then
    maestro --device "$SERIAL" test --test-output-dir "$OUT/permission-flow" "$(hostpath "$HERE/permission.yaml")" | tee -a "$OUT/ac22.txt"
  else
    tap_text "Web heads" || fail "no Web heads row"
    sleep 2
    shot ac22-explainer
    [ -n "$(node_centre "Allow web heads to float")" ] || fail "the explainer did not show"
    tap_text "Grant" || fail "no Grant button"
    sleep 3
    shot ac22-system-screen
  fi
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | tee -a "$OUT/ac22.txt"
  a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  a shell input keyevent BACK
  sleep 2
  tap_text "Not now" || true
  app_logcat ac22-permission
  [ ! -s "$OUT/ac22-permission.crash.txt" ] || fail "crash during the permission flow (see ac22-permission.crash.txt)"

  # 2. Process killed: a link starts the foreground service of type specialUse.
  a shell input keyevent HOME
  sleep 1
  a shell am kill "$PKG"
  a logcat -c
  open_link "https://example.com/?ac22=1"
  sleep 5
  a shell dumpsys activity services "$WEB_HEAD_SERVICE" | tr -d '\r' > "$OUT/ac22-service.txt"
  shot ac22-head
  grep -qE "isForeground=true" "$OUT/ac22-service.txt" || fail "the web head service is not in the foreground"
  grep -qE "(foregroundServiceType|types)=0x40000000" "$OUT/ac22-service.txt" || fail "the service type is not specialUse"
  log "foreground service: specialUse"

  # 3. Start refused (debug marker file): the link opens in a Custom Tab with a message.
  close_all_heads
  a shell run-as "$PKG" sh -c "'mkdir -p files/debug-faults && touch files/debug-faults/webhead-start-fail'"
  a shell input keyevent HOME
  sleep 1
  a shell am kill "$PKG"
  a logcat -c
  open_link "https://example.com/?ac22=2"
  sleep 2
  shot ac22-fallback
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | tee -a "$OUT/ac22.txt"
  app_logcat ac22-fallback
  a shell run-as "$PKG" rm -f files/debug-faults/webhead-start-fail
  grep -q "Web heads could not start" "$OUT/ac22-fallback.logcat.txt" || fail "no start refusal in logcat"
  [ ! -s "$OUT/ac22-fallback.crash.txt" ] || fail "crash during the fallback (see ac22-fallback.crash.txt)"
  [ "$(overlay_count)" -eq 0 ] || fail "a head showed although the start was refused"
  log "ac22 passed"
  a shell input keyevent HOME
}

ac23() {
  a shell am force-stop "$PKG"
  choose_mode "Native Bubbles (Beta)"
  # The legacy guide dialog first; dismissing it brings the notification explainer.
  a shell input keyevent BACK
  sleep 2
  if tap_text "Grant"; then
    sleep 2
    shot ac23-notification-request
    tap_text "Allow" || fail "no system notification request"
    sleep 1
  fi
  a shell input keyevent HOME
  # The per-app "all conversations can bubble" switch a person sets in system settings.
  a shell cmd notification set_bubbles "$PKG" 1
  a logcat -c
  open_link "https://example.com/?ac23=1"
  for _ in $(seq 1 10); do
    sleep 2
    a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/ac23-notifications.txt"
    grep -qE "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/ac23-notifications.txt" && break
  done
  grep -E "pkg=$PKG.*flags=[A-Z_|]*BUBBLE" "$OUT/ac23-notifications.txt" | tee -a "$OUT/ac23.txt" ||
    fail "no notification of the app carries the bubble flag (see ac23-notifications.txt)"
  sleep 2
  shot ac23-bubble
  # The bubble is drawn by the system shell and is missing from UI dumps; its stack position is
  # in the shell's dump (top left of the bubble, px).
  local xy
  xy="$(a shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | tr -d '\r' |
    sed -nE 's/.*currentStackPos: *PointF\((-?[0-9.]+), *(-?[0-9.]+)\).*/\1 \2/p' | head -1 |
    awk 'NF == 2 { x = $1 < 0 ? 0 : $1; print int(x + 70), int($2 + 70) }' || true)"
  [ -n "$xy" ] || fail "no bubble stack on screen (see ac23-bubble.png)"
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
  shot ac23-bubble-open
  a shell dumpsys activity activities | tr -d '\r' | grep -m1 "topResumedActivity" | tee -a "$OUT/ac23.txt"
  [ -n "$opened" ] || fail "the bubble did not open the page"
  app_logcat ac23
  a shell input keyevent HOME
  log "ac23 passed"
}

case "$WHICH" in
  ac19) ac19 ;;
  ac22) ac22 ;;
  ac23) ac23 ;;
  *) echo "unknown check: $WHICH" >&2; exit 2 ;;
esac
