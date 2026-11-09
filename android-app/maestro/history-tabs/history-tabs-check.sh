#!/usr/bin/env bash
# Device check of the History and Tabs screens on the debug build.
#
#   legacy        reference captures of the History and Tabs screens of the installed build:
#                 History with 500 rows (history-check.sh seeds release 2.1.3 and updates over
#                 it), at the top and after two scrolls; Tabs with four links open in Custom Tabs
#                 (clean install)
#   captures      phone: History with 500 rows at the top, after a search and with the delete-all
#                 dialog open (then cancelled); Tabs with four links; both empty states on a clean
#                 install. Fold (a screen wider than 1800 px): History with 500 rows at the top
#   clear-all     500 rows, delete all through the dialog; the snackbar count equals the rows the
#                 store held, and the store is empty afterwards (clear-all.yaml)
#   tabs-reorder  two links open; a tap on the first tab's row in Tabs brings its own task forward
#                 and opens no new task (tabs-reorder.yaml)
#   back-gesture  home, then History from the drawer; a back swipe held mid-way shows home behind
#                 the screen, and releasing it returns home (run it on the fold)
#   worst         the worst addresses people keep (a long host with a deep path, an untitled
#                 host-only page, an IP address with a query, an untitled file: download, a
#                 content: address) seeded straight into the database: History, then Tabs with a
#                 long, a host-only and an IP address, then Tabs again at 200% font (worst-*.png and
#                 their UI trees; no address line shows https:// or www.)
#   motion        rows over two days; at a tenfold animation scale, row-motion.yaml swipes the
#                 second row away and taps Undo, then closes the second of three tabs, while the
#                 Android CLI takes frames about every 150 ms (motion-frames/) and the screen is
#                 recorded (motion.mp4)
#   motion-off    the same at animation scale 0, then one more delete with two frames 100 ms apart
#                 that both show the rows in their final place
# worst, motion and motion-off restore the font and animation scales on exit.
#
# Usage: maestro/history-tabs/history-tabs-check.sh <serial> <evidence-dir> <mode>   (from android-app/)
# SEED=direct skips history-check.sh and writes the 500 rows straight into the database.
# APK=<path> picks the debug APK (default: the last assembleDebug output). Every mode reinstalls
# the app, which clears its data on that device.
set -euo pipefail

USAGE="usage: history-tabs-check.sh <serial> <evidence-dir> <legacy|captures|clear-all|tabs-reorder|back-gesture|worst|motion|motion-off>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
HT_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
APK="${APK:-$HT_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk}"
HISTORY=arun.com.chromer.history.HistoryActivity
MAIN=arun.com.chromer.MainActivity
TABS=arun.com.chromer.tabs.ui.TabsActivity
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$MODE"
CHECK_NAME="history-tabs $MODE check"
: > "$OUT/$MODE.txt"
[ -f "$APK" ] || fail "debug APK missing at $APK; run ./gradlew :lynket:assembleDebug first"

# Maestro's driver keeps the device's UI automation after a flow ends, and every later
# uiautomator dump then hangs; stopping the driver frees it.
free_ui_automation() {
  a shell am force-stop dev.mobile.maestro >/dev/null 2>&1 || true
  a shell am force-stop dev.mobile.maestro.test >/dev/null 2>&1 || true
}

# The screen's UI tree. A dump fails while the screen is still moving, so the old file is removed
# first and must not stand in for the new screen.
ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/history-tabs-ui.xml
    # A dump can hang on a struggling emulator; it gets 30 s.
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/history-tabs-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/history-tabs-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/history-tabs-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}

# Waits until a node with this text or description shows (up to about 90 s on a busy emulator),
# clearing "isn't responding" dialogs on the way.
wait_text() {  # $1 = text
  local _
  for _ in $(seq 1 30); do
    ui_dump | grep -qF -e "text=\"$1\"" -e "content-desc=\"$1\"" && return 0
    dismiss_anr
    sleep 2
  done
  log "no \"$1\" on screen after waiting"
  return 1
}

capture() {  # $1 = name: a screenshot and the UI tree
  shot "$1"
  ui_dump > "$OUT/$1.xml"
}

scroll_down() {
  local w h
  read -r w h < <(screen_size)
  a shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 400
  sleep 2
}

fresh_install() {
  a uninstall "$PKG" >/dev/null 2>&1 || true
  a install "$APK" | tr -d '\r' | tail -1 | grep -q '^Success' || fail "install of $APK failed"
}

