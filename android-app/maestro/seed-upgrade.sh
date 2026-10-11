#!/usr/bin/env bash
# Seeds an emulator with the user data of store release 2.1.3, written by the 2.1.3 app itself,
# for the upgrade test: install the new build over it afterwards with `adb install -r`.
#
#   1. installs the 2.1.3 debug build (maestro/build-old-apk.sh makes it) on a clean device state
#   2. passes the intro once (first-run state of an upgrading person)
#   3. opens five links through the app's own link entry point, so the app records history
#   4. sets three per-app rules in the 2.1.3 per-app settings screen
#   5. pins a home-screen shortcut through the 2.1.3 shortcut creator
#   6. merges seed/default_preferences.xml (every settings key, non-default values) into the
#      app's settings file while the app is stopped
#   7. dumps every store to the evidence folder and checks it
#
# Usage: maestro/seed-upgrade.sh <serial> <evidence-dir>   (run from android-app/)
# Needs: adb, Maestro, host sqlite3, the 2.1.3 APK at build/old-apk/lynket-2.1.3-debug.apk.
set -euo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: seed-upgrade.sh <serial> <evidence-dir>}"
OUT="${2:?usage: seed-upgrade.sh <serial> <evidence-dir>}"
# Windows tools (adb, aapt2) need host paths as C:/...; MSYS path conversion is off for adb's
# device paths, so convert host paths here.
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
here="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
app_root="$(hostpath "$(cd "$here/.." && pwd)")"
apk="${OLD_APK:-$app_root/build/old-apk/lynket-2.1.3-debug.apk}"
PKG=arun.com.chromer.debug
INTERCEPT=arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
PREFS=shared_prefs/${PKG}_preferences.xml
FIXTURE="$here/seed/default_preferences.xml"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
tmp="$(hostpath "$(mktemp -d)")"
trap 'rm -rf "$tmp"' EXIT

a() { adb -s "$SERIAL" "$@"; }
flow() { maestro --device "$SERIAL" test -e OUT="$OUT" "$@"; }
fail() { echo "seed check failed: $*" >&2; exit 1; }
open_link() {  # $1 = url; opens it through the 2.1.3 link entry point (a Custom Tab)
  a shell input keyevent HOME
  a shell am force-stop com.android.chrome
  a shell "am start -W -a android.intent.action.VIEW -d '$1' -n $PKG/$INTERCEPT" >/dev/null
  sleep 3
}

[ -f "$apk" ] || fail "2.1.3 APK missing at $apk; run maestro/build-old-apk.sh first"

# 1. Clean install of 2.1.3.
a uninstall "$PKG" >/dev/null 2>&1 || true
a install "$apk"

# 2. First run.
flow "$here/seed/first-run.yaml"

# 3. History: five links, one with ?, #, % and a non-ASCII path.
URLS=(
  "https://example.com/"
  "https://example.org/docs/page.html"
  "https://www.wikipedia.org/"
  "https://example.com/search?q=lynket&lang=en"
  "https://example.com/caf%C3%A9/über-straße?q=a%20b&x=1#top"
)
printf '%s\n' "${URLS[@]}" > "$OUT/seeded-urls.txt"
for url in "${URLS[@]}"; do open_link "$url"; done

# 4. Per-app rules (the per-app switch needs usage access).
a shell appops set "$PKG" GET_USAGE_STATS allow
a shell am force-stop "$PKG"
flow "$here/seed/per-app-rules.yaml"

# 5. Pinned shortcut for the first seeded link.
open_link "${URLS[0]}"
flow "$here/seed/pin-shortcut.yaml"
sleep 2
a shell input keyevent HOME
sleep 2
a exec-out screencap -p > "$OUT/seed-shortcut-home.png"

# 6. Settings: merge the fixture into the app's own file while the app is stopped.
a shell am force-stop "$PKG"
a shell am force-stop com.android.chrome
a exec-out run-as "$PKG" cat "$PREFS" | tr -d '\r' > "$tmp/before.xml"
cp "$tmp/before.xml" "$OUT/prefs-before-merge.xml"
for key in blacklist_preference blacklist_preference_dummy firstrun_3; do
  want="$(grep "name=\"$key\"" "$FIXTURE")"
  grep -qF "$(echo "$want" | sed 's/^ *//')" "$tmp/before.xml" \
    || fail "$key on the device differs from the fixture (the 2.1.3 flows wrote it): $(grep "name=\"$key\"" "$tmp/before.xml" || echo absent)"
