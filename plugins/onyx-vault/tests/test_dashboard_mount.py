"""Preuve d'intégration : le vrai Dashboard Hermes monte le plugin et le sert.

Ce fichier n'est pas un test unitaire : il démarre le vrai ``hermes_cli.web_server``
dans un ``HERMES_HOME`` jetable (aucun accès au serveur de production), puis vérifie
de bout en bout :

1. plugin installé mais absent de ``plugins.enabled`` -> aucune route montée ;
2. ajout dans ``plugins.enabled`` -> montage à chaud à la première requête ;
3. sans jeton de session -> ``401 Unauthorized`` ;
4. avec jeton -> ``put`` / ``list`` / ``revoke`` (empreinte seulement, jamais la valeur) ;
5. plugin désactivé -> ``404 Plugin not found`` (porte de consentement par requête) ;
6. clé absente -> ``503`` + remédiation, sans faire tomber le serveur.

Lancer (le venv Hermes n'est pas sur le PATH) :
    PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)
    cd ~/.hermes/hermes-agent
    PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" \\
        /home/john/Bureau/onyx-vault-plugin/tests/test_dashboard_mount.py
"""

from __future__ import annotations

import json
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PLUGIN_NAME = "onyx-vault"
SENTINEL = "SENTINEL-dashboard-do-not-leak-3b8e-QRST"

_state: dict = {}


def _write_config(home: Path, enabled: list) -> None:
    (home / "config.yaml").write_text(
        "plugins:\n  enabled: " + json.dumps(enabled) + "\n  disabled: []\n", encoding="utf-8"
    )


def setUpModule() -> None:
    """Installe le plugin dans un HERMES_HOME jetable AVANT d'importer le serveur."""
    tmp = tempfile.mkdtemp(prefix="onyx-vault-test-")
    home = Path(tmp) / "home"
    home.mkdir(parents=True)
    (home / "plugins").mkdir()

    dst = home / "plugins" / PLUGIN_NAME
    shutil.copytree(ROOT / "dashboard", dst / "dashboard")

    os.environ["HERMES_HOME"] = str(home)
    os.environ.pop("HERMES_DASHBOARD_SESSION_TOKEN", None)
    os.environ.pop("ONYX_VAULT_MOUNT", None)
    os.environ["ONYX_VAULT_DIR"] = str(Path(tmp) / "vault")
    _write_config(home, [])  # installé, pas encore activé

    try:
        import hermes_bootstrap  # noqa: F401  (prépare sys.path, comme le serveur)

        # cryptography n'est visible qu'après le bootstrap : le venv Hermes a la
        # dépendance, l'interpréteur de départ non.
        from cryptography.fernet import Fernet

        from hermes_cli import web_server, web_server_dashboard
    except Exception as exc:  # environnement sans Hermes : on saute proprement
        raise unittest.SkipTest(f"hermes_cli indisponible : {exc}") from None

    os.environ["ONYX_VAULT_KEY"] = Fernet.generate_key().decode()

    _state.update(
        tmp=tmp,
        home=home,
        web_server=web_server,
        dashboard=web_server_dashboard,
        client=_TestClient(web_server.app),
    )


def tearDownModule() -> None:
    tmp = _state.get("tmp")
    if tmp:
        shutil.rmtree(tmp, ignore_errors=True)


class _TestClient:
    """Petit adaptateur : TestClient paresseux pour ne pas dépendre de httpx à l'import."""

    def __init__(self, app):
        from starlette.testclient import TestClient

        self._client = TestClient(app)

    def __getattr__(self, name):
        return getattr(self._client, name)


