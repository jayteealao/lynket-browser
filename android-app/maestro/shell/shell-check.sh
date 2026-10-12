#!/usr/bin/env bash
# Device checks of the home, the first run and the back stack on the debug build.
#
#   intro         fresh install: the intro shows on the first launch and not on the second
#                 (intro-first-run.yaml)
#   seed-recents  opens eight links through the link handler so the home has recents
#   captures      home, drawer, intro pages, changelog, About and Tips, in light and dark
#   offline       fresh install with airplane mode on: empty recents and the offline message with
#                 Retry; after airplane mode off and Retry the message is gone
#   back-gesture  a back swipe from the left edge, held mid-way, from About, from Tips and from
#                 the open drawer
#   deeplink      a web link handed to the launcher activity opens the browsing screen; a malformed
#                 one shows the link error
#
# Usage: maestro/shell/shell-check.sh <serial> <evidence-dir> <mode>   (from android-app/)
# intro, captures and offline reinstall the debug build, which clears the app's data on that device.
set -euo pipefail

USAGE="usage: shell-check.sh <serial> <evidence-dir> <intro|seed-recents|captures|offline|back-gesture|deeplink>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
WHICH="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
SHELL_HERE="$(cd "$(dirname "$0")" && pwd)"
APK="$SHELL_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk"
MAIN=arun.com.chromer.MainActivity
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$WHICH"
CHECK_NAME="shell $WHICH check"
: > "$OUT/$WHICH.txt"

# The screen's UI tree. A dump fails while the screen is still moving, so the old file is removed
# first and must not stand in for the new screen.
ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/shell-ui.xml
    a shell uiautomator dump /sdcard/shell-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/shell-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/shell-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

