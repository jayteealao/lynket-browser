#!/usr/bin/env bash
# The page that did not load, on a device with the debug build: the cause and Retry in the page
# area under the top bar, in a native bubble and in Web View mode, and a page that keeps showing
# when only a frame fails or the site sends its own 404 page. One mode per run:
#
#   offline-bubble   run after webheads-check.sh <serial> <dir> bubble; airplane mode on, a link
#                    in a native bubble shows "You are offline", Retry, Close and the count, and
#                    no bottom bar
#   offline-webview  Web View mode, airplane mode on: the same page with no count
#   certificate      Web View mode, network on: a site with an expired certificate shows "This
#                    connection is not secure" and Retry, and nothing that proceeds past it.
#                    CERT_URL picks the site (https://expired.badssl.com/ by default); CERT_LOCAL=1
#                    serves the fixtures over HTTPS with a self-signed certificate instead
#   subframe         Web View mode, network on: a page whose frame never loads and the fixture
#                    server's own 404 page both show their page and no error page; HTTPBIN=1 also
#                    opens https://httpbin.org/status/404
#   worst            Web View mode, airplane mode on, a short window (wm size 1080x1000) and a very
#                    long address: the cause and Retry are on screen and the picture is gone
#
# Each mode writes <mode>.png, <mode>.xml (the UI dump), <mode>-crash.txt (the crash buffer; a
# crash of the app fails the mode) and a PASS or FAIL line in error-page.txt. On exit it restores
# airplane mode off, the screen size and the Web View mode setting it found.
#
# Usage: maestro/browsing/error-page-check.sh <serial> <evidence-dir> <offline-bubble|offline-webview|certificate|subframe|worst>
#        (run from android-app/)
set -euo pipefail

USAGE="usage: error-page-check.sh <serial> <evidence-dir> <offline-bubble|offline-webview|certificate|subframe|worst>"
SERIAL="${1:?$USAGE}"
OUT="${2:?$USAGE}"
MODE="${3:?$USAGE}"
case "$MODE" in offline-bubble|offline-webview|certificate|subframe|worst) ;; *) echo "$USAGE" >&2; exit 2 ;; esac
source "$(dirname "$0")/../webheads/lib.sh"
EP_HERE="$(hostpath "$(cd "$(dirname "$0")" && pwd)")"
FIXTURES="$EP_HERE/fixtures"
mkdir -p "$OUT"
OUT="$(hostpath "$(cd "$OUT" && pwd)")"
LOG_NAME="error-page-$MODE"
CHECK_NAME="error page $MODE"
: > "$OUT/$LOG_NAME.txt"
CERT_URL="${CERT_URL:-https://expired.badssl.com/}"

# ---------------------------------------------------------------------------------------------
# Fixture server: plain HTTP, or HTTPS with a self-signed certificate (the same shape as
# maestro/browsing/link-open-timing.sh)

PORT=""
SERVER_PID=""
WEBVIEW_BEFORE=""
AIRPLANE_ON=""
SIZE_CHANGED=""

start_server() {
  local scheme=http
  PORT="$(python -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')"
  if [ "${1:-}" = https ]; then
    scheme=https
    openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=localhost \
      -keyout "$OUT/local-key.pem" -out "$OUT/local-cert.pem" >/dev/null 2>&1 || fail "openssl could not make a certificate"
    python - "$PORT" "$FIXTURES" "$OUT/local-cert.pem" "$OUT/local-key.pem" > "$OUT/server-$MODE.log" 2>&1 <<'PY' &
import functools, http.server, ssl, sys
port, root, cert, key = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root)
server = http.server.HTTPServer(("127.0.0.1", port), handler)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(cert, key)
server.socket = context.wrap_socket(server.socket, server_side=True)
server.serve_forever()
PY
    SERVER_PID=$!
  else
    python -m http.server "$PORT" --bind 127.0.0.1 --directory "$FIXTURES" > "$OUT/server-$MODE.log" 2>&1 &
    SERVER_PID=$!
  fi
  a reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
  local _
  for _ in $(seq 1 20); do
    python -c "import ssl, urllib.request; urllib.request.urlopen('$scheme://127.0.0.1:$PORT/broken-frame.html', context=ssl._create_unverified_context())" 2>/dev/null && break
    sleep 0.5
  done
  log "fixture server ($scheme) on port $PORT (adb reverse tcp:$PORT)"
}

airplane() {
  a shell cmd connectivity airplane-mode "$1"
  if [ "$1" = enable ]; then AIRPLANE_ON=1; else AIRPLANE_ON=""; fi
  sleep 4
}

