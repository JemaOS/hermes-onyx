"""Loopback-only security and streaming contracts for the Onyx proxy.

Run: python3 -m unittest discover -s tools/tests -p test_onyx_one_security.py -v
"""
import contextlib
import http.client
import importlib.util
import io
import os
from pathlib import Path
import queue
import socket
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch


TOKEN = "test-installation-secret-0123456789"
TICKET = "ticket-must-never-appear-in-logs"


class Upstream(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def do_GET(self):
        self.server.requests.put((self.command, self.path, b""))
        if self.headers.get("Upgrade", "").lower() == "websocket":
            self.send_response(101)
            self.send_header("Upgrade", "websocket")
            self.send_header("Connection", "Upgrade")
            self.end_headers()
            self.wfile.flush()
            # An idle upgraded transport must survive the intake deadline.
            time.sleep(0.5)
            data = self.connection.recv(4096)
            self.connection.sendall(data)
            self.close_connection = True
            return
        if self.path.startswith("/v1/events"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(b"data: first\n\n")
            self.wfile.flush()
            time.sleep(0.5)
            self.wfile.write(b"data: second\n\n")
            self.close_connection = True
            return
        self.send_response(200)
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"ok")

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.server.requests.put((self.command, self.path, body))
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class ProxySecurityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="onyx-security-", dir=os.environ.get("TMPDIR"))
        root = Path(cls.temp.name)
        (root / "key").write_text(TOKEN, encoding="ascii")
        env = {
            "ONYX_ONE_KEY_FILE": str(root / "key"),
            "ONYX_ONE_HERMES_CONFIG": str(root / "missing-config"),
            "ONYX_ONE_DASH_TOKEN_FILE": str(root / "missing-dashboard-token"),
            "ONYX_ONE_BROWSER_PEERS": str(root / "missing-peers"),
        }
        spec = importlib.util.spec_from_file_location("onyx_one_security", Path(__file__).parents[1] / "onyx-one.py")
        cls.proxy = importlib.util.module_from_spec(spec)
        with patch.dict(os.environ, env):
            spec.loader.exec_module(cls.proxy)

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def setUp(self):
        self.output = io.StringIO()
        capture = contextlib.ExitStack()
        self.addCleanup(capture.close)
        capture.enter_context(contextlib.redirect_stdout(self.output))
        capture.enter_context(contextlib.redirect_stderr(self.output))
        self.upstream = ThreadingHTTPServer(("127.0.0.1", 0), Upstream)
        self.upstream.daemon_threads = True
        self.upstream.requests = queue.Queue()
        self.start_server(self.upstream)
        port = self.upstream.server_port
        self.proxy.API_HOTE, self.proxy.API_PORT = "127.0.0.1", port
        self.proxy.DASH_HOTE, self.proxy.DASH_PORT = "127.0.0.1", port
        self.proxy.BROWSER_HOTE, self.proxy.BROWSER_PORT = "127.0.0.1", port
        self.proxy._TICKETS.clear()
        self.proxy._TICKETS[TICKET] = (time.time(), port)
        accepted = queue.Queue()
        base = self.proxy.Relais

        class Handler(base):
            def setup(self):
                super().setup()
                accepted.put(True)

            def do_TRACE(self):
                raise RuntimeError(self.path)

        class Server(self.proxy.BoundedHTTPServer):
            max_workers = 2
            request_timeout = 0.3

        self.server = Server(("127.0.0.1", 0), Handler)
        self.accepted = accepted
        self.start_server(self.server)

    def start_server(self, server):
        worker = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.01})
        worker.start()

        def stop():
            server.shutdown()
            server.server_close()
            worker.join(2)
        self.addCleanup(stop)

    def connect(self):
        connection = socket.create_connection(self.server.server_address, timeout=2)
        connection.settimeout(2)
        self.addCleanup(connection.close)
        return connection

    def request(self, headers="", body=b"", auth=True, path="/v1/echo", method="POST", half_close=False):
        connection = self.connect()
        authorization = f"Authorization: Bearer {TOKEN}\r\n" if auth else ""
        connection.sendall((f"{method} {path} HTTP/1.1\r\nHost: localhost\r\n" + authorization + headers + "\r\n").encode("ascii") + body)
        if half_close:
            connection.shutdown(socket.SHUT_WR)
        response = http.client.HTTPResponse(connection)
        response.begin()
        content = response.read()
        response.close()
        return response.status, content

    def assert_not_forwarded(self):
        self.assertTrue(self.upstream.requests.empty(), "rejected request reached upstream")

    def test_unauthenticated_body_is_not_read_or_validated(self):
        for framing in ("Content-Length: 10000\r\n", "Content-Length: invalid\r\n", "Transfer-Encoding: chunked\r\n"):
            with self.subTest(framing=framing):
                status, _ = self.request(framing, auth=False)
                self.assertEqual(status, 401)
        self.assert_not_forwarded()

    def test_expect_continue_does_not_bypass_auth_or_size_limit(self):
        for auth, length, expected in ((False, 5, 401), (True, self.proxy.MAX_BODY_BYTES + 1, 413)):
            with self.subTest(auth=auth):
                status, _ = self.request(f"Expect: 100-continue\r\nContent-Length: {length}\r\n", auth=auth)
                self.assertEqual(status, expected)
        self.assert_not_forwarded()

    def test_rejects_ambiguous_invalid_and_unsupported_framing(self):
        cases = [
            "Content-Length: -1\r\n", "Content-Length: +1\r\n",
            "Content-Length: 1.0\r\n", "Content-Length: 1, 1\r\n",
            "Content-Length: \r\n", "Content-Length: 1\r\nContent-Length: 1\r\n",
            "Content-Length: 1\r\nContent-Length: 2\r\n",
            "Transfer-Encoding: chunked\r\n", "Transfer-Encoding: identity\r\n",
            "Transfer-Encoding: \r\nContent-Length: 0\r\n",
            "Transfer-Encoding: chunked\r\nContent-Length: 0\r\n",
        ]
        for framing in cases:
            with self.subTest(framing=framing):
                self.assertEqual(self.request(framing)[0], 400)
        self.assert_not_forwarded()

    def test_oversized_and_huge_decimal_lengths_are_rejected(self):
        for length in (str(self.proxy.MAX_BODY_BYTES + 1), "9" * 5000):
            with self.subTest(length=length[:30]):
                self.assertEqual(self.request(f"Content-Length: {length}\r\n")[0], 413)
        self.assert_not_forwarded()

    def test_short_body_is_rejected_without_upstream_side_effects(self):
        self.assertEqual(self.request("Content-Length: 8\r\n", b"abc", half_close=True)[0], 400)
        self.assert_not_forwarded()

    def test_body_limit_is_inclusive(self):
        with patch.object(self.proxy, "MAX_BODY_BYTES", 4):
            self.assertEqual(self.request("Content-Length: 4\r\n", b"abcd"), (200, b"abcd"))
            self.assertEqual(self.upstream.requests.get(timeout=1)[2], b"abcd")
            self.assertEqual(self.request("Content-Length: 5\r\n")[0], 413)
        self.assert_not_forwarded()

    def test_stalled_body_times_out_without_upstream_side_effects(self):
        self.assertEqual(self.request("Content-Length: 8\r\n", b"abc")[0], 408)
        self.assert_not_forwarded()

    def test_body_deadline_is_not_reset_by_drip_feed(self):
        connection = self.connect()
        connection.sendall((f"POST /v1/echo HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer {TOKEN}\r\nContent-Length: 8\r\n\r\n").encode())
        for _ in range(3):
            connection.sendall(b"a")
            time.sleep(0.09)
        response = http.client.HTTPResponse(connection)
        response.begin()
        self.assertEqual(response.status, 408)
        response.read()
        response.close()
        self.assert_not_forwarded()

    def test_valid_and_zero_length_bodies_and_health_remain_supported(self):
        for framing, body in (("Content-Length: 3\r\n", b"abc"), ("Content-Length: 0003\r\n", b"xyz"), ("", b"")):
            with self.subTest(framing=framing):
                self.assertEqual(self.request(framing, body), (200, body))
                self.assertEqual(self.upstream.requests.get(timeout=1)[2], body)
        self.assertEqual(self.request(auth=False, path="/health", method="GET")[0], 200)

    def test_partial_headers_time_out_and_workers_are_reclaimed(self):
        first, second = self.connect(), self.connect()
        self.accepted.get(timeout=1)
        self.accepted.get(timeout=1)
        first.sendall(b"GET /health HTTP/1.1\r\n")
        second.sendall(b"GET /health HTTP/1.1\r\n")
        overflow = self.connect()
        self.assertEqual(overflow.recv(1), b"", "worker cap should close excess sockets")
        self.assertEqual(first.recv(1), b"", "partial headers should time out")
        self.assertEqual(second.recv(1), b"")
        self.assertEqual(self.request(auth=False, path="/health", method="GET")[0], 200)

    def test_drip_intake_cannot_hold_workers_past_acceptance_deadline(self):
        cases = (
            (b"G", b"E"),
            (b"GET /health HTTP/1.1\r\nX-Slow: ", b"a"),
            (b"GET /health HTTP/1.1\r\n", b"X-Slow: a\r\n"),
        )
        for initial, drip in cases:
            with self.subTest(initial=initial):
                # Discard acceptance notices left by the previous health check.
                while not self.accepted.empty():
                    self.accepted.get_nowait()
                clients = [self.connect(), self.connect()]
                for client in clients:
                    self.accepted.get(timeout=1)
                    client.sendall(initial)
                overflow = self.connect()
                self.assertEqual(overflow.recv(1), b"", "worker cap must reject overflow")
                stopped = threading.Event()

                def feed(client):
                    while not stopped.wait(0.04):
                        try:
                            client.sendall(drip)
                        except OSError:
                            return

                feeders = [threading.Thread(target=feed, args=(client,)) for client in clients]
                for feeder in feeders:
                    feeder.start()
                try:
                    for client in clients:
                        client.settimeout(0.65)
                        try:
                            closed = client.recv(1) == b""
                        except ConnectionResetError:
                            closed = True
                        except socket.timeout:
                            closed = False
                        self.assertTrue(closed, "dripping intake retained a worker past its deadline")
                    self.assertEqual(self.request(auth=False, path="/health", method="GET")[0], 200)
                    self.assert_not_forwarded()
                finally:
                    stopped.set()
                    for feeder in feeders:
                        feeder.join(1)
                    for client in clients:
                        client.close()

    def test_headers_and_body_share_one_acceptance_deadline(self):
        connection = self.connect()
        self.accepted.get(timeout=1)
        connection.sendall((f"POST /v1/echo HTTP/1.1\r\nHost: localhost\r\n"
                            f"Authorization: Bearer {TOKEN}\r\nContent-Length: 1\r\n").encode())
        time.sleep(0.18)
        connection.sendall(b"\r\n")
        # There is 0.12s left in the acceptance budget, not a fresh 0.3s.
        # Read before sending more data: writing after closure can reset the
        # socket and discard the 408 response on some platforms.
        connection.settimeout(0.22)
        response = http.client.HTTPResponse(connection)
        try:
            response.begin()
            self.assertEqual(response.status, 408)
            response.read()
        except (http.client.RemoteDisconnected, ConnectionResetError):
            # Expiration while finishing headers can close without a response.
            pass
        finally:
            response.close()
        self.assert_not_forwarded()

    def test_sse_outlives_request_intake_timeout(self):
        status, body = self.request(path="/v1/events", method="GET")
        self.assertEqual(status, 200)
        self.assertEqual(body, b"data: first\n\ndata: second\n\n")

    def test_websocket_tunnel_outlives_timeout_and_redacts_upstream_query(self):
        connection = self.connect()
        connection.sendall((f"GET /onyx-browser/ws?token={TOKEN}&ticket={TICKET} HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer {TOKEN}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n").encode())
        headers = bytearray()
        while not headers.endswith(b"\r\n\r\n"):
            headers.extend(connection.recv(1))
        self.assertIn(b" 101 ", headers)
        time.sleep(0.55)
        # A real masked client text frame, echoed by the loopback transport peer.
        frame = b"\x81\x82\x01\x02\x03\x04ik"
        connection.sendall(frame)
        echoed = bytearray()
        while len(echoed) < len(frame):
            echoed.extend(connection.recv(len(frame) - len(echoed)))
        self.assertEqual(bytes(echoed), frame)
        connection.shutdown(socket.SHUT_WR)
        while connection.recv(1024):
            pass
        log = self.output.getvalue()
        self.assertIn("WS ->", log)
        self.assertNotIn(TOKEN, log)
        self.assertNotIn(TICKET, log)
        forwarded = self.upstream.requests.get(timeout=1)[1]
        self.assertIn("token=" + TOKEN, forwarded, "redaction must not change routing")

    def test_browser_generic_access_and_error_logs_redact_queries(self):
        paths = [f"/onyx-browser/status?token={TOKEN}&ticket={TICKET}", f"/v1/test?token={TOKEN}&ticket={TICKET}"]
        for path in paths:
            self.assertEqual(self.request(path=path, method="GET")[0], 200)
            forwarded = self.upstream.requests.get(timeout=1)[1]
            while forwarded == "/onyx-browser/health":
                forwarded = self.upstream.requests.get(timeout=1)[1]
            self.assertEqual(forwarded, path)
        self.assertEqual(self.request(auth=False, path=f"/unknown?token={TOKEN}&ticket={TICKET}", method="GET")[0], 401)
        self.assertEqual(self.request(auth=False, path=f"/unknown?token={TOKEN}&ticket={TICKET}", method="PROPFIND")[0], 501)
        log = self.output.getvalue()
        self.assertIn("BROWSER REQ", log)
        self.assertIn("[REDACTED]", log)
        self.assertNotIn(TOKEN, log)
        self.assertNotIn(TICKET, log)


    def test_uncaught_worker_exception_trace_redacts_query_and_secrets(self):
        connection = self.connect()
        connection.sendall((f"TRACE /failure?token={TOKEN}&ticket={TICKET} HTTP/1.1\r\nHost: localhost\r\n\r\n").encode())
        self.assertEqual(connection.recv(1), b"")
        log = self.output.getvalue()
        self.assertIn("RuntimeError", log)
        self.assertIn("[REDACTED]", log)
        self.assertNotIn(TOKEN, log)
        self.assertNotIn(TICKET, log)


if __name__ == "__main__":
    unittest.main()
