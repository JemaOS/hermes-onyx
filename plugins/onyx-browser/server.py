"""Le serveur du navigateur : un aiohttp sur la boucle locale, derrière `onyx-one`.

Trois routes et un WebSocket, rien de plus :

    GET  /onyx-browser/health   → le plugin est là, et y a-t-il un navigateur vivant ?
    POST /onyx-browser/observe  → un ticket à usage unique (30 s) et un `viewer_id`
    POST /onyx-browser/lease    → « je conduis » / « je rends la main »
    GET  /onyx-browser/ws       → les trames, les saisies, la fin

Le port n'est **jamais** exposé : `onyx-one.py` relaie `/onyx-browser/` vers 127.0.0.1:8788, et
c'est lui qui porte l'authentification de l'app. Le plugin ajoute par-dessus son ticket à usage
unique. Deux verrous, et le port reste en boucle locale.
"""

from __future__ import annotations

import asyncio
import atexit
import json
import logging
import os
import socket
import sys
import threading
import time
from pathlib import Path
from typing import Any

from aiohttp import WSMsgType, web

log = logging.getLogger(__name__)

from .cdp import MOBILE_HEIGHT, MOBILE_WIDTH, BrowserUnavailable, Controller
from .session import TAKEOVER_TIMEOUT_S, AlreadyHeld, Session

#: **L'échelle et la qualité de capture, par conducteur.** L'ancien réglage (`0.6` + `quality 55`
#: + `optimizeForSpeed`) donnait une trame de 450 px de large, étirée à ~1080 px par l'app avec
#: `FilterQuality.Low` : le formulaire de connexion était illisible et le doigt tombait à côté.
#:
#: **Ne jamais dépasser 1.0.** Le viewport téléphone est posé en DPR 2 (`use_phone_viewport`), donc
#: `scale 1.0` capture déjà 750×1688 px. Au-delà, `Page.captureScreenshot` avec `clip.scale > 1`
#: rend une **mosaïque** (la page répétée en 2×2, « captureBeyondViewport » n'y change rien) :
#: constaté et mesuré, cf. `frames/beyond_1.5.jpg`.
WATCH_SCALE = 0.6        # 450×1013 px, ~26 Ko, 3 img/s ≈ 0,6 Mbit/s
DRIVE_SCALE = 1.0        # 750×1688 px, ~68 Ko, 8 img/s ≈ 4,4 Mbit/s (net sur le téléphone)
WATCH_QUALITY = 62
DRIVE_QUALITY = 66
#: 6 img/s en conduite : à 750 px de large et q66 la trame pèse ~110 Ko, soit ~5 Mbit/s — le
#: plafond qu'un tunnel + un forfait mobile portent sans à-coups. Au-delà, le fil prend du retard
#: et l'image « saute » au lieu de simplement payer plus cher.
DRIVE_DELAY_S = 0.15
WATCH_DELAY_S = 0.3      # §2 : 3 img/s quand l'app regarde
IDLE_DELAY_S = 1.5
#: Le rythme du battement `idle`. Il doit rester **très en dessous** du délai de silence que
#: l'app compte comme un flux mort (12 s) : sinon le chien de garde de l'app croit le flux
#: mort pendant qu'il est parfaitement vivant, et se reconnecte en boucle.
IDLE_BEAT_S = 2.0

# **Hermes charge le même plugin plusieurs fois dans un seul processus** : une fois en portée
# « lancement », une fois par profil. Chaque import créait sa propre `Session`, sa propre boucle et
# son propre serveur — la première copie prenait le port, la seconde mourait en `EADDRINUSE` en
# gardant une boucle **déjà fermée** dans `_LOOP`. L'outil de prise en main tournait alors dans la
# copie morte : `RuntimeError: Event loop is closed`, et la demande n'atteignait jamais l'écran.
#
# Le hub est rangé dans `sys.modules` : c'est le seul endroit que toutes les copies d'un même
# processus partagent. Une session, un état, une boucle, un port.
def _hub() -> Any:
    import types

    module = sys.modules.get("onyx_browser_hub")
    if module is None:
        module = types.ModuleType("onyx_browser_hub")
        module.SESSION = Session()
        module.STATE: dict[str, Any] = {"task_id": None, "clients": set(), "pump": None}
        module.STARTED = threading.Event()
        module.loop = None
        module.port = None
        module.pending: str | None = None
        module.lance = threading.Lock()
        module.tente = False
        sys.modules["onyx_browser_hub"] = module
    return module


HUB = _hub()
SESSION = HUB.SESSION
STATE = HUB.STATE
STARTED = HUB.STARTED
#: Gardée pour l'écran qui s'ouvre : une trame de plus de quinze secondes décrit une page qui a
#: probablement changé, et la montrer telle quelle ferait croire à l'humain qu'il regarde le présent.
STALE_FRAME_S = 15.0
STATE.setdefault("last_frame", None)


def _bind(value: str) -> tuple[str, int]:
    host, _, port = value.partition(":")
    return (host or "127.0.0.1", int(port or 8788))


HOST, PORT = _bind(os.environ.get("BROWSER_BIND", "127.0.0.1:8788"))
TOKEN = os.environ.get("BROWSER_TOKEN", "").strip()

# **Un port par processus, et un registre pour les trouver.**
#
# Hermes fait tourner plusieurs processus qui chargent tous ce plugin : `gateway run` (le chat de
# l'app, l'api_server) et `serve --port 9119` (le dashboard). Le navigateur de l'agent vit dans
# **le** processus qui exécute le tour — et c'est rarement celui qui a pris 8788 en premier.
#
# Avant ce registre, la deuxième copie mourait en `EADDRINUSE` : elle marquait « démarré » sans
# écouter nulle part, et l'outil de prise en main levait `RuntimeError: Event loop is closed`.
#
# Maintenant chaque processus écoute — 8788 s'il est libre, un port éphémère sinon — et dépose sa
# fiche ici. `onyx-one` choisit la fiche de celui qui a une demande en attente, donc l'écran du
# téléphone arrive toujours sur le processus qui tient le navigateur.
PEERS_DIR = Path(os.environ.get("BROWSER_PEERS") or (Path.home() / ".hermes" / "onyx-browser" / "peers"))
PEER_TTL_S = 20.0        # au-delà, la fiche est un fantôme : `onyx-one` l'ignore
PEER_BEAT_S = 2.0        # rafraîchissement de la fiche


