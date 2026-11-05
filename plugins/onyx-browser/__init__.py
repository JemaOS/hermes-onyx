"""onyx-browser — le navigateur de l'agent, en direct sur le téléphone, et sa prise en main.

Un plugin de plateforme au sens de Hermes : `register(ctx)` lui donne un outil (qui bloque le tour)
et deux crochets (qui retiennent le `task_id` du navigateur). Le serveur aiohttp démarre avec lui,
sur la boucle locale, et ne s'arrête jamais : `onyx-one` le relaie sous `/onyx-browser/`.
"""

from __future__ import annotations

from . import server, tools


def register(ctx):
    # **Le serveur démarre ici, pas dans l'outil.** Hermes enregistre d'abord les crochets puis
    # différe les outils (« tools (next session) ») : tant que le serveur ne se levait qu'au
    # premier appel d'outil, `/onyx-browser/health` ne répondait jamais, et la ligne de Réglages
    # n'apparaissait jamais non plus. Le serveur n'a pas besoin qu'on l'appelle : il a besoin
    # d'être là quand le téléphone sonde.
    server.ensure_started()
    tools.register(ctx)
    return {"tools": ["browser_request_takeover"], "hooks": ["pre_tool_call", "post_tool_call"]}
