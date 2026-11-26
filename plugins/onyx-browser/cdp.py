"""The only place that talks to Chromium: capturing a frame, and replaying a gesture.

We go through the seam Hermes reserves for plugins (`SUPERVISOR_REGISTRY.capture`), with the
`task_id` of the turn's `browser_*` tools. The rest of the plugin ignores CDP.

Three things this module does that the rest must not redo:

- **aim at the right page**: the supervisor is pinned to the first tab, often an empty page;
  we take the last real web page and attach to it (`Target.attachToTarget`, sessions
  cached);
- **set a phone viewport** before typing (§2.1 of the plan), and **restore it** at the end;
- **detect the login form** at the moment control is handed back — the host and the presence of a
  `type=password` field — so the app can offer to save it, without ever guessing.
"""

from __future__ import annotations

import base64
import glob
import json
import os
import re
import socket
import struct
import subprocess
import time
import urllib.request
from pathlib import Path
from typing import Any

#: A credible Android Chromium: it is what the site serves when we ask for its mobile version.
MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/131.0.0.0 Mobile Safari/537.36"
)
MOBILE_WIDTH, MOBILE_HEIGHT, MOBILE_DPR = 390, 844, 2

_KEYS = {
    "Enter": ("Enter", "Enter", 13, "\r"),
    "Backspace": ("Backspace", "Backspace", 8, ""),
    "Tab": ("Tab", "Tab", 9, ""),
    "Escape": ("Escape", "Escape", 27, ""),
}

_SKIP_PREFIXES = (
    "chrome://",
    "about:blank",
    "about:srcdoc",
    "devtools://",
    "chrome-error://",
    "edge://",
    "view-source:",
)


def is_real_page(url: str) -> bool:
    """A tab that shows something: it is neither an internal page nor blank.

    **The distinction is not cosmetic.** Hermes closes a browser left idle for two minutes without
    a tool (`browser.inactivity_timeout`) and reopens an empty one on the next turn. A takeover
    requested on that fresh browser showed a blank screen for fifteen minutes: the human
    thought the page was not arriving, and the agent waited for an impossible rendezvous.
    """
    return bool(url) and not str(url).startswith(_SKIP_PREFIXES)


class BrowserUnavailable(RuntimeError):
    """No live agent browser for this turn."""


#: The file where Chromium writes the DevTools port it chose, at the root of its
#: `--user-data-dir`. It is the only source that survives a browser being relearned.
_DEVTOOLS_PORT_FILE = "DevToolsActivePort"


def _session_keys(task_id: str) -> list[str]:
    """The session keys under which this turn can have a browser.

    The same turn appears under several forms: the bare key (`api-xxx`), the profile key
    (`api-xxx@/home/john/.hermes`), and the key remembered as "last active session"
    when a navigation took place. We try them in that order, keeping only those
    that **already exist**: querying an absent key would launch a fresh browser, and
    the phone screen would then show a blank page instead of the right one.
    """
    if not task_id:
        return []
    cles = [task_id]
    if "@" not in task_id:
        try:
            from hermes_constants import hermes_home_key

            cles.append(f"{task_id}@{hermes_home_key()}")
        except Exception:
            pass
    try:
        from tools.browser_tool import _last_active_session_key

        memorisee = _last_active_session_key.get(task_id)
        if memorisee:
            cles.append(str(memorisee))
    except Exception:
        pass
    return cles


def _session_cdp_url(task_id: str) -> str:
    """The DevTools URL **of this turn's session**, or ``""``.

    This is Hermes' canonical path: the `agent-browser get cdp-url` command on the turn's
    session. It is bound to the task, so it cannot return another session's
    browser — which matters, since several turns run in parallel on this
    machine.

    **Why it is necessary:** the `SUPERVISOR_REGISTRY` seam only resolves in the
    process that launched the browser, and its CDP URL becomes stale as soon as that browser
    is reaped and reopened on another port. The supervisor would then try to reconnect
    to a dead port, stop after five failures, and the plugin would conclude "no
    browser" — whereas the page existed and the phone showed only a blank screen.

    This command also refreshes the daemon's timer: direct CDP traffic, for its part, is
    invisible to it.
    """
    try:
        from tools.browser_tool import _active_sessions
        from tools.browser_tool_session import _run_browser_command
    except Exception:
        return ""
    for cle in _session_keys(task_id):
        # **Only a session that already exists**: an absent key would launch a new
        # browser, and the phone screen would show blank instead of the page.
        if cle not in _active_sessions:
            continue
        try:
            res = _run_browser_command(cle, "get", ["cdp-url"], timeout=15)
        except Exception:
            continue
        if not (res or {}).get("success"):
            continue
        cdp = str(((res or {}).get("data") or {}).get("cdpUrl") or "")
        if cdp.startswith(("ws://", "wss://")):
            return cdp
    return ""


