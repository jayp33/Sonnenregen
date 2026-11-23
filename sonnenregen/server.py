"""Tiny HTTP JSON service around :func:`sonnenregen.analyze`.

Run with ``python -m sonnenregen.server`` and query
``GET /?city=berlin``.
"""

from __future__ import annotations

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from .core import DEFAULT_CITY, SourceError, analyze

_VALID_PATHS = {"/", "/weather", "/health"}


class Handler(BaseHTTPRequestHandler):
    server_version = "sonnenregen"
    sys_version = ""
    timeout = 15  # close idle connections instead of holding threads forever

    def version_string(self) -> str:
        return self.server_version

    def do_GET(self) -> None:  # noqa: N802 - stdlib naming
        parsed = urlparse(self.path)
        if parsed.path not in _VALID_PATHS:
            self._send(404, {"error": "Not found"})
            return
        if parsed.path == "/health":
            self._send(200, {"status": "ok"})
            return

        params = parse_qs(parsed.query)
        city = params.get("city", [DEFAULT_CITY])[0]
        try:
            answer = analyze(city)
        except ValueError as exc:
            self._send(400, {"error": str(exc)})
        except (SourceError, RuntimeError) as exc:
            self._send(502, {"error": str(exc)})
        except Exception as exc:
            # Never let an unexpected bug close the connection without a reply.
            self.log_error("Unhandled error while serving %s: %r", self.path, exc)
            self._send(500, {"error": "Internal server error"})
        else:
            self._send(200, answer.as_dict())

    def _send(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8") + b"\n"
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:  # noqa: A002 - stdlib signature
        super().log_message(format, *args)


def main() -> None:
    host = os.environ.get("HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "8080"))
    if host not in ("127.0.0.1", "::1", "localhost"):
        print(
            "Warning: this unauthenticated, rate-unlimited service is bound to "
            f"{host!r}; keep it on loopback unless access is restricted.",
            file=sys.stderr,
            flush=True,
        )
    with ThreadingHTTPServer((host, port), Handler) as server:
        print(f"sonnenregen listening on http://{host}:{port}", flush=True)
        server.serve_forever()


if __name__ == "__main__":  # pragma: no cover
    main()
