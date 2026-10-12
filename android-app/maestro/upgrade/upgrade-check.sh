#!/usr/bin/env bash
# Upgrade check for settings and per-app rules: seeds a 2.1.3 user, installs the new debug build
# over it, and proves every settings key and per-app rule survived.
#
#   1. seed-upgrade.sh: clean install of the 2.1.3 debug build, seeded by the 2.1.3 app itself
#   2. `adb install -r` of the new debug build (same applicationId and signing key)
#   3. launch, so the settings and per-app migrations run
#   4. dump both stores with the debug-only dump receiver and pull the dump
#   5. compare with the seed fixture: every key with its value (list settings as ints), no failed
#      key, three per-app rules, both backups present, the old settings file still present
#   6. Maestro: the legacy settings screens and the per-app screen show the seeded values
#   7. the crash buffer holds no entry for the app
#
# Usage: maestro/upgrade/upgrade-check.sh <serial> <evidence-dir>   (run from android-app/)
# Needs: adb, Maestro, host sqlite3, the 2.1.3 APK (maestro/build-old-apk.sh makes it) and the
# new debug APK (./gradlew :lynket:assembleDebug).
set -euo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: upgrade-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: upgrade-check.sh <serial> <evidence-dir>}"
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
here="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
maestro_root="$(hostpath "$(cd "$here/.." && pwd)")"
app_root="$(hostpath "$(cd "$maestro_root/.." && pwd)")"
NEW_APK="${NEW_APK:-$app_root/lynket/build/outputs/apk/debug/lynket-debug.apk}"
OLD_APK="${OLD_APK:-$app_root/build/old-apk/lynket-2.1.3-debug.apk}"
PKG=arun.com.chromer.debug
FIXTURE="$maestro_root/seed/default_preferences.xml"
LIST_KEYS=" animation_preference animation_speed_preference preferred_action_preference webhead_spawn_preference webhead_size_preference article_theme_preference "
RULES=("com.google.android.calendar blacklisted" "com.google.android.deskclock incognito" "com.google.android.apps.messaging blacklisted")
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"

a() { adb -s "$SERIAL" "$@"; }
flow() { maestro --device "$SERIAL" test -e OUT="$OUT" "$@"; }
fail() { echo "upgrade check failed: $*" | tee -a "$OUT/compare.txt" >&2; exit 1; }
pull() { a exec-out run-as "$PKG" cat "$1" | tr -d '\r'; }
dump() {  # $1 = folder for the four dump files
  mkdir -p "$1"
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  for _ in $(seq 1 40); do
    a logcat -d -s StoreDump:I | grep -q "done" && break
    sleep 0.5
  done
  a logcat -d -s StoreDump:I | grep -q "done" || fail "the store dump did not finish"
  for f in settings.tsv per-app.tsv settings-report.tsv per-app-report.tsv; do
    pull "files/debug-dump/$f" > "$1/$f" || true
  done
}

[ -f "$OLD_APK" ] || fail "2.1.3 APK missing at $OLD_APK; run maestro/build-old-apk.sh first"
[ -f "$NEW_APK" ] || fail "new debug APK missing at $NEW_APK; run ./gradlew :lynket:assembleDebug first"

# 1. Seed a 2.1.3 user.
OLD_APK="$OLD_APK" bash "$maestro_root/seed-upgrade.sh" "$SERIAL" "$OUT/seed"

# 2. Install the new build over it.
a install -r "$NEW_APK" 2>&1 | tr -d '\r' | tee "$OUT/install-r.txt"
grep -q '^Success' "$OUT/install-r.txt" || fail "adb install -r was refused (applicationId or signing key differ)"

# 3. Launch; the first settings read runs both migrations.
a logcat -c
a shell am start -W -n "$PKG/arun.com.chromer.MainActivity" | tr -d '\r' > "$OUT/launch.txt"
sleep 5
a exec-out screencap -p > "$OUT/after-update-launch.png"
pid="$(a shell pidof "$PKG" | tr -d '\r' || true)"
if [ -n "$pid" ]; then a logcat -d --pid="$pid" > "$OUT/upgrade.logcat.txt"; else a logcat -d > "$OUT/upgrade.logcat.txt"; fi

