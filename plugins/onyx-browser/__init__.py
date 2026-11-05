"""onyx-browser — le navigateur de l'agent, en direct sur le téléphone, et sa prise en main.

Un plugin de plateforme au sens de Hermes : `register(ctx)` lui donne un outil (qui bloque le tour)
et deux crochets (qui retiennent le `task_id` du navigateur). Le serveur aiohttp démarre avec lui,
sur la boucle locale, et ne s'arrête jamais : `onyx-one` le relaie sous `/onyx-browser/`.
"""

from __future__ import annotations

from . import server, tools


def register(ctx):
    tools.register(ctx)
    return {"tools": ["browser_request_takeover"], "hooks": ["pre_tool_call", "post_tool_call"]}
