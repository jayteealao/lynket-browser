#!/usr/bin/env bash
# Release build on a device: the minified, resource-shrunk APK opens a link, the article reader
# and a web head. The release build type has no signing config, so the script signs a copy of the
# unsigned APK with this host's SDK debug key, for an emulator only.
#
#   build     ./gradlew :lynket:assembleRelease (skipped with SKIP_BUILD=1), then zipalign and
#             apksigner (SDK build-tools) into <evidence-dir>/lynket-release-debugkey.apk
#   install   stops if arun.com.chromer is already installed (it may be a store install with
#             another key; nothing is uninstalled that this script did not install)
#   link      a VIEW intent through the link entry point opens a Chrome Custom Tab
#   reader    the Article tile on, a fixed article served from this host through adb reverse
#             (maestro/reader/fixtures) opens in the reader with its headline (the article extractor and jsoup
#             through R8); the tile off again
#   webhead   the Web heads tile on, a link shows at least one overlay window; the heads closed
#   crash     the crash buffer holds no entry for arun.com.chromer
#
# Each step writes PASS or FAIL to <evidence-dir>/release.txt; the exit code is 1 when any failed.
# The script uninstalls its own install at the end.
#
# Usage: maestro/release/release-check.sh <serial> <evidence-dir>   (run from android-app/)
set -euo pipefail

USAGE="usage: release-check.sh <serial> <evidence-dir>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
PKG=arun.com.chromer
RL_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
ROOT="$RL_HERE/../.."
FIXTURES="$RL_HERE/../reader/fixtures"
WEB_HEAD_TILE=$PKG.bubbles.webheads.qs.WebHeadTile
ARTICLE_TILE=$PKG.browsing.article.qs.ArticleTile
HEADLINE="Android 17 brings resizable apps everywhere"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=release
CHECK_NAME="release check"
: > "$OUT/release.txt"

FAILED=0
pass() { log "PASS $1${2:+: $2}"; }
failed() { log "FAIL $1${2:+: $2}"; FAILED=1; }

# ---------------------------------------------------------------------------------------------
# Helpers

PORT=""
SERVER_PID=""
INSTALLED=0
cleanup() {
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
  if [ "$INSTALLED" = 1 ]; then a uninstall "$PKG" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

start_server() {
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  python -m http.server "$PORT" --bind 127.0.0.1 --directory "$FIXTURES" > "$OUT/server.log" 2>&1 &
  SERVER_PID=$!
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  local _
  for _ in $(seq 1 20); do
    python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/article.html')" 2>/dev/null && break
    sleep 0.5
  done
}

ui_dump() {
  local _
  for _ in 1 2 3 4 5; do
    a shell rm -f /sdcard/release-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/release-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/release-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/release-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}

wait_top() {  # $1 = pattern; waits up to 30 s for a matching top resumed activity
  local _ top=""
  for _ in $(seq 1 15); do
    top="$(top_activity)"
    echo "$top" | grep -q -- "$1" && { echo "$top"; return 0; }
    dismiss_anr
    sleep 2
  done
  echo "$top"
  return 1
}

click_tile() {  # $1 = tile class; a click registers only with Quick Settings open
  a shell cmd statusbar add-tile "$PKG/$1" >/dev/null 2>&1 || true
  a shell cmd statusbar expand-settings >/dev/null 2>&1 || true
  sleep 6
  a shell cmd statusbar click-tile "$PKG/$1" >/dev/null
  sleep 2
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
}

fresh_link() {  # $1 = url, from the home screen with no app process left over
  a shell input keyevent KEYCODE_HOME
  a shell am force-stop "$PKG" || true
  a shell am force-stop com.android.chrome || true
  open_link "$1"
}

# ---------------------------------------------------------------------------------------------
# Build and sign

cd "$ROOT"
if [ "${SKIP_BUILD:-0}" != 1 ]; then
  ./gradlew :lynket:assembleRelease --console=plain > "$OUT/assembleRelease.log" 2>&1 || { failed build "assembleRelease failed (see assembleRelease.log)"; exit 1; }
fi
UNSIGNED="$ROOT/lynket/build/outputs/apk/release/lynket-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { failed build "no APK at $UNSIGNED"; exit 1; }
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-}/Android/Sdk}}"
BT="$(hostpath "$SDK")/build-tools/${BUILD_TOOLS:-36.1.0}"
APK="$OUT/lynket-release-debugkey.apk"
"$BT/zipalign" -p -f 4 "$UNSIGNED" "$OUT/lynket-release-aligned.apk"
# The SDK's debug keystore with its published default alias and password (an emulator-only copy).
java -jar "$BT/lib/apksigner.jar" sign --ks "$(hostpath "$HOME/.android/debug.keystore")" \
  --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$APK" "$OUT/lynket-release-aligned.apk"