# 4. Dump both stores.
dump "$OUT"

# 5. Compare.
{
  echo "# key expected_type expected_value stored_type stored_value result"
  ok=0; total=0
  while IFS=$'\t' read -r kind key value; do
    total=$((total + 1))
    want_type="$kind"
    if [ "$kind" = string ] && [[ "$LIST_KEYS" == *" $key "* ]]; then want_type=int; fi
    row="$(awk -F'\t' -v k="$key" '$1 == k { print $2 "\t" $3 }' "$OUT/settings.tsv")"
    got_type="${row%%$'\t'*}"; got_value="${row#*$'\t'}"
    if [ "$got_type" = "$want_type" ] && [ "$got_value" = "$value" ]; then
      result=equal; ok=$((ok + 1))
    else
      result=DIFFERENT
    fi
    printf '%s %s %s %s %s %s\n' "$key" "$want_type" "$value" "$got_type" "$got_value" "$result"
  done < <(sed -nE \
    -e 's/^ *<(int|boolean|long|float) name="([^"]+)" value="([^"]*)" *\/>.*/\1\t\2\t\3/p' \
    -e 's/^ *<string name="([^"]+)">([^<]*)<\/string>.*/string\t\1\t\2/p' "$FIXTURE")
  echo "settings: $ok of $total keys equal"
} > "$OUT/compare.txt"
cat "$OUT/compare.txt"
grep -q "^settings: 35 of 35 keys equal$" "$OUT/compare.txt" || fail "not every settings key survived (see compare.txt)"

failed="$(awk -F'\t' '$2 == "failed"' "$OUT/settings-report.tsv" | wc -l | tr -d ' ')"
echo "settings migration: $failed failed keys" | tee -a "$OUT/compare.txt"
[ "$failed" -eq 0 ] || fail "the settings migration reported failed keys"

for rule in "${RULES[@]}"; do
  set -- $rule
  col=2; [ "$2" = incognito ] && col=3
  awk -F'\t' -v p="$1" -v c="$col" '$1 == p && $c == "true" { found = 1 } END { exit !found }' "$OUT/per-app.tsv" \
    || fail "per-app rule missing after the update: $1 $2"
  echo "per-app rule: $1 $2" | tee -a "$OUT/compare.txt"
done

{
  echo "## files/settings-migration"; a exec-out run-as "$PKG" ls -l files/settings-migration
  echo "## files/per-app-migration"; a exec-out run-as "$PKG" ls -lR files/per-app-migration
  echo "## shared_prefs"; a exec-out run-as "$PKG" ls -l shared_prefs
  echo "## files/APPS"; a exec-out run-as "$PKG" ls -l files/APPS
} | tr -d '\r' > "$OUT/migration-files.txt"
grep -q "${PKG}_preferences-[0-9]*\.xml" "$OUT/migration-files.txt" || fail "no settings backup"
grep -q "APPS-[0-9]*" "$OUT/migration-files.txt" || fail "no per-app backup"
grep -q " ${PKG}_preferences\.xml$" "$OUT/migration-files.txt" || fail "the old settings file is gone"
echo "backups: settings and per-app present; old settings file present" | tee -a "$OUT/compare.txt"

# 6. The screens show the values. The settings screens are opened by name through the main
#    activity, because the seeded web heads setting sends links to web heads.
a shell am start -W -n "$PKG/arun.com.chromer.MainActivity" --es arun.com.chromer.extra.DESTINATION Settings >/dev/null
flow "$here/settings-values.yaml"
a shell am start -W -n "$PKG/arun.com.chromer.MainActivity" --es arun.com.chromer.extra.DESTINATION PerApp >/dev/null
flow "$here/per-app-values.yaml"

# 7. No crash.
a logcat -d -b crash | tr -d '\r' > "$OUT/logcat-crash.txt"
if grep -q "$PKG" "$OUT/logcat-crash.txt"; then fail "the crash buffer holds an entry for $PKG"; fi
echo "upgrade check passed" | tee -a "$OUT/compare.txt"