# Four links in Custom Tabs (the default browsing mode), each its own task, then home.
open_tabs() {  # $@ = URLs
  local url
  quiet_chrome
  for url in "$@"; do
    dismiss_anr
    open_link "$url"
    sleep 8
    dismiss_anr
  done
  a shell input keyevent KEYCODE_HOME
  sleep 1
}

# A hung Chrome on a busy emulator raises "Chrome isn't responding" over every screen; a fresh
# Chrome process clears it.
quiet_chrome() {
  a shell am force-stop com.android.chrome
  sleep 2
  tap_text "Close app" >/dev/null 2>&1 || true
}

crash_check() {  # $1 = name
  app_logcat "$1"
  if grep -q "$PKG" "$OUT/$1.crash.txt"; then fail "the crash buffer holds an entry for $PKG ($1)"; fi
}

# History with 500 rows: history-check.sh seeds release 2.1.3 and updates over it. A busy emulator
# can raise an "isn't responding" dialog (often Chrome's) over the seed flow; the seed runs once
# more after it is cleared.
seed_history() {  # $1 = evidence sub-folder
  local try
  if [ "${SEED:-}" = direct ]; then seed_rows; return 0; fi
  for try in 1 2; do
    dismiss_anr
    a shell am force-stop com.android.chrome
    if NEW_APK="$APK" bash "$HT_HERE/../upgrade/history-check.sh" "$SERIAL" "$OUT/$1-$try"; then
      log "history seeded (500 rows) on try $try"
      return 0
    fi
    log "history-check.sh did not pass on try $try (see $1-$try/compare.txt)"
    sleep 20
  done
  log "seeding 500 rows straight into the history database instead"
  seed_rows
}

# The 500 rows of seed_rows: one every 20 minutes back from now, so they span about a week; every
# 10th row has an AMP address. Same titles and addresses as history-check.sh appends ("Seed page
# NNN", https://seed.example/page-NNN).
seed_500_sql() {  # $1 = now in ms
  local n nnn amp
  for n in $(seq 1 500); do
    nnn="$(printf '%03d' "$n")"
    amp="NULL"; [ $((n % 10)) -eq 0 ] && amp="'https://seed.example/amp/page-$nnn'"
    echo "INSERT INTO history (url, title, amp_url, bookmarked, created_at, visit_count) VALUES ('https://seed.example/page-$nnn', 'Seed page $nnn', $amp, 0, $(($1 - n * 1200000)), $((n % 7 + 1)));"
  done
}

# Rows written into the app's own history database (fresh install). With no arguments, the 500
# rows of seed_500_sql; else $1 names a function that prints the INSERT statements for a given
# now, and $2 is the number of rows it writes.
seed_rows() {
  local sql="${1:-seed_500_sql}" want="${2:-500}"
  local tmp now
  tmp="$(hostpath "$(mktemp -d)")"
  fresh_install
  # The history database is created on first use: open the History screen once.
  a shell am start -W -n "$PKG/$HISTORY" >/dev/null
  pull_db() {
    rm -f "$tmp"/chromer.db*
    for f in chromer.db chromer.db-wal chromer.db-shm; do
      a exec-out run-as "$PKG" sh -c "cat databases/$f 2>/dev/null" > "$tmp/$f" || true
      [ -s "$tmp/$f" ] || rm -f "$tmp/$f"
    done
  }
  # Wait until the app has created the history table, then stop it and take the settled files.
  for _ in $(seq 1 45); do
    pull_db
    [ -f "$tmp/chromer.db" ] && [ "$(sqlite3 "$tmp/chromer.db" "select count(*) from sqlite_master where name = 'history';" 2>/dev/null | tr -d '\r')" = 1 ] && break
    sleep 2
  done
  sleep 3
  a shell am force-stop "$PKG"
  pull_db
  [ -f "$tmp/chromer.db" ] || fail "the app made no history database"
  now="$(($(date +%s) * 1000))"
  {
    echo "BEGIN;"
    "$sql" "$now"
    echo "COMMIT;"
    echo "PRAGMA wal_checkpoint(TRUNCATE);"
    echo "PRAGMA journal_mode=DELETE;"
  } | sqlite3 "$tmp/chromer.db" >/dev/null
  a exec-in run-as "$PKG" sh -c "cat > databases/chromer.db" < "$tmp/chromer.db"
  a shell run-as "$PKG" rm -f databases/chromer.db-wal databases/chromer.db-shm
  local rows
  rows="$(sqlite3 "$tmp/chromer.db" 'select count(*) from history;' | tr -d '\r')"
  rm -rf "$tmp"
  log "seeded rows: $rows"
  [ "$rows" = "$want" ] || fail "the history database holds $rows rows after seeding"
}

