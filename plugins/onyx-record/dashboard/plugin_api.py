"""onyx-record — API of the "demonstration → skill" server.

Mounted under ``/api/plugins/onyx-record/`` by the Hermes Dashboard.

HTTP contract (``docs/SPEC-SKILLS-RECORD.md``):

* ``POST skills/record/start`` -> opens a lease ``{session_id, started_at, deadline_at, max_seconds, grace_seconds}``
* ``POST skills/record/stop``  -> closes the lease and returns the **canonical** journal (never the client's)
* ``POST skills/draft``        -> deterministic Markdown draft + steps to review (stateless)
* ``GET  health`` -> state of the recording server (no secret)
* ``GET  info``   -> version + mount target

A single mount mode
-------------------
This module exposes ``router`` (an ``APIRouter``) and **nothing else**: the
Dashboard includes it under its prefix, with its authentication dependency and the
per-request consent gate. No route is registered directly on
the application, and no environment variable changes the mode.

This is a deliberate correction of the ``onyx-vault`` model: that plugin had two
mount modes (application or router, chosen by ``ONYX_VAULT_MOUNT``), and the
non-authoritative mode broke tests when suites shared an
interpreter. The vault needed the workaround because its routes read
``?profile=`` as a profile name; here no route reads ``?profile=`` (the
profile is refused, and the Hermes profile is the Dashboard's), so the
standard dependency is harmless and sufficient.

Nothing sensitive goes out: no text, no label, no URL, no journal in a log.
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

# A journal weighs at most 262144 bytes; the envelope and the rest fit
# comfortably within double. Beyond that, it is a send that should not have gone out.
_MAX_BODY_BYTES = 2 * 262144

_NO_STORE = {"Cache-Control": "no-store, no-cache, must-revalidate"}


def _load_core():
    """Load the neighboring ``onyx_record.py``, under a name derived from its path.

    The name is not fixed (unlike ``onyx_vault_core``): two copies of the
    plugin in the same interpreter — the repository's and a
    throwaway ``HERMES_HOME``'s — must have **two** memory states, otherwise the
    test suite of one folder sees the leases of the other.
    """
    path = (Path(__file__).resolve().parent / "onyx_record.py").resolve()
    module_name = "onyx_record_core_" + hashlib.sha1(str(path).encode("utf-8")).hexdigest()[:10]
    if module_name in sys.modules:
        return sys.modules[module_name]
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:  # pragma: no cover - file moved
        raise ImportError(f"{PLUGIN_NAME}: onyx_record.py introuvable à côté de plugin_api.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


record_core = _load_core()


# ---------------------------------------------------------------------------
# Responses / refusals
# ---------------------------------------------------------------------------


def _json(payload: dict, status_code: int = 200) -> JSONResponse:
    return JSONResponse(payload, status_code=status_code, headers=_NO_STORE)


def _refused(exc: "record_core.RecordError") -> JSONResponse:
    """The refusal as the spec fixes it: ``{"detail": {"code": …}}``.

    ``code`` is the first key of ``detail`` (hence of the body) because the
    client keeps only the first 300 characters of an error response.
    No field is derived from the journal: no text, no label, no URL.
    """
    return _json({"detail": exc.payload()}, status_code=exc.status)


def _reject_constant(value: str) -> None:
    raise ValueError(f"non-finite JSON constant: {value}")


def _bad_request(message: str) -> "record_core.RecordError":
    return record_core.RecordError("bad_request", message)


async def _json_body(request: Request) -> dict:
    """JSON body read by hand: no validation error copies a field.

    A FastAPI ``RequestValidationError`` would copy the offending value into
    the response; here, an unreadable body returns only a code.
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
        # ``NaN``/``Infinity`` are not JSON: without this refusal, they
        # would reach the response serialization.
        body = json.loads(raw or b"{}", parse_constant=_reject_constant)
    except Exception:
        raise _bad_request("Invalid JSON body.") from None
    if not isinstance(body, dict):
        raise _bad_request("A JSON object is expected.")
    return body


# ---------------------------------------------------------------------------
# The three routes
# ---------------------------------------------------------------------------


async def record_start(request: Request):
    try:
        body = await _json_body(request)
        opened = record_core.get_store().start(body)
    except record_core.RecordError as exc:
        return _refused(exc)
    except Exception as exc:  # never a detail: a journal could get in there
        log.error("onyx-record: start failed (%s)", type(exc).__name__)
        return _refused(record_core.RecordError("recorder_unavailable"))
    log.info("onyx-record: bail ouvert %s", opened["session_id"])
    return _json(opened)


async def record_stop(request: Request):
    try:
        body = await _json_body(request)
        closed = record_core.get_store().stop(body)
    except record_core.RecordError as exc:
        # A refusal says nothing about the journal: code + message, never an excerpt.
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
    # The draft writes nothing (D5): no file, no state, no cache.
    log.info("onyx-record: brouillon servi (%s étapes, %s octets reçus)", draft["steps"], journal_bytes)
    return _json(draft)


# ---------------------------------------------------------------------------
# Router: the only mount mode
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
    """State of the recording server — without journal, without lease identifier."""
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
