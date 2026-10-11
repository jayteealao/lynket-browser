#!/usr/bin/env bash
# Upgrade check for history: seeds a 2.1.3 user with 500 history rows, installs the new debug
# build over it, and proves every row is in the new database and shows in the History screen.
#
#   1. seed-upgrade.sh: clean install of the 2.1.3 debug build; the 2.1.3 app writes 5 history rows
#   2. with the app stopped, append 495 rows in the 2.1.3 table format (text CREATED) to the app's
#      own History file and record all 500 rows
#   3. `adb install -r` of the new debug build (same applicationId and signing key)
#   4. open the History screen (debug build only, DUMP-guarded), which brings the old file in
#   5. dump the history with the debug-only dump receiver and pull it
#   6. compare: 500 rows, each seeded URL once with its time and visit count, no other URL; the
#      import report says ok; the old file sits in files/history-migration/, not in databases/
#   7. Maestro: the newest rows, then every 50th appended row down to the oldest, show on screen
#   8. the crash buffer holds no entry for the app
#
# Usage: maestro/upgrade/history-check.sh <serial> <evidence-dir>   (run from android-app/)
# Needs: adb, Maestro, host sqlite3, the 2.1.3 APK (maestro/build-old-apk.sh makes it) and the
# new debug APK (./gradlew :lynket:assembleDebug).
set -euo pipefail
export MSYS_NO_PATHCONV=1

SERIAL="${1:?usage: history-check.sh <serial> <evidence-dir>}"
OUT="${2:?usage: history-check.sh <serial> <evidence-dir>}"
hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
here="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
maestro_root="$(hostpath "$(cd "$here/.." && pwd)")"
app_root="$(hostpath "$(cd "$maestro_root/.." && pwd)")"
NEW_APK="${NEW_APK:-$app_root/lynket/build/outputs/apk/debug/lynket-debug.apk}"
OLD_APK="${OLD_APK:-$app_root/build/old-apk/lynket-2.1.3-debug.apk}"
PKG=arun.com.chromer.debug
EXTRA=495
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
tmp="$(hostpath "$(mktemp -d)")"
trap 'rm -rf "$tmp"' EXIT

a() { adb -s "$SERIAL" "$@"; }
fail() { echo "history check failed: $*" | tee -a "$OUT/compare.txt" >&2; exit 1; }
pull() { a exec-out run-as "$PKG" cat "$1" | tr -d '\r'; }

[ -f "$OLD_APK" ] || fail "2.1.3 APK missing at $OLD_APK; run maestro/build-old-apk.sh first"
[ -f "$NEW_APK" ] || fail "new debug APK missing at $NEW_APK; run ./gradlew :lynket:assembleDebug first"
: > "$OUT/compare.txt"

# 1. Seed a 2.1.3 user (5 history rows written by the 2.1.3 app).
OLD_APK="$OLD_APK" bash "$maestro_root/seed-upgrade.sh" "$SERIAL" "$OUT/seed"

# 2. Grow the history to 500 rows in the 2.1.3 format, in the app's own file, app stopped.
a shell am force-stop "$PKG"
journal="$(a exec-out run-as "$PKG" sh -c 'stat -c %s databases/History-journal 2>/dev/null || echo 0' | tr -d '\r')"
[ "$journal" -eq 0 ] || fail "History-journal holds $journal bytes; the 2.1.3 file is not settled"
a exec-out run-as "$PKG" cat databases/History > "$tmp/History"
oldest="$(sqlite3 "$tmp/History" "select min(cast(CREATED as integer)) from History;" | tr -d '\r')"
{
  echo "BEGIN;"
  for n in $(seq 1 "$EXTRA"); do
    nnn="$(printf '%03d' "$n")"
    created=$((oldest - 3600000 - n * 60000))
    echo "INSERT INTO History (URL, TITLE, BOOKMARKED, CREATED, VISITED) VALUES ('https://seed.example/page-$nnn', 'Seed page $nnn', 0, '$created', $((n % 7 + 1)));"
  done
  echo "COMMIT;"
} | sqlite3 "$tmp/History"
a exec-in run-as "$PKG" sh -c "cat > databases/History" < "$tmp/History"
a exec-out run-as "$PKG" cat databases/History > "$OUT/seeded-History.db"
sqlite3 -separator $'\t' "$OUT/seeded-History.db" "select URL, CREATED, VISITED from History order by cast(CREATED as integer) desc;" \
  | tr -d '\r' > "$OUT/seeded-history.tsv"
