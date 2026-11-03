"""Preuve d'intégration : le vrai Dashboard Hermes monte le plugin et le sert.

Le Dashboard tourne dans un interpréteur neuf avec un ``HERMES_HOME`` jetable
(``tests/_mount_child.py``) : aucun accès au serveur de production, aucune
écriture dans ``~/.hermes``. Deux scénarios, deux processus :

* ``enabled``  — installé **et** activé avant l'import : le montage au démarrage
  pose les cinq routes, sans jeton c'est ``401``, avec jeton ``start`` / ``stop``
  / ``draft`` répondent, un journal secret rend ``422`` sans le recopier, et
  désactiver à chaud rend ``404 Plugin not found`` ;
* ``disabled`` — installé mais absent de ``plugins.enabled`` : aucune route.

Pourquoi deux processus plutôt qu'un ``TestClient`` in-process (méthode du
plugin du coffre) : mesuré ici, un montage appelé *après* l'import du serveur
atterrit derrière le repli ``/api/plugins/{name}/{path:path}``, qui répond
``404 {"detail":"Not Found"}``. Le processus neuf reproduit le seul ordre que la
production connaisse.

Lancer (le venv Hermes n'est pas sur le PATH) :
    PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)
    cd ~/.hermes/hermes-agent
    PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" \\
        -m unittest discover -s /home/john/Bureau/onyx-record-plugin/tests \\
                           -t /home/john/Bureau/onyx-record-plugin/tests -v
"""

from __future__ import annotations

import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import journal_fixture as fixture  # noqa: E402
import support  # noqa: E402

PLUGIN_NAME = support.PLUGIN_NAME
CHILD = Path(__file__).resolve().parent / "_mount_child.py"
_state: dict = {}


def _hermes_agent_dir():
    spec = importlib.util.find_spec("hermes_cli")
    if spec and spec.submodule_search_locations:
        return Path(list(spec.submodule_search_locations)[0]).parent
    candidate = Path(
        os.environ.get("HERMES_AGENT_DIR", str(Path.home() / ".hermes" / "hermes-agent"))
    )
    return candidate if (candidate / "hermes_cli").is_dir() else None


def _home(root: Path, enabled: bool) -> Path:
    home = root / ("home" if enabled else "home-disabled")
    home.mkdir(parents=True)
    (home / "plugins").mkdir()
    shutil.copytree(support.ROOT / "dashboard", home / "plugins" / PLUGIN_NAME / "dashboard")
    (home / "config.yaml").write_text(
        "plugins:\n  enabled: " + json.dumps([PLUGIN_NAME] if enabled else []) + "\n  disabled: []\n",
        encoding="utf-8",
    )
    return home


def _run(agent_dir: Path, home: Path, mode: str) -> dict:
    env = dict(os.environ)
    env["PYTHONPATH"] = str(agent_dir)
    env["HERMES_HOME"] = str(home)
    env.pop("HERMES_DASHBOARD_SESSION_TOKEN", None)
    completed = subprocess.run(
        [sys.executable, str(CHILD), str(home), mode],
        cwd=str(agent_dir),
        env=env,
        capture_output=True,
        text=True,
        timeout=180,
    )
    if completed.returncode != 0:
        raise AssertionError(
            f"le Dashboard jetable a échoué ({mode}) :\n"
            f"--- stdout ---\n{completed.stdout[-3000:]}\n"
            f"--- stderr ---\n{completed.stderr[-3000:]}"
        )
    line = [ln for ln in completed.stdout.splitlines() if ln.startswith("{")]
    if not line:
        raise AssertionError(f"rapport illisible ({mode}) :\n{completed.stdout[-3000:]}")
    return json.loads(line[-1])


def setUpModule() -> None:
    agent_dir = _hermes_agent_dir()
    if agent_dir is None:
        raise unittest.SkipTest("dépôt hermes-agent introuvable")

    tmp = tempfile.mkdtemp(prefix="onyx-record-test-")
    root = Path(tmp)
    try:
        _state["enabled"] = _run(agent_dir, _home(root, enabled=True), "enabled")
        _state["disabled"] = _run(agent_dir, _home(root, enabled=False), "disabled")
    finally:
        # On garde les rapports, pas les foyers.
        _state["tmp"] = tmp
    print("\n[montage] enabled :", json.dumps(_state["enabled"], ensure_ascii=False)[:400])
    print("[montage] disabled :", json.dumps(_state["disabled"], ensure_ascii=False)[:200])


def tearDownModule() -> None:
    tmp = _state.get("tmp")
    if tmp:
        shutil.rmtree(tmp, ignore_errors=True)