cleanup() {
  [ -n "$AIRPLANE_ON" ] && a shell cmd connectivity airplane-mode disable >/dev/null 2>&1 || true
  [ -n "$SIZE_CHANGED" ] && a shell wm size reset >/dev/null 2>&1 || true
  if [ "$WEBVIEW_BEFORE" = false ]; then webview_mode off || true; fi
  [ -n "$PORT" ] && a reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# ---------------------------------------------------------------------------------------------
# UI helpers (copied from maestro/browsing/link-open-timing.sh)

ui_dump() {
  local i
  for i in 1 2 3 4 5; do
    a shell rm -f /sdcard/error-page-ui.xml
    timeout 30 adb -s "$SERIAL" shell uiautomator dump /sdcard/error-page-ui.xml >/dev/null 2>&1 || true
    a shell ls /sdcard/error-page-ui.xml >/dev/null 2>&1 && { a exec-out cat /sdcard/error-page-ui.xml | tr -d '\r'; return 0; }
    sleep 1
  done
}
centre_of() {
  { ui_dump | tr '>' '\n' | grep -F -- "$1" || true; } | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}
tap_on() {
  local xy _
  for _ in $(seq 1 15); do
    xy="$(centre_of "$1")"
    if [ -n "$xy" ]; then
      # shellcheck disable=SC2086
      a shell input tap $xy
      return 0
    fi
    dismiss_anr
    sleep 2
  done
  log "nothing matching $1 on screen"
  return 1
}
wait_for() {
  local _
  for _ in $(seq 1 20); do
    ui_dump | grep -qF -- "$1" && return 0
    dismiss_anr
    sleep 2
  done
  return 1
}
scroll_to() {
  local _ w h
  read -r w h < <(screen_size)
  for _ in $(seq 1 8); do
    ui_dump | grep -qF -- "$1" && return 0
    a shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 3)) 300
    sleep 1
  done
  ui_dump | grep -qF -- "$1"
}
stored() {
  a logcat -c
  a shell am broadcast -n "$PKG/arun.com.chromer.debug.StoreDumpReceiver" >/dev/null
  local _
  for _ in $(seq 1 40); do a logcat -d -s StoreDump:I | grep -q done && break; sleep 0.5; done
  a exec-out run-as "$PKG" cat files/debug-dump/settings.tsv | tr -d '\r' | awk -F'\t' -v k="$1" '$1 == k { print $3 }'
}
top_activity() {
  a shell dumpsys activity activities | tr -d '\r' | awk '!done && /topResumedActivity/ { for (i = 1; i <= NF; i++) if ($i ~ /\//) { print $i; done = 1; break } }'
}
destination() {
  a shell am force-stop com.android.chrome
  dismiss_anr
  a shell am start -W -n "$PKG/$MAIN" -f 0x24000000 --es "$DESTINATION" "$1" >/dev/null
  sleep 3
}

# Web View mode on (Use Web View on the provider screen) or off (Chrome chosen again).
webview_mode() {
  destination ProviderSelection
  if [ "$1" = on ]; then
    [ "$(stored use_webview_pref)" = true ] && { a shell input keyevent KEYCODE_HOME; return 0; }
    local attempt
    for attempt in 1 2 3; do
      [ "$attempt" = 1 ] || destination ProviderSelection
      scroll_to 'resource-id="provider-webview"' || fail "no Use Web View row"
      tap_on 'resource-id="provider-webview"'
      wait_for 'text="Are you sure?"' || true
      sleep 1
      tap_on 'text="OK"' || tap_on 'text="Yes"' || true
      sleep 2
      [ "$(stored use_webview_pref)" = true ] && break
    done
  else
    [ "$(stored use_webview_pref)" = true ] || { a shell input keyevent KEYCODE_HOME; return 0; }
    tap_on 'resource-id="provider-com.android.chrome"'
    wait_for 'text="Use"' || true
    tap_on 'resource-id="provider-confirm"' || tap_on 'text="Use"' || true
  fi
  sleep 1
  a shell input keyevent KEYCODE_HOME
  local now; now="$(stored use_webview_pref)"
  log "web view mode $1 -> use_webview_pref $now"
  if [ "$1" = on ]; then [ "$now" = true ] || fail "Web View mode did not turn on"
  else [ "$now" = true ] && fail "Web View mode did not turn off"; fi
  return 0
}

# Web View mode on for this run; the setting found at the start comes back on exit.
webview_on() {
  WEBVIEW_BEFORE="$(stored use_webview_pref)"
  [ -n "$WEBVIEW_BEFORE" ] || WEBVIEW_BEFORE=false
  webview_mode on
}

# The shell's bubble stack position as a tap point "X Y", empty while no bubble shows.
bubble_point() {
  a shell dumpsys activity service com.android.systemui/.SystemUIService WMShell | tr -d '\r' |
    awk -v pkg="$PKG" '/currentStackPos/ { pos = $0 } index($0, pkg) { seen = 1 } END { if (seen) print pos }' |
    sed -nE 's/.*currentStackPos: *PointF\((-?[0-9.]+), *(-?[0-9.]+)\).*/\1 \2/p' | head -1 |
    awk 'NF == 2 { x = $1 < 0 ? 0 : $1; print int(x + 70), int($2 + 70) }' || true
}

