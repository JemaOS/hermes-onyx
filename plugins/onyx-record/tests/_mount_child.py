"""Démarrage réel du Dashboard dans un ``HERMES_HOME`` jetable — un scénario.

Lancé par ``test_dashboard_mount.py`` dans un interpréteur neuf, avec
``PYTHONPATH`` sur le dépôt Hermes. Il n'écrit rien hors de son ``HERMES_HOME``
et ne touche ni au serveur de production ni à ``~/.hermes``.

Pourquoi un processus séparé : le Dashboard monte les routes des plugins au
moment où il importe ``hermes_cli.web_server``, **avant** d'installer son repli
``/api/plugins/{name}/{path:path}``. Monter après coup (comme le ferait un test
in-process) ajoute les routes *derrière* ce repli, qui les masque — mesuré :
``404 {"detail":"Not Found"}``. Le montage au démarrage est donc le seul
autoritaire, et c'est celui qu'on exerce ici.

Usage : python _mount_child.py <HERMES_HOME> <enabled|disabled>
Sortie : une ligne JSON sur stdout (le rapport) ; les logs vont sur stderr.
"""

from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

PLUGIN_NAME = "onyx-record"
SENTINEL = "SENTINEL-dashboard-do-not-leak-3b8e-QRST"


def main() -> int:
    home, mode = sys.argv[1], sys.argv[2]
    os.environ["HERMES_HOME"] = home
    os.environ.pop("HERMES_DASHBOARD_SESSION_TOKEN", None)

    import hermes_bootstrap  # noqa: F401

    from hermes_cli import web_server, web_server_dashboard
    from starlette.testclient import TestClient

    import journal_fixture as fixture

    app = web_server.app
    client = TestClient(app)
    prefix = f"/api/plugins/{PLUGIN_NAME}"
    report: dict = {"mode": mode}

    def routes() -> list:
        return [
            route.path
            for route in app.router.routes
            if getattr(route, "path", "").startswith(prefix)
        ]

    report["routes_after_start"] = routes()
    plugins = web_server._get_dashboard_plugins(force_rescan=True)
    report["discovered"] = [
        {"name": p.get("name"), "source": p.get("source"), "api": p.get("_api_file")}
        for p in plugins
        if p.get("name") == PLUGIN_NAME
    ]
    # Idempotent : appelé après le démarrage, il n'ajoute rien au bon ordre.
    web_server_dashboard._mount_plugin_api_routes()
    report["routes_after_mount_call"] = routes()

    headers = {web_server._SESSION_HEADER_NAME: web_server._SESSION_TOKEN}
    start_body = {"bridge": {"endpoint": "192.168.1.24:8765", "session": "pc-7f21"}}

    no_token = client.post(f"{prefix}/skills/record/start", json=start_body)
    report["no_token"] = {"status": no_token.status_code, "body": no_token.text}

    if mode == "disabled":
        with_token = client.post(f"{prefix}/skills/record/start", json=start_body, headers=headers)
        report["with_token"] = {"status": with_token.status_code, "body": with_token.text}
        print(json.dumps(report, ensure_ascii=False))
        return 0

    opened = client.post(f"{prefix}/skills/record/start", json=start_body, headers=headers)
    report["start"] = {"status": opened.status_code, "body": opened.json()}
    session_id = opened.json()["session_id"]

    again = client.post(f"{prefix}/skills/record/start", json=start_body, headers=headers)
    report["second_start"] = {"status": again.status_code, "body": again.json()}

    journal = fixture.golden_journal(session_id=session_id, bridge_session="pc-7f21")
    stopped = client.post(
        f"{prefix}/skills/record/stop",
        json={"session_id": session_id, "journal": journal},
        headers=headers,
    )
    report["stop"] = {"status": stopped.status_code, "body": stopped.json()}

    repeat = client.post(
        f"{prefix}/skills/record/stop",
        json={"session_id": session_id, "journal": journal},
        headers=headers,
    )
    report["second_stop"] = {"status": repeat.status_code, "body": repeat.json()}

    drafted = client.post(
        f"{prefix}/skills/draft", json={"journal": stopped.json()["journal"]}, headers=headers
    )
    report["draft"] = {"status": drafted.status_code, "body": drafted.json()}

    # Journal secret : 422, et le texte ne doit apparaître nulle part.
    # Le journal du stop précédent attend encore : on le jette explicitement
    # (c'est ce que fait le client après avoir converti, voir ECARTS.md §6).
    secret_open = client.post(
        f"{prefix}/skills/record/start",
        json=dict(start_body, discard_pending=True),
        headers=headers,
    )
    secret_id = secret_open.json()["session_id"]
    secret_journal = fixture.golden_journal(session_id=secret_id, bridge_session="pc-7f21")
    secret_journal["events"][6]["text"] = SENTINEL
    refused = client.post(
        f"{prefix}/skills/record/stop",
        json={"session_id": secret_id, "journal": secret_journal},
        headers=headers,
    )
    report["secret"] = {
        "status": refused.status_code,
        "body": refused.json(),
        "leaked": SENTINEL in refused.text,
        "length": len(refused.text),
    }

    abort = client.post(
        f"{prefix}/skills/record/stop",
        json={"session_id": secret_id, "journal": None},
        headers=headers,
    )
    report["abort"] = {"status": abort.status_code, "body": abort.json()}

    health = client.get(f"{prefix}/health", headers=headers)
    report["health"] = {
        "status": health.status_code,
        "body": health.json(),
        "mentions_session": session_id in health.text or secret_id in health.text,
    }

    # Désactivation à chaud : la porte de consentement par requête doit refuser.
    with open(os.path.join(home, "config.yaml"), "w", encoding="utf-8") as handle:
        handle.write("plugins:\n  enabled: []\n  disabled: []\n")
    gated = client.get(f"{prefix}/health", headers=headers)
    report["gated"] = {"status": gated.status_code, "body": gated.text}

    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