def _shows_real_page(cdp: Any) -> bool:
    """Does this CDP client see at least one real web page?

    Used to rule out a supervisor that is still "alive" but attached to a closed Chromium: without this
    filter, `capture` succeeds, then every frame fails, and the phone screen shows "blank"
    whereas another browser shows the page.
    """
    try:
        reply = cdp.call("Target.getTargets", timeout=3)
    except Exception:
        return False
    targets = list((reply.get("result") or {}).get("targetInfos") or [])
    return any(
        t.get("type") == "page" and not str(t.get("url") or "").startswith(_SKIP_PREFIXES)
        for t in targets
    )


def _profils_sous(racine: Path) -> list[Path]:
    """The `agent-browser-chrome-*` profiles directly under `root` and one level deeper.

    Two forms coexist on this machine: `~/.hermes/cache/scratch/agent-browser-chrome-*`
    and `~/.hermes/profiles/<name>/cache/scratch/agent-browser-chrome-*`. A single level of descent
    suffices and keeps the scan bounded.
    """
    trouves: list[Path] = []
    try:
        trouves.extend(racine.glob("agent-browser-chrome-*"))
        for sous in racine.glob("*/cache/scratch"):
            trouves.extend(sous.glob("agent-browser-chrome-*"))
    except Exception:
        pass
    return trouves


def _ports_depuis_proc() -> list[tuple[float, int]]:
    """(freshness, DevTools port) of the Chromium browsers launched by the agent, read from `/proc`.

    **Chrome rewrites `/proc/<pid>/cmdline` as a single space-separated string** (a single
    NUL, at the end): splitting on `\\x00` alone sees no argument — a trap verified on this
    machine. We therefore split on both.
    """
    resultats: list[tuple[float, int]] = []
    for proc in glob.glob("/proc/[0-9]*"):
        try:
            raw = Path(proc, "cmdline").read_bytes()
        except Exception:
            continue
        args = re.split(r"[\x00\s]+", raw.decode("utf-8", "ignore"))
        udd = next((a.split("=", 1)[1] for a in args if a.startswith("--user-data-dir=")), "")
        if "agent-browser-chrome" not in udd:
            continue
        fichier = Path(udd) / _DEVTOOLS_PORT_FILE
        try:
            port = int(fichier.read_text().splitlines()[0])
            resultats.append((fichier.stat().st_mtime, port))
        except Exception:
            continue
    return resultats


def _live_devtools_ws(task_id: str = "") -> str:
    """The `webSocketDebuggerUrl` of the browser that shows a real page.

    Two paths, in order:

    1. **the turn's session** (`_session_cdp_url`) — it is bound to the task, hence exact;
    2. **the most recently touched profile** that exposes an `http(s)` page — a safety
       net when the turn's session does not answer.

    An `about:blank` tab does not count as a page: the plugin must never show
    blank while believing it is showing the page.
    """
    if task_id:
        cible = _session_cdp_url(task_id)
        if cible.startswith(("ws://", "wss://")):
            return cible
    scratch = Path(os.environ.get("TMPDIR") or (Path.home() / ".hermes" / "cache" / "scratch"))
    candidats: list[tuple[float, int]] = []
    for racine in {scratch, Path.home() / ".hermes" / "cache" / "scratch",
                   Path.home() / ".hermes" / "profiles"}:
        for profil in _profils_sous(racine):
            fichier = profil / _DEVTOOLS_PORT_FILE
            try:
                port = int(fichier.read_text().splitlines()[0])
            except Exception:
                continue  # dead browser: no file, or unreadable file
            try:
                mtime = fichier.stat().st_mtime
            except OSError:
                mtime = 0.0
            candidats.append((mtime, port))
    # **The path that always works: `/proc`.** The glob above depends on where the
    # browser placed its profile; a gateway launched with another `TMPDIR` looked in the
    # wrong folder and found nothing, while the Chromium was running. We therefore ask the
    # processes themselves where their profile is, which is independent of any variable.
    candidats.extend(_ports_depuis_proc())
    for _mtime, port in sorted(candidats, reverse=True):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=1.0) as r:
                cibles = json.loads(r.read().decode())
        except Exception:
            continue  # dead port: the browser has been reaped
        if not any(
            t.get("type") == "page" and str(t.get("url") or "").startswith("http") for t in cibles
        ):
            continue  # an empty tab is not a page to show
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/version", timeout=2.0) as r:
                ws = str(json.loads(r.read().decode()).get("webSocketDebuggerUrl") or "")
        except Exception:
            continue
        if ws.startswith(("ws://", "wss://")):
            return ws
    return ""