seeded="$(wc -l < "$OUT/seeded-history.tsv" | tr -d ' ')"
echo "seeded history rows: $seeded (5 written by the 2.1.3 app, $EXTRA appended)" | tee -a "$OUT/compare.txt"
[ "$seeded" -eq $((5 + EXTRA)) ] || fail "the seeded file holds $seeded rows"
[ "$(sqlite3 "$OUT/seeded-History.db" "select count(*) from History where typeof(CREATED) <> 'text';" | tr -d '\r')" -eq 0 ] \
  || fail "a seeded CREATED value is not text (2.1.3 stores text)"

# 3. Install the new build over it.
a install -r "$NEW_APK" 2>&1 | tr -d '\r' | tee "$OUT/install-r.txt"
grep -q '^Success' "$OUT/install-r.txt" || fail "adb install -r was refused (applicationId or signing key differ)"

# 4. Open the History screen; its first page opens the database and brings the old file in.
a logcat -c
a shell am start -W -n "$PKG/arun.com.chromer.history.HistoryActivity" | tr -d '\r' > "$OUT/history-start.txt"
sleep 5
a exec-out screencap -p > "$OUT/history-top.png"

# 5. Dump the history.
a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
for _ in $(seq 1 40); do
  a logcat -d -s StoreDump:I | grep -q "done" && break
  sleep 0.5
done
a logcat -d -s StoreDump:I | grep -q "done" || fail "the store dump did not finish"
pull files/debug-dump/history.tsv > "$OUT/history.tsv"
pull files/debug-dump/history-report.tsv > "$OUT/history-report.tsv" || true

# 6. Compare.
{
  echo "# url seeded_created seeded_visited stored_created stored_visited result"
  awk -F'\t' '
    NR == FNR { if (FNR > 1) { created[$1] = $2; visits[$1] = $3; seen[$1]++ } next }
    {
      url = $1; want_c = $2; want_v = ($3 < 1 ? 1 : $3)
      if (seen[url] == 1 && created[url] == want_c && visits[url] == want_v) { r = "equal"; ok++ } else r = "DIFFERENT"
      printf "%s %s %s %s %s %s\n", url, $2, $3, created[url], visits[url], r
      delete seen[url]; total++
    }
    END {
      extra = 0; for (u in seen) { print "EXTRA " u; extra++ }
      printf "history: %d of %d seeded rows equal; %d other rows\n", ok, total, extra
    }' "$OUT/history.tsv" "$OUT/seeded-history.tsv"
} > "$tmp/compare.txt"
cat "$tmp/compare.txt" >> "$OUT/compare.txt"
tail -1 "$tmp/compare.txt"
grep -q "^history: $seeded of $seeded seeded rows equal; 0 other rows$" "$tmp/compare.txt" \
  || fail "not every seeded history row survived (see compare.txt)"
stored="$(($(wc -l < "$OUT/history.tsv") - 1))"
[ "$stored" -eq "$seeded" ] || fail "the new database holds $stored rows, expected $seeded"
cat "$OUT/history-report.tsv" | tee -a "$OUT/compare.txt"
awk -F'\t' 'NR == 2 && $5 == "ok" { found = 1 } END { exit !found }' "$OUT/history-report.tsv" \
  || fail "the import report does not say ok"
{
  echo "## files/history-migration"; a exec-out run-as "$PKG" ls -l files/history-migration
  echo "## databases"; a exec-out run-as "$PKG" ls -l databases
} | tr -d '\r' > "$OUT/migration-files.txt"
grep -qE " History-[0-9]+$" "$OUT/migration-files.txt" || fail "the old history file is not kept in files/history-migration/"
if awk '/^## databases/ { f = 1; next } f' "$OUT/migration-files.txt" | grep -qE " History$"; then
  fail "the old history file is still in databases/"
fi
echo "old history file: kept in files/history-migration/, gone from databases/" | tee -a "$OUT/compare.txt"

# 7. The History screen shows the rows, newest first, down to the oldest.
a shell am start -W -n "$PKG/arun.com.chromer.history.HistoryActivity" >/dev/null
maestro --device "$SERIAL" test -e OUT="$OUT" "$here/history-values.yaml"

# 8. No crash.
pid="$(a shell pidof "$PKG" | tr -d '\r' || true)"
if [ -n "$pid" ]; then a logcat -d --pid="$pid" > "$OUT/history.logcat.txt"; else a logcat -d > "$OUT/history.logcat.txt"; fi
a logcat -d -b crash | tr -d '\r' > "$OUT/logcat-crash.txt"
if grep -q "$PKG" "$OUT/logcat-crash.txt"; then fail "the crash buffer holds an entry for $PKG"; fi
echo "history check passed" | tee -a "$OUT/compare.txt"
