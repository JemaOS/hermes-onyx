#!/usr/bin/env python3
"""Onyx One — the "one key" gateway in front of Hermes, for the Onyx app.

What the app sends, and what this relay must answer (captured from its own traffic):

    GET  /v1/models              → 200, the list of models        (iro api_server :8642)
    GET  /api/sessions           → 200, the registry              (api_server :8642)
    GET  /api/profiles           → 200, {"profiles":[…]}           (read from disk)
    GET  /api/skills             → 200, {"skills":[…]}             (read from disk)
    POST /api/auth/ws-ticket     → 200, {"ticket":…,"ttl_seconds":…}
    GET  /api/onyx/endpoints     → 200, the connection manifest

A single key: the one from the `onyx://connect` link. It is required on **everything**, except the health
probe (`/api/status`), so that Cloudflare can see the service is up.

The 404 is what broke the app: it requests `/api/profiles` and `/api/auth/ws-ticket`, receives
`404`, and concludes the gateway is not there — even though `/v1/models` had just answered.
A relay that answers **200** to these routes, even with an empty list, lets it continue.
"""

from __future__ import annotations

import http.client
import io
import json
import os
import re
import socket
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

# --- Settings ------------------------------------------------------------------------------

PORT = int(os.environ.get("ONYX_ONE_PORT", "8890"))
HOTE = os.environ.get("ONYX_ONE_HOST", "127.0.0.1")

CLE_FICHIER = os.environ.get("ONYX_ONE_KEY_FILE", "/home/john/.onyx-one-key")
API_HOTE, API_PORT = "127.0.0.1", 8642          # hermes gateway api_server (/v1, /api/sessions)
DASH_HOTE, DASH_PORT = "127.0.0.1", 9119        # hermes serve: the dashboard's /api routes
# The `onyx-browser` plugin: its own server, never exposed. It is **this relay** that puts it
# behind the address and the key the app already knows — no extra tunnel, no open port.
BROWSER_HOTE, BROWSER_PORT = "127.0.0.1", 8788
BROWSER_PREFIXE = "/onyx-browser/"
# **The browser is not always on 8788.** Hermes runs several processes that
# load the plugin, and the agent's browser lives in the one that executes the turn — often
# `hermes serve --port 9119`, not the one that grabbed 8788 first. Each drops a record
# (`{pid, port, pending, browser, ts}`) and the relay sends the screen to the right one: first the one with
# a takeover request pending, otherwise the one with a browser open, otherwise 8788.
BROWSER_PEERS = os.environ.get(
    "ONYX_ONE_BROWSER_PEERS", os.path.join(os.path.expanduser("~/.hermes"), "onyx-browser", "peers"))
BROWSER_PEER_TTL = 20.0
# Plugin ticket → port that issued it. `observe` and the WebSocket are two separate requests:
# without this pin, a turn that ends between the two would present the ticket to the wrong
# process, and the handshake would end in a 401 with nothing to explain it.
_TICKETS: dict[str, tuple[float, int]] = {}

RACINE = os.path.expanduser("~/.hermes")
PROFILS = os.path.join(RACINE, "profiles")

VOIX = os.environ.get("ONYX_ONE_VOICES", "")
PONT = os.environ.get("ONYX_ONE_BRIDGE", "")
PONT_JETON = os.environ.get("ONYX_ONE_BRIDGE_TOKEN", "")
LANGUE = os.environ.get("ONYX_ONE_LANG", "fr")
MODELE = os.environ.get("ONYX_ONE_MODEL", "hermes-agent")

# Headers we never copy in either direction: they belong to the current hop.
SAUT = {"host", "connection", "content-length", "transfer-encoding", "keep-alive",
        "proxy-authenticate", "proxy-authorization", "te", "trailers", "upgrade"}


def _sante(port: int) -> dict:
    """What this process says about itself, or `{}` if it does not answer.

    A peer record is a **declaration**: it says what the process believed at the moment it
    wrote it. A process started before the registry was installed writes none — yet
    it holds the browser and the pending request. So we ask it directly, which
    depends neither on its version nor on the freshness of a file.
    """
    try:
        cx = http.client.HTTPConnection(BROWSER_HOTE, port, timeout=0.4)
        try:
            cx.request("GET", BROWSER_PREFIXE + "health")
            reponse = cx.getresponse()
            if reponse.status != 200:
                return {}
            charge = reponse.read()
        finally:
            cx.close()
    except OSError:
        return {}
    try:
        sante = json.loads(charge or b"{}")
    except ValueError:
        return {}
    return sante if isinstance(sante, dict) else {}