def _port_de(ws_url: str) -> int:
    """The DevTools port contained in a `ws://host:port/...` URL, or `-1`."""
    try:
        reste = ws_url.split("://", 1)[-1]
        hote = reste.partition("/")[0]
        return int(hote.partition(":")[2] or -1)
    except (ValueError, IndexError):
        return -1


#: The `agent-browser` daemon carries its own inactivity timer, and **the plugin's direct CDP
#: traffic is invisible to it**: it stops after ten minutes and takes the Chromium with
#: it. The phone screen then reads "no page is open" whereas the page existed
#: two minutes earlier. The remedy is to talk to it through its own CLI: `get cdp-url` does not
#: navigate, it re-reads the DevTools URL and restarts its timer.
DEMON_SESSION_ENV = "AGENT_BROWSER_SESSION"


def _pids_chrome() -> list[int]:
    """The Chromium browsers launched by the daemon, read from `/proc`."""
    pids: list[int] = []
    for proc in glob.glob("/proc/[0-9]*"):
        try:
            pid = int(proc.rsplit("/", 1)[-1])
            raw = Path(proc, "cmdline").read_bytes()
        except (OSError, ValueError):
            continue
        args = raw.decode("utf-8", "ignore")
        if "chrome-linux64/chrome" in args and "--remote-debugging-port=0" in args:
            pids.append(pid)
    return pids


def _port_du_chrome(pid: int) -> int:
    """The DevTools port of the Chromium `pid`, or `-1`."""
    try:
        raw = Path(f"/proc/{pid}/cmdline").read_bytes().decode("utf-8", "ignore")
    except OSError:
        return -1
    udd = next(
        (a.split("=", 1)[1] for a in raw.replace("\x00", " ").split() if a.startswith("--user-data-dir=")),
        "",
    )
    if not udd:
        return -1
    try:
        return int((Path(udd) / _DEVTOOLS_PORT_FILE).read_text().splitlines()[0])
    except (OSError, ValueError, IndexError):
        return -1


def _env_du_demon(pid: int) -> dict[str, str]:
    """The `AGENT_BROWSER_*` variables of the `pid` process."""
    try:
        raw = Path(f"/proc/{pid}/environ").read_bytes()
    except OSError:
        return {}
    env: dict[str, str] = {}
    for chunk in raw.split(b"\x00"):
        if b"=" not in chunk:
            continue
        cle, _, valeur = chunk.partition(b"=")
        nom = cle.decode("utf-8", "ignore")
        if nom.startswith("AGENT_BROWSER_"):
            env[nom] = valeur.decode("utf-8", "ignore")
    return env


def demon_du_port(port: int) -> dict[str, str]:
    """The `AGENT_BROWSER_*` variables of the daemon that holds the browser on `port`.

    The daemon is the Chromium's **parent**: it is the one that keeps the session and counts its
    idle time. `{"AGENT_BROWSER_SESSION": ...}` when we found it, `{}` otherwise.
    """
    if port <= 0:
        return {}
    for pid in _pids_chrome():
        if _port_du_chrome(pid) != port:
            continue
        try:
            stat = Path(f"/proc/{pid}/stat").read_text()
            ppid = int(stat.rsplit(")", 1)[1].split()[1])
        except (OSError, IndexError, ValueError):
            continue
        env = _env_du_demon(ppid)
        if env.get(DEMON_SESSION_ENV):
            return env
    return {}


