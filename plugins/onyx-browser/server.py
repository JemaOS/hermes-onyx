"""The browser server: an aiohttp on the loopback interface, behind `onyx-one`.

Three routes and one WebSocket, nothing more:

    GET  /onyx-browser/health   → the plugin is there, and is there a live browser?
    POST /onyx-browser/observe  → a single-use ticket (30 s) and a `viewer_id`
    POST /onyx-browser/lease    → "I drive" / "I hand back control"
    GET  /onyx-browser/ws       → frames, inputs, the end

The port is **never** exposed: `onyx-one.py` relays `/onyx-browser/` to 127.0.0.1:8788, and
it is the one carrying the app's authentication. The plugin adds its single-use ticket on top.
Two locks, and the port stays on the loopback interface.
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

#: **The capture scale and quality, per driver.** The old setting (`0.6` + `quality 55`
#: + `optimizeForSpeed`) produced a 450 px wide frame, stretched to ~1080 px by the app with
#: `FilterQuality.Low`: the login form was unreadable and the finger missed.
#:
#: **Never exceed 1.0.** The phone viewport is set at DPR 2 (`use_phone_viewport`), so
#: `scale 1.0` already captures 750×1688 px. Beyond that, `Page.captureScreenshot` with `clip.scale > 1`
#: returns a **mosaic** (the page repeated 2×2, "captureBeyondViewport" changes nothing):
#: observed and measured, cf. `frames/beyond_1.5.jpg`.
WATCH_SCALE = 0.6        # 450×1013 px, ~26 KB, 3 fps ≈ 0.6 Mbit/s
DRIVE_SCALE = 1.0        # 750×1688 px, ~68 KB, 8 fps ≈ 4.4 Mbit/s (clean on the phone)
WATCH_QUALITY = 62
DRIVE_QUALITY = 66
#: 6 fps while driving: at 750 px wide and q66 the frame weighs ~110 KB, i.e. ~5 Mbit/s — the
#: ceiling that a tunnel + a mobile plan carry without stutter. Beyond that, the wire falls behind
#: and the image "jumps" instead of simply costing more.
DRIVE_DELAY_S = 0.15
WATCH_DELAY_S = 0.3      # §2: 3 fps when the app is watching
IDLE_DELAY_S = 1.5
#: The `idle` heartbeat rate. It must stay **far below** the silence delay that
#: the app counts as a dead stream (12 s): otherwise the app's watchdog believes the stream
#: dead while it is perfectly alive, and reconnects in a loop.
IDLE_BEAT_S = 2.0

# **Hermes loads the same plugin several times in a single process**: once in "launch"
# scope, once per profile. Each import created its own `Session`, its own loop and
# its own server — the first copy took the port, the second died with `EADDRINUSE` while
# keeping an **already closed** loop in `_LOOP`. The takeover tool then ran in the
# dead copy: `RuntimeError: Event loop is closed`, and the request never reached the screen.
#
# The hub is stored in `sys.modules`: it is the only place that all copies of the same
# process share. One session, one state, one loop, one port.
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
#: Kept for the screen that opens: a frame more than fifteen seconds old describes a page that has
#: probably changed, and showing it as-is would make the human believe they are looking at the present.
STALE_FRAME_S = 15.0
STATE.setdefault("last_frame", None)


def _bind(value: str) -> tuple[str, int]:
    host, _, port = value.partition(":")
    return (host or "127.0.0.1", int(port or 8788))


HOST, PORT = _bind(os.environ.get("BROWSER_BIND", "127.0.0.1:8788"))
TOKEN = os.environ.get("BROWSER_TOKEN", "").strip()

# **One port per process, and a registry to find them.**
#
# Hermes runs several processes that all load this plugin: `gateway run` (the app's
# chat, the api_server) and `serve --port 9119` (the dashboard). The agent's browser lives in
# **the** process running the turn — and it is rarely the one that took 8788 first.
#
# Before this registry, the second copy died with `EADDRINUSE`: it marked itself "started" without
# listening anywhere, and the takeover tool raised `RuntimeError: Event loop is closed`.
#
# Now each process listens — 8788 if free, an ephemeral port otherwise — and drops its
# record here. `onyx-one` chooses the record of the one with a pending request, so the phone
# screen always arrives at the process holding the browser.
PEERS_DIR = Path(os.environ.get("BROWSER_PEERS") or (Path.home() / ".hermes" / "onyx-browser" / "peers"))
PEER_TTL_S = 20.0        # beyond that, the record is a ghost: `onyx-one` ignores it
PEER_BEAT_S = 2.0        # record refresh


def _peer_file(pid: int | None = None) -> Path:
    return PEERS_DIR / f"{pid or os.getpid()}.json"


# **A single record write at a time, and a temporary name per write.**
#
# The heartbeat (every 2 s) and `notify_pending` write the same record from two different
# threads. Both went through the **same** temporary `<pid>.tmp`: one truncated while
# the other was writing, and `os.replace` published a hybrid JSON. Observed for real:
#
#     {"…", "ts": 1793846556.4075263}.4075465}
#
# A relay that cannot read the record ignores the process — so the phone screen goes
# to port 8788, that is, to another process, and waits for a page that is not there. The
# defect does not look like an error: it looks like a blank screen.
_ECRITURE = threading.Lock()


def _peer_avec_page() -> int | None:
    """The pid of **another** process that shows a real page.

    Two Hermes processes run in parallel — the gateway (`gateway run`) and the dashboard
    (`hermes serve`) — and each can have its own browser. The agent's browser
    lives in the one running the turn; the other has nothing to show. A screen connecting to the
    wrong one therefore stays on "Waiting…" indefinitely, and the relay — which pins the ticket
    on the sender — sends it back to the same place on every attempt.

    We therefore look at the records in the shared directory: if another process has a page (`ok`),
    that is where the screen must go. The record is refreshed every two seconds and
    stale after twenty: it is only a hint, hence the pid check.

    **Unless it is the same browser.** Both processes can be attached to the *same*
    Chromium (DevTools discovery finds it through `/proc`, with no distinction of owner):
    the DevTools port is then identical. In that case, the other process is not showing "the
    page elsewhere" — it shows **the same page**, and sending the screen there cuts it off in the
    middle for nothing. We therefore ignore it, and the screen stays where the page really is.
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
        # **The same browser is not "elsewhere".** We only send the screen to the other
        # process if it holds an *other* Chromium.
        if notre_port != -1 and port_fiche != -1 and port_fiche == notre_port:
            continue
        return pid
    return None