def _retenir_ticket(charge: bytes, port: int) -> None:
    """Pin the ticket to the process that issued it.

    `observe` and the WebSocket are two distinct requests: if the selection changes between the
    two — a turn ending, another opening — the ticket would be presented to the wrong
    process and the handshake would end in a 401. The ticket is valid only on its issuer.
    """
    try:
        ticket = (json.loads(charge or b"{}") or {}).get("ticket")
    except ValueError:
        return
    if not ticket:
        return
    maintenant = time.time()
    for cle in [k for k, (quand, _) in _TICKETS.items() if maintenant - quand > 40]:
        _TICKETS.pop(cle, None)
    _TICKETS[str(ticket)] = (maintenant, port)


def _ticket_de(chemin: str) -> int:
    """The port pinned for this URL's ticket, or 0."""
    from urllib.parse import parse_qs

    ticket = (parse_qs(urlsplit(chemin).query).get("ticket") or [""])[0]
    quand_port = _TICKETS.get(ticket)
    if not quand_port or time.time() - quand_port[0] > 40:
        return 0
    return quand_port[1]


def _candidats_navigateur() -> list[int]:
    """Every process where a browser can live, from the most promising to the least.

    The selection (_navigateur_a_joindre) chooses **one** process. Here we want all the
    candidates, so we can retry elsewhere when the one chosen rejects the ticket
    because the page lives in another.
    """
    fiches: list[dict] = []
    try:
        noms = os.listdir(BROWSER_PEERS)
    except OSError:
        noms = []
    maintenant = time.time()
    for nom in noms:
        if not nom.endswith(".json"):
            continue
        try:
            with open(os.path.join(BROWSER_PEERS, nom), encoding="utf-8") as flux:
                fiche = json.load(flux)
            port = int(fiche.get("port") or 0)
            pid = int(fiche.get("pid") or 0)
            age = maintenant - float(fiche.get("ts") or 0.0)
        except (OSError, ValueError, TypeError):
            continue
        if not port or age > BROWSER_PEER_TTL:
            continue
        if pid:
            try:
                os.kill(pid, 0)
            except OSError:
                continue
        fiches.append(fiche)
    candidats: list[int] = []
    for port in [int(f.get("port") or 0) for f in fiches] + [BROWSER_PORT]:
        if port and port not in candidats:
            candidats.append(port)
    # **Those with a real page first**: that is where the screen must land.
    avec_page = []
    for port in candidats:
        sante = _sante(port)
        if sante.get("ok") and sante.get("alive") is not False:
            avec_page.append((1 if str((sante.get("page") or {}).get("state")) == "ok" else 0, port))
    avec_page.sort(reverse=True)
    return [port for _, port in avec_page]


def _navigateur_a_joindre() -> tuple[str, int]:
    """Which process holds the agent's browser?

    Two sources, and the second decides: the records dropped by each copy of the plugin
    (`~/.hermes/onyx-browser/peers/<pid>.json`), then a **direct question** to each candidate
    (`/onyx-browser/health`). We prefer, in order:

      1. the one with a **takeover pending** — that is where the screen must arrive;
      2. the one with a **browser open** in its turn;
      3. the historical port 8788;
      4. the most recent.

    Landing on the wrong process is not a visible error: the app would see a screen that
    waits forever. This is exactly the defect we fix here — a missing record must not
    make a process invisible.
    """
    fiches: list[dict] = []
    try:
        noms = os.listdir(BROWSER_PEERS)
    except OSError:
        noms = []
    maintenant = time.time()
    for nom in noms:
        if not nom.endswith(".json"):
            continue
        try:
            with open(os.path.join(BROWSER_PEERS, nom), encoding="utf-8") as flux:
                fiche = json.load(flux)
            port = int(fiche.get("port") or 0)
            pid = int(fiche.get("pid") or 0)
            age = maintenant - float(fiche.get("ts") or 0)
        except (OSError, ValueError, TypeError):
            continue
        if not port or age > BROWSER_PEER_TTL:
            continue
        if pid:
            try:
                os.kill(pid, 0)
            except OSError:
                continue
        fiches.append(fiche)

    # **The direct question, and 8788 always a candidate.** A process that holds the browser
    # but writes no record (started before the registry was installed) must stay reachable:
    # this is exactly the case that left the phone screen on an empty process.
    candidats: list[int] = []
    for port in [int(f.get("port") or 0) for f in fiches] + [BROWSER_PORT]:
        if port and port not in candidats:
            candidats.append(port)
    if not candidats:
        return BROWSER_HOTE, BROWSER_PORT

    meilleur: tuple[int, int, int, float] | None = None  # (score, real page, 8788, freshness)
    gagnant = BROWSER_PORT
    for port in candidats:
        sante = _sante(port)
        if not sante.get("ok"):
            continue
        # **A peer whose page is dead must never win.** `browser` alone is not enough:
        # the gateway copy advertised `browser: true` (its `task_id` stays stuck after the
        # browser closes) with `alive: false` — and the phone screen landed on
        # a process that had nothing left to show, hence an `idle` stream instead of images.
        # `alive` absent = older plugin, which does not know how to lie: we keep its chance.
        if sante.get("alive") is False:
            continue
        score = 3 if sante.get("pending") else (2 if sante.get("browser") else 1)
        # **A real page beats an empty tab, at equal demand.** Two Hermes processes each
        # hold their own browser; without this tie-break, the screen could land on a blank tab
        # while the requested page lived in the other process.
        page = sante.get("page") or {}
        page_reelle = 1 if str(page.get("state") or "") == "ok" else 0
        # **A real page beats a mere "there is a browser".** This tie-break is what
        # sends the phone screen to the process showing *the* page, rather than to a
        # process that has a browser but nothing to display.
        score = score + (2 if page_reelle else 0)
        fraicheur = max([float(f.get("ts") or 0) for f in fiches if int(f.get("port") or 0) == port]
                        or [0.0])
        cle = (score, page_reelle, 1 if port == BROWSER_PORT else 0, fraicheur)
        if meilleur is None or cle > meilleur:
            meilleur = cle
            gagnant = port
    if meilleur is not None:
        return BROWSER_HOTE, gagnant

    # No candidate answers: we fall back to the old heuristic based on records alone.
    if not fiches:
        return BROWSER_HOTE, BROWSER_PORT
    en_attente = [f for f in fiches if f.get("pending")]
    if en_attente:
        return BROWSER_HOTE, int(max(en_attente, key=lambda f: float(f.get("ts") or 0))["port"])
    ouverts = [f for f in fiches if f.get("browser")]
    if ouverts:
        return BROWSER_HOTE, int(max(ouverts, key=lambda f: float(f.get("ts") or 0))["port"])
    return BROWSER_HOTE, int(max(fiches, key=lambda f: float(f.get("ts") or 0))["port"])


