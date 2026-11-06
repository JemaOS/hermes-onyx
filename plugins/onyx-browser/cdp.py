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


def _session_keys(task_id: str) -> list[str]:
    """Les clés de session sous lesquelles ce tour peut avoir un navigateur.

    Le même tour apparaît sous plusieurs formes : la clé nue (`api-xxx`), la clé de profil
    (`api-xxx@/home/john/.hermes`), et la clé mémorisée comme « dernière session active »
    quand une navigation a eu lieu. On les essaie dans cet ordre, en ne gardant que celles
    qui **existent déjà** : interroger une clé absente ferait lancer un navigateur neuf, et
    l'écran du téléphone afficherait alors une page blanche au lieu de la bonne.
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
    """L'URL DevTools **de la session de ce tour**, ou ``""``.

    C'est la voie canonique de Hermes : la commande `agent-browser get cdp-url` sur la session
    du tour. Elle est liée à la tâche, donc elle ne peut pas renvoyer le navigateur d'une
    autre session — ce qui compte, puisque plusieurs tours tournent en parallèle sur cette
    machine.

    **Pourquoi elle est nécessaire :** le seam `SUPERVISOR_REGISTRY` ne résout que dans le
    processus qui a lancé le navigateur, et son URL CDP devient périmée dès que ce navigateur
    est récolté puis rouvert sur un autre port. Le superviseur essayait alors de se reconnecter
    à un port mort, s'arrêtait après cinq échecs, et le plugin en concluait « pas de
    navigateur » — alors que la page existait et que le téléphone ne montrait qu'un écran vide.

    Cette commande rafraîchit aussi le minuteur du démon : le trafic CDP direct, lui, est
    invisible pour lui.
    """
    try:
        from tools.browser_tool import _active_sessions
        from tools.browser_tool_session import _run_browser_command
    except Exception:
        return ""
    for cle in _session_keys(task_id):
        # **Seulement une session qui existe déjà** : une clé absente ferait lancer un
        # navigateur neuf, et l'écran du téléphone montrerait du blanc au lieu de la page.
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
    """Ce client CDP voit-il au moins une page web réelle ?

    Sert à écarter un superviseur encore « vivant » mais accroché à un Chromium fermé : sans ce
    filtre, `capture` réussit, puis chaque trame échoue, et l'écran du téléphone affiche « blank »
    alors qu'un autre navigateur, lui, montre la page.
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
    """Les profils `agent-browser-chrome-*` directement sous `racine` et un niveau plus bas.

    Deux formes coexistent sur cette machine : `~/.hermes/cache/scratch/agent-browser-chrome-*`
    et `~/.hermes/profiles/<nom>/cache/scratch/agent-browser-chrome-*`. Un seul niveau de descente
    suffit et garde le scan borné.
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
    """(fraîcheur, port DevTools) des Chromium lancés par l'agent, lus dans `/proc`.

    **Chrome réécrit `/proc/<pid>/cmdline` en une seule chaîne séparée par des espaces** (un seul
    NUL, à la fin) : couper sur `\\x00` seul ne voit aucun argument — piège vérifié sur cette
    machine. On découpe donc sur les deux.
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
    for racine in {scratch, Path.home() / ".hermes" / "cache" / "scratch",
                   Path.home() / ".hermes" / "profiles"}:
        for profil in _profils_sous(racine):
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
    # **La voie qui marche toujours : `/proc`.** Le glob ci-dessus dépend de l'endroit où le
    # navigateur a posé son profil ; un gateway lancé avec un autre `TMPDIR` cherchait dans le
    # mauvais dossier et ne trouvait rien, alors que le Chromium tournait. On demande donc aux
    # processus eux-mêmes où est leur profil, ce qui est indépendant de toute variable.
    candidats.extend(_ports_depuis_proc())
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


def _port_de(ws_url: str) -> int:
    """Le port DevTools contenu dans une URL `ws://host:port/...`, ou `-1`."""
    try:
        reste = ws_url.split("://", 1)[-1]
        hote = reste.partition("/")[0]
        return int(hote.partition(":")[2] or -1)
    except (ValueError, IndexError):
        return -1


