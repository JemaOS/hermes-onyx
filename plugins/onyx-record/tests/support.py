"""Plugin module loading the way the Dashboard does it (outside the package).

The tests run without installing the plugin: ``dashboard/plugin_api.py`` is executed
by path, exactly as ``_mount_one_plugin_api`` does. The core module name
is derived from the path, as in ``plugin_api._load_core``: two copies of the
plugin in the same interpreter (repository + throwaway ``HERMES_HOME``) keep two
distinct memory states.
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
    """Make ``fastapi`` importable, as the server does at startup.

    The Hermes python is a standalone binary: its dependencies (``fastapi``,
    ``cryptography``…) are on ``sys.path`` only after ``hermes_bootstrap``.
    Returns ``False`` if the environment definitely does not have them.
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
    """The core alone (without FastAPI), under the same name as ``plugin_api``."""
    name = core_name()
    if name in sys.modules:
        return sys.modules[name]
    return _load(DASHBOARD / "onyx_record.py", name)


def load_api(name: str = "onyx_record_plugin_api"):
    """``plugin_api.py`` — the repository's, not a throwaway HERMES_HOME's."""
    if name in sys.modules:
        return sys.modules[name]
    return _load(DASHBOARD / "plugin_api.py", name)