def _peer_file(pid: int | None = None) -> Path:
    return PEERS_DIR / f"{pid or os.getpid()}.json"


# **Une seule écriture de fiche à la fois, et un nom de temporaire par écriture.**
#
# Le battement (toutes les 2 s) et `notify_pending` écrivent la même fiche depuis deux fils
# différents. Tous deux passaient par le **même** temporaire `<pid>.tmp` : l'un tronquait pendant
# que l'autre écrivait, et `os.replace` publiait un JSON hybride. Observé en vrai :
#
#     {"…", "ts": 1793846556.4075263}.4075465}
#
# Un relais qui n'arrive pas à lire la fiche ignore le processus — donc l'écran du téléphone part
# sur le port 8788, c'est-à-dire sur un autre processus, et attend une page qui n'y est pas. Le
# défaut ne ressemble pas à une erreur : il ressemble à un écran vide.
_ECRITURE = threading.Lock()


def _peer_avec_page() -> int | None:
    """Le pid d'un **autre** processus qui montre une vraie page.

    Deux processus Hermes tournent en parallèle — la passerelle (`gateway run`) et le tableau de
    bord (`hermes serve`) — et chacun peut avoir son propre navigateur. Le navigateur de l'agent
    vit dans celui qui exécute le tour ; l'autre n'a rien à montrer. Un écran qui se connecte au
    mauvais reste donc sur « En attente… » indéfiniment, et le relais — qui épingle le ticket
    sur l'émetteur — le renvoie au même endroit à chaque tentative.

    On regarde donc les fiches du répertoire partagé : si un autre processus a une page (`ok`),
    c'est là que l'écran doit aller. La fiche est rafraîchie toutes les deux secondes et
    périmée au bout de vingt : ce n'est qu'un indice, d'où la vérification du pid.

    **Sauf si c'est le même navigateur.** Les deux processus peuvent être attachés au *même*
    Chromium (la découverte DevTools le trouve par `/proc`, sans distinction de propriétaire) :
    le port DevTools est alors identique. Dans ce cas, l'autre processus ne montre pas « la
    page ailleurs » — il montre **la même page**, et l'envoyer chez lui coupe l'écran en plein
    milieu pour rien. On l'ignore donc, et l'écran reste là où la page est réellement.
    """
    try:
        noms = os.listdir(PEERS_DIR)
    except OSError:
        return None
    maintenant = time.time()
    notre_port = int(_PAGE.get("port") or -1)
    for nom in noms:
        if not nom.endswith(".json"):
            continue
        try:
            with open(PEERS_DIR / nom, encoding="utf-8") as flux:
                fiche = json.load(flux)
            pid = int(fiche.get("pid") or 0)
            age = maintenant - float(fiche.get("ts") or 0.0)
            etat = str(fiche.get("state") or "")
            port_fiche = int(fiche.get("browser_port") or -1)
        except (OSError, ValueError, TypeError):
            continue
        if not pid or pid == os.getpid() or age > PEER_TTL_S or etat != "ok":
            continue
        try:
            os.kill(pid, 0)
        except OSError:
            continue
        # **Le même navigateur n'est pas « ailleurs ».** On ne renvoie l'écran chez l'autre
        # processus que s'il tient un *autre* Chromium.
        if notre_port != -1 and port_fiche != -1 and port_fiche == notre_port:
            continue
        return pid
    return None


def write_peer(**extra: Any) -> None:
    """Dépose (ou rafraîchit) la fiche de ce processus. Sans serveur, il n'y a rien à déposer."""
    if HUB.port is None:
        return
    # La sonde CDP est lente et mise en cache : elle se fait **hors** du verrou.
    page = page_snapshot()
    chemin = _peer_file()
    with _ECRITURE:
        approach = {
            "pid": os.getpid(),
            "port": HUB.port,
            # Lu sous le verrou : une demande posée par `notify_pending` ne peut plus être
            # écrasée par un battement parti juste avant.
            "pending": HUB.pending,
            # **`browser` veut dire « il y a une page à montrer », pas « ce processus a déjà eu
            # un navigateur ».** Le drapeau tiré de `STATE` restait vrai après la fermeture de la
            # page : le relais choisissait alors ce processus pour l'écran du téléphone, qui
            # n'avait rien à montrer. `health` et la fiche doivent dire la même chose.
            "browser": page["state"] == "ok",
            # **Ce que ce processus peut réellement montrer.** Deux processus Hermes tiennent chacun
            # leur navigateur : la fiche dit lequel a une vraie page, pour que le relais n'envoie pas
            # l'écran du téléphone sur un onglet vide.
            "page": page["url"],
            "state": page["state"],
            # **L'identité du navigateur** (son port DevTools). Deux processus attachés au même
            # Chromium le partagent : sans cette valeur, l'un se prend pour « la page est
            # ailleurs » et renvoie l'écran du téléphone chez l'autre — pour la même page.
            "browser_port": int(page.get("port") or -1),
            "frames": SESSION.frames,
            "viewport": list(SESSION.viewport),
            "ts": time.time(),
        }
        approach.update(extra)
        try:
            PEERS_DIR.mkdir(parents=True, exist_ok=True)
            temporaire = chemin.with_name(
                "%s.%d.%d.tmp" % (chemin.stem, os.getpid(), threading.get_ident())
            )
            temporaire.write_text(json.dumps(approach), encoding="utf-8")
            os.replace(temporaire, chemin)
        except OSError as erreur:
            log.debug("onyx-browser: fiche de pair non écrite (%s)", erreur)


def drop_peer() -> None:
    try:
        _peer_file().unlink(missing_ok=True)
    except OSError:
        pass


async def _heartbeat() -> None:
    """Une fiche périmée est une fiche morte : on la garde fraîche tant que la boucle vit."""
    while True:
        write_peer()
        await asyncio.sleep(PEER_BEAT_S)


def clear_pending() -> None:
    HUB.pending = None
    write_peer()


