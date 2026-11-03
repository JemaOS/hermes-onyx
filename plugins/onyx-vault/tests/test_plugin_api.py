"""Contrat HTTP du plugin, sur une application FastAPI nue (sans Hermes).

Couvre : aucune valeur dans une réponse, empreinte, 503 sans clé, 400 sans fuite,
``GET vault.list?profile=`` avec le nom de profil demandé.

Lancer :
    python -m unittest tests.test_plugin_api -v
"""

from __future__ import annotations

import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from cryptography.fernet import Fernet
from fastapi import FastAPI

ROOT = Path(__file__).resolve().parent.parent
API_PATH = ROOT / "dashboard" / "plugin_api.py"

SENTINEL = "SENTINEL-http-do-not-leak-7c1d-WXYZ"


def load_plugin_api():
    """Charge le module comme le fait le Dashboard (hors package, par chemin)."""
    spec = importlib.util.spec_from_file_location("onyx_vault_plugin_api", API_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules["onyx_vault_plugin_api"] = module
    spec.loader.exec_module(module)
    return module


class PluginApiTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Force le montage sur l'APIRouter : pas de Dashboard dans ce processus.
        os.environ["ONYX_VAULT_MOUNT"] = "router"
        cls.api = load_plugin_api()

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name)
        self.env = mock.patch.dict(
            os.environ,
            {
                "ONYX_VAULT_DIR": str(self.dir / "vault"),
                "ONYX_VAULT_KEY": Fernet.generate_key().decode(),
            },
        )
        self.env.start()
        os.environ.pop("ONYX_VAULT_KEY_FILE", None)
        self.api.vault_core.reset_vault()

        app = FastAPI()
        app.include_router(self.api.router, prefix="/api/plugins/onyx-vault")
        from starlette.testclient import TestClient

        self.client = TestClient(app)
        self.base = "/api/plugins/onyx-vault"

    def tearDown(self):
        self.api.vault_core.reset_vault()
        self.env.stop()
        self._tmp.cleanup()

    # -- helpers ---------------------------------------------------------
    def put(self, **overrides):
        payload = {"profile": "onyx", "name": "OPENAI_KEY", "value": SENTINEL}
        payload.update(overrides)
        return self.client.post(f"{self.base}/vault.put", json=payload)

    # -- tests -----------------------------------------------------------
    def test_put_returns_fingerprint_only(self):
        response = self.put(used_by=["onyx-android"])
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        self.assertEqual(body["fingerprint"], "WXYZ")
        self.assertEqual(body["name"], "OPENAI_KEY")
        self.assertEqual(body["used_by"], ["onyx-android"])
        self.assertTrue(body["created"])
        self.assertNotIn(SENTINEL, response.text)
        self.assertNotIn("value", body)
        self.assertEqual(response.headers["cache-control"], "no-store, no-cache, must-revalidate")

    def test_list_returns_metadata_only(self):
        self.put()
        response = self.client.get(f"{self.base}/vault.list", params={"profile": "onyx"})
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        self.assertEqual(body["profile"], "onyx")
        self.assertEqual(body["count"], 1)
        self.assertEqual(
            sorted(body["secrets"][0]),
            ["fingerprint", "name", "updated_at", "used_by"],
        )
        self.assertEqual(body["secrets"][0]["fingerprint"], "WXYZ")
        self.assertNotIn(SENTINEL, response.text)
        self.assertNotIn(SENTINEL[:-4], response.text)

    def test_list_requires_profile(self):
        response = self.client.get(f"{self.base}/vault.list")
        self.assertEqual(response.status_code, 400)
        self.assertIn("profil", response.json()["detail"])

    def test_unknown_profile_lists_nothing(self):
        self.put()
        response = self.client.get(f"{self.base}/vault.list", params={"profile": "autre"})
        self.assertEqual(response.json()["count"], 0)

    def test_revoke(self):
        self.put()
        response = self.client.post(f"{self.base}/vault.revoke", json={"profile": "onyx", "name": "OPENAI_KEY"})
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json()["revoked"])
        again = self.client.post(
            f"{self.base}/vault.revoke", json={"profile": "onyx", "name": "OPENAI_KEY"}
        )
        self.assertFalse(again.json()["revoked"])
        listing = self.client.get(f"{self.base}/vault.list", params={"profile": "onyx"}).json()
        self.assertEqual(listing["secrets"], [])

    def test_no_value_in_any_response_or_state(self):
        self.put()
        texts = [
            self.client.get(f"{self.base}/vault.list", params={"profile": "onyx"}).text,
            self.client.get(f"{self.base}/health").text,
            self.client.get(f"{self.base}/info").text,
        ]
        for text in texts:
            self.assertNotIn(SENTINEL, text)
            self.assertNotIn(SENTINEL[:-4], text)

    def test_bad_payload_never_echoes_the_value(self):
        cases = [
            {"profile": "onyx", "name": "K"},  # valeur absente
            {"profile": "onyx", "name": "K", "value": 42},  # type invalide
            {"profile": "onyx", "name": "K", "value": ""},  # vide
            {"profile": "onyx", "name": "K", "value": SENTINEL, "used_by": ["mauvais nom!"]},
        ]
        for payload in cases:
            response = self.client.post(f"{self.base}/vault.put", json=payload)
            self.assertEqual(response.status_code, 400, response.text)
            self.assertNotIn(SENTINEL, response.text)
            self.assertNotIn(SENTINEL[:-4], response.text)

    def test_malformed_body_is_refused(self):
        response = self.client.post(
            f"{self.base}/vault.put", content=b"pas du json", headers={"content-type": "application/json"}
        )
        self.assertEqual(response.status_code, 400)
        self.assertNotIn("pas du json", response.text)

    def test_value_is_never_written_to_disk_in_clear(self):
        self.put()
        db = self.dir / "vault" / "onyx-vault.db"
        self.assertTrue(db.is_file())
        blob = db.read_bytes()
        self.assertNotIn(SENTINEL.encode(), blob)

    def test_health_reports_ok_and_key_source(self):
        body = self.client.get(f"{self.base}/health").json()
        self.assertTrue(body["ok"])
        self.assertEqual(body["key_source"], "env:ONYX_VAULT_KEY")
        self.assertEqual(body["secrets_total"], 0)

    def test_missing_key_answers_503_with_remediation(self):
        os.environ.pop("ONYX_VAULT_KEY", None)
        os.environ["ONYX_VAULT_DIR"] = str(self.dir / "sans-cle")
        self.api.vault_core.reset_vault()

        response = self.client.get(f"{self.base}/vault.list", params={"profile": "onyx"})
        self.assertEqual(response.status_code, 503)
        body = response.json()
        self.assertEqual(body["code"], "vault_unavailable")
        self.assertIn("Fernet.generate_key", body["remediation"])

        self.assertEqual(self.put().status_code, 503)
        health = self.client.get(f"{self.base}/health").json()
        self.assertFalse(health["ok"])

    def test_info_lists_routes(self):
        body = self.client.get(f"{self.base}/info").json()
        self.assertEqual(body["plugin"], "onyx-vault")
        self.assertIn("vault.list", json.dumps(body["routes"]))
        self.assertNotIn(SENTINEL, json.dumps(body))


if __name__ == "__main__":
    unittest.main(verbosity=2)
