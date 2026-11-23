"""onyx-vault — vault API, mounted under ``/api/plugins/onyx-vault/``.

HTTP contract (no response ever contains a value):

* ``GET  vault.list?profile=<profile>``  -> ``{profile, count, secrets:[{name, fingerprint, updated_at, used_by}]}``
* ``POST vault.put``   ``{profile, name, value, used_by?}`` -> ``{profile, name, fingerprint, updated_at, used_by, created}``
* ``POST vault.revoke`` ``{profile, name}`` -> ``{profile, name, revoked}``
* ``GET  health`` -> vault state (never a secret)
* ``GET  info``   -> version + mount target

Why two mounting mechanisms
---------------------------
The Dashboard mounts each plugin with the
``hermes_cli.web_server_dashboard._plugin_route_secret_scope`` dependency, whose
``profile`` parameter is read from the *query string* and designates a **Hermes profile**. A
``?profile=onyx`` (vault profile) therefore answers ``404 Profile 'onyx' does not exist``
even before reaching this module (measured, cf. ``tests/test_dashboard_mount.py``).

The routes without ``?profile=`` (``health``, ``info``) go through the normal
``APIRouter``, with the Dashboard dependency — that is the standard path. The three
vault routes are registered directly on the Dashboard application, which
bypasses *only* that profile-scope dependency. What the
bypass does not remove: the Dashboard authentication, the per-request consent
gate (``_plugin_api_runtime_gate``, which refuses a disabled plugin) and the body size
limit still apply, since they are mounted as middleware on the application. This plugin
reads no Hermes secret: profile scope is of no use to it.
"""

from __future__ import annotations

import importlib.util
import json
import logging
import os
import sys
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException
from starlette.requests import Request
from starlette.responses import JSONResponse

log = logging.getLogger(__name__)

PLUGIN_NAME = "onyx-vault"
VERSION = "1.0.0"
PREFIX = f"/api/plugins/{PLUGIN_NAME}"
_MAX_BODY_BYTES = 64 * 1024

_NO_STORE = {"Cache-Control": "no-store, no-cache, must-revalidate"}