# **Un refus tient pour la conversation et la page où il a été dit.**
#
# Sans mémoire, refuser ne servait à rien : le tour suivant rappelait l'outil sur la même page,
# une demande neuve était publiée avec un identifiant neuf, et la carte revenait — à chaque
# prompt réécrit, dans la conversation où l'on venait de dire non comme dans les autres. Le
# refus n'était donc pas une réponse, seulement un geste sans suite.
#
# La clé est le couple (conversation, page) : ailleurs — un autre site, une autre conversation —
# une nouvelle demande est légitime et s'affiche.
DECLINE_TTL_S = 30 * 60
DECLINES: dict[tuple[str, str], float] = {}
_DECLINES = threading.Lock()


def _url_key(url: str) -> str:
    """Une page, sans son ancre ni sa barre finale : deux formes de la même adresse sont un refus."""
    return str(url or "").split("#", 1)[0].rstrip("/")


def page_key() -> str:
    """L'identité de ce qu'on a refusé : la page, sans son ancre ni sa barre finale."""
    try:
        url = str(page_snapshot().get("url") or "")
    except Exception:
        url = ""
    return _url_key(url)


def note_declined(session: str, url: str | None = None) -> None:
    """Retient un refus, pour que la même demande ne repose pas la même question."""
    key = (str(session or ""), page_key() if url is None else _url_key(url))
    with _DECLINES:
        DECLINES[key] = time.time()


def declined_recently(session: str, url: str | None = None) -> bool:
    """Vrai si cette conversation a déjà refusé la main sur cette page."""
    key = (str(session or ""), page_key() if url is None else _url_key(url))
    with _DECLINES:
        at = DECLINES.get(key)
        if at is None:
            return False
        if time.time() - at > DECLINE_TTL_S:
            DECLINES.pop(key, None)
            return False
        return True


# **Le navigateur meurt d'inactivité pendant que l'humain tape.**
#
# `browser.inactivity_timeout` vaut 120 s par défaut, et le balayeur de Hermes ferme la session
# quand aucun outil `browser_*` n'a été appelé. Or une prise en main bloque le tour jusqu'à 15
# minutes **sans appeler d'outil** : la page était donc fermée au bout de deux minutes, et
# l'écran du téléphone affichait `about:blank` — un formulaire de connexion qui disparaît pendant
# qu'on le remplit. On demande explicitement à Hermes de compter ce temps comme de l'activité.
KEEPALIVE_S = 20.0


def _touch() -> None:
    """Compte le temps de la prise en main comme de l'activité pour le balayeur de Hermes.

    **Le nom de la session n'est pas toujours nu.** Le balayeur indexe `_session_last_activity`
    tantôt sous `api-xxx`, tantôt sous `api-xxx@/home/john/.hermes` selon le processus qui a ouvert
    le navigateur. Ne rafraîchir que la forme reçue par le crochet laissait l'autre expirer : la
    page se fermait au bout de 120 s, en plein formulaire, et l'écran du téléphone passait à
    `about:blank`. On rafraîchit donc **toutes** les clés qui désignent cette session.
    """
    task_id = STATE.get("task_id")
    if not task_id:
        return
    try:
        from tools import browser_tool_lifecycle as cycle
    except Exception:  # version de Hermes sans ce crochet : la prise en main reste possible
        return
    marques = [str(task_id)]
    registre = getattr(getattr(cycle, "_bt", None), "_session_last_activity", None)
    if isinstance(registre, dict):
        prefixe = str(task_id) + "@"
        marques += [cle for cle in list(registre) if str(cle).startswith(prefixe)]
    for marque in marques:
        try:
            cycle._update_session_activity(marque)
        except Exception:
            pass


def _keepalive() -> None:
    """Un battement **tant qu'un navigateur d'agent existe**, pas seulement pendant qu'on regarde.

    **La page mourait entre deux tours.** `_touch()` n'était appelé que si une prise en main
    attendait ou qu'un écran était connecté — c'est-à-dire précisément quand personne n'a besoin
    d'ouvrir l'écran pour la première fois. L'humain ouvrait donc le panneau sur une page déjà
    récoltée par le balayeur d'inactivité, et lisait « aucune page n'est ouverte » alors que
    l'agent venait de la charger. Maintenir le navigateur vivant tant qu'un `task_id` est connu est
    le comportement attendu d'un écran qu'on peut consulter à tout moment.

    **Et il y avait un deuxième minuteur, celui du démon.** Même refreshed par `_touch()`, le
    démon `agent-browser` s'arrêtait de lui-même au bout de dix minutes : il ne voit que ses
    propres commandes CLI, et le trafic CDP du plugin lui est invisible. Le Chromium partait avec
    lui, et l'écran du téléphone — ouvert, en attente — lisait « aucune page n'est ouverte » pour
    une page qui existait encore deux minutes plus tôt. Le battement parle donc aussi au démon,
    par sa CLI (`get cdp-url`, qui ne navigue pas).
    """
    while True:
        try:
            if HUB.pending is not None or STATE["clients"] or STATE.get("task_id"):
                _touch()
                _relance_le_demon()
        except Exception:
            pass
        time.sleep(KEEPALIVE_S)


def _relance_le_demon() -> None:
    """Relance le minuteur du démon `agent-browser` qui tient le navigateur courant.

    **Sans ça, la page finit par disparaître de l'écran.** Le démon ne voit que les commandes
    CLI qu'il reçoit : le plugin, qui parle CDP en direct, lui est invisible. `get cdp-url` ne
    navigue pas — elle relit l'URL DevTools — et son minuteur repart.

    On ne parle qu'au démon du navigateur **réellement accroché** : un démon inconnu ne mérite
    pas qu'on le relance, et le plugin ignore lequel est vivant quand il n'a pas de page.
    """
    port = int(_PAGE.get("port") or -1)
    if port <= 0:
        # Le port n'est connu que si une sonde a tourné récemment. On en déclenche une :
        # elle rafraîchit `_PAGE` et rend le port du navigateur accroché.
        try:
            page_snapshot()
        except Exception:
            return
        port = int(_PAGE.get("port") or -1)
    if port <= 0:
        return
    try:
        from . import cdp as _cdp

        env = _cdp.demon_du_port(port)
        _cdp.relance_le_minuteur_du_demon(port)
        _rafraichit_activite_hermes(str(env.get(_cdp.DEMON_SESSION_ENV) or ""))
    except Exception:
        pass