# Centre of the first node matching the grep arguments, from a fresh dump; empty if none.
centre_of() {
  ui_dump | tr '>' '\n' | grep -F "$@" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

# Compose test tags show as resource ids.
id_centre() { centre_of -e "resource-id=\"$1\""; }
node_centre() { centre_of -e "text=\"$1\"" -e "content-desc=\"$1\""; }
has_id() { [ -n "$(id_centre "$1")" ]; }
tap_id() {
  local xy
  xy="$(id_centre "$1")"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  a shell input tap $xy
}

wait_id() {  # $1 = test tag; up to about 90 s
  local _
  for _ in $(seq 1 30); do
    has_id "$1" && return 0
    dismiss_anr
    sleep 2
  done
  log "no $1 on screen after waiting"
  return 1
}

# Rows in the history store, from the debug build's store dump (header line not counted).
stored_rows() {  # $1 = output name
  local _
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  for _ in $(seq 1 30); do
    a logcat -d -s StoreDump:I | grep -q "done" && break
    sleep 1
  done
  a logcat -d -s StoreDump:I | grep -q "done" || fail "the store dump did not finish"
  a exec-out run-as "$PKG" cat files/debug-dump/history.tsv | tr -d '\r' > "$OUT/$1.tsv"
  echo $(($(wc -l < "$OUT/$1.tsv") - 1))
}

is_fold() {
  local w h
  read -r w h < <(screen_size)
  [ "$w" -gt 1800 ]
}

start_history() {
  a shell am start -W -n "$PKG/$HISTORY" >/dev/null
  wait_id history-list || fail "History did not show"
  sleep 2
}

captures() {
  seed_history history-seed
  quiet_chrome
  a logcat -c
  start_history
  if is_fold; then
    capture history-default-fold
    crash_check captures-fold
    log "captures (fold) done"
    return 0
  fi
  capture history-default-phone
  tap_id history-search-toggle || fail "no search action"
  wait_id history-search || fail "the search field did not open"
  tap_id history-search
  # Word by word: a busy emulator drops characters of one long "input text".
  a shell input text Seed
  a shell input keyevent KEYCODE_SPACE
  a shell input text page
  a shell input keyevent KEYCODE_SPACE
  a shell input text 25
  wait_text "Seed page 250" || fail "the search did not list Seed page 250"
  # Back hides the keyboard so the results show; the search stays open.
  a shell input keyevent KEYCODE_BACK
  sleep 2
  has_id history-search || fail "back closed the search instead of the keyboard"
  capture history-search-phone
  grep -qF 'text="Seed page 250"' "$OUT/history-search-phone.xml" || fail "the search did not list Seed page 250"
  if grep -qF 'text="Seed page 024"' "$OUT/history-search-phone.xml"; then fail "the search listed a row that does not match"; fi
  tap_id history-search-toggle
  sleep 2
  tap_id history-clear-all || fail "no delete-all button"
  # A dialog is a window of its own whose test tags do not show as ids; find it by its text.
  wait_text "Are you sure?" || fail "the delete-all dialog did not open"
  capture history-clear-confirm-phone
  tap_text "Cancel" || fail "no Cancel button in the dialog"
  sleep 2
  [ "$(stored_rows history-after-cancel)" = 500 ] || fail "cancelling the dialog changed the stored rows"
  crash_check captures-history

  fresh_install
  a logcat -c
  open_tabs "https://example.com/?t=1" "https://example.org/?t=2" "https://example.net/?t=3" "https://www.iana.org/?t=4"
  a shell dumpsys activity recents | tr -d '\r' > "$OUT/tabs-recents.txt"
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_id tabs-list || fail "Tabs did not show"
  sleep 3
  capture tabs-default-phone
  crash_check captures-tabs

  fresh_install
  a logcat -c
  a shell am start -W -n "$PKG/$HISTORY" >/dev/null
  wait_text "No history yet" || fail "the empty History did not show"
  capture history-empty-phone
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_text "No tabs open" || fail "the empty Tabs did not show"
  capture tabs-empty-phone
  crash_check captures-empty
  log "captures (phone) done"
}

clear_all() {
  local before after
  seed_history history-seed
  quiet_chrome
  before="$(stored_rows store-before)"
  log "rows before: $before"
  [ "$before" -gt 0 ] || fail "no rows to delete"
  a logcat -c
  start_history
  maestro --device "$SERIAL" test -e OUT="$OUT" -e COUNT="$before" "$HT_HERE/clear-all.yaml" \
    > "$OUT/clear-all.maestro.txt" 2>&1 || { free_ui_automation; fail "clear-all.yaml did not pass (see clear-all.maestro.txt)"; }
  free_ui_automation
  crash_check clear-all
  after="$(stored_rows store-after)"
  log "rows after: $after"
  [ "$after" = 0 ] || fail "the store still holds $after rows"
  log "clear-all passed"
}

# Task id and base URL of each recent task of the app, one "id url" per line.
app_tasks() {  # $1 = recents dump
  awk -v pkg="$PKG" '
    /\* Recent #/ { id = ""; if (index($0, ":" pkg "}") && match($0, /#[0-9]+ /)) id = substr($0, RSTART + 1, RLENGTH - 2); next }
    id != "" && /intent=\{/ && match($0, /dat=[^ ]+/) { print id, substr($0, RSTART + 4, RLENGTH - 4); id = "" }' "$1"
}

tabs_reorder() {
  local first="https://example.com/?t=a" second="https://example.org/?t=b"
  local before_count after_count first_task resumed
  fresh_install
  open_tabs "$first" "$second"
  a shell dumpsys activity recents | tr -d '\r' > "$OUT/recents-before.txt"
  app_tasks "$OUT/recents-before.txt" > "$OUT/tasks-before.txt"
  before_count="$(wc -l < "$OUT/tasks-before.txt" | tr -d ' ')"
  first_task="$(awk -v u="$first" '$2 == u { print $1; exit }' "$OUT/tasks-before.txt")"
  log "app tasks before: $before_count; task of $first: ${first_task:-none}"
  [ -n "$first_task" ] || fail "no task holds $first"
  a logcat -c
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_id tabs-list || fail "Tabs did not show"
  maestro --device "$SERIAL" test -e OUT="$OUT" -e 'URL_RE=example\.com/\?t=a' -e 'OTHER_RE=example\.org/\?t=b' \
    "$HT_HERE/tabs-reorder.yaml" > "$OUT/tabs-reorder.maestro.txt" 2>&1 || { free_ui_automation; fail "tabs-reorder.yaml did not pass (see tabs-reorder.maestro.txt)"; }
  free_ui_automation
  sleep 3
  shot tabs-reorder-after
  a shell dumpsys activity recents | tr -d '\r' > "$OUT/recents-after.txt"
  a shell dumpsys activity activities | tr -d '\r' > "$OUT/activities-after.txt"
  # The Tabs screen is a single-instance task with no link, so it is not listed.
  app_tasks "$OUT/recents-after.txt" > "$OUT/tasks-after.txt"
  after_count="$(wc -l < "$OUT/tasks-after.txt" | tr -d ' ')"
  resumed="$(grep -m1 'topResumedActivity' "$OUT/activities-after.txt" | sed -E 's/.* t([0-9]+)\}.*/\1/')"
  log "app tasks after: $after_count; resumed task: $resumed"
  [ "$after_count" = "$before_count" ] || fail "the tap changed the number of tabs ($before_count to $after_count)"
  [ "$resumed" = "$first_task" ] || fail "the resumed task is $resumed, not the task of $first ($first_task)"
  crash_check tabs-reorder
  log "tabs-reorder passed"
}

back_swipe() {  # $1 = capture name: a swipe from the left edge, held mid-way
  local w h y x
  read -r w h < <(screen_size)
  y=$((h / 2))
  a shell input motionevent DOWN 2 "$y"
  for x in $((w / 20)) $((w / 10)) $((w / 5)) $((w * 3 / 10)) $((w * 2 / 5)); do
    a shell input motionevent MOVE "$x" "$y"
  done
  shot "$1"
  a shell input motionevent UP $((w * 2 / 5)) "$y"
  sleep 2
}

back_gesture() {
  local _
  fresh_install
  a logcat -c
  a shell am start -W -n "$PKG/$MAIN" >/dev/null
  for _ in $(seq 1 30); do if has_id intro || has_id home-search-field; then break; fi; sleep 1; done
  if tap_id intro-skip; then sleep 2; fi
  if tap_text "OK"; then sleep 2; fi
  wait_id home-search-field || fail "home did not show"
  tap_id home-menu || fail "no menu button"
  sleep 2
  tap_text "History" || fail "no History in the drawer"
  wait_id history-list || fail "History did not show from the drawer"
  sleep 1
  back_swipe back-history-mid
  shot back-history-after
  wait_id home-search-field || fail "back from History did not show home"
  crash_check back-gesture
  log "back-gesture passed"
}

legacy() {
  seed_history history-seed
  quiet_chrome
  a logcat -c
  a shell am start -W -n "$PKG/$HISTORY" >/dev/null
  wait_text "Incognito browsing" || true
  sleep 2
  capture history-legacy-top
  scroll_down
  capture history-legacy-scroll-01
  scroll_down
  capture history-legacy-scroll-02
  crash_check history-legacy

  # Tabs: four links in Custom Tabs on a clean install.
  fresh_install
  a logcat -c
  open_tabs "https://example.com/?t=1" "https://example.com/?t=2" "https://example.com/?t=3" "https://example.com/?t=4"
  a shell dumpsys activity recents | tr -d '\r' > "$OUT/tabs-legacy-recents.txt"
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_text "Tabs" || true
  sleep 3
  capture tabs-legacy
  crash_check tabs-legacy
  log "legacy captures done"
}

# The Android CLI's capture of the screen. The Android CLI has no recorder and no settings
# command, so the screen recording and the animation and font settings below stay on adb.
cli_shot() {  # $1 = name
  android screen capture --device="$SERIAL" --output="$OUT/$1.png" >/dev/null 2>&1 || shot "$1"
}

# The settings these modes change, put back on exit, failures included.
restore_settings() {
  a shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
  a shell settings put global window_animation_scale 1 >/dev/null 2>&1 || true
  a shell settings put global transition_animation_scale 1 >/dev/null 2>&1 || true
  a shell settings put global animator_duration_scale 1 >/dev/null 2>&1 || true
}

animation_scale() {  # $1 = the scale of every system animation
  a shell settings put global window_animation_scale "$1"
  a shell settings put global transition_animation_scale "$1"
  a shell settings put global animator_duration_scale "$1"
}

insert_row() {  # $1 = url, $2 = title or NULL (quoted already), $3 = created_at
  echo "INSERT INTO history (url, title, amp_url, bookmarked, created_at, visit_count) VALUES ('$1', $2, NULL, 0, $3, 1);"
}

# The worst addresses, newest first: three today, two yesterday. A NULL title is an untitled page.
worst_sql() {  # $1 = now in ms
  local h=3600000
  insert_row "https://www.docs.engineering-platform.example-company.co.uk/teams/mobile/android/release-notes/2026/q3/build-4821" "'Release notes'" $(($1 - h))
  insert_row "https://github.com/" NULL $(($1 - 2 * h))
  insert_row "http://192.168.1.1:8080/settings/network/wireless?band=5ghz" "'Router settings'" $(($1 - 3 * h))
  insert_row "file:///storage/emulated/0/Download/report-2026-q3-final-v2.pdf" NULL $(($1 - 26 * h))
  insert_row "content://downloads/document/12" "'Downloaded document'" $(($1 - 27 * h))
}

# Three rows today and one yesterday: the rows the motion modes delete and put back.
two_days_sql() {  # $1 = now in ms
  local h=3600000
  insert_row "https://example.com/motion-1" "'Motion row 1'" $(($1 - h))
  insert_row "https://example.com/motion-2" "'Motion row 2'" $(($1 - 2 * h))
  insert_row "https://example.com/motion-3" "'Motion row 3'" $(($1 - 3 * h))
  insert_row "https://example.com/motion-4" "'Motion row 4'" $(($1 - 26 * h))
}

worst() {
  trap restore_settings EXIT
  seed_rows worst_sql 5
  a logcat -c
  start_history
  cli_shot worst-history
  ui_dump > "$OUT/worst-history.xml"
  if grep -qE 'text="(https?://|www\.)' "$OUT/worst-history.xml"; then fail "a History line shows https:// or www."; fi
  grep -qF 'text="github.com"' "$OUT/worst-history.xml" || fail "the untitled github.com row has no github.com title"
  grep -qF 'text="file:///storage/emulated/0/Download/report-2026-q3-final-v2.pdf"' "$OUT/worst-history.xml" || fail "the file: row lost its scheme"
  crash_check worst-history
  open_tabs "https://www.docs.engineering-platform.example-company.co.uk/teams/mobile/android/release-notes/2026/q3/build-4821" \
    "https://github.com/" "http://192.168.1.1:8080/settings/network/wireless?band=5ghz"
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_id tabs-list || fail "Tabs did not show"
  sleep 3
  cli_shot worst-tabs
  ui_dump > "$OUT/worst-tabs.xml"
  if grep -qE 'text="(https?://|www\.)' "$OUT/worst-tabs.xml"; then fail "a Tabs line shows https:// or www."; fi
  a shell settings put system font_scale 2.0
  a shell am force-stop "$PKG"
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_id tabs-list || fail "Tabs did not show at 200% font"
  sleep 3
  cli_shot worst-tabs-font200
  ui_dump > "$OUT/worst-tabs-font200.xml"
  crash_check worst-tabs
  log "worst captures done: worst-history.png, worst-tabs.png, worst-tabs-font200.png"
}

# Frames from the Android CLI about every 150 ms into $OUT/$2/ until the file $1 exists.
frames_until() {  # $1 = stop file, $2 = frame folder name
  local i=0
  mkdir -p "$OUT/$2"
  while [ ! -f "$1" ]; do
    android screen capture --device="$SERIAL" --output="$OUT/$2/$(printf '%04d' "$i").png" >/dev/null 2>&1 || true
    i=$((i + 1))
    sleep 0.15
  done
  log "$2: $i frames"
}

motion() {  # $1 = the animation scale (10 for motion, 0 for motion-off), $2 = the evidence name
  local scale="$1" name="$2" stop
  trap restore_settings EXIT
  seed_rows two_days_sql 4
  animation_scale "$scale"
  a logcat -c
  start_history
  stop="$OUT/$name.stop"
  rm -f "$stop"
  frames_until "$stop" "$name-frames" &
  a shell screenrecord --time-limit 120 "/sdcard/$name.mp4" &
  maestro --device "$SERIAL" test -e OUT="$OUT" -e HISTORY=1 -e ROW2="Motion row 2" "$HT_HERE/row-motion.yaml" \
    > "$OUT/$name.history.maestro.txt" 2>&1 || { touch "$stop"; free_ui_automation; fail "row-motion.yaml (History) did not pass (see $name.history.maestro.txt)"; }
  free_ui_automation
  open_tabs "https://example.com/?t=1" "https://example.org/?t=2" "https://example.net/?t=3"
  a shell am start -W -n "$PKG/$TABS" >/dev/null
  wait_id tabs-list || { touch "$stop"; fail "Tabs did not show"; }
  maestro --device "$SERIAL" test -e OUT="$OUT" -e TABS=1 "$HT_HERE/row-motion.yaml" \
    > "$OUT/$name.tabs.maestro.txt" 2>&1 || { touch "$stop"; free_ui_automation; fail "row-motion.yaml (Tabs) did not pass (see $name.tabs.maestro.txt)"; }
  free_ui_automation
  touch "$stop"
  a shell pkill -INT screenrecord >/dev/null 2>&1 || true
  wait
  sleep 2
  a pull "/sdcard/$name.mp4" "$OUT/$name.mp4" >/dev/null 2>&1 || log "no screen recording to pull"
  rm -f "$stop"
  crash_check "$name"
  log "$name done: $name-frames/, $name.mp4"
}

motion_off() {
  local xy x y
  motion 0 motion-off
  # One more delete at scale 0, with two frames 100 ms apart right after it.
  start_history
  xy="$(id_centre history-row)"
  [ -n "$xy" ] || fail "no History row to delete"
  read -r x y <<< "$xy"
  a shell input swipe "$x" "$y" 10 "$y" 150
  cli_shot motion-off-after-delete-1
  sleep 0.1
  cli_shot motion-off-after-delete-2
  log "motion-off: motion-off-after-delete-1.png and -2.png must both show the rows in their place"
}

free_ui_automation
case "$MODE" in
  legacy) legacy ;;
  captures) captures ;;
  clear-all) clear_all ;;
  tabs-reorder) tabs_reorder ;;
  back-gesture) back_gesture ;;
  worst) worst ;;
  motion) motion 10 motion ;;
  motion-off) motion_off ;;
  *) fail "unknown mode $MODE; $USAGE" ;;
esac
