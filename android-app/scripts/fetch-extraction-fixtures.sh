#!/usr/bin/env bash
# Fetches the openly licensed web pages that the article extraction parity test reads.
#
# Usage: bash scripts/fetch-extraction-fixtures.sh <out-dir>
#
# Each page is saved as raw bytes, with the app's own User-Agent (WebsiteUtilities.USER_AGENT), so
# the HTML is the one a site serves the app. The committed copies are the test input; the test never
# fetches. Re-running this script changes the HTML, and the parity goldens must then be recorded
# again from the old extractor, so only run it to replace the fixture set on purpose.
# Licences and attribution: lynket/src/test/resources/extraction/fixtures/SOURCES.md.
set -euo pipefail

out="${1:?usage: fetch-extraction-fixtures.sh <out-dir>}"
mkdir -p "$out"

UA="Mozilla/5.0 (iPad; CPU OS 6_0 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10A5376e Safari/8536.25"

fetch() {
  local name="$1" url="$2"
  local code type
  read -r code type < <(curl -sS -L --max-time 60 -A "$UA" -o "$out/$name" -w '%{http_code} %{content_type}\n' "$url")
  if [ "$code" != "200" ]; then
    echo "FAIL $name $url -> HTTP $code" >&2
    exit 1
  fi
  echo "$name $url HTTP $code $type $(wc -c < "$out/$name") bytes"
}

fetch news.html "https://en.wikinews.org/wiki/Grateful_Dead_rhythm_guitarist_Bob_Weir_dies,_aged_78"
fetch blog.html "https://creativecommons.org/2026/09/21/a-principled-approach-as-the-sands-shift/"
fetch no-article.html "https://www.wikipedia.org/"
fetch images.html "https://en.wikipedia.org/wiki/Lighthouse"
fetch non-english.html "https://de.wikipedia.org/wiki/Leuchtturm"
