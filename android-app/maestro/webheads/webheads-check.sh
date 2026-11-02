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
#   rotation  web heads chosen: two heads resting on the left at 70 % of the height in portrait; after a
#         turn to landscape a new link spawns its head on the left edge at the same share of the
#         height, not at the default point (restores the rotation settings and the navigation mode)
#   resnap  web heads chosen: three heads resting on the right at half the height in portrait; after a
#         turn to landscape every head rests on the right edge inside the screen at the same share of
#         the height; then six heads at 90 % on the left, and after a turn every head rests on the
#         left edge inside the screen (restores the rotation settings and the navigation mode)
#   fling  web heads chosen: one head; at the phone's own width and then on a 1280 dp window (wm size and
#         density), the same flick in dp per second from the middle snaps the head to the edge it
#         points to, both ways (restores the size, the density, the rotation settings and the
#         navigation mode)
#   speech  web heads chosen: three links open three heads; a dump of every window shows the front
#         head described as "Web head: <title>, 1 of 3" and the other two as 2 of 3 and 3 of 3
#   trash  web heads chosen: one head held in a drag (input motionevent); a dump of every window shows
#         the shown trash as "Close target", "Release to close" while the head is on it, nothing after
#         it leaves, and again after it returns; the trash window takes touches only while it shows;
#         with TalkBack installed, a probe of whether a held drag still moves the head with TalkBack
#         on (restores the accessibility services and the navigation mode); it also records what the
#         vibrator service saw of the lock and leave ticks
#
# Usage: maestro/webheads/webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart|rotation|resnap|fling|speech|trash>   (from android-app/)
set -euo pipefail

SERIAL="${1:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart|rotation|resnap|fling|speech|trash>}"
OUT="${2:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart|rotation|resnap|fling|speech|trash>}"
WHICH="${3:?usage: webheads-check.sh <serial> <evidence-dir> <head|permission|bubble|refused|restart|rotation|resnap|fling|speech|trash>}"
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
  # The device's SELinux policy may refuse a kill from run-as; su then works on an AOSP image.
  a shell run-as "$PKG" kill -9 "$pid" >/dev/null 2>&1 || true
  sleep 1
  if ! process_alive "$pid"; then
    log "process $pid killed with run-as"
    return
  fi
  a shell su 0 kill -9 "$pid" >/dev/null 2>&1 || true
  sleep 1
  if ! process_alive "$pid"; then
    log "process $pid killed with su"
    return
  fi
  fail "could not kill the app's process"
}

# True while the process $1 exists.
process_alive() {
  [ -n "$(a shell "[ -d /proc/$1 ] && echo alive" | tr -d '\r' || true)" ]
}