def _rafraichit_activite_hermes(nom_session: str) -> None:
    """Compte la présence de l'écran comme de l'activité pour le ramasse-miettes de Hermes.

    **Le ramasse-miettes de Hermes est un second timer, indépendant du démon.** Il ferme les
    sessions dont le dernier passage d'un outil `browser_*` date de plus de
    `browser.inactivity_timeout`. Or `_touch()` ne peut rien faire sans `task_id` — et il est
    vide pour toute session gateway (`attached: false` dans `health`). La session était donc
    récoltée au même moment que le démon, et l'écran lisait « aucune page n'est ouverte ».

    On rafraîchit donc l'activité de **la session qui porte ce navigateur**, retrouvée par le
    nom de session du démon (`session_name`), et non par un `task_id` que l'on n'a pas.
    """
    if not nom_session:
        return
    try:
        from tools import browser_tool as _bt
        from tools import browser_tool_lifecycle as cycle
    except Exception:
        return
    try:
        with _bt._cleanup_lock:
            cles = [
                cle
                for cle, info in list(_bt._active_sessions.items())
                if str((info or {}).get("session_name") or "") == nom_session
            ]
    except Exception:
        return
    for cle in cles:
        try:
            cycle._update_session_activity(cle)
        except Exception:
            continue


def _registry_task_id() -> str:
    """Le `task_id` du navigateur vivant, lu dans le registre du superviseur.

    **Le crochet ne suffit pas.** `model_tools._CallIds.task_id` vaut `""` quand l'appel vient
    d'une session gateway : `STATE["task_id"]` restait donc vide, `health` annonçait
    `attached: false`, et l'écran du téléphone n'avait aucune page à capturer — alors que le
    superviseur, lui, en tenait une. On demande donc directement à qui sait.
    """
    try:
        from tools.browser_supervisor import SUPERVISOR_REGISTRY as registre
    except Exception:
        return ""
    par_tache = getattr(registre, "_by_task", None)
    if isinstance(par_tache, dict):
        for task_id in reversed(list(par_tache)):
            return str(task_id)
    return ""


def _task_id() -> str:
    """L'identifiant retenu pour ce processus : le crochet d'abord, le registre ensuite."""
    task_id = str(STATE.get("task_id") or "")
    if not task_id:
        task_id = _registry_task_id()
        if task_id:
            STATE["task_id"] = task_id
    return task_id


def _controller() -> Controller:
    """Le contrôleur du navigateur du tour — même sans `task_id`.

    **Le `task_id` n'est pas un prérequis.** Il ne sert qu'à la première voie (le seam du
    superviseur) ; la seconde (DevTools, lue dans `/proc`) n'en veut pas. Exiger un identifiant
    ici transformait « aucun superviseur » en « aucun navigateur » et l'écran du téléphone
    annonçait une page absente alors que le Chromium affichait la page.
    """
    return Controller(_task_id())


