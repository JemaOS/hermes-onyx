"""onyx-record — API du serveur « démonstration → skill ».

Montée sous ``/api/plugins/onyx-record/`` par le Dashboard Hermes.

Contrat HTTP (``docs/SPEC-SKILLS-RECORD.md``) :

* ``POST skills/record/start`` -> ouvre un bail ``{session_id, started_at, deadline_at, max_seconds, grace_seconds}``
* ``POST skills/record/stop``  -> ferme le bail et rend le journal **canonique** (jamais celui du client)
* ``POST skills/draft``        -> brouillon Markdown déterministe + étapes à relire (sans état)
* ``GET  health`` -> état du serveur d'enregistrement (aucun secret)
* ``GET  info``   -> version + cible de montage

Un seul mode de montage
-----------------------
Ce module expose ``router`` (un ``APIRouter``) et **rien d'autre** : le
Dashboard l'inclut sous son préfixe, avec sa dépendance d'authentification et la
porte de consentement par requête. Aucune route n'est enregistrée directement sur
l'application, aucune variable d'environnement ne change le mode.

C'est une correction délibérée du modèle ``onyx-vault`` : ce plugin-là avait deux
modes de montage (application ou routeur, choisis par ``ONYX_VAULT_MOUNT``), et le
mode non autoritaire cassait des tests quand les suites partageaient un
interpréteur. Le coffre avait besoin du contournement parce que ses routes lisent
``?profile=`` comme nom de profil ; ici aucune route ne lit ``?profile=`` (le
profil est refusé, et le profil Hermes est celui du Dashboard), donc la
dépendance standard est inoffensive et suffisante.

Rien de sensible ne sort : ni texte, ni libellé, ni URL, ni journal dans un log.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import logging
import sys
from pathlib import Path
from typing import Any, Optional

from fastapi import APIRouter
from starlette.requests import Request
from starlette.responses import JSONResponse

log = logging.getLogger(__name__)

PLUGIN_NAME = "onyx-record"
VERSION = "1.0.0"
PREFIX = f"/api/plugins/{PLUGIN_NAME}"

# Un journal pèse au plus 262144 octets ; l'enveloppe et le reste tiennent
# largement dans le double. Au-delà, c'est un envoi qui n'aurait pas dû partir.
_MAX_BODY_BYTES = 2 * 262144

_NO_STORE = {"Cache-Control": "no-store, no-cache, must-revalidate"}


def _load_core():
    """Charge ``onyx_record.py`` voisin, sous un nom dérivé de son chemin.

    Le nom n'est pas fixe (contrairement à ``onyx_vault_core``) : deux copies du
    plugin dans le même interpréteur — celle du dépôt et celle d'un
    ``HERMES_HOME`` jetable — doivent avoir **deux** états mémoire, sinon la
    suite de tests d'un dossier voit les baux de l'autre.
    """
    path = (Path(__file__).resolve().parent / "onyx_record.py").resolve()
    module_name = "onyx_record_core_" + hashlib.sha1(str(path).encode("utf-8")).hexdigest()[:10]
    if module_name in sys.modules:
        return sys.modules[module_name]
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:  # pragma: no cover - fichier déplacé
        raise ImportError(f"{PLUGIN_NAME}: onyx_record.py introuvable à côté de plugin_api.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


record_core = _load_core()


# ---------------------------------------------------------------------------
# Réponses / refus
# ---------------------------------------------------------------------------


def _json(payload: dict, status_code: int = 200) -> JSONResponse:
    return JSONResponse(payload, status_code=status_code, headers=_NO_STORE)


def _refused(exc: "record_core.RecordError") -> JSONResponse:
    """Le refus tel que la spec le fixe : ``{"detail": {"code": …}}``.

    ``code`` est la première clé de ``detail`` (donc du corps) parce que le
    client ne garde que les 300 premiers caractères d'une réponse en erreur.
    Aucun champ n'est dérivé du journal : ni texte, ni libellé, ni URL.
    """
    return _json({"detail": exc.payload()}, status_code=exc.status)


def _reject_constant(value: str) -> None:
    raise ValueError(f"non-finite JSON constant: {value}")


def _bad_request(message: str) -> "record_core.RecordError":
    return record_core.RecordError("bad_request", message)


async def _json_body(request: Request) -> dict:
    """Corps JSON lu à la main : aucune erreur de validation ne recopie un champ.

    Une ``RequestValidationError`` de FastAPI recopierait la valeur fautive dans
    la réponse ; ici, un corps illisible ne rend qu'un code.
    """
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > _MAX_BODY_BYTES:
        raise record_core.RecordError("journal_too_large")
    try:
        raw = await request.body()
    except Exception:
        raise _bad_request("Unreadable body.") from None
    if len(raw) > _MAX_BODY_BYTES:
        raise record_core.RecordError("journal_too_large")
    try:
        # ``NaN``/``Infinity`` ne sont pas du JSON : sans ce refus, ils
        # atteindraient la sérialisation de la réponse.
        body = json.loads(raw or b"{}", parse_constant=_reject_constant)
    except Exception:
        raise _bad_request("Invalid JSON body.") from None
    if not isinstance(body, dict):
        raise _bad_request("A JSON object is expected.")
    return body


# ---------------------------------------------------------------------------
# Les trois routes
# ---------------------------------------------------------------------------


async def record_start(request: Request):
    try:
        body = await _json_body(request)
        opened = record_core.get_store().start(body)
    except record_core.RecordError as exc:
        return _refused(exc)
    except Exception as exc:  # jamais de détail : un journal pourrait y passer
        log.error("onyx-record: start failed (%s)", type(exc).__name__)
        return _refused(record_core.RecordError("recorder_unavailable"))
    log.info("onyx-record: bail ouvert %s", opened["session_id"])
    return _json(opened)


async def record_stop(request: Request):
    try:
        body = await _json_body(request)
        closed = record_core.get_store().stop(body)
    except record_core.RecordError as exc:
        # Un refus ne dit rien du journal : code + message, jamais un extrait.
        log.info("onyx-record: stop refusé (%s)", exc.code)
        return _refused(exc)
    except Exception as exc:
        log.error("onyx-record: stop failed (%s)", type(exc).__name__)
        return _refused(record_core.RecordError("recorder_unavailable"))
    log.info(
        "onyx-record: bail fermé %s (%s, %s actions, %s octets)",
        closed["session_id"],
        closed["end_reason"],
        closed["actions"],
        closed["bytes"],
    )
    return _json(closed)


async def record_draft(request: Request):
    try:
        body = await _json_body(request)
        journal = body.get("journal")
        journal_id = body.get("journal_id")
        if journal is not None and journal_id is not None:
            raise record_core.RecordError("two_journals")
        if journal is None and journal_id is None:
            raise _bad_request("journal or journal_id is required.")
        if journal is None:
            if not isinstance(journal_id, str) or not journal_id:
                raise _bad_request("journal_id must be a string.")
            journal = record_core.get_store().journal_for_draft(journal_id)
            journal_bytes = record_core.serialized_size(journal)
        else:
            if not isinstance(journal, dict):
                raise record_core.RecordError("bad_journal", "journal must be an object.")
            journal_bytes = record_core.serialized_size(journal)
            if journal_bytes > record_core.MAX_JOURNAL_BYTES:
                raise record_core.RecordError("journal_too_large")

        name_hint = body.get("name_hint")
        if name_hint is not None and not isinstance(name_hint, str):
            raise _bad_request("name_hint must be a string.")

        result = record_core.validate(journal)
        draft = record_core.draft(result.journal, name_hint)
    except record_core.RecordError as exc:
        log.info("onyx-record: draft refusé (%s)", exc.code)
        return _refused(exc)
    except Exception as exc:
        log.error("onyx-record: draft failed (%s)", type(exc).__name__)
        return _refused(record_core.RecordError("recorder_unavailable"))

    draft.update(
        {
            "warnings": list(result.warnings),
            "journal_bytes": journal_bytes,
        }
    )
    # Le brouillon n'écrit rien (D5) : aucun fichier, aucun état, aucun cache.
    log.info("onyx-record: brouillon servi (%s étapes, %s octets reçus)", draft["steps"], journal_bytes)
    return _json(draft)


# ---------------------------------------------------------------------------
# Router : le seul mode de montage
# ---------------------------------------------------------------------------

router = APIRouter()


@router.post("/skills/record/start", include_in_schema=False)
async def skills_record_start(request: Request):
    return await record_start(request)


@router.post("/skills/record/stop", include_in_schema=False)
async def skills_record_stop(request: Request):
    return await record_stop(request)


@router.post("/skills/draft", include_in_schema=False)
async def skills_draft(request: Request):
    return await record_draft(request)


@router.get("/health")
def health() -> dict:
    """État du serveur d'enregistrement — sans journal, sans identifiant de bail."""
    state = record_core.get_store().health()
    return {"plugin": PLUGIN_NAME, "version": VERSION, "ok": True, **state}


@router.get("/info")
def info() -> dict:
    return {
        "plugin": PLUGIN_NAME,
        "version": VERSION,
        "prefix": PREFIX,
        "mount": "router",
        "limits": {
            "max_seconds": record_core.MAX_SECONDS,
            "grace_seconds": record_core.GRACE_SECONDS,
            "max_events": record_core.MAX_EVENTS,
            "max_journal_bytes": record_core.MAX_JOURNAL_BYTES,
        },
        "routes": [
            {
                "name": "skills.record.start",
                "method": "POST",
                "path": f"{PREFIX}/skills/record/start",
                "body": ["bridge.endpoint", "bridge.session", "discard_pending?"],
            },
            {
                "name": "skills.record.stop",
                "method": "POST",
                "path": f"{PREFIX}/skills/record/stop",
                "body": ["session_id", "journal?"],
            },
            {
                "name": "skills.draft",
                "method": "POST",
                "path": f"{PREFIX}/skills/draft",
                "body": ["journal|journal_id", "name_hint?"],
            },
            {"name": "health", "method": "GET", "path": f"{PREFIX}/health"},
            {"name": "info", "method": "GET", "path": f"{PREFIX}/info"},
        ],
    }