def _cle() -> str:
    try:
        with open(CLE_FICHIER, encoding="utf-8") as fh:
            cle = fh.read().strip()
    except FileNotFoundError:
        sys.exit(f"onyx-one : clé introuvable ({CLE_FICHIER})")
    if len(cle) < 16:
        sys.exit("onyx-one : clé trop courte dans " + CLE_FICHIER)
    return cle


CLE = _cle()

MAX_BODY_BYTES = 16 * 1024 * 1024
REQUEST_TIMEOUT = 15.0
MAX_WORKERS = 64


def _redact_log(value: object) -> str:
    # Drop all query values, including tickets and encoded credentials.
    text = re.sub(r"\?\S*", "?[REDACTED]", str(value))
    from urllib.parse import quote
    for name in ("CLE", "CLE_API", "JETON_DASHBOARD"):
        secret = globals().get(name, "")
        for candidate in (secret, quote(secret, safe="")):
            if candidate:
                text = text.replace(candidate, "[REDACTED]")
    return text


class IntakeSocket(socket.socket):
    """Carry the acceptance deadline into the handler without global state."""

    intake_deadline: float


class IntakeReader(io.RawIOBase):
    def __init__(self, connection):
        super().__init__()
        self.connection = connection
        self.deadline = connection.intake_deadline

    def readable(self):
        return True

    def check_deadline(self):
        if self.deadline is not None:
            remaining = self.deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Request intake deadline exceeded")
            self.connection.settimeout(remaining)

    def readinto(self, buffer):
        # Buffered readline may perform many receives; bound every receive.
        self.check_deadline()
        return self.connection.recv_into(buffer)


class BoundedHTTPServer(ThreadingHTTPServer):
    max_workers = MAX_WORKERS
    request_timeout = REQUEST_TIMEOUT
    daemon_threads = True

    def __init__(self, *args, **kwargs):
        self._workers = threading.BoundedSemaphore(self.max_workers)
        super().__init__(*args, **kwargs)

    def get_request(self):
        connection, address = super().get_request()
        deadline = time.monotonic() + self.request_timeout
        connection = IntakeSocket(fileno=connection.detach())
        connection.intake_deadline = deadline
        connection.settimeout(self.request_timeout)
        return connection, address

    def process_request(self, request, client_address):
        # Reject excess sockets instead of queueing unbounded threads or work.
        if not self._workers.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except BaseException:
            self._workers.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self._workers.release()

    def handle_error(self, request, client_address):
        import traceback
        sys.stderr.write(_redact_log(traceback.format_exc()))