done
grep -o 'name="[^"]*"' "$FIXTURE" | sed 's/name="//; s/"$//' > "$tmp/keys.txt"
[ "$(wc -l < "$tmp/keys.txt")" -eq 35 ] || fail "fixture holds $(wc -l < "$tmp/keys.txt") keys, expected 35"
{
  grep -v -F -f <(sed 's/.*/name="&"/' "$tmp/keys.txt") "$tmp/before.xml" | grep -v '</map>'
  grep -E '^ *<(int|string|boolean|long|float) ' "$FIXTURE"
  echo '</map>'
} > "$tmp/merged.xml"
a exec-in run-as "$PKG" sh -c "cat > $PREFS" < "$tmp/merged.xml"

# 7. Dump and check.
d="$OUT"
a exec-out run-as "$PKG" ls -R > "$d/app-files.txt"
a exec-out run-as "$PKG" cat "$PREFS" | tr -d '\r' > "$d/prefs.xml"
while read -r key; do
  want="$(grep "name=\"$key\"" "$FIXTURE" | sed 's/^ *//')"
  grep -qF "$want" "$d/prefs.xml" || fail "settings key $key missing or changed in the dump"
done < "$tmp/keys.txt"
echo "settings: all 35 keys hold the fixture values" | tee "$d/checks.txt"

a exec-out run-as "$PKG" tar -cf - files/APPS > "$d/paper-APPS.tar"
tar -tvf - < "$d/paper-APPS.tar" > "$d/paper-APPS-listing.txt"
for p in com.google.android.calendar com.google.android.deskclock com.google.android.apps.messaging; do
  grep -q "files/APPS/$p.pt" "$d/paper-APPS-listing.txt" || fail "no per-app entry for $p"
done
echo "per-app rules: entries for com.google.android.calendar, com.google.android.deskclock and com.google.android.apps.messaging" | tee -a "$d/checks.txt"

a exec-out run-as "$PKG" cat databases/History > "$d/History.db"
if a exec-out run-as "$PKG" ls databases | grep -q 'History-journal'; then
  a exec-out run-as "$PKG" cat databases/History-journal > "$d/History.db-journal"
fi
sqlite3 "$d/History.db" "select count(*) from History;" > "$d/history-count.txt"
sqlite3 -header "$d/History.db" "select _ID, URL, VISITED from History order by _ID;" > "$d/history-rows.txt"
sqlite3 "$d/History.db" "select URL from History order by _ID;" | tr -d '\r' > "$tmp/stored-urls.txt"
[ "$(cat "$d/history-count.txt" | tr -d '\r')" -eq ${#URLS[@]} ] || fail "history holds $(cat "$d/history-count.txt") rows, expected ${#URLS[@]}"
for url in "${URLS[@]}"; do grep -qxF "$url" "$tmp/stored-urls.txt" || fail "history row missing: $url"; done
echo "history: ${#URLS[@]} rows with the exact seeded URLs" | tee -a "$d/checks.txt"

a shell dumpsys shortcut > "$tmp/shortcut.txt"
awk -v pkg="Package: $PKG " 'index($0, pkg) {f=1; print; next} f && /^ *Package: / {f=0} f' "$tmp/shortcut.txt" \
  | tr -d '\r' > "$d/dumpsys-shortcut.txt"
# The launcher lists the pin ("Pinned: <id>"); the app's entry carries the Pin flag.
grep -qF "Pinned: ${URLS[0]}" "$d/dumpsys-shortcut.txt" || fail "launcher holds no pin for ${URLS[0]}"
grep -F "ShortcutInfo {id=${URLS[0]}," "$d/dumpsys-shortcut.txt" | grep -q '\[Pin' \
  || fail "shortcut ${URLS[0]} is not flagged pinned"
echo "shortcut: one pinned shortcut, id ${URLS[0]}, target BrowserInterceptActivity" | tee -a "$d/checks.txt"

sdk="$(sed -n 's/^sdk.dir=//p' "$app_root/local.properties" | sed 's/\\:/:/; s/\\\\/\//g')"
a pull "$(a shell pm path "$PKG" | tr -d '\r' | sed 's/^package://')" "$tmp/installed.apk" >/dev/null
"$sdk/build-tools/36.0.0/aapt2" dump badging "$tmp/installed.apk" | head -1 > "$d/installed-badging.txt"
grep -q "versionCode='56'" "$d/installed-badging.txt" || fail "installed build is not versionCode 56"
cat "$d/installed-badging.txt" | tee -a "$d/checks.txt"
echo "seed complete"
