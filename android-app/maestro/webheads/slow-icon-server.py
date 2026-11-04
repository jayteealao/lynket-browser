"""A site whose icon stays on the way until it is released, for the late icon check of overflow-probe.sh.

    python slow-icon-server.py <port>

GET /         a page that names the icon
GET /icon.png the first bytes of a solid PNG, then one byte each 2 s until released, then the rest.
              The trickle keeps a read timeout from turning the wait into a failed icon.
GET /release  releases every waiting icon, and every later one
"""
import struct
import sys
import threading
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SIZE = 192
COLOUR = bytes((0xE6, 0x51, 0x00))
TRICKLE_S = 2
FIRST_BYTES = 64
PAGE = b'<!doctype html><title>Slow icon</title><link rel="icon" href="/icon.png"><p>Slow icon</p>'

released = threading.Event()


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def solid_png():
    row = b"\0" + COLOUR * SIZE
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(row * SIZE, 0))
        + chunk(b"IEND", b"")
    )


PNG = solid_png()


class Handler(BaseHTTPRequestHandler):
    def respond(self, content_type, body):
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/":
            self.respond("text/html", PAGE)
        elif path == "/release":
            released.set()
            self.respond("text/plain", b"released\n")
        elif path == "/icon.png":
            self.send_icon()
        else:
            self.send_error(404)

    def send_icon(self):
        self.send_response(200)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(PNG)))
        self.end_headers()
        sent = FIRST_BYTES
        self.wfile.write(PNG[:sent])
        self.wfile.flush()
        while not released.wait(TRICKLE_S):
            self.wfile.write(PNG[sent : sent + 1])
            self.wfile.flush()
            sent += 1
        self.wfile.write(PNG[sent:])

    def log_message(self, format, *args):
        print(format % args, flush=True)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Handler).serve_forever()
