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

from .cdp import BrowserUnavailable, Controller
from .session import AlreadyHeld, Session

SCALE = 0.6
WATCH_DELAY_S = 0.3      # §2 : 3 img/s quand l'app regarde
DRIVE_DELAY_S = 0.12     # §2 : 8 img/s quand l'humain conduit
IDLE_DELAY_S = 1.5

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
            "browser": bool(STATE.get("task_id")),
            # **Ce que ce processus peut réellement montrer.** Deux processus Hermes tiennent chacun
            # leur navigateur : la fiche dit lequel a une vraie page, pour que le relais n'envoie pas
            # l'écran du téléphone sur un onglet vide.
            "page": page["url"],
            "state": page["state"],
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
    """Un battement tant qu'une prise en main est en attente ou qu'un écran regarde."""
    while True:
        try:
            if HUB.pending is not None or STATE["clients"]:
                _touch()
        except Exception:
            pass
        time.sleep(KEEPALIVE_S)


def _controller() -> Controller:
    task_id = STATE.get("task_id")
    if not task_id:
        raise BrowserUnavailable("no browser open in this turn")
    return Controller(str(task_id))


def _taille(body: Any) -> dict:
    """La taille CSS que l'app demande, quand elle la dit. Bornée, jamais de valeur absurde."""
    if not isinstance(body, dict):
        return {}
    taille: dict[str, Any] = {}
    try:
        largeur = int(body.get("width") or 0)
        if 200 <= largeur <= 4000:
            taille["width"] = largeur
    except (TypeError, ValueError):
        pass
    try:
        hauteur = int(body.get("height") or 0)
        if 200 <= hauteur <= 4000:
            taille["height"] = hauteur
    except (TypeError, ValueError):
        pass
    try:
        rapport = float(body.get("dpr") or 0)
        if 0.5 <= rapport <= 6:
            taille["dpr"] = rapport
    except (TypeError, ValueError):
        pass
    return taille


async def _pose_viewport(phone: bool, size: dict | None = None) -> None:
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
            largeur = int((size or {}).get("width") or 0)
            hauteur = int((size or {}).get("height") or 0)
            rapport = float((size or {}).get("dpr") or 0)
            await asyncio.to_thread(
                controleur.use_phone_viewport,
                largeur or None,
                hauteur or None,
                rapport or None,
            )
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
_PAGE: dict[str, Any] = {"at": 0.0, "state": "gone", "url": ""}


def page_snapshot() -> dict[str, str]:
    """L'état de la page du tour, avec deux secondes de mémoire."""
    now = time.time()
    if now - float(_PAGE["at"]) < ALIVE_CACHE_S:
        return {"state": str(_PAGE["state"]), "url": str(_PAGE["url"])}
    task_id = STATE.get("task_id")
    etat, url = "gone", ""
    if task_id:
        try:
            controleur = Controller(str(task_id))
            etat = controleur.page_state()
            url = controleur.current_url() if etat == "ok" else ""
        except Exception:
            etat, url = "gone", ""
    _PAGE.update(at=now, state=etat, url=url)
    return {"state": etat, "url": url}


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
            "lease": {"holder": SESSION.holder, "epoch": SESSION.epoch},
            "watching": len(STATE["clients"]),
            "frames": SESSION.frames,
            "pending": (
                {"id": waiting.takeover_id, "reason": waiting.reason} if waiting is not None else None
            ),
        }
    )


async def observe(request: web.Request) -> web.Response:
    if not _authorised(request):
        return _refused("token")
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
        await _pose_viewport(True, _taille(body))
        await _broadcast({"type": "lease", **state})
        return web.json_response({"ok": True, **state})
    if action == "viewport":
        # **La page suit l'écran quand il change de forme.** Plier ou déplier un appareil change
        # la largeur sans changer de page : sans ce message, la page gardait la forme d'avant et
        # flottait dans un cadre qui n'était plus le sien.
        if state.get("holder") == "human":
            await _pose_viewport(True, _taille(body))
        return web.json_response({"ok": True, **state})
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


async def _pump() -> None:
    """Une trame à la fois, tant qu'un client regarde. Personne ne regarde : rien ne tourne."""
    idle_sent = False
    while STATE["clients"]:
        trame = await _une_trame()
        if trame:
            idle_sent = False
            await _broadcast(trame)
            await asyncio.sleep(DRIVE_DELAY_S if SESSION.holder == "human" else WATCH_DELAY_S)
        else:
            if not idle_sent:
                # **Dire pourquoi**, pas seulement « rien » : `blank` (navigateur neuf, onglet
                # vide) et `gone` (aucun navigateur) demandent deux gestes différents à l'humain.
                await _broadcast({"type": "idle", "state": page_snapshot()["state"]})
                idle_sent = True
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
        frame = await asyncio.to_thread(controller.frame, SCALE)
    except Exception:
        return None
    if not frame:
        return None
    seq = SESSION.next_seq()
    css = frame.get("css") or [0, 0]
    SESSION.viewport = (int(css[0]), int(css[1]))
    return {"type": "frame", "seq": seq, "live": True, **frame}


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


def notify_pending(takeover_id: str, reason: str) -> bool:
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
            _broadcast({"type": "takeover", "id": takeover_id, "reason": reason}), boucle
        )
        return True
    except RuntimeError:  # boucle fermée entre-temps
        log.warning("onyx-browser: boucle fermée pendant la notification")
        clear_pending()
        return False