check_restart() {
  web_heads_mode
  # The shade shows the web heads notification only while the app may post notifications (Android 13
  # and later ask for it; web_heads_mode declines the request). Without it every read below is empty.
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
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

# Frame of the master head (the front touchable overlay window) as "left top right bottom".
master_frame() {
  overlay_windows | awk '$1 == 1 { print $2, $3, $4, $5; exit }'
}

# Current logical display size as "W H"; unlike `wm size` it follows the rotation.
display_size() {
  a shell dumpsys window displays | tr -d '\r' | grep -o -m1 'cur=[0-9]*x[0-9]*' | sed 's/cur=//' | tr 'x' ' '
}

# Display density as a scale of 160 dpi (the override density when one is set).
density() {
  a shell wm density | tr -d '\r' | awk -F': ' '/Override density/ { o = $2 } /Physical density/ { p = $2 } END { print (o != "" ? o : p) / 160 }'
}

# Rest range of a head on a display of height $1 at density $2, as "top bottom": 25 dp from the top
# to 85 % of the height, as the app computes it.
rest_range() {
  awk -v h="$1" -v d="$2" 'BEGIN { print int(25 * d + 0.5), int(h * 0.85) }'
}

# Share of the rest range ($2 top, $3 bottom) at which a frame top $1 sits.
share_of_range() {
  awk -v y="$1" -v t="$2" -v b="$3" 'BEGIN { printf "%.3f", (y - t) / (b - t) }'
}

ROT_ACCEL_BEFORE=""
ROT_USER_BEFORE=""
restore_rotation_setting() {  # $1 = key, $2 = saved value ("null" when the key was unset)
  if [ -z "$2" ] || [ "$2" = "null" ]; then a shell settings delete system "$1" >/dev/null 2>&1 || true
  else a shell settings put system "$1" "$2" >/dev/null 2>&1 || true; fi
}
rotation_restore() {
  restore_rotation_setting user_rotation "$ROT_USER_BEFORE"
  restore_rotation_setting accelerometer_rotation "$ROT_ACCEL_BEFORE"
  nav_restore
  close_all_heads
}

check_rotation() {
  # 1. Portrait, fixed, with three-button navigation (the gesture Back zone takes edge swipes).
  ROT_ACCEL_BEFORE="$(a shell settings get system accelerometer_rotation | tr -d '\r')"
  ROT_USER_BEFORE="$(a shell settings get system user_rotation | tr -d '\r')"
  trap rotation_restore EXIT
  web_heads_mode
  nav_three_button
  a shell settings put system accelerometer_rotation 0
  a shell settings put system user_rotation 0
  sleep 2

  # 2. Two heads (and the trash window).
  open_link "https://example.com/?rotation=1"
  sleep 3
  open_link "https://example.com/?rotation=2"
  local n=0
  for _ in $(seq 1 60); do
    n="$(overlay_count)"
    [ "$n" -ge 3 ] && break
    sleep 1
  done
  [ "$n" -ge 3 ] || fail "expected two heads and the trash window, found $n overlay windows"
  sleep 3

  # 3. The stack dragged slowly to the left edge at 70 % of the rest range.
  local w h d top bottom fl ft fr fb cx cy ty
  read -r w h < <(display_size) || fail "no display size"
  d="$(density)"
  read -r top bottom < <(rest_range "$h" "$d")
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  read -r cx cy < <(head_centre) || fail "no master head to drag"
  ty="$(awk -v t="$top" -v b="$bottom" -v fh="$((fb - ft))" 'BEGIN { print int(t + 0.7 * (b - t) + fh / 2) }')"
  log "portrait ${w}x${h}, density $d, rest range $top..$bottom, master frame $fl $ft $fr $fb"
  a shell input swipe "$cx" "$cy" 150 "$ty" 1500
  sleep 3
  shot rotation-portrait
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  local ratio_p
  ratio_p="$(share_of_range "$ft" "$top" "$bottom")"
  log "portrait master frame $fl $ft $fr $fb, share of the height $ratio_p"
  [ $(((fl + fr) / 2)) -lt $((w / 2)) ] || fail "the stack did not rest on the left at 70 % (frame $fl $ft $fr $fb)"
  awk -v r="$ratio_p" 'BEGIN { exit !(r - 0.70 <= 0.05 && 0.70 - r <= 0.05) }' || fail "the stack did not rest on the left at 70 % (share $ratio_p)"

  # 4. A turn to landscape.
  a shell settings put system user_rotation 1
  for _ in $(seq 1 10); do
    read -r w h < <(display_size)
    [ "$w" -gt "$h" ] && break
    sleep 1
  done
  [ "$w" -gt "$h" ] || fail "the display did not turn to landscape (${w}x${h})"
  sleep 2
  shot rotation-landscape-before

  # 5. A new link: its head must spawn at the stack, before the master coasts (6 s).
  a logcat -c
  open_link "https://example.com/?rotation=3"
  for _ in $(seq 1 30); do
    n="$(overlay_count)"
    [ "$n" -ge 4 ] && break
    sleep 1
  done
  [ "$n" -ge 4 ] || fail "the new link showed no head ($n overlay windows)"
  sleep 3
  shot rotation-landscape-after
  overlay_windows > "$OUT/rotation-windows.txt"
  a shell dumpsys window windows | tr -d '\r' > "$OUT/rotation-window-dump.txt"
  read -r top bottom < <(rest_range "$h" "$d")
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  local ratio_l
  ratio_l="$(share_of_range "$ft" "$top" "$bottom")"
  log "landscape ${w}x${h}, rest range $top..$bottom, new master frame $fl $ft $fr $fb, share of the height $ratio_l"
  [ $(((fl + fr) / 2)) -lt $((w / 2)) ] || fail "the new head is not on the stack's side (frame $fl $ft $fr $fb)"
  [ "$fl" -le 0 ] || fail "the new head is not on the edge (left $fl)"
  [ "$ft" -ge $((top - 2)) ] && [ "$ft" -le $((bottom + 2)) ] || fail "the new head is outside the bounds (top $ft, range $top..$bottom)"
  awk -v a="$ratio_l" -v b="$ratio_p" 'BEGIN { exit !(a - b <= 0.05 && b - a <= 0.05) }' || fail "the new head is not at the stack's height ($ratio_l against $ratio_p)"
  awk -v y="$ft" -v h="$h" 'BEGIN { d = y - h / 3; if (d < 0) d = -d; exit !(d >= 0.1 * h) }' || fail "the new head is at the default spawn point (top $ft)"

  # 6. No crash.
  app_logcat rotation
  [ ! -s "$OUT/rotation.crash.txt" ] || fail "crash during the rotation (see rotation.crash.txt)"
  log "ratio_p $ratio_p, ratio_l $ratio_l, API level: $(a shell getprop ro.build.version.sdk | tr -d '\r')"
  log "rotation passed"
}

# Size the app measures, into APP_W and APP_H: `app=WxH` of the display dump (the logical size
# without the system bars the app does not draw under), else the logical size. APP_SIZE_SOURCE
# names which one it used.
APP_W=0
APP_H=0
APP_SIZE_SOURCE=""
app_size() {
  local s
  s="$(a shell dumpsys window displays | tr -d '\r' | grep -o -m1 'app=[0-9]*x[0-9]*' | sed 's/app=//' | tr 'x' ' ' || true)"
  if [ -n "$s" ]; then
    APP_SIZE_SOURCE="app"
  else
    APP_SIZE_SOURCE="cur"
    s="$(display_size)"
  fi
  read -r APP_W APP_H <<< "$s" || fail "no display size"
}

# Frame of every touchable overlay window (the heads; the trash takes touches only while a head is
# dragged), one "left top right bottom" per line, front first.
head_frames() {
  overlay_windows | awk '$1 == 1 { print $2, $3, $4, $5 }'
}

# Waits up to $1 s for at least $2 overlay windows; prints the count it saw last.
wait_for_overlays() {
  local n=0
  for _ in $(seq 1 "$1"); do
    n="$(overlay_count)"
    [ "$n" -ge "$2" ] && break
    sleep 1
  done
  echo "$n"
}

# Turns the display to $1 (0 portrait, 1 landscape) and waits up to 10 s for it.
turn_to() {
  local w=0 h=0
  a shell settings put system user_rotation "$1"
  for _ in $(seq 1 10); do
    read -r w h < <(display_size)
    if [ "$1" = 1 ] && [ "$w" -gt "$h" ]; then return 0; fi
    if [ "$1" = 0 ] && [ "$h" -gt "$w" ]; then return 0; fi
    sleep 1
  done
  fail "the display did not turn (user_rotation $1, ${w}x${h})"
}

# Drags the master slowly so its window rests at share $2 of the rest range, the finger let go at
# x = $1; sets RATIO_P to the share of the height it rests at. Needs APP_H and RS_D (portrait).
RATIO_P=""
rest_master_at() {
  local top bottom fl ft fr fb cx cy ty
  read -r top bottom < <(rest_range "$APP_H" "$RS_D")
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  read -r cx cy < <(head_centre) || fail "no master head to drag"
  ty="$(awk -v t="$top" -v b="$bottom" -v s="$2" -v fh="$((fb - ft))" 'BEGIN { print int(t + s * (b - t) + fh / 2) }')"
  a shell input swipe "$cx" "$cy" "$1" "$ty" 1500
  sleep 3
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  RATIO_P="$(share_of_range "$ft" "$top" "$bottom")"
}

# After a turn to landscape: every head rests on the $1 edge (left or right) inside the screen, and
# the master at the share $2 of the height (within 0.03). Sets RATIO_L to the landscape share.
RATIO_L=""
check_stack_on() {
  local w h top bottom fl ft fr fb
  app_size
  w="$APP_W"
  h="$APP_H"
  read -r top bottom < <(rest_range "$h" "$RS_D")
  [ -n "$(head_frames)" ] || fail "no head window after the turn"
  while read -r fl ft fr fb; do
    if [ "$1" = right ]; then
      [ $(((fl + fr) / 2)) -gt $((w / 2)) ] || fail "a head left the stack's side (frame $fl $ft $fr $fb, width $w)"
      [ "$fr" -ge "$w" ] || fail "a head is not on the edge (frame $fl $ft $fr $fb, width $w)"
      [ "$fl" -lt "$w" ] || fail "a head is off screen (frame $fl $ft $fr $fb, width $w)"
    else
      [ $(((fl + fr) / 2)) -lt $((w / 2)) ] || fail "a head left the stack's side (frame $fl $ft $fr $fb, width $w)"
      [ "$fl" -le 0 ] || fail "a head is not on the edge (frame $fl $ft $fr $fb)"
      [ "$fr" -gt 0 ] || fail "a head is off screen (frame $fl $ft $fr $fb)"
    fi
    [ "$ft" -ge $((top - 2)) ] && [ "$ft" -le $((bottom + 30)) ] || fail "a head is outside the bounds (top $ft, range $top..$bottom)"
  done < <(head_frames)
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  RATIO_L="$(share_of_range "$ft" "$top" "$bottom")"
  log "landscape ${w}x${h} (size from $APP_SIZE_SOURCE), rest range $top..$bottom, master frame $fl $ft $fr $fb, share $RATIO_L"
  awk -v a="$RATIO_L" -v b="$2" 'BEGIN { exit !(a - b <= 0.03 && b - a <= 0.03) }' || fail "the stack lost its height ($RATIO_L against $2)"
}

check_resnap() {
  # 1. Portrait, fixed, with three-button navigation (the gesture Back zone takes edge swipes).
  ROT_ACCEL_BEFORE="$(a shell settings get system accelerometer_rotation | tr -d '\r')"
  ROT_USER_BEFORE="$(a shell settings get system user_rotation | tr -d '\r')"
  trap rotation_restore EXIT
  web_heads_mode
  nav_three_button
  a shell settings put system accelerometer_rotation 0
  a shell settings put system user_rotation 0
  sleep 2

  # 2. Three heads (and the trash window).
  local i n
  for i in 1 2 3; do
    open_link "https://example.com/?resnap=$i"
    sleep 3
  done
  n="$(wait_for_overlays 60 4)"
  [ "$n" -ge 4 ] || fail "expected three heads and the trash window, found $n overlay windows"
  sleep 3

  # 3. The stack dragged slowly to the right edge at half the rest range.
  app_size
  RS_D="$(density)"
  log "portrait ${APP_W}x${APP_H} (size from $APP_SIZE_SOURCE), density $RS_D"
  local ratio_p fl ft fr fb
  rest_master_at $((APP_W - 150)) 0.5
  ratio_p="$RATIO_P"
  shot resnap-portrait
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  log "portrait master frame $fl $ft $fr $fb, share of the height $ratio_p"
  [ $(((fl + fr) / 2)) -gt $((APP_W / 2)) ] || fail "the stack did not rest on the right at half the height (frame $fl $ft $fr $fb)"
  awk -v r="$ratio_p" 'BEGIN { exit !(r - 0.50 <= 0.05 && 0.50 - r <= 0.05) }' || fail "the stack did not rest on the right at half the height (share $ratio_p)"

  # 4. A turn to landscape; the spring settles in under a second, the coasting (after 6 s) moves only x.
  turn_to 1
  sleep 3
  shot resnap-landscape
  overlay_windows > "$OUT/resnap-windows.txt"
  a shell dumpsys window windows | tr -d '\r' > "$OUT/resnap-window-dump.txt"

  # 5. Every head on the right edge inside the screen, at the same share of the height.
  check_stack_on right "$ratio_p"
  log "three heads on the right: ratio_p $ratio_p, ratio_l $RATIO_L"

  # 6. The worst state: six heads low on the left, then a turn.
  turn_to 0
  sleep 2
  for i in 4 5 6; do
    open_link "https://example.com/?resnap=$i"
    sleep 3
  done
  n="$(wait_for_overlays 60 7)"
  [ "$n" -ge 7 ] || fail "expected six heads and the trash window, found $n overlay windows"
  sleep 3
  app_size
  rest_master_at 150 0.9
  ratio_p="$RATIO_P"
  shot resnap-worst-portrait
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  log "worst portrait ${APP_W}x${APP_H}, master frame $fl $ft $fr $fb, share of the height $ratio_p"
  [ $(((fl + fr) / 2)) -lt $((APP_W / 2)) ] || fail "the stack did not rest on the left at 90 % (frame $fl $ft $fr $fb)"
  awk -v r="$ratio_p" 'BEGIN { exit !(r - 0.90 <= 0.05 && 0.90 - r <= 0.05) }' || fail "the stack did not rest on the left at 90 % (share $ratio_p)"
  turn_to 1
  sleep 3
  shot resnap-worst-landscape
  overlay_windows > "$OUT/resnap-worst-windows.txt"
  check_stack_on left "$ratio_p"
  log "six heads on the left: ratio_p $ratio_p, ratio_l $RATIO_L"

  # 7. No crash.
  app_logcat resnap
  [ ! -s "$OUT/resnap.crash.txt" ] || fail "crash during the rotation (see resnap.crash.txt)"
  log "API level: $(a shell getprop ro.build.version.sdk | tr -d '\r')"
  log "resnap passed"
}

# The size and density overrides the device had before the fling check ("" when none was set).
FLING_SIZE_BEFORE=""
FLING_DENSITY_BEFORE=""
fling_restore() {
  if [ -n "$FLING_SIZE_BEFORE" ]; then a shell wm size "$FLING_SIZE_BEFORE" >/dev/null 2>&1 || true
  else a shell wm size reset >/dev/null 2>&1 || true; fi
  if [ -n "$FLING_DENSITY_BEFORE" ]; then a shell wm density "$FLING_DENSITY_BEFORE" >/dev/null 2>&1 || true
  else a shell wm density reset >/dev/null 2>&1 || true; fi
  sleep 2
  rotation_restore
}

# Swipe duration in ms that moves $1 px at $2 dp per second on a display of density $3; at least
# 50 ms, because the velocity tracker reads the last 100 ms of a gesture.
flick_ms() {
  awk -v d="$1" -v v="$2" -v s="$3" 'BEGIN { ms = int(d / (v * s) * 1000 + 0.5); print (ms < 50 ? 50 : ms) }'
}

# Flicks the master horizontally from its centre to share $1 of the width APP_W at $2 dp per second
# (density FL_D); `input swipe` moves at a constant speed, so the release speed is the distance over
# the duration. Logs the distance, the duration and the speed.
flick_to() {
  local cx cy tx dist ms
  read -r cx cy < <(head_centre) || fail "no master head to flick"
  tx="$(awk -v s="$1" -v w="$APP_W" 'BEGIN { print int(s * w + 0.5) }')"
  dist=$((cx > tx ? cx - tx : tx - cx))
  ms="$(flick_ms "$dist" "$2" "$FL_D")"
  a shell input swipe "$cx" "$cy" "$tx" "$cy" "$ms"
  log "flick from x $cx to $tx (share $1 of $APP_W): $dist px in $ms ms, $(awk -v d="$dist" -v m="$ms" -v s="$FL_D" 'BEGIN { printf "%.0f", d / s / m * 1000 }') dp/s"
}

# The edge the master rests on, from its frame's x only (frame y sits below the layout position by
# the status bar): left, right or middle.
edge_of_master() {
  local fl ft fr fb
  read -r fl ft fr fb < <(master_frame) || fail "no master head window"
  log "master frame $fl $ft $fr $fb (width $APP_W)" >&2
  if [ "$fl" -le 0 ]; then echo left
  elif [ "$fr" -ge "$APP_W" ]; then echo right
  else echo middle; fi
}

# Two flicks from a head resting on the right edge at 1000 dp/s: to the left, let go right of the
# middle (so the nearer edge is the right one), then back to the right, let go left of the middle.
# $1 names the phase in the screenshots.
flick_both_ways() {
  local edge
  flick_to 0.55 1000
  sleep 3
  shot "fling-$1-left"
  edge="$(edge_of_master)"
  log "$1: flung left, rests on the $edge"
  [ "$edge" = left ] || fail "the head did not snap to the edge it was flung towards ($1, left, found $edge)"
  flick_to 0.45 1000
  sleep 3
  shot "fling-$1-right"
  edge="$(edge_of_master)"
  log "$1: flung right, rests on the $edge"
  [ "$edge" = right ] || fail "the head did not snap to the edge it was flung towards ($1, right, found $edge)"
}

check_fling() {
  # 1. Save the overrides and the rotation settings; portrait, fixed, with three-button navigation.
  FLING_SIZE_BEFORE="$(a shell wm size | tr -d '\r' | awk -F': ' '/Override size/ { print $2 }')"
  FLING_DENSITY_BEFORE="$(a shell wm density | tr -d '\r' | awk -F': ' '/Override density/ { print $2 }')"
  ROT_ACCEL_BEFORE="$(a shell settings get system accelerometer_rotation | tr -d '\r')"
  ROT_USER_BEFORE="$(a shell settings get system user_rotation | tr -d '\r')"
  trap fling_restore EXIT
  web_heads_mode
  nav_three_button
  a shell settings put system accelerometer_rotation 0
  a shell settings put system user_rotation 0
  sleep 2

  # 2. The phone's own width: one head on the right edge, flung both ways.
  local n
  open_link "https://example.com/?fling=1"
  n="$(wait_for_overlays 60 2)"
  [ "$n" -ge 2 ] || fail "expected a head and the trash window, found $n overlay windows"
  sleep 3
  app_size
  FL_D="$(density)"
  log "phone ${APP_W}x${APP_H} (size from $APP_SIZE_SOURCE), density $FL_D, $(awk -v w="$APP_W" -v d="$FL_D" 'BEGIN { printf "%.0f", w / d }') dp wide"
  flick_both_ways phone

  # 3. A 1280 dp window: the service gets the configuration change and the head finds the stack's place.
  a shell wm size 2560x1600
  a shell wm density 320
  sleep 4
  app_size
  FL_D="$(density)"
  local wide_dp
  wide_dp="$(awk -v w="$APP_W" -v d="$FL_D" 'BEGIN { printf "%.0f", w / d }')"
  log "wide ${APP_W}x${APP_H} (size from $APP_SIZE_SOURCE), density $FL_D, $wide_dp dp wide"
  awk -v w="$wide_dp" 'BEGIN { exit !(w - 1280 <= 16 && 1280 - w <= 16) }' || fail "the window is not 1280 dp wide ($wide_dp dp)"
  [ "$(edge_of_master)" = right ] || fail "the head did not rest on the right edge of the wide window"
  flick_both_ways wide
  overlay_windows > "$OUT/fling-wide-windows.txt"
  a shell dumpsys window windows | tr -d '\r' > "$OUT/fling-wide-window-dump.txt"

  # 4. The same flick speed in dp per second in both phases (logged per flick); no crash.
  log "API level: $(a shell getprop ro.build.version.sdk | tr -d '\r')"
  app_logcat fling
  [ ! -s "$OUT/fling.crash.txt" ] || fail "crash during the flings (see fling.crash.txt)"
  log "fling passed"
}

# Prints every web head description that a UI dump shows, one per line. Dumps every window first
# (a head is an overlay, never the active window), then the active window, then the maestro
# hierarchy; keeps the dump it used in the evidence and logs which source answered.
head_descriptions() {
  local dump=/sdcard/webheads-speech.xml
  a shell uiautomator dump --windows "$dump" >/dev/null 2>&1 || true
  a shell cat "$dump" 2>/dev/null | tr -d '\r' > "$OUT/speech-ui.xml" || true
  if grep -q 'content-desc="Web head: ' "$OUT/speech-ui.xml"; then
    log "dump source: uiautomator dump --windows" >&2
  else
    a shell uiautomator dump "$dump" >/dev/null 2>&1 || true
    a shell cat "$dump" 2>/dev/null | tr -d '\r' > "$OUT/speech-ui.xml" || true
    if grep -q 'content-desc="Web head: ' "$OUT/speech-ui.xml"; then
      log "dump source: uiautomator dump (active window)" >&2
    elif command -v maestro >/dev/null; then
      maestro --device "$SERIAL" hierarchy > "$OUT/speech-hierarchy.json" 2>/dev/null || true
      log "dump source: maestro hierarchy" >&2
      grep -o 'Web head: [^"]*' "$OUT/speech-hierarchy.json" || true
      return 0
    else
      log "dump source: none had a web head node" >&2
    fi
  fi
  grep -o 'Web head: [^"]*' "$OUT/speech-ui.xml" || true
}

check_speech() {
  # 1. Web heads mode with a clean log.
  web_heads_mode
  a logcat -c
  # 2. Three links, three heads; the newest is at the front.
  open_link "https://example.com/?speech=1"
  sleep 3
  open_link "https://example.com/?speech=2"
  sleep 3
  open_link "https://example.com/?speech=3"
  local windows
  windows="$(wait_for_overlays 60 4)"
  log "overlay windows: $windows"
  [ "$windows" -ge 4 ] || fail "expected three head windows and the trash, found $windows"
  sleep 8  # the page titles
  # 3. What a screen reader is given for each head.
  shot speech-heads
  head_descriptions > "$OUT/speech-descriptions.txt"
  log "descriptions:"
  sed 's/^/  /' "$OUT/speech-descriptions.txt" | tee -a "$OUT/speech.txt"
  [ -s "$OUT/speech-descriptions.txt" ] || fail "no head description in any UI dump"
  local front
  front="$(grep -cE '^Web head: .+, 1 of 3$' "$OUT/speech-descriptions.txt" || true)"
  [ "$front" -eq 1 ] || fail "the front head does not say 1 of 3"
  grep -qE ', 2 of 3$' "$OUT/speech-descriptions.txt" || fail "no head says 2 of 3"
  grep -qE ', 3 of 3$' "$OUT/speech-descriptions.txt" || fail "no head says 3 of 3"
  log "front title: $(grep -E ', 1 of 3$' "$OUT/speech-descriptions.txt" | sed -E 's/^Web head: (.*), 1 of 3$/\1/') (\"Example Domain\" online, else the address)"
  log "API level: $(a shell getprop ro.build.version.sdk | tr -d '\r')"
  # 4. No crash; close the heads.
  app_logcat speech
  [ ! -s "$OUT/speech.crash.txt" ] || fail "crash with three heads open (see speech.crash.txt)"
  close_all_heads
  log "speech passed"
}

# Dumps every window to $OUT/$1.xml (a head and the trash are overlays, never the active window).
dump_windows() {
  local dump=/sdcard/webheads-trash.xml
  a shell uiautomator dump --windows "$dump" >/dev/null 2>&1 || true
  a shell cat "$dump" 2>/dev/null | tr -d '\r' > "$OUT/$1.xml" || true
}

# Centre "X Y" of the node described as "Close target" in the dump $1.
trash_node_centre() {
  grep -o 'content-desc="Close target"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$1" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk '{ print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Number of overlay windows that take touches in the list $1.
touchable_windows() { grep -c '^1 ' "$1" || true; }

# Moves the held touch from ($1, $2) to ($3, $4) in $5 steps.
move_in_steps() {
  local i
  for i in $(seq 1 "$5"); do
    a shell input motionevent MOVE $(( $1 + ($3 - $1) * i / $5 )) $(( $2 + ($4 - $2) * i / $5 ))
  done
}

TRASH_TALKBACK_BEFORE=""
trash_restore() {
  nav_restore
  if [ "$TRASH_TALKBACK_BEFORE" = "none" ]; then
    a shell settings delete secure enabled_accessibility_services >/dev/null 2>&1 || true
    a shell settings put secure accessibility_enabled 0 >/dev/null 2>&1 || true
  elif [ -n "$TRASH_TALKBACK_BEFORE" ]; then
    a shell settings put secure enabled_accessibility_services "${TRASH_TALKBACK_BEFORE#none}" >/dev/null 2>&1 || true
  fi
  TRASH_TALKBACK_BEFORE=""
}

check_trash() {
  trap trash_restore EXIT
  # 1. Web heads mode, one head; the hidden trash takes no touches. Three-button navigation, so the
  # gesture Back zone at the right edge does not take the drag of the head resting there.
  web_heads_mode
  nav_three_button
  a logcat -c
  open_link "https://example.com/?trash=1"
  local windows
  windows="$(wait_for_overlays 60 2)"
  log "overlay windows: $windows"
  [ "$windows" -ge 2 ] || fail "expected a head window and the trash, found $windows"
  sleep 3
  local hx hy w h
  read -r hx hy < <(head_centre)
  read -r w h < <(screen_size)
  log "head centre: $hx $hy, screen: $w x $h"
  overlay_windows > "$OUT/trash-windows-idle.txt"
  [ "$(touchable_windows "$OUT/trash-windows-idle.txt")" -eq 1 ] || fail "the hidden trash window takes touches"
  # 2. The dumps come first: while an events capture holds the UI automation connection,
  # uiautomator dump writes nothing, so the capture gets its own drag in part 8.
  # 3. A held drag to the middle shows the trash, named and touchable.
  local mx=$((w / 2)) my=$((h / 2)) ax=$((w / 2)) ay=$((h / 3))
  a shell dumpsys vibrator_manager | tr -d '\r' > "$OUT/trash-vibrator-before.txt" || true
  a shell input motionevent DOWN "$hx" "$hy"
  move_in_steps "$hx" "$hy" "$mx" "$my" 4
  sleep 1
  overlay_windows > "$OUT/trash-windows-shown.txt"
  [ "$(touchable_windows "$OUT/trash-windows-shown.txt")" -eq 2 ] || fail "the shown trash window does not take touches"
  dump_windows trash-shown
  grep -q 'content-desc="Close target"' "$OUT/trash-shown.xml" || fail "the shown trash is not called Close target in any window"
  if grep -q 'content-desc="Release to close"' "$OUT/trash-shown.xml"; then fail "Release to close before a lock"; fi
  log "shown: Close target, no Release to close; the trash window takes touches"
  local tx ty
  read -r tx ty < <(trash_node_centre "$OUT/trash-shown.xml") || true
  [ -n "$tx" ] || fail "no bounds for the Close target node"
  log "trash centre: $tx $ty"
  # 4. Onto the trash, then eight more moves that stay on it: one lock.
  a shell input motionevent MOVE "$tx" "$ty"
  sleep 1
  local i
  for i in $(seq 1 8); do
    a shell input motionevent MOVE $((tx + (i % 3) * 5 - 5)) $((ty + (i % 2) * 10 - 5))
    sleep 0.1
  done
  sleep 1
  dump_windows trash-locked
  grep -q 'content-desc="Release to close"' "$OUT/trash-locked.xml" || fail "the locked trash does not say Release to close"
  log "locked: Release to close"
  # 5. Off the trash: the name stays, the announcement empties.
  a shell input motionevent MOVE "$ax" "$ay"
  sleep 1
  dump_windows trash-left
  grep -q 'content-desc="Close target"' "$OUT/trash-left.xml" || fail "the trash lost its name after the head left"
  if grep -q 'content-desc="Release to close"' "$OUT/trash-left.xml"; then fail "Release to close after the head left"; fi
  log "left: Close target, no Release to close"
  # 6. Back onto the trash: it locks again.
  a shell input motionevent MOVE "$tx" "$ty"
  sleep 1
  dump_windows trash-relocked
  grep -q 'content-desc="Release to close"' "$OUT/trash-relocked.xml" || fail "the trash does not say Release to close after the second lock"
  log "relocked: Release to close"
  # 7. Off the trash and let go: the trash hides and takes no touches.
  a shell input motionevent MOVE "$ax" "$ay"
  a shell input motionevent UP "$ax" "$ay"
  sleep 2
  overlay_windows > "$OUT/trash-windows-after.txt"
  [ "$(touchable_windows "$OUT/trash-windows-after.txt")" -eq 1 ] || fail "the trash window still takes touches after the release"
  log "released: the trash window takes no touches"
  # The haptic ticks: what the vibrator service saw of the lock and leave ticks. It never fails the
  # check; the felt ticks are a person's sign-off on a real phone (feel-checklist.md).
  a shell dumpsys vibrator_manager | tr -d '\r' > "$OUT/trash-vibrator-after.txt" || true
  local vib vib_before vib_after
  vib="$(grep -m1 -iE "hasVibrator|vibratorIds|Vibrator IDs" "$OUT/trash-vibrator-after.txt" || true)"
  log "vibrator: ${vib:-none}"
  vib_before="$(grep -c "$PKG" "$OUT/trash-vibrator-before.txt" || true)"
  vib_after="$(grep -c "$PKG" "$OUT/trash-vibrator-after.txt" || true)"
  log "vibrator: app entries before ${vib_before:-0}, after ${vib_after:-0}"
  if [ -z "$vib" ] || echo "$vib" | grep -qiE "hasVibrator[=: ]*false|vibratorIds[=: ]*\[\]|Vibrator IDs[=: ]*\[\]"; then
    log "vibrator: none on this device; the felt ticks are the person's sign-off"
  fi
  # 8. The same drag again with an accessibility events capture: shown, locked with eight more moves
  # on the trash, left, locked again, released.
  sleep 3
  read -r hx hy < <(head_centre)
  a shell "timeout 30 uiautomator events" > "$OUT/trash-events.txt" 2>&1 &
  local events=$!
  sleep 2
  a shell input motionevent DOWN "$hx" "$hy"
  move_in_steps "$hx" "$hy" "$mx" "$my" 4
  sleep 1
  a shell input motionevent MOVE "$tx" "$ty"
  sleep 1
  for i in $(seq 1 8); do
    a shell input motionevent MOVE $((tx + (i % 3) * 5 - 5)) $((ty + (i % 2) * 10 - 5))
    sleep 0.1
  done
  sleep 1
  a shell input motionevent MOVE "$ax" "$ay"
  sleep 1
  a shell input motionevent MOVE "$tx" "$ty"
  sleep 1
  a shell input motionevent MOVE "$ax" "$ay"
  a shell input motionevent UP "$ax" "$ay"
  wait "$events" || true
  tr -d '\r' < "$OUT/trash-events.txt" > "$OUT/trash-events.clean.txt"
  local changes spoken
  changes="$(grep -F "PackageName: $PKG" "$OUT/trash-events.clean.txt" | grep -F TYPE_WINDOW_CONTENT_CHANGED | grep -cF CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION || true)"
  log "content-description change events from the app: $changes"
  spoken="$(grep -cF "Release to close" "$OUT/trash-events.clean.txt" || true)"
  if [ "$spoken" -gt 0 ]; then
    log "event lines with Release to close: $spoken"
    [ "$spoken" -eq 2 ] || fail "Release to close came $spoken times for two locks"
  else
    log "content-change events carry no text here; the dumps and the JVM tests carry the words"
  fi
  # 9. TalkBack probe: it records what happens and never fails the check.
  local talkback
  talkback="$(a shell pm list packages | tr -d '\r' | grep -x 'package:com.google.android.marvin.talkback' | sed 's/package://' | head -1 || true)"
  if [ -z "$talkback" ]; then
    log "talkback: not installed"
  else
    TRASH_TALKBACK_BEFORE="none$(a shell settings get secure enabled_accessibility_services | tr -d '\r' | sed 's/^null$//')"
    a shell settings put secure enabled_accessibility_services "$talkback/com.google.android.marvin.talkback.TalkBackService"
    a shell settings put secure accessibility_enabled 1
    local bound=0
    for _ in $(seq 1 10); do
      a shell dumpsys accessibility | tr -d '\r' > "$OUT/trash-accessibility.txt"
      if grep -q "Bound services:{Service\[label=TalkBack" "$OUT/trash-accessibility.txt"; then bound=1; break; fi
      sleep 1
    done
    if [ "$bound" -eq 0 ]; then
      log "talkback: installed but not bound (see trash-accessibility.txt)"
    else
      a logcat -c
      local before after
      before="$(overlay_windows | awk '$1 == 1 { print $2, $3; exit }')"
      a shell input motionevent DOWN "$hx" "$hy"
      a shell input motionevent MOVE "$tx" "$ty"
      sleep 1
      after="$(overlay_windows | awk '$1 == 1 { print $2, $3; exit }')"
      sleep 1
      a shell input motionevent MOVE "$ax" "$ay"
      a shell input motionevent UP "$ax" "$ay"
      sleep 2
      log "talkback: head frame before $before, after the move $after"
      if [ -n "$before" ] && [ "$before" != "$after" ]; then
        log "talkback: a held drag moves the head"
      else
        log "talkback: injected touches are explored, the head does not move"
      fi
      a logcat -d | tr -d '\r' | grep -iE "talkback|speech|utterance" > "$OUT/trash-talkback.logcat.txt" || true
      if grep -qF "Release to close" "$OUT/trash-talkback.logcat.txt"; then
        log "talkback: its log has Release to close"
      else
        log "talkback: its log has no Release to close"
      fi
    fi
    trash_restore
  fi
  # 10. No crash; close the head.
  app_logcat trash
  [ ! -s "$OUT/trash.crash.txt" ] || fail "crash during the trash drags (see trash.crash.txt)"
  close_all_heads
  log "API level: $(a shell getprop ro.build.version.sdk | tr -d '\r')"
  log "trash passed"
}

case "$WHICH" in
  head) check_head ;;
  permission) check_permission ;;
  bubble) check_bubble ;;
  refused) check_refused ;;
  restart) check_restart ;;
  rotation) check_rotation ;;
  resnap) check_resnap ;;
  fling) check_fling ;;
  speech) check_speech ;;
  trash) check_trash ;;
  *) echo "unknown check: $WHICH" >&2; exit 2 ;;
esac
