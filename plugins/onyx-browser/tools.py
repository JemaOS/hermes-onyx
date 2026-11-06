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
    """**La conversation qui demande la main**, telle que Hermes la nomme.

    Le plugin tourne dans le processus de l'agent : le contexte de session y est lisible. En CLI
    ou dans un test il ne l'est pas, et une demande sans conversation reste une demande — elle
    s'affiche alors dans le fil vivant, comme avant.
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
        # **Pas de page, pas de prise en main.** Hermes récolte un navigateur resté deux minutes
        # sans outil et en rouvre un, vide, au tour suivant : `page_state()` distingue ce cas
        # (`blank`) de l'absence de navigateur (`gone`), et le modèle reçoit le geste à faire au
        # lieu d'attendre quinze minutes devant un écran blanc.
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
    # **Une demande sait d'où elle vient.** La conversation est lue ici, dans le contexte du tour :
    # c'est ce que l'écran affiche pour poser la carte dans le bon fil, et la clé d'un refus déjà
    # donné sur cette page.
    waiting.session = _session_id()
    try:
        waiting.url = str(server.page_snapshot().get("url") or "")
    except Exception:
        waiting.url = ""
    # **Refusé, cela reste refusé.** La personne a dit non sur cette page : reposer la question au
    # tour suivant — à chaque prompt réécrit — n'est pas une demande, c'est un harcèlement. On le
    # dit au modèle tout de suite, sans publier de carte.
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
        # Aucun serveur vivant dans ce processus : l'écran du téléphone ne verra jamais cette
        # demande. Le dire tout de suite vaut mieux que bloquer quinze minutes sur un écran vide.
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
        # **On ne dort pas quinze minutes d'un bloc : on surveille la page.**
        #
        # Le navigateur de Hermes meurt après deux minutes sans appel d'outil. Quand ça arrive
        # pendant une prise en main, l'humain voit un écran noir et l'agent attend un rendez-vous
        # qui n'aura jamais lieu. La sonde (un simple aller-retour CDP, sans capture) rend la main
        # en quelques secondes, avec une raison exacte que le modèle peut expliquer.
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
        # **Un refus se dit à l'agent, pas seulement à l'écran.** Sans cette branche, un refus
        # aurait laissé l'outil croire que la personne avait pris la main puis était revenue :
        # il aurait repris son tour en croyant la page modifiée, avec des « facts » vides.
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
    _recharger_si_demande()


def _recharger_si_demande() -> None:
    """Recharge le plugin **dans ce processus**, quand on le demande.

    Le plugin est importé une fois pour toutes : un changement de `server.py` n'est pris en
    compte qu'au redémarrage du gateway — et le gateway ne peut pas être redémarré depuis
    l'intérieur de lui-même (le SIGTERM tuerait la commande qui l'a demandé).

    On recharge donc `cdp` et `server` en place, depuis le crochet qui tourne **dans** le
    processus du gateway. La boucle aiohttp et le port en écoute ne bougent pas ; seul le
    code change. Le rechargement est idempotent : la balise dans `sys.modules` l'empêche de
    se répéter à chaque appel d'outil.

    **La demande vient d'un fichier, pas d'une variable.** Un drapeau dans `sys.modules`
    n'atteint jamais le processus du gateway depuis l'extérieur : il faudrait déjà y exécuter
    du code. Un fichier sur disque, en revanche, se dépose de n'importe où — et le crochet
    `pre_tool_call` le voit au prochain appel d'outil. Le fichier est **effacé** après usage :
    le rechargement est donc demandé une fois, et jamais rejoué.

    **Le nom du module n'est pas `onyx_browser`.** Le plugin est chargé sous le nom de son
    répertoire d'origine — `hermes_plugins.onyx_browser.server` pour la copie globale, et
    `hermes_plugins.onyx_browser__home_<empreinte>.server` pour une copie logée ailleurs. On
    cherche donc **tous** les modules dont le nom finit par `.cdp` ou `.server` et qui portent
    `onyx_browser` : sans cela, la demande de rechargement ne trouvait rien, et le correctif
    restait inerte jusqu'au prochain redémarrage du gateway.
    """
    if not _demande_de_rechargement():
        return
    try:
        import importlib

        # **`tools.py` d'abord** : c'est lui qui porte le crochet, et c'est lui qui décide de
        # recharger les autres. Le recharger après les autres laisserait son propre code
        # (la liste des noms de modules, la demande par fichier) périmé pour cette fois.
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
    except Exception as erreur:  # pragma: no cover — un rechargement ne doit rien casser
        log.warning("onyx-browser: rechargement à chaud impossible (%s)", erreur)


#: Le fichier qui demande un rechargement à chaud. Il est effacé dès qu'il a été pris en
#: compte : la demande est donc ponctuelle, et le prochain appel d'outil la traite.
FICHIER_DEMANDE = "/home/john/.hermes/onyx-browser/recharger"


def _demande_de_rechargement() -> bool:
    """Vrai quand un rechargement à chaud vient d'être demandé (et le consume)."""
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
        # Une image après chaque geste de l'agent : l'écran qui s'ouvrira ensuite verra où en est
        # la page, même si personne ne regardait pendant l'action.
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
