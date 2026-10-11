#!/usr/bin/env bash
# Link-source check: after the update, per-app rules work without a background service. Run after
# upgrade-check.sh, on the same device (the seed turns per-app rules and the app-based toolbar
# colour on, and blacklists Messages).
#
#   1. grants usage access, cold-starts the app; the app starts with no crash
#   2. no app-detection service runs and the app shows no app-detection notification
#   3. brings Messages (blacklisted) to the front, then sends it a link through the app's link
#      entry point; the app logs Messages as the link source and its blacklist rule, and the
#      link goes to the secondary browser (Chrome), not to Lynket
#   4. the crash buffer holds no entry for the app
#
# Usage: maestro/upgrade/link-source-check.sh <serial> <evidence-dir>   (run from android-app/)
set -euo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: link-source-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: link-source-check.sh <serial> <evidence-dir>}"
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
PKG=arun.com.chromer.debug
SOURCE=com.google.android.apps.messaging
INTERCEPT=arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
URL="https://example.com/?from=link-source-check"

a() { adb -s "$SERIAL" "$@"; }
fail() { echo "link-source check failed: $*" | tee -a "$OUT/link-source.txt" >&2; exit 1; }
: > "$OUT/link-source.txt"

# 1. Usage access on, cold start.
a shell appops set "$PKG" GET_USAGE_STATS allow
a shell am force-stop "$PKG"
a logcat -c
a logcat -b crash -c || true
a shell am start -W -n "$PKG/arun.com.chromer.MainActivity" | tr -d '\r' > "$OUT/launch.txt"
sleep 4
a shell pidof "$PKG" >/dev/null || fail "the app is not running after a cold start"
echo "cold start: running" | tee -a "$OUT/link-source.txt"

# 2. No app-detection service, no app-detection notification.
a shell dumpsys activity services "$PKG" | tr -d '\r' > "$OUT/services.txt"
if grep -qi "AppDetectService" "$OUT/services.txt"; then fail "an app-detection service is running"; fi
a shell dumpsys notification --noredact | tr -d '\r' > "$OUT/notifications.txt"
if grep -i "pkg=$PKG" "$OUT/notifications.txt" | grep -qi "App detection"; then
  fail "the app shows an app-detection notification"
fi
# Earlier versions created the channel; the app deletes it at start (dumpsys keeps it, marked deleted).
if grep "mId='App detection service'" "$OUT/notifications.txt" | grep -vq "mDeleted=true"; then
  fail "the app-detection notification channel still exists"
fi
echo "app-detection service: none; app-detection notification: none" | tee -a "$OUT/link-source.txt"

# 3. A link from Messages follows its blacklist rule.
a shell am force-stop com.android.chrome
a shell monkey -p "$SOURCE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 3
a logcat -c
a shell am start -a android.intent.action.VIEW -d "$URL" -n "$PKG/$INTERCEPT" >/dev/null
sleep 5
a logcat -d | tr -d '\r' > "$OUT/route.logcat.txt"
a exec-out screencap -p > "$OUT/after-link.png"
a shell dumpsys activity activities | tr -d '\r' | grep -E "mResumedActivity|topResumedActivity" > "$OUT/resumed.txt" || true
grep -q "Link source: $SOURCE" "$OUT/route.logcat.txt" || fail "the link source was not $SOURCE (see route.logcat.txt)"
grep -q "Per-app rule for $SOURCE: blacklisted=true" "$OUT/route.logcat.txt" \
  || fail "the blacklist rule of $SOURCE was not applied (see route.logcat.txt)"
# The blacklist action hands the link to the secondary browser's package. Chrome has two VIEW
# activities, so the system may first show its chooser limited to Chrome (after-link.png).
grep -qE "com.android.chrome|com.android.internal.app.ResolverActivity" "$OUT/resumed.txt" \
  || fail "the link did not leave the app for the secondary browser (see resumed.txt)"
if grep -q "$PKG/" "$OUT/resumed.txt"; then fail "the link opened in the app despite the blacklist rule"; fi
echo "link from $SOURCE: blacklist rule followed, handed to com.android.chrome ($(grep -oE '[a-z.]+/[A-Za-z.$]+' "$OUT/resumed.txt" | head -1))" | tee -a "$OUT/link-source.txt"

# 4. No crash.
a logcat -d -b crash | tr -d '\r' > "$OUT/logcat-crash.txt"
if grep -q "$PKG" "$OUT/logcat-crash.txt"; then fail "the crash buffer holds an entry for $PKG"; fi
echo "link-source check passed" | tee -a "$OUT/link-source.txt"
