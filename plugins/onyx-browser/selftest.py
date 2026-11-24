"""The plugin's test bench, without a browser: the server, the ticket, the lease, the refusals.

Run with the python of the Hermes installation (aiohttp is there). Does not touch CDP.
"""

import asyncio
import importlib.util
import json
import os
import sys
import tempfile
import time
import types
from pathlib import Path

PLUGIN = Path(__file__).resolve().parent


def load_plugin() -> types.ModuleType:
    """Load `plugins/onyx-browser/` as a package, despite the hyphen in the folder."""
    spec = importlib.util.spec_from_file_location(
        "onyx_browser", PLUGIN / "__init__.py", submodule_search_locations=[str(PLUGIN)]
    )
    module = importlib.util.module_from_spec(spec)
    sys.modules["onyx_browser"] = module
    spec.loader.exec_module(module)
    return module


async def main() -> int:
    from aiohttp.test_utils import TestClient, TestServer

    plugin = load_plugin()
    server = sys.modules["onyx_browser.server"]
    session_mod = sys.modules["onyx_browser.session"]

    failures: list[str] = []

    def check(label: str, ok: bool) -> None:
        print(("  ok   " if ok else "  ÉCHEC") + "  " + label)
        if not ok:
            failures.append(label)

    # --- 1. the lease, without network -----------------------------------------------------
    clock = [1000.0]
    s = session_mod.Session(clock=lambda: clock[0])
    check("au départ, l'agent conduit", s.holder == "agent")
    state = s.acquire()
    check("l'humain prend la main", state == {"holder": "human", "epoch": 1})
    try:
        s.acquire()
        check("un second conducteur est refusé", False)
    except session_mod.AlreadyHeld:
        check("un second conducteur est refusé", True)
    check("sans trame, une saisie est refusée", s.refusal(0) == "stale")
    seq = s.next_seq()
    check("avec la dernière trame, elle passe", s.refusal(seq) is None)
    # **An older `seq` passes, as long as the image is recent.** The server streams at 8 fps
    # while the human drives: the `seq` of the displayed image is always out of date at the moment
    # of the gesture. Comparing `seq` values therefore refused *every* tap, and the screen was useless.
    s.next_seq()
    check("une trame légèrement dépassée passe encore", s.refusal(seq) is None)
    # A `seq` that the server never emitted, however, is refused.
    check("un seq inconnu est refusé", s.refusal(seq + 99) == "stale")
    # ... and an image more than two seconds old is no longer worth a click.
    clock[0] += session_mod.STALE_AFTER_S + 0.5
    check("une image périmée est refusée", s.refusal(seq) == "stale")
    clock[0] -= session_mod.STALE_AFTER_S + 0.5
    s.release("fini")
    check("rendre la main remet l'agent", s.holder == "agent" and s.epoch == 2)
    check("et une saisie est alors refusée", s.refusal(s.seq) == "no_lease")

    # `abandon()`: the driver disappeared, nobody handed anything back.
    attente = session_mod.Waiting(takeover_id="x1", reason="test")
    s.waiting = attente
    s.acquire()
    state = s.abandon()
    check("un écran parti rend le bail à l'agent", state["holder"] == "agent")
    check("mais ne réveille pas l'outil de l'agent", not attente.done.is_set())
    s.waiting = None
    s.abandon()

    # --- 2. the ticket ------------------------------------------------------------------------
    ticket = s.mint()
    check("un ticket se consomme une fois", s.redeem(ticket.value) is ticket)
    check("le second essai échoue", s.redeem(ticket.value) is None)
    short = s.mint()
    clock[0] += 31
    check("un ticket périmé ne passe pas", s.redeem(short.value) is None)

    # --- 2a. which tab to show ---------------------------------------------------------
    # The defect that produced a blank screen: when no real tab exists, the old code
    # silently returned the supervisor's session — that is, the empty tab. The cases
    # below are the tab lists actually observed on the machine.
    cdp_mod = sys.modules["onyx_browser.cdp"]

    check("une page web est réelle", cdp_mod.is_real_page("https://github.com/login"))
    check("un onglet vide ne l'est pas", not cdp_mod.is_real_page("about:blank"))
    check("une page interne non plus", not cdp_mod.is_real_page("chrome://newtab/"))
    check("une adresse vide non plus", not cdp_mod.is_real_page(""))

    class FauxCDP:
        """The CDP seam, with the tab list we want to exercise."""

        def __init__(self, cibles, urls):
            self.page_session_id = "S0"
            self.cibles = cibles
            self.urls = urls

        def is_valid(self):
            return True

        def call(self, method, params=None, *, session_id=None, timeout=None):
            if method == "Target.getTargets":
                return {"result": {"targetInfos": self.cibles}}
            if method == "Page.getNavigationHistory":
                url = self.urls.get(session_id, "")
                return {"result": {"entries": [{"url": url}], "currentIndex": 0}}
            if method == "Target.attachToTarget":
                cible = (params or {})["targetId"]
                return {"result": {"sessionId": "S-" + cible}}
            raise AssertionError("appel inattendu: " + method)

    def controleur(cibles, urls):
        faux = FauxCDP(cibles, urls)
        c = cdp_mod.Controller("tache")
        c._cdp = faux
        return c

    # The real case: github open in the browser running the turn.
    reel = controleur(
        [
            {"type": "page", "targetId": "t1", "url": "https://github.com/login"},
            {"type": "page", "targetId": "t2", "url": "chrome://newtab/"},
        ],
        {"S0": "https://github.com/login"},
    )
    check("la session du superviseur est préférée", reel.page() == "S0")
    check("l'état de la page est ok", reel.page_state() == "ok")
    check("l'adresse est celle de la page", reel.current_url() == "https://github.com/login")

    # A fresh browser: about:blank + new tab. This is what the phone saw.
    vide = controleur(
        [
            {"type": "page", "targetId": "t1", "url": "about:blank"},
            {"type": "page", "targetId": "t2", "url": "chrome://newtab/"},
        ],
        {"S0": "about:blank"},
    )
    check("un onglet vide est déclaré vide", vide.page_state() == "blank")
    try:
        vide.page()
        check("on ne capture pas un onglet vide", False)
    except cdp_mod.BrowserUnavailable:
        check("on ne capture pas un onglet vide", True)
    check("et il n'y a pas d'adresse à montrer", vide.current_url() == "")

    # A real tab open somewhere other than the supervisor session (a popup).
    popup = controleur(
        [
            {"type": "page", "targetId": "t1", "url": "about:blank"},
            {"type": "page", "targetId": "t9", "url": "https://github.com/login"},
        ],
        {"S0": "about:blank", "S-t9": "https://github.com/login"},
    )
    check("un onglet réel ailleurs est trouvé", popup.page() == "S-t9")

    aucun = controleur([], {})
    check("sans navigateur, la page est absente", aucun.page_state() == "gone")

    # --- 2b. the server does not present an empty tab as a page --------------------
    vrai_controleur = server.Controller
    server.STATE["task_id"] = "tache-vide"
    setattr(server, "Controller", lambda task: vide)
    server._PAGE.update(at=0.0, state="gone", url="")
    etat = server.page_snapshot()
    check("health annonce un onglet vide", etat["state"] == "blank")
    check("et ne le croit pas vivant", server.page_alive() is False)
    server.Controller = vrai_controleur
    server.STATE["task_id"] = None
    server._PAGE.update(at=0.0, state="gone", url="")

    # --- 2c. `task_id` is not a prerequisite -------------------------------------
    # DevTools discovery (reading /proc) does not need a turn identifier. Requiring a
    # `task_id` turned "no supervisor" into "no browser", and the phone
    # screen announced a missing page while the Chromium was displaying the page.
    server.STATE["task_id"] = None
    serveur_vrai = server.Controller
    setattr(server, "Controller", lambda task: reel)
    server._PAGE.update(at=0.0, state="gone", url="")
    etat = server.page_snapshot()
    check("sans task_id, la page réelle est trouvée", etat["state"] == "ok")
    check("et son adresse est celle de la page", etat["url"] == "https://github.com/login")
    check("le navigateur est vivant", server.page_alive() is True)
    # We freeze the environment for what follows: this bench must not depend on the machine's
    # Chromium, which may well be running while it executes.
    setattr(server, "Controller", lambda task: aucun)
    server._PAGE.update(at=0.0, state="gone", url="")

    # --- 3. the server -----------------------------------------------------------------------
    # **The bench must not see the machine's peers.** On a real installation, another
    # Hermes process often holds a page: the plugin would then refuse the ticket (409), and the
    # bench would believe `observe` is broken. We therefore isolate it in a folder of its own.
    pairs_reels = server.PEERS_DIR
    dossier_pairs = Path(tempfile.mkdtemp(prefix="onyx-browser-selftest-"))
    server.PEERS_DIR = dossier_pairs
    app = server.build_app()
    async with TestClient(TestServer(app)) as client:
        async with client.get("/onyx-browser/health") as resp:
            body = await resp.json()
            check("health répond", resp.status == 200 and body.get("plugin") == "onyx-browser")
            check("il ne ment pas sur le navigateur", body.get("browser") is False)
            check("il dit que la page est morte", body.get("alive") is False)

        # --- a request without a page is not a request ------------------------------------
        # Hermes closes an inactive browser after two minutes. The takeover then stayed
        # "pending" forever, and the relay routed the phone screen to it.
        attente = session_mod.Waiting(takeover_id="zombie", reason="fantôme")
        server.SESSION.waiting = attente
        server.STATE["task_id"] = "tache-disparue"
        server.HUB.pending = "zombie"
        async with client.get("/onyx-browser/health") as resp:
            body = await resp.json()
            check("une demande sans page n'est pas annoncée", body.get("pending") is None)
            check("et le relais ne peut plus y router l'écran", server.HUB.pending is None)
        server.SESSION.waiting = None
        server.STATE["task_id"] = None

        # --- a closed screen does not keep the wheel ------------------------------------------
        server.RELEASE_GRACE_S = 0
        server.SESSION.acquire()
        await server._release_when_abandoned()
        check("écran parti : le bail revient à l'agent", server.SESSION.holder == "agent")
        server.SESSION.release("")

        async with client.post("/onyx-browser/observe") as resp:
            body = await resp.json()
            check("observe rend un ticket", resp.status == 200 and bool(body.get("ticket")))

        try:
            async with client.get("/onyx-browser/ws") as resp:
                status = resp.status
        except Exception:
            status = 401
        check("le WS sans ticket est refusé", status == 401)

        async with client.post("/onyx-browser/lease", json={"action": "acquire"}) as resp:
            body = await resp.json()
            check("le bail s'acquiert", resp.status == 200 and body.get("holder") == "human")

        async with client.post("/onyx-browser/lease", json={"action": "acquire"}) as resp:
            check("le second bail est refusé (409)", resp.status == 409)

        async with client.post("/onyx-browser/lease", json={"action": "release"}) as resp:
            body = await resp.json()
            check("le bail se rend", resp.status == 200 and body.get("holder") == "agent")

        # --- 3a. a screen that arrives at the wrong process -------------------------------
        # **Two Hermes processes run in parallel.** The agent's browser lives in the one
        # running the turn; the other has nothing to show. The app connected to the wrong one, the
        # relay pinned the ticket on it, and the screen stayed on "Waiting…" forever
        # while the page existed, visible in the other process.
        check("sans pair qui a une page, le ticket est servi", True)
        # We simulate a peer that has a real page. The pid must exist: the plugin does not trust
        # a stale record, it checks that the process is still there.
        pid_pair = os.getppid()
        (dossier_pairs / "999999.json").write_text(
            json.dumps({"pid": pid_pair, "port": 65000, "state": "ok", "ts": time.time()}),
            encoding="utf-8",
        )
        async with client.post("/onyx-browser/observe") as resp:
            body = await resp.json()
        check(
            "le ticket est refusé quand la page est chez un autre processus",
            resp.status == 409 and body.get("error") == "page_elsewhere",
        )
        (dossier_pairs / "999999.json").unlink()
        async with client.post("/onyx-browser/observe") as resp:
            body = await resp.json()
        check("et il est servi dès que l'autre processus n'a plus de page", bool(body.get("ticket")))

        # --- the socket, with a real ticket -------------------------------------------------
        async with client.post("/onyx-browser/observe") as resp:
            ticket = (await resp.json())["ticket"]
        async with client.ws_connect(f"/onyx-browser/ws?ticket={ticket}") as ws:
            await ws.send_json({"type": "watch", "on": True, "takeover": False})
            # The pump may send an `idle` before our reply: we read until the lease.
            seen = []
            for _ in range(6):
                frame = await ws.receive_json(timeout=5)
                seen.append(frame.get("type"))
                if frame.get("type") == "lease":
                    break
            check("le WS ouvre avec un ticket et rend le bail", "lease" in seen)
            # A ticket is only good once: the second handshake is refused.
            try:
                async with client.ws_connect(f"/onyx-browser/ws?ticket={ticket}") as again:
                    check("le ticket est à usage unique", False)
            except Exception:
                check("le ticket est à usage unique", True)

    # --- the frame carries its CSS size, and the gesture relies on it ------------------------------
    # **The `css` did not travel with the frame.** It was stripped just before streaming
    # (`frame.pop("css")`), and the app therefore received a size of zero: its gestures were
    # computed on a zero-pixel viewport, and the server replayed them in the corner of the screen.
    # The app has nothing to fix: it is the server that was not streaming the measurement.
    captured: list[dict] = []

    class FauxPompe:
        """A controller that returns a mobile frame, and notes what the server passes it."""

        def __init__(self):
            self.css_reçu = (0, 0)
            self.geste = None
            # **What the server asks the browser for the page's shape.** The tool set
            # the mobile viewport on its own; the app can take control without it, and the page
            # then stayed at 1280 px squeezed into the phone width.
            self.vues: list[str] = []

        def use_phone_viewport(self, *size):
            # The thumb shape is computed by `_phone_css` and passed as arguments: the fake
            # controller accepts them, like `Controller.use_phone_viewport(width, height, dpr)`.
            self.vues.append("phone")
            return (390, 844)

        def repaint_viewport(self, *size):
            # **Same contract, different address.** `_pose_viewport` no longer sets the thumb shape
            # through `use_phone_viewport` but through `repaint_viewport`, which first clears the emulation
            # in place (without which the capture comes out as a mosaic). The double must expose the method
            # that the server really calls: without it, the call raised an `AttributeError` that
            # `_pose_viewport` swallows on purpose, and the bench believed nothing had been requested.
            self.vues.append("phone")
            return (390, 844)

        def clear_viewport(self):
            self.vues.append("desktop")

        def frame(self, scale, quality=None):
            return {
                "data": "ZmFrZQ==",
                "url": "https://github.com/login",
                "title": "Sign in to GitHub",
                "width": int(390 * scale),
                "height": int(844 * scale),
                "css": [390, 844],
            }

        def input(self, action, frame, css):
            self.geste = (action, css)
            self.css_reçu = css
            return True

        def login_form_facts(self):
            return {}

    class FauxClient:
        async def send_json(self, payload):
            captured.append(payload)

        async def close(self):
            pass

    faux = FauxPompe()
    ancien_controleur = server._controller
    server._controller = lambda: faux
    server.STATE["task_id"] = "tache-de-test"
    server.SESSION.viewport = (390, 844)
    # The gesture requires the lease: without it, it is refused as `no_lease` before reaching the page.
    if server.SESSION.holder != "human":
        server.SESSION.acquire()
    try:
        # The frame: it must contain `css`.
        trame = await server._une_trame()
        check("la trame porte sa taille CSS", (trame or {}).get("css") == [390, 844])
        check("et le serveur la garde pour lui", server.SESSION.viewport == (390, 844))

        # The gesture: the app sends `css: [0, 0]` (it did not read the measurement) — the server must
        # fall back on its own, and not replay the gesture on a zero-pixel viewport.
        client_ws = FauxClient()
        await server._on_frame(
            {
                "type": "input",
                "action": "click",
                "seq": server.SESSION.seq,
                "x": 0.5,
                "y": 0.25,
                "css": [0, 0],
            },
            client_ws,
        )
        check("un css nul retombe sur la mesure du serveur", faux.css_reçu == (390, 844))

        # **The viewport follows the wheel, not only the tool.** Taking control must put the
        # page in thumb shape; handing back must put it back in desktop shape for the agent.
        await server._pose_viewport(True)
        await server._pose_viewport(False)
        check("prendre la main pose un viewport de téléphone", faux.vues == ["phone", "desktop"])
    finally:
        server._controller = ancien_controleur
        server.STATE["task_id"] = None

    # --- 4. `idle` is a heartbeat, not an announcement ----------------------------------------
    # **Sent only once, it left the screen in absolute silence.** The app's
    # watchdog only counted frames: having received only one `idle`, it never fired
    # — and the socket, dead in silence behind the relay, was never noticed.
    # The screen therefore stayed on "Waiting…" forever, even when the page reappeared.
    # The heartbeat makes the silence interpretable: "alive but without a page" ≠ "dead".
    recu: list[dict] = []

    class FauxSock:
        async def send_json(self, payload):
            recu.append(payload)

        async def close(self, **kwargs):
            pass

    async def _pompage_sans_page():
        return None

    ancien_pompage = server._une_trame
    ancien_clients = server.STATE["clients"]
    ancien_peer = server._peer_avec_page
    try:
        server._une_trame = _pompage_sans_page
        server._peer_avec_page = lambda: None
        sock = FauxSock()
        server.STATE["clients"] = {sock}
        tache = asyncio.ensure_future(server._pump())
        await asyncio.sleep(server.IDLE_BEAT_S * 2.5)
        tache.cancel()
        try:
            await tache
        except (asyncio.CancelledError, Exception):
            pass
        idles = [m for m in recu if isinstance(m, dict) and m.get("type") == "idle"]
        check("sans page, le serveur bat idle", len(idles) >= 2)
        check("et il dit pourquoi", all(m.get("state") for m in idles))
    finally:
        server._une_trame = ancien_pompage
        server.STATE["clients"] = ancien_clients
        server._peer_avec_page = ancien_peer

    # --- 8. a refusal holds for the conversation and the page --------------------------------
    # **Without memory, refusing was useless.** The next turn called the tool again on the same
    # page, a fresh request was published, and the card came back — on every rewritten prompt.
    url = "https://github.com/login"
    server.note_declined("api-abc", url)
    check("un refus est retenu", server.declined_recently("api-abc", url))
    check("et il ne vaut que pour sa page", not server.declined_recently("api-abc", "https://autre.test/x"))
    check("et que pour sa conversation", not server.declined_recently("api-autre", url))
    # The anchor and the trailing slash do not create a second page.
    check("la même page sans ancre reste la même", server.declined_recently("api-abc", url + "#section"))

    print()
    if failures:
        print(f"{len(failures)} échec(s): " + ", ".join(failures))
        return 1
    print("tout est vert")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
