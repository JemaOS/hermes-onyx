# Installation — Hermes plugin `onyx-vault`

**Per-profile** secret vault, encrypted at rest (Fernet), served by the Hermes
Dashboard under `/api/plugins/onyx-vault/`. No UI, no restart required.

All paths below are those of John's machine (`/home/john`).

---

## 0. What is installed, and where

| Item | Location |
|---|---|
| Plugin code | `~/.hermes/plugins/onyx-vault/dashboard/` |
| Activation | `plugins.enabled` in `~/.hermes/config.yaml` |
| Encrypted database | `~/.hermes/vault/onyx-vault.db` (0600, outside the Hermes repository) |
| Fernet key | `~/.hermes/vault/onyx-vault.key` (0600) |

The vault lives under `~/.hermes/vault/`, **not** under `~/.hermes/hermes-agent/`: a
`hermes update` can neither overwrite it nor lose it.

---

## 1. Copy the plugin

```bash
mkdir -p ~/.hermes/plugins/onyx-vault
cp -r /home/john/Bureau/onyx-vault-plugin/dashboard \
      ~/.hermes/plugins/onyx-vault/dashboard
chmod -R go-w ~/.hermes/plugins/onyx-vault
```

Expected result:

```
~/.hermes/plugins/onyx-vault/dashboard/
├── manifest.json      # plugin declaration (name, hidden tab, api)
├── plugin_api.py      # router = APIRouter() + mounted routes
├── onyx_vault.py      # core: Fernet, SQLite, fingerprints
└── dist/index.js      # empty module (no UI)
```

`INSTALL.md` and `tests/` do not need to be copied — they are working files. Only
the `dashboard/` folder is read by Hermes (it looks for
`<plugins>/*/dashboard/manifest.json`).

---

## 2. Generate the encryption key

```bash
mkdir -p ~/.hermes/vault && chmod 700 ~/.hermes/vault

# 32 random bytes in urlsafe base64 = exactly what Fernet expects.
# No dependencies: any python3 on the machine will do.
python3 -c 'import base64, os; print(base64.urlsafe_b64encode(os.urandom(32)).decode())' \
  > ~/.hermes/vault/onyx-vault.key

chmod 600 ~/.hermes/vault/onyx-vault.key
ls -l ~/.hermes/vault/onyx-vault.key     # must show -rw------- (1, john)
```

Alternative if you prefer to go through `cryptography` (the Hermes venv has it, the
system python does not):

```bash
PY=$(ls -d ~/.hermes/installs/*/environments/*/venv/bin/python3 | head -1)
"$PY" -c 'from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())' \
  > ~/.hermes/vault/onyx-vault.key
```

Check: the key must be 44 characters long and end with `=`.

File-less alternative: put the same value in `ONYX_VAULT_KEY` (for example via
a systemd `EnvironmentFile` with 0600). It is less good: an environment
variable is readable in `/proc/<pid>/environ` by the same user.

> **Back up the key elsewhere** (password manager). Without it, the
> secrets are permanently unreadable: there is no backup key.
> Never commit it, never put it in `~/.hermes/config.yaml`.

---

## 3. Enable the plugin

Open `~/.hermes/config.yaml` and add **exactly this line** (4 spaces
of indentation, under `plugins.enabled`):

```yaml
    - onyx-vault
```

Full block if `plugins.enabled` does not exist yet:

```yaml
plugins:
  enabled:
    - onyx-vault
```

If `plugins:` already exists with other keys, add only the line `- onyx-vault`
to the existing `enabled:` list, same indentation.

> ⚠️ `hermes plugins enable onyx-vault` **does not work** for this plugin:
> the CLI only knows plugins that carry a `plugin.yaml` (with Python
> `register(ctx)` code). A "dashboard-only" plugin — like `kanban` or
> `hermes-achievements`, shipped with Hermes — is enabled only through this line
> in `config.yaml`. It is also the Dashboard's safety guard: without it,
> a user plugin's `plugin_api.py` is never imported
> (`_plugin_api_mount_skip_reason`, GHSA-mcfc-hp25-cjv7).

No restart is required: the first request to
`/api/plugins/onyx-vault/...` triggers hot mounting
(`_PluginApiHotMount`). If the Dashboard is running, it picks up the new config on
the next request.

---

## 4. Verify

```bash
PORT=9119   # Hermes Dashboard port
TOKEN=$(curl -s "http://127.0.0.1:$PORT/" \
        | sed -n 's/.*__HERMES_SESSION_TOKEN__="\([^"]*\)".*/\1/p' | head -1)
B="http://127.0.0.1:$PORT/api/plugins/onyx-vault"
H="X-Hermes-Session-Token: $TOKEN"

# 1. is the vault ready? (no secret in this response)
curl -s "$B/health"; echo

# 2. write a secret (the response contains only the fingerprint)
curl -s -X POST "$B/vault.put" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY","value":"sk-test-1234","used_by":["onyx-android"]}'; echo

# 3. list (fingerprint = last 4 characters, never the value)
curl -s "$B/vault.list?profile=onyx" -H "$H"; echo

# 4. revoke
curl -s -X POST "$B/vault.revoke" -H "$H" -H 'Content-Type: application/json' \
  -d '{"profile":"onyx","name":"OPENAI_API_KEY"}'; echo
```

