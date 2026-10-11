#!/usr/bin/env bash
# Captures screenshots of the app's current (pre-rebuild) screens for later side-by-side checks.
# Usage: capture-legacy.sh <serial> <output-dir>
# Needs: the debug build installed (./gradlew :lynket:installDebug), Maestro, the emulator's Chrome.
set -euo pipefail
export MSYS_NO_PATHCONV=1
SERIAL="$1"; OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=arun.com.chromer.debug
INTERCEPT=arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
mkdir -p "$OUT"

open_link() {  # $1 = url; opens it in a Custom Tab through the app's own entry point
  adb -s "$SERIAL" shell input keyevent HOME
  adb -s "$SERIAL" shell am force-stop com.android.chrome
  adb -s "$SERIAL" shell am start -W -a android.intent.action.VIEW -d "$1" -n "$PKG/$INTERCEPT" >/dev/null
  sleep 5
}

flow() { maestro --device "$SERIAL" test -e OUT="$OUT" "$@"; }

flow "$HERE/app-screens.yaml"

open_link "https://example.com/"
adb -s "$SERIAL" exec-out screencap -p > "$OUT/custom-tab.png"
flow "$HERE/options-menu.yaml"

item() {  # $1 = options-sheet item text, $2 = screenshot name
  open_link "https://example.com/?capture=$2"
  flow -e ITEM="$1" -e NAME="$2" "$HERE/options-item.yaml" || echo "capture failed: $2" >&2
}
item "New tab" new-tab
# Tabs, Add to Home Screen, Open with... and Open article view crash or fail in this build, so
# they are not captured; the capture index records why.

open_link "https://example.com/?capture=per-app"
flow "$HERE/per-app.yaml"
