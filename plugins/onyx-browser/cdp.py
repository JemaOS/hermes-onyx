"""Le seul endroit qui parle à Chromium : capture d'une trame, et rejeu d'un geste.

On passe par le seam que Hermes réserve aux plugins (`SUPERVISOR_REGISTRY.capture`), avec le
`task_id` des outils `browser_*` du tour. Tout le reste du plugin ignore CDP.

Trois choses que ce module fait et que le reste ne doit pas refaire :

- **viser la bonne page** : le superviseur est épinglé au premier onglet, souvent une page vide ;
  on prend la dernière page web réelle et on s'y attache (`Target.attachToTarget`, sessions mises
  en cache) ;
- **poser un viewport de téléphone** avant la frappe (§2.1 du plan), et le **rendre** à la fin ;
- **détecter le formulaire de connexion** au moment de rendre la main — l'hôte et la présence d'un
  champ `type=password` — pour que l'app puisse proposer d'enregistrer, sans jamais deviner.
"""

from __future__ import annotations

import base64
import json
import os
import socket
import struct
import time
import urllib.request
from pathlib import Path
from typing import Any

#: Un Chromium Android crédible : c'est lui que le site sert quand on demande sa version mobile.
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
    """Un onglet qui montre quelque chose : ce n'est ni une page interne, ni du blanc.

    **La distinction n'est pas cosmétique.** Hermes ferme un navigateur resté deux minutes sans
    outil (`browser.inactivity_timeout`) et en rouvre un, vide, au tour suivant. Une prise en main
    demandée sur ce navigateur neuf montrait un écran blanc pendant quinze minutes : l'humain
    croyait que la page n'arrivait pas, et l'agent attendait un rendez-vous impossible.
    """
    return bool(url) and not str(url).startswith(_SKIP_PREFIXES)


class BrowserUnavailable(RuntimeError):
    """Aucun navigateur d'agent vivant pour ce tour."""


#: Le fichier où Chromium écrit le port DevTools qu'il a choisi, à la racine de son
#: `--user-data-dir`. C'est la seule source qui survit au réapprentissage d'un navigateur.
_DEVTOOLS_PORT_FILE = "DevToolsActivePort"


def _session_cdp_url(task_id: str) -> str:
    """L'URL DevTools **de la session de ce tour**, ou ``""``.

    C'est la voie canonique de Hermes : la commande `agent-browser get cdp-url` sur la session
    du tour. Elle est liée à la tâche, donc elle ne peut pas renvoyer le navigateur d'une autre
    session — ce qui compte, puisque plusieurs tours tournent en parallèle sur cette machine.

    **Pourquoi elle est nécessaire :** le seam `SUPERVISOR_REGISTRY` ne résout que dans le
    processus qui a lancé le navigateur, et son URL CDP devient périmée dès que ce navigateur
    est récolté puis rouvert sur un autre port. Le superviseur essayait alors de se reconnecter
    à un port mort, s'arrêtait après cinq échecs, et le plugin en concluait « pas de
    navigateur » — alors que la page existait et que le téléphone ne montrait qu'un écran vide.

    Cette commande relance le navigateur au besoin, suit un réapprentissage, et rafraîchit le
    minuteur du démon : le trafic CDP direct, lui, est invisible pour lui.
    """
    try:
        from tools.browser_tool_session import _run_browser_command
    except Exception:
        return ""
    try:
        res = _run_browser_command(task_id, "get", ["cdp-url"], timeout=20)
    except Exception:
        return ""
    if not (res or {}).get("success"):
        return ""
    return str(((res or {}).get("data") or {}).get("cdpUrl") or "")


def _live_devtools_ws(task_id: str = "") -> str:
    """Le `webSocketDebuggerUrl` du navigateur qui montre une vraie page.

    Deux voies, dans l'ordre :

    1. **la session du tour** (`_session_cdp_url`) — elle est liée à la tâche, donc exacte ;
    2. **le profil le plus récemment touché** qui expose une page `http(s)` — un filet de
       sécurité quand la session du tour ne répond pas.

    Un onglet `about:blank` ne compte pas comme une page : le plugin ne doit jamais montrer
    du blanc en croyant montrer la page.
    """
    if task_id:
        cible = _session_cdp_url(task_id)
        if cible.startswith(("ws://", "wss://")):
            return cible
    scratch = Path(os.environ.get("TMPDIR") or (Path.home() / ".hermes" / "cache" / "scratch"))
    candidats: list[tuple[float, int]] = []
    for profil in scratch.glob("agent-browser-chrome-*"):
        fichier = profil / _DEVTOOLS_PORT_FILE
        try:
            port = int(fichier.read_text().splitlines()[0])
        except Exception:
            continue  # navigateur mort : pas de fichier, ou fichier illisible
        try:
            mtime = fichier.stat().st_mtime
        except OSError:
            mtime = 0.0
        candidats.append((mtime, port))
    for _mtime, port in sorted(candidats, reverse=True):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=1.0) as r:
                cibles = json.loads(r.read().decode())
        except Exception:
            continue  # port mort : le navigateur a été récolté
        if not any(
            t.get("type") == "page" and str(t.get("url") or "").startswith("http") for t in cibles
        ):
            continue  # un onglet vide n'est pas une page à montrer
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/version", timeout=2.0) as r:
                ws = str(json.loads(r.read().decode()).get("webSocketDebuggerUrl") or "")
        except Exception:
            continue
        if ws.startswith(("ws://", "wss://")):
            return ws
    return ""


