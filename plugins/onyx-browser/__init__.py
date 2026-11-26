"""onyx-browser — the agent's browser, live on the phone, and its takeover flow.

A platform plugin in the Hermes sense: `register(ctx)` gives it one tool (which blocks the turn)
and two hooks (which retain the browser's `task_id`). The aiohttp server starts with it,
on the loopback interface, and never stops: `onyx-one` relays it under `/onyx-browser/`.
"""

from __future__ import annotations

from . import server, tools


def register(ctx):
    # **The server starts here, not in the tool.** Hermes registers the hooks first and then
    # defers the tools ("tools (next session)"): as long as the server only came up on the
    # first tool call, `/onyx-browser/health` never answered, and the Settings row
    # never appeared either. The server does not need to be called: it needs
    # to be there when the phone probes.
    server.ensure_started()
    tools.register(ctx)
    return {"tools": ["browser_request_takeover"], "hooks": ["pre_tool_call", "post_tool_call"]}
