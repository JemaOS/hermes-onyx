"""L'outil qui bloque le tour, et les crochets d'où vient le `task_id` du navigateur.

Le point non négociable du plan (§4) : **Hermes ferme son navigateur à la fin du tour**. Une prise
en main n'a donc de sens que si l'agent **attend** pendant son tour. C'est ce que fait
`browser_request_takeover` : il pose une demande, publie l'attente, et bloque sur un
`threading.Event` jusqu'à ce que l'humain rende la main (ou 15 minutes).

L'outil ne voit **jamais** ce que l'humain tape : il reçoit une note libre, l'hôte de la page et un
booléen « il y avait un champ mot de passe ». Rien d'autre ne traverse.
"""

from __future__ import annotations

import json
import threading
import uuid
from typing import Any

from . import cdp, server
from .session import TAKEOVER_TIMEOUT_S, Waiting

TAKEOVER_SCHEMA = {
    "name": "browser_request_takeover",
    "description": (
        "Hand your web browser to the owner from their phone, then wait until they hand it back. "
        "Use it whenever the current page needs a human: login or password, 2FA code, captcha, "
        "consent or payment wall, anything you must not or cannot type yourself. Leave the browser "
        "on that page before calling. This call blocks (up to 15 min) and keeps your browser open; "
        "never ask for a password in the chat instead."
    ),
    "parameters": {
        "type": "object",
        "properties": {
            "reason": {
                "type": "string",
                "description": "Short sentence, in the owner's language, telling them what to do on the page.",
            }
        },
        "required": ["reason"],
    },
}


def request_takeover(reason: str) -> str:
    """Pose la demande, attend, et rend au modèle un compte rendu sans aucun secret."""
    waiting = Waiting(takeover_id=uuid.uuid4().hex[:12], reason=reason.strip() or "Take over my browser.")
    server.ensure_started()
    server.SESSION.waiting = waiting
    # Le viewport de téléphone est posé **avant** que l'humain tape (§2.1).
    try:
        with controller_guard():
            width, height = cdp_controller().use_phone_viewport()
            server.SESSION.viewport = (width, height)
    except Exception:
        # Pas de navigateur : l'outil le dira, sans bloquer quinze minutes pour rien.
        server.SESSION.waiting = None
        return json.dumps(
            {"status": "unavailable", "message": "No browser page is open in this turn."},
            ensure_ascii=False,
        )
    server.notify_pending(waiting.takeover_id, waiting.reason)
    try:
        if not waiting.done.wait(TAKEOVER_TIMEOUT_S):
            return json.dumps(
                {
                    "status": "timeout",
                    "message": "The owner did not take over within 15 minutes.",
                },
                ensure_ascii=False,
            )
        try:
            cdp_controller().clear_viewport()
        except Exception:
            pass
        facts = waiting.facts or {}
        payload: dict[str, Any] = {
            "status": "done",
            "message": (
                "The owner finished on your browser and handed it back."
                + (f" Their note: {waiting.note}" if waiting.note else "")
                + " Check the page state before continuing."
            ),
        }
        if facts.get("host"):
            payload["page_host"] = facts["host"]
            payload["login_form"] = bool(facts.get("hasPassword"))
        return json.dumps(payload, ensure_ascii=False)
    finally:
        server.SESSION.waiting = None


def cdp_controller() -> cdp.Controller:
    return server._controller()


class controller_guard:
    """Un contexte vide, pour que l'appel ressemble aux autres et reste lisible."""

    def __enter__(self) -> None:
        return None

    def __exit__(self, *_: Any) -> None:
        return None


# -- crochets ---------------------------------------------------------------------------------

def on_pre_tool_call(tool_name: str = "", **kwargs: Any) -> None:
    """Retient le `task_id` du navigateur : c'est lui qui désigne la page à capturer."""
    task_id = kwargs.get("task_id")
    if task_id and str(tool_name).startswith("browser_"):
        server.STATE["task_id"] = str(task_id)


def on_post_tool_call(tool_name: str = "", **kwargs: Any) -> None:
    on_pre_tool_call(tool_name=tool_name, **kwargs)


def register(ctx: Any) -> None:
    server.ensure_started()
    ctx.register_tool(
        name="browser_request_takeover",
        toolset="onyx",
        schema=TAKEOVER_SCHEMA,
        handler=lambda args, **_: request_takeover(str((args or {}).get("reason") or "")),
        check_fn=lambda: True,
        emoji="\U0001F590",
        description=TAKEOVER_SCHEMA["description"],
    )
    ctx.register_hook("pre_tool_call", on_pre_tool_call)
    ctx.register_hook("post_tool_call", on_post_tool_call)
