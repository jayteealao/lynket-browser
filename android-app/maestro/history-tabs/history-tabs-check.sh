#!/usr/bin/env bash
# Device check of the History and Tabs screens on the debug build.
#
#   legacy   reference captures of the History and Tabs screens of the installed build: History
#            with 500 rows (history-check.sh seeds release 2.1.3 and updates over it), at the top
#            and after two scrolls; Tabs with four links open in Custom Tabs (clean install)
#
# Usage: maestro/history-tabs/history-tabs-check.sh <serial> <evidence-dir> <mode>   (from android-app/)
# SEED=direct skips history-check.sh and writes the 500 rows straight into the database.
# APK=<path> picks the debug APK (default: the last assembleDebug output). Every mode reinstalls
# the app, which clears its data on that device.
set -euo pipefail

USAGE="usage: history-tabs-check.sh <serial> <evidence-dir> <legacy>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
source "$(dirname "$0")/../webheads/lib.sh"
HT_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
APK="${APK:-$HT_HERE/../../lynket/build/outputs/apk/debug/lynket-debug.apk}"
HISTORY=arun.com.chromer.history.HistoryActivity
TABS=arun.com.chromer.tabs.ui.TabsActivity
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="$MODE"
CHECK_NAME="history-tabs $MODE check"
: > "$OUT/$MODE.txt"
[ -f "$APK" ] || fail "debug APK missing at $APK; run ./gradlew :lynket:assembleDebug first"

# The screen's UI tree. A dump fails while the screen is still moving, so the old file is removed
# first and must not stand in for the new screen.
ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/history-tabs-ui.xml
    a shell uiautomator dump /sdcard/history-tabs-ui.xml >/dev/null 2>&1 || true
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

# 500 rows written into the app's own history database (fresh install): one every 20 minutes back
# from now, so they span about a week; every 10th row has an AMP address. Same titles and
# addresses as history-check.sh appends ("Seed page NNN", https://seed.example/page-NNN).
seed_rows() {
  local tmp now n nnn
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
    for n in $(seq 1 500); do
      nnn="$(printf '%03d' "$n")"
      amp="NULL"; [ $((n % 10)) -eq 0 ] && amp="'https://seed.example/amp/page-$nnn'"
      echo "INSERT INTO history (url, title, amp_url, bookmarked, created_at, visit_count) VALUES ('https://seed.example/page-$nnn', 'Seed page $nnn', $amp, 0, $((now - n * 1200000)), $((n % 7 + 1)));"
    done
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
  [ "$rows" = 500 ] || fail "the history database holds $rows rows after seeding"
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

case "$MODE" in
  legacy) legacy ;;
  *) fail "unknown mode $MODE; $USAGE" ;;
esac