class _DirectCDP:
    """Un client CDP synchrone et minimal, branché en direct sur le DevTools du navigateur.

    Il parle la même surface que le seam de Hermes (`call`, `is_valid`, `page_session_id`),
    donc le reste du plugin n'a pas à savoir comment la connexion a été obtenue. Sans
    dépendance : la poignée WebSocket est faite à la main sur une socket nue.
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
            if op == 8:  # fermeture
                raise BrowserUnavailable("DevTools a fermé la connexion")
            if op == 1:
                return json.loads(charge)

    # -- surface du seam ----------------------------------------------------------------------

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
                continue  # un événement : on n'y est pas abonné, on l'ignore
            if "error" in reponse:
                raise RuntimeError(f"CDP {method}: {reponse['error']}")
            return reponse

    def is_valid(self) -> bool:
        return self._vivant


class Controller:
    """Un client CDP pour le navigateur d'un tour, avec ses sessions de page en cache."""

    def __init__(self, task_id: str) -> None:
        self.task_id = task_id
        self._cdp: Any = None
        self._sessions: dict[str, str] = {}

    # -- attache ------------------------------------------------------------------------------

    def _client(self) -> Any:
        # **L'import est différé ET conditionnel.** Il ne sert que lorsqu'il faut ouvrir une
        # connexion : le faire à chaque appel coûtait un import et, quand le dossier du plugin est
        # le répertoire courant, `tools` désignait le `tools.py` du plugin au lieu du paquet de
        # Hermes (`attempted relative import with no known parent package`).
        cdp = self._cdp
        if cdp is not None and getattr(cdp, "is_valid", lambda: False)():
            return cdp
        # Voie 1 : le seam de Hermes. Elle est la plus rapide quand elle répond, mais elle ne
        # voit que le navigateur du processus courant et son URL CDP peut être périmée.
        try:
            from tools.browser_supervisor import SUPERVISOR_REGISTRY

            cdp = self._cdp = SUPERVISOR_REGISTRY.capture(self.task_id, timeout=3)
            self._sessions = {}
            return cdp
        except Exception:
            pass
        # Voie 2 : le DevTools du navigateur d'agent, relu à chaque fois. C'est cette voie qui
        # survit au réapprentissage du navigateur — le superviseur, lui, reste lié à l'ancien
        # port et finit par s'arrêter après cinq échecs de reconnexion.
        ws = _live_devtools_ws(self.task_id)
        if not ws:
            raise BrowserUnavailable("no browser")
        cdp = self._cdp = _DirectCDP(ws)
        self._sessions = {}
        return cdp

    def _targets(self) -> list[dict[str, Any]]:
        cdp = self._client()
        reply = cdp.call("Target.getTargets", timeout=4)
        return list((reply.get("result") or {}).get("targetInfos") or [])

    def real_pages(self) -> list[dict[str, Any]]:
        """Les onglets qui montrent réellement quelque chose (ni blanc, ni page interne)."""
        return [
            t
            for t in self._targets()
            if t.get("type") == "page" and is_real_page(str(t.get("url") or ""))
        ]

    def _session_url(self, cdp: Any, session: str) -> str:
        """L'URL que cette session CDP montre, ou `""` si la question échoue."""
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
        """L'adresse que l'humain voit dans la barre, ou `""` s'il n'y a pas de page réelle."""
        try:
            cdp = self._client()
            return self._session_url(cdp, self.page())
        except Exception:
            return ""

    def page_state(self) -> str:
        """`ok`, `blank` (un navigateur, mais rien dedans) ou `gone` (aucun navigateur).

        L'outil et l'écran ont besoin de faire la différence : `blank` veut dire « rouvre la page
        avant de m'appeler », `gone` veut dire « ce tour n'a pas de navigateur ».
        """
        try:
            cdp = self._client()
        except Exception:
            return "gone"
        try:
            if self.real_pages():
                return "ok"
            # Aucun onglet réel : le navigateur peut être neuf (blanc) ou déjà récolté.
            return "blank" if self._targets() else "gone"
        except Exception:
            return "gone"

    def page(self) -> str:
        """La session CDP de la page que l'agent regarde vraiment.

        **Ordre de préférence, et il compte** : la session que le superviseur a ouverte — c'est
        celle que `browser_navigate` conduit, donc celle de l'agent — puis le dernier onglet réel.
        Si aucune des deux ne montre une vraie page, on lève : c'est ce qui empêche l'écran du
        téléphone d'afficher du blanc en croyant montrer la page.
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

    def use_phone_viewport(self) -> tuple[int, int]:
        """Pose un viewport de téléphone et rend la taille CSS obtenue."""
        cdp = self._client()
        session = self.page()
        cdp.call(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": MOBILE_WIDTH,
                "height": MOBILE_HEIGHT,
                "deviceScaleFactor": MOBILE_DPR,
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
        return MOBILE_WIDTH, MOBILE_HEIGHT

    def clear_viewport(self) -> None:
        cdp = self._client()
        session = self.page()
        cdp.call("Emulation.clearDeviceMetricsOverride", session_id=session, timeout=4)
        cdp.call("Emulation.setUserAgentOverride", {"userAgent": ""}, session_id=session, timeout=4)

    # -- trame --------------------------------------------------------------------------------

    def alive(self) -> bool:
        """La page répond-elle encore ? Un aller-retour CDP, sans capture.

        **Le navigateur de Hermes meurt d'inactivité** (`browser.inactivity_timeout`, 120 s). Une
        prise en main bloque le tour sans appeler d'outil : la page peut donc disparaître pendant
        que l'humain se connecte. Sans cette sonde, l'outil attendait ses quinze minutes devant un
        écran noir, et l'app affichait « en attente » sur un processus sans page.
        """
        try:
            cdp = self._client()
            cdp.call("Page.getLayoutMetrics", session_id=self.page(), timeout=3)
            return True
        except Exception:
            return False

    def frame(self, scale: float) -> dict[str, Any] | None:
        """Une capture JPEG de la page, prête pour le fil."""
        cdp = self._client()
        session = self.page()
        metrics = cdp.call("Page.getLayoutMetrics", session_id=session, timeout=4)["result"]
        view = metrics.get("cssVisualViewport") or metrics.get("layoutViewport") or {}
        width = int(view.get("clientWidth", MOBILE_WIDTH))
        height = int(view.get("clientHeight", MOBILE_HEIGHT))
        shot = cdp.call(
            "Page.captureScreenshot",
            {
                "format": "jpeg",
                "quality": 55,
                "optimizeForSpeed": True,
                "clip": {
                    "x": view.get("pageX", 0),
                    "y": view.get("pageY", 0),
                    "width": width,
                    "height": height,
                    "scale": scale,
                },
            },
            session_id=session,
            timeout=6,
        )["result"]
        history = cdp.call("Page.getNavigationHistory", session_id=session, timeout=4)["result"]
        entry = history["entries"][history["currentIndex"]]
        url = str(entry.get("url") or "")
        if not is_real_page(url):
            # **Jamais de blanc présenté comme la page.** Une capture d'un onglet vide pèse 18 ko
            # identiques, trois fois par seconde : l'écran a l'air vivant, et il ne montre rien.
            raise BrowserUnavailable("blank")
        return {
            "data": shot["data"],
            "url": url,
            "title": entry.get("title", ""),
            "width": int(width * scale),
            "height": int(height * scale),
            "css": [width, height],
        }

    # -- saisie -------------------------------------------------------------------------------

    def input(self, action: str, frame: dict[str, Any], css: tuple[int, int]) -> bool:
        """Rejoue un geste. Les coordonnées arrivent en fractions de l'image."""
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

    # -- formulaire ---------------------------------------------------------------------------

    def login_form_facts(self) -> dict[str, Any]:
        """L'hôte de la page et la présence d'un champ mot de passe — rien de plus."""
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
    """Une trame base64 → octets JPEG (utile aux tests et au débogage)."""
    return base64.b64decode(payload)


def dump_facts(facts: dict[str, Any]) -> str:
    """Les faits d'un formulaire, sans jamais la valeur d'un champ."""
    return json.dumps(facts, ensure_ascii=False, sort_keys=True)
