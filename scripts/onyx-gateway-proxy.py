#!/usr/bin/env python3
"""Point d'entrée unique pour l'app Onyx : un seul hôte public sert les deux serveurs Hermes.

- `/v1/...`  → serveur API du gateway (127.0.0.1:8642)  : le chat, qui fait tourner l'agent.
- tout le reste (`/api/...`, `/auth/...`, `/assets/...`, `/`) → dashboard (127.0.0.1:9119) :
  sessions, cron, kanban, fichiers, mémoire, gateway.

Pourquoi : l'app exige deux adresses distinctes (API + Dashboard) et il est très facile de les
confondre — erreur qui se traduit par « 404 Endpoint introuvable » ou « 401 ». Avec ce proxy,
**la même adresse marche dans les deux champs**.

Lancement : voir /home/john/start-proxy.sh. Écoute sur 127.0.0.1:8650 (à exposer par un tunnel).
"""
from __future__ import annotations

import http.client
import http.server
import socketserver
import sys

API_HOST, API_PORT = "127.0.0.1", 8642
DASH_HOST, DASH_PORT = "127.0.0.1", 9119
LISTEN_HOST, LISTEN_PORT = "127.0.0.1", 8650

# Ces en-têtes ne se relayent pas d'un saut à l'autre.
HOP_BY_HOP = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade",
}


def upstream_for(path: str) -> tuple[str, int]:
    """Route selon le chemin : l'API pour `/v1`, le dashboard pour tout le reste."""
    return (API_HOST, API_PORT) if path.startswith("/v1/") else (DASH_HOST, DASH_PORT)


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "OnyxGatewayProxy/1.0"

    def log_message(self, fmt: str, *args) -> None:  # journal compact
        sys.stderr.write("proxy: " + (fmt % args) + "\n")
        sys.stderr.flush()

    def _relay(self) -> None:
        host, port = upstream_for(self.path)
        length = self.headers.get("Content-Length")
        body = self.rfile.read(int(length)) if length else None

        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP_BY_HOP}
        headers["Host"] = f"{host}:{port}"
        # Sans terminateur TLS devant cette connexion, le client parle réellement HTTP.
        # Cloudflared fournit X-Forwarded-Proto=https: le conserver pour que les cookies
        # Secure restent limités à HTTPS, sans en fabriquer pour le HTTP local.
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
            # Réponses en flux (SSE du chat): on relaie en chunked, sans bufferiser.
            streaming = upstream.getheader("Transfer-Encoding", "").lower() == "chunked" or \
                upstream.getheader("Content-Type", "").startswith("text/event-stream")
            if streaming:
                self.send_header("Transfer-Encoding", "chunked")
            else:
                raw = upstream.read()
                self.send_header("Content-Length", str(len(raw)))
            self.end_headers()

            if streaming:
                # read(4096) attend de remplir tout le tampon et peut taire un petit heartbeat
                # SSE jusqu'au timeout du client. read1 relaie chaque chunk disponible aussitôt.
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
        except Exception as exc:  # upstream injoignable : erreur lisible pour l'app
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