def _jeton_dashboard() -> str:
    """The local dashboard's session token (`hermes serve`), when it is running.

    The loopback dashboard asks for no password: it requests the `X-Hermes-Session-Token`
    header, drawn at startup. Onyx One reads it here and presents it on every
    relay — this is what unlocks the routes the app requests and the api_server lacks:
    `api/cron/jobs`, `api/tools/toolsets`, `api/mcp/servers`, `api/system/stats`…
    """
    try:
        with open(os.environ.get("ONYX_ONE_DASH_TOKEN_FILE",
                                 "/home/john/.onyx-one-dashboard-token"), encoding="utf-8") as fh:
            return fh.read().strip()
    except OSError:
        return ""


JETON_DASHBOARD = _jeton_dashboard()


def _cle_api_server() -> str:
    """The key Hermes's `api_server` expects, read from **its** configuration.

    The app does not know it and has no reason to: it presents the installation key
    (a single one, its own), and it is Onyx One that talks to the api_server with the latter's key.
    The two therefore have no reason to be equal — and a new user has nothing to
    synchronize by hand.

    The read is **structural** when PyYAML is available: the block may live under `platforms`,
    under `gateway`, or elsewhere, and guessing its place from the indentation has already produced a second
    competing block on an existing installation.
    """
    chemin = os.environ.get("ONYX_ONE_HERMES_CONFIG",
                            os.path.expanduser("~/.hermes/config.yaml"))
    try:
        import yaml  # shipped with /usr/bin/python3; otherwise we fall back to a textual read
    except ImportError:
        yaml = None
    try:
        with open(chemin, encoding="utf-8", errors="replace") as fh:
            brut = fh.read()
    except OSError:
        return CLE

    if yaml is not None:
        try:
            cfg = yaml.safe_load(brut) or {}

            def trouver(noeud):
                if isinstance(noeud, dict):
                    for nom, valeur in noeud.items():
                        if nom == "api_server" and isinstance(valeur, dict):
                            cle = valeur.get("key")
                            if isinstance(cle, str) and cle.strip():
                                return cle.strip()
                        trouve = trouver(valeur)
                        if trouve:
                            return trouve
                return None

            trouve = trouver(cfg)
            if trouve:
                return trouve
        except Exception:  # noqa: BLE001 — an unreadable config must not kill the gateway
            pass

    # Fallback: the first `key:` of an indented block named `api_server:`.
    dans_bloc = False
    for ligne in brut.splitlines():
        if ligne.strip() == "api_server:":
            dans_bloc = True
            continue
        if dans_bloc:
            if ligne and not ligne[0].isspace():
                dans_bloc = False
                continue
            m = re.match(r"^\s+key\s*:\s*(\S+)\s*$", ligne)
            if m:
                return m.group(1).strip("\"'")
    return CLE


CLE_API = _cle_api_server()


# --- Data read from disk -------------------------------------------------------------------

def profils() -> list[dict]:
    """The Hermes profiles, read from `~/.hermes/profiles` (the default kept apart)."""
    rows = [{"name": "default", "path": RACINE, "is_default": True, "hidden": False}]
    try:
        for nom in sorted(os.listdir(PROFILS)):
            chemin = os.path.join(PROFILS, nom)
            if not os.path.isdir(chemin) or nom.startswith("."):
                continue
            rows.append({"name": nom, "path": chemin, "is_default": False, "hidden": False})
    except FileNotFoundError:
        pass
    return rows


def competences() -> list[dict]:
    """The installed skills: one SKILL.md per folder, in each known root."""
    vues, rows = set(), []
    for racine in ("skills", os.path.join("profiles", "kilo", "skills")):
        base = os.path.join(RACINE, racine)
        if not os.path.isdir(base):
            continue
        for dossier, _, fichiers in os.walk(base):
            if "SKILL.md" not in fichiers:
                continue
            nom = os.path.basename(dossier)
            if nom in vues:
                continue
            vues.add(nom)
            rows.append({
                "name": nom,
                "path": os.path.relpath(dossier, base),
                "description": _description(os.path.join(dossier, "SKILL.md")),
                "enabled": True,
                "source": "local",
            })
    return rows


def _description(chemin: str) -> str:
    try:
        with open(chemin, encoding="utf-8") as fh:
            for ligne in fh:
                if ligne.lower().startswith("description:"):
                    return ligne.split(":", 1)[1].strip().strip('"')[:200]
    except OSError:
        pass
    return ""


def _voix_vivante() -> str:
    """The voices' address, **re-read from disk** on every request.

    A disposable tunnel changes name when `cloudflared` restarts. Writing its address here once
    and for all is a guarantee that it will be wrong one day — and the app keeps what the
    manifest told it. So we read the last address it published.
    """
    if VOIX and VOIX != "auto":
        return VOIX
    try:
        with open("/home/john/kokoro-tts/tunnel.log", encoding="utf-8", errors="replace") as fh:
            urls = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", fh.read())
        return urls[-1] if urls else ""
    except OSError:
        return ""


