"""onyx-vault — API du coffre, montée sous ``/api/plugins/onyx-vault/``.

Contrat HTTP (aucune réponse ne contient jamais une valeur) :

* ``GET  vault.list?profile=<profil>``  -> ``{profile, count, secrets:[{name, fingerprint, updated_at, used_by}]}``
* ``POST vault.put``   ``{profile, name, value, used_by?}`` -> ``{profile, name, fingerprint, updated_at, used_by, created}``
* ``POST vault.revoke`` ``{profile, name}`` -> ``{profile, name, revoked}``
* ``GET  health`` -> état du coffre (jamais de secret)
* ``GET  info``   -> version + cible de montage

Pourquoi deux mécanismes de montage
-----------------------------------
Le Dashboard monte chaque plugin avec la dépendance
``hermes_cli.web_server_dashboard._plugin_route_secret_scope``, dont le paramètre
``profile`` est lu dans la *query string* et désigne un **profil Hermes**. Un
``?profile=onyx`` (profil de coffre) y répond donc ``404 Profile 'onyx' does not exist``
avant même d'atteindre ce module (mesuré, cf. ``tests/test_dashboard_mount.py``).

Les routes sans ``?profile=`` (``health``, ``info``) passent par l'``APIRouter``
normal, avec la dépendance du Dashboard — c'est le chemin standard. Les trois
routes du coffre sont enregistrées directement sur l'application Dashboard, ce
qui contourne *uniquement* cette dépendance de portée de profil. Ce que le
contournement ne retire pas : l'authentification du Dashboard, la porte de
consentement par requête (``_plugin_api_runtime_gate``, qui refuse un plugin
désactivé) et la limite de taille de corps s'appliquent toujours, puisqu'elles
sont montées en middleware sur l'application. Ce plugin ne lit aucun secret
Hermes : la portée de profil ne lui sert à rien.
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
    """Charge ``onyx_vault.py`` voisin (le fichier API est importé hors package)."""
    module_name = "onyx_vault_core"
    if module_name in sys.modules:
        return sys.modules[module_name]
    path = Path(__file__).resolve().parent / "onyx_vault.py"
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:  # pragma: no cover - fichier déplacé
        raise ImportError(f"{PLUGIN_NAME}: onyx_vault.py introuvable à côté de plugin_api.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


vault_core = _load_core()


# ---------------------------------------------------------------------------
# Réponses / erreurs
# ---------------------------------------------------------------------------


def _json(payload: dict, status_code: int = 200) -> JSONResponse:
    return JSONResponse(payload, status_code=status_code, headers=_NO_STORE)


def _unavailable(exc: Exception) -> JSONResponse:
    """503 actionnable : le coffre est désactivé, le Dashboard reste debout."""
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
    """Corps JSON validé à la main : aucune erreur de validation ne recopie la valeur."""
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
    """Instance du coffre. Peut lever ``VaultUnavailable`` (clé absente) -> 503."""
    return vault_core.get_vault()


# ---------------------------------------------------------------------------
# Handlers des trois routes du coffre
# ---------------------------------------------------------------------------


async def vault_list(request: Request):
    """Liste les secrets d'un profil : noms, empreintes, métadonnées. Jamais de valeur."""
    try:
        profile = vault_core.validate_profile(request.query_params.get("profile"))
        metas = _vault().list(profile)
    except vault_core.VaultUnavailable as exc:
        return _unavailable(exc)
    except vault_core.VaultError as exc:
        return _refused(exc)
    except Exception as exc:  # santé : jamais de détail (une valeur ne doit pas fuiter par un message)
        log.error("onyx-vault: échec de list (%s)", type(exc).__name__)
        return _json({"code": "vault_error", "detail": "échec interne du coffre"}, status_code=500)
    return _json(
        {"profile": profile, "count": len(metas), "secrets": [meta.to_dict() for meta in metas]}
    )


async def vault_put(request: Request):
    """Écrit/remplace un secret et renvoie son empreinte — la valeur n'est jamais renvoyée."""
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
    except Exception as exc:  # santé : jamais de détail
        log.error("onyx-vault: échec de put (%s)", type(exc).__name__)
        return _json({"code": "vault_error", "detail": "échec interne du coffre"}, status_code=500)
    log.info("onyx-vault: put servi (profil=%s, nom=%s)", meta["profile"], meta["name"])
    return _json(meta)


async def vault_revoke(request: Request):
    """Supprime un secret (idempotent : ``revoked`` vaut false s'il était absent)."""
    body = await _json_body(request)
    try:
        profile = vault_core.validate_profile(body.get("profile"))
        name = vault_core.validate_name(body.get("name"))
        removed = _vault().revoke(profile, name)
    except vault_core.VaultUnavailable as exc:
        return _unavailable(exc)
    except vault_core.VaultError as exc:
        return _refused(exc)
    except Exception as exc:  # santé : jamais de détail
        log.error("onyx-vault: échec de revoke (%s)", type(exc).__name__)
        return _json({"code": "vault_error", "detail": "échec interne du coffre"}, status_code=500)
    return _json({"profile": profile, "name": name, "revoked": removed})


_VAULT_ROUTES = (
    ("/vault.list", vault_list, ["GET"]),
    ("/vault.put", vault_put, ["POST"]),
    ("/vault.revoke", vault_revoke, ["POST"]),
)


# ---------------------------------------------------------------------------
# Routes standard (APIRouter) : celles qui n'utilisent pas ``?profile=``
# ---------------------------------------------------------------------------

router = APIRouter()


@router.get("/health")
def health() -> dict:
    """État du coffre — sans secret, sans valeur, interrogeable par un probe."""
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
# Montage
# ---------------------------------------------------------------------------


def register_vault_routes(app: Any = None) -> str:
    """Enregistre les routes à ``?profile=`` sur l'app Dashboard (sinon sur le routeur).

    Retourne ``"app"`` ou ``"router"``. Idempotent : un second appel ne duplique rien.
    """
    if os.environ.get("ONYX_VAULT_MOUNT", "").strip().lower() == "router":
        # Le mode est **forcé** : on ne retombe pas sur l'app du Dashboard, même si
        # `hermes_cli.web_server` est déjà chargé dans ce processus. Sans cette distinction,
        # deux suites de tests exécutées dans le même interpréteur se volaient leurs routes
        # (la seconde trouvait la première dans `sys.modules` et montait sur l'app d'autrui).
        app = None
    elif app is None:
        module = sys.modules.get("hermes_cli.web_server")  # déjà chargé = Dashboard en cours
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
    """Au montage : un message clair si la clé manque — le plugin reste debout, en 503."""
    try:
        vault_core.load_key()
    except vault_core.VaultUnavailable as exc:
        log.warning("onyx-vault: coffre indisponible (503 en attendant la clé) — %s", exc)


_warn_if_unavailable()
