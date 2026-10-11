#!/usr/bin/env bash
# Builds a debug APK of store release 2.1.3 for the upgrade test (AC-6).
#
# The tag does not build as tagged on a current host, so this script applies a
# build-only patch in a throwaway worktree:
#   - Fabric plugin and Crashlytics removed (repository returns 403)
#   - the five artifacts that no longer resolve are substituted with JitPack
#     builds of the same tags (PhotoView 1.3.1 has no build; 1.3.0 is used,
#     same uk.co.senab.photoview.PhotoView class, image viewer only)
#   - kapt processes as Java 8, so Dagger 2.23 output compiles on JDK 11
#   - base-android SNAPSHOT AARs from a pinned commit; build-tools 30.0.3
# The data packages (Preferences, per-app PaperDB store, legacy history) must
# stay identical to the tag; the script fails when they differ.
#
# Usage: maestro/build-old-apk.sh [evidence-dir]   (run from android-app/)
# Needs: JDK 11 at $HOME/.jdks/temurin-11, platforms/android-29, build-tools 30.0.3.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
app_root="$(cd "$here/.." && pwd)"
repo_root="$(git -C "$app_root" rev-parse --show-toplevel)"
evidence="${1:-$app_root/build/old-apk}"
# Short path: aidl.exe fails on long Windows paths.
wt="${OLD_APK_WORKTREE:-C:/w213}"
jdk="${JDK11_HOME:-$HOME/.jdks/temurin-11}"
out="$app_root/build/old-apk"
mkdir -p "$evidence" "$out"

cleanup() { git -C "$repo_root" worktree remove --force "$wt" >/dev/null 2>&1 || true; }
trap cleanup EXIT

"$jdk/bin/java" -version 2>&1 | head -1 | grep -q '"11\.' || { echo "JDK 11 not found at $jdk" >&2; exit 1; }

cleanup
git -C "$repo_root" worktree add --detach "$wt" 2.1.3
git -C "$wt" apply "$here/old-apk/2.1.3-build.patch"
cp "$app_root/local.properties" "$wt/android-app/local.properties"

{
  echo "== data-package diff against tag 2.1.3 (must be empty)"
  git -C "$wt" diff --exit-code 2.1.3 -- \
    android-app/app/src/main/java/arun/com/chromer/settings/Preferences.java \
    android-app/app/src/main/java/arun/com/chromer/data/apps/ \
    android-app/app/src/main/java/arun/com/chromer/data/history/
  echo "(empty)"
  echo "== patched files"
  git -C "$wt" diff --stat 2.1.3
} > "$evidence/data-diff.txt"

(cd "$wt/android-app" && JAVA_HOME="$jdk" ./gradlew --no-daemon \
  -PFABRIC_KEY=dummy -PPLAY_LICENSE_KEY=dummy :app:assembleDebug) > "$evidence/build.log" 2>&1

cp "$wt/android-app/app/build/outputs/apk/debug/app-debug.apk" "$out/lynket-2.1.3-debug.apk"

sdk="$(sed -n 's/^sdk.dir=//p' "$app_root/local.properties" | sed 's/\\:/:/; s/\\\\/\//g')"
bt="$sdk/build-tools/36.0.0"
new_apk="$app_root/lynket/build/outputs/apk/debug/lynket-debug.apk"
{
  echo "tag commit: $(git -C "$wt" rev-parse HEAD)"
  echo "old apk sha256: $(sha256sum "$out/lynket-2.1.3-debug.apk" | cut -d' ' -f1)"
  "$bt/aapt2" dump badging "$out/lynket-2.1.3-debug.apk" | head -1
  old_cert="$("$bt/apksigner.bat" verify --print-certs "$out/lynket-2.1.3-debug.apk" | grep 'SHA-256')"
  echo "old $old_cert"
  if [ -f "$new_apk" ]; then
    new_cert="$("$bt/apksigner.bat" verify --print-certs "$new_apk" | grep 'SHA-256')"
    echo "new $new_cert"
    [ "${old_cert#*: }" = "${new_cert#*: }" ] || { echo "signing certificates differ" >&2; exit 1; }
  fi
} | tee "$evidence/apk-identity.txt"
