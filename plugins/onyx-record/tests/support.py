"""Chargement des modules du plugin comme le fait le Dashboard (hors package).

Les tests tournent sans installer le plugin : on exécute ``dashboard/plugin_api.py``
par chemin, exactement comme ``_mount_one_plugin_api`` le fait. Le nom du module
cœur est dérivé du chemin, comme dans ``plugin_api._load_core`` : deux copies du
plugin dans le même interpréteur (dépôt + ``HERMES_HOME`` jetable) gardent deux
états mémoire distincts.
"""

from __future__ import annotations

import hashlib
import importlib.util
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DASHBOARD = ROOT / "dashboard"
PLUGIN_NAME = "onyx-record"
BASE = f"/api/plugins/{PLUGIN_NAME}"


def ensure_runtime() -> bool:
    """Rend ``fastapi`` importable, comme le fait le serveur au démarrage.

    Le python de Hermes est un binaire autonome : ses dépendances (``fastapi``,
    ``cryptography``…) ne sont sur ``sys.path`` qu'après ``hermes_bootstrap``.
    Renvoie ``False`` si l'environnement ne les a décidément pas.
    """
    try:
        import fastapi  # noqa: F401

        return True
    except ImportError:
        pass
    try:
        import hermes_bootstrap  # noqa: F401

        import fastapi  # noqa: F401

        return True
    except Exception:
        return False


def _load(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def core_name() -> str:
    path = (DASHBOARD / "onyx_record.py").resolve()
    return "onyx_record_core_" + hashlib.sha1(str(path).encode("utf-8")).hexdigest()[:10]


def load_core():
    """Le cœur seul (sans FastAPI), sous le même nom que ``plugin_api``."""
    name = core_name()
    if name in sys.modules:
        return sys.modules[name]
    return _load(DASHBOARD / "onyx_record.py", name)


def load_api(name: str = "onyx_record_plugin_api"):
    """``plugin_api.py`` — celui du dépôt, pas celui d'un HERMES_HOME jetable."""
    if name in sys.modules:
        return sys.modules[name]
    return _load(DASHBOARD / "plugin_api.py", name)
