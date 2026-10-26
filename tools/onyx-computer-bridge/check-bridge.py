#!/usr/bin/env python3
"""Éprouve le pont « Ordinateur » de bout en bout, sans l'app.

Ce que le script vérifie, et qui doit apparaître dans le journal du pont :

1. un jeton absent ou faux est refusé (la session se ferme) ;
2. une entrée **sans la main** est refusée (`not_holder`) ;
3. `take` donne la main, `tap` clique vraiment (l'appelant compare ensuite l'écran) ;
4. `type` frappe vraiment les caractères, accents compris ;
5. `run` sans `approved` est refusé (`needs_approval`) ; avec `approved`, la commande part ;
6. `release` rend la main et une entrée redevient refusée.

Usage :
    check-bridge.py --token-file ~/.onyx-computer-bridge/token --tap 320,180
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
from pathlib import Path


async def connect(url: str, token: str, want_frames: bool = True):
    from websockets.asyncio.client import connect as ws_connect

    return await ws_connect(url, additional_headers={"Authorization": f"Bearer {token}"},
                            max_size=None, open_timeout=10)


async def read_until(ws, wanted: str, timeout: float = 8.0):
    loop = asyncio.get_running_loop()
    deadline = loop.time() + timeout
    while True:
        left = deadline - loop.time()
        if left <= 0:
            raise TimeoutError(f"rien de type {wanted!r} reçu")
        raw = await asyncio.wait_for(ws.recv(), timeout=left)
        if isinstance(raw, (bytes, bytearray)):
            continue
        message = json.loads(raw)
        if message.get("t") == wanted:
            return message


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="ws://127.0.0.1:8790/ws")
    parser.add_argument("--token-file", default=str(Path.home() / ".onyx-computer-bridge" / "token"))
    parser.add_argument("--tap", default=None, help="coordonnées, « x,y »")
    parser.add_argument("--space", default="view",
                        help="l'espace des coordonnées : « view » (l'image) ou « screen » (le PC)")
    parser.add_argument("--type", dest="text", default=None)
    parser.add_argument("--command", default=None, help="commande à envoyer avec approbation")
    parser.add_argument("--refuse-first", action="store_true",
                        help="envoie la commande d'abord sans approbation")
    args = parser.parse_args()
    token = Path(args.token_file).read_text(encoding="utf-8").strip()

    # 1. un jeton faux ne passe pas.
    try:
        ws = await connect(args.url, "faux-jeton")
        await asyncio.wait_for(ws.recv(), timeout=5)
        print("ÉCHEC : un jeton faux a été accepté")
        return 1
    except Exception as error:
        print(f"jeton faux refusé ({type(error).__name__})")

    ws = await connect(args.url, token)
    hello = await read_until(ws, "hello")
    print(f"salut : écran {hello['screen']} → image {hello['view']}, {hello['fps']} i/s")
    state = await read_until(ws, "state")
    print(f"état initial : main à {state['holder']}")

    async def send(payload: dict) -> dict:
        await ws.send(json.dumps(payload))
        while True:
            raw = await asyncio.wait_for(ws.recv(), timeout=15)
            if isinstance(raw, (bytes, bytearray)):
                continue
            message = json.loads(raw)
            if message.get("t") == "state":
                continue
            return message

    # 2. sans la main, une entrée est refusée.
    denied = await send({"t": "tap", "id": 1, "x": 10, "y": 10})
    print(f"sans la main : {denied.get('t')} / {denied.get('reason')}")

    taken = await send({"t": "take", "id": 2})
    print(f"prise de main : {taken.get('t')} / {taken.get('detail')}")

    if args.tap:
        x, y = (float(v) for v in args.tap.split(","))
        ack = await send({"t": "tap", "id": 3, "x": x, "y": y, "space": args.space})
        print(f"appui en {x},{y} ({args.space}) : {ack.get('t')} / {ack.get('detail')}")

    if args.text:
        ack = await send({"t": "text", "id": 4, "text": args.text})
        print(f"frappe : {ack.get('t')} / {ack.get('detail')}")

    if args.command:
        if args.refuse_first:
            denied = await send({"t": "run", "id": 5, "command": args.command})
            print(f"commande sans approbation : {denied.get('t')} / {denied.get('reason')}")
        result = await send({"t": "run", "id": 6, "command": args.command, "approved": True})
        output = (result.get("output") or "").strip().splitlines()
        print(f"commande approuvée : {result.get('t')} / sortie {result.get('exit')} : "
              f"{output[-1] if output else '(vide)'}")

    released = await send({"t": "release", "id": 7})
    print(f"main rendue : {released.get('t')} / {released.get('detail')}")
    denied = await send({"t": "tap", "id": 8, "x": 10, "y": 10})
    print(f"après le rendu : {denied.get('t')} / {denied.get('reason')}")
    await ws.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