def _load_core():
    """Load the neighboring ``onyx_vault.py`` (the API file is imported outside the package)."""
    module_name = "onyx_vault_core"
    if module_name in sys.modules:
        return sys.modules[module_name]
    path = Path(__file__).resolve().parent / "onyx_vault.py"
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:  # pragma: no cover - file moved
        raise ImportError(f"{PLUGIN_NAME}: onyx_vault.py introuvable à côté de plugin_api.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


vault_core = _load_core()


# ---------------------------------------------------------------------------
# Responses / errors
# ---------------------------------------------------------------------------


def _json(payload: dict, status_code: int = 200) -> JSONResponse:
    return JSONResponse(payload, status_code=status_code, headers=_NO_STORE)


def _unavailable(exc: Exception) -> JSONResponse:
    """Actionable 503: the vault is disabled, the Dashboard stays up."""
    return _json(
        {
            "code": "vault_unavailable",
            "plugin": PLUGIN_NAME,
            "detail": str(exc),
            "remediation": vault_core.KEY_HELP,
        },
        status_code=503,
    )


def _refused(exc: Exception, status_code: int = 400) -> JSONResponse:
    return _json({"code": "invalid_request", "detail": str(exc)}, status_code=status_code)


async def _json_body(request: Request) -> dict:
    """JSON body validated by hand: no validation error copies the value."""
    declared_length = request.headers.get("content-length")
    if declared_length and declared_length.isdigit() and int(declared_length) > _MAX_BODY_BYTES:
        raise HTTPException(status_code=413, detail="corps trop volumineux")
    try:
        raw = await request.body()
    except Exception:
        raise HTTPException(status_code=400, detail="corps illisible") from None
    if len(raw) > _MAX_BODY_BYTES:
        raise HTTPException(status_code=413, detail="corps trop volumineux")
    try:
        body = json.loads(raw or b"{}")
    except Exception:
        raise HTTPException(status_code=400, detail="corps JSON invalide") from None
    if not isinstance(body, dict):
        raise HTTPException(status_code=400, detail="corps JSON attendu : un objet")
    return body


def _string_field(body: dict, field: str) -> str:
    value = body.get(field)
    if not isinstance(value, str) or not value.strip():
        raise HTTPException(status_code=400, detail=f"champ « {field} » manquant ou invalide")
    return value


def _vault():
    """Vault instance. May raise ``VaultUnavailable`` (missing key) -> 503."""
    return vault_core.get_vault()


# ---------------------------------------------------------------------------
# Handlers of the three vault routes
# ---------------------------------------------------------------------------


async def vault_list(request: Request):
    """List a profile's secrets: names, fingerprints, metadata. Never a value."""
    try:
        profile = vault_core.validate_profile(request.query_params.get("profile"))
        metas = _vault().list(profile)
    except vault_core.VaultUnavailable as exc:
        return _unavailable(exc)
    except vault_core.VaultError as exc:
        return _refused(exc)
    except Exception as exc:  # health: never a detail (a value must not leak through a message)
        log.error("onyx-vault: échec de list (%s)", type(exc).__name__)
        return _json({"code": "vault_error", "detail": "échec interne du coffre"}, status_code=500)
    return _json(
        {"profile": profile, "count": len(metas), "secrets": [meta.to_dict() for meta in metas]}
    )


async def vault_put(request: Request):
    """Write/replace a secret and return its fingerprint — the value is never returned."""
    body = await _json_body(request)
    try:
        profile = vault_core.validate_profile(body.get("profile"))
        name = vault_core.validate_name(body.get("name"))
        value = vault_core.validate_value(body.get("value"))
        meta = _vault().put(profile, name, value, body.get("used_by", None))
    except vault_core.VaultUnavailable as exc:
        return _unavailable(exc)
    except vault_core.VaultError as exc:
        return _refused(exc)
    except Exception as exc:  # health: never a detail
        log.error("onyx-vault: échec de put (%s)", type(exc).__name__)
        return _json({"code": "vault_error", "detail": "échec interne du coffre"}, status_code=500)
    log.info("onyx-vault: put servi (profil=%s, nom=%s)", meta["profile"], meta["name"])
    return _json(meta)


async def vault_revoke(request: Request):
    """Remove a secret (idempotent: ``revoked`` is false if it was absent)."""
    body = await _json_body(request)
    try:
        profile = vault_core.validate_profile(body.get("profile"))
        name = vault_core.validate_name(body.get("name"))
        removed = _vault().revoke(profile, name)
    except vault_core.VaultUnavailable as exc:
        return _unavailable(exc)
    except vault_core.VaultError as exc:
        return _refused(exc)
    except Exception as exc:  # health: never a detail
        log.error("onyx-vault: échec de revoke (%s)", type(exc).__name__)
        return _json({"code": "vault_error", "detail": "échec interne du coffre"}, status_code=500)
    return _json({"profile": profile, "name": name, "revoked": removed})


_VAULT_ROUTES = (
    ("/vault.list", vault_list, ["GET"]),
    ("/vault.put", vault_put, ["POST"]),
    ("/vault.revoke", vault_revoke, ["POST"]),
)


# ---------------------------------------------------------------------------
# Standard routes (APIRouter): those that do not use ``?profile=``
# ---------------------------------------------------------------------------

router = APIRouter()


@router.get("/health")
def health() -> dict:
    """Vault state — without secret, without value, queryable by a probe."""
    try:
        vault = vault_core.get_vault()
    except vault_core.VaultUnavailable as exc:
        return {
            "plugin": PLUGIN_NAME,
            "version": VERSION,
            "ok": False,
            "code": "vault_unavailable",
            "detail": str(exc),
            "remediation": vault_core.KEY_HELP,
        }
    return {
        "plugin": PLUGIN_NAME,
        "version": VERSION,
        "ok": True,
        "db": str(vault.db_path),
        "key_source": vault.key.source,
        "key_permissions_secure": vault.key.secure_permissions,
        "secrets_total": vault.count(),
    }


@router.get("/info")
def info() -> dict:
    return {
        "plugin": PLUGIN_NAME,
        "version": VERSION,
        "prefix": PREFIX,
        "mount": _MOUNT_TARGET,
        "routes": [
            {"method": "GET", "path": f"{PREFIX}/vault.list", "query": "profile=<nom>"},
            {"method": "POST", "path": f"{PREFIX}/vault.put", "body": ["profile", "name", "value", "used_by?"]},
            {"method": "POST", "path": f"{PREFIX}/vault.revoke", "body": ["profile", "name"]},
            {"method": "GET", "path": f"{PREFIX}/health"},
            {"method": "GET", "path": f"{PREFIX}/info"},
        ],
    }


# ---------------------------------------------------------------------------
# Mounting
# ---------------------------------------------------------------------------


def register_vault_routes(app: Any = None) -> str:
    """Register the ``?profile=`` routes on the Dashboard app (otherwise on the router).

    Returns ``"app"`` or ``"router"``. Idempotent: a second call duplicates nothing.
    """
    if os.environ.get("ONYX_VAULT_MOUNT", "").strip().lower() == "router":
        # The mode is **forced**: we do not fall back on the Dashboard app, even if
        # `hermes_cli.web_server` is already loaded in this process. Without this distinction,
        # two test suites run in the same interpreter stole each other's routes
        # (the second found the first in `sys.modules` and mounted on someone else's app).
        app = None
    elif app is None:
        module = sys.modules.get("hermes_cli.web_server")  # already loaded = Dashboard in progress
        app = getattr(module, "app", None) if module is not None else None

    if app is not None and hasattr(app, "router"):
        for sub_path, endpoint, methods in _VAULT_ROUTES:
            full_path = PREFIX + sub_path
            if any(getattr(route, "path", "") == full_path for route in app.router.routes):
                continue
            app.router.add_route(full_path, endpoint, methods=methods, include_in_schema=False)
        log.debug("%s: routes montées sur l'application Dashboard (%s)", PLUGIN_NAME, PREFIX)
        return "app"

    for sub_path, endpoint, methods in _VAULT_ROUTES:
        router.add_api_route(sub_path, endpoint, methods=methods)
    log.warning(
        "%s: application Dashboard absente, routes montées sur le routeur du plugin — "
        "dans ce mode, ?profile= est lu par le Dashboard comme un profil Hermes",
        PLUGIN_NAME,
    )
    return "router"


_MOUNT_TARGET = register_vault_routes()


def _warn_if_unavailable() -> None:
    """At mount time: a clear message if the key is missing — the plugin stays up, in 503."""
    try:
        vault_core.load_key()
    except vault_core.VaultUnavailable as exc:
        log.warning("onyx-vault: coffre indisponible (503 en attendant la clé) — %s", exc)


_warn_if_unavailable()
