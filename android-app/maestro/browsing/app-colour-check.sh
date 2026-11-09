#!/usr/bin/env bash
# App colour check: five links from one app in one process look for that app's colour once.
#
# The app-coloured toolbar takes the colour of the app a link came from. When that colour is not
# stored yet, the app looks for it in the background; it must do so once per app and process, not on
# every link open. The source app is the small white app in fixtures/no-colour-app/ (white theme,
# white icon), which this script builds with the SDK tools.
#
#   1. builds and installs the white app and installs the debug build (android install)
#   2. seeds seed/app-colour.xml (coloured toolbar, dynamic colour, based on app), grants usage access
#      and cold-starts the app; records its pid
#   3. five times: brings the white app to the front and sends a link to the app's link entry point,
#      waiting for "Link source: arun.com.chromer.fixture.nocolour"
#   4. reads the log of the app's process through lazylogcat and counts, for the white app, the
#      "Color not found for", "Extracting the colour of" and end ("Extracted ... for" or "No colour
#      found for") lines
#   5. the crash buffer, the pid, and the stored per-app colour of the white app (debug store dump)
#
# Pass: 5 link-source lines; exactly 1 "Color not found for", exactly 1 "Extracting the colour of"
# and exactly 1 end line for the white app; the same pid before and after; no crash entry for the
# app. The stored colour is recorded, not checked: the icon palette reads a white icon as #F8F8F8,
# so the white app may get a near-white colour after its one look-up.
#
# Writes app-colour.txt (PASS or FAIL lines), app-colour.logcat.txt, store.tsv and custom-tab-5.png.
#
# Usage: maestro/browsing/app-colour-check.sh <serial> <evidence-dir>   (run from android-app/)
#        maestro/browsing/app-colour-check.sh --build-fixture <dir>     (only builds the white app)
#
# adb is used where the Android CLI has no equivalent: app ops (usage access), am start with an intent (the CLI launches no intent with data or a component),
# run-as for the settings seed and the store dump, pidof, and logcat (read through lazylogcat).
set -euo pipefail
export MSYS_NO_PATHCONV=1

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$HERE/../.." && pwd)"
FIXTURE="$HERE/fixtures/no-colour-app"
SEED="$HERE/../seed/app-colour.xml"
SOURCE=arun.com.chromer.fixture.nocolour
APK_DEBUG="$APP_DIR/lynket/build/outputs/apk/debug/lynket-debug.apk"

hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }

sdk_dir() {
  local dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$dir" ] && [ -f "$APP_DIR/local.properties" ]; then
    dir="$(sed -n 's/^sdk.dir=//p' "$APP_DIR/local.properties" | sed 's/\\:/:/g; s/\\\\/\//g')"
  fi
  if [ -z "$dir" ] && [ -n "${LOCALAPPDATA:-}" ]; then dir="$LOCALAPPDATA/Android/Sdk"; fi
  [ -d "$dir" ] || { echo "no Android SDK found (set ANDROID_HOME)" >&2; exit 1; }
  echo "$dir"
}

tool() {
  local bt="$1" name="$2"
  for f in "$bt/$name" "$bt/$name.exe" "$bt/$name.bat"; do [ -f "$f" ] && { echo "$f"; return; }; done
  echo "no $name in $bt" >&2; exit 1
}

# Builds the white app into <dir>/no-colour.apk, signed with the SDK debug key; prints its path.
build_fixture() {
  local dir sdk bt jar keystore work
  dir="$1"; mkdir -p "$dir"; dir="$(cd "$dir" && pwd)"
  sdk="$(sdk_dir)"
  bt="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
  jar="$sdk/platforms/android-36/android.jar"
  [ -f "$jar" ] || { echo "no platforms/android-36 in $sdk" >&2; exit 1; }
  keystore="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
  [ -f "$keystore" ] || { echo "no debug keystore at $keystore" >&2; exit 1; }
  work="$dir/no-colour-build"; rm -rf "$work"; mkdir -p "$work"
  "$(tool "$bt" aapt2)" compile --dir "$(hostpath "$FIXTURE/res")" -o "$(hostpath "$work/res.zip")"
  "$(tool "$bt" aapt2)" link -o "$(hostpath "$work/unsigned.apk")" -I "$(hostpath "$jar")" \
    --manifest "$(hostpath "$FIXTURE/AndroidManifest.xml")" "$(hostpath "$work/res.zip")"
  "$(tool "$bt" zipalign)" -f 4 "$(hostpath "$work/unsigned.apk")" "$(hostpath "$work/aligned.apk")"
  "$(tool "$bt" apksigner)" sign --ks "$(hostpath "$keystore")" --ks-pass pass:android --key-pass pass:android \
    --out "$(hostpath "$dir/no-colour.apk")" "$(hostpath "$work/aligned.apk")"
  "$(tool "$bt" aapt2)" dump badging "$(hostpath "$dir/no-colour.apk")" | grep -E "^package|launchable-activity" >&2
  echo "$dir/no-colour.apk"
}

if [ "${1:-}" = "--build-fixture" ]; then
  build_fixture "${2:?usage: app-colour-check.sh --build-fixture <dir>}"
  exit 0
fi

