"""Banc d'essai du plugin, sans navigateur : le serveur, le ticket, le bail, les refus.

Lancé avec le python de l'installation Hermes (aiohttp y est). Ne touche pas à CDP.
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
    # **Un `seq` plus ancien passe, tant que l'image est récente.** Le serveur diffuse à 8 img/s
    # pendant que l'humain conduit : le `seq` de l'image affichée est toujours dépassé au moment
    # du geste. Comparer des `seq` refusait donc *chaque* tap, et l'écran ne servait à rien.
    s.next_seq()
    check("une trame légèrement dépassée passe encore", s.refusal(seq) is None)
    # Un `seq` que le serveur n'a jamais émis, lui, est refusé.
    check("un seq inconnu est refusé", s.refusal(seq + 99) == "stale")
    # ... et une image vieille de plus de deux secondes ne vaut plus un clic.
    clock[0] += session_mod.STALE_AFTER_S + 0.5
    check("une image périmée est refusée", s.refusal(seq) == "stale")
    clock[0] -= session_mod.STALE_AFTER_S + 0.5
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

    # --- 2 quater. le `task_id` n'est pas un prérequis -------------------------------------
    # La découverte DevTools (lecture de /proc) n'a pas besoin d'identifiant de tour. Exiger un
    # `task_id` transformait « pas de superviseur » en « pas de navigateur », et l'écran du
    # téléphone annonçait une page absente pendant que le Chromium affichait la page.
    server.STATE["task_id"] = None
    serveur_vrai = server.Controller
    setattr(server, "Controller", lambda task: reel)
    server._PAGE.update(at=0.0, state="gone", url="")
    etat = server.page_snapshot()
    check("sans task_id, la page réelle est trouvée", etat["state"] == "ok")
    check("et son adresse est celle de la page", etat["url"] == "https://github.com/login")
    check("le navigateur est vivant", server.page_alive() is True)
    # On fige l'environnement pour la suite : ce banc ne doit pas dépendre du Chromium de la
    # machine, qui peut très bien tourner pendant qu'il s'exécute.
    setattr(server, "Controller", lambda task: aucun)
    server._PAGE.update(at=0.0, state="gone", url="")

    # --- 3. le serveur -----------------------------------------------------------------------
    # **Le banc ne doit pas voir les pairs de la machine.** Sur une vraie installation, un autre
    # processus Hermes tient souvent une page : le plugin refuserait alors le ticket (409), et le
    # banc croirait que `observe` est cassé. On l'isole donc dans un dossier à lui.
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

        # --- 3 bis. un écran qui arrive chez le mauvais processus -------------------------------
        # **Deux processus Hermes tournent en parallèle.** Le navigateur de l'agent vit dans celui
        # qui exécute le tour ; l'autre n'a rien à montrer. L'app se connectait au mauvais, le
        # relais épinglait le ticket sur lui, et l'écran restait sur « En attente… » pour toujours
        # alors que la page existait, visible dans l'autre processus.
        check("sans pair qui a une page, le ticket est servi", True)
        # On simule un pair qui a une vraie page. Le pid doit exister : le plugin ne se fie pas à
        # une fiche périmée, il vérifie que le processus est toujours là.
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

    # --- la trame porte sa taille CSS, et le geste s'y appuie ------------------------------
    # **Le `css` ne partait pas avec la trame.** Il en était retiré juste avant la diffusion
    # (`frame.pop("css")`), et l'app recevait donc une taille de zéro : ses gestes étaient
    # calculés sur un viewport de zéro pixel, et le serveur les rejouait au coin de l'écran.
    # L'app n'a rien à corriger : c'est le serveur qui ne diffusait pas la mesure.
    captured: list[dict] = []

    class FauxPompe:
        """Un contrôleur qui rend une trame en mobile, et note ce que le serveur lui passe."""

        def __init__(self):
            self.css_reçu = (0, 0)
            self.geste = None
            # **Ce que le serveur demande au navigateur pour la forme de la page.** L'outil posait
            # le viewport mobile de son côté ; l'app peut prendre la main sans lui, et la page
            # restait alors en 1280 px écrasés dans la largeur du téléphone.
            self.vues: list[str] = []

        def use_phone_viewport(self, *size):
            # La forme du pouce est calculée par `_phone_css` et passée en arguments : le faux
            # contrôleur les accepte, comme `Controller.use_phone_viewport(width, height, dpr)`.
            self.vues.append("phone")
            return (390, 844)

        def repaint_viewport(self, *size):
            # **Même contrat, autre adresse.** `_pose_viewport` ne pose plus la forme du pouce
            # par `use_phone_viewport` mais par `repaint_viewport`, qui efface d'abord l'émulation
            # en place (sans quoi la capture sort en mosaïque). Le double doit exposer la méthode
            # que le serveur appelle vraiment : sans elle, l'appel levait un `AttributeError` que
            # `_pose_viewport` avale exprès, et le banc croyait que rien n'avait été demandé.
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
    # Le geste exige le bail : sans lui, il est refusé `no_lease` avant d'atteindre la page.
    if server.SESSION.holder != "human":
        server.SESSION.acquire()
    try:
        # La trame : elle doit contenir `css`.
        trame = await server._une_trame()
        check("la trame porte sa taille CSS", (trame or {}).get("css") == [390, 844])
        check("et le serveur la garde pour lui", server.SESSION.viewport == (390, 844))

        # Le geste : l'app envoie `css: [0, 0]` (elle n'a pas lu la mesure) — le serveur doit
        # retomber sur la sienne, et non rejouer le geste sur un viewport de zéro pixel.
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

        # **Le viewport suit le volant, pas seulement l'outil.** Prendre la main doit mettre la
        # page à la forme du pouce ; rendre la main doit la remettre en bureau pour l'agent.
        await server._pose_viewport(True)
        await server._pose_viewport(False)
        check("prendre la main pose un viewport de téléphone", faux.vues == ["phone", "desktop"])
    finally:
        server._controller = ancien_controleur
        server.STATE["task_id"] = None

    # --- 4. `idle` est un battement, pas une annonce ----------------------------------------
    # **Envoyé une seule fois, il laissait l'écran dans un silence absolu.** Le chien de garde
    # de l'app ne comptait que les trames : n'ayant reçu qu'un `idle`, il ne se déclenchait
    # jamais — et le socket, mort en silence derrière le relais, n'était jamais noticed.
    # L'écran restait donc sur « En attente… » pour toujours, même quand la page réapparaissait.
    # Le battement rend le silence interprétable : « vivant mais sans page » ≠ « mort ».
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

    # --- 8. un refus tient pour la conversation et la page --------------------------------
    # **Sans mémoire, refuser ne servait à rien.** Le tour suivant rappelait l'outil sur la même
    # page, une demande neuve était publiée, et la carte revenait — à chaque prompt réécrit.
    url = "https://github.com/login"
    server.note_declined("api-abc", url)
    check("un refus est retenu", server.declined_recently("api-abc", url))
    check("et il ne vaut que pour sa page", not server.declined_recently("api-abc", "https://autre.test/x"))
    check("et que pour sa conversation", not server.declined_recently("api-autre", url))
    # L'ancre et la barre finale ne créent pas une seconde page.
    check("la même page sans ancre reste la même", server.declined_recently("api-abc", url + "#section"))

    print()
    if failures:
        print(f"{len(failures)} échec(s): " + ", ".join(failures))
        return 1
    print("tout est vert")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
