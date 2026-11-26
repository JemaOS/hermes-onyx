#!/usr/bin/env python3
"""Régression du schéma d'authentification relayé par le proxy Dashboard Onyx."""

from __future__ import annotations

import http.client
import http.server
import importlib.util
import os
from pathlib import Path
import sys
import threading
import unittest
from typing import Any, cast

sys.dont_write_bytecode = True

PROXY_PATH = Path(
    os.environ.get(
        "ONYX_PROXY_UNDER_TEST",
        Path(__file__).with_name("onyx-gateway-proxy.py"),
    )
)


def load_proxy() -> Any:
    spec = importlib.util.spec_from_file_location("onyx_gateway_proxy_under_test", PROXY_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Impossible de charger {PROXY_PATH}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class UpstreamHandler(http.server.BaseHTTPRequestHandler):
    """Réplique minimale la décision Secure du Dashboard selon le schéma reçu."""

    protocol_version = "HTTP/1.1"

    def do_POST(self) -> None:
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        cast(RecordingUpstream, self.server).requests.append(
            {"path": self.path, "headers": dict(self.headers), "body": body}
        )
        https = self.headers.get("X-Forwarded-Proto") == "https"
        name = "__Host-hermes_session_at" if https else "hermes_session_at"
        cookie = f"{name}=test-session; Path=/; HttpOnly; SameSite=lax"
        if https:
            cookie += "; Secure"
        payload = b'{"ok":true}'
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Set-Cookie", cookie)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args) -> None:
        pass


class RecordingUpstream(http.server.ThreadingHTTPServer):
    daemon_threads = True
    requests: list[dict]


class GatewayProxySchemeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.proxy = load_proxy()

    def setUp(self) -> None:
        self.upstream = RecordingUpstream(("127.0.0.1", 0), UpstreamHandler)
        self.upstream.requests = []
        self.upstream_thread = threading.Thread(target=self.upstream.serve_forever, daemon=True)
        self.upstream_thread.start()

        self.proxy.DASH_HOST = "127.0.0.1"
        self.proxy.DASH_PORT = self.upstream.server_address[1]
        quiet_handler = type(
            "QuietHandler",
            (self.proxy.Handler,),
            {"log_message": lambda self, fmt, *args: None},
        )
        self.relay = self.proxy.Server(("127.0.0.1", 0), quiet_handler)
        self.relay_thread = threading.Thread(target=self.relay.serve_forever, daemon=True)
        self.relay_thread.start()

    def tearDown(self) -> None:
        for server, thread in (
            (self.relay, self.relay_thread),
            (self.upstream, self.upstream_thread),
        ):
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)

    def login(self, extra_headers=None):
        headers = {"Content-Type": "application/json", **(extra_headers or {})}
        conn = http.client.HTTPConnection("127.0.0.1", self.relay.server_address[1], timeout=5)
        self.addCleanup(conn.close)
        conn.request("POST", "/auth/password-login", body=b"{}", headers=headers)
        response = conn.getresponse()
        self.assertEqual(200, response.status)
        self.assertEqual(b'{"ok":true}', response.read())
        self.assertEqual(1, len(self.upstream.requests))
        return response, self.upstream.requests[0]

    def test_local_http_gets_a_cookie_the_client_can_return(self) -> None:
        response, seen = self.login()

        self.assertEqual("http", seen["headers"].get("X-Forwarded-Proto"))
        self.assertEqual(f"127.0.0.1:{self.upstream.server_address[1]}", seen["headers"].get("Host"))
        cookie = response.getheader("Set-Cookie")
        assert cookie is not None
        self.assertTrue(cookie.startswith("hermes_session_at="), cookie)
        self.assertNotIn("Secure", cookie)

    def test_https_tunnel_keeps_secure_cookie_and_auth_headers(self) -> None:
        response, seen = self.login(
            {
                "X-Forwarded-Proto": "https",
                "Cookie": "hermes_session_at=existing-session",
                "Authorization": "Bearer test-token",
            }
        )

        self.assertEqual("https", seen["headers"].get("X-Forwarded-Proto"))
        self.assertEqual("hermes_session_at=existing-session", seen["headers"].get("Cookie"))
        self.assertEqual("Bearer test-token", seen["headers"].get("Authorization"))
        cookie = response.getheader("Set-Cookie")
        assert cookie is not None
        self.assertTrue(cookie.startswith("__Host-hermes_session_at="), cookie)
        self.assertIn("Secure", cookie)


if __name__ == "__main__":
    unittest.main(verbosity=2)