# Centre of the first node matching the grep arguments, from a fresh dump; empty if none.
centre_of() {
  ui_dump | tr '>' '\n' | grep -F "$@" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Compose test tags are exposed as resource ids.
id_centre() { centre_of -e "resource-id=\"$1\""; }

# Replaces the shared helper, which can read a stale dump.
node_centre() { centre_of -e "text=\"$1\"" -e "content-desc=\"$1\""; }

tap_id() {
  local xy
  xy="$(id_centre "$1")"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  a shell input tap $xy
}

has_id() { [ -n "$(id_centre "$1")" ]; }
has_text() { [ -n "$(node_centre "$1")" ]; }

wait_for_id() {  # $1 = resource id, $2 = seconds
  local i
  for i in $(seq 1 "${2:-15}"); do has_id "$1" && return 0; sleep 1; done
  return 1
}

fresh_install() {
  [ -f "$APK" ] || fail "missing $APK; run ./gradlew :lynket:assembleDebug"
  a uninstall "$PKG" >/dev/null 2>&1 || true
  a install -r "$(hostpath "$APK")" >/dev/null
}

launch() {
  a shell am start -W -n "$PKG/$MAIN" >/dev/null
  sleep 3
}

# Past a first run: skip the intro and close the changelog when they show.
past_first_run() {
  local i
  for i in $(seq 1 30); do has_id intro || has_id home-search-field && break; sleep 1; done
  tap_id intro-skip && sleep 2 || true
  tap_text "OK" && sleep 2 || true
  wait_for_id home-search-field 30 || fail "home did not show"
}

crash_check() {  # $1 = evidence name
  app_logcat "$1"
  if grep -E "FATAL EXCEPTION" "$OUT/$1.logcat.txt" "$OUT/$1.crash.txt" >/dev/null 2>&1; then
    fail "crash (see $1.logcat.txt, $1.crash.txt)"
  fi
}

intro() {
  fresh_install
  a logcat -c
  maestro --device "$SERIAL" test -e OUT="$OUT" --test-output-dir "$OUT/maestro" \
    "$(hostpath "$SHELL_HERE/intro-first-run.yaml")" | tee "$OUT/intro.maestro.txt"
  crash_check intro
  log "intro passed"
}

seed_recents() {
  dismiss_anr
  local site
  for site in github.com wikipedia.org reddit.com medium.com youtube.com news.ycombinator.com theverge.com android.com; do
    open_link "https://$site/"
    sleep 6
    a shell input keyevent HOME
    sleep 1
  done
  log "seed-recents opened 8 links"
}

night() { a shell cmd uimode night "$1" >/dev/null; sleep 2; }

captures() {
  fresh_install
  a logcat -c
  night no
  launch
  shot intro-1
  local page
  for page in 2 3 4 5 6 7 8 9; do
    tap_id intro-next || break
    sleep 1
    shot "intro-$page"
  done
  { tap_id intro-done || tap_id intro-skip || true; }
  sleep 2
  if has_text "Changelog"; then shot changelog; tap_text "OK"; sleep 2; fi
  wait_for_id home-search-field 15 || fail "home did not show"
  shot home--empty
  seed_recents
  launch
  shot home--default
  tap_id home-menu || fail "no menu button"
  sleep 2
  shot home--drawer
  tap_text "About" || fail "no About in the drawer"
  sleep 2
  shot about
  a shell input keyevent BACK
  sleep 2
  tap_id home-tips-card || fail "no tips card"
  sleep 2
  shot tips
  a shell input keyevent BACK
  sleep 2
  night yes
  shot home--default--dark
  tap_id home-menu && sleep 2 && shot home--drawer--dark && a shell input keyevent BACK || true
  night no
  crash_check captures
  log "captures saved"
}

offline() {
  fresh_install
  launch
  past_first_run
  a logcat -c
  a shell cmd connectivity airplane-mode enable
  sleep 5
  shot offline-on
  has_text "You're offline. Pages open when you reconnect." || fail "no offline message with airplane mode on"
  has_id empty-state || fail "no empty state on a fresh install"
  a shell cmd connectivity airplane-mode disable
  sleep 8
  tap_text "Retry" || true
  sleep 3
  shot offline-off
  if has_text "You're offline. Pages open when you reconnect."; then fail "offline message still shown"; fi
  crash_check offline
  log "offline passed"
}

# Holds a back swipe from the left edge at 40% of the width, captures it, then lets go.
back_swipe() {  # $1 = capture name
  local w h y
  read -r w h < <(screen_size)
  y=$((h / 2))
  a shell input motionevent DOWN 2 "$y"
  local x
  for x in $((w / 20)) $((w / 10)) $((w / 5)) $((w * 3 / 10)) $((w * 2 / 5)); do
    a shell input motionevent MOVE "$x" "$y"
  done
  shot "$1"
  a shell input motionevent UP $((w * 2 / 5)) "$y"
  sleep 2
}

back_gesture() {
  launch
  past_first_run
  a logcat -c
  tap_id home-menu || fail "no menu button"
  sleep 2
  tap_text "About" || fail "no About in the drawer"
  sleep 2
  back_swipe back-about-mid
  shot back-about-after
  has_id home-search-field || fail "back from About did not show home"
  tap_id home-tips-card || fail "no tips card"
  sleep 2
  back_swipe back-tips-mid
  has_id home-search-field || fail "back from Tips did not show home"
  tap_id home-menu
  sleep 2
  back_swipe back-drawer-mid
  if has_id home-drawer; then fail "back did not close the drawer"; fi
  has_id home-search-field || fail "back from the drawer left home"
  crash_check back-gesture
  log "back-gesture passed"
}

deeplink() {
  launch
  past_first_run
  a shell am force-stop "$PKG"
  a logcat -c
  local url='https://example.com/a%20b?q=ü&x=1#frag'
  a shell "am start -W -a android.intent.action.VIEW -d '$url' -n $PKG/$MAIN" >/dev/null
  sleep 5
  shot deeplink-valid
  app_logcat deeplink-valid
  a shell am force-stop "$PKG"
  a logcat -c
  a shell "am start -W -a android.intent.action.VIEW -d 'https://' -n $PKG/$MAIN" >/dev/null
  sleep 4
  shot deeplink-malformed
  has_id link-error || fail "a malformed link did not show the link error"
  crash_check deeplink
  log "deeplink passed"
}

case "$WHICH" in
  intro) intro ;;
  seed-recents) seed_recents ;;
  captures) captures ;;
  offline) offline ;;
  back-gesture) back_gesture ;;
  deeplink) deeplink ;;
  *) fail "unknown mode $WHICH" ;;
esac