class TestDashboardMount(unittest.TestCase):
    def _routes(self) -> list:
        return [
            route.path
            for route in _state["web_server"].app.router.routes
            if getattr(route, "path", "").startswith(f"/api/plugins/{PLUGIN_NAME}")
        ]

    def _auth(self) -> dict:
        server = _state["web_server"]
        return {server._SESSION_HEADER_NAME: server._SESSION_TOKEN}

    def test_01_not_enabled_means_no_route(self):
        self.assertEqual(self._routes(), [], "un plugin non activé ne doit monter aucune route")
        print(f"\n[1] plugins.enabled=[] -> routes montées : {self._routes()}")

    def test_02_hot_mount_on_first_request(self):
        _write_config(_state["home"], [PLUGIN_NAME])
        mounted = _state["dashboard"]._try_hot_mount_plugin_api(PLUGIN_NAME)
        self.assertTrue(mounted, "le montage à chaud aurait dû réussir")
        routes = self._routes()
        self.assertIn(f"/api/plugins/{PLUGIN_NAME}/vault.list", routes)
        self.assertIn(f"/api/plugins/{PLUGIN_NAME}/vault.put", routes)
        self.assertIn(f"/api/plugins/{PLUGIN_NAME}/vault.revoke", routes)
        print(f"[2] plugins.enabled=['{PLUGIN_NAME}'] -> routes montées : {routes}")

    def test_03_unauthenticated_is_401(self):
        response = _state["client"].get(
            f"/api/plugins/{PLUGIN_NAME}/vault.list", params={"profile": "onyx"}
        )
        self.assertEqual(response.status_code, 401)
        print(f"[3] GET sans jeton -> {response.status_code} {response.text}")

    def test_04_put_list_revoke_over_http(self):
        client = _state["client"]
        base = f"/api/plugins/{PLUGIN_NAME}"

        put = client.post(
            f"{base}/vault.put",
            json={
                "profile": "onyx",
                "name": "OPENAI_API_KEY",
                "value": SENTINEL,
                "used_by": ["onyx-android"],
            },
            headers=self._auth(),
        )
        self.assertEqual(put.status_code, 200, put.text)
        self.assertNotIn(SENTINEL, put.text)
        self.assertEqual(put.json()["fingerprint"], "QRST")
        print(f"[4a] POST vault.put -> {put.status_code} {put.text}")

        listing = client.get(
            f"{base}/vault.list", params={"profile": "onyx"}, headers=self._auth()
        )
        self.assertEqual(listing.status_code, 200, listing.text)
        body = listing.json()
        self.assertEqual(body["count"], 1)
        self.assertEqual(body["secrets"][0]["name"], "OPENAI_API_KEY")
        self.assertNotIn(SENTINEL, listing.text)
        self.assertNotIn(SENTINEL[:-4], listing.text)
        print(f"[4b] GET vault.list?profile=onyx -> {listing.status_code} {listing.text}")

        db = Path(os.environ["ONYX_VAULT_DIR"]) / "onyx-vault.db"
        self.assertTrue(db.is_file())
        self.assertNotIn(SENTINEL.encode(), db.read_bytes(), "valeur en clair sur disque")
        print(f"[4c] disque : {db} ({db.stat().st_size} octets, 0 occurrence de la valeur)")

        revoke = client.post(
            f"{base}/vault.revoke",
            json={"profile": "onyx", "name": "OPENAI_API_KEY"},
            headers=self._auth(),
        )
        self.assertEqual(revoke.status_code, 200, revoke.text)
        self.assertTrue(revoke.json()["revoked"])
        print(f"[4d] POST vault.revoke -> {revoke.status_code} {revoke.text}")

        emptied = client.get(f"{base}/vault.list", params={"profile": "onyx"}, headers=self._auth())
        self.assertEqual(emptied.json()["count"], 0)
        print(f"[4e] GET vault.list après révocation -> {emptied.status_code} {emptied.text}")

    def test_05_missing_key_is_503_not_a_crash(self):
        client = _state["client"]
        previous = os.environ.pop("ONYX_VAULT_KEY")
        os.environ["ONYX_VAULT_DIR"] = str(Path(_state["tmp"]) / "sans-cle")
        try:
            _reload_core(_state["web_server"])
            response = client.get(
                f"/api/plugins/{PLUGIN_NAME}/vault.list", params={"profile": "onyx"}, headers=self._auth()
            )
            self.assertEqual(response.status_code, 503, response.text)
            self.assertIn("vault_unavailable", response.text)
            print(f"[5] clé absente -> {response.status_code} {response.text[:160]}...")
            health = client.get(f"/api/plugins/{PLUGIN_NAME}/health", headers=self._auth())
            self.assertEqual(health.status_code, 200)
            self.assertFalse(health.json()["ok"])
            print(f"[5b] GET health -> {health.status_code} {health.text[:120]}...")
        finally:
            os.environ["ONYX_VAULT_KEY"] = previous
            os.environ["ONYX_VAULT_DIR"] = str(Path(_state["tmp"]) / "vault")
            _reload_core(_state["web_server"])

    def test_06_disabled_at_runtime_is_refused(self):
        _write_config(_state["home"], [])
        response = _state["client"].get(
            f"/api/plugins/{PLUGIN_NAME}/vault.list", params={"profile": "onyx"}, headers=self._auth()
        )
        self.assertEqual(response.status_code, 404)
        self.assertIn("Plugin not found", response.text)
        print(f"[6] plugin désactivé -> {response.status_code} {response.text}")


def _reload_core(web_server=None) -> None:
    """Le plugin garde un singleton paresseux : on le vide entre deux scénarios."""
    core = sys.modules.get("onyx_vault_core")
    if core is not None and hasattr(core, "reset_vault"):
        core.reset_vault()


if __name__ == "__main__":
    unittest.main(verbosity=2)
