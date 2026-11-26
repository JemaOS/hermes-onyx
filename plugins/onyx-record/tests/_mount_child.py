"""Real startup of the Dashboard in a throwaway ``HERMES_HOME`` — one scenario.

Launched by ``test_dashboard_mount.py`` in a fresh interpreter, with
``PYTHONPATH`` pointing at the Hermes repository. It writes nothing outside its ``HERMES_HOME``
and touches neither the production server nor ``~/.hermes``.

Why a separate process: the Dashboard mounts plugin routes at the
moment it imports ``hermes_cli.web_server``, **before** installing its
``/api/plugins/{name}/{path:path}`` fallback. Mounting afterwards (as an in-process
test would do) adds the routes *behind* that fallback, which masks them — measured:
``404 {"detail":"Not Found"}``. Mounting at startup is therefore the only
authoritative way, and it is the one exercised here.

Usage: python _mount_child.py <HERMES_HOME> <enabled|disabled>
Output: one JSON line on stdout (the report); logs go to stderr.
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
    # Idempotent: called after startup, it adds nothing when the order is right.
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

    # Secret journal: 422, and the text must not appear anywhere.
    # The previous stop's journal is still waiting: we discard it explicitly
    # (this is what the client does after converting, see ECARTS.md §6).
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

    # Hot deactivation: the per-request consent gate must refuse.
    with open(os.path.join(home, "config.yaml"), "w", encoding="utf-8") as handle:
        handle.write("plugins:\n  enabled: []\n  disabled: []\n")
    gated = client.get(f"{prefix}/health", headers=headers)
    report["gated"] = {"status": gated.status_code, "body": gated.text}

    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
