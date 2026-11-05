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
import json
import logging
import os
import sys
import threading
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


def _controller() -> Controller:
    task_id = STATE.get("task_id")
    if not task_id:
        raise BrowserUnavailable("no browser open in this turn")
    return Controller(str(task_id))


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


# -- routes -----------------------------------------------------------------------------------

async def health(request: web.Request) -> web.Response:
    if not _authorised(request):
        return _refused("token")
    waiting = SESSION.waiting
    return web.json_response(
        {
            "ok": True,
            "plugin": "onyx-browser",
            "browser": bool(STATE.get("task_id")),
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
        await _broadcast({"type": "lease", **state})
        return web.json_response({"ok": True, **state})
    if action == "release":
        state = SESSION.release(str((body or {}).get("note") or ""))
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
    return ws


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
            raw_css = payload.get("css") or list(SESSION.viewport)
            css = (int(raw_css[0]), int(raw_css[1]))
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
        SESSION.release(note)
        if SESSION.waiting is not None:
            SESSION.waiting.facts.update(facts)
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
        try:
            controller = _controller()
            frame = await asyncio.to_thread(controller.frame, SCALE)
        except Exception:
            frame = None
        if frame:
            idle_sent = False
            seq = SESSION.next_seq()
            SESSION.viewport = tuple(frame.pop("css"))
            await _broadcast({"type": "frame", "seq": seq, "live": True, **frame})
            await asyncio.sleep(DRIVE_DELAY_S if SESSION.holder == "human" else WATCH_DELAY_S)
        else:
            if not idle_sent:
                await _broadcast({"type": "idle"})
                idle_sent = True
            await asyncio.sleep(IDLE_DELAY_S)


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
    site = web.TCPSite(runner, HOST, PORT)
    try:
        await site.start()
    except OSError as erreur:
        # Hermes charge un plugin **une fois par portée** (lancement puis chaque profil) : le
        # deuxième `register()` trouve le port déjà pris par le premier. Ce n'est pas une panne —
        # c'est le même serveur, une fois. On le dit et on s'arrête là, au lieu de laisser
        # remonter un OSError qui fait passer le plugin pour cassé.
        if getattr(erreur, "errno", None) in (98, 48):  # EADDRINUSE : Linux, macOS
            log.info("onyx-browser: déjà en écoute sur %s:%s — instance ignorée", HOST, PORT)
            STARTED.set()
            return
        raise
    # **La boucle n'est retenue qu'une fois le port réellement pris.** Avant, elle était posée en
    # entrée : si le `site.start()` échouait, `asyncio.run` fermait la boucle et `_LOOP` restait
    # pointé sur un cadavre — `notify_pending` levait alors un `RuntimeError` en pleine prise en main.
    HUB.loop = asyncio.get_running_loop()
    STARTED.set()
    log.info("onyx-browser: en écoute sur %s:%s/onyx-browser/health", HOST, PORT)
    # **Le serveur vit aussi longtemps que la boucle.** `_serve()` rendait la main juste après le
    # `site.start()` : `asyncio.run()` fermait alors la boucle et le runner tombait avec elle — le
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
    STARTED.wait(timeout=5)


def notify_pending(takeover_id: str, reason: str) -> bool:
    """Prévient les clients déjà à l'écran qu'une demande vient d'arriver.

    Rend `False` — sans lever — quand aucun serveur ne vit dans ce processus : c'est une
    information utile à l'outil, pas une panne de l'appelant.
    """
    boucle = HUB.loop
    if boucle is None or boucle.is_closed():
        log.warning("onyx-browser: aucune boucle vivante, la prise en main reste locale")
        return False
    try:
        asyncio.run_coroutine_threadsafe(
            _broadcast({"type": "takeover", "id": takeover_id, "reason": reason}), boucle
        )
        return True
    except RuntimeError:  # boucle fermée entre-temps
        log.warning("onyx-browser: boucle fermée pendant la notification")
        return False