def write_peer(**extra: Any) -> None:
    """Drop (or refresh) this process's record. Without a server, there is nothing to drop."""
    if HUB.port is None:
        return
    # The CDP probe is slow and cached: it is done **outside** the lock.
    page = page_snapshot()
    chemin = _peer_file()
    with _ECRITURE:
        approach = {
            "pid": os.getpid(),
            "port": HUB.port,
            # Read under the lock: a request posted by `notify_pending` can no longer be
            # overwritten by a heartbeat that started just before.
            "pending": HUB.pending,
            # **`browser` means "there is a page to show", not "this process has already had
            # a browser".** The flag taken from `STATE` stayed true after the page closed:
            # the relay then chose this process for the phone screen, which
            # had nothing to show. `health` and the record must say the same thing.
            "browser": page["state"] == "ok",
            # **What this process can really show.** Two Hermes processes each hold
            # their browser: the record says which one has a real page, so the relay does not send
            # the phone screen to an empty tab.
            "page": page["url"],
            "state": page["state"],
            # **The browser's identity** (its DevTools port). Two processes attached to the same
            # Chromium share it: without this value, one takes itself for "the page is
            # elsewhere" and sends the phone screen to the other — for the same page.
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
    """A stale record is a dead record: we keep it fresh as long as the loop lives."""
    while True:
        write_peer()
        await asyncio.sleep(PEER_BEAT_S)


def clear_pending() -> None:
    HUB.pending = None
    write_peer()


# **A refusal holds for the conversation and the page where it was said.**
#
# Without memory, refusing was useless: the next turn called the tool again on the same page,
# a fresh request was published with a new identifier, and the card came back — on every
# rewritten prompt, in the conversation where the no had just been said as in the others. The
# refusal was therefore not an answer, only a gesture with no follow-up.
#
# The key is the pair (conversation, page): elsewhere — another site, another conversation —
# a new request is legitimate and is displayed.
DECLINE_TTL_S = 30 * 60
DECLINES: dict[tuple[str, str], float] = {}
_DECLINES = threading.Lock()


def _url_key(url: str) -> str:
    """A page, without its anchor or trailing slash: two forms of the same address are one refusal."""
    return str(url or "").split("#", 1)[0].rstrip("/")


def page_key() -> str:
    """The identity of what was refused: the page, without its anchor or trailing slash."""
    try:
        url = str(page_snapshot().get("url") or "")
    except Exception:
        url = ""
    return _url_key(url)


def note_declined(session: str, url: str | None = None) -> None:
    """Retain a refusal, so the same request does not ask the same question again."""
    key = (str(session or ""), page_key() if url is None else _url_key(url))
    with _DECLINES:
        DECLINES[key] = time.time()


def declined_recently(session: str, url: str | None = None) -> bool:
    """True if this conversation has already refused control on this page."""
    key = (str(session or ""), page_key() if url is None else _url_key(url))
    with _DECLINES:
        at = DECLINES.get(key)
        if at is None:
            return False
        if time.time() - at > DECLINE_TTL_S:
            DECLINES.pop(key, None)
            return False
        return True


# **The browser dies of inactivity while the human types.**
#
# `browser.inactivity_timeout` is 120 s by default, and the Hermes sweeper closes the session
# when no `browser_*` tool has been called. But a takeover blocks the turn for up to 15
# minutes **without calling a tool**: the page was therefore closed after two minutes, and
# the phone screen showed `about:blank` — a login form disappearing while
# it is being filled in. We explicitly ask Hermes to count this time as activity.
KEEPALIVE_S = 20.0


def _touch() -> None:
    """Count the takeover time as activity for the Hermes sweeper.

    **The session name is not always bare.** The sweeper indexes `_session_last_activity`
    sometimes under `api-xxx`, sometimes under `api-xxx@/home/john/.hermes` depending on the process that opened
    the browser. Refreshing only the form received by the hook let the other expire: the
    page closed after 120 s, in the middle of a form, and the phone screen switched to
    `about:blank`. We therefore refresh **all** the keys that designate this session.
    """
    task_id = STATE.get("task_id")
    if not task_id:
        return
    try:
        from tools import browser_tool_lifecycle as cycle
    except Exception:  # Hermes version without this hook: the takeover remains possible
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
    """A heartbeat **as long as an agent browser exists**, not only while we are watching.

    **The page died between two turns.** `_touch()` was only called if a takeover
    was waiting or a screen was connected — that is, precisely when nobody needs
    to open the screen for the first time. The human therefore opened the panel on a page already
    reaped by the inactivity sweeper, and read "no page is open" whereas
    the agent had just loaded it. Keeping the browser alive as long as a `task_id` is known is
    the expected behavior of a screen that can be consulted at any time.

    **And there was a second timer, the daemon's.** Even refreshed by `_touch()`, the
    `agent-browser` daemon stopped by itself after ten minutes: it only sees its
    own CLI commands, and the plugin's CDP traffic is invisible to it. The Chromium left with
    it, and the phone screen — open, waiting — read "no page is open" for
    a page that still existed two minutes earlier. The heartbeat therefore also talks to the daemon,
    through its CLI (`get cdp-url`, which does not navigate).
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
    """Restart the timer of the `agent-browser` daemon holding the current browser.

    **Without this, the page eventually disappears from the screen.** The daemon only sees the
    CLI commands it receives: the plugin, which speaks CDP directly, is invisible to it. `get cdp-url` does not
    navigate — it re-reads the DevTools URL — and its timer restarts.

    We only talk to the daemon of the browser **really attached**: an unknown daemon does not deserve
    to be restarted, and the plugin does not know which one is alive when it has no page.
    """
    port = int(_PAGE.get("port") or -1)
    if port <= 0:
        # The port is only known if a probe ran recently. We trigger one:
        # it refreshes `_PAGE` and returns the attached browser's port.
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
    """Count the screen's presence as activity for the Hermes garbage collector.

    **The Hermes garbage collector is a second timer, independent of the daemon.** It closes
    sessions whose last `browser_*` tool call is older than
    `browser.inactivity_timeout`. But `_touch()` can do nothing without `task_id` — and it is
    empty for any gateway session (`attached: false` in `health`). The session was therefore
    reaped at the same time as the daemon, and the screen read "no page is open".

    We therefore refresh the activity of **the session carrying this browser**, found by the
    daemon's session name (`session_name`), and not by a `task_id` we do not have.
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
    """The `task_id` of the live browser, read from the supervisor registry.

    **The hook is not enough.** `model_tools._CallIds.task_id` is `""` when the call comes
    from a gateway session: `STATE["task_id"]` therefore stayed empty, `health` announced
    `attached: false`, and the phone screen had no page to capture — whereas the
    supervisor held one. We therefore ask directly whoever knows.
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
    """The identifier retained for this process: the hook first, the registry next."""
    task_id = str(STATE.get("task_id") or "")
    if not task_id:
        task_id = _registry_task_id()
        if task_id:
            STATE["task_id"] = task_id
    return task_id


def _controller() -> Controller:
    """The controller of the turn's browser — even without `task_id`.

    **`task_id` is not a prerequisite.** It is only used by the first path (the supervisor
    seam); the second (DevTools, read from `/proc`) does not want it. Requiring an identifier
    here turned "no supervisor" into "no browser" and the phone screen
    announced a missing page whereas the Chromium was displaying the page.
    """
    return Controller(_task_id())


def _phone_css(size: dict[str, Any] | None = None) -> tuple[int, int, float]:
    """The CSS size to set so the capture makes **exactly** the screen's pixels.

    The app announces its screen in physical pixels (`{width, height, dpr}`). We keep a DPR of 2 —
    that of the targeted phones, and the one for which pages are written — and derive the
    CSS width: `css = pixels / 2`. The capture is then worth `css × 2`, that is, the phone's
    definition, with no upscaling at display time: this is where the blur disappears.

    Bounded: never less than 200 px of CSS (a tiny page is not operable), never more
    than 1 400 (beyond that, the frame weighs more than the network carries).
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
    """Give the page the thumb's shape, or take it away.

    **The viewport follows the wheel, not the tool.** The tool set the mobile viewport before
    ringing, but the app can also take control on its own: the page then stayed at 1280 px reduced to
    the width of a phone — the unreadable form and the finger beside the field. Setting it here
    guarantees the same page for the one driving, whatever path brought them there.
    """
    try:
        controleur = _controller()
    except Exception:
        return  # no page: nothing to lay out
    try:
        if phone:
            # **`repaint_viewport`, not `use_phone_viewport`.** It first clears the emulation in
            # place then reloads: without this cleanup, an emulation already set (by the browser
            # of the agent, by a previous screen) overlaps and the capture comes out as a **mosaic** —
            # the page repeated 2×2, unreadable and impossible to drive.
            await asyncio.to_thread(controleur.repaint_viewport, *_phone_css(size))
        else:
            await asyncio.to_thread(controleur.clear_viewport)
    except Exception:
        # A refused viewport must not make the takeover fail: we prefer a desktop
        # screen to a "Take over" button that does not respond.
        pass


# -- authentication -------------------------------------------------------------------------

def _authorised(request: web.Request) -> bool:
    if not TOKEN:
        # Without a token, we only accept the loopback interface: `onyx-one` is the only caller.
        peer = (request.remote or "").split("%")[0]
        return peer in ("127.0.0.1", "::1", "")
    header = request.headers.get("Authorization") or ""
    if header.lower().startswith("bearer "):
        header = header[7:].strip()
    header = header or (request.headers.get("X-Browser-Token") or "").strip()
    return bool(header) and header == TOKEN


def _refused(reason: str, status: int = 401) -> web.Response:
    return web.json_response({"ok": False, "error": reason}, status=status)


# -- is the page still there? ---------------------------------------------------------------

#: A CDP probe costs a few milliseconds, and `health` is called by the relay on every
#: screen opening. We keep the answer for two seconds: fresh enough not to route the screen
#: to a dead page, rare enough not to harass Chromium.
ALIVE_CACHE_S = 2.0
#: How long the takeover tool sleeps before asking again whether the page is still alive.
SONDE_S = 2.0
#: `state`: `ok`, `blank` (fresh browser, nothing in it) or `gone` (no browser at all).
#: `url`: what the page shows, so the app can display it instead of guessing.
_PAGE: dict[str, Any] = {"at": 0.0, "state": "gone", "url": "", "port": -1}


def page_snapshot() -> dict[str, str]:
    """The state of the turn's page, with two seconds of memory."""
    now = time.time()
    if now - float(_PAGE["at"]) < ALIVE_CACHE_S:
        return {"state": str(_PAGE["state"]), "url": str(_PAGE["url"])}
    task_id = _task_id()
    etat, url = "gone", ""
    port = -1
    # **No `if task_id`.** DevTools discovery does not need an identifier: requiring
    # a `task_id` here caused "no browser" to be announced while the page was there, and the phone
    # screen stayed blank.
    try:
        controleur = Controller(task_id)
        etat = controleur.page_state()
        url = controleur.current_url() if etat == "ok" else ""
        # **The DevTools port of the attached browser.** It is the browser's identity: two
        # processes attached to the same Chromium must recognize each other, otherwise one of them concludes
        # that "the page is elsewhere" and sends the phone screen to the other.
        port = controleur.browser_port()
    except Exception:
        etat, url = "gone", ""
    _PAGE.update(at=now, state=etat, url=url, port=port)
    return {"state": etat, "url": url, "port": port}


def page_alive() -> bool:
    """Does the turn's page really show something?

    **`blank` counts as dead.** Otherwise the tool waited fifteen minutes in front of an empty tab,
    and the model announced to the human a takeover that had no page to show.
    """
    return page_snapshot()["state"] == "ok"


# -- routes -----------------------------------------------------------------------------------

async def health(request: web.Request) -> web.Response:
    if not _authorised(request):
        return _refused("token")
    waiting = SESSION.waiting
    # **A request without a page is not a request.** The Hermes browser dies after two
    # minutes without a tool: a takeover left pending becomes a ghost that the relay
    # keeps preferring — the phone screen then ended up at a process that could
    # show it nothing. We declare it dead here, and remove the flag so nothing is routed
    # there anymore. The tool, for its part, sees it through its own probe and hands back without waiting 15 minutes.
    alive = page_alive()
    page = page_snapshot()
    if waiting is not None and not alive:
        clear_pending()
        waiting = None
    return web.json_response(
        {
            "ok": True,
            "plugin": "onyx-browser",
            # **`browser` means "there is a page to show", not "this process has already had a
            # browser".** The flag taken from `STATE` stayed true after the page closed,
            # and the relay routed the phone screen to an empty process (`idle` instead of
            # images). `attached` keeps track of the turn, for diagnostics.
            "browser": alive,
            "attached": bool(STATE.get("task_id")),
            "alive": alive,
            # **The screen needs to know what there is to show.** `blank` is not a failure:
            # it is a fresh browser, and the app can say so ("reopen the page") instead of
            # displaying blank with no explanation.
            "page": page,
            # **The capture settings, in plain sight.** After a plugin change, it is the only
            # way to know what the *live* process is executing: `hermes` does not reload
            # modules hot when the hook is inert, and two values identical to the file
            # say nothing about memory.
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
                    # **Which conversation the request belongs to.** The app no longer guesses: it
                    # displays the card in the thread that asked, and in no other.
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
    # **A screen that arrives at the wrong process must leave elsewhere.** The relay pins
    # the ticket on the sender: without this refusal, the app asked for a ticket, the plugin
    # served it, and the screen stayed on "Waiting…" while the real page was playing at
    # the other process. By refusing (`409`), the app asks for a ticket again — and the relay, which
    # then sees that this process has nothing to show, routes the screen to the one with the page.
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
        # **The page takes the thumb's shape as soon as the human drives.** The tool did it before
        # ringing, but the app can also take control alone ("Take over" button):
        # it then inherited a viewport of 1280 px reduced to the screen width — tiny
        # text, three-pixel targets, and a tap that lands beside.
        await _pose_viewport(True)
        await _broadcast({"type": "lease", **state})
        return web.json_response({"ok": True, **state})
    if action == "viewport":
        # **The screen that watches says its size, and the page takes that shape.** This is what
        # replaces the fixed 390×844 template: the capture then arrives at the phone's
        # definition instead of being upscaled at display time (the reported soft image).
        taille = (body or {}).get("size")
        if isinstance(taille, dict):
            SESSION.phone_size = taille
            if SESSION.holder == "human":
                await _pose_viewport(True, taille)
        return web.json_response({"ok": True, "css": list(_phone_css()[:2])})
    if action == "decline":
        # **Refusing is a right, not a lack of response.** Without this action, the only outcome
        # was to do nothing: the card stayed displayed, the agent waited up to 15 minutes,
        # and the request followed the person into every open conversation. Here, the refusal
        # is **said**: the request drops, the agent resumes its turn with the reason, and the app can
        # remove the card immediately.
        waiting = SESSION.waiting
        if waiting is None:
            clear_pending()
            return web.json_response({"ok": True, "declined": False, "reason": "nothing_pending"})
        waiting.refused = True
        waiting.note = str((body or {}).get("note") or "")
        # **The refusal is retained.** Without this memory, the next turn called the tool on the
        # same page and the card came back — which is exactly what was reported.
        note_declined(waiting.session, waiting.url or None)
        waiting.done.set()
        clear_pending()
        await _broadcast({"type": "takeover_done", "id": waiting.takeover_id, "refused": True})
        return web.json_response({"ok": True, "declined": True, "id": waiting.takeover_id})
    if action == "release":
        state = SESSION.release(str((body or {}).get("note") or ""))
        # Handing back control also gives the page back its desktop shape: the agent resumes its turn, and
        # it must not inherit the phone template.
        await _pose_viewport(False)
        await _broadcast({"type": "lease", **state})
        return web.json_response({"ok": True, **state})
    return _refused("action", status=400)


# -- the WebSocket -----------------------------------------------------------------------------

async def websocket(request: web.Request) -> web.WebSocketResponse:
    ticket = SESSION.redeem(request.query.get("ticket", ""))
    if ticket is None:
        # A dead ticket does not get a 101: the handshake is refused outright.
        raise web.HTTPUnauthorized(text="ticket")
    ws = web.WebSocketResponse(heartbeat=30)
    await ws.prepare(request)
    STATE["clients"].add(ws)
    # **A screen that opens sees the page right away.** Without this frame, it waited for the
    # next pump round — and stayed on "Waiting for the agent's browser…" while
    # the page already existed.
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
        # **A closed screen does not keep the wheel.** The lease was taken from the phone: if
        # the app closes, loses the network or changes conversation, nobody hands it back. The lease
        # stayed human forever, and the next takeover was refused with `409` — a
        # ghost "someone is already driving", impossible to clear without restarting Hermes.
        if not STATE["clients"]:
            asyncio.ensure_future(_release_when_abandoned())
    return ws


#: How long a screen has to come back before we consider it gone. A new ticket and a
#: reconnection take less than a second; twenty leaves room for a network change.
RELEASE_GRACE_S = 20.0


async def _release_when_abandoned() -> None:
    await asyncio.sleep(RELEASE_GRACE_S)
    if STATE["clients"] or SESSION.holder != "human":
        return
    state = SESSION.abandon()
    await _broadcast({"type": "lease", **state})
    # **The screen gone, the page regains its shape.** The phone viewport was set for the
    # takeover; if the app closes without handing back control, it stayed applied and the agent
    # inherited a page 390 px wide — unreadable for it, whereas it believed it was getting
    # the page back as it had left it.
    await _pose_viewport(phone=False)
    log.info("onyx-browser: écran parti, le bail revient à l'agent")


async def _on_frame(payload: dict[str, Any], ws: web.WebSocketResponse) -> None:
    kind = payload.get("type")
    if kind == "watch":
        # Nothing to do: the pump runs as long as there is a client. We send back the state.
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
                # **The server's measurement is worth more than the app's.** The app sends the CSS
                # size it read on the last frame; if it did not read it (frame
                # received before the fix, or an older app version), it sends
                # zero — and the gesture was replayed on a zero-pixel viewport, that is,
                # in the corner of the screen. We then fall back on what the server measured itself.
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
        # **The facts before the wake-up.** `release()` unblocks the agent's tool: the facts
        # must already be stored when it wakes up, otherwise it reads an empty dictionary and
        # announces to the model "no form" whereas the page had one.
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
    """An image after each `browser_*` tool, even when nobody is watching.

    The pump only runs if there is a spectator: the open screen therefore received its first
    image on the first pump round, and a screen reopened later saw nothing of what had
    happened in the meantime. We capture a single frame here, without broadcasting, which serves as a guard for the
    next connection (see `websocket`). Called from a thread that is not the loop's.
    """
    if STATE["clients"]:
        return  # the pump is already running: it will produce the frame
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
    """One frame at a time, as long as a client is watching. Nobody is watching: nothing runs."""
    idle_sent = False
    dernier_idle = 0.0
    while STATE["clients"]:
        try:
            trame = await _une_trame()
        except Exception:
            # **A capture failure does not kill the screen.** It is logged with its traceback, then
            # we retry: without this safeguard, the pump task died and the phone screen
            # froze without a word in the log.
            log.exception("onyx-browser: capture en échec — le pompage continue")
            await asyncio.sleep(IDLE_DELAY_S)
            continue
        if trame:
            idle_sent = False
            await _broadcast(trame)
            await asyncio.sleep(DRIVE_DELAY_S if SESSION.holder == "human" else WATCH_DELAY_S)
        else:
            # **`idle` is a heartbeat, not an announcement.** Sent only once, it left
            # the screen in absolute silence: the app's watchdog, which only counts
            # frames, never fired, and the socket dead in silence behind the relay
            # was never noticed. The screen therefore stayed on "Waiting…" forever,
            # even when the page reappeared. We therefore repeat `idle` — this is what lets
            # the app distinguish "the stream is alive but there is no page" from "the stream
            # is dead", and reconnect by itself in the second case.
            maintenant = time.monotonic()
            if not idle_sent or (maintenant - dernier_idle) >= IDLE_BEAT_S:
                # **Say why**, not just "nothing": `blank` (fresh browser, empty tab)
                # and `gone` (no browser) require two different actions from the human.
                await _broadcast({"type": "idle", "state": page_snapshot()["state"]})
                idle_sent = True
                dernier_idle = maintenant
            # **A screen stuck at the wrong process must leave.** The page lives in another
            # process: we tell the screen, which then asks for a new ticket — and the relay
            # routes it to the right one this time. Without that, the app stayed on "Waiting…" for
            # ever, whereas the page existed and was visible elsewhere.
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
    """Capture the page, and return the frame to broadcast — `None` if there is nothing to show.

    **The CSS size travels with the frame.** It was stripped just before broadcasting
    (`frame.pop("css")`), and the app therefore received `cssWidth = 0`: its gestures were computed on
    a zero-pixel viewport, and the server replayed them in the corner of the screen. The server keeps the
    measurement for itself, but it also broadcasts it: it is what tells the app where to put the finger.
    """
    try:
        controller = _controller()
        # The capture mode follows the wheel: while the human drives, the frame is full
        # resolution and readable; otherwise it stays light (3 fps monitoring).
        conduit = SESSION.holder == "human"
        frame = await asyncio.to_thread(
            controller.frame,
            DRIVE_SCALE if conduit else WATCH_SCALE,
            DRIVE_QUALITY if conduit else WATCH_QUALITY,
        )
    except (BrowserUnavailable, TimeoutError, RuntimeError, KeyError, IndexError, TypeError) as erreur:
        # **We only catch what means "no page".** A bare `except Exception`
        # turned a real failure (missing attribute, frame bug) into a silent `idle`:
        # the screen showed "nothing to show" and nobody saw the error. The rest propagates,
        # and `_pump` logs it.
        log.debug("onyx-browser: pas de trame (%s)", type(erreur).__name__)
        return None
    if not frame:
        return None
    seq = SESSION.next_seq()
    css = frame.get("css") or [0, 0]
    SESSION.viewport = (int(css[0]), int(css[1]))
    trame = {"type": "frame", "seq": seq, "live": True, **frame}
    # **The last image is not lost.** A screen that opens between two frames waited for the
    # next pump round (or stayed empty if no pump was running): the thumbnail captured
    # after each `browser_*` tool is therefore kept here, and served on connection.
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


# -- startup --------------------------------------------------------------------------------

def build_app() -> web.Application:
    app = web.Application()
    app.router.add_get("/onyx-browser/health", health)
    app.router.add_post("/onyx-browser/observe", observe)
    app.router.add_post("/onyx-browser/lease", lease)
    app.router.add_get("/onyx-browser/ws", websocket)
    # The same paths without the prefix: `onyx-one` can relay either one.
    app.router.add_get("/health", health)
    app.router.add_get("/ws", websocket)
    return app


async def _serve() -> None:
    runner = web.AppRunner(build_app(), access_log=None)
    await runner.setup()

    # **The preferred port, otherwise a free one**: never the exception that kills the loop.
    #
    # `TCPSite` raises `EADDRINUSE` when another Hermes process already holds 8788 — and
    # `asyncio.run` then closed the loop, leaving `HUB.loop` pointing at a corpse. We take
    # the socket ourselves: we then know which port we landed on, and we publish it.
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind((HOST, PORT))
    except OSError as erreur:
        if getattr(erreur, "errno", None) not in (98, 48):  # EADDRINUSE: Linux, macOS
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
    # **The loop is only retained once the socket is actually listening.**
    HUB.loop = asyncio.get_running_loop()
    write_peer()
    atexit.register(drop_peer)
    STARTED.set()
    log.info("onyx-browser: en écoute sur %s:%s/onyx-browser/health", HOST, HUB.port)
    asyncio.ensure_future(_heartbeat())
    # **The server lives as long as the loop.** `_serve()` returned right after
    # startup: `asyncio.run()` then closed the loop and the runner fell with it — the
    # port closed again a handful of milliseconds after opening (connection reset,
    # never a 500). We wait here, forever.
    await asyncio.Event().wait()


def ensure_started() -> None:
    """Start the server once, in its own thread: `register()` is synchronous.

    The lock is not decorative: Hermes calls `register()` several times (launch, then
    each profile), and two close calls both saw `STARTED` empty — two `TCPSite`
    on the same port, an `EADDRINUSE`, and the plugin looked dead.
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
    """Notify open screens, from a thread that is not the loop's."""
    boucle = HUB.loop
    if boucle is None or boucle.is_closed():
        return False
    try:
        asyncio.run_coroutine_threadsafe(_broadcast(payload), boucle)
        return True
    except RuntimeError:
        return False


def notify_pending(takeover_id: str, reason: str, session: str = "") -> bool:
    """Notify clients already on screen that a request has just arrived.

    Returns `False` — without raising — when no server lives in this process: this is
    useful information for the tool, not a failure of the caller.
    """
    boucle = HUB.loop
    if boucle is None or boucle.is_closed() or HUB.port is None:
        log.warning("onyx-browser: aucune boucle vivante, la prise en main reste locale")
        return False
    # **The record says "request pending"**: this is the flag `onyx-one` reads to send
    # the phone screen to THIS process rather than the one holding port 8788.
    HUB.pending = takeover_id
    write_peer()
    _touch()
    try:
        asyncio.run_coroutine_threadsafe(
            _broadcast({"type": "takeover", "id": takeover_id, "reason": reason, "session": session}), boucle
        )
        return True
    except RuntimeError:  # loop closed in the meantime
        log.warning("onyx-browser: boucle fermée pendant la notification")
        clear_pending()
        return False
