"""A site whose pages take a set time to arrive, for the page-state checks of webheads-check.sh.

    python slow-page-server.py <port>

GET /slow?s=<seconds>&id=<n>  the start of a page at once, then one space each 2 s, and after
                              <seconds> the end of its head with <title>Slow page <n></title>
                              and its body. The trickle keeps a read timeout from turning the
                              wait into a failed page; the app reads a page only up to the end
                              of its head, so a head stays loading until then.

Each request prints its start and end time, so a check can tell when the page finished. The
server answers requests side by side, so five held pages run together.
"""
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

TRICKLE_S = 2
START = b"<!doctype html><html><head><meta charset=\"utf-8\">"


def stamp():
    return time.strftime("%H:%M:%S", time.localtime()) + ".%03d" % (time.time() % 1 * 1000)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        url = urlsplit(self.path)
        if url.path != "/slow":
            self.send_error(404)
            return
        query = parse_qs(url.query)
        seconds = float(query.get("s", ["5"])[0])
        page_id = query.get("id", ["1"])[0]
        end = (
            f"<title>Slow page {page_id}</title></head>"
            f"<body><h1>Slow page {page_id}</h1><p>This page took {seconds:g} s.</p></body></html>"
        ).encode()
        print(f"{stamp()} start id={page_id} s={seconds:g}", flush=True)
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try:
            self.wfile.write(START)
            self.wfile.flush()
            finish = time.monotonic() + seconds
            while (left := finish - time.monotonic()) > 0:
                time.sleep(min(TRICKLE_S, left))
                if finish - time.monotonic() > 0:
                    self.wfile.write(b" ")
                    self.wfile.flush()
            self.wfile.write(end)
            self.wfile.flush()
            print(f"{stamp()} end id={page_id}", flush=True)
        except (BrokenPipeError, ConnectionResetError):
            print(f"{stamp()} dropped id={page_id}", flush=True)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
