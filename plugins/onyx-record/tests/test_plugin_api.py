"""Contrat HTTP du plugin sur une application FastAPI nue (sans Hermes).

Vérifie ce que le client lit vraiment : le statut, ``detail.code`` en première
clé, un corps de refus sous 300 caractères, jamais un texte secret. Le montage
par le vrai Dashboard (authentification, activation) est prouvé séparément dans
``test_dashboard_mount.py``.

Lancer depuis la racine du plugin :
    python -m unittest discover -s tests -t tests -v
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import journal_fixture as fixture  # noqa: E402
import support  # noqa: E402

if not support.ensure_runtime():
    raise unittest.SkipTest("fastapi indisponible dans cet interpréteur")

from fastapi import FastAPI  # noqa: E402
from starlette.testclient import TestClient  # noqa: E402

api = support.load_api()
core = api.record_core

# Petit corps de refus : ``ServeClient`` ne garde que 300 caractères.
MAX_ERROR_BODY = 300


class ApiCase(unittest.TestCase):
    def setUp(self) -> None:
        core.reset_store()
        self.client = TestClient(self._app())
        self.base = support.BASE
        self.started = self.start()

    def _app(self) -> FastAPI:
        app = FastAPI()
        app.include_router(api.router, prefix=support.BASE)
        return app

    def start(self, **overrides) -> dict:
        body = {"bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"}}
        body.update(overrides)
        response = self.client.post(f"{self.base}/skills/record/start", json=body)
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def journal(self, session_id: str, **overrides) -> dict:
        journal = fixture.golden_journal(session_id=session_id, bridge_session="pc-7f21")
        journal.update(overrides)
        return journal

    def assertRefusal(self, response, status: int, code: str) -> dict:
        self.assertEqual(response.status_code, status, response.text)
        body = response.json()
        self.assertEqual(list(body), ["detail"], "le refus est sous 'detail' (spec §1)")
        detail = body["detail"]
        self.assertEqual(list(detail)[0], "code", "code est la première clé")
        self.assertEqual(detail["code"], code)
        self.assertTrue(detail["message"])
        self.assertLessEqual(len(response.text), MAX_ERROR_BODY, "le corps tient en 300 caractères")
        self.assertEqual(response.headers["cache-control"], "no-store, no-cache, must-revalidate")
        return detail


class TestHappyPath(ApiCase):
    def test_start_stop_draft(self):
        session_id = self.started["session_id"]
        journal = self.journal(session_id)

        stopped = self.client.post(
            f"{self.base}/skills/record/stop", json={"session_id": session_id, "journal": journal}
        )
        self.assertEqual(stopped.status_code, 200, stopped.text)
        closed = stopped.json()
        self.assertEqual(closed["end_reason"], "stopped")
        self.assertEqual(closed["journal"]["session_id"], session_id)
        self.assertEqual(closed["bytes"], core.serialized_size(closed["journal"]))

        drafted = self.client.post(
            f"{self.base}/skills/draft", json={"journal": closed["journal"], "name_hint": "creer-une-facture"}
        )
        self.assertEqual(drafted.status_code, 200, drafted.text)
        body = drafted.json()
        self.assertEqual(
            sorted(body), ["journal_bytes", "markdown", "name", "review", "steps", "title", "warnings"]
        )
        self.assertEqual(body["name"], "creer-une-facture")
        self.assertEqual(body["steps"], 9)
        self.assertEqual(body["markdown"], Path(fixture.GOLDEN_PATH).read_text(encoding="utf-8"))
        print(f"\n[happy] stop -> {closed['actions']} actions, {closed['bytes']} octets ; "
              f"draft -> {body['steps']} étapes, {body['journal_bytes']} octets reçus")

    def test_draft_accepts_the_journal_it_stored(self):
        session_id = self.started["session_id"]
        self.client.post(
            f"{self.base}/skills/record/stop", json={"session_id": session_id, "journal": self.journal(session_id)}
        )
        via_journal = self.client.post(
            f"{self.base}/skills/draft", json={"journal": self.journal(session_id)}
        )
        via_id = self.client.post(f"{self.base}/skills/draft", json={"journal_id": session_id})
        self.assertEqual(via_journal.status_code, 200, via_journal.text)
        self.assertEqual(via_id.status_code, 200, via_id.text)
        self.assertEqual(via_journal.json()["markdown"], via_id.json()["markdown"])

    def test_draft_is_deterministic_over_http(self):
        journal = self.journal(self.started["session_id"])
        first = self.client.post(f"{self.base}/skills/draft", json={"journal": journal}).json()
        second = self.client.post(f"{self.base}/skills/draft", json={"journal": journal}).json()
        self.assertEqual(first, second)

    def test_abort_with_null_journal(self):
        response = self.client.post(
            f"{self.base}/skills/record/stop",
            json={"session_id": self.started["session_id"], "journal": None},
        )
        self.assertEqual(response.status_code, 200, response.text)
        self.assertEqual(response.json()["end_reason"], "discarded")
        self.assertNotIn("journal", response.json())


class TestRefusals(ApiCase):
    def test_double_start(self):
        response = self.client.post(
            f"{self.base}/skills/record/start",
            json={"bridge": {"endpoint": "h:1", "session": "s"}},
        )
        detail = self.assertRefusal(response, 409, "already_recording")
        self.assertEqual(detail["session_id"], self.started["session_id"])

    def test_not_recording(self):
        core.reset_store()
        response = self.client.post(
            f"{self.base}/skills/record/stop", json={"session_id": "rec_AAAAAAAAAAAA"}
        )
        self.assertRefusal(response, 409, "not_recording")

    def test_unknown_session(self):
        response = self.client.post(
            f"{self.base}/skills/record/stop", json={"session_id": "rec_BBBBBBBBBBBB"}
        )
        self.assertRefusal(response, 409, "unknown_session")

    def test_profile_is_refused(self):
        core.reset_store()
        response = self.client.post(
            f"{self.base}/skills/record/start",
            json={"profile": "onyx", "bridge": {"endpoint": "h:1", "session": "s"}},
        )
        self.assertRefusal(response, 400, "profile_not_supported")

    def test_bad_bridge(self):
        core.reset_store()
        response = self.client.post(f"{self.base}/skills/record/start", json={"bridge": {}})
        self.assertRefusal(response, 400, "bad_bridge")

    def test_bad_json_never_echoes_the_body(self):
        secret = "SENTINEL-json-9f2b"
        response = self.client.post(
            f"{self.base}/skills/record/stop",
            content=f'{{"session_id": "{secret}"'.encode(),
            headers={"content-type": "application/json"},
        )
        self.assertRefusal(response, 400, "bad_request")
        self.assertNotIn(secret, response.text)

    def test_nan_is_not_json(self):
        response = self.client.post(
            f"{self.base}/skills/record/start",
            content=b'{"bridge": {"endpoint": "h:1", "session": NaN}}',
            headers={"content-type": "application/json"},
        )
        self.assertRefusal(response, 400, "bad_request")

    def test_two_journals(self):
        response = self.client.post(
            f"{self.base}/skills/draft",
            json={"journal": self.journal("rec_AAAAAAAAAAAA"), "journal_id": "rec_AAAAAAAAAAAA"},
        )
        self.assertRefusal(response, 400, "two_journals")

    def test_draft_without_anything(self):
        response = self.client.post(f"{self.base}/skills/draft", json={})
        self.assertRefusal(response, 400, "bad_request")

    def test_draft_while_recording(self):
        response = self.client.post(
            f"{self.base}/skills/draft", json={"journal_id": self.started["session_id"]}
        )
        self.assertRefusal(response, 409, "still_recording")

    def test_draft_with_unknown_id(self):
        response = self.client.post(f"{self.base}/skills/draft", json={"journal_id": "rec_CCCCCCCCCCCC"})
        self.assertRefusal(response, 409, "no_journal")

    def test_journal_too_large(self):
        journal = self.journal(self.started["session_id"])
        journal["events"] = [
            {
                "seq": index + 1,
                "at_ms": index,
                "kind": "type",
                "target": {"window": "F"},
                "text": "x" * 2000,
                "chars": 2000,
            }
            for index in range(200)
        ]
        response = self.client.post(
            f"{self.base}/skills/record/stop",
            json={"session_id": self.started["session_id"], "journal": journal},
        )
        self.assertRefusal(response, 413, "journal_too_large")

    def test_version_unsupported(self):
        journal = self.journal(self.started["session_id"], version=2)
        response = self.client.post(
            f"{self.base}/skills/record/stop",
            json={"session_id": self.started["session_id"], "journal": journal},
        )
        self.assertRefusal(response, 422, "version_unsupported")


class TestSecret(ApiCase):
    def test_secret_text_is_refused_and_never_echoed(self):
        secret = "SENTINEL-mot-de-passe-7c1d"
        journal = self.journal(self.started["session_id"])
        journal["events"][6]["text"] = secret

        for path in ("/skills/record/stop", "/skills/draft"):
            with self.subTest(path=path):
                payload = {"journal": journal}
                if path.endswith("stop"):
                    payload["session_id"] = self.started["session_id"]
                response = self.client.post(f"{self.base}{path}", json=payload)
                self.assertRefusal(response, 422, "secret_text_present")
                self.assertNotIn(secret, response.text)
                self.assertNotIn(secret[:12], response.text)
        print(f"\n[secret] 422 secret_text_present, aucune occurrence du texte dans la réponse")

    def test_the_draft_never_contains_a_secret(self):
        journal = self.journal(self.started["session_id"])
        drafted = self.client.post(f"{self.base}/skills/draft", json={"journal": journal})
        self.assertEqual(drafted.status_code, 200, drafted.text)
        self.assertNotIn(fixture.SECRET, drafted.text)


class TestHealthAndInfo(ApiCase):
    def test_health_has_no_journal_and_no_session(self):
        session_id = self.started["session_id"]
        body = self.client.get(f"{self.base}/health").json()
        self.assertTrue(body["ok"])
        self.assertTrue(body["recording"])
        self.assertEqual(body["plugin"], "onyx-record")
        self.assertNotIn(session_id, json.dumps(body))
        self.assertEqual(
            sorted(body),
            [
                "grace_seconds",
                "journal_pending",
                "max_events",
                "max_journal_bytes",
                "max_seconds",
                "ok",
                "plugin",
                "recording",
                "remaining_seconds",
                "version",
            ],
            "l'état ne porte aucun journal, aucun identifiant de bail",
        )

    def test_health_after_stop_says_a_journal_waits(self):
        self.client.post(
            f"{self.base}/skills/record/stop",
            json={"session_id": self.started["session_id"], "journal": self.journal(self.started["session_id"])},
        )
        body = self.client.get(f"{self.base}/health").json()
        self.assertFalse(body["recording"])
        self.assertTrue(body["journal_pending"])
        self.assertNotIn("markdown", json.dumps(body))

    def test_info_lists_the_three_routes(self):
        body = self.client.get(f"{self.base}/info").json()
        self.assertEqual(body["prefix"], support.BASE)
        self.assertEqual(body["mount"], "router")
        paths = [route["path"] for route in body["routes"]]
        for suffix in ("/skills/record/start", "/skills/record/stop", "/skills/draft", "/health", "/info"):
            self.assertIn(support.BASE + suffix, paths)
        for route in body["routes"]:
            self.assertLessEqual(set(route), {"name", "method", "path", "body"})
        self.assertEqual(sorted(body), ["limits", "mount", "plugin", "prefix", "routes", "version"])


class TestNoWrite(ApiCase):
    def test_nothing_is_written_on_disk(self):
        """D5 : un brouillon qui n'a pas été relu n'atterrit nulle part."""
        with tempfile.TemporaryDirectory() as tmp:
            before = sorted(p.name for p in Path(tmp).rglob("*"))
            journal = self.journal(self.started["session_id"])
            self.client.post(
                f"{self.base}/skills/record/stop",
                json={"session_id": self.started["session_id"], "journal": journal},
            )
            self.client.post(f"{self.base}/skills/draft", json={"journal": journal})
            after = sorted(p.name for p in Path(tmp).rglob("*"))
        self.assertEqual(before, after)

    def test_the_dashboard_directory_is_untouched(self):
        before = {p.name for p in support.DASHBOARD.rglob("*")}
        self.client.post(f"{self.base}/skills/draft", json={"journal": self.journal(self.started["session_id"])})
        after = {p.name for p in support.DASHBOARD.rglob("*")}
        self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main(verbosity=2)
