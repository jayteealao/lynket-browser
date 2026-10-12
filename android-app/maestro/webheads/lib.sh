#!/usr/bin/env bash
# Shared helpers for the web head device checks. Source it after setting SERIAL and OUT.
# Needs adb on PATH and the debug build installed.

export MSYS_NO_PATHCONV=1
PKG=arun.com.chromer.debug
INTERCEPT=arun.com.chromer.browsing.browserintercept.BrowserInterceptActivity
BROWSING_MODE=arun.com.chromer.settings.browsingmode.BrowsingModeActivity
WEB_HEAD_SERVICE=arun.com.chromer.bubbles.webheads.WebHeadService
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

hostpath() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
a() { adb -s "$SERIAL" "$@"; }
log() { echo "$*" | tee -a "$OUT/${LOG_NAME:-check}.txt"; }
fail() { echo "${CHECK_NAME:-check} failed: $*" | tee -a "$OUT/${LOG_NAME:-check}.txt" >&2; exit 1; }

# Screenshot of the default display (a fold has several, and screencap then needs one named).
shot() {
  local display
  display="$(a shell dumpsys SurfaceFlinger --display-id 2>/dev/null | tr -d '\r' | awk 'NR == 1 { print $2 }')"
  if [ -n "$display" ]; then a exec-out screencap -p -d "$display" > "$OUT/$1.png"; else a exec-out screencap -p > "$OUT/$1.png"; fi
}

# Screen size in px as "W H" (the override size when one is set).
screen_size() {
  a shell wm size | tr -d '\r' | awk -F': ' '/Override size/ { o = $2 } /Physical size/ { p = $2 } END { print (o != "" ? o : p) }' | tr 'x' ' '
}

# Overlay windows of the app: one line per window, "touchable left top right bottom", front first.
overlay_windows() {
  a shell dumpsys window windows | tr -d '\r' | awk -v pkg="$PKG" '
    /^  Window #/ { if (keep) print t, f; keep = 0; t = 1; f = ""; inwin = ($0 ~ ("u0 " pkg "}")) ; next }
    inwin && /ty=APPLICATION_OVERLAY/ { keep = 1 }
    inwin && /NOT_TOUCHABLE/ { t = 0 }
    inwin && f == "" && match($0, / frame=\[-?[0-9]+,-?[0-9]+\]\[-?[0-9]+,-?[0-9]+\]/) {
      s = substr($0, RSTART + 8, RLENGTH - 8); gsub(/[\[\]]/, " ", s); gsub(/,/, " ", s); f = s
    }
    END { if (keep) print t, f }'
}

overlay_count() { overlay_windows | grep -c . || true; }

# Visible centre of the front (master) head as "X Y".
head_centre() {
  local w h
  read -r w h < <(screen_size)
  overlay_windows | awk -v w="$w" -v h="$h" '$1 == 1 {
      l = $2 < 0 ? 0 : $2; r = $4 > w ? w : $4; t = $3 < 0 ? 0 : $3; b = $5 > h ? h : $5
      print int((l + r) / 2), int((t + b) / 2); exit }'
}

# Centre of the first node whose text or description is exactly $1, from a UI dump; empty if none.
node_centre() {
  a shell uiautomator dump /sdcard/webheads-ui.xml >/dev/null 2>&1 || true
  a exec-out cat /sdcard/webheads-ui.xml | tr -d '\r' | tr '>' '\n' |
    grep -F -e "text=\"$1\"" -e "content-desc=\"$1\"" | head -1 |
    sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/' |
    awk 'NF == 4 { print int(($1 + $3) / 2), int(($2 + $4) / 2) }'
}

tap_text() {
  local xy
  xy="$(node_centre "$1")"
  [ -n "$xy" ] || return 1
  # shellcheck disable=SC2086
  a shell input tap $xy
}

# A system "isn't responding" dialog of another app (a busy emulator) takes the next touch; "Wait"
# keeps that app running and clears the screen.
dismiss_anr() {
  tap_text "Wait" >/dev/null 2>&1 && sleep 1 || true
}

open_link() {
  a shell "am start -a android.intent.action.VIEW -d '$1' -n $PKG/$INTERCEPT" >/dev/null
}

app_logcat() {  # $1 = output name; the app's lines since the last logcat -c, plus the crash buffer
  local pid
  pid="$(a shell pidof "$PKG" | tr -d '\r' || true)"  # no process (the app handed off and exited)
  if [ -n "$pid" ]; then a logcat -d --pid="$pid" | tr -d '\r' > "$OUT/$1.logcat.txt"; else a logcat -d | tr -d '\r' | grep -F "$PKG" > "$OUT/$1.logcat.txt" || true; fi
  a logcat -d -b crash | tr -d '\r' > "$OUT/$1.crash.txt"
}

# Stops the web head service the way a person does: a tap on its notification closes all heads.
close_all_heads() {
  a shell cmd statusbar expand-notifications >/dev/null 2>&1 || true
  sleep 2
  tap_text "Tap to close all" || true
  sleep 3
  a shell cmd statusbar collapse >/dev/null 2>&1 || true
  sleep 1
}

# The browsing mode screen; picks a mode by its title. The debug build exports the screen.
choose_mode() {
  dismiss_anr
  a shell am start -W -n "$PKG/$BROWSING_MODE" >/dev/null
  sleep 2
  tap_text "$1" || fail "no \"$1\" row on the browsing mode screen"
  sleep 2
}

# Three-button navigation while swiping: the gesture Back zone at the screen edge takes swipes
# that start on a head resting there. Restores the navigation mode the device had.
NAV_BEFORE=""
nav_three_button() {
  NAV_BEFORE="$(a shell cmd overlay list | tr -d '\r' | awk '/\[x\] com.android.internal.systemui.navbar/ { print $2 }' | head -1)"
  a shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton >/dev/null
  sleep 2
}
nav_restore() {
  [ -n "$NAV_BEFORE" ] && a shell cmd overlay enable-exclusive --category "$NAV_BEFORE" >/dev/null || true
}