def relance_le_minuteur_du_demon(port: int) -> bool:
    """Restart the inactivity timer of the daemon that holds the browser on `port`.

    **This is the cause of "no page is open".** The `agent-browser` daemon counts its
    idle time, and it only sees its own CLI commands: the plugin's CDP traffic
    is totally invisible to it. Without a command now and then, it stops — and the
    Chromium leaves with it — while the phone screen is open and waiting.

    `get cdp-url` does not navigate: it re-reads the DevTools URL and the timer restarts. True when
    the daemon has not already disappeared.
    """
    env = demon_du_port(port)
    session = env.get(DEMON_SESSION_ENV) or ""
    socket_dir = env.get("AGENT_BROWSER_SOCKET_DIR") or ""
    if not session or not socket_dir:
        return False
    cli = os.environ.get("AGENT_BROWSER_CLI") or ""
    if not cli:
        for candidat in (
            Path.home() / ".hermes/tools/agent-browser-0.26.0-linux-x64/bin/agent-browser-linux-x64",
            Path.home() / ".hermes/tools/agent-browser/bin/agent-browser",
        ):
            if candidat.exists():
                cli = str(candidat)
                break
    if not cli or not Path(cli).exists():
        return False
    env_jeu = dict(os.environ)
    env_jeu.update(env)
    # **`AGENT_BROWSER_DAEMON` must not be passed on.** It is the daemon's marker; inherited
    # by the client, the CLI takes itself for a daemon, opens its own loop and never returns
    # control — the call expired after thirty seconds instead of refreshing the timer.
    env_jeu.pop("AGENT_BROWSER_DAEMON", None)
    env_jeu["AGENT_BROWSER_SOCKET_DIR"] = socket_dir
    env_jeu["AGENT_BROWSER_SESSION"] = session
    try:
        subprocess.run(
            [cli, "--session", session, "--json", "get", "cdp-url"],
            env=env_jeu,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except Exception:
        return False
    return True


class _DirectCDP:
    """A synchronous and minimal CDP client, wired directly to the browser's DevTools.

    It speaks the same surface as the Hermes seam (`call`, `is_valid`, `page_session_id`),
    so the rest of the plugin does not have to know how the connection was obtained. Without
    any dependency: the WebSocket handshake is done by hand on a raw socket.
    """

    def __init__(self, ws_url: str, timeout: float = 10.0) -> None:
        self._timeout = timeout
        self.page_session_id = ""
        reste = ws_url.split("://", 1)[-1]
        hote, _, chemin = reste.partition("/")
        self._hote = hote.partition(":")[0]
        self._port = int(hote.partition(":")[2] or 80)
        self._sock = socket.create_connection((self._hote, self._port), timeout=timeout)
        self._sock.settimeout(timeout)
        cle = base64.b64encode(os.urandom(16)).decode()
        self._sock.sendall(
            (
                f"GET /{chemin} HTTP/1.1\r\nHost: {hote}\r\nUpgrade: websocket\r\n"
                f"Connection: Upgrade\r\nSec-WebSocket-Key: {cle}\r\n"
                "Sec-WebSocket-Version: 13\r\n\r\n"
            ).encode()
        )
        entete = b""
        while b"\r\n\r\n" not in entete:
            bloc = self._sock.recv(4096)
            if not bloc:
                raise BrowserUnavailable("deuxième connexion DevTools interrompue")
            entete += bloc
        if b" 101 " not in entete.split(b"\r\n", 1)[0]:
            raise BrowserUnavailable("DevTools n'a pas accepté la poignée WebSocket")
        self._buf = entete.split(b"\r\n\r\n", 1)[1]
        self._prochain = 0
        self._vivant = True

    # -- transport ---------------------------------------------------------------------------

    def _lire(self, n: int) -> bytes:
        while len(self._buf) < n:
            bloc = self._sock.recv(1 << 20)
            if not bloc:
                raise BrowserUnavailable("DevTools a fermé la connexion")
            self._buf += bloc
        sortie, self._buf = self._buf[:n], self._buf[n:]
        return sortie

    def _envoyer(self, texte: str) -> None:
        charge = texte.encode()
        masque = os.urandom(4)
        entete = bytes([0x81])
        n = len(charge)
        if n < 126:
            entete += bytes([0x80 | n])
        elif n < 65536:
            entete += bytes([0x80 | 126]) + struct.pack(">H", n)
        else:
            entete += bytes([0x80 | 127]) + struct.pack(">Q", n)
        melange = bytes(b ^ masque[i % 4] for i, b in enumerate(charge))
        self._sock.sendall(entete + masque + melange)

    def _recevoir(self) -> dict[str, Any]:
        while True:
            tete = self._lire(2)
            op = tete[0] % 16
            n = tete[1] % 128
            if n == 126:
                n = struct.unpack(">H", self._lire(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._lire(8))[0]
            charge = self._lire(n)
            if op == 8:  # close
                raise BrowserUnavailable("DevTools a fermé la connexion")
            if op == 1:
                return json.loads(charge)

    # -- seam surface ----------------------------------------------------------------------

    def call(
        self,
        method: str,
        params: dict[str, Any] | None = None,
        *,
        session_id: str | None = None,
        timeout: float | None = None,
    ) -> dict[str, Any]:
        self._prochain += 1
        message: dict[str, Any] = {"id": self._prochain, "method": method}
        if params:
            message["params"] = params
        if session_id:
            message["sessionId"] = session_id
        if timeout:
            self._sock.settimeout(timeout)
        self._envoyer(json.dumps(message))
        while True:
            reponse = self._recevoir()
            if reponse.get("id") != self._prochain:
                continue  # an event: we are not subscribed to it, we ignore it
            if "error" in reponse:
                raise RuntimeError(f"CDP {method}: {reponse['error']}")
            return reponse

    def is_valid(self) -> bool:
        return self._vivant


class Controller:
    """A CDP client for a turn's browser, with its page sessions cached."""

    def __init__(self, task_id: str) -> None:
        self.task_id = task_id
        self._cdp: Any = None
        self._sessions: dict[str, str] = {}
        #: The DevTools port of the browser we are attached to — **the browser's identity**.
        #: `-1` when we do not know it (the supervisor seam does not say). This value is used
        #: not to confuse "the page is on another process" with "the page is on the
        #: *same* browser, seen by two processes": both attach to the same Chromium, and
        #: one believed the page was "elsewhere" on the other — while the other showed it very
        #: well on the same screen.
        self._port = -1

    # -- attach ------------------------------------------------------------------------------

    def _client(self) -> Any:
        # **The import is deferred AND conditional.** It is only needed when a connection has to
        # be opened: doing it on every call cost an import and, when the plugin folder is
        # the current directory, `tools` referred to the plugin's `tools.py` instead of the
        # Hermes package (`attempted relative import with no known parent package`).
        cdp = self._cdp
        if cdp is not None and getattr(cdp, "is_valid", lambda: False)():
            return cdp
        # Path 1: the Hermes seam. It is the fastest when it answers, but it only
        # sees the browser of the current process and its CDP URL may be stale.
        try:
            from tools.browser_supervisor import SUPERVISOR_REGISTRY

            candidat = SUPERVISOR_REGISTRY.capture(self.task_id, timeout=3)
            # **A supervisor without a real page shows nothing.** It stays attached to a closed
            # Chromium (restart, inactivity) and still answers: `capture` succeeds, then every
            # capture fails and the phone screen switches to "blank" whereas another browser,
            # alive, displays the page. So we check what it sees before keeping it.
            if _shows_real_page(candidat):
                self._cdp = candidat
                self._sessions = {}
                # **The browser's identity, also through the seam.** Without it, two processes
                # attached to the *same* Chromium took themselves for two different browsers —
                # and one believed the page was "elsewhere" on the other.
                self._port = _port_de(str(getattr(candidat, "cdp_url", "") or ""))
                return candidat
            try:
                candidat.close()
            except Exception:
                pass
        except Exception:
            pass
        # Path 2: the agent browser's DevTools, re-read every time. This is the path that
        # survives the browser being relearned — the supervisor, for its part, stays bound to the old
        # port and ends up stopping after five reconnection failures.
        ws = _live_devtools_ws(self.task_id)
        if not ws:
            raise BrowserUnavailable("no browser")
        cdp = self._cdp = _DirectCDP(ws)
        self._sessions = {}
        self._port = _port_de(ws)
        return cdp

    def browser_port(self) -> int:
        """The DevTools port of the attached browser, or `-1` if we do not know it."""
        return self._port

    def _targets(self) -> list[dict[str, Any]]:
        cdp = self._client()
        reply = cdp.call("Target.getTargets", timeout=4)
        return list((reply.get("result") or {}).get("targetInfos") or [])

    def real_pages(self) -> list[dict[str, Any]]:
        """The tabs that really show something (neither blank nor internal page)."""
        return [
            t
            for t in self._targets()
            if t.get("type") == "page" and is_real_page(str(t.get("url") or ""))
        ]

    def _session_url(self, cdp: Any, session: str) -> str:
        """The URL this CDP session shows, or `""` if the question fails."""
        try:
            history = cdp.call("Page.getNavigationHistory", session_id=session, timeout=3)["result"]
            entries = history.get("entries") or []
            if not entries:
                return ""
            index = int(history.get("currentIndex", len(entries) - 1))
            return str(entries[index].get("url") or "")
        except Exception:
            return ""

    def current_url(self) -> str:
        """The address the human sees in the bar, or `""` if there is no real page."""
        try:
            cdp = self._client()
            return self._session_url(cdp, self.page())
        except Exception:
            return ""

    def page_state(self) -> str:
        """`ok`, `blank` (a browser, but nothing in it) or `gone` (no browser at all).

        The tool and the screen need to tell the difference: `blank` means "reopen the page
        before calling me", `gone` means "this turn has no browser".
        """
        try:
            cdp = self._client()
        except Exception:
            return "gone"
        try:
            if self.real_pages():
                return "ok"
            # No real tab: the browser may be fresh (blank) or already reaped.
            return "blank" if self._targets() else "gone"
        except Exception:
            return "gone"

    def page(self) -> str:
        """The CDP session of the page the agent really looks at.

        **Order of preference, and it matters**: the session the supervisor opened — it is
        the one `browser_navigate` drives, hence the agent's — then the last real tab.
        If neither shows a real page, we raise: this is what keeps the phone
        screen from displaying blank while believing it is showing the page.
        """
        cdp = self._client()
        superviseur = str(getattr(cdp, "page_session_id", "") or "")
        if superviseur and is_real_page(self._session_url(cdp, superviseur)):
            return superviseur
        reelles = self.real_pages()
        if not reelles:
            raise BrowserUnavailable("blank")
        target_id = reelles[-1]["targetId"]
        if target_id not in self._sessions:
            reply = cdp.call(
                "Target.attachToTarget", {"targetId": target_id, "flatten": True}, timeout=4
            )
            self._sessions[target_id] = reply["result"]["sessionId"]
        return self._sessions[target_id]

    # -- viewport -----------------------------------------------------------------------------

    def use_phone_viewport(self, width_css: int | None = None, height_css: int | None = None,
                           dpr: float | None = None) -> tuple[int, int]:
        """Set a phone viewport and return the resulting CSS size.

        **The size comes from the screen that is watching, not from a constant.** The fixed template
        (`390×844` at DPR 2) captured **780 px wide**; the app stretched it to the real
        phone width (~1080 px), i.e. a 1.4× upscale — hence the soft image, the fuzzy-bold
        text and the fields one cannot make out. By setting the CSS size so that
        `css × dpr` is worth **exactly** the screen's pixels, the capture arrives at the right
        resolution: no more upscaling, hence no more blur.

        `clip.scale` stays at 1.0: beyond that, `Page.captureScreenshot` returns a mosaic. It is the
        CSS size that carries the sharpness, never the capture scale.
        """
        largeur = width_css if width_css and width_css >= 200 else MOBILE_WIDTH
        hauteur = height_css if height_css and height_css >= 200 else MOBILE_HEIGHT
        facteur = dpr if dpr and 1.0 <= dpr <= 4.0 else MOBILE_DPR
        cdp = self._client()
        session = self.page()
        cdp.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": largeur,
                "height": hauteur,
                "deviceScaleFactor": facteur,
                "mobile": True,
            },
            session_id=session,
            timeout=4,
        )
        cdp.call(
            "Emulation.setUserAgentOverride",
            {"userAgent": MOBILE_UA},
            session_id=session,
            timeout=4,
        )
        return largeur, hauteur

    def clear_viewport(self) -> None:
        """Give the page back its desktop shape, and verify it.

        **`clearDeviceMetricsOverride` is not enough on this Chromium.** Observed on the 145: after
        the call, `Page.getLayoutMetrics` still returns 390×844, and the agent works on a narrow
        page believing it has got it back. We therefore remeasure, and if the page is still in phone
        shape, we set an explicit desktop size (window width, read from the
        browser). Setting is better than only observing.
        """
        cdp = self._client()
        session = self.page()
        cdp.call("Emulation.clearDeviceMetricsOverride", session_id=session, timeout=4)
        cdp.call("Emulation.setUserAgentOverride", {"userAgent": ""}, session_id=session, timeout=4)
        try:
            metrics = cdp.call("Page.getLayoutMetrics", session_id=session, timeout=4)["result"]
            view = metrics.get("cssVisualViewport") or metrics.get("layoutViewport") or {}
            largeur = int(view.get("clientWidth") or 0)
        except Exception:
            return
        if largeur >= 700:
            return  # the page has indeed regained its shape: nothing to fix
        largeur, hauteur = self._window_size(cdp)
        try:
            cdp.call(
                "Emulation.setDeviceMetricsOverride",
                {
                    "width": largeur,
                    "height": hauteur,
                    "deviceScaleFactor": 1,
                    "mobile": False,
                },
                session_id=session,
                timeout=4,
            )
        except Exception:
            pass

    def _window_size(self, cdp: Any) -> tuple[int, int]:
        """The browser window size, or a reasonable desktop as fallback."""
        try:
            fenetre = cdp.call("Browser.getWindowForTarget", timeout=3)["result"].get("bounds") or {}
            largeur = int(fenetre.get("width") or 0)
            hauteur = int(fenetre.get("height") or 0)
            if largeur > 700 and hauteur > 400:
                # The browser toolbar eats about sixty pixels in height.
                return largeur - 15, max(hauteur - 140, 400)
        except Exception:
            pass
        return 1265, 577

    # -- frame --------------------------------------------------------------------------------

    def alive(self) -> bool:
        """Does the page still answer? A CDP round trip, without capture.

        **The Hermes browser dies of inactivity** (`browser.inactivity_timeout`, 120 s). A
        takeover blocks the turn without calling a tool: the page can therefore disappear while
        the human logs in. Without this probe, the tool waited its fifteen minutes in front of a
        black screen, and the app showed "waiting" on a process without a page.
        """
        try:
            cdp = self._client()
            cdp.call("Page.getLayoutMetrics", session_id=self.page(), timeout=3)
            return True
        except Exception:
            return False

    def repaint_viewport(self, width_css: int | None = None, height_css: int | None = None,
                         dpr: float | None = None) -> tuple[int, int]:
        """Flatten the rendering then give the page the thumb's shape.

        **The only sequence that yields a sharp, non-mosaicked frame**, measured on jeuxvideo.com
        and Instagram: `clearDeviceMetricsOverride` → `use_phone_viewport` → `Page.reload`. Without
        the prior clearing, an emulation already in place (set by another CDP client, or
        left by a previous screen) overlaps and `Page.captureScreenshot` returns the page
        **repeated as a mosaic** — a false image, not merely a soft one.
        Cf. `frames/after_reload.jpg` (sharp) against `frames/etat_sabote.jpg` (mosaic).
        """
        cdp = self._client()
        session = self.page()
        try:
            cdp.call("Emulation.clearDeviceMetricsOverride", session_id=session, timeout=4)
        except Exception:
            pass  # nothing to clear: we set it anyway
        taille = self.use_phone_viewport(width_css, height_css, dpr)
        try:
            cdp.call("Page.reload", {}, session_id=session, timeout=8)
        except Exception:
            pass  # a refused reload must not prevent the takeover
        return taille

    def frame(self, scale: float, quality: int = 55) -> dict[str, Any] | None:
        """A JPEG capture of the page, ready for the wire.

        `quality` comes from the caller: a readable image when the human types a password does
        not cost the same as a monitoring thumbnail (see `server.WATCH_QUALITY`).
        """
        cdp = self._client()
        session = self.page()
        metrics = cdp.call("Page.getLayoutMetrics", session_id=session, timeout=4)["result"]
        view = metrics.get("cssVisualViewport") or metrics.get("layoutViewport") or {}
        width = int(view.get("clientWidth", MOBILE_WIDTH))
        height = int(view.get("clientHeight", MOBILE_HEIGHT))
        # **No emulation tweak here — ever.** Changing `deviceScaleFactor` (or the width)
        # just before `Page.captureScreenshot` makes the page render as a **2×2 mosaic**, even with
        # 400 ms apart: measured, cf. `frames/etat_sabote.jpg`. The DPR is set at lease time
        # (`server._pose_viewport` → `use_phone_viewport`), not at capture. Accepted consequence:
        # if another CDP client brings it back to 1, the frame drops to 375 px — soft, but **correct**.
        # A mosaic is not a lower-quality image, it is a false image.
        if scale >= 1.0:
            # Without `clip`: the capture comes out in physical pixels (css × DPR), without the scrollbar
            # offset that was cropping 15 px off the width.
            capture: dict[str, Any] = {
                "format": "jpeg",
                "quality": int(quality),
                "optimizeForSpeed": False,
            }
        else:
            capture = {
                "format": "jpeg",
                "quality": int(quality),
                "optimizeForSpeed": False,
                "clip": {
                    "x": view.get("pageX", 0),
                    "y": view.get("pageY", 0),
                    "width": width,
                    "height": height,
                    "scale": scale,
                },
            }
        shot = cdp.call("Page.captureScreenshot", capture, session_id=session, timeout=6)["result"]
        history = cdp.call("Page.getNavigationHistory", session_id=session, timeout=4)["result"]
        entry = history["entries"][history["currentIndex"]]
        url = str(entry.get("url") or "")
        if not is_real_page(url):
            # **Never blank presented as the page.** A capture of an empty tab weighs 18 KB
            # of identical bytes, three times per second: the screen looks alive, and it shows nothing.
            raise BrowserUnavailable("blank")
        return {
            "data": shot["data"],
            "url": url,
            "title": entry.get("title", ""),
            "width": int(width * scale),
            "height": int(height * scale),
            "css": [width, height],
        }

    # -- input -------------------------------------------------------------------------------

    def input(self, action: str, frame: dict[str, Any], css: tuple[int, int]) -> bool:
        """Replay a gesture. The coordinates arrive as fractions of the image."""
        cdp = self._client()
        session = self.page()
        width, height = css
        x = min(max(float(frame.get("x") or 0.0), 0.0), 1.0) * width
        y = min(max(float(frame.get("y") or 0.0), 0.0), 1.0) * height
        button = {"x": x, "y": y, "button": "left", "clickCount": 1}
        if action == "click":
            cdp.call("Input.dispatchMouseEvent", {"type": "mouseMoved", "x": x, "y": y},
                     session_id=session, timeout=4)
            cdp.call("Input.dispatchMouseEvent", {"type": "mousePressed", **button},
                     session_id=session, timeout=4)
            cdp.call("Input.dispatchMouseEvent", {"type": "mouseReleased", **button},
                     session_id=session, timeout=4)
            return True
        if action == "scroll":
            cdp.call(
                "Input.dispatchMouseEvent",
                {
                    "type": "mouseWheel",
                    "x": x,
                    "y": y,
                    "deltaX": 0,
                    "deltaY": float(frame.get("dy") or 0.0) * height,
                },
                session_id=session,
                timeout=4,
            )
            return True
        if action == "type":
            text = str(frame.get("text") or "")[:2000]
            if not text:
                return False
            cdp.call("Input.insertText", {"text": text}, session_id=session, timeout=4)
            return True
        if action == "key":
            key = _KEYS.get(str(frame.get("key") or ""))
            if key is None:
                return False
            name, code, vk, text = key
            down = {"type": "keyDown", "key": name, "code": code, "windowsVirtualKeyCode": vk}
            if text:
                down["text"] = text
            cdp.call("Input.dispatchKeyEvent", down, session_id=session, timeout=4)
            cdp.call(
                "Input.dispatchKeyEvent",
                {"type": "keyUp", "key": name, "code": code, "windowsVirtualKeyCode": vk},
                session_id=session,
                timeout=4,
            )
            return True
        return False

    # -- form ---------------------------------------------------------------------------

    def login_form_facts(self) -> dict[str, Any]:
        """The page's host and the presence of a password field — nothing more."""
        cdp = self._client()
        session = self.page()
        expression = (
            "(() => ({host: location.host,"
            " hasPassword: !!document.querySelector('form input[type=password]'),"
            " url: location.href}))()"
        )
        reply = cdp.call(
            "Runtime.evaluate",
            {"expression": expression, "returnByValue": True},
            session_id=session,
            timeout=4,
        )
        value = ((reply.get("result") or {}).get("result") or {}).get("value")
        if not isinstance(value, dict):
            return {}
        return {
            "host": str(value.get("host") or ""),
            "hasPassword": bool(value.get("hasPassword")),
            "url": str(value.get("url") or ""),
        }


def decode_frame(payload: str) -> bytes:
    """A base64 frame → JPEG bytes (useful for tests and debugging)."""
    return base64.b64decode(payload)


def dump_facts(facts: dict[str, Any]) -> str:
    """The facts of a form, never a field's value."""
    return json.dumps(facts, ensure_ascii=False, sort_keys=True)