def _pont_vivant() -> str:
    """The public address of the "Computer" bridge, re-read on every request (same reason as the voices)."""
    if PONT and PONT != "auto":
        return PONT
    try:
        with open("/home/john/.onyx-computer-bridge/tunnel.log", encoding="utf-8", errors="replace") as fh:
            urls = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", fh.read())
        return urls[-1] if urls else ""
    except OSError:
        return ""


def _jeton_pont() -> str:
    """The bridge token, read from its 0600 file — never copied elsewhere."""
    if PONT_JETON:
        return PONT_JETON
    try:
        with open("/home/john/.onyx-computer-bridge/token", encoding="utf-8") as fh:
            return fh.read().strip()
    except OSError:
        return ""


def manifeste() -> dict:
    return {
        "version": 1,
        "server": f"http://{HOTE}:{PORT}",
        "model": MODELE,
        "voices": _voix_vivante(),
        "bridge": _pont_vivant(),
        "bridgeToken": _jeton_pont(),
        "language": LANGUE,
        # **What the gateway can really do, told to the app.**
        #
        # The app decides to show the Bots tab and the rooms based on this list, not on what
        # it discovers while calling: a missing capability reads as "this gateway has no
        # bots", and the screen displays "No bot on this gateway" even though the server has some.
        # `profiles` and `groups` are served by the dashboard (9119) that this relay relays.
        "capabilities": ["sessions", "voices", "skills", "profiles", "bots", "groups"],
    }


# --- The relay ------------------------------------------------------------------------------

