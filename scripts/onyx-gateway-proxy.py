#!/usr/bin/env python3
"""Single entry point for the Onyx app: one public host serves both Hermes servers.

- `/v1/...`  → gateway API server (127.0.0.1:8642)  : the chat, which runs the agent.
- everything else (`/api/...`, `/auth/...`, `/assets/...`, `/`) → dashboard (127.0.0.1:9119) :
  sessions, cron, kanban, files, memory, gateway.

Why: the app requires two distinct addresses (API + Dashboard) and it is very easy to
confuse them — an error that translates to "404 Endpoint not found" or "401". With this proxy,
**the same address works in both fields**.

Launch: see /home/john/start-proxy.sh. Listens on 127.0.0.1:8650 (to be exposed by a tunnel).
"""
from __future__ import annotations

import http.client
import http.server
import socketserver
import sys

API_HOST, API_PORT = "127.0.0.1", 8642
DASH_HOST, DASH_PORT = "127.0.0.1", 9119
LISTEN_HOST, LISTEN_PORT = "127.0.0.1", 8650

# These headers are not relayed from one hop to the next.
HOP_BY_HOP = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade",
}


def upstream_for(path: str) -> tuple[str, int]:
    """Route according to the path: the API for `/v1`, the dashboard for everything else."""
    return (API_HOST, API_PORT) if path.startswith("/v1/") else (DASH_HOST, DASH_PORT)


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "OnyxGatewayProxy/1.0"

    def log_message(self, fmt: str, *args) -> None:  # compact log
        sys.stderr.write("proxy: " + (fmt % args) + "\n")
        sys.stderr.flush()

    def _relay(self) -> None:
        host, port = upstream_for(self.path)
        length = self.headers.get("Content-Length")
        body = self.rfile.read(int(length)) if length else None

        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP}
        headers["Host"] = f"{host}:{port}"
        # With no TLS terminator in front of this connection, the client actually speaks HTTP.
        # Cloudflared provides X-Forwarded-Proto=https: keep it so that Secure
        # cookies stay limited to HTTPS, without fabricating one for local HTTP.
        headers.setdefault("X-Forwarded-Proto", "http")

        conn = http.client.HTTPConnection(host, port, timeout=600)
        try:
            conn.request(self.command, self.path, body=body, headers=headers)
            upstream = conn.getresponse()

            self.send_response(upstream.status)
            for key, value in upstream.getheaders():
                if key.lower() in HOP_BY_HOP or key.lower() == "content-length":
                    continue
                self.send_header(key, value)
            # Streaming responses (chat SSE): relay as chunked, without buffering.
            streaming = upstream.getheader("Transfer-Encoding", "").lower() == "chunked" or \
                upstream.getheader("Content-Type", "").startswith("text/event-stream")
            if streaming:
                self.send_header("Transfer-Encoding", "chunked")
            else:
                raw = upstream.read()
                self.send_header("Content-Length", str(len(raw)))
            self.end_headers()

            if streaming:
                # read(4096) waits to fill the whole buffer and can silence a small heartbeat
                # SSE until the client timeout. read1 relays each available chunk immediately.
                while True:
                    payload = upstream.read1(65536)
                    if not payload:
                        break
                    for i in range(0, len(payload), 512):
                        piece = payload[i:i + 512]
                        self.wfile.write(f"{len(piece):X}\r\n".encode() + piece + b"\r\n")
                        self.wfile.flush()
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
            else:
                self.wfile.write(raw)
                self.wfile.flush()
        except Exception as exc:  # upstream unreachable: readable error for the app
            self.log_message("échec du relais vers %s:%s -> %s", host, port, exc)
            try:
                self.send_response(502)
                self.send_header("Content-Type", "application/json")
                payload = b'{"error":"proxy: upstream unreachable"}'
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except Exception:
                pass
        finally:
            conn.close()

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = do_OPTIONS = _relay


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    with Server((LISTEN_HOST, LISTEN_PORT), Handler) as httpd:
        print(f"proxy Onyx en écoute sur http://{LISTEN_HOST}:{LISTEN_PORT} "
              f"(/v1 -> {API_HOST}:{API_PORT}, reste -> {DASH_HOST}:{DASH_PORT})", flush=True)
        httpd.serve_forever()
