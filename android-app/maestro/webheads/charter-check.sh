#!/usr/bin/env bash
# The visible path of a 2.1.3 user who updates and turns on web heads:
#
#   1. upgrade-check.sh: 2.1.3 seeded, the new debug build installed over it, values read back
#   2. the mode switched to "Slide over"; a link is intercepted and opens in a tab (a Custom Tab,
#      or the app's web view when the seeded user chose it)
#   3. web heads chosen without the overlay permission: explainer, system screen (permission.yaml);
#      after the grant the stored setting reads web heads on
#   4. drag-check.sh: the person watches the recording and signs feel-checklist.md
#   5. native bubbles: webheads-check.sh ac23 (bubble screenshot)
#
# Usage: maestro/webheads/charter-check.sh <serial> <evidence-dir>   (from android-app/)
set -euo pipefail

SERIAL="${1:?usage: charter-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: charter-check.sh <serial> <evidence-dir>}"
source "$(dirname "$0")/lib.sh"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME=charter
CHECK_NAME="charter check"
: > "$OUT/charter.txt"
UPGRADE="$(hostpath "$(cd "$HERE/../upgrade" && pwd)")"

# The value of one settings key as stored and as read, from the debug-only dump receiver.
setting() {
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  for _ in $(seq 1 40); do
    a logcat -d -s StoreDump:I | grep "done" >/dev/null && break
    sleep 0.5
  done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' > "$OUT/$2"
  awk -F'\t' -v k="$1" '$1 == k { print $3 }' "$OUT/$2"
}

# 1. Upgrade from 2.1.3.
bash "$UPGRADE/upgrade-check.sh" "$SERIAL" "$OUT/upgrade" || fail "the upgrade check failed (see upgrade/)"
log "step 1: upgrade check passed"

# 2. Custom Tab mode and a link. The 2.1.3 seed turns AMP and article mode on (step 1 read them
#    back); they would send the link to the reader instead, so they are switched off first.
choose_mode "Slide over"
[ "$(setting amp_mode_pref charter-2-settings-before.tsv)" = "true" ] && { tap_text "Accelerated Mobile Pages (AMP)"; sleep 1; }
[ "$(setting article_mode_pref charter-2-settings-before.tsv)" = "true" ] && { tap_text "Article mode"; sleep 1; }
[ "$(setting amp_mode_pref charter-2-settings.tsv)" = "false" ] || fail "AMP mode is still on"
[ "$(setting article_mode_pref charter-2-settings.tsv)" = "false" ] || fail "article mode is still on"
a shell input keyevent HOME
sleep 1
a logcat -c
open_link "https://example.com/?charter=1"
sleep 8
shot charter-2-page
a logcat -d | tr -d '\r' | grep "Open https://example.com/?charter=1" > "$OUT/charter-2-route.logcat.txt" ||
  fail "the link was not intercepted (no router line)"
a shell dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | tee -a "$OUT/charter.txt" |
  grep -qE "CustomTabActivity|WebViewActivity" || fail "the link did not open a page"
# The 2.1.3 seed sets use_webview_pref, so the page opens in the app's web view, not Chrome.
log "step 2: link opened in a tab"
a shell input keyevent HOME

# 3. Web heads with the permission explainer.
a shell am force-stop "$PKG"
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW deny
a shell am start -W -n "$PKG/$BROWSING_MODE" >/dev/null
sleep 2
maestro --device "$SERIAL" test --test-output-dir "$OUT/permission-flow" "$(hostpath "$HERE/permission.yaml")" | tee -a "$OUT/charter.txt"
# The person switches "Allow display over other apps" on; appops does the same here.
a shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
a shell input keyevent BACK
sleep 3
tap_text "Not now" || true
on="$(setting webhead_enabled_pref charter-3-settings.tsv)"
log "step 3: webhead_enabled_pref after the grant = $on"
[ "$on" = "true" ] || fail "web heads did not turn on after the grant"
a shell input keyevent HOME

# 4. Drag, with the person's sign-off.
bash "$HERE/drag-check.sh" "$SERIAL" "$OUT/drag" || fail "the drag check failed (see drag/)"
log "step 4: drag recorded; feel-checklist.md waits for the person's signature"

# 5. Native bubbles.
bash "$HERE/webheads-check.sh" "$SERIAL" "$OUT/bubbles" ac23 || fail "the native bubble check failed (see bubbles/)"
log "step 5: native bubble shown"
log "charter check done"