#: Le démon `agent-browser` porte son propre minuteur d'inactivité, et **le trafic CDP direct du
#: plugin lui est invisible** : il s'arrête au bout de dix minutes et emporte le Chromium avec
#: lui. L'écran du téléphone lit alors « aucune page n'est ouverte » alors que la page existait
#: deux minutes plus tôt. La parade est de lui parler par sa propre CLI : `get cdp-url` ne
#: navigue pas, elle relit l'URL DevTools et relance son minuteur.
DEMON_SESSION_ENV = "AGENT_BROWSER_SESSION"


def _pids_chrome() -> list[int]:
    """Les Chromium lancés par le démon, lus dans `/proc`."""
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
    """Le port DevTools du Chromium `pid`, ou `-1`."""
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
    """Les variables `AGENT_BROWSER_*` du processus `pid`."""
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
    """Les variables `AGENT_BROWSER_*` du démon qui tient le navigateur sur `port`.

    Le démon est le **père** du Chromium : c'est lui qui garde la session et qui compte son
    temps d'inactivité. `{"AGENT_BROWSER_SESSION": ...}` quand on l'a trouvé, `{}` sinon.
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
    """Relance le minuteur d'inactivité du démon qui tient le navigateur sur `port`.

    **C'est la cause du « aucune page n'est ouverte ».** Le démon `agent-browser` compte son
    temps d'inactivité, et il ne voit que ses propres commandes CLI : le trafic CDP du plugin
    lui est totalement invisible. Sans une commande de temps en temps, il s'arrête — et le
    Chromium part avec lui — alors que l'écran du téléphone est ouvert et attend.

    `get cdp-url` ne navigue pas : elle relit l'URL DevTools et le minuteur repart. Vrai quand
    le démon n'a pas déjà disparu.
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
    # **`AGENT_BROWSER_DAEMON` ne doit pas être transmis.** C'est le marqueur du démon ; hérité
    # par le client, la CLI se prend pour un démon, ouvre sa propre boucle et ne rend jamais la
    # main — l'appel expirait au bout de trente secondes au lieu de rafraîchir le minuteur.
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
        #: Le port DevTools du navigateur auquel on est accroché — **l'identité du navigateur**.
        #: `-1` quand on ne le sait pas (le seam du superviseur ne le dit pas). Cette valeur sert
        #: à ne pas confondre « la page est chez un autre processus » avec « la page est chez le
        #: *même* navigateur, vu par deux processus » : les deux attachent au même Chromium, et
        #: l'un croyait la page « ailleurs » chez l'autre — pendant que l'autre la montrait très
        #: bien au même écran.
        self._port = -1

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

            candidat = SUPERVISOR_REGISTRY.capture(self.task_id, timeout=3)
            # **Un superviseur sans page réelle ne montre rien.** Il reste accroché à un Chromium
            # fermé (redémarrage, inactivité) et répond encore : `capture` réussit, puis toute
            # capture échoue et l'écran du téléphone passe à « blank » alors qu'un autre navigateur,
            # vivant, affiche la page. On vérifie donc ce qu'il voit avant de le garder.
            if _shows_real_page(candidat):
                self._cdp = candidat
                self._sessions = {}
                # **L'identité du navigateur, aussi par le seam.** Sans elle, deux processus
                # attachés au *même* Chromium se prenaient pour deux navigateurs différents —
                # et l'un croyait la page « ailleurs » chez l'autre.
                self._port = _port_de(str(getattr(candidat, "cdp_url", "") or ""))
                return candidat
            try:
                candidat.close()
            except Exception:
                pass
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
        self._port = _port_de(ws)
        return cdp

    def browser_port(self) -> int:
        """Le port DevTools du navigateur accroché, ou `-1` si on ne le sait pas."""
        return self._port

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

    def use_phone_viewport(self, width_css: int | None = None, height_css: int | None = None,
                           dpr: float | None = None) -> tuple[int, int]:
        """Pose un viewport de téléphone et rend la taille CSS obtenue.

        **La taille vient de l'écran qui regarde, pas d'une constante.** Le gabarit figé
        (`390×844` en DPR 2) capturait **780 px de large** ; l'app l'étirait à la largeur réelle
        du téléphone (~1080 px), soit un agrandissement de 1,4× — d'où l'image molle, le texte
        gras-flou et les champs qu'on ne distingue pas. En posant la taille CSS telle que
        `css × dpr` vaille **exactement** les pixels de l'écran, la capture arrive à la bonne
        définition : plus d'agrandissement, donc plus de flou.

        `clip.scale` reste à 1.0 : au-delà, `Page.captureScreenshot` rend une mosaïque. C'est la
        taille CSS qui porte la finesse, jamais l'échelle de capture.
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
        """Rend à la page sa forme de bureau, et le vérifie.

        **`clearDeviceMetricsOverride` ne suffit pas sur ce Chromium.** Constaté sur le 145 : après
        l'appel, `Page.getLayoutMetrics` rend toujours 390×844, et l'agent travaille sur une page
        étroite en croyant l'avoir retrouvée. On remesure donc, et si la page est restée en forme
        de téléphone, on repose une taille de bureau explicite (largeur de la fenêtre, lue au
        navigateur). Poser vaut mieux que constater.
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
            return  # la page a bien repris sa forme : rien à corriger
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
        """La taille de la fenêtre du navigateur, ou un bureau raisonnable en repli."""
        try:
            fenetre = cdp.call("Browser.getWindowForTarget", timeout=3)["result"].get("bounds") or {}
            largeur = int(fenetre.get("width") or 0)
            hauteur = int(fenetre.get("height") or 0)
            if largeur > 700 and hauteur > 400:
                # La barre d'outils du navigateur mange une soixantaine de pixels en hauteur.
                return largeur - 15, max(hauteur - 140, 400)
        except Exception:
            pass
        return 1265, 577

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

    def repaint_viewport(self, width_css: int | None = None, height_css: int | None = None,
                         dpr: float | None = None) -> tuple[int, int]:
        """Remet le rendu à plat puis donne à la page la forme du pouce.

        **La seule séquence qui donne une trame nette et non mosaïquée**, mesurée sur jeuxvideo.com
        et Instagram : `clearDeviceMetricsOverride` → `use_phone_viewport` → `Page.reload`. Sans
        l'effacement préalable, une émulation déjà en place (posée par un autre client CDP, ou
        laissée par un écran précédent) se superpose et `Page.captureScreenshot` rend la page
        **répétée en mosaïque** — une image fausse, pas seulement une image molle.
        Cf. `frames/after_reload.jpg` (nette) contre `frames/etat_sabote.jpg` (mosaïque).
        """
        cdp = self._client()
        session = self.page()
        try:
            cdp.call("Emulation.clearDeviceMetricsOverride", session_id=session, timeout=4)
        except Exception:
            pass  # rien à effacer : on pose quand même
        taille = self.use_phone_viewport(width_css, height_css, dpr)
        try:
            cdp.call("Page.reload", {}, session_id=session, timeout=8)
        except Exception:
            pass  # un rechargement refusé ne doit pas empêcher la prise en main
        return taille

    def frame(self, scale: float, quality: int = 55) -> dict[str, Any] | None:
        """Une capture JPEG de la page, prête pour le fil.

        `quality` vient de l'appelant : une image lisible quand l'humain tape un mot de passe n'a
        pas le même prix qu'une vignette de surveillance (voir `server.WATCH_QUALITY`).
        """
        cdp = self._client()
        session = self.page()
        metrics = cdp.call("Page.getLayoutMetrics", session_id=session, timeout=4)["result"]
        view = metrics.get("cssVisualViewport") or metrics.get("layoutViewport") or {}
        width = int(view.get("clientWidth", MOBILE_WIDTH))
        height = int(view.get("clientHeight", MOBILE_HEIGHT))
        # **Aucune retouche d'émulation ici — jamais.** Changer `deviceScaleFactor` (ou la largeur)
        # juste avant `Page.captureScreenshot` fait rendre la page en **mosaïque 2×2**, même avec
        # 400 ms d'écart : mesuré, cf. `frames/etat_sabote.jpg`. Le DPR se pose au bail
        # (`server._pose_viewport` → `use_phone_viewport`), pas à la capture. Conséquence assumée :
        # si un autre client CDP le ramène à 1, la trame tombe à 375 px — molle, mais **juste**.
        # Une mosaïque n'est pas une image moins bonne, c'est une image fausse.
        if scale >= 1.0:
            # Sans `clip` : la capture sort en pixels physiques (css × DPR), sans le décalage de
            # barre de défilement qui rognait 15 px sur la largeur.
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