def _phone_css(size: dict[str, Any] | None = None) -> tuple[int, int, float]:
    """La forme CSS à poser pour que la capture fasse **exactement** les pixels de l'écran.

    L'app annonce son écran en pixels physiques (`{width, height, dpr}`). On garde un DPR de 2 —
    celui des téléphones visés, et celui pour lequel les pages sont écrites — et on en déduit la
    largeur CSS : `css = pixels / 2`. La capture vaut alors `css × 2`, c'est-à-dire la définition
    du téléphone, sans agrandissement à l'affichage : c'est là que le flou disparaît.

    Bornée : jamais moins de 200 px de CSS (une page minuscule n'est pas pilotable), jamais plus
    de 1 400 (au-delà, la trame pèse plus que le réseau ne porte).
    """
    brut = size if isinstance(size, dict) else (SESSION.phone_size or {})
    try:
        pixels_l = int(brut.get("width") or 0)
        pixels_h = int(brut.get("height") or 0)
    except (TypeError, ValueError):
        pixels_l = pixels_h = 0
    if pixels_l < 400 or pixels_h < 400:
        return MOBILE_WIDTH, MOBILE_HEIGHT, 2.0
    return (
        max(200, min(1400, pixels_l // 2)),
        max(200, min(2800, pixels_h // 2)),
        2.0,
    )


async def _pose_viewport(phone: bool, size: dict[str, Any] | None = None) -> None:
    """Donne à la page la forme du pouce, ou la lui retire.

    **Le viewport suit le volant, pas l'outil.** L'outil posait le viewport mobile avant de
    sonner, mais l'app peut prendre la main seule : la page restait alors en 1280 px réduits à
    la largeur d'un téléphone — le formulaire illisible et le doigt à côté du champ. Poser ici
    garantit la même page à celui qui conduit, quel que soit le chemin qui l'a amené.
    """
    try:
        controleur = _controller()
    except Exception:
        return  # pas de page : rien à mettre en forme
    try:
        if phone:
            # **`repaint_viewport`, pas `use_phone_viewport`.** Il efface d'abord l'émulation en
            # place puis recharge : sans ce nettoyage, une émulation déjà posée (par le navigateur
            # de l'agent, par un écran précédent) se superpose et la capture sort en **mosaïque** —
            # la page répétée en 2×2, illisible et impossible à piloter.
            await asyncio.to_thread(controleur.repaint_viewport, *_phone_css(size))
        else:
            await asyncio.to_thread(controleur.clear_viewport)
    except Exception:
        # Un viewport refusé ne doit pas faire échouer la prise en main : on préfère un écran
        # en desktop à un bouton « Prendre la main » qui ne répond pas.
        pass


# -- authentification -------------------------------------------------------------------------

def _authorised(request: web.Request) -> bool:
    if not TOKEN:
        # Sans jeton, on n'accepte que la boucle locale : `onyx-one` est le seul appelant.
        peer = (request.remote or "").split("%")[0]
        return peer in ("127.0.0.1", "::1", "")
    header = request.headers.get("Authorization") or ""
    if header.lower().startswith("bearer "):
        header = header[7:].strip()
    header = header or (request.headers.get("X-Browser-Token") or "").strip()
    return bool(header) and header == TOKEN


def _refused(reason: str, status: int = 401) -> web.Response:
    return web.json_response({"ok": False, "error": reason}, status=status)


# -- la page est-elle encore là ? ---------------------------------------------------------------

#: Une sonde CDP coûte quelques millisecondes, et `health` est appelé par le relais à chaque
#: ouverture d'écran. On garde la réponse deux secondes : assez frais pour ne pas router l'écran
#: vers une page morte, assez rare pour ne pas harceler Chromium.
ALIVE_CACHE_S = 2.0
#: Combien de temps l'outil de prise en main dort avant de redemander si la page vit encore.
SONDE_S = 2.0
#: `state` : `ok`, `blank` (navigateur neuf, rien dedans) ou `gone` (aucun navigateur du tout).
#: `url` : ce que montre la page, pour que l'app puisse l'afficher au lieu de deviner.
_PAGE: dict[str, Any] = {"at": 0.0, "state": "gone", "url": "", "port": -1}


def page_snapshot() -> dict[str, str]:
    """L'état de la page du tour, avec deux secondes de mémoire."""
    now = time.time()
    if now - float(_PAGE["at"]) < ALIVE_CACHE_S:
        return {"state": str(_PAGE["state"]), "url": str(_PAGE["url"])}
    task_id = _task_id()
    etat, url = "gone", ""
    port = -1
    # **Pas de `if task_id`.** La découverte DevTools n'a pas besoin d'identifiant : exiger ici
    # un `task_id` faisait annoncer « aucun navigateur » alors que la page était là, et l'écran
    # du téléphone restait vide.
    try:
        controleur = Controller(task_id)
        etat = controleur.page_state()
        url = controleur.current_url() if etat == "ok" else ""
        # **Le port DevTools du navigateur accroché.** C'est l'identité du navigateur : deux
        # processus attachés au même Chromium doivent se reconnaître, sinon l'un d'eux concludes
        # que « la page est ailleurs » et renvoie l'écran du téléphone chez l'autre.
        port = controleur.browser_port()
    except Exception:
        etat, url = "gone", ""
    _PAGE.update(at=now, state=etat, url=url, port=port)
    return {"state": etat, "url": url, "port": port}


def page_alive() -> bool:
    """La page du tour montre-t-elle vraiment quelque chose ?

    **`blank` compte comme mort.** Sinon l'outil attendait quinze minutes devant un onglet vide,
    et le modèle annonçait à l'humain une prise en main qui n'avait aucune page à montrer.
    """
    return page_snapshot()["state"] == "ok"


# -- routes -----------------------------------------------------------------------------------

async def health(request: web.Request) -> web.Response:
    if not _authorised(request):
        return _refused("token")
    waiting = SESSION.waiting
    # **Une demande sans page n'est pas une demande.** Le navigateur de Hermes meurt après deux
    # minutes sans outil : une prise en main restée en attente devient un fantôme que le relais
    # continue de préférer — l'écran du téléphone aboutissait alors sur un processus qui ne pouvait
    # rien lui montrer. On la déclare morte ici, et on retire le drapeau pour ne plus y router
    # personne. L'outil, lui, la voit par sa propre sonde et rend la main sans attendre 15 minutes.
    alive = page_alive()
    page = page_snapshot()
    if waiting is not None and not alive:
        clear_pending()
        waiting = None
    return web.json_response(
        {
            "ok": True,
            "plugin": "onyx-browser",
            # **`browser` veut dire « il y a une page à montrer », pas « ce processus a déjà eu un
            # navigateur ».** Le drapeau tiré de `STATE` restait vrai après la fermeture de la page,
            # et le relais routait l'écran du téléphone vers un processus vide (`idle` au lieu
            # d'images). `attached` garde la trace du tour, pour le diagnostic.
            "browser": alive,
            "attached": bool(STATE.get("task_id")),
            "alive": alive,
            # **L'écran a besoin de savoir ce qu'il y a à montrer.** `blank` n'est pas une panne :
            # c'est un navigateur neuf, et l'app peut le dire (« rouvre la page ») au lieu
            # d'afficher du blanc sans explication.
            "page": page,
            # **Les réglages de capture, en clair.** Après une modification du plugin, c'est le seul
            # moyen de savoir ce que le processus *vivant* exécute : `hermes` ne recharge pas les
            # modules à chaud quand le crochet est inerte, et deux valeurs identiques au fichier ne
            # disent rien de la mémoire.
            "stream": {
                "watch": [WATCH_SCALE, WATCH_QUALITY],
                "drive": [DRIVE_SCALE, DRIVE_QUALITY],
                "takeover_timeout_s": TAKEOVER_TIMEOUT_S,
            },
            "lease": {"holder": SESSION.holder, "epoch": SESSION.epoch},
            "watching": len(STATE["clients"]),
            "frames": SESSION.frames,
            "pending": (
                {
                    "id": waiting.takeover_id,
                    "reason": waiting.reason,
                    # **À quelle conversation la demande appartient.** L'app ne devine plus : elle
                    # affiche la carte dans le fil qui a demandé, et dans aucun autre.
                    "session": waiting.session,
                    "url": waiting.url,
                }
                if waiting is not None
                else None
            ),
        }
    )


async def observe(request: web.Request) -> web.Response:
    if not _authorised(request):
        return _refused("token")
    # **Un écran qui arrive chez le mauvais processus doit repartir ailleurs.** Le relais épingle
    # le ticket sur l'émetteur : sans ce refus, l'app demandait un ticket, le plugin le lui
    # servait, et l'écran restait sur « En attente… » pendant que la vraie page jouait chez
    # l'autre processus. En refusant (`409`), l'app redemande un ticket — et le relais, qui
    # voit alors que ce processus n'a rien à montrer, route l'écran vers celui qui a la page.
    if not page_alive():
        autre = _peer_avec_page()
        if autre is not None:
            log.info("onyx-browser: la page est chez le pid %d — ticket refusé ici", autre)
            return web.json_response(
                {"ok": False, "error": "page_elsewhere", "pid": autre}, status=409
            )
    ticket = SESSION.mint()
    return web.json_response(
        {
            "ok": True,
            "ticket": ticket.value,
            "path": "/onyx-browser/ws",
            "viewer_id": ticket.viewer_id,
            "expires_in": 30,
        }
    )


async def lease(request: web.Request) -> web.Response:
    if not _authorised(request):
        return _refused("token")
    body = {}
    try:
        body = await request.json()
    except Exception:
        body = {}
    action = str((body or {}).get("action") or "")
    if action == "acquire":
        try:
            state = SESSION.acquire()
        except AlreadyHeld as held:
            return web.json_response(
                {"ok": False, "error": "lease_held", "epoch": held.epoch}, status=409
            )
        # **La page prend la forme du pouce dès que l'humain conduit.** L'outil le faisait avant
        # de sonner, mais l'app peut aussi prendre la main seule (bouton « Prendre la main ») :
        # elle héritait alors d'un viewport de 1280 px réduit à la largeur de l'écran — texte
        # minuscule, cibles de trois pixels, et un tap qui tombe à côté.
        await _pose_viewport(True)
        await _broadcast({"type": "lease", **state})
        return web.json_response({"ok": True, **state})
    if action == "viewport":
        # **L'écran qui regarde dit sa taille, et la page prend cette forme-là.** C'est ce qui
        # remplace le gabarit figé 390×844 : la capture arrive ensuite à la définition du
        # téléphone au lieu d'être agrandie à l'affichage (l'image molle signalée).
        taille = (body or {}).get("size")
        if isinstance(taille, dict):
            SESSION.phone_size = taille
            if SESSION.holder == "human":
                await _pose_viewport(True, taille)
        return web.json_response({"ok": True, "css": list(_phone_css()[:2])})
    if action == "decline":
        # **Refuser est un droit, pas une absence de réponse.** Sans cette action, la seule issue
        # était de ne rien faire : la carte restait affichée, l'agent attendait jusqu'à 15 minutes,
        # et la demande suivait la personne dans toutes les conversations ouvertes. Ici, le refus
        # est **dit** : la demande tombe, l'agent reprend son tour avec la raison, et l'app peut
        # retirer la carte immédiatement.
        waiting = SESSION.waiting
        if waiting is None:
            clear_pending()
            return web.json_response({"ok": True, "declined": False, "reason": "nothing_pending"})
        waiting.refused = True
        waiting.note = str((body or {}).get("note") or "")
        # **Le refus se retient.** Sans cette mémoire, le tour suivant rappelait l'outil sur la
        # même page et la carte revenait — c'est exactement ce qui était signalé.
        note_declined(waiting.session, waiting.url or None)
        waiting.done.set()
        clear_pending()
        await _broadcast({"type": "takeover_done", "id": waiting.takeover_id, "refused": True})
        return web.json_response({"ok": True, "declined": True, "id": waiting.takeover_id})
    if action == "release":
        state = SESSION.release(str((body or {}).get("note") or ""))
        # Rendre la main rend aussi la page à sa forme de bureau : l'agent reprend son tour, et
        # il ne doit pas hériter du gabarit du téléphone.
        await _pose_viewport(False)
        await _broadcast({"type": "lease", **state})
        return web.json_response({"ok": True, **state})
    return _refused("action", status=400)


# -- le WebSocket -----------------------------------------------------------------------------

async def websocket(request: web.Request) -> web.WebSocketResponse:
    ticket = SESSION.redeem(request.query.get("ticket", ""))
    if ticket is None:
        # Un ticket mort n'obtient pas de 101 : la poignée de main est refusée franchement.
        raise web.HTTPUnauthorized(text="ticket")
    ws = web.WebSocketResponse(heartbeat=30)
    await ws.prepare(request)
    STATE["clients"].add(ws)
    # **Un écran qui s'ouvre voit la page tout de suite.** Sans cette trame, il attendait le
    # prochain tour de pompage — et restait sur « En attente du navigateur de l'agent… » alors que
    # la page existait déjà.
    garde = STATE.get("last_frame") or {}
    if garde and time.time() - float(garde.get("at") or 0.0) <= STALE_FRAME_S:
        await _send(ws, {**garde["trame"], "live": False})
    _ensure_pump()
    try:
        async for message in ws:
            if message.type is not WSMsgType.TEXT:
                continue
            try:
                payload = json.loads(message.data)
            except Exception:
                continue
            await _on_frame(payload, ws)
    finally:
        STATE["clients"].discard(ws)
        # **Un écran fermé ne garde pas le volant.** Le bail se prenait depuis le téléphone : si
        # l'app se ferme, perd le réseau ou change de conversation, personne ne le rend. Le bail
        # restait humain pour toujours, et la prise en main suivante était refusée en `409` — un
        # « quelqu'un conduit déjà » fantôme, impossible à lever sans redémarrer Hermes.
        if not STATE["clients"]:
            asyncio.ensure_future(_release_when_abandoned())
    return ws


#: Le temps qu'un écran revienne avant qu'on considère qu'il est parti. Un ticket neuf et une
#: reconnexion prennent moins d'une seconde ; vingt laissent la place à un changement de réseau.
RELEASE_GRACE_S = 20.0


async def _release_when_abandoned() -> None:
    await asyncio.sleep(RELEASE_GRACE_S)
    if STATE["clients"] or SESSION.holder != "human":
        return
    state = SESSION.abandon()
    await _broadcast({"type": "lease", **state})
    # **L'écran parti, la page reprend sa forme.** Le viewport de téléphone était posé pour la
    # prise en main ; si l'app se ferme sans rendre la main, il restait appliqué et l'agent
    # héritait d'une page en 390 px de large — illisible pour lui, alors qu'il croyait retrouver
    # la page comme il l'avait laissée.
    await _pose_viewport(phone=False)
    log.info("onyx-browser: écran parti, le bail revient à l'agent")


async def _on_frame(payload: dict[str, Any], ws: web.WebSocketResponse) -> None:
    kind = payload.get("type")
    if kind == "watch":
        # Rien à faire : le pompage tourne tant qu'il y a un client. On renvoie l'état.
        await _send(ws, {"type": "lease", "holder": SESSION.holder, "epoch": SESSION.epoch})
        return
    if kind == "input":
        refusal = SESSION.refusal(int(payload.get("seq") or 0))
        if refusal is not None:
            await _send(ws, {"type": "refused", "seq": payload.get("seq"), "reason": refusal})
            return
        action = str(payload.get("action") or "")
        try:
            controller = _controller()
            raw_css = payload.get("css") or []
            css = (int(raw_css[0]), int(raw_css[1])) if len(raw_css) >= 2 else (0, 0)
            if css[0] <= 0 or css[1] <= 0:
                # **La mesure du serveur vaut mieux que celle de l'app.** L'app envoie la taille
                # CSS qu'elle a lue sur la dernière trame ; si elle ne l'a pas lue (trame
                # reçue avant la correction, ou version de l'app plus ancienne), elle envoie
                # zéro — et le geste était rejoué sur un viewport de zéro pixel, c'est-à-dire
                # au coin de l'écran. On retombe alors sur ce que le serveur a mesuré lui-même.
                css = tuple(SESSION.viewport)
            if css[0] <= 0 or css[1] <= 0:
                await _send(ws, {"type": "refused", "seq": payload.get("seq"), "reason": "no_page"})
                return
            ok = await asyncio.to_thread(controller.input, action, payload, css)
        except BrowserUnavailable:
            await _send(ws, {"type": "refused", "seq": payload.get("seq"), "reason": "no_page"})
            return
        except Exception:
            await _send(ws, {"type": "refused", "seq": payload.get("seq"), "reason": "no_page"})
            return
        if not ok:
            await _send(ws, {"type": "refused", "seq": payload.get("seq"), "reason": "action"})
        return
    if kind == "done":
        note = str(payload.get("note") or "")
        try:
            facts = await asyncio.to_thread(_controller().login_form_facts)
        except Exception:
            facts = {}
        facts["origin_refused"] = False
        # **Les faits avant le réveil.** `release()` débloque l'outil de l'agent : les faits
        # doivent déjà être rangés quand il se réveille, sinon il lit un dictionnaire vide et
        # annonce au modèle « pas de formulaire » alors que la page en avait un.
        waiting = SESSION.waiting
        if waiting is not None:
            waiting.facts.update(facts)
        SESSION.release(note)
        clear_pending()
        await _broadcast({"type": "takeover_done", "id": payload.get("id"), "facts": facts})
        return


def _ensure_pump() -> None:
    pump = STATE.get("pump")
    if pump is None or pump.done():
        STATE["pump"] = asyncio.ensure_future(_pump())


def vignette() -> None:
    """Une image après chaque outil `browser_*`, même quand personne ne regarde.

    Le pompage ne tourne que s'il y a un spectateur : l'écran ouvert recevait donc sa première
    image au premier tour de pompage, et un écran rouvert plus tard ne voyait rien de ce qui s'était
    passé entre-temps. On capture ici une trame unique, sans diffusion, qui sert de garde à la
    prochaine connexion (voir `websocket`). Appelée depuis un fil qui n'est pas celui de la boucle.
    """
    if STATE["clients"]:
        return  # le pompage tourne déjà : il produira la trame
    boucle = HUB.loop
    if boucle is None or boucle.is_closed():
        return
    try:
        asyncio.run_coroutine_threadsafe(_capture_once(), boucle)
    except RuntimeError:
        pass


async def _capture_once() -> None:
    if STATE["clients"]:
        return
    await _une_trame()


async def _pump() -> None:
    """Une trame à la fois, tant qu'un client regarde. Personne ne regarde : rien ne tourne."""
    idle_sent = False
    dernier_idle = 0.0
    while STATE["clients"]:
        try:
            trame = await _une_trame()
        except Exception:
            # **Une panne de capture ne tue pas l'écran.** Elle est journalisée avec sa trace, puis
            # on réessaie : sans ce garde-fou, la tâche de pompage mourait et l'écran du téléphone
            # se figeait sans un mot dans le journal.
            log.exception("onyx-browser: capture en échec — le pompage continue")
            await asyncio.sleep(IDLE_DELAY_S)
            continue
        if trame:
            idle_sent = False
            await _broadcast(trame)
            await asyncio.sleep(DRIVE_DELAY_S if SESSION.holder == "human" else WATCH_DELAY_S)
        else:
            # **`idle` est un battement, pas une annonce.** Envoyé une seule fois, il laissait
            # l'écran dans un silence absolu : le chien de garde de l'app, qui ne compte que les
            # trames, ne se déclenchait jamais, et le socket mort en silence derrière le relais
            # n'était jamais noticed. L'écran restait donc sur « En attente… » pour toujours,
            # même quand la page réapparaissait. On répète donc `idle` — c'est ce qui permet à
            # l'app de distinguer « le flux est vivant mais il n'y a pas de page » de « le flux
            # est mort », et de se reconnecter tout seul dans le second cas.
            maintenant = time.monotonic()
            if not idle_sent or (maintenant - dernier_idle) >= IDLE_BEAT_S:
                # **Dire pourquoi**, pas seulement « rien » : `blank` (navigateur neuf, onglet
                # vide) et `gone` (aucun navigateur) demandent deux gestes différents à l'humain.
                await _broadcast({"type": "idle", "state": page_snapshot()["state"]})
                idle_sent = True
                dernier_idle = maintenant
            # **Un écran coincé chez le mauvais processus doit repartir.** La page vit dans un
            # autre processus : on le dit à l'écran, qui redemande alors un ticket — et le relais
            # le route cette fois vers le bon. Sans cela, l'app restait sur « En attente… » pour
            # toujours, alors que la page existait et était visible ailleurs.
            autre = _peer_avec_page()
            if autre is not None:
                log.info("onyx-browser: la page est chez le pid %d — l'écran est renvoyé", autre)
                await _broadcast({"type": "page_elsewhere", "pid": autre})
                for client in list(STATE["clients"]):
                    try:
                        await client.close(code=1012, message=b"page_elsewhere")
                    except Exception:
                        pass
                STATE["clients"].clear()
                return
            await asyncio.sleep(IDLE_DELAY_S)

async def _une_trame() -> dict[str, Any] | None:
    """Capture la page, et rend la trame à diffuser — `None` s'il n'y a rien à montrer.

    **La taille CSS part avec la trame.** Elle en était retirée juste avant la diffusion
    (`frame.pop("css")`), et l'app recevait donc `cssWidth = 0` : ses gestes étaient calculés sur
    un viewport de zéro pixel, et le serveur les rejouait au coin de l'écran. Le serveur garde la
    mesure pour lui, mais il la diffuse aussi : c'est elle qui dit à l'app où poser le doigt.
    """
    try:
        controller = _controller()
        # Le mode de capture suit le volant : pendant que l'humain conduit, la trame est pleine
        # résolution et lisible ; sinon elle reste légère (3 img/s de surveillance).
        conduit = SESSION.holder == "human"
        frame = await asyncio.to_thread(
            controller.frame,
            DRIVE_SCALE if conduit else WATCH_SCALE,
            DRIVE_QUALITY if conduit else WATCH_QUALITY,
        )
    except (BrowserUnavailable, TimeoutError, RuntimeError, KeyError, IndexError, TypeError) as erreur:
        # **On n'attrape que ce qui veut dire « pas de page ».** Un `except Exception` nu
        # transformait une vraie panne (attribut manquant, bug de trame) en `idle` silencieux :
        # l'écran affichait « rien à montrer » et personne ne voyait l'erreur. Le reste remonte,
        # et `_pump` le journalise.
        log.debug("onyx-browser: pas de trame (%s)", type(erreur).__name__)
        return None
    if not frame:
        return None
    seq = SESSION.next_seq()
    css = frame.get("css") or [0, 0]
    SESSION.viewport = (int(css[0]), int(css[1]))
    trame = {"type": "frame", "seq": seq, "live": True, **frame}
    # **La dernière image ne se perd pas.** Un écran qui s'ouvre entre deux trames attendait le
    # prochain tour de pompage (ou restait vide si aucun pompage ne tournait) : la vignette capturée
    # après chaque outil `browser_*` est donc gardée ici, et servie à la connexion.
    STATE["last_frame"] = {"at": time.time(), "trame": trame}
    return trame


async def _send(ws: web.WebSocketResponse, payload: dict[str, Any]) -> None:
    try:
        await ws.send_json(payload)
    except Exception:
        STATE["clients"].discard(ws)


async def _broadcast(payload: dict[str, Any]) -> None:
    for ws in list(STATE["clients"]):
        await _send(ws, payload)


# -- démarrage --------------------------------------------------------------------------------

def build_app() -> web.Application:
    app = web.Application()
    app.router.add_get("/onyx-browser/health", health)
    app.router.add_post("/onyx-browser/observe", observe)
    app.router.add_post("/onyx-browser/lease", lease)
    app.router.add_get("/onyx-browser/ws", websocket)
    # Les mêmes chemins sans préfixe : `onyx-one` peut relayer l'un ou l'autre.
    app.router.add_get("/health", health)
    app.router.add_get("/ws", websocket)
    return app


async def _serve() -> None:
    runner = web.AppRunner(build_app(), access_log=None)
    await runner.setup()

    # **Le port préféré, sinon un port libre** : jamais l'exception qui tue la boucle.
    #
    # `TCPSite` lève `EADDRINUSE` quand un autre processus Hermes tient déjà 8788 — et
    # `asyncio.run` fermait alors la boucle, laissant `HUB.loop` pointer sur un cadavre. On prend
    # la socket nous-mêmes : on sait alors sur quel port on a atterri, et on le publie.
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind((HOST, PORT))
    except OSError as erreur:
        if getattr(erreur, "errno", None) not in (98, 48):  # EADDRINUSE : Linux, macOS
            raise
        sock.close()
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind((HOST, 0))
        log.info(
            "onyx-browser: %s:%s est pris par un autre processus Hermes — ce processus écoute sur un "
            "port éphémère, et son navigateur reste joignable par le registre de pairs",
            HOST,
            PORT,
        )
    sock.listen(128)

    HUB.port = sock.getsockname()[1]
    site = web.SockSite(runner, sock)
    await site.start()
    # **La boucle n'est retenue qu'une fois la socket réellement en écoute.**
    HUB.loop = asyncio.get_running_loop()
    write_peer()
    atexit.register(drop_peer)
    STARTED.set()
    log.info("onyx-browser: en écoute sur %s:%s/onyx-browser/health", HOST, HUB.port)
    asyncio.ensure_future(_heartbeat())
    # **Le serveur vit aussi longtemps que la boucle.** `_serve()` rendait la main juste après le
    # démarrage : `asyncio.run()` fermait alors la boucle et le runner tombait avec elle — le
    # port se refermait une poignée de millisecondes après s'être ouvert (connexion réinitialisée,
    # jamais un 500). On attend ici, pour toujours.
    await asyncio.Event().wait()


def ensure_started() -> None:
    """Démarre le serveur une fois, dans son propre fil : `register()` est synchrone.

    Le verrou n'est pas décoratif : Hermes appelle `register()` plusieurs fois (lancement, puis
    chaque profil), et deux appels rapprochés voyaient tous deux `STARTED` vide — deux `TCPSite`
    sur le même port, un `EADDRINUSE`, et le plugin passait pour mort.
    """
    if STARTED.is_set():
        return
    with HUB.lance:
        if STARTED.is_set() or HUB.tente:
            return
        HUB.tente = True

        def run() -> None:
            asyncio.run(_serve())

        threading.Thread(target=run, name="onyx-browser", daemon=True).start()
        threading.Thread(target=_keepalive, name="onyx-browser-keepalive", daemon=True).start()
    STARTED.wait(timeout=5)


def announce(payload: dict[str, Any]) -> bool:
    """Prévient les écrans ouverts, depuis un fil qui n'est pas celui de la boucle."""
    boucle = HUB.loop
    if boucle is None or boucle.is_closed():
        return False
    try:
        asyncio.run_coroutine_threadsafe(_broadcast(payload), boucle)
        return True
    except RuntimeError:
        return False


def notify_pending(takeover_id: str, reason: str, session: str = "") -> bool:
    """Prévient les clients déjà à l'écran qu'une demande vient d'arriver.

    Rend `False` — sans lever — quand aucun serveur ne vit dans ce processus : c'est une
    information utile à l'outil, pas une panne de l'appelant.
    """
    boucle = HUB.loop
    if boucle is None or boucle.is_closed() or HUB.port is None:
        log.warning("onyx-browser: aucune boucle vivante, la prise en main reste locale")
        return False
    # **La fiche dit « demande en attente »** : c'est ce drapeau qu'`onyx-one` lit pour envoyer
    # l'écran du téléphone à CE processus plutôt qu'à celui qui tient le port 8788.
    HUB.pending = takeover_id
    write_peer()
    _touch()
    try:
        asyncio.run_coroutine_threadsafe(
            _broadcast({"type": "takeover", "id": takeover_id, "reason": reason, "session": session}), boucle
        )
        return True
    except RuntimeError:  # boucle fermée entre-temps
        log.warning("onyx-browser: boucle fermée pendant la notification")
        clear_pending()
        return False
