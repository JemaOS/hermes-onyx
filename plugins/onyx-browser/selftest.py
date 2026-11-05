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
    check("une trame périmée est refusée", s.refusal(seq) == "stale")
    s.release("fini")
    check("rendre la main remet l'agent", s.holder == "agent" and s.epoch == 2)
    check("et une saisie est alors refusée", s.refusal(s.seq) == "no_lease")

    # --- 2. le ticket ------------------------------------------------------------------------
    ticket = s.mint()
    check("un ticket se consomme une fois", s.redeem(ticket.value) is ticket)
    check("le second essai échoue", s.redeem(ticket.value) is None)
    short = s.mint()
    clock[0] += 31
    check("un ticket périmé ne passe pas", s.redeem(short.value) is None)

    # --- 3. le serveur -----------------------------------------------------------------------
    app = server.build_app()
    async with TestClient(TestServer(app)) as client:
        async with client.get("/onyx-browser/health") as resp:
            body = await resp.json()
            check("health répond", resp.status == 200 and body.get("plugin") == "onyx-browser")
            check("il ne ment pas sur le navigateur", body.get("browser") is False)

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

    print()
    if failures:
        print(f"{len(failures)} échec(s): " + ", ".join(failures))
        return 1
    print("tout est vert")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
