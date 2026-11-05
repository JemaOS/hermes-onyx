"""Banc d'essai du plugin, sans navigateur : le serveur, le ticket, le bail, les refus.

Lancé avec le python de l'installation Hermes (aiohttp y est). Ne touche pas à CDP.
"""

import asyncio
import importlib.util
import sys
import types
from pathlib import Path

PLUGIN = Path(__file__).resolve().parent


def load_plugin() -> types.ModuleType:
    """Charge `plugins/onyx-browser/` comme un paquet, malgré le tiret du dossier."""
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

    # --- 1. le bail, sans réseau -----------------------------------------------------------
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
    s.next_seq()
    # **La fraîcheur se mesure en secondes, pas en numéros de trame.** À 8 img/s pendant la
    # conduite, le `seq` de l'image affichée est toujours dépassé : comparer les `seq` refusait
    # chaque tap, et l'écran ne servait à rien. Ce qui compte, c'est que l'image soit récente.
    check("une trame encore fraîche passe, même dépassée", s.refusal(seq) is None)
    check("un seq inconnu est refusé", s.refusal(seq + 500) == "stale")
    clock[0] += session_mod.STALE_AFTER_S + 1
    check("une trame périmée est refusée", s.refusal(seq) == "stale")
    s.release("fini")
    check("rendre la main remet l'agent", s.holder == "agent" and s.epoch == 2)
    check("et une saisie est alors refusée", s.refusal(s.seq) == "no_lease")

    # `abandon()` : le conducteur a disparu, personne n'a rien rendu.
    attente = session_mod.Waiting(takeover_id="x1", reason="test")
    s.waiting = attente
    s.acquire()
    state = s.abandon()
    check("un écran parti rend le bail à l'agent", state["holder"] == "agent")
    check("mais ne réveille pas l'outil de l'agent", not attente.done.is_set())
    s.waiting = None
    s.abandon()

    # --- 2. le ticket ------------------------------------------------------------------------
    ticket = s.mint()
    check("un ticket se consomme une fois", s.redeem(ticket.value) is ticket)
    check("le second essai échoue", s.redeem(ticket.value) is None)
    short = s.mint()
    clock[0] += 31
    check("un ticket périmé ne passe pas", s.redeem(short.value) is None)

    # --- 2 bis. quel onglet montrer ---------------------------------------------------------
    # Le défaut qui a produit un écran blanc : quand aucun onglet réel n'existe, l'ancien code
    # rendait silencieusement la session du superviseur — c'est-à-dire l'onglet vide. Les cas
    # ci-dessous sont les listes d'onglets réellement relevées sur la machine.
    cdp_mod = sys.modules["onyx_browser.cdp"]

    check("une page web est réelle", cdp_mod.is_real_page("https://github.com/login"))
    check("un onglet vide ne l'est pas", not cdp_mod.is_real_page("about:blank"))
    check("une page interne non plus", not cdp_mod.is_real_page("chrome://newtab/"))
    check("une adresse vide non plus", not cdp_mod.is_real_page(""))

    class FauxCDP:
        """Le seam CDP, avec la liste d'onglets qu'on veut éprouver."""

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

    # Le cas réel : github ouvert dans le navigateur qui exécute le tour.
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

    # Un navigateur neuf : about:blank + nouvel onglet. C'est ce que voyait le téléphone.
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

    # Un vrai onglet ouvert ailleurs que dans la session du superviseur (une popup).
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

    # --- 2 ter. le serveur ne présente pas un onglet vide comme une page --------------------
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

    # --- 3. le serveur -----------------------------------------------------------------------
    app = server.build_app()
    async with TestClient(TestServer(app)) as client:
        async with client.get("/onyx-browser/health") as resp:
            body = await resp.json()
            check("health répond", resp.status == 200 and body.get("plugin") == "onyx-browser")
            check("il ne ment pas sur le navigateur", body.get("browser") is False)
            check("il dit que la page est morte", body.get("alive") is False)

        # --- une demande sans page n'est pas une demande ------------------------------------
        # Hermes ferme un navigateur inactif au bout de deux minutes. La prise en main restait
        # alors « en attente » pour toujours, et le relais y routait l'écran du téléphone.
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

        # --- un écran fermé ne garde pas le volant ------------------------------------------
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

        # --- le socket, avec un vrai ticket -------------------------------------------------
        async with client.post("/onyx-browser/observe") as resp:
            ticket = (await resp.json())["ticket"]
        async with client.ws_connect(f"/onyx-browser/ws?ticket={ticket}") as ws:
            await ws.send_json({"type": "watch", "on": True, "takeover": False})
            # Le pompage peut envoyer un `idle` avant notre réponse : on lit jusqu'au bail.
            seen = []
            for _ in range(6):
                frame = await ws.receive_json(timeout=5)
                seen.append(frame.get("type"))
                if frame.get("type") == "lease":
                    break
            check("le WS ouvre avec un ticket et rend le bail", "lease" in seen)
            # Un ticket ne sert qu'une fois : la seconde poignée de main est refusée.
            try:
                async with client.ws_connect(f"/onyx-browser/ws?ticket={ticket}") as again:
                    check("le ticket est à usage unique", False)
            except Exception:
                check("le ticket est à usage unique", True)

        # --- le viewport suit le volant ------------------------------------------------------
        # L'outil posait le viewport mobile de son côté ; l'app peut prendre la main seule, et
        # la page restait alors en 1280 px écrasés dans la largeur du téléphone.
        class FauxEcran:
            def __init__(self):
                self.vues = []

            def use_phone_viewport(self):
                self.vues.append("phone")
                return (390, 844)

            def clear_viewport(self):
                self.vues.append("desktop")

        ecran = FauxEcran()
        ancien_controleur = server._controller
        ancien_task = server.STATE.get("task_id")
        server._controller = lambda: ecran
        server.STATE["task_id"] = "tache-de-test"
        try:
            await server._pose_viewport(True)
            await server._pose_viewport(False)
        finally:
            server._controller = ancien_controleur
            server.STATE["task_id"] = ancien_task
        check(
            "prendre la main pose un viewport de téléphone, la rendre le retire",
            ecran.vues == ["phone", "desktop"],
        )

    print()
    if failures:
        print(f"{len(failures)} échec(s): " + ", ".join(failures))
        return 1
    print("tout est vert")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