SERIAL="${1:?usage: app-colour-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: app-colour-check.sh <serial> <evidence-dir>}"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
export CHECK_NAME="app colour check" LOG_NAME=app-colour
# shellcheck source=../webheads/lib.sh
# shellcheck disable=SC1091 # sdlc-debt: lib.sh is sourced by a path built at run time; shellcheck cannot follow it, no upgrade path.
. "$HERE/../webheads/lib.sh"
: > "$OUT/app-colour.txt"
FAILED=0
check() { if [ "$1" = ok ]; then log "PASS $2"; else log "FAIL $2"; FAILED=1; fi; }
# check_eq <actual> <expected> <what>
check_eq() { if [ "$1" = "$2" ]; then check ok "$3"; else check no "$3 (saw $1)"; fi; }

# 1. Install both apps.
[ -f "$APK_DEBUG" ] || fail "no debug build at $APK_DEBUG (run ./gradlew :lynket:assembleDebug)"
FIXTURE_APK="$(build_fixture "$OUT")"
android install --device="$SERIAL" --apks="$(hostpath "$FIXTURE_APK")" >/dev/null
android install --device="$SERIAL" --apks="$(hostpath "$APK_DEBUG")" >/dev/null
a shell pm list packages "$SOURCE" | grep -q "package:$SOURCE" || fail "the white app did not install"
log "installed $SOURCE and $PKG"

# 2. Settings, usage access, cold start.
seed_settings "$SEED"
a shell appops set "$PKG" GET_USAGE_STATS allow
a logcat -c
a logcat -b crash -c || true
a shell am start -W -n "$PKG/$MAIN" >/dev/null
sleep 4
PID_BEFORE="$(a shell pidof "$PKG" | tr -d '\r')"
[ -n "$PID_BEFORE" ] || fail "the app is not running after a cold start"
log "cold start: pid $PID_BEFORE"

# 3. Five links from the white app.
SOURCE_LINES=0
for i in 1 2 3 4 5; do
  a shell am start -W -n "$SOURCE/android.app.Activity" >/dev/null
  sleep 2
  a shell am start -a android.intent.action.VIEW -d "https://example.com/?from=app-colour-check&n=$i" \
    -n "$PKG/$INTERCEPT" >/dev/null
  seen=""
  for _ in $(seq 1 20); do
    count="$(a logcat -d | tr -d '\r' | grep -c "Link source: $SOURCE" || true)"
    if [ "$count" -ge "$i" ]; then seen=1; break; fi
    sleep 0.5
  done
  [ -n "$seen" ] && SOURCE_LINES=$((SOURCE_LINES + 1))
  sleep 3
  if [ "$i" = 5 ]; then
    android screen capture --device="$SERIAL" --output="$OUT/custom-tab-5.png" >/dev/null 2>&1 || shot custom-tab-5
  fi
  a shell input keyevent BACK
  sleep 1
done

# 4. The log of the app's process, filtered on the white app.
a logcat -d -v threadtime | tr -d '\r' \
  | lazylogcat logs parse --device "$SERIAL" --pkg "$PKG" --text "$SOURCE" --format text > "$OUT/app-colour.logcat.txt" || true
count_lines() { grep -c "$1" "$OUT/app-colour.logcat.txt" || true; }
NOT_FOUND="$(count_lines "Color not found for $SOURCE")"
STARTED="$(count_lines "Extracting the colour of $SOURCE")"
ENDED=$(( $(count_lines "Extracted -\?[0-9]* for $SOURCE") + $(count_lines "No colour found for $SOURCE") ))
log "link-source lines: $SOURCE_LINES; Color not found: $NOT_FOUND; extraction starts: $STARTED; extraction ends: $ENDED"
check_eq "$SOURCE_LINES" 5 "5 links came from $SOURCE"
check_eq "$NOT_FOUND" 1 "one 'Color not found' for $SOURCE"
check_eq "$STARTED" 1 "one extraction started for $SOURCE"
check_eq "$ENDED" 1 "one extraction ended for $SOURCE"

# 5. One process, no crash, the stored colour.
PID_AFTER="$(a shell pidof "$PKG" | tr -d '\r' || true)"
check_eq "$PID_AFTER" "$PID_BEFORE" "one process (pid $PID_BEFORE)"
a logcat -d -b crash | tr -d '\r' > "$OUT/app-colour-crash.txt"
check_eq "$(grep -c "$PKG" "$OUT/app-colour-crash.txt" || true)" 0 "no crash entry for $PKG"
a shell run-as "$PKG" rm -f files/debug-dump/per-app.tsv
a logcat -c
a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
for _ in $(seq 1 40); do
  if a logcat -d -s StoreDump:I | grep -q "done"; then break; fi
  sleep 0.5
done
a exec-out run-as "$PKG" cat files/debug-dump/per-app.tsv | tr -d '\r' > "$OUT/store.tsv" || true
STORED="$(awk -F'\t' -v p="$SOURCE" '$1 == p { print $4 }' "$OUT/store.tsv")"
log "stored colour of $SOURCE: ${STORED:-none}"

if [ "$FAILED" = 0 ]; then log "app colour check passed"; else log "app colour check failed"; exit 1; fi