rm -f "$OUT/lynket-release-aligned.apk"
java -jar "$BT/lib/apksigner.jar" verify "$APK" && pass build "signed copy $APK" || { failed build "apksigner verify failed"; exit 1; }

# ---------------------------------------------------------------------------------------------
# Install

if a shell pm list packages | tr -d '\r' | grep -qx "package:$PKG"; then
  failed install "$PKG is already installed on $SERIAL; uninstall it yourself first"
  exit 1
fi
a install "$APK" | tr -d '\r' | tail -1 | grep -q '^Success' || { failed install "adb install failed"; exit 1; }
INSTALLED=1
a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
a shell am start -W -n "$PKG/$MAIN" >/dev/null  # first start (the intro), then out of the way
sleep 3
shot first-start
a shell am force-stop "$PKG"
a logcat -c
a logcat -b crash -c
pass install "$(a shell dumpsys package "$PKG" | tr -d '\r' | grep -m1 versionName | xargs)"

# ---------------------------------------------------------------------------------------------
# Link: a Custom Tab in Chrome

fresh_link "https://example.com/?release-check=1"
sleep 4
# Chrome passes the intent through its IntentDispatcher; the Custom Tab is the activity after it.
if top="$(wait_top customtabs.CustomTabActivity)"; then pass link "top activity $top"; else failed link "top activity $top"; fi
shot link

# ---------------------------------------------------------------------------------------------
# Reader: the Article tile, then the fixture article

start_server
click_tile "$ARTICLE_TILE"
reader_ok=0
for attempt in 1 2; do
  fresh_link "http://localhost:$PORT/article.html"
  if top="$(wait_top ArticleActivity)"; then
    for _ in $(seq 1 20); do
      ui_dump > "$OUT/reader.xml"
      grep -qF "$HEADLINE" "$OUT/reader.xml" && { reader_ok=1; break; }
      sleep 2
    done
    break
  fi
  # A click before Quick Settings had bound the tile is lost; click once more.
  log "attempt $attempt: top activity $top, not the reader; clicking the Article tile again"
  click_tile "$ARTICLE_TILE"
done
shot reader
if [ "$reader_ok" = 1 ]; then pass reader "ArticleActivity shows \"$HEADLINE\""; else failed reader "top activity $top; headline not found (see reader.xml)"; fi
click_tile "$ARTICLE_TILE"

# ---------------------------------------------------------------------------------------------
# Web head: the Web heads tile, then a link

click_tile "$WEB_HEAD_TILE"
heads=0
for attempt in 1 2; do
  fresh_link "https://example.com/?release-check=head"
  sleep 8
  heads="$(overlay_count)"
  [ "$heads" -ge 1 ] && break
  log "attempt $attempt: no overlay window; clicking the Web heads tile again"
  click_tile "$WEB_HEAD_TILE"
done
a shell input keyevent KEYCODE_HOME
sleep 2
shot webhead
if [ "$heads" -ge 1 ]; then pass webhead "$heads overlay window(s)"; else failed webhead "no overlay window"; fi
close_all_heads
click_tile "$WEB_HEAD_TILE"

# ---------------------------------------------------------------------------------------------
# Crashes

app_logcat release
if grep -qE "arun\.com\.chromer([^.a-z]|$)" "$OUT/release.crash.txt"; then
  failed crash "the crash buffer holds an entry for $PKG (see release.crash.txt)"
else
  pass crash "no entry for $PKG in the crash buffer"
fi

exit "$FAILED"