Expected: `health` → `"ok":true`, `put` → `"fingerprint":"1234"`,
`list` → `{"count":1,...}`, `revoke` → `"revoked":true`.

Without a token, every request answers `401 {"detail":"Unauthorized"}`: that is
the Dashboard authentication, which the plugin does not bypass.

If the Dashboard is behind the login page (`dashboard_auth`), the header
token is replaced by the session cookie: use the browser's Réseau (Network) tab
to replay the requests, or test from the UI.

---

## 5. Uninstall

```bash
# 1. remove the plugin
rm -rf ~/.hermes/plugins/onyx-vault

# 2. remove the line "    - onyx-vault" from ~/.hermes/config.yaml
```

Without step 2, the Dashboard logs a plugin that is enabled but not found.

Delete the data (⚠️ irreversible, the secrets are lost):

```bash
rm -f ~/.hermes/vault/onyx-vault.db ~/.hermes/vault/onyx-vault.key
rmdir ~/.hermes/vault 2>/dev/null || true
```

To cleanly wipe everything on the SQLite side before deleting the file:

```bash
sqlite3 ~/.hermes/vault/onyx-vault.db 'VACUUM;'
```

(A `DELETE` leaves the ciphertext in free pages; `VACUUM` rewrites them. The plaintext
is never in the file, only the ciphertext.)

---

## 6. Settings (environment variables)

| Variable | Default | Role |
|---|---|---|
| `ONYX_VAULT_DIR` | `$HERMES_HOME/vault` otherwise `~/.hermes/vault` | Directory of the database and default key |
| `ONYX_VAULT_KEY` | — | Fernet key in plaintext (takes precedence over the file) |
| `ONYX_VAULT_KEY_FILE` | — | Path to a Fernet key (takes precedence over the default) |
| `ONYX_VAULT_MOUNT` | `app` | `router` forces mounting on the plugin router (tests) |

These variables must be present in the environment of the **Dashboard server**
(`hermes dashboard` / gateway), not in the client shell.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `401 {"detail":"Unauthorized"}` | no session token | `X-Hermes-Session-Token` header (see §4) |
| `404 {"detail":"Plugin not found"}` | plugin absent from `plugins.enabled`, or disabled at runtime | add the line from §3, check `plugins.disabled` |
| `404 {"detail":"No such API endpoint: …"}` | plugin enabled but `api`/`manifest.json` not found or path refused | check `~/.hermes/plugins/onyx-vault/dashboard/manifest.json`; consult the Dashboard log (`Plugin …: refusing unsafe api path`, `declares api=… but file not found`) |
| `503 {"code":"vault_unavailable",…}` | key missing, empty or invalid | §2; the response contains the exact command to replay |
| `health` → `key_permissions_secure:false` | key readable by group/all | `chmod 600 ~/.hermes/vault/onyx-vault.key` |
| `400 profile invalide` (invalid profile) | profile name outside `[a-z0-9._-]` | use e.g. `onyx` |
| `503` persisting after key creation | vault already instantiated without a key in another process | the key is re-read on every new instance; otherwise restart the Dashboard (outside this plugin's scope) |

---

## 8. Accepted security limitations

1. **Encryption at rest only.** The server process holds the key: anyone who
   can read `~/.hermes/vault/onyx-vault.key` (or the process memory) can decrypt
   everything. The 0600/0700 permissions are the only local barrier.
2. **The fingerprint is plaintext data**: the last 4 characters of each
   value are stored and returned (contract requirement). Below 8
   characters, the value is entirely masked (`****`) so that the fingerprint is
   not the secret itself.
3. **`used_by` is declarative.** It is an intent metadata
   (`["onyx-android"]`), not an ACL: it does not prevent any caller from reading the
   metadata of another profile. The only access control is the Dashboard
   token — hence the same level of trust as the Dashboard itself.
4. **No built-in key rotation, no read auditing.** Rotation =
   rewriting each secret with `vault.put` after changing the key (the vault does not
   migrate on its own); reads are not logged (only
   `put`/`revoke` are, without value).
5. **No soft delete.** `revoke` deletes the row; the ciphertext may
   remain in SQLite free pages until `VACUUM` (never the plaintext).
6. **The profile-scope bypass is documented and bounded**: the three
   vault routes are registered on the Dashboard application to escape the
   `_plugin_route_secret_scope` dependency, which reads `?profile=` as a **Hermes**
   profile and would answer `404 Profile 'onyx' does not exist` (measured
   behavior, see `tests/test_dashboard_mount.py`). Dashboard authentication,
   the per-request consent gate (`_plugin_api_runtime_gate`) and body
   limits remain active: they are mounted as middleware.
7. **`plugins.isolation: host` mode**: the plugin then runs in the profile's
   host process, where the Dashboard application is not present; the routes
   fall back to the plugin router and `?profile=` there undergoes the "Hermes profile"
   read. Use in-process mode (default) for this plugin.
8. **Maximum size of a value: 16 KiB**, HTTP body limited to 64 KiB.
