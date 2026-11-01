#!/usr/bin/env bash
# Release build on a device: reads the per-app rules that release 2.1.3 saved with PaperDB (Kryo,
# through R8) and moves them into the per-app rule store, as the debug-build upgrade check does.
# The release app is not debuggable, so the 2.1.3 book is placed in its data folder with adb root:
# this needs an emulator image that allows it (google_apis or default, not google_apis_playstore).
#
#   build     signs a copy of lynket/build/outputs/apk/release/lynket-release-unsigned.apk with
#             this host's SDK debug key (zipalign and apksigner, as release-check.sh); never builds
#   root      adb root, and the shell user is root
#   install   stops if arun.com.chromer is already installed (nothing is uninstalled that this
#             script did not install)
#   seed      the 2.1.3 book (files/APPS/*.pt, from <paper-APPS.tar>, which maestro/seed-upgrade.sh
#             writes to <dir>/seed/ during maestro/upgrade/upgrade-check.sh) goes into the app's data
#             folder with the app's owner and SELinux label
#   migrate   the per-app screen opens and the migration writes files/per-app-migration/report.tsv
#   report    no record failed; Calendar and Messages are blacklisted, Clock is incognito; the rule
#             store and the original book are both present
#   crash     the crash buffer holds no entry for arun.com.chromer, and the app's log names no
#             KryoException, ClassNotFoundException, NoSuchFieldException or NoSuchMethodException
#
# Each step writes PASS or FAIL to <evidence-dir>/per-app-release.txt; the exit code is 1 when any
# failed. The script uninstalls its own install at the end.
#
# Usage: maestro/release/per-app-upgrade-check.sh <serial> <evidence-dir> <paper-APPS.tar>
#        (run from android-app/)
set -euo pipefail

USAGE="usage: per-app-upgrade-check.sh <serial> <evidence-dir> <paper-APPS.tar>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
TAR="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
PKG=arun.com.chromer
RL_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
ROOT="$RL_HERE/../.."
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=per-app-release
CHECK_NAME="release per-app check"
: > "$OUT/per-app-release.txt"
DATA="/data/data/$PKG"
REPORT="$DATA/files/per-app-migration/report.tsv"

FAILED=0
pass() { log "PASS $1${2:+: $2}"; }
failed() { log "FAIL $1${2:+: $2}"; FAILED=1; }

INSTALLED=0
cleanup() {
  a shell rm -f /data/local/tmp/paper-APPS.tar >/dev/null 2>&1 || true
  if [ "$INSTALLED" = 1 ]; then a uninstall "$PKG" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

[ -f "$TAR" ] || { failed seed "no tar at $TAR"; exit 1; }
TAR="$(hostpath "$(cd "$(dirname "$TAR")" && pwd)")/$(basename "$TAR")"

# ---------------------------------------------------------------------------------------------
# Sign

cd "$ROOT"
UNSIGNED="$ROOT/lynket/build/outputs/apk/release/lynket-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { failed build "no APK at $UNSIGNED (run ./gradlew :lynket:assembleRelease)"; exit 1; }
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
# Root

a root >/dev/null 2>&1 || true
a wait-for-device
sleep 2
if [ "$(a shell whoami | tr -d '\r')" = root ]; then
  pass root "adb shell runs as root"
else
  failed root "adb root was refused; needs a google_apis or default image"
  exit 1
fi

# ---------------------------------------------------------------------------------------------
# Install

if a shell pm list packages | tr -d '\r' | grep -qx "package:$PKG"; then
  failed install "$PKG is already installed on $SERIAL; uninstall it yourself first"
  exit 1
fi
a install "$APK" | tr -d '\r' | tail -1 | grep -q '^Success' || { failed install "adb install failed"; exit 1; }
INSTALLED=1
a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
pass install "$(a shell dumpsys package "$PKG" | tr -d '\r' | grep -m1 versionName | xargs)"

# ---------------------------------------------------------------------------------------------
# Seed: the 2.1.3 book in the app's data folder, before the app first runs

a push "$TAR" /data/local/tmp/paper-APPS.tar >/dev/null
APP_UID="$(a shell stat -c %u "$DATA" | tr -d '\r')"
a shell "cd $DATA && tar -xf /data/local/tmp/paper-APPS.tar && chown -R $APP_UID:$APP_UID $DATA/files && restorecon -R $DATA/files"
a shell ls -ln "$DATA/files/APPS" | tr -d '\r' > "$OUT/seed-listing.txt"
records="$(grep -c '\.pt$' "$OUT/seed-listing.txt" || true)"
if [ "$records" -ge 3 ]; then pass seed "$records records in files/APPS (owner $APP_UID)"; else failed seed "$records records in files/APPS (see seed-listing.txt)"; exit 1; fi

# ---------------------------------------------------------------------------------------------
# Migrate: the per-app screen reads the per-app rule store, which runs the migration once

a logcat -c
a logcat -b crash -c
for attempt in 1 2; do
  a shell am start -W -n "$PKG/$MAIN" --es "$DESTINATION" PerApp >/dev/null
  for _ in $(seq 1 15); do
    a shell ls "$REPORT" >/dev/null 2>&1 && break 2
    sleep 2
  done
  # The first start can show the intro instead of the per-app screen; start once more.
  log "attempt $attempt: no report yet; starting the app again"
  a shell am force-stop "$PKG"
done
if a shell ls "$REPORT" >/dev/null 2>&1; then
  pass migrate "report.tsv written"
else
  failed migrate "no $REPORT after two starts"
fi

# ---------------------------------------------------------------------------------------------
# Report

a shell cat "$REPORT" 2>/dev/null | tr -d '\r' > "$OUT/report.tsv" || true
report_ok=1
if grep -q $'\tfailed' "$OUT/report.tsv"; then report_ok=0; log "a record failed to read: $(grep $'\tfailed' "$OUT/report.tsv" | cut -f1 | xargs)"; fi
expect() {  # $1 = package, $2 = field that must be true
  grep -qE "^$1"$'\t'"migrated .*$2=true" "$OUT/report.tsv" || { report_ok=0; log "$1: not migrated with $2=true"; }
}
expect com.google.android.calendar blacklisted
expect com.google.android.deskclock incognito
expect com.google.android.apps.messaging blacklisted
a shell ls "$DATA/files/datastore/per_app_rules.preferences_pb" >/dev/null 2>&1 || { report_ok=0; log "no per_app_rules store"; }
a shell ls "$DATA/files/APPS" >/dev/null 2>&1 || { report_ok=0; log "the 2.1.3 book is gone"; }
shot per-app
if [ "$report_ok" = 1 ]; then
  pass report "Calendar and Messages blacklisted, Clock incognito, no failed record"
else
  failed report "see report.tsv"
fi

# ---------------------------------------------------------------------------------------------
# Crashes and reflection errors

app_logcat per-app-release
if grep -qE "arun\.com\.chromer([^.a-z]|$)" "$OUT/per-app-release.crash.txt"; then
  failed crash "the crash buffer holds an entry for $PKG (see per-app-release.crash.txt)"
elif grep -qE 'KryoException|ClassNotFoundException|NoSuchFieldException|NoSuchMethodException' "$OUT/per-app-release.logcat.txt"; then
  failed crash "the app log names a reflection error (see per-app-release.logcat.txt)"
else
  pass crash "no crash entry and no reflection error for $PKG"
fi

exit "$FAILED"