class TestDashboardMount(unittest.TestCase):
    @property
    def enabled(self) -> dict:
        return _state["enabled"]

    @property
    def disabled(self) -> dict:
        return _state["disabled"]

    def _routes(self, report: dict) -> list:
        return report["routes_after_start"]

    # -- découverte / activation ------------------------------------------
    def test_01_installed_but_not_enabled_means_no_route(self):
        self.assertEqual(
            self._routes(self.disabled), [], "un plugin non activé ne doit monter aucune route"
        )
        self.assertNotEqual(self.disabled["with_token"]["status"], 200)

    def test_02_enabled_plugin_is_discovered_and_mounted(self):
        report = self.enabled
        self.assertEqual(
            [p["name"] for p in report["discovered"]], [PLUGIN_NAME], "découverte du plugin utilisateur"
        )
        self.assertEqual(report["discovered"][0]["source"], "user")
        self.assertEqual(report["discovered"][0]["api"], "plugin_api.py")

        routes = self._routes(report)
        for suffix in ("/skills/record/start", "/skills/record/stop", "/skills/draft", "/health", "/info"):
            self.assertIn(f"/api/plugins/{PLUGIN_NAME}{suffix}", routes)
        self.assertEqual(routes, report["routes_after_mount_call"], "montage idempotent")

    # -- authentification --------------------------------------------------
    def test_03_unauthenticated_is_401(self):
        self.assertEqual(self.enabled["no_token"]["status"], 401)
        self.assertIn("Unauthorized", self.enabled["no_token"]["body"])

    # -- start / stop / draft ---------------------------------------------
    def test_04_start_stop_draft(self):
        report = self.enabled
        opened = report["start"]
        self.assertEqual(opened["status"], 200)
        body = opened["body"]
        self.assertEqual(
            sorted(body), ["deadline_at", "grace_seconds", "max_seconds", "session_id", "started_at"]
        )
        self.assertEqual(body["max_seconds"], 600)
        self.assertEqual(body["grace_seconds"], 120)
        self.assertEqual(body["deadline_at"], round(body["started_at"] + 600, 3))

        self.assertEqual(report["second_start"]["status"], 409)
        self.assertEqual(report["second_start"]["body"]["detail"]["code"], "already_recording")
        self.assertEqual(
            report["second_start"]["body"]["detail"]["session_id"], body["session_id"]
        )

        stopped = report["stop"]
        self.assertEqual(stopped["status"], 200)
        self.assertEqual(stopped["body"]["end_reason"], "stopped")
        self.assertEqual(stopped["body"]["actions"], 11)
        self.assertEqual(stopped["body"]["bytes"], len(
            json.dumps(
                stopped["body"]["journal"],
                ensure_ascii=False,
                allow_nan=False,
                separators=(",", ":"),
            ).encode("utf-8")
        ))

        repeat = report["second_stop"]
        self.assertEqual(repeat["status"], 200)
        self.assertEqual(repeat["body"]["bytes"], stopped["body"]["bytes"], "stop idempotent à l'octet")
        self.assertEqual(repeat["body"]["journal"], stopped["body"]["journal"])

        drafted = report["draft"]
        self.assertEqual(drafted["status"], 200)
        self.assertEqual(drafted["body"]["steps"], 9)
        self.assertEqual(
            drafted["body"]["markdown"],
            Path(fixture.GOLDEN_PATH).read_text(encoding="utf-8"),
            "le serveur rend le brouillon du golden de l'app",
        )
        self.assertEqual(drafted["body"]["journal_bytes"], stopped["body"]["bytes"])

    # -- secret -----------------------------------------------------------
    def test_05_secret_is_refused_not_cleaned(self):
        report = self.enabled["secret"]
        self.assertEqual(report["status"], 422)
        self.assertEqual(report["body"]["detail"]["code"], "secret_text_present")
        self.assertFalse(report["leaked"])
        self.assertLessEqual(report["length"], 300)
        self.assertEqual(
            list(report["body"]["detail"])[0], "code", "code est la première clé du refus"
        )

    def test_06_abort_after_refusal_works(self):
        abort = self.enabled["abort"]
        self.assertEqual(abort["status"], 200)
        self.assertEqual(abort["body"]["end_reason"], "discarded")
        self.assertNotIn("journal", abort["body"])

    # -- health / porte de consentement -----------------------------------
    def test_07_health_has_no_journal_and_no_session(self):
        health = self.enabled["health"]
        self.assertEqual(health["status"], 200)
        self.assertTrue(health["body"]["ok"])
        self.assertFalse(health["mentions_session"], "aucun identifiant de bail dans health")

    def test_08_disabled_at_runtime_is_refused(self):
        gated = self.enabled["gated"]
        self.assertEqual(gated["status"], 404)
        self.assertIn("Plugin not found", gated["body"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
