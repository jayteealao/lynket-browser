#!/usr/bin/env bash
# Tile check: toggling the web heads Quick Settings tile changes the setting that every reader
# uses. Run after upgrade-check.sh, on the same device.
#
#   1. adds the web heads tile to Quick Settings and dumps the settings store
#   2. clicks the tile; the dump shows webhead_enabled_pref flipped, stored and reader values equal
#   3. the legacy Browsing mode screen shows the new state (layout dump and screenshot)
#   4. a link through the app's link entry point logs the web heads value the router read
#   5. clicks the tile again to restore the setting
#
# Usage: maestro/upgrade/tile-check.sh <serial> <evidence-dir>   (run from android-app/)
set -euo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: tile-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: tile-check.sh <serial> <evidence-dir>}"
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
PKG=arun.com.chromer.debug
TILE="$PKG/arun.com.chromer.bubbles.webheads.qs.WebHeadTile"
INTERCEPT=arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
KEY=webhead_enabled_pref

a() { adb -s "$SERIAL" "$@"; }
fail() { echo "tile check failed: $*" | tee -a "$OUT/tile.txt" >&2; exit 1; }
dump() {  # $1 = output file; prints "<stored> <reader>" of the web heads key
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  for _ in $(seq 1 40); do
    a logcat -d -s StoreDump:I | grep -q "done" && break
    sleep 0.5
  done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' > "$1"
  awk -F'\t' -v k="$KEY" '$1 == k { print $3, $4 }' "$1"
}
toggle() {
  # Click the tile in the open Quick Settings panel, as a person does. With the panel closed the
  # system may not have the tile service bound yet and drops the click.
  a shell cmd statusbar expand-settings >/dev/null 2>&1 || true
  sleep 4
  a shell cmd statusbar click-tile "$TILE" >/dev/null
  sleep 2
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
}

: > "$OUT/tile.txt"
a shell am force-stop "$PKG"  # ends a web head service restart loop left by an earlier run
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
a shell cmd statusbar add-tile "$TILE" | tr -d '\r' >> "$OUT/tile.txt"

read -r before before_reader < <(dump "$OUT/dump-before.tsv")
echo "before: stored=$before reader=$before_reader" | tee -a "$OUT/tile.txt"

toggle
read -r after after_reader < <(dump "$OUT/dump-after.tsv")
echo "after click: stored=$after reader=$after_reader" | tee -a "$OUT/tile.txt"
[ "$after" != "$before" ] || fail "the tile did not change $KEY"
[ "$after" = "$after_reader" ] || fail "stored and reader values differ"

# 3. The Browsing mode screen, opened by name through the main activity.
a shell am start -W -n "$PKG/arun.com.chromer.MainActivity" -f 0x24000000 --es arun.com.chromer.extra.DESTINATION BrowsingMode >/dev/null
sleep 2
android layout --device="$SERIAL" --pretty > "$OUT/browsing-mode.layout.txt"
a exec-out screencap -p > "$OUT/browsing-mode.png"
a shell input keyevent BACK

# 4. The router's read of the setting. Send the link from the home screen: a link sent while an
#    app with a per-app rule (the seed blacklists Messages) was in front follows that rule instead.
a shell input keyevent HOME
sleep 1
a logcat -c
a shell am force-stop com.android.chrome
a shell "am start -W -a android.intent.action.VIEW -d 'https://example.com/?tile-check=1' -n $PKG/$INTERCEPT" >/dev/null
sleep 4
a logcat -d | tr -d '\r' | grep "Open https://example.com/?tile-check=1" > "$OUT/route.logcat.txt" || true
grep -q "webHeads=$after" "$OUT/route.logcat.txt" || fail "the router did not read webHeads=$after (see route.logcat.txt)"
echo "router: webHeads=$after" | tee -a "$OUT/tile.txt"
a shell input keyevent HOME
# With web heads on, the link starts the web head service and its overlay windows. Stop the app
# so the restore click reaches a fresh tile service with no heads on screen.
a logcat -d -b crash | tr -d '\r' > "$OUT/route-crash.txt"
a shell am force-stop "$PKG"

# 5. Restore.
toggle
read -r restored restored_reader < <(dump "$OUT/dump-restored.tsv")
echo "restored: stored=$restored reader=$restored_reader" | tee -a "$OUT/tile.txt"
[ "$restored" = "$before" ] || fail "the second click did not restore $KEY"
echo "tile check passed" | tee -a "$OUT/tile.txt"
