"""Static dev server for the mobile SPA on http://localhost:9700 with caching disabled, so browsers always load
the latest JS/CSS (the plain http.server lets browsers cache ES modules heuristically)."""
import functools
import http.server
import os

PORT = int(os.environ.get("PORT", "9700"))


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


if __name__ == "__main__":
    handler = functools.partial(NoCacheHandler, directory=os.path.dirname(os.path.abspath(__file__)))
    with http.server.ThreadingHTTPServer(("127.0.0.1", PORT), handler) as server:
        print(f"Serving web-mobile on http://localhost:{PORT}")
        server.serve_forever()
