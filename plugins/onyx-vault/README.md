# onyx-vault — per-profile secret vault for Onyx

Hermes "dashboard-only" plugin: it adds backend routes under
`/api/plugins/onyx-vault/` and nothing else. The secrets are encrypted at rest
(Fernet) in SQLite, outside the Hermes repository, one set per profile (`onyx`, `staging`,
…). No value is ever returned over HTTP, written in plaintext to disk,
logged or copied into an exception.

Installation, activation, uninstallation: see **[INSTALL.md](INSTALL.md)**.

## API

All routes require the Dashboard session token
(`X-Hermes-Session-Token`), like any `/api/` route.

| Method | Path | Input | Output |
|---|---|---|---|
| `GET` | `/api/plugins/onyx-vault/vault.list` | `?profile=<name>` | `{profile, count, secrets:[{name, fingerprint, updated_at, used_by}]}` |
| `POST` | `/api/plugins/onyx-vault/vault.put` | `{profile, name, value, used_by?}` | `{profile, name, fingerprint, updated_at, used_by, created}` |
| `POST` | `/api/plugins/onyx-vault/vault.revoke` | `{profile, name}` | `{profile, name, revoked}` |
| `GET` | `/api/plugins/onyx-vault/health` | — | vault state (never a secret) |
| `GET` | `/api/plugins/onyx-vault/info` | — | version, routes, mount target |

```bash
B=http://127.0.0.1:9119/api/plugins/onyx-vault
H="X-Hermes-Session-Token: $TOKEN"

curl -s -X POST "$B/vault.put" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY","value":"sk-…","used_by":["onyx-android"]}'
# -> {"profile":"onyx","name":"OPENAI_API_KEY","fingerprint":"…1234","updated_at":"…","used_by":["onyx-android"],"created":true}

curl -s "$B/vault.list?profile=onyx" -H "$H"
# -> {"profile":"onyx","count":1,"secrets":[{"name":"OPENAI_API_KEY","fingerprint":"…1234",…}]}

curl -s -X POST "$B/vault.revoke" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY"}'
# -> {"profile":"onyx","name":"OPENAI_API_KEY","revoked":true}
```

Codes: `200` ok · `400` invalid input · `401` no token · `404` plugin not
enabled · `413` body too large · `503` key missing or invalid (response with the
remediation command).

`fingerprint` = the last 4 characters of the value, `****` if the value is
shorter than 8 characters.

## On-disk format

`~/.hermes/vault/onyx-vault.db` (0600) — `secrets` table:

| column | content |
|---|---|
| `profile`, `name` | identifiers, in plaintext |
| `ciphertext` | Fernet token of an envelope `{v, p, n, s}`: the value **and** its `(profile, name)` |
| `fingerprint` | last 4 characters |
| `used_by` | declarative JSON list |
| `created_at`, `updated_at` | ISO 8601 UTC |

Binding the ciphertext to its `(profile, name)` detects when a row is moved to
another. A different key produces `400 "déchiffrement impossible"` (decryption
impossible), never a trace of the value.

## Structure

```
onyx-vault-plugin/
├── INSTALL.md
├── README.md
├── dashboard/            # <- only folder to copy into ~/.hermes/plugins/onyx-vault/
│   ├── manifest.json     # name, hidden tab, api: plugin_api.py
│   ├── plugin_api.py     # router = APIRouter() + mounting of routes at ?profile=
│   ├── onyx_vault.py     # core testable on its own: Fernet + SQLite + fingerprints
│   └── dist/index.js     # empty module (no UI)
└── tests/
    ├── test_vault_core.py       # 17 tests: no-leak, encryption, validation
    ├── test_plugin_api.py       # 12 tests: HTTP contract (bare FastAPI)
    └── test_dashboard_mount.py  #  6 tests: real Dashboard, 401/mount/put/list/revoke
```

## Tests

```bash
cd /home/john/Bureau/onyx-vault-plugin
PY=$(ls -d ~/.hermes/tools/python-3.14.7*/bin/python3 | head -1)

# core + HTTP contract (no need for Hermes)
"$PY" -m unittest tests.test_vault_core tests.test_plugin_api -v

# integration with the real Dashboard, in a throwaway HERMES_HOME (does not touch the running server)
cd ~/.hermes/hermes-agent
PYTHONPATH=/home/john/.hermes/hermes-agent "$PY" \
  /home/john/Bureau/onyx-vault-plugin/tests/test_dashboard_mount.py
```

## Two decisions to be aware of

**1. `?profile=` is a vault profile name, not a Hermes profile.**
The Dashboard mounts each plugin with a dependency whose `profile` parameter
is read from the query string and designates a **Hermes** profile; with
`?profile=onyx` it answers `404 Profile 'onyx' does not exist` before reaching
the plugin. The vault's three routes are therefore registered on the Dashboard
application (which rules out only that scope dependency: authentication,
per-request consent and body limits remain active middleware). Routes without
`?profile=` (`health`, `info`) go through the standard `APIRouter`.
Details and measurements: `tests/test_dashboard_mount.py`, docstring of `plugin_api.py`,
§8 of `INSTALL.md`.

**2. A missing key breaks nothing.** `plugin_api.py` always imports: without a key
it logs an explicit warning and answers `503` with the generation
command. As soon as the key appears, the next request works — no
restart, no Dashboard crash.