# Taps the bubble as soon as it shows (polling, at most 20 s), until its page is in front.
expand_bubble() {
  local xy="" _
  for _ in $(seq 1 80); do
    xy="$(bubble_point)"
    [ -n "$xy" ] && break
    sleep 0.25
  done
  [ -n "$xy" ] || return 1
  for _ in 1 2 3; do
    # shellcheck disable=SC2086
    a shell input tap $xy
    for _ in $(seq 1 16); do
      top_activity | grep -q "EmbeddableWebViewActivity" && return 0
      sleep 0.25
    done
  done
  return 1
}

# ---------------------------------------------------------------------------------------------
# Checks

# Saves the UI dump and the screenshot of the screen as $1.
capture() {
  ui_dump > "$OUT/$1.xml"
  shot "$1"
}

# Fails when the current screen shows $1 (fixed text of a UI dump).
absent() {
  ui_dump | grep -qF -- "$1" && fail "$2" || true
}

no_way_past() {
  local dump
  dump="$(ui_dump)"
  echo "$dump" | grep -qiE 'text="(Proceed|Continue|Open anyway)' && fail "an action continues past the certificate error" || true
}

finish_mode() {
  a logcat -d -b crash | tr -d '\r' > "$OUT/$MODE-crash.txt"
  grep -qF "$PKG" "$OUT/$MODE-crash.txt" && fail "the app crashed (see $MODE-crash.txt)" || true
  echo "$MODE PASS" >> "$OUT/error-page.txt"
  log "$MODE PASS"
}
on_fail() { echo "$MODE FAIL" >> "$OUT/error-page.txt"; }

# The cause, Retry and Close of an error page in the front window, with no bottom bar.
assert_error_page() {
  wait_for "text=\"$1\"" || fail "no \"$1\" on screen"
  wait_for 'text="Retry"' || fail "no Retry on screen"
  wait_for 'resource-id="webview-close"' || fail "no Close in the top bar"
  absent 'resource-id="browsing-bottom-bar"' "the bottom bar shows over the error"
}

# ---------------------------------------------------------------------------------------------

a shell input keyevent KEYCODE_WAKEUP
a shell wm dismiss-keyguard >/dev/null 2>&1 || true
a logcat -b crash -c
trap 'status=$?; [ $status -ne 0 ] && on_fail; cleanup; exit $status' EXIT

case "$MODE" in

offline-bubble)
  airplane enable
  a shell input keyevent KEYCODE_HOME
  open_link "https://example.com/?offline=$(date +%s)"  # a fresh address: a cached page would skip the lookup
  expand_bubble || fail "no bubble to expand (run webheads-check.sh <serial> <dir> bubble first)"
  assert_error_page "You are offline"
  wait_for 'resource-id="webview-count"' || fail "no count in the bubble's top bar"
  capture offline-bubble
  ;;

offline-webview)
  webview_on
  airplane enable
  open_link "https://example.com/?offline=$(date +%s)"
  assert_error_page "You are offline"
  absent 'resource-id="webview-count"' "a count shows in Web View mode"
  capture offline-webview
  ;;

certificate)
  webview_on
  if [ "${CERT_LOCAL:-}" = 1 ]; then
    start_server https
    open_link "https://localhost:$PORT/broken-frame.html"
  else
    open_link "$CERT_URL"
  fi
  assert_error_page "This connection is not secure"
  no_way_past
  absent 'text="A page with a broken frame"' "the page of the untrusted site loaded"
  capture certificate
  ;;

subframe)
  webview_on
  start_server
  open_link "http://localhost:$PORT/broken-frame.html"
  wait_for 'text="A page with a broken frame"' || fail "the page with a broken frame did not show its title"
  sleep 3  # the frame's lookup fails after the page
  absent 'resource-id="webview-error"' "an error page replaced the page whose frame failed"
  capture subframe
  open_link "http://localhost:$PORT/missing-page.html"
  wait_for 'resource-id="webview-title"' || fail "the site's 404 page did not show"
  sleep 2
  absent 'resource-id="webview-error"' "an error page replaced the site's own 404 page"
  capture site-404
  if [ "${HTTPBIN:-}" = 1 ]; then
    open_link "https://httpbin.org/status/404"
    wait_for 'resource-id="webview-title"' || fail "httpbin's 404 page did not show"
    sleep 2
    absent 'resource-id="webview-error"' "an error page replaced httpbin's 404 page"
    capture httpbin-404
  fi
  ;;

worst)
  webview_on
  airplane enable
  a shell wm size 1080x1000
  SIZE_CHANGED=1
  sleep 2
  open_link "https://a-very-long-subdomain-name.of-a-site-with-many-parts.example.co.uk/some/deep/path/that/goes/on?offline=$(date +%s)"
  wait_for 'text="Retry"' || fail "no Retry on screen"
  ui_dump | grep -qE 'text="(This site cannot be reached|You are offline)"' || fail "no cause on screen"
  absent 'resource-id="error-state-blob"' "the picture shows in a short window"
  capture worst
  a shell wm size reset
  SIZE_CHANGED=""
  ;;

esac

finish_mode
