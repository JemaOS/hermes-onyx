"""The tool that blocks the turn, and the hooks from which the browser's `task_id` comes.

The non-negotiable point of the plan (§4): **Hermes closes its browser at the end of the turn**. A takeover
therefore only makes sense if the agent **waits** during its turn. That is what
`browser_request_takeover` does: it posts a request, publishes the wait, and blocks on a
`threading.Event` until the human hands control back (or 15 minutes).

The tool **never** sees what the human types: it receives a free-form note, the page host and a
boolean "there was a password field". Nothing else crosses over.
"""

from __future__ import annotations

import json
import logging
import os
import sys
import threading
import time
import uuid
from typing import Any

from . import cdp, server
from .session import TAKEOVER_TIMEOUT_S, Waiting

log = logging.getLogger(__name__)

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


def _session_id() -> str:
    """**The conversation asking for control**, as Hermes names it.

    The plugin runs in the agent's process: the session context is readable there. In CLI
    mode or in a test it is not, and a request without a conversation remains a request — it
    is then shown in the live thread, as before.
    """
    try:
        from gateway.session_context import get_session_env

        value = str(get_session_env("HERMES_SESSION_ID") or "").strip()
        if value:
            return value
    except Exception:
        pass
    return str(os.environ.get("HERMES_SESSION_ID") or "").strip()


def request_takeover(reason: str) -> str:
    """Post the request, wait, and return to the model a report with no secret in it."""
    waiting = Waiting(takeover_id=uuid.uuid4().hex[:12], reason=reason.strip() or "Take over my browser.")
    server.ensure_started()
    server.SESSION.waiting = waiting
    # The phone viewport is set **before** the human types (§2.1).
    try:
        with controller_guard():
            width, height = cdp_controller().use_phone_viewport()
            server.SESSION.viewport = (width, height)
    except Exception:
        # **No page, no takeover.** Hermes reaps a browser left two minutes
        # without a tool and reopens an empty one on the next turn: `page_state()` distinguishes this case
        # (`blank`) from the absence of a browser (`gone`), and the model receives the gesture to perform
        # instead of waiting fifteen minutes in front of a blank screen.
        server.SESSION.waiting = None
        try:
            etat = server.page_snapshot()["state"]
        except Exception:
            etat = "gone"
        if etat == "blank":
            return json.dumps(
                {
                    "status": "unavailable",
                    "reason": "blank_page",
                    "message": (
                        "Your browser is open on an empty tab, so there is nothing to hand over. "
                        "Navigate to the page first (browser_navigate), confirm it loaded, then "
                        "call this tool in the same turn."
                    ),
                },
                ensure_ascii=False,
            )
        return json.dumps(
            {
                "status": "unavailable",
                "reason": "no_browser",
                "message": "No browser page is open in this turn.",
            },
            ensure_ascii=False,
        )
    # **A request knows where it comes from.** The conversation is read here, in the turn's context:
    # it is what the screen displays to place the card in the right thread, and the key of a refusal already
    # given on this page.
    waiting.session = _session_id()
    try:
        waiting.url = str(server.page_snapshot().get("url") or "")
    except Exception:
        waiting.url = ""
    # **Refused stays refused.** The person said no on this page: asking the question again on the
    # next turn — on every rewritten prompt — is not a request, it is harassment. We say it
    # to the model right away, without publishing a card.
    if waiting.session and server.declined_recently(waiting.session, waiting.url):
        server.SESSION.waiting = None
        return json.dumps(
            {
                "status": "declined",
                "reason": "declined_by_owner",
                "message": (
                    "The owner already declined to take over on this page. Do not ask again: "
                    "either solve it yourself with what you have, or tell them what you need and "
                    "stop."
                ),
            },
            ensure_ascii=False,
        )
    if not server.notify_pending(waiting.takeover_id, waiting.reason, waiting.session):
        # No live server in this process: the phone screen will never see this
        # request. Saying so right away is better than blocking fifteen minutes on a blank screen.
        server.SESSION.waiting = None
        return json.dumps(
            {
                "status": "unavailable",
                "message": (
                    "The browser screen server is not running in this process, so the phone cannot "
                    "reach this page. Another Hermes process owns the screen port."
                ),
            },
            ensure_ascii=False,
        )
    try:
        # **We do not sleep fifteen minutes in one go: we watch the page.**
        #
        # The Hermes browser dies after two minutes without a tool call. When that happens
        # during a takeover, the human sees a black screen and the agent waits for a rendezvous
        # that will never happen. The probe (a simple CDP round trip, without capture) hands back
        # within seconds, with an exact reason the model can explain.
        limite = time.monotonic() + TAKEOVER_TIMEOUT_S
        page_perdue = False
        while True:
            reste = limite - time.monotonic()
            if reste <= 0:
                break
            if waiting.done.wait(min(server.SONDE_S, reste)):
                break
            if not server.page_alive():
                page_perdue = True
                break
        if page_perdue:
            server.SESSION.abandon()
            server.clear_pending()
            try:
                etat = server.page_snapshot()["state"]
            except Exception:
                etat = "gone"
            server.announce(
                {
                    "type": "takeover_done",
                    "id": waiting.takeover_id,
                    "facts": {},
                    "lost": True,
                    "reason": "page_closed" if etat == "gone" else "page_blank",
                }
            )
            if etat == "gone":
                explication = (
                    "The page closed while the owner was taking over (Hermes closes an idle "
                    "browser after about two minutes). Reopen the page and call this tool again."
                )
            else:
                explication = (
                    "Your browser now shows an empty tab, so the owner had nothing to type into. "
                    "Navigate to the page again, then call this tool."
                )
            return json.dumps(
                {
                    "status": "lost",
                    "reason": "page_closed" if etat == "gone" else "page_blank",
                    "message": explication,
                },
                ensure_ascii=False,
            )
        # **A refusal is told to the agent, not only to the screen.** Without this branch, a refusal
        # would have left the tool believing the person had taken control and then come back:
        # it would have resumed its turn believing the page had been modified, with empty "facts".
        if getattr(waiting, "refused", False):
            server.announce(
                {
                    "type": "takeover_done",
                    "id": waiting.takeover_id,
                    "facts": {},
                    "refused": True,
                    "reason": "declined",
                }
            )
            return json.dumps(
                {
                    "status": "declined",
                    "reason": "declined_by_owner",
                    "message": (
                        "The owner declined to take over. Nothing was typed and the page is as you "
                        "left it. Do not wait and do not ask again in this turn: either solve it "
                        "yourself, or tell them what you need and stop."
                    ),
                },
                ensure_ascii=False,
            )
        if not waiting.done.is_set():
            server.clear_pending()
            server.announce(
                {
                    "type": "takeover_done",
                    "id": waiting.takeover_id,
                    "facts": {},
                    "lost": True,
                    "reason": "timeout",
                }
            )
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
        server.clear_pending()