class Relais(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def setup(self):
        super().setup()
        self.rfile.close()
        self._intake_reader = IntakeReader(self.connection)
        self.rfile = io.BufferedReader(self._intake_reader)

    def log_message(self, fmt, *args):
        print(_redact_log(fmt % args), flush=True)

    def _body_length(self):
        if self.headers.get_all("Transfer-Encoding") is not None:
            self.send_error(400, "Unsupported transfer framing")
            return None
        lengths = self.headers.get_all("Content-Length", [])
        if not lengths:
            return 0
        if len(lengths) != 1 or not re.fullmatch(r"[0-9]+", lengths[0]):
            self.send_error(400, "Invalid Content-Length")
            return None
        # Bound before int conversion, including adversarial huge numerals.
        digits = lengths[0].lstrip("0") or "0"
        limit = str(MAX_BODY_BYTES)
        if len(digits) > len(limit) or (len(digits) == len(limit) and digits > limit):
            self.send_error(413, "Request body too large")
            return None
        return int(digits)

    def _public(self):
        return urlsplit(self.path).path in ("/health", "/healthz", "/api/status")

    def _unauthenticated(self):
        self.close_connection = True
        self._repondre(401, {"error": "unauthenticated", "detail": "Unauthorized"})

    def handle_expect_100(self):
        if not self._public() and not self._autorise():
            self._unauthenticated()
            return False
        if self._body_length() is None:
            return False
        return super().handle_expect_100()

    def _read_body(self, length):
        data = bytearray()
        try:
            self._intake_reader.check_deadline()
            while len(data) < length:
                self._intake_reader.check_deadline()
                chunk = self.rfile.read1(min(length - len(data), 65536))
                if not chunk:
                    self.send_error(400, "Incomplete request body")
                    return None
                data.extend(chunk)
            self._intake_reader.check_deadline()
        except (TimeoutError, OSError):
            self.send_error(408, "Request body timeout")
            return None
        # Only intake is bounded: SSE and WebSockets can outlive this deadline.
        self._intake_reader.deadline = None
        self.connection.settimeout(None)
        return bytes(data)

    # -- helpers --

    def _repondre(self, statut: int, charge: object) -> None:
        corps = json.dumps(charge, ensure_ascii=False).encode()
        self.send_response(statut)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(corps)))
        self.end_headers()
        self.wfile.write(corps)

    def _trace(self, statut: int = 0) -> None:
        """**Every request received, on one line.** Without that, "the app cannot connect" and "the app
        connects and fails" look alike: both leave a blank screen and no trace."""
        try:
            chemin = self.path.split("?")[0]
            self.log_message("HTTP %s %s -> %s", self.command, chemin, statut or "-")
        except Exception:
            pass

    def _autorise(self) -> bool:
        brut = (self.headers.get("Authorization") or "").strip()
        if brut[:7].lower() == "bearer ":
            brut = brut[7:].strip()
        if not brut:
            brut = (self.headers.get("X-API-Key") or "").strip()
        return bool(brut) and _constant(brut, CLE)

    def _entetes_amont(self, entete_auth: dict[str, str]) -> dict[str, str]:
        """The headers to present upstream: the client's, minus its identity.

        `X-Hermes-Profile` and `X-Hermes-Keep-Running` **pass through**: the first tells which
        Hermes profile the request is about (a session stored in `profiles/coder` does not exist
        in the default profile — 404 on every poll), the second says a turn must
        survive the phone's disconnection.
        """
        entetes = {}
        for nom, valeur in self.headers.items():
            if nom.lower() in SAUT or nom.lower() in ("authorization", "x-api-key",
                                                      "x-hermes-session-token"):
                continue
            entetes[nom] = valeur
        entetes.update(entete_auth)
        return entetes

    def _veut_un_flux(self, corps: bytes | None) -> bool:
        """Does this request expect a stream (SSE) rather than a response?

        Chat in `stream` mode: it is the only api_server route whose response
        lasts minutes. Everything else (session registry, model, etc.) is short
        and is copied as-is.
        """
        if not self.path.startswith("/v1/chat/completions"):
            return False
        if not corps:
            return False
        try:
            charge = json.loads(corps.decode("utf-8", errors="replace"))
        except ValueError:
            return False
        return bool(isinstance(charge, dict) and charge.get("stream"))

    def _vers_amont(self, hote: str, port: int, entete_auth: dict[str, str],
                    corps: bytes | None) -> tuple[int, list[tuple[str, str]], bytes]:
        """A round trip upstream, with the identity the relay presents in its place."""
        entetes = self._entetes_amont(entete_auth)
        cx = http.client.HTTPConnection(hote, port, timeout=300)
        try:
            cx.request(self.command, self.path, body=corps, headers=entetes)
            reponse = cx.getresponse()
            return reponse.status, [(k, v) for k, v in reponse.getheaders()], reponse.read()
        finally:
            cx.close()

    def _vers_amont_flux(self, hote: str, port: int, entete_auth: dict[str, str],
                         corps: bytes | None):
        """Opens the conversation with upstream and returns its response **without** reading the body.

        The body is left to the caller: an SSE stream is copied block by block, and
        swallowing it here would collect it in advance — that is, at the end of the turn.
        """
        entetes = self._entetes_amont(entete_auth)
        cx = http.client.HTTPConnection(hote, port, timeout=300)
        cx.request(self.command, self.path, body=corps, headers=entetes)
        return cx, cx.getresponse()

    def _relier_flux(self, reponse) -> None:
        """Copies a stream from upstream to the client, block by block.

        **This is the fix for the wait.** The relay read the **entire** response before
        writing the first line to the phone: during the whole agent turn
        (30 minutes observed on 2026-11-05), the app received nothing and displayed only
        "nothing from the server since …". Here each block leaves as soon as it arrives, and the turn
        stays visible live.

        The client may leave (app closed, tunnel cut): we stop writing, close
        upstream, and the turn **continues** on the gateway side (`X-Hermes-Keep-Running`), its
        response is stored in the session where the app will read it again.
        """
        self.send_response(reponse.status)
        for nom, valeur in reponse.getheaders():
            # `server` and `date` are rewritten by `send_response`: copying them
            # would produce two of each header.
            if nom.lower() in SAUT or nom.lower() in ("content-length", "server", "date"):
                continue
            self.send_header(nom, valeur)
        # The length is unknown: it is a stream, we declare it as such.
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        try:
            while True:
                bloc = reponse.read1(65536)
                if not bloc:
                    break
                self.wfile.write(b"%X\r\n" % len(bloc) + bloc + b"\r\n")
                self.wfile.flush()
            self.wfile.write(b"0\r\n\r\n")
            self.wfile.flush()
        except OSError:
            # The phone has left: the turn continues upstream, the response will be read later.
            pass
        finally:
            self.close_connection = True

    # -- routing --

    def _servir(self) -> None:
        # Do not reuse sockets with unread or ambiguously framed request bodies.
        self.close_connection = True
        self._trace()
        chemin = urlsplit(self.path).path

        # **Every browser request is traced, even refused.** The identity refusal (401)
        # made the response silent: nothing in the log showed that the app had
        # tried, and nothing showed what it had requested. One trace per request —
        # path, identity present or not, WebSocket upgrade — is enough to say whether
        # the screen is stuck before the socket (app side) or after (relay side).
        if chemin.startswith(BROWSER_PREFIXE):
            brut = (self.headers.get("Authorization") or "").strip()
            if brut[:7].lower() == "bearer ":
                brut = brut[7:].strip()
            self.log_message("%s",
                f"BROWSER REQ {self.command} {self.path} "
                f"ident={'oui' if brut else 'non'} "
                f"identok={_constant(brut, CLE) if brut else False} "
                f"maj={(self.headers.get('Upgrade') or '-')} "
                f"ua={(self.headers.get('User-Agent') or '-')[:24]}",
            )

        if not self._public() and not self._autorise():
            return self._unauthenticated()
        longueur = self._body_length()
        if longueur is None:
            return
        corps = self._read_body(longueur)
        if corps is None:
            return

        if chemin in ("/health", "/healthz"):
            return self._repondre(200, {"status": "ok"})
        if chemin == "/api/status":
            return self._repondre(200, {"version": "0.22.3", "auth_required": True,
                                        "gateway_running": True, "onyx_one": True})

        if chemin == "/api/onyx/endpoints":
            return self._repondre(200, manifeste())

        # **Two upstreams, and the boundary is the app's.**
        #
        #   api_server (8642): chat — `/v1/*` and the conversation registry.
        #   dashboard  (9119): all the rest of `/api` — cron, tools, MCP, stats, skills.
        #
        # Both are public only on this machine; it is Onyx One that carries the app's single
        # key and presents to each the identity it expects (Bearer on one side, session token
        # on the other). The app itself knows only one address and one key.
        vers_api = chemin.startswith("/v1/") or chemin.startswith("/api/sessions")
        vers_navigateur = chemin.startswith(BROWSER_PREFIXE)
        if self.command in ("GET", "POST") and (self.headers.get("Upgrade") or "").lower() == "websocket":
            if vers_navigateur:
                # **The ticket designates its process.** If it was pinned by the `observe` that
                # preceded it, we go there directly: the selection must not be allowed to change
                # between the two requests.
                epingle = _ticket_de(self.path)
                if epingle:
                    return self._relayer_websocket(chemin, BROWSER_HOTE, epingle)
                hote_nav, port_nav = _navigateur_a_joindre()
                return self._relayer_websocket(chemin, hote_nav, port_nav)
            return self._relayer_websocket(chemin)

        if vers_navigateur:
            # The plugin's ticket is not an identity for it: it is the app's. The plugin
            # accepts the loopback, and its port listens only there — it has nothing more to check.
            #
            # **A mute upstream does not kill the request.** The plugin listens only once the gateway
            # has started; before that, `http.client` raises, and without this guard the connection closed without
            # a response (the app saw "nothing", not "absent"). We answer 503, which the capability probe
            # understands: the line does not appear, and nothing lies.
            try:
                hote_nav, port_nav = _navigateur_a_joindre()
                statut, entetes, charge = self._vers_amont(hote_nav, port_nav, {}, corps)
                # **What the app really requests.** The plugin ignores these calls: only the relay
                # sees them. Without this trace, a black screen does not say whether the probe left
                # (and failed) or was never sent — two opposite diagnoses.
                self.log_message("BROWSER %s %s -> %s (port %s)", self.command, chemin, statut, port_nav)
                # **The plugin rejects the ticket when the page is in another process.** We
                # do not return this refusal to the app: we retry elsewhere, in the order of the
                # candidates, until we find the one that has the page. The app then receives a valid
                # ticket, and the screen finally connects to the right image.
                if statut == 409 and chemin.rstrip("/").endswith("/observe"):
                    for autre in _candidats_navigateur():
                        if autre == port_nav:
                            continue
                        sante = _sante(autre)
                        if not sante.get("ok") or sante.get("alive") is False:
                            continue
                        try:
                            s2, e2, c2 = self._vers_amont(hote_nav, autre, {}, corps)
                        except OSError:
                            continue
                        self.log_message("BROWSER POST %s -> %s (port %s, reroute)", chemin, s2, autre)
                        if s2 == 200:
                            statut, entetes, charge = s2, e2, c2
                            port_nav = autre
                            break
                if statut == 200 and chemin.rstrip("/").endswith("/observe"):
                    _retenir_ticket(charge, port_nav)
            except OSError:
                return self._repondre(503, {"error": "browser_unavailable",
                                            "detail": "no onyx-browser plugin is listening"})
        elif vers_api:
            # **Chat is a stream.** `/v1/chat/completions` in `stream` mode must reach
            # the phone block by block: swallowing it first would leave it facing "nothing from the
            # server" during the whole turn. The other api_server routes are short
            # responses: they keep the simple path.
            if self._veut_un_flux(corps):
                cx, reponse = self._vers_amont_flux(
                    API_HOTE, API_PORT, {"Authorization": "Bearer " + CLE_API}, corps)
                try:
                    self._relier_flux(reponse)
                finally:
                    cx.close()
                return
            statut, entetes, charge = self._vers_amont(
                API_HOTE, API_PORT, {"Authorization": "Bearer " + CLE_API}, corps)
        elif JETON_DASHBOARD:
            statut, entetes, charge = self._vers_amont(
                DASH_HOTE, DASH_PORT, {"X-Hermes-Session-Token": JETON_DASHBOARD}, corps)
            # The dashboard does not know how to issue a ticket for a client without a cookie: we do it.
            if chemin == "/api/auth/ws-ticket" and statut in (401, 404):
                return self._repondre(200, {"ticket": uuid.uuid4().hex, "ttl_seconds": 60})
        else:
            return self._repondre(503, {"error": "dashboard_unavailable",
                                        "detail": "hermes serve is not running on 9119"})

        self.send_response(statut)
        for nom, valeur in entetes:
            if nom.lower() in SAUT:
                continue
            self.send_header(nom, valeur)
        self.send_header("Content-Length", str(len(charge)))
        self.end_headers()
        self.wfile.write(charge)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_OPTIONS = _servir


    def _relayer_websocket(self, chemin: str, hote: str = DASH_HOTE,
                           port: int = DASH_PORT) -> None:
        """A WebSocket connection, relayed **byte for byte**.

        `http.server` does not know how to negotiate an upgrade; we therefore take the socket as
        it is and connect it to upstream, slipping the dashboard's identity header into
        the upgrade request. This is what gives the app its live event stream.
        """
        import select
        import socket

        amont = socket.create_connection((hote, port), timeout=10)
        try:
            # The app's ticket means nothing to the dashboard (it is fake — see
            # `api/auth/ws-ticket`): on loopback, it is `?token=` that opens the socket. We
            # therefore replace the parameter on the way.
            import urllib.parse as _up
            morceaux = _up.urlsplit(self.path)
            parametres = [(k, v) for k, v in _up.parse_qsl(morceaux.query, keep_blank_values=True)
                          if k != "ticket"]
            parametres.append(("token", JETON_DASHBOARD))
            if (hote, port) == (DASH_HOTE, DASH_PORT):
                chemin_amont = morceaux.path + "?" + _up.urlencode(parametres)
            else:
                # The browser plugin does not know the dashboard token: the ticket, though,
                # stays as-is — it is its own.
                chemin_amont = self.path
            lignes = [f"GET {chemin_amont} HTTP/1.1", f"Host: {hote}:{port}"]
            # **We keep the upgrade.** `SAUT` is used by the ordinary HTTP relay; here, removing
            # `Upgrade`/`Connection` would turn the handshake into a plain GET, and the
            # dashboard would answer 401 instead of 101.
            garder = {"upgrade", "connection", "sec-websocket-key", "sec-websocket-version",
                      "sec-websocket-protocol", "sec-websocket-extensions", "origin"}
            for nom, valeur in self.headers.items():
                if nom.lower() in ("authorization", "x-api-key", "host"):
                    continue
                if nom.lower() in SAUT and nom.lower() not in garder:
                    continue
                lignes.append(f"{nom}: {valeur}")
            amont.sendall(("\r\n".join(lignes) + "\r\n\r\n").encode())
            self.log_message("WS -> %s:%s%s", hote, port, chemin_amont)

            client = self.connection
            client.setblocking(False)
            amont.setblocking(False)
            while True:
                lisibles, _, en_erreur = select.select([client, amont], [], [client, amont], 60)
                if en_erreur:
                    break
                for source in lisibles:
                    autre = amont if source is client else client
                    try:
                        donnees = source.recv(65536)
                    except BlockingIOError:
                        continue
                    except OSError:
                        return
                    if not donnees:
                        return
                    # **`sendall` on a non-blocking socket raises `BlockingIOError`** as soon as the
                    # buffer is full — this is the normal case for a phone on 4G, which reads more
                    # slowly than the server writes. The exception propagated up here: the relay
                    # lost the connection **without saying anything**, and the phone screen stayed on
                    # "Waiting…" without ever knowing why. So we write in pieces, waiting
                    # for the socket to be writable.
                    reste = donnees
                    while reste:
                        try:
                            envoye = autre.send(reste)
                        except BlockingIOError:
                            select.select([], [autre], [], 30)
                            continue
                        except OSError:
                            return
                        reste = reste[envoye:]
        finally:
            amont.close()
            self.close_connection = True


def _constant(a: str, b: str) -> bool:
    import hmac
    return hmac.compare_digest(a.encode(), b.encode())


if __name__ == "__main__":
    srv = BoundedHTTPServer((HOTE, PORT), Relais)
    srv.daemon_threads = True
    print(f"onyx-one sur {HOTE}:{PORT} -> api_server {API_HOTE}:{API_PORT}", flush=True)
    srv.serve_forever()
