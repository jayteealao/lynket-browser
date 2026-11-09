"""A local page server that shows whether a browser kept a cookie.

Usage: python maestro/browsing/cookie-server.py <port> <log>

  GET /set   a page titled "Ephemeral check" that sets the cookie lynket_probe=1 for an hour
  GET /read  a page titled "Cookie read"

Every request appends "<path>\t<Cookie header, or ->" to <log>, so a read after a tab closed shows
whether the browser kept the cookie of that tab. Binds 127.0.0.1 only; a device reaches it through
adb reverse. Port 0 picks a free port; the first line printed is "port <n>".
"""

import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PAGES = {
    "/set": ("Ephemeral check", "lynket_probe=1; Max-Age=3600; Path=/"),
    "/read": ("Cookie read", None),
}


def handler_for(log_path):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            path = self.path.split("?", 1)[0]
            with open(log_path, "a", encoding="utf-8", newline="\n") as log:
                log.write("%s\t%s\n" % (path, self.headers.get("Cookie") or "-"))
            page = PAGES.get(path)
            if page is None:
                self.send_error(404)
                return
            title, cookie = page
            body = ("<!doctype html><html><head><meta charset='utf-8'><title>%s</title></head>"
                    "<body><h1>%s</h1></body></html>" % (title, title)).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            if cookie:
                self.send_header("Set-Cookie", cookie)
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    return Handler


def main():
    if len(sys.argv) != 3:
        print("usage: cookie-server.py <port> <log>", file=sys.stderr)
        sys.exit(2)
    server = ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), handler_for(sys.argv[2]))
    print("port %d" % server.server_address[1], flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