def cdp_controller() -> cdp.Controller:
    return server._controller()


class controller_guard:
    """An empty context, so the call looks like the others and stays readable."""

    def __enter__(self) -> None:
        return None

    def __exit__(self, *_: Any) -> None:
        return None


# -- hooks ---------------------------------------------------------------------------------

def on_pre_tool_call(tool_name: str = "", **kwargs: Any) -> None:
    """Retain the browser's `task_id`: it is what designates the page to capture."""
    task_id = kwargs.get("task_id")
    if task_id and str(tool_name).startswith("browser_"):
        server.STATE["task_id"] = str(task_id)
    _recharger_si_demande()


def _recharger_si_demande() -> None:
    """Reload the plugin **in this process**, when asked to.

    The plugin is imported once and for all: a change to `server.py` is only taken into
    account when the gateway restarts — and the gateway cannot be restarted from
    inside itself (the SIGTERM would kill the command that asked for it).

    We therefore reload `cdp` and `server` in place, from the hook that runs **inside** the
    gateway process. The aiohttp loop and the listening port do not move; only the
    code changes. The reload is idempotent: the tag in `sys.modules` prevents it from
    repeating on every tool call.

    **The request comes from a file, not a variable.** A flag in `sys.modules`
    never reaches the gateway process from outside: code would first have to run there.
    A file on disk, on the other hand, can be dropped from anywhere — and the hook
    `pre_tool_call` sees it on the next tool call. The file is **deleted** after use:
    the reload is therefore requested once, and never replayed.

    **The module name is not `onyx_browser`.** The plugin is loaded under the name of its
    origin directory — `hermes_plugins.onyx_browser.server` for the global copy, and
    `hermes_plugins.onyx_browser__home_<hash>.server` for a copy located elsewhere. We
    therefore look for **all** modules whose name ends with `.cdp` or `.server` and which carry
    `onyx_browser`: without that, the reload request found nothing, and the fix
    stayed inert until the next gateway restart.
    """
    if not _demande_de_rechargement():
        return
    try:
        import importlib

        # **`tools.py` first**: it is the one carrying the hook, and the one deciding to
        # reload the others. Reloading it after the others would leave its own code
        # (the list of module names, the file-based request) stale for this time.
        cibles = [
            nom
            for nom in list(sys.modules)
            if "onyx_browser" in nom and nom.endswith((".cdp", ".server", ".tools"))
        ]
        if not cibles:
            log.warning("onyx-browser: aucun module à recharger dans ce processus")
            return
        for nom in cibles:
            module = sys.modules.get(nom)
            if module is not None:
                importlib.reload(module)
        log.info("onyx-browser: cdp, server et tools rechargés à chaud (%s)", ", ".join(sorted(cibles)))
    except Exception as erreur:  # pragma: no cover — a reload must not break anything
        log.warning("onyx-browser: rechargement à chaud impossible (%s)", erreur)


#: The file that requests a hot reload. It is deleted as soon as it has been taken
#: into account: the request is therefore one-shot, and the next tool call handles it.
FICHIER_DEMANDE = "/home/john/.hermes/onyx-browser/recharger"


def _demande_de_rechargement() -> bool:
    """True when a hot reload has just been requested (and consumes it)."""
    if sys.modules.get("onyx_browser_recharge_demande") == "oui":
        sys.modules.pop("onyx_browser_recharge_demande", None)
        return True
    try:
        if not os.path.exists(FICHIER_DEMANDE):
            return False
        os.unlink(FICHIER_DEMANDE)
    except OSError:
        return False
    return True


def on_post_tool_call(tool_name: str = "", **kwargs: Any) -> None:
    on_pre_tool_call(tool_name=tool_name, **kwargs)
    if str(tool_name).startswith("browser_"):
        # One image after each of the agent's gestures: the screen that opens next will see where
        # the page stands, even if nobody was watching during the action.
        server.vignette()


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
